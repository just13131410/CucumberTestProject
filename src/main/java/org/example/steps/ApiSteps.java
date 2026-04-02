package org.example.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.utils.ConfigReader;
import org.junit.jupiter.api.Assumptions;
import org.w3c.dom.Document;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ApiSteps {

    private static final String INVALID_TOKEN = "invalid_token_for_auth_test";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String baseUrl;
    private String authToken;
    private Response response;
    private Map<String, Object> testData;

    // --- Todo API Felder ---
    private String todoApiUrl;
    private String jwtToken;
    private Integer lastCreatedTodoId;

    @Given("API Basis-URL ist gesetzt")
    public void setBaseUrl() {
        this.baseUrl = ConfigReader.get("apiURL", "https://api.github.com");
        Allure.step("Base URL gesetzt auf: " + this.baseUrl);
    }

    @Given("ein gültiger API-Token ist konfiguriert")
    public void requireAuthToken() {
        this.authToken = ConfigReader.get("api.token", null);
        Assumptions.assumeTrue(
                authToken != null && !authToken.isBlank(),
                "Kein API-Token konfiguriert (api.token) – Szenario wird übersprungen."
        );
        Allure.step("API-Token geladen (Länge: " + authToken.length() + " Zeichen)");
    }

    @When("ich GET an {string} ausführe ohne Authentifizierung")
    public void performGetWithoutAuth(String path) {
        response = baseRequest()
                .when()
                .get(path)
                .then()
                .extract()
                .response();
    }

    @When("ich GET an {string} ausführe mit Bearer-Token")
    public void performGetWithBearerToken(String path) {
        response = baseRequest()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get(path)
                .then()
                .extract()
                .response();
    }

    @When("ich GET an {string} ausführe mit ungültigem Token")
    public void performGetWithInvalidToken(String path) {
        response = baseRequest()
                .header("Authorization", "Bearer " + INVALID_TOKEN)
                .when()
                .get(path)
                .then()
                .extract()
                .response();
    }

    @Then("ist der Statuscode {int}")
    public void verifyStatusCode(int expected) {
        assertThat("Statuscode stimmt nicht", response.getStatusCode(), equalTo(expected));
    }

    @Then("enthält das JSON-Feld {string} mit String-Wert {string}")
    public void verifyJsonFieldEqualsString(String field, String expected) {
        assertThat("JSON-Feld stimmt nicht", response.jsonPath().getString(field), equalTo(expected));
    }

    @Then("enthält das JSON-Feld {string} mit Wert {int}")
    public void verifyJsonFieldEquals(String field, int expected) {
        assertThat("JSON-Feld stimmt nicht", response.jsonPath().getInt(field), equalTo(expected));
    }

    @Then("das JSON-Feld {string} ist nicht leer")
    public void verifyJsonFieldNotEmpty(String field) {
        String value = response.jsonPath().getString(field);
        assertThat("JSON-Feld '" + field + "' ist null oder leer", value, not(emptyOrNullString()));
    }

    @Then("das JSON-Feld {string} ist vorhanden")
    public void verifyJsonFieldExists(String field) {
        Object value = response.jsonPath().get(field);
        assertThat("JSON-Feld '" + field + "' fehlt in der Response", value, notNullValue());
    }

    @Then("die Response ist eine JSON-Liste")
    public void verifyResponseIsList() {
        List<?> list = response.jsonPath().getList("$");
        assertThat("Response ist keine JSON-Liste", list, notNullValue());
    }

    // --- PDF-Szenarien (unverändert) ---

    @Given("die Testdatei {string} ist geladen")
    public void loadTestData(String fileName) throws Exception {
        String path = "TestData/" + fileName;
        Allure.step("Testdaten laden aus Classpath: " + path);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Testdatei nicht im Classpath gefunden: " + path);
            }
            testData = objectMapper.readValue(is, new TypeReference<>() {});
        }
        Allure.addAttachment("Geladene Testdaten (" + fileName + ")", "application/json",
                new ByteArrayInputStream(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(testData)), ".json");
    }

    @Then("die PDF-Datei {string} enthält alle Felder aus {string}")
    public void verifyPdfContainsFieldsFromJson(String pdfFile, String jsonFile) throws Exception {
        byte[] pdfBytes = loadTestDataFile(pdfFile);

        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            String rawPdfText = new PDFTextStripper().getText(doc);
            String pdfText = rawPdfText.replaceAll("\\s+", " ");

            Allure.addAttachment("Extrahierter PDF-Text", "text/plain",
                    new ByteArrayInputStream(rawPdfText.getBytes()), ".txt");

            Map<String, String> felder = new LinkedHashMap<>();
            felder.put("userId", String.valueOf(testData.get("userId")));
            felder.put("id",     String.valueOf(testData.get("id")));
            felder.put("title",  String.valueOf(testData.get("title")));
            felder.put("body",   String.valueOf(testData.get("body")));

            for (Map.Entry<String, String> entry : felder.entrySet()) {
                String feldName  = entry.getKey();
                String erwartung = entry.getValue().replaceAll("\\s+", " ");
                String gekuerzteErwartung = erwartung.length() > 50 ? erwartung.substring(0, 50) : erwartung;
                Allure.step(
                        String.format("PDF enthält Feld '%s' → erwartet: \"%s\"", feldName, erwartung),
                        () -> assertThat(
                                String.format("Feld '%s' wurde im PDF nicht mit dem Wert '%s' gefunden.", feldName, gekuerzteErwartung),
                                pdfText, containsString(gekuerzteErwartung))
                );
            }
        }
    }

    @Then("das eingebettete XML in {string} stimmt mit {string} überein")
    public void verifyEmbeddedXmlMatchesJson(String pdfFile, String jsonFile) throws Exception {
        byte[] pdfBytes = loadTestDataFile(pdfFile);

        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
            PDEmbeddedFilesNameTreeNode efTree = names.getEmbeddedFiles();
            Map<String, PDComplexFileSpecification> embeddedFiles = efTree.getNames();

            Allure.step("Response.xml ist im PDF eingebettet",
                    () -> assertThat("Response.xml fehlt als Anhang im PDF",
                            embeddedFiles.containsKey("Response.xml")));

            PDEmbeddedFile embeddedFile = embeddedFiles.get("Response.xml").getEmbeddedFile();
            byte[] xmlBytes = embeddedFile.toByteArray();

            Allure.addAttachment("Eingebettetes XML (aus PDF)", "text/xml",
                    new ByteArrayInputStream(xmlBytes), ".xml");

            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document xml = db.parse(new ByteArrayInputStream(xmlBytes));

            Map<String, String[]> vergleiche = new LinkedHashMap<>();
            vergleiche.put("userId", new String[]{
                    xmlValue(xml, "userId"), String.valueOf(testData.get("userId"))});
            vergleiche.put("id", new String[]{
                    xmlValue(xml, "id"), String.valueOf(testData.get("id"))});
            vergleiche.put("title", new String[]{
                    xmlValue(xml, "title"), String.valueOf(testData.get("title"))});
            vergleiche.put("body", new String[]{
                    xmlValue(xml, "body").trim(), String.valueOf(testData.get("body")).trim()});

            Allure.addAttachment("Feldvergleich XML ↔ " + jsonFile, "text/plain",
                    new ByteArrayInputStream(vergleichstabelle(vergleiche, jsonFile).getBytes()), ".txt");

            for (Map.Entry<String, String[]> entry : vergleiche.entrySet()) {
                String feldName = entry.getKey();
                String xmlWert  = entry.getValue()[0];
                String jsonWert = entry.getValue()[1];
                Allure.step(
                        String.format("Feld '%s': XML=\"%s\" == %s=\"%s\"",
                                feldName, kurz(xmlWert), jsonFile, kurz(jsonWert)),
                        () -> assertThat(
                                String.format("Feld '%s' stimmt nicht überein", feldName),
                                xmlWert, equalTo(jsonWert))
                );
            }
        }
    }

    @Given("pdfDatei erzeugt")
    public void createXML() {
        String userId = "1";
        String id = "1";
        String title = "sunt aut facere repellat provident occaecati excepturi optio reprehenderit";
        String body = "quia et suscipit\nsuscipit recusandae consequuntur expedita et cum...";

        String htmlContent = String.format(
                "<html>" +
                        "<head>" +
                        "  <style>" +
                        "    body { font-family: sans-serif; padding: 30px; }" +
                        "    table { width: 100%%; border-collapse: collapse; margin-top: 20px; }" +
                        "    th, td { border: 1px solid #ccc; padding: 12px; text-align: left; }" +
                        "    th { background-color: #f2f2f2; color: navy; width: 30%%; }" +
                        "    .title-row { background-color: #fafafa; font-weight: bold; }" +
                        "  </style>" +
                        "</head>" +
                        "<body>" +
                        "  <h2 style='color: navy;'>Datenblatt Response</h2>" +
                        "  <hr />" +
                        "  <table>" +
                        "    <tr><th>User ID</th><td>%s</td></tr>" +
                        "    <tr><th>ID</th><td>%s</td></tr>" +
                        "    <tr class='title-row'><th>Titel</th><td>%s</td></tr>" +
                        "    <tr><th>Inhalt</th><td>%s</td></tr>" +
                        "  </table>" +
                        "</body>" +
                        "</html>",
                userId, id, title, body.replace("\n", "<br />")
        );

        try (OutputStream os = new FileOutputStream("test-results/Response.pdf")) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, "/");
            builder.toStream(os);
            builder.run();
        } catch (Exception e) {
            throw new RuntimeException("PDF konnte nicht erstellt werden", e);
        }
    }

    // --- Todo API Schritte ---

    @Given("die Todo-API Basis-URL ist gesetzt")
    public void setTodoApiBaseUrl() {
        this.todoApiUrl = ConfigReader.get("todoApiUrl", "http://localhost:3000");
        Allure.step("Todo-API Basis-URL gesetzt auf: " + this.todoApiUrl);
    }

    @When("ich mich mit Benutzernaaaaaaame {string} und Passwort {string} einlogge")
    public void loginWithCredentials(String username, String password) {
        response = todoRequest()
                .contentType("application/json")
                .body(Map.of("username", username, "password", password))
                .when()
                .post("/api/auth/login")
                .then()
                .extract()
                .response();
        if (response.getStatusCode() == 200) {
            jwtToken = response.jsonPath().getString("token");
        }
    }

    @Given("ich bin als {string} mit Passwort {string} in der Todo-API eingeloggt")
    public void loginAndStoreToken(String username, String password) {
        loginWithCredentials(username, password);
        Assumptions.assumeTrue(jwtToken != null,
                "Login fehlgeschlagen – Szenario wird übersprungen.");
        Allure.step("JWT-Token erhalten (Länge: " + jwtToken.length() + " Zeichen)");
    }

    @Given("ich ein neues Todo mit Titel {string} angelegt habe")
    public void createTodoAsGiven(String title) {
        createTodoInternal(title);
        Assumptions.assumeTrue(lastCreatedTodoId != null,
                "Todo-Erstellung fehlgeschlagen – Szenario wird übersprungen.");
    }

    @When("ich GET an {string} ausführe mit JWT-Token")
    public void performGetWithJwt(String path) {
        response = todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .get(path)
                .then()
                .extract()
                .response();
    }

    @When("ich die Todo-Liste ohne Token abrufe")
    public void getTodosWithoutToken() {
        response = todoRequest()
                .when()
                .get("/api/todos")
                .then()
                .extract()
                .response();
    }

    @When("ich ein neues Todo mit Titel {string} anlege")
    public void createTodoStep(String title) {
        createTodoInternal(title);
    }

    @When("ich ein neues Todo ohne Titel anlege")
    public void createTodoWithoutTitle() {
        response = todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType("application/json")
                .body(Map.of("title", ""))
                .when()
                .post("/api/todos")
                .then()
                .extract()
                .response();
    }

    @When("ich das Todo mit dem Titel {string} aktualisiere")
    public void updateTodoStep(String newTitle) {
        response = todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType("application/json")
                .body(Map.of("title", newTitle))
                .when()
                .put("/api/todos/" + lastCreatedTodoId)
                .then()
                .extract()
                .response();
    }

    @When("ich das zuletzt angelegte Todo lösche")
    public void deleteLastTodo() {
        response = todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .delete("/api/todos/" + lastCreatedTodoId)
                .then()
                .extract()
                .response();
    }

    private void createTodoInternal(String title) {
        response = todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType("application/json")
                .body(Map.of("title", title))
                .when()
                .post("/api/todos")
                .then()
                .extract()
                .response();
        if (response.getStatusCode() == 201) {
            lastCreatedTodoId = response.jsonPath().getInt("id");
        }
    }

    // --- Hilfsmethoden ---

    private RequestSpecification todoRequest() {
        return given()
                .filter(new AllureRestAssured())
                .baseUri(todoApiUrl);
    }

    private RequestSpecification baseRequest() {
        return given()
                .filter(new AllureRestAssured())
                .baseUri(baseUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private byte[] loadTestDataFile(String fileName) throws Exception {
        String path = "TestData/" + fileName;
        Allure.step("Testdatei laden aus Classpath: " + path);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Testdatei nicht im Classpath gefunden: " + path);
            }
            return is.readAllBytes();
        }
    }

    private String vergleichstabelle(Map<String, String[]> vergleiche, String jsonFile) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-10s | %-55s | %-55s | %s%n",
                "Feld", "XML-Wert", jsonFile + "-Wert", "OK?"));
        sb.append("-".repeat(135)).append("\n");
        for (Map.Entry<String, String[]> e : vergleiche.entrySet()) {
            boolean gleich = e.getValue()[0].equals(e.getValue()[1]);
            sb.append(String.format("%-10s | %-55s | %-55s | %s%n",
                    e.getKey(),
                    kurz(e.getValue()[0], 55),
                    kurz(e.getValue()[1], 55),
                    gleich ? "✓" : "✗ FEHLER"));
        }
        return sb.toString();
    }

    private String xmlValue(Document xml, String tagName) {
        return xml.getElementsByTagName(tagName).item(0).getTextContent();
    }

    private String kurz(String wert) {
        return kurz(wert, 40);
    }

    private String kurz(String wert, int maxLen) {
        if (wert == null) return "null";
        String einzeilig = wert.replace("\n", "↵").replace("\r", "");
        return einzeilig.length() > maxLen ? einzeilig.substring(0, maxLen - 1) + "…" : einzeilig;
    }
}
