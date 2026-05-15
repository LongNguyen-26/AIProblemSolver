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

    @Override
    public String toString() {
        return "Problem{title='" + title + "'}";
    }
}
