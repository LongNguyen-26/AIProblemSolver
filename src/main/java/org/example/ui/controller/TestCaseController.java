package org.example.ui.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.example.model.TestCase;
import org.example.service.ReportService;
import org.example.ui.util.AlertUtil;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.function.Consumer;

public class TestCaseController implements Initializable {
    @FXML private TableView<TestCase> testCaseTable;
    @FXML private TableColumn<TestCase, String> idCol;
    @FXML private TableColumn<TestCase, String> inputCol;
    @FXML private TableColumn<TestCase, String> expectedCol;
    @FXML private TableColumn<TestCase, String> descCol;
    @FXML private TableColumn<TestCase, Boolean> edgeCol;
    @FXML private TableColumn<TestCase, String> verdictCol;
    @FXML private TableColumn<TestCase, Long> timeCol;
    @FXML private TextArea detailInputArea;
    @FXML private TextArea detailExpectedArea;
    @FXML private TextArea detailActualArea;
    @FXML private Label coverageLabel;

    private final ObservableList<TestCase> testCases = FXCollections.observableArrayList();
    private final ReportService reportService = new ReportService();
    private final Map<String, Integer> requiredCoverage = new LinkedHashMap<>();
    private Consumer<List<TestCase>> testCasesUpdatedListener = updated -> {
    };
    private boolean updatingDetails;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupCoverageRequirements();
        setupTable();
        setupSelectionDetails();
        updateCoverageSummary();
    }

    public void setTestCases(List<TestCase> values) {
        testCases.setAll(values == null ? List.of() : values);
        clearDetails();
        updateCoverageSummary();
    }

    public List<TestCase> getTestCases() {
        return List.copyOf(testCases);
    }

    public void setOnTestCasesUpdated(Consumer<List<TestCase>> listener) {
        this.testCasesUpdatedListener = listener == null ? updated -> {
        } : listener;
    }

    public void refreshTable() {
        testCaseTable.refresh();
        displayDetails(testCaseTable.getSelectionModel().getSelectedItem());
        updateCoverageSummary();
    }

    @FXML
    private void onAddManual() {
        Dialog<TestCase> dialog = new Dialog<>();
        dialog.setTitle("Them test case");
        dialog.setHeaderText(null);
        if (testCaseTable.getScene() != null) {
            dialog.initOwner(testCaseTable.getScene().getWindow());
        }

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        TextArea inputArea = new TextArea();
        inputArea.setPromptText("Input");
        inputArea.setPrefRowCount(5);
        TextArea expectedArea = new TextArea();
        expectedArea.setPromptText("Expected output");
        expectedArea.setPrefRowCount(4);
        TextField descriptionField = new TextField();
        descriptionField.setPromptText("Mo ta");
        CheckBox edgeCheck = new CheckBox("Edge case");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(new Label("Input:"), 0, 0);
        grid.add(inputArea, 1, 0);
        grid.add(new Label("Expected:"), 0, 1);
        grid.add(expectedArea, 1, 1);
        grid.add(new Label("Mo ta:"), 0, 2);
        grid.add(descriptionField, 1, 2);
        grid.add(edgeCheck, 1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != addButtonType) {
                return null;
            }
            return new TestCase(
                    "tc_" + UUID.randomUUID().toString().substring(0, 8),
                    inputArea.getText(),
                    expectedArea.getText(),
                    descriptionField.getText(),
                    edgeCheck.isSelected()
            );
        });

        dialog.showAndWait().ifPresent(testCase -> {
            testCases.add(testCase);
            updateCoverageSummary();
            notifyTestCasesUpdated();
        });
    }

    @FXML
    private void onDeleteSelected() {
        TestCase selected = testCaseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.showWarning(testCaseTable.getScene().getWindow(), "Hay chon test case can xoa.");
            return;
        }
        testCases.remove(selected);
        clearDetails();
        updateCoverageSummary();
        notifyTestCasesUpdated();
    }

    private void setupCoverageRequirements() {
        requiredCoverage.put("sample", 1);
        requiredCoverage.put("boundary", 2);
        requiredCoverage.put("edge_structural", 2);
        requiredCoverage.put("adversarial", 3);
        requiredCoverage.put("stress_random", 2);
        requiredCoverage.put("performance", 3);
    }

    @FXML
    private void onExportZip() {
        if (testCases.isEmpty()) {
            AlertUtil.showWarning(testCaseTable.getScene().getWindow(), "Chua co test case de export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export test cases");
        fileChooser.setInitialFileName("testcases.zip");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP files", "*.zip"));
        File target = fileChooser.showSaveDialog(testCaseTable.getScene().getWindow());
        if (target == null) {
            return;
        }

        try {
            String temporaryPath = reportService.exportTestCasesZip(testCases);
            Files.move(
                    Paths.get(temporaryPath),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
            AlertUtil.showInfo(testCaseTable.getScene().getWindow(),
                    "Da export " + testCases.size() + " test cases: " + target.getName());
        } catch (Exception e) {
            AlertUtil.showError(testCaseTable.getScene().getWindow(), "Loi export ZIP: " + e.getMessage());
        }
    }

    private void setupTable() {
        testCaseTable.setItems(testCases);
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        inputCol.setCellValueFactory(new PropertyValueFactory<>("input"));
        expectedCol.setCellValueFactory(new PropertyValueFactory<>("expectedOutput"));
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        edgeCol.setCellValueFactory(new PropertyValueFactory<>("edgeCase"));
        verdictCol.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        timeCol.setCellValueFactory(new PropertyValueFactory<>("executionTimeMs"));
        inputCol.setCellFactory(column -> singleLineCell());
        expectedCol.setCellFactory(column -> singleLineCell());
        descCol.setCellFactory(column -> singleLineCell());
        edgeCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().isEdgeCase()));
        edgeCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item ? "Yes" : "No");
            }
        });
        verdictCol.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(valueOrEmpty(cellData.getValue().getVerdict())));
        verdictCol.setCellFactory(column -> verdictCell());
    }

    private void setupSelectionDetails() {
        testCaseTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
                displayDetails(selected)
        );

        detailInputArea.textProperty().addListener((observable, oldValue, value) -> {
            if (!updatingDetails && testCaseTable.getSelectionModel().getSelectedItem() != null) {
                testCaseTable.getSelectionModel().getSelectedItem().setInput(value);
                testCaseTable.refresh();
                notifyTestCasesUpdated();
            }
        });
        detailExpectedArea.textProperty().addListener((observable, oldValue, value) -> {
            if (!updatingDetails && testCaseTable.getSelectionModel().getSelectedItem() != null) {
                testCaseTable.getSelectionModel().getSelectedItem().setExpectedOutput(value);
                testCaseTable.refresh();
                notifyTestCasesUpdated();
            }
        });
    }

    private TableCell<TestCase, String> singleLineCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : valueOrEmpty(item).replace('\n', ' '));
            }
        };
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

    private void displayDetails(TestCase testCase) {
        updatingDetails = true;
        if (testCase == null) {
            clearDetails();
        } else {
            detailInputArea.setText(valueOrEmpty(testCase.getInput()));
            detailExpectedArea.setText(valueOrEmpty(testCase.getExpectedOutput()));
            detailActualArea.setText(valueOrEmpty(testCase.getActualOutput()));
        }
        updatingDetails = false;
    }

    private void clearDetails() {
        updatingDetails = true;
        detailInputArea.clear();
        detailExpectedArea.clear();
        detailActualArea.clear();
        updatingDetails = false;
    }

    private void notifyTestCasesUpdated() {
        updateCoverageSummary();
        testCasesUpdatedListener.accept(List.copyOf(testCases));
    }

    private void updateCoverageSummary() {
        if (coverageLabel == null) {
            return;
        }
        Map<String, Integer> coverage = new LinkedHashMap<>();
        for (String category : requiredCoverage.keySet()) {
            coverage.put(category, 0);
        }
        for (TestCase testCase : testCases) {
            String category = inferCategory(testCase);
            coverage.put(category, coverage.getOrDefault(category, 0) + 1);
        }

        int complete = 0;
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requiredCoverage.entrySet()) {
            int have = coverage.getOrDefault(entry.getKey(), 0);
            int need = entry.getValue();
            if (have >= need) {
                complete++;
            } else {
                missing.add(entry.getKey() + " " + have + "/" + need);
            }
        }

        if (missing.isEmpty()) {
            coverageLabel.setText("Coverage: " + complete + "/" + requiredCoverage.size()
                    + " loai - OK");
            coverageLabel.setStyle("-fx-text-fill: #7bd88f; -fx-font-weight: bold;");
        } else {
            coverageLabel.setText("Coverage: " + complete + "/" + requiredCoverage.size()
                    + " loai - thieu: " + String.join(", ", missing));
            coverageLabel.setStyle("-fx-text-fill: #e5b567; -fx-font-weight: bold;");
        }
    }

    private String inferCategory(TestCase testCase) {
        String description = valueOrEmpty(testCase == null ? null : testCase.getDescription())
                .toLowerCase(Locale.ROOT);
        if (description.contains("sample") || description.contains("official")) {
            return "sample";
        }
        if (description.contains("performance")
                || description.contains("killer")
                || description.contains("[killer]")
                || description.contains("maximum-size")
                || description.contains("max-size")) {
            return "performance";
        }
        if (description.contains("boundary")
                || description.contains("minimum")
                || description.contains("maximum")
                || description.contains("n=1")
                || description.contains("n=max")
                || description.contains("min/max")) {
            return "boundary";
        }
        if (description.contains("edge_structural")
                || description.contains("structural")
                || description.contains("edge")
                || description.contains("all equal")
                || description.contains("all-equal")
                || description.contains("empty")
                || description.contains("single")
                || description.contains("sorted")
                || description.contains("star")
                || description.contains("chain")
                || description.contains("bamboo")) {
            return "edge_structural";
        }
        if (description.contains("stress_random")
                || description.contains("random")
                || description.contains("smoke")) {
            return "stress_random";
        }
        return "adversarial";
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
