package org.example.pages;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.example.config.BrowserConfig;

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

        boolean headless = !"false".equalsIgnoreCase(System.getProperty("browser.headless", "true"));
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);

        // Dev:  application-dev.properties → browser.executable.path = lokaler Browser-Pfad
        // Prod: BROWSER_EXECUTABLE_PATH Env-Var (OpenShift) → aus Artifactory installierter Browser
        //       Spring Relaxed Binding übersetzt die Env-Var automatisch auf browser.executable.path.
        // Leer → Playwright nutzt seinen eingebetteten Browser (kein externer Pfad nötig).
        String executablePath = BrowserConfig.getExecutablePath();
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