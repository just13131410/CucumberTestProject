package org.example.hooks;

import io.qameta.allure.Allure;
import com.microsoft.playwright.Page;
import org.example.cucumber.context.TestContext;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TakeScreenshots {

    /**
     * Erstellt einen Screenshot der aktuellen Playwright-Seite,
     * speichert ihn auf Disk und haengt ihn an den Allure-Report an.
     */
    public static void captureScreenshot(Page page, String name) {
        if (page == null || page.isClosed()) {
            return;
        }

        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));

            // Auf Disk speichern
            Path screenshotsDir = resolveScreenshotsDir();
            Files.createDirectories(screenshotsDir);

            String fileName = name + "_" + System.currentTimeMillis() + ".png";
            Files.write(screenshotsDir.resolve(fileName), screenshot);

            // An Allure-Report anhaengen
            Allure.addAttachment(
                    "Screenshot - " + name,
                    "image/png",
                    new ByteArrayInputStream(screenshot),
                    "png"
            );
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen des Screenshots: " + e.getMessage());
        }
    }

    private static Path resolveScreenshotsDir() {
        if (TestContext.isInitialized()) {
            return TestContext.getScreenshotsDir();
        }
        return Paths.get("target", "screenshots");
    }
}
