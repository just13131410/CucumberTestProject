package org.example;

import org.junit.runner.RunWith;
import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;



import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = "classpath:features",
        glue = "org.example",
        plugin = {"pretty", "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm", "html:target/cucumber-reports/Cucumber.html", "json:target/cucumber-reports/Cucumber.json"}
)
public class RunCucumberTest {
    static {
        Path p = Path.of("target", "allure-results");
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new RuntimeException("Kann Verzeichnis `target/allure-results` nicht erstellen", e);
        }
    }
}
