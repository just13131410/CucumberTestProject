package org.example.cucumber.service;

import org.example.config.PlaywrightBrowserInstaller;
import org.example.config.PlaywrightStartupInstaller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Start-Aufgaben laufen standardmäßig und sind per {@code app.startup-tasks.enabled=false} abschaltbar. */
class StartupReconcilerTest {

    private final TestExecutionService service = mock(TestExecutionService.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TestExecutionService.class, () -> service)
            .withBean(PlaywrightBrowserInstaller.class, () -> mock(PlaywrightBrowserInstaller.class))
            .withUserConfiguration(StartupReconciler.class, PlaywrightStartupInstaller.class);

    @Test
    void onApplicationReady_DelegatesToReconcile() {
        new StartupReconciler(service).onApplicationReady();

        verify(service).reconcileAfterRestart();
    }

    @Test
    void startupTasks_ActiveByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(StartupReconciler.class);
            assertThat(ctx).hasSingleBean(PlaywrightStartupInstaller.class);
        });
    }

    @Test
    void startupTasks_DisabledByProperty() {
        runner.withPropertyValues(StartupReconciler.STARTUP_TASKS_ENABLED + "=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(StartupReconciler.class);
            assertThat(ctx).doesNotHaveBean(PlaywrightStartupInstaller.class);
            assertThat(ctx).hasSingleBean(TestExecutionService.class);
        });
    }
}
