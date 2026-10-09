package org.example.pages;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.example.config.BrowserConfig;
import org.example.config.BrowserLaunchPlan;
import org.example.config.PlaywrightRemoteConfig;

import java.nio.file.Path;
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
        // Entscheidung (remote/Mirror/externer Browser/Standard) liegt unit-getestet in BrowserLaunchPlan;
        // hier wird sie nur ausgeführt. Unbekannte Browser scheitern schon vor Playwright.create().
        BrowserLaunchPlan plan = BrowserLaunchPlan.fromConfig(browserTypeAsString, browserConfig);

        Playwright.CreateOptions createOptions = new Playwright.CreateOptions();
        if (!plan.driverEnv().isEmpty()) {
            createOptions.setEnv(plan.driverEnv());
        }

        // Als Feld gehalten (nicht lokal!) und in DashboardSteps.tearDown() geschlossen: der
        // Node.js-Treiberprozess von Playwright.create() überlebt sonst den Test-Run als Zombie
        // (führte im Lasttest zu 9 verwaisten ~110-Mi-node-Prozessen -> RSS über das 2-Gi-Limit -> OOMKill,
        // unabhaengig von JVM-Heap/Metaspace-Caps, da der Treiber ein eigener Prozess ist).
        playwright = Playwright.create(createOptions);
        BrowserType browserType = switch (plan.browserName()) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };

        if (plan.isRemote()) {
            // Der Server startet den Browser; headless, Channel und extra args reicht er aus dem
            // Launch-Options-Header durch (executablePath entfällt, der Browser liegt im Server-Image).
            BrowserType.ConnectOptions connectOptions = new BrowserType.ConnectOptions()
                    .setTimeout(PlaywrightRemoteConfig.connectTimeoutMs())
                    .setHeaders(Map.of(PlaywrightRemoteConfig.LAUNCH_OPTIONS_HEADER, plan.launchOptionsJson()));
            browser = browserType.connect(PlaywrightRemoteConfig.endpoint(), connectOptions);
        } else {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(plan.headless());
            if (plan.executablePath() != null) {
                options.setExecutablePath(Path.of(plan.executablePath()));
            }
            // Zusätzliche Args aus browser.extra.args (z.B. --no-zygote für OpenShift seccomp).
            if (!plan.args().isEmpty()) {
                options.setArgs(plan.args());
            }
            browser = browserType.launch(options);
        }
        page = browser.newPage();
        return page;
    }

}