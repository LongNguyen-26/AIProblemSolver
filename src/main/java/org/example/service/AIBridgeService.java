package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.model.CodeSubmission;
import org.example.model.Problem;
import org.example.model.StressResult;
import org.example.model.TestCase;
import org.example.util.AppConfig;
import org.example.util.HttpUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AIBridgeService {
    private static final int MAX_TESTCASE_ATTEMPTS = 3;

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
        return generateTestCases(problem, count, includeEdgeCases, profile, List.of());
    }

    public List<TestCase> generateTestCases(Problem problem, int count,
                                            boolean includeEdgeCases,
                                            String profile,
                                            List<String> existingInputs) throws Exception {
        if (count <= 0) {
            return List.of();
        }

        String normalizedProfile = profile == null || profile.isBlank() ? "SMALL" : profile;
        List<TestCase> result = new ArrayList<>();
        List<String> avoidInputs = new ArrayList<>(
                existingInputs == null ? List.of() : existingInputs
        );
        Set<String> seenInputs = inputKeySet(avoidInputs);

        int attempts = 0;
        while (result.size() < count && attempts < MAX_TESTCASE_ATTEMPTS) {
            int missing = count - result.size();
            List<TestCase> batch = callTestcaseEndpoint(
                    problem,
                    missing,
                    includeEdgeCases,
                    normalizedProfile,
                    avoidInputs
            );

            for (TestCase testCase : batch) {
                if (testCase == null) {
                    continue;
                }
                String key = inputKey(testCase.getInput());
                if (key.isBlank() || seenInputs.contains(key)) {
                    continue;
                }
                result.add(testCase);
                seenInputs.add(key);
                avoidInputs.add(testCase.getInput());
                if (result.size() >= count) {
                    break;
                }
            }
            attempts++;
        }

        return new ArrayList<>(result.subList(0, Math.min(result.size(), count)));
    }

    private List<TestCase> callTestcaseEndpoint(Problem problem, int count,
                                                boolean includeEdgeCases,
                                                String profile,
                                                List<String> existingInputs) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        body.put("count", count);
        body.put("requested_count", count);
        body.put("include_edge_cases", includeEdgeCases);
        body.put("profile", profile == null || profile.isBlank() ? "SMALL" : profile);
        body.put("existing_inputs", existingInputs == null ? List.of() : existingInputs);

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

    private Set<String> inputKeySet(List<String> values) {
        Set<String> keys = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            String key = inputKey(value);
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private String inputKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(stripTrailingWhitespace(lines[i]));
        }
        return builder.toString().strip();
    }

    private String stripTrailingWhitespace(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    public CodeSubmission generateCode(Problem problem, String type,
                                       String language) throws Exception {
        return generateCode(problem, type, language, List.of());
    }

    public CodeSubmission generateCode(Problem problem, String type,
                                       String language,
                                       List<TestCase> validationCases) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        body.put("type", type);
        body.put("language", language);
        body.put("validation_cases", validationCases == null ? List.of() : validationCases);

        return HttpUtil.postJson(baseUrl + "/codegen", body, CodeSubmission.class);
    }

    public StressResult runStress(Problem problem, List<TestCase> smallCases,
                                  int rounds) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        body.put("small_cases", smallCases == null ? List.of() : smallCases);
        body.put("rounds", rounds);

        return HttpUtil.postJson(
                baseUrl + "/stress",
                body,
                StressResult.class,
                Duration.ofSeconds(180)
        );
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8000";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
