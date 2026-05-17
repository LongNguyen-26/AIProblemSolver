# UPGRADE ROADMAP V3 — AIProblemSolver

Tài liệu này hướng dẫn coding agent nâng cấp app theo từng Phase độc lập, giải quyết 4 vấn đề chính sau Task 11.

---

## Tổng Quan Vấn Đề

| # | Vấn đề | Mức độ | Phase |
|---|--------|--------|-------|
| 1 | KILLER testcase sinh quá lâu, tỉ lệ thành công thấp | Cao | A |
| 2 | Không đủ loại testcase (thiếu coverage) | Cao | B |
| 3 | "Phan tich de" có thể báo lỗi → UX kém | Cao | C |
| 4 | WA code sinh ra vẫn pass AC trên SMALL testcases | Trung bình | D |

Mỗi Phase có thể làm độc lập. Thứ tự khuyến nghị: **C → A → B → D**.

---

## PHASE C — ZERO-ERROR UX (Ưu tiên cao nhất)

> **Mục tiêu:** Bấm "Phan tich de" không bao giờ văng lỗi ra UI. Mọi thất bại đều bị xử lý gracefully và app tiếp tục chạy với kết quả tốt nhất có thể.

### C.1 — Java: Global Error Boundary trong MainController

**File:** `src/main/java/org/example/ui/controller/MainController.java`

Bọc toàn bộ logic `analyzeProblem()` bằng try-catch tổng, cập nhật status bar thay vì throw:

```java
// PATTERN: mọi bước đều có fallback
private void analyzeProblem() {
    try {
        runPipelineWithFallback();
    } catch (Exception e) {
        // KHÔNG bao giờ show error dialog
        Platform.runLater(() -> {
            statusLabel.setText("Phan tich gap su co, dang thu lai...");
        });
        runLegacyFallback(); // fallback sang luong analyze legacy
    }
}
```

**Các điểm cần bọc thêm:**
- `AIBridgeService.runPipeline()` — nếu SSE fail hoặc timeout, fallback sang `/analyze` + `/testcase`
- `AIBridgeService.callStress()` — nếu stress fail, tiếp tục với brute oracle (không retry vô hạn)
- `ExecutionService.generateOutputs()` — nếu compile oracle fail, mark testcase expected_output = "N/A" thay vì crash
- `ResultController.generateCode()` — nếu codegen fail, show message "Sinh code that bai, thu lai" trong UI thay vì exception

### C.2 — Python: Global Exception Handler trong FastAPI

**File:** `ai_service/main.py`

```python
from fastapi import Request
from fastapi.responses import JSONResponse

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    import traceback
    return JSONResponse(
        status_code=500,
        content={
            "error": str(exc),
            "detail": traceback.format_exc()[-500:],  # truncate
            "fallback": True
        }
    )
```

Mỗi router function cũng cần try-except riêng để trả partial result thay vì 500:

```python
# Ví dụ trong routers/pipeline.py
@router.post("/pipeline/run")
async def run_pipeline(...):
    try:
        async for event in orchestrator.run(...):
            yield event
    except Exception as e:
        # Trả DONE với warning thay vì crash SSE stream
        yield {
            "state": "DONE",
            "problem": partial_problem,
            "all_testcases": partial_testcases,
            "warnings": [f"Pipeline gap loi: {str(e)}"],
            "progress_pct": 100
        }
```

### C.3 — Java: SSE Stream Timeout & Reconnect

**File:** `src/main/java/org/example/service/AIBridgeService.java`

SSE stream có thể bị treo nếu Python không gửi event. Cần timeout per-event:

```java
// Thêm watchdog thread: nếu không nhận event trong 30s, abort và dùng fallback
ScheduledFuture<?> watchdog = scheduler.schedule(() -> {
    connection.cancel(true);
    runLegacyAnalyzeFallback(problemText);
}, 30, TimeUnit.SECONDS);

// Reset watchdog mỗi khi nhận event
onEventReceived(() -> {
    watchdog.cancel(false);
    watchdog = scheduler.schedule(..., 30, TimeUnit.SECONDS);
});
```

### C.4 — Stress Agent: Fail-Safe Return

**File:** `ai_service/services/stress_testing_agent.py`

Hiện tại nếu stress agent fail qua nhiều round, có thể return lỗi. Sửa để **luôn return kết quả**:

```python
async def run_stress(self, ...) -> StressResult:
    try:
        # ... logic hiện tại
        return result
    except Exception as e:
        # Fallback: trả brute oracle như trusted oracle
        return StressResult(
            trusted=False,
            trusted_oracle_code=self.last_brute_code or "",
            message=f"Stress agent gap loi: {e}. Dung brute oracle tam thoi.",
            rounds_completed=self.rounds_completed,
            found_counterexample=False
        )
```

### Kiểm tra Phase C hoàn tất

- [ ] Bấm "Phan tich de" với input rỗng → không crash
- [ ] Tắt Python service → Java hiển thị "Service offline, thu lai sau" thay vì exception
- [ ] Input text quá dài (>50000 chars) → pipeline trả warning, không crash
- [ ] Timeout mô phỏng (Python sleep 120s) → Java tự fallback sau 30s

---

## PHASE A — KILLER TESTCASE: NHANH VÀ ĐẢM BẢO SINH ĐƯỢC

> **Mục tiêu:** KILLER testcases phải được sinh ra với độ tin cậy ~95%+, thời gian chấp nhận được.

### Hiểu nguyên nhân hiện tại

KILLER testcase thường fail vì:
1. Prompt quá mở → AI sinh ra input giống MEDIUM
2. Size validation quá chặt → input bị reject mặc dù hợp lệ
3. AI không hiểu "worst case" cho từng problem type → sinh ra input ngẫu nhiên
4. Không có retry thông minh → thất bại im lặng

### A.1 — Tách riêng KILLER Generator với Prompt Chuyên Biệt

**File:** `ai_service/services/testcase_generator.py`

Thêm function `generate_killer_cases()` riêng biệt, không dùng cùng prompt với SMALL/MEDIUM:

```python
KILLER_STRATEGIES = {
    "ARRAY_SEQUENCE": [
        "All elements equal to MAX_VALUE (n=MAX_N)",
        "Strictly increasing sequence n=MAX_N",  
        "Alternating max/min pattern n=MAX_N",
        "All elements -MAX_VALUE (minimum case)",
        "Single large positive followed by n-1 zeros"
    ],
    "GRAPH_UNDIRECTED": [
        "Complete graph n=MAX_N/2 (dense)",
        "Long path graph (bamboo) n=MAX_N",
        "Star graph center + MAX_N-1 leaves",
        "Two cliques connected by single edge"
    ],
    "TREE": [
        "Bamboo tree (path) n=MAX_N",
        "Star tree n=MAX_N",
        "Perfect binary tree n closest power of 2 below MAX_N",
        "Caterpillar tree (spine + leaves)"
    ],
    "DP_1D": [
        "All states reachable, n=MAX_N",
        "Worst case transitions (all values equal)",
    ],
    "DP_2D": [
        "n=m=MAX_N_SQRT (square grid)",
        "n=1, m=MAX_N (degenerate row)",
    ],
    "GRAPH_DIRECTED": [
        "DAG with MAX_N nodes, MAX_M edges in topological order",
        "Dense graph n=MAX_N, m=MAX_M"
    ],
    # ... thêm các type khác tương tự
}

async def generate_killer_cases(self, problem, count, existing_inputs):
    problem_type = problem.get("problem_type", "ARRAY_SEQUENCE")
    strategies = KILLER_STRATEGIES.get(problem_type, KILLER_STRATEGIES["ARRAY_SEQUENCE"])
    
    results = []
    for strategy in strategies[:count]:
        # Gọi AI với ĐÚNG strategy này
        tc = await self._generate_single_killer(problem, strategy)
        if tc and tc["input"] not in existing_inputs:
            results.append(tc)
    
    # Nếu chưa đủ, generate random KILLER
    while len(results) < count:
        tc = await self._generate_random_killer(problem)
        if tc:
            results.append(tc)
    
    return results[:count]
```

### A.2 — Prompt KILLER Cải Tiến

Thay vì prompt chung, dùng prompt có **template cụ thể** cho từng strategy:

```python
KILLER_PROMPT_TEMPLATE = """
You are generating a KILLER test case for a competitive programming problem.

Problem type: {problem_type}
Constraints: {constraints}
Strategy: {strategy}

YOUR TASK: Generate EXACTLY ONE test case input that:
1. Uses MAXIMUM possible values (n={max_n})
2. Follows this exact strategy: {strategy}
3. Is syntactically valid per the input format
4. Would cause O(n^2) or worse algorithms to TLE

CRITICAL OUTPUT FORMAT:
- Output ONLY the raw test input text
- NO explanation, NO code, NO markdown
- Start directly with the first line of input

Input format: {input_format}

Generate the killer test case now:
"""
```

### A.3 — Parallel Generation cho KILLER

Thay vì sinh KILLER cases tuần tự, chạy parallel:

**File:** `ai_service/services/pipeline_orchestrator.py`

```python
import asyncio

async def generate_killer_parallel(self, problem, count):
    strategies = get_killer_strategies(problem["problem_type"])
    
    # Sinh tất cả KILLER cases song song
    tasks = [
        self.tc_generator.generate_single_killer(problem, strategy)
        for strategy in strategies[:count * 2]  # Dư 2x để có buffer
    ]
    
    results = await asyncio.gather(*tasks, return_exceptions=True)
    
    # Lọc exception, lấy đủ count
    valid = [r for r in results if not isinstance(r, Exception) and r]
    return valid[:count]
```

Điều này giảm thời gian KILLER generation từ O(count) → O(1) về mặt wall-clock time.

### A.4 — KILLER Quality Gate Thực Dụng

**File:** `ai_service/services/pipeline_orchestrator.py`

Quality gate hiện tại chỉ dựa trên kích thước heuristic. Cải thiện:

```python
def is_valid_killer(self, tc_input: str, problem: dict) -> bool:
    """Kiểm tra KILLER case có đủ mạnh không."""
    constraints = problem.get("constraints", [])
    max_n = extract_max_n(constraints)  # parse n <= 100000 từ constraints
    
    # Rule 1: Size check - KILLER phải có ít nhất 60% max_n
    input_size = len(tc_input.split())
    if max_n > 0 and input_size < max_n * 0.6:
        return False
    
    # Rule 2: Value range check - phải có giá trị gần max
    numbers = extract_numbers(tc_input)
    if numbers and max(abs(x) for x in numbers) < max_n * 0.5:
        return False
    
    return True

def extract_max_n(constraints: list) -> int:
    """Parse max N từ constraints."""
    import re
    for c in constraints:
        m = re.search(r'n\s*<=?\s*(\d+)', c, re.IGNORECASE)
        if m:
            return int(m.group(1))
    return 0
```

### A.5 — Regenerate KILLER Nếu Không Đủ

**File:** `ai_service/services/pipeline_orchestrator.py`

```python
MAX_KILLER_RETRIES = 3

async def ensure_killer_cases(self, problem, count):
    for attempt in range(MAX_KILLER_RETRIES):
        cases = await self.generate_killer_parallel(problem, count * 2)
        valid = [c for c in cases if self.is_valid_killer(c["input"], problem)]
        
        if len(valid) >= count:
            return valid[:count]
        
        # Yield progress warning
        yield {
            "state": "GENERATING_KILLER",
            "message": f"KILLER attempt {attempt+1}: {len(valid)}/{count}. Retrying...",
            "progress_pct": 60 + attempt * 5
        }
    
    # Fallback: dùng MEDIUM cases promote lên làm KILLER
    yield {"state": "GENERATING_KILLER", "message": "KILLER fallback: promoting MEDIUM cases", "progress_pct": 80}
    return await self.promote_medium_to_killer(problem, count)
```

### Kiểm tra Phase A hoàn tất

- [ ] Bấm "Phan tich de" với problem ARRAY_SEQUENCE → sinh đủ KILLER trong <20s
- [ ] KILLER input size >= 60% MAX_N
- [ ] Progress bar không bị treo ở bước GENERATING_KILLER
- [ ] Nếu AI trả thiếu KILLER, app tự retry và không văng lỗi

---

## PHASE B — TESTCASE COVERAGE ĐẦY ĐỦ

> **Mục tiêu:** Mỗi lần phân tích đảm bảo đủ các loại testcase theo chuẩn Competitive Programming.

### B.1 — Taxonomy Testcase Chuẩn CP

Tham khảo Codeforces/IOI/ICPC, mỗi problem cần các nhóm testcase sau:

```python
REQUIRED_TESTCASE_TAXONOMY = {
    "boundary": {
        "description": "Giá trị biên (n=1, n=MAX, val=MIN, val=MAX)",
        "min_count": 2,
        "profiles": ["SMALL"]
    },
    "edge_structural": {
        "description": "Cấu trúc đặc biệt (empty, single element, all equal)",
        "min_count": 2,
        "profiles": ["SMALL"]
    },
    "adversarial": {
        "description": "Input thiết kế để phá common bugs",
        "min_count": 3,
        "profiles": ["SMALL", "MEDIUM"]
    },
    "stress_random": {
        "description": "Random valid input để smoke test",
        "min_count": 2,
        "profiles": ["SMALL"]
    },
    "performance": {
        "description": "Input tối đa để test TLE",
        "min_count": 3,
        "profiles": ["KILLER"]
    },
    "sample": {
        "description": "Sample cases từ đề bài",
        "min_count": 1,
        "profiles": ["SMALL"]
    }
}
```

### B.2 — Coverage Checker

**File mới:** `ai_service/services/testcase_coverage_checker.py`

```python
class TestcaseCoverageChecker:
    def check_coverage(self, testcases: list, problem: dict) -> dict:
        """
        Kiểm tra coverage và trả về danh sách loại còn thiếu.
        Dùng heuristic phân loại dựa trên description của testcase.
        """
        coverage = {cat: 0 for cat in REQUIRED_TESTCASE_TAXONOMY}
        
        for tc in testcases:
            desc = tc.get("description", "").lower()
            if "sample" in desc or "official" in desc:
                coverage["sample"] += 1
            elif "boundary" in desc or "minimum" in desc or "maximum" in desc:
                coverage["boundary"] += 1
            elif "edge" in desc or "equal" in desc or "empty" in desc:
                coverage["edge_structural"] += 1
            elif "killer" in desc or "max" in desc.lower():
                coverage["performance"] += 1
            elif "random" in desc:
                coverage["stress_random"] += 1
            else:
                coverage["adversarial"] += 1
        
        missing = []
        for cat, info in REQUIRED_TESTCASE_TAXONOMY.items():
            if coverage[cat] < info["min_count"]:
                missing.append({
                    "category": cat,
                    "have": coverage[cat],
                    "need": info["min_count"],
                    "profiles": info["profiles"]
                })
        
        return {
            "coverage": coverage,
            "missing": missing,
            "complete": len(missing) == 0
        }
```

### B.3 — Top-up Theo Coverage

**File:** `ai_service/services/pipeline_orchestrator.py`

Sau khi sinh xong initial cases, chạy coverage check và top-up:

```python
async def fill_coverage_gaps(self, problem, testcases):
    checker = TestcaseCoverageChecker()
    report = checker.check_coverage(testcases, problem)
    
    for missing in report["missing"]:
        cat = missing["category"]
        needed = missing["need"] - missing["have"]
        profile = missing["profiles"][0]  # Lấy profile ưu tiên nhất
        
        new_cases = await self.generate_targeted_cases(
            problem, 
            category=cat,
            count=needed,
            profile=profile
        )
        testcases.extend(new_cases)
    
    return testcases

async def generate_targeted_cases(self, problem, category, count, profile):
    """Sinh testcase theo category cụ thể."""
    prompt_hint = CATEGORY_PROMPT_HINTS[category]  # Xem B.4
    return await self.tc_generator.generate_with_hint(
        problem, count, profile, hint=prompt_hint
    )
```

### B.4 — Category Prompt Hints

**File:** `ai_service/services/testcase_generator.py`

```python
CATEGORY_PROMPT_HINTS = {
    "boundary": "Focus on boundary values: n=1, n=MAX_N, all values = MIN, all values = MAX",
    "edge_structural": "Focus on structural edge cases: empty arrays, single elements, all equal values, sorted ascending, sorted descending",
    "adversarial": "Design input that specifically breaks these common bugs: off-by-one errors, integer overflow, wrong handling of negative numbers, wrong handling of duplicates",
    "stress_random": "Generate completely random valid input within constraints",
    "performance": "Generate maximum-size input (n=MAX_N) with values chosen to maximize algorithm work",
    "sample": "Use exactly the sample input provided in the problem statement"
}
```

### B.5 — Java: Hiển Thị Coverage Report

**File:** `src/main/java/org/example/ui/controller/TestCaseController.java`

Sau khi load testcases, tính và hiển thị coverage summary:

```java
// Thêm vào phía dưới bảng testcase
private void updateCoverageSummary(List<TestCase> testCases) {
    Map<String, Integer> coverage = new HashMap<>();
    for (TestCase tc : testCases) {
        String category = inferCategory(tc.getDescription());
        coverage.merge(category, 1, Integer::sum);
    }
    
    // Hiển thị: "Coverage: 6/6 loại ✓" hoặc "Coverage: 4/6 loại - thiếu: boundary, adversarial"
    StringBuilder sb = new StringBuilder("Coverage: ");
    // ... build summary string
    coverageLabel.setText(sb.toString());
}
```

### Kiểm tra Phase B hoàn tất

- [ ] Sau "Phan tich de", có ít nhất 1 testcase có description chứa "sample"
- [ ] Có ít nhất 2 testcase có description chứa "boundary" hoặc "minimum/maximum"
- [ ] Có ít nhất 3 testcase với profile KILLER
- [ ] Coverage label hiển thị đúng

---

## PHASE D — WA CODE PHẢI THẬT SỰ WA

> **Mục tiêu:** Code sinh với loại WA phải fail trên ít nhất 1 testcase, không được pass tất cả AC.

### Nguyên nhân hiện tại

Prompt sinh WA hiện tại chỉ nói "tạo code có bug" → AI sinh code "gần đúng" → may mắn pass SMALL cases vì SMALL cases không đủ mạnh để expose bug.

Hai hướng giải quyết song song:

### D.1 — Cải Thiện WA Prompt

**File:** `ai_service/services/code_generator.py`

```python
WA_PROMPT_V2 = """
You are generating a WRONG ANSWER solution for a competitive programming problem.

Problem: {problem_description}

REQUIREMENTS FOR THE WA CODE:
1. The code must be SYNTACTICALLY CORRECT and COMPILE WITHOUT ERRORS
2. The code must FAIL on at least one of these test cases:
{sample_test_cases_with_answers}

3. The bug must be SUBTLE — not obviously wrong at first glance
4. Choose ONE of these bug categories (pick the most appropriate):
   - Off-by-one: Wrong loop boundary (i < n vs i <= n)
   - Wrong formula: Small calculation error (n*(n+1)/2 vs n*(n-1)/2) 
   - Missing case: Forgets to handle n=1, or negative numbers, or all-equal arrays
   - Wrong initialization: Variable initialized to 0 instead of -INF, or vice versa
   - Greedy mistake: Correct structure but wrong greedy choice
   - DP transition error: dp[i] = dp[i-1] + ... instead of dp[i] = max(dp[i-1], ...)

5. In a comment at the top of the code, write:
   // BUG: [one sentence describing the exact bug]
   // FAILS ON: [describe what kind of input exposes this bug]

IMPORTANT: The code MUST produce wrong output on the provided sample cases OR
on cases where [specific condition related to the problem].

Generate the WA code now:
"""
```

### D.2 — Post-Generation WA Validation

**File:** `src/main/java/org/example/ui/controller/ResultController.java`

Sau khi sinh WA code, chạy test và kiểm tra xem có thật sự WA không:

```java
private void generateAndValidateWACode(Problem problem, List<TestCase> testCases) {
    // Bước 1: Sinh WA code
    String waCode = aiService.generateCode(problem, "WA", language);
    
    // Bước 2: Chạy WA code trên tất cả testcase
    List<ExecutionResult> results = executionService.runAllTests(waCode, language, testCases);
    
    // Bước 3: Kiểm tra xem có ít nhất 1 WA không
    long waCount = results.stream()
        .filter(r -> r.getVerdict() == Verdict.WA)
        .count();
    
    if (waCount == 0) {
        // WA code pass tất cả → sinh lại với feedback
        String feedback = buildWAFeedback(results, testCases);
        waCode = aiService.regenerateWACode(problem, language, feedback);
        results = executionService.runAllTests(waCode, language, testCases);
        waCount = results.stream().filter(r -> r.getVerdict() == Verdict.WA).count();
    }
    
    if (waCount == 0) {
        // Vẫn không WA → thêm hardcoded wrong case vào output
        waCode = injectKnownBug(waCode, testCases.get(0));
    }
    
    displayResults(waCode, results);
}

private String buildWAFeedback(List<ExecutionResult> results, List<TestCase> testCases) {
    StringBuilder sb = new StringBuilder();
    sb.append("The generated WA code actually passed ALL test cases. ");
    sb.append("You must create code that FAILS on one of these cases:\n");
    for (int i = 0; i < Math.min(3, testCases.size()); i++) {
        TestCase tc = testCases.get(i);
        ExecutionResult r = results.get(i);
        sb.append(String.format("Input: %s | Expected: %s | Got: %s\n",
            tc.getInput().substring(0, Math.min(50, tc.getInput().length())),
            tc.getExpectedOutput(),
            r.getActualOutput()
        ));
    }
    return sb.toString();
}
```

### D.3 — Adversarial Testcases Tăng Xác Suất Catch WA

Để WA code bị catch, cần testcase "adversarial" — thiết kế để phá common bugs. Xem B.4 để sinh loại testcase này. Kết hợp Phase B + D sẽ tăng đáng kể tỷ lệ WA code bị detect.

### D.4 — Python: Regenerate WA Endpoint

**File:** `ai_service/routers/codegen.py`

Thêm endpoint mới cho Java gọi khi WA code không thật sự WA:

```python
@router.post("/codegen/wa_retry")
async def regenerate_wa_code(request: WARetryRequest):
    """
    Sinh lại WA code khi code trước pass tất cả test.
    Request có thêm feedback về các test đã pass.
    """
    return await code_generator.generate_code(
        problem=request.problem,
        code_type="WA",
        language=request.language,
        error_log=request.feedback,  # Feedback chứa thông tin test đã pass
        validation_cases=request.test_samples
    )
```

### Kiểm tra Phase D hoàn tất

- [ ] Sinh WA code → chạy test → ít nhất 1 test có verdict WA
- [ ] Nếu WA code pass hết → tự động retry và sinh lại
- [ ] Sau tối đa 3 retry, WA code phải có ít nhất 1 WA verdict
- [ ] Comment ở đầu WA code mô tả đúng bug

---

## Thứ Tự Thực Hiện Khuyến Nghị

```
Phase C (Zero-error UX)
    └── C.1 Java global error boundary
    └── C.2 Python global exception handler
    └── C.3 SSE timeout watchdog
    └── C.4 Stress agent fail-safe
    ↓
Phase A (KILLER testcase)
    └── A.1 Tách KILLER generator riêng
    └── A.2 Prompt KILLER theo strategy
    └── A.3 Parallel generation
    └── A.4 Quality gate thực dụng
    └── A.5 Retry nếu thiếu KILLER
    ↓
Phase B (Coverage đầy đủ)
    └── B.1 Taxonomy chuẩn CP
    └── B.2 Coverage checker
    └── B.3 Top-up theo coverage
    └── B.4 Category prompt hints
    └── B.5 Java hiển thị coverage
    ↓
Phase D (WA code thật sự WA)
    └── D.1 WA prompt v2
    └── D.2 Post-generation validation
    └── D.3 Kết hợp adversarial cases từ Phase B
    └── D.4 WA retry endpoint
```

---

## Checklist Tổng Kết

### Trước khi bắt đầu mỗi Phase
- [ ] Đọc lại README_V2.md để nắm trạng thái hiện tại
- [ ] Chạy `git status --short` để xem file nào đang dirty
- [ ] Chạy Python compileall để kiểm tra baseline

### Sau mỗi thay đổi Python
```powershell
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service
```

### Sau mỗi thay đổi Java
```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

### Smoke test sau mỗi Phase
```powershell
Invoke-RestMethod http://localhost:8000/health
# Paste problem text → Phan tich de → kiểm tra không có error dialog
# Kiểm tra KILLER cases được sinh
# Kiểm tra Coverage hiển thị
# Kiểm tra WA code thật sự WA
```

---

## Lưu Ý Quan Trọng

1. **Không bao giờ để AI sinh expected output** — nguyên tắc bất biến từ Task 07.
2. **Không revert sandbox/** — đây là artifact tạm, không phải source chính.
3. **Khi sửa API schema** — cập nhật cả Python models/schemas.py lẫn Java model/ tương ứng.
4. **Phase C phải làm trước** — đảm bảo foundation vững trước khi thêm tính năng mới.
5. **Parallel generation (A.3) cần test kỹ** — asyncio.gather có thể ẩn exception nếu không xử lý return_exceptions=True.
