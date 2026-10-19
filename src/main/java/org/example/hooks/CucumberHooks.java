package org.example.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.spring.CucumberContextConfiguration;
import io.qameta.allure.Allure;
import org.example.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

/**
 * Cucumber Hooks fuer Setup und Teardown. Trägt die Spring-Konfiguration der Glue-Klassen:
 * voller App-Kontext, damit Steps jede Bean der Anwendung injizieren können, aber ohne Web-Server
 * und ohne Start-Aufgaben ({@link #SPRING_PROPERTIES}). Spring cached den Kontext, er entsteht
 * einmal pro JVM, nicht pro Lauf.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = CucumberHooks.SPRING_PROPERTIES)
public class CucumberHooks {

    /**
     * Die Anwendung läuft in derselben JVM bereits: kein Reconcile (würde den laufenden Lauf als
     * INTERRUPTED markieren) und kein Browser-Download beim Start.
     */
    static final String SPRING_PROPERTIES = "app.startup-tasks.enabled=false";

    private static final Logger log = LoggerFactory.getLogger(CucumberHooks.class);

    private final ThreadLocal<LocalDateTime> scenarioStartTime = new ThreadLocal<>();

    @Before(order = 0)
    public void beforeScenario(Scenario scenario) {
        scenarioStartTime.set(LocalDateTime.now());

        log.info("========================================");
        log.info("Starting Scenario: {}", scenario.getName());
        log.info("Tags: {}", scenario.getSourceTagNames());
        log.info("========================================");

        Allure.getLifecycle().updateTestCase(testResult -> {
            testResult.setName(scenario.getName());
            testResult.setDescription("Feature: " + scenario.getUri());
        });
    }

    @After(order = 100)
    public void afterScenario(Scenario scenario) {
        LocalDateTime endTime = LocalDateTime.now();

        log.info("========================================");
        log.info("Scenario: {} - Status: {}",
                scenario.getName(),
                scenario.getStatus());
        log.info("Duration: {} ms",
                java.time.Duration.between(scenarioStartTime.get(), endTime).toMillis());
        log.info("========================================");

        scenarioStartTime.remove();
    }
}
