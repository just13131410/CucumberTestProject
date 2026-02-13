package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC Configuration für statische Report-Dateien
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${test.results.path:test-results}")
    private String testResultsPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String envPath = System.getenv("TEST_RESULTS_PATH");
        String resultsPath = (envPath != null && !envPath.isBlank())
            ? envPath
            : testResultsPath;

        Path absolutePath = Paths.get(resultsPath).toAbsolutePath().normalize();
        String locationUri = absolutePath.toUri().toString();
        if (!locationUri.endsWith("/")) {
            locationUri += "/";
        }

        // Serve Allure reports from test-results directory
        // Format: file:///C:/path/to/reports/
        registry.addResourceHandler("/reports/**")
                .addResourceLocations(locationUri);
    }
}
