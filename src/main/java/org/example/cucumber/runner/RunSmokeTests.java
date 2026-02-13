package org.example.cucumber.runner;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runner fuer Smoke Tests
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
        value = "@smoke and not @ignore"
)
@ConfigurationParameter(
        key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, " +
                "json:target/cucumber-reports/smoke-tests.json, " +
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"
)
@ConfigurationParameter(
        key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME,
        value = "true"
)
public class RunSmokeTests {
}
