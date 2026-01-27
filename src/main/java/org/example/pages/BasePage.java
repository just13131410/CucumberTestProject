package org.example.pages;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public abstract class BasePage {

    protected Browser browser;
    protected Page page;

    public Page createPlaywrightPageInstance(String browserTypeAsString) {
        String normalized = (browserTypeAsString == null ? "chromium" : browserTypeAsString.trim()).toLowerCase();

        BrowserType browserType = switch (normalized) {
            case "firefox" -> Playwright.create().firefox();
            case "chromium", "chrome" -> Playwright.create().chromium();
            case "webkit" -> Playwright.create().webkit();
            default -> throw new IllegalArgumentException(
                    "Unsupported browser type: '" + browserTypeAsString + "'. " +
                            "Use one of: Chromium, Firefox, Webkit"
            );
        };

        browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        return page;
    }

}