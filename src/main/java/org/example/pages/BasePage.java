package org.example.pages;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;

public abstract class BasePage {

    protected Browser browser;
    protected Page page;

    public Page createPlaywrightPageInstance(String browserTypeAsString) {
        String normalized = (browserTypeAsString == null ? "chromium" : browserTypeAsString.trim()).toLowerCase();

        BrowserType browserType = switch (normalized) {
            case "firefox" -> Playwright.create().firefox();
            case "chromium", "chrome" -> Playwright.create().chromium();
            case "webkit" -> Playwright.create().webkit();
            default -> throw new IllegalArgumentException(
                    "Unsupported browser type: '" + browserTypeAsString + "'. " +
                            "Use one of: Chromium, Firefox, Webkit"
            );
        };

        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(true);

        // Nutze systemseitig installierten Chrome falls CHROME_EXECUTABLE_PATH gesetzt ist
        // (z.B. per RPM aus Artifactory installiert, kein Playwright-eigener Chromium-Download nötig)
        String executablePath = System.getenv("CHROME_EXECUTABLE_PATH");
        if (executablePath == null) {
            executablePath = System.getProperty("chrome.executable.path");
        }
        if (executablePath != null && !executablePath.isBlank()) {
            options.setExecutablePath(Path.of(executablePath));
        }

        browser = browserType.launch(options);
        page = browser.newPage();
        return page;
    }

}