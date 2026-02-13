package org.example.cucumber.service;

import org.example.CucumberRunnerService;
import org.example.cucumber.context.TestContext;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestExecutionResponse;
import org.example.cucumber.model.TestStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TestExecutionService {

    private static final int MAX_CONCURRENT_RUNS = 5;

    private final CucumberRunnerService cucumberRunnerService;
    private final ExecutorService executor;
    private final Map<UUID, TestStatus> statusMap = new ConcurrentHashMap<>();
    private final Map<UUID, Future<?>> runningFutures = new ConcurrentHashMap<>();
    private final Semaphore concurrencyLimiter = new Semaphore(MAX_CONCURRENT_RUNS);

    public TestExecutionService(CucumberRunnerService cucumberRunnerService) {
        this.cucumberRunnerService = cucumberRunnerService;
        this.executor = Executors.newFixedThreadPool(MAX_CONCURRENT_RUNS, r -> {
            Thread t = new Thread(r);
            t.setName("test-executor-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    public TestExecutionResponse queueTestExecution(TestExecutionRequest request) {
        UUID runId = UUID.randomUUID();
        String tagsExpression = buildTagsExpression(request.getTags());
        String features = request.getFeatures() != null
                ? String.join(",", request.getFeatures())
                : null;

        // Initial status: QUEUED
        TestStatus status = TestStatus.builder()
                .runId(runId)
                .status("QUEUED")
                .environment(request.getEnvironment())
                .progress(0)
                .build();
        statusMap.put(runId, status);

        // Submit async execution
        Future<?> future = executor.submit(() -> executeTest(runId, tagsExpression, features, request));
        runningFutures.put(runId, future);

        log.info("Test execution queued: runId={}, tags={}, environment={}",
                runId, tagsExpression, request.getEnvironment());

        return TestExecutionResponse.builder()
                .runId(runId)
                .status("QUEUED")
                .environment(request.getEnvironment())
                .message("Test execution queued successfully")
                .timestamp(LocalDateTime.now())
                .tags(tagsExpression)
                .statusUrl("/api/v1/test/status/" + runId)
                .build();
    }

    private void executeTest(UUID runId, String tags, String features, TestExecutionRequest request) {
        try {
            // Acquire concurrency permit (blocks if at max)
            concurrencyLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            updateStatus(runId, "CANCELLED", "Interrupted while waiting in queue");
            return;
        }

        try {
            updateStatus(runId, "RUNNING", null);
            statusMap.get(runId).setStartTime(LocalDateTime.now());
            statusMap.get(runId).setCurrentPhase("EXECUTING");

            // Set environment variables from request
            if (request.getEnvironmentVariables() != null) {
                request.getEnvironmentVariables().forEach(System::setProperty);
            }
            if (request.getBrowser() != null) {
                System.setProperty("browser", request.getBrowser());
            }

            CucumberRunnerService.RunResult result = cucumberRunnerService.run(
                    runId.toString(), tags, features);

            TestStatus status = statusMap.get(runId);
            status.setEndTime(LocalDateTime.now());
            status.setDuration(Duration.between(status.getStartTime(), status.getEndTime()));
            status.setProgress(100);
            status.setCurrentPhase("COMPLETED");

            // Build report URLs
            Map<String, String> reportUrls = new HashMap<>();
            reportUrls.put("cucumber-json", "/api/v1/test/report/" + runId);
            reportUrls.put("allure", "/api/v1/test/report/" + runId + "/url");
            reportUrls.put("accessibility", "/reports/" + runId + "/axe-result/index.html");
            status.setReportUrls(reportUrls);

            if (result.exitCode() == 0) {
                updateStatus(runId, "COMPLETED", null);
            } else {
                updateStatus(runId, "FAILED", "Tests finished with exit code: " + result.exitCode());
            }

            log.info("Test execution finished: runId={}, exitCode={}", runId, result.exitCode());

        } catch (Exception e) {
            log.error("Test execution error: runId={}", runId, e);
            updateStatus(runId, "FAILED", e.getMessage());
            statusMap.get(runId).setEndTime(LocalDateTime.now());
        } finally {
            concurrencyLimiter.release();
            runningFutures.remove(runId);
        }
    }

    private void updateStatus(UUID runId, String newStatus, String errorMessage) {
        TestStatus status = statusMap.get(runId);
        if (status != null) {
            status.setStatus(newStatus);
            if (errorMessage != null) {
                status.setErrorMessage(errorMessage);
            }
        }
    }

    public Optional<TestStatus> getTestStatus(UUID runId) {
        return Optional.ofNullable(statusMap.get(runId));
    }

    public List<TestStatus> getActiveTests() {
        return statusMap.values().stream()
                .filter(s -> "QUEUED".equals(s.getStatus()) || "RUNNING".equals(s.getStatus()))
                .collect(Collectors.toList());
    }

    public Optional<Object> getTestReport(UUID runId) {
        Path reportPath = getResultsPath(runId).resolve("cucumber-reports").resolve("Cucumber.json");
        if (Files.exists(reportPath)) {
            try {
                String json = Files.readString(reportPath);
                return Optional.of(json);
            } catch (IOException e) {
                log.error("Failed to read report for runId={}", runId, e);
            }
        }
        return Optional.empty();
    }

    public Optional<String> generateAllureReport(UUID runId) {
        try {
            Path allureResultsDir = getResultsPath(runId).resolve("allure-results");
            Path allureReportDir = getResultsPath(runId).resolve("allure-report");

            if (!Files.exists(allureResultsDir)) {
                log.warn("Allure results directory not found for runId: {}", runId);
                return Optional.empty();
            }

            // Create report directory if it doesn't exist
            Files.createDirectories(allureReportDir);

            // Check if Allure CLI is available
            String allureCommand = isAllureAvailable();
            if (allureCommand == null) {
                log.warn("Allure CLI not found. Please install Allure CLI or add it to the Docker image. Skipping report generation for runId: {}", runId);
                return Optional.empty();
            }

            // Generate Allure report using command line
            ProcessBuilder pb = new ProcessBuilder(
                allureCommand, "generate",
                allureResultsDir.toString(),
                "-o", allureReportDir.toString(),
                "--clean"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                // Verify index.html was created
                Path indexHtml = allureReportDir.resolve("index.html");
                if (Files.exists(indexHtml)) {
                    String reportUrl = "/reports/" + runId + "/allure-report/index.html";
                    log.info("Allure report generated successfully for runId: {} at file: {}, URL: {}",
                            runId, indexHtml.toAbsolutePath(), reportUrl);
                    return Optional.of(reportUrl);
                } else {
                    log.error("Allure command succeeded but index.html not found at: {}", indexHtml.toAbsolutePath());
                    return Optional.empty();
                }
            } else {
                log.error("Failed to generate Allure report for runId: {}, exit code: {}", runId, exitCode);
                return Optional.empty();
            }

        } catch (IOException e) {
            log.error("Error generating Allure report for runId: {}", runId, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while generating Allure report for runId: {}", runId, e);
            return Optional.empty();
        }
    }

    private String isAllureAvailable() {
        String[] possibleCommands = {"allure", "allure.bat", "allure.cmd"};
        for (String cmd : possibleCommands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return cmd;
                }
            } catch (IOException | InterruptedException e) {
                // Command not found or failed, try next
            }
        }
        return null;
    }

    public Optional<String> getReportUrl(UUID runId) {
        Path allureReportDir = getResultsPath(runId).resolve("allure-report");
        if (Files.exists(allureReportDir) && Files.exists(allureReportDir.resolve("index.html"))) {
            return Optional.of("/reports/" + runId + "/allure-report/index.html");
        }
        return Optional.empty();
    }

    public Optional<TestStatus> cancelTestExecution(UUID runId) {
        Future<?> future = runningFutures.get(runId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            updateStatus(runId, "CANCELLED", "Cancelled by user");
            statusMap.get(runId).setEndTime(LocalDateTime.now());
            runningFutures.remove(runId);
            log.info("Test execution cancelled: runId={}", runId);
            return Optional.ofNullable(statusMap.get(runId));
        }
        return Optional.empty();
    }

    public boolean deleteTestExecution(UUID runId) {
        TestStatus status = statusMap.get(runId);
        if (status == null) return false;

        // Don't delete running tests
        if ("RUNNING".equals(status.getStatus()) || "QUEUED".equals(status.getStatus())) {
            return false;
        }

        statusMap.remove(runId);

        // Clean up files
        Path resultsPath = getResultsPath(runId);
        if (Files.exists(resultsPath)) {
            try {
                deleteDirectory(resultsPath);
            } catch (IOException e) {
                log.warn("Failed to delete results for runId={}", runId, e);
            }
        }
        return true;
    }

    public Object getStatistics(String environment) {
        var allStatuses = statusMap.values().stream()
                .filter(s -> environment == null || environment.equals(s.getEnvironment()))
                .toList();

        long total = allStatuses.size();
        long completed = allStatuses.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long failed = allStatuses.stream().filter(s -> "FAILED".equals(s.getStatus())).count();
        long running = allStatuses.stream().filter(s -> "RUNNING".equals(s.getStatus())).count();
        long queued = allStatuses.stream().filter(s -> "QUEUED".equals(s.getStatus())).count();

        return Map.of(
                "totalRuns", total,
                "completedRuns", completed,
                "failedRuns", failed,
                "runningRuns", running,
                "queuedRuns", queued,
                "successRate", total > 0 ? (completed * 100.0 / total) : 0.0,
                "maxConcurrentRuns", MAX_CONCURRENT_RUNS
        );
    }

    private Path getResultsPath(UUID runId) {
        String envPath = System.getenv("TEST_RESULTS_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath, runId.toString());
        }
        String sysProp = System.getProperty("test.results.path");
        if (sysProp != null && !sysProp.isBlank()) {
            return Path.of(sysProp, runId.toString());
        }
        return Path.of("test-results", runId.toString());
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException ignored) {}
                    });
        }
    }

    private String buildTagsExpression(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return tags.stream()
                .map(t -> t.startsWith("@") ? t : "@" + t)
                .collect(Collectors.joining(" or "));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
