package org.example.steps;

import com.microsoft.playwright.*;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.de.Und;
import org.example.config.PlaywrightBrowserInstaller;
import org.example.hooks.AxeReportHook;
import org.example.pages.BasePage;
import org.example.utils.ConfigReader;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.example.hooks.TakeScreenshots.captureScreenshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DashboardSteps extends BasePage {

    private final String baseUrl = ConfigReader.get("baseUrl", "http://localhost:4200/");
    private final PlaywrightBrowserInstaller browserInstaller;
    private Scenario currentScenario;

    public DashboardSteps(PlaywrightBrowserInstaller browserInstaller) {
        this.browserInstaller = browserInstaller;
    }

    @Before
    public void setUp(Scenario scenario) {
        this.currentScenario = scenario;
        String browserName = System.getProperty("browser");
        browserInstaller.ensureInstalled(browserName);
        page = createPlaywrightPageInstance(browserName);
    }

    @After
    public void tearDown(Scenario scenario) {
        if (page != null && !page.isClosed()) {
            String label = scenario.isFailed()
                    ? "FAILED_" + scenario.getName()
                    : "END_" + scenario.getName();
            captureScreenshot(page, label, scenario);
            page.close();
        }
        if (browser != null) {
            browser.close();
        }
    }

    @Gegebensei("ich öffne die Login-Seite")
    public void ich_oeffne_die_login_seite() {
        page.navigate(baseUrl + "login");
        AxeReportHook.runAndSave(page, "Login-audit-" + System.currentTimeMillis());
    }

    @Wenn("ich mich mit Benutzername {string} und Passwort {string} einlogge")
    public void ich_mich_mit_benutzername_und_passwort_einlogge(String user, String pass) {
        page.fill("input[name=\"username\"]", user);
        page.fill("input[name=\"password\"]", pass);
        captureScreenshot(page, "LoginAttempt", currentScenario);
        page.click("button[type=\"submit\"]");
    }

    @Dann("sollte ich das Dashboard sehen")
    public void sollte_ich_das_dashboard_sehen() {
        assertThat(page).hasURL(Pattern.compile(".*dashboard"));
        captureScreenshot(page, "Dashboard", currentScenario);
        AxeReportHook.runAndSave(page, "Dashboard-audit-" + System.currentTimeMillis());
        assertThat(page.locator("mat-toolbar")).containsText("Vorgangs-Dashboard");
    }

    @Dann("die Tabelle sollte mindestens eine Zeile mit Daten enthalten")
    public void die_tabelle_sollte_mindestens_eine_zeile_mit_daten_enthalten() {
        page.waitForSelector("table tbody tr");
        assertThat(page.locator("table tbody")).not().containsText("Keine Daten für den Filter gefunden");
        int rowCount = page.locator("table tbody tr").count();
        assertTrue(rowCount > 0);
    }

    @Gegebensei("ich bin auf dem Dashboard eingeloggt")
    public void ich_bin_auf_dem_dashboard_eingeloggt() {
        page.navigate(baseUrl + "login");
        page.fill("input[name=\"username\"]", "admin");
        page.fill("input[name=\"password\"]", "admin");
        page.click("button[type=\"submit\"]");
        assertThat(page).hasURL(Pattern.compile(".*dashboard"));
        page.waitForSelector("table");
    }

    @Wenn("ich im Filter für den Typ {string} eingebe")
    public void ich_im_filter_fuer_den_typ_eingebe(String typ) {
        page.locator("mat-form-field").filter(new Locator.FilterOptions().setHasText("Typ")).locator("mat-select").click();
        page.locator("mat-option").filter(new Locator.FilterOptions().setHasText(typ)).click();
    }

    @Wenn("ich im Filter für den Status {string} eingebe")
    public void ich_im_filter_fuer_den_status_eingebe(String status) {
        page.locator("mat-form-field").filter(new Locator.FilterOptions().setHasText("Status")).locator("mat-select").click();
        page.locator("mat-option").filter(new Locator.FilterOptions().setHasText(status)).click();
    }

    @Wenn("ich im Filter für die UUID {string} eingebe")
    public void ich_im_filter_fuer_die_uuid_eingebe(String uuid) {
        page.locator("mat-form-field").filter(new Locator.FilterOptions().setHasText("UUID")).locator("input").fill(uuid);
    }

    private boolean isValidGuid(String value) {
        return value != null && value.matches(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        );
    }

    private int heutigesDatum() {
        return java.time.LocalDate.now().getDayOfMonth();
    }

    private String heutigesDatumFormatiert() {
        return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    @Wenn("ich im Filter für das Datum heute auswähle")
    public void ich_im_filter_fuer_das_datum_heute_auswaehle() {
        ich_im_filter_fuer_das_datum_den_auswaehle(String.valueOf(heutigesDatum()));
    }

    @Wenn("ich im Filter für das Datum den {string} auswähle")
    public void ich_im_filter_fuer_das_datum_den_auswaehle(String day) {
        page.locator("mat-datepicker-toggle").click();
        page.locator("button.mat-calendar-body-cell").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + day + "\\s*$"))).click();
    }

    @Wenn("ich im Filter für das Datum {string} auswähle")
    public void ich_im_filter_fuer_das_datum_auswaehle(String isoDate) {
        java.time.LocalDate target = java.time.LocalDate.parse(isoDate,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        page.locator("mat-datepicker-toggle").click();

        // Der Kalender öffnet auf dem aktuellen Monat. Deterministisch – und
        // locale-unabhängig – über die Vor-/Zurück-Pfeile zum Zielmonat navigieren,
        // statt uns auf lokalisierte Monatsnamen zu verlassen.
        java.time.YearMonth current = java.time.YearMonth.now();
        java.time.YearMonth targetMonth = java.time.YearMonth.from(target);
        long months = java.time.temporal.ChronoUnit.MONTHS.between(current, targetMonth);
        String navButton = months < 0
                ? "button.mat-calendar-previous-button"
                : "button.mat-calendar-next-button";
        for (long i = 0; i < Math.abs(months); i++) {
            page.locator(navButton).click();
        }

        page.locator("button.mat-calendar-body-cell")
                .filter(new Locator.FilterOptions().setHasText(
                        Pattern.compile("^\\s*" + target.getDayOfMonth() + "\\s*$")))
                .click();
    }

    @Wenn("auf den Button {string} klicke")
    public void auf_den_button_klicke(String buttonText) {
        page.locator("button").filter(new Locator.FilterOptions().setHasText(buttonText)).click();
        page.waitForTimeout(500);
    }

    @Dann("sollten in der Tabelle nur Zeilen mit dem Typ {string} angezeigt werden")
    public void sollten_in_der_tabelle_nur_zeilen_mit_dem_typ_angezeigt_werden(String expectedTyp) {
        Locator rows = page.locator("table tbody tr");
        int count = rows.count();
        assertTrue(count > 0, "Tabelle enthält keine Zeilen — kein Typ prüfbar");
        for (int i = 0; i < count; i++) {
            String text = rows.nth(i).locator("td.mat-column-typ").textContent();
            assertEquals(expectedTyp, text.trim(), "Zeile %d: erwarteter Typ '%s'".formatted(i, expectedTyp));
        }
        captureScreenshot(page, "FilterTyp", currentScenario);
    }

    @Dann("sollten in der Tabelle nur Zeilen mit dem Typ {string} und Status {string} angezeigt werden")
    public void sollten_in_der_tabelle_nur_zeilen_mit_dem_typ_und_status_angezeigt_werden(String expectedTyp, String expectedStatus) {
        Locator rows = page.locator("table tbody tr");
        int count = rows.count();
        assertTrue(count > 0, "Tabelle enthält keine Zeilen — kein Typ/Status prüfbar");
        for (int i = 0; i < count; i++) {
            String typ = rows.nth(i).locator("td.mat-column-typ").textContent();
            String status = rows.nth(i).locator("td.mat-column-status").textContent();
            assertEquals(expectedTyp, typ.trim(), "Zeile %d: erwarteter Typ '%s'".formatted(i, expectedTyp));
            assertEquals(expectedStatus, status.trim(), "Zeile %d: erwarteter Status '%s'".formatted(i, expectedStatus));
        }
        captureScreenshot(page, "FilterTypStatus", currentScenario);
    }

    @Dann("sollte die Tabelle genau {int} Zeilen anzeigen")
    public void sollte_die_tabelle_genau_zeilen_anzeigen(Integer count) {
        assertThat(page.locator("table tbody")).not().containsText("Keine Daten für den Filter gefunden");
        int rowCount = page.locator("table tbody tr").count();
        assertEquals(count.intValue(), rowCount, "Erwartete %d Zeilen, aber %d gefunden".formatted(count, rowCount));
    }

    @Dann("sollte die Tabelle genau {int} Zeile anzeigen")
    public void sollte_die_tabelle_genau_zeile_anzeigen(Integer count) {
        assertThat(page.locator("table tbody")).not().containsText("Keine Daten für den Filter gefunden");
        int rowCount = page.locator("table tbody tr").count();
        assertEquals(count.intValue(), rowCount, "Erwartete %d Zeile, aber %d gefunden".formatted(count, rowCount));
    }

    @Dann("sollte der Paginator {string} anzeigen")
    public void sollte_der_paginator_anzeigen(String rangeText) {
        Locator rangeLabel = page.locator(".mat-mdc-paginator-range-label");
        assertThat(rangeLabel).containsText(rangeText);
    }

    @Dann("der Paginator sollte {string} anzeigen")
    public void der_paginator_sollte_anzeigen(String rangeText) {
        Locator rangeLabel = page.locator(".mat-mdc-paginator-range-label");
        captureScreenshot(page, "Paginator", currentScenario);
        assertThat(rangeLabel).containsText(rangeText);
    }

    @Wenn("ich die Seitengröße auf {int} ändere")
    public void ich_die_seitengroesse_auf_aendere(Integer size) {
        page.locator(".mat-mdc-paginator-page-size-select mat-select").click(new Locator.ClickOptions().setForce(true));
        page.locator("mat-option").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + size + "\\s*$"))).click();
    }

    @Wenn("ich auf die nächste Seite blättere")
    public void ich_auf_die_naechste_seite_blaettere() {
        page.locator("button.mat-mdc-paginator-navigation-next").click();
    }

    @Und("ich blättere auf die nächste Seite")
    public void ich_blaettere_auf_die_naechste_seite() {
        page.locator("button.mat-mdc-paginator-navigation-next").click();
    }

    @Dann("sollte der Paginator wieder bei der ersten Seite beginnen")
    public void sollte_der_paginator_wieder_bei_der_ersten_seite_beginnen() {
        Locator rangeLabel = page.locator(".mat-mdc-paginator-range-label");
        assertThat(rangeLabel).containsText(Pattern.compile("1 –"));
    }

    @Dann("sollte die Tabelle Zeilen vom {string} anzeigen")
    public void sollte_die_tabelle_zeilen_vom_anzeigen(String datePrefix) {
        assertThat(page.locator("table tbody")).not().containsText("Keine Daten für den Filter gefunden");
        java.util.List<String> contents = page.locator("table tbody tr td.mat-column-erstellungszeitpunkt").allTextContents();
        assertTrue(contents.size() > 0, "Tabelle enthält keine Zeilen — kein Datum prüfbar");
        java.time.LocalDate minDatum = java.time.LocalDate.parse(datePrefix, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        for (String cell : contents) {
            String raw = cell.trim().substring(0, 10);
            java.time.LocalDate zellDatum = java.time.LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            assertTrue(!zellDatum.isBefore(minDatum),
                "Datum '%s' ist älter als Filtergrenze '%s'".formatted(raw, datePrefix));
        }
        captureScreenshot(page, "DatumFilter", currentScenario);
    }

    @Wenn("ich das Spalten-Konfigurations-Menü öffne")
    public void ich_das_spalten_konfigurations_menue_oeffne() {
        page.locator("button").filter(new Locator.FilterOptions().setHasText("Spalten konfigurieren")).click();
    }

    @Wenn("die Spalte {string} deaktiviere")
    public void die_spalte_deaktiviere(String colLabel) {
        Locator checkbox = page.locator("mat-checkbox").filter(new Locator.FilterOptions().setHasText(colLabel)).locator("input");
        checkbox.uncheck(new Locator.UncheckOptions().setForce(true));
        page.keyboard().press("Escape");
    }

    @Dann("sollte die Spalte {string} nicht mehr in der Tabelle sichtbar sein")
    public void sollte_die_spalte_nicht_mehr_in_der_tabelle_sichtbar_sein(String colLabel) {
        java.util.List<String> headers = page.locator("table thead th").allTextContents();
        assertTrue(!headers.contains(colLabel));
    }

    @Wenn("ich in der ersten Zeile auf den Detail-Button klicke")
    public void ich_in_der_ersten_zeile_auf_den_detail_button_klicke() {
        page.locator("table tbody tr").first().locator("button[color=\"primary\"]").click();
    }

    @Dann("sollte ein Dialog mit dem Titel {string} erscheinen")
    public void sollte_ein_dialog_mit_dem_titel_erscheinen(String title) {
        assertThat(page.locator("h2[mat-dialog-title]")).containsText(title);
    }

    @Dann("der Dialog sollte die UUID des Vorgangs anzeigen")
    public void der_dialog_sollte_die_uuid_des_vorgangs_anzeigen() {
        assertThat(page.locator("mat-dialog-content")).containsText("UUID:");
    }

    @Gegebensei("ich bin nicht eingeloggt")
    public void ich_bin_nicht_eingeloggt() {
        page.navigate(baseUrl + "login");
        page.evaluate("() => sessionStorage.clear()");
        page.evaluate("() => localStorage.clear()");
        captureScreenshot(page, "NotLoggedIn", currentScenario);
    }

    @Wenn("ich versuche die URL {string} direkt aufzurufen")
    public void ich_versuche_die_url_direkt_aufzurufen(String url) {
        page.navigate(baseUrl + url.replaceFirst("^/", ""));
    }

    @Dann("sollte ich zur Login-Seite weitergeleitet werden")
    public void sollte_ich_zur_login_seite_weitergeleitet_werden() {
        assertThat(page).hasURL(Pattern.compile(".*login"));
        captureScreenshot(page, "RedirectToLogin", currentScenario);
    }

    @Dann("sollte die Tabelle {int} Zeilen anzeigen")
    public void sollte_die_tabelle_zeilen_anzeigen(Integer count) {
        page.waitForTimeout(500);
        if (count == 0) {
            assertThat(page.locator("table tbody")).containsText("Keine Daten für den Filter gefunden");
        } else {
            assertThat(page.locator("table tbody")).not().containsText("Keine Daten für den Filter gefunden");
            int rowCount = page.locator("table tbody tr").count();
            assertEquals(count.intValue(), rowCount, "Erwartete %d Zeilen, aber %d gefunden".formatted(count, rowCount));
        }
    }

    @Wenn("die Tabelle {int} Zeilen anzeigt")
    public void die_tabelle_zeilen_anzeigt(Integer count) {
        page.waitForTimeout(500);
        if (count == 0) {
            assertThat(page.locator("table tbody")).containsText("Keine Daten für den Filter gefunden");
        } else {
            assertThat(page.locator("table tbody")).not().containsText("Keine Daten für den Filter gefunden");
            int rowCount = page.locator("table tbody tr").count();
            assertEquals(count.intValue(), rowCount, "Erwartete %d Zeilen, aber %d gefunden".formatted(count, rowCount));
        }
    }

    @Wenn("ich den Filter zurücksetze")
    public void ich_den_filter_zuruecksetze() {
        page.locator("button").filter(new Locator.FilterOptions().setHasText("Filter zurücksetzen")).click();
        page.waitForTimeout(500);
    }

    @Dann("eine Meldung {string} sollte sichtbar sein")
    public void eine_meldung_sollte_sichtbar_sein(String meldung) {
        assertThat(page.locator("body")).containsText(meldung);
        captureScreenshot(page, "KeineTreffer", currentScenario);
    }

    @Dann("sollte die Tabelle wieder mindestens eine Zeile mit Daten enthalten")
    public void sollte_die_tabelle_wieder_mindestens_eine_zeile_mit_daten_enthalten() {
        page.waitForSelector("table tbody tr");
        assertThat(page.locator("table tbody")).not().containsText("Keine Daten für den Filter gefunden");
        int rowCount = page.locator("table tbody tr").count();
        assertTrue(rowCount > 0);
        captureScreenshot(page, "FilterZurueckgesetzt", currentScenario);
    }
}
