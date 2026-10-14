package org.example.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceChainRegistration;
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
        ResourceChainRegistration chain = mock(ResourceChainRegistration.class);
        when(registration.resourceChain(true)).thenReturn(chain);
        when(chain.addResolver(any())).thenReturn(chain);
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

    // --- S4: nur fertige Reports ausliefern ---

    private static final String RUN = "1d1a418e-c8ae-4d56-a93f-80e3cbcc39b7";

    @Test
    void isServable_ReportFoldersAllowed() {
        assertTrue(WebConfig.isServable(RUN + "/allure-report/index.html"));
        assertTrue(WebConfig.isServable(RUN + "/allure-report/data/attachments/a.txt"));
        assertTrue(WebConfig.isServable(RUN + "/cucumber-reports/Cucumber.html"));
        assertTrue(WebConfig.isServable(RUN + "/axe-result/index.html"));
        assertTrue(WebConfig.isServable(RUN + "/screenshots/FAILED_Login Szenario_1791530338170.png"));
        assertTrue(WebConfig.isServable("combined/allure-report/index.html"));
    }

    @Test
    void isServable_InternalFilesAndRawResultsBlocked() {
        for (String blocked : new String[]{
                RUN + "/status.json",
                RUN + "/integration-outbox.json",
                RUN + "/runner.log",
                RUN + "/allure-results/abc-attachment.html",
                "dumps/java_pid1.hprof",
                "combined/allure-results/x.json",
                RUN + "/allure-report/../status.json",
                "not-a-uuid/allure-report/index.html",
                RUN + "/allure-report/",
                null}) {
            assertFalse(WebConfig.isServable(blocked), String.valueOf(blocked));
        }
    }

    @Test
    void resolver_ReturnsNullForBlockedPath_WithoutTouchingFilesystem() throws Exception {
        var resolver = new WebConfig.ReportOnlyResourceResolver();
        var location = new org.springframework.core.io.FileSystemResource(System.getProperty("java.io.tmpdir") + "/");
        var method = WebConfig.ReportOnlyResourceResolver.class.getDeclaredMethod(
                "getResource", String.class, org.springframework.core.io.Resource.class);
        method.setAccessible(true);
        assertNull(method.invoke(resolver, RUN + "/status.json", location));
    }

    @Test
    void addResourceHandlers_RegistersReportOnlyResolver() {
        ReflectionTestUtils.setField(webConfig, "testResultsPath", "target/runs");
        ResourceChainRegistration chain = registration.resourceChain(true);

        webConfig.addResourceHandlers(registry);

        verify(chain).addResolver(any(WebConfig.ReportOnlyResourceResolver.class));
    }
}
