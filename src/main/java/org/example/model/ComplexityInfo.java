package org.example.model;

import com.google.gson.annotations.SerializedName;

public class ComplexityInfo {
    @SerializedName("optimal_complexity")
    private String optimalComplexity;
    @SerializedName("tle_target_complexity")
    private String tleTargetComplexity;
    @SerializedName("max_n")
    private int maxN;
    @SerializedName("tle_strategy")
    private String tleStrategy;
    @SerializedName("tle_explanation")
    private String tleExplanation;

    public String getOptimalComplexity() {
        return optimalComplexity;
    }

    public void setOptimalComplexity(String optimalComplexity) {
        this.optimalComplexity = optimalComplexity;
    }

    public String getTleTargetComplexity() {
        return tleTargetComplexity;
    }

    public void setTleTargetComplexity(String tleTargetComplexity) {
        this.tleTargetComplexity = tleTargetComplexity;
    }

    public int getMaxN() {
        return maxN;
    }

    public void setMaxN(int maxN) {
        this.maxN = maxN;
    }

    public String getTleStrategy() {
        return tleStrategy;
    }

    public void setTleStrategy(String tleStrategy) {
        this.tleStrategy = tleStrategy;
    }

    public String getTleExplanation() {
        return tleExplanation;
    }

    public void setTleExplanation(String tleExplanation) {
        this.tleExplanation = tleExplanation;
    }
}
