package org.example.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Problem {
    private String title;
    private String description;
    @SerializedName("input_format")
    private String inputFormat;
    @SerializedName("output_format")
    private String outputFormat;
    private List<String> constraints;
    @SerializedName("sample_inputs")
    private List<String> sampleInputs;
    @SerializedName("sample_outputs")
    private List<String> sampleOutputs;
    @SerializedName("problem_type")
    private String problemType;
    @SerializedName("secondary_type")
    private String secondaryType;
    @SerializedName("type_confidence")
    private double typeConfidence;
    @SerializedName("tle_strategy")
    private String tleStrategy;
    @SerializedName("max_constraint_n")
    private Integer maxConstraintN;
    @SerializedName("is_small_n")
    private Boolean isSmallN;

    public Problem() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInputFormat() {
        return inputFormat;
    }

    public void setInputFormat(String inputFormat) {
        this.inputFormat = inputFormat;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<String> constraints) {
        this.constraints = constraints;
    }

    public List<String> getSampleInputs() {
        return sampleInputs;
    }

    public void setSampleInputs(List<String> sampleInputs) {
        this.sampleInputs = sampleInputs;
    }

    public List<String> getSampleOutputs() {
        return sampleOutputs;
    }

    public void setSampleOutputs(List<String> sampleOutputs) {
        this.sampleOutputs = sampleOutputs;
    }

    public String getProblemType() {
        return problemType;
    }

    public void setProblemType(String problemType) {
        this.problemType = problemType;
    }

    public String getSecondaryType() {
        return secondaryType;
    }

    public void setSecondaryType(String secondaryType) {
        this.secondaryType = secondaryType;
    }

    public double getTypeConfidence() {
        return typeConfidence;
    }

    public void setTypeConfidence(double typeConfidence) {
        this.typeConfidence = typeConfidence;
    }

    public String getTleStrategy() {
        return tleStrategy;
    }

    public void setTleStrategy(String tleStrategy) {
        this.tleStrategy = tleStrategy;
    }

    public Integer getMaxConstraintN() {
        return maxConstraintN;
    }

    public void setMaxConstraintN(Integer maxConstraintN) {
        this.maxConstraintN = maxConstraintN;
    }

    public boolean isSmallN() {
        return Boolean.TRUE.equals(isSmallN);
    }

    public void setSmallN(Boolean smallN) {
        isSmallN = smallN;
    }

    @Override
    public String toString() {
        return "Problem{title='" + title + "'}";
    }
}
