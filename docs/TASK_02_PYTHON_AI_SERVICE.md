# TASK_02_PYTHON_AI_SERVICE.md

## Mục tiêu
Xây dựng Python FastAPI microservice chạy tại `localhost:8000`, cung cấp 3 endpoints: `/analyze`, `/testcase`, `/codegen`.

---

## 1. Cấu trúc thư mục `ai_service/`

```
ai_service/
├── main.py
├── config.py
├── requirements.txt
├── .env.example
├── routers/
│   ├── __init__.py
│   ├── analyze.py
│   ├── testcase.py
│   └── codegen.py
├── services/
│   ├── __init__.py
│   ├── ocr_service.py
│   ├── problem_analyzer.py
│   ├── testcase_generator.py
│   └── code_generator.py
└── models/
    ├── __init__.py
    └── schemas.py
```

---

## 2. `ai_service/requirements.txt`

```
fastapi==0.111.0
uvicorn[standard]==0.29.0
openai>=1.30.0
pytesseract==0.3.10
Pillow==10.3.0
python-dotenv==1.0.1
pydantic>=2.0.0
```

---

## 3. `ai_service/.env.example`

```env
GROQ_API_KEY=gsk-your-key-here
GROQ_MODEL=openai/gpt-oss-120b
GROQ_BASE_URL=https://api.groq.com/openai/v1
MAX_TOKENS=4096
```

---

## 4. `ai_service/config.py`

```python
from dotenv import load_dotenv
import os

load_dotenv()

GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
GROQ_MODEL = os.getenv("GROQ_MODEL", "openai/gpt-oss-120b")
GROQ_BASE_URL = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1")
MAX_TOKENS = int(os.getenv("MAX_TOKENS", "4096"))

if not GROQ_API_KEY:
    raise ValueError("GROQ_API_KEY is not set in .env")
```

---

## 5. `ai_service/models/schemas.py`

```python
from pydantic import BaseModel
from typing import Optional, List

# ---- Shared ----
class ProblemSchema(BaseModel):
    title: str = ""
    description: str = ""
    input_format: str = ""
    output_format: str = ""
    constraints: List[str] = []
    sample_inputs: List[str] = []
    sample_outputs: List[str] = []

class TestCaseSchema(BaseModel):
    id: str
    input: str
    expected_output: str
    description: str = ""
    is_edge_case: bool = False

# ---- /analyze ----
class AnalyzeRequest(BaseModel):
    text: Optional[str] = None
    image_base64: Optional[str] = None

class AnalyzeResponse(BaseModel):
    problem: ProblemSchema

# ---- /testcase ----
class TestCaseRequest(BaseModel):
    problem: ProblemSchema
    count: int = 10
    include_edge_cases: bool = True

class TestCaseResponse(BaseModel):
    testcases: List[TestCaseSchema]
    checker_code: str = ""

# ---- /codegen ----
class CodeGenRequest(BaseModel):
    problem: ProblemSchema
    type: str = "AC"        # "AC" | "WA" | "TLE"
    language: str = "cpp"   # "cpp" | "python" | "java"

class CodeGenResponse(BaseModel):
    code: str
    language: str
    type: str
    explanation: str = ""
```

---

## 6. `ai_service/services/ocr_service.py`

```python
import base64
import io
from PIL import Image
import pytesseract

def image_base64_to_text(image_base64: str) -> str:
    """Decode base64 image và extract text bằng Tesseract OCR."""
    try:
        image_bytes = base64.b64decode(image_base64)
        image = Image.open(io.BytesIO(image_bytes))
        # Tăng độ chính xác: convert sang grayscale
        image = image.convert("L")
        text = pytesseract.image_to_string(image, lang="eng")
        return text.strip()
    except Exception as e:
        raise RuntimeError(f"OCR failed: {e}")
```

---

## 7. `ai_service/services/problem_analyzer.py`

```python
from openai import OpenAI
from config import GROQ_API_KEY, GROQ_MODEL, GROQ_BASE_URL, MAX_TOKENS
from models.schemas import ProblemSchema
import json

client = OpenAI(api_key=GROQ_API_KEY, base_url=GROQ_BASE_URL)

ANALYZE_SYSTEM_PROMPT = """
You are an expert competitive programming problem analyzer.
Given a problem statement, extract and return ONLY a JSON object with these fields:
- title: string
- description: string (full problem description)  
- input_format: string
- output_format: string
- constraints: array of strings (each constraint as one element)
- sample_inputs: array of strings
- sample_outputs: array of strings

Respond with ONLY valid JSON, no markdown, no explanation.
"""

def analyze_problem(text: str) -> ProblemSchema:
    response = client.chat.completions.create(
        model=GROQ_MODEL,
        max_tokens=MAX_TOKENS,
        response_format={"type": "json_object"},
        messages=[
            {"role": "system", "content": ANALYZE_SYSTEM_PROMPT},
            {"role": "user", "content": f"Analyze this problem:\n\n{text}"}
        ]
    )
    data = json.loads(response.choices[0].message.content)
    return ProblemSchema(**data)
```

---

## 8. `ai_service/services/testcase_generator.py`

```python
from openai import OpenAI
from config import GROQ_API_KEY, GROQ_MODEL, GROQ_BASE_URL, MAX_TOKENS
from models.schemas import ProblemSchema, TestCaseSchema, TestCaseResponse
import json
import uuid

client = OpenAI(api_key=GROQ_API_KEY, base_url=GROQ_BASE_URL)

def generate_testcases(
    problem: ProblemSchema, count: int, include_edge_cases: bool
) -> TestCaseResponse:

    prompt = f"""
You are an expert at generating test cases for competitive programming problems.

Problem:
{problem.model_dump_json(indent=2)}

Generate {count} test cases{"including edge cases" if include_edge_cases else ""}.
Also generate a Python checker function.

Return ONLY valid JSON with this structure:
{{
  "testcases": [
    {{
      "input": "...",
      "expected_output": "...",
      "description": "...",
      "is_edge_case": false
    }}
  ],
  "checker_code": "# Python checker\\ndef check(input_data, expected, actual):\\n    return expected.strip() == actual.strip()"
}}
"""

    response = client.chat.completions.create(
        model=GROQ_MODEL,
        max_tokens=MAX_TOKENS,
        response_format={"type": "json_object"},
        messages=[{"role": "user", "content": prompt}]
    )

    data = json.loads(response.choices[0].message.content)
    testcases = [
        TestCaseSchema(id=f"tc_{str(uuid.uuid4())[:8]}", **tc)
        for tc in data.get("testcases", [])
    ]
    return TestCaseResponse(
        testcases=testcases,
        checker_code=data.get("checker_code", "")
    )
```

---

## 9. `ai_service/services/code_generator.py`

```python
from openai import OpenAI
from config import GROQ_API_KEY, GROQ_MODEL, GROQ_BASE_URL, MAX_TOKENS
from models.schemas import ProblemSchema, CodeGenResponse
import json

client = OpenAI(api_key=GROQ_API_KEY, base_url=GROQ_BASE_URL)

TYPE_INSTRUCTIONS = {
    "AC": "Write a CORRECT, optimal solution. It must pass all test cases.",
    "WA": "Write a solution with a subtle bug that gives WRONG ANSWER on some cases. The bug should not be obvious.",
    "TLE": "Write a solution that is CORRECT but has TIME LIMIT EXCEEDED due to poor complexity (e.g., O(n^2) when O(n) is needed)."
}

def generate_code(
    problem: ProblemSchema, code_type: str, language: str
) -> CodeGenResponse:

    instruction = TYPE_INSTRUCTIONS.get(code_type, TYPE_INSTRUCTIONS["AC"])

    prompt = f"""
{instruction}

Problem:
{problem.model_dump_json(indent=2)}

Language: {language}

Return ONLY valid JSON:
{{
  "code": "... full source code ...",
  "explanation": "... brief explanation of the approach and why it is {code_type} ..."
}}
"""
    response = client.chat.completions.create(
        model=GROQ_MODEL,
        max_tokens=MAX_TOKENS,
        response_format={"type": "json_object"},
        messages=[{"role": "user", "content": prompt}]
    )

    data = json.loads(response.choices[0].message.content)
    return CodeGenResponse(
        code=data.get("code", ""),
        language=language,
        type=code_type,
        explanation=data.get("explanation", "")
    )
```

---

## 10. `ai_service/routers/analyze.py`

```python
from fastapi import APIRouter, HTTPException
from models.schemas import AnalyzeRequest, AnalyzeResponse
from services.ocr_service import image_base64_to_text
from services.problem_analyzer import analyze_problem

router = APIRouter()

@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest):
    if not request.text and not request.image_base64:
        raise HTTPException(400, "Provide either 'text' or 'image_base64'")
    
    text = request.text
    if request.image_base64:
        text = image_base64_to_text(request.image_base64)
        if request.text:
            text = request.text + "\n" + text  # merge nếu có cả hai
    
    problem = analyze_problem(text)
    return AnalyzeResponse(problem=problem)
```

---

## 11. `ai_service/routers/testcase.py`

```python
from fastapi import APIRouter
from models.schemas import TestCaseRequest, TestCaseResponse
from services.testcase_generator import generate_testcases

router = APIRouter()

@router.post("/testcase", response_model=TestCaseResponse)
async def testcase(request: TestCaseRequest):
    return generate_testcases(
        request.problem, request.count, request.include_edge_cases
    )
```

---

## 12. `ai_service/routers/codegen.py`

```python
from fastapi import APIRouter
from models.schemas import CodeGenRequest, CodeGenResponse
from services.code_generator import generate_code

router = APIRouter()

@router.post("/codegen", response_model=CodeGenResponse)
async def codegen(request: CodeGenRequest):
    return generate_code(request.problem, request.type, request.language)
```

---

## 13. `ai_service/main.py`

```python
from fastapi import FastAPI
from routers import analyze, testcase, codegen

app = FastAPI(title="AIProblemSolver AI Service", version="1.0.0")

app.include_router(analyze.router)
app.include_router(testcase.router)
app.include_router(codegen.router)

@app.get("/health")
async def health():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
```

---

## 14. Chạy Python service

```bash
cd ai_service
pip install -r requirements.txt
cp .env.example .env
# Điền GROQ_API_KEY vào .env
python main.py
```

Kiểm tra: `http://localhost:8000/health` → `{"status":"ok"}`

---

## Checklist

- [ ] Tạo đủ cấu trúc thư mục `ai_service/`
- [ ] `requirements.txt` tạo xong
- [ ] `.env` với GROQ_API_KEY hợp lệ
- [ ] `schemas.py` với đầy đủ Pydantic models
- [ ] 3 routers tạo xong: `/analyze`, `/testcase`, `/codegen`
- [ ] 4 service files tạo xong
- [ ] `main.py` chạy được, `/health` trả 200 OK
- [ ] Test bằng curl hoặc Swagger UI (`http://localhost:8000/docs`)
