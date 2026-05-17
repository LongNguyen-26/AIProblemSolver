# AIProblemSolver README V3.1

Snapshot nay tong hop trang thai app sau cac dot implement theo
`docs/UPGRADE_ROADMAP_V3.md` va `docs/UPGRADE_ROADMAP_V3.1.md`.

Ngay tao snapshot: 2026-05-17.

Trang thai roadmap hien tai:

| Phase | Trang thai | Noi dung |
|---|---|---|
| C | Done | Zero-error UX, fallback khi AI/SSE/oracle/stress fail |
| A | Done | KILLER testcase nhanh hon, co strategy, retry va fallback |
| B | Reversed/cleaned | Coverage taxonomy 6 nhom da bi loai bo vi sai huong |
| E | Done | Don rac Phase B va dam bao du SMALL/MEDIUM/KILLER |
| D | Done | WA prompt V2 va Java validation/retry de WA that su ra WA |

## 1. Tong Quan App

AIProblemSolver la desktop app ho tro phan tich de bai competitive programming,
sinh testcase, sinh code AC/WA/TLE/reference, chay code local va xuat bao cao.

App gom 2 phan:

- JavaFX desktop app:
  - UI nhap de bai text/image.
  - Dieu phoi pipeline, hien progress/status.
  - Quan ly bang testcase, expected output, verdict, diff.
  - Sinh code AC/WA/TLE va chay tren testcases.
  - Compile/run C++/Java/Python trong `sandbox/`.
  - Export report.
- Python FastAPI AI service:
  - OCR/analyze de bai.
  - Classify problem type.
  - Sinh input-only testcases SMALL/MEDIUM/KILLER.
  - Sinh code AC/WA/TLE/oracle/brute/generator.
  - Stress testing va pipeline SSE.

Nguyen tac bat bien:

- `/testcase` chi sinh input. AI khong duoc sinh expected output.
- Expected output do Java local oracle tinh qua `ExecutionService`.
- `sandbox/` va `ai_service/cache/problems/` la artifact/cache, khong coi la source chinh khi review.
- Khong dua lai Phase B taxonomy coverage neu nguoi dung khong yeu cau ro.

## 2. Tech Stack Va Runtime

| Thanh phan | Cong nghe |
|---|---|
| Desktop UI | Java + JavaFX 17 |
| Java build | Maven |
| Java JSON | Gson |
| Java HTTP | `java.net.http.HttpClient`, HTTP/1.1 cho SSE |
| AI service | Python 3.10+ + FastAPI + Uvicorn |
| AI provider | Groq OpenAI-compatible API |
| Default model | `openai/gpt-oss-120b` |
| OCR | Tesseract + pytesseract + Pillow |
| Local execution | Java `ProcessBuilder` |
| Cache | JSON files duoi `ai_service/cache/problems/` |

Python venv dang dung:

```powershell
C:\Code_Storage\AIProblemSolver\ai_service\.venv
```

Maven nen dung ban bundled cua IntelliJ:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

## 3. Cach Chay Va Verify

Chay AI service:

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

Compile Java:

```powershell
cd C:\Code_Storage\AIProblemSolver
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Smoke check route Phase D:

```powershell
@'
import sys
sys.path.insert(0, 'ai_service')
from main import app
assert '/codegen/wa_retry' in {getattr(route, 'path', '') for route in app.routes}
print('codegen_routes_ok')
'@ | .\ai_service\.venv\Scripts\python.exe -
```

Neu thay warning `Python-dotenv could not parse statement...`, do la warning tu
`ai_service/.env`. Warning nay da xuat hien trong smoke test truoc va khong phai
loi compile Python/Java.

## 4. Luong Xu Ly Hien Tai

### Text Mode

```text
User paste problem text
  -> Java MainController goi AIBridgeService.runPipeline(...)
  -> Java POST /pipeline/run bang SSE
  -> Python pipeline analyze + classify + generate SMALL/MEDIUM/KILLER input cases
  -> Java cap nhat progress bar/status theo SSE event
  -> Java nhan Problem + input-only testcases o DONE event
  -> Java log distribution: X SMALL | Y MEDIUM | Z KILLER
  -> Java sinh/chay oracle local de tinh expected output
  -> Java goi /stress de tim trusted optimized oracle khi co the
  -> Java dung trusted/canonical oracle tinh expected output cho MEDIUM/KILLER
  -> UI hien Problem, Test Cases, cached AC oracle
```

### Image Mode

Image mode van theo legacy flow:

```text
Image -> OCR/analyze -> generate input cases theo profile -> Java oracle expected output
```

Image mode chua di qua `/pipeline/run` sau OCR.

### Codegen Tab

```text
User chon AC/WA/TLE va language
  -> ResultController.generateCodeWithValidation(...)
  -> AC: goi /codegen truc tiep, co the dung cached AC oracle neu language khop
  -> TLE: goi /codegen voi complexity_info + validation_cases, validate local
  -> WA: goi /codegen voi WA prompt V2, validate local, retry /codegen/wa_retry neu pass het
  -> UI hien source code + explanation, khong show error dialog khi codegen fail mem
```

## 5. API Hien Tai

### `GET /health`

Response:

```json
{"status":"ok"}
```

### `POST /analyze`

Input:

- `text`
- hoac `image_base64`

Output:

- `problem: ProblemSchema`

`ProblemSchema` co cac field chinh:

- `title`
- `description`
- `input_format`
- `output_format`
- `constraints`
- `sample_inputs`
- `sample_outputs`
- `problem_type`
- `secondary_type`
- `type_confidence`
- `tle_strategy`

### `POST /testcase`

Sinh input-only testcases.

Request chinh:

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

Luu y: `expected_output` tu endpoint nay luon rong. Java se fill sau.

### `POST /pipeline/run`

SSE endpoint cho text mode.

`PipelineProgress` event co:

- `state`
- `message`
- `progress_pct`
- `testcases_ready`
- `testcases_target`
- `warnings`
- `problem`
- `all_testcases`
- `cached`

Event `DONE` la ket qua chinh Java dung de cap nhat UI.

### `POST /codegen`

Sinh code AC/WA/TLE/reference oracle/generator.

Request:

- `problem`
- `type`: `AC`, `WA`, `TLE`, `ORACLE`, `BRUTE`, `BRUTE_ALT`, `ORACLE_ALT`, `OPTIMIZED_ALT`, `GENERATOR`
- `language`: `cpp`, `python`, `java`
- `validation_cases`
- `error_log`
- `complexity_info`

Response `CodeGenResponse`:

- `code`
- `language`
- `type`
- `explanation`

### `POST /codegen/wa_retry`

Endpoint Phase D cho Java goi khi WA code vua sinh lai pass het validation cases.

Request `WaRetryRequest`:

- `problem: ProblemSchema`
- `language`
- `feedback`
- `sample_cases: List[TestCaseSchema]`

Response dung `CodeGenResponse`, `type = "WA"`.

### `POST /stress`

Chay `StressTestingAgent`.

Request:

- `problem`
- `small_cases`
- `rounds`

Response `StressResult`:

- `trusted`
- `trusted_oracle_code`
- `trusted_oracle_language`
- `found_counterexample`
- `counterexample_input`
- `brute_output`
- `optimized_output`
- `rounds_completed`
- `oracle_retries`
- `generator_trusted`
- `message`
- `problem_type`

## 6. Phase C - Zero-Error UX

Muc tieu: Bam "Phan tich de" khong bi vang exception/error dialog ra UI khi AI
service, SSE, stress, codegen hoac oracle output fail.

Da implement:

- `src/main/java/org/example/service/AIBridgeService.java`
  - `runPipeline(...)` thu SSE `/pipeline/run`.
  - Neu SSE fail/timeout thi fallback sang legacy `/analyze` + `/testcase`.
  - SSE doc bang `BodyHandlers.ofInputStream()`.
  - Moi SSE event co timeout 30 giay (`SSE_EVENT_TIMEOUT_SECONDS`).
- `src/main/java/org/example/ui/controller/MainController.java`
  - Analyze flow co best-effort result.
  - Pipeline fail thi thu legacy.
  - Expected-output fail thi danh dau `N/A` thay vi crash UI.
  - Stress/oracle fail thi tiep tuc voi ket qua tot nhat co duoc.
- `src/main/java/org/example/service/ExecutionService.java`
  - `generateOutputs(...)` tra list `N/A` khi source rong, compile fail, runtime fail hoac timeout.
  - Luong generate expected output khong throw len UI cho cac loi nay.
- `src/main/java/org/example/ui/controller/ResultController.java`
  - Codegen fail hien `"Sinh code that bai, thu lai"` trong UI.
  - Khong show error dialog cho loi codegen mem.
- `ai_service/main.py`
  - Global exception handler tra JSON `{error, detail, fallback}`.
- `ai_service/routers/analyze.py`, `testcase.py`, `codegen.py`, `stress.py`, `pipeline.py`
  - Co fallback response/stream `DONE` de giam crash/500.
- `ai_service/services/pipeline_orchestrator.py`
  - Problem text qua dai duoc cat ve 50000 ky tu va ghi warning.
  - Pipeline exception ket thuc bang state `DONE` voi partial result.
- `ai_service/services/stress_testing_agent.py`
  - Stress agent co fail-safe `StressResult`, co gang tra brute oracle fallback neu co.

## 7. Phase A - KILLER Testcase

Muc tieu: KILLER testcase nhanh hon, co strategy rieng, co retry/quality gate va fallback.

Da implement:

- `ai_service/services/testcase_generator.py`
  - Them `KILLER_STRATEGIES` theo problem type.
  - `/testcase` voi `profile = "KILLER"` di qua `generate_killer_cases(...)`.
  - Them `generate_single_killer_case(...)` voi prompt raw stdin only theo strategy.
  - Them parser max N tu constraints cho cac dang:
    - `n <= 100000`
    - `n <= 2*10^5`
    - `n <= 2e5`
  - SMALL prompt duoc bo sung de co case n=1/minimum size, all-equal/repeated, all-negative/mixed-sign khi constraints cho phep.
- `ai_service/services/pipeline_orchestrator.py`
  - Sinh KILLER bang `asyncio.to_thread(...)` va `asyncio.gather(..., return_exceptions=True)`.
  - Retry toi da 3 lan khi KILLER khong du/khong qua quality gate.
  - Quality gate kiem input usable, token/char size, max N, numeric value.
  - Fallback promote MEDIUM thanh KILLER khi thieu.

Luu y: KILLER quality gate van la heuristic. Backend chua chay brute/oracle timeout
tren KILLER de chung minh no that su bat TLE.

## 8. Phase B - Coverage Taxonomy Da Bi Loai Bo

Phase B tung them coverage checker/taxonomy 6 nhom, nhung nguoi dung da reverse vi
ke hoach sai huong. Phase E da don sach cac artifact nay.

Nhung thu da bi loai bo:

- `ai_service/services/testcase_coverage_checker.py`
- `TestcaseCoverageChecker`
- `REQUIRED_TESTCASE_TAXONOMY`
- `MIN_COVERAGE_TESTCASES`
- `CATEGORY_PROMPT_HINTS`
- `generate_with_hint(...)`
- `_fill_coverage_gaps(...)`
- `_generate_targeted_cases(...)`
- `_sample_cases(...)`
- `_prioritize_cases_for_coverage(...)`
- Java `MIN_COVERAGE_TESTCASE_COUNT`
- Java `updateCoverageSummary(...)`
- FXML `coverageLabel`

Grep mong doi khong co ket qua source:

```powershell
rg "TestcaseCoverageChecker|REQUIRED_TESTCASE_TAXONOMY|MIN_COVERAGE_TESTCASES|fill_coverage_gaps|generate_with_hint|CATEGORY_PROMPT_HINTS" ai_service
rg "MIN_COVERAGE_TESTCASE_COUNT|updateCoverageSummary|coverageLabel" src
```

`README_V3.md` va file nay van co nhac den cac symbol do nhu tai lieu lich su;
khong coi cac mention trong README la source artifact.

## 9. Phase E - Dam Bao Du 3 Profile

Muc tieu: Giai quyet dung van de "testcase chi ra SMALL/MEDIUM, thieu KILLER" bang
phan phoi profile, khong dung taxonomy coverage.

Da implement:

- `ai_service/services/pipeline_orchestrator.py`
  - Target testcase toi thieu la 3.
  - Khong con enforce minimum 13.
  - `_build_test_pyramid_plan(total)` chia profile theo bang V3.1.
  - `_generate_killer_with_retry(...)` tach rieng retry KILLER.
  - `_generate_killer_parallel(...)` sinh nhieu KILLER song song.
  - `_is_valid_killer(...)` loc KILLER theo heuristic.
  - `_promote_medium_to_killer(...)` fallback neu KILLER thieu.
  - `_promoted_killer_case(...)` mark description `[KILLER-promoted]`.
- `src/main/java/org/example/ui/controller/MainController.java`
  - `buildTestPyramidPlan(...)` dong bo voi Python.
  - Sau analyze thanh cong, `logProfileDistribution(...)` hien:
    - `Testcases: X SMALL | Y MEDIUM | Z KILLER`
  - Neu killer = 0, status co warning ro.

Distribution hien tai:

| N | SMALL | MEDIUM | KILLER |
|---|---:|---:|---:|
| 5 | 2 | 2 | 1 |
| 8 | 3 | 3 | 2 |
| 10 | 4 | 3 | 3 |
| 15 | 6 | 5 | 4 |
| 20 | 8 | 6 | 6 |

Cong thuc:

- `total = max(3, requested_count)`
- `killer = max(1, floor(total * 0.3))`
- `small` khoang 40%
- `medium = total - small - killer`, dam bao toi thieu 1

## 10. Phase D - WA Code Phai That Su WA

Muc tieu: Code sinh voi type `WA` khong duoc pass het testcases hien co ma phai co
it nhat mot verdict `WA` neu co validation cases phu hop.

Da implement Python:

- `ai_service/services/code_generator.py`
  - Them `WA_SYSTEM_PROMPT`.
  - Them `WA_USER_PROMPT`.
  - Neu `code_type == "WA"`, `generate_code(...)` dung flow text rieng thay vi prompt JSON chung.
  - Prompt bat buoc:
    - compile/run duoc;
    - dung cau truc thuat toan dung;
    - chua dung 1 bug tinh vi;
    - comment dau source co `BUG_TYPE`, `BUG_DESC`, `FAILS_ON`;
    - Java phai dung `public class Main`, C++ phai co `int main()`, Python doc stdin.
  - `_build_wa_prompt(...)` dua toi da 3 validation/sample cases vao prompt.
  - Retry feedback duoc dua vao prompt khi Java goi `/codegen/wa_retry`.
  - `_line_comment_prefix(...)` dung `#` cho Python, `//` cho C++/Java.
- `ai_service/models/schemas.py`
  - Them `WaRetryRequest`.
- `ai_service/routers/codegen.py`
  - Them endpoint `POST /codegen/wa_retry`.
  - Endpoint goi `generate_code(..., "WA", ..., validation_cases=sample_cases, error_log=feedback)`.
  - Loi codegen tra `CodeGenResponse` rong voi explanation thay vi crash.

Da implement Java:

- `src/main/java/org/example/service/AIBridgeService.java`
  - Them `retryWACode(Problem problem, String language, String feedback, List<TestCase> sampleCases)`.
  - Body JSON:
    - `problem`
    - `language`
    - `feedback`
    - `sample_cases`
  - POST toi `/codegen/wa_retry`.
- `src/main/java/org/example/ui/controller/ResultController.java`
  - Them constants:
    - `MAX_WA_RETRIES = 3`
    - `MAX_WA_VALIDATION_CASES = 5`
    - `MAX_WA_PROMPT_CASES = 3`
  - `generateCodeWithValidation(...)` route `WA` sang `generateWaCodeWithValidation(...)`.
  - `waValidationCases()` uu tien SMALL cases co input va expected output hop le.
  - Expected output `N/A` khong duoc dung de validate WA.
  - `validateWaSubmission(...)` chay copied testcases bang `ExecutionService.runTestCases(...)`.
  - Neu co it nhat 1 verdict `WA`, chap nhan submission.
  - Neu pass het, build feedback gom input/expected/actual va retry.
  - Neu ra CE/RE/TLE thay vi WA, coi la chua hop le va retry.
  - Sau toi da 3 retry ma van chua confirm WA, van hien code cuoi cung kem warning trong explanation, khong crash UI.

Luu y: WA validation chi manh bang pool testcase hien co. Neu testcase yeu hoac expected output
la `N/A`, app co the khong confirm duoc WA that su.

## 11. TLE Logic Hien Tai

TLE da co logic validate rieng trong `ResultController.java`.

Flow:

- Lay toi da 3 validation cases, uu tien SMALL.
- Goi `/analyze/complexity` de lay `ComplexityInfo`.
- Goi `/codegen` voi type `TLE`, validation cases, complexity info va error feedback lan truoc.
- Validate source shape:
  - Cam `sleep`, `usleep`, `Thread.sleep`, `this_thread::sleep`, `setTimeout`.
  - Cam dummy/busy wait.
  - Cam `while(true)`/infinite loop ro rang.
- Chay local tren validation cases.
- TLE candidate hop le neu moi case la `AC` hoac `TLE`.
- Neu case ra `WA`/`RE`/`CE`, retry toi da 3 lan.

Python `TYPE_INSTRUCTIONS["TLE"]` cung yeu cau:

- Logic phai dung.
- Cham do asymptotic complexity xau.
- Khong sleep, dummy loop, random, infinite loop.
- Neu chuong trinh ket thuc tren validation case thi output phai dung.

## 12. Expected Output Va Oracle Logic

Nguyen tac: Python testcase generator khong sinh expected output.

Java expected-output flow nam chu yeu trong `MainController.java` va `ExecutionService.java`:

- Input-only testcases tu pipeline/testcase endpoint.
- Java sinh/chay oracle code local de tinh expected output.
- Co nhieu oracle strategy:
  - `AC`
  - `ORACLE`
  - `BRUTE`
  - `BRUTE_ALT`
  - `ORACLE_ALT`
  - `OPTIMIZED_ALT`
- Co stress flow de tim trusted optimized oracle.
- Neu oracle output fail, compile fail, timeout, runtime fail, app fill `N/A`.
- `ExecutionService.normalizeOutput(...)` strip trailing whitespace tung dong.
- `ExecutionService.outputMatches(...)` exact-match sau normalize.

Gioi han:

- Chua co special judge/checker rieng.
- Multiple-valid-output/constructive problems van co risk exact-match sai.
- Pipeline cache khong luu expected output Java da tinh.

## 13. File Map Quan Trong

Java:

- `src/main/java/org/example/ui/controller/MainController.java`
  - Dieu phoi analyze text/image, pipeline, legacy fallback, expected output, stress oracle, profile distribution.
- `src/main/java/org/example/ui/controller/ResultController.java`
  - Sinh AC/WA/TLE, validate WA/TLE, run all tests, display code/explanation/verdict.
- `src/main/java/org/example/ui/controller/TestCaseController.java`
  - Quan ly bang testcase, detail pane, edit/export. Coverage UI Phase B da bi go bo.
- `src/main/java/org/example/service/AIBridgeService.java`
  - Java HTTP bridge toi FastAPI, including `/pipeline/run`, `/codegen`, `/codegen/wa_retry`, `/stress`.
- `src/main/java/org/example/service/ExecutionService.java`
  - Compile/run C++/Java/Python local trong sandbox.
  - Fill expected outputs va verdict.
- `src/main/java/org/example/model/Problem.java`
  - Java model mapping `ProblemSchema`.
- `src/main/java/org/example/model/TestCase.java`
  - Java model mapping `TestCaseSchema`, gom `expected_output`, verdict, actual output.
- `src/main/java/org/example/model/CodeSubmission.java`
  - Java model mapping `CodeGenResponse`.

Python:

- `ai_service/main.py`
  - FastAPI app, router registration, global exception handler.
- `ai_service/models/schemas.py`
  - Pydantic schemas: Problem, TestCase, Pipeline, Stress, CodeGen, WaRetry.
- `ai_service/routers/analyze.py`
  - Analyze/OCR route.
- `ai_service/routers/testcase.py`
  - `/testcase`.
- `ai_service/routers/pipeline.py`
  - `/pipeline/run` SSE.
- `ai_service/routers/codegen.py`
  - `/codegen`, `/codegen/wa_retry`.
- `ai_service/routers/stress.py`
  - `/stress`.
- `ai_service/services/pipeline_orchestrator.py`
  - Analyze/classify/generate all profiles/SSE progress/cache.
- `ai_service/services/testcase_generator.py`
  - SMALL/MEDIUM/KILLER input generation, KILLER strategies, fallback, duplicate filter.
- `ai_service/services/code_generator.py`
  - Prompt va generation cho AC/WA/TLE/oracles/generator.
- `ai_service/services/stress_testing_agent.py`
  - Stress oracle/generator/counterexample logic va fail-safe.
- `ai_service/services/problem_classifier.py`
  - Fast problem classification.
- `ai_service/services/problem_taxonomy.py`
  - 16 problem type taxonomy dung cho strategy, khong phai Phase B coverage checker.
- `ai_service/services/groq_json.py`
  - Groq/OpenAI-compatible request helpers, JSON/text completion.

Docs:

- `README_V3.md`
  - Snapshot truoc do, da cap nhat toi Phase D.
- `README_V3.1.md`
  - File nay, snapshot handoff moi nhat.
- `docs/UPGRADE_ROADMAP_V3.md`
  - Roadmap goc Phase A/B/C.
- `docs/UPGRADE_ROADMAP_V3.1.md`
  - Roadmap Phase E/D.

## 14. Cach Review Sau Nay

Truoc khi sua:

```powershell
git status --short
```

Neu sua Python:

```powershell
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service
```

Neu sua Java/FXML:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Neu nghi Phase B artifact quay lai:

```powershell
rg "TestcaseCoverageChecker|REQUIRED_TESTCASE_TAXONOMY|MIN_COVERAGE_TESTCASES|fill_coverage_gaps|generate_with_hint|CATEGORY_PROMPT_HINTS" ai_service
rg "MIN_COVERAGE_TESTCASE_COUNT|updateCoverageSummary|coverageLabel" src
```

Ket qua mong doi: khong co match trong source. README/docs co the co match lich su.

Neu test Phase E:

- N=10 pipeline nen ra khoang 4 SMALL, 3 MEDIUM, 3 KILLER.
- Status bar sau analyze hien `Testcases: X SMALL | Y MEDIUM | Z KILLER`.
- Neu AI fail sinh KILLER, fallback `[KILLER-promoted]` va khong crash.

Neu test Phase D:

- Sinh WA code, source nen co `BUG_TYPE`, `BUG_DESC`, `FAILS_ON`.
- Chay WA code tren validation cases, it nhat 1 verdict nen la `WA`.
- Neu WA code pass het, UI summary hien retry va Java goi `/codegen/wa_retry`.
- Sau toi da 3 retry, UI van hien code cuoi cung va explanation warning neu chua confirm duoc WA.

## 15. Known Limitations

- Special judge chua co; app van exact-match output.
- Constructive/multiple valid output problems co risk bi danh WA sai do exact-match.
- KILLER quality gate la heuristic, chua co proof bang runtime.
- Image mode chua di qua pipeline SSE.
- WA validation chi chay tren toi da 5 testcases co expected output, uu tien SMALL.
- Neu expected output la `N/A`, testcase do bi bo qua trong WA validation.
- Pipeline cache khong luu expected output Java da tinh.
- `.env` co the dang co dong parse loi, gay warning `Python-dotenv could not parse statement...`.

## 16. Luu Y Git Va Artifact

- Khong revert user changes neu working tree dirty.
- Khong revert `sandbox/` hoac `ai_service/cache/problems/` neu khong duoc yeu cau ro.
- Cache files duoi `ai_service/cache/problems/` co the bi modified/deleted do run app.
- Neu can commit, review diff source truoc; docs co the mention cac symbol da bi xoa nhu lich su.

## 17. Trang Thai Verify Gan Nhat

Trong session implement Phase D da chay thanh cong:

```powershell
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
git diff --check
```

Smoke check:

- `_build_wa_prompt(...)` co `BUG_TYPE` va retry feedback.
- `WaRetryRequest` parse duoc sample cases.
- FastAPI app co route `/codegen/wa_retry`.

`git diff --check` chi bao warning line-ending LF/CRLF cua Git tren Windows, khong
co whitespace error.
