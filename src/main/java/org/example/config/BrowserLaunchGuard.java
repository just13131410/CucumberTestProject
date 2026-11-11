package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Startet einen Browser und repariert dabei einen fehlenden Playwright-Browser im Mirror-Modus.
 * <p>
 * {@link PlaywrightBrowserInstaller#ensureInstalled} merkt sich pro JVM, dass ein Browser installiert
 * ist. Fehlt die Executable trotzdem (Cache gelöscht/verschoben, oder der Lauf braucht die
 * Headless-Shell), meldet Playwright erst beim Launch „Executable doesn't exist“. Dann wird hier
 * einmal am Memo vorbei neu installiert und der Launch wiederholt. Bleibt die Datei aus, ist der
 * Revisionsordner unvollständig ({@code INSTALLATION_COMPLETE} vorhanden, Datei fehlt): Playwright
 * hält ihn für installiert und lädt nie nach – die Fehlermeldung sagt dann, was zu löschen ist.
 */
@Component
public class BrowserLaunchGuard {

    private static final Logger log = LoggerFactory.getLogger(BrowserLaunchGuard.class);

    static final String MISSING_EXECUTABLE = "Executable doesn't exist";
    private static final String DEFAULT_CACHE = "<OS-Standard>";

    private final PlaywrightBrowserInstaller installer;
    private final BrowserConfig browserConfig;

    public BrowserLaunchGuard(PlaywrightBrowserInstaller installer, BrowserConfig browserConfig) {
        this.installer = installer;
        this.browserConfig = browserConfig;
    }

    /**
     * Installiert (falls nötig) und startet den Browser; bei fehlender Executable genau ein
     * Reparaturversuch.
     *
     * @param browser Browser-Bezeichnung wie für {@link PlaywrightBrowserInstaller#ensureInstalled}
     * @param launch  startet den Browser (z.B. {@code BasePage#createPlaywrightPageInstance})
     * @param cleanup räumt einen fehlgeschlagenen Start auf, bevor neu versucht wird (Treiberprozess)
     */
    public <T> T launch(String browser, Supplier<T> launch, Runnable cleanup) {
        log.info("Browser-Start: {}", describe(BrowserLaunchPlan.fromConfig(browser, browserConfig)));
        installer.ensureInstalled(browser);
        try {
            return launch.get();
        } catch (RuntimeException e) {
            if (!isMissingExecutable(e) || !mirrorInstallActive()) {
                throw e;
            }
            cleanup.run();
            String normalized = PlaywrightMirrorConfig.normalizeBrowser(browser);
            log.warn("Playwright-Browser '{}' fehlt trotz Installation ({}) – installiere erneut über Mirror {}.",
                    normalized, e.getMessage(), PlaywrightMirrorConfig.downloadHost());
            reinstall(normalized);
            return retry(normalized, launch);
        }
    }

    private <T> T retry(String browser, Supplier<T> launch) {
        try {
            return launch.get();
        } catch (RuntimeException e) {
            if (!isMissingExecutable(e)) {
                throw e;
            }
            String cache = PlaywrightMirrorConfig.browsersPath();
            throw new IllegalStateException("Playwright-Browser '" + browser + "' fehlt auch nach erneuter "
                    + "Installation: " + e.getMessage() + " – Der Revisionsordner ist vermutlich unvollständig "
                    + "(INSTALLATION_COMPLETE vorhanden, Datei fehlt). Den genannten Ordner unter "
                    + (cache != null ? cache : "dem Playwright-Standard-Cache (%LOCALAPPDATA%\\ms-playwright "
                    + "bzw. ~/.cache/ms-playwright)")
                    + " löschen; der nächste Lauf lädt ihn über den Mirror neu.", e);
        }
    }

    private void reinstall(String browser) {
        String host = PlaywrightMirrorConfig.downloadHost();
        int exitCode;
        // Gleicher Monitor wie ensureInstalled: keine zwei Install-Subprozesse gleichzeitig.
        synchronized (installer) {
            try {
                exitCode = installer.runInstall(browser, host, PlaywrightMirrorConfig.chromiumDownloadHost(),
                        PlaywrightMirrorConfig.browsersPath());
            } catch (Exception e) {
                throw new IllegalStateException("Erneute Playwright-Browser-Installation für '" + browser
                        + "' über Mirror " + host + " ist fehlgeschlagen: " + e.getMessage(), e);
            }
        }
        if (exitCode != 0) {
            throw new IllegalStateException("Erneute Playwright-Browser-Installation fehlgeschlagen (Exit-Code "
                    + exitCode + ") für '" + browser + "' über Mirror " + host);
        }
    }

    private static boolean mirrorInstallActive() {
        return !PlaywrightRemoteConfig.isRemoteEnabled() && PlaywrightMirrorConfig.isMirrorEnabled();
    }

    /** Sucht die Playwright-Meldung „Executable doesn't exist“ in der ganzen Cause-Kette. */
    static boolean isMissingExecutable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(MISSING_EXECUTABLE)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** Einzeilige Beschreibung des Launch-Plans fürs Log (Modus, headless, Cache, Executable, Channel). */
    static String describe(BrowserLaunchPlan plan) {
        return "browser=" + plan.browserName()
                + ", mode=" + plan.mode()
                + ", headless=" + plan.headless()
                + ", browsersPath=" + plan.driverEnv().getOrDefault(BrowserLaunchPlan.BROWSERS_PATH, DEFAULT_CACHE)
                + ", executablePath=" + (plan.executablePath() != null ? plan.executablePath() : "-")
                + ", channel=" + (plan.channel() != null ? plan.channel() : "-");
    }
}
