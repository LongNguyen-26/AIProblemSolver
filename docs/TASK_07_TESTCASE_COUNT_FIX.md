# TASK 07 – Fix Sinh Đủ Số Lượng Test Case

## Vấn đề

- AI trả về ít testcase hơn yêu cầu mà không có cơ chế phát hiện hay bổ sung.
- Java nhận danh sách rồi hiển thị ngay, không kiểm tra `len(testcases) == requested`.
- Không có vòng lặp top-up để gọi thêm khi thiếu.

## Mục tiêu sau task này

- App **luôn đạt đủ** số test case người dùng yêu cầu (±1 do rounding pyramid).
- Python service trả về đúng số lượng hoặc retry nội bộ trước khi respond.
- Java có top-up loop nếu service vẫn trả thiếu.

---

## Thay đổi Python (`ai_service/services/testcase_generator.py`)

### 1. Thêm tham số `count` vào prompt sinh testcase

Prompt hiện tại không chỉ định rõ số lượng yêu cầu. Sửa hàm `generate_testcases` để:

```python
# Thêm vào system prompt
SYSTEM_PROMPT_SUFFIX = """
CRITICAL: You MUST return EXACTLY {count} test cases in the JSON array.
If you cannot think of {count} distinct cases, generate variations of existing cases
with different valid inputs. Never return fewer than {count} items.
"""
```

### 2. Thêm retry + merge loop trong `generate_testcases`

```python
async def generate_testcases(problem, count, profile, include_edge_cases):
    MAX_ATTEMPTS = 3
    collected = []
    attempt = 0

    while len(collected) < count and attempt < MAX_ATTEMPTS:
        still_needed = count - len(collected)
        result = await _call_ai_for_testcases(
            problem=problem,
            count=still_needed,
            profile=profile,
            include_edge_cases=include_edge_cases,
            existing_inputs=[tc["input"] for tc in collected],  # tránh trùng
        )
        new_cases = result.get("testcases", [])
        # Lọc duplicate input
        existing_set = {tc["input"].strip() for tc in collected}
        for tc in new_cases:
            if tc["input"].strip() not in existing_set:
                collected.append(tc)
                existing_set.add(tc["input"].strip())
        attempt += 1

    return {"testcases": collected[:count], "checker_code": result.get("checker_code", "")}
```

### 3. Truyền `existing_inputs` vào prompt

Khi gọi top-up lần 2+, thêm vào prompt:

```
Already generated inputs (DO NOT repeat these):
{existing_inputs_summary}
Generate {still_needed} NEW and DISTINCT inputs for profile {profile}.
```

---

## Thay đổi Java (`MainController.java`)

### 4. Kiểm tra số lượng sau mỗi batch và gọi top-up

Sau khi nhận response `/testcase` cho mỗi profile:

```java
// Sau khi nhận SMALL batch:
int received = smallCases.size();
int expected = targetSmall;
if (received < expected) {
    log.warn("SMALL: nhận {} / {} → gọi top-up", received, expected);
    int topUp = expected - received;
    List<TestCase> extra = aiBridgeService.generateTestCases(
        problem, topUp, "SMALL", includeEdgeCases
    );
    smallCases.addAll(extra);
}
```

Áp dụng tương tự cho MEDIUM và KILLER.

### 5. Thêm hàm `generateTestCases` với retry trong `AIBridgeService.java`

```java
public List<TestCase> generateTestCases(Problem problem, int count,
                                         String profile, boolean edgeCases)
        throws IOException, InterruptedException {
    int attempts = 0;
    List<TestCase> result = new ArrayList<>();
    while (result.size() < count && attempts < 3) {
        List<TestCase> batch = callTestcaseEndpoint(problem, count - result.size(),
                                                     profile, edgeCases);
        result.addAll(batch);
        attempts++;
    }
    return result.subList(0, Math.min(result.size(), count));
}
```

---

## Thay đổi Schema (`models/schemas.py`)

Thêm field `requested_count` vào request để backend biết số cần trả:

```python
class TestCaseRequest(BaseModel):
    problem: ProblemSchema
    count: int = 5
    requested_count: int = 5   # alias rõ ràng hơn, dùng cái này
    include_edge_cases: bool = True
    profile: str = "SMALL"
    existing_inputs: list[str] = []   # NEW – danh sách input đã có để tránh trùng
```

---

## Verification

```powershell
# 1. Compile-check Python
python -m compileall ai_service

# 2. Test API trực tiếp – yêu cầu 8 SMALL cases
$body = '{"problem":{...},"count":8,"profile":"SMALL","include_edge_cases":true}'
Invoke-RestMethod -Method Post -Uri http://localhost:8000/testcase `
    -ContentType "application/json" -Body $body
# Kiểm tra response.testcases.length == 8

# 3. Compile Java
mvn -q compile

# 4. Smoke test trong app: đặt "So test case" = 10, bấm "Phan tich de"
# Kiểm tra bảng Test Cases có đúng ~10 rows
```

## Definition of Done

- [ ] Gọi `/testcase` với `count=8` luôn trả về 8 cases (test 5 lần).
- [ ] App với 10 test cases hiển thị 9–10 rows (±1 do rounding pyramid).
- [ ] Không có duplicate input trong cùng một profile batch.
