# TASK_06_INTEGRATION.md

## Mục tiêu
Kết nối end-to-end, hoàn thiện `ReportService`, export ZIP test cases, và viết báo cáo đánh giá.

---

## 1. Kết nối TestCaseController ↔ ResultController

Trong `MainController.java`, thêm sau khi testcases load xong:

```java
// Trong onAnalyze() sau khi generate testcases thành công:
resultViewController.setTestCases(testCases);
```

Và thêm method để sync khi user edit test case thủ công:

```java
// MainController.java
public void onTestCasesUpdated(List<TestCase> updated) {
    resultViewController.setTestCases(updated);
}
```

---

## 2. `ReportService.java`

```java
package org.example.service;

import org.example.model.*;
import org.example.util.FileUtil;

import java.io.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.*;

public class ReportService {

    /**
     * Xuất báo cáo HTML ra file.
     * @return path tới file HTML đã tạo
     */
    public String exportHtmlReport(AnalysisReport report) throws IOException {
        String filename = "report_" + System.currentTimeMillis() + ".html";
        String html = buildHtml(report);
        FileUtil.writeString(filename, html);
        return filename;
    }

    /**
     * Export tất cả test cases ra file ZIP: input_001.txt / output_001.txt
     */
    public String exportTestCasesZip(List<TestCase> testCases) throws IOException {
        String zipPath = "testcases_" + System.currentTimeMillis() + ".zip";
        try (ZipOutputStream zos = new ZipOutputStream(
                new FileOutputStream(zipPath))) {

            int idx = 1;
            for (TestCase tc : testCases) {
                String name = String.format("%03d", idx++);
                addZipEntry(zos, "input_" + name + ".txt", tc.getInput());
                addZipEntry(zos, "output_" + name + ".txt", tc.getExpectedOutput());
            }
        }
        return zipPath;
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content)
            throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content != null ? content.getBytes() : new byte[0]);
        zos.closeEntry();
    }

    private String buildHtml(AnalysisReport report) {
        Problem p = report.getProblem();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder sb = new StringBuilder();

        sb.append("""
            <!DOCTYPE html>
            <html lang="vi">
            <head>
            <meta charset="UTF-8">
            <title>AIProblemSolver Report</title>
            <style>
              body { font-family: 'Segoe UI', sans-serif; background:#1e1e2e; color:#cdd6f4;
                     max-width:900px; margin:0 auto; padding:2rem; }
              h1   { color:#89b4fa; }
              h2   { color:#cba6f7; border-bottom:1px solid #313244; padding-bottom:5px; }
              .stat-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:1rem; margin:1rem 0; }
              .stat-card { background:#181825; border-radius:8px; padding:1rem; text-align:center; }
              .stat-value { font-size:2rem; font-weight:bold; }
              .ac  { color:#a6e3a1; }
              .wa  { color:#f38ba8; }
              .tle { color:#fab387; }
              .ce  { color:#cba6f7; }
              table { width:100%; border-collapse:collapse; margin:1rem 0; }
              th, td { padding:8px 12px; border:1px solid #313244; text-align:left; }
              th { background:#181825; color:#89b4fa; }
              pre { background:#11111b; padding:1rem; border-radius:6px;
                    overflow-x:auto; font-size:12px; }
            </style>
            </head>
            <body>
            """);

        sb.append("<h1>📊 Báo cáo phân tích: ").append(esc(p.getTitle())).append("</h1>\n");
        sb.append("<p>Thời gian: ").append(report.getGeneratedAt().format(fmt)).append("</p>\n");

        // Stats
        sb.append("<div class='stat-grid'>");
        sb.append(statCard("Total", String.valueOf(report.getTotalTests()), ""));
        sb.append(statCard("AC", String.valueOf(report.getPassedTests()), "ac"));
        sb.append(statCard("Failed", String.valueOf(report.getFailedTests()), "wa"));
        sb.append(statCard("Avg Time", report.getAvgExecutionTimeMs() + " ms", ""));
        sb.append("</div>\n");

        // Problem info
        sb.append("<h2>Thông tin bài toán</h2>\n");
        sb.append("<p><strong>Mô tả:</strong> ").append(esc(p.getDescription())).append("</p>\n");
        if (p.getConstraints() != null) {
            sb.append("<ul>");
            for (String c : p.getConstraints()) sb.append("<li>").append(esc(c)).append("</li>");
            sb.append("</ul>\n");
        }

        // Test case results
        sb.append("<h2>Kết quả Test Cases</h2>\n");
        sb.append("<table><tr><th>ID</th><th>Verdict</th><th>Time</th><th>Edge?</th><th>Mô tả</th></tr>\n");
        for (TestCase tc : report.getTestCases()) {
            String cls = tc.getVerdict() != null ? tc.getVerdict().toLowerCase() : "";
            sb.append("<tr>")
              .append("<td>").append(esc(tc.getId())).append("</td>")
              .append("<td class='").append(cls).append("'><strong>")
              .append(esc(tc.getVerdict())).append("</strong></td>")
              .append("<td>").append(tc.getExecutionTimeMs()).append(" ms</td>")
              .append("<td>").append(tc.isEdgeCase() ? "✓" : "").append("</td>")
              .append("<td>").append(esc(tc.getDescription())).append("</td>")
              .append("</tr>\n");
        }
        sb.append("</table>\n");

        // Code
        if (report.getSubmission() != null) {
            sb.append("<h2>Code mẫu (").append(report.getSubmission().getType()).append(")</h2>\n");
            sb.append("<pre>").append(esc(report.getSubmission().getCode())).append("</pre>\n");
            if (report.getSubmission().getExplanation() != null) {
                sb.append("<p><em>").append(esc(report.getSubmission().getExplanation()))
                  .append("</em></p>\n");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String statCard(String label, String value, String cls) {
        return "<div class='stat-card'><div class='stat-value " + cls + "'>"
             + value + "</div><div>" + label + "</div></div>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

---

## 3. Hoàn thiện `ResultController.onExportReport()`

```java
@FXML
private void onExportReport() {
    if (currentProblem == null || currentTestCases == null) {
        new Alert(Alert.AlertType.WARNING, "Chưa có dữ liệu để xuất báo cáo").showAndWait();
        return;
    }

    AnalysisReport report = new AnalysisReport();
    report.setProblem(currentProblem);
    report.setTestCases(currentTestCases);

    CodeSubmission sub = new CodeSubmission(
        codeArea.getText(), languageCombo.getValue(), codeTypeCombo.getValue()
    );
    sub.setExplanation(explanationArea.getText());
    report.setSubmission(sub);

    ReportService reportService = new ReportService();

    // Chọn nơi lưu
    javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
    fc.setTitle("Lưu báo cáo HTML");
    fc.getExtensionFilters().add(
        new javafx.stage.FileChooser.ExtensionFilter("HTML", "*.html")
    );
    fc.setInitialFileName("report_" + currentProblem.getTitle() + ".html");
    java.io.File dest = fc.showSaveDialog(null);

    if (dest != null) {
        try {
            String tmpPath = reportService.exportHtmlReport(report);
            java.nio.file.Files.move(
                java.nio.file.Paths.get(tmpPath),
                dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            new Alert(Alert.AlertType.INFORMATION,
                "Đã lưu báo cáo: " + dest.getAbsolutePath()).showAndWait();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Lỗi: " + e.getMessage()).showAndWait();
        }
    }
}
```

---

## 4. Hoàn thiện `TestCaseController.onExportZip()`

```java
@FXML
private void onExportZip() {
    ReportService reportService = new ReportService();
    javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
    fc.setTitle("Lưu test cases ZIP");
    fc.getExtensionFilters().add(
        new javafx.stage.FileChooser.ExtensionFilter("ZIP", "*.zip")
    );
    fc.setInitialFileName("testcases.zip");
    java.io.File dest = fc.showSaveDialog(null);

    if (dest != null) {
        try {
            String tmpPath = reportService.exportTestCasesZip(testCaseData);
            java.nio.file.Files.move(
                java.nio.file.Paths.get(tmpPath),
                dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            new Alert(Alert.AlertType.INFORMATION,
                "Đã xuất " + testCaseData.size() + " test cases").showAndWait();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Lỗi: " + e.getMessage()).showAndWait();
        }
    }
}
```

---

## 5. End-to-end test checklist

### Kịch bản 1: Text input
1. Paste đề bài Two Sum vào TextArea
2. Click "Phân tích đề"
3. Tab "Phân tích" hiển thị đúng title, constraints
4. Tab "Test Cases" có 10 test case
5. Tab "Code" → Sinh AC code C++
6. "Chạy tất cả test" → tất cả AC ✅
7. "Xuất báo cáo" → file HTML đúng

### Kịch bản 2: Ảnh input
1. Chọn ảnh đề bài
2. Phân tích → OCR hoạt động
3. Test cases sinh ra hợp lý

### Kịch bản 3: WA code
1. Sinh WA code
2. Chạy test → một số WA, diff hiển thị

### Kịch bản 4: TLE code
1. Sinh TLE code
2. Chạy test → một số TLE (>5s timeout)

---

## Checklist hoàn thiện

- [ ] `ReportService.exportHtmlReport()` tạo HTML đẹp
- [ ] `ReportService.exportTestCasesZip()` tạo ZIP đúng format
- [ ] `ResultController.onExportReport()` FileChooser hoạt động
- [ ] `TestCaseController.onExportZip()` hoạt động
- [ ] End-to-end kịch bản 1 pass
- [ ] End-to-end kịch bản 3 (WA) pass
- [ ] End-to-end kịch bản 4 (TLE) pass
- [ ] Không có memory leak (ExecutorService shutdown đúng)
- [ ] Giao diện không đơ khi chạy AI call (async đúng)
