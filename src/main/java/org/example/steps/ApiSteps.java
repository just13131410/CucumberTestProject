// java
package org.example.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
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
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class ApiSteps {
    private String baseUrl;
    private Response response;

    @Given("API Basis-URL ist gesetzt")
    public void setBaseUrl() {
        this.baseUrl = ConfigReader.get("apiURL", "default-token");
        Allure.step("Base URl gesetzt auf: " + this.baseUrl);
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

    @Then("die Datei {string} enthält die Felder der API-Antwort")
    public void verifyPdfContainsApiResponseFields(String fileName) throws Exception {
        File pdfFile = new File(fileName);

        try (PDDocument doc = PDDocument.load(pdfFile)) {
            String pdfText = new PDFTextStripper().getText(doc);

            // Extrahierten PDF-Text als Attachment anfügen
            Allure.addAttachment("Extrahierter PDF-Text", "text/plain",
                    new ByteArrayInputStream(pdfText.getBytes()), ".txt");

            // Geprüfte Felder mit Werten aus der API-Antwort
            Map<String, String> felder = new LinkedHashMap<>();
            felder.put("userId", String.valueOf(response.jsonPath().getInt("userId")));
            felder.put("id",     String.valueOf(response.jsonPath().getInt("id")));
            felder.put("title",  response.jsonPath().getString("title"));

            // Jeden Feldvergleich als eigenen Allure-Unterschritt ausgeben
            for (Map.Entry<String, String> entry : felder.entrySet()) {
                String feldName  = entry.getKey();
                String erwartung = entry.getValue();
                Allure.step(
                        String.format("PDF-Text enthält Feld '%s' → erwartet: \"%s\"", feldName, erwartung),
                        () -> assertThat(
                                String.format("PDF enthält '%s' mit Wert '%s'", feldName, erwartung),
                                pdfText, containsString(erwartung))
                );
            }
        }
    }

    @Then("das eingebettete XML in {string} stimmt mit der API-Antwort überein")
    public void verifyEmbeddedXmlMatchesApiResponse(String fileName) throws Exception {
        File pdfFile = new File(fileName);

        try (PDDocument doc = PDDocument.load(pdfFile)) {
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
            PDEmbeddedFilesNameTreeNode efTree = names.getEmbeddedFiles();
            Map<String, PDComplexFileSpecification> embeddedFiles = efTree.getNames();

            Allure.step("Response.xml ist im PDF eingebettet",
                    () -> assertThat("Response.xml fehlt als Anhang im PDF",
                            embeddedFiles.containsKey("Response.xml")));

            PDEmbeddedFile embeddedFile = embeddedFiles.get("Response.xml").getEmbeddedFile();
            byte[] xmlBytes = embeddedFile.toByteArray();

            // Eingebettetes XML als Attachment anfügen
            Allure.addAttachment("Eingebettetes XML (aus PDF)", "text/xml",
                    new ByteArrayInputStream(xmlBytes), ".xml");

            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document xml = db.parse(new ByteArrayInputStream(xmlBytes));

            // Vergleichswerte: XML-Wert vs. API-Wert
            Map<String, String[]> vergleiche = new LinkedHashMap<>();
            vergleiche.put("userId", new String[]{
                    xmlValue(xml, "userId"),
                    String.valueOf(response.jsonPath().getInt("userId"))});
            vergleiche.put("id", new String[]{
                    xmlValue(xml, "id"),
                    String.valueOf(response.jsonPath().getInt("id"))});
            vergleiche.put("title", new String[]{
                    xmlValue(xml, "title"),
                    response.jsonPath().getString("title")});
            vergleiche.put("body", new String[]{
                    xmlValue(xml, "body").trim(),
                    response.jsonPath().getString("body").trim()});

            // Vergleichstabelle als Attachment (übersichtliche Gegenüberstellung)
            Allure.addAttachment("Feldvergleich XML ↔ API", "text/plain",
                    new ByteArrayInputStream(vergleichstabelle(vergleiche).getBytes()), ".txt");

            // Jeden Feldvergleich als eigenen Allure-Unterschritt ausgeben
            for (Map.Entry<String, String[]> entry : vergleiche.entrySet()) {
                String feldName  = entry.getKey();
                String xmlWert   = entry.getValue()[0];
                String apiWert   = entry.getValue()[1];
                Allure.step(
                        String.format("Feld '%s': XML=\"%s\" == API=\"%s\"",
                                feldName, kurz(xmlWert), kurz(apiWert)),
                        () -> assertThat(
                                String.format("Feld '%s' stimmt nicht überein", feldName),
                                xmlWert, equalTo(apiWert))
                );
            }
        }
    }

    /** Erstellt eine lesbare Vergleichstabelle für das Allure-Attachment. */
    private String vergleichstabelle(Map<String, String[]> vergleiche) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-10s | %-55s | %-55s | %s%n",
                "Feld", "XML-Wert", "API-Wert", "OK?"));
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