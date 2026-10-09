package org.example.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.utils.ConfigReader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zustandslose Auflösung des Browser-Ausführungsmodus.
 * <p>
 * {@code playwright.mode} (Env: {@code PLAYWRIGHT_MODE}) wählt, wo der Browser läuft:
 * <ul>
 *   <li>{@code local} (Default) – die App startet den Browser selbst, über Mirror-Download
 *       ({@link PlaywrightMirrorConfig}) oder einen externen Browser ({@code browser.executable.path}).</li>
 *   <li>{@code remote} – die App verbindet sich per {@code BrowserType.connect} mit einem
 *       Playwright-Server ({@code playwright.remote.endpoint}, Default {@value #DEFAULT_ENDPOINT}),
 *       typischerweise dem offiziellen Node-Image als Sidecar im selben Pod
 *       ({@code playwright run-server}). Lokal wird dann kein Browser installiert oder gestartet.</li>
 * </ul>
 * Server-Version (npm {@code playwright}) und Client-Version (Maven {@code playwright}) müssen
 * in Major.Minor übereinstimmen, sonst schlägt {@code connect} mit einem Protokollfehler fehl.
 */
public final class PlaywrightRemoteConfig {

    /**
     * Header, über den {@code playwright run-server} Launch-Optionen vom Client übernimmt
     * (u.a. {@code headless} und {@code args}; {@code executablePath} wird serverseitig ignoriert).
     */
    public static final String LAUNCH_OPTIONS_HEADER = "x-playwright-launch-options";

    public static final String MODE_LOCAL = "local";
    public static final String MODE_REMOTE = "remote";

    /** Default-Endpoint: Playwright-Server als Sidecar im selben Pod. */
    public static final String DEFAULT_ENDPOINT = "ws://localhost:3000/";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Default-Timeout für den Verbindungsaufbau zum Playwright-Server. */
    public static final double DEFAULT_CONNECT_TIMEOUT_MS = 30_000;

    private PlaywrightRemoteConfig() {
    }

    /**
     * Konfigurierter Modus, normalisiert auf {@link #MODE_LOCAL} oder {@link #MODE_REMOTE}.
     * Leer → {@link #MODE_LOCAL}.
     *
     * @throws IllegalArgumentException bei unbekanntem Wert (Tippfehler sollen nicht still lokal laufen)
     */
    public static String mode() {
        String value = ConfigReader.get("playwright.mode", MODE_LOCAL);
        String normalized = (value == null || value.isBlank()) ? MODE_LOCAL : value.trim().toLowerCase();
        return switch (normalized) {
            case MODE_LOCAL, MODE_REMOTE -> normalized;
            default -> throw new IllegalArgumentException(
                    "Unbekannter playwright.mode: '" + value + "'. Erlaubt: local, remote");
        };
    }

    /** Läuft der Browser auf einem entfernten Playwright-Server ({@code playwright.mode=remote})? */
    public static boolean isRemoteEnabled() {
        return MODE_REMOTE.equals(mode());
    }

    /** WebSocket-Endpoint des Playwright-Servers; leer → {@value #DEFAULT_ENDPOINT}. Nur im Remote-Modus relevant. */
    public static String endpoint() {
        String value = ConfigReader.get("playwright.remote.endpoint", DEFAULT_ENDPOINT);
        return (value == null || value.isBlank()) ? DEFAULT_ENDPOINT : value.trim();
    }

    /**
     * Browser-Channel, den der Server starten soll ({@code playwright.remote.channel}); {@code null}
     * = Playwright-Default. {@code chromium} erzwingt das volle Chromium statt der
     * {@code chromium-headless-shell} – nötig u.a. für {@code --unsafely-treat-insecure-origin-as-secure},
     * das die Headless-Shell ignoriert.
     */
    public static String channel() {
        String value = ConfigReader.get("playwright.remote.channel", "");
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Timeout für {@code BrowserType.connect} in Millisekunden; ungültige Werte → Default. */
    public static double connectTimeoutMs() {
        String value = ConfigReader.get("playwright.remote.connect-timeout-ms", "");
        if (value == null || value.isBlank()) {
            return DEFAULT_CONNECT_TIMEOUT_MS;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed > 0 ? parsed : DEFAULT_CONNECT_TIMEOUT_MS;
        } catch (NumberFormatException e) {
            return DEFAULT_CONNECT_TIMEOUT_MS;
        }
    }

    /**
     * Serialisiert die an den Server durchzureichenden Launch-Optionen als JSON für
     * {@link #LAUNCH_OPTIONS_HEADER}. {@code channel} und {@code args} werden nur gesetzt, wenn vorhanden.
     */
    public static String launchOptionsJson(boolean headless, String channel, List<String> args) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("headless", headless);
        if (channel != null && !channel.isBlank()) {
            options.put("channel", channel.trim());
        }
        if (args != null && !args.isEmpty()) {
            options.put("args", args);
        }
        try {
            return MAPPER.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Launch-Optionen nicht serialisierbar: " + options, e);
        }
    }
}
