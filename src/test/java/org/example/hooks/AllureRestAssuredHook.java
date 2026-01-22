// java
package org.example.hooks;

import io.cucumber.java.Before;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;

/**
 * Registriert AllureRestAssured als RestAssured-Filter vor jedem Scenario,
 * so dass Request- und Response-Inhalte als Allure-Anhänge erscheinen.
 */
public class AllureRestAssuredHook {

    @Before
    public void registerAllureFilter() {
        // ersetzt bestehende Filter durch einen neuen Allure-Filter
        RestAssured.filters(new AllureRestAssured());
    }
}