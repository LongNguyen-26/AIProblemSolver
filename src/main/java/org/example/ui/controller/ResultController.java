package org.example.ui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.model.CodeSubmission;
import org.example.model.Problem;
import org.example.model.TestCase;
import org.example.service.AIBridgeService;
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
    private AIBridgeService aiService = new AIBridgeService();
    private Problem problem;

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
        AlertUtil.showInfo(codeArea.getScene().getWindow(),
                "Execution engine se duoc trien khai o Task 05.");
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
