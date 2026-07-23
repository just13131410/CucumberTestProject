package org.example.config;

import com.microsoft.playwright.impl.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lädt genau den benötigten Playwright-Browser über den konfigurierten Mirror herunter.
 * <p>
 * Statt {@code com.microsoft.playwright.CLI#main} (ruft {@code System.exit} auf und würde
 * den laufenden Service beenden) wird die öffentliche Treiber-API
 * {@link Driver#ensureDriverInstalled(Map, Boolean)} + {@link Driver#createProcessBuilder()}
 * genutzt. Deren {@link ProcessBuilder} lässt sich vollständig mit den Mirror-Env-Variablen
 * bestücken – dadurch funktioniert der Download turnkey (auch im Spring-Boot-Fat-Jar) ohne
 * Abhängigkeit von vererbten Prozess-Umgebungsvariablen.
 * <p>
 * {@code playwright install <browser>} lädt eine Browser-Revision nur, wenn sie im
 * {@code PLAYWRIGHT_BROWSERS_PATH}-Cache fehlt. Bereits vorhandene Browser werden
 * wiederverwendet; erst eine neuere Playwright-Version (= neue Revision) löst einen erneuten
 * Download vom Mirror aus.
 */
@Component
public class PlaywrightBrowserInstaller {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserInstaller.class);

    /** Merkt sich pro JVM bereits installierte Browser, um wiederholte Subprozesse zu vermeiden. */
    private final Set<String> installedBrowsers = ConcurrentHashMap.newKeySet();

    /**
     * Stellt sicher, dass der angegebene Browser über den Mirror installiert ist.
     * Idempotent und thread-safe; im Nicht-Mirror-Modus ein No-Op.
     *
     * @param browser Browser-Bezeichnung (z.B. {@code chromium}, {@code firefox}, {@code webkit});
     *                {@code null} → {@code chromium}
     */
    public synchronized void ensureInstalled(String browser) {
        if (!PlaywrightMirrorConfig.isMirrorEnabled()) {
            log.debug("Playwright-Mirror deaktiviert – überspringe Browser-Installation.");
            return;
        }

        String normalized = PlaywrightMirrorConfig.normalizeBrowser(browser);
        if (installedBrowsers.contains(normalized)) {
            log.debug("Browser '{}' wurde in dieser JVM bereits installiert – überspringe.", normalized);
            return;
        }

        String host = PlaywrightMirrorConfig.downloadHost();
        String chromiumHost = PlaywrightMirrorConfig.chromiumDownloadHost();
        String browsersPath = PlaywrightMirrorConfig.browsersPath();
        log.info("Installiere Playwright-Browser '{}' über Mirror {} (Chromium: {}, Cache: {})",
                normalized, host, chromiumHost, browsersPath != null ? browsersPath : "<OS-Standard>");

        try {
            int exitCode = runInstall(normalized, host, chromiumHost, browsersPath);
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "Playwright-Browser-Installation fehlgeschlagen (Exit-Code " + exitCode
                                + ") für '" + normalized + "' über Mirror " + host);
            }
            installedBrowsers.add(normalized);
            log.info("Playwright-Browser '{}' ist installiert (Mirror {}).", normalized, host);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Playwright-Browser-Installation für '" + normalized + "' über Mirror " + host
                            + " ist fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /**
     * Führt den eigentlichen {@code playwright install <browser>}-Subprozess aus.
     * Als eigene Methode (protected) gekapselt, damit Unit-Tests den Aufruf ohne echten
     * Download abfangen können.
     *
     * @return Exit-Code des Treiber-Prozesses
     */
    protected int runInstall(String browser, String downloadHost, String chromiumDownloadHost,
                             String browsersPath) throws Exception {
        Map<String, String> driverEnv = buildDriverEnv(downloadHost, chromiumDownloadHost, browsersPath);

        Driver driver = Driver.ensureDriverInstalled(driverEnv, false);
        ProcessBuilder pb = driver.createProcessBuilder();
        pb.command().addAll(installArguments(browser));
        pb.environment().putAll(driverEnv);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[playwright] {}", line);
            }
        }
        return process.waitFor();
    }

    /** CLI-Argumente für den Treiber: {@code install <browser>}. Package-private für Tests. */
    static List<String> installArguments(String browser) {
        List<String> args = new ArrayList<>();
        args.add("install");
        args.add(browser);
        return args;
    }

    /**
     * Baut die Umgebungsvariablen für den Treiber-Subprozess:
     * <ul>
     *   <li>{@code PLAYWRIGHT_DOWNLOAD_HOST} – allgemeiner Mirror-Host
     *       (Firefox/WebKit/ffmpeg/winldd/driver laufen darüber zum Microsoft-Origin).</li>
     *   <li>{@code PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST} – Mirror-Host inkl.
     *       Chrome-for-Testing-Präfix, damit Chromium-Downloads auf die Google-Route des
     *       Mirrors zeigen (Playwright hängt für Chromium nur {@code <version>/<platform>/
     *       chrome-*.zip} ohne Bucket-Präfix an).</li>
     *   <li>{@code PLAYWRIGHT_BROWSERS_PATH} – optionaler persistenter Cache.</li>
     * </ul>
     * Package-private für Tests.
     */
    static Map<String, String> buildDriverEnv(String downloadHost, String chromiumDownloadHost,
                                              String browsersPath) {
        Map<String, String> env = new HashMap<>();
        if (downloadHost != null && !downloadHost.isBlank()) {
            env.put("PLAYWRIGHT_DOWNLOAD_HOST", downloadHost.trim());
        }
        if (chromiumDownloadHost != null && !chromiumDownloadHost.isBlank()) {
            env.put("PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST", chromiumDownloadHost.trim());
        }
        if (browsersPath != null && !browsersPath.isBlank()) {
            env.put("PLAYWRIGHT_BROWSERS_PATH", browsersPath.trim());
        }
        return env;
    }
}
