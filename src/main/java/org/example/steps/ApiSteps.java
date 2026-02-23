package org.example.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.response.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.utils.ConfigReader;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class ApiSteps {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String baseUrl;
    private Response response;
    private Map<String, Object> testData;

    @Given("API Basis-URL ist gesetzt")
    public void setBaseUrl() {
        this.baseUrl = ConfigReader.get("apiURL", "https://jsonplaceholder.typicode.com");
        Allure.step("Base URL gesetzt auf: " + this.baseUrl);
    }

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

    @When("ich GET an {string} ausführe")
    public void performGet(String path) {
        response = given()
                .filter(new AllureRestAssured())
                .baseUri(baseUrl)
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

    @Then("enthält das JSON-Feld {string} mit Wert {int}")
    public void verifyJsonFieldEquals(String field, int expected) {
        assertThat("JSON-Feld stimmt nicht", response.jsonPath().getInt(field), equalTo(expected));
    }

    @Then("die PDF-Datei {string} enthält alle Felder aus {string}")
    public void verifyPdfContainsFieldsFromJson(String pdfFile, String jsonFile) throws Exception {
        byte[] pdfBytes = loadTestDataFile(pdfFile);

        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            String pdfText = new PDFTextStripper().getText(doc);

            Allure.addAttachment("Extrahierter PDF-Text", "text/plain",
                    new ByteArrayInputStream(pdfText.getBytes()), ".txt");

            Map<String, String> felder = new LinkedHashMap<>();
            felder.put("userId", String.valueOf(testData.get("userId")));
            felder.put("id",     String.valueOf(testData.get("id")));
            felder.put("title",  String.valueOf(testData.get("title")));

            for (Map.Entry<String, String> entry : felder.entrySet()) {
                String feldName  = entry.getKey();
                String erwartung = entry.getValue();
                Allure.step(
                        String.format("PDF enthält Feld '%s' → erwartet aus %s: \"%s\"",
                                feldName, jsonFile, erwartung),
                        () -> assertThat(
                                String.format("PDF enthält '%s' mit Wert '%s'", feldName, erwartung),
                                pdfText, containsString(erwartung))
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
