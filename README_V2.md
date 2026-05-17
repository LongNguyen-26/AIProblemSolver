# AIProblemSolver README V2

Tai lieu nay tong hop trang thai hien tai cua app sau chuoi nang cap den Task 11. Muc tieu la de nguoi dung, coding agent session khac, hoac AI phan tich co the nam nhanh ung dung dang co gi, da thay doi gi, va can luu y gi khi tiep tuc phat trien.

## 1. Tong Quan

AIProblemSolver la ung dung desktop ho tro phan tich de bai lap trinh thi dau IOI/ICPC, sinh test case, sinh code mau AC/WA/TLE, chay code local va xuat bao cao.

Ung dung co 2 phan chinh:

- JavaFX desktop app: UI, quan ly testcase, compile/run code local trong `sandbox/`, hien thi verdict/diff, export ZIP/report.
- Python FastAPI AI service: phan tich de bai, OCR, sinh testcase input-only, sinh code, classify problem type, stress/orchestration pipeline.

Trang thai hien tai:

- Da hoan thanh cac task goc 01-06.
- Da implement Upgrade Roadmap Task 07-11.
- Luong expected output van duoc tinh local bang oracle code, khong de AI tu bia expected output.
- Pipeline moi co SSE progress va cache, nhung Java van giu buoc tinh expected output bang oracle local de dam bao chat luong.

## 2. Tech Stack

| Thanh phan | Cong nghe |
|---|---|
| Desktop UI | Java + JavaFX 17 |
| Build Java | Maven |
| Java JSON | Gson |
| Java HTTP | `java.net.http.HttpClient` HTTP/1.1 |
| AI service | Python 3.10+ + FastAPI + Uvicorn |
| AI provider | Groq OpenAI-compatible API |
| Default model | `openai/gpt-oss-120b` |
| OCR | Tesseract + pytesseract + Pillow |
| Local execution | Java `ProcessBuilder` |
| Cache | JSON files under `cache/problems/` |

## 3. Cach Chay

### Python AI Service

Tao `ai_service/.env`:

```env
GROQ_API_KEY=gsk_...
GROQ_MODEL=openai/gpt-oss-120b
GROQ_BASE_URL=https://api.groq.com/openai/v1
MAX_TOKENS=4096
```

Chay service:

```powershell
cd C:\Code_Storage\AIProblemSolver\ai_service
.\.venv\Scripts\Activate.ps1
python main.py
```

Kiem tra:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

Neu terminal hien warning `Python-dotenv could not parse statement...`, kiem tra lai `ai_service/.env` xem co dong bi sai format khong.

### JavaFX App

Neu Maven co trong PATH:

```powershell
cd C:\Code_Storage\AIProblemSolver
mvn javafx:run
```

Trong may hien tai, `mvn` khong co trong PATH. Co the dung Maven bundled cua IntelliJ:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Neu can run app tu IntelliJ, mo project `AIProblemSolver` va chay Maven goal `javafx:run` hoac run class `org.example.Main`.

## 4. Cau Truc Thu Muc Chinh

```text
AIProblemSolver/
|-- README.md
|-- README_V2.md
|-- pom.xml
|-- docs/
|   |-- UPGRADE_ROADMAP.md
|   |-- TASK_07_TESTCASE_COUNT_FIX.md
|   |-- TASK_08_STRESS_TESTING_AGENT.md
|   |-- TASK_09_TLE_GENERATION_FIX.md
|   |-- TASK_10_PROBLEM_TYPE_COVERAGE.md
|   `-- TASK_11_AGENT_ORCHESTRATION.md
|-- ai_service/
|   |-- main.py
|   |-- models/schemas.py
|   |-- routers/
|   |   |-- analyze.py
|   |   |-- testcase.py
|   |   |-- codegen.py
|   |   |-- stress.py
|   |   `-- pipeline.py
|   `-- services/
|       |-- problem_analyzer.py
|       |-- problem_classifier.py
|       |-- problem_taxonomy.py
|       |-- complexity_analyzer.py
|       |-- testcase_generator.py
|       |-- code_generator.py
|       |-- stress_testing_agent.py
|       |-- pipeline_orchestrator.py
|       |-- problem_cache.py
|       |-- groq_json.py
|       `-- ocr_service.py
|-- cache/problems/
|-- sandbox/
`-- src/main/
    |-- java/org/example/
    |   |-- model/
    |   |   |-- Problem.java
    |   |   |-- TestCase.java
    |   |   |-- CodeSubmission.java
    |   |   |-- ComplexityInfo.java
    |   |   |-- StressResult.java
    |   |   `-- PipelineProgress.java
    |   |-- service/
    |   |   |-- AIBridgeService.java
    |   |   |-- ExecutionService.java
    |   |   |-- ReportService.java
    |   |   `-- TestCaseService.java
    |   `-- ui/controller/
    |       |-- MainController.java
    |       |-- ProblemInputController.java
    |       |-- TestCaseController.java
    |       `-- ResultController.java
    `-- resources/
        |-- config.properties
        |-- css/style.css
        `-- fxml/
```

## 5. Luong Xu Ly Hien Tai

### Text Mode

Text mode da duoc noi voi pipeline moi:

```text
User paste problem text
  -> Java goi POST /pipeline/run bang SSE
  -> Python pipeline analyze + classify + generate SMALL/MEDIUM/KILLER input cases
  -> Java cap nhat progress bar theo SSE
  -> Java nhan Problem + input-only testcases tu pipeline
  -> Java sinh/chay oracle local de tinh expected output
  -> Java stress agent endpoint /stress de tim trusted optimized oracle
  -> Java dung trusted oracle tinh expected output cho MEDIUM/KILLER
  -> UI hien Problem, Test Cases, cached AC oracle
```

### Image Mode

Image mode van dung luong legacy:

```text
Image -> OCR/analyze -> generate input cases theo profile -> Java oracle expected output
```

Neu muon full pipeline cho image mode, can them buoc OCR truoc roi goi `/pipeline/run` bang text OCR.

## 6. API Hien Tai

### `GET /health`

Response:

```json
{"status":"ok"}
```

### `POST /analyze`

Phan tich de bai thanh `ProblemSchema`, dong thoi classify problem type.

Request:

```json
{
  "text": "problem statement",
  "image_base64": null
}
```

Response chinh:

```json
{
  "problem": {
    "title": "...",
    "description": "...",
    "input_format": "...",
    "output_format": "...",
    "constraints": ["..."],
    "sample_inputs": ["..."],
    "sample_outputs": ["..."],
    "problem_type": "TREE",
    "secondary_type": "DP_1D",
    "type_confidence": 0.9,
    "tle_strategy": "bamboo tree of n=max ..."
  }
}
```

### `POST /analyze/complexity`

Phan tich complexity de sinh TLE dung chien luoc.

Response:

```json
{
  "optimal_complexity": "O(n log n)",
  "tle_target_complexity": "O(n^2)",
  "max_n": 100000,
  "tle_strategy": "quadratic_nested_loop",
  "tle_explanation": "..."
}
```

### `POST /testcase`

Sinh input-only testcases. Backend co top-up loop va duplicate filter.

Request fields:

- `problem`
- `count`
- `requested_count`
- `include_edge_cases`
- `profile`: `SMALL`, `MEDIUM`, `KILLER`
- `existing_inputs`: danh sach input can tranh lap

Response:

```json
{
  "testcases": [
    {
      "id": "tc_...",
      "input": "...",
      "expected_output": "",
      "description": "[SMALL] ...",
      "is_edge_case": true
    }
  ],
  "checker_code": "..."
}
```

Quan trong: `/testcase` khong sinh expected output. Java se tinh expected output bang oracle local.

### `POST /codegen`

Sinh code AC/WA/TLE/reference oracle.

Request fields moi:

- `validation_cases`
- `error_log`
- `complexity_info`

Voi `type = "TLE"`, prompt ep code:

- dung logic;
- cham do thuat toan phu thuoc input;
- cam `sleep`, dummy loop, infinite loop, random;
- neu chay xong thi output phai AC.

### `POST /stress`

Chay `StressTestingAgent` o Python:

- generate/fix brute oracle;
- generate/fix optimized oracle;
- generate/fix input generator;
- stress compare brute vs optimized;
- tra trusted oracle neu pass.

Response:

```json
{
  "trusted": true,
  "trusted_oracle_code": "...",
  "trusted_oracle_language": "python",
  "found_counterexample": false,
  "counterexample_input": "",
  "rounds_completed": 30,
  "oracle_retries": 1,
  "generator_trusted": true,
  "message": "...",
  "problem_type": "graph"
}
```

### `POST /pipeline/run`

SSE endpoint cho multi-step pipeline.

Request:

```json
{
  "problem_text": "...",
  "count": 10,
  "include_edge_cases": true,
  "language_preference": "cpp"
}
```

Response stream:

```text
data: {"state":"CLASSIFYING","message":"...","progress_pct":5,...}

data: {"state":"GENERATING_SMALL","message":"...","progress_pct":18,...}

data: {"state":"DONE","message":"Pipeline hoan thanh.","progress_pct":100,...}
```

`DONE` event co:

- `problem`
- `all_testcases`
- `warnings`
- `cached`

## 7. Nhung Nang Cap Da Lam

### Task 07 - Fix Testcase Count

Da them:

- Python top-up loop toi da 3 attempt.
- Prompt bat buoc return exactly `count`.
- `existing_inputs` de tranh duplicate.
- Java `AIBridgeService.generateTestCases` retry/top-up.
- `MainController.generateProfileCases` truyen input da co.

Ket qua: khi AI tra thieu case, backend/Java se goi bo sung va merge unique.

### Task 08 - Stress Testing Agent

Da them:

- `ai_service/services/stress_testing_agent.py`
- `POST /stress`
- `fix_code`
- `generate_input_generator`
- Java `StressResult`
- Java goi stress agent de lay trusted optimized oracle

Luu y: Java van tinh expected output bang `ExecutionService`. Python stress agent chay oracle Python rieng de verify y tuong.

### Task 09 - TLE Generation Fix

Da them:

- `complexity_analyzer.py`
- `POST /analyze/complexity`
- `ComplexityInfo` Python/Java
- `/codegen` nhan `complexity_info` va `error_log`
- TLE prompt cam dummy loop/sleep/infinite loop
- Java validate TLE chi tren SMALL toi da 3 case
- Retry TLE co feedback chi tiet neu WA/RE/CE

Ket qua: TLE generation nhanh hon va it bi treo do khong validate tren KILLER.

### Task 10 - Problem Type Coverage

Da them:

- `problem_taxonomy.py` voi 16 problem types:
  - `ARRAY_SEQUENCE`
  - `GRAPH_UNDIRECTED`
  - `GRAPH_DIRECTED`
  - `TREE`
  - `DP_1D`
  - `DP_2D`
  - `DP_BITMASK`
  - `MATH_NUMBER_THEORY`
  - `MATH_COMBINATORICS`
  - `STRING`
  - `GEOMETRY`
  - `GREEDY`
  - `BINARY_SEARCH`
  - `DATA_STRUCTURE`
  - `CONSTRUCTIVE`
  - `GAME_THEORY`
- `problem_classifier.py`
- `/analyze` tu gan `problem_type`, `secondary_type`, `type_confidence`, `tle_strategy`
- Testcase prompt co type-specific edge/killer strategy.
- UI tab Phan tich hien problem type va TLE strategy.

### Task 11 - Pipeline Orchestration

Da them:

- `pipeline_orchestrator.py`
- `problem_cache.py`
- `POST /pipeline/run` SSE
- Java `PipelineProgress`
- `AIBridgeService.runPipeline(...)`
- `MainController` text-mode cap nhat progress bar theo SSE
- cache theo hash problem text trong `cache/problems`
- quality gate heuristic cho KILLER cases

Luu y quan trong: Pipeline sinh input cases va progress. Expected output van do Java oracle local tinh sau khi pipeline done.

## 8. Java Components Quan Trong

### `MainController`

Dieu phoi luong analyze:

- Text mode: goi `/pipeline/run`, nhan progress SSE, lay problem/testcases seed.
- Image mode: goi analyze/testcase legacy.
- Sinh validation SMALL cases.
- Tao expected output bang brute/majority/canonical oracle.
- Goi `/stress` de lay trusted optimized oracle.
- Tinh expected output cho MEDIUM/KILLER bang trusted oracle.
- Truyen problem/testcases/cached AC oracle sang cac tab con.

### `ResultController`

Quan ly sinh code va run result:

- Sinh AC/WA/TLE.
- AC co the dung cached oracle neu ngon ngu khop.
- TLE:
  - goi `/analyze/complexity`;
  - chi validate SMALL toi da 3 case;
  - reject source co sleep/dummy/infinite loop;
  - retry toi da 3 lan voi feedback.
- Run all tests va update verdict/diff.

### `ExecutionService`

Compile/run local:

- C++: `g++`
- Java: `javac`
- Python: `python3`, `python`, hoac `py -3`
- Timeout mac dinh doc tu `src/main/resources/config.properties`
- Co `generateOutputs` de chay oracle mot lan compile, nhieu input.

### `AIBridgeService`

Java HTTP bridge:

- `/health`
- `/analyze`
- `/analyze/complexity`
- `/testcase`
- `/codegen`
- `/stress`
- `/pipeline/run` SSE

## 9. Verification Da Chay Trong Qua Trinh Nang Cap

Da chay nhieu lan:

```powershell
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service
```

Da chay Maven compile bang IntelliJ bundled Maven:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Da smoke test:

- backend top-up testcase mock;
- stress agent mock;
- `/stress` route mock;
- complexity analyzer/codegen prompt;
- `/analyze/complexity` route mock;
- problem classifier/testcase type prompt;
- problem analyzer auto classification;
- `/pipeline/run` SSE mock;
- pipeline cache path.

## 10. Cac Gioi Han Hien Tai

- Special judge that su chua co checker rieng. App van exact-match output khi run user code. Cach giam rui ro hien tai la dung canonical AC oracle cho bai multiple-output.
- Pipeline quality gate hien tai la heuristic dua tren kich thuoc input KILLER vs SMALL; chua chay brute timeout tren KILLER trong backend vi expected output/oracle execution chinh van o Java layer.
- Text mode da dung pipeline SSE. Image mode van legacy, chua goi pipeline sau OCR.
- Cache pipeline luu input cases/problem metadata, khong luu expected output da tinh trong Java.
- Neu `.env` sai format, Python service co the in warning dotenv.
- `sandbox/` la thu muc tam. Khong coi file trong do la source chinh.
- Trong working tree hien tai co the thay `sandbox/Main.cpp` va `sandbox/prog.exe` deleted do qua trinh compile/run truoc do; day la artifact tam, khong nen revert/commit neu khong can.

## 11. Huong Dan Cho Coding Agent Tiep Theo

Nguyen tac bat buoc:

- Khong de AI sinh expected output trong `/testcase`.
- Expected output phai duoc tinh bang oracle local trong Java.
- Khi sua Python, chay compileall.
- Khi sua Java, chay Maven compile bang IntelliJ bundled Maven neu `mvn` khong co PATH.
- Khong revert file dirty khong lien quan, dac biet `sandbox/`.
- Neu sua API schema, cap nhat ca Python schema va Java model.

Lenh huu ich:

```powershell
# Python compile
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service

# Java compile bang IntelliJ Maven
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile

# Health check
Invoke-RestMethod http://localhost:8000/health

# Git status
git status --short
```

## 12. Y Tuong Nang Cap Tiep Theo

- Dua image mode vao `/pipeline/run` sau OCR.
- Luu cache expected output/oracle code o Java side de lan sau khong tinh lai.
- Them real checker/special judge endpoint cho bai multiple-output.
- Nang quality gate backend de thuc su chay brute/KILLER voi timeout khi co oracle.
- Them unit tests rieng cho:
  - `problem_classifier`
  - `complexity_analyzer`
  - `testcase_generator` top-up
  - `pipeline_orchestrator`
  - `AIBridgeService.runPipeline`
- Them cancel button cho AI/pipeline jobs dai.
