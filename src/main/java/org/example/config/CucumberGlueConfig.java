package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Schlanker Spring-Kontext für die Cucumber-Glue-Klassen (Steps/Hooks).
 * <p>
 * Vorher startete {@code @SpringBootTest} pro Lauf die komplette Anwendung ein zweites Mal in
 * derselben JVM: Web-Kontext, {@code TestExecutionService} mit eigenem Executor,
 * {@code PlaywrightStartupInstaller} und den Reconcile mit Outbox-Replay. Die Steps brauchen davon
 * nur die hier importierten Beans; weitere bei Bedarf ergänzen.
 */
@Configuration
@Import({BrowserConfig.class, PlaywrightBrowserInstaller.class})
public class CucumberGlueConfig {
}
