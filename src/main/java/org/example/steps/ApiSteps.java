// java
package org.example.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.qameta.allure.Allure;
import org.example.utils.ConfigReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ApiSteps {
    private String baseUrl;
    private Response response;

    @Given("API Basis-URL ist gesetzt")
    public void setBaseUrl() {
        this.baseUrl = ConfigReader.get("apiURL", "default-token");
        // ... browser navigation logic ...
        Allure.step("Base URl gesetzt auf: " + this.baseUrl);
    }

    @When("ich GET an {string} ausführe")
    public void performGet(String path) {
        ByteArrayOutputStream reqBaos = new ByteArrayOutputStream();
        ByteArrayOutputStream resBaos = new ByteArrayOutputStream();

        try (PrintStream reqPrint = new PrintStream(reqBaos, true, StandardCharsets.UTF_8);
             PrintStream resPrint = new PrintStream(resBaos, true, StandardCharsets.UTF_8)) {

            response = given()
                    .baseUri(baseUrl)
                    .filter(new RequestLoggingFilter(reqPrint))
                    .filter(new ResponseLoggingFilter(resPrint))
                    .when()
                    .get(path)
                    .then()
                    .extract()
                    .response();

            reqPrint.flush();
            resPrint.flush();

            if (response != null) {
                Allure.addAttachment("HTTP Request", "text/plain",
                        new ByteArrayInputStream(reqBaos.toByteArray()), ".txt");
                Allure.addAttachment("HTTP Response", "text/plain",
                        new ByteArrayInputStream(resBaos.toByteArray()), ".txt");

                byte[] bodyBytes = response.getBody() != null ? response.getBody().asByteArray() : null;
                if (bodyBytes != null && bodyBytes.length > 0) {
                    Allure.addAttachment("Response Body", "application/json",
                            new ByteArrayInputStream(bodyBytes), ".json");
                }
            }
        }
    }

    @Then("ist der Statuscode {int}")
    public void verifyStatusCode(int expected) {
        assertThat("Statuscode stimmt nicht", response.getStatusCode(), equalTo(expected));
    }

    @Then("enthält das JSON-Feld {string} mit Wert {int}")
    public void verifyJsonFieldEquals(String field, int expected) {
        assertThat("JSON-Feld stimmt nicht", response.jsonPath().getInt(field), equalTo(expected));
    }
}