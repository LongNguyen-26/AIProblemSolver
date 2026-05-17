# TASK 08 – Nâng Stress Testing Lên Agent Workflow

## Vấn đề

- Oracle code AI sinh ra thường fail compile hoặc cho kết quả sai trên các input edge case.
- Không có feedback loop: nếu brute compile fail thì không retry với error message.
- Stress testing hiện tại là zero-shot: sinh oracle → chạy ngay → không sửa khi fail.
- Random input generator cũng do AI sinh, thường không cover đủ constraint.

## Mục tiêu

- Biến stress testing thành một **agent loop** gồm các bước verify-and-fix.
- Oracle code phải pass compile + small cases trước khi dùng cho stress.
- Nếu oracle fail, AI nhận error log và retry có thông tin.
- Input generator cũng được verify: output đúng format, trong constraint.

---

## Kiến trúc mới: `StressTestingAgent`

Tạo file mới: `ai_service/services/stress_testing_agent.py`

### Luồng agent

```
[1] Classify problem type (graph/dp/math/string/...)
       ↓
[2] Generate brute-force oracle (attempt 1)
       ↓
[3] Compile & run brute on small cases
       ↓ FAIL? → send compile error + small case → AI fixes → goto [3] (max 3 retries)
       ↓ PASS
[4] Generate optimized oracle (attempt 1)
       ↓
[5] Compile & run optimized on small cases
       ↓ FAIL? → AI fixes → goto [5] (max 3 retries)
       ↓ PASS
[6] Generate random input generator code
       ↓
[7] Validate generator: run 3 samples, check format matches constraints
       ↓ FAIL? → AI fixes generator → goto [7] (max 2 retries)
       ↓ PASS
[8] Run stress loop: gen input → run brute → run optimized → compare
       ↓ Mismatch found → return (input, brute_out, opt_out) as counterexample
       ↓ No mismatch after N rounds → optimized oracle trusted
[9] Return: trusted optimized oracle + counterexample if found
```

---

## Chi tiết từng bước

### Bước 2–3: Verify-and-fix oracle loop

```python
async def _get_verified_oracle(
    problem: ProblemSchema,
    oracle_type: str,       # "BRUTE" | "ORACLE"
    small_cases: list[dict],
    max_retries: int = 3,
) -> tuple[str, bool]:      # (code, is_trusted)

    code = await code_gen.generate_code(problem, oracle_type, "python")
    
    for attempt in range(max_retries):
        compile_error = await executor.try_compile(code, "python")
        if compile_error:
            feedback = f"Compile error:\n{compile_error}\nFix the code."
            code = await code_gen.fix_code(problem, code, feedback, oracle_type)
            continue

        wrong_case = None
        for case in small_cases:
            actual = await executor.run(code, case["input"], "python", timeout=10)
            if actual.strip() != case["expected_output"].strip():
                wrong_case = case
                actual_out = actual
                break

        if wrong_case is None:
            return code, True   # trusted

        feedback = (
            f"Wrong answer on input:\n{wrong_case['input']}\n"
            f"Expected: {wrong_case['expected_output']}\n"
            f"Got: {actual_out}\n"
            f"Fix the logic."
        )
        code = await code_gen.fix_code(problem, code, feedback, oracle_type)

    return code, False   # không trust được sau max_retries
```

### Bước 6–7: Verify input generator

```python
async def _get_verified_generator(
    problem: ProblemSchema,
    profile: str,
    max_retries: int = 2,
) -> tuple[str, bool]:

    gen_code = await code_gen.generate_input_generator(problem, profile)

    for attempt in range(max_retries):
        samples = []
        errors = []
        for seed in range(3):
            result = await executor.run(
                gen_code, str(seed), "python", timeout=5
            )
            if result.startswith("ERROR"):
                errors.append(result)
            else:
                samples.append(result)

        if errors:
            feedback = f"Generator crashed:\n{errors[0]}\nFix it."
            gen_code = await code_gen.fix_code(problem, gen_code, feedback, "GENERATOR")
            continue

        # Kiểm tra format bằng validator đơn giản (dựa constraints từ Problem)
        format_error = _validate_input_format(samples, problem)
        if format_error:
            feedback = f"Generated input fails format check:\n{format_error}"
            gen_code = await code_gen.fix_code(problem, gen_code, feedback, "GENERATOR")
            continue

        return gen_code, True

    return gen_code, False
```

### Bước 8: Stress loop

```python
async def run_stress(
    brute_code: str,
    opt_code: str,
    gen_code: str,
    rounds: int = 50,
    timeout: int = 5,
) -> dict:

    for i in range(rounds):
        seed = str(random.randint(0, 10**9))
        inp = await executor.run(gen_code, seed, "python", timeout=3)
        if inp.startswith("ERROR"):
            continue

        brute_out = await executor.run(brute_code, inp, "python", timeout=timeout)
        opt_out   = await executor.run(opt_code,   inp, "python", timeout=timeout)

        if brute_out.strip() != opt_out.strip():
            return {
                "found_counterexample": True,
                "input": inp,
                "brute_output": brute_out,
                "opt_output": opt_out,
                "round": i,
            }

    return {"found_counterexample": False, "rounds": rounds}
```

---

## Thêm endpoint mới: `POST /stress`

File: `ai_service/routers/stress.py`

```python
@router.post("/stress")
async def run_stress_test(req: StressRequest):
    agent = StressTestingAgent()
    result = await agent.run(
        problem=req.problem,
        small_cases=req.small_cases,
        rounds=req.rounds or 30,
    )
    return result
```

`StressRequest` schema:
```python
class StressRequest(BaseModel):
    problem: ProblemSchema
    small_cases: list[TestCaseSchema]
    rounds: int = 30
```

Response:
```python
class StressResult(BaseModel):
    trusted_oracle_code: str
    trusted_oracle_language: str
    found_counterexample: bool
    counterexample_input: str = ""
    rounds_completed: int
    oracle_retries: int
    generator_trusted: bool
```

---

## Thay đổi Java (`MainController.java`)

Sau khi có SMALL cases với expected output, thay vì gọi `/testcase` ngay cho MEDIUM/KILLER:

```java
// 1. Gọi /stress để lấy trusted oracle
StressResult stressResult = aiBridgeService.runStress(problem, smallCases, 30);

if (stressResult.isTrustedOracle()) {
    // Dùng trusted oracle để tính expected output cho MEDIUM + KILLER
    cachedOracle = stressResult.getTrustedOracleCode();
    cachedOracleLang = stressResult.getTrustedOracleLanguage();
} else {
    // Fallback: chỉ dùng small cases, hiển thị warning
    showWarning("Oracle không đáng tin cậy – chỉ dùng small test cases.");
}
```

---

## Thêm `fix_code` vào `code_generator.py`

```python
async def fix_code(
    problem: ProblemSchema,
    broken_code: str,
    error_feedback: str,
    code_type: str,
) -> str:
    prompt = f"""
You previously generated this {code_type} code for the problem below.
It has a bug. Fix it.

Problem:
{problem.description}

Constraints:
{problem.constraints}

Broken code:
```python
{broken_code}
```

Error / wrong answer feedback:
{error_feedback}

Return ONLY the fixed source code, no explanation.
"""
    return await groq_client.request_text(prompt)
```

---

## Verification

```powershell
# 1. Python compile check
python -m compileall ai_service

# 2. Test /stress endpoint
$body = '{
  "problem": {"title":"A+B","description":"Print a+b","input_format":"Two integers a b","output_format":"a+b","constraints":"1<=a,b<=1000","sample_input":"1 2","sample_output":"3"},
  "small_cases": [{"id":"t1","input":"1 2","expected_output":"3","description":"sample","is_edge_case":false}],
  "rounds": 10
}'
Invoke-RestMethod -Method Post -Uri http://localhost:8000/stress `
    -ContentType "application/json" -Body $body
# Kiểm tra response.trusted_oracle_code != "" và rounds_completed == 10

# 3. Compile Java
mvn -q compile
```

## Definition of Done

- [ ] `POST /stress` hoạt động và trả về oracle code đã verify.
- [ ] Oracle luôn pass small cases trước khi được dùng.
- [ ] Nếu AI sinh oracle fail compile 3 lần, trả về `trusted: false` thay vì crash.
- [ ] Java hiển thị warning khi oracle không tin cậy.
- [ ] Stress loop chạy 30 rounds trong dưới 60 giây với bài đơn giản.
