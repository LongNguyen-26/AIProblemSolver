# TASK 09 – Fix TLE Generation: Correctness + Performance

## Vấn đề

1. **TLE code hay bị WA/RE** vì AI không biết chính xác "chậm ở đâu" mà sinh code sai.
2. **Validate TLE quá chậm**: chạy code TLE trên tất cả test case (kể cả KILLER) → timeout cả app.
3. **TLE không scale đúng**: code TLE dùng vòng lặp giả (dummy loop) thay vì thuật toán thực sự chậm → không phụ thuộc input nên với bài có N lớn vẫn chạy nhanh.
4. **Không biết complexity target**: AI không biết cần sinh code O(n²) hay O(2^n) hay O(n!) vì không có constraint analysis.

## Mục tiêu

- TLE code phải **chậm phụ thuộc input**, không phải dummy loop cố định.
- Validate TLE chỉ trên **SMALL cases** (2–3 cases) thay vì toàn bộ.
- Thêm **complexity classification** để chọn đúng chiến lược TLE.
- Thêm **verify-and-fix loop** tương tự Task 08.

---

## Bước 1: Thêm Complexity Classifier

File: `ai_service/services/complexity_analyzer.py`

```python
COMPLEXITY_PROMPT = """
Analyze this competitive programming problem and determine:
1. The EXPECTED optimal time complexity (e.g. O(n log n), O(n sqrt n))
2. A SLOW but CORRECT complexity that would TLE on large inputs (e.g. O(n^2), O(2^n))
3. The input size N based on constraints

Return JSON:
{
  "optimal_complexity": "O(n log n)",
  "tle_target_complexity": "O(n^2)",
  "max_n": 100000,
  "tle_strategy": "nested loop / brute enumeration / exponential backtracking / ...",
  "tle_explanation": "why this approach is slow but correct"
}

Problem:
{problem_text}
"""

async def analyze_complexity(problem: ProblemSchema) -> ComplexityInfo:
    result = await groq_client.request_json(
        COMPLEXITY_PROMPT.format(problem_text=format_problem(problem))
    )
    return ComplexityInfo(**result)
```

Schema `ComplexityInfo`:
```python
class ComplexityInfo(BaseModel):
    optimal_complexity: str
    tle_target_complexity: str
    max_n: int
    tle_strategy: str       # "nested_loop" | "backtracking" | "brute_dp" | ...
    tle_explanation: str
```

---

## Bước 2: Sửa prompt sinh TLE code

File: `ai_service/services/code_generator.py`

Thay prompt TLE hiện tại (dùng dummy loop) bằng:

```python
TLE_PROMPT = """
Generate a {language} solution for this problem that is:
1. CORRECT: produces the right answer for all valid inputs
2. SLOW: uses {tle_strategy} with complexity {tle_complexity}
3. The slowness must come from the algorithm, NOT from artificial sleep/dummy loops

Specifically:
- Target complexity: {tle_complexity} on input size N up to {max_n}
- Strategy: {tle_strategy}
- Explanation: {tle_explanation}

FORBIDDEN:
- sleep(), usleep(), delay
- Infinite loops or loops not bounded by input
- Random or non-deterministic behavior
- Any deliberate wrong answer

The code MUST produce correct output. It will be validated against expected outputs.

Sample cases to verify against:
{validation_cases}

Problem:
{problem_description}

Constraints:
{constraints}

Return ONLY the source code.
"""
```

---

## Bước 3: Validate chỉ trên SMALL cases

File: `src/main/java/org/example/service/ResultController.java` (hoặc nơi validate TLE)

```java
private static final int TLE_VALIDATION_MAX_CASES = 3;

private boolean validateTleCandidate(String code, String language, List<TestCase> allCases) {
    // Chỉ lấy SMALL cases hoặc tối đa 3 cases đầu
    List<TestCase> validationSubset = allCases.stream()
        .filter(tc -> tc.getDescription().startsWith("[SMALL]"))
        .limit(TLE_VALIDATION_MAX_CASES)
        .collect(Collectors.toList());

    if (validationSubset.isEmpty()) {
        validationSubset = allCases.subList(0, Math.min(TLE_VALIDATION_MAX_CASES, allCases.size()));
    }

    for (TestCase tc : validationSubset) {
        ExecutionResult result = executionService.runCode(code, language, tc.getInput(), 
                                                          VALIDATION_TIMEOUT_SECONDS);
        String verdict = computeVerdict(result, tc.getExpectedOutput());
        // TLE hoặc AC đều chấp nhận được
        if (!verdict.equals("AC") && !verdict.equals("TLE")) {
            log.warn("TLE candidate got {} on case {}", verdict, tc.getId());
            return false;
        }
    }
    return true;
}
```

---

## Bước 4: Verify-and-fix loop cho TLE

Tương tự Task 08, thêm fix_code loop cho TLE:

```java
private String generateValidatedTleCode(String language) throws Exception {
    ComplexityInfo complexity = aiBridgeService.analyzeComplexity(problem);
    int maxRetries = 3;

    for (int attempt = 0; attempt < maxRetries; attempt++) {
        // Gọi /codegen với complexity info
        CodeResult candidate = aiBridgeService.generateCodeWithComplexity(
            problem, "TLE", language, smallCases, complexity
        );

        boolean valid = validateTleCandidate(candidate.getCode(), language, allTestCases);
        if (valid) {
            return candidate.getCode();
        }

        // Thu thập feedback từ lần chạy fail
        String feedback = collectTleValidationFeedback(candidate.getCode(), language);
        // Gọi /codegen/fix với feedback
        complexity = complexity; // giữ nguyên complexity target
        // Pass feedback vào request tiếp theo qua validation_cases + error_log field
    }

    throw new Exception("Không thể sinh TLE code hợp lệ sau " + maxRetries + " lần thử.");
}
```

---

## Bước 5: Thêm endpoint `/analyze/complexity`

File: `ai_service/routers/analyze.py` – thêm route:

```python
@router.post("/analyze/complexity")
async def analyze_complexity(req: AnalyzeRequest):
    analyzer = ComplexityAnalyzer()
    result = await analyzer.analyze(req.problem)
    return result
```

Java gọi endpoint này trước khi sinh TLE code.

---

## Bước 6: Thêm `error_log` vào `CodegenRequest`

```python
class CodegenRequest(BaseModel):
    problem: ProblemSchema
    type: str
    language: str
    validation_cases: list[TestCaseSchema] = []
    error_log: str = ""          # NEW – feedback từ lần thử trước
    complexity_info: dict = {}   # NEW – kết quả từ complexity analyzer
```

Trong `code_generator.py`, nếu `error_log` không rỗng, thêm vào prompt:

```python
if request.error_log:
    prompt += f"\nPrevious attempt failed:\n{request.error_log}\nFix these issues."
```

---

## Verification

```powershell
# 1. Test complexity analyzer
$body = '{"problem":{"title":"Sort","constraints":"n<=100000","..."}}'
Invoke-RestMethod -Method Post -Uri http://localhost:8000/analyze/complexity `
    -ContentType "application/json" -Body $body
# Kiểm tra tle_target_complexity != "" và tle_strategy != ""

# 2. Sinh TLE code cho bài sorting
# Kiểm tra code KHÔNG chứa dummy loop cố định
# Kiểm tra code chứa thuật toán chậm (O(n^2) sort, backtracking, ...)

# 3. Validate chỉ chạy trên SMALL cases (log phải thấy <= 3 runs)

# 4. Compile Java
mvn -q compile
```

## Definition of Done

- [ ] TLE code không chứa `dummy`, `sleep`, vòng lặp không phụ thuộc input.
- [ ] TLE validation chạy ≤ 3 cases và hoàn thành trong < 30 giây.
- [ ] Complexity analyzer trả về `tle_strategy` khác nhau cho các dạng bài khác nhau.
- [ ] Nếu TLE candidate bị WA, có error feedback cụ thể truyền vào retry.
- [ ] Sau 3 retry, app thông báo lỗi rõ ràng thay vì treo.
