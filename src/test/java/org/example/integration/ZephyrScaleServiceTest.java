package org.example.integration;

import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestStatus;
import org.example.integration.jira.JiraClient;
import org.example.integration.model.*;
import org.example.integration.zephyr.CucumberResultReader;
import org.example.integration.zephyr.MockIntegrationService;
import org.example.integration.zephyr.ZephyrScaleClient;
import org.example.integration.zephyr.ZephyrScaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZephyrScaleServiceTest {

    @Mock
    private ZephyrScaleClient zephyrClient;
    @Mock
    private JiraClient jiraClient;

    private ZephyrScaleService service;

    private MockIntegrationService mockIntegrationService;

    @BeforeEach
    void setUp() {
        mockIntegrationService = new MockIntegrationService();
        ReflectionTestUtils.setField(mockIntegrationService, "zephyrBaseUrl", "https://jira.test.com");

        service = new ZephyrScaleService(zephyrClient, jiraClient, new CucumberResultReader(), mockIntegrationService);
        ReflectionTestUtils.setField(service, "zephyrEnabled", true);
        ReflectionTestUtils.setField(service, "defaultProjectKey", "PROJ");
        ReflectionTestUtils.setField(service, "jiraEnabled", false);
        ReflectionTestUtils.setField(service, "jiraAssigneeAccountId", "automation-user");
        ReflectionTestUtils.setField(service, "jiraIssueType", "Bug");
        ReflectionTestUtils.setField(service, "mockEnabled", false);
    }

    private TestExecutionRequest createRequest(List<String> tags) {
        TestExecutionRequest req = new TestExecutionRequest();
        req.setTags(tags);
        req.setEnvironment("dev");
        req.setProjectKey("PROJ");
        return req;
    }

    private ZephyrTestCycle stubCycle(String key) {
        ZephyrTestCycle cycle = new ZephyrTestCycle();
        cycle.setKey(key);
        cycle.setId(1L);
        when(zephyrClient.createTestCycle(any())).thenReturn(cycle);
        return cycle;
    }

    // --- Guard checks ---

    @Test
    void uploadRunResults_BothDisabled_DoesNothing() {
        ReflectionTestUtils.setField(service, "zephyrEnabled", false);
        ReflectionTestUtils.setField(service, "jiraEnabled", false);
        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 1, new TestStatus());
        verifyNoInteractions(zephyrClient, jiraClient);
    }

    @Test
    void uploadRunResults_NoProjectKey_DoesNothing() {
        ReflectionTestUtils.setField(service, "defaultProjectKey", "");
        TestExecutionRequest req = createRequest(List.of("@smoke"));
        req.setProjectKey(null);
        service.uploadRunResults(UUID.randomUUID(), req, 1, new TestStatus());
        verifyNoInteractions(zephyrClient, jiraClient);
    }

    @Test
    void uploadRunResults_ZephyrDisabled_JiraEnabled_FailedRun_CreatesTicket() {
        ReflectionTestUtils.setField(service, "zephyrEnabled", false);
        ReflectionTestUtils.setField(service, "jiraEnabled", true);
        JiraIssueResponse jiraResponse = new JiraIssueResponse();
        jiraResponse.setKey("PROJ-77");
        when(jiraClient.createIssue(any())).thenReturn(jiraResponse);

        TestStatus status = new TestStatus();
        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 1, status);

        verifyNoInteractions(zephyrClient);
        verify(jiraClient).createIssue(any());
        assertEquals("PROJ-77", status.getJiraTicketKey());
    }

    @Test
    void uploadRunResults_ZephyrDisabled_JiraEnabled_SuccessfulRun_NoTicket() {
        ReflectionTestUtils.setField(service, "zephyrEnabled", false);
        ReflectionTestUtils.setField(service, "jiraEnabled", true);

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 0, new TestStatus());

        verifyNoInteractions(zephyrClient, jiraClient);
    }

    // --- Testrun-Erstellung: kein Folder-Anlegen mehr, "folder" ist ein String-Pfad aus der Config ---

    @Test
    void uploadRunResults_NoResultFolderConfigured_BodyHasNoFolderField() {
        stubCycle("T-R1");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@SmokeTest")), 0, new TestStatus());

        verify(zephyrClient).createTestCycle(argThat(m ->
                "PROJ".equals(m.get("projectKey")) && !m.containsKey("folder")));
    }

    @Test
    void uploadRunResults_ResultFolderConfigured_IncludesFolderPathInBody() {
        ReflectionTestUtils.setField(service, "resultFolder", "/Testautomation/Smoketest");
        stubCycle("T-R1");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@SmokeTest")), 0, new TestStatus());

        verify(zephyrClient).createTestCycle(argThat(m ->
                "/Testautomation/Smoketest".equals(m.get("folder"))));
    }

    @Test
    void uploadRunResults_RequestResultFolderOverridesGlobalConfig() {
        ReflectionTestUtils.setField(service, "resultFolder", "/Testautomation/Smoketest");
        stubCycle("T-R1");

        TestExecutionRequest req = createRequest(List.of("@API-Test"));
        req.setZephyrResultFolder("/Testautomation/API");

        service.uploadRunResults(UUID.randomUUID(), req, 0, new TestStatus());

        verify(zephyrClient).createTestCycle(argThat(m ->
                "/Testautomation/API".equals(m.get("folder"))));
    }

    // --- Testfaelle aus Template-Testrun klonen ---

    @Test
    void uploadRunResults_NoTemplateConfigured_CreatesRunWithoutItems() {
        stubCycle("T-R1");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@SmokeTest")), 0, new TestStatus());

        verify(zephyrClient, never()).getTestRun(any());
        verify(zephyrClient).createTestCycle(argThat(m -> !m.containsKey("items")));
    }

    @Test
    void uploadRunResults_TemplateConfigured_ClonesTestCaseItemsIntoNewRun() {
        ReflectionTestUtils.setField(service, "templateTestRunKey", "PROJ-R1");
        ZephyrTestRun template = new ZephyrTestRun();
        ZephyrTestRunItem item1 = new ZephyrTestRunItem();
        item1.setTestCaseKey("PROJ-T1");
        ZephyrTestRunItem item2 = new ZephyrTestRunItem();
        item2.setTestCaseKey("PROJ-T2");
        template.setItems(List.of(item1, item2));
        when(zephyrClient.getTestRun("PROJ-R1")).thenReturn(template);
        stubCycle("T-R2");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@SmokeTest")), 0, new TestStatus());

        verify(zephyrClient).getTestRun("PROJ-R1");
        verify(zephyrClient).createTestCycle(argThat(m -> {
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, String>> items = (List<java.util.Map<String, String>>) m.get("items");
            return items != null && items.size() == 2
                    && "PROJ-T1".equals(items.get(0).get("testCaseKey"))
                    && "PROJ-T2".equals(items.get(1).get("testCaseKey"));
        }));
    }

    @Test
    void uploadRunResults_RequestTemplateOverridesGlobalConfig_ClonesFromRequestTemplate() {
        ReflectionTestUtils.setField(service, "templateTestRunKey", "PROJ-R1");
        ZephyrTestRun apiTemplate = new ZephyrTestRun();
        ZephyrTestRunItem apiItem = new ZephyrTestRunItem();
        apiItem.setTestCaseKey("PROJ-T99");
        apiTemplate.setItems(List.of(apiItem));
        when(zephyrClient.getTestRun("PROJ-R2")).thenReturn(apiTemplate);
        stubCycle("T-R2");

        TestExecutionRequest req = createRequest(List.of("@API-Test"));
        req.setZephyrTemplateTestRunKey("PROJ-R2");

        service.uploadRunResults(UUID.randomUUID(), req, 0, new TestStatus());

        verify(zephyrClient, never()).getTestRun("PROJ-R1");
        verify(zephyrClient).getTestRun("PROJ-R2");
        verify(zephyrClient).createTestCycle(argThat(m -> {
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, String>> items = (List<java.util.Map<String, String>>) m.get("items");
            return items != null && items.size() == 1 && "PROJ-T99".equals(items.get(0).get("testCaseKey"));
        }));
    }

    @Test
    void uploadRunResults_TemplateTestRunNotFound_CreatesRunWithoutItems() {
        ReflectionTestUtils.setField(service, "templateTestRunKey", "PROJ-R404");
        when(zephyrClient.getTestRun("PROJ-R404")).thenReturn(null);
        stubCycle("T-R3");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@SmokeTest")), 0, new TestStatus());

        verify(zephyrClient).createTestCycle(argThat(m -> !m.containsKey("items")));
    }

    // --- Cucumber JSON parsing (detailed mode) ---

    @Test
    void uploadRunResults_DetailedMode_ParsesCucumberJson(@TempDir Path tempDir) throws IOException {
        String cucumberJson = """
                [{"elements": [
                  {"tags": [{"name": "@T-3511"}, {"name": "@Backend"}],
                   "steps": [{"result": {"status": "passed"}}, {"result": {"status": "passed"}}]}
                ]}]
                """;
        UUID runId = UUID.randomUUID();
        Path cucumberDir = tempDir.resolve(runId.toString()).resolve("cucumber-reports");
        Files.createDirectories(cucumberDir);
        Files.writeString(cucumberDir.resolve("Cucumber.json"), cucumberJson);

        System.setProperty("test.results.path", tempDir.toString());
        try {
            stubCycle("T-R1");

            service.uploadRunResults(runId, createRequest(List.of("@Backend")), 0, new TestStatus());

            verify(zephyrClient).uploadTestResults(eq("T-R1"), argThat(execs ->
                    execs.size() == 1
                            && "T-3511".equals(execs.get(0).getTestCaseKey())
                            && "Pass".equals(execs.get(0).getStatus())));
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    // --- Report-Attachments (Cucumber-/Axe-Report auf Cycle-Ebene) ---

    @Test
    void uploadRunResults_ReportFilesExist_AttachesBothToCycle(@TempDir Path tempDir) throws IOException {
        UUID runId = UUID.randomUUID();
        Path runDir = tempDir.resolve(runId.toString());
        Files.createDirectories(runDir.resolve("cucumber-reports"));
        Files.createDirectories(runDir.resolve("axe-result"));
        Path cucumberHtml = runDir.resolve("cucumber-reports").resolve("Cucumber.html");
        Path axeIndex = runDir.resolve("axe-result").resolve("index.html");
        Files.writeString(cucumberHtml, "<html>cucumber</html>");
        Files.writeString(axeIndex, "<html>axe</html>");

        System.setProperty("test.results.path", tempDir.toString());
        try {
            stubCycle("T-R5");

            service.uploadRunResults(runId, createRequest(List.of("@Backend")), 0, new TestStatus());

            verify(zephyrClient).uploadAttachment("T-R5", cucumberHtml);
            verify(zephyrClient).uploadAttachment("T-R5", axeIndex);
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void uploadRunResults_ReportFilesMissing_NoAttachmentUpload() {
        stubCycle("T-R6");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@Backend")), 0, new TestStatus());

        verify(zephyrClient, never()).uploadAttachment(any(), any());
    }

    // --- Fallback mode ---

    @Test
    void uploadRunResults_FallbackMode_UsesOverallStatus() {
        UUID runId = UUID.randomUUID();
        stubCycle("T-R2");

        System.setProperty("test.results.path", "non-existent-dir-" + UUID.randomUUID());
        try {
            service.uploadRunResults(runId, createRequest(List.of("@Backend")), 1, new TestStatus());

            verify(zephyrClient).uploadTestResults(eq("T-R2"), argThat(execs ->
                    execs.size() == 1 && "Fail".equals(execs.get(0).getStatus())));
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    // --- Jira ticket creation ---

    @Test
    void uploadRunResults_FailedRun_CreatesJiraTicket() {
        ReflectionTestUtils.setField(service, "jiraEnabled", true);
        stubCycle("T-R3");
        JiraIssueResponse jiraResponse = new JiraIssueResponse();
        jiraResponse.setKey("PROJ-99");
        when(jiraClient.createIssue(any())).thenReturn(jiraResponse);

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 1, new TestStatus());

        verify(jiraClient).createIssue(argThat(req ->
                req.getFields() != null
                        && req.getFields().getSummary() != null
                        && req.getFields().getSummary().contains("Test Automation Failure")
                        && "Bug".equals(req.getFields().getIssuetype().get("name"))));
    }

    @Test
    void uploadRunResults_SuccessfulRun_NoJiraTicket() {
        ReflectionTestUtils.setField(service, "jiraEnabled", true);
        stubCycle("T-R4");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 0, new TestStatus());

        verifyNoInteractions(jiraClient);
    }

    // --- Metadata storage ---

    @Test
    void uploadRunResults_StoresKeysInMetadata() {
        stubCycle("T-R5");

        TestStatus status = new TestStatus();
        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 0, status);

        assertNotNull(status.getMetadata());
        assertEquals("T-R5", status.getMetadata().get("zephyrCycleKey"));
    }

    @Test
    void uploadRunResults_FailedRun_StoresJiraKeyInMetadataAndDedicatedField() {
        ReflectionTestUtils.setField(service, "jiraEnabled", true);
        stubCycle("T-R6");
        JiraIssueResponse jiraResponse = new JiraIssueResponse();
        jiraResponse.setKey("PROJ-55");
        when(jiraClient.createIssue(any())).thenReturn(jiraResponse);

        TestStatus status = new TestStatus();
        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 1, status);

        assertNotNull(status.getMetadata());
        assertEquals("PROJ-55", status.getMetadata().get("jiraTicket"));
        assertEquals("PROJ-55", status.getJiraTicketKey(),
                "jiraTicketKey must be set as dedicated field for the status endpoint response");
    }

    // --- Mock-Modus ---

    @Test
    void mockMode_CompletedRun_SetsZephyrRunUrlInReportUrls() {
        ReflectionTestUtils.setField(service, "mockEnabled", true);
        ReflectionTestUtils.setField(service, "zephyrEnabled", false);

        UUID runId = UUID.randomUUID();
        TestStatus status = new TestStatus();
        service.uploadRunResults(runId, createRequest(List.of("@SmokeTest")), 0, status);

        verifyNoInteractions(zephyrClient, jiraClient);
        assertNotNull(status.getReportUrls(), "reportUrls muss gesetzt sein");
        assertTrue(status.getReportUrls().containsKey("zephyr-run"),
                "reportUrls muss 'zephyr-run' enthalten");
        assertTrue(status.getReportUrls().get("zephyr-run").contains("T-R-"),
                "Zephyr-URL muss simulierten Cycle-Key enthalten");
        assertNull(status.getJiraTicketKey(), "kein Jira-Ticket bei erfolgreichem Run");
    }

    @Test
    void mockMode_FailedRun_SetsJiraTicketKeyAndUrl() {
        ReflectionTestUtils.setField(service, "mockEnabled", true);
        ReflectionTestUtils.setField(service, "zephyrEnabled", false);

        UUID runId = UUID.randomUUID();
        TestStatus status = new TestStatus();
        service.uploadRunResults(runId, createRequest(List.of("@SmokeTest")), 1, status);

        verifyNoInteractions(zephyrClient, jiraClient);
        assertNotNull(status.getJiraTicketKey(), "jiraTicketKey muss gesetzt sein");
        assertTrue(status.getJiraTicketKey().startsWith("PROJ-"),
                "Ticket-Key muss mit Projekt-Key beginnen");
        assertTrue(status.getReportUrls().containsKey("jira-ticket"),
                "reportUrls muss 'jira-ticket' enthalten");
        assertTrue(status.getReportUrls().get("jira-ticket").contains("/browse/PROJ-"),
                "Jira-URL muss Browse-Pfad enthalten");
        assertEquals(status.getJiraTicketKey(),
                status.getMetadata().get("jiraTicket"),
                "jiraTicketKey und metadata.jiraTicket müssen übereinstimmen");
    }

    @Test
    void mockMode_FailedRun_TicketNumberDeterministicPerRunId() {
        ReflectionTestUtils.setField(service, "mockEnabled", true);
        ReflectionTestUtils.setField(service, "zephyrEnabled", false);

        UUID runId = UUID.randomUUID();
        TestStatus s1 = new TestStatus();
        TestStatus s2 = new TestStatus();
        service.uploadRunResults(runId, createRequest(List.of("@Backend")), 1, s1);
        service.uploadRunResults(runId, createRequest(List.of("@Backend")), 1, s2);

        assertEquals(s1.getJiraTicketKey(), s2.getJiraTicketKey(),
                "gleiche runId muss immer denselben Ticket-Key erzeugen");
    }
}
