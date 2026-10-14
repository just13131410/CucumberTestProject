package org.example.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Schlanker Spring-Kontext für die Cucumber-Glue-Klassen (Steps/Hooks).
 * <p>
 * Vorher startete {@code @SpringBootTest} pro Lauf die komplette Anwendung ein zweites Mal in
 * derselben JVM: Web-Kontext, {@code TestExecutionService} mit eigenem Executor,
 * {@code PlaywrightStartupInstaller} und den Reconcile mit Outbox-Replay. Die Steps brauchen davon
 * nur die hier importierten Beans.
 * <p>
 * Hilfs-Beans für Steps (z.B. Zustand pro Szenario mit {@code @ScenarioScope}) gehören nach
 * {@code org.example.utils} und werden per Scan gefunden. Den Scan nicht auf {@code org.example}
 * ausweiten, sonst kommen Controller, Services und Startup-Runner wieder mit.
 */
@Configuration
@Import({BrowserConfig.class, PlaywrightBrowserInstaller.class})
@ComponentScan("org.example.utils")
public class CucumberGlueConfig {
}
