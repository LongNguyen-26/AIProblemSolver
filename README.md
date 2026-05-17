# AIProblemSolver

AIProblemSolver là ứng dụng desktop hỗ trợ phân tích đề bài lập trình thi đấu IOI/ICPC, sinh test case, sinh code mẫu và chạy đánh giá kết quả ngay trên máy local.

Ứng dụng gồm hai phần chạy song song:

- JavaFX desktop app: giao diện, quản lý test case, compile/run code trong sandbox, xuất ZIP/báo cáo.
- Python FastAPI AI service: gọi Groq API để phân tích đề, sinh input test case, sinh code AC/WA/TLE.

README này là tài liệu tổng hợp cho người dùng và cho coding agent ở các session sau. Nội dung phản ánh trạng thái hiện tại sau các task 01-06 và các chỉnh sửa trong ngày hôm nay.

## Tính Năng Chính

- Nhập đề bài bằng text hoặc ảnh.
- OCR ảnh đề bài qua Tesseract.
- Phân tích đề thành `Problem`: title, description, input format, output format, constraints, sample I/O.
- Sinh test case tự động theo mô hình hình chóp:
  - `SMALL`: sample/small tests để kiểm tra logic cơ bản và dùng brute/stress validation.
  - `MEDIUM`: test vừa, bắt lỗi logic và edge cases.
  - `KILLER`: test lớn/adversarial, dùng để bắt TLE.
- Sinh expected output bằng code oracle chạy local, không bắt AI tự bịa cả input/output.
- Với bài có nhiều output hợp lệ/special judge style, dùng canonical AC oracle để tạo expected output nhất quán.
- Sinh code mẫu:
  - `AC`: code đúng.
  - `WA`: code cố ý có lỗi tinh vi.
  - `TLE`: code đúng về logic nhưng chậm; nếu chạy xong thì phải AC, không được WA.
- Chạy code C++/Python/Java bằng `ExecutionService` trong thư mục `sandbox/`.
- Verdict: `AC`, `WA`, `TLE`, `CE`, `RE`.
- Quản lý test case thủ công: thêm, xóa, sửa input/expected output.
- Export test cases thành ZIP.
- Export báo cáo HTML.
- UI dark theme, popup custom, bảng diff tối ưu hơn, chống double-click/rate-limit cho các nút chạy AI/run code.

## Tech Stack

| Thành phần | Công nghệ |
|---|---|
| Desktop UI | Java + JavaFX 17 |
| Build Java | Maven |
| HTTP client | Java `HttpClient` HTTP/1.1 + Gson |
| AI service | Python 3.10+ + FastAPI + Uvicorn |
| AI provider | Groq OpenAI-compatible API |
| Default model | `openai/gpt-oss-120b` |
| OCR | Tesseract + pytesseract + Pillow |
| Local execution | Java `ProcessBuilder` |
| Config Java | `src/main/resources/config.properties` |
| Config Python | `ai_service/.env` |

## Cấu Trúc Thư Mục

```text
AIProblemSolver/
|-- pom.xml
|-- README.md
|-- ai_service/
|   |-- main.py
|   |-- config.py
|   |-- requirements.txt
|   |-- .env                  # local only, chứa GROQ_API_KEY
|   |-- models/
|   |   `-- schemas.py
|   |-- routers/
|   |   |-- analyze.py
|   |   |-- testcase.py
|   |   `-- codegen.py
|   `-- services/
|       |-- groq_json.py
|       |-- problem_analyzer.py
|       |-- testcase_generator.py
|       |-- code_generator.py
|       `-- ocr_service.py
|-- docs/
|   |-- README.md
|   |-- ARCHITECTURE.md
|   |-- SETUP_AND_USAGE.md
|   |-- TASK_01_PROJECT_SETUP.md
|   |-- TASK_02_PYTHON_AI_SERVICE.md
|   |-- TASK_03_JAVA_MODEL_SERVICE.md
|   |-- TASK_04_JAVAFX_UI.md
|   |-- TASK_05_EXECUTION_ENGINE.md
|   `-- TASK_06_INTEGRATION.md
|-- sandbox/                  # file tạm khi compile/run code
`-- src/main/
    |-- java/org/example/
    |   |-- Main.java
    |   |-- app/MainApp.java
    |   |-- model/
    |   |   |-- Problem.java
    |   |   |-- TestCase.java
    |   |   |-- CodeSubmission.java
    |   |   `-- AnalysisReport.java
    |   |-- service/
    |   |   |-- AIBridgeService.java
    |   |   |-- ExecutionService.java
    |   |   |-- ReportService.java
    |   |   `-- TestCaseService.java
    |   |-- ui/controller/
    |   |   |-- MainController.java
    |   |   |-- ProblemInputController.java
    |   |   |-- TestCaseController.java
    |   |   `-- ResultController.java
    |   |-- ui/util/AlertUtil.java
    |   `-- util/
    |       |-- AppConfig.java
    |       |-- FileUtil.java
    |       `-- HttpUtil.java
    `-- resources/
        |-- config.properties
        |-- css/style.css
        `-- fxml/
            |-- main.fxml
            |-- problem_input.fxml
            |-- testcase_view.fxml
            `-- result_view.fxml
```

## Cài Đặt

### Yêu Cầu

- Java JDK 17 hoặc JDK tương thích JavaFX 17.
- Maven 3.8+.
- Python 3.10+.
- `g++` nếu muốn chạy C++.
- `javac` nếu muốn chạy Java submissions.
- Tesseract OCR nếu dùng input ảnh.
- Groq API key.

### Python AI Service

Tạo file `ai_service/.env`:

```env
GROQ_API_KEY=gsk_...
GROQ_MODEL=openai/gpt-oss-120b
GROQ_BASE_URL=https://api.groq.com/openai/v1
MAX_TOKENS=4096
```

Cài dependencies và chạy service:

```powershell
cd C:\Code_Storage\AIProblemSolver\ai_service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python main.py
```

Service mặc định chạy ở:

```text
http://localhost:8000
```

Kiểm tra:

```text
http://localhost:8000/health
```

Kết quả đúng:

```json
{"status":"ok"}
```

### JavaFX App

Ở terminal khác:

```powershell
cd C:\Code_Storage\AIProblemSolver
mvn javafx:run
```

Nếu chỉ muốn compile:

```powershell
mvn -q compile
```

Java app đọc Python service URL từ:

```text
src/main/resources/config.properties
```

Giá trị hiện tại:

```properties
ai.service.baseUrl=http://localhost:8000
execution.timeoutSeconds=5
execution.sandboxPath=sandbox
```

## Cách Sử Dụng App

1. Chạy `ai_service` trước bằng `python main.py`.
2. Chạy JavaFX app bằng `mvn javafx:run`.
3. Kiểm tra góc phải trên của app có trạng thái `Online`.
4. Chọn chế độ nhập đề:
   - `Text`: paste đề bài vào ô bên trái.
   - `Ảnh`: chọn ảnh PNG/JPG để OCR.
5. Chọn số test case, mặc định là 10.
6. Tick `Bao gom edge cases` nếu muốn sinh thêm trường hợp biên.
7. Bấm `Phan tich de`.
8. Xem kết quả phân tích ở tab `Phan tich`.
9. Xem và chỉnh test case ở tab `Test Cases`.
10. Qua tab `Code va Ket qua`, chọn loại code `AC`, `WA`, hoặc `TLE`.
11. Chọn ngôn ngữ `cpp`, `python`, hoặc `java`.
12. Bấm `Sinh code`.
13. Bấm `Chay tat ca test`.
14. Xem verdict và diff.
15. Dùng `Export ZIP` để xuất input/output hoặc `Xuat bao cao` để lưu báo cáo HTML.

## Luồng Xử Lý Chính

```text
User nhập đề
  |
  v
JavaFX MainController
  |
  | POST /analyze
  v
Python AI service phân tích đề
  |
  v
Java nhận Problem object
  |
  | POST /testcase theo profile SMALL/MEDIUM/KILLER
  v
AI chỉ sinh INPUT
  |
  v
Java sinh/chạy oracle code local để tạo Expected Output
  |
  v
Java hiển thị test cases đã có expected output
  |
  | POST /codegen
  v
AI sinh AC/WA/TLE code
  |
  v
ExecutionService compile/run code trong sandbox
  |
  v
UI hiển thị AC/WA/TLE/CE/RE và diff
```

## Luồng Sinh Test Case Hiện Tại

Mục tiêu hiện tại không còn là "AI sinh input và output". AI chỉ sinh input; expected output được tính bằng code oracle chạy local.

Các bước chính:

1. Phân tích đề thành `Problem`.
2. Lập kế hoạch hình chóp từ số lượng test case người dùng nhập:
   - Khoảng 20% `SMALL`.
   - Khoảng 40% `MEDIUM`.
   - Khoảng 40% `KILLER`.
3. Sinh `SMALL` cases để validation.
4. Sinh nhiều oracle độc lập:
   - `BRUTE`
   - `BRUTE_ALT`
   - `ORACLE`
   - `AC`
   - `OPTIMIZED_ALT`
   - `ORACLE_ALT`
5. Với bài output duy nhất:
   - Dùng majority/cross-check trên small validation cases.
   - Nếu optimized oracle pass stress với expected small, dùng optimized oracle để tính expected output cho `MEDIUM` và `KILLER`.
   - Nếu optimized oracle fail, fallback về small cases đã có expected output thay vì dừng app.
6. Với bài có nhiều output hợp lệ/special judge style:
   - Không dùng exact-match giữa nhiều oracle, vì nhiều lời giải đúng có thể in nghiệm khác nhau.
   - Dùng một canonical AC oracle để tạo expected output nhất quán.
   - Nút `Sinh code` loại `AC` sẽ ưu tiên dùng lại cached AC oracle đó để demo không tự WA vì khác nghiệm hợp lệ.
7. Các testcase được tag description:
   - `[SMALL] ...`
   - `[MEDIUM] ...`
   - `[KILLER] ...`

## Luồng Sinh Code TLE

Yêu cầu hiện tại: code TLE nếu không bị TLE thì phải là AC, không được WA.

Luồng xử lý:

1. Khi chọn `TLE` và bấm `Sinh code`, Java gửi thêm một số `validation_cases` có input/expected output sang `/codegen`.
2. Prompt backend yêu cầu:
   - Code phải đúng logic.
   - Nếu chạy xong thì output phải accepted.
   - Không được dùng sleep, infinite loop, random, deliberate WA, CE, RE.
   - Với bài constructive/multiple-output, phải dùng slow search hoặc canonical construction, không đoán heuristic.
3. Sau khi AI sinh code TLE, Java chạy thử candidate trên testcase hiện có.
4. Candidate chỉ được nhận nếu mọi testcase validate có verdict `AC` hoặc `TLE`.
5. Nếu gặp `WA`, `RE`, `CE`, candidate bị loại và app retry tối đa 3 lần.
6. Nếu sau 3 lần vẫn không có code hợp lệ, app báo lỗi rõ lý do gần nhất.

## API Spec

### `GET /health`

Response:

```json
{"status":"ok"}
```

### `POST /analyze`

Request:

```json
{
  "text": "problem statement text",
  "image_base64": null
}
```

Response:

```json
{
  "problem": {
    "title": "...",
    "description": "...",
    "input_format": "...",
    "output_format": "...",
    "constraints": ["..."],
    "sample_inputs": ["..."],
    "sample_outputs": ["..."]
  }
}
```

### `POST /testcase`

Request:

```json
{
  "problem": { "...": "Problem object" },
  "count": 10,
  "include_edge_cases": true,
  "profile": "SMALL"
}
```

`profile` hợp lệ:

- `SMALL`
- `MEDIUM`
- `KILLER`
- `STRONG` và `LARGE` được map về `KILLER`.

Response:

```json
{
  "testcases": [
    {
      "id": "tc_abcd1234",
      "input": "full stdin",
      "expected_output": "",
      "description": "case description",
      "is_edge_case": false
    }
  ],
  "checker_code": "# Python checker\n..."
}
```

Lưu ý: backend `/testcase` cố ý để `expected_output` rỗng. Java sẽ tự chạy oracle để điền expected output.

### `POST /codegen`

Request:

```json
{
  "problem": { "...": "Problem object" },
  "type": "TLE",
  "language": "cpp",
  "validation_cases": [
    {
      "id": "tc_...",
      "input": "full stdin",
      "expected_output": "expected stdout",
      "description": "[SMALL] sample",
      "is_edge_case": false
    }
  ]
}
```

`type` hợp lệ:

- `AC`
- `WA`
- `TLE`
- Internal/reference types currently used by Java:
  - `BRUTE`
  - `BRUTE_ALT`
  - `ORACLE`
  - `ORACLE_ALT`
  - `OPTIMIZED_ALT`

Response:

```json
{
  "code": "full source code",
  "language": "cpp",
  "type": "TLE",
  "explanation": "approach explanation"
}
```

## Java Components

### `MainController`

Điều phối luồng chính:

- đọc input text/image;
- gọi `/analyze`;
- gọi `/testcase`;
- sinh/chạy oracle để tạo expected output;
- tạo test pyramid;
- nhận diện special judge/multiple-output problem;
- truyền `Problem`, `TestCase`, cached AC oracle sang các tab con.

### `ResultController`

Quản lý tab code/result:

- sinh code AC/WA/TLE;
- cache AC oracle khi có;
- validate TLE candidate trước khi hiển thị;
- chạy tất cả testcase;
- cập nhật bảng verdict/diff;
- export report.

### `ExecutionService`

Compile/run local:

- C++: ghi `sandbox/Main.cpp`, compile bằng `g++`, chạy `sandbox/prog.exe` hoặc `sandbox/prog`.
- Java: ghi `sandbox/Main.java`, compile bằng `javac`, chạy `java -cp sandbox Main`.
- Python: ghi `sandbox/Main.py`, chạy bằng `python3`, `python`, hoặc `py -3`.
- Timeout mặc định: 5 giây.
- Có batch runner `runTestCases` để compile một lần và chạy nhiều input.

### `AIBridgeService`

HTTP bridge từ Java sang FastAPI:

- `/health`
- `/analyze`
- `/testcase`
- `/codegen`

Sử dụng HTTP/1.1 để tránh lỗi body khi Java `HttpClient` nói chuyện với Uvicorn.

## Python Components

### `groq_json.py`

Wrapper gọi Groq:

- JSON mode trước.
- Nếu JSON mode fail/invalid JSON, fallback sang normal text completion rồi parse JSON.
- Có `request_json` và `request_text`.

### `problem_analyzer.py`

Prompt phân tích đề thành structured `ProblemSchema`.

### `testcase_generator.py`

Prompt sinh input-only test cases:

- không sinh expected output;
- mỗi testcase là một stdin đầy đủ;
- nếu format có `t/T`, phải nhất quán số lượng scenario;
- profile `SMALL`, `MEDIUM`, `KILLER`;
- giới hạn input khoảng `MAX_TESTCASE_INPUT_CHARS = 200000`.

### `code_generator.py`

Prompt sinh code:

- `AC`: đúng và tối ưu.
- `WA`: sai tinh vi.
- `TLE`: đúng logic nhưng chậm; nếu chạy xong phải AC.
- reference/internal oracle types để Java dùng trong stress testing.
- nhận thêm `validation_cases` để bám expected output style.

## Những Gì Đã Làm Hôm Nay

- Đổi provider từ `OPENAI_API_KEY` sang `GROQ_API_KEY`.
- Chọn model mặc định `openai/gpt-oss-120b`.
- Thêm wrapper Groq JSON fallback để giảm lỗi JSON invalid.
- Sửa Java HTTP client dùng HTTP/1.1.
- Hoàn thiện các task 01-06:
  - project setup;
  - Python AI service;
  - Java model/service;
  - JavaFX UI;
  - execution engine;
  - tích hợp end-to-end và report.
- Chạy thử end-to-end với `.env` có `GROQ_API_KEY`.
- Viết hướng dẫn sử dụng app.
- Sửa lỗi Groq JSON mode làm `/analyze` và `/testcase` trả HTTP 500.
- Cải thiện UI dark theme:
  - text trong khung không còn chìm;
  - constraints list ăn khớp màu;
  - popup thông báo custom;
  - diff column tối ưu hơn.
- Thêm lock cho các nút:
  - `Phan tich de`;
  - `Sinh code`;
  - `Chay tat ca test`.
- Đổi logic sinh testcase:
  - AI chỉ sinh input;
  - Java sinh/chạy AC oracle để tạo expected output.
- Thêm stress testing với brute/optimized oracle.
- Thêm mô hình test pyramid `SMALL/MEDIUM/KILLER`.
- Sửa lỗi fallback chỉ tạo được vài testcase khi optimized oracle fail.
- Sửa lỗi expected output trống.
- Sửa lỗi bài special judge/multiple-output bị WA do exact-match nghiệm khác nhau.
- Thêm canonical AC oracle cho bài có nhiều output hợp lệ.
- Cache AC oracle để nút `Sinh code` dùng lại đúng code tạo expected output.
- Nâng cấp sinh code TLE:
  - code TLE không được WA;
  - nếu chạy xong phải AC;
  - Java tự validate candidate TLE và retry nếu sai.

## Lưu Ý Cho Coding Agent Sau

- Không để AI sinh expected output trực tiếp trong `/testcase`. Expected output phải do Java chạy oracle tạo ra.
- Với bài có nhiều output hợp lệ, không dùng exact output giữa hai lời giải AC khác nhau để kết luận WA. Cần checker thật hoặc canonical oracle.
- Hiện tại app vẫn so sánh exact output khi chạy user code. Vì chưa có special judge checker đầy đủ, giải pháp tạm là dùng cached canonical AC cho demo AC.
- `TLE` code phải đúng logic. Nếu candidate hoàn thành mà `WA`, reject.
- Khi sửa Java file, chạy:

```powershell
mvn -q compile
```

- Khi sửa Python service, chạy:

```powershell
python -m compileall ai_service
```

- Nếu sửa API schema giữa Java/Python, cần restart cả `ai_service` và JavaFX app.
- `sandbox/` là thư mục sinh file tạm khi compile/run. Không coi file trong đó là source chính của app.
- Các docs trong `docs/` có thể bị lỗi encoding khi mở bằng một số terminal; README gốc này nên được xem là tài liệu cập nhật hơn.

## Lỗi Thường Gặp

### Java app báo Offline

Chưa chạy Python service hoặc URL sai.

```powershell
cd C:\Code_Storage\AIProblemSolver\ai_service
.\.venv\Scripts\Activate.ps1
python main.py
```

Kiểm tra `src/main/resources/config.properties`:

```properties
ai.service.baseUrl=http://localhost:8000
```

### Python báo thiếu GROQ_API_KEY

Kiểm tra `ai_service/.env`:

```env
GROQ_API_KEY=gsk_...
```

Không thêm dấu quote lạ hoặc ký tự thừa ở đầu file.

### Code C++ bị CE do không thấy g++

Cài MinGW/MSYS2 hoặc compiler tương ứng, rồi thêm `g++` vào PATH.

### OCR không đọc được ảnh

Kiểm tra Tesseract đã cài và có trong PATH. Nếu ảnh mờ hoặc OCR sai, dùng chế độ Text để paste đề bài.

### TLE generation mất lâu

Đây là hành vi mong muốn: app đang sinh code TLE, chạy validation và reject các candidate bị WA/RE/CE. Có thể mất nhiều hơn sinh AC/WA.

## Commands Hữu Ích

```powershell
# Kiểm tra service
Invoke-RestMethod http://localhost:8000/health

# Chạy Python service
cd C:\Code_Storage\AIProblemSolver\ai_service
.\.venv\Scripts\Activate.ps1
python main.py

# Chạy Java app
cd C:\Code_Storage\AIProblemSolver
mvn javafx:run

# Compile Java
mvn -q compile

# Compile-check Python
python -m compileall ai_service

# Xem file thay đổi
git status --short
```

## Trạng Thái Hiện Tại

App đã có luồng end-to-end:

```text
Input problem -> analyze -> generate input tests -> compute expected output by oracle
-> generate AC/WA/TLE code -> run all tests -> export ZIP/report
```

Các điểm còn có thể nâng cấp về sau:

- Viết checker/special judge thật thay cho exact-match.
- Lưu metadata testcase rõ hơn thay vì chỉ tag trong description.
- Tách stress testing thành service riêng để dễ test unit/integration.
- Thêm progress/cancel cho các tác vụ AI và TLE validation dài.
- Thêm cache problem/testcase/code theo hash đề bài.
- Thêm test tự động cho `ExecutionService`, `AIBridgeService`, và parser JSON fallback.
