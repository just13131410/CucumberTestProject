package org.example.config;

import org.example.cucumber.service.StartupReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Lädt beim Applikationsstart genau den übergebenen Browser (Default: Chromium) über den
 * Mirror herunter, sofern {@code playwright.install.on-startup=true} gesetzt ist.
 * <p>
 * Bewusst über eine separate Property gesteuert (Default {@code false}), damit Unit-Tests
 * mit {@code @SpringBootTest} beim Context-Start keinen Browser-Download auslösen. In
 * Prod/OpenShift bzw. beim lokalen App-Start wird die Property auf {@code true} gesetzt.
 * Im Cucumber-Kontext (zweiter App-Start pro JVM) per {@code app.startup-tasks.enabled=false} ganz
 * abgeschaltet.
 */
@Component
@ConditionalOnProperty(name = StartupReconciler.STARTUP_TASKS_ENABLED, havingValue = "true", matchIfMissing = true)
public class PlaywrightStartupInstaller implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightStartupInstaller.class);

    private final PlaywrightBrowserInstaller installer;

    public PlaywrightStartupInstaller(PlaywrightBrowserInstaller installer) {
        this.installer = installer;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (PlaywrightRemoteConfig.isRemoteEnabled()) {
            log.info("playwright.mode=remote ({}) – kein Browser-Download beim Start.",
                    PlaywrightRemoteConfig.endpoint());
            return;
        }
        boolean mirrorEnabled = PlaywrightMirrorConfig.isMirrorEnabled();
        boolean installOnStartup = PlaywrightMirrorConfig.isInstallOnStartup();
        if (!mirrorEnabled || !installOnStartup) {
            log.debug("Playwright-Browser-Download beim Start übersprungen "
                    + "(mirror.enabled={}, install.on-startup={}).",
                    mirrorEnabled, installOnStartup);
            return;
        }

        String browser = PlaywrightMirrorConfig.startupBrowser();
        log.info("Applikationsstart: lade Browser '{}' über den Playwright-Mirror.", browser);
        installer.ensureInstalled(browser);
    }
}
