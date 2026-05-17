# UPGRADE_ROADMAP_V3.2 – F2: KILLER Testcase Qua Script Thay Vì Dữ Liệu Trực Tiếp

Ngày tạo: 2026-05-17
Phụ thuộc: README_V3.1.md (snapshot hiện tại)

---

## 1. Vấn Đề Gốc

Khi AI sinh KILLER testcase có `N = 100 000`, nó cố in thẳng 100 000 con số vào
JSON response. Với model giới hạn 4 096–8 192 output tokens, chuỗi bị cắt ngang
→ JSON parse lỗi → luồng bị văng → log `0 KILLER`.

---

## 2. Giải Pháp: Script-Based Generation

Thay vì yêu cầu AI **sinh dữ liệu**, yêu cầu AI **viết script Python** sinh dữ liệu.

```
AI viết script Python nhỏ (~10-20 dòng)
  -> Java nhận script text (vài trăm tokens, không bao giờ bị cắt)
  -> Java chạy script qua ProcessBuilder
  -> Script ghi output ra stdout hoặc file tạm
  -> Java đọc output, nhét vào TestCase.input
```

Script Python được chọn vì Python đã có sẵn trong `.venv` của project.

---

## 3. Thay Đổi Python – `ai_service`

### 3.1 `ai_service/models/schemas.py`

Thêm field vào `TestCaseSchema`:

```python
generator_script: Optional[str] = None   # Python script để sinh input
```

Thêm schema mới cho request sinh KILLER bằng script:

```python
class KillerScriptRequest(BaseModel):
    problem: ProblemSchema
    count: int = 1
```

### 3.2 `ai_service/services/testcase_generator.py`

#### Thêm hàm `generate_killer_script(...)`

```python
KILLER_SCRIPT_SYSTEM_PROMPT = """Bạn là chuyên gia tạo test data cho competitive programming.
Nhiệm vụ: viết một script Python ngắn gọn, standalone, in ra STDIN một testcase KILLER (dữ liệu lớn).

Quy tắc NGHIÊM NGẶT:
- Script phải chạy được với python3, không import thư viện ngoài (chỉ random, sys, os).
- Script in dữ liệu ra stdout (print/sys.stdout.write). KHÔNG ghi file.
- Script không được có input() hay sys.stdin.
- Script phải kết thúc trong vòng 5 giây.
- Chỉ trả về code Python thuần túy, KHÔNG có markdown fence, KHÔNG giải thích.
- Comment đầu file: # KILLER: <mô tả ngắn chiến lược>
"""

async def generate_killer_script(problem: ProblemSchema, groq_client) -> Optional[str]:
    """
    Sinh một Python script để tạo KILLER testcase.
    Trả về source code Python dạng string, hoặc None nếu lỗi.
    """
    constraints_summary = _summarize_constraints(problem)
    
    user_prompt = f"""Bài toán: {problem.title or 'Competitive programming problem'}

Mô tả ngắn: {(problem.description or '')[:300]}

Constraints: {constraints_summary}

Input format: {(problem.input_format or '')[:200]}

Hãy viết một Python script in ra một testcase KILLER theo đúng input format trên.
Chiến lược gợi ý: dùng N lớn nhất, giá trị cực biên (max/min), hoặc pattern gây TLE cho thuật toán O(N^2).
"""
    try:
        response = await groq_client.complete_text(
            system=KILLER_SCRIPT_SYSTEM_PROMPT,
            user=user_prompt,
            max_tokens=800,   # Script ngắn, 800 token là đủ
            temperature=0.3,
        )
        script = response.strip()
        # Bóc markdown fence nếu AI vẫn bọc
        if script.startswith("```"):
            lines = script.splitlines()
            script = "\n".join(
                l for l in lines if not l.startswith("```")
            ).strip()
        return script if script else None
    except Exception as e:
        logger.warning(f"generate_killer_script failed: {e}")
        return None


def _summarize_constraints(problem: ProblemSchema) -> str:
    """Trích xuất constraints ngắn gọn để đưa vào prompt."""
    constraints = problem.constraints or ""
    # Giới hạn 300 ký tự để tiết kiệm token
    return constraints[:300] + ("..." if len(constraints) > 300 else "")
```

#### Sửa hàm sinh KILLER trong pipeline

Trong `generate_testcases(...)` hoặc hàm tương đương xử lý `profile="KILLER"`,
bọc bằng **try script-first, fallback direct**:

```python
async def _generate_single_killer(problem, groq_client, existing_inputs) -> Optional[TestCase]:
    # Bước 1: thử sinh qua script
    script = await generate_killer_script(problem, groq_client)
    if script:
        return TestCase(
            id=f"tc_{uuid4().hex[:8]}",
            input="",                      # Java sẽ fill sau khi chạy script
            expected_output="",
            description="[KILLER] Script-generated large input",
            is_edge_case=False,
            generator_script=script,       # <-- field mới
        )
    
    # Bước 2: fallback về cách cũ (sinh JSON trực tiếp)
    logger.warning("KILLER script generation failed, falling back to direct generation")
    return await _generate_killer_direct(problem, groq_client, existing_inputs)
```

### 3.3 `ai_service/routers/testcase.py`

Thêm endpoint mới (tùy chọn, để Java gọi trực tiếp khi cần):

```python
@router.post("/testcase/killer_script")
async def get_killer_script(req: KillerScriptRequest):
    """Trả về Python script để sinh KILLER input, thay vì trả về dữ liệu trực tiếp."""
    scripts = []
    for _ in range(req.count):
        script = await generate_killer_script(req.problem, groq_client)
        if script:
            scripts.append(script)
    return {"scripts": scripts}
```

---

## 4. Thay Đổi Java

### 4.1 `src/main/java/org/example/model/TestCase.java`

Thêm field:

```java
@SerializedName("generator_script")
private String generatorScript;

public String getGeneratorScript() { return generatorScript; }
public void setGeneratorScript(String s) { this.generatorScript = s; }
public boolean hasGeneratorScript() {
    return generatorScript != null && !generatorScript.isBlank();
}
```

### 4.2 `src/main/java/org/example/service/ScriptRunner.java` (file mới)

```java
package org.example.service;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * Chạy một Python script tạm thời và trả về stdout.
 * Dùng Python từ venv của ai_service.
 */
public class ScriptRunner {

    private static final String PYTHON_EXEC =
        "C:\\Code_Storage\\AIProblemSolver\\ai_service\\.venv\\Scripts\\python.exe";
    private static final int TIMEOUT_SECONDS = 10;

    /**
     * Chạy scriptContent bằng Python, trả về stdout dạng String.
     * Ném IOException nếu script lỗi hoặc timeout.
     */
    public static String runPythonScript(String scriptContent) throws IOException {
        // Ghi script ra file tạm
        Path tmpScript = Files.createTempFile("killer_gen_", ".py");
        try {
            Files.writeString(tmpScript, scriptContent);

            ProcessBuilder pb = new ProcessBuilder(PYTHON_EXEC, tmpScript.toString());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Đọc stdout
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Script timed out after " + TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                // Đọc stderr để debug
                String stderr = new String(process.getErrorStream().readAllBytes());
                throw new IOException("Script exited with code " + process.exitValue() + ": " + stderr);
            }

            return output.toString();
        } finally {
            Files.deleteIfExists(tmpScript);  // Dọn file tạm
        }
    }
}
```

**Lưu ý:** Nếu muốn `PYTHON_EXEC` configurable, đọc từ một constant class hoặc
properties file thay vì hardcode.

### 4.3 `src/main/java/org/example/ui/controller/MainController.java`

Sau khi nhận testcases từ pipeline (event `DONE`), thêm bước **materialize scripts**:

```java
/**
 * Với các testcase có generatorScript, chạy script Python để lấy input thực.
 * Gọi trên background thread (đã có sẵn trong pipeline flow).
 */
private void materializeScriptTestCases(List<TestCase> testCases) {
    for (TestCase tc : testCases) {
        if (!tc.hasGeneratorScript()) continue;
        try {
            String generatedInput = ScriptRunner.runPythonScript(tc.getGeneratorScript());
            if (generatedInput != null && !generatedInput.isBlank()) {
                tc.setInput(generatedInput.stripTrailing());
                tc.setGeneratorScript(null); // Script đã dùng xong, clear
                logger.info("Materialized KILLER testcase {} via script ({} chars)",
                    tc.getId(), generatedInput.length());
            } else {
                logger.warning("Script produced empty output for {}", tc.getId());
                // Giữ testcase nhưng đánh dấu
                tc.setDescription(tc.getDescription() + " [script-empty]");
            }
        } catch (IOException e) {
            logger.warning("Script failed for {}: {}", tc.getId(), e.getMessage());
            // Không xóa testcase, chỉ flag lỗi
            tc.setDescription(tc.getDescription() + " [script-error]");
        }
    }
}
```

Gọi `materializeScriptTestCases(allTestCases)` **trước khi** tính expected output
(trước khi gọi oracle), trong luồng xử lý event `DONE`.

---

## 5. Luồng Hoàn Chỉnh Sau Upgrade

```
Pipeline DONE event (testcases có thể có generator_script)
  -> Java materializeScriptTestCases(testcases)
     -> tc.hasGeneratorScript() = true
        -> ScriptRunner.runPythonScript(script)
        -> tc.setInput(generatedInput)
        -> tc.setGeneratorScript(null)
     -> tc.hasGeneratorScript() = false -> bỏ qua
  -> Java tiếp tục chạy oracle tính expected output như bình thường
  -> UI hiển thị KILLER testcase với dữ liệu thật
```

---

## 6. Xử Lý Lỗi & Failsafe

| Tình huống | Xử Lý |
|---|---|
| Script timeout (> 10s) | Log warning, đánh dấu `[script-timeout]`, tiếp tục |
| Script exit code ≠ 0 | Log warning + stderr, đánh dấu `[script-error]` |
| Script sinh empty output | Đánh dấu `[script-empty]`, giữ testcase |
| Python exec không tìm thấy | IOException → log error, testcase bị skip |
| AI không sinh được script | Fallback về direct generation (cách cũ) |

Không crash app trong mọi trường hợp. KILLER count có thể thấp hơn target
nhưng app vẫn tiếp tục.

---

## 7. Verify

```powershell
# Python compile
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service

# Java compile
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Test thủ công:

1. Nhập bài toán có `N ≤ 100 000` (ví dụ bài sắp xếp, đồ thị).
2. Chạy pipeline, kiểm tra log: nên thấy `"Materialized KILLER testcase tc_xxx via script"`.
3. Vào bảng testcase, KILLER row có input với hàng nghìn số.
4. Status bar vẫn hiện `Z KILLER` đúng số lượng.

Smoke check script runner riêng:

```java
// Trong main() tạm thời hoặc unit test
String script = "import random\nn=5\nprint(n)\nprint(*[random.randint(1,10) for _ in range(n)])";
String output = ScriptRunner.runPythonScript(script);
System.out.println(output); // Phải in ra 2 dòng số
```

---

## 8. Lưu Ý

- Script chỉ được dùng `random`, `sys`, `os`, `math` — các thư viện stdlib Python.
  Không cần cài thêm gì.
- File tạm được xóa ngay sau khi chạy (trong `finally` block).
- `PYTHON_EXEC` path nên extract thành constant hoặc config để dễ thay đổi khi
  deploy trên máy khác.
- Pipeline cache hiện tại không lưu `generator_script` (field được clear sau khi
  materialize), phù hợp với nguyên tắc "cache không lưu expected output Java tính".
