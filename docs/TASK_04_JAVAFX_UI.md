# TASK_04_JAVAFX_UI.md

## Mục tiêu
Xây dựng giao diện JavaFX với 3 tab chính: nhập đề, quản lý test case, xem kết quả code.

---

## 1. `src/main/resources/fxml/main.fxml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.control.*?>
<?import javafx.geometry.*?>

<BorderPane xmlns:fx="http://javafx.com/fxml"
            fx:controller="org.example.ui.controller.MainController"
            prefWidth="1200" prefHeight="750"
            styleClass="root-pane">

    <!-- TOP BAR -->
    <top>
        <HBox styleClass="top-bar" alignment="CENTER_LEFT" spacing="10">
            <Insets topRightBottomLeft="10"/>
            <Label text="🧩 AIProblemSolver" styleClass="app-title"/>
            <Region HBox.hgrow="ALWAYS"/>
            <Label fx:id="serviceStatusLabel" text="● Checking..." styleClass="status-offline"/>
            <Button fx:id="checkServiceBtn" text="Reconnect"
                    onAction="#onCheckService" styleClass="btn-secondary"/>
        </HBox>
    </top>

    <!-- MAIN CONTENT: Sidebar + TabPane -->
    <center>
        <SplitPane dividerPositions="0.25">

            <!-- SIDEBAR: Problem Input -->
            <VBox styleClass="sidebar" spacing="10" minWidth="250">
                <Insets topRightBottomLeft="15"/>
                <Label text="Nhập đề bài" styleClass="section-title"/>
                <ToggleGroup fx:id="inputModeGroup"/>
                <HBox spacing="10">
                    <RadioButton text="Text" fx:id="textModeBtn"
                                 toggleGroup="$inputModeGroup" selected="true"/>
                    <RadioButton text="Ảnh" fx:id="imageModeBtn"
                                 toggleGroup="$inputModeGroup"/>
                </HBox>

                <!-- Text input -->
                <TextArea fx:id="problemTextArea"
                          promptText="Paste đề bài vào đây..."
                          wrapText="true" VBox.vgrow="ALWAYS"/>

                <!-- Image input -->
                <VBox fx:id="imageInputBox" visible="false" managed="false" spacing="5">
                    <Button text="📁 Chọn ảnh..." onAction="#onSelectImage"
                            maxWidth="Infinity" styleClass="btn-secondary"/>
                    <ImageView fx:id="imagePreview" fitWidth="220" fitHeight="150"
                               preserveRatio="true"/>
                </VBox>

                <Separator/>

                <Label text="Số test case:" style="-fx-font-size:12px;"/>
                <Spinner fx:id="testCaseCountSpinner" min="1" max="50"
                         initialValue="10" editable="true"/>
                <CheckBox fx:id="edgeCasesCheck" text="Bao gồm edge cases"
                          selected="true"/>

                <Button text="🔍 Phân tích đề" onAction="#onAnalyze"
                        maxWidth="Infinity" styleClass="btn-primary"/>
                <ProgressBar fx:id="progressBar" maxWidth="Infinity"
                             visible="false"/>
            </VBox>

            <!-- RIGHT: Tab content -->
            <TabPane fx:id="mainTabPane" tabClosingPolicy="UNAVAILABLE">

                <!-- TAB 1: Analysis -->
                <Tab text="📋 Phân tích">
                    <fx:include source="problem_input.fxml"
                                fx:id="problemInputView"/>
                </Tab>

                <!-- TAB 2: Test Cases -->
                <Tab text="🧪 Test Cases">
                    <fx:include source="testcase_view.fxml"
                                fx:id="testcaseView"/>
                </Tab>

                <!-- TAB 3: Code & Results -->
                <Tab text="💻 Code & Kết quả">
                    <fx:include source="result_view.fxml"
                                fx:id="resultView"/>
                </Tab>

            </TabPane>
        </SplitPane>
    </center>

    <!-- STATUS BAR -->
    <bottom>
        <HBox styleClass="status-bar" alignment="CENTER_LEFT" spacing="10">
            <Insets topRightBottomLeft="5"/>
            <Label fx:id="statusLabel" text="Sẵn sàng"/>
        </HBox>
    </bottom>
</BorderPane>
```

---

## 2. `src/main/resources/fxml/problem_input.fxml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.control.*?>
<?import javafx.geometry.*?>

<VBox xmlns:fx="http://javafx.com/fxml"
      fx:controller="org.example.ui.controller.ProblemInputController"
      spacing="10" styleClass="tab-content">
    <Insets topRightBottomLeft="15"/>

    <Label text="Kết quả phân tích" styleClass="section-title"/>

    <GridPane hgap="10" vgap="8">
        <Label text="Tiêu đề:" GridPane.columnIndex="0" GridPane.rowIndex="0"/>
        <TextField fx:id="titleField" editable="false"
                   GridPane.columnIndex="1" GridPane.rowIndex="0"
                   GridPane.hgrow="ALWAYS"/>

        <Label text="Input format:" GridPane.columnIndex="0" GridPane.rowIndex="1"/>
        <TextArea fx:id="inputFormatArea" editable="false" prefRowCount="2"
                  wrapText="true"
                  GridPane.columnIndex="1" GridPane.rowIndex="1"/>

        <Label text="Output format:" GridPane.columnIndex="0" GridPane.rowIndex="2"/>
        <TextArea fx:id="outputFormatArea" editable="false" prefRowCount="2"
                  wrapText="true"
                  GridPane.columnIndex="1" GridPane.rowIndex="2"/>
    </GridPane>

    <Label text="Constraints:" styleClass="field-label"/>
    <ListView fx:id="constraintsList" prefHeight="100"/>

    <Label text="Mô tả bài toán:" styleClass="field-label"/>
    <TextArea fx:id="descriptionArea" editable="false"
              wrapText="true" VBox.vgrow="ALWAYS"/>
</VBox>
```

---

## 3. `src/main/resources/fxml/testcase_view.fxml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.control.*?>
<?import javafx.geometry.*?>

<VBox xmlns:fx="http://javafx.com/fxml"
      fx:controller="org.example.ui.controller.TestCaseController"
      spacing="10" styleClass="tab-content">
    <Insets topRightBottomLeft="15"/>

    <HBox spacing="10" alignment="CENTER_LEFT">
        <Label text="Test Cases" styleClass="section-title"/>
        <Region HBox.hgrow="ALWAYS"/>
        <Button text="➕ Thêm thủ công" onAction="#onAddManual"
                styleClass="btn-secondary"/>
        <Button text="🗑 Xóa đã chọn" onAction="#onDeleteSelected"
                styleClass="btn-danger"/>
        <Button text="📦 Export ZIP" onAction="#onExportZip"
                styleClass="btn-secondary"/>
    </HBox>

    <TableView fx:id="testCaseTable" VBox.vgrow="ALWAYS">
        <columns>
            <TableColumn fx:id="idCol" text="ID" prefWidth="80"/>
            <TableColumn fx:id="inputCol" text="Input" prefWidth="250"/>
            <TableColumn fx:id="expectedCol" text="Expected Output" prefWidth="200"/>
            <TableColumn fx:id="descCol" text="Mô tả" prefWidth="150"/>
            <TableColumn fx:id="edgeCol" text="Edge?" prefWidth="60"/>
            <TableColumn fx:id="verdictCol" text="Verdict" prefWidth="80"/>
            <TableColumn fx:id="timeCol" text="Time (ms)" prefWidth="90"/>
        </columns>
    </TableView>

    <!-- Detail panel -->
    <TitledPane text="Chi tiết test case đã chọn" expanded="false">
        <GridPane hgap="10" vgap="8">
            <Insets topRightBottomLeft="10"/>
            <Label text="Input:" GridPane.columnIndex="0" GridPane.rowIndex="0"/>
            <TextArea fx:id="detailInputArea" prefRowCount="4" wrapText="true"
                      GridPane.columnIndex="1" GridPane.rowIndex="0"/>
            <Label text="Expected:" GridPane.columnIndex="0" GridPane.rowIndex="1"/>
            <TextArea fx:id="detailExpectedArea" prefRowCount="3" wrapText="true"
                      GridPane.columnIndex="1" GridPane.rowIndex="1"/>
            <Label text="Actual:" GridPane.columnIndex="0" GridPane.rowIndex="2"/>
            <TextArea fx:id="detailActualArea" editable="false" prefRowCount="3"
                      wrapText="true"
                      GridPane.columnIndex="1" GridPane.rowIndex="2"/>
        </GridPane>
    </TitledPane>
</VBox>
```

---

## 4. `src/main/resources/fxml/result_view.fxml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.control.*?>
<?import javafx.geometry.*?>

<VBox xmlns:fx="http://javafx.com/fxml"
      fx:controller="org.example.ui.controller.ResultController"
      spacing="10" styleClass="tab-content">
    <Insets topRightBottomLeft="15"/>

    <HBox spacing="10" alignment="CENTER_LEFT">
        <VBox spacing="5" HBox.hgrow="ALWAYS">
            <Label text="Sinh code mẫu" styleClass="section-title"/>
            <HBox spacing="10">
                <ComboBox fx:id="codeTypeCombo" promptText="Loại code"
                          prefWidth="120"/>
                <ComboBox fx:id="languageCombo" promptText="Ngôn ngữ"
                          prefWidth="120"/>
                <Button text="🤖 Sinh code" onAction="#onGenerateCode"
                        styleClass="btn-primary"/>
            </HBox>
        </VBox>
    </HBox>

    <SplitPane orientation="HORIZONTAL" VBox.vgrow="ALWAYS">
        <!-- Code editor -->
        <VBox spacing="5" minWidth="400">
            <Label text="Source Code:" styleClass="field-label"/>
            <TextArea fx:id="codeArea" styleClass="code-editor"
                      VBox.vgrow="ALWAYS" wrapText="false"/>
            <HBox spacing="10">
                <Button text="▶ Chạy tất cả test" onAction="#onRunAllTests"
                        styleClass="btn-primary" HBox.hgrow="ALWAYS"
                        maxWidth="Infinity"/>
                <Button text="📄 Xuất báo cáo" onAction="#onExportReport"
                        styleClass="btn-secondary"/>
            </HBox>
        </VBox>

        <!-- Results -->
        <VBox spacing="5" minWidth="300">
            <HBox spacing="10" alignment="CENTER_LEFT">
                <Label text="Kết quả:" styleClass="field-label"/>
                <Label fx:id="summaryLabel" text="—" styleClass="summary-label"/>
            </HBox>
            <TableView fx:id="resultTable" VBox.vgrow="ALWAYS">
                <columns>
                    <TableColumn fx:id="rTcIdCol" text="TC" prefWidth="60"/>
                    <TableColumn fx:id="rVerdictCol" text="Verdict" prefWidth="70"/>
                    <TableColumn fx:id="rTimeCol" text="Time" prefWidth="70"/>
                    <TableColumn fx:id="rDiffCol" text="Diff" prefWidth="120"/>
                </columns>
            </TableView>
            <Label text="Explanation:" styleClass="field-label"/>
            <TextArea fx:id="explanationArea" editable="false"
                      prefRowCount="4" wrapText="true"/>
        </VBox>
    </SplitPane>
</VBox>
```

---

## 5. `src/main/resources/css/style.css`

```css
.root-pane { -fx-background-color: #1e1e2e; }

.top-bar {
    -fx-background-color: #181825;
    -fx-border-color: #313244;
    -fx-border-width: 0 0 1 0;
    -fx-padding: 10;
}

.app-title {
    -fx-text-fill: #cdd6f4;
    -fx-font-size: 16px;
    -fx-font-weight: bold;
}

.sidebar {
    -fx-background-color: #181825;
    -fx-border-color: #313244;
    -fx-border-width: 0 1 0 0;
}

.section-title {
    -fx-text-fill: #89b4fa;
    -fx-font-size: 14px;
    -fx-font-weight: bold;
}

.field-label { -fx-text-fill: #a6adc8; -fx-font-size: 12px; }

.tab-content { -fx-background-color: #1e1e2e; -fx-padding: 15; }

.btn-primary {
    -fx-background-color: #89b4fa;
    -fx-text-fill: #1e1e2e;
    -fx-font-weight: bold;
    -fx-cursor: hand;
}
.btn-primary:hover { -fx-background-color: #b4befe; }

.btn-secondary {
    -fx-background-color: #313244;
    -fx-text-fill: #cdd6f4;
    -fx-cursor: hand;
}
.btn-secondary:hover { -fx-background-color: #45475a; }

.btn-danger {
    -fx-background-color: #f38ba8;
    -fx-text-fill: #1e1e2e;
    -fx-cursor: hand;
}

.status-online { -fx-text-fill: #a6e3a1; }
.status-offline { -fx-text-fill: #f38ba8; }

.status-bar {
    -fx-background-color: #181825;
    -fx-border-color: #313244;
    -fx-border-width: 1 0 0 0;
    -fx-padding: 5;
}

.code-editor {
    -fx-font-family: "JetBrains Mono", "Consolas", monospace;
    -fx-font-size: 13px;
    -fx-background-color: #11111b;
    -fx-text-fill: #cdd6f4;
}

.table-view {
    -fx-background-color: #181825;
    -fx-border-color: #313244;
}

.table-row-cell:selected { -fx-background-color: #313244; }
```

---

## 6. `MainController.java` — skeleton

```java
package org.example.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.FileChooser;
import org.example.model.*;
import org.example.service.AIBridgeService;
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
    @FXML private RadioButton textModeBtn, imageModeBtn;
    @FXML private Spinner<Integer> testCaseCountSpinner;
    @FXML private CheckBox edgeCasesCheck;
    @FXML private TabPane mainTabPane;

    // Sub-controllers injected by FXML include
    @FXML private ProblemInputController problemInputViewController;
    @FXML private TestCaseController testcaseViewController;
    @FXML private ResultController resultViewController;

    private final AIBridgeService aiService = new AIBridgeService();
    private Problem currentProblem;
    private String selectedImageBase64;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupToggleListeners();
        checkServiceAsync();
    }

    private void setupToggleListeners() {
        imageModeBtn.selectedProperty().addListener((obs, o, n) -> {
            imageInputBox.setVisible(n);
            imageInputBox.setManaged(n);
            problemTextArea.setVisible(!n);
            problemTextArea.setManaged(!n);
        });
    }

    @FXML
    private void onCheckService() {
        checkServiceAsync();
    }

    private void checkServiceAsync() {
        setStatus("Đang kiểm tra kết nối Python service...");
        CompletableFuture.supplyAsync(aiService::isServiceAlive)
            .thenAcceptAsync(alive -> {
                if (alive) {
                    serviceStatusLabel.setText("● Online");
                    serviceStatusLabel.getStyleClass().setAll("status-online");
                } else {
                    serviceStatusLabel.setText("● Offline");
                    serviceStatusLabel.getStyleClass().setAll("status-offline");
                }
                setStatus(alive ? "Python AI Service: Connected" : "Python AI Service: Offline — Hãy chạy ai_service/main.py");
            }, Platform::runLater);
    }

    @FXML
    private void onSelectImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh đề bài");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );
        File file = fc.showOpenDialog(null);
        if (file != null) {
            try {
                selectedImageBase64 = FileUtil.toBase64(file);
                imagePreview.setImage(new Image(file.toURI().toString()));
                setStatus("Đã chọn ảnh: " + file.getName());
            } catch (Exception e) {
                showError("Lỗi đọc ảnh: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onAnalyze() {
        setLoading(true);
        setStatus("Đang phân tích đề bài...");

        CompletableFuture.supplyAsync(() -> {
            try {
                if (imageModeBtn.isSelected()) {
                    return aiService.analyzeProblemImage(selectedImageBase64);
                } else {
                    return aiService.analyzeProblemText(problemTextArea.getText());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(problem -> {
            currentProblem = problem;
            problemInputViewController.displayProblem(problem);
            mainTabPane.getSelectionModel().select(0);

            // Sinh test cases
            setStatus("Đang sinh test cases...");
            try {
                int count = testCaseCountSpinner.getValue();
                boolean edgeCases = edgeCasesCheck.isSelected();
                List<TestCase> testCases = aiService.generateTestCases(
                    problem, count, edgeCases
                );
                testcaseViewController.setTestCases(testCases);
                resultViewController.setProblem(problem);
                mainTabPane.getSelectionModel().select(1);
                setStatus("✅ Phân tích xong — " + testCases.size() + " test cases đã tạo");
            } catch (Exception e) {
                showError("Lỗi sinh test case: " + e.getMessage());
            }
            setLoading(false);
        }, Platform::runLater)
        .exceptionally(ex -> {
            Platform.runLater(() -> {
                showError("Lỗi: " + ex.getCause().getMessage());
                setLoading(false);
            });
            return null;
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisible(loading);
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.showAndWait();
        setStatus("Lỗi: " + msg);
    }
}
```

---

## 7. `ProblemInputController.java`

```java
package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.model.Problem;

public class ProblemInputController {
    @FXML private TextField titleField;
    @FXML private TextArea inputFormatArea, outputFormatArea, descriptionArea;
    @FXML private ListView<String> constraintsList;

    public void displayProblem(Problem p) {
        titleField.setText(p.getTitle());
        inputFormatArea.setText(p.getInputFormat());
        outputFormatArea.setText(p.getOutputFormat());
        descriptionArea.setText(p.getDescription());
        constraintsList.getItems().setAll(
            p.getConstraints() != null ? p.getConstraints() : java.util.List.of()
        );
    }
}
```

---

## Checklist

- [ ] 4 FXML files tạo xong, load không lỗi
- [ ] `style.css` áp dụng đúng
- [ ] `MainController` khởi tạo, service status hiển thị
- [ ] Toggle Text/Image input hoạt động
- [ ] `ProblemInputController.displayProblem()` hiển thị đúng
- [ ] `mvn javafx:run` — giao diện đẹp, không lỗi CSS
