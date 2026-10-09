package org.example.config;

import org.example.utils.ConfigReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entscheidet ohne Browser-Abhängigkeit, wie ein Browser bereitgestellt wird. {@code BasePage}
 * führt den Plan nur noch aus ({@code connect} bzw. {@code launch}).
 * <p>
 * Priorität: {@code playwright.mode=remote} → Mirror ({@code playwright.mirror.enabled}) →
 * externer Browser ({@code browser.executable.path}) → Playwright-Standard (eigener Download).
 *
 * @param browserName    normalisierter Playwright-Browsername ({@code chromium|firefox|webkit})
 * @param mode           gewählte Bereitstellung
 * @param driverEnv      Umgebungsvariablen für den Playwright-Treiberprozess ({@code Playwright.create})
 * @param headless       headless starten
 * @param args           zusätzliche Browser-Args
 * @param executablePath externer Browser, nur bei {@link Mode#EXTERNAL_BROWSER}, sonst {@code null}
 * @param channel        Browser-Channel für den Remote-Server, nur bei {@link Mode#REMOTE}, sonst {@code null}
 */
public record BrowserLaunchPlan(
        String browserName,
        Mode mode,
        Map<String, String> driverEnv,
        boolean headless,
        List<String> args,
        String executablePath,
        String channel) {

    public enum Mode {
        /** Verbindung zu einem Playwright-Server ({@code BrowserType.connect}). */
        REMOTE,
        /** Lokaler Start des per Mirror installierten Playwright-Browsers. */
        MIRROR,
        /** Lokaler Start eines extern installierten Browsers ({@code browser.executable.path}). */
        EXTERNAL_BROWSER,
        /** Lokaler Start, Playwright lädt seinen Browser selbst (Standardverhalten). */
        PLAYWRIGHT_DEFAULT
    }

    static final String SKIP_BROWSER_DOWNLOAD = "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD";
    static final String BROWSERS_PATH = "PLAYWRIGHT_BROWSERS_PATH";

    public BrowserLaunchPlan {
        driverEnv = Map.copyOf(driverEnv);
        args = List.copyOf(args);
    }

    /** Plan aus der aktuellen Konfiguration ({@link PlaywrightRemoteConfig}, {@link PlaywrightMirrorConfig}, {@link ConfigReader}). */
    public static BrowserLaunchPlan fromConfig(String browser, BrowserConfig browserConfig) {
        boolean remote = PlaywrightRemoteConfig.isRemoteEnabled();
        return resolve(browser, browserConfig, remote,
                !remote && PlaywrightMirrorConfig.isMirrorEnabled(),
                ConfigReader.get("browser.executable.path", browserConfig.getExecutablePath()),
                PlaywrightMirrorConfig.browsersPath(),
                PlaywrightRemoteConfig.channel());
    }

    /**
     * Reine Entscheidungslogik, alle Eingaben explizit.
     *
     * @throws IllegalArgumentException bei unbekanntem Browser – vor {@code Playwright.create},
     *                                  damit kein Treiberprozess verwaist
     */
    static BrowserLaunchPlan resolve(String browser, BrowserConfig browserConfig, boolean remote,
                                     boolean mirrorEnabled, String executablePath, String browsersPath,
                                     String channel) {
        String browserName = PlaywrightMirrorConfig.normalizeBrowser(browser);
        boolean hasExecutable = executablePath != null && !executablePath.isBlank();
        Mode mode = remote ? Mode.REMOTE
                : mirrorEnabled ? Mode.MIRROR
                : hasExecutable ? Mode.EXTERNAL_BROWSER
                : Mode.PLAYWRIGHT_DEFAULT;

        Map<String, String> env = new HashMap<>();
        // REMOTE: Browser liegt im Server-Image. MIRROR: gezielt per PlaywrightBrowserInstaller
        // installiert. EXTERNAL_BROWSER: eigener Browser. In allen drei Fällen darf create() nichts nachladen.
        if (mode != Mode.PLAYWRIGHT_DEFAULT) {
            env.put(SKIP_BROWSER_DOWNLOAD, "1");
        }
        if (mode == Mode.MIRROR && browsersPath != null && !browsersPath.isBlank()) {
            env.put(BROWSERS_PATH, browsersPath.trim());
        }

        return new BrowserLaunchPlan(browserName, mode, env,
                browserConfig.isHeadless(),
                browserConfig.getExtraArgs(),
                mode == Mode.EXTERNAL_BROWSER ? executablePath.trim() : null,
                mode == Mode.REMOTE && channel != null && !channel.isBlank() ? channel.trim() : null);
    }

    public boolean isRemote() {
        return mode == Mode.REMOTE;
    }

    /** JSON für den {@link PlaywrightRemoteConfig#LAUNCH_OPTIONS_HEADER} (headless, channel, args). */
    public String launchOptionsJson() {
        return PlaywrightRemoteConfig.launchOptionsJson(headless, channel, args);
    }
}
