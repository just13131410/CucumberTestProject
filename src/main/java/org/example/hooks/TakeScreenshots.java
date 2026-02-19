package org.example.hooks;

import com.microsoft.playwright.Page;
import io.cucumber.java.Scenario;
import org.example.cucumber.context.TestContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TakeScreenshots {

    /**
     * Erstellt einen Screenshot und haengt ihn via Cucumber Scenario an den Allure-Report an.
     * scenario.attach() geht durch den Cucumber-Eventbus, den AllureCucumber7Jvm direkt
     * ueber seine eigene Lifecycle-Referenz verarbeitet – zuverlaessiger als Allure.addAttachment().
     */
    public static void captureScreenshot(Page page, String name, Scenario scenario) {
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

            // An Allure-Report anhaengen via Cucumber-Eventbus
            scenario.attach(screenshot, "image/png", "Screenshot - " + name);
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
