package org.example.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PipelineProgress {
    private String state;
    private String message;
    @SerializedName("progress_pct")
    private int progressPct;
    @SerializedName("testcases_ready")
    private int testcasesReady;
    @SerializedName("testcases_target")
    private int testcasesTarget;
    private List<String> warnings;
    private Problem problem;
    @SerializedName("all_testcases")
    private List<TestCase> allTestCases;
    private boolean cached;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(int progressPct) {
        this.progressPct = progressPct;
    }

    public int getTestcasesReady() {
        return testcasesReady;
    }

    public void setTestcasesReady(int testcasesReady) {
        this.testcasesReady = testcasesReady;
    }

    public int getTestcasesTarget() {
        return testcasesTarget;
    }

    public void setTestcasesTarget(int testcasesTarget) {
        this.testcasesTarget = testcasesTarget;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Problem getProblem() {
        return problem;
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
    }

    public List<TestCase> getAllTestCases() {
        return allTestCases;
    }

    public void setAllTestCases(List<TestCase> allTestCases) {
        this.allTestCases = allTestCases;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }
}
