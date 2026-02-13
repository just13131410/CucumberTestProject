package org.example.cucumber.runner;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Cucumber Runner fuer JUnit 5 Platform
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@SpringBootTest
@ConfigurationParameter(
        key = Constants.GLUE_PROPERTY_NAME,
        value = "org.example.steps,org.example.hooks"
)
@ConfigurationParameter(
        key = Constants.FILTER_TAGS_PROPERTY_NAME,
        value = "${cucumber.filter.tags:@smoke}"
)
@ConfigurationParameter(
        key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, " +
                "html:target/cucumber-reports/cucumber.html, " +
                "json:target/cucumber-reports/cucumber.json, " +
                "junit:target/cucumber-reports/cucumber.xml, " +
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"
)
@ConfigurationParameter(
        key = Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME,
        value = "true"
)
@ConfigurationParameter(
        key = Constants.EXECUTION_DRY_RUN_PROPERTY_NAME,
        value = "${cucumber.dry-run:false}"
)
@ConfigurationParameter(
        key = Constants.SNIPPET_TYPE_PROPERTY_NAME,
        value = "camelcase"
)
@ConfigurationParameter(
        key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME,
        value = "true"
)
@ConfigurationParameter(
        key = Constants.PARALLEL_CONFIG_STRATEGY_PROPERTY_NAME,
        value = "dynamic"
)
@ConfigurationParameter(
        key = Constants.PARALLEL_CONFIG_DYNAMIC_FACTOR_PROPERTY_NAME,
        value = "2"
)
public class RunCucumberTest {
}
