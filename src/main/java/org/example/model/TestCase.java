package org.example.model;

import com.google.gson.annotations.SerializedName;

public class TestCase {
    private String id;
    private String input;
    @SerializedName("expected_output")
    private String expectedOutput;
    private String description;
    @SerializedName(value = "is_edge_case", alternate = {"edgeCase"})
    private boolean edgeCase;
    private String verdict;
    private long executionTimeMs;
    private String actualOutput;

    public TestCase() {
    }

    public TestCase(String id, String input, String expectedOutput, String description, boolean edgeCase) {
        this.id = id;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.description = description;
        this.edgeCase = edgeCase;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEdgeCase() {
        return edgeCase;
    }

    public void setEdgeCase(boolean edgeCase) {
        this.edgeCase = edgeCase;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public void setActualOutput(String actualOutput) {
        this.actualOutput = actualOutput;
    }
}
