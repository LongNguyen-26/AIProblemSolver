package org.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.example.model.TestCase;
import org.example.util.FileUtil;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestCaseService {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String storageDir;

    public TestCaseService() {
        this("testcases");
    }

    public TestCaseService(String storageDir) {
        this.storageDir = storageDir;
    }

    public void save(String problemId, List<TestCase> testCases) throws Exception {
        FileUtil.ensureDir(storageDir);
        String json = gson.toJson(testCases);
        FileUtil.writeString(storageDir + "/" + problemId + ".json", json);
    }

    public List<TestCase> load(String problemId) throws Exception {
        String path = storageDir + "/" + problemId + ".json";
        if (!FileUtil.exists(path)) {
            return new ArrayList<>();
        }
        String json = FileUtil.readString(path);
        Type listType = new TypeToken<List<TestCase>>() {
        }.getType();
        List<TestCase> testCases = gson.fromJson(json, listType);
        return testCases == null ? new ArrayList<>() : testCases;
    }

    public void addTestCase(String problemId, TestCase testCase) throws Exception {
        List<TestCase> testCases = load(problemId);
        testCase.setId("tc_" + UUID.randomUUID().toString().substring(0, 8));
        testCases.add(testCase);
        save(problemId, testCases);
    }

    public void deleteTestCase(String problemId, String testCaseId) throws Exception {
        List<TestCase> testCases = load(problemId);
        testCases.removeIf(testCase -> testCaseId.equals(testCase.getId()));
        save(problemId, testCases);
    }
}
