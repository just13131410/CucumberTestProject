package org.example.config;

import org.example.cucumber.service.TestExecutionService;
import org.example.hooks.CucumberHooks;
import org.example.utils.GlueScanProbe;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.SimpleThreadScope;

import static org.junit.jupiter.api.Assertions.*;

class CucumberGlueConfigTest {

    @Test
    void context_ContainsOnlyGlueDependencies_NoAppServices() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(CucumberGlueConfig.class)
                .web(WebApplicationType.NONE)
                // Cucumber registriert den Scope "cucumber-glue" erst zur Laufzeit
                .initializers(c -> c.getBeanFactory().registerScope("cucumber-glue", new SimpleThreadScope()))
                .run()) {
            assertNotNull(ctx.getBean(BrowserConfig.class));
            assertNotNull(ctx.getBean(PlaywrightBrowserInstaller.class));
            assertNotNull(ctx.getBean(GlueScanProbe.class),
                    "@ScenarioScope-Bean aus org.example.utils wird per @ComponentScan gefunden");
            assertTrue(ctx.getBeansOfType(TestExecutionService.class).isEmpty(),
                    "Kein zweiter TestExecutionService (Executor, Reconcile) im Cucumber-Kontext");
            assertTrue(ctx.getBeansOfType(PlaywrightStartupInstaller.class).isEmpty(),
                    "Kein Startup-Download pro Lauf");
        }
    }

    @Test
    void componentScan_LimitedToUtilsPackage() {
        ComponentScan scan = CucumberGlueConfig.class.getAnnotation(ComponentScan.class);

        assertArrayEquals(new String[]{"org.example.utils"}, scan.value(),
                "Scan auf org.example würde App-Services in den Cucumber-Kontext holen");
    }

    @Test
    void cucumberHooks_UseSlimConfigWithoutWebEnvironment() {
        SpringBootTest annotation = CucumberHooks.class.getAnnotation(SpringBootTest.class);

        assertArrayEquals(new Class<?>[]{CucumberGlueConfig.class}, annotation.classes());
        assertEquals(SpringBootTest.WebEnvironment.NONE, annotation.webEnvironment());
    }
}
