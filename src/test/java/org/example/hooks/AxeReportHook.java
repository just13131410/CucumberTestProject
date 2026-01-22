package org.example.hooks;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Page;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AxeReportHook {
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Führt den Axe Scan aus und speichert das Ergebnis sofort als JSON.
     */
    public static void runAndSave(Page page, String fileName) {
        try {
            AxeResults results = new AxeBuilder(page).analyze();
            Path path = Paths.get("target/axe-result/");

            if (Files.notExists(path)) {
                Files.createDirectories(path);
            }

            mapper.writeValue(new File("target/axe-result/" + fileName + ".json"), results);
            System.out.println("Axe-Report generiert: " + fileName + System.currentTimeMillis() + ".json");
            generateSimpleHtml (results, "axe_html_report_" + System.currentTimeMillis());
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
        Files.writeString(Paths.get("target/axe-result/" + fileName + ".html"), html.toString());
    }
}