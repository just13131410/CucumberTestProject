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
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.FileOutputStream;
import java.io.OutputStream;

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
            String rawPdfText = new PDFTextStripper().getText(doc);
            // Normalisierung: Entfernt harte Zeilenumbrüche und doppelte Leerzeichen für stabilere Vergleiche
            String pdfText = rawPdfText.replaceAll("\\s+", " ");

            Allure.addAttachment("Extrahierter PDF-Text", "text/plain",
                    new ByteArrayInputStream(rawPdfText.getBytes()), ".txt");

            // Alle Felder aus der XML/Tabelle mappen
            Map<String, String> felder = new LinkedHashMap<>();
            felder.put("userId", String.valueOf(testData.get("userId")));
            felder.put("id",     String.valueOf(testData.get("id")));
            felder.put("title",  String.valueOf(testData.get("title")));
            felder.put("body",   String.valueOf(testData.get("body"))); // Wichtig: Body hinzufügen!

            for (Map.Entry<String, String> entry : felder.entrySet()) {
                String feldName  = entry.getKey();
                // Auch den Erwartungswert normalisieren (falls im XML Umbrüche sind)
                String erwartung = entry.getValue().replaceAll("\\s+", " ");

                String gekuerzteErwartung = erwartung.length() > 50 ? erwartung.substring(0, 50) : erwartung;
                System.out.println("DEBUG PDF TEXT: " + pdfText);
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
    @Given("pdfDatei erzeugt")
    public void createXML(){
        String userId = "1";
        String id = "1";
        String title = "sunt aut facere repellat provident occaecati excepturi optio reprehenderit";
        String body = "quia et suscipit\nsuscipit recusandae consequuntur expedita et cum...";

// Das XHTML-Template mit einer Tabelle
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
                userId, id, title, body.replace("\n", "<br />") // Zeilenumbrüche für HTML umwandeln
        );

        try (OutputStream os = new FileOutputStream("test-results/Response.pdf")) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, "/");
            builder.toStream(os);
            builder.run();
            System.out.println("PDF wurde erstellt!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
