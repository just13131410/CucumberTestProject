package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stellt den Browser-Executable-Pfad profilabhängig bereit.
 *
 * Dev  (application-dev.properties):  lokaler Browser, z.B. C:/Program Files/Mozilla Firefox/firefox.exe
 * Prod (application-prod.properties):  leer – wird durch Env-Var BROWSER_EXECUTABLE_PATH überschrieben,
 *                                       die auf den aus dem Artifactory installierten Browser zeigt.
 *
 * Spring Relaxed Binding mappt BROWSER_EXECUTABLE_PATH automatisch auf browser.executable.path,
 * sodass kein manueller System.getenv()-Aufruf nötig ist.
 */
@Component
public class BrowserConfig {

    private static String executablePath = "";

    @Value("${browser.executable.path:}")
    public void setExecutablePath(String path) {
        BrowserConfig.executablePath = path;
    }

    /**
     * Gibt den konfigurierten Browser-Pfad zurück.
     * Leerer String bedeutet: Playwright nutzt seinen eingebetteten Browser-Download.
     */
    public static String getExecutablePath() {
        return executablePath;
    }
}
