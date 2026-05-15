package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.model.CodeSubmission;
import org.example.model.Problem;
import org.example.model.TestCase;
import org.example.util.AppConfig;
import org.example.util.HttpUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AIBridgeService {
    private final String baseUrl;
    private final Gson gson = new Gson();

    public AIBridgeService() {
        this(AppConfig.get("ai.service.baseUrl"));
    }

    public AIBridgeService(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public boolean isServiceAlive() {
        try {
            String result = HttpUtil.getString(baseUrl + "/health");
            return result.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }

    public Problem analyzeProblemText(String text) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("text", text);

        JsonObject response = HttpUtil.postJson(baseUrl + "/analyze", body, JsonObject.class);
        return gson.fromJson(response.getAsJsonObject("problem"), Problem.class);
    }

    public Problem analyzeProblemImage(String base64Image) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("image_base64", base64Image);

        JsonObject response = HttpUtil.postJson(baseUrl + "/analyze", body, JsonObject.class);
        return gson.fromJson(response.getAsJsonObject("problem"), Problem.class);
    }

    public List<TestCase> generateTestCases(Problem problem, int count,
                                            boolean includeEdgeCases) throws Exception {
        return generateTestCases(problem, count, includeEdgeCases, "SMALL");
    }

    public List<TestCase> generateTestCases(Problem problem, int count,
                                            boolean includeEdgeCases,
                                            String profile) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        body.put("count", count);
        body.put("include_edge_cases", includeEdgeCases);
        body.put("profile", profile == null || profile.isBlank() ? "SMALL" : profile);

        JsonObject response = HttpUtil.postJson(baseUrl + "/testcase", body, JsonObject.class);
        JsonArray testCaseArray = response.getAsJsonArray("testcases");
        List<TestCase> testCases = new ArrayList<>();
        if (testCaseArray == null) {
            return testCases;
        }
        for (JsonElement element : testCaseArray) {
            testCases.add(gson.fromJson(element, TestCase.class));
        }
        return testCases;
    }

    public CodeSubmission generateCode(Problem problem, String type,
                                       String language) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        body.put("type", type);
        body.put("language", language);

        return HttpUtil.postJson(baseUrl + "/codegen", body, CodeSubmission.class);
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8000";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
