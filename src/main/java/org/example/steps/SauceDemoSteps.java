package org.example.steps;

import com.microsoft.playwright.Page;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import org.example.cucumber.context.TestContext;
import org.example.hooks.AxeReportHook;
import org.example.pages.BasePage;
import org.example.pages.CheckoutPage;
import org.example.pages.ItemsPage;
import org.example.pages.LoginPage;
import org.example.utils.ConfigReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.example.hooks.TakeScreenshots.captureScreenshot;

public class SauceDemoSteps extends BasePage {
    String url = ConfigReader.get("baseUrl", "https://www.saucedemo.com/");

    //String apiToken = ConfigReader.get("ZEPHYR_TOKEN", "default-token");
    LoginPage loginPage;
    ItemsPage itemsPage;
    CheckoutPage checkoutPage;

    // Note: 'page' and 'browser' are inherited from BasePage (protected fields)
    Scenario currentScenario;

    @Given("^User launched SwagLabs application$")
    public void user_launched_swaglabs_application() {
        Allure.step("GUI Base URl gesetzt auf: " + this.url);

        String browserName = System.getProperty("browser"); // may be null outside Surefire -> BasePage defaults to chromium
        String targetUrl = System.getProperty("applicationUrl");
        if (targetUrl == null || targetUrl.isBlank()) {
            targetUrl = this.url; // use config.properties baseUrl as the long-term stable default
        }

        page = createPlaywrightPageInstance(browserName);
        page.navigate(targetUrl);

        loginPage = new LoginPage(page);
        itemsPage = new ItemsPage(page);
        checkoutPage = new CheckoutPage(page);
    }

    @When("User logged in the app using username {string} and password {string}")
    public void user_logged_in_the_app_using_username_and_password(String username, String password) {
        if (loginPage == null) {
            throw new IllegalStateException("LoginPage was not initialized. Did 'User launched SwagLabs application' fail?");
        }
        loginPage.login(username, password);
        captureScreenshot(page, "LoginAttempt", currentScenario);
        // Axe Accessibility Scan
        AxeReportHook.runAndSave(page, "LoginSuccessfully-audit{}".replace("{}", System.currentTimeMillis() + ""));
    }

    @Then("^user should be able to log in$")
    public void logInSuccessful() {
        itemsPage.loginSuccessful();
    }

    @Then("^User should not get logged in$")
    public void logInFailed() {
        loginPage.loginFailed();
    }

    @When("User adds {string} product to the cart")
    public void user_adds_product_to_the_cart(String product) {
        itemsPage.orderProduct(product);
        captureScreenshot(page, "OrderProduct", currentScenario);
    }

    @When("User enters Checkout details with {string}, {string}, {string}")
    public void user_enters_Checkout_details_with(String FirstName, String LastName, String Zipcode) {
        checkoutPage.fillCheckoutDetails(FirstName, LastName, Zipcode);
        captureScreenshot(page, "fillCheckoutDetails", currentScenario);
    }

    @When("User completes Checkout process")
    public void user_completes_checkout_process() {
        checkoutPage.completeCheckout();
    }

    @Then("User should get the Confirmation of Order")
    public void user_should_get_the_Confirmation_of_Order() {
        checkoutPage.checkoutSuccessful();
    }

    @After
    public void tearDown(Scenario scenario) {
        // Screenshot immer aufnehmen (bei Fehler mit Prefix)
        if (page != null && !page.isClosed()) {
            String label = scenario.isFailed()
                    ? "FAILED_" + scenario.getName()
                    : "END_" + scenario.getName();
            captureScreenshot(page, label, scenario);
        }

        // Axe HTML-Report via scenario.attach() anhaengen (falls vorhanden)
        try {
            Path axeDir = TestContext.isInitialized()
                    ? TestContext.getAxeResultDir()
                    : Paths.get(ConfigReader.get("axe.reportPath", "target/axe-result/"));
            Path indexHtml = axeDir.resolve("index.html");
            if (Files.exists(indexHtml)) {
                scenario.attach(Files.readAllBytes(indexHtml), "text/html", "Accessibility Report");
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Anhaengen des Axe-Reports: " + e.getMessage());
        }

        if (browser != null) {
            browser.close();
        }
        if (page != null && !page.isClosed()) {
            page.close();
        }
    }

    @Before
    public void setUp(Scenario scenario) {
        this.currentScenario = scenario;
        System.out.println("Starting Scenario: " + scenario.getName());
    }

}
