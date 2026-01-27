package org.example.hooks;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.example.utils.ConfigReader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AxeReportHook {
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String reportPath = ConfigReader.get("axe.reportPath", "target/axe-result/");
    /**
     * Führt den Axe Scan aus und speichert das Ergebnis sofort als JSON.
     */
    public static void runAndSave(Page page, String fileName) {

        try {
            AxeResults results = new AxeBuilder(page).analyze();
            Path path = Paths.get(reportPath);

            if (Files.notExists(path)) {
                Files.createDirectories(path);
            }

            mapper.writeValue(new File(reportPath + fileName + ".json"), results);
            String jsonString = mapper.writeValueAsString(results);
            System.out.println("Axe-Report generiert: " + fileName + System.currentTimeMillis() + ".json");
            generateSimpleHtml (results, "axe_html_report_" + System.currentTimeMillis());
            Allure.addAttachment(
                    "Accessibility Scan - " + fileName,
                    "application/json",
                    new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)),
                    ".json"
            );
        } catch (IOException e) {
            System.err.println("Fehler beim Schreiben des JSON-Reports: " + e.getMessage());
        }
    }
    private static void generateSimpleHtml(AxeResults results, String fileName) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><h1>Accessibility Report</h1>");

        for (var violation : results.getViolations()) {
            html.append("<div style='border:1px solid red; margin:10px; padding:10px;'>");
            html.append("<h2>").append(violation.getHelp()).append("</h2>");
            html.append("<p>Impact: ").append(violation.getImpact()).append("</p>");
            html.append("<a href='").append(violation.getHelpUrl()).append("'>Info</a>");
            html.append("</div>");
        }

        html.append("</body></html>");
        Files.writeString(Paths.get(reportPath + fileName + ".html"), html.toString());
    }
}