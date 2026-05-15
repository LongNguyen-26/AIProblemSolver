# TASK_03_JAVA_MODEL_SERVICE.md

## Mục tiêu
Tạo toàn bộ Java model (POJO) và service layer. Sau task này, Java app có thể giao tiếp với Python service qua HTTP.

---

## 1. Models

### `src/main/java/org/example/model/Problem.java`

```java
package org.example.model;

import java.util.List;

public class Problem {
    private String title;
    private String description;
    private String inputFormat;
    private String outputFormat;
    private List<String> constraints;
    private List<String> sampleInputs;
    private List<String> sampleOutputs;

    // Constructors
    public Problem() {}

    // Getters & Setters (generate all)
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getInputFormat() { return inputFormat; }
    public void setInputFormat(String inputFormat) { this.inputFormat = inputFormat; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public List<String> getConstraints() { return constraints; }
    public void setConstraints(List<String> constraints) { this.constraints = constraints; }

    public List<String> getSampleInputs() { return sampleInputs; }
    public void setSampleInputs(List<String> sampleInputs) { this.sampleInputs = sampleInputs; }

    public List<String> getSampleOutputs() { return sampleOutputs; }
    public void setSampleOutputs(List<String> sampleOutputs) { this.sampleOutputs = sampleOutputs; }

    @Override
    public String toString() {
        return "Problem{title='" + title + "'}";
    }
}
```

---

### `src/main/java/org/example/model/TestCase.java`

```java
package org.example.model;

public class TestCase {
    private String id;
    private String input;
    private String expectedOutput;
    private String description;
    private boolean edgeCase;

    // Verdict sau khi chạy (null = chưa chạy)
    private String verdict;      // "AC" | "WA" | "TLE" | "CE" | "RE"
    private long executionTimeMs;
    private String actualOutput;

    public TestCase() {}

    public TestCase(String id, String input, String expectedOutput, String description, boolean edgeCase) {
        this.id = id;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.description = description;
        this.edgeCase = edgeCase;
    }

    // Getters & Setters (generate all)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEdgeCase() { return edgeCase; }
    public void setEdgeCase(boolean edgeCase) { this.edgeCase = edgeCase; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getActualOutput() { return actualOutput; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }
}
```

---

### `src/main/java/org/example/model/CodeSubmission.java`

```java
package org.example.model;

public class CodeSubmission {
    private String code;
    private String language;    // "cpp" | "python" | "java"
    private String type;        // "AC" | "WA" | "TLE" | "custom"
    private String explanation;

    public CodeSubmission() {}

    public CodeSubmission(String code, String language, String type) {
        this.code = code;
        this.language = language;
        this.type = type;
    }

    // Getters & Setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
```

---

### `src/main/java/org/example/model/AnalysisReport.java`

```java
package org.example.model;

import java.time.LocalDateTime;
import java.util.List;

public class AnalysisReport {
    private Problem problem;
    private List<TestCase> testCases;
    private CodeSubmission submission;
    private LocalDateTime generatedAt;

    // Stats
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private long avgExecutionTimeMs;
    private String overallVerdict;  // "STRONG" | "WEAK" | "INCORRECT"

    public AnalysisReport() {
        this.generatedAt = LocalDateTime.now();
    }

    // Getters & Setters (generate all)
    public Problem getProblem() { return problem; }
    public void setProblem(Problem problem) { this.problem = problem; }

    public List<TestCase> getTestCases() { return testCases; }
    public void setTestCases(List<TestCase> testCases) {
        this.testCases = testCases;
        if (testCases != null) {
            this.totalTests = testCases.size();
            this.passedTests = (int) testCases.stream()
                .filter(tc -> "AC".equals(tc.getVerdict())).count();
            this.failedTests = totalTests - passedTests;
            this.avgExecutionTimeMs = (long) testCases.stream()
                .mapToLong(TestCase::getExecutionTimeMs).average().orElse(0);
        }
    }

    public CodeSubmission getSubmission() { return submission; }
    public void setSubmission(CodeSubmission submission) { this.submission = submission; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public int getTotalTests() { return totalTests; }
    public int getPassedTests() { return passedTests; }
    public int getFailedTests() { return failedTests; }
    public long getAvgExecutionTimeMs() { return avgExecutionTimeMs; }

    public String getOverallVerdict() { return overallVerdict; }
    public void setOverallVerdict(String overallVerdict) { this.overallVerdict = overallVerdict; }
}
```

---

## 2. Utility: `HttpUtil.java`

```java
package org.example.util;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpUtil {
    private static final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final Gson gson = new Gson();

    public static <T> T postJson(String url, Object requestBody, Class<T> responseType)
            throws Exception {
        String json = gson.toJson(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode()
                + ": " + response.body());
        }
        return gson.fromJson(response.body(), responseType);
    }

    public static String getString(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
```

---

## 3. `AIBridgeService.java`

```java
package org.example.service;

import com.google.gson.*;
import org.example.model.*;
import org.example.util.AppConfig;
import org.example.util.HttpUtil;
import java.util.*;

public class AIBridgeService {

    private final String baseUrl;
    private final Gson gson = new Gson();

    public AIBridgeService() {
        this.baseUrl = AppConfig.get("ai.service.baseUrl");
    }

    /** Kiểm tra Python service đang chạy không */
    public boolean isServiceAlive() {
        try {
            String result = HttpUtil.getString(baseUrl + "/health");
            return result.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }

    /** Phân tích đề bài text */
    public Problem analyzeProblemText(String text) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("text", text);
        JsonObject response = HttpUtil.postJson(baseUrl + "/analyze", body, JsonObject.class);
        return gson.fromJson(response.getAsJsonObject("problem"), Problem.class);
    }

    /** Phân tích đề bài từ ảnh (base64) */
    public Problem analyzeProblemImage(String base64Image) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("image_base64", base64Image);
        JsonObject response = HttpUtil.postJson(baseUrl + "/analyze", body, JsonObject.class);
        return gson.fromJson(response.getAsJsonObject("problem"), Problem.class);
    }

    /** Sinh test cases */
    public List<TestCase> generateTestCases(Problem problem, int count,
                                             boolean includeEdgeCases) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        body.put("count", count);
        body.put("include_edge_cases", includeEdgeCases);

        JsonObject response = HttpUtil.postJson(baseUrl + "/testcase", body, JsonObject.class);
        JsonArray arr = response.getAsJsonArray("testcases");
        List<TestCase> testCases = new ArrayList<>();
        for (JsonElement el : arr) {
            testCases.add(gson.fromJson(el, TestCase.class));
        }
        return testCases;
    }

    /** Sinh code mẫu */
    public CodeSubmission generateCode(Problem problem, String type,
                                        String language) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("problem", problem);
        body.put("type", type);
        body.put("language", language);
        return HttpUtil.postJson(baseUrl + "/codegen", body, CodeSubmission.class);
    }
}
```

---

## 4. `TestCaseService.java`

```java
package org.example.service;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.example.model.TestCase;
import org.example.util.FileUtil;
import java.lang.reflect.Type;
import java.util.*;

public class TestCaseService {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String storageDir = "testcases";

    public void save(String problemId, List<TestCase> testCases) throws Exception {
        FileUtil.ensureDir(storageDir);
        String json = gson.toJson(testCases);
        FileUtil.writeString(storageDir + "/" + problemId + ".json", json);
    }

    public List<TestCase> load(String problemId) throws Exception {
        String path = storageDir + "/" + problemId + ".json";
        if (!FileUtil.exists(path)) return new ArrayList<>();
        String json = FileUtil.readString(path);
        Type listType = new TypeToken<List<TestCase>>(){}.getType();
        return gson.fromJson(json, listType);
    }

    public void addTestCase(String problemId, TestCase testCase) throws Exception {
        List<TestCase> list = load(problemId);
        testCase.setId("tc_" + UUID.randomUUID().toString().substring(0, 8));
        list.add(testCase);
        save(problemId, list);
    }

    public void deleteTestCase(String problemId, String testCaseId) throws Exception {
        List<TestCase> list = load(problemId);
        list.removeIf(tc -> testCaseId.equals(tc.getId()));
        save(problemId, list);
    }
}
```

---

## 5. `FileUtil.java`

```java
package org.example.util;

import java.io.*;
import java.nio.file.*;
import java.util.Base64;

public class FileUtil {
    public static void ensureDir(String path) throws IOException {
        Files.createDirectories(Paths.get(path));
    }

    public static void writeString(String path, String content) throws IOException {
        Files.writeString(Paths.get(path), content);
    }

    public static String readString(String path) throws IOException {
        return Files.readString(Paths.get(path));
    }

    public static boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    public static String toBase64(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static void delete(String path) throws IOException {
        Files.deleteIfExists(Paths.get(path));
    }
}
```

---

## Checklist

- [ ] Tạo 4 model classes: `Problem`, `TestCase`, `CodeSubmission`, `AnalysisReport`
- [ ] `HttpUtil.java` tạo xong
- [ ] `AIBridgeService.java` tạo xong — test `isServiceAlive()` trả `true`
- [ ] `TestCaseService.java` tạo xong
- [ ] `FileUtil.java` tạo xong
- [ ] `AppConfig.java` đọc được `config.properties`
- [ ] Compile không lỗi: `mvn compile`
