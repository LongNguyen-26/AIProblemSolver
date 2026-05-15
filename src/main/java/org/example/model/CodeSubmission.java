package org.example.model;

public class CodeSubmission {
    private String code;
    private String language;
    private String type;
    private String explanation;

    public CodeSubmission() {
    }

    public CodeSubmission(String code, String language, String type) {
        this.code = code;
        this.language = language;
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
