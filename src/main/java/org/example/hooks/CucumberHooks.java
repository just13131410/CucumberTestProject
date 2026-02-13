package org.example.hooks;

import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.spring.CucumberContextConfiguration;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Cucumber Hooks fuer Setup und Teardown
 */
@CucumberContextConfiguration
@SpringBootTest
public class CucumberHooks {

    private static final Logger log = LoggerFactory.getLogger(CucumberHooks.class);

    private WebDriver webDriver;

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

        if (scenario.isFailed() && webDriver != null) {
            takeScreenshot(scenario);
        }

        scenarioName.remove();
        scenarioStartTime.remove();

        if (webDriver != null) {
            try {
                webDriver.quit();
            } catch (Exception e) {
                log.warn("Failed to quit WebDriver", e);
            }
        }
    }

    @AfterStep
    public void afterStep(Scenario scenario) {
        if (scenario.isFailed() && webDriver != null) {
            takeScreenshot(scenario);
        }
    }

    private void takeScreenshot(Scenario scenario) {
        try {
            if (webDriver instanceof TakesScreenshot) {
                byte[] screenshot = ((TakesScreenshot) webDriver)
                        .getScreenshotAs(OutputType.BYTES);

                String timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                scenario.attach(screenshot, "image/png", "screenshot_" + timestamp);

                Allure.addAttachment("Screenshot - " + timestamp,
                        "image/png",
                        new ByteArrayInputStream(screenshot),
                        "png");

                log.info("Screenshot captured for scenario: {}", scenario.getName());
            }
        } catch (Exception e) {
            log.error("Failed to capture screenshot", e);
        }
    }
}
