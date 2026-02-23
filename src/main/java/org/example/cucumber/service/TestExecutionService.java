package org.example.cucumber.service;

import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.ReportGenerator;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            if (request.getHeadless() != null) {
                System.setProperty("browser.headless", request.getHeadless().toString());
            }

            CucumberRunnerService.RunResult result = cucumberRunnerService.run(
                    runId.toString(), tags, features);

            TestStatus status = statusMap.get(runId);
            status.setEndTime(LocalDateTime.now());
            Duration elapsed = Duration.between(status.getStartTime(), status.getEndTime());
            status.setDuration(String.format("%02d:%02d", elapsed.toMinutes(), elapsed.toSecondsPart()));
            status.setProgress(100);
            status.setCurrentPhase("COMPLETED");

            // Build report URLs – einheitlich als /reports/** Direktpfade
            Map<String, String> reportUrls = new HashMap<>();
            reportUrls.put("cucumber-report", "/reports/" + runId + "/cucumber-reports/Cucumber.html");

            // Accessibility report only for runs that include Frontend/UI tests
            boolean isBackendOnly = request.getTags() != null && !request.getTags().isEmpty()
                    && request.getTags().stream().allMatch(t -> {
                        String tag = t.startsWith("@") ? t.substring(1) : t;
                        return tag.equalsIgnoreCase("Backend") || tag.equalsIgnoreCase("API-Test");
                    });
            if (!isBackendOnly) {
                reportUrls.put("accessibility", "/reports/" + runId + "/axe-result/index.html");
            }

            status.setReportUrls(reportUrls);

            if (result.exitCode() == 0) {
                updateStatus(runId, "COMPLETED", null);
            } else {
                updateStatus(runId, "FAILED", "Tests finished with exit code: " + result.exitCode());
            }

            // Write executor.json for Allure (enables executor widget and trends in combined reports)
            writeExecutorJson(runId, request);

            // Auto-generate Allure report so the URL is immediately accessible
            generateAllureReport(runId).ifPresent(url -> reportUrls.put("allure", url));
            status.setReportUrls(reportUrls);

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
        Path allureResultsDir = getResultsPath(runId).resolve("allure-results");
        Path allureReportDir = getResultsPath(runId).resolve("allure-report");

        if (!Files.exists(allureResultsDir)) {
            log.warn("Allure results directory not found for runId: {}", runId);
            return Optional.empty();
        }

        try {
            Files.createDirectories(allureReportDir);
            // Copy history from previous report (enables trends)
            copyHistory(allureReportDir, allureResultsDir);

            generateWithJavaApi(allureReportDir, List.of(allureResultsDir));

            Path indexHtml = allureReportDir.resolve("index.html");
            if (Files.exists(indexHtml)) {
                String reportUrl = "/reports/" + runId + "/allure-report/index.html";
                log.info("Allure report generated successfully for runId: {}", runId);
                return Optional.of(reportUrl);
            } else {
                log.error("Report generation completed but index.html not found at: {}", indexHtml.toAbsolutePath());
                return Optional.empty();
            }
        } catch (IOException e) {
            log.error("Error generating Allure report for runId: {}", runId, e);
            return Optional.empty();
        }
    }

    private void generateWithJavaApi(Path outputDir, List<Path> resultDirs) throws IOException {
        var config = new ConfigurationBuilder().useDefault().build();
        ReportGenerator generator = new ReportGenerator(config);
        generator.generate(outputDir, resultDirs);
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

    public List<UUID> listAvailableRuns() {
        Path basePath = getBaseResultsPath();
        if (!Files.exists(basePath)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(basePath)) {
            return dirs
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("allure-results")))
                    .map(dir -> {
                        try {
                            return UUID.fromString(dir.getFileName().toString());
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list available runs", e);
            return List.of();
        }
    }

    public Optional<String> generateCombinedAllureReport(List<UUID> runIds) {
        List<UUID> effectiveRunIds = (runIds == null || runIds.isEmpty())
                ? listAvailableRuns()
                : runIds;

        if (effectiveRunIds.isEmpty()) {
            log.warn("No runs available for combined report");
            return Optional.empty();
        }

        // Filter to runs that actually have allure-results
        List<UUID> validRunIds = effectiveRunIds.stream()
                .filter(id -> Files.exists(getResultsPath(id).resolve("allure-results")))
                .collect(Collectors.toList());

        if (validRunIds.isEmpty()) {
            log.warn("No allure-results directories found for the specified runs");
            return Optional.empty();
        }

        Path tempDir = null;
        try {
            Path combinedReportDir = getBaseResultsPath().resolve("combined").resolve("allure-report");
            Files.createDirectories(combinedReportDir);

            // Read timestamps and sort runs ascending by time (oldest = buildOrder 1, newest = N)
            // so that Allure trend chart is chronologically correct (left=old, right=new)
            record RunEntry(UUID id, long timestamp) {}
            List<RunEntry> sortedRuns = validRunIds.stream()
                    .map(id -> new RunEntry(id,
                            readTimestampFromExecutorJson(getResultsPath(id).resolve("allure-results"))))
                    .sorted(Comparator.comparingLong(RunEntry::timestamp))
                    .collect(Collectors.toList());

            // Create temp directory with enriched copies of all allure-results
            tempDir = Files.createTempDirectory("allure-combined-");
            for (int i = 0; i < sortedRuns.size(); i++) {
                RunEntry run = sortedRuns.get(i);
                Path sourceDir = getResultsPath(run.id()).resolve("allure-results");
                Path targetDir = tempDir.resolve(run.id().toString());
                copyAndEnrichResults(sourceDir, targetDir, run.id(), i + 1, run.timestamp());
            }

            // Copy history from previous combined report to newest run's temp dir (enables trends)
            UUID newestId = sortedRuns.getLast().id();
            copyHistory(combinedReportDir, tempDir.resolve(newestId.toString()));

            // Generate report via Java API (kein CLI-Subprocess nötig)
            final Path resolvedTempDir = tempDir;
            List<Path> resultDirs = sortedRuns.stream()
                    .map(run -> resolvedTempDir.resolve(run.id().toString()))
                    .collect(Collectors.toList());
            generateWithJavaApi(combinedReportDir, resultDirs);

            Path indexHtml = combinedReportDir.resolve("index.html");
            if (Files.exists(indexHtml)) {
                String reportUrl = "/reports/combined/allure-report/index.html";
                log.info("Combined Allure report generated successfully from {} runs at URL: {}",
                        validRunIds.size(), reportUrl);
                return Optional.of(reportUrl);
            } else {
                log.error("Combined report generation completed but index.html not found at: {}", indexHtml.toAbsolutePath());
                return Optional.empty();
            }
        } catch (IOException e) {
            log.error("Error generating combined Allure report", e);
            return Optional.empty();
        } finally {
            // Clean up temp directory
            if (tempDir != null) {
                try {
                    deleteDirectory(tempDir);
                } catch (IOException e) {
                    log.warn("Failed to clean up temp directory: {}", tempDir, e);
                }
            }
        }
    }

    private Path getBaseResultsPath() {
        String envPath = System.getenv("TEST_RESULTS_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath);
        }
        String sysProp = System.getProperty("test.results.path");
        if (sysProp != null && !sysProp.isBlank()) {
            return Path.of(sysProp);
        }
        return Path.of("test-results");
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

    private static final DateTimeFormatter RUN_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private void copyAndEnrichResults(Path sourceDir, Path targetDir, UUID runId,
                                      long buildOrder, long runTimestamp) throws IOException {
        Files.createDirectories(targetDir);
        String runLabel = runId.toString().substring(0, 8);

        // Format the run timestamp as yyyyMMddHHmm (e.g. 202602191316)
        String formattedDate = runTimestamp > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(runTimestamp), ZoneId.systemDefault())
                        .format(RUN_DATE_FMT)
                : "";

        // Read tags from existing executor.json (written during test execution)
        String runTags = readTagsFromExecutorJson(sourceDir);

        // Build label:  <yyyyMMddHHmm> <shortId> <tags>
        String suiteLabel = (formattedDate.isEmpty() ? "" : formattedDate + " ")
                + runLabel
                + (runTags.isEmpty() ? "" : " " + runTags);

        try (var files = Files.list(sourceDir)) {
            files.forEach(source -> {
                try {
                    Path target = targetDir.resolve(source.getFileName());
                    String fileName = source.getFileName().toString();

                    if (fileName.endsWith("-result.json")) {
                        // Enrich test result: add run label, make historyId unique per run
                        String content = Files.readString(source);

                        // Add parentSuite label with runId + tags for grouping/filtering
                        String runLabelJson = String.format(
                                "{\"name\":\"parentSuite\",\"value\":\"%s\"}", suiteLabel);
                        String tagJson = String.format(
                                "{\"name\":\"tag\",\"value\":\"run-%s\"}", runLabel);

                        // Insert labels into the labels array
                        content = content.replaceFirst(
                                "\"labels\"\\s*:\\s*\\[",
                                "\"labels\":[" + runLabelJson + "," + tagJson + ",");

                        // Make historyId unique per run so each execution shows as separate entry
                        content = content.replaceAll(
                                "\"historyId\"\\s*:\\s*\"([^\"]+)\"",
                                "\"historyId\":\"$1-" + runId + "\"");

                        Files.writeString(target, content);
                    } else {
                        // Copy other files as-is (attachments, etc.)
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.warn("Failed to copy/enrich file: {}", source, e);
                }
            });
        }

        // Write executor.json for this run
        String executorJson = String.format("""
                {
                  "name": "Cucumber Test Service",
                  "type": "api",
                  "buildName": "%s",
                  "buildOrder": %d,
                  "reportUrl": "/reports/%s/allure-report/index.html"
                }""", suiteLabel, buildOrder, runId);
        Files.writeString(targetDir.resolve("executor.json"), executorJson);
    }

    private String readTagsFromExecutorJson(Path allureResultsDir) {
        Path executorFile = allureResultsDir.resolve("executor.json");
        if (!Files.exists(executorFile)) {
            return "";
        }
        try {
            String content = Files.readString(executorFile);
            // reportName format: "Run <id> [<env>] <tags>"
            // Extract tags from reportName field
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"reportName\"\\s*:\\s*\"[^\\[]*\\[[^\\]]*\\]\\s*(.*)\"")
                    .matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException e) {
            log.warn("Failed to read executor.json from {}", allureResultsDir, e);
        }
        return "";
    }

    /** Reads the buildOrder field (Unix timestamp in ms) from a run's executor.json. */
    private long readTimestampFromExecutorJson(Path allureResultsDir) {
        Path executorFile = allureResultsDir.resolve("executor.json");
        if (!Files.exists(executorFile)) return 0L;
        try {
            String content = Files.readString(executorFile);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"buildOrder\"\\s*:\\s*(\\d+)")
                    .matcher(content);
            if (m.find()) return Long.parseLong(m.group(1));
        } catch (IOException e) {
            log.warn("Failed to read timestamp from executor.json at {}", allureResultsDir, e);
        }
        return 0L;
    }

    private void writeExecutorJson(UUID runId, TestExecutionRequest request) {
        try {
            Path allureResultsDir = getResultsPath(runId).resolve("allure-results");
            if (!Files.exists(allureResultsDir)) return;

            String buildName = "Run " + runId.toString().substring(0, 8);
            String env = request.getEnvironment() != null ? request.getEnvironment() : "unknown";
            String tags = request.getTags() != null ? String.join(", ", request.getTags()) : "";

            String executorJson = String.format("""
                    {
                      "name": "Cucumber Test Service",
                      "type": "api",
                      "buildName": "%s",
                      "buildOrder": %d,
                      "reportName": "%s [%s] %s",
                      "reportUrl": "/reports/%s/allure-report/index.html"
                    }""",
                    buildName,
                    System.currentTimeMillis(),
                    buildName, env, tags,
                    runId);

            Files.writeString(allureResultsDir.resolve("executor.json"), executorJson);
        } catch (IOException e) {
            log.warn("Failed to write executor.json for runId={}", runId, e);
        }
    }

    private void copyHistory(Path sourceReportDir, Path targetResultsDir) {
        Path historySource = sourceReportDir.resolve("history");
        if (!Files.exists(historySource)) return;

        Path historyTarget = targetResultsDir.resolve("history");
        try {
            Files.createDirectories(historyTarget);
            try (var files = Files.list(historySource)) {
                files.forEach(source -> {
                    try {
                        Files.copy(source, historyTarget.resolve(source.getFileName()),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.warn("Failed to copy history file: {}", source, e);
                    }
                });
            }
        } catch (IOException e) {
            log.warn("Failed to copy history directory", e);
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
