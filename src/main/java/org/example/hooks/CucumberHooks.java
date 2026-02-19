package org.example.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.spring.CucumberContextConfiguration;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

/**
 * Cucumber Hooks fuer Setup und Teardown
 */
@CucumberContextConfiguration
@SpringBootTest
public class CucumberHooks {

    private static final Logger log = LoggerFactory.getLogger(CucumberHooks.class);

    private final ThreadLocal<String> scenarioName = new ThreadLocal<>();
    private final ThreadLocal<LocalDateTime> scenarioStartTime = new ThreadLocal<>();

    @Before(order = 0)
    public void beforeScenario(Scenario scenario) {
        scenarioName.set(scenario.getName());
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

        scenarioName.remove();
        scenarioStartTime.remove();
    }
}
