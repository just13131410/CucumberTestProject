# Zephyr Scale Integration – Postman Collection

Bildet den Ablauf nach, den `ZephyrScaleService`/`ZephyrScaleClient`/`JiraClient` im
**Cucumber Test Service** nach jedem Testlauf gegen **Zephyr Scale Server (ATM v1)** und
**Jira** ausführen: Testfälle aus einem Template-Testrun klonen, neuen Testrun anlegen,
Ergebnisse hochladen, bei Fehlschlag ein Jira-Ticket erstellen.

> Deckt die **Fremd-APIs** (Zephyr Scale Server, Jira) ab, gegen die der Cucumber Test
> Service selbst als Client spricht – nicht die eigene Service-API (siehe dafür
> `CucumberTestService.postman_collection.json`).

Verifiziert gegen die offizielle RAML-Spezifikation:
`https://support.smartbear.com/zephyr-scale-server/api-docs/v1/kanoah/api.raml`.

## Dateien

| Datei | Zweck |
|---|---|
| `ZephyrScaleIntegration.postman_collection.json` | Die Collection (6 Requests in 3 Ordnern) |
| `ZephyrScaleIntegration.postman_environment.json` | Environment mit Ziel-URL + Credentials + Test-Parametern |

## Import

1. In Postman: **Import** → beide JSON-Dateien auswählen.
2. Rechts oben die Environment **Zephyr Scale Integration** wählen.
3. In der Environment `baseUrl`, `zephyrUsername`, `zephyrApiToken`, `projectKey`,
   `templateTestRunKey`, `resultFolder` auf die echten Werte eures Jira/Zephyr-Systems setzen
   (entsprechen 1:1 `zephyr.base-url` / `zephyr.username` / `zephyr.api-token` /
   `zephyr.default-project-key` / `zephyr.template-test-run-key` / `zephyr.result-folder`
   in der App-Konfiguration).

## Ablauf

0. **Whoami (Auth-Check)** – optional zuerst ausführen, um Basic-Auth-Probleme von
   Zephyr-Contract-Problemen zu unterscheiden (Standard-Jira-Endpunkt, kein Zephyr).
1. **Get Template Testrun** senden. Die Antwort-Testfälle werden per Test-Skript
   automatisch als JSON in `{{clonedItems}}` gespeichert.
2. **Create Testrun** verwendet `{{clonedItems}}` sowie `{{resultFolder}}` (String-Pfad,
   muss in Zephyr bereits existieren) und speichert den neuen Testrun-Key automatisch in
   `{{testRunKey}}`.
3. **Upload Test Results** und **Get Created Testrun** verwenden dieses `{{testRunKey}}`
   automatisch.
4. **Create Jira Ticket** (Ordner 2) simuliert den Fehlschlagsfall – im echten Service nur
   ausgelöst, wenn `jira.enabled=true` und der Testlauf fehlgeschlagen ist.

Am einfachsten der Reihe nach über den **Collection Runner** ausführen (Ordner 1, dann
optional Ordner 2).

## Variablen

| Variable | Ebene | Bedeutung |
|---|---|---|
| `baseUrl` | Environment | Jira/Zephyr-Basis-URL (`zephyr.base-url`) |
| `zephyrUsername` | Environment | Basic-Auth-Username (`zephyr.username`) |
| `zephyrApiToken` | Environment (secret) | Basic-Auth-Token (`zephyr.api-token`) |
| `projectKey` | Environment | Jira-Projekt-Key (`zephyr.default-project-key`) |
| `templateTestRunKey` | Environment | Testrun, dessen Testfälle geklont werden (`zephyr.template-test-run-key`) |
| `resultFolder` | Environment | Zephyr-Folder-Pfad als String, z. B. `/Testautomation/Smoketest` (`zephyr.result-folder`) |
| `jiraIssueType` | Environment | Jira-Issue-Typ (`jira.issue-type`) |
| `jiraAssigneeAccountId` | Environment | Jira-Assignee (`jira.default-assignee-account-id`) |
| `clonedItems` | Collection | automatisch aus *Get Template Testrun* |
| `testRunKey` | Collection | automatisch aus *Create Testrun* |

## Wichtig: `folder` ist ein String-Pfad, kein Objekt

`folder` im `POST /testrun`-Body ist laut RAML-Schema ein reiner **String** (z. B.
`"/Testautomation/Smoketest"`), keine numerische ID und kein verschachteltes Objekt. Der
Pfad muss in Zephyr **bereits existieren** – die Collection legt keinen Folder an
(`GET /folder` existiert in der Zephyr Scale Server API v1 nicht, nur `POST`/`PUT`, die
der Service bewusst nicht nutzt).

## Bekannte Fehlerquellen (403/500)

- **403 mit HTML-Body:** meist ein vorgeschalteter Reverse-Proxy/WAF, nicht Zephyr selbst
  – oft ausgelöst durch doppelte Slashes in der URL (`baseUrl` mit trailing `/`).
- **500 ohne Response-Body:** deutet auf einen ungültigen `folder`-Pfad oder ein Problem
  serverseitig in Zephyr hin, nicht auf einen Auth-Fehler (Auth-Fehler liefern 401/403).
- Zuerst **0. Whoami** ausführen: `200` = Credentials sind korrekt, Fehler liegt dann im
  Zephyr-Contract oder am Proxy.

## Enthaltene Endpunkte

**Diagnose:** `GET /rest/api/2/myself`

**Zephyr Scale (ATM v1):** `GET /rest/atm/1.0/testrun/{key}` ·
`POST /rest/atm/1.0/testrun` · `POST /rest/atm/1.0/testrun/{key}/testresults`

**Jira:** `POST /rest/api/2/issue`
