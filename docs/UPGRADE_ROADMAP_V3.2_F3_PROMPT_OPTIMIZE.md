# UPGRADE_ROADMAP_V3.2 – F3: Tối Ưu Prompt Giảm Token Tiêu Thụ Groq

Ngày tạo: 2026-05-17
Phụ thuộc: README_V3.1.md (snapshot hiện tại)

---

## 1. Vấn Đề Gốc

Model `openai/gpt-oss-120b` trên Groq có giới hạn TPM (Tokens Per Minute) nghiêm ngặt.
App hiện tại bị rate limit vì:

1. **System prompt dài** – lặp lại nhiều ngữ cảnh không cần thiết mỗi lần gọi.
2. **Description bài toán được paste toàn bộ** vào mọi lần gọi (cả khi chỉ cần một
   trường con).
3. **Không có caching** – mỗi lần sinh testcase đều gửi lại constraints từ đầu.
4. **Parallel calls không có throttle** – nhiều lần gọi API cùng lúc dễ vượt TPM.
5. **JSON schema response** có thể quá phức tạp, ép model "suy nghĩ" nhiều hơn.

---

## 2. Chiến Lược Tối Ưu (Ưu Tiên Cao → Thấp)

| # | Chiến lược | Giảm token ước tính | Độ phức tạp |
|---|---|---|---|
| 1 | Rút ngắn system prompt | 30–50% input tokens | Thấp |
| 2 | Cắt ngắn problem payload theo từng task | 20–40% | Thấp |
| 3 | Sequential thay vì parallel call | Không giảm token nhưng tránh spike | Thấp |
| 4 | Request throttle / retry-with-backoff | Không giảm token, giảm lỗi 429 | Trung bình |
| 5 | Token budget per call (max_tokens nhỏ hơn) | 10–20% output tokens | Thấp |

---

## 3. Thay Đổi Python – `ai_service`

### 3.1 `ai_service/services/groq_json.py`

#### Thêm retry-with-exponential-backoff

```python
import asyncio
import logging

logger = logging.getLogger(__name__)

MAX_RETRIES = 4
BASE_DELAY_SECONDS = 2.0   # 2s, 4s, 8s, 16s

async def groq_complete_with_retry(call_fn, *args, **kwargs):
    """
    Wrapper cho mọi lần gọi Groq API.
    Tự động retry khi gặp lỗi 429 (rate limit) với exponential backoff.
    call_fn là coroutine async function.
    """
    delay = BASE_DELAY_SECONDS
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            return await call_fn(*args, **kwargs)
        except Exception as e:
            err_str = str(e).lower()
            is_rate_limit = "429" in err_str or "rate limit" in err_str or "too many" in err_str
            if is_rate_limit and attempt < MAX_RETRIES:
                logger.warning(
                    f"Groq rate limit (attempt {attempt}/{MAX_RETRIES}), "
                    f"retry sau {delay:.0f}s..."
                )
                await asyncio.sleep(delay)
                delay *= 2   # exponential backoff
            else:
                raise   # Hết retry hoặc lỗi khác, re-raise
```

Bọc tất cả các lần gọi `groq_client.chat.completions.create(...)` bằng wrapper này.

#### Thêm semaphore giới hạn concurrent calls

```python
# Đặt ở module level trong groq_json.py
_groq_semaphore = asyncio.Semaphore(2)  # Tối đa 2 call đồng thời

async def groq_complete_limited(call_fn, *args, **kwargs):
    """Kết hợp semaphore + retry."""
    async with _groq_semaphore:
        return await groq_complete_with_retry(call_fn, *args, **kwargs)
```

### 3.2 `ai_service/services/testcase_generator.py`

#### Rút ngắn system prompt

Thay thế system prompt hiện tại bằng phiên bản cô đọng. Nguyên tắc:

- Không giải thích lý do → chỉ ra lệnh.
- Không ví dụ dài trong system → để trong user prompt nếu cần.
- Không lặp constraint schema trong system → schema một lần duy nhất.

```python
# TRƯỚC (ví dụ dài ~400 tokens)
TESTCASE_SYSTEM_PROMPT = """
Bạn là một chuyên gia tạo test cases cho competitive programming...
[nhiều đoạn giải thích, quy tắc, ví dụ]
"""

# SAU (~80 tokens)
TESTCASE_SYSTEM_PROMPT_COMPACT = """Generate competitive programming test inputs.
Output ONLY valid JSON: {"testcases":[{"input":"...","description":"...","is_edge_case":bool}]}
Rules: match input format exactly; no expected output; no explanation outside JSON."""
```

#### Cắt ngắn problem payload theo profile

Tạo hàm `_build_minimal_problem_context(problem, profile)`:

```python
def _build_minimal_problem_context(problem: ProblemSchema, profile: str) -> dict:
    """
    Trả về dict chứa chỉ những field cần thiết cho từng profile.
    Giảm ~40% input tokens so với truyền toàn bộ ProblemSchema.
    """
    base = {
        "input_format": (problem.input_format or "")[:400],
        "constraints": (problem.constraints or "")[:300],
    }
    if profile == "SMALL":
        # SMALL chỉ cần format và constraints
        base["sample_input"] = (problem.sample_inputs[0] if problem.sample_inputs else "")
        return base
    elif profile == "MEDIUM":
        base["problem_type"] = problem.problem_type
        return base
    else:  # KILLER
        base["problem_type"] = problem.problem_type
        base["tle_strategy"] = problem.tle_strategy or ""
        base["max_n"] = problem.max_constraint_n
        return base
```

Dùng `_build_minimal_problem_context` thay vì serialize toàn bộ `problem` vào prompt.

#### Giảm `max_tokens` theo profile

```python
MAX_TOKENS_BY_PROFILE = {
    "SMALL": 600,    # Testcase nhỏ, không cần nhiều token
    "MEDIUM": 900,
    "KILLER": 500,   # KILLER giờ chỉ cần script ngắn (F2)
}
```

Truyền `max_tokens=MAX_TOKENS_BY_PROFILE[profile]` vào mỗi lần gọi API.

#### Sequential thay vì gather() cho KILLER

```python
# TRƯỚC: song song, dễ spike TPM
killer_tasks = [generate_single_killer(...) for _ in range(killer_count)]
killers = await asyncio.gather(*killer_tasks)

# SAU: tuần tự với delay nhỏ
killers = []
for _ in range(killer_count):
    tc = await generate_single_killer(...)
    killers.append(tc)
    await asyncio.sleep(0.3)  # 300ms gap giữa các call KILLER
```

Áp dụng tương tự cho MEDIUM nếu `medium_count > 3`.

### 3.3 `ai_service/services/pipeline_orchestrator.py`

#### Cache problem analysis trong session

Nếu cùng một problem text được analyze nhiều lần (ví dụ user bấm retry), dùng
in-memory cache:

```python
from functools import lru_cache
import hashlib

_analysis_cache: dict[str, ProblemSchema] = {}

def _problem_cache_key(text: str) -> str:
    return hashlib.md5(text.encode()).hexdigest()

async def analyze_with_cache(text: str, groq_client) -> ProblemSchema:
    key = _problem_cache_key(text)
    if key in _analysis_cache:
        logger.info("Pipeline: dùng cached problem analysis")
        return _analysis_cache[key]
    result = await analyze_problem(text, groq_client)
    _analysis_cache[key] = result
    return result
```

Cache này là **in-memory, per-process** (không persist). Reset khi restart service.
Đủ để tránh analyze lại khi user bấm retry trong cùng session.

### 3.4 `ai_service/services/code_generator.py`

#### Rút ngắn TYPE_INSTRUCTIONS

`TYPE_INSTRUCTIONS` hiện tại có thể dài (nhiều đoạn giải thích). Rút gọn còn
bullet point ngắn, mỗi bullet ≤ 10 từ:

```python
# Ví dụ cho TLE
TYPE_INSTRUCTIONS_COMPACT = {
    "TLE": (
        "- Correct logic, slow complexity (e.g., O(N^2) or worse).\n"
        "- No sleep/dummy loops/infinite loops.\n"
        "- Must finish on small inputs, TLE on large.\n"
    ),
    "WA": (
        "- Correct structure, one subtle logic bug.\n"
        "- Bug comment: BUG_TYPE, BUG_DESC, FAILS_ON.\n"
        "- Must compile and run.\n"
    ),
    "AC": (
        "- Correct, efficient, production-quality code.\n"
    ),
}
```

#### Cắt problem description trong codegen prompt

```python
def _truncate_for_codegen(problem: ProblemSchema) -> dict:
    return {
        "title": problem.title or "",
        "description": (problem.description or "")[:500],  # Cắt còn 500 ký tự
        "input_format": (problem.input_format or "")[:300],
        "output_format": (problem.output_format or "")[:200],
        "constraints": (problem.constraints or "")[:200],
        "sample_inputs": problem.sample_inputs[:2] if problem.sample_inputs else [],
        "sample_outputs": problem.sample_outputs[:2] if problem.sample_outputs else [],
    }
```

---

## 4. Thay Đổi Java (Nhỏ)

### 4.1 `src/main/java/org/example/service/AIBridgeService.java`

Không cần thay đổi lớn ở Java. Một điều cần thêm là **timeout dài hơn** cho các
call có thể bị delay do retry:

```java
// Tăng timeout từ 30s lên 60s để chịu được backoff retry phía Python
private static final int API_TIMEOUT_SECONDS = 60;
```

---

## 5. Tóm Tắt Thay Đổi Và Tác Động

| File | Thay đổi | Giảm token |
|---|---|---|
| `groq_json.py` | Retry backoff + semaphore | Giảm lỗi 429, không giảm token |
| `testcase_generator.py` | Compact system prompt | ~200–300 tokens/call |
| `testcase_generator.py` | Minimal problem context | ~100–200 tokens/call |
| `testcase_generator.py` | max_tokens theo profile | ~100–300 tokens output/call |
| `testcase_generator.py` | Sequential KILLER calls | Tránh TPM spike |
| `pipeline_orchestrator.py` | In-memory analysis cache | Tiết kiệm 1 lần analyze khi retry |
| `code_generator.py` | Compact TYPE_INSTRUCTIONS | ~100–200 tokens/call |
| `code_generator.py` | Truncate problem description | ~100–200 tokens/call |

Ước tính tổng: giảm **40–60% token** mỗi pipeline run, giảm đáng kể nguy cơ
vượt TPM với Groq.

---

## 6. Verify

```powershell
# Python compile
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service

# Java compile
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Test thủ công:

1. Chạy pipeline 2–3 lần liên tiếp cho cùng một bài.
2. Kiểm tra log Python: không xuất hiện `429 Too Many Requests` thường xuyên.
3. Nếu có `429`, phải thấy log `"Groq rate limit (attempt X/4), retry sau Xs..."`.
4. Pipeline vẫn hoàn thành thành công sau khi retry.
5. Kiểm tra Groq dashboard (ảnh Image 1 trong ticket): đường `Total Processed Tokens`
   không còn vượt Rate Limit liên tục.

Smoke check retry logic:

```python
# Trong Python shell
import asyncio
from ai_service.services.groq_json import groq_complete_with_retry

async def fake_call():
    raise Exception("429 Too Many Requests")

try:
    asyncio.run(groq_complete_with_retry(fake_call))
except Exception as e:
    print(f"Expected failure after retries: {e}")
```

---

## 7. Lưu Ý

- **Không thay đổi logic sinh testcase** – chỉ tối ưu token. Output quality không
  được giảm.
- Compact system prompt phải vẫn đảm bảo AI hiểu đúng format JSON output.
  Test kỹ sau khi rút ngắn.
- `_analysis_cache` là dict đơn giản, không thread-safe với multiple workers.
  Nếu Uvicorn chạy với `--workers > 1`, bỏ qua cache hoặc dùng `asyncio.Lock`.
  Mặc định dev environment chạy 1 worker nên an toàn.
- `Semaphore(2)` có thể tăng lên 3 nếu Groq TPM limit đủ lớn.
  Bắt đầu với 2 để an toàn.
- Các truncation length (500, 300, 200...) là điểm khởi đầu. Điều chỉnh nếu
  output quality giảm.
