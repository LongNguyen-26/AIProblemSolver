package org.example.model;

import com.google.gson.annotations.SerializedName;

public class StressResult {
    private boolean trusted;
    @SerializedName("trusted_oracle_code")
    private String trustedOracleCode;
    @SerializedName("trusted_oracle_language")
    private String trustedOracleLanguage;
    @SerializedName("found_counterexample")
    private boolean foundCounterexample;
    @SerializedName("counterexample_input")
    private String counterexampleInput;
    @SerializedName("brute_output")
    private String bruteOutput;
    @SerializedName("optimized_output")
    private String optimizedOutput;
    @SerializedName("rounds_completed")
    private int roundsCompleted;
    @SerializedName("oracle_retries")
    private int oracleRetries;
    @SerializedName("generator_trusted")
    private boolean generatorTrusted;
    private String message;
    @SerializedName("problem_type")
    private String problemType;

    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    public String getTrustedOracleCode() {
        return trustedOracleCode;
    }

    public void setTrustedOracleCode(String trustedOracleCode) {
        this.trustedOracleCode = trustedOracleCode;
    }

    public String getTrustedOracleLanguage() {
        return trustedOracleLanguage;
    }

    public void setTrustedOracleLanguage(String trustedOracleLanguage) {
        this.trustedOracleLanguage = trustedOracleLanguage;
    }

    public boolean isFoundCounterexample() {
        return foundCounterexample;
    }

    public void setFoundCounterexample(boolean foundCounterexample) {
        this.foundCounterexample = foundCounterexample;
    }

    public String getCounterexampleInput() {
        return counterexampleInput;
    }

    public void setCounterexampleInput(String counterexampleInput) {
        this.counterexampleInput = counterexampleInput;
    }

    public String getBruteOutput() {
        return bruteOutput;
    }

    public void setBruteOutput(String bruteOutput) {
        this.bruteOutput = bruteOutput;
    }

    public String getOptimizedOutput() {
        return optimizedOutput;
    }

    public void setOptimizedOutput(String optimizedOutput) {
        this.optimizedOutput = optimizedOutput;
    }

    public int getRoundsCompleted() {
        return roundsCompleted;
    }

    public void setRoundsCompleted(int roundsCompleted) {
        this.roundsCompleted = roundsCompleted;
    }

    public int getOracleRetries() {
        return oracleRetries;
    }

    public void setOracleRetries(int oracleRetries) {
        this.oracleRetries = oracleRetries;
    }

    public boolean isGeneratorTrusted() {
        return generatorTrusted;
    }

    public void setGeneratorTrusted(boolean generatorTrusted) {
        this.generatorTrusted = generatorTrusted;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProblemType() {
        return problemType;
    }

    public void setProblemType(String problemType) {
        this.problemType = problemType;
    }
}
