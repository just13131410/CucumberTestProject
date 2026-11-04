package org.example.cucumber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.utils.RunName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AllureReportServiceTest {

    private AllureReportService allureReportService;

    @BeforeEach
    void setUp() {
        allureReportService = new AllureReportService();
    }

    // --- sortSuitesNewestFirst tests (private method via reflection) ---

    private void sortSuitesNewestFirst(Path reportDir, Map<String, Long> suiteTimestamps) throws Exception {
        Method method = AllureReportService.class.getDeclaredMethod("sortSuitesNewestFirst", Path.class, Map.class);
        method.setAccessible(true);
        method.invoke(allureReportService, reportDir, suiteTimestamps);
    }

    @Test
    void sortSuitesNewestFirst_WidgetAndDataSuites_OrderedByRunTimestampNewestFirst(@TempDir Path tempDir) throws Exception {
        Path widgetsDir = Files.createDirectories(tempDir.resolve("widgets"));
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));

        // Gleicher Tag: nach Namen stuende [dev] vor [staging], der staging-Run ist aber der neuere
        String older = "20260727 [staging] 938da71e @Frontend";
        String newer = "20260727 [dev] f3dbe6c2 @Backend";
        Files.writeString(widgetsDir.resolve("suites.json"), """
                {
                  "total": 2,
                  "items": [
                    {"uid": "a", "name": "%s"},
                    {"uid": "b", "name": "%s"}
                  ]
                }""".formatted(older, newer));
        Files.writeString(dataDir.resolve("suites.json"), """
                {
                  "uid": "root",
                  "name": "suites",
                  "children": [
                    {"name": "%s", "children": []},
                    {"name": "%s", "children": []}
                  ]
                }""".formatted(older, newer));

        sortSuitesNewestFirst(tempDir, Map.of(older, 1000L, newer, 2000L));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode widgetItems = mapper.readTree(widgetsDir.resolve("suites.json").toFile()).get("items");
        assertEquals(newer, widgetItems.get(0).get("name").asText());
        assertEquals(older, widgetItems.get(1).get("name").asText());

        JsonNode dataChildren = mapper.readTree(dataDir.resolve("suites.json").toFile()).get("children");
        assertEquals(newer, dataChildren.get(0).get("name").asText());
        assertEquals(older, dataChildren.get(1).get("name").asText());
    }

    @Test
    void sortSuitesNewestFirst_UnknownSuitesLast(@TempDir Path tempDir) throws Exception {
        Path widgetsDir = Files.createDirectories(tempDir.resolve("widgets"));
        Files.writeString(widgetsDir.resolve("suites.json"), """
                {"items": [{"name": "unbekannt"}, {"name": "20260727 [dev] f3dbe6c2"}]}""");

        sortSuitesNewestFirst(tempDir, Map.of("20260727 [dev] f3dbe6c2", 1000L));

        JsonNode items = new ObjectMapper().readTree(widgetsDir.resolve("suites.json").toFile()).get("items");
        assertEquals("20260727 [dev] f3dbe6c2", items.get(0).get("name").asText());
        assertEquals("unbekannt", items.get(1).get("name").asText());
    }

    @Test
    void sortSuitesNewestFirst_MissingFiles_DoesNotThrow(@TempDir Path tempDir) {
        assertDoesNotThrow(() -> sortSuitesNewestFirst(tempDir, Map.of()));
    }

    // --- readRunName tests (private method via reflection) ---

    private static final UUID RUN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long JULY_27_NOON = LocalDate.of(2026, 7, 27).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

    private String readRunName(Path dir, long timestamp) throws Exception {
        Method method = AllureReportService.class.getDeclaredMethod("readRunName", Path.class, UUID.class, long.class);
        method.setAccessible(true);
        return (String) method.invoke(allureReportService, dir, RUN_ID, timestamp);
    }

    @Test
    void readRunName_CurrentFormat_ReturnedAsIs(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("executor.json"), """
                {"buildName": "20260727 [staging] 550e8400 @Backend", "reportName": "20260727 [staging] 550e8400 @Backend"}""");

        assertEquals("20260727 [staging] 550e8400 @Backend", readRunName(tempDir, JULY_27_NOON));
    }

    @Test
    void readRunName_LegacyFormatWithTags_RebuiltWithEnvironmentAndDate(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("executor.json"), """
                {
                  "name": "Cucumber Test Service",
                  "type": "api",
                  "buildName": "Run 550e8400",
                  "buildOrder": 1234567890,
                  "reportName": "Run 550e8400 [dev] @Backend, @smoke",
                  "reportUrl": "/reports/550e8400/allure-report/index.html"
                }""");

        assertEquals("20260727 [dev] 550e8400 @Backend @smoke", readRunName(tempDir, JULY_27_NOON));
    }

    @Test
    void readRunName_LegacyFormatWithoutTags(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("executor.json"), """
                {"buildName": "Run 550e8400", "reportName": "Run 550e8400 [dev] "}""");

        assertEquals("20260727 [dev] 550e8400", readRunName(tempDir, JULY_27_NOON));
    }

    @Test
    void readRunName_NoExecutorFile_OnlyRunId(@TempDir Path tempDir) throws Exception {
        assertEquals("550e8400", readRunName(tempDir, 0L));
    }

    @Test
    void readRunName_NoReportNameField_DateAndRunId(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("executor.json"), """
                {"name": "Cucumber Test Service", "type": "api", "buildName": "Run 550e8400"}""");

        assertEquals("20260727 550e8400", readRunName(tempDir, JULY_27_NOON));
    }

    // --- writeExecutorJson ---

    @Test
    void writeExecutorJson_UsesUnifiedRunName(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve(RUN_ID.toString()).resolve("allure-results"));
        TestExecutionRequest request = new TestExecutionRequest();
        request.setEnvironment("staging");
        request.setTags(List.of("@SmokeTest", "@API-Test"));

        System.setProperty("test.results.path", tempDir.toString());
        try {
            allureReportService.writeExecutorJson(RUN_ID, request);
        } finally {
            System.clearProperty("test.results.path");
        }

        JsonNode executor = new ObjectMapper().readTree(
                tempDir.resolve(RUN_ID.toString()).resolve("allure-results").resolve("executor.json").toFile());
        String expected = RunName.of(LocalDate.now(), RUN_ID, "staging", List.of("@SmokeTest", "@API-Test"));
        assertEquals(expected, executor.get("reportName").asText());
        assertEquals(expected, executor.get("buildName").asText());
        assertTrue(expected.contains(" [staging] 550e8400 @SmokeTest @API-Test"), expected);
    }

    // --- getTestReport / generateAllureReport / getReportUrl (non-existing) ---

    @Test
    void getTestReport_NonExistingRun_ReturnsEmpty() {
        assertFalse(allureReportService.getTestReport(java.util.UUID.randomUUID()).isPresent());
    }

    @Test
    void generateAllureReport_NonExistingRun_ReturnsEmpty() {
        assertFalse(allureReportService.generateAllureReport(java.util.UUID.randomUUID()).isPresent());
    }

    @Test
    void getReportUrl_NonExistingRun_ReturnsEmpty() {
        assertFalse(allureReportService.getReportUrl(java.util.UUID.randomUUID()).isPresent());
    }

    // --- listAvailableRuns tests ---

    @Test
    void listAvailableRuns_NoResultsDir_ReturnsEmptyList() {
        System.setProperty("test.results.path", "non-existent-dir-" + java.util.UUID.randomUUID());
        try {
            var runs = allureReportService.listAvailableRuns();
            assertNotNull(runs);
            assertTrue(runs.isEmpty());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void listAvailableRuns_WithValidRuns_ReturnsUUIDs(@TempDir Path tempDir) throws Exception {
        var run1 = java.util.UUID.randomUUID();
        var run2 = java.util.UUID.randomUUID();

        Files.createDirectories(tempDir.resolve(run1.toString()).resolve("allure-results"));
        Files.createDirectories(tempDir.resolve(run2.toString()).resolve("allure-results"));
        Files.createDirectories(tempDir.resolve(java.util.UUID.randomUUID().toString()));
        Files.createDirectories(tempDir.resolve("not-a-uuid").resolve("allure-results"));

        System.setProperty("test.results.path", tempDir.toString());
        try {
            var runs = allureReportService.listAvailableRuns();
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
        System.setProperty("test.results.path", "non-existent-dir-" + java.util.UUID.randomUUID());
        try {
            var result = allureReportService.generateCombinedAllureReport(null);
            assertFalse(result.isPresent());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void generateCombinedAllureReport_NoAllureResultsDirs_ReturnsEmpty(@TempDir Path tempDir) throws Exception {
        var runId = java.util.UUID.randomUUID();
        Files.createDirectories(tempDir.resolve(runId.toString()));

        System.setProperty("test.results.path", tempDir.toString());
        try {
            var result = allureReportService.generateCombinedAllureReport(java.util.List.of(runId));
            assertFalse(result.isPresent());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    // --- S7: Serialisierung der Report-Erzeugung ---

    @Test
    void generateCombinedAllureReport_WhileAnotherIsRunning_ThrowsCapacityExceeded() throws Exception {
        java.lang.reflect.Field field = AllureReportService.class.getDeclaredField("combinedLock");
        field.setAccessible(true);
        java.util.concurrent.locks.ReentrantLock lock =
                (java.util.concurrent.locks.ReentrantLock) field.get(allureReportService);
        java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Thread other = new Thread(() -> {
            lock.lock();
            try {
                held.countDown();
                release.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        other.start();
        try {
            held.await();
            assertThrows(CapacityExceededException.class,
                    () -> allureReportService.generateCombinedAllureReport(java.util.List.of(java.util.UUID.randomUUID())));
        } finally {
            release.countDown();
            other.join();
        }
    }

    @Test
    void generateCombinedAllureReport_ReleasesLockAfterwards() {
        // Kein Run vorhanden → Optional.empty(); der Lock muss danach wieder frei sein
        assertTrue(allureReportService.generateCombinedAllureReport(java.util.List.of(java.util.UUID.randomUUID())).isEmpty());
        assertTrue(allureReportService.generateCombinedAllureReport(java.util.List.of(java.util.UUID.randomUUID())).isEmpty());
    }

    @Test
    void generateAllureReport_RemovesPerRunLockAfterwards() throws Exception {
        allureReportService.generateAllureReport(java.util.UUID.randomUUID());

        java.lang.reflect.Field field = AllureReportService.class.getDeclaredField("runLocks");
        field.setAccessible(true);
        assertTrue(((java.util.Map<?, ?>) field.get(allureReportService)).isEmpty(), "Kein Lock-Leck pro Run");
    }
}
