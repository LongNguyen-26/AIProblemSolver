# AIProblemSolver

Ứng dụng hỗ trợ phân tích đề thi lập trình thi đấu (IOI/ICPC), tự động sinh test case, checker, và đánh giá code mẫu.

## Tech Stack

| Layer | Công nghệ |
|---|---|
| Frontend / Execution Engine | Java 16 + JavaFX 17 (Maven) |
| AI Microservice | Python 3.10+ (FastAPI) |
| AI Provider | OpenAI API (GPT-4o / GPT-4o-mini) |
| OCR (ảnh → text) | Tesseract OCR (qua Python) |
| HTTP Bridge | Java `HttpClient` ↔ FastAPI REST |
| Code Sandbox | ProcessBuilder (Java) + Docker (tuỳ chọn) |

---

## Cấu trúc thư mục

```
AIProblemSolver/
│
├── pom.xml                          # Maven config (JavaFX, Gson, HTTP client)
│
├── src/main/
│   ├── java/org/example/
│   │   ├── Main.java                # Entry point → khởi động JavaFX
│   │   ├── app/
│   │   │   └── MainApp.java         # JavaFX Application class
│   │   ├── ui/
│   │   │   ├── controller/
│   │   │   │   ├── MainController.java
│   │   │   │   ├── ProblemInputController.java
│   │   │   │   ├── TestCaseController.java
│   │   │   │   └── ResultController.java
│   │   │   └── util/
│   │   │       └── AlertUtil.java
│   │   ├── model/
│   │   │   ├── Problem.java         # POJO: tiêu đề, mô tả, constraints
│   │   │   ├── TestCase.java        # input/output/verdict
│   │   │   ├── CodeSubmission.java  # code + ngôn ngữ + loại (AC/WA/TLE)
│   │   │   └── AnalysisReport.java  # kết quả tổng hợp
│   │   ├── service/
│   │   │   ├── AIBridgeService.java     # Gọi Python AI microservice qua HTTP
│   │   │   ├── TestCaseService.java     # Quản lý, lưu/load test cases
│   │   │   ├── ExecutionService.java    # Biên dịch + chạy code sandbox
│   │   │   └── ReportService.java       # Xuất báo cáo
│   │   └── util/
│   │       ├── HttpUtil.java        # Wrapper cho Java HttpClient
│   │       ├── FileUtil.java
│   │       └── AppConfig.java       # Đọc config.properties
│   │
│   └── resources/
│       ├── fxml/
│       │   ├── main.fxml
│       │   ├── problem_input.fxml
│       │   ├── testcase_view.fxml
│       │   └── result_view.fxml
│       ├── css/
│       │   └── style.css
│       └── config.properties        # API URL, API key (env override)
│
├── ai_service/                      # Python microservice (chạy song song)
│   ├── main.py                      # FastAPI app, port 8000
│   ├── routers/
│   │   ├── analyze.py               # POST /analyze  → phân tích đề
│   │   ├── testcase.py              # POST /testcase → sinh test case
│   │   └── codegen.py               # POST /codegen  → sinh code mẫu
│   ├── services/
│   │   ├── ocr_service.py           # Ảnh → text (Tesseract)
│   │   ├── problem_analyzer.py      # GPT: phân tích đề, constraints
│   │   ├── testcase_generator.py    # GPT: sinh test case + checker
│   │   └── code_generator.py        # GPT: sinh AC / WA / TLE code
│   ├── models/
│   │   └── schemas.py               # Pydantic request/response models
│   ├── config.py                    # Settings (dotenv)
│   ├── requirements.txt
│   └── .env.example
│
├── sandbox/                         # Thư mục tạm cho code execution
│   └── .gitkeep
│
├── docs/
│   ├── README.md                    # File này
│   ├── ARCHITECTURE.md              # Kiến trúc chi tiết + luồng xử lý
│   ├── SETUP.md                     # Hướng dẫn cài đặt
│   ├── USAGE.md                     # Hướng dẫn sử dụng
│   ├── TASK_01_PROJECT_SETUP.md     # Task: Cấu hình Maven + JavaFX
│   ├── TASK_02_PYTHON_AI_SERVICE.md # Task: Python FastAPI microservice
│   ├── TASK_03_JAVA_MODEL_SERVICE.md# Task: Java model + service layer
│   ├── TASK_04_JAVAFX_UI.md         # Task: Giao diện JavaFX
│   ├── TASK_05_EXECUTION_ENGINE.md  # Task: Sandbox biên dịch/chạy code
│   └── TASK_06_INTEGRATION.md       # Task: Tích hợp end-to-end + báo cáo
│
└── .gitignore
```

---

## Luồng chính (Happy Path)

```
User nhập đề (text hoặc ảnh)
        │
        ▼
[Java UI] → gửi HTTP POST /analyze → [Python AI Service]
        │         OCR nếu là ảnh, GPT phân tích constraints
        ◄─────────────────────────────────────────────────
        │  Nhận: Problem object (tên, mô tả, constraints, format)
        │
        ▼
[Java UI] → POST /testcase → [Python AI Service]
        │         GPT sinh N test cases + expected output + checker
        ◄─────────────────────────────────────────────────
        │  Nhận: List<TestCase>
        │
        ▼
[Java UI] → POST /codegen?type=AC|WA|TLE → [Python AI Service]
        │         GPT sinh code mẫu tương ứng
        ◄─────────────────────────────────────────────────
        │  Nhận: source code (C++/Python/Java)
        │
        ▼
[ExecutionService - Java] biên dịch + chạy code
        │  → so sánh output với expected (checker)
        │  → xác nhận test case đúng/sai/mạnh
        │
        ▼
[ReportService] xuất báo cáo đánh giá
```

---

## Thứ tự thực hiện Tasks

1. `TASK_01_PROJECT_SETUP.md` — Cấu hình pom.xml, module-info, khởi động JavaFX
2. `TASK_02_PYTHON_AI_SERVICE.md` — Toàn bộ Python AI microservice
3. `TASK_03_JAVA_MODEL_SERVICE.md` — Model POJO + Java service layer
4. `TASK_04_JAVAFX_UI.md` — FXML + Controllers + CSS
5. `TASK_05_EXECUTION_ENGINE.md` — Biên dịch & chạy code trong sandbox
6. `TASK_06_INTEGRATION.md` — Kết nối end-to-end, báo cáo, polish
