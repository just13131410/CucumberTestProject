package org.example.cucumber.service;

import org.example.CucumberRunnerService;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestExecutionResponse;
import org.example.cucumber.model.TestStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestExecutionServiceTest {

    @Mock
    private CucumberRunnerService cucumberRunnerService;

    private TestExecutionService testExecutionService;

    @BeforeEach
    void setUp() {
        testExecutionService = new TestExecutionService(cucumberRunnerService);
    }

    @AfterEach
    void tearDown() {
        testExecutionService.shutdown();
    }

    private TestExecutionRequest createRequest(String environment, List<String> tags) {
        TestExecutionRequest request = new TestExecutionRequest();
        request.setEnvironment(environment);
        request.setTags(tags);
        return request;
    }

    // --- buildTagsExpression tests (private method via reflection) ---

    private String buildTagsExpression(List<String> tags) throws Exception {
        Method method = TestExecutionService.class.getDeclaredMethod("buildTagsExpression", List.class);
        method.setAccessible(true);
        return (String) method.invoke(testExecutionService, tags);
    }

    @Test
    void buildTagsExpression_NullTags_ReturnsNull() throws Exception {
        assertNull(buildTagsExpression(null));
    }

    @Test
    void buildTagsExpression_EmptyTags_ReturnsNull() throws Exception {
        assertNull(buildTagsExpression(List.of()));
    }

    @Test
    void buildTagsExpression_SingleTag_WithAtPrefix() throws Exception {
        assertEquals("@smoke", buildTagsExpression(List.of("@smoke")));
    }

    @Test
    void buildTagsExpression_SingleTag_WithoutAtPrefix_AddsIt() throws Exception {
        assertEquals("@smoke", buildTagsExpression(List.of("smoke")));
    }

    @Test
    void buildTagsExpression_MultipleTags_JoinedWithOr() throws Exception {
        String result = buildTagsExpression(List.of("@smoke", "@regression"));
        assertEquals("@smoke or @regression", result);
    }

    @Test
    void buildTagsExpression_MixedPrefixes() throws Exception {
        String result = buildTagsExpression(List.of("smoke", "@critical"));
        assertEquals("@smoke or @critical", result);
    }

    // --- queueTestExecution tests ---

    @Test
    void queueTestExecution_ReturnsQueuedResponse() {
        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));

        TestExecutionResponse response = testExecutionService.queueTestExecution(request);

        assertNotNull(response);
        assertNotNull(response.getRunId());
        assertEquals("QUEUED", response.getStatus());
        assertEquals("dev", response.getEnvironment());
        assertEquals("Test execution queued successfully", response.getMessage());
        assertTrue(response.getStatusUrl().contains(response.getRunId().toString()));
    }

    @Test
    void queueTestExecution_WithMultipleTags_IncludesTagsInResponse() {
        TestExecutionRequest request = createRequest("dev", List.of("@smoke", "@regression"));

        TestExecutionResponse response = testExecutionService.queueTestExecution(request);

        assertNotNull(response.getTags());
        assertTrue(response.getTags().contains("@smoke"));
        assertTrue(response.getTags().contains("@regression"));
    }

    @Test
    void queueTestExecution_GeneratesUniqueRunIds() {
        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));

        TestExecutionResponse r1 = testExecutionService.queueTestExecution(request);
        TestExecutionResponse r2 = testExecutionService.queueTestExecution(request);

        assertNotEquals(r1.getRunId(), r2.getRunId());
    }

    // --- Execution lifecycle tests ---

    @Test
    void execution_SuccessfulRun_StatusCompleted() throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenReturn(new CucumberRunnerService.RunResult("id", "@smoke", 0, "target/runs/id"));

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);
        UUID runId = response.getRunId();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Optional<TestStatus> status = testExecutionService.getTestStatus(runId);
            assertTrue(status.isPresent());
            assertEquals("COMPLETED", status.get().getStatus());
        });

        TestStatus finalStatus = testExecutionService.getTestStatus(runId).orElseThrow();
        assertEquals(100, finalStatus.getProgress());
        assertNotNull(finalStatus.getStartTime());
        assertNotNull(finalStatus.getEndTime());
    }

    @Test
    void execution_FailedExitCode_StatusFailed() throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenReturn(new CucumberRunnerService.RunResult("id", "@smoke", 1, "target/runs/id"));

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);
        UUID runId = response.getRunId();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Optional<TestStatus> status = testExecutionService.getTestStatus(runId);
            assertTrue(status.isPresent());
            assertEquals("FAILED", status.get().getStatus());
        });

        TestStatus finalStatus = testExecutionService.getTestStatus(runId).orElseThrow();
        assertNotNull(finalStatus.getErrorMessage());
        assertTrue(finalStatus.getErrorMessage().contains("exit code: 1"));
    }

    @Test
    void execution_ExceptionThrown_StatusFailed() throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenThrow(new RuntimeException("Cucumber crashed"));

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);
        UUID runId = response.getRunId();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Optional<TestStatus> status = testExecutionService.getTestStatus(runId);
            assertTrue(status.isPresent());
            assertEquals("FAILED", status.get().getStatus());
        });

        TestStatus finalStatus = testExecutionService.getTestStatus(runId).orElseThrow();
        assertTrue(finalStatus.getErrorMessage().contains("Cucumber crashed"));
    }

    // --- getTestStatus tests ---

    @Test
    void getTestStatus_ExistingRun_ReturnsStatus() {
        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);

        Optional<TestStatus> status = testExecutionService.getTestStatus(response.getRunId());

        assertTrue(status.isPresent());
        assertEquals(response.getRunId(), status.get().getRunId());
    }

    @Test
    void getTestStatus_NonExistingRun_ReturnsEmpty() {
        Optional<TestStatus> status = testExecutionService.getTestStatus(UUID.randomUUID());

        assertFalse(status.isPresent());
    }

    // --- getActiveTests tests ---

    @Test
    void getActiveTests_ReturnsOnlyActiveStatuses() throws Exception {
        // Make the run block so it stays in RUNNING state
        CountDownLatch blockLatch = new CountDownLatch(1);
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenAnswer(invocation -> {
                    blockLatch.await(5, TimeUnit.SECONDS);
                    return new CucumberRunnerService.RunResult("id", "@smoke", 0, "out");
                });

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        testExecutionService.queueTestExecution(request);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            List<TestStatus> active = testExecutionService.getActiveTests();
            assertFalse(active.isEmpty());
            assertTrue(active.stream().allMatch(t ->
                    "QUEUED".equals(t.getStatus()) || "RUNNING".equals(t.getStatus())));
        });

        blockLatch.countDown(); // unblock
    }

    // --- cancelTestExecution tests ---

    @Test
    void cancelTestExecution_NonExistingRun_ReturnsEmpty() {
        Optional<TestStatus> result = testExecutionService.cancelTestExecution(UUID.randomUUID());

        assertFalse(result.isPresent());
    }

    @Test
    void cancelTestExecution_RunningTest_CancelsSuccessfully() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenAnswer(invocation -> {
                    blockLatch.await(10, TimeUnit.SECONDS);
                    return new CucumberRunnerService.RunResult("id", "@smoke", 0, "out");
                });

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);

        // Wait until running
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            Optional<TestStatus> status = testExecutionService.getTestStatus(response.getRunId());
            assertTrue(status.isPresent());
            assertTrue(List.of("QUEUED", "RUNNING").contains(status.get().getStatus()));
        });

        Optional<TestStatus> cancelled = testExecutionService.cancelTestExecution(response.getRunId());

        if (cancelled.isPresent()) {
            assertEquals("CANCELLED", cancelled.get().getStatus());
            assertEquals(response.getRunId(), cancelled.get().getRunId());
        }

        blockLatch.countDown();
    }

    // --- deleteTestExecution tests ---

    @Test
    void deleteTestExecution_NonExisting_ReturnsFalse() {
        assertFalse(testExecutionService.deleteTestExecution(UUID.randomUUID()));
    }

    @Test
    void deleteTestExecution_CompletedRun_ReturnsTrue() throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenReturn(new CucumberRunnerService.RunResult("id", "@smoke", 0, "target/runs/id"));

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);
        UUID runId = response.getRunId();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Optional<TestStatus> status = testExecutionService.getTestStatus(runId);
            assertTrue(status.isPresent());
            assertEquals("COMPLETED", status.get().getStatus());
        });

        assertTrue(testExecutionService.deleteTestExecution(runId));
        assertFalse(testExecutionService.getTestStatus(runId).isPresent());
    }

    @Test
    void deleteTestExecution_RunningTest_ReturnsFalse() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenAnswer(invocation -> {
                    blockLatch.await(10, TimeUnit.SECONDS);
                    return new CucumberRunnerService.RunResult("id", "@smoke", 0, "out");
                });

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            Optional<TestStatus> status = testExecutionService.getTestStatus(response.getRunId());
            assertTrue(status.isPresent());
            assertTrue(List.of("QUEUED", "RUNNING").contains(status.get().getStatus()));
        });

        assertFalse(testExecutionService.deleteTestExecution(response.getRunId()));

        blockLatch.countDown();
    }

    // --- getStatistics tests ---

    @Test
    void getStatistics_NoFilter_ReturnsAllKeys() {
        testExecutionService.queueTestExecution(createRequest("dev", List.of("@smoke")));

        Object stats = testExecutionService.getStatistics(null);

        assertNotNull(stats);
        @SuppressWarnings("unchecked")
        Map<String, Object> statsMap = (Map<String, Object>) stats;
        assertTrue(statsMap.containsKey("totalRuns"));
        assertTrue(statsMap.containsKey("completedRuns"));
        assertTrue(statsMap.containsKey("failedRuns"));
        assertTrue(statsMap.containsKey("runningRuns"));
        assertTrue(statsMap.containsKey("queuedRuns"));
        assertTrue(statsMap.containsKey("successRate"));
        assertTrue(statsMap.containsKey("maxConcurrentRuns"));
    }

    @Test
    void getStatistics_WithEnvironmentFilter_FiltersByEnvironment() throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenReturn(new CucumberRunnerService.RunResult("id", "@smoke", 0, "out"));

        testExecutionService.queueTestExecution(createRequest("dev", List.of("@smoke")));
        testExecutionService.queueTestExecution(createRequest("staging", List.of("@smoke")));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> allStats = (Map<String, Object>) testExecutionService.getStatistics(null);
            assertTrue((Long) allStats.get("totalRuns") >= 2);
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> devStats = (Map<String, Object>) testExecutionService.getStatistics("dev");
        long devTotal = (Long) devStats.get("totalRuns");

        @SuppressWarnings("unchecked")
        Map<String, Object> stagingStats = (Map<String, Object>) testExecutionService.getStatistics("staging");
        long stagingTotal = (Long) stagingStats.get("totalRuns");

        assertTrue(devTotal >= 1);
        assertTrue(stagingTotal >= 1);
    }

    // --- getTestReport / generateAllureReport / getReportUrl (non-existing) ---

    @Test
    void getTestReport_NonExistingRun_ReturnsEmpty() {
        assertFalse(testExecutionService.getTestReport(UUID.randomUUID()).isPresent());
    }

    @Test
    void generateAllureReport_NonExistingRun_ReturnsEmpty() {
        assertFalse(testExecutionService.generateAllureReport(UUID.randomUUID()).isPresent());
    }

    @Test
    void getReportUrl_NonExistingRun_ReturnsEmpty() {
        assertFalse(testExecutionService.getReportUrl(UUID.randomUUID()).isPresent());
    }

    // --- Features support ---

    @Test
    void queueTestExecution_WithFeatures_QueuesSuccessfully() {
        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        request.setFeatures(List.of("login.feature", "checkout.feature"));

        TestExecutionResponse response = testExecutionService.queueTestExecution(request);

        assertNotNull(response);
        assertEquals("QUEUED", response.getStatus());
    }

    @Test
    void queueTestExecution_WithEnvVars_QueuesSuccessfully() {
        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        request.setEnvironmentVariables(Map.of("TEST_ENV", "qa"));

        TestExecutionResponse response = testExecutionService.queueTestExecution(request);

        assertNotNull(response);
        assertEquals("QUEUED", response.getStatus());
    }

    // --- listAvailableRuns tests ---

    @Test
    void listAvailableRuns_NoResultsDir_ReturnsEmptyList() {
        // Default test-results dir doesn't exist in test environment
        // Use a non-existent path
        System.setProperty("test.results.path", "non-existent-dir-" + UUID.randomUUID());
        try {
            List<UUID> runs = testExecutionService.listAvailableRuns();
            assertNotNull(runs);
            assertTrue(runs.isEmpty());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void listAvailableRuns_WithValidRuns_ReturnsUUIDs(@TempDir Path tempDir) throws IOException {
        UUID run1 = UUID.randomUUID();
        UUID run2 = UUID.randomUUID();

        // Create run directories with allure-results
        Files.createDirectories(tempDir.resolve(run1.toString()).resolve("allure-results"));
        Files.createDirectories(tempDir.resolve(run2.toString()).resolve("allure-results"));
        // Create a directory without allure-results (should be excluded)
        Files.createDirectories(tempDir.resolve(UUID.randomUUID().toString()));
        // Create a non-UUID directory with allure-results (should be excluded)
        Files.createDirectories(tempDir.resolve("not-a-uuid").resolve("allure-results"));

        System.setProperty("test.results.path", tempDir.toString());
        try {
            List<UUID> runs = testExecutionService.listAvailableRuns();
            assertEquals(2, runs.size());
            assertTrue(runs.contains(run1));
            assertTrue(runs.contains(run2));
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    // --- generateCombinedAllureReport tests ---

    @Test
    void generateCombinedAllureReport_NoRuns_ReturnsEmpty() {
        System.setProperty("test.results.path", "non-existent-dir-" + UUID.randomUUID());
        try {
            Optional<String> result = testExecutionService.generateCombinedAllureReport(null);
            assertFalse(result.isPresent());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void generateCombinedAllureReport_NoAllureResultsDirs_ReturnsEmpty(@TempDir Path tempDir) throws IOException {
        UUID runId = UUID.randomUUID();
        // Create run directory without allure-results subdirectory
        Files.createDirectories(tempDir.resolve(runId.toString()));

        System.setProperty("test.results.path", tempDir.toString());
        try {
            Optional<String> result = testExecutionService.generateCombinedAllureReport(List.of(runId));
            assertFalse(result.isPresent());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    // --- readTagsFromExecutorJson tests (private method via reflection) ---

    private String readTagsFromExecutorJson(Path dir) throws Exception {
        Method method = TestExecutionService.class.getDeclaredMethod("readTagsFromExecutorJson", Path.class);
        method.setAccessible(true);
        return (String) method.invoke(testExecutionService, dir);
    }

    @Test
    void readTagsFromExecutorJson_WithTags_ReturnsTags(@TempDir Path tempDir) throws Exception {
        String executorJson = """
                {
                  "name": "Cucumber Test Service",
                  "type": "api",
                  "buildName": "Run 550e8400",
                  "buildOrder": 1234567890,
                  "reportName": "Run 550e8400 [dev] @Backend, @smoke",
                  "reportUrl": "/reports/550e8400/allure-report/index.html"
                }""";
        Files.writeString(tempDir.resolve("executor.json"), executorJson);

        String tags = readTagsFromExecutorJson(tempDir);
        assertEquals("@Backend, @smoke", tags);
    }

    @Test
    void readTagsFromExecutorJson_WithoutTags_ReturnsEmpty(@TempDir Path tempDir) throws Exception {
        String executorJson = """
                {
                  "name": "Cucumber Test Service",
                  "type": "api",
                  "buildName": "Run 550e8400",
                  "buildOrder": 1234567890,
                  "reportName": "Run 550e8400 [dev] ",
                  "reportUrl": "/reports/550e8400/allure-report/index.html"
                }""";
        Files.writeString(tempDir.resolve("executor.json"), executorJson);

        String tags = readTagsFromExecutorJson(tempDir);
        assertEquals("", tags);
    }

    @Test
    void readTagsFromExecutorJson_NoExecutorFile_ReturnsEmpty(@TempDir Path tempDir) throws Exception {
        String tags = readTagsFromExecutorJson(tempDir);
        assertEquals("", tags);
    }

    @Test
    void readTagsFromExecutorJson_NoReportNameField_ReturnsEmpty(@TempDir Path tempDir) throws Exception {
        String executorJson = """
                {
                  "name": "Cucumber Test Service",
                  "type": "api",
                  "buildName": "Run 550e8400"
                }""";
        Files.writeString(tempDir.resolve("executor.json"), executorJson);

        String tags = readTagsFromExecutorJson(tempDir);
        assertEquals("", tags);
    }
}
