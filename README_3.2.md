# AIProblemSolver README V3.2

Snapshot nay tong hop tong quan app (V3.1) va ke hoach nang cap V3.2 (F1-F3).

Ngay tao snapshot: 2026-05-17.

---

## 0. Trang thai tong quan

- Baseline hien tai: V3.1 (da hoan thanh Phase A/C/D/E; Phase B da bi reverse/clean).
- V3.2 tap trung vao 3 nang cap: F1 (Disable TLE khi N nho), F2 (KILLER qua script), F3 (Prompt optimize, giam token Groq).
- Tai lieu nguon: `README_V3.1.md` va `docs/UPGRADE_ROADMAP_V3.2_*.md`.

---

## 1. Tong quan app

AIProblemSolver la desktop app ho tro phan tich de bai competitive programming,
sinh testcase, sinh code AC/WA/TLE/reference, chay code local va xuat bao cao.

App gom 2 phan chinh:

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

---

## 2. Tech stack va runtime

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

---

## 3. Kien truc he thong (tom tat)

```
User Machine
  ├─ Java/JavaFX App (UI + Execution Engine)
  │   ├─ ProcessBuilder -> sandbox/
  │   └─ HTTP/JSON -> Python FastAPI (localhost:8000)
  └─ Python FastAPI AI Service
      └─ Groq GPT-OSS 120B (external API)
```

Quyet dinh ky thuat quan trong:

- Giao tiep Java ↔ Python: REST HTTP (localhost).
- Sandbox execution: ProcessBuilder + timeout, khong can Docker.
- AI Provider: Groq `openai/gpt-oss-120b`.
- JSON: Gson (Java) + Pydantic (Python).
- Config: `config.properties` + `.env`.

---

## 4. Luong xu ly hien tai

### Text Mode

```
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

```
Image -> OCR/analyze -> generate input cases theo profile -> Java oracle expected output
```

Image mode chua di qua `/pipeline/run` sau OCR.

### Codegen Tab

```
User chon AC/WA/TLE va language
  -> ResultController.generateCodeWithValidation(...)
  -> AC: goi /codegen truc tiep, co the dung cached AC oracle neu language khop
  -> TLE: goi /codegen voi complexity_info + validation_cases, validate local
  -> WA: goi /codegen voi WA prompt V2, validate local, retry /codegen/wa_retry neu pass het
  -> UI hien source code + explanation, khong show error dialog khi codegen fail mem
```

---

## 5. API hien tai (Python FastAPI)

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

---

## 6. Phase hoan thanh (V3.1 baseline)

- Phase C: Zero-error UX, fallback khi AI/SSE/oracle/stress fail.
- Phase A: KILLER testcase nhanh hon, co strategy, retry va fallback.
- Phase B: Coverage taxonomy bi reverse/clean.
- Phase E: Dam bao du SMALL/MEDIUM/KILLER.
- Phase D: WA code phai that su WA, co retry /codegen/wa_retry.

Ghi chu: Chi tiet tung phase xem `README_V3.1.md`.

---

## 7. V3.2 – Tong hop nang cap moi

### F1: Disable TLE khi N nho

Muc tieu:

- Nguoi dung khong sinh TLE voi bai toan khong co nghia ve hieu nang.
- Neu `N_max < 1000` hoac `problem_type` la `MATH_FORMULA`/`STRING_BASIC` thi disable TLE.

Thay doi chinh (du kien):

- Python:
  - `ProblemSchema` them `max_constraint_n` va `is_small_n`.
  - `problem_classifier.extract_max_n(...)` parse constraints.
  - `is_small_n` true neu `max_n < 1000` hoac `problem_type` thuoc nhom nho.
- Java:
  - `Problem.java` them `max_constraint_n`, `is_small_n`.
  - `ResultController` them `N_SMALL_THRESHOLD = 1000` va `updateTleButtonState()`.
  - Disable button TLE + tooltip giai thich, guard trong `generateCodeWithValidation()`.

### F2: KILLER qua script thay vi du lieu truc tiep

Muc tieu:

- Tranh JSON bi cat do KILLER rat lon.
- AI sinh Python script ngan, Java chay script de materialize input.

Thay doi chinh (du kien):

- Python:
  - `TestCaseSchema` them `generator_script`.
  - Them `KillerScriptRequest` (tuy chon endpoint).
  - `testcase_generator.generate_killer_script(...)` va flow script-first, fallback direct.
- Java:
  - `TestCase` them `generator_script`.
  - Them `ScriptRunner` (ProcessBuilder chay python trong `.venv`).
  - `MainController` materialize scripts truoc khi tinh expected output.

Failsafe:

- Timeout script, exit code != 0, empty output -> log warning + flag testcase.
- AI khong sinh duoc script -> fallback direct generation.

### F3: Prompt optimize giam token Groq

Muc tieu:

- Giam 40-60% token moi pipeline run, tranh rate limit TPM.

Thay doi chinh (du kien):

- `groq_json.py`: retry exponential backoff cho 429, semaphore gioi han concurrency.
- `testcase_generator.py`:
  - System prompt compact.
  - Minimal problem context theo profile.
  - `max_tokens` theo profile.
  - Sequential KILLER calls + delay nho.
- `pipeline_orchestrator.py`: in-memory cache cho analyze (per-process).
- `code_generator.py`: compact TYPE_INSTRUCTIONS + truncate problem payload.
- Java: tang API timeout (vi backoff).

---

## 8. Expected output va oracle logic

- Python testcase generator khong sinh expected output.
- Java sinh/chay oracle local de tinh expected output.
- Co nhieu oracle strategy: `AC`, `ORACLE`, `BRUTE`, `BRUTE_ALT`, `ORACLE_ALT`, `OPTIMIZED_ALT`.
- Stress flow tim trusted optimized oracle.
- Neu oracle fail, app fill `N/A`, khong crash UI.

Gioi han:

- Chua co special judge/checker rieng (exact-match).
- Constructive/multiple valid output co risk WA sai.
- Pipeline cache khong luu expected output Java da tinh.

---

## 9. File map quan trong

Java:

- `src/main/java/org/example/ui/controller/MainController.java`
- `src/main/java/org/example/ui/controller/ResultController.java`
- `src/main/java/org/example/ui/controller/TestCaseController.java`
- `src/main/java/org/example/service/AIBridgeService.java`
- `src/main/java/org/example/service/ExecutionService.java`
- `src/main/java/org/example/model/Problem.java`
- `src/main/java/org/example/model/TestCase.java`
- `src/main/java/org/example/model/CodeSubmission.java`

Python:

- `ai_service/main.py`
- `ai_service/models/schemas.py`
- `ai_service/routers/analyze.py`
- `ai_service/routers/testcase.py`
- `ai_service/routers/pipeline.py`
- `ai_service/routers/codegen.py`
- `ai_service/routers/stress.py`
- `ai_service/services/pipeline_orchestrator.py`
- `ai_service/services/testcase_generator.py`
- `ai_service/services/code_generator.py`
- `ai_service/services/stress_testing_agent.py`
- `ai_service/services/problem_classifier.py`
- `ai_service/services/problem_taxonomy.py`
- `ai_service/services/groq_json.py`

Docs:

- `README_V3.md`
- `README_V3.1.md`
- `docs/UPGRADE_ROADMAP_V3.md`
- `docs/UPGRADE_ROADMAP_V3.1.md`
- `docs/UPGRADE_ROADMAP_V3.2_F1_TLE_DISABLE.md`
- `docs/UPGRADE_ROADMAP_V3.2_F2_KILLER_SCRIPT.md`
- `docs/UPGRADE_ROADMAP_V3.2_F3_PROMPT_OPTIMIZE.md`

---

## 10. Cach chay va verify (tham khao)

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

---

## 11. Known limitations

- Special judge chua co; app van exact-match output.
- Constructive/multiple valid output problems co risk bi danh WA sai do exact-match.
- KILLER quality gate la heuristic, chua co proof bang runtime.
- Image mode chua di qua pipeline SSE.
- WA validation chi chay tren toi da 5 testcases co expected output, uu tien SMALL.
- Neu expected output la `N/A`, testcase do bi bo qua trong WA validation.
- Pipeline cache khong luu expected output Java da tinh.
- `.env` co the dang co dong parse loi, gay warning `Python-dotenv could not parse statement...`.

---

## 12. Luu y git va artifact

- Khong revert user changes neu working tree dirty.
- Khong revert `sandbox/` hoac `ai_service/cache/problems/` neu khong duoc yeu cau ro.
- Cache files duoi `ai_service/cache/problems/` co the bi modified/deleted do run app.


