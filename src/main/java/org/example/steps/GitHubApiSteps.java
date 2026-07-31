package org.example.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.specification.RequestSpecification;
import org.example.utils.ConfigReader;
import org.junit.jupiter.api.Assumptions;

import static io.restassured.RestAssured.given;

/**
 * API-Steps gegen die GitHub-API (api.github.com). Response-Assertions liegen in
 * {@link RestAssertionSteps}, da sie generisch sind und auch von {@link TodoApiSteps}
 * verwendet werden.
 */
public class GitHubApiSteps {

    private static final String INVALID_TOKEN = "invalid_token_for_auth_test";

    private final RestAssertionSteps assertions;

    private String baseUrl;
    private String authToken;

    public GitHubApiSteps(RestAssertionSteps assertions) {
        this.assertions = assertions;
    }

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
        assertions.setResponse(baseRequest()
                .when()
                .get(path)
                .then()
                .extract()
                .response());
    }

    @When("ich GET an {string} ausführe mit Bearer-Token")
    public void performGetWithBearerToken(String path) {
        assertions.setResponse(baseRequest()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get(path)
                .then()
                .extract()
                .response());
    }

    @When("ich GET an {string} ausführe mit ungültigem Token")
    public void performGetWithInvalidToken(String path) {
        assertions.setResponse(baseRequest()
                .header("Authorization", "Bearer " + INVALID_TOKEN)
                .when()
                .get(path)
                .then()
                .extract()
                .response());
    }

    private RequestSpecification baseRequest() {
        return given()
                .filter(new AllureRestAssured())
                .baseUri(baseUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }
}
