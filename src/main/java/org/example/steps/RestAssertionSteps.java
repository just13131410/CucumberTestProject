package org.example.steps;

import io.cucumber.java.en.Then;
import io.restassured.response.Response;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Generische REST-Response-Assertions, unabhängig davon, ob die Response von
 * {@link GitHubApiSteps} oder {@link TodoApiSteps} stammt. Cucumber-Spring verwaltet diese
 * Klasse wie jede andere Glue-Klasse scenario-scoped (eine Instanz pro Szenario); die beiden
 * anderen Step-Klassen injizieren sie per Konstruktor und schreiben die jeweils aktuelle
 * Response hinein, bevor eine dieser Assertions ausgeführt wird.
 */
public class RestAssertionSteps {

    private Response response;

    void setResponse(Response response) {
        this.response = response;
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
}
