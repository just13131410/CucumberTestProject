package org.example.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebConfigTest {

    private WebConfig webConfig;
    private ResourceHandlerRegistry registry;
    private ResourceHandlerRegistration registration;

    @BeforeEach
    void setUp() {
        webConfig = new WebConfig();
        registry = mock(ResourceHandlerRegistry.class);
        registration = mock(ResourceHandlerRegistration.class);

        when(registry.addResourceHandler("/reports/**")).thenReturn(registration);
        when(registration.addResourceLocations(anyString())).thenReturn(registration);
    }

    private String captureLocation(String testResultsPath) {
        ReflectionTestUtils.setField(webConfig, "testResultsPath", testResultsPath);
        webConfig.addResourceHandlers(registry);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(registration).addResourceLocations(captor.capture());
        return captor.getValue();
    }

    @Test
    void addResourceHandlers_RegistersReportsPattern() {
        ReflectionTestUtils.setField(webConfig, "testResultsPath", "target/runs");

        webConfig.addResourceHandlers(registry);

        verify(registry).addResourceHandler("/reports/**");
    }

    @Test
    void addResourceHandlers_UsesFileUriScheme() {
        String location = captureLocation("target/runs");

        assertTrue(location.startsWith("file:/"),
                "Expected file URI scheme but got: " + location);
    }

    @Test
    void addResourceHandlers_LocationContainsConfiguredPath() {
        String location = captureLocation("target/runs");

        assertTrue(location.contains("target") && location.contains("runs"),
                "Expected location to contain 'target' and 'runs' but got: " + location);
    }

    @Test
    void addResourceHandlers_WithCustomPath() {
        String location = captureLocation("test-results");

        assertTrue(location.startsWith("file:/"),
                "Expected file URI scheme but got: " + location);
        assertTrue(location.contains("test-results"),
                "Expected location to contain 'test-results' but got: " + location);
    }

    @Test
    void addResourceHandlers_WithRelativePath() {
        String location = captureLocation("test-results");

        assertTrue(location.startsWith("file:/"),
                "Expected file URI scheme but got: " + location);
        assertTrue(location.contains("test-results"),
                "Expected location to contain 'test-results' but got: " + location);
    }

    @Test
    void addResourceHandlers_CalledOnlyOnce() {
        ReflectionTestUtils.setField(webConfig, "testResultsPath", "target/runs");

        webConfig.addResourceHandlers(registry);

        verify(registry, times(1)).addResourceHandler("/reports/**");
        verify(registration, times(1)).addResourceLocations(anyString());
    }
}
