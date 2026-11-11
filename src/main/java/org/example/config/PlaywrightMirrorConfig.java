package org.example.config;

import org.example.utils.ConfigReader;

/**
 * Zentrale, zustandslose Auflösung der Playwright-Mirror-Konfiguration.
 * <p>
 * Alle Werte werden über {@link ConfigReader} bezogen (Env-Var → System-Property →
 * .env → CONFIGPATH → config.properties → Default). Durch die ConfigReader-Konvention
 * (<code>key.toUpperCase().replace('.', '_')</code>) sind die Schlüssel
 * <code>playwright.download.host</code> bzw. <code>playwright.browsers.path</code>
 * identisch mit den echten Playwright-Umgebungsvariablen
 * <code>PLAYWRIGHT_DOWNLOAD_HOST</code> bzw. <code>PLAYWRIGHT_BROWSERS_PATH</code> –
 * eine einzige Quelle für Treiber-Subprozess und Java-Code.
 */
public final class PlaywrightMirrorConfig {

    /** Default-Adresse des internen Playwright-Mirrors (siehe /etc/hosts bzw. OpenShift-Service). */
    public static final String DEFAULT_DOWNLOAD_HOST = "http://playwright-mirror:10123";

    private PlaywrightMirrorConfig() {
    }

    /**
     * Ist der Mirror-Modus aktiv? Wenn ja, nutzt Playwright seinen eigenen (über den Mirror
     * heruntergeladenen) Browser statt eines extern installierten Browsers
     * (<code>browser.executable.path</code> wird dann ignoriert).
     */
    public static boolean isMirrorEnabled() {
        return parseBoolean(ConfigReader.get("playwright.mirror.enabled", "true"), true);
    }

    /**
     * Soll der Browser bereits beim Applikationsstart heruntergeladen werden?
     * Default {@code false}, damit Unit-Tests (@SpringBootTest) keinen Download auslösen.
     * In Prod/OpenShift und für lokale App-Starts auf {@code true} setzen.
     */
    public static boolean isInstallOnStartup() {
        return parseBoolean(ConfigReader.get("playwright.install.on-startup", "false"), false);
    }

    /** Basis-URL des Mirrors, die als {@code PLAYWRIGHT_DOWNLOAD_HOST} an den Treiber übergeben wird. */
    public static String downloadHost() {
        return ConfigReader.get("playwright.download.host", DEFAULT_DOWNLOAD_HOST);
    }

    /**
     * Download-Host speziell für Chromium ({@code PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST}).
     * <p>
     * Nur ein explizit gesetzter Wert (Config/Env {@code playwright.chromium.download.host} bzw.
     * {@code PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST}), sonst {@code null}: Playwright (ab 1.64) baut die
     * Chromium-URL dann selbst als {@code {downloadHost}/builds/cft/<version>/<platform>/…}. Ein
     * früher abgeleiteter {@code {host}/builds/cft} würde den Präfix verdoppeln.
     */
    public static String chromiumDownloadHost() {
        String explicit = ConfigReader.get("playwright.chromium.download.host", "");
        return (explicit == null || explicit.isBlank()) ? null : explicit.trim();
    }

    /**
     * Persistentes Browser-Cache-Verzeichnis ({@code PLAYWRIGHT_BROWSERS_PATH}).
     * Leer/{@code null} → Playwright nutzt seinen OS-Standard-Cache
     * ({@code ~/.cache/ms-playwright} bzw. {@code %LOCALAPPDATA%\ms-playwright}).
     */
    public static String browsersPath() {
        String value = ConfigReader.get("playwright.browsers.path", "");
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Der Browser, der beim Start heruntergeladen wird. Priorität:
     * System-Property {@code -Dbrowser} (wie von den Step-Definitionen verwendet)
     * → Konfiguration {@code playwright.browser} → Default {@code chromium}.
     */
    public static String startupBrowser() {
        String sysProp = System.getProperty("browser");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        return ConfigReader.get("playwright.browser", "chromium");
    }

    /**
     * Normalisiert eine Browser-Bezeichnung auf einen gültigen Playwright-Install-Namen.
     * {@code chrome} wird auf {@code chromium} abgebildet; {@code null}/leer → {@code chromium}.
     *
     * @throws IllegalArgumentException bei unbekanntem Browser
     */
    public static String normalizeBrowser(String browser) {
        String normalized = (browser == null ? "chromium" : browser.trim()).toLowerCase();
        return switch (normalized) {
            case "", "chromium", "chrome" -> "chromium";
            case "firefox" -> "firefox";
            case "webkit" -> "webkit";
            default -> throw new IllegalArgumentException(
                    "Unsupported browser type: '" + browser + "'. Use one of: chromium, firefox, webkit");
        };
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
