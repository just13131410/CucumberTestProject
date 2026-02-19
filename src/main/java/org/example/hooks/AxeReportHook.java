package org.example.hooks;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.example.cucumber.context.TestContext;
import org.example.utils.ConfigReader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class AxeReportHook {
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Tracks already-scanned URLs per output directory (= per run).
     * Key: absolute path of the axe result directory (unique per run).
     * Value: set of page URLs already scanned in that run.
     * Prevents duplicate axe scans when multiple scenarios visit the same page.
     */
    private static final ConcurrentHashMap<String, Set<String>> scannedUrlsByRun =
            new ConcurrentHashMap<>();

    /**
     * Resolves the axe report output directory.
     * Uses per-run isolation if TestContext is active, falls back to config/default.
     */
    private static Path resolveReportPath() {
        if (TestContext.isInitialized()) {
            return TestContext.getAxeResultDir();
        }
        return Paths.get(ConfigReader.get("axe.reportPath", "target/axe-result/"));
    }

    /**
     * Fuehrt den Axe Scan aus und speichert das Ergebnis sofort als JSON + HTML.
     * Aktualisiert danach die Uebersichtsseite (index.html) im Report-Verzeichnis.
     * Innerhalb eines Runs wird jede URL nur einmal gescannt – Duplikate werden uebersprungen.
     */
    public static void runAndSave(Page page, String fileName) {
        Path reportDir = resolveReportPath();
        String runKey  = reportDir.toAbsolutePath().toString();
        String pageUrl = page.url();

        Set<String> scanned = scannedUrlsByRun.computeIfAbsent(runKey, k -> ConcurrentHashMap.newKeySet());
        if (!scanned.add(pageUrl)) {
            System.out.println("Axe-Scan uebersprungen (URL bereits gescannt in diesem Run): " + pageUrl);
            return;
        }

        try {
            AxeResults results = new AxeBuilder(page).analyze();

            if (Files.notExists(reportDir)) {
                Files.createDirectories(reportDir);
            }

            // Stabile Benennung: JSON und HTML teilen sich den gleichen Basisnamen
            String baseName = fileName + "_" + System.currentTimeMillis();

            mapper.writeValue(new File(reportDir.resolve(baseName + ".json").toString()), results);
            String jsonString = mapper.writeValueAsString(results);
            System.out.println("Axe-Report generiert: " + baseName + ".json");

            generateSimpleHtml(results, baseName, reportDir);
            generateIndexHtml(reportDir);

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

    // ───────────────────────────────────────────────
    //  Uebersichtsseite (index.html)
    // ───────────────────────────────────────────────

    /**
     * Liest alle JSON-Dateien im Report-Verzeichnis ein und erzeugt eine
     * index.html mit Gesamtstatistik und Verlinkung zu den Einzelberichten.
     */
    static void generateIndexHtml(Path reportDir) throws IOException {
        List<ScanSummary> scans = collectScans(reportDir);

        int totalViolations = scans.stream().mapToInt(s -> s.violations).sum();
        int totalPasses = scans.stream().mapToInt(s -> s.passes).sum();
        int totalCritical = scans.stream().mapToInt(s -> s.critical).sum();
        int totalSerious = scans.stream().mapToInt(s -> s.serious).sum();
        int totalModerate = scans.stream().mapToInt(s -> s.moderate).sum();
        int totalMinor = scans.stream().mapToInt(s -> s.minor).sum();

        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html lang="de">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Barrierefreiheit – Testrun-Übersicht</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        line-height: 1.6; color: #333; background: #f5f5f5; padding: 20px;
                    }

                    .container {
                        max-width: 1200px; margin: 0 auto; background: white;
                        border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden;
                    }

                    header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white; padding: 30px; text-align: center;
                    }

                    h1 { font-size: 2rem; margin-bottom: 10px; }
                    .subtitle { font-size: 1rem; opacity: 0.9; }

                    .summary {
                        display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
                        gap: 20px; padding: 30px; background: #f8f9fa; border-bottom: 1px solid #e0e0e0;
                    }

                    .summary-card {
                        background: white; padding: 20px; border-radius: 8px;
                        text-align: center; box-shadow: 0 2px 4px rgba(0,0,0,0.05); border-left: 4px solid;
                    }
                    .summary-card.success { border-color: #28a745; }
                    .summary-card.danger  { border-color: #dc3545; }
                    .summary-card.warning { border-color: #ffc107; }
                    .summary-card.info    { border-color: #667eea; }
                    .summary-card .number { font-size: 2.5rem; font-weight: bold; margin: 10px 0; }
                    .summary-card.success .number { color: #28a745; }
                    .summary-card.danger  .number { color: #dc3545; }
                    .summary-card.info    .number { color: #667eea; }
                    .summary-card .label {
                        font-size: 0.9rem; color: #666; text-transform: uppercase; letter-spacing: 0.5px;
                    }

                    .scans-section { padding: 30px; }

                    .section-title {
                        font-size: 1.5rem; margin-bottom: 20px; color: #333;
                        border-bottom: 3px solid #667eea; padding-bottom: 10px;
                    }

                    table {
                        width: 100%; border-collapse: collapse; margin-top: 10px;
                    }
                    th, td {
                        padding: 12px 16px; text-align: left; border-bottom: 1px solid #e0e0e0;
                    }
                    th {
                        background: #f8f9fa; font-weight: 600; color: #555;
                        text-transform: uppercase; font-size: 0.85rem; letter-spacing: 0.5px;
                    }
                    tr:hover { background: #f8f9fa; }

                    .badge {
                        display: inline-block; padding: 3px 10px; border-radius: 12px;
                        font-size: 0.8rem; font-weight: 600;
                    }
                    .badge.success { background: #d4edda; color: #155724; }
                    .badge.danger  { background: #f8d7da; color: #721c24; }
                    .badge.warning { background: #fff3cd; color: #856404; }

                    .report-link {
                        color: #667eea; text-decoration: none; font-weight: 500;
                    }
                    .report-link:hover { color: #764ba2; text-decoration: underline; }
                    .report-link:focus { outline: 2px solid #667eea; outline-offset: 2px; }

                    .no-scans {
                        text-align: center; padding: 60px 20px; color: #666;
                    }

                    footer {
                        background: #f8f9fa; padding: 20px; text-align: center;
                        border-top: 1px solid #e0e0e0; color: #666; font-size: 0.9rem;
                    }

                    .skip-link {
                        position: absolute; top: -40px; left: 0; background: #667eea;
                        color: white; padding: 8px 15px; text-decoration: none;
                        border-radius: 0 0 5px 0; z-index: 100;
                    }
                    .skip-link:focus { top: 0; }

                    @media (max-width: 768px) {
                        .summary { grid-template-columns: 1fr 1fr; }
                        table { font-size: 0.9rem; }
                        th, td { padding: 8px 10px; }
                        h1 { font-size: 1.5rem; }
                    }

                    @media print {
                        body { background: white; }
                        tr { page-break-inside: avoid; }
                    }
                </style>
            </head>
            <body>
                <a href="#main-content" class="skip-link">Zum Hauptinhalt springen</a>
                <div class="container">
                    <header role="banner">
                        <h1>Barrierefreiheit &ndash; Testrun-&Uuml;bersicht</h1>
                        <p class="subtitle">Alle automatisierten WCAG 2.1 Pr&uuml;fungen dieses Testlaufs</p>
                    </header>

                    <section class="summary" aria-label="Gesamtstatistik">
                        <div class="summary-card info">
                            <div class="label">Pr&uuml;fungen</div>
                            <div class="number">""").append(scans.size()).append("""
                            </div>
                        </div>
                        <div class="summary-card success">
                            <div class="label">Bestanden</div>
                            <div class="number">""").append(totalPasses).append("""
                            </div>
                        </div>
                        <div class="summary-card danger">
                            <div class="label">Verst&ouml;&szlig;e</div>
                            <div class="number">""").append(totalViolations).append("""
                            </div>
                        </div>
                        <div class="summary-card danger">
                            <div class="label">Kritisch</div>
                            <div class="number">""").append(totalCritical).append("""
                            </div>
                        </div>
                        <div class="summary-card danger">
                            <div class="label">Schwerwiegend</div>
                            <div class="number">""").append(totalSerious).append("""
                            </div>
                        </div>
                        <div class="summary-card warning">
                            <div class="label">Moderat</div>
                            <div class="number">""").append(totalModerate).append("""
                            </div>
                        </div>
                        <div class="summary-card warning">
                            <div class="label">Geringf&uuml;gig</div>
                            <div class="number">""").append(totalMinor).append("""
                            </div>
                        </div>
                    </section>

                    <main id="main-content" class="scans-section" role="main">
            """);

        if (scans.isEmpty()) {
            html.append("            <div class=\"no-scans\"><p>Keine Pr&uuml;fungen vorhanden.</p></div>\n");
        } else {
            html.append("            <h2 class=\"section-title\">Einzelne Pr&uuml;fungen (").append(scans.size()).append(")</h2>\n");
            html.append("""
                        <table>
                            <thead>
                                <tr>
                                    <th scope="col">Pr&uuml;fung</th>
                                    <th scope="col">Gepr&uuml;fte URL</th>
                                    <th scope="col">Bestanden</th>
                                    <th scope="col">Verst&ouml;&szlig;e</th>
                                    <th scope="col">Status</th>
                                    <th scope="col">Detailbericht</th>
                                </tr>
                            </thead>
                            <tbody>
            """);

            for (ScanSummary scan : scans) {
                String badgeClass = scan.violations == 0 ? "success"
                        : scan.critical > 0 ? "danger" : "warning";
                String badgeLabel = scan.violations == 0 ? "Bestanden"
                        : scan.critical > 0 ? "Kritisch" : "Auff\u00e4llig";

                html.append("                <tr>\n");
                html.append("                    <td>").append(escapeHtml(scan.title)).append("</td>\n");
                html.append("                    <td>").append(escapeHtml(scan.url)).append("</td>\n");
                html.append("                    <td>").append(scan.passes).append("</td>\n");
                html.append("                    <td>").append(scan.violations).append("</td>\n");
                html.append("                    <td><span class=\"badge ").append(badgeClass).append("\">").append(badgeLabel).append("</span></td>\n");
                if (scan.htmlFile != null) {
                    html.append("                    <td><a class=\"report-link\" href=\"").append(escapeHtml(scan.htmlFile)).append("\">Bericht &ouml;ffnen</a></td>\n");
                } else {
                    html.append("                    <td>&ndash;</td>\n");
                }
                html.append("                </tr>\n");
            }

            html.append("            </tbody></table>\n");
        }

        html.append("""
                    </main>
                    <footer role="contentinfo">
                        <p>Generiert mit axe-core &bull; WCAG 2.1 Level A/AA Konformit&auml;t</p>
                        <p>Bericht erstellt am:\u0020""").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).append("""
                        </p>
                    </footer>
                </div>
            </body>
            </html>
            """);

        Files.writeString(reportDir.resolve("index.html"), html.toString());
    }

    /**
     * Liest alle JSON-Dateien im Verzeichnis und extrahiert die Zusammenfassungsdaten.
     */
    private static List<ScanSummary> collectScans(Path reportDir) {
        List<ScanSummary> scans = new ArrayList<>();

        try (Stream<Path> files = Files.list(reportDir)) {
            List<Path> jsonFiles = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            for (Path jsonFile : jsonFiles) {
                try {
                    JsonNode root = mapper.readTree(jsonFile.toFile());
                    ScanSummary s = new ScanSummary();

                    // Titel aus Dateiname ableiten (z.B. "LoginSuccessfully-audit1234567890_1707812345678")
                    String rawName = jsonFile.getFileName().toString().replace(".json", "");
                    s.title = beautifyTitle(rawName);

                    // URL der geprueften Seite
                    s.url = root.has("url") ? root.get("url").asText("") : "";

                    // Statistiken
                    s.passes = root.has("passes") ? root.get("passes").size() : 0;
                    s.violations = root.has("violations") ? root.get("violations").size() : 0;

                    JsonNode violations = root.get("violations");
                    if (violations != null && violations.isArray()) {
                        for (JsonNode v : violations) {
                            String impact = v.has("impact") ? v.get("impact").asText("") : "";
                            switch (impact) {
                                case "critical" -> s.critical++;
                                case "serious" -> s.serious++;
                                case "moderate" -> s.moderate++;
                                case "minor" -> s.minor++;
                            }
                        }
                    }

                    // Passenden HTML-Einzelbericht suchen
                    String htmlName = rawName + ".html";
                    if (Files.exists(reportDir.resolve(htmlName))) {
                        s.htmlFile = htmlName;
                    }

                    scans.add(s);
                } catch (IOException e) {
                    System.err.println("Fehler beim Lesen von " + jsonFile.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Auflisten der Axe-Reports: " + e.getMessage());
        }

        return scans;
    }

    /**
     * Erzeugt einen lesbaren Titel aus dem Dateinamen.
     * "LoginSuccessfully-audit1707812345678_1707812345999" -> "Login Successfully - Audit"
     */
    private static String beautifyTitle(String rawName) {
        // Timestamp-Suffix entfernen (letzter _\d+ Teil)
        String name = rawName.replaceAll("_\\d+$", "");
        // Eingebettete Timestamps aus dem Originalnamen entfernen (z.B. audit1707812345678)
        name = name.replaceAll("\\d{10,}", "");
        // CamelCase aufloesen
        name = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        // Bindestriche und Unterstriche durch Leerzeichen
        name = name.replace("-", " - ").replace("_", " ");
        // Doppelte Leerzeichen bereinigen
        name = name.replaceAll("\\s{2,}", " ").trim();
        // Leere geschweifte Klammern entfernen
        name = name.replace("{}", "").trim();
        // Trailendes " - " entfernen
        if (name.endsWith(" -")) {
            name = name.substring(0, name.length() - 2).trim();
        }
        return name.isEmpty() ? "Barrierefreiheits-Scan" : name;
    }

    private static class ScanSummary {
        String title = "";
        String url = "";
        int passes;
        int violations;
        int critical;
        int serious;
        int moderate;
        int minor;
        String htmlFile;
    }

    // ───────────────────────────────────────────────
    //  Einzelbericht (pro Scan)
    // ───────────────────────────────────────────────

    private static void generateSimpleHtml(AxeResults results, String fileName, Path reportDir) throws IOException {
        int totalViolations = results.getViolations().size();
        int totalPasses = results.getPasses().size();
        int criticalCount = (int) results.getViolations().stream().filter(v -> "critical".equals(v.getImpact())).count();
        int seriousCount = (int) results.getViolations().stream().filter(v -> "serious".equals(v.getImpact())).count();
        int moderateCount = (int) results.getViolations().stream().filter(v -> "moderate".equals(v.getImpact())).count();
        int minorCount = (int) results.getViolations().stream().filter(v -> "minor".equals(v.getImpact())).count();

        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html lang="de">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Barrierefreiheits-Prüfbericht</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }

                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        background: #f5f5f5;
                        padding: 20px;
                    }

                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        background: white;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                        overflow: hidden;
                    }

                    header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 30px;
                        text-align: center;
                    }

                    h1 {
                        font-size: 2rem;
                        margin-bottom: 10px;
                    }

                    .subtitle {
                        font-size: 1rem;
                        opacity: 0.9;
                    }

                    .back-link {
                        display: inline-block;
                        margin-top: 12px;
                        color: white;
                        opacity: 0.9;
                        text-decoration: none;
                        font-size: 0.95rem;
                    }
                    .back-link:hover { opacity: 1; text-decoration: underline; }

                    .summary {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 20px;
                        padding: 30px;
                        background: #f8f9fa;
                        border-bottom: 1px solid #e0e0e0;
                    }

                    .summary-card {
                        background: white;
                        padding: 20px;
                        border-radius: 8px;
                        text-align: center;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.05);
                        border-left: 4px solid;
                    }

                    .summary-card.success { border-color: #28a745; }
                    .summary-card.danger  { border-color: #dc3545; }
                    .summary-card.warning { border-color: #ffc107; }
                    .summary-card .number { font-size: 2.5rem; font-weight: bold; margin: 10px 0; }
                    .summary-card.success .number { color: #28a745; }
                    .summary-card.danger  .number { color: #dc3545; }
                    .summary-card .label {
                        font-size: 0.9rem; color: #666; text-transform: uppercase; letter-spacing: 0.5px;
                    }

                    .violations-section { padding: 30px; }

                    .section-title {
                        font-size: 1.5rem; margin-bottom: 20px; color: #333;
                        border-bottom: 3px solid #667eea; padding-bottom: 10px;
                    }

                    .violation-card {
                        background: white; border: 1px solid #e0e0e0; border-left: 4px solid;
                        border-radius: 6px; padding: 20px; margin-bottom: 20px;
                        transition: box-shadow 0.3s ease;
                    }
                    .violation-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    .violation-card.critical { border-left-color: #721c24; background: #f8d7da; }
                    .violation-card.serious  { border-left-color: #dc3545; background: #fff5f5; }
                    .violation-card.moderate  { border-left-color: #ffc107; background: #fff9e6; }
                    .violation-card.minor     { border-left-color: #17a2b8; background: #e7f7f9; }

                    .violation-header {
                        display: flex; align-items: center; justify-content: space-between;
                        margin-bottom: 15px; flex-wrap: wrap; gap: 10px;
                    }
                    .violation-title { font-size: 1.2rem; font-weight: 600; color: #333; flex: 1; }

                    .impact-badge {
                        display: inline-block; padding: 5px 15px; border-radius: 20px;
                        font-size: 0.85rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;
                    }
                    .impact-badge.critical { background: #721c24; color: white; }
                    .impact-badge.serious  { background: #dc3545; color: white; }
                    .impact-badge.moderate { background: #ffc107; color: #333; }
                    .impact-badge.minor    { background: #17a2b8; color: white; }

                    .violation-description { margin-bottom: 15px; color: #555; line-height: 1.6; }

                    .violation-id {
                        font-family: 'Courier New', monospace; background: #f5f5f5;
                        padding: 2px 8px; border-radius: 4px; font-size: 0.85rem;
                        color: #666; display: inline-block; margin-bottom: 10px;
                    }

                    .violation-nodes { background: #f8f9fa; border-radius: 4px; padding: 15px; margin-top: 15px; }
                    .violation-nodes summary { cursor: pointer; font-weight: 600; color: #667eea; padding: 5px; user-select: none; }
                    .violation-nodes summary:hover { color: #764ba2; }
                    .violation-nodes summary:focus { outline: 2px solid #667eea; outline-offset: 2px; }

                    .node-item { background: white; border-left: 3px solid #667eea; padding: 10px; margin: 10px 0; border-radius: 4px; }
                    .node-target { font-family: 'Courier New', monospace; color: #d73a49; font-size: 0.9rem; margin-bottom: 8px; }
                    .node-html {
                        font-family: 'Courier New', monospace; background: #f6f8fa; padding: 10px;
                        border-radius: 4px; overflow-x: auto; font-size: 0.85rem; color: #24292e; margin-top: 8px;
                    }

                    .help-link {
                        display: inline-block; margin-top: 10px; padding: 10px 20px;
                        background: #667eea; color: white; text-decoration: none;
                        border-radius: 5px; font-weight: 500; transition: background 0.3s ease;
                    }
                    .help-link:hover { background: #764ba2; }
                    .help-link:focus { outline: 3px solid #667eea; outline-offset: 2px; }

                    .no-violations { text-align: center; padding: 60px 20px; color: #28a745; }
                    .no-violations svg { width: 80px; height: 80px; margin-bottom: 20px; }
                    .no-violations h2 { font-size: 1.8rem; margin-bottom: 10px; }

                    footer {
                        background: #f8f9fa; padding: 20px; text-align: center;
                        border-top: 1px solid #e0e0e0; color: #666; font-size: 0.9rem;
                    }

                    .skip-link {
                        position: absolute; top: -40px; left: 0; background: #667eea;
                        color: white; padding: 8px 15px; text-decoration: none;
                        border-radius: 0 0 5px 0; z-index: 100;
                    }
                    .skip-link:focus { top: 0; }

                    @media (max-width: 768px) {
                        .summary { grid-template-columns: 1fr; }
                        .violation-header { flex-direction: column; align-items: flex-start; }
                        h1 { font-size: 1.5rem; }
                    }

                    @media print {
                        body { background: white; }
                        .violation-card { page-break-inside: avoid; }
                    }
                </style>
            </head>
            <body>
                <a href="#main-content" class="skip-link">Zum Hauptinhalt springen</a>

                <div class="container">
                    <header role="banner">
                        <h1>Barrierefreiheits-Pr&uuml;fbericht</h1>
                        <p class="subtitle">Automatisierte WCAG 2.1 Pr&uuml;fung mit axe-core</p>
                        <a href="index.html" class="back-link">&larr; Zur&uuml;ck zur &Uuml;bersicht</a>
                    </header>

                    <section class="summary" aria-label="Zusammenfassung der Ergebnisse">
                        <div class="summary-card success">
                            <div class="label">Erfolgreiche Tests</div>
                            <div class="number">""").append(totalPasses).append("""
                </div>
                        </div>
                        <div class="summary-card danger">
                            <div class="label">Verstöße Gesamt</div>
                            <div class="number">""").append(totalViolations).append("""
                </div>
                        </div>
                        <div class="summary-card danger">
                            <div class="label">Kritisch</div>
                            <div class="number">""").append(criticalCount).append("""
                </div>
                        </div>
                        <div class="summary-card danger">
                            <div class="label">Schwerwiegend</div>
                            <div class="number">""").append(seriousCount).append("""
                </div>
                        </div>
                        <div class="summary-card warning">
                            <div class="label">Moderat</div>
                            <div class="number">""").append(moderateCount).append("""
                </div>
                        </div>
                        <div class="summary-card warning">
                            <div class="label">Geringfügig</div>
                            <div class="number">""").append(minorCount).append("""
                </div>
                        </div>
                    </section>

                    <main id="main-content" class="violations-section" role="main">
            """);

        if (totalViolations == 0) {
            html.append("""
                        <div class="no-violations">
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                            <h2>Keine Verstöße gefunden!</h2>
                            <p>Die Seite erfüllt alle geprüften Barrierefreiheits-Kriterien.</p>
                        </div>
                """);
        } else {
            html.append("                        <h2 class=\"section-title\">Gefundene Verstöße (").append(totalViolations).append(")</h2>\n");

            for (var violation : results.getViolations()) {
                String impact = violation.getImpact() != null ? violation.getImpact() : "minor";
                String impactLabel = switch (impact) {
                    case "critical" -> "Kritisch";
                    case "serious" -> "Schwerwiegend";
                    case "moderate" -> "Moderat";
                    case "minor" -> "Geringfügig";
                    default -> impact;
                };

                html.append("                        <article class=\"violation-card ").append(impact).append("\">\n");
                html.append("                            <div class=\"violation-header\">\n");
                html.append("                                <h3 class=\"violation-title\">").append(escapeHtml(violation.getHelp())).append("</h3>\n");
                html.append("                                <span class=\"impact-badge ").append(impact).append("\">").append(impactLabel).append("</span>\n");
                html.append("                            </div>\n");
                html.append("                            <div class=\"violation-id\">Regel-ID: ").append(escapeHtml(violation.getId())).append("</div>\n");
                html.append("                            <p class=\"violation-description\">").append(escapeHtml(violation.getDescription())).append("</p>\n");

                if (violation.getNodes() != null && !violation.getNodes().isEmpty()) {
                    html.append("                            <details class=\"violation-nodes\">\n");
                    html.append("                                <summary>Betroffene Elemente anzeigen (").append(violation.getNodes().size()).append(")</summary>\n");

                    for (var node : violation.getNodes()) {
                        html.append("                                <div class=\"node-item\">\n");
                        if (node.getTarget() != null) {
                            String target = String.valueOf(node.getTarget());
                            if (!target.isEmpty() && !"null".equals(target)) {
                                html.append("                                    <div class=\"node-target\">Target: ").append(escapeHtml(target)).append("</div>\n");
                            }
                        }
                        if (node.getHtml() != null && !node.getHtml().isEmpty()) {
                            html.append("                                    <div class=\"node-html\">").append(escapeHtml(node.getHtml())).append("</div>\n");
                        }
                        html.append("                                </div>\n");
                    }
                    html.append("                            </details>\n");
                }

                html.append("                            <a href=\"").append(escapeHtml(violation.getHelpUrl())).append("\" class=\"help-link\" target=\"_blank\" rel=\"noopener noreferrer\">Mehr Informationen und Lösungsvorschläge</a>\n");
                html.append("                        </article>\n");
            }
        }

        html.append("""
                    </main>

                    <footer role="contentinfo">
                        <p>Generiert mit axe-core • WCAG 2.1 Level A/AA Konformität</p>
                        <p>Bericht erstellt am: """).append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).append("""
                </p>
                    </footer>
                </div>
            </body>
            </html>
            """);

        Files.writeString(reportDir.resolve(fileName + ".html"), html.toString());
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
