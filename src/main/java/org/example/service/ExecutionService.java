package org.example.service;

import org.example.model.TestCase;
import org.example.util.AppConfig;
import org.example.util.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutionService {
    private static final int TIMEOUT_SEC = AppConfig.getInt("execution.timeoutSeconds", 5);
    private static final String SANDBOX = AppConfig.get("execution.sandboxPath").isBlank()
            ? "sandbox"
            : AppConfig.get("execution.sandboxPath");
    private static final Path SANDBOX_PATH = Paths.get(SANDBOX).toAbsolutePath().normalize();

    public void runTestCase(String sourceCode, String language, TestCase testCase)
            throws Exception {
        if (testCase == null) {
            throw new IllegalArgumentException("TestCase must not be null");
        }
        FileUtil.ensureDir(SANDBOX_PATH.toString());

        String normalizedLanguage = normalizeLanguage(language);
        Path sourceFile = writeSourceFile(sourceCode, normalizedLanguage);

        CompileResult compileResult = compile(normalizedLanguage, sourceFile);
        if (!compileResult.success()) {
            testCase.setVerdict("CE");
            testCase.setExecutionTimeMs(0);
            testCase.setActualOutput(nonBlank(compileResult.stderr(), compileResult.stdout()));
            return;
        }

        RunResult runResult = run(normalizedLanguage, testCase.getInput());
        testCase.setExecutionTimeMs(runResult.timeMs());
        testCase.setActualOutput(runResult.stdout());

        if (runResult.timedOut()) {
            testCase.setVerdict("TLE");
            if (testCase.getActualOutput() == null || testCase.getActualOutput().isBlank()) {
                testCase.setActualOutput(runResult.stderr());
            }
        } else if (runResult.exitCode() != 0) {
            testCase.setVerdict("RE");
            testCase.setActualOutput(nonBlank(runResult.stdout(), runResult.stderr()));
        } else {
            testCase.setVerdict(outputMatches(testCase.getExpectedOutput(), runResult.stdout())
                    ? "AC"
                    : "WA");
        }
    }

    private Path writeSourceFile(String code, String language) throws IOException {
        String filename = switch (language) {
            case "cpp" -> "Main.cpp";
            case "java" -> "Main.java";
            case "python" -> "Main.py";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
        Path path = SANDBOX_PATH.resolve(filename);
        FileUtil.writeString(path.toString(), code == null ? "" : code);
        return path;
    }

    private CompileResult compile(String language, Path sourceFile) throws Exception {
        List<String> command = switch (language) {
            case "cpp" -> List.of(
                    "g++",
                    sourceFile.toString(),
                    "-o",
                    executablePath().toString(),
                    "-O2"
            );
            case "java" -> List.of(
                    "javac",
                    "-encoding",
                    "UTF-8",
                    "-d",
                    SANDBOX_PATH.toString(),
                    sourceFile.toString()
            );
            case "python" -> null;
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };

        if (command == null) {
            return new CompileResult(true, "", "");
        }

        ProcessResult result = runProcess(command, null, 30);
        return new CompileResult(result.exitCode() == 0, result.stdout(), result.stderr());
    }

    private RunResult run(String language, String input) throws Exception {
        List<String> command = switch (language) {
            case "cpp" -> List.of(executablePath().toString());
            case "java" -> List.of("java", "-cp", SANDBOX_PATH.toString(), "Main");
            case "python" -> pythonCommand(SANDBOX_PATH.resolve("Main.py"));
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };

        long start = System.currentTimeMillis();
        ProcessResult result = runProcess(command, input, TIMEOUT_SEC);
        long elapsed = System.currentTimeMillis() - start;
        return new RunResult(
                result.stdout(),
                result.stderr(),
                result.exitCode(),
                elapsed,
                result.timedOut()
        );
    }

    private ProcessResult runProcess(List<String> command, String stdinData, int timeoutSec)
            throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(SANDBOX_PATH.toFile());
        processBuilder.redirectErrorStream(false);

        Process process = processBuilder.start();
        ExecutorService readerPool = Executors.newFixedThreadPool(2);
        Future<String> stdoutFuture = readerPool.submit(readStream(process.getInputStream()));
        Future<String> stderrFuture = readerPool.submit(readStream(process.getErrorStream()));

        try (OutputStream stdin = process.getOutputStream()) {
            if (stdinData != null) {
                stdin.write(stdinData.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }
        }

        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            String stdout = getFutureValue(stdoutFuture, 1);
            String stderr = nonBlank(getFutureValue(stderrFuture, 1), "Time limit exceeded");
            readerPool.shutdownNow();
            return new ProcessResult(stdout, stderr, -1, true);
        }

        String stdout = getFutureValue(stdoutFuture, 2);
        String stderr = getFutureValue(stderrFuture, 2);
        readerPool.shutdown();
        return new ProcessResult(stdout, stderr, process.exitValue(), false);
    }

    private Callable<String> readStream(InputStream inputStream) {
        return () -> {
            try (InputStream stream = inputStream;
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                stream.transferTo(buffer);
                return buffer.toString(StandardCharsets.UTF_8);
            }
        };
    }

    private String getFutureValue(Future<String> future, int timeoutSec) {
        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return "";
        }
    }

    private boolean outputMatches(String expected, String actual) {
        return normalizeOutput(expected).equals(normalizeOutput(actual));
    }

    private String normalizeOutput(String value) {
        if (value == null) {
            return "";
        }
        return value.lines()
                .map(String::stripTrailing)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                .stripTrailing();
    }

    private List<String> pythonCommand(Path sourceFile) {
        String source = sourceFile.toString();
        for (String candidate : List.of("python3", "python")) {
            if (isCommandAvailable(candidate, "--version")) {
                return List.of(candidate, source);
            }
        }
        if (isCommandAvailable("py", "-3", "--version")) {
            List<String> command = new ArrayList<>();
            command.add("py");
            command.add("-3");
            command.add(source);
            return command;
        }
        return List.of("python3", source);
    }

    private boolean isCommandAvailable(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Path executablePath() {
        return SANDBOX_PATH.resolve(isWindows() ? "prog.exe" : "prog");
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            throw new IllegalArgumentException("Language must not be null");
        }
        String value = language.toLowerCase(Locale.ROOT).trim();
        if ("py".equals(value)) {
            return "python";
        }
        if ("c++".equals(value)) {
            return "cpp";
        }
        if (!List.of("cpp", "java", "python").contains(value)) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        return value;
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private record CompileResult(boolean success, String stdout, String stderr) {
    }

    private record RunResult(String stdout, String stderr, int exitCode, long timeMs, boolean timedOut) {
    }

    private record ProcessResult(String stdout, String stderr, int exitCode, boolean timedOut) {
    }
}
