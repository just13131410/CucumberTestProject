package org.example.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.spring.CucumberContextConfiguration;
import io.qameta.allure.Allure;
import org.example.config.CucumberGlueConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

/**
 * Cucumber Hooks fuer Setup und Teardown. Trägt die Spring-Konfiguration der Glue-Klassen:
 * nur {@link CucumberGlueConfig}, ohne Web-Server und ohne die Service-Beans der Anwendung.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = CucumberGlueConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class CucumberHooks {

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
