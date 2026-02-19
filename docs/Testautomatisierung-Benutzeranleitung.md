# Benutzeranleitung Testautomatisierung

**Zielgruppe:** Testautomatisierer
**Gültig für:** CucumberTestProject (Spring Boot 4, Java 21, Cucumber 7, Playwright, REST Assured)

---

## Inhaltsverzeichnis

1. [Projektstruktur im Überblick](#1-projektstruktur-im-überblick)
2. [Testfälle erstellen (Feature-Dateien)](#2-testfälle-erstellen-feature-dateien)
3. [Step Definitions programmieren](#3-step-definitions-programmieren)
4. [Hook-Methoden richtig einsetzen](#4-hook-methoden-richtig-einsetzen)
5. [Konfiguration und ConfigReader](#5-konfiguration-und-configreader)
6. [Allure-Reporting in Testcode nutzen](#6-allure-reporting-in-testcode-nutzen)
7. [Barrierefreiheits-Scans (Axe)](#7-barrierefreiheits-scans-axe)
8. [Häufige Fehler und Lösungen](#8-häufige-fehler-und-lösungen)

---

## 1. Projektstruktur im Überblick

```
src/main/
├── java/org/example/
│   ├── hooks/          ← Cucumber-Hooks (Lifecycle, Screenshots, Axe)
│   ├── steps/          ← Step Definitions (UI + API)
│   ├── pages/          ← Page-Object-Model (BasePage, LoginPage, ...)
│   └── utils/          ← Hilfsklassen (ConfigReader)
└── resources/
    ├── features/       ← Gherkin Feature-Dateien
    └── config.properties
```

**Glue-Pakete** (Cucumber scannt diese automatisch):
- `org.example.steps` – alle Step Definitions
- `org.example.hooks` – alle Hook-Klassen

---

## 2. Testfälle erstellen (Feature-Dateien)

### 2.1 Speicherort

Feature-Dateien gehören ausschließlich nach:
```
src/main/resources/features/
```

Nicht nach `src/test/resources/` – das Projekt ist als Spring Boot Service aufgebaut und liest Features aus dem Classpath.

### 2.2 Grundstruktur einer Feature-Datei

```gherkin
@T-XXXX @Frontend @SmokeTest
Feature: Name des Fachbereichs

  Scenario: Kurze Beschreibung des Testfalls
    Given <Vorbedingung>
    When  <Aktion>
    Then  <Erwartetes Ergebnis>
    And   <Weitere Bedingung oder Prüfung>
```

**Regeln:**
- Ein Feature pro Datei
- Jedes `Scenario` ist unabhängig und darf keinen Zustand von einem anderen Szenario voraussetzen
- `Given` beschreibt den Ausgangszustand, `When` die Aktion, `Then` die Prüfung
- `And` / `But` sind Aliase – sie haben keinen eigenen Step-Annotation-Typ

### 2.3 Scenario Outline mit Testdaten

Wenn derselbe Testablauf mit mehreren Datensätzen laufen soll, wird `Scenario Outline` mit einer `Examples`-Tabelle verwendet:

```gherkin
@T-3513 @Frontend
Scenario Outline: Login mit gültigen Zugangsdaten
  Given User launched SwagLabs application
  When User logged in the app using username "<UserName>" and password "<Password>"
  Then user should be able to log in

  Examples:
    | UserName      | Password     |
    | standard_user | secret_sauce |
    | problem_user  | secret_sauce |
```

Jede Zeile in der `Examples`-Tabelle erzeugt einen eigenständigen Testlauf. Die Platzhalterwerte in spitzen Klammern `<UserName>` werden automatisch ersetzt.

### 2.4 Tag-Konventionen

| Tag | Bedeutung |
|-----|-----------|
| `@T-XXXX` | Zephyr-Testfall-ID (Pflicht für Rückverfolgbarkeit) |
| `@SmokeTest` oder `@smoke` | Schnelle Basisprüfung, wird im Smoke-Profil ausgeführt |
| `@regression` | Vollständiger Regressionsdurchlauf |
| `@End2End` | Übergreifender End-to-End-Test |
| `@Frontend` | UI-Test mit Playwright |
| `@Backend` | API-Test mit REST Assured |
| `@API-Test` | Weiteres Label für API-Tests |
| `@ignore` | Testfall temporär deaktivieren |

**Tags können auf Feature-Ebene oder Scenario-Ebene gesetzt werden.** Tags auf Feature-Ebene gelten für alle Szenarien darin.

```gherkin
@T-3511 @Backend @SmokeTest
Feature: Erste API Prüfung
  Scenario: GET /posts/1 liefert 200 und id == 1
    ...
```

### 2.5 Teststufen mit Profilen ausführen

```bash
# Nur Smoke-Tests
mvn verify -Psmoke

# Nur Regression
mvn verify -Pregression

# Gezielt nach Tag filtern
mvn verify -Dcucumber.filter.tags="@Frontend and not @ignore"
```

---

## 3. Step Definitions programmieren

### 3.1 Glue-Pakete und Klassenerstellung

Jede Step-Definition-Klasse muss im Paket `org.example.steps` liegen. Cucumber scannt dieses Paket automatisch.

```java
package org.example.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

public class MeineSteps {
    // Step-Methoden hier
}
```

### 3.2 Step-Annotationen

Cucumber bietet Annotationen für alle Gherkin-Schlüsselwörter:

| Annotation | Gherkin-Schlüsselwort |
|------------|----------------------|
| `@Given`   | Given                |
| `@When`    | When                 |
| `@Then`    | Then                 |
| `@And`     | And / But (kein eigener Typ) |

`And` und `But` werden dem jeweils vorherigen Typ (`@Given`, `@When` oder `@Then`) zugeordnet.

### 3.3 Cucumber Expressions (empfohlen)

Cucumber Expressions sind die bevorzugte Schreibweise – einfacher zu lesen als reguläre Ausdrücke:

```java
// Parameter in geschweiften Klammern:
@When("User logged in the app using username {string} and password {string}")
public void userLogsIn(String username, String password) { ... }

@When("User adds {string} product to the cart")
public void addProductToCart(String product) { ... }

@Then("ist der Statuscode {int}")
public void verifyStatusCode(int expected) { ... }
```

**Typen in Cucumber Expressions:**

| Ausdruck | Java-Typ | Beispiel im Gherkin |
|----------|----------|---------------------|
| `{string}` | `String` | `"standard_user"` (mit Anführungszeichen) |
| `{int}` | `int` | `200` |
| `{long}` | `long` | `1234567890` |
| `{double}` | `double` | `3.14` |
| `{word}` | `String` | `admin` (ohne Anführungszeichen, kein Leerzeichen) |

### 3.4 Reguläre Ausdrücke (Alternative)

Für komplexere Muster kann weiterhin Regex verwendet werden – dann den Ausdruck in `^...$` einschließen:

```java
@Given("^User launched SwagLabs application$")
public void launchApplication() { ... }

@Then("^user should be able to log in$")
public void loginSuccessful() { ... }
```

**Wichtig:** Nicht Cucumber Expressions und Regex-Syntax mischen. Entweder nur geschweifte Klammern (Cucumber Expressions) oder `^...$` mit Capture Groups (Regex).

### 3.5 UI-Tests: BasePage erweitern

Alle Step-Klassen, die Playwright-UI-Tests ausführen, erben von `BasePage`:

```java
package org.example.steps;

import com.microsoft.playwright.Page;
import org.example.pages.BasePage;
import org.example.pages.LoginPage;
import org.example.pages.ItemsPage;

public class MeineUISteps extends BasePage {

    LoginPage loginPage;
    ItemsPage itemsPage;

    @Given("Die Anwendung ist geöffnet")
    public void launchApp() {
        String browserName = System.getProperty("browser"); // null → Chromium
        page = createPlaywrightPageInstance(browserName);   // aus BasePage
        page.navigate("https://www.saucedemo.com/");

        loginPage = new LoginPage(page);
        itemsPage = new ItemsPage(page);
    }
}
```

`BasePage` stellt die geschützten Felder `page` (Playwright `Page`) und `browser` (Playwright `Browser`) bereit und öffnet den Browser immer im Headless-Modus.

**Unterstützte Browser:**

| Systemparameter | Browser |
|----------------|---------|
| `chromium` oder `chrome` (Standard) | Chromium |
| `firefox` | Firefox |
| `webkit` | WebKit/Safari |

```bash
mvn verify -Dbrowser=firefox
```

### 3.6 API-Tests: REST Assured Muster

API-Tests brauchen **keine** `BasePage`-Vererbung. Standard-Muster:

```java
package org.example.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.response.Response;
import org.example.utils.ConfigReader;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ApiSteps {

    private String baseUrl;
    private Response response;

    @Given("API Basis-URL ist gesetzt")
    public void setBaseUrl() {
        this.baseUrl = ConfigReader.get("apiURL", "https://jsonplaceholder.typicode.com");
        Allure.step("Base URL gesetzt auf: " + this.baseUrl);
    }

    @When("ich GET an {string} ausführe")
    public void performGet(String path) {
        response = given()
                .filter(new AllureRestAssured())   // ← HTTP-Traffic in Allure aufzeichnen
                .baseUri(baseUrl)
                .when()
                .get(path)
                .then()
                .extract()
                .response();
    }

    @Then("ist der Statuscode {int}")
    public void verifyStatusCode(int expected) {
        assertThat("Statuscode stimmt nicht", response.getStatusCode(), equalTo(expected));
    }

    @Then("enthält das JSON-Feld {string} mit Wert {int}")
    public void verifyJsonField(String field, int expected) {
        assertThat("JSON-Feld stimmt nicht",
                response.jsonPath().getInt(field), equalTo(expected));
    }
}
```

**`AllureRestAssured`-Filter** immer als `.filter(new AllureRestAssured())` hinzufügen – dann werden Request und Response automatisch im Allure-Report sichtbar.

---

## 4. Hook-Methoden richtig einsetzen

### 4.1 Überblick der vorhandenen Hook-Klassen

| Klasse | Zweck |
|--------|-------|
| `CucumberHooks` | Globales Logging, Allure-Metadaten, ThreadLocal-Cleanup |
| `TakeScreenshots` | Statische Utility-Methode zum Screenshot erstellen |
| `AxeReportHook` | Barrierefreiheits-Scan und HTML-Report-Generierung |
| `SauceDemoSteps` | Lokale `@Before`/`@After` für Browser-Lifecycle |

### 4.2 Globale Hooks: CucumberHooks

`CucumberHooks` trägt die Annotationen `@CucumberContextConfiguration` und `@SpringBootTest` – damit wird der Spring-Kontext für alle Cucumber-Tests aktiviert. **Diese Klasse darf nur einmal im Projekt existieren.**

```java
@CucumberContextConfiguration
@SpringBootTest
public class CucumberHooks {

    private final ThreadLocal<String> scenarioName = new ThreadLocal<>();
    private final ThreadLocal<LocalDateTime> scenarioStartTime = new ThreadLocal<>();

    @Before(order = 0)          // läuft als ERSTE @Before-Methode
    public void beforeScenario(Scenario scenario) {
        scenarioName.set(scenario.getName());
        scenarioStartTime.set(LocalDateTime.now());
        // Logging und Allure-Metadaten setzen ...
    }

    @After(order = 100)         // läuft als LETZTE @After-Methode
    public void afterScenario(Scenario scenario) {
        // Dauer berechnen, Logging ...
        scenarioName.remove();          // ThreadLocal aufräumen!
        scenarioStartTime.remove();
    }
}
```

**`order`-Parameter:**

- `@Before(order = X)`: **Niedrigerer Wert läuft zuerst.** `order = 0` läuft vor `order = 10`.
- `@After(order = X)`: **Höherer Wert läuft zuerst.** `order = 100` läuft vor `order = 10`.

**Merkhilfe:** Bei `@Before` gilt "erster Wert = kleinste Zahl". Bei `@After` ist es umgekehrt – der höchste Wert räumt zuletzt auf, damit spätere Hooks auf Ressourcen zugreifen können.

```
Ausführungsreihenfolge bei order-Werten 0, 10, 100:

  @Before(order=0)    →  @Before(order=10)    →  Szenario  →  @After(order=100) →  @After(order=10)  →  @After(order=0)
  (zuerst)                                                      (zuerst aufräumen)                         (zuletzt)
```

### 4.3 Wann eigene Hooks in der Step-Klasse anlegen?

Hooks in der Step-Klasse (statt in `CucumberHooks`) sind sinnvoll, wenn der Hook **spezifisch für einen Testtyp** ist (z. B. nur UI-Tests):

```java
public class SauceDemoSteps extends BasePage {

    Scenario currentScenario;

    @Before                         // kein order → Standardwert (1000)
    public void setUp(Scenario scenario) {
        this.currentScenario = scenario;    // Szenario-Referenz speichern
    }

    @After                          // kein order → Standardwert (1000)
    public void tearDown(Scenario scenario) {
        // 1. End-Screenshot aufnehmen
        if (page != null && !page.isClosed()) {
            String label = scenario.isFailed()
                    ? "FAILED_" + scenario.getName()
                    : "END_" + scenario.getName();
            captureScreenshot(page, label, scenario);
        }
        // 2. Browser schließen
        if (browser != null) browser.close();
        if (page != null && !page.isClosed()) page.close();
    }
}
```

**Regeln für Step-Klassen-Hooks:**
- Immer `browser.close()` vor `page.close()` aufrufen (Browser schließt alle Pages mit)
- Nur die für diesen Test spezifischen Ressourcen freigeben
- Keine Spring-Beans `@Autowire`'n wenn kein Spring-Kontext benötigt wird

### 4.4 ThreadLocal richtig verwenden

`ThreadLocal` wird für threadlokale Zustandsspeicherung verwendet (wichtig bei Parallelausführung):

```java
// Anlegen (als Instanzvariable, nicht statisch):
private final ThreadLocal<String> scenarioName = new ThreadLocal<>();

// Schreiben (in @Before):
scenarioName.set(scenario.getName());

// Lesen (in @After oder Steps):
String name = scenarioName.get();

// Aufräumen (WICHTIG – in @After):
scenarioName.remove();
```

**Wichtig:** `remove()` in `@After` niemals vergessen. Ein nicht aufgeräumter `ThreadLocal`-Wert bleibt im Thread-Pool erhalten und kann im nächsten Szenario zu falschen Daten führen.

### 4.5 Screenshots: TakeScreenshots

`TakeScreenshots.captureScreenshot()` ist eine statische Hilfsmethode – kein Hook, sondern ein expliziter Aufruf aus Step-Methoden oder `@After`:

```java
import static org.example.hooks.TakeScreenshots.captureScreenshot;

// In einem Step (z. B. nach dem Login):
captureScreenshot(page, "LoginAttempt", currentScenario);

// In @After bei Fehler:
if (scenario.isFailed()) {
    captureScreenshot(page, "FAILED_" + scenario.getName(), scenario);
}
```

Die Methode:
1. Erstellt einen Vollseiten-Screenshot als PNG
2. Speichert ihn im richtigen Verzeichnis (`target/screenshots/` bei Maven, `test-results/{runId}/screenshots/` bei API-Aufruf)
3. Hängt ihn über `scenario.attach()` an den Allure-Report an

**`scenario.attach()` ist zuverlässiger als `Allure.addAttachment()`**, weil es über den Cucumber-Eventbus geht, den der Allure-Cucumber-Adapter direkt verarbeitet.

---

## 5. Konfiguration und ConfigReader

### 5.1 Prioritätsreihenfolge

`ConfigReader.get(key, defaultValue)` löst Werte in dieser Reihenfolge auf:

```
1. Umgebungsvariable  (KEY_NAME in Großbuchstaben, Punkte → Unterstriche)
2. System Property    (-Dkey=value beim Maven-Aufruf)
3. config.properties  (src/main/resources/config.properties)
4. defaultValue       (im Code angegeben)
```

### 5.2 Verwendung in Steps

```java
// URL aus Config lesen (Fallback auf Hardcode-Default):
String url = ConfigReader.get("baseUrl", "https://www.saucedemo.com/");

// API-URL:
String apiUrl = ConfigReader.get("apiURL", "https://jsonplaceholder.typicode.com");

// Axe-Reportpfad:
String axePath = ConfigReader.get("axe.reportPath", "target/axe-result/");
```

### 5.3 Werte überschreiben

```bash
# Über System Property:
mvn verify -DbaseUrl=https://staging.example.com

# Über Umgebungsvariable (passiert automatisch auf OpenShift):
export BASEURL=https://prod.example.com
```

Der Schlüssel `baseUrl` wird als Umgebungsvariable zu `BASEURL` (Großbuchstaben, Punkte werden zu Unterstrichen).

---

## 6. Allure-Reporting in Testcode nutzen

### 6.1 Allure Steps

Einzelne Schritte im Allure-Report sichtbar machen:

```java
import io.qameta.allure.Allure;

// Einfacher Beschreibungsschritt:
Allure.step("Navigiere zu: " + url);

// Schritt mit Aktion (Lambda):
Allure.step("Prüfe Login-Ergebnis", () -> {
    assertThat(page.url(), containsString("/inventory.html"));
});
```

### 6.2 `@Step`-Annotation für Page Objects

In Page-Object-Methoden kann `@Step` aus dem Allure-Java-API verwendet werden:

```java
import io.qameta.allure.Step;

public class LoginPage {

    @Step("Einloggen mit Benutzer: {username}")
    public void login(String username, String password) {
        page.fill("#user-name", username);
        page.fill("#password", password);
        page.click("#login-button");
    }
}
```

### 6.3 Dateien anhängen

```java
// Binäre Daten (z. B. Screenshot):
scenario.attach(byteArray, "image/png", "Mein Screenshot");

// Text (z. B. API-Response):
Allure.addAttachment("Response Body", "text/plain", responseBody);

// JSON:
Allure.addAttachment("API Response", "application/json",
        new ByteArrayInputStream(json.getBytes()), ".json");
```

### 6.4 Report generieren und öffnen

```bash
# Lokaler Report-Server (öffnet Browser):
mvn allure:serve

# Report nur generieren (Ausgabe: target/site/allure-maven-plugin/):
mvn allure:report
```

---

## 7. Barrierefreiheits-Scans (Axe)

Der Axe-Scan überprüft eine Seite auf WCAG-2.1-Verstöße. Der Aufruf erfolgt direkt aus dem Step heraus – **kein Hook erforderlich**.

### 7.1 Scan auslösen

```java
import org.example.hooks.AxeReportHook;

// Scan der aktuell geöffneten Seite:
AxeReportHook.runAndSave(page, "LoginSeite-audit");
```

Der Name (zweiter Parameter) wird für die Dateinamen der Reports verwendet. Ein Zeitstempel wird automatisch angehängt.

### 7.2 Deduplizierung

Innerhalb eines Testlaufs wird jede URL **nur einmal** gescannt. Wenn mehrere Szenarien dieselbe Seite aufrufen, überspringt `runAndSave()` den Scan stillschweigend. Das verhindert doppelte Reports und spart Zeit.

### 7.3 Empfohlene Scan-Punkte

| Wann scannen? | Beispiel |
|---------------|----------|
| Nach dem Laden einer neuen Seite | Nach Navigation zur Login-Seite |
| Nach wichtigen Zustandsänderungen | Nach erfolgreichem Login |
| Nicht nach jedem einzelnen Klick | Zu viele Duplikate |

### 7.4 Reports lesen

Axe-Reports werden gespeichert unter:
- Maven-Läufe: `target/axe-result/index.html`
- API-Läufe: `test-results/{runId}/axe-result/index.html`

Die `index.html` zeigt eine Übersicht aller gescannten Seiten mit Gesamtstatistik. Einzelberichte sind verlinkt.

**Schweregrade:**

| Schweregrad | Bedeutung |
|-------------|-----------|
| `critical` | Muss sofort behoben werden |
| `serious` | Sollte zeitnah behoben werden |
| `moderate` | Mittlere Priorität |
| `minor` | Niedrige Priorität |

---

## 8. Häufige Fehler und Lösungen

### `@CucumberContextConfiguration` doppelt vorhanden

**Symptom:** `java.lang.IllegalStateException: Found more than one class...`
**Ursache:** Zwei Klassen tragen `@CucumberContextConfiguration`
**Lösung:** Nur `CucumberHooks` darf diese Annotation tragen. Aus anderen Klassen entfernen.

### Step nicht gefunden (Undefined Step)

**Symptom:** `Undefined step: "User launches the app"`
**Ursache:** Der Step-Text in der Feature-Datei stimmt nicht exakt mit dem Ausdruck in der `@Given`/`@When`/`@Then`-Annotation überein
**Lösung:**
- Auf Tipp- und Großschreibfehler prüfen
- Bei Cucumber Expressions: Parameter in `{string}` – der Wert muss in der Feature-Datei in Anführungszeichen stehen
- Bei Regex: `^` und `$` nicht vergessen

### Browser wird nicht geschlossen (Ressourcenleck)

**Symptom:** Playwright-Browser-Prozesse bleiben nach dem Test laufen
**Ursache:** `browser.close()` fehlt im `@After`-Hook
**Lösung:** Im `tearDown`-Hook immer aufräumen:

```java
@After
public void tearDown(Scenario scenario) {
    if (browser != null) browser.close();   // schließt auch alle Pages
}
```

### Screenshot wird im Allure-Report nicht angezeigt

**Ursache:** `Allure.addAttachment()` statt `scenario.attach()` verwendet
**Lösung:** Immer `scenario.attach(bytes, "image/png", "Name")` verwenden – nicht `Allure.addAttachment()` für Screenshots.

### ThreadLocal-Werte aus vorherigem Szenario vorhanden

**Symptom:** Bei Parallelausführung enthält `ThreadLocal.get()` Daten vom vorherigen Szenario
**Ursache:** `ThreadLocal.remove()` fehlt im `@After`-Hook
**Lösung:** Jeden `ThreadLocal.set()`-Aufruf in `@Before` mit einem `ThreadLocal.remove()`-Aufruf in `@After` paaren.

### ConfigReader gibt null zurück

**Symptom:** `NullPointerException` bei der Verwendung von Konfigurations-Keys
**Ursache:** Weder Umgebungsvariable noch System Property noch `config.properties`-Eintrag gefunden, und kein Standardwert angegeben
**Lösung:** Immer einen sinnvollen Standardwert als zweiten Parameter übergeben:

```java
// Falsch:
String url = ConfigReader.get("baseUrl", null);

// Richtig:
String url = ConfigReader.get("baseUrl", "https://www.saucedemo.com/");
```
