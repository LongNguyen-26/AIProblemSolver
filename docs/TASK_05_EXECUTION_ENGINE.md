# TASK_05_EXECUTION_ENGINE.md

## Mục tiêu
Xây dựng `ExecutionService` — biên dịch và chạy source code trong sandbox, so sánh output với expected, trả về verdict.

---

## 1. Hỗ trợ ngôn ngữ

| Language | Compile | Run |
|---|---|---|
| `cpp` | `g++ -o sandbox/prog sandbox/Main.cpp -O2` | `sandbox/prog` |
| `java` | `javac -d sandbox sandbox/Main.java` | `java -cp sandbox Main` |
| `python` | (không compile) | `python3 sandbox/Main.py` |

---

## 2. `ExecutionService.java`

```java
package org.example.service;

import org.example.model.TestCase;
import org.example.util.AppConfig;
import org.example.util.FileUtil;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class ExecutionService {

    private static final int TIMEOUT_SEC = AppConfig.getInt("execution.timeoutSeconds", 5);
    private static final String SANDBOX = AppConfig.get("execution.sandboxPath");

    /**
     * Chạy source code trên một test case, cập nhật verdict vào TestCase object.
     */
    public void runTestCase(String sourceCode, String language, TestCase tc)
            throws Exception {

        FileUtil.ensureDir(SANDBOX);

        // 1. Ghi source code ra file
        String srcFile = writeSourceFile(sourceCode, language);

        // 2. Compile (nếu cần)
        CompileResult compileResult = compile(language, srcFile);
        if (!compileResult.success) {
            tc.setVerdict("CE");
            tc.setActualOutput(compileResult.stderr);
            return;
        }

        // 3. Chạy với input
        RunResult runResult = run(language, tc.getInput());

        // 4. Gán kết quả
        tc.setExecutionTimeMs(runResult.timeMs);
        tc.setActualOutput(runResult.stdout);

        if (runResult.timedOut) {
            tc.setVerdict("TLE");
        } else if (runResult.exitCode != 0) {
            tc.setVerdict("RE");
        } else {
            boolean correct = outputMatches(tc.getExpectedOutput(), runResult.stdout);
            tc.setVerdict(correct ? "AC" : "WA");
        }
    }

    // ---- Private helpers ----

    private String writeSourceFile(String code, String language) throws IOException {
        String filename = switch (language.toLowerCase()) {
            case "cpp"    -> "Main.cpp";
            case "java"   -> "Main.java";
            case "python" -> "Main.py";
            default -> throw new IllegalArgumentException("Unsupported: " + language);
        };
        String path = SANDBOX + "/" + filename;
        FileUtil.writeString(path, code);
        return path;
    }

    private CompileResult compile(String language, String srcFile) throws Exception {
        String[] cmd = switch (language.toLowerCase()) {
            case "cpp"  -> new String[]{"g++", srcFile, "-o", SANDBOX + "/prog", "-O2"};
            case "java" -> new String[]{"javac", "-d", SANDBOX, srcFile};
            case "python" -> null; // no compile
            default -> null;
        };

        if (cmd == null) return new CompileResult(true, "");

        ProcessResult result = runProcess(cmd, null, 30);
        return new CompileResult(result.exitCode == 0, result.stderr);
    }

    private RunResult run(String language, String input) throws Exception {
        String[] cmd = switch (language.toLowerCase()) {
            case "cpp"    -> new String[]{SANDBOX + "/prog"};
            case "java"   -> new String[]{"java", "-cp", SANDBOX, "Main"};
            case "python" -> new String[]{"python3", SANDBOX + "/Main.py"};
            default -> throw new IllegalArgumentException("Unsupported: " + language);
        };

        long start = System.currentTimeMillis();
        ProcessResult result = runProcess(cmd, input, TIMEOUT_SEC);
        long elapsed = System.currentTimeMillis() - start;

        return new RunResult(result.stdout, result.stderr, result.exitCode,
                             elapsed, result.timedOut);
    }

    /**
     * Chạy process với timeout, stdin, trả về stdout/stderr/exitCode.
     */
    private ProcessResult runProcess(String[] cmd, String stdinData, int timeoutSec)
            throws Exception {

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(SANDBOX));
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // Ghi stdin
        if (stdinData != null) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(stdinData.getBytes());
                os.flush();
            }
        }

        // Đọc stdout + stderr song song để tránh deadlock
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Future<String> stdoutFuture = exec.submit(
            () -> new String(process.getInputStream().readAllBytes())
        );
        Future<String> stderrFuture = exec.submit(
            () -> new String(process.getErrorStream().readAllBytes())
        );

        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            exec.shutdownNow();
            return new ProcessResult("", "Time limit exceeded", -1, true);
        }

        String stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
        String stderr = stderrFuture.get(2, TimeUnit.SECONDS);
        exec.shutdown();

        return new ProcessResult(stdout, stderr, process.exitValue(), false);
    }

    private boolean outputMatches(String expected, String actual) {
        // Normalize: trim trailing whitespace mỗi dòng, bỏ trailing newline
        String norm1 = normalizeOutput(expected);
        String norm2 = normalizeOutput(actual);
        return norm1.equals(norm2);
    }

    private String normalizeOutput(String s) {
        if (s == null) return "";
        return s.lines()
                .map(String::stripTrailing)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("")
                .stripTrailing();
    }

    // ---- Inner result classes ----

    private record CompileResult(boolean success, String stderr) {}

    private record RunResult(
        String stdout, String stderr, int exitCode, long timeMs, boolean timedOut
    ) {}

    private record ProcessResult(
        String stdout, String stderr, int exitCode, boolean timedOut
    ) {}
}
```

---

## 3. `TestCaseController.java` — kết hợp với ExecutionService

```java
package org.example.ui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.model.TestCase;
import org.example.service.TestCaseService;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class TestCaseController implements Initializable {

    @FXML private TableView<TestCase> testCaseTable;
    @FXML private TableColumn<TestCase, String> idCol, inputCol, expectedCol,
                                                 descCol, verdictCol;
    @FXML private TableColumn<TestCase, Boolean> edgeCol;
    @FXML private TableColumn<TestCase, Long>    timeCol;
    @FXML private TextArea detailInputArea, detailExpectedArea, detailActualArea;

    private final ObservableList<TestCase> testCaseData = FXCollections.observableArrayList();
    private final TestCaseService testCaseService = new TestCaseService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        inputCol.setCellValueFactory(new PropertyValueFactory<>("input"));
        expectedCol.setCellValueFactory(new PropertyValueFactory<>("expectedOutput"));
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        edgeCol.setCellValueFactory(new PropertyValueFactory<>("edgeCase"));
        verdictCol.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        timeCol.setCellValueFactory(new PropertyValueFactory<>("executionTimeMs"));

        testCaseTable.setItems(testCaseData);

        // Verdict color coding
        verdictCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String verdict, boolean empty) {
                super.updateItem(verdict, empty);
                if (empty || verdict == null) { setText(null); setStyle(""); return; }
                setText(verdict);
                setStyle(switch (verdict) {
                    case "AC"  -> "-fx-text-fill: #a6e3a1; -fx-font-weight: bold;";
                    case "WA"  -> "-fx-text-fill: #f38ba8; -fx-font-weight: bold;";
                    case "TLE" -> "-fx-text-fill: #fab387; -fx-font-weight: bold;";
                    case "CE"  -> "-fx-text-fill: #cba6f7; -fx-font-weight: bold;";
                    default    -> "-fx-text-fill: #a6adc8;";
                });
            }
        });

        // Detail panel on selection
        testCaseTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, o, tc) -> {
                if (tc != null) {
                    detailInputArea.setText(tc.getInput());
                    detailExpectedArea.setText(tc.getExpectedOutput());
                    detailActualArea.setText(tc.getActualOutput() != null
                        ? tc.getActualOutput() : "—");
                }
            }
        );
    }

    public void setTestCases(List<TestCase> list) {
        testCaseData.setAll(list);
    }

    public List<TestCase> getTestCases() {
        return testCaseData;
    }

    public void refreshTable() {
        testCaseTable.refresh();
    }

    @FXML
    private void onAddManual() {
        // Dialog thêm test case thủ công
        Dialog<TestCase> dialog = new Dialog<>();
        dialog.setTitle("Thêm test case");
        // TODO: form fields
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.showAndWait();
    }

    @FXML
    private void onDeleteSelected() {
        TestCase selected = testCaseTable.getSelectionModel().getSelectedItem();
        if (selected != null) testCaseData.remove(selected);
    }

    @FXML
    private void onExportZip() {
        // TODO: export test cases to ZIP (TASK_06)
    }
}
```

---

## 4. `ResultController.java`

```java
package org.example.ui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.model.*;
import org.example.service.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class ResultController implements Initializable {

    @FXML private ComboBox<String> codeTypeCombo, languageCombo;
    @FXML private TextArea codeArea, explanationArea;
    @FXML private TableView<TestCase> resultTable;
    @FXML private TableColumn<TestCase,String> rTcIdCol, rVerdictCol, rDiffCol;
    @FXML private TableColumn<TestCase,Long>   rTimeCol;
    @FXML private Label summaryLabel;

    private final AIBridgeService aiService = new AIBridgeService();
    private final ExecutionService execService = new ExecutionService();
    private final ObservableList<TestCase> resultData = FXCollections.observableArrayList();

    private Problem currentProblem;
    private List<TestCase> currentTestCases;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        codeTypeCombo.setItems(FXCollections.observableArrayList("AC", "WA", "TLE"));
        codeTypeCombo.setValue("AC");
        languageCombo.setItems(FXCollections.observableArrayList("cpp", "python", "java"));
        languageCombo.setValue("cpp");

        rTcIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        rVerdictCol.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        rTimeCol.setCellValueFactory(new PropertyValueFactory<>("executionTimeMs"));
        resultTable.setItems(resultData);
    }

    public void setProblem(Problem p) { this.currentProblem = p; }
    public void setTestCases(List<TestCase> tc) { this.currentTestCases = tc; }

    @FXML
    private void onGenerateCode() {
        if (currentProblem == null) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                return aiService.generateCode(
                    currentProblem,
                    codeTypeCombo.getValue(),
                    languageCombo.getValue()
                );
            } catch (Exception e) { throw new RuntimeException(e); }
        }).thenAcceptAsync(sub -> {
            codeArea.setText(sub.getCode());
            explanationArea.setText(sub.getExplanation());
        }, Platform::runLater);
    }

    @FXML
    private void onRunAllTests() {
        String code = codeArea.getText();
        String lang = languageCombo.getValue();
        if (code.isBlank() || currentTestCases == null) return;

        resultData.setAll(currentTestCases);

        CompletableFuture.runAsync(() -> {
            for (TestCase tc : currentTestCases) {
                try {
                    execService.runTestCase(code, lang, tc);
                } catch (Exception e) {
                    tc.setVerdict("RE");
                    tc.setActualOutput(e.getMessage());
                }
                Platform.runLater(() -> resultTable.refresh());
            }
            Platform.runLater(this::updateSummary);
        });
    }

    private void updateSummary() {
        long ac  = currentTestCases.stream().filter(t -> "AC".equals(t.getVerdict())).count();
        long total = currentTestCases.size();
        summaryLabel.setText(ac + "/" + total + " AC");
        summaryLabel.setStyle(ac == total
            ? "-fx-text-fill: #a6e3a1; -fx-font-weight: bold;"
            : "-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
    }

    @FXML
    private void onExportReport() {
        // Delegate to ReportService (TASK_06)
    }
}
```

---

## 5. Sandbox cleanup

Thêm vào `MainApp.java` trong `stop()`:

```java
@Override
public void stop() throws Exception {
    // Dọn sandbox khi đóng app
    Path sandbox = Paths.get(AppConfig.get("execution.sandboxPath"));
    if (Files.exists(sandbox)) {
        Files.walk(sandbox)
            .filter(p -> !p.equals(sandbox))
            .map(Path::toFile)
            .forEach(File::delete);
    }
    super.stop();
}
```

---

## Checklist

- [ ] `ExecutionService.java` tạo xong
- [ ] Test thủ công: C++ Hello World compile + chạy được
- [ ] Test thủ công: Python script chạy được
- [ ] Timeout 5s hoạt động (thử với `while True: pass`)
- [ ] `TestCaseController` color-code verdict đúng
- [ ] `ResultController.onRunAllTests()` chạy async, update UI từng TC
- [ ] Sandbox cleanup khi đóng app
