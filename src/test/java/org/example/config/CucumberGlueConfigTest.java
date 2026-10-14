package org.example.config;

import org.example.cucumber.service.TestExecutionService;
import org.example.hooks.CucumberHooks;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class CucumberGlueConfigTest {

    @Test
    void context_ContainsOnlyGlueDependencies_NoAppServices() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(CucumberGlueConfig.class)
                .web(WebApplicationType.NONE)
                .run()) {
            assertNotNull(ctx.getBean(BrowserConfig.class));
            assertNotNull(ctx.getBean(PlaywrightBrowserInstaller.class));
            assertTrue(ctx.getBeansOfType(TestExecutionService.class).isEmpty(),
                    "Kein zweiter TestExecutionService (Executor, Reconcile) im Cucumber-Kontext");
            assertTrue(ctx.getBeansOfType(PlaywrightStartupInstaller.class).isEmpty(),
                    "Kein Startup-Download pro Lauf");
        }
    }

    @Test
    void cucumberHooks_UseSlimConfigWithoutWebEnvironment() {
        SpringBootTest annotation = CucumberHooks.class.getAnnotation(SpringBootTest.class);

        assertArrayEquals(new Class<?>[]{CucumberGlueConfig.class}, annotation.classes());
        assertEquals(SpringBootTest.WebEnvironment.NONE, annotation.webEnvironment());
    }
}
