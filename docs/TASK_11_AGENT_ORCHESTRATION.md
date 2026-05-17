# TASK 11 – Nâng Toàn Bộ Pipeline Lên Multi-Step Agent

## Prerequisite

Task 07, 08, 09, 10 phải hoàn thành trước.

## Vấn đề còn lại

- Luồng hiện tại vẫn là: analyze → generate → done. Không có vòng lặp kiểm tra chất lượng tổng thể.
- Không có cơ chế phát hiện "test cases quá dễ" (ví dụ: toàn bộ KILLER cases đều AC bởi brute force).
- Không có progress feedback chi tiết cho người dùng khi pipeline chạy lâu.
- Không cache kết quả: phân tích lại đề giống nhau từ đầu mỗi lần.

## Mục tiêu

- Một `PipelineOrchestrator` điều phối toàn bộ luồng với state machine rõ ràng.
- Progress streaming về Java UI (SSE hoặc polling).
- Cache problem analysis + oracle code theo hash của đề bài.
- "Quality gate": kiểm tra độ khó của KILLER test cases.

---

## Bước 1: State Machine cho Pipeline

File mới: `ai_service/services/pipeline_orchestrator.py`

```python
from enum import Enum

class PipelineState(Enum):
    IDLE           = "IDLE"
    CLASSIFYING    = "CLASSIFYING"        # classify problem type
    GENERATING_SMALL = "GENERATING_SMALL" # sinh SMALL cases
    VALIDATING_ORACLE = "VALIDATING_ORACLE" # verify-fix oracle loop
    STRESS_TESTING = "STRESS_TESTING"     # stress loop
    GENERATING_MEDIUM = "GENERATING_MEDIUM"
    GENERATING_KILLER = "GENERATING_KILLER"
    QUALITY_GATE   = "QUALITY_GATE"       # kiểm tra killer có đủ khó không
    DONE           = "DONE"
    ERROR          = "ERROR"

class PipelineProgress(BaseModel):
    state: str
    message: str
    progress_pct: int       # 0-100
    testcases_ready: int
    testcases_target: int
    warnings: list[str] = []
```

---

## Bước 2: Endpoint `/pipeline/run` (async với SSE)

File: `ai_service/routers/pipeline.py`

```python
from fastapi.responses import StreamingResponse
import asyncio, json

@router.post("/pipeline/run")
async def run_pipeline(req: PipelineRequest):
    async def event_stream():
        orchestrator = PipelineOrchestrator(req)
        async for progress in orchestrator.run():
            data = json.dumps(progress.dict())
            yield f"data: {data}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")
```

`PipelineRequest`:
```python
class PipelineRequest(BaseModel):
    problem_text: str
    count: int = 10
    include_edge_cases: bool = True
    language_preference: str = "python"  # oracle language
```

---

## Bước 3: Orchestrator logic

```python
class PipelineOrchestrator:
    async def run(self):
        # 1. Classify
        yield PipelineProgress(state="CLASSIFYING", message="Đang phân loại dạng bài...", progress_pct=5)
        self.classification = await classify_problem(self.problem)
        
        # 2. Generate SMALL
        yield PipelineProgress(state="GENERATING_SMALL", message="Đang sinh SMALL test cases...", progress_pct=15)
        self.small_cases = await generate_testcases(self.problem, self.small_count, "SMALL", True)
        
        # 3. Validate oracle (Task 08 agent)
        yield PipelineProgress(state="VALIDATING_ORACLE", message="Đang xác minh oracle...", progress_pct=30)
        oracle_result = await stress_agent.get_verified_oracle(self.problem, self.small_cases)
        
        # 4. Stress test
        yield PipelineProgress(state="STRESS_TESTING", message=f"Stress testing ({STRESS_ROUNDS} rounds)...", progress_pct=45)
        stress_result = await stress_agent.run_stress(oracle_result, rounds=STRESS_ROUNDS)
        if stress_result.found_counterexample:
            self.warnings.append(f"Oracle discrepancy found on round {stress_result.round}")
        
        # 5. Generate MEDIUM
        yield PipelineProgress(state="GENERATING_MEDIUM", message="Đang sinh MEDIUM test cases...", progress_pct=60)
        self.medium_cases = await generate_testcases(self.problem, self.medium_count, "MEDIUM", True)
        
        # 6. Generate KILLER
        yield PipelineProgress(state="GENERATING_KILLER", message="Đang sinh KILLER test cases...", progress_pct=75)
        self.killer_cases = await generate_testcases(self.problem, self.killer_count, "KILLER", True)
        
        # 7. Quality gate
        yield PipelineProgress(state="QUALITY_GATE", message="Kiểm tra độ khó test cases...", progress_pct=88)
        quality = await self._quality_gate()
        if not quality.passed:
            self.warnings.extend(quality.issues)
        
        # 8. Done
        all_cases = self.small_cases + self.medium_cases + self.killer_cases
        yield PipelineProgress(
            state="DONE",
            message="Hoàn thành!",
            progress_pct=100,
            testcases_ready=len(all_cases),
            testcases_target=self.req.count,
            warnings=self.warnings,
        )
```

---

## Bước 4: Quality Gate

```python
async def _quality_gate(self) -> QualityResult:
    """
    Kiểm tra KILLER cases có đủ khó không:
    Chạy brute force trên KILLER cases với timeout ngắn.
    Nếu brute AC tất cả KILLER → test quá dễ → regenerate.
    """
    issues = []
    brute_code = self.oracle_result.brute_code
    
    killer_passed_by_brute = 0
    for case in self.killer_cases[:3]:  # Kiểm tra 3 đại diện
        result = await executor.run(brute_code, case["input"], "python", timeout=2)
        if result.strip() == case["expected_output"].strip():
            killer_passed_by_brute += 1
    
    if killer_passed_by_brute >= 3:
        issues.append("KILLER cases quá dễ – brute force pass hết trong 2 giây. Regenerate với constraints lớn hơn.")
        # Trigger regenerate
        self.killer_cases = await generate_testcases(
            self.problem, self.killer_count, "KILLER", True,
            extra_hint="Make inputs as large as constraints allow. Previous tests were too easy."
        )
    
    return QualityResult(passed=len(issues)==0, issues=issues)
```

---

## Bước 5: Cache theo hash đề bài

File: `ai_service/services/problem_cache.py`

```python
import hashlib, json
from pathlib import Path

CACHE_DIR = Path("cache/problems")
CACHE_DIR.mkdir(parents=True, exist_ok=True)

def problem_hash(problem_text: str) -> str:
    return hashlib.sha256(problem_text.encode()).hexdigest()[:16]

def load_cache(problem_text: str) -> dict | None:
    h = problem_hash(problem_text)
    path = CACHE_DIR / f"{h}.json"
    if path.exists():
        return json.loads(path.read_text())
    return None

def save_cache(problem_text: str, data: dict):
    h = problem_hash(problem_text)
    path = CACHE_DIR / f"{h}.json"
    path.write_text(json.dumps(data, indent=2))
```

Trong `pipeline_orchestrator.py`, kiểm tra cache trước khi chạy toàn bộ pipeline:

```python
cached = load_cache(req.problem_text)
if cached:
    yield PipelineProgress(state="DONE", message="Dùng kết quả đã cache.", progress_pct=100, ...)
    return
```

---

## Bước 6: Java nhận SSE progress

File: `AIBridgeService.java` – thêm `runPipeline` với streaming:

```java
public void runPipeline(PipelineRequest req, Consumer<PipelineProgress> onProgress)
        throws IOException, InterruptedException {

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/pipeline/run"))
        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(req)))
        .header("Content-Type", "application/json")
        .header("Accept", "text/event-stream")
        .build();

    HttpResponse<Stream<String>> response = httpClient.send(request,
        HttpResponse.BodyHandlers.ofLines());

    response.body().forEach(line -> {
        if (line.startsWith("data: ")) {
            String json = line.substring(6);
            PipelineProgress progress = gson.fromJson(json, PipelineProgress.class);
            Platform.runLater(() -> onProgress.accept(progress));
        }
    });
}
```

---

## Bước 7: Progress bar trong UI

Trong `MainController.java`, khi bấm "Phan tich de":

```java
progressBar.setVisible(true);
statusLabel.setText("Đang phân tích...");

aiBridgeService.runPipeline(req, progress -> {
    progressBar.setProgress(progress.getProgressPct() / 100.0);
    statusLabel.setText(progress.getMessage());

    if (progress.getState().equals("DONE")) {
        progressBar.setVisible(false);
        loadTestCases(progress.getAllTestCases());
        if (!progress.getWarnings().isEmpty()) {
            showWarnings(progress.getWarnings());
        }
    }
});
```

---

## Verification

```powershell
# 1. Test pipeline endpoint
$body = '{"problem_text":"...","count":10,"include_edge_cases":true}'
Invoke-RestMethod -Method Post -Uri http://localhost:8000/pipeline/run `
    -ContentType "application/json" -Body $body
# Phải thấy SSE stream với các state thay đổi

# 2. Test cache: gọi lần 2 với cùng problem_text
# State đầu tiên phải là DONE ngay lập tức

# 3. Test quality gate: dùng bài đơn giản (A+B)
# Phải thấy warning "KILLER cases quá dễ"

# 4. Compile Java
mvn -q compile

# 5. Smoke test UI: progress bar hiển thị và cập nhật khi bấm Phan tich de
```

## Definition of Done

- [ ] Progress bar hiển thị đúng % khi pipeline chạy.
- [ ] Cache hoạt động: lần 2 cùng đề xong ngay trong < 1 giây.
- [ ] Quality gate phát hiện KILLER quá dễ và tự regenerate.
- [ ] Warnings hiển thị trong UI khi có vấn đề.
- [ ] Không có regression với các task 07–10.
