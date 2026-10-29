# Cucumber Test Service - API Benutzeranleitung

## Inhaltsverzeichnis

1. [Überblick](#überblick)
2. [Basis-URL](#basis-url)
3. [Swagger UI](#swagger-ui)
4. [Endpoints](#endpoints)
   - [Health Check](#1-health-check)
   - [Test starten](#2-test-ausführung-starten)
   - [Status abfragen](#3-status-einer-test-ausführung-abfragen)
   - [Aktive Tests anzeigen](#4-alle-aktiven-tests-anzeigen)
   - [Report abrufen](#5-test-report-abrufen)
   - [Allure-Report generieren](#6-allure-report-generieren)
   - [Report-URL abrufen](#7-allure-report-url-abrufen)
   - [Test abbrechen](#8-test-ausführung-abbrechen)
   - [Test löschen](#9-test-ausführung-löschen)
   - [Statistiken](#10-statistiken-abrufen)
   - [Verfügbare Runs auflisten](#11-verfügbare-runs-auflisten)
   - [Kombinierten Report generieren](#12-kombinierten-allure-report-generieren)
5. [Typischer Workflow](#typischer-workflow)
6. [Parallele Ausführung](#parallele-ausführung-mehrere-teams)
7. [Verfügbare Tags](#verfügbare-test-tags)
8. [Fehlerbehebung](#fehlerbehebung)

---

## Überblick

Der Cucumber Test Service stellt eine REST API bereit, über die Teams automatisierte Backend- und Frontend-Tests auslösen können. Der Dienst läuft auf OpenShift und unterstützt die gleichzeitige Ausführung mehrerer Test-Läufe mit isolierten Ergebnissen pro Lauf.

**Basis-Pfad:** `/api/v1/test`
**Content-Type:** `application/json`
**Max. gleichzeitige Läufe:** 5 ohne Browser + 2 mit Browser (Subprozess-Modus, siehe [Parallele Ausführung](#parallele-ausführung-mehrere-teams))

---

## Basis-URL

| Umgebung   | URL                                                   |
|------------|-------------------------------------------------------|
| Lokal      | `http://localhost:8080`                               |
| OpenShift  | `https://cucumber-test-service-test-automation.<cluster-domain>` |

---

## Swagger UI

Die interaktive API-Dokumentation ist unter folgendem Pfad erreichbar:

```
<BASIS-URL>/swagger-ui.html
```

Dort können alle Endpoints direkt im Browser ausprobiert werden.

---

## Endpoints

### 1. Health Check

Prüft, ob der Service verfügbar ist.

**Request:**
```
GET /api/v1/test/health
```

**Beispiel:**
```bash
curl http://localhost:8080/api/v1/test/health
```

**Response (200):**
```json
{
  "status": "UP"
}
```

---

### 2. Test-Ausführung starten

Startet eine neue Test-Ausführung. Der Aufruf ist asynchron - die Tests werden im Hintergrund ausgeführt und der Endpoint gibt sofort eine Run-ID zurück.

**Request:**
```
POST /api/v1/test/execute
Content-Type: application/json
```

**Request Body:**

| Feld                  | Typ              | Pflicht | Beschreibung                                           |
|-----------------------|------------------|---------|--------------------------------------------------------|
| `environment`         | String           | Ja      | Ziel-Umgebung, eine der konfigurierten Umgebungen (Standard: `dev`, `staging`, `prod`, `performance`; siehe [Ziel-Umgebungen](#ziel-umgebungen)). Unbekannte Namen → 400 |
| `tags`                | Liste (Strings)  | Ja      | Einfache Cucumber-Tags (`@smoke`, `API-Test`), ODER-verknüpft, max. 20. Tag-Ausdrücke wie `@a and not @b` werden mit 400 abgelehnt |
| `features`            | Liste (Strings)  | Nein    | Feature-Dateien relativ zu `features/`, optional mit Zeilennummer: `GUIProjectTests.feature`, `API-Test.feature:12`. Keine absoluten Pfade, kein `..`, max. 50 |
| `browser`             | String           | Nein    | Browser für UI-Tests: `chromium`, `chrome`, `firefox`, `webkit` |
| `headless`            | Boolean          | Nein    | Browser ohne Fenster starten (Standard: `true`)        |
| `environmentVariables`| Map              | Nein    | Laufzeit-Overrides als System-Properties für die Dauer des Laufs (z.B. `baseUrl`, `apiURL`, `todoApiUrl`). Keys mit den Präfixen `java.`, `sun.`, `os.`, `user.`, `spring.`, `management.`, `server.` werden ignoriert und geloggt. Max. 20 Einträge |
| `projectKey`          | String           | Nein    | Jira/Zephyr-Projekt-Schlüssel (`PROJ`, Großbuchstaben) für diesen Lauf (überschreibt `zephyr.default-project-key`) |
| `zephyrTemplateTestRunKey` | String      | Nein    | Zephyr-Testrun (`PROJ-C15`, `PROJ-R1`), dessen Testfälle in den neuen Cycle geklont werden (überschreibt `zephyr.template-test-run-key`) - z.B. für einen abweichenden Testfallsatz wie API-Tests |
| `zephyrResultFolder`  | String           | Nein    | Zephyr-Folder-Pfad für den neuen Cycle (überschreibt `zephyr.result-folder`), max. 255 Zeichen |

Die früher reservierten, nie ausgewerteten Felder `parallelCount`, `retryFailedTests`, `maxRetries`,
`timeoutMinutes`, `webhookUrl`, `priority` und `initiator` wurden entfernt. Schicken Clients sie noch
mit, werden sie ohne Fehler ignoriert.

**Hinweis zu `environmentVariables`:** Die Werte überschreiben für die Dauer des Laufs die
Konfiguration (System-Property vor `config.properties`, aber nach echten Umgebungsvariablen des Pods).
Zugangsdaten gehören nicht in den Request, sondern in Secrets/Vault.

**Beispiel - Smoke Tests starten:**
```bash
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "dev",
    "tags": ["@smoke"]
  }'
```

**Beispiel - nur bestimmte Feature-Dateien:**
```bash
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "staging",
    "tags": ["@API-Test"],
    "features": ["API-Test.feature", "Todo-API-Test.feature"]
  }'
```

**Beispiel - API-Tests mit eigenem Zephyr-Testfallsatz:**

Frontend-Tests (z.B. `@SmokeTest`) klonen standardmäßig aus dem global konfigurierten
`zephyr.template-test-run-key` (z.B. `PROJ-C8`). Für API-Tests mit einem eigenen, davon
unabhängigen Testfallsatz muss der Cycle stattdessen aus einem Zephyr-Testrun geklont werden,
der die API-Testfälle als Items enthält - sonst schlägt der Ergebnis-Upload mit `400`
fehl ("Testfall nicht im Testrun gefunden"). Dazu `zephyrTemplateTestRunKey` (und optional
`zephyrResultFolder`) explizit im Request setzen:

```bash
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "staging",
    "tags": ["@API-Test"],
    "projectKey": "PROJ",
    "zephyrTemplateTestRunKey": "PROJ-C15",
    "zephyrResultFolder": "/Testautomation/API"
  }'
```

**Beispiel - UI-Tests mit Firefox:**
```bash
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "dev",
    "tags": ["@End2End"],
    "browser": "firefox"
  }'
```

Die Ziel-URL der UI-Tests (`baseUrl`) wird pro Deployment gesetzt (Umgebungsvariable `BASEURL`)
und je Ziel-Umgebung über `ENV_<UMGEBUNG>_BASEURL` (siehe [Ziel-Umgebungen](#ziel-umgebungen)).
Pro Lauf lässt sie sich per `"environmentVariables": {"baseUrl": "https://meine-app.example.com/"}`
überschreiben, sofern im Pod keine passende Umgebungsvariable gesetzt ist (die hat Vorrang).

### Ziel-Umgebungen

Ein Lauf gegen `"environment": "staging"` liest jeden Konfigurationswert zuerst
umgebungsspezifisch als `env.staging.<key>` und erst danach global als `<key>`. Das gilt für alle
Quellen (Umgebungsvariable, `.env`, Vault/Secret-Datei, `config.properties`):

| Wert                       | global (z.B. `dev`)         | nur Umgebung `staging`                      |
|----------------------------|-----------------------------|---------------------------------------------|
| UI-Ziel (`baseUrl`)        | `BASEURL`                   | `ENV_STAGING_BASEURL`                       |
| Todo-API (`todoApiUrl`)    | `TODOAPIURL`                | `ENV_STAGING_TODOAPIURL`                    |
| GUI-Login Rolle `admin`    | `GUI_USER_ADMIN_USERNAME` / `GUI_USER_ADMIN_PASSWORD` | `ENV_STAGING_GUI_USER_ADMIN_USERNAME` / `ENV_STAGING_GUI_USER_ADMIN_PASSWORD` |
| Todo-Login Rolle `admin`   | `TODO_USER_ADMIN_USERNAME` / `TODO_USER_ADMIN_PASSWORD` | `ENV_STAGING_TODO_USER_ADMIN_USERNAME` / `ENV_STAGING_TODO_USER_ADMIN_PASSWORD` |

Fehlt ein umgebungsspezifischer Wert, gilt der globale. Zugangsdaten stehen nie in Feature-Dateien:
Steps wie `ich mich als Rolle "admin" einlogge` lesen `<system>.user.<rolle>.username/password`.
Fehlen sie, schlägt der Step mit dem Namen der erwarteten Variable fehl.

**Umgebungen ändern (ohne Rebuild):**

1. Erlaubte Namen setzen: `TEST_ENVIRONMENTS=dev,feature,test` (lokal in `.env`, auf OpenShift in der
   ConfigMap `cucumber-test-config`) oder `test.environments=...` in `config.properties`.
   Erlaubt sind Buchstaben, Ziffern, `_` und `-`; aus `feature-x` wird `ENV_FEATURE_X_...`.
2. Abweichende Werte je Umgebung hinterlegen: URLs in ConfigMap/`config.properties`
   (`ENV_FEATURE_BASEURL` bzw. `env.feature.baseUrl=...`), Zugangsdaten in `.env` bzw. Vault
   (`ENV_FEATURE_GUI_USER_ADMIN_PASSWORD`).
3. Pod neu starten (ConfigMap-Änderungen) und aufrufen: `{"environment": "feature", ...}`.

**Szenarien nur auf bestimmten Umgebungen ausführen (`@env:<umgebung>`):**

Szenarien ohne `@env:`-Tag laufen in jeder Umgebung. Mit einem oder mehreren `@env:`-Tags laufen sie
nur in den genannten Umgebungen. Die Tags werden wie alle Tags vererbt, ein Tag am `Feature`,
an einer `Rule` oder an einem `Examples`-Block gilt also für alle Szenarien darunter.

```gherkin
@GUI
Feature: Dashboard

  Scenario: Login                       # alle Umgebungen

  @env:staging @env:prod
  Scenario: Export nach SAP             # nur staging und prod
```

Der Service verknüpft dazu die Tags aus dem Request per UND mit einem Umgebungsfilter. Bei
`"environment": "staging"` und `"tags": ["@smoke"]` lautet der Cucumber-Ausdruck
`(@smoke) and (@env:staging or not (@env:dev or @env:performance or @env:prod))`. Ausgefilterte
Szenarien werden nicht gestartet und erscheinen deshalb nicht als „skipped“ im Report. Steht ihr
Testfall über das Zephyr-Template im Testrun, wird er dort mit Grund als nicht ausgeführt markiert
(siehe [Nicht ausgeführte Testfälle](#nicht-ausgeführte-testfälle-in-zephyr)). Der wirksame Ausdruck
steht im Feld `tags` der Response.

- Erlaubt sind nur Namen aus `test.environments`. Ein Unit-Test prüft die Feature-Dateien darauf.
  Ein unbekannter Name, etwa durch einen Tippfehler wie `@env:stagign`, lässt das Szenario in
  **keiner** Umgebung laufen, und der Service loggt eine Warnung.
- Auch ein gezielt per Zeilennummer ausgewähltes Szenario (`"features": ["X.feature:12"]`) entfällt,
  wenn es nicht für die Ziel-Umgebung freigegeben ist.
- `@env:`-Tags lassen sich nicht über `tags` im Request angeben. Das ist auch nicht nötig, denn die
  Umgebung kommt aus `environment`.

**Response (202 Accepted):**
```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "QUEUED",
  "environment": "dev",
  "message": "Test execution queued successfully",
  "timestamp": "2026-02-11T14:30:00",
  "statusUrl": "/api/v1/test/status/550e8400-e29b-41d4-a716-446655440000",
  "tags": "(@smoke) and (@env:dev or not (@env:performance or @env:prod or @env:staging))"
}
```

**Fehler:**

| Code | Bedeutung                                      |
|------|-------------------------------------------------|
| 400  | Ungültige Parameter (z.B. fehlende Tags, Tag-Ausdruck, ungültiger Feature-Pfad oder Zephyr-Key, unbekannte Umgebung) |
| 429  | Kapazität erschöpft: alle Slots des zuständigen Pools (mit/ohne Browser) belegt und dessen Warteschlange voll |
| 500  | Interner Serverfehler                           |

---

### 3. Status einer Test-Ausführung abfragen

Fragt den aktuellen Stand eines laufenden oder abgeschlossenen Test-Laufs ab.

**Request:**
```
GET /api/v1/test/status/{runId}
```

**Beispiel:**
```bash
curl http://localhost:8080/api/v1/test/status/550e8400-e29b-41d4-a716-446655440000
```

**Response (200):**
```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "environment": "dev",
  "progress": 45,
  "startTime": "2026-02-11T14:30:05",
  "currentPhase": "EXECUTING",
  "totalTests": 10,
  "passedTests": 4,
  "failedTests": 1,
  "skippedTests": 0
}
```

**Mögliche Status-Werte:**

| Status      | Bedeutung                                          |
|-------------|-----------------------------------------------------|
| `QUEUED`    | Test ist eingeplant, wartet auf freien Slot          |
| `RUNNING`   | Test wird gerade ausgeführt                         |
| `COMPLETED` | Alle Tests erfolgreich abgeschlossen                 |
| `FAILED`    | Tests abgeschlossen, aber mit Fehlern                |
| `CANCELLED` | Test wurde manuell abgebrochen                       |
| `TIMEOUT`   | Test hat das Zeitlimit überschritten                |

**Fehler:**

| Code | Bedeutung                    |
|------|------------------------------|
| 404  | Run-ID nicht gefunden         |

---

### 4. Alle aktiven Tests anzeigen

Zeigt alle aktuell laufenden und wartenden Test-Ausführungen an.

**Request:**
```
GET /api/v1/test/active
```

**Beispiel:**
```bash
curl http://localhost:8080/api/v1/test/active
```

**Response (200):**
```json
[
  {
    "runId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "RUNNING",
    "environment": "dev",
    "progress": 45,
    "startTime": "2026-02-11T14:30:05"
  },
  {
    "runId": "660f9511-f39c-52e5-b827-557766551111",
    "status": "QUEUED",
    "environment": "staging"
  }
]
```

---

### 5. Test-Report abrufen

Ruft den Cucumber JSON-Report eines abgeschlossenen Test-Laufs ab.

**Request:**
```
GET /api/v1/test/report/{runId}
```

**Beispiel:**
```bash
curl http://localhost:8080/api/v1/test/report/550e8400-e29b-41d4-a716-446655440000
```

**Response (200):** Cucumber JSON-Report (vollständiger Testbericht)

**Fehler:**

| Code | Bedeutung                              |
|------|----------------------------------------|
| 404  | Report nicht gefunden                   |
| 425  | Test läuft noch - Report nicht bereit  |

---

### 6. Allure-Report generieren

Generiert einen HTML Allure-Report für eine Test-Ausführung und gibt die URL zum Report zurück.

**Request:**
```
POST /api/v1/test/report/{runId}/generate
```

**Beispiel:**
```bash
curl -X POST http://localhost:8080/api/v1/test/report/550e8400-e29b-41d4-a716-446655440000/generate
```

**Response (200):**
```json
{
  "reportUrl": "http://localhost:8080/reports/550e8400-e29b-41d4-a716-446655440000/allure-report/index.html",
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Allure report successfully generated"
}
```

**Hinweise:**
- Der Report wird als statische HTML-Seite generiert und unter `/reports/{runId}/allure-report/` bereitgestellt
- Unter `/reports/` werden nur die fertigen Report-Ordner ausgeliefert (`allure-report`, `cucumber-reports`, `axe-result`, `screenshots` je Run sowie `combined/allure-report`). Rohdaten (`allure-results`), `status.json`, die Integrations-Outbox und Logs liefern `404`
- Die `reportUrl` kann direkt im Browser geöffnet werden
- Der Report wird über die Allure-Java-API erzeugt, eine Allure CLI ist nicht nötig
- Es läuft immer nur eine Erzeugung gleichzeitig; ein zweiter Aufruf währenddessen erhält `429`
- Die Report-Generierung dauert je nach Testumfang 5-30 Sekunden

**Enthaltene Anhänge im Allure-Report:**

| Anhangstyp                   | Beschreibung                                                                 |
|------------------------------|------------------------------------------------------------------------------|
| Screenshot (PNG)             | Wird nach jedem UI-Schritt (`@When`) aufgenommen; am Szenario-Ende mit Prefix `END_` (bestanden) bzw. `FAILED_` (fehlgeschlagen) |
| Barrierefreiheitsbericht (HTML) | Axe-Accessibility-Scan-Ergebnis im Teardown-Schritt jedes UI-Szenarios    |
| HTTP Request / Response      | Bei API-Tests (`@API-Test`) wird jede HTTP-Anfrage mit Request und Response eingebettet |

**Axe-Accessibility-Report direkt abrufen:**

Der Axe-Report ist auch ohne Allure-Generierung direkt aufrufbar:
```
GET /reports/{runId}/axe-result/index.html
```
Pro Seiten-URL wird innerhalb eines Runs genau ein Scan durchgeführt (Deduplizierung).

**Fehler:**

| Code | Bedeutung                                      |
|------|------------------------------------------------|
| 404  | Test-Ausführung nicht gefunden                 |
| 500  | Fehler bei Report-Generierung (z.B. Allure CLI nicht installiert) |

---

### 7. Allure Report-URL abrufen

Gibt die URL zu einem bereits generierten Allure-Report zurück (ohne Neu-Generierung).

**Request:**
```
GET /api/v1/test/report/{runId}/url
```

**Beispiel:**
```bash
curl http://localhost:8080/api/v1/test/report/550e8400-e29b-41d4-a716-446655440000/url
```

**Response (200):**
```json
{
  "url": "http://localhost:8080/reports/550e8400-e29b-41d4-a716-446655440000/allure-report/index.html"
}
```

**Fehler:**

| Code | Bedeutung                              |
|------|----------------------------------------|
| 404  | Report nicht gefunden oder noch nicht generiert |

---

### 8. Test-Ausführung abbrechen

Bricht eine laufende Test-Ausführung ab.

**Request:**
```
DELETE /api/v1/test/cancel/{runId}
```

**Beispiel:**
```bash
curl -X DELETE http://localhost:8080/api/v1/test/cancel/550e8400-e29b-41d4-a716-446655440000
```

**Response (200):**
```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CANCELLED",
  "endTime": "2026-02-11T14:35:12"
}
```

**Fehler:**

| Code | Bedeutung                                  |
|------|--------------------------------------------|
| 404  | Run-ID nicht gefunden                       |
| 409  | Test kann nicht abgebrochen werden           |

---

### 9. Test-Ausführung löschen

Löscht eine abgeschlossene Test-Ausführung und alle zugehörigen Reports. Laufende Tests können nicht gelöscht werden.

**Request:**
```
DELETE /api/v1/test/{runId}
```

**Beispiel:**
```bash
curl -X DELETE http://localhost:8080/api/v1/test/550e8400-e29b-41d4-a716-446655440000
```

**Response:** `204 No Content` (erfolgreich gelöscht)

**Fehler:**

| Code | Bedeutung                                          |
|------|-----------------------------------------------------|
| 404  | Run-ID nicht gefunden oder Test läuft noch          |

---

### 10. Statistiken abrufen

Gibt aggregierte Statistiken über alle Test-Ausführungen zurück. Optional nach Umgebung filterbar.

**Request:**
```
GET /api/v1/test/statistics
GET /api/v1/test/statistics?environment=dev
```

**Beispiel:**
```bash
curl http://localhost:8080/api/v1/test/statistics

# Nur für eine bestimmte Umgebung:
curl http://localhost:8080/api/v1/test/statistics?environment=staging
```

**Response (200):**
```json
{
  "totalRuns": 42,
  "completedRuns": 35,
  "failedRuns": 5,
  "runningRuns": 1,
  "queuedRuns": 1,
  "successRate": 83.33,
  "maxConcurrentRuns": 5,
  "maxConcurrentBrowserRuns": 2
}
```

---

### 11. Verfügbare Runs auflisten

Listet alle Test-Runs auf, die Allure-Ergebnisse auf dem Dateisystem enthalten. Nützlich, um Run-IDs für den kombinierten Report auszuwählen.

**Request:**
```
GET /api/v1/test/runs
```

**Beispiel:**
```bash
curl http://localhost:8080/api/v1/test/runs
```

**Response (200):**
```json
[
  "550e8400-e29b-41d4-a716-446655440000",
  "660f9511-f39c-52e5-b827-557766551111"
]
```

**Hinweise:**
- Es werden nur Runs zurückgegeben, die ein `allure-results/`-Verzeichnis enthalten
- Die Liste basiert auf dem Dateisystem, nicht auf dem In-Memory-Status

---

### 12. Kombinierten Allure-Report generieren

Generiert einen übergreifenden Allure-Report über mehrere Test-Runs. Der kombinierte Report wird unter `/reports/combined/allure-report/index.html` bereitgestellt.

**Request:**
```
POST /api/v1/test/report/combined/generate
Content-Type: application/json
```

**Request Body (optional):**

| Feld     | Typ             | Pflicht | Beschreibung                                        |
|----------|-----------------|---------|-----------------------------------------------------|
| `runIds` | Liste (UUIDs)   | Nein    | Run-IDs für den Report. Leer oder ohne Body = alle  |

**Beispiel - Alle Runs kombinieren:**
```bash
curl -X POST http://localhost:8080/api/v1/test/report/combined/generate
```

**Beispiel - Bestimmte Runs kombinieren:**
```bash
curl -X POST http://localhost:8080/api/v1/test/report/combined/generate \
  -H "Content-Type: application/json" \
  -d '{
    "runIds": [
      "550e8400-e29b-41d4-a716-446655440000",
      "660f9511-f39c-52e5-b827-557766551111"
    ]
  }'
```

**Response (200):**
```json
{
  "reportUrl": "/reports/combined/allure-report/index.html",
  "message": "Combined Allure report successfully generated"
}
```

**Hinweise:**
- Ohne Request-Body oder mit leerer `runIds`-Liste werden alle verfügbaren Runs kombiniert
- Der Report wird über die Allure-Java-API erzeugt, eine Allure CLI ist nicht nötig
- Es läuft immer nur eine Erzeugung gleichzeitig; ein zweiter Aufruf währenddessen erhält `429`
- Der Report ist sofort unter der zurückgegebenen URL im Browser aufrufbar
- Bei erneuter Generierung wird der vorherige kombinierte Report überschrieben
- Nützlich für Sprint-Reports oder teamübergreifende Auswertungen
- Im Report werden die einzelnen Runs als Suites gruppiert, z.B. `Run a76c4874 @Backend, @smoke` - so ist sofort erkennbar, welche Tags bei welchem Lauf verwendet wurden
- Jeder Testfall erscheint pro Run einzeln (keine Deduplizierung), sodass alle Ausführungen sichtbar sind

**Fehler:**

| Code | Bedeutung                                      |
|------|-------------------------------------------------|
| 404  | Keine Runs mit Allure-Ergebnissen gefunden          |
| 429  | Es wird bereits ein kombinierter Report erzeugt     |
| 500  | Fehler bei Report-Generierung                  |

---

## Typischer Workflow

```
1. Test starten              POST /api/v1/test/execute
       |
       v
2. Run-ID merken             --> "runId": "abc-123-..."
       |
       v
3. Status pollen             GET /api/v1/test/status/abc-123-...
       |                     (wiederholen bis status != RUNNING/QUEUED)
       v
4. Allure-Report generieren  POST /api/v1/test/report/abc-123-/generate
       |
       v
5. Report-URL öffnen        Browser: http://localhost:8080/reports/abc-123-.../allure-report/index.html
       |
       v
6. Optional: Aufräumen      DELETE /api/v1/test/abc-123-...
```

**Vollständiges Beispiel mit curl:**

```bash
# 1. Test starten
RUN_ID=$(curl -s -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{"environment":"dev","tags":["@smoke"]}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['runId'])")

echo "Test gestartet: $RUN_ID"

# 2. Auf Abschluss warten
while true; do
  STATUS=$(curl -s http://localhost:8080/api/v1/test/status/$RUN_ID \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
  echo "Status: $STATUS"
  if [ "$STATUS" != "RUNNING" ] && [ "$STATUS" != "QUEUED" ]; then
    break
  fi
  sleep 5
done

# 3. Allure-Report generieren
REPORT_URL=$(curl -s -X POST http://localhost:8080/api/v1/test/report/$RUN_ID/generate \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['reportUrl'])")

echo "Allure-Report verfügbar unter: $REPORT_URL"

# 4. Optional: JSON-Report herunterladen
curl -s http://localhost:8080/api/v1/test/report/$RUN_ID > report.json
echo "JSON-Report gespeichert: report.json"
```

### Kombinierter Report über mehrere Runs

```bash
# 1. Verfügbare Runs auflisten
curl -s http://localhost:8080/api/v1/test/runs
# --> ["550e8400-...", "660f9511-..."]

# 2. Kombinierten Report generieren (alle Runs)
curl -s -X POST http://localhost:8080/api/v1/test/report/combined/generate

# Oder: Nur bestimmte Runs kombinieren
curl -s -X POST http://localhost:8080/api/v1/test/report/combined/generate \
  -H "Content-Type: application/json" \
  -d '{"runIds": ["550e8400-...", "660f9511-..."]}'

# 3. Report im Browser öffnen
# --> http://localhost:8080/reports/combined/allure-report/index.html
```

---

## Parallele Ausführung (mehrere Teams)

Im Subprozess-Modus (`test.execution.mode=subprocess`, auf OpenShift/k8s gesetzt) läuft jeder Test-Lauf
in einer eigenen JVM. Läufe gegen **unterschiedliche Ziel-Umgebungen** können daher gleichzeitig laufen,
ohne sich Konfiguration oder Zugangsdaten zu teilen. Jeder Lauf erhält eine eigene Run-ID und isolierte
Ergebnis-Verzeichnisse.

Es gibt zwei getrennte Pools:

| Pool          | Gilt für                                                        | Gleichzeitig | Property                                     |
|---------------|-----------------------------------------------------------------|--------------|----------------------------------------------|
| Mit Browser   | Läufe, die mindestens ein Szenario mit `@GUI` enthalten          | 2            | `test.execution.max-concurrent-browser-runs` |
| Ohne Browser  | alle übrigen Läufe (z.B. nur `@API-Test`, `@Backend`)            | 5            | `test.execution.max-concurrent-runs`         |

Welche Tags einen Browser bedeuten, steht in `test.execution.browser-tags` (Standard `@GUI`, auch vom
Feature geerbt). Ein Lauf mit gemischten Szenarien zählt als Browser-Lauf. Im In-Process-Modus (lokaler
Standard) laufen alle Läufe nacheinander.

**Beispiel: Team A und Team B starten gleichzeitig Tests:**

```bash
# Team A - Smoke Tests
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "dev",
    "tags": ["@smoke"]
  }'

# Team B - API Tests
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "staging",
    "tags": ["@API-Test"]
  }'
```

Falls alle Slots eines Pools belegt sind, werden weitere Anfragen in dessen Warteschlange gestellt
(max. 20 je Pool) und automatisch ausgeführt, sobald ein Slot frei wird. Ist auch die Warteschlange
voll, antwortet der Dienst mit `429`. Volle Browser-Slots blockieren API-Läufe nicht.

---

## Verfügbare Test-Tags

| Tag           | Beschreibung                          |
|---------------|---------------------------------------|
| `@smoke`      | Schnelle Smoke Tests                  |
| `@regression` | Vollständige Regressionstests        |
| `@End2End`    | End-to-End UI-Tests                   |
| `@API-Test`   | API-Tests                             |
| `@env:<name>` | Szenario nur in dieser Ziel-Umgebung (siehe [Ziel-Umgebungen](#ziel-umgebungen)); nicht als Request-Tag |
| `@T-3511`     | Einzelner Testfall (nach ID)          |
| `@T-3512`     | Einzelner Testfall (nach ID)          |
| `@T-3513`     | Einzelner Testfall (nach ID)          |
| `@T-3514`     | Einzelner Testfall (nach ID)          |

Mehrere Tags werden ODER-verknüpft (`@smoke or @API-Test`):
```json
{
  "tags": ["@smoke", "@API-Test"]
}
```
Tag-Ausdrücke mit `and`, `not` oder Klammern werden abgelehnt (400).

**Zuordnung der Ergebnisse zu Zephyr-Testfällen:**
- Pro Szenario zählt der **letzte** `@T-…`-Tag (bzw. `@PROJ-T…`). Tags vom Feature oder von der
  Scenario Outline werden so von spezifischeren Tags am Szenario bzw. am `Examples`-Block übersteuert.
  Mehrere eigene Testfall-Tags an einem Szenario werden nicht unterstützt, nur der letzte erhält das Ergebnis.
- Alle Beispielzeilen einer Scenario Outline mit demselben Testfall-Key werden zu **einem** Ergebnis
  zusammengefasst: `Fail`, sobald eine Zeile fehlschlägt. Der Kommentar listet jede Zeile einzeln auf.
- Schritt-Status: Standardmäßig erhält jeder Skript-Schritt den schlechtesten Status über alle Zeilen.
  Haben die Zephyr-Testfälle eine Testdaten-Tabelle (Schritte werden je Datenzeile wiederholt),
  `zephyr.outline-rows-as-test-data=true` setzen: Dann wird der Schritt-Status je Zeile fortlaufend
  gesendet. Die Reihenfolge der `Examples`-Zeilen muss der der Zephyr-Datenzeilen entsprechen.
- Ein fehlgeschlagener Before-/After-Hook (z.B. Screenshot) führt ebenfalls zu `Fail`.

#### Nicht ausgeführte Testfälle in Zephyr

Wird der Testrun aus einem Template angelegt (`zephyr.template-test-run-key` bzw.
`zephyrTemplateTestRunKey`), bekommen alle Testfälle des Templates ein Ergebnis, auch die ohne
Cucumber-Ergebnis. Diese werden in einem eigenen Upload nach den echten Ergebnissen gesendet. Der
Kommentar beginnt mit „Nicht ausgeführt:“ und nennt den Grund:

| Grund | Status | Kommentar (Beispiel) |
|-------|--------|----------------------|
| Alle Szenarien des Testfalls per `@env:` für andere Umgebungen | `zephyr.env-excluded-status` | Nicht für Umgebung **dev** freigegeben (nur: prod, staging) |
| Szenario passt nicht zu `tags`/`features` des Requests (auch bei Auswahl per Zeilennummer) | `zephyr.not-executed-status` | Nicht im Lauf ausgewählt (Tags: @Backend) |
| Szenario war ausgewählt, lieferte aber kein Ergebnis | `zephyr.not-executed-status` | Szenario war ausgewählt, hat aber kein Ergebnis geliefert (Lauf abgebrochen …) |
| Kein Szenario mit diesem `@T-`-Tag in den Features | `zephyr.not-executed-status` | Kein automatisiertes Szenario mit Tag @PROJ-T99 vorhanden |

Beide Status sind standardmäßig `Not Executed` (Env `ZEPHYR_NOT_EXECUTED_STATUS` bzw.
`ZEPHYR_ENV_EXCLUDED_STATUS`). Für einen eigenen Status, etwa „Not Applicable“ für
Umgebungs-Ausschlüsse, muss der Status im Zephyr-Projekt angelegt sein. Ist er dort unbekannt,
lehnt Zephyr nur diesen zweiten Upload ab, die echten Ergebnisse sind dann schon gespeichert. Die
markierten Keys stehen im Lauf-Status unter `metadata.zephyrNotExecuted`. Ohne Template enthält der
Testrun nur die ausgeführten Testfälle, dann gibt es nichts zu markieren.

---

## Fehlerbehebung

| Problem                                    | Lösung                                                        |
|--------------------------------------------|----------------------------------------------------------------|
| `400 Bad Request`                          | Request-Body prüfen: `environment` und `tags` sind Pflicht; `environment` muss in `TEST_ENVIRONMENTS` stehen |
| Step meldet „Zugangsdaten fehlen“          | Genannte Variable (z.B. `ENV_STAGING_GUI_USER_ADMIN_PASSWORD`) in `.env` bzw. Vault setzen |
| `404 Not Found`                            | Run-ID prüfen - ist sie korrekt?                              |
| `429 Too Many Requests`                    | Warten bis laufende Tests abgeschlossen sind                   |
| Status bleibt auf `QUEUED`                 | Alle Slots des Pools belegt (In-Process: nur ein Lauf gleichzeitig), weitere warten (max. 20 je Pool) - mit `GET /active` prüfen |
| Report leer oder nicht vorhanden           | Test muss erst abgeschlossen sein (Status: COMPLETED/FAILED)   |
| UI-Tests schlagen fehl                     | Browser prüfen (`chromium` ist Standard im Container)         |
| Keine Screenshots im Allure-Report         | Screenshots werden nur bei UI-Tests (`@End2End`, `@smoke`) erstellt, nicht bei `@API-Test` |
| Kein Barrierefreiheitsbericht im Report    | Axe-Scan wird nur bei UI-Tests ausgeführt; Report unter `/reports/{runId}/axe-result/index.html` prüfen |

**Monitoring-Endpoints:**

| Endpoint             | Beschreibung                    |
|----------------------|---------------------------------|
| `/actuator/health`   | Spring Boot Health Check        |
| `/actuator/info`     | Anwendungsinformationen         |
| `/actuator/metrics`  | Metriken (Prometheus-kompatibel)|
