# Cucumber Test Service - API Benutzeranleitung

## Inhaltsverzeichnis

1. [Ueberblick](#ueberblick)
2. [Basis-URL](#basis-url)
3. [Swagger UI](#swagger-ui)
4. [Endpoints](#endpoints)
   - [Health Check](#1-health-check)
   - [Test starten](#2-test-ausfuehrung-starten)
   - [Status abfragen](#3-status-einer-test-ausfuehrung-abfragen)
   - [Aktive Tests anzeigen](#4-alle-aktiven-tests-anzeigen)
   - [Report abrufen](#5-test-report-abrufen)
   - [Allure-Report generieren](#6-allure-report-generieren)
   - [Report-URL abrufen](#7-allure-report-url-abrufen)
   - [Test abbrechen](#8-test-ausfuehrung-abbrechen)
   - [Test loeschen](#9-test-ausfuehrung-loeschen)
   - [Statistiken](#10-statistiken-abrufen)
   - [Verfuegbare Runs auflisten](#11-verfuegbare-runs-auflisten)
   - [Kombinierten Report generieren](#12-kombinierten-allure-report-generieren)
5. [Typischer Workflow](#typischer-workflow)
6. [Parallele Ausfuehrung](#parallele-ausfuehrung-mehrere-teams)
7. [Verfuegbare Tags](#verfuegbare-test-tags)
8. [Fehlerbehebung](#fehlerbehebung)

---

## Ueberblick

Der Cucumber Test Service stellt eine REST API bereit, ueber die Teams automatisierte Backend- und Frontend-Tests ausloesen koennen. Der Dienst laeuft auf OpenShift und unterstuetzt die gleichzeitige Ausfuehrung mehrerer Test-Laeufe mit isolierten Ergebnissen pro Lauf.

**Basis-Pfad:** `/api/v1/test`
**Content-Type:** `application/json`
**Max. gleichzeitige Laeufe:** 5

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

Dort koennen alle Endpoints direkt im Browser ausprobiert werden.

---

## Endpoints

### 1. Health Check

Prueft, ob der Service verfuegbar ist.

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

### 2. Test-Ausfuehrung starten

Startet eine neue Test-Ausfuehrung. Der Aufruf ist asynchron - die Tests werden im Hintergrund ausgefuehrt und der Endpoint gibt sofort eine Run-ID zurueck.

**Request:**
```
POST /api/v1/test/execute
Content-Type: application/json
```

**Request Body:**

| Feld                  | Typ              | Pflicht | Beschreibung                                           |
|-----------------------|------------------|---------|--------------------------------------------------------|
| `environment`         | String           | Ja      | Ziel-Umgebung: `dev`, `staging`, `prod`, `performance` |
| `tags`                | Liste (Strings)  | Ja      | Cucumber-Tags zum Filtern der Tests                    |
| `features`            | Liste (Strings)  | Nein    | Spezifische Feature-Dateien                            |
| `parallelCount`       | Integer          | Nein    | Anzahl paralleler Threads (Standard: 5)                |
| `browser`             | String           | Nein    | Browser fuer UI-Tests: `chromium`, `firefox`, `webkit` |
| `environmentVariables`| Map              | Nein    | Benutzerdefinierte Umgebungsvariablen                  |
| `retryFailedTests`    | Boolean          | Nein    | Fehlgeschlagene Tests wiederholen (Standard: true)     |
| `maxRetries`          | Integer          | Nein    | Max. Wiederholungsversuche (Standard: 2)               |
| `timeoutMinutes`      | Integer          | Nein    | Timeout in Minuten (Standard: 30)                      |
| `webhookUrl`          | String           | Nein    | URL fuer Ergebnis-Benachrichtigung                     |
| `priority`            | String           | Nein    | `LOW`, `NORMAL`, `HIGH`, `CRITICAL` (Standard: NORMAL) |
| `initiator`           | String           | Nein    | Wer den Test ausloest (z.B. Pipeline-Name)             |

**Beispiel - Smoke Tests starten:**
```bash
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "dev",
    "tags": ["@smoke"]
  }'
```

**Beispiel - API-Tests mit Optionen:**
```bash
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "staging",
    "tags": ["@API-Test"],
    "parallelCount": 3,
    "timeoutMinutes": 15,
    "initiator": "jenkins-pipeline-team-a"
  }'
```

**Beispiel - UI-Tests mit Firefox:**
```bash
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "dev",
    "tags": ["@End2End"],
    "browser": "firefox",
    "environmentVariables": {
      "BASE_URL": "https://meine-app.example.com"
    }
  }'
```

**Response (202 Accepted):**
```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "QUEUED",
  "environment": "dev",
  "message": "Test execution queued successfully",
  "timestamp": "2026-02-11T14:30:00",
  "statusUrl": "/api/v1/test/status/550e8400-e29b-41d4-a716-446655440000",
  "tags": "@smoke"
}
```

**Fehler:**

| Code | Bedeutung                                      |
|------|-------------------------------------------------|
| 400  | Ungueltige Parameter (z.B. fehlende Tags)       |
| 429  | Maximale Anzahl gleichzeitiger Laeufe erreicht  |
| 500  | Interner Serverfehler                           |

---

### 3. Status einer Test-Ausfuehrung abfragen

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

**Moegliche Status-Werte:**

| Status      | Bedeutung                                          |
|-------------|-----------------------------------------------------|
| `QUEUED`    | Test ist eingeplant, wartet auf freien Slot          |
| `RUNNING`   | Test wird gerade ausgefuehrt                         |
| `COMPLETED` | Alle Tests erfolgreich abgeschlossen                 |
| `FAILED`    | Tests abgeschlossen, aber mit Fehlern                |
| `CANCELLED` | Test wurde manuell abgebrochen                       |
| `TIMEOUT`   | Test hat das Zeitlimit ueberschritten                |

**Fehler:**

| Code | Bedeutung                    |
|------|------------------------------|
| 404  | Run-ID nicht gefunden         |

---

### 4. Alle aktiven Tests anzeigen

Zeigt alle aktuell laufenden und wartenden Test-Ausfuehrungen an.

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

**Response (200):** Cucumber JSON-Report (vollstaendiger Testbericht)

**Fehler:**

| Code | Bedeutung                              |
|------|----------------------------------------|
| 404  | Report nicht gefunden                   |
| 425  | Test laeuft noch - Report nicht bereit  |

---

### 6. Allure-Report generieren

Generiert einen HTML Allure-Report fuer eine Test-Ausfuehrung und gibt die URL zum Report zurueck.

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
- Die `reportUrl` kann direkt im Browser geoeffnet werden
- **Voraussetzung:** Allure CLI muss auf dem Server installiert sein
- Die Report-Generierung dauert je nach Testumfang 5-30 Sekunden

**Enthaltene Anhaenge im Allure-Report:**

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
Pro Seiten-URL wird innerhalb eines Runs genau ein Scan durchgefuehrt (Deduplizierung).

**Fehler:**

| Code | Bedeutung                                      |
|------|------------------------------------------------|
| 404  | Test-Ausfuehrung nicht gefunden                 |
| 500  | Fehler bei Report-Generierung (z.B. Allure CLI nicht installiert) |

---

### 7. Allure Report-URL abrufen

Gibt die URL zu einem bereits generierten Allure-Report zurueck (ohne Neu-Generierung).

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

### 8. Test-Ausfuehrung abbrechen

Bricht eine laufende Test-Ausfuehrung ab.

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

### 9. Test-Ausfuehrung loeschen

Loescht eine abgeschlossene Test-Ausfuehrung und alle zugehoerigen Reports. Laufende Tests koennen nicht geloescht werden.

**Request:**
```
DELETE /api/v1/test/{runId}
```

**Beispiel:**
```bash
curl -X DELETE http://localhost:8080/api/v1/test/550e8400-e29b-41d4-a716-446655440000
```

**Response:** `204 No Content` (erfolgreich geloescht)

**Fehler:**

| Code | Bedeutung                                          |
|------|-----------------------------------------------------|
| 404  | Run-ID nicht gefunden oder Test laeuft noch          |

---

### 10. Statistiken abrufen

Gibt aggregierte Statistiken ueber alle Test-Ausfuehrungen zurueck. Optional nach Umgebung filterbar.

**Request:**
```
GET /api/v1/test/statistics
GET /api/v1/test/statistics?environment=dev
```

**Beispiel:**
```bash
curl http://localhost:8080/api/v1/test/statistics

# Nur fuer eine bestimmte Umgebung:
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
  "maxConcurrentRuns": 5
}
```

---

### 11. Verfuegbare Runs auflisten

Listet alle Test-Runs auf, die Allure-Ergebnisse auf dem Dateisystem enthalten. Nuetzlich, um Run-IDs fuer den kombinierten Report auszuwaehlen.

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
- Es werden nur Runs zurueckgegeben, die ein `allure-results/`-Verzeichnis enthalten
- Die Liste basiert auf dem Dateisystem, nicht auf dem In-Memory-Status

---

### 12. Kombinierten Allure-Report generieren

Generiert einen uebergreifenden Allure-Report ueber mehrere Test-Runs. Der kombinierte Report wird unter `/reports/combined/allure-report/index.html` bereitgestellt.

**Request:**
```
POST /api/v1/test/combined-report/generate
Content-Type: application/json
```

**Request Body (optional):**

| Feld     | Typ             | Pflicht | Beschreibung                                        |
|----------|-----------------|---------|-----------------------------------------------------|
| `runIds` | Liste (UUIDs)   | Nein    | Run-IDs fuer den Report. Leer oder ohne Body = alle  |

**Beispiel - Alle Runs kombinieren:**
```bash
curl -X POST http://localhost:8080/api/v1/test/combined-report/generate
```

**Beispiel - Bestimmte Runs kombinieren:**
```bash
curl -X POST http://localhost:8080/api/v1/test/combined-report/generate \
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
- Ohne Request-Body oder mit leerer `runIds`-Liste werden alle verfuegbaren Runs kombiniert
- **Voraussetzung:** Allure CLI muss auf dem Server installiert sein
- Der Report ist sofort unter der zurueckgegebenen URL im Browser aufrufbar
- Bei erneuter Generierung wird der vorherige kombinierte Report ueberschrieben
- Nuetzlich fuer Sprint-Reports oder teamuebergreifende Auswertungen
- Im Report werden die einzelnen Runs als Suites gruppiert, z.B. `Run a76c4874 @Backend, @smoke` - so ist sofort erkennbar, welche Tags bei welchem Lauf verwendet wurden
- Jeder Testfall erscheint pro Run einzeln (keine Deduplizierung), sodass alle Ausfuehrungen sichtbar sind

**Fehler:**

| Code | Bedeutung                                      |
|------|-------------------------------------------------|
| 404  | Keine Runs gefunden oder Allure CLI nicht verfuegbar |
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
5. Report-URL oeffnen        Browser: http://localhost:8080/reports/abc-123-.../allure-report/index.html
       |
       v
6. Optional: Aufraeumen      DELETE /api/v1/test/abc-123-...
```

**Vollstaendiges Beispiel mit curl:**

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

echo "Allure-Report verfuegbar unter: $REPORT_URL"

# 4. Optional: JSON-Report herunterladen
curl -s http://localhost:8080/api/v1/test/report/$RUN_ID > report.json
echo "JSON-Report gespeichert: report.json"
```

### Kombinierter Report ueber mehrere Runs

```bash
# 1. Verfuegbare Runs auflisten
curl -s http://localhost:8080/api/v1/test/runs
# --> ["550e8400-...", "660f9511-..."]

# 2. Kombinierten Report generieren (alle Runs)
curl -s -X POST http://localhost:8080/api/v1/test/combined-report/generate

# Oder: Nur bestimmte Runs kombinieren
curl -s -X POST http://localhost:8080/api/v1/test/combined-report/generate \
  -H "Content-Type: application/json" \
  -d '{"runIds": ["550e8400-...", "660f9511-..."]}'

# 3. Report im Browser oeffnen
# --> http://localhost:8080/reports/combined/allure-report/index.html
```

---

## Parallele Ausfuehrung (mehrere Teams)

Der Dienst unterstuetzt bis zu **5 gleichzeitige Test-Laeufe**. Jeder Lauf erhaelt eine eigene Run-ID und isolierte Ergebnis-Verzeichnisse, sodass sich parallele Ausfuehrungen nicht gegenseitig beeinflussen.

**Beispiel: Team A und Team B starten gleichzeitig Tests:**

```bash
# Team A - Smoke Tests
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "dev",
    "tags": ["@smoke"],
    "initiator": "team-a-pipeline"
  }'

# Team B - API Tests
curl -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "staging",
    "tags": ["@API-Test"],
    "initiator": "team-b-pipeline"
  }'
```

Falls alle 5 Slots belegt sind, werden weitere Anfragen in eine Warteschlange gestellt und automatisch ausgefuehrt, sobald ein Slot frei wird.

---

## Verfuegbare Test-Tags

| Tag           | Beschreibung                          |
|---------------|---------------------------------------|
| `@smoke`      | Schnelle Smoke Tests                  |
| `@regression` | Vollstaendige Regressionstests        |
| `@End2End`    | End-to-End UI-Tests                   |
| `@API-Test`   | API-Tests                             |
| `@T-3511`     | Einzelner Testfall (nach ID)          |
| `@T-3512`     | Einzelner Testfall (nach ID)          |
| `@T-3513`     | Einzelner Testfall (nach ID)          |
| `@T-3514`     | Einzelner Testfall (nach ID)          |

Tags koennen kombiniert werden:
```json
{
  "tags": ["@smoke", "@API-Test"]
}
```

---

## Fehlerbehebung

| Problem                                    | Loesung                                                        |
|--------------------------------------------|----------------------------------------------------------------|
| `400 Bad Request`                          | Request-Body pruefen: `environment` und `tags` sind Pflicht    |
| `404 Not Found`                            | Run-ID pruefen - ist sie korrekt?                              |
| `429 Too Many Requests`                    | Warten bis laufende Tests abgeschlossen sind                   |
| Status bleibt auf `QUEUED`                 | Alle 5 Slots belegt - mit `GET /active` aktive Tests pruefen   |
| Report leer oder nicht vorhanden           | Test muss erst abgeschlossen sein (Status: COMPLETED/FAILED)   |
| UI-Tests schlagen fehl                     | Browser pruefen (`chromium` ist Standard im Container)         |
| Keine Screenshots im Allure-Report         | Screenshots werden nur bei UI-Tests (`@End2End`, `@smoke`) erstellt, nicht bei `@API-Test` |
| Kein Barrierefreiheitsbericht im Report    | Axe-Scan wird nur bei UI-Tests ausgefuehrt; Report unter `/reports/{runId}/axe-result/index.html` pruefen |

**Monitoring-Endpoints:**

| Endpoint             | Beschreibung                    |
|----------------------|---------------------------------|
| `/actuator/health`   | Spring Boot Health Check        |
| `/actuator/info`     | Anwendungsinformationen         |
| `/actuator/metrics`  | Metriken (Prometheus-kompatibel)|
