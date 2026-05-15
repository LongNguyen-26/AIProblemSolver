package org.example.ui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.model.CodeSubmission;
import org.example.model.Problem;
import org.example.model.TestCase;
import org.example.service.AIBridgeService;
import org.example.service.ExecutionService;
import org.example.ui.util.AlertUtil;

import java.net.URL;
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

    private final ObservableList<TestCase> testCases = FXCollections.observableArrayList();
    private final ExecutionService executionService = new ExecutionService();
    private AIBridgeService aiService = new AIBridgeService();
    private Problem problem;
    private Runnable resultUpdateListener = () -> {
    };

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        codeTypeCombo.getItems().setAll("AC", "WA", "TLE");
        codeTypeCombo.getSelectionModel().select("AC");
        languageCombo.getItems().setAll("cpp", "python", "java");
        languageCombo.getSelectionModel().select("cpp");

        resultTable.setItems(testCases);
        rTcIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        rVerdictCol.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        rTimeCol.setCellValueFactory(new PropertyValueFactory<>("executionTimeMs"));
        rDiffCol.setCellValueFactory(new PropertyValueFactory<>("actualOutput"));
        rVerdictCol.setCellFactory(column -> verdictCell());
        rDiffCol.setCellFactory(column -> singleLineCell());
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

    @FXML
    private void onGenerateCode() {
        if (problem == null) {
            AlertUtil.showWarning(codeArea.getScene().getWindow(), "Hay phan tich de bai truoc.");
            return;
        }

        String codeType = codeTypeCombo.getValue();
        String language = languageCombo.getValue();
        summaryLabel.setText("Dang sinh code...");

        CompletableFuture.supplyAsync(() -> {
            try {
                return aiService.generateCode(problem, codeType, language);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(this::displaySubmission, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        String message = rootCauseMessage(error);
                        summaryLabel.setText("Loi sinh code");
                        AlertUtil.showError(codeArea.getScene().getWindow(), message);
                    });
                    return null;
                });
    }

    @FXML
    private void onRunAllTests() {
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
        });
    }

    @FXML
    private void onExportReport() {
        AlertUtil.showInfo(codeArea.getScene().getWindow(),
                "Export report se duoc trien khai o Task 06.");
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

    private TableCell<TestCase, String> singleLineCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.replace('\n', ' '));
            }
        };
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
