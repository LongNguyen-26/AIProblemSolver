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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                        resultViewController.setPreferredAcSubmission(result.preferredAcSubmission);
                        resultViewController.setTestCases(result.testCases);
                        mainTabPane.getSelectionModel().select(1);
                        setStatus(result.message);
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

            TestPyramidPlan plan = buildTestPyramidPlan(testCaseCount);

            setStatusAsync("Dang sinh input nho de stress brute-force...");
            List<TestCase> smallCases = generateProfileCases(
                    problem, plan.smallCount(), includeEdgeCases, "SMALL"
            );
            if (smallCases.isEmpty()) {
                throw new RuntimeException("AI did not generate runnable input test cases.");
            }

            ExpectedBatch batch = buildStressTestCases(
                    problem, smallCases, plan, includeEdgeCases
            );
            return new AnalyzeResult(
                    problem,
                    batch.testCases(),
                    batch.message(),
                    batch.preferredAcSubmission()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ExpectedBatch buildStressTestCases(
            Problem problem,
            List<TestCase> smallCases,
            TestPyramidPlan plan,
            boolean includeEdgeCases
    ) throws Exception {
        List<TestCase> validationCases = buildValidationCases(
                problem, smallCases, plan, includeEdgeCases
        );
        List<String> smallInputs = inputsOf(validationCases);

        if (isSpecialJudgeLike(problem)) {
            return buildCanonicalAcExpectedCases(
                    problem,
                    validationCases,
                    plan,
                    includeEdgeCases
            );
        }

        setStatusAsync("Dang sinh code brute-force de tao Expected Output...");
        List<OracleRun> bruteRuns = generateOracleRuns(
                problem,
                smallInputs,
                List.of("BRUTE", "BRUTE_ALT", "ORACLE"),
                true
        );
        if (bruteRuns.isEmpty()) {
            bruteRuns = generateOracleRuns(
                    problem,
                    smallInputs,
                    List.of("BRUTE", "BRUTE_ALT", "ORACLE"),
                    false
            );
        }

        if (bruteRuns.isEmpty()) {
            throw new RuntimeException(
                    "Khong tao duoc code brute-force hop le de tinh Expected Output."
            );
        }

        int minAgreement = bruteRuns.size() >= 2 ? 2 : 1;
        List<TestCase> verifiedValidationCases = applyMajorityExpectedOutputs(
                validationCases, bruteRuns, minAgreement
        );
        boolean singleOracleFallback = false;
        if (verifiedValidationCases.isEmpty()) {
            verifiedValidationCases = applyMajorityExpectedOutputs(
                    validationCases, List.of(bruteRuns.get(0)), 1
            );
            singleOracleFallback = !verifiedValidationCases.isEmpty();
        }
        if (verifiedValidationCases.isEmpty()) {
            throw new RuntimeException("Khong tinh duoc Expected Output cho input nho.");
        }

        OracleRun preferredSmallOracle = findRunMatchingExpected(bruteRuns, verifiedValidationCases);
        if (preferredSmallOracle == null) {
            preferredSmallOracle = bruteRuns.get(0);
            verifiedValidationCases = applyOutputsToCases(
                    validationCases,
                    preferredSmallOracle.outputs()
            );
            singleOracleFallback = true;
        }

        if (singleOracleFallback) {
            List<TestCase> fallbackCases = takeCases(verifiedValidationCases, plan.totalCount());
            return new ExpectedBatch(
                    fallbackCases,
                    "Phan tich xong - " + fallbackCases.size()
                            + " test cases nho tu 1 brute oracle; bo qua strong vi oracle chua dong thuan",
                    submissionFromOracle(
                            preferredSmallOracle,
                            "AC",
                            "Cached brute oracle used to generate these expected outputs."
                    )
            );
        }

        List<TestCase> verifiedSmallCases = takeCases(verifiedValidationCases, plan.smallCount());
        OracleRun optimized = findTrustedOptimizedOracle(problem, verifiedValidationCases);
        if (optimized == null || plan.mediumCount() + plan.killerCount() <= 0) {
            String reason = optimized == null
                    ? "; optimized code chua pass stress nen bo qua medium/killer"
                    : "";
            List<TestCase> fallbackCases = takeCases(verifiedValidationCases, plan.totalCount());
            return new ExpectedBatch(
                    fallbackCases,
                    "Phan tich xong - " + fallbackCases.size()
                            + " test cases nho da xac minh bang brute-force" + reason,
                    submissionFromOracle(
                            preferredSmallOracle,
                            "AC",
                            "Cached brute oracle used to generate these expected outputs."
                    )
            );
        }

        List<TestCase> mediumCases = generateOptimizedExpectedCases(
                problem,
                plan.mediumCount(),
                includeEdgeCases,
                "MEDIUM",
                optimized
        );
        List<TestCase> killerCases = generateOptimizedExpectedCases(
                problem,
                plan.killerCount(),
                includeEdgeCases,
                "KILLER",
                optimized
        );

        List<TestCase> allCases = fillToCount(
                mergeUniqueCases(verifiedSmallCases, mediumCases, killerCases),
                verifiedValidationCases,
                plan.totalCount()
        );
        return new ExpectedBatch(
                allCases,
                "Phan tich xong - " + allCases.size()
                        + " test cases ("
                        + verifiedSmallCases.size()
                        + " sample/small, "
                        + mediumCases.size()
                        + " medium, "
                        + killerCases.size()
                        + " killer)",
                submissionFromOracle(
                        optimized,
                        "AC",
                        "Cached optimized oracle used to generate these expected outputs."
                )
        );
    }

    private ExpectedBatch buildCanonicalAcExpectedCases(
            Problem problem,
            List<TestCase> validationCases,
            TestPyramidPlan plan,
            boolean includeEdgeCases
    ) throws Exception {
        setStatusAsync("Bai co nhieu output hop le; dang sinh canonical AC oracle...");
        OracleRun canonical = findRunnableCanonicalOracle(problem, inputsOf(validationCases));
        if (canonical == null) {
            throw new RuntimeException("Khong tao duoc canonical AC oracle de tinh Expected Output.");
        }

        List<TestCase> verifiedValidationCases = applyOutputsToCases(
                validationCases,
                canonical.outputs()
        );
        if (verifiedValidationCases.isEmpty()) {
            throw new RuntimeException("Canonical AC oracle khong tao duoc Expected Output hop le.");
        }

        List<TestCase> verifiedSmallCases = takeCases(verifiedValidationCases, plan.smallCount());
        List<TestCase> mediumCases = generateOptimizedExpectedCases(
                problem,
                plan.mediumCount(),
                includeEdgeCases,
                "MEDIUM",
                canonical
        );
        List<TestCase> killerCases = generateOptimizedExpectedCases(
                problem,
                plan.killerCount(),
                includeEdgeCases,
                "KILLER",
                canonical
        );

        List<TestCase> allCases = fillToCount(
                mergeUniqueCases(verifiedSmallCases, mediumCases, killerCases),
                verifiedValidationCases,
                plan.totalCount()
        );
        if (allCases.isEmpty()) {
            throw new RuntimeException("Khong tao duoc test case co Expected Output.");
        }

        return new ExpectedBatch(
                allCases,
                "Phan tich xong - " + allCases.size()
                        + " test cases theo canonical AC oracle ("
                        + verifiedSmallCases.size()
                        + " sample/small, "
                        + mediumCases.size()
                        + " medium, "
                        + killerCases.size()
                        + " killer)",
                submissionFromOracle(
                        canonical,
                        "AC",
                        "Cached canonical AC oracle used to generate these expected outputs."
                )
        );
    }

    private List<TestCase> buildValidationCases(
            Problem problem,
            List<TestCase> smallCases,
            TestPyramidPlan plan,
            boolean includeEdgeCases
    ) {
        try {
            int validationCount = Math.max(plan.totalCount(), plan.smallCount());
            setStatusAsync("Dang sinh input nho an de stress optimized code...");
            List<TestCase> hiddenSmallCases = generateProfileCases(
                    problem, validationCount, includeEdgeCases, "SMALL"
            );
            return mergeUniqueCases(smallCases, hiddenSmallCases);
        } catch (Exception ignored) {
            return smallCases;
        }
    }

    private List<TestCase> generateOptimizedExpectedCases(
            Problem problem,
            int count,
            boolean includeEdgeCases,
            String profile,
            OracleRun optimized
    ) {
        if (count <= 0) {
            return List.of();
        }
        try {
            setStatusAsync("Dang sinh " + profile + " tests de bat TLE...");
            List<TestCase> cases = generateProfileCases(
                    problem, count, includeEdgeCases, profile
            );
            if (cases.isEmpty()) {
                return List.of();
            }
            setStatusAsync("Dang tinh Expected Output cho " + profile + " tests...");
            List<String> outputs = expectedOutputExecutionService.generateOutputs(
                    optimized.code(),
                    optimized.language(),
                    inputsOf(cases),
                    optimized.name() + " " + profile + " phase"
            );
            fillExpectedOutputs(cases, outputs);
            return cases;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<OracleRun> generateOracleRuns(
            Problem problem,
            List<String> inputs,
            List<String> oracleTypes,
            boolean validateSamples
    ) {
        List<OracleRun> runs = new ArrayList<>();
        for (String oracleType : oracleTypes) {
            try {
                setStatusAsync("Dang sinh va chay " + oracleType + "...");
                runs.add(generateAndRunOracle(problem, inputs, oracleType, validateSamples));
            } catch (Exception ignored) {
                // Keep the UX moving; other independent oracles may still be usable.
            }
        }
        return runs;
    }

    private OracleRun generateAndRunOracle(
            Problem problem,
            List<String> inputs,
            String oracleType,
            boolean validateSamples
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

        if (validateSamples) {
            validateOracleAgainstSamples(problem, code, language, oracleType);
        }
        setStatusAsync("Dang chay " + oracleType + " tren input test cases...");
        List<String> outputs = expectedOutputExecutionService.generateOutputs(
                code, language, inputs, oracleType
        );
        return new OracleRun(oracleType, code, language, List.copyOf(inputs), outputs);
    }

    private OracleRun findRunnableCanonicalOracle(
            Problem problem,
            List<String> inputs
    ) {
        for (String oracleType : List.of("AC", "OPTIMIZED_ALT", "ORACLE_ALT", "ORACLE")) {
            try {
                setStatusAsync("Dang chay canonical oracle " + oracleType + "...");
                return generateAndRunOracle(problem, inputs, oracleType, false);
            } catch (Exception ignored) {
                // Try another independently generated oracle.
            }
        }
        return null;
    }

    private OracleRun findTrustedOptimizedOracle(
            Problem problem,
            List<TestCase> verifiedSmallCases
    ) {
        List<String> inputs = inputsOf(verifiedSmallCases);
        List<String> expected = verifiedSmallCases.stream()
                .map(TestCase::getExpectedOutput)
                .toList();

        for (String oracleType : List.of("AC", "OPTIMIZED_ALT", "ORACLE_ALT")) {
            try {
                setStatusAsync("Dang stress optimized oracle " + oracleType + "...");
                OracleRun run = generateAndRunOracle(problem, inputs, oracleType, true);
                if (outputsMatch(expected, run.outputs())) {
                    return run;
                }
            } catch (Exception ignored) {
                // If an optimized oracle fails stress, fall back to brute-verified tests.
            }
        }
        return null;
    }

    private OracleRun findRunMatchingExpected(List<OracleRun> runs, List<TestCase> cases) {
        for (OracleRun run : runs) {
            Map<String, String> outputByInput = new LinkedHashMap<>();
            for (int i = 0; i < run.inputs().size() && i < run.outputs().size(); i++) {
                outputByInput.put(
                        run.inputs().get(i).strip(),
                        expectedOutputExecutionService.normalizeOutput(run.outputs().get(i))
                );
            }

            boolean matches = true;
            for (TestCase testCase : cases) {
                String output = outputByInput.get(
                        testCase.getInput() == null ? "" : testCase.getInput().strip()
                );
                if (output == null || !expectedOutputExecutionService.outputMatches(
                        testCase.getExpectedOutput(),
                        output
                )) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return run;
            }
        }
        return null;
    }

    private List<TestCase> applyMajorityExpectedOutputs(
            List<TestCase> cases,
            List<OracleRun> runs,
            int minAgreement
    ) {
        List<TestCase> verifiedCases = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (OracleRun run : runs) {
                if (index >= run.outputs().size()) {
                    continue;
                }
                String output = expectedOutputExecutionService.normalizeOutput(
                        run.outputs().get(index)
                );
                counts.put(output, counts.getOrDefault(output, 0) + 1);
            }

            String bestOutput = "";
            int bestCount = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > bestCount) {
                    bestOutput = entry.getKey();
                    bestCount = entry.getValue();
                }
            }

            if (bestCount >= minAgreement) {
                TestCase testCase = cases.get(index);
                testCase.setExpectedOutput(bestOutput);
                testCase.setActualOutput("");
                testCase.setVerdict("");
                testCase.setExecutionTimeMs(0);
                verifiedCases.add(testCase);
            }
        }
        return verifiedCases;
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

    private boolean outputsMatch(List<String> expected, List<String> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!expectedOutputExecutionService.outputMatches(expected.get(i), actual.get(i))) {
                return false;
            }
        }
        return true;
    }

    private void fillExpectedOutputs(List<TestCase> testCases, List<String> outputs) {
        if (outputs.size() != testCases.size()) {
            throw new IllegalStateException("Oracle output count does not match test case count.");
        }
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            String output = expectedOutputExecutionService.normalizeOutput(outputs.get(i));
            if (output.isBlank()) {
                throw new IllegalStateException(
                        "Oracle produced blank Expected Output for input #" + (i + 1)
                );
            }
            testCase.setExpectedOutput(output);
            testCase.setActualOutput("");
            testCase.setVerdict("");
            testCase.setExecutionTimeMs(0);
        }
    }

    private List<TestCase> applyOutputsToCases(List<TestCase> cases, List<String> outputs) {
        List<TestCase> withExpected = new ArrayList<>();
        for (int i = 0; i < cases.size() && i < outputs.size(); i++) {
            String output = expectedOutputExecutionService.normalizeOutput(outputs.get(i));
            if (output.isBlank()) {
                continue;
            }

            TestCase testCase = cases.get(i);
            testCase.setExpectedOutput(output);
            testCase.setActualOutput("");
            testCase.setVerdict("");
            testCase.setExecutionTimeMs(0);
            withExpected.add(testCase);
        }
        return withExpected;
    }

    private List<TestCase> generateProfileCases(
            Problem problem,
            int count,
            boolean includeEdgeCases,
            String profile
    ) throws Exception {
        if (count <= 0) {
            return List.of();
        }

        List<TestCase> collected = new ArrayList<>();
        int attempts = 0;
        while (collected.size() < count && attempts < 3) {
            int missing = count - collected.size();
            List<TestCase> next = sanitizeTestCases(
                    aiService.generateTestCases(
                            problem,
                            Math.max(missing, count),
                            includeEdgeCases,
                            profile
                    )
            );
            collected = mergeUniqueCases(collected, next);
            attempts++;
        }
        List<TestCase> result = takeCases(collected, count);
        tagProfileCases(result, profile);
        return result;
    }

    private void tagProfileCases(List<TestCase> testCases, String profile) {
        String label = "[" + valueOrEmpty(profile).toUpperCase(Locale.ROOT) + "] ";
        for (TestCase testCase : testCases == null ? List.<TestCase>of() : testCases) {
            String description = valueOrEmpty(testCase.getDescription());
            if (description.startsWith("[")) {
                continue;
            }
            testCase.setDescription(label + description);
        }
    }

    private List<TestCase> sanitizeTestCases(List<TestCase> values) {
        Map<String, TestCase> uniqueByInput = new LinkedHashMap<>();
        for (TestCase testCase : values == null ? List.<TestCase>of() : values) {
            if (testCase == null || testCase.getInput() == null
                    || testCase.getInput().isBlank()) {
                continue;
            }
            uniqueByInput.putIfAbsent(testCase.getInput().strip(), testCase);
        }
        return new ArrayList<>(uniqueByInput.values());
    }

    @SafeVarargs
    private final List<TestCase> mergeUniqueCases(List<TestCase>... groups) {
        Map<String, TestCase> uniqueByInput = new LinkedHashMap<>();
        for (List<TestCase> group : groups) {
            for (TestCase testCase : group == null ? List.<TestCase>of() : group) {
                if (testCase == null || testCase.getInput() == null
                        || testCase.getInput().isBlank()) {
                    continue;
                }
                uniqueByInput.putIfAbsent(testCase.getInput().strip(), testCase);
            }
        }
        return new ArrayList<>(uniqueByInput.values());
    }

    private List<TestCase> fillToCount(
            List<TestCase> base,
            List<TestCase> fallback,
            int count
    ) {
        List<TestCase> merged = mergeUniqueCases(base, fallback);
        return takeCases(merged, count);
    }

    private List<TestCase> takeCases(List<TestCase> values, int count) {
        if (values == null || values.isEmpty() || count <= 0) {
            return List.of();
        }
        return new ArrayList<>(values.subList(0, Math.min(count, values.size())));
    }

    private List<String> inputsOf(List<TestCase> testCases) {
        return testCases.stream().map(TestCase::getInput).toList();
    }

    private boolean isSpecialJudgeLike(Problem problem) {
        String text = String.join("\n",
                valueOrEmpty(problem == null ? null : problem.getTitle()),
                valueOrEmpty(problem == null ? null : problem.getDescription()),
                valueOrEmpty(problem == null ? null : problem.getInputFormat()),
                valueOrEmpty(problem == null ? null : problem.getOutputFormat()),
                String.join("\n", problem == null || problem.getConstraints() == null
                        ? List.of()
                        : problem.getConstraints())
        ).toLowerCase(Locale.ROOT);

        return text.contains("multiple solutions")
                || text.contains("any of them")
                || text.contains("any valid")
                || text.contains("any solution")
                || text.contains("any answer")
                || text.contains("print any")
                || text.contains("output any")
                || text.contains("can output any")
                || text.contains("may output")
                || text.contains("print a solution")
                || text.contains("output a solution")
                || text.contains("one possible")
                || text.contains("one of the")
                || text.contains("if there are multiple")
                || text.contains("multiple answers")
                || text.contains("co nhieu cach")
                || text.contains("bat ky cach")
                || text.contains("in ra bat ky");
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private CodeSubmission submissionFromOracle(
            OracleRun oracle,
            String type,
            String explanation
    ) {
        if (oracle == null) {
            return null;
        }
        CodeSubmission submission = new CodeSubmission(
                oracle.code(),
                oracle.language(),
                type
        );
        submission.setExplanation(explanation);
        return submission;
    }

    private TestPyramidPlan buildTestPyramidPlan(int requestedCount) {
        int total = Math.max(1, requestedCount);
        if (total == 1) {
            return new TestPyramidPlan(1, 0, 0, 1);
        }

        int small = Math.max(1, (int) Math.round(total * 0.2));
        int medium = Math.max(1, (int) Math.round(total * 0.4));
        int killer = total - small - medium;
        if (killer < 0) {
            medium = Math.max(0, medium + killer);
            killer = 0;
        }
        if (total >= 3 && killer == 0) {
            if (medium > 1) {
                medium--;
            } else {
                small = Math.max(1, small - 1);
            }
            killer = 1;
        }
        return new TestPyramidPlan(small, medium, killer, total);
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
        private final String message;
        private final CodeSubmission preferredAcSubmission;

        private AnalyzeResult(
                Problem problem,
                List<TestCase> testCases,
                String message,
                CodeSubmission preferredAcSubmission
        ) {
            this.problem = problem;
            this.testCases = testCases == null ? List.of() : testCases;
            this.message = message == null || message.isBlank()
                    ? "Phan tich xong - " + this.testCases.size()
                    + " test cases da co Expected Output"
                    : message;
            this.preferredAcSubmission = preferredAcSubmission;
        }
    }

    private record ExpectedBatch(
            List<TestCase> testCases,
            String message,
            CodeSubmission preferredAcSubmission
    ) {
    }

    private record OracleRun(
            String name,
            String code,
            String language,
            List<String> inputs,
            List<String> outputs
    ) {
    }

    private record TestPyramidPlan(
            int smallCount,
            int mediumCount,
            int killerCount,
            int totalCount
    ) {
    }
}
