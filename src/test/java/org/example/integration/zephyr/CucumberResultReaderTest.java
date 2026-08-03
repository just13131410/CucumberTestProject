package org.example.integration.zephyr;

import org.example.integration.model.ZephyrScriptResult;
import org.example.integration.model.ZephyrTestExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CucumberResultReaderTest {

    private final CucumberResultReader reader = new CucumberResultReader();

    private void writeCucumberJson(Path tempDir, UUID runId, String json) throws IOException {
        Path cucumberDir = tempDir.resolve(runId.toString()).resolve("cucumber-reports");
        Files.createDirectories(cucumberDir);
        Path file = cucumberDir.resolve("Cucumber.json");
        Files.writeString(file, json);
    }

    @Test
    void buildExecutions_AllStepsPassed_CommentListsEachStepChecked(@TempDir Path tempDir) throws IOException {
        UUID runId = UUID.randomUUID();
        writeCucumberJson(tempDir, runId, """
                [{"elements": [
                  {"tags": [{"name": "@PROJ-T53"}],
                   "steps": [
                     {"keyword": "Gegeben sei ", "name": "ich oeffne die Login-Seite", "result": {"status": "passed"}},
                     {"keyword": "Dann ", "name": "sollte ich das Dashboard sehen", "result": {"status": "passed"}}
                   ]}
                ]}]
                """);
        System.setProperty("test.results.path", tempDir.toString());
        try {
            List<ZephyrTestExecution> executions = reader.buildExecutions(runId, 0);

            assertEquals(1, executions.size());
            ZephyrTestExecution execution = executions.get(0);
            assertEquals("PROJ-T53", execution.getTestCaseKey());
            assertEquals("Pass", execution.getStatus());
            assertEquals("✅ Gegeben sei ich oeffne die Login-Seite<br>✅ Dann sollte ich das Dashboard sehen",
                    execution.getComment());

            List<ZephyrScriptResult> scriptResults = execution.getScriptResults();
            assertEquals(2, scriptResults.size());
            assertEquals(0, scriptResults.get(0).getIndex());
            assertEquals("Pass", scriptResults.get(0).getStatus());
            assertNull(scriptResults.get(0).getComment());
            assertEquals(1, scriptResults.get(1).getIndex());
            assertEquals("Pass", scriptResults.get(1).getStatus());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void buildExecutions_StepFails_CommentMarksFailureWithErrorAndSkipsRemainingSteps(@TempDir Path tempDir) throws IOException {
        UUID runId = UUID.randomUUID();
        writeCucumberJson(tempDir, runId, """
                [{"elements": [
                  {"tags": [{"name": "@PROJ-T53"}],
                   "steps": [
                     {"keyword": "Gegeben sei ", "name": "ich bin eingeloggt", "result": {"status": "passed"}},
                     {"keyword": "Wenn ", "name": "ich auf Suche klicke", "result": {"status": "failed", "error_message": "TimeoutError: timeout 30000ms exceeded\\n\\tat Page.click"}},
                     {"keyword": "Dann ", "name": "sollte die Tabelle 1 Zeile zeigen", "result": {"status": "skipped"}}
                   ]}
                ]}]
                """);
        System.setProperty("test.results.path", tempDir.toString());
        try {
            List<ZephyrTestExecution> executions = reader.buildExecutions(runId, 1);

            ZephyrTestExecution execution = executions.get(0);
            assertEquals("Fail", execution.getStatus());
            assertEquals("✅ Gegeben sei ich bin eingeloggt"
                            + "<br>❌ Wenn ich auf Suche klicke<br>&nbsp;&nbsp;&nbsp;Fehler: TimeoutError: timeout 30000ms exceeded"
                            + "<br>⏭️ Dann sollte die Tabelle 1 Zeile zeigen",
                    execution.getComment());

            List<ZephyrScriptResult> scriptResults = execution.getScriptResults();
            assertEquals(3, scriptResults.size());
            assertEquals("Pass", scriptResults.get(0).getStatus());
            assertEquals("Fail", scriptResults.get(1).getStatus());
            assertEquals("TimeoutError: timeout 30000ms exceeded", scriptResults.get(1).getComment());
            assertEquals("Not Executed", scriptResults.get(2).getStatus());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void buildExecutions_StepTextContainsHtmlSpecialChars_IsEscaped(@TempDir Path tempDir) throws IOException {
        UUID runId = UUID.randomUUID();
        writeCucumberJson(tempDir, runId, """
                [{"elements": [
                  {"tags": [{"name": "@T-1"}],
                   "steps": [
                     {"keyword": "Wenn ", "name": "ich <b>Test & Co</b> eingebe", "result": {"status": "passed"}}
                   ]}
                ]}]
                """);
        System.setProperty("test.results.path", tempDir.toString());
        try {
            List<ZephyrTestExecution> executions = reader.buildExecutions(runId, 0);

            assertEquals("✅ Wenn ich &lt;b&gt;Test &amp; Co&lt;/b&gt; eingebe", executions.get(0).getComment());
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void buildExecutions_NoCucumberJson_FallsBackToRunIdWithFailStatus() {
        UUID runId = UUID.randomUUID();
        System.setProperty("test.results.path", "non-existent-dir-" + UUID.randomUUID());
        try {
            List<ZephyrTestExecution> executions = reader.buildExecutions(runId, 1);

            assertEquals(1, executions.size());
            ZephyrTestExecution execution = executions.get(0);
            assertEquals(runId.toString().substring(0, 8), execution.getTestCaseKey());
            assertEquals("Fail", execution.getStatus());
        } finally {
            System.clearProperty("test.results.path");
        }
    }
}
