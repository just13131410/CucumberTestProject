package org.example.hooks;

import org.example.cucumber.context.TestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AxeReportHookTest {

    @AfterEach
    void cleanup() {
        TestContext.clear();
    }

    // Helper to invoke private static escapeHtml via reflection
    private String escapeHtml(String text) throws Exception {
        Method method = AxeReportHook.class.getDeclaredMethod("escapeHtml", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, text);
    }

    // Helper to invoke private static resolveReportPath via reflection
    private Path resolveReportPath() throws Exception {
        Method method = AxeReportHook.class.getDeclaredMethod("resolveReportPath");
        method.setAccessible(true);
        return (Path) method.invoke(null);
    }

    // --- escapeHtml tests ---

    @Test
    void escapeHtml_Null_ReturnsEmpty() throws Exception {
        assertEquals("", escapeHtml(null));
    }

    @Test
    void escapeHtml_EmptyString_ReturnsEmpty() throws Exception {
        assertEquals("", escapeHtml(""));
    }

    @Test
    void escapeHtml_NormalText_Unchanged() throws Exception {
        assertEquals("Hello World", escapeHtml("Hello World"));
    }

    @Test
    void escapeHtml_Ampersand_Escaped() throws Exception {
        assertEquals("foo &amp; bar", escapeHtml("foo & bar"));
    }

    @Test
    void escapeHtml_LessThan_Escaped() throws Exception {
        assertEquals("&lt;div&gt;", escapeHtml("<div>"));
    }

    @Test
    void escapeHtml_GreaterThan_Escaped() throws Exception {
        assertEquals("a &gt; b", escapeHtml("a > b"));
    }

    @Test
    void escapeHtml_DoubleQuotes_Escaped() throws Exception {
        assertEquals("attr=&quot;value&quot;", escapeHtml("attr=\"value\""));
    }

    @Test
    void escapeHtml_SingleQuotes_Escaped() throws Exception {
        assertEquals("it&#39;s", escapeHtml("it's"));
    }

    @Test
    void escapeHtml_ScriptTag_FullyEscaped() throws Exception {
        String input = "<script>alert('xss')</script>";
        String expected = "&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;";
        assertEquals(expected, escapeHtml(input));
    }

    @Test
    void escapeHtml_AllSpecialChars_Escaped() throws Exception {
        String input = "& < > \" '";
        String expected = "&amp; &lt; &gt; &quot; &#39;";
        assertEquals(expected, escapeHtml(input));
    }

    @Test
    void escapeHtml_AmpersandFirst_NoDoubleEscape() throws Exception {
        // & must be replaced first to avoid double-escaping
        String input = "&lt;";
        String expected = "&amp;lt;";
        assertEquals(expected, escapeHtml(input));
    }

    // --- resolveReportPath tests ---

    @Test
    void resolveReportPath_WithTestContext_ReturnsAxeResultDir() throws Exception {
        TestContext.init("test-run-axe");

        Path result = resolveReportPath();

        assertEquals(TestContext.getAxeResultDir(), result);
        assertTrue(result.toString().contains("axe-result"));
    }

    @Test
    void resolveReportPath_WithoutTestContext_ReturnsFallback() throws Exception {
        // TestContext not initialized, should fall back to ConfigReader
        Path result = resolveReportPath();

        assertNotNull(result);
        assertTrue(result.toString().contains("axe"));
    }

    // --- P2: inkrementelle Zusammenfassung statt Neu-Parsen aller JSONs ---

    private static com.fasterxml.jackson.databind.JsonNode json(String text) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
    }

    @Test
    void summarize_CountsPassesViolationsAndImpacts() throws Exception {
        var root = json("""
                {"url":"http://angular-gui:8080/login",
                 "passes":[{},{},{}],
                 "violations":[{"impact":"critical"},{"impact":"serious"},{"impact":"serious"},{"impact":"minor"},{"impact":null}]}
                """);

        AxeReportHook.ScanSummary s = AxeReportHook.summarize(root, "Login-audit-1791530279164_1791530279628", "x.html");

        assertEquals("http://angular-gui:8080/login", s.url);
        assertEquals(3, s.passes);
        assertEquals(5, s.violations);
        assertEquals(1, s.critical);
        assertEquals(2, s.serious);
        assertEquals(0, s.moderate);
        assertEquals(1, s.minor);
        assertEquals("x.html", s.htmlFile);
        assertFalse(s.title.matches(".*\\d{10,}.*"), "Zeitstempel nicht im Titel: " + s.title);
    }

    @Test
    void summarize_MissingFields_DefaultsToZero() throws Exception {
        AxeReportHook.ScanSummary s = AxeReportHook.summarize(json("{}"), "Leer", null);

        assertEquals("", s.url);
        assertEquals(0, s.passes);
        assertEquals(0, s.violations);
        assertNull(s.htmlFile);
    }

    @Test
    void generateIndexHtml_WritesTotalsEscapedUrlsAndNoTempFiles(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        AxeReportHook.ScanSummary a = AxeReportHook.summarize(
                json("{\"url\":\"http://x/<script>\",\"violations\":[{\"impact\":\"critical\"}]}"), "A", "A.html");
        AxeReportHook.ScanSummary b = AxeReportHook.summarize(
                json("{\"url\":\"http://y\",\"violations\":[{\"impact\":\"minor\"},{\"impact\":\"minor\"}]}"), "B", "B.html");

        AxeReportHook.generateIndexHtml(dir, java.util.List.of(a, b));

        String html = java.nio.file.Files.readString(dir.resolve("index.html"));
        assertTrue(html.contains("http://x/&lt;script&gt;"), "URL wird HTML-escaped");
        assertFalse(html.contains("<script>"));
        try (var files = java.nio.file.Files.list(dir)) {
            assertEquals(1, files.count(), "Nur index.html, keine .tmp-Reste");
        }
    }

    @Test
    void generateIndexHtml_LinksToSectionsInSameFile_WithoutDetailFiles(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        AxeReportHook.ScanSummary login = AxeReportHook.summarize(json("""
                {"url":"http://gui/login","violations":[{"id":"landmark-one-main","impact":"moderate",
                  "help":"Document should have one main landmark","helpUrl":"https://dequeuniversity.com/rules/x",
                  "nodes":[{"html":"<html lang=\\"en\\">","target":["html"]}]}]}
                """), "Login-audit-1_2", "Login-audit-1_2.html");
        AxeReportHook.ScanSummary dashboard = AxeReportHook.summarize(json("""
                {"url":"http://gui/dashboard","violations":[{"id":"button-name","impact":"critical",
                  "help":"Buttons must have discernible text","nodes":[]}]}
                """), "Dashboard-audit-3_4", "Dashboard-audit-3_4.html");

        AxeReportHook.generateIndexHtml(dir, java.util.List.of(dashboard, login));

        String html = java.nio.file.Files.readString(dir.resolve("index.html"));
        for (AxeReportHook.ScanSummary scan : java.util.List.of(login, dashboard)) {
            assertTrue(html.contains("href=\"#" + scan.anchor + "\""), "Tabelle verlinkt " + scan.anchor);
            assertTrue(html.contains("id=\"" + scan.anchor + "\""), "Abschnitt " + scan.anchor + " vorhanden");
        }
        assertTrue(html.contains("Document should have one main landmark"));
        assertTrue(html.contains("Buttons must have discernible text"));
        assertTrue(html.contains("&lt;html lang=&quot;en&quot;&gt;"), "Element-HTML escaped");
        assertFalse(html.contains("href=\"Login-audit-1_2.html\""), "keine Links auf separate Dateien");
    }

    @Test
    void summarize_AnchorIsSafeAndUnique() throws Exception {
        AxeReportHook.ScanSummary a = AxeReportHook.summarize(json("{}"), "Login-audit-1_2", null);
        AxeReportHook.ScanSummary b = AxeReportHook.summarize(json("{}"), "Login audit\"<x>_3", null);

        assertEquals("scan-Login-audit-1_2", a.anchor);
        assertTrue(b.anchor.matches("[A-Za-z0-9_-]+"), b.anchor);
        assertNotEquals(a.anchor, b.anchor);
    }

    @Test
    void renderViolations_NoViolations_ShowsSuccessMessage() throws Exception {
        assertTrue(AxeReportHook.renderViolations(json("[]")).contains("Keine Verstöße gefunden"));
        assertTrue(AxeReportHook.renderViolations(null).contains("Keine Verstöße gefunden"));
    }

    @Test
    void renderViolations_RendersImpactTargetsAndOnlyHttpHelpLinks() throws Exception {
        String html = AxeReportHook.renderViolations(json("""
                [{"id":"r1","impact":"serious","help":"H1","helpUrl":"javascript:alert(1)",
                  "nodes":[{"target":["#a",["iframe","#b"]],"html":"<b>"}]},
                 {"id":"r2","impact":null,"help":"H2","helpUrl":"https://example.org/r2"}]
                """));

        assertTrue(html.contains("violation-card serious") && html.contains("Schwerwiegend"));
        assertTrue(html.contains("violation-card minor"), "fehlender Impact -> minor");
        assertTrue(html.contains("Target: #a, iframe, #b"));
        assertTrue(html.contains("&lt;b&gt;"));
        assertFalse(html.contains("javascript:"), "nur http(s)-Hilfelinks");
        assertTrue(html.contains("href=\"https://example.org/r2\""));
    }

    @Test
    void forgetRun_RemovesRunState() throws Exception {
        Path dir = Path.of("target", "axe-forget-" + java.util.UUID.randomUUID());
        java.lang.reflect.Field runsField = AxeReportHook.class.getDeclaredField("runs");
        runsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var runs = (java.util.Map<String, AxeReportHook.RunScans>) runsField.get(null);
        runs.put(dir.toAbsolutePath().toString(), new AxeReportHook.RunScans());
        int before = AxeReportHook.trackedRuns();

        AxeReportHook.forgetRun(dir);

        assertEquals(before - 1, AxeReportHook.trackedRuns());
        assertFalse(runs.containsKey(dir.toAbsolutePath().toString()));
    }
}
