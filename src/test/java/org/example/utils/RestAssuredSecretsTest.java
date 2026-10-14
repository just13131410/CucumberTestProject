package org.example.utils;

import com.sun.net.httpserver.HttpServer;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.FileSystemResultsWriter;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

class RestAssuredSecretsTest {

    private static final String TOKEN = "Bearer super-secret-token-4711";

    @TempDir
    Path resultsDir;

    private HttpServer server;
    private AllureLifecycle previousLifecycle;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        previousLifecycle = Allure.getLifecycle();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        Allure.setLifecycle(previousLifecycle);
    }

    @Test
    void config_BlacklistsAuthorizationAndCookies() {
        var blacklisted = RestAssuredSecrets.config().getLogConfig().blacklistedHeaders();
        assertTrue(blacklisted.containsAll(RestAssuredSecrets.SENSITIVE_HEADERS));
    }

    @Test
    void allureAttachment_WithSecretsConfig_DoesNotContainToken() throws IOException {
        String report = requestAndCollectAllureOutput(RestAssuredSecrets.config());

        assertFalse(report.contains("super-secret-token-4711"), "Token darf nicht im Allure-Anhang stehen");
        assertTrue(report.contains("BLACKLISTED"));
    }

    @Test
    void allureAttachment_WithoutSecretsConfig_ContainsToken_ControlCase() throws IOException {
        // Gegenprobe: belegt, dass der Test oben ohne die Konfiguration tatsächlich anschlagen würde.
        String report = requestAndCollectAllureOutput(RestAssuredConfig.config());

        assertTrue(report.contains("super-secret-token-4711"));
    }

    private String requestAndCollectAllureOutput(RestAssuredConfig config) throws IOException {
        AllureLifecycle lifecycle = new AllureLifecycle(new FileSystemResultsWriter(resultsDir));
        Allure.setLifecycle(lifecycle);
        String uuid = UUID.randomUUID().toString();
        lifecycle.scheduleTestCase(new TestResult().setUuid(uuid).setName("secrets"));
        lifecycle.startTestCase(uuid);

        given().config(config)
                .filter(new AllureRestAssured())
                .header("Authorization", TOKEN)
                .get("http://127.0.0.1:" + server.getAddress().getPort() + "/user");

        lifecycle.stopTestCase(uuid);
        lifecycle.writeTestCase(uuid);

        try (Stream<Path> files = Files.list(resultsDir)) {
            return files.map(f -> {
                try {
                    return Files.readString(f);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.joining("\n"));
        }
    }
}
