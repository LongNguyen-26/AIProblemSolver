package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.model.CodeSubmission;
import org.example.model.ComplexityInfo;
import org.example.model.PipelineProgress;
import org.example.model.Problem;
import org.example.model.StressResult;
import org.example.model.TestCase;
import org.example.util.AppConfig;
import org.example.util.HttpUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class AIBridgeService {
    private static final int MAX_TESTCASE_ATTEMPTS = 3;
    private static final int SSE_EVENT_TIMEOUT_SECONDS = 30;

    private final String baseUrl;
    private final Gson gson = new Gson();
    private final HttpClient streamingClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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

    public ComplexityInfo analyzeComplexity(Problem problem) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        return HttpUtil.postJson(
                baseUrl + "/analyze/complexity",
                body,
                ComplexityInfo.class
        );
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
        return generateCode(problem, type, language, validationCases, null, "");
    }

    public CodeSubmission generateCode(Problem problem, String type,
                                       String language,
                                       List<TestCase> validationCases,
                                       ComplexityInfo complexityInfo,
                                       String errorLog) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        body.put("type", type);
        body.put("language", language);
        body.put("validation_cases", validationCases == null ? List.of() : validationCases);
        body.put("complexity_info", complexityInfo == null ? Map.of() : complexityInfo);
        body.put("error_log", errorLog == null ? "" : errorLog);

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

    public PipelineProgress runPipeline(
            String problemText,
            int count,
            boolean includeEdgeCases,
            String languagePreference,
            Consumer<PipelineProgress> onProgress
    ) throws Exception {
        try {
            return runPipelineSse(
                    problemText,
                    count,
                    includeEdgeCases,
                    languagePreference,
                    onProgress
            );
        } catch (Exception e) {
            return runLegacyAnalyzeFallback(
                    problemText,
                    count,
                    includeEdgeCases,
                    rootCauseMessage(e)
            );
        }
    }

    private PipelineProgress runPipelineSse(
            String problemText,
            int count,
            boolean includeEdgeCases,
            String languagePreference,
            Consumer<PipelineProgress> onProgress
    ) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem_text", problemText == null ? "" : problemText);
        body.put("count", count);
        body.put("include_edge_cases", includeEdgeCases);
        body.put("language_preference",
                languagePreference == null || languagePreference.isBlank()
                        ? "python"
                        : languagePreference);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/pipeline/run"))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        gson.toJson(body),
                        StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<InputStream> response = streamingClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }

        AtomicReference<PipelineProgress> latest = new AtomicReference<>();
        ExecutorService lineReader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pipeline-sse-reader");
            thread.setDaemon(true);
            return thread;
        });
        try (InputStream bodyStream = response.body();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(bodyStream, StandardCharsets.UTF_8)
             )) {
            while (true) {
                Future<String> nextLine = lineReader.submit(reader::readLine);
                String line;
                try {
                    line = nextLine.get(SSE_EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    nextLine.cancel(true);
                    closeQuietly(bodyStream);
                    throw new SocketTimeoutException(
                            "No pipeline SSE event for "
                                    + SSE_EVENT_TIMEOUT_SECONDS
                                    + " seconds"
                    );
                }

                if (line == null) {
                    break;
                }
                if (line == null || !line.startsWith("data: ")) {
                    continue;
                }
                String json = line.substring("data: ".length());
                PipelineProgress progress = gson.fromJson(json, PipelineProgress.class);
                latest.set(progress);
                if (onProgress != null) {
                    onProgress.accept(progress);
                }
            }
        } finally {
            lineReader.shutdownNow();
        }
        return latest.get();
    }

    private PipelineProgress runLegacyAnalyzeFallback(
            String problemText,
            int count,
            boolean includeEdgeCases,
            String failureMessage
    ) {
        List<String> warnings = new ArrayList<>();
        warnings.add("Pipeline fallback: " + valueOrDefault(failureMessage, "unknown error"));

        Problem problem;
        boolean analyzed = false;
        try {
            problem = analyzeProblemText(problemText);
            analyzed = true;
        } catch (Exception e) {
            problem = fallbackProblem(problemText);
            warnings.add("Legacy analyze failed: " + rootCauseMessage(e));
        }

        List<TestCase> testCases = List.of();
        if (analyzed) {
            try {
                testCases = generateTestCases(
                        problem,
                        Math.max(1, count),
                        includeEdgeCases,
                        "SMALL"
                );
            } catch (Exception e) {
                warnings.add("Legacy testcase failed: " + rootCauseMessage(e));
            }
        }

        PipelineProgress progress = new PipelineProgress();
        progress.setState("DONE");
        progress.setProgressPct(100);
        progress.setTestcasesReady(testCases.size());
        progress.setTestcasesTarget(Math.max(1, count));
        progress.setWarnings(warnings);
        progress.setProblem(problem);
        progress.setAllTestCases(testCases);
        progress.setCached(false);
        progress.setMessage(analyzed
                ? "Pipeline gap su co; da dung luong legacy /analyze + /testcase."
                : "Service offline, thu lai sau.");
        return progress;
    }

    private Problem fallbackProblem(String problemText) {
        Problem problem = new Problem();
        problem.setTitle("Problem (unparsed)");
        problem.setDescription(valueOrDefault(problemText, "No problem text was parsed."));
        problem.setInputFormat("");
        problem.setOutputFormat("");
        problem.setConstraints(List.of());
        problem.setSampleInputs(List.of());
        problem.setSampleOutputs(List.of());
        problem.setProblemType("UNKNOWN");
        problem.setSecondaryType("");
        problem.setTypeConfidence(0.0);
        problem.setTleStrategy("");
        return problem;
    }

    private void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Closing the stream is only used to unblock a timed-out SSE read.
        }
    }

    private String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.toString() : cursor.getMessage();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8000";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
