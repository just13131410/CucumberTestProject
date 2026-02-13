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
}
