package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class BrowserConfig {

    private final String executablePath;
    private final boolean headless;
    private final List<String> extraArgs;

    public BrowserConfig(
            @Value("${browser.executable.path:}") String executablePath,
            @Value("${browser.headless:true}") boolean headless,
            @Value("${browser.extra.args:}") String extraArgs) {
        this.executablePath = executablePath;
        this.headless = headless;
        this.extraArgs = (extraArgs != null && !extraArgs.isBlank())
                ? Arrays.asList(extraArgs.split(","))
                : List.of();
    }

    /**
     * Gibt den konfigurierten Browser-Pfad zurück.
     * Leerer String bedeutet: Playwright nutzt seinen eingebetteten Browser-Download.
     */
    public String getExecutablePath() {
        return executablePath;
    }

    public boolean isHeadless() {
        return headless;
    }

    /** Gibt zusätzliche Browser-Launch-Argumente zurück (z.B. für Container-Umgebungen). */
    public List<String> getExtraArgs() {
        return extraArgs;
    }
}
