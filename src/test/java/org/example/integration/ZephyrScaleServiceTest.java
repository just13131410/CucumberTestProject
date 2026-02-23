package org.example.integration;

import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestStatus;
import org.example.integration.jira.JiraClient;
import org.example.integration.model.*;
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
import java.util.Map;
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

    @BeforeEach
    void setUp() {
        service = new ZephyrScaleService(zephyrClient, jiraClient);
        ReflectionTestUtils.setField(service, "zephyrEnabled", true);
        ReflectionTestUtils.setField(service, "defaultProjectKey", "PROJ");
        ReflectionTestUtils.setField(service, "jiraEnabled", false);
        ReflectionTestUtils.setField(service, "jiraAssigneeAccountId", "automation-user");
        ReflectionTestUtils.setField(service, "jiraIssueType", "Bug");
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

    // --- Folder + Cycle creation ---

    @Test
    void uploadRunResults_CreatesFolderAndCycle() {
        when(zephyrClient.getFolders("PROJ", "TEST_RUN")).thenReturn(List.of());
        ZephyrFolder folder = new ZephyrFolder();
        folder.setId(5L);
        folder.setName("SmokeTest");
        when(zephyrClient.createFolder("SmokeTest", "PROJ", "TEST_RUN")).thenReturn(folder);
        stubCycle("T-R1");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@SmokeTest")), 0, new TestStatus());

        verify(zephyrClient).createFolder("SmokeTest", "PROJ", "TEST_RUN");
        verify(zephyrClient).createTestCycle(argThat(m -> "PROJ".equals(m.get("projectKey"))));
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
            when(zephyrClient.getFolders(any(), any())).thenReturn(List.of());
            ZephyrFolder folder = new ZephyrFolder();
            folder.setId(1L);
            when(zephyrClient.createFolder(any(), any(), any())).thenReturn(folder);
            stubCycle("T-R1");

            service.uploadRunResults(runId, createRequest(List.of("@Backend")), 0, new TestStatus());

            verify(zephyrClient).uploadTestResults(eq("T-R1"), argThat(execs ->
                    execs.size() == 1
                            && "T-3511".equals(execs.get(0).getTestCaseKey())
                            && "Pass".equals(execs.get(0).getStatusName())));
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    // --- Fallback mode ---

    @Test
    void uploadRunResults_FallbackMode_UsesOverallStatus() {
        when(zephyrClient.getFolders(any(), any())).thenReturn(List.of());
        when(zephyrClient.createFolder(any(), any(), any())).thenReturn(null);
        UUID runId = UUID.randomUUID();
        stubCycle("T-R2");

        System.setProperty("test.results.path", "non-existent-dir-" + UUID.randomUUID());
        try {
            service.uploadRunResults(runId, createRequest(List.of("@Backend")), 1, new TestStatus());

            verify(zephyrClient).uploadTestResults(eq("T-R2"), argThat(execs ->
                    execs.size() == 1 && "Fail".equals(execs.get(0).getStatusName())));
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    // --- Jira ticket creation ---

    @Test
    void uploadRunResults_FailedRun_CreatesJiraTicket() {
        ReflectionTestUtils.setField(service, "jiraEnabled", true);
        when(zephyrClient.getFolders(any(), any())).thenReturn(List.of());
        when(zephyrClient.createFolder(any(), any(), any())).thenReturn(null);
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
        when(zephyrClient.getFolders(any(), any())).thenReturn(List.of());
        when(zephyrClient.createFolder(any(), any(), any())).thenReturn(null);
        stubCycle("T-R4");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 0, new TestStatus());

        verifyNoInteractions(jiraClient);
    }

    // --- Metadata storage ---

    @Test
    void uploadRunResults_StoresKeysInMetadata() {
        when(zephyrClient.getFolders(any(), any())).thenReturn(List.of());
        when(zephyrClient.createFolder(any(), any(), any())).thenReturn(null);
        stubCycle("T-R5");

        TestStatus status = new TestStatus();
        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@smoke")), 0, status);

        assertNotNull(status.getMetadata());
        assertEquals("T-R5", status.getMetadata().get("zephyrCycleKey"));
    }

    @Test
    void uploadRunResults_FailedRun_StoresJiraKeyInMetadataAndDedicatedField() {
        ReflectionTestUtils.setField(service, "jiraEnabled", true);
        when(zephyrClient.getFolders(any(), any())).thenReturn(List.of());
        when(zephyrClient.createFolder(any(), any(), any())).thenReturn(null);
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

    // --- Folder mapping ---

    @Test
    void folderMapping_SmokeTestTag_ReturnsSmokeTestFolder() {
        when(zephyrClient.getFolders("PROJ", "TEST_RUN")).thenReturn(List.of());
        ZephyrFolder folder = new ZephyrFolder();
        folder.setId(1L);
        when(zephyrClient.createFolder(eq("SmokeTest"), eq("PROJ"), eq("TEST_RUN"))).thenReturn(folder);
        stubCycle("T-R7");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@SmokeTest")), 0, new TestStatus());

        verify(zephyrClient).createFolder("SmokeTest", "PROJ", "TEST_RUN");
    }

    @Test
    void folderMapping_FrontendTag_ReturnsFrontendFolder() {
        when(zephyrClient.getFolders("PROJ", "TEST_RUN")).thenReturn(List.of());
        ZephyrFolder folder = new ZephyrFolder();
        folder.setId(2L);
        when(zephyrClient.createFolder(eq("Frontend"), eq("PROJ"), eq("TEST_RUN"))).thenReturn(folder);
        stubCycle("T-R8");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@Frontend")), 0, new TestStatus());

        verify(zephyrClient).createFolder("Frontend", "PROJ", "TEST_RUN");
    }

    // --- Existing folder reuse ---

    @Test
    void uploadRunResults_ExistingFolder_SkipsCreation() {
        ZephyrFolder existing = new ZephyrFolder();
        existing.setId(99L);
        existing.setName("Backend");
        when(zephyrClient.getFolders("PROJ", "TEST_RUN")).thenReturn(List.of(existing));
        stubCycle("T-R9");

        service.uploadRunResults(UUID.randomUUID(), createRequest(List.of("@Backend")), 0, new TestStatus());

        verify(zephyrClient, never()).createFolder(any(), any(), any());
        verify(zephyrClient).createTestCycle(argThat(m -> Long.valueOf(99L).equals(m.get("folderId"))));
    }
}
