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
import org.example.ui.util.AlertUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    private final ObservableList<TestCase> testCases = FXCollections.observableArrayList();
    private boolean updatingDetails;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupSelectionDetails();
    }

    public void setTestCases(List<TestCase> values) {
        testCases.setAll(values == null ? List.of() : values);
        clearDetails();
    }

    public List<TestCase> getTestCases() {
        return List.copyOf(testCases);
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

        dialog.showAndWait().ifPresent(testCases::add);
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

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target))) {
            for (TestCase testCase : testCases) {
                String id = safeId(testCase.getId());
                writeZipEntry(zip, id + ".in", valueOrEmpty(testCase.getInput()));
                writeZipEntry(zip, id + ".out", valueOrEmpty(testCase.getExpectedOutput()));
            }
            AlertUtil.showInfo(testCaseTable.getScene().getWindow(), "Da export: " + target.getName());
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
    }

    private void setupSelectionDetails() {
        testCaseTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
                displayDetails(selected)
        );

        detailInputArea.textProperty().addListener((observable, oldValue, value) -> {
            if (!updatingDetails && testCaseTable.getSelectionModel().getSelectedItem() != null) {
                testCaseTable.getSelectionModel().getSelectedItem().setInput(value);
                testCaseTable.refresh();
            }
        });
        detailExpectedArea.textProperty().addListener((observable, oldValue, value) -> {
            if (!updatingDetails && testCaseTable.getSelectionModel().getSelectedItem() != null) {
                testCaseTable.getSelectionModel().getSelectedItem().setExpectedOutput(value);
                testCaseTable.refresh();
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

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String safeId(String id) {
        String value = id == null || id.isBlank()
                ? "tc_" + UUID.randomUUID().toString().substring(0, 8)
                : id;
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
