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
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.example.model.AnalysisReport;
import org.example.model.CodeSubmission;
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
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class ResultController implements Initializable {
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
    private boolean codeGenerationInProgress;
    private boolean testRunInProgress;
    private boolean externalBusy;
    private Runnable resultUpdateListener = () -> {
    };

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        codeTypeCombo.getItems().setAll("AC", "WA", "TLE");
        codeTypeCombo.getSelectionModel().select("AC");
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
        setCodeGenerationRunning(true);
        summaryLabel.setText("Dang sinh code...");

        CompletableFuture.supplyAsync(() -> {
            try {
                return aiService.generateCode(problem, codeType, language);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenCompleteAsync((submission, error) -> {
            try {
                if (error != null) {
                    String message = rootCauseMessage(error);
                    summaryLabel.setText("Loi sinh code");
                    AlertUtil.showError(codeArea.getScene().getWindow(), message);
                    return;
                }
                displaySubmission(submission);
            } finally {
                setCodeGenerationRunning(false);
            }
        }, Platform::runLater);
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
        if (submission == null) {
            return;
        }
        codeArea.setText(valueOrEmpty(submission.getCode()));
        explanationArea.setText(valueOrEmpty(submission.getExplanation()));
        summaryLabel.setText("Da sinh " + valueOrEmpty(submission.getType())
                + " / " + valueOrEmpty(submission.getLanguage()));
        summaryLabel.setStyle("");
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

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
