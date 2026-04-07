package org.example.cucumber.runner;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * IDE-Runner für lokale Entwicklung und schnelle Test-Iteration.
 * NICHT für CI/CD oder Maven-Builds gedacht.
 *
 * IntelliJ zeigt ▶-Buttons neben jedem Szenario in .feature-Files.
 * Einzelne Szenarien, ganze Features oder alle Tests per Klick ausführbar.
 *
 * Tag-Filter überschreiben: Run Configuration → VM Options → -Dcucumber.filter.tags=@SmokeTest
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
        key = Constants.GLUE_PROPERTY_NAME,
        value = "org.example.steps,org.example.hooks"
)
@ConfigurationParameter(
        key = Constants.FILTER_TAGS_PROPERTY_NAME,
        value = "not @ignore"
)
@ConfigurationParameter(
        key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty"
)
@ConfigurationParameter(
        key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME,
        value = "false"
)
public class CucumberIDERunner {
}
