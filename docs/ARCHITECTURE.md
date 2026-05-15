# ARCHITECTURE.md — Kiến trúc hệ thống AIProblemSolver

## 1. Tổng quan kiến trúc

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER MACHINE                             │
│                                                                 │
│   ┌──────────────────────────┐    HTTP/JSON (localhost:8000)    │
│   │   Java / JavaFX App      │◄────────────────────────────────►│
│   │  (Execution Engine +     │                                  │
│   │   GUI)                   │   ┌──────────────────────────┐  │
│   │                          │   │  Python FastAPI Service  │  │
│   │  - Problem Input UI      │   │                          │  │
│   │  - TestCase Manager UI   │   │  - OCR (Tesseract)       │  │
│   │  - Result Viewer UI      │   │  - GPT Analysis          │  │
│   │  - ExecutionService      │   │  - TestCase Generation   │  │
│   │  - ReportService         │   │  - Code Generation       │  │
│   └──────────────────────────┘   └──────────┬───────────────┘  │
│            │                                │                   │
│            │ ProcessBuilder                 │ Groq API          │
│            ▼                                ▼                   │
│   ┌──────────────────┐          ┌───────────────────────┐      │
│   │  sandbox/        │          │  Groq GPT-OSS 120B    │      │
│   │  (temp compile   │          │  (External API)       │      │
│   │   & run)         │          └───────────────────────┘      │
│   └──────────────────┘                                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Python AI Microservice — API Endpoints

### `POST /analyze`
Phân tích đề bài → trả về Problem object

**Request:**
```json
{
  "text": "...",          // text đề bài (nullable nếu có image)
  "image_base64": "..."   // base64 ảnh (nullable nếu có text)
}
```
**Response:**
```json
{
  "title": "Two Sum",
  "description": "...",
  "input_format": "...",
  "output_format": "...",
  "constraints": ["1 ≤ n ≤ 10^5", "..."],
  "sample_inputs": ["..."],
  "sample_outputs": ["..."]
}
```

---

### `POST /testcase`
Sinh test cases từ Problem object

**Request:**
```json
{
  "problem": { /* Problem object từ /analyze */ },
  "count": 10,
  "include_edge_cases": true
}
```
**Response:**
```json
{
  "testcases": [
    {
      "id": "tc_001",
      "input": "5\n1 2 3 4 5",
      "expected_output": "15",
      "description": "basic case",
      "is_edge_case": false
    }
  ],
  "checker_code": "# Python checker\n..."
}
```

---

### `POST /codegen`
Sinh code mẫu (AC / WA / TLE)

**Request:**
```json
{
  "problem": { /* Problem object */ },
  "type": "AC",       // "AC" | "WA" | "TLE"
  "language": "cpp"   // "cpp" | "python" | "java"
}
```
**Response:**
```json
{
  "code": "#include<bits/stdc++.h>...",
  "language": "cpp",
  "type": "AC",
  "explanation": "Sử dụng prefix sum, O(n)"
}
```

---

## 3. Java Service Layer

### `AIBridgeService`
- Gọi HTTP POST tới Python microservice
- Serialize/deserialize JSON (Gson)
- Retry logic (3 lần) + timeout 30s

### `ExecutionService`
- Nhận source code + language
- Ghi file tạm vào `sandbox/`
- Compile: `javac`, `g++`, `python3` (tuỳ ngôn ngữ)
- Run với timeout (ProcessBuilder + Future)
- So sánh output (exact match hoặc chạy checker)
- Trả về `ExecutionResult` (verdict, stdout, stderr, time_ms)

### `TestCaseService`
- Lưu/load test cases vào JSON file (`testcases/{problem_id}.json`)
- CRUD operations
- Export test cases ra file ZIP

### `ReportService`
- Tổng hợp kết quả chạy
- Xuất báo cáo HTML/PDF

---

## 4. JavaFX UI — Màn hình chính

```
┌─────────────────────────────────────────────────────────┐
│  AIProblemSolver                              [_ □ ×]   │
├────────────┬────────────────────────────────────────────┤
│  [1] Input │  TabPane                                   │
│  Problem   │  ┌──────────┬──────────┬────────────────┐  │
│            │  │ Analysis │TestCases │ Code & Results │  │
│  ○ Text    │  └──────────┴──────────┴────────────────┘  │
│  ○ Image   │                                            │
│            │  [Analysis Tab]                            │
│  [TextArea │  - Hiển thị Problem parsed                 │
│   / Image  │  - Constraints list                        │
│   Preview] │  - Sample I/O                              │
│            │                                            │
│  [Analyze] │  [TestCases Tab]                           │
│            │  - TableView: ID | Input | Expected | Type │
│  [Python   │  - [Generate] [Add Manual] [Export ZIP]    │
│   Service  │                                            │
│   Status:  │  [Code & Results Tab]                      │
│   ● Online]│  - CodeEditor (TextArea + syntax hint)     │
│            │  - Language selector + Type (AC/WA/TLE)    │
│            │  - [Generate Code] [Run All Tests]         │
│            │  - ResultTable: TC | Verdict | Time | Diff │
│            │  - [Export Report]                         │
└────────────┴────────────────────────────────────────────┘
```

---

## 5. Quyết định kỹ thuật quan trọng

| Vấn đề | Quyết định | Lý do |
|---|---|---|
| Giao tiếp Java ↔ Python | REST HTTP (localhost) | Đơn giản, không cần JNI, dễ debug |
| Sandbox execution | ProcessBuilder + timeout | Không cần Docker cho demo, dễ cài đặt |
| AI Provider | Groq `openai/gpt-oss-120b` | Production model, context lớn, tốt cho JSON/code generation |
| OCR | Tesseract (pytesseract) | Free, chạy local, không cần API |
| JSON | Gson (Java) + Pydantic (Python) | Consistent serialization |
| Config | config.properties + .env | Tách biệt Java config và Python config |
| JavaFX version | 17 LTS | Ổn định, hỗ trợ Java 16+ |

---

## 6. Dependency Graph (Maven)

```
pom.xml dependencies:
├── org.openjfx:javafx-controls:17
├── org.openjfx:javafx-fxml:17
├── com.google.code.gson:gson:2.10.1
└── (remove langchain4j — dùng direct HTTP call thay thế)

Python requirements.txt:
├── fastapi
├── uvicorn
├── openai>=1.0.0
├── pytesseract
├── Pillow
├── python-dotenv
└── pydantic
```
