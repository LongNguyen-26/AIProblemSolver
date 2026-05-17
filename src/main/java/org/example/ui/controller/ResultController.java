package org.example.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.example.model.AnalysisReport;
import org.example.model.CodeSubmission;
import org.example.model.ComplexityInfo;
import org.example.model.Problem;
import org.example.model.TestCase;
import org.example.service.AIBridgeService;
import org.example.service.ExecutionService;
import org.example.service.ReportService;
import org.example.ui.util.AlertUtil;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class ResultController implements Initializable {
    private static final int TLE_GENERATION_ATTEMPTS = 3;
    private static final int MAX_TLE_VALIDATION_CASES = 3;
    private static final int N_SMALL_THRESHOLD = 1000;
    private static final int MAX_WA_RETRIES = 3;
    private static final int MAX_WA_VALIDATION_CASES = 5;
    private static final int MAX_WA_PROMPT_CASES = 3;
    private static final String TLE_DISABLED_MESSAGE =
            "Bai toan co gioi han du lieu nho, khong yeu cau kiem thu hieu nang (TLE).";

    @FXML private ComboBox<String> codeTypeCombo;
    @FXML private ComboBox<String> languageCombo;
    @FXML private TextArea codeArea;
    @FXML private Label summaryLabel;
    @FXML private TableView<TestCase> resultTable;
    @FXML private TableColumn<TestCase, String> rTcIdCol;
    @FXML private TableColumn<TestCase, String> rVerdictCol;
    @FXML private TableColumn<TestCase, Long> rTimeCol;
    @FXML private TableColumn<TestCase, String> rDiffCol;
    @FXML private TextArea explanationArea;
    @FXML private Button generateCodeBtn;
    @FXML private Button runAllTestsBtn;

    private final ObservableList<TestCase> testCases = FXCollections.observableArrayList();
    private final ExecutionService executionService = new ExecutionService();
    private final ReportService reportService = new ReportService();
    private AIBridgeService aiService = new AIBridgeService();
    private Problem problem;
    private CodeSubmission preferredAcSubmission;
    private boolean codeGenerationInProgress;
    private boolean testRunInProgress;
    private boolean externalBusy;
    private Runnable resultUpdateListener = () -> {
    };

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        codeTypeCombo.getItems().setAll("AC", "WA", "TLE");
        codeTypeCombo.getSelectionModel().select("AC");
        configureCodeTypeCombo();
        languageCombo.getItems().setAll("cpp", "python", "java");
        languageCombo.getSelectionModel().select("cpp");

        resultTable.setItems(testCases);
        resultTable.setFixedCellSize(34.0);
        resultTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        rTcIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        rVerdictCol.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        rTimeCol.setCellValueFactory(new PropertyValueFactory<>("executionTimeMs"));
        rDiffCol.setCellValueFactory(features ->
                new ReadOnlyStringWrapper(diffPreview(features.getValue()))
        );
        rVerdictCol.setCellFactory(column -> verdictCell());
        rDiffCol.setCellFactory(column -> diffCell());
    }

    public void setAiService(AIBridgeService aiService) {
        if (aiService != null) {
            this.aiService = aiService;
        }
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
        this.preferredAcSubmission = null;
        updateTleOptionState();
    }

    public void setPreferredAcSubmission(CodeSubmission submission) {
        this.preferredAcSubmission = submission;
    }

    public void setTestCases(List<TestCase> values) {
        testCases.setAll(values == null ? List.of() : values);
        summaryLabel.setText(testCases.isEmpty() ? "-" : testCases.size() + " test cases");
        summaryLabel.setStyle("");
    }

    public void setResultUpdateListener(Runnable resultUpdateListener) {
        this.resultUpdateListener = resultUpdateListener == null ? () -> {
        } : resultUpdateListener;
    }

    public void setExternalBusy(boolean busy) {
        externalBusy = busy;
        updateActionButtons();
    }

    @FXML
    private void onGenerateCode() {
        if (codeGenerationInProgress) {
            return;
        }
        if (problem == null) {
            AlertUtil.showWarning(codeArea.getScene().getWindow(), "Hay phan tich de bai truoc.");
            return;
        }

        String codeType = codeTypeCombo.getValue();
        String language = languageCombo.getValue();
        if ("TLE".equals(codeType) && isTleIrrelevant(problem)) {
            showTleUnavailable();
            return;
        }
        if ("AC".equals(codeType) && canUsePreferredAc(language)) {
            displaySubmission(preferredAcSubmission);
            summaryLabel.setText("Da dung cached AC oracle");
            summaryLabel.setStyle("");
            return;
        }

        setCodeGenerationRunning(true);
        summaryLabel.setText("Dang sinh code...");

        CompletableFuture.supplyAsync(() -> {
            try {
                return generateCodeWithValidation(codeType, language);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenCompleteAsync((submission, error) -> {
            try {
                if (error != null) {
                    String message = rootCauseMessage(error);
                    summaryLabel.setText("Sinh code that bai, thu lai");
                    summaryLabel.setStyle("-fx-text-fill: #e5b567; -fx-font-weight: bold;");
                    explanationArea.setText(message);
                    return;
                }
                displaySubmission(submission);
            } finally {
                setCodeGenerationRunning(false);
            }
        }, Platform::runLater);
    }

    private CodeSubmission generateCodeWithValidation(String codeType, String language)
            throws Exception {
        if ("TLE".equals(codeType) && isTleIrrelevant(problem)) {
            throw new IllegalStateException(TLE_DISABLED_MESSAGE);
        }
        if ("WA".equals(codeType)) {
            return generateWaCodeWithValidation(language);
        }
        if (!"TLE".equals(codeType)) {
            return aiService.generateCode(problem, codeType, language);
        }

        List<TestCase> validationCases = tleValidationCases();
        if (validationCases.isEmpty()) {
            return aiService.generateCode(problem, codeType, language);
        }

        String lastFailure = "";
        List<TestCase> promptCases = promptValidationCases(validationCases);
        ComplexityInfo complexityInfo = analyzeComplexityForTle();
        for (int attempt = 1; attempt <= TLE_GENERATION_ATTEMPTS; attempt++) {
            setSummaryAsync("Dang sinh code TLE lan " + attempt + "...");
            CodeSubmission submission = aiService.generateCode(
                    problem,
                    codeType,
                    language,
                    promptCases,
                    complexityInfo,
                    lastFailure
            );
            setSummaryAsync("Dang validate code TLE lan " + attempt + "...");
            TleValidationResult validation = validateTleSubmission(
                    submission,
                    language,
                    validationCases
            );
            if (validation.valid()) {
                submission.setExplanation(
                        valueOrEmpty(submission.getExplanation())
                                + "\n\nLocal validation: "
                                + validation.message()
                                + "\nComplexity target: "
                                + valueOrEmpty(complexityInfo.getTleTargetComplexity())
                                + " via "
                                + valueOrEmpty(complexityInfo.getTleStrategy())
                );
                return submission;
            }
            lastFailure = validation.message();
        }

        throw new RuntimeException(
                "AI chua tao duoc code TLE dung sau "
                        + TLE_GENERATION_ATTEMPTS
                        + " lan. Ly do gan nhat: "
                        + lastFailure
        );
    }

    private CodeSubmission generateWaCodeWithValidation(String language) throws Exception {
        List<TestCase> validationCases = waValidationCases();
        List<TestCase> promptCases = waPromptCases(validationCases);
        if (validationCases.isEmpty()) {
            CodeSubmission submission = aiService.generateCode(
                    problem,
                    "WA",
                    language,
                    promptCases,
                    null,
                    ""
            );
            appendSubmissionExplanation(
                    submission,
                    "Local WA validation skipped: no testcase with usable expected output."
            );
            return submission;
        }

        CodeSubmission lastSubmission = null;
        WaValidationResult lastValidation = new WaValidationResult(
                false,
                "WA validation has not run.",
                List.of()
        );
        String feedback = "";

        for (int retry = 0; retry <= MAX_WA_RETRIES; retry++) {
            CodeSubmission submission;
            try {
                if (retry == 0) {
                    setSummaryAsync("Dang sinh code WA...");
                    submission = aiService.generateCode(
                            problem,
                            "WA",
                            language,
                            promptCases,
                            null,
                            feedback
                    );
                } else {
                    setSummaryAsync("WA retry lan " + retry + "/" + MAX_WA_RETRIES + "...");
                    submission = aiService.retryWACode(
                            problem,
                            language,
                            feedback,
                            promptCases
                    );
                }
            } catch (Exception e) {
                if (hasSource(lastSubmission)) {
                    appendSubmissionExplanation(
                            lastSubmission,
                            "Local WA retry stopped: " + rootCauseMessage(e)
                    );
                    return lastSubmission;
                }
                throw e;
            }

            lastSubmission = submission;
            setSummaryAsync("Dang validate code WA lan " + (retry + 1) + "...");
            WaValidationResult validation = validateWaSubmission(
                    submission,
                    language,
                    validationCases
            );
            lastValidation = validation;
            if (validation.valid()) {
                appendSubmissionExplanation(
                        submission,
                        "Local WA validation: " + validation.message()
                );
                return submission;
            }

            feedback = buildWaFeedback(validation);
        }

        if (hasSource(lastSubmission)) {
            appendSubmissionExplanation(
                    lastSubmission,
                    "Local WA validation warning: "
                            + lastValidation.message()
                            + "\nReturned after "
                            + MAX_WA_RETRIES
                            + " retries without a confirmed WA verdict."
            );
            return lastSubmission;
        }

        throw new RuntimeException(
                "AI chua tao duoc code WA sau "
                        + MAX_WA_RETRIES
                        + " retry. Ly do gan nhat: "
                        + lastValidation.message()
        );
    }

    private WaValidationResult validateWaSubmission(
            CodeSubmission submission,
            String requestedLanguage,
            List<TestCase> validationCases
    ) {
        if (!hasSource(submission)) {
            return new WaValidationResult(false, "Code WA rong.", List.of());
        }

        String language = valueOrEmpty(submission.getLanguage()).isBlank()
                ? requestedLanguage
                : submission.getLanguage();
        List<TestCase> probes = validationCases.stream()
                .map(this::copyForValidation)
                .toList();
        try {
            executionService.runTestCases(submission.getCode(), language, probes);
        } catch (Exception e) {
            return new WaValidationResult(false, rootCauseMessage(e), probes);
        }

        int wrongAnswer = 0;
        int accepted = 0;
        List<String> nonWaVerdicts = new ArrayList<>();
        for (TestCase probe : probes) {
            String verdict = valueOrEmpty(probe.getVerdict());
            if ("WA".equals(verdict)) {
                wrongAnswer++;
            } else if ("AC".equals(verdict)) {
                accepted++;
            } else {
                nonWaVerdicts.add(
                        valueOrEmpty(probe.getId())
                                + "="
                                + (verdict.isBlank() ? "UNKNOWN" : verdict)
                );
            }
        }

        if (wrongAnswer > 0) {
            return new WaValidationResult(
                    true,
                    "WA candidate hop le: "
                            + wrongAnswer
                            + "/"
                            + probes.size()
                            + " validation cases ra WA.",
                    probes
            );
        }
        if (accepted == probes.size()) {
            return new WaValidationResult(
                    false,
                    "WA code pass het "
                            + probes.size()
                            + " validation cases.",
                    probes
            );
        }
        return new WaValidationResult(
                false,
                "WA code khong tao WRONG ANSWER; verdict khac: "
                        + String.join(", ", nonWaVerdicts),
                probes
        );
    }

    private List<TestCase> waValidationCases() {
        List<TestCase> available = testCases.stream()
                .filter(this::hasUsableExpectedOutput)
                .toList();
        List<TestCase> selected = new ArrayList<>();
        for (TestCase testCase : available) {
            if (selected.size() >= MAX_WA_VALIDATION_CASES) {
                break;
            }
            if (isSmallCase(testCase)) {
                selected.add(testCase);
            }
        }
        for (TestCase testCase : available) {
            if (selected.size() >= MAX_WA_VALIDATION_CASES) {
                break;
            }
            if (!selected.contains(testCase)) {
                selected.add(testCase);
            }
        }
        return List.copyOf(selected);
    }

    private List<TestCase> waPromptCases(List<TestCase> validationCases) {
        return validationCases.stream()
                .filter(testCase -> valueOrEmpty(testCase.getInput()).length() <= 4000)
                .filter(testCase -> valueOrEmpty(testCase.getExpectedOutput()).length() <= 4000)
                .limit(MAX_WA_PROMPT_CASES)
                .toList();
    }

    private boolean hasUsableExpectedOutput(TestCase testCase) {
        if (testCase == null
                || testCase.getInput() == null
                || testCase.getInput().isBlank()) {
            return false;
        }
        String expected = valueOrEmpty(testCase.getExpectedOutput()).strip();
        return !expected.isBlank() && !"N/A".equalsIgnoreCase(expected);
    }

    private String buildWaFeedback(WaValidationResult validation) {
        StringBuilder builder = new StringBuilder();
        builder.append("The previous WA code did not produce a WRONG ANSWER verdict. ");
        builder.append("It must compile, run, and print a wrong output on at least one case.\n");
        builder.append("Local validation result: ")
                .append(validation == null ? "unknown" : validation.message())
                .append("\n\n");

        List<TestCase> probes = validation == null ? List.of() : validation.probes();
        for (int i = 0; i < Math.min(MAX_WA_PROMPT_CASES, probes.size()); i++) {
            TestCase probe = probes.get(i);
            builder.append("Case ")
                    .append(i + 1)
                    .append(":\nInput:\n")
                    .append(truncateForPrompt(probe.getInput(), 1200))
                    .append("\nExpected output:\n")
                    .append(truncateForPrompt(probe.getExpectedOutput(), 600))
                    .append("\nPrevious actual output:\n")
                    .append(truncateForPrompt(probe.getActualOutput(), 600))
                    .append("\n\n");
        }
        builder.append("Pick exactly one subtle bug that fails one of these cases with WA.");
        return builder.toString();
    }

    private TleValidationResult validateTleSubmission(
            CodeSubmission submission,
            String requestedLanguage,
            List<TestCase> validationCases
    ) {
        if (submission == null || submission.getCode() == null
                || submission.getCode().isBlank()) {
            return new TleValidationResult(false, "Code TLE rong.");
        }

        String language = valueOrEmpty(submission.getLanguage()).isBlank()
                ? requestedLanguage
                : submission.getLanguage();
        TleValidationResult sourceCheck = validateTleSourceShape(submission.getCode());
        if (!sourceCheck.valid()) {
            return sourceCheck;
        }

        List<TestCase> probes = validationCases.stream()
                .map(this::copyForValidation)
                .toList();
        try {
            executionService.runTestCases(submission.getCode(), language, probes);
        } catch (Exception e) {
            return new TleValidationResult(false, rootCauseMessage(e));
        }

        int accepted = 0;
        int timedOut = 0;
        for (TestCase probe : probes) {
            String verdict = valueOrEmpty(probe.getVerdict());
            if ("AC".equals(verdict)) {
                accepted++;
                continue;
            }
            if ("TLE".equals(verdict)) {
                timedOut++;
                continue;
            }
            return new TleValidationResult(
                    false,
                    "Case " + valueOrEmpty(probe.getId())
                            + " ra " + (verdict.isBlank() ? "UNKNOWN" : verdict)
                            + " thay vi AC/TLE"
                            + "\nInput:\n" + valueOrEmpty(probe.getInput()).strip()
                            + "\nExpected:\n" + valueOrEmpty(probe.getExpectedOutput()).strip()
                            + "\nActual:\n" + valueOrEmpty(probe.getActualOutput()).strip()
            );
        }

        return new TleValidationResult(
                true,
                "TLE candidate hop le tren " + probes.size()
                        + " cases (" + accepted + " AC, " + timedOut + " TLE)."
        );
    }

    private ComplexityInfo analyzeComplexityForTle() {
        try {
            setSummaryAsync("Dang phan tich complexity cho TLE...");
            ComplexityInfo info = aiService.analyzeComplexity(problem);
            return info == null ? defaultComplexityInfo() : info;
        } catch (Exception e) {
            return defaultComplexityInfo();
        }
    }

    private ComplexityInfo defaultComplexityInfo() {
        ComplexityInfo info = new ComplexityInfo();
        info.setOptimalComplexity("unknown");
        info.setTleTargetComplexity("O(n^2)");
        info.setMaxN(0);
        info.setTleStrategy("input_dependent_bruteforce");
        info.setTleExplanation(
                "Use a straightforward but correct algorithm whose runtime grows with input size."
        );
        return info;
    }

    private List<TestCase> tleValidationCases() {
        List<TestCase> available = testCases.stream()
                .filter(testCase -> testCase != null
                        && testCase.getInput() != null
                        && !testCase.getInput().isBlank()
                        && testCase.getExpectedOutput() != null
                        && !testCase.getExpectedOutput().isBlank())
                .toList();
        List<TestCase> small = available.stream()
                .filter(this::isSmallCase)
                .limit(MAX_TLE_VALIDATION_CASES)
                .toList();
        if (!small.isEmpty()) {
            return small;
        }
        return available.stream()
                .limit(MAX_TLE_VALIDATION_CASES)
                .toList();
    }

    private List<TestCase> promptValidationCases(List<TestCase> validationCases) {
        return validationCases.stream()
                .filter(testCase -> valueOrEmpty(testCase.getInput()).length() <= 4000)
                .filter(testCase -> valueOrEmpty(testCase.getExpectedOutput()).length() <= 4000)
                .limit(MAX_TLE_VALIDATION_CASES)
                .toList();
    }

    private boolean isSmallCase(TestCase testCase) {
        String description = valueOrEmpty(testCase.getDescription()).toUpperCase();
        return description.startsWith("[SMALL]") || description.contains("SMALL");
    }

    private TleValidationResult validateTleSourceShape(String code) {
        String source = valueOrEmpty(code);
        String lowered = source.toLowerCase();
        if (lowered.contains("sleep(")
                || lowered.contains("usleep")
                || lowered.contains("thread.sleep")
                || lowered.contains("this_thread::sleep")
                || lowered.contains("settimeout")) {
            return new TleValidationResult(false, "TLE code uses sleep/delay instead of a slow algorithm.");
        }
        if (lowered.contains("dummy") || lowered.contains("busy wait") || lowered.contains("busywait")) {
            return new TleValidationResult(false, "TLE code appears to use dummy busy work.");
        }
        if (lowered.contains("while(true)") || lowered.contains("while (true)")) {
            return new TleValidationResult(false, "TLE code contains an infinite loop.");
        }
        return new TleValidationResult(true, "Source shape accepted.");
    }

    private TestCase copyForValidation(TestCase source) {
        return new TestCase(
                source.getId(),
                source.getInput(),
                source.getExpectedOutput(),
                source.getDescription(),
                source.isEdgeCase()
        );
    }

    private boolean hasSource(CodeSubmission submission) {
        return submission != null
                && submission.getCode() != null
                && !submission.getCode().isBlank();
    }

    private void appendSubmissionExplanation(CodeSubmission submission, String addition) {
        if (submission == null || addition == null || addition.isBlank()) {
            return;
        }
        String existing = valueOrEmpty(submission.getExplanation()).strip();
        submission.setExplanation(existing.isBlank() ? addition : existing + "\n\n" + addition);
    }

    private String truncateForPrompt(String value, int maxLength) {
        String text = valueOrEmpty(value).strip();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength).stripTrailing() + "\n...[truncated]";
    }

    private void setSummaryAsync(String message) {
        Platform.runLater(() -> {
            summaryLabel.setText(message);
            summaryLabel.setStyle("");
        });
    }

    @FXML
    private void onRunAllTests() {
        if (testRunInProgress) {
            return;
        }
        String code = codeArea.getText();
        String language = languageCombo.getValue();
        if (code == null || code.isBlank()) {
            AlertUtil.showWarning(codeArea.getScene().getWindow(), "Hay nhap hoac sinh source code truoc.");
            return;
        }
        if (testCases.isEmpty()) {
            AlertUtil.showWarning(codeArea.getScene().getWindow(), "Chua co test case de chay.");
            return;
        }

        setTestRunRunning(true);
        summaryLabel.setText("Dang chay...");
        summaryLabel.setStyle("");
        testCases.forEach(testCase -> {
            testCase.setVerdict("");
            testCase.setActualOutput("");
            testCase.setExecutionTimeMs(0);
        });
        resultTable.refresh();
        resultUpdateListener.run();

        List<TestCase> snapshot = List.copyOf(testCases);
        CompletableFuture.runAsync(() -> {
            for (TestCase testCase : snapshot) {
                try {
                    executionService.runTestCase(code, language, testCase);
                } catch (Exception e) {
                    testCase.setVerdict("RE");
                    testCase.setActualOutput(rootCauseMessage(e));
                    testCase.setExecutionTimeMs(0);
                }
                Platform.runLater(() -> {
                    resultTable.refresh();
                    updateSummary();
                    resultUpdateListener.run();
                });
            }
        }).whenCompleteAsync((ignored, error) -> {
            try {
                if (error != null) {
                    summaryLabel.setText("Loi chay test");
                    AlertUtil.showError(codeArea.getScene().getWindow(), rootCauseMessage(error));
                } else {
                    resultTable.refresh();
                    updateSummary();
                    resultUpdateListener.run();
                }
            } finally {
                setTestRunRunning(false);
            }
        }, Platform::runLater);
    }

    private void setCodeGenerationRunning(boolean running) {
        codeGenerationInProgress = running;
        updateActionButtons();
    }

    private void setTestRunRunning(boolean running) {
        testRunInProgress = running;
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (generateCodeBtn != null) {
            generateCodeBtn.setDisable(externalBusy || codeGenerationInProgress);
            generateCodeBtn.setText(codeGenerationInProgress ? "Dang sinh..." : "Sinh code");
        }
        if (runAllTestsBtn != null) {
            runAllTestsBtn.setDisable(externalBusy || testRunInProgress);
            runAllTestsBtn.setText(testRunInProgress ? "Dang chay..." : "Chay tat ca test");
        }
    }

    private void configureCodeTypeCombo() {
        codeTypeCombo.setCellFactory(listView -> codeTypeCell());
        codeTypeCombo.setButtonCell(codeTypeCell());
        codeTypeCombo.valueProperty().addListener((ignored, oldValue, newValue) -> {
            if ("TLE".equals(newValue) && isTleIrrelevant(problem)) {
                String fallback = oldValue == null || "TLE".equals(oldValue) ? "AC" : oldValue;
                Platform.runLater(() -> codeTypeCombo.getSelectionModel().select(fallback));
                summaryLabel.setText("TLE khong kha dung cho bai nay");
                summaryLabel.setStyle("-fx-text-fill: #e5b567; -fx-font-weight: bold;");
            }
        });
    }

    private ListCell<String> codeTypeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setDisable(false);
                    setOpacity(1.0);
                    setTooltip(null);
                    setStyle("");
                    return;
                }

                boolean tleDisabled = "TLE".equals(item) && isTleIrrelevant(problem);
                setText(item);
                setDisable(tleDisabled);
                setOpacity(tleDisabled ? 0.45 : 1.0);
                setTooltip(tleDisabled ? tleDisabledTooltip() : null);
                setStyle(tleDisabled ? "-fx-text-fill: #8f8981;" : "");
            }
        };
    }

    private void updateTleOptionState() {
        boolean disabled = isTleIrrelevant(problem);
        if (codeTypeCombo != null) {
            codeTypeCombo.setTooltip(disabled ? tleDisabledTooltip() : null);
            if (disabled && "TLE".equals(codeTypeCombo.getValue())) {
                codeTypeCombo.getSelectionModel().select("AC");
            }
            codeTypeCombo.requestLayout();
        }
    }

    private boolean isTleIrrelevant(Problem problem) {
        if (problem == null) {
            return false;
        }
        if (problem.isSmallN()) {
            return true;
        }
        Integer maxN = problem.getMaxConstraintN();
        if (maxN != null && maxN < N_SMALL_THRESHOLD) {
            return true;
        }
        return isSmallNProblemType(problem.getProblemType());
    }

    private boolean isSmallNProblemType(String problemType) {
        String normalized = valueOrEmpty(problemType).trim().toUpperCase();
        return normalized.equals("MATH_FORMULA")
                || normalized.equals("STRING_BASIC")
                || normalized.equals("AD_HOC_SIMPLE");
    }

    private Tooltip tleDisabledTooltip() {
        Tooltip tooltip = new Tooltip(TLE_DISABLED_MESSAGE);
        tooltip.setShowDelay(Duration.millis(300));
        return tooltip;
    }

    private void showTleUnavailable() {
        summaryLabel.setText("TLE khong kha dung cho bai nay");
        summaryLabel.setStyle("-fx-text-fill: #e5b567; -fx-font-weight: bold;");
        AlertUtil.showInfo(codeArea.getScene().getWindow(), TLE_DISABLED_MESSAGE);
    }

    @FXML
    private void onExportReport() {
        if (problem == null || testCases.isEmpty()) {
            AlertUtil.showWarning(codeArea.getScene().getWindow(), "Chua co du lieu de xuat bao cao.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Luu bao cao HTML");
        fileChooser.setInitialFileName(reportFilename());
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML files", "*.html"));
        File destination = fileChooser.showSaveDialog(codeArea.getScene().getWindow());
        if (destination == null) {
            return;
        }

        try {
            AnalysisReport report = buildReport();
            String temporaryPath = reportService.exportHtmlReport(report);
            Files.move(
                    Paths.get(temporaryPath),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
            AlertUtil.showInfo(codeArea.getScene().getWindow(),
                    "Da luu bao cao: " + destination.getAbsolutePath());
        } catch (Exception e) {
            AlertUtil.showError(codeArea.getScene().getWindow(), "Loi xuat bao cao: " + e.getMessage());
        }
    }

    private void displaySubmission(CodeSubmission submission) {
        if (submission == null || submission.getCode() == null
                || submission.getCode().isBlank()) {
            summaryLabel.setText("Sinh code that bai, thu lai");
            summaryLabel.setStyle("-fx-text-fill: #e5b567; -fx-font-weight: bold;");
            explanationArea.setText(submission == null
                    ? "AI service did not return a submission."
                    : valueOrEmpty(submission.getExplanation()));
            return;
        }
        codeArea.setText(valueOrEmpty(submission.getCode()));
        explanationArea.setText(valueOrEmpty(submission.getExplanation()));
        summaryLabel.setText("Da sinh " + valueOrEmpty(submission.getType())
                + " / " + valueOrEmpty(submission.getLanguage()));
        summaryLabel.setStyle("");
    }

    private boolean canUsePreferredAc(String language) {
        if (preferredAcSubmission == null
                || preferredAcSubmission.getCode() == null
                || preferredAcSubmission.getCode().isBlank()) {
            return false;
        }
        String cachedLanguage = valueOrEmpty(preferredAcSubmission.getLanguage()).trim();
        String requestedLanguage = valueOrEmpty(language).trim();
        return cachedLanguage.equalsIgnoreCase(requestedLanguage);
    }

    private void updateSummary() {
        long total = testCases.size();
        long accepted = testCases.stream()
                .filter(testCase -> "AC".equals(testCase.getVerdict()))
                .count();
        long finished = testCases.stream()
                .filter(testCase -> testCase.getVerdict() != null && !testCase.getVerdict().isBlank())
                .count();

        summaryLabel.setText(accepted + "/" + total + " AC (" + finished + " done)");
        summaryLabel.setStyle(accepted == total && finished == total
                ? "-fx-text-fill: #7bd88f; -fx-font-weight: bold;"
                : "-fx-text-fill: #e38284; -fx-font-weight: bold;");
    }

    private AnalysisReport buildReport() {
        AnalysisReport report = new AnalysisReport();
        report.setProblem(problem);
        report.setTestCases(List.copyOf(testCases));
        report.setOverallVerdict(computeOverallVerdict());

        String code = codeArea.getText();
        if (code != null && !code.isBlank()) {
            CodeSubmission submission = new CodeSubmission(
                    code,
                    languageCombo.getValue(),
                    codeTypeCombo.getValue()
            );
            submission.setExplanation(explanationArea.getText());
            report.setSubmission(submission);
        }
        return report;
    }

    private String computeOverallVerdict() {
        if (testCases.isEmpty()) {
            return "WEAK";
        }
        boolean allAccepted = testCases.stream().allMatch(testCase -> "AC".equals(testCase.getVerdict()));
        boolean anyAccepted = testCases.stream().anyMatch(testCase -> "AC".equals(testCase.getVerdict()));
        if (allAccepted) {
            return "STRONG";
        }
        return anyAccepted ? "WEAK" : "INCORRECT";
    }

    private String reportFilename() {
        String title = problem == null ? "problem" : valueOrEmpty(problem.getTitle());
        String safeTitle = title.isBlank()
                ? "problem"
                : title.replaceAll("[^a-zA-Z0-9_-]+", "_");
        return "report_" + safeTitle + ".html";
    }

    private TableCell<TestCase, String> verdictCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String verdict, boolean empty) {
                super.updateItem(verdict, empty);
                if (empty || verdict == null || verdict.isBlank()) {
                    setText("");
                    setStyle("");
                    return;
                }
                setText(verdict);
                setStyle(switch (verdict) {
                    case "AC" -> "-fx-text-fill: #7bd88f; -fx-font-weight: bold;";
                    case "WA" -> "-fx-text-fill: #e38284; -fx-font-weight: bold;";
                    case "TLE" -> "-fx-text-fill: #e5b567; -fx-font-weight: bold;";
                    case "CE" -> "-fx-text-fill: #a88bd8; -fx-font-weight: bold;";
                    case "RE" -> "-fx-text-fill: #d18f52; -fx-font-weight: bold;";
                    default -> "-fx-text-fill: #c7c2b8;";
                });
            }
        };
    }

    private TableCell<TestCase, String> diffCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                TestCase testCase = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || testCase == null) {
                    setText("");
                    setTooltip(null);
                    setStyle("");
                    return;
                }

                setText(item == null ? "" : item);
                setTextOverrun(OverrunStyle.ELLIPSIS);
                setWrapText(false);
                setTooltip(new Tooltip(diffTooltip(testCase)));
                setStyle(diffStyle(testCase));
            }
        };
    }

    private String diffPreview(TestCase testCase) {
        if (testCase == null) {
            return "";
        }

        String verdict = valueOrEmpty(testCase.getVerdict());
        String expected = compactOutput(testCase.getExpectedOutput());
        String actual = compactOutput(testCase.getActualOutput());

        if (verdict.isBlank()) {
            return "";
        }
        if ("AC".equals(verdict)) {
            return actual.isBlank() ? "Matched" : "Matched: " + actual;
        }
        if ("WA".equals(verdict)) {
            return "Expected: " + emptyMarker(expected) + " -> Actual: " + emptyMarker(actual);
        }
        if ("TLE".equals(verdict)) {
            return actual.isBlank() ? "Time limit exceeded" : actual;
        }
        if ("CE".equals(verdict)) {
            return actual.isBlank() ? "Compile error" : actual;
        }
        if ("RE".equals(verdict)) {
            return actual.isBlank() ? "Runtime error" : actual;
        }
        return actual;
    }

    private String diffTooltip(TestCase testCase) {
        String verdict = valueOrEmpty(testCase.getVerdict());
        String expected = valueOrEmpty(testCase.getExpectedOutput()).strip();
        String actual = valueOrEmpty(testCase.getActualOutput()).strip();

        if ("AC".equals(verdict)) {
            return "Output matched expected value.\n\nActual:\n" + emptyMarker(actual);
        }
        return "Expected:\n" + emptyMarker(expected)
                + "\n\nActual:\n" + emptyMarker(actual);
    }

    private String diffStyle(TestCase testCase) {
        String verdict = valueOrEmpty(testCase.getVerdict());
        return switch (verdict) {
            case "AC" -> "-fx-text-fill: #9be7ad;";
            case "WA" -> "-fx-text-fill: #ff9ca0; -fx-font-weight: 600;";
            case "TLE" -> "-fx-text-fill: #ffd38a;";
            case "CE" -> "-fx-text-fill: #c9aeff;";
            case "RE" -> "-fx-text-fill: #f0b074;";
            default -> "-fx-text-fill: #c7c2b8;";
        };
    }

    private String compactOutput(String value) {
        String text = valueOrEmpty(value)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        if (text.isBlank()) {
            return "";
        }
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + " / " + right)
                .orElse("");
    }

    private String emptyMarker(String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }

    private String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.toString() : cursor.getMessage();
    }

    private record TleValidationResult(boolean valid, String message) {
    }

    private record WaValidationResult(
            boolean valid,
            String message,
            List<TestCase> probes
    ) {
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
