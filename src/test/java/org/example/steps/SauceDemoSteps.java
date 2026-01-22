package org.example.steps;

import com.microsoft.playwright.Page;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import org.example.hooks.AxeReportHook;
import org.example.pages.BasePage;
import org.example.pages.CheckoutPage;
import org.example.pages.ItemsPage;
import org.example.pages.LoginPage;
import org.example.pages.*;
import org.example.utils.ConfigReader;

import static org.example.hooks.TakeScreenshots.captureScreenshot;

public class SauceDemoSteps extends BasePage {
    String url = ConfigReader.get("baseUrl", "https://www.saucedemo.com/");

    //String apiToken = ConfigReader.get("ZEPHYR_TOKEN", "default-token");
    LoginPage loginPage;
    ItemsPage itemsPage;
    CheckoutPage checkoutPage;

    Page page;

    @Given("^User launched SwagLabs application$")
    public void user_launched_swaglabs_application() {
        Allure.step("GUI Base URl gesetzt auf: " + this.url);
        try {
            page = createPlaywrightPageInstance(System.getProperty("browser"));
            page.navigate(System.getProperty("applicationUrl"));
            loginPage = new LoginPage(page);
            itemsPage = new ItemsPage(page);
            checkoutPage = new CheckoutPage(page);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @When("User logged in the app using username {string} and password {string}")
    public void user_logged_in_the_app_using_username_and_password(String username, String password) {
        loginPage.login(username, password);
        captureScreenshot(page, "LoginAttempt");
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
        captureScreenshot(page, "OrderProduct");
    }

    @When("User enters Checkout details with {string}, {string}, {string}")
    public void user_enters_Checkout_details_with(String FirstName, String LastName, String Zipcode) {
        checkoutPage.fillCheckoutDetails(FirstName, LastName, Zipcode);
        captureScreenshot(page, "fillCheckoutDetails");
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
    public void tearDown() {
        if (browser != null) {
            browser.close();
        }
        if (page != null) {
            page.close();
        }
    }
    @Before
    public void setUp(Scenario scenario) {
        System.out.println("Starting Scenario: " + scenario.getName());
    }
}
