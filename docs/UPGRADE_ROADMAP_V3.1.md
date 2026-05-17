# UPGRADE ROADMAP V3.1 — AIProblemSolver

Snapshot cơ sở: README_V3.md (2026-05-17).
Phase C và Phase A đã hoàn tất. Phase B đã reverse nhưng chưa sạch.

---

## Tổng Quan

| Phase | Mục tiêu | Trạng thái |
|-------|----------|------------|
| C | Zero-error UX | ✅ Hoàn tất |
| A | KILLER testcase nhanh hơn, có strategy | ✅ Hoàn tất |
| B | Coverage checker (taxonomy 6 nhóm) | ❌ Đã reverse nhưng còn rác |
| **E** | **Dọn rác Phase B + đảm bảo đủ 3 profile** | 🔲 Cần làm |
| **D** | **WA code phải thật sự WA** | 🔲 Cần làm |

Thứ tự thực hiện: **E → D**.

---

## PHASE E — DỌN SẠCH PHASE B VÀ ĐẢM BẢO ĐỦ 3 PROFILE

> **Vấn đề thực sự:** Người dùng báo testcase sinh ra không đủ 3 loại SMALL / MEDIUM / KILLER —
> chỉ ra SMALL hoặc MEDIUM. Đây là vấn đề phân phối (distribution), không phải vấn đề coverage taxonomy.
> Phase B cố giải quyết bằng cách sai (taxonomy checker), cần dọn sạch rồi giải quyết đúng chỗ.

### E.1 — Dọn Rác Phase B: Python

**Mục tiêu:** Xóa hoàn toàn các artifact Phase B còn sót lại.

#### E.1.1 — Xóa file coverage checker

Xóa file sau nếu không còn được dùng ở nơi nào khác:

```
ai_service/services/testcase_coverage_checker.py
```

Trước khi xóa, `grep -r "testcase_coverage_checker" ai_service/` để xác nhận không còn import nào ngoài `pipeline_orchestrator.py`.

#### E.1.2 — Dọn `pipeline_orchestrator.py`

Tìm và xóa/thay thế các symbol sau:

```python
# Xóa các import này
from .testcase_coverage_checker import TestcaseCoverageChecker
from .testcase_coverage_checker import REQUIRED_TESTCASE_TAXONOMY

# Xóa các constant này
MIN_COVERAGE_TESTCASES = ...

# Xóa các method này hoàn toàn
async def _fill_coverage_gaps(self, ...)
async def _generate_targeted_cases(self, ...)
def _sample_cases(self, ...)
def _prioritize_cases_for_coverage(self, ...)

# Sửa _build_test_pyramid_plan để không còn enforce minimum 13
```

Sau khi xóa, kiểm tra `_build_test_pyramid_plan(...)` còn logic coverage không và đưa về logic đơn giản:

```python
def _build_test_pyramid_plan(self, count: int) -> dict:
    """
    Chia count testcase thành 3 profile theo tỉ lệ cố định.
    Tỉ lệ: SMALL 40%, MEDIUM 30%, KILLER 30% (tối thiểu 1 mỗi loại).
    """
    small  = max(1, round(count * 0.4))
    killer = max(1, round(count * 0.3))
    medium = max(1, count - small - killer)
    return {"SMALL": small, "MEDIUM": medium, "KILLER": killer}
```

#### E.1.3 — Dọn `testcase_generator.py`

Xóa các symbol Phase B không còn cần thiết:

```python
# Xóa nếu không còn dùng ở nơi nào khác
CATEGORY_PROMPT_HINTS = { ... }

async def generate_with_hint(self, problem, count, profile, hint): ...
```

KILLER_STRATEGIES và generate_killer_cases() từ Phase A giữ nguyên — đây là Phase A, không phải Phase B.

Sau khi xóa, chạy:

```powershell
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service
```

#### E.1.4 — Dọn duplicate import

Trong `pipeline_orchestrator.py` có `import asyncio` bị duplicate. Xóa dòng thừa.

### E.2 — Dọn Rác Phase B: Java

#### E.2.1 — `MainController.java`

Tìm và xóa:

```java
private static final int MIN_COVERAGE_TESTCASE_COUNT = 13;
```

Nếu constant này đang được dùng để enforce minimum testcase count, thay bằng logic phân phối đúng (xem E.3).

#### E.2.2 — `TestCaseController.java`

Xóa toàn bộ coverage summary logic:

```java
// Xóa method này
private void updateCoverageSummary(List<TestCase> testCases) { ... }

// Xóa mọi lời gọi tới updateCoverageSummary(...)
```

#### E.2.3 — `testcase_view.fxml`

Xóa phần tử `coverageLabel` và container của nó khỏi FXML.

Sau khi xóa, chạy:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

### E.3 — Đảm Bảo Pipeline Luôn Sinh Đủ 3 Profile

Đây là phần cốt lõi của Phase E. Nguyên nhân thật sự khiến KILLER vắng mặt:

1. `_build_test_pyramid_plan` bị ghi đè bởi logic coverage → không còn enforce KILLER riêng.
2. Nếu KILLER generation fail (quá ít case pass quality gate), pipeline không báo rõ và tiếp tục với 0 KILLER.
3. Java nhận về `all_testcases` nhưng không verify xem có đủ 3 profile không.

#### E.3.1 — Python: Enforce Profile Quota Nghiêm Ngặt

**File:** `ai_service/services/pipeline_orchestrator.py`

Sửa luồng generate để mỗi profile là một bước riêng, không thể bị skip:

```python
async def _generate_all_profiles(self, problem, plan, existing_inputs, request_id):
    """
    Sinh testcase theo đúng plan {SMALL: n, MEDIUM: m, KILLER: k}.
    Mỗi profile được sinh độc lập, fail của một profile không ảnh hưởng profile khác.
    Luôn trả về dict có đủ 3 key, giá trị có thể là list rỗng nếu fail hoàn toàn.
    """
    results = {"SMALL": [], "MEDIUM": [], "KILLER": []}

    # --- SMALL ---
    try:
        yield self._progress("GENERATING_SMALL", f"Sinh {plan['SMALL']} SMALL cases...", 20)
        small_cases = await self._generate_profile(
            problem, "SMALL", plan["SMALL"], existing_inputs
        )
        results["SMALL"] = small_cases
        existing_inputs.extend(tc["input"] for tc in small_cases)
    except Exception as e:
        yield self._progress("GENERATING_SMALL", f"SMALL gap loi: {e}", 25)

    # --- MEDIUM ---
    try:
        yield self._progress("GENERATING_MEDIUM", f"Sinh {plan['MEDIUM']} MEDIUM cases...", 40)
        medium_cases = await self._generate_profile(
            problem, "MEDIUM", plan["MEDIUM"], existing_inputs
        )
        results["MEDIUM"] = medium_cases
        existing_inputs.extend(tc["input"] for tc in medium_cases)
    except Exception as e:
        yield self._progress("GENERATING_MEDIUM", f"MEDIUM gap loi: {e}", 45)

    # --- KILLER ---
    try:
        yield self._progress("GENERATING_KILLER", f"Sinh {plan['KILLER']} KILLER cases...", 60)
        killer_cases = await self._generate_killer_with_retry(
            problem, plan["KILLER"], existing_inputs
        )
        results["KILLER"] = killer_cases
    except Exception as e:
        yield self._progress("GENERATING_KILLER", f"KILLER gap loi: {e}", 65)

    # --- Kiểm tra và cảnh báo ---
    warnings = []
    for profile, cases in results.items():
        if len(cases) == 0:
            warnings.append(f"Khong sinh duoc bat ky {profile} case nao.")
        elif len(cases) < plan[profile]:
            warnings.append(f"{profile}: can {plan[profile]}, chi sinh duoc {len(cases)}.")

    all_testcases = results["SMALL"] + results["MEDIUM"] + results["KILLER"]
    return all_testcases, warnings
```

**Lưu ý quan trọng:** `yield` trong method này là yield của async generator. Nếu kiến trúc hiện tại không hỗ trợ yield bên trong method con (vì SSE stream là ở orchestrator), hãy tách thành callback hoặc dùng queue để pass progress event ra ngoài.

#### E.3.2 — Python: Hàm `_generate_killer_with_retry` Độc Lập

Tách riêng retry logic của KILLER để dễ test và dễ debug:

```python
MAX_KILLER_RETRIES = 3

async def _generate_killer_with_retry(self, problem, count, existing_inputs):
    """
    Sinh KILLER cases với retry. Nếu sau MAX_KILLER_RETRIES vẫn thiếu,
    promote MEDIUM cases (tăng kích thước) thay vì trả về rỗng.
    """
    for attempt in range(MAX_KILLER_RETRIES):
        try:
            # Dùng asyncio.gather để sinh song song (từ Phase A)
            cases = await self._generate_killer_parallel(problem, count * 2, existing_inputs)
            valid = [c for c in cases if self._is_valid_killer(c["input"], problem)]

            if len(valid) >= count:
                return valid[:count]

            # Chưa đủ, tiếp tục retry
        except Exception:
            pass  # retry

    # Fallback: promote MEDIUM → KILLER bằng cách tăng n
    return await self._promote_medium_to_killer(problem, count, existing_inputs)

async def _promote_medium_to_killer(self, problem, count, existing_inputs):
    """
    Fallback cuối cùng: sinh MEDIUM cases nhưng yêu cầu AI dùng n = MAX_N.
    Mark description là [KILLER-promoted].
    """
    cases = await self._generate_profile(problem, "MEDIUM", count, existing_inputs,
                                          force_max_n=True)
    for c in cases:
        c["description"] = c.get("description", "").replace("[MEDIUM]", "[KILLER-promoted]")
    return cases
```

#### E.3.3 — Java: Verify Profile Distribution Sau Khi Nhận Kết Quả

**File:** `src/main/java/org/example/ui/controller/MainController.java`

Sau khi nhận `all_testcases` từ pipeline DONE event, log profile distribution vào status bar:

```java
private void logProfileDistribution(List<TestCase> testCases) {
    long small  = testCases.stream().filter(tc -> tc.getDescription().contains("[SMALL")).count();
    long medium = testCases.stream().filter(tc -> tc.getDescription().contains("[MEDIUM")).count();
    long killer = testCases.stream().filter(tc ->
        tc.getDescription().contains("[KILLER")).count();

    String summary = String.format(
        "Testcases: %d SMALL | %d MEDIUM | %d KILLER",
        small, medium, killer
    );
    Platform.runLater(() -> statusLabel.setText(summary));

    // Nếu thiếu KILLER hoàn toàn, log warning rõ ràng (không show dialog)
    if (killer == 0) {
        Platform.runLater(() ->
            statusLabel.setText(summary + " ⚠ Khong co KILLER case — kiem tra pipeline log")
        );
    }
}
```

Gọi `logProfileDistribution(testCases)` ngay sau khi parse DONE event.

#### E.3.4 — Tỉ Lệ Phân Phối Mặc Định

Tỉ lệ khuyến nghị khi user nhập N testcases:

| N | SMALL | MEDIUM | KILLER |
|---|-------|--------|--------|
| 5 | 2 | 2 | 1 |
| 8 | 3 | 3 | 2 |
| 10 | 4 | 3 | 3 |
| 15 | 6 | 5 | 4 |
| 20 | 8 | 6 | 6 |

Công thức: `killer = max(1, floor(N * 0.3))`, `medium = max(1, floor(N * 0.3))`, `small = N - medium - killer`.

### Kiểm Tra Phase E Hoàn Tất

```powershell
# Python compile sạch
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service

# Java compile sạch
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile

# Grep xác nhận không còn artifact Phase B
grep -r "TestcaseCoverageChecker\|REQUIRED_TESTCASE_TAXONOMY\|MIN_COVERAGE_TESTCASES\|fill_coverage_gaps\|generate_with_hint\|CATEGORY_PROMPT_HINTS" ai_service/
grep -r "MIN_COVERAGE_TESTCASE_COUNT\|updateCoverageSummary\|coverageLabel" src/
```

Kết quả mong đợi của grep: **không có dòng nào**.

Kiểm tra hành vi:

- [ ] N=10 → pipeline trả đúng ~4 SMALL, ~3 MEDIUM, ~3 KILLER
- [ ] Status bar sau phân tích hiển thị "X SMALL | Y MEDIUM | Z KILLER"
- [ ] Nếu AI fail sinh KILLER, app fallback promote MEDIUM và hiển thị warning, không crash
- [ ] Không còn coverageLabel trong UI

---

## PHASE D — WA CODE PHẢI THẬT SỰ WA

> **Vấn đề:** Code sinh với loại WA pass tất cả AC trên testcases hiện có.
> Hai nguyên nhân song song: (1) prompt WA không đủ mạnh, (2) không có vòng kiểm tra sau khi sinh.

### D.1 — WA Prompt V2

**File:** `ai_service/services/code_generator.py`

Thay thế prompt WA hiện tại bằng prompt có cấu trúc sau:

```python
WA_SYSTEM_PROMPT = """You are an expert competitive programmer tasked with writing
INTENTIONALLY WRONG solutions. Your bugs must be subtle — not obvious — but must
cause wrong output on specific inputs."""

WA_USER_PROMPT = """Problem:
{problem_description}

Input format: {input_format}
Output format: {output_format}
Constraints: {constraints}

Sample test cases (your code MUST fail on at least one of these):
{sample_cases_formatted}

TASK: Write a {language} solution that:
1. Compiles and runs without errors
2. Uses the CORRECT algorithm structure (not random/brute-force)
3. Contains EXACTLY ONE subtle bug from this list — choose the most natural fit:
   - Off-by-one: wrong loop bound (i < n instead of i <= n, or vice versa)
   - Wrong initialization: variable init to 0 instead of -1e18, or INT_MAX instead of 0
   - Missing edge case: does not handle n=1, all-equal array, negative values, or empty input
   - Wrong formula: small arithmetic error (e.g. n*(n-1)/2 instead of n*(n+1)/2)
   - Wrong greedy choice: correct greedy structure but picks min instead of max (or vice versa)
   - Wrong DP transition: dp[i] uses wrong previous state (dp[i-2] instead of dp[i-1])

REQUIRED: Add these two comment lines at the very top of your code:
// BUG_TYPE: [name of bug category chosen above]
// BUG_DESC: [one sentence: what is wrong and why it fails]
// FAILS_ON: [describe the shape of input that exposes this bug]

Output ONLY the source code. No explanation outside the comments."""
```

Cập nhật hàm build prompt trong `code_generator.py` để dùng template mới khi `code_type == "WA"`:

```python
def _build_wa_prompt(self, problem, language, sample_cases, error_log=""):
    sample_formatted = "\n".join(
        f"Input:\n{tc['input']}\nExpected output: {tc['expected_output']}"
        for tc in sample_cases[:3]  # Tối đa 3 sample để không overflow context
    )
    retry_note = f"\n\nPREVIOUS ATTEMPT FEEDBACK:\n{error_log}" if error_log else ""

    return WA_USER_PROMPT.format(
        problem_description=problem.get("description", ""),
        input_format=problem.get("input_format", ""),
        output_format=problem.get("output_format", ""),
        constraints="\n".join(problem.get("constraints", [])),
        sample_cases_formatted=sample_formatted,
        language=language
    ) + retry_note
```

### D.2 — Python: Endpoint `/codegen/wa_retry`

**File:** `ai_service/routers/codegen.py`

Thêm endpoint để Java gọi khi WA code không thật sự WA:

```python
class WaRetryRequest(BaseModel):
    problem: dict
    language: str
    feedback: str          # Mô tả các testcase mà WA code đã pass
    sample_cases: list     # Danh sách testcase để ràng buộc AI

@router.post("/codegen/wa_retry")
async def wa_retry(request: WaRetryRequest):
    """
    Sinh lại WA code khi code trước pass tất cả test.
    feedback mô tả test nào đã pass để AI cố tình fail trên đó.
    """
    code = await code_generator.generate_code(
        problem=request.problem,
        code_type="WA",
        language=request.language,
        error_log=request.feedback,
        validation_cases=request.sample_cases
    )
    return {"code": code}
```

Cũng thêm `WaRetryRequest` vào `ai_service/models/schemas.py` nếu project dùng Pydantic schema chung.

### D.3 — Java: Post-Generation WA Validation

**File:** `src/main/java/org/example/ui/controller/ResultController.java`

Thêm validation flow ngay sau khi sinh WA code:

```java
private static final int MAX_WA_RETRIES = 3;

private void generateWACodeWithValidation(Problem problem, List<TestCase> testCases, String language) {
    String waCode = null;
    List<ExecutionResult> results = null;

    for (int attempt = 0; attempt < MAX_WA_RETRIES; attempt++) {
        // Lần đầu: gọi /codegen bình thường
        // Lần 2+: gọi /codegen/wa_retry với feedback
        if (attempt == 0) {
            waCode = aiBridge.generateCode(problem, "WA", language, null, null);
        } else {
            String feedback = buildWAFeedback(results, testCases);
            waCode = aiBridge.retryWACode(problem, language, feedback, testCases.subList(0, 3));
        }

        if (waCode == null || waCode.isBlank()) continue;

        // Chạy trên tối đa 5 testcase (ưu tiên SMALL để nhanh)
        List<TestCase> checkCases = testCases.stream()
            .filter(tc -> tc.getDescription().contains("[SMALL"))
            .limit(5)
            .collect(Collectors.toList());
        if (checkCases.isEmpty()) checkCases = testCases.subList(0, Math.min(5, testCases.size()));

        results = executionService.runTests(waCode, language, checkCases);

        long waCount = results.stream().filter(r -> r.getVerdict() == Verdict.WA).count();
        if (waCount > 0) {
            // Đã có WA thật sự — dừng retry
            break;
        }

        // Log cảnh báo nhưng không crash
        Platform.runLater(() ->
            statusLabel.setText(String.format("WA code pass het lan %d, dang retry...", attempt + 1))
        );
    }

    // Hiển thị kết quả dù có WA thật hay không
    final String finalCode = waCode;
    final List<ExecutionResult> finalResults = results;
    Platform.runLater(() -> displayCodeAndResults(finalCode, finalResults));
}

private String buildWAFeedback(List<ExecutionResult> results, List<TestCase> testCases) {
    StringBuilder sb = new StringBuilder();
    sb.append("The previous WA code actually PASSED all test cases. ");
    sb.append("You MUST produce a solution that gives WRONG OUTPUT on at least one of these:\n\n");
    for (int i = 0; i < Math.min(3, testCases.size()); i++) {
        TestCase tc = testCases.get(i);
        sb.append(String.format("Test %d:\nInput: %s\nExpected: %s\n\n",
            i + 1,
            tc.getInput().substring(0, Math.min(100, tc.getInput().length())),
            tc.getExpectedOutput()
        ));
    }
    sb.append("Pick a bug that specifically fails on one of the above cases.");
    return sb.toString();
}
```

### D.4 — Java: `AIBridgeService.retryWACode()`

**File:** `src/main/java/org/example/service/AIBridgeService.java`

Thêm method gọi endpoint `/codegen/wa_retry`:

```java
public String retryWACode(Problem problem, String language, String feedback,
                           List<TestCase> sampleCases) {
    // Build request JSON tương tự generateCode nhưng gọi /codegen/wa_retry
    // Body: { problem, language, feedback, sample_cases }
    // Return: parse response["code"]
    // Nếu fail: return null (caller đã có fallback)
}
```

### D.5 — Kiểm Tra Bổ Sung: WA Code Cần SMALL Cases Mạnh

WA code dễ bị lộ nhất trên SMALL cases có các đặc điểm sau. Đảm bảo trong pool SMALL cases (từ Phase A) có ít nhất:

- 1 case với n=1
- 1 case với tất cả phần tử bằng nhau
- 1 case với tất cả phần tử âm (nếu problem cho phép)
- 1 case sample từ đề bài

Những điều này đã là một phần của KILLER_STRATEGIES trong Phase A. Đảm bảo các pattern tương tự cũng được sinh cho SMALL profile.

### Kiểm Tra Phase D Hoàn Tất

```powershell
# Python compile
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service

# Java compile
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Kiểm tra hành vi:

- [ ] Sinh WA code → trong source code có comment `// BUG_TYPE:` và `// BUG_DESC:`
- [ ] Chạy WA code trên SMALL testcases → ít nhất 1 verdict WA
- [ ] Nếu WA code pass hết → status bar hiển thị "WA retry lần 1/2/3", tự sinh lại
- [ ] Sau tối đa 3 retry, WA code hiển thị kết quả (dù có WA thật hay không — không crash)
- [ ] Endpoint `/codegen/wa_retry` trả HTTP 200 với field `code`

---

## Checklist Thực Hiện

### Trước khi bắt đầu

```powershell
git status --short
# Xác nhận không còn file untracked/dirty không liên quan
```

### Thứ tự thực hiện trong Phase E

1. E.1.1 → Xóa `testcase_coverage_checker.py`
2. E.1.2 → Dọn `pipeline_orchestrator.py` (xóa symbol Phase B, sửa pyramid plan)
3. E.1.3 → Dọn `testcase_generator.py` (xóa CATEGORY_PROMPT_HINTS, generate_with_hint)
4. E.1.4 → Fix duplicate import asyncio
5. Chạy Python compileall → phải pass
6. E.2.1 → Xóa MIN_COVERAGE_TESTCASE_COUNT trong Java
7. E.2.2 → Xóa coverage summary trong TestCaseController
8. E.2.3 → Xóa coverageLabel trong FXML
9. Chạy Maven compile → phải pass
10. E.3.1 → Sửa `_generate_all_profiles` enforce 3 profile
11. E.3.2 → Tách `_generate_killer_with_retry`
12. E.3.3 → Thêm `logProfileDistribution` trong Java
13. Chạy compileall + Maven compile → phải pass
14. Chạy grep kiểm tra không còn rác Phase B

### Thứ tự thực hiện trong Phase D

1. D.1 → Sửa WA prompt trong `code_generator.py`
2. Chạy Python compileall
3. D.2 → Thêm endpoint `/codegen/wa_retry`
4. D.3 → Thêm WA validation loop trong `ResultController.java`
5. D.4 → Thêm `retryWACode()` trong `AIBridgeService.java`
6. Chạy Maven compile
7. Test end-to-end

### Lệnh Hữu Ích

```powershell
# Python compile
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service

# Java compile
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile

# Health check
Invoke-RestMethod http://localhost:8000/health

# Grep kiểm tra rác Phase B (kết quả mong đợi: rỗng)
grep -r "TestcaseCoverageChecker\|REQUIRED_TESTCASE_TAXONOMY\|MIN_COVERAGE_TESTCASES\|fill_coverage_gaps\|generate_with_hint\|CATEGORY_PROMPT_HINTS" ai_service/
grep -r "MIN_COVERAGE_TESTCASE_COUNT\|updateCoverageSummary\|coverageLabel" src/

# Git status
git status --short
```

---

## Nguyên Tắc Bất Biến (Giữ Nguyên Từ Trước)

- `/testcase` chỉ sinh input. Không bao giờ để AI sinh expected output.
- Expected output do Java local oracle tính bằng `ExecutionService`.
- `sandbox/` là artifact tạm, không revert/commit nếu không được yêu cầu rõ.
- Khi sửa API schema, cập nhật cả Python `models/schemas.py` lẫn Java `model/` tương ứng.
- Không revert file dirty không liên quan.
