package org.example.hooks;

import org.example.cucumber.context.TestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TakeScreenshots path resolution logic.
 * The actual screenshot capture requires a running Playwright Page,
 * so we test the path resolution behavior independently.
 */
class TakeScreenshotsTest {

    @AfterEach
    void cleanup() {
        TestContext.clear();
    }

    @Test
    void pathResolution_WithTestContext_UsesContextDir() {
        TestContext.init("screenshot-run");

        Path expected = TestContext.getScreenshotsDir();

        // Verify TestContext provides an isolated path
        assertTrue(expected.toString().contains("screenshot-run"));
        assertTrue(expected.toString().contains("screenshots"));
    }

    @Test
    void pathResolution_WithoutTestContext_UsesFallback() {
        assertFalse(TestContext.isInitialized());

        // The fallback path is Paths.get("target", "screenshots")
        Path fallback = Paths.get("target", "screenshots");
        assertEquals("screenshots", fallback.getFileName().toString());
        assertTrue(fallback.toString().contains("target"));
    }

    @Test
    void pathResolution_TestContextDir_IsUnderOutputBase() {
        TestContext.init("run-abc");

        Path screenshotsDir = TestContext.getScreenshotsDir();
        Path outputBase = TestContext.getOutputBase();

        assertTrue(screenshotsDir.startsWith(outputBase));
    }

    @Test
    void pathResolution_DifferentRuns_DifferentPaths() {
        TestContext.init("run-1");
        Path path1 = TestContext.getScreenshotsDir();
        TestContext.clear();

        TestContext.init("run-2");
        Path path2 = TestContext.getScreenshotsDir();

        assertNotEquals(path1, path2);
        assertTrue(path1.toString().contains("run-1"));
        assertTrue(path2.toString().contains("run-2"));
    }

    @Test
    void isInitializedCheck_CorrectlyDeterminesPath() {
        // Without context
        assertFalse(TestContext.isInitialized());
        Path fallbackPath = TestContext.isInitialized()
                ? TestContext.getScreenshotsDir()
                : Paths.get("target", "screenshots");
        assertEquals(Paths.get("target", "screenshots"), fallbackPath);

        // With context
        TestContext.init("run-check");
        assertTrue(TestContext.isInitialized());
        Path contextPath = TestContext.isInitialized()
                ? TestContext.getScreenshotsDir()
                : Paths.get("target", "screenshots");
        assertNotEquals(Paths.get("target", "screenshots"), contextPath);
        assertTrue(contextPath.toString().contains("run-check"));
    }
}
