package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Web MVC Configuration für statische Report-Dateien unter {@code /reports/**}.
 * <p>
 * Das Ergebnisverzeichnis enthält neben den Reports auch Rohdaten und interne Dateien
 * ({@code allure-results} mit Request-Anhängen, {@code status.json}, die Integrations-Outbox mit dem
 * kompletten Request, {@code runner.log}, ggf. Heap-Dumps). Ausgeliefert werden deshalb nur die
 * fertigen Report-Ordner; alles andere beantwortet der Resolver mit 404.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Allowlist relativ zum Ergebnisverzeichnis: {@code <runId>/<report-ordner>/…} bzw. der kombinierte Report. */
    static final Pattern SERVABLE_PATH = Pattern.compile(
            "^(?!.*\\.\\.)(?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "/(?:allure-report|cucumber-reports|axe-result|screenshots)"
                    + "|combined/allure-report)/[^\\\\]+$");

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
                .addResourceLocations(locationUri)
                .resourceChain(true)
                .addResolver(new ReportOnlyResourceResolver());
    }

    static boolean isServable(String resourcePath) {
        return resourcePath != null && SERVABLE_PATH.matcher(resourcePath).matches();
    }

    /** Liefert nur Pfade aus {@link #SERVABLE_PATH}; alle anderen werden wie nicht vorhanden behandelt. */
    static class ReportOnlyResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            return isServable(resourcePath) ? super.getResource(resourcePath, location) : null;
        }
    }
}
