package org.example.cucumber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AllureReportServiceTest {

    private AllureReportService allureReportService;

    @BeforeEach
    void setUp() {
        allureReportService = new AllureReportService();
    }

    // --- sortSuitesNewestFirst tests (private method via reflection) ---

    private void sortSuitesNewestFirst(Path reportDir) throws Exception {
        Method method = AllureReportService.class.getDeclaredMethod("sortSuitesNewestFirst", Path.class);
        method.setAccessible(true);
        method.invoke(allureReportService, reportDir);
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
        Method method = AllureReportService.class.getDeclaredMethod("readTagsFromExecutorJson", Path.class);
        method.setAccessible(true);
        return (String) method.invoke(allureReportService, dir);
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
