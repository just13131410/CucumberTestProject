package org.example.pages;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.example.config.BrowserConfig;
import org.example.utils.ConfigReader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public abstract class BasePage {

    protected Browser browser;
    protected Page page;

    public Page createPlaywrightPageInstance(String browserTypeAsString) {
        String normalized = (browserTypeAsString == null ? "chromium" : browserTypeAsString.trim()).toLowerCase();

        String executablePath = ConfigReader.get("browser.executable.path", BrowserConfig.getExecutablePath());

        // Ist ein externer Browser-Pfad gesetzt, Download des Playwright-eigenen Browsers unterdrücken.
        Playwright.CreateOptions createOptions = new Playwright.CreateOptions();
        if (executablePath != null && !executablePath.isBlank()) {
            Map<String, String> env = new HashMap<>();
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            createOptions.setEnv(env);
        }

        Playwright playwright = Playwright.create(createOptions);
        BrowserType browserType = switch (normalized) {
            case "firefox" -> playwright.firefox();
            case "chromium", "chrome" -> playwright.chromium();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException(
                    "Unsupported browser type: '" + browserTypeAsString + "'. " +
                            "Use one of: Chromium, Firefox, Webkit"
            );
        };

        boolean headless = BrowserConfig.isHeadless();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);

        if (executablePath != null && !executablePath.isBlank()) {
            options.setExecutablePath(Path.of(executablePath));
        }

        // Zusätzliche Args aus browser.extra.args (z.B. --no-zygote für OpenShift seccomp).
        java.util.List<String> extraArgs = BrowserConfig.getExtraArgs();
        if (!extraArgs.isEmpty()) {
            options.setArgs(extraArgs);
        }

        browser = browserType.launch(options);
        page = browser.newPage();
        return page;
    }

}