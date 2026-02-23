# Cucumber Test Service – Integration für Entwickler

Dieses Dokument richtet sich an Entwickler anderer Projekte, die den Cucumber Test Service zur Testausführung nutzen möchten. Es beschreibt, wie Tests per `curl` lokal getriggert werden und wie die Integration in eine **Tekton-Pipeline** erfolgt.

---

## Inhaltsverzeichnis

1. [Überblick](#überblick)
2. [Voraussetzungen](#voraussetzungen)
3. [API-Endpunkte auf einen Blick](#api-endpunkte-auf-einen-blick)
4. [Verfügbare Test-Tags](#verfügbare-test-tags)
5. [Lokale Entwicklung – curl-Snippets](#lokale-entwicklung--curl-snippets)
   - [Test-Run starten](#1-test-run-starten)
   - [Status abfragen](#2-status-abfragen)
   - [Polling-Loop bis Abschluss](#3-polling-loop-bis-abschluss)
   - [Report-URLs anzeigen](#4-report-urls-anzeigen)
   - [Test abbrechen](#5-test-abbrechen)
   - [Alle aktiven Runs anzeigen](#6-alle-aktiven-runs-anzeigen)
6. [Tekton-Integration](#tekton-integration)
   - [Task: cucumber-test-runner](#task-cucumber-test-runner)
   - [Pipeline-Einbindung](#pipeline-einbindung-nach-deploy)
7. [Empfehlung – Was wann nutzen?](#empfehlung--was-wann-nutzen)

---

## Überblick

Der Cucumber Test Service stellt eine REST-API bereit, über die beliebige Projekte automatisierte Backend- und Frontend-Tests auslösen können. Nach dem Start eines Test-Runs wird eine **Run-ID** zurückgegeben, mit der Status und Reports asynchron abgefragt werden können.

| Eigenschaft | Wert |
|---|---|
| Basis-Pfad | `/api/v1/test` |
| Content-Type | `application/json` |
| Port (lokal) | `8080` |
| Zephyr-/Jira-Integration | optional, per `projectKey` im Request |

---

## Voraussetzungen

Für die curl-Snippets werden folgende Tools benötigt:

- **`curl`** ≥ 7.x
- **`jq`** ≥ 1.6 – JSON-Parser für die Shell ([Installation](https://jqlang.github.io/jq/download/))

```bash
# macOS
brew install jq

# Linux (Debian/Ubuntu)
apt-get install -y jq
```

---

## API-Endpunkte auf einen Blick

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/v1/test/execute` | Test-Run starten |
| `GET` | `/api/v1/test/status/{runId}` | Status abfragen |
| `GET` | `/api/v1/test/active` | Alle laufenden Tests anzeigen |
| `GET` | `/api/v1/test/report/{runId}` | Cucumber-JSON-Report abrufen |
| `POST` | `/api/v1/test/report/{runId}/generate` | Allure-Report generieren |
| `GET` | `/api/v1/test/report/{runId}/url` | Allure-Report-URL abrufen |
| `DELETE` | `/api/v1/test/cancel/{runId}` | Laufenden Test abbrechen |
| `DELETE` | `/api/v1/test/{runId}` | Abgeschlossenen Run löschen |
| `GET` | `/api/v1/test/runs` | Alle verfügbaren Runs auflisten |
| `GET` | `/api/v1/test/health` | Health Check |

Vollständige Dokumentation mit Swagger UI: `http://<service-host>:8080/swagger-ui.html`

---

## Verfügbare Test-Tags

Mit Tags wird gesteuert, welche Szenarien ausgeführt werden. Mehrere Tags können kombiniert werden.

| Tag | Beschreibung |
|---|---|
| `@SmokeTest` | Schnelle Smoke-Tests (Backend + Frontend) |
| `@Backend` | REST-API-Tests |
| `@Frontend` | Playwright UI-Tests |
| `@T-3511` | Einzelnes Zephyr-Testszenario |
| `@T-3512` | PDF/XML-Validierung gegen Response.json |
| `@T-3513` | Login-Test (gültige Credentials) |
| `@T-3514` | Login-Test (ungültige Credentials) |

---

## Lokale Entwicklung – curl-Snippets

**Basis-URL für lokale Entwicklung:** `http://localhost:8080`

### 1) Test-Run starten

Startet einen neuen Test-Run und gibt die `runId` zurück.

```bash
RUN_ID=$(curl -s -X POST http://localhost:8080/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tags":        ["@SmokeTest"],
    "environment": "dev",
    "projectKey":  "PROJ"
  }' | jq -r '.runId')

echo "Run gestartet: $RUN_ID"
```

**Request-Body Parameter:**

| Feld | Typ | Pflicht | Beschreibung |
|---|---|---|---|
| `tags` | `string[]` | nein | Cucumber-Tags, z.B. `["@SmokeTest", "@Backend"]` |
| `environment` | `string` | nein | Ziel-Umgebung (`dev`, `staging`, `prod`) |
| `projectKey` | `string` | nein | Zephyr/Jira-Projektschlüssel, z.B. `PROJ` |
| `features` | `string[]` | nein | Explizite Feature-Dateien (Standard: alle) |

---

### 2) Status abfragen

Einmalige Statusabfrage für einen laufenden oder abgeschlossenen Run.

```bash
curl -s http://localhost:8080/api/v1/test/status/$RUN_ID | jq .
```

**Mögliche Status-Werte:** `QUEUED` → `RUNNING` → `COMPLETED` / `FAILED` / `CANCELLED` / `TIMEOUT`

**Beispiel-Response (fehlgeschlagener Run):**
```json
{
  "runId": "086d45d9-1066-44cd-bb4e-61b1dc72786c",
  "status": "FAILED",
  "environment": "dev",
  "progress": 100,
  "duration": "00:09",
  "errorMessage": "Tests finished with exit code: 1",
  "jiraTicketKey": "PROJ-42",
  "reportUrls": {
    "allure":           "/reports/086d45d9-.../allure-report/index.html",
    "cucumber-report":  "/reports/086d45d9-.../cucumber-reports/Cucumber.html",
    "accessibility":    "/reports/086d45d9-.../axe-result/index.html"
  }
}
```

---

### 3) Polling-Loop bis Abschluss

Wartet automatisch auf den Abschluss des Runs und gibt den finalen Status aus.

```bash
while true; do
  RESPONSE=$(curl -s http://localhost:8080/api/v1/test/status/$RUN_ID)
  STATUS=$(echo "$RESPONSE"   | jq -r '.status')
  PROGRESS=$(echo "$RESPONSE" | jq -r '.progress')
  echo "[$(date +%T)] Status: $STATUS  Fortschritt: ${PROGRESS}%"
  [[ "$STATUS" == "COMPLETED" || "$STATUS" == "FAILED" || "$STATUS" == "TIMEOUT" ]] && break
  sleep 15
done

echo "Finaler Status: $STATUS"
```

---

### 4) Report-URLs anzeigen

```bash
curl -s http://localhost:8080/api/v1/test/status/$RUN_ID | jq '.reportUrls'
```

---

### 5) Test abbrechen

```bash
curl -s -X DELETE http://localhost:8080/api/v1/test/cancel/$RUN_ID | jq .
```

---

### 6) Alle aktiven Runs anzeigen

```bash
curl -s http://localhost:8080/api/v1/test/active | jq .
```

---

## Tekton-Integration

### Task: `cucumber-test-runner`

Der folgende Tekton-Task triggert den Test-Service, wartet auf den Abschluss und **bricht die Pipeline bei fehlgeschlagenen Tests ab**. Die Task-Results (Run-ID, Status, Report-URL, Jira-Ticket) können von nachgelagerten Pipeline-Tasks weiterverwendet werden.

> **Deployment:** Die YAML-Datei gehört ins Repository des **aufrufenden Projekts**, nicht in dieses hier. Nur die Service-URL muss als Parameter angepasst werden.

```yaml
apiVersion: tekton.dev/v1
kind: Task
metadata:
  name: cucumber-test-runner
  labels:
    app.kubernetes.io/component: testing
spec:
  description: >
    Triggert einen Cucumber-Test-Run via REST-API, wartet auf den Abschluss
    und schlägt fehl wenn Tests fehlschlagen.

  params:
    - name: service-url
      type: string
      description: "Basis-URL des Cucumber Test Service (z.B. http://cucumber-test-service:8080)"
    - name: tags
      type: string
      default: "@SmokeTest"
      description: "Cucumber Tag-Filter (z.B. @SmokeTest, @Backend, @T-3511)"
    - name: environment
      type: string
      default: "dev"
      description: "Ziel-Environment (dev / staging / prod)"
    - name: project-key
      type: string
      default: ""
      description: "Zephyr/Jira Projekt-Key (optional, z.B. PROJ)"
    - name: timeout-seconds
      type: string
      default: "600"
      description: "Maximale Wartezeit in Sekunden bevor der Task abbricht"

  results:
    - name: run-id
      description: "UUID des gestarteten Test-Runs"
    - name: status
      description: "Finaler Status (COMPLETED / FAILED / TIMEOUT)"
    - name: report-url
      description: "Relativer Pfad zum Allure-Report"
    - name: jira-ticket
      description: "Jira-Ticket-Key bei fehlgeschlagenem Run (leer wenn kein Fehler)"

  steps:
    - name: trigger-and-wait
      image: alpine:3.19
      script: |
        #!/bin/sh
        set -e

        apk add --no-cache curl jq > /dev/null 2>&1

        SERVICE_URL="$(params.service-url)"
        TIMEOUT="$(params.timeout-seconds)"
        INTERVAL=15

        # ── Payload aufbauen ─────────────────────────────────────────
        PROJ="$(params.project-key)"
        PAYLOAD=$(jq -n \
          --arg tags "$(params.tags)" \
          --arg env  "$(params.environment)" \
          --arg proj "$PROJ" \
          '{
            tags:        [$tags],
            environment: $env,
            projectKey:  (if $proj == "" then null else $proj end)
          }')

        echo "▶ Starte Test-Run"
        echo "  Service:     $SERVICE_URL"
        echo "  Tags:        $(params.tags)"
        echo "  Environment: $(params.environment)"
        echo "  ProjectKey:  ${PROJ:-nicht gesetzt}"

        # ── Test-Run triggern ─────────────────────────────────────────
        EXEC_RESPONSE=$(curl -sf -X POST \
          "${SERVICE_URL}/api/v1/test/execute" \
          -H "Content-Type: application/json" \
          -d "$PAYLOAD")

        RUN_ID=$(echo "$EXEC_RESPONSE" | jq -r '.runId')
        echo -n "$RUN_ID" > "$(results.run-id.path)"
        echo "  Run-ID:      $RUN_ID"

        # ── Polling-Loop ──────────────────────────────────────────────
        ELAPSED=0
        STATUS="QUEUED"

        while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
          RESP=$(curl -sf "${SERVICE_URL}/api/v1/test/status/${RUN_ID}")
          STATUS=$(echo "$RESP"   | jq -r '.status')
          PROGRESS=$(echo "$RESP" | jq -r '.progress // 0')

          printf "[%4ds] Status: %-12s Fortschritt: %s%%\n" \
            "$ELAPSED" "$STATUS" "$PROGRESS"

          case "$STATUS" in
            COMPLETED|FAILED|CANCELLED|TIMEOUT) break ;;
          esac

          sleep "$INTERVAL"
          ELAPSED=$((ELAPSED + INTERVAL))
        done

        # ── Ergebnisse in Task-Results schreiben ──────────────────────
        echo -n "$STATUS" > "$(results.status.path)"

        FINAL=$(curl -sf "${SERVICE_URL}/api/v1/test/status/${RUN_ID}")

        REPORT=$(echo "$FINAL" | jq -r '.reportUrls.allure // ""')
        echo -n "$REPORT" > "$(results.report-url.path)"

        JIRA=$(echo "$FINAL" | jq -r '.jiraTicketKey // ""')
        echo -n "$JIRA" > "$(results.jira-ticket.path)"

        # ── Zusammenfassung ───────────────────────────────────────────
        echo ""
        echo "══════════════════════════════════════════"
        echo " Test-Run Ergebnis"
        echo "══════════════════════════════════════════"
        echo " Status:        $STATUS"
        echo " Allure-Report: ${SERVICE_URL}${REPORT}"
        [ -n "$JIRA" ] && echo " Jira-Ticket:   $JIRA"
        echo "══════════════════════════════════════════"

        # ── Pipeline abbrechen bei Test-Fehler ────────────────────────
        if [ "$STATUS" = "FAILED" ]; then
          echo "✗ Tests fehlgeschlagen — Pipeline wird abgebrochen."
          exit 1
        fi

        echo "✓ Tests erfolgreich abgeschlossen."
```

---

### Pipeline-Einbindung (nach Deploy)

Der Task wird nach dem Deploy-Schritt eingebunden. Status und Report-URL stehen als Pipeline-Results zur Verfügung.

```yaml
apiVersion: tekton.dev/v1
kind: Pipeline
metadata:
  name: deploy-and-test
spec:
  params:
    - name: cucumber-service-url
      default: "http://cucumber-test-service:8080"
    - name: test-tags
      default: "@SmokeTest"
    - name: environment
      default: "dev"
    - name: project-key
      default: "PROJ"

  tasks:
    # ... bestehende Build- und Deploy-Tasks ...

    - name: smoke-tests
      taskRef:
        name: cucumber-test-runner
      runAfter:
        - deploy                          # ← nach dem Deploy-Task ausführen
      params:
        - name: service-url
          value: "$(params.cucumber-service-url)"
        - name: tags
          value: "$(params.test-tags)"
        - name: environment
          value: "$(params.environment)"
        - name: project-key
          value: "$(params.project-key)"
        - name: timeout-seconds
          value: "900"

  results:
    - name: test-report-url
      value: "$(tasks.smoke-tests.results.report-url)"
    - name: jira-ticket
      value: "$(tasks.smoke-tests.results.jira-ticket)"
```

**PipelineRun (manueller Trigger):**

```yaml
apiVersion: tekton.dev/v1
kind: PipelineRun
metadata:
  generateName: deploy-and-test-run-
spec:
  pipelineRef:
    name: deploy-and-test
  params:
    - name: cucumber-service-url
      value: "http://cucumber-test-service:8080"
    - name: test-tags
      value: "@SmokeTest"
    - name: environment
      value: "staging"
    - name: project-key
      value: "PROJ"
```

---

## Empfehlung – Was wann nutzen?

| Szenario | Empfehlung |
|---|---|
| Automatische Smoke-Tests nach jedem Deploy | **Tekton Task** |
| Regression-Tests nach Release-Branch-Merge | **Tekton Task** |
| Schneller manueller Check während Entwicklung | **curl-Snippet** |
| Debugging eines fehlgeschlagenen Runs | **curl-Snippet** |
| Tests aus Anwendungscode heraus triggern | Nur in Ausnahmefällen — bevorzugt Tekton |

> **Hinweis:** Der `cucumber-test-runner` Task gehört als `tekton/tasks/cucumber-test-runner.yaml`
> ins Repository des **aufrufenden Projekts**. Er benötigt ausschließlich die Service-URL als
> Konfigurationsparameter — keine Abhängigkeit zum Test-Service-Repository.
