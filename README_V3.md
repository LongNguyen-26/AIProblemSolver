# AIProblemSolver README V3

Tai lieu nay la snapshot sau khi lam viec voi `docs/UPGRADE_ROADMAP_V3.md` va `docs/UPGRADE_ROADMAP_V3.1.md`. Phase E da don lai cac artifact Phase B sai huong va thay bang logic dam bao phan phoi 3 profile SMALL/MEDIUM/KILLER.

Ngay kiem tra gan nhat: 2026-05-17.

## 1. Tong Quan App

AIProblemSolver la ung dung desktop ho tro phan tich de bai lap trinh thi dau, sinh input testcases, sinh code mau AC/WA/TLE, chay code local va xuat bao cao.

Ung dung gom 2 phan:

- JavaFX desktop app: UI, quan ly testcase, compile/run code trong `sandbox/`, hien verdict/diff, export ZIP/report.
- Python FastAPI AI service: phan tich/OCR de bai, classify problem type, sinh input-only testcases, sinh code, stress/orchestration pipeline.

Nguyen tac quan trong nhat van giu nguyen:

- `/testcase` chi sinh input. Khong de AI sinh expected output.
- Expected output phai do Java local oracle tinh bang `ExecutionService`.
- `sandbox/` va `ai_service/cache/problems/` la artifact/cache, khong coi la source chinh khi review tinh nang.

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
| Pipeline cache | JSON files under `ai_service/cache/problems/` |

## 3. Cach Chay Va Verify

Chay Python AI service:

```powershell
cd C:\Code_Storage\AIProblemSolver\ai_service
.\.venv\Scripts\Activate.ps1
python main.py
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

Compile Python:

```powershell
cd C:\Code_Storage\AIProblemSolver
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service
```

Compile Java bang Maven bundled cua IntelliJ:

```powershell
cd C:\Code_Storage\AIProblemSolver
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Neu thay warning `Python-dotenv could not parse statement...`, kiem tra lai `ai_service/.env`. Warning nay da tung xuat hien trong smoke test va khong phai loi compile.

## 4. Luong Xu Ly Hien Tai

### Text Mode

```text
User paste problem text
  -> Java goi POST /pipeline/run bang SSE
  -> Python pipeline analyze + classify + generate SMALL/MEDIUM/KILLER input cases
  -> Java cap nhat progress bar theo SSE
  -> Java nhan Problem + input-only testcases tu pipeline
  -> Java sinh/chay oracle local de tinh expected output
  -> Java goi /stress de tim trusted optimized oracle
  -> Java dung trusted oracle tinh expected output cho MEDIUM/KILLER khi co the
  -> UI hien Problem, Test Cases, cached AC oracle
```

### Image Mode

Image mode van theo legacy flow:

```text
Image -> OCR/analyze -> generate input cases theo profile -> Java oracle expected output
```

Image mode chua duoc dua vao `/pipeline/run` sau OCR.

## 5. API Chinh

### `GET /health`

Tra:

```json
{"status":"ok"}
```

### `POST /analyze`

Phan tich de bai thanh `ProblemSchema`, co classification metadata:

- `problem_type`
- `secondary_type`
- `type_confidence`
- `tle_strategy`

### `POST /testcase`

Sinh input-only testcases.

Request fields quan trong:

- `problem`
- `count`
- `requested_count`
- `include_edge_cases`
- `profile`: `SMALL`, `MEDIUM`, `KILLER`
- `existing_inputs`

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

### `POST /codegen`

Sinh code AC/WA/TLE/reference oracle. Request hien co:

- `validation_cases`
- `error_log`
- `complexity_info`

TLE prompt da duoc fix tu cac task truoc: cam sleep/dummy/infinite loop/random va yeu cau neu chuong trinh chay xong thi output phai dung.

### `POST /stress`

Chay Python `StressTestingAgent`, generate/fix brute oracle, optimized oracle, input generator, compare brute vs optimized, tra `StressResult`.

### `POST /pipeline/run`

SSE endpoint cho pipeline. Event `DONE` co:

- `problem`
- `all_testcases`
- `warnings`
- `cached`

## 6. Roadmap V3 - Trang Thai Da Ap Dung

### Phase C - Zero-error UX: da implement

Muc tieu: bam "Phan tich de" khong vang exception/error dialog ra UI khi AI service, SSE, stress, codegen, oracle output fail.

Da ap dung:

- `src/main/java/org/example/ui/controller/MainController.java`
  - Analyze flow co fallback/best-effort result.
  - Text mode neu pipeline fail thi thu legacy `/analyze` + `/testcase`.
  - Neu expected output khong tinh duoc thi testcases duoc danh dau `N/A` thay vi crash.
- `src/main/java/org/example/service/AIBridgeService.java`
  - `runPipeline(...)` co SSE per-event timeout 30s.
  - Neu SSE fail/timeout thi fallback sang legacy analyze/testcase.
- `src/main/java/org/example/service/ExecutionService.java`
  - `generateOutputs(...)` tra list `N/A` khi source rong, compile fail, runtime fail, timeout; khong throw len UI trong luong expected-output.
- `src/main/java/org/example/ui/controller/ResultController.java`
  - Codegen fail hien `"Sinh code that bai, thu lai"` trong UI thay vi error dialog.
- `ai_service/main.py`
  - Co global exception handler tra JSON `{error, detail, fallback}`.
- `ai_service/routers/*.py`
  - Analyze/testcase/codegen/stress/pipeline co fallback response de giam 500/stream crash.
- `ai_service/services/stress_testing_agent.py`
  - Stress agent co fail-safe return `StressResult`, co gang tra brute oracle fallback neu co.
- `ai_service/services/pipeline_orchestrator.py`
  - Problem text qua dai cat ve 50000 ky tu va ghi warning.
  - Pipeline exception ket thuc bang state `DONE` voi partial result thay vi crash stream.

### Phase A - KILLER testcase: da implement phan lon

Muc tieu: KILLER nhanh hon, co strategy rieng, co retry/quality gate/fallback.

Da ap dung:

- `ai_service/services/testcase_generator.py`
  - Them `KILLER_STRATEGIES` theo problem type.
  - `/testcase` voi profile `KILLER` di qua `generate_killer_cases(...)`.
  - Them `generate_single_killer_case(...)` voi prompt raw stdin only theo strategy.
  - Them parser max N tu constraints cho cac dang nhu `n <= 100000`, `n <= 2*10^5`, `n <= 2e5`.
- `ai_service/services/pipeline_orchestrator.py`
  - Sinh KILLER bang `asyncio.to_thread(...)` + `asyncio.gather(..., return_exceptions=True)`.
  - Retry toi da 3 lan khi KILLER khong du/khong qua gate.
  - Quality gate kiem token size, max N, max numeric value.
  - Fallback promote MEDIUM thanh KILLER neu van thieu.

Luu y: quality gate van la heuristic, chua chay brute/oracle timeout tren KILLER trong backend.

### Phase B - Coverage testcase: da reverse va da don trong Phase E

Phase B taxonomy coverage khong dung y nguoi dung, nen da duoc don sach trong Phase E:

- Da xoa `ai_service/services/testcase_coverage_checker.py`.
- Da go bo `CATEGORY_PROMPT_HINTS` va `generate_with_hint(...)`.
- Da go bo coverage imports/top-up/prioritization trong `pipeline_orchestrator.py`.
- Da go bo minimum 13 testcase trong Python/Java.
- Da go bo coverage summary UI trong `TestCaseController` va `testcase_view.fxml`.

Grep kiem tra artifact Phase B mong doi khong co ket qua:

```powershell
rg "TestcaseCoverageChecker|REQUIRED_TESTCASE_TAXONOMY|MIN_COVERAGE_TESTCASES|fill_coverage_gaps|generate_with_hint|CATEGORY_PROMPT_HINTS" ai_service
rg "MIN_COVERAGE_TESTCASE_COUNT|updateCoverageSummary|coverageLabel" src
```

### Phase E - Don Phase B + dam bao du 3 profile: da implement

Da ap dung:

- `ai_service/services/pipeline_orchestrator.py`
  - Target testcase toi thieu la 3, khong con enforce 13.
  - `_build_test_pyramid_plan(...)` chia theo bang V3.1: vi du N=10 -> 4 SMALL, 3 MEDIUM, 3 KILLER.
  - KILLER retry duoc tach thanh `_generate_killer_with_retry(...)`.
  - Neu KILLER thieu sau retry, fallback promote MEDIUM sang `[KILLER-promoted]`.
  - Duplicate `import asyncio` da duoc don.
- `src/main/java/org/example/ui/controller/MainController.java`
  - Plan Java dong bo voi Python.
  - Sau analyze, status bar hien `Testcases: X SMALL | Y MEDIUM | Z KILLER`.
- `src/main/java/org/example/ui/controller/TestCaseController.java`
  - Da go bo coverage summary logic.
- `src/main/resources/fxml/testcase_view.fxml`
  - Da go bo `coverageLabel`.

### Phase D - WA code phai that su WA: chua implement

Chua thay cac thay doi Phase D:

- Chua co WA prompt v2 day du trong `code_generator.py`.
- Chua co post-generation WA validation/retry trong Java `ResultController`.
- Chua co endpoint `/codegen/wa_retry`.

## 7. Kiem Tra Reverse Hien Tai

Da chay ngay 2026-05-17:

```powershell
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Ket qua: ca Python compileall va Maven compile deu pass.

Van de/phat hien:

- Phase B artifact grep hien khong con ket qua.
- Duplicate `import asyncio` da duoc don.
- `git status --short` tai thoi diem kiem tra bao cac cache files bi delete:
  - `ai_service/cache/problems/0f81176b4b040f1d.json`
  - `ai_service/cache/problems/3358b4b334ded8b2.json`
  - `ai_service/cache/problems/891882c9a82276bc.json`
  - `ai_service/cache/problems/fe1c69d65e3059c8.json`

## 8. Cac File Quan Trong Cho Session Sau

Java:

- `src/main/java/org/example/ui/controller/MainController.java`
  - Dieu phoi analyze pipeline/legacy, tao expected output, stress oracle, push data sang tabs.
- `src/main/java/org/example/service/AIBridgeService.java`
  - Java HTTP bridge toi FastAPI, co SSE pipeline timeout/fallback.
- `src/main/java/org/example/service/ExecutionService.java`
  - Compile/run C++/Java/Python local, generate expected outputs.
- `src/main/java/org/example/ui/controller/ResultController.java`
  - Sinh AC/WA/TLE, validate TLE, run all tests.
- `src/main/java/org/example/ui/controller/TestCaseController.java`
  - Quan ly bang testcase, detail panes, export ZIP. Hien con coverage UI neu Phase B chua duoc revert sach.

Python:

- `ai_service/main.py`
  - FastAPI app + global exception handler.
- `ai_service/routers/pipeline.py`
  - SSE route `/pipeline/run`.
- `ai_service/services/pipeline_orchestrator.py`
  - Orchestration pipeline. Hien da don Phase B va enforce 3 profile.
- `ai_service/services/testcase_generator.py`
  - Sinh SMALL/MEDIUM/KILLER, KILLER strategies, duplicate filter.
- `ai_service/services/stress_testing_agent.py`
  - Stress oracle agent va fail-safe fallback.
- `ai_service/services/code_generator.py`
  - AC/WA/TLE/reference code generation prompts.
- `ai_service/services/problem_classifier.py`
  - Problem type classifier.
- `ai_service/services/problem_taxonomy.py`
  - 16 problem type taxonomy.

## 9. Huong Dan Cho Coding Agent Sau

Truoc khi sua:

1. Chay `git status --short`.
2. Khong dua lai Phase B taxonomy coverage neu nguoi dung khong yeu cau ro.
3. Khong revert `sandbox/` hoac cache artifact neu khong duoc yeu cau ro.
4. Neu sua Python, chay compileall.
5. Neu sua Java/FXML, chay Maven compile bang IntelliJ bundled Maven.

Neu session sau thay cac symbol Phase B quay lai, can coi do la regression tru khi nguoi dung yeu cau implement coverage taxonomy lai tu dau.

## 10. Gioi Han Hien Tai

- Special judge that su chua co checker rieng; app van exact-match output khi run user code.
- Pipeline cache luu problem/testcase input, khong luu expected output Java da tinh.
- Quality gate KILLER la heuristic.
- Image mode chua di qua pipeline SSE.
- Phase D chua lam, nen WA code co the van pass tat ca testcases neu AI sinh bug qua nhe.
