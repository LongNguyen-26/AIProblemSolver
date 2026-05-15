package org.example.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.model.CodeSubmission;
import org.example.model.Problem;
import org.example.model.TestCase;
import org.example.service.AIBridgeService;
import org.example.service.ExecutionService;
import org.example.ui.util.AlertUtil;
import org.example.util.FileUtil;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class MainController implements Initializable {
    private static final String EXPECTED_OUTPUT_LANGUAGE = "cpp";

    @FXML private Label serviceStatusLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TextArea problemTextArea;
    @FXML private ImageView imagePreview;
    @FXML private VBox imageInputBox;
    @FXML private RadioButton textModeBtn;
    @FXML private RadioButton imageModeBtn;
    @FXML private Spinner<Integer> testCaseCountSpinner;
    @FXML private CheckBox edgeCasesCheck;
    @FXML private TabPane mainTabPane;
    @FXML private Button analyzeBtn;

    @FXML private ProblemInputController problemInputViewController;
    @FXML private TestCaseController testcaseViewController;
    @FXML private ResultController resultViewController;

    private final AIBridgeService aiService = new AIBridgeService();
    private final ExecutionService expectedOutputExecutionService = new ExecutionService();
    private Problem currentProblem;
    private String selectedImageBase64;
    private boolean analyzeInProgress;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        testCaseCountSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 10)
        );
        setupToggleListeners();
        updateInputMode(imageModeBtn.isSelected());
        resultViewController.setAiService(aiService);
        resultViewController.setResultUpdateListener(testcaseViewController::refreshTable);
        testcaseViewController.setOnTestCasesUpdated(this::onTestCasesUpdated);
        checkServiceAsync();
    }

    private void setupToggleListeners() {
        imageModeBtn.selectedProperty().addListener((observable, wasSelected, isSelected) ->
                updateInputMode(isSelected)
        );
    }

    private void updateInputMode(boolean imageMode) {
        imageInputBox.setVisible(imageMode);
        imageInputBox.setManaged(imageMode);
        problemTextArea.setVisible(!imageMode);
        problemTextArea.setManaged(!imageMode);
    }

    @FXML
    private void onCheckService() {
        checkServiceAsync();
    }

    private void checkServiceAsync() {
        setStatus("Dang kiem tra Python service...");
        serviceStatusLabel.setText("Checking...");
        serviceStatusLabel.getStyleClass().setAll("status-offline");

        CompletableFuture.supplyAsync(aiService::isServiceAlive)
                .thenAcceptAsync(alive -> {
                    serviceStatusLabel.setText(alive ? "Online" : "Offline");
                    serviceStatusLabel.getStyleClass().setAll(alive ? "status-online" : "status-offline");
                    setStatus(alive
                            ? "Python AI Service: Connected"
                            : "Python AI Service: Offline - hay chay ai_service/main.py");
                }, Platform::runLater);
    }

    @FXML
    private void onSelectImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chon anh de bai");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(getWindow());
        if (file == null) {
            return;
        }

        try {
            selectedImageBase64 = FileUtil.toBase64(file);
            imagePreview.setImage(new Image(file.toURI().toString()));
            setStatus("Da chon anh: " + file.getName());
        } catch (Exception e) {
            showError("Loi doc anh: " + e.getMessage());
        }
    }

    @FXML
    private void onAnalyze() {
        if (analyzeInProgress) {
            return;
        }

        boolean imageMode = imageModeBtn.isSelected();
        String text = problemTextArea.getText();
        String imageBase64 = selectedImageBase64;
        int testCaseCount = testCaseCountSpinner.getValue();
        boolean includeEdgeCases = edgeCasesCheck.isSelected();

        if (!imageMode && (text == null || text.isBlank())) {
            AlertUtil.showWarning(getWindow(), "Hay nhap de bai dang text.");
            return;
        }
        if (imageMode && (imageBase64 == null || imageBase64.isBlank())) {
            AlertUtil.showWarning(getWindow(), "Hay chon anh de bai.");
            return;
        }

        setAnalyzeRunning(true);
        setStatus("Dang phan tich de bai...");

        CompletableFuture.supplyAsync(() -> analyzeAndGenerate(
                        imageMode, text, imageBase64, testCaseCount, includeEdgeCases
                ))
                .whenCompleteAsync((result, error) -> {
                    try {
                        if (error != null) {
                            showError(rootCauseMessage(error));
                            return;
                        }
                        currentProblem = result.problem;
                        problemInputViewController.displayProblem(result.problem);
                        testcaseViewController.setTestCases(result.testCases);
                        resultViewController.setProblem(result.problem);
                        resultViewController.setTestCases(result.testCases);
                        mainTabPane.getSelectionModel().select(1);
                        setStatus("Phan tich xong - " + result.testCases.size()
                                + " test cases da co Expected Output");
                    } finally {
                        setAnalyzeRunning(false);
                    }
                }, Platform::runLater);
    }

    private void setAnalyzeRunning(boolean running) {
        analyzeInProgress = running;
        setLoading(running);
        resultViewController.setExternalBusy(running);
        if (analyzeBtn != null) {
            analyzeBtn.setDisable(running);
            analyzeBtn.setText(running ? "Dang phan tich..." : "Phan tich de");
        }
    }

    private AnalyzeResult analyzeAndGenerate(boolean imageMode, String text, String imageBase64,
                                             int testCaseCount, boolean includeEdgeCases) {
        try {
            Problem problem = imageMode
                    ? aiService.analyzeProblemImage(imageBase64)
                    : aiService.analyzeProblemText(text);
            setStatusAsync("Dang sinh input test cases...");
            List<TestCase> testCases = aiService.generateTestCases(
                    problem, testCaseCount, includeEdgeCases
            );
            testCases = testCases.stream()
                    .filter(testCase -> testCase != null
                            && testCase.getInput() != null
                            && !testCase.getInput().isBlank())
                    .toList();
            if (testCases.isEmpty()) {
                throw new RuntimeException("AI did not generate runnable input test cases.");
            }

            List<String> expectedOutputs = generateVerifiedExpectedOutputs(problem, testCases);
            for (int i = 0; i < testCases.size(); i++) {
                TestCase testCase = testCases.get(i);
                testCase.setExpectedOutput(expectedOutputs.get(i));
                testCase.setActualOutput("");
                testCase.setVerdict("");
                testCase.setExecutionTimeMs(0);
            }
            return new AnalyzeResult(problem, testCases);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> generateVerifiedExpectedOutputs(
            Problem problem,
            List<TestCase> testCases
    ) throws Exception {
        List<String> inputs = testCases.stream().map(TestCase::getInput).toList();
        List<OracleRun> verifiedRuns = new java.util.ArrayList<>();
        Exception lastError = null;

        for (String oracleType : List.of("ORACLE", "ORACLE_ALT", "AC")) {
            try {
                setStatusAsync("Dang sinh va kiem tra " + oracleType + "...");
                OracleRun run = generateAndRunOracle(problem, inputs, oracleType);
                verifiedRuns.add(run);
                if (verifiedRuns.size() == 2) {
                    break;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (verifiedRuns.size() < 2) {
            throw new RuntimeException(
                    "Khong tao duoc 2 code oracle hop le de xac minh Expected Output."
                            + (lastError == null ? "" : " Loi cuoi: " + lastError.getMessage())
            );
        }

        assertOracleOutputsAgree(verifiedRuns.get(0), verifiedRuns.get(1));
        return verifiedRuns.get(0).outputs();
    }

    private OracleRun generateAndRunOracle(
            Problem problem,
            List<String> inputs,
            String oracleType
    ) throws Exception {
        CodeSubmission submission = aiService.generateCode(
                problem, oracleType, EXPECTED_OUTPUT_LANGUAGE
        );
        String code = submission == null ? "" : submission.getCode();
        String language = submission == null || submission.getLanguage() == null
                || submission.getLanguage().isBlank()
                ? EXPECTED_OUTPUT_LANGUAGE
                : submission.getLanguage();
        if (code == null || code.isBlank()) {
            throw new RuntimeException(oracleType + " did not include source code.");
        }

        validateOracleAgainstSamples(problem, code, language, oracleType);
        setStatusAsync("Dang chay " + oracleType + " tren input test cases...");
        List<String> outputs = expectedOutputExecutionService.generateOutputs(
                code, language, inputs, oracleType
        );
        return new OracleRun(oracleType, outputs);
    }

    private void validateOracleAgainstSamples(
            Problem problem,
            String code,
            String language,
            String oracleType
    ) throws Exception {
        if (problem == null || problem.getSampleInputs() == null
                || problem.getSampleOutputs() == null) {
            return;
        }
        int sampleCount = Math.min(
                problem.getSampleInputs().size(),
                problem.getSampleOutputs().size()
        );
        if (sampleCount == 0) {
            return;
        }

        List<String> sampleInputs = problem.getSampleInputs().subList(0, sampleCount);
        List<String> sampleOutputs = problem.getSampleOutputs().subList(0, sampleCount);
        List<String> actualOutputs = expectedOutputExecutionService.generateOutputs(
                code, language, sampleInputs, oracleType + " sample validation"
        );
        for (int i = 0; i < sampleCount; i++) {
            if (!expectedOutputExecutionService.outputMatches(
                    sampleOutputs.get(i), actualOutputs.get(i))) {
                throw new RuntimeException(
                        oracleType + " failed sample #" + (i + 1)
                );
            }
        }
    }

    private void assertOracleOutputsAgree(OracleRun first, OracleRun second) {
        List<String> left = first.outputs();
        List<String> right = second.outputs();
        if (left.size() != right.size()) {
            throw new RuntimeException("Oracle output count mismatch.");
        }
        for (int i = 0; i < left.size(); i++) {
            if (!expectedOutputExecutionService.outputMatches(left.get(i), right.get(i))) {
                throw new RuntimeException(
                        "Oracle outputs disagree on generated test #" + (i + 1)
                                + " (" + first.name() + " vs " + second.name() + ")."
                );
            }
        }
    }

    private void setStatusAsync(String message) {
        Platform.runLater(() -> setStatus(message));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisible(loading);
        progressBar.setManaged(loading);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        AlertUtil.showError(getWindow(), message);
        setStatus("Loi: " + message);
    }

    public void onTestCasesUpdated(List<TestCase> updated) {
        resultViewController.setTestCases(updated);
        setStatus("Da cap nhat " + updated.size() + " test cases");
    }

    private Window getWindow() {
        return statusLabel == null || statusLabel.getScene() == null
                ? null
                : statusLabel.getScene().getWindow();
    }

    private String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.toString() : cursor.getMessage();
    }

    private static class AnalyzeResult {
        private final Problem problem;
        private final List<TestCase> testCases;

        private AnalyzeResult(Problem problem, List<TestCase> testCases) {
            this.problem = problem;
            this.testCases = testCases == null ? List.of() : testCases;
        }
    }

    private record OracleRun(String name, List<String> outputs) {
    }
}
