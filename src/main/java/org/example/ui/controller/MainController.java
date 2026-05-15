package org.example.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
import org.example.model.Problem;
import org.example.model.TestCase;
import org.example.service.AIBridgeService;
import org.example.ui.util.AlertUtil;
import org.example.util.FileUtil;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class MainController implements Initializable {
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

    @FXML private ProblemInputController problemInputViewController;
    @FXML private TestCaseController testcaseViewController;
    @FXML private ResultController resultViewController;

    private final AIBridgeService aiService = new AIBridgeService();
    private Problem currentProblem;
    private String selectedImageBase64;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        testCaseCountSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 10)
        );
        setupToggleListeners();
        updateInputMode(imageModeBtn.isSelected());
        resultViewController.setAiService(aiService);
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

        setLoading(true);
        setStatus("Dang phan tich de bai...");

        CompletableFuture.supplyAsync(() -> analyzeAndGenerate(
                        imageMode, text, imageBase64, testCaseCount, includeEdgeCases
                ))
                .thenAcceptAsync(result -> {
                    currentProblem = result.problem;
                    problemInputViewController.displayProblem(result.problem);
                    testcaseViewController.setTestCases(result.testCases);
                    resultViewController.setProblem(result.problem);
                    resultViewController.setTestCases(result.testCases);
                    mainTabPane.getSelectionModel().select(1);
                    setStatus("Phan tich xong - " + result.testCases.size() + " test cases da tao");
                    setLoading(false);
                }, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        showError(rootCauseMessage(error));
                        setLoading(false);
                    });
                    return null;
                });
    }

    private AnalyzeResult analyzeAndGenerate(boolean imageMode, String text, String imageBase64,
                                             int testCaseCount, boolean includeEdgeCases) {
        try {
            Problem problem = imageMode
                    ? aiService.analyzeProblemImage(imageBase64)
                    : aiService.analyzeProblemText(text);
            List<TestCase> testCases = aiService.generateTestCases(
                    problem, testCaseCount, includeEdgeCases
            );
            return new AnalyzeResult(problem, testCases);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
}
