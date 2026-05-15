package org.example.service;

import org.example.model.AnalysisReport;
import org.example.model.CodeSubmission;
import org.example.model.Problem;
import org.example.model.TestCase;
import org.example.util.FileUtil;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ReportService {
    public String exportHtmlReport(AnalysisReport report) throws IOException {
        if (report == null) {
            throw new IllegalArgumentException("Report must not be null");
        }
        String filename = "report_" + System.currentTimeMillis() + ".html";
        FileUtil.writeString(filename, buildHtml(report));
        return filename;
    }

    public String exportTestCasesZip(List<TestCase> testCases) throws IOException {
        String zipPath = "testcases_" + System.currentTimeMillis() + ".zip";
        try (ZipOutputStream zip = new ZipOutputStream(
                new FileOutputStream(zipPath), StandardCharsets.UTF_8)) {
            int index = 1;
            for (TestCase testCase : testCases == null ? List.<TestCase>of() : testCases) {
                String suffix = String.format("%03d", index++);
                addZipEntry(zip, "input_" + suffix + ".txt", testCase.getInput());
                addZipEntry(zip, "output_" + suffix + ".txt", testCase.getExpectedOutput());
            }
        }
        return zipPath;
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String buildHtml(AnalysisReport report) {
        Problem problem = report.getProblem();
        CodeSubmission submission = report.getSubmission();
        List<TestCase> testCases = report.getTestCases() == null ? List.of() : report.getTestCases();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>AIProblemSolver Report</title>
                    <style>
                        body {
                            margin: 0;
                            font-family: "Segoe UI", Arial, sans-serif;
                            background: #f5f2ea;
                            color: #202224;
                        }
                        main {
                            max-width: 1080px;
                            margin: 0 auto;
                            padding: 32px 24px;
                        }
                        h1, h2 { margin: 0 0 12px; }
                        h1 { font-size: 28px; }
                        h2 {
                            margin-top: 28px;
                            padding-bottom: 8px;
                            border-bottom: 1px solid #d6d0c4;
                            color: #275c56;
                        }
                        .muted { color: #66645f; }
                        .stat-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                            gap: 12px;
                            margin: 20px 0;
                        }
                        .stat-card {
                            background: #ffffff;
                            border: 1px solid #ddd6c8;
                            border-radius: 8px;
                            padding: 16px;
                        }
                        .stat-value {
                            font-size: 26px;
                            font-weight: 700;
                        }
                        .ac { color: #287a45; }
                        .wa, .re { color: #b23b42; }
                        .tle { color: #9a6515; }
                        .ce { color: #6744a0; }
                        table {
                            width: 100%;
                            border-collapse: collapse;
                            background: #ffffff;
                            border: 1px solid #ddd6c8;
                        }
                        th, td {
                            padding: 9px 10px;
                            border-bottom: 1px solid #e5ded1;
                            text-align: left;
                            vertical-align: top;
                        }
                        th {
                            background: #e8e2d6;
                            color: #202224;
                        }
                        pre {
                            background: #151719;
                            color: #f4f1eb;
                            padding: 16px;
                            border-radius: 8px;
                            overflow-x: auto;
                            white-space: pre-wrap;
                        }
                        ul { margin-top: 8px; }
                    </style>
                </head>
                <body>
                <main>
                """);

        html.append("<h1>AIProblemSolver Report: ")
                .append(escape(problem == null ? "Untitled problem" : problem.getTitle()))
                .append("</h1>\n");
        html.append("<p class=\"muted\">Generated at ")
                .append(report.getGeneratedAt().format(formatter))
                .append("</p>\n");

        html.append("<section class=\"stat-grid\">")
                .append(statCard("Total", String.valueOf(report.getTotalTests()), ""))
                .append(statCard("Accepted", String.valueOf(report.getPassedTests()), "ac"))
                .append(statCard("Failed", String.valueOf(report.getFailedTests()), "wa"))
                .append(statCard("Average Time", report.getAvgExecutionTimeMs() + " ms", ""))
                .append("</section>\n");

        appendProblem(html, problem);
        appendTestResults(html, testCases);
        appendSubmission(html, submission);

        html.append("""
                </main>
                </body>
                </html>
                """);
        return html.toString();
    }

    private void appendProblem(StringBuilder html, Problem problem) {
        if (problem == null) {
            return;
        }
        html.append("<h2>Problem</h2>\n");
        html.append("<p>").append(escape(problem.getDescription())).append("</p>\n");
        html.append("<p><strong>Input format:</strong><br>")
                .append(escape(problem.getInputFormat()))
                .append("</p>\n");
        html.append("<p><strong>Output format:</strong><br>")
                .append(escape(problem.getOutputFormat()))
                .append("</p>\n");
        if (problem.getConstraints() != null && !problem.getConstraints().isEmpty()) {
            html.append("<strong>Constraints:</strong><ul>\n");
            for (String constraint : problem.getConstraints()) {
                html.append("<li>").append(escape(constraint)).append("</li>\n");
            }
            html.append("</ul>\n");
        }
    }

    private void appendTestResults(StringBuilder html, List<TestCase> testCases) {
        html.append("<h2>Test Results</h2>\n");
        html.append("<table><thead><tr>")
                .append("<th>ID</th><th>Verdict</th><th>Time</th><th>Edge</th><th>Description</th>")
                .append("</tr></thead><tbody>\n");
        for (TestCase testCase : testCases) {
            String verdict = testCase.getVerdict() == null ? "" : testCase.getVerdict();
            html.append("<tr>")
                    .append("<td>").append(escape(testCase.getId())).append("</td>")
                    .append("<td class=\"").append(escape(verdict.toLowerCase())).append("\"><strong>")
                    .append(escape(verdict)).append("</strong></td>")
                    .append("<td>").append(testCase.getExecutionTimeMs()).append(" ms</td>")
                    .append("<td>").append(testCase.isEdgeCase() ? "Yes" : "No").append("</td>")
                    .append("<td>").append(escape(testCase.getDescription())).append("</td>")
                    .append("</tr>\n");
        }
        html.append("</tbody></table>\n");
    }

    private void appendSubmission(StringBuilder html, CodeSubmission submission) {
        if (submission == null) {
            return;
        }
        html.append("<h2>Submission</h2>\n")
                .append("<p><strong>Language:</strong> ").append(escape(submission.getLanguage()))
                .append(" &nbsp; <strong>Type:</strong> ").append(escape(submission.getType()))
                .append("</p>\n");
        if (submission.getExplanation() != null && !submission.getExplanation().isBlank()) {
            html.append("<p>").append(escape(submission.getExplanation())).append("</p>\n");
        }
        html.append("<pre>").append(escape(submission.getCode())).append("</pre>\n");
    }

    private String statCard(String label, String value, String cssClass) {
        return "<article class=\"stat-card\"><div class=\"stat-value " + cssClass + "\">"
                + escape(value)
                + "</div><div class=\"muted\">"
                + escape(label)
                + "</div></article>";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
