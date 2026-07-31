package org.example.cucumber.controller;

import org.example.cucumber.model.TestStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Wandelt server-relative Report-Pfade (z.B. {@code /reports/{runId}/allure-report/index.html})
 * in absolute URLs auf Basis des aktuellen Request-Kontexts um. Von {@link TestExecutionController}
 * und {@link ReportController} gemeinsam genutzt.
 */
@Component
public class ReportUrlResolver {

    public void resolveReportUrls(TestStatus status) {
        if (status.getReportUrls() != null) {
            status.getReportUrls().replaceAll((key, value) ->
                    value.startsWith("/") ? toAbsoluteUrl(value) : value);
        }
    }

    public String toAbsoluteUrl(String relativePath) {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path(relativePath)
                .build()
                .toString();
    }
}
