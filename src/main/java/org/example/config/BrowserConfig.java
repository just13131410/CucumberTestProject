package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class BrowserConfig {

    private static String executablePath = "";
    private static List<String> extraArgs = List.of();

    @Value("${browser.executable.path:}")
    public void setExecutablePath(String path) {
        BrowserConfig.executablePath = path;
    }

    @Value("${browser.extra.args:}")
    public void setExtraArgs(String args) {
        if (args != null && !args.isBlank()) {
            BrowserConfig.extraArgs = Arrays.asList(args.split(","));
        }
    }

    /**
     * Gibt den konfigurierten Browser-Pfad zurück.
     * Leerer String bedeutet: Playwright nutzt seinen eingebetteten Browser-Download.
     */
    public static String getExecutablePath() {
        return executablePath;
    }

    /** Gibt zusätzliche Browser-Launch-Argumente zurück (z.B. für Container-Umgebungen). */
    public static List<String> getExtraArgs() {
        return extraArgs;
    }
}
