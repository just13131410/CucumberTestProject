package org.example.pages;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public abstract class BasePage {


    protected Browser browser;
    protected Page page;

    public Page createPlaywrightPageInstance(String browserTypeAsString) {
        BrowserType browserType = switch (browserTypeAsString) {
            case "Firefox" -> Playwright.create().firefox();
            case "Chromium" -> Playwright.create().chromium();
            case "Webkit" -> Playwright.create().webkit();
            default -> null;
        };
        if (browserType == null) {
            throw new IllegalArgumentException("Could not launch a browser for type " + browserTypeAsString);
        }
        browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        return page;

    }

}