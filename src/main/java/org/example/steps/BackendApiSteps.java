package org.example.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.specification.RequestSpecification;
import org.example.utils.ConfigReader;
import org.example.utils.OAuthTokenProvider;
import org.example.utils.RestAssuredSecrets;

import static io.restassured.RestAssured.given;

/**
 * API-Steps gegen das Backend mit OAuth2-Token vom OAuth-Server ({@link OAuthTokenProvider}).
 * Backend-URL ({@code backendUrl}) und OAuth-Zugangsdaten sind je Ziel-Umgebung konfigurierbar;
 * Response-Assertions liegen in {@link RestAssertionSteps}.
 */
public class BackendApiSteps {

    private final RestAssertionSteps assertions;

    private String accessToken;

    public BackendApiSteps(RestAssertionSteps assertions) {
        this.assertions = assertions;
    }

    @Given("ich habe ein OAuth-Token für das Backend")
    public void fetchOAuthToken() {
        accessToken = OAuthTokenProvider.fetchAccessToken();
        Allure.step("OAuth-Token erhalten (Länge: " + accessToken.length() + " Zeichen)");
    }

    @When("ich GET an {string} im Backend ausführe")
    public void performGet(String path) {
        assertions.setResponse(authorizedRequest().when().get(path).then().extract().response());
    }

    @When("ich GET an {string} im Backend ohne Token ausführe")
    public void performGetWithoutToken(String path) {
        assertions.setResponse(backendRequest().when().get(path).then().extract().response());
    }

    @When("ich POST an {string} im Backend mit folgendem Body ausführe")
    public void performPost(String path, String body) {
        assertions.setResponse(authorizedRequest()
                .contentType("application/json")
                .body(body)
                .when().post(path).then().extract().response());
    }

    @When("ich DELETE an {string} im Backend ausführe")
    public void performDelete(String path) {
        assertions.setResponse(authorizedRequest().when().delete(path).then().extract().response());
    }

    private RequestSpecification authorizedRequest() {
        if (accessToken == null) {
            throw new IllegalStateException("Kein OAuth-Token: Schritt \"ich habe ein OAuth-Token für das Backend\" fehlt");
        }
        return backendRequest().header("Authorization", "Bearer " + accessToken);
    }

    private RequestSpecification backendRequest() {
        String backendUrl = ConfigReader.get("backendUrl", null);
        if (backendUrl == null) {
            throw new IllegalStateException("backendUrl ist nicht konfiguriert");
        }
        return given()
                .config(RestAssuredSecrets.config()) // Bearer-Token nicht im Klartext in Allure-Anhängen
                .filter(new AllureRestAssured())
                .baseUri(backendUrl);
    }
}
