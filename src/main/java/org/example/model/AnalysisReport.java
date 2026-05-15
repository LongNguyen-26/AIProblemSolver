package org.example.model;

import java.time.LocalDateTime;
import java.util.List;

public class AnalysisReport {
    private Problem problem;
    private List<TestCase> testCases;
    private CodeSubmission submission;
    private LocalDateTime generatedAt;
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private long avgExecutionTimeMs;
    private String overallVerdict;

    public AnalysisReport() {
        this.generatedAt = LocalDateTime.now();
    }

    public Problem getProblem() {
        return problem;
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
    }

    public List<TestCase> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<TestCase> testCases) {
        this.testCases = testCases;
        if (testCases != null) {
            this.totalTests = testCases.size();
            this.passedTests = (int) testCases.stream()
                    .filter(testCase -> "AC".equals(testCase.getVerdict()))
                    .count();
            this.failedTests = totalTests - passedTests;
            this.avgExecutionTimeMs = (long) testCases.stream()
                    .mapToLong(TestCase::getExecutionTimeMs)
                    .average()
                    .orElse(0);
        } else {
            this.totalTests = 0;
            this.passedTests = 0;
            this.failedTests = 0;
            this.avgExecutionTimeMs = 0;
        }
    }

    public CodeSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(CodeSubmission submission) {
        this.submission = submission;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public int getTotalTests() {
        return totalTests;
    }

    public int getPassedTests() {
        return passedTests;
    }

    public int getFailedTests() {
        return failedTests;
    }

    public long getAvgExecutionTimeMs() {
        return avgExecutionTimeMs;
    }

    public String getOverallVerdict() {
        return overallVerdict;
    }

    public void setOverallVerdict(String overallVerdict) {
        this.overallVerdict = overallVerdict;
    }
}
