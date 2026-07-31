package org.example.pages;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.example.config.BrowserConfig;
import org.example.config.PlaywrightMirrorConfig;
import org.example.utils.ConfigReader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public abstract class BasePage {

    protected Playwright playwright;
    protected Browser browser;
    protected Page page;

    private final BrowserConfig browserConfig;

    protected BasePage(BrowserConfig browserConfig) {
        this.browserConfig = browserConfig;
    }

    public Page createPlaywrightPageInstance(String browserTypeAsString) {
        String normalized = (browserTypeAsString == null ? "chromium" : browserTypeAsString.trim()).toLowerCase();

        boolean mirrorEnabled = PlaywrightMirrorConfig.isMirrorEnabled();
        String executablePath = ConfigReader.get("browser.executable.path", browserConfig.getExecutablePath());
        // Im Mirror-Modus wird immer der Playwright-eigene (über den Mirror geladene) Browser
        // genutzt; ein externer browser.executable.path wird bewusst ignoriert.
        boolean useExternalBrowser = !mirrorEnabled && executablePath != null && !executablePath.isBlank();

        Playwright.CreateOptions createOptions = new Playwright.CreateOptions();
        Map<String, String> env = new HashMap<>();
        if (mirrorEnabled) {
            // Der Browser wurde bereits gezielt per PlaywrightBrowserInstaller über den Mirror
            // installiert. SKIP verhindert, dass create() die übrigen Browser nachlädt.
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            String browsersPath = PlaywrightMirrorConfig.browsersPath();
            if (browsersPath != null) {
                env.put("PLAYWRIGHT_BROWSERS_PATH", browsersPath);
            }
        } else if (useExternalBrowser) {
            // Externer Browser gesetzt → Download des Playwright-eigenen Browsers unterdrücken.
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }
        if (!env.isEmpty()) {
            createOptions.setEnv(env);
        }

        // Als Feld gehalten (nicht lokal!) und in DashboardSteps.tearDown() geschlossen: der
        // Node.js-Treiberprozess von Playwright.create() überlebt sonst den Test-Run als Zombie
        // (führte im Lasttest zu 9 verwaisten ~110-Mi-node-Prozessen -> RSS über das 2-Gi-Limit -> OOMKill,
        // unabhaengig von JVM-Heap/Metaspace-Caps, da der Treiber ein eigener Prozess ist).
        playwright = Playwright.create(createOptions);
        BrowserType browserType = switch (normalized) {
            case "firefox" -> playwright.firefox();
            case "chromium", "chrome" -> playwright.chromium();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException(
                    "Unsupported browser type: '" + browserTypeAsString + "'. " +
                            "Use one of: Chromium, Firefox, Webkit"
            );
        };

        boolean headless = browserConfig.isHeadless();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);

        if (useExternalBrowser) {
            options.setExecutablePath(Path.of(executablePath));
        }

        // Zusätzliche Args aus browser.extra.args (z.B. --no-zygote für OpenShift seccomp).
        java.util.List<String> extraArgs = browserConfig.getExtraArgs();
        if (!extraArgs.isEmpty()) {
            options.setArgs(extraArgs);
        }

        browser = browserType.launch(options);
        page = browser.newPage();
        return page;
    }

}