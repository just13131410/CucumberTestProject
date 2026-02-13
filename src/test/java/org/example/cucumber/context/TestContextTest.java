package org.example.cucumber.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestContextTest {

    @AfterEach
    void cleanup() {
        TestContext.clear();
        System.clearProperty("test.results.path");
    }

    @Test
    void init_SetsRunIdAndOutputBase() {
        String runId = "test-run-123";

        TestContext.init(runId);

        assertEquals(runId, TestContext.getRunId());
        assertNotNull(TestContext.getOutputBase());
        assertTrue(TestContext.getOutputBase().toString().contains(runId));
    }

    @Test
    void getRunId_ThrowsWhenNotInitialized() {
        assertThrows(IllegalStateException.class, TestContext::getRunId);
    }

    @Test
    void getOutputBase_ThrowsWhenNotInitialized() {
        assertThrows(IllegalStateException.class, TestContext::getOutputBase);
    }

    @Test
    void isInitialized_FalseWhenNotInitialized() {
        assertFalse(TestContext.isInitialized());
    }

    @Test
    void isInitialized_TrueAfterInit() {
        TestContext.init("test-run");

        assertTrue(TestContext.isInitialized());
    }

    @Test
    void clear_RemovesContext() {
        TestContext.init("test-run");
        assertTrue(TestContext.isInitialized());

        TestContext.clear();

        assertFalse(TestContext.isInitialized());
        assertThrows(IllegalStateException.class, TestContext::getRunId);
    }

    @Test
    void getScreenshotsDir_ContainsScreenshots() {
        TestContext.init("run-1");

        Path dir = TestContext.getScreenshotsDir();

        assertTrue(dir.endsWith("screenshots"));
        assertTrue(dir.startsWith(TestContext.getOutputBase()));
    }

    @Test
    void getAxeResultDir_ContainsAxeResult() {
        TestContext.init("run-1");

        Path dir = TestContext.getAxeResultDir();

        assertTrue(dir.endsWith("axe-result"));
        assertTrue(dir.startsWith(TestContext.getOutputBase()));
    }

    @Test
    void getAllureResultsDir_ContainsAllureResults() {
        TestContext.init("run-1");

        Path dir = TestContext.getAllureResultsDir();

        assertTrue(dir.endsWith("allure-results"));
        assertTrue(dir.startsWith(TestContext.getOutputBase()));
    }

    @Test
    void getCucumberReportsDir_ContainsCucumberReports() {
        TestContext.init("run-1");

        Path dir = TestContext.getCucumberReportsDir();

        assertTrue(dir.endsWith("cucumber-reports"));
        assertTrue(dir.startsWith(TestContext.getOutputBase()));
    }

    @Test
    void allDirectories_AreUnderOutputBase() {
        TestContext.init("hierarchy-test");
        Path outputBase = TestContext.getOutputBase();

        assertTrue(TestContext.getScreenshotsDir().startsWith(outputBase));
        assertTrue(TestContext.getAxeResultDir().startsWith(outputBase));
        assertTrue(TestContext.getAllureResultsDir().startsWith(outputBase));
        assertTrue(TestContext.getCucumberReportsDir().startsWith(outputBase));
    }

    @Test
    void multipleInit_UpdatesContext() {
        TestContext.init("first-run");
        assertEquals("first-run", TestContext.getRunId());

        TestContext.init("second-run");
        assertEquals("second-run", TestContext.getRunId());
    }

    @Test
    void systemProperty_OverridesDefaultBasePath() {
        System.setProperty("test.results.path", "/custom/path");

        TestContext.init("run-123");
        Path outputBase = TestContext.getOutputBase();

        assertTrue(outputBase.toString().contains("run-123"));
        assertTrue(outputBase.startsWith(Paths.get("/custom/path")));
    }

    @Test
    void defaultBasePath_IsTestResults() {
        // Ensure no env var or system property overrides
        System.clearProperty("test.results.path");

        TestContext.init("run-default");
        Path outputBase = TestContext.getOutputBase();

        assertTrue(outputBase.toString().contains("run-default"));
        // Default should end with test-results/run-default (or platform equivalent)
        String pathStr = outputBase.toString().replace("\\", "/");
        assertTrue(pathStr.contains("test-results/run-default"),
                "Expected default path to contain 'test-results/run-default' but was: " + pathStr);
    }

    @Test
    void childThread_InheritsContext() throws Exception {
        TestContext.init("parent-run");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> childRunId = new AtomicReference<>();
        AtomicReference<Path> childOutputBase = new AtomicReference<>();

        Thread child = new Thread(() -> {
            childRunId.set(TestContext.getRunId());
            childOutputBase.set(TestContext.getOutputBase());
            latch.countDown();
        });
        child.start();
        latch.await();

        assertEquals("parent-run", childRunId.get());
        assertEquals(TestContext.getOutputBase(), childOutputBase.get());
    }

    @Test
    void separateThreads_HaveIndependentContexts() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicReference<String> thread1RunId = new AtomicReference<>();
        AtomicReference<String> thread2RunId = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            TestContext.init("run-thread-1");
            ready.countDown();
            try { proceed.await(); } catch (InterruptedException ignored) {}
            thread1RunId.set(TestContext.getRunId());
            TestContext.clear();
        });

        Thread t2 = new Thread(() -> {
            TestContext.init("run-thread-2");
            ready.countDown();
            try { proceed.await(); } catch (InterruptedException ignored) {}
            thread2RunId.set(TestContext.getRunId());
            TestContext.clear();
        });

        t1.start();
        t2.start();
        ready.await();
        proceed.countDown();
        t1.join();
        t2.join();

        assertEquals("run-thread-1", thread1RunId.get());
        assertEquals("run-thread-2", thread2RunId.get());
    }
}
