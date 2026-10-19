package org.example.utils;

import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;

import java.util.List;

/**
 * RestAssured-Konfiguration, die Zugangsdaten aus Logs und Allure-Anhängen heraushält.
 * <p>
 * {@code AllureRestAssured} liest {@link LogConfig#blacklistedHeaders()} und ersetzt die Werte im
 * Report durch {@code [ BLACKLISTED ]}. Ohne diese Konfiguration landen Bearer-Tokens im Klartext
 * in {@code allure-results}, die über {@code /reports/**} ausgeliefert werden.
 */
public final class RestAssuredSecrets {

    /** Header, deren Werte nie in Reports oder Logs erscheinen dürfen. */
    public static final List<String> SENSITIVE_HEADERS =
            List.of("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie");

    private RestAssuredSecrets() {
    }

    /** Für jeden Request mit {@code given().config(RestAssuredSecrets.config())} setzen. */
    public static RestAssuredConfig config() {
        return RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().blacklistHeaders(SENSITIVE_HEADERS));
    }
}
