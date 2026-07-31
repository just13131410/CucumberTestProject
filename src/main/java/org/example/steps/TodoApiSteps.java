package org.example.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.example.utils.ConfigReader;
import org.junit.jupiter.api.Assumptions;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * API-Steps gegen die Todo-API (JWT-Login, CRUD). Response-Assertions liegen in
 * {@link RestAssertionSteps}, da sie generisch sind und auch von {@link GitHubApiSteps}
 * verwendet werden.
 */
public class TodoApiSteps {

    private final RestAssertionSteps assertions;

    private String todoApiUrl;
    private String jwtToken;
    private Integer lastCreatedTodoId;

    public TodoApiSteps(RestAssertionSteps assertions) {
        this.assertions = assertions;
    }

    @Given("die Todo-API Basis-URL ist gesetzt")
    public void setTodoApiBaseUrl() {
        this.todoApiUrl = ConfigReader.get("todoApiUrl", "http://localhost:3000");
        Allure.step("Todo-API Basis-URL gesetzt auf: " + this.todoApiUrl);
    }

    @When("ich mich an der Todo-API mit Benutzername {string} und Passwort {string} anmelde")
    public void loginWithCredentials(String username, String password) {
        Response response = todoRequest()
                .contentType("application/json")
                .body(Map.of("username", username, "password", password))
                .when()
                .post("/api/auth/login")
                .then()
                .extract()
                .response();
        assertions.setResponse(response);
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
        assertions.setResponse(todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .get(path)
                .then()
                .extract()
                .response());
    }

    @When("ich die Todo-Liste ohne Token abrufe")
    public void getTodosWithoutToken() {
        assertions.setResponse(todoRequest()
                .when()
                .get("/api/todos")
                .then()
                .extract()
                .response());
    }

    @When("ich ein neues Todo mit Titel {string} anlege")
    public void createTodoStep(String title) {
        createTodoInternal(title);
    }

    @When("ich ein neues Todo ohne Titel anlege")
    public void createTodoWithoutTitle() {
        assertions.setResponse(todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType("application/json")
                .body(Map.of("title", ""))
                .when()
                .post("/api/todos")
                .then()
                .extract()
                .response());
    }

    @When("ich das Todo mit dem Titel {string} aktualisiere")
    public void updateTodoStep(String newTitle) {
        assertions.setResponse(todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType("application/json")
                .body(Map.of("title", newTitle))
                .when()
                .put("/api/todos/" + lastCreatedTodoId)
                .then()
                .extract()
                .response());
    }

    @When("ich das zuletzt angelegte Todo lösche")
    public void deleteLastTodo() {
        assertions.setResponse(todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .delete("/api/todos/" + lastCreatedTodoId)
                .then()
                .extract()
                .response());
    }

    private void createTodoInternal(String title) {
        Response response = todoRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType("application/json")
                .body(Map.of("title", title))
                .when()
                .post("/api/todos")
                .then()
                .extract()
                .response();
        assertions.setResponse(response);
        if (response.getStatusCode() == 201) {
            lastCreatedTodoId = response.jsonPath().getInt("id");
        }
    }

    private RequestSpecification todoRequest() {
        return given()
                .filter(new AllureRestAssured())
                .baseUri(todoApiUrl);
    }
}
