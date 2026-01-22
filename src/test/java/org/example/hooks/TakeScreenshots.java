package org.example.hooks;

import io.qameta.allure.Allure;
import com.microsoft.playwright.Page;
import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
public class TakeScreenshots {
    public static void captureScreenshot(Page page, String name) {
        // Screenshot direkt als byte[] für maximale Geschwindigkeit ohne I/O Umwege
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target/screenshots/" + name + ".png"))
                .setFullPage(true));

        // Direktes Anhängen an den Allure Report
        Allure.addAttachment(name, "image/png", new ByteArrayInputStream(screenshot), ".png");
    }
}