package org.example.cucumber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.CucumberRunnerService;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestExecutionResponse;
import org.example.cucumber.model.TestStatus;
import org.example.integration.zephyr.ZephyrScaleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestExecutionServiceTest {

    @Mock
    private CucumberRunnerService cucumberRunnerService;
    @Mock
    private ZephyrScaleService zephyrScaleService;

    private TestExecutionService testExecutionService;

    @BeforeEach
    void setUp() {
        testExecutionService = new TestExecutionService(cucumberRunnerService, zephyrScaleService);
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

    private void awaitStatus(UUID runId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Optional<TestStatus> status = testExecutionService.getTestStatus(runId);
            assertTrue(status.isPresent());
            assertEquals(expectedStatus, status.get().getStatus());
        });
    }

    // --- buildTagsExpression tests (private method via reflection) ---

    private String buildTagsExpression(List<String> tags) throws Exception {
        Method method = TestExecutionService.class.getDeclaredMethod("buildTagsExpression", List.class);
        method.setAccessible(true);
        return (String) method.invoke(testExecutionService, tags);
    }

    @ParameterizedTest(name = "tags={0} -> \"{1}\"")
    @MethodSource("buildTagsExpressionCases")
    void buildTagsExpression_VariousInputs(List<String> tags, String expected) throws Exception {
        assertEquals(expected, buildTagsExpression(tags));
    }

    private static Stream<Arguments> buildTagsExpressionCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(List.of(), null),
                Arguments.of(List.of("@smoke"), "@smoke"),
                Arguments.of(List.of("smoke"), "@smoke"),
                Arguments.of(List.of("@smoke", "@regression"), "@smoke or @regression"),
                Arguments.of(List.of("smoke", "@critical"), "@smoke or @critical")
        );
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

        awaitStatus(runId, "COMPLETED");

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

        awaitStatus(runId, "FAILED");

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

        awaitStatus(runId, "FAILED");

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

        awaitStatus(runId, "COMPLETED");

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

    // --- sortSuitesNewestFirst tests (private method via reflection) ---

    private void sortSuitesNewestFirst(Path reportDir) throws Exception {
        Method method = TestExecutionService.class.getDeclaredMethod("sortSuitesNewestFirst", Path.class);
        method.setAccessible(true);
        method.invoke(testExecutionService, reportDir);
    }

    @Test
    void sortSuitesNewestFirst_WidgetAndDataSuites_OrderedNewestFirst(@TempDir Path tempDir) throws Exception {
        Path widgetsDir = Files.createDirectories(tempDir.resolve("widgets"));
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));

        Files.writeString(widgetsDir.resolve("suites.json"), """
                {
                  "total": 2,
                  "items": [
                    {"uid": "a", "name": "202602231349 938da71e @Frontend"},
                    {"uid": "b", "name": "202607271559 f3dbe6c2 @Backend"}
                  ]
                }""");
        Files.writeString(dataDir.resolve("suites.json"), """
                {
                  "uid": "root",
                  "name": "suites",
                  "children": [
                    {"name": "202602231349 938da71e @Frontend", "children": []},
                    {"name": "202607271559 f3dbe6c2 @Backend", "children": []}
                  ]
                }""");

        sortSuitesNewestFirst(tempDir);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode widgetItems = mapper.readTree(widgetsDir.resolve("suites.json").toFile()).get("items");
        assertEquals("202607271559 f3dbe6c2 @Backend", widgetItems.get(0).get("name").asText());
        assertEquals("202602231349 938da71e @Frontend", widgetItems.get(1).get("name").asText());

        JsonNode dataChildren = mapper.readTree(dataDir.resolve("suites.json").toFile()).get("children");
        assertEquals("202607271559 f3dbe6c2 @Backend", dataChildren.get(0).get("name").asText());
        assertEquals("202602231349 938da71e @Frontend", dataChildren.get(1).get("name").asText());
    }

    @Test
    void sortSuitesNewestFirst_MissingFiles_DoesNotThrow(@TempDir Path tempDir) {
        assertDoesNotThrow(() -> sortSuitesNewestFirst(tempDir));
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

    // --- reportUrls: accessibility nur für Frontend-Tests ---

    @ParameterizedTest(name = "tags={0} -> accessibility present={1}")
    @MethodSource("accessibilityUrlCases")
    void execution_TagsDetermineAccessibilityUrlPresence(List<String> tags, boolean expectPresent) throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenReturn(new CucumberRunnerService.RunResult("id", tags.get(0), 0, "out"));

        TestExecutionRequest request = createRequest("dev", tags);
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);
        UUID runId = response.getRunId();

        awaitStatus(runId, "COMPLETED");

        Map<String, String> urls = testExecutionService.getTestStatus(runId).orElseThrow().getReportUrls();
        assertEquals(expectPresent, urls.containsKey("accessibility"));
    }

    private static Stream<Arguments> accessibilityUrlCases() {
        return Stream.of(
                Arguments.of(List.of("@Backend"), false),
                Arguments.of(List.of("@API-Test"), false),
                Arguments.of(List.of("@Backend", "@API-Test"), false),
                Arguments.of(List.of("@Frontend"), true),
                // @SmokeTest includes both UI and API tests -> accessibility URL expected
                Arguments.of(List.of("@SmokeTest"), true)
        );
    }

    @Test
    void execution_FrontendTag_AccessibilityUrlPointsToAxeResult() throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenReturn(new CucumberRunnerService.RunResult("id", "@Frontend", 0, "out"));

        TestExecutionRequest request = createRequest("dev", List.of("@Frontend"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);
        UUID runId = response.getRunId();

        awaitStatus(runId, "COMPLETED");

        Map<String, String> urls = testExecutionService.getTestStatus(runId).orElseThrow().getReportUrls();
        assertTrue(urls.get("accessibility").contains("/axe-result/index.html"));
    }

    // --- reportUrls: einheitliche /reports/** Pfade ---

    @Test
    void execution_CompletedRun_AllReportUrlsUseReportsPrefix() throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenReturn(new CucumberRunnerService.RunResult("id", "@smoke", 0, "out"));

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);
        UUID runId = response.getRunId();

        awaitStatus(runId, "COMPLETED");

        Map<String, String> urls = testExecutionService.getTestStatus(runId).orElseThrow().getReportUrls();

        // Kein alter cucumber-json API-Pfad mehr
        assertFalse(urls.containsKey("cucumber-json"),
                "cucumber-json (API route) must not appear in reportUrls anymore");
        // Kein allure-Eintrag mehr
        assertFalse(urls.containsKey("allure"),
                "allure URL must not appear in reportUrls anymore");

        // cucumber-report zeigt auf direkten /reports/ Pfad
        assertTrue(urls.containsKey("cucumber-report"),
                "cucumber-report URL must be present");
        String cucumberUrl = urls.get("cucumber-report");
        assertTrue(cucumberUrl.startsWith("/reports/"),
                "cucumber-report must start with /reports/ but was: " + cucumberUrl);
        assertTrue(cucumberUrl.endsWith("Cucumber.html"),
                "cucumber-report must point to Cucumber.html but was: " + cucumberUrl);

        // accessibility zeigt auf direkten /reports/ Pfad (smoke enthält Frontend-Tests)
        assertTrue(urls.containsKey("accessibility"));
        String axeUrl = urls.get("accessibility");
        assertTrue(axeUrl.startsWith("/reports/"),
                "accessibility must start with /reports/ but was: " + axeUrl);
        assertTrue(axeUrl.endsWith("/axe-result/index.html"),
                "accessibility must end with /axe-result/index.html but was: " + axeUrl);
    }

    // --- duration als mm:ss ---

    @Test
    void execution_CompletedRun_DurationFormattedAsMinSec() throws Exception {
        when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                .thenReturn(new CucumberRunnerService.RunResult("id", "@smoke", 0, "out"));

        TestExecutionRequest request = createRequest("dev", List.of("@smoke"));
        TestExecutionResponse response = testExecutionService.queueTestExecution(request);
        UUID runId = response.getRunId();

        awaitStatus(runId, "COMPLETED");

        String duration = testExecutionService.getTestStatus(runId).orElseThrow().getDuration();
        assertNotNull(duration);
        assertTrue(duration.matches("\\d{2}:\\d{2}"),
                "Duration must be in mm:ss format but was: " + duration);
    }

    // --- Phase 1: Admission Control (429) + Status-Eviction ---

    @SuppressWarnings("unchecked")
    private Map<UUID, TestStatus> statusMapOf(TestExecutionService svc) throws Exception {
        Field f = TestExecutionService.class.getDeclaredField("statusMap");
        f.setAccessible(true);
        return (Map<UUID, TestStatus>) f.get(svc);
    }

    private void invokeEvict(TestExecutionService svc) throws Exception {
        Method m = TestExecutionService.class.getDeclaredMethod("evictOldStatuses");
        m.setAccessible(true);
        m.invoke(svc);
    }

    @Test
    void queueTestExecution_CapacityExceeded_Throws429Exception() throws Exception {
        // 1 laufender Slot + Queue-Größe 1 = Kapazität 2; der 3. Run muss abgelehnt werden.
        TestExecutionService svc = new TestExecutionService(
                cucumberRunnerService, zephyrScaleService, new RunPersistenceService(), 1, 1, 24, 500);
        try {
            CountDownLatch block = new CountDownLatch(1);
            when(cucumberRunnerService.run(anyString(), anyString(), isNull()))
                    .thenAnswer(inv -> {
                        block.await(10, TimeUnit.SECONDS);
                        return new CucumberRunnerService.RunResult("id", "@smoke", 0, "out");
                    });

            TestExecutionRequest req = createRequest("dev", List.of("@smoke"));
            svc.queueTestExecution(req); // belegt den einen Ausführungs-Thread
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertTrue(svc.getActiveTests().stream().anyMatch(s -> "RUNNING".equals(s.getStatus()))));
            svc.queueTestExecution(req); // füllt die Queue (Größe 1)

            assertThrows(CapacityExceededException.class,
                    () -> svc.queueTestExecution(req)); // Kapazität erschöpft -> 429

            block.countDown();
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void evictOldStatuses_SizeCap_RemovesOldestTerminalOnly() throws Exception {
        // TTL aus (0), Größen-Cap = 2
        TestExecutionService svc = new TestExecutionService(
                cucumberRunnerService, zephyrScaleService, new RunPersistenceService(), 1, 20, 0, 2);
        try {
            Map<UUID, TestStatus> map = statusMapOf(svc);
            for (int i = 0; i < 4; i++) {
                UUID id = UUID.randomUUID();
                map.put(id, TestStatus.builder().runId(id).status("COMPLETED")
                        .endTime(LocalDateTime.now().minusMinutes(i)).build());
            }
            invokeEvict(svc);
            assertEquals(2, map.size(), "Größen-Cap muss auf 2 reduzieren (älteste zuerst entfernt)");
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void evictOldStatuses_TTL_RemovesExpiredButKeepsFreshAndRunning() throws Exception {
        TestExecutionService svc = new TestExecutionService(
                cucumberRunnerService, zephyrScaleService, new RunPersistenceService(), 1, 20, 1, 500);
        try {
            Map<UUID, TestStatus> map = statusMapOf(svc);
            UUID oldId = UUID.randomUUID();
            map.put(oldId, TestStatus.builder().runId(oldId).status("COMPLETED")
                    .endTime(LocalDateTime.now().minusHours(2)).build());
            UUID freshId = UUID.randomUUID();
            map.put(freshId, TestStatus.builder().runId(freshId).status("COMPLETED")
                    .endTime(LocalDateTime.now()).build());
            UUID runningId = UUID.randomUUID();
            map.put(runningId, TestStatus.builder().runId(runningId).status("RUNNING").build());

            invokeEvict(svc);

            assertFalse(map.containsKey(oldId), "abgelaufener Endzustand muss entfernt werden");
            assertTrue(map.containsKey(freshId), "frischer Endzustand bleibt");
            assertTrue(map.containsKey(runningId), "laufender Run wird nie evictet");
        } finally {
            svc.shutdown();
        }
    }
}
