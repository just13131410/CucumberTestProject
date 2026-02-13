# Testkonzept – Backend-Verbund und Frontend Logviewer

| Version | Datum      | Autor         | Beschreibung             |
|---------|------------|---------------|--------------------------|
| 1.0     | 13.02.2026 | Testteam      | Initiale Erstellung      |

---

## Inhaltsverzeichnis

1. [Testobjekte und Architektur](#1-testobjekte-und-architektur)
2. [Testeingangskriterien](#2-testeingangskriterien)
3. [Testausgangskriterien](#3-testausgangskriterien)
4. [Zu testende Eigenschaften](#4-zu-testende-eigenschaften)
5. [Testansatz und Aufgabenverteilung](#5-testansatz-und-aufgabenverteilung)
6. [Backend-Test mit Umgebungskonzept](#6-backend-test-mit-umgebungskonzept)
7. [Frontend-Test mit Umgebungskonzept](#7-frontend-test-mit-umgebungskonzept)
8. [Testdatenmanagement](#8-testdatenmanagement)
9. [Testreporting](#9-testreporting)
10. [Testfallermittlung](#10-testfallermittlung)
11. [Best Practices fuer API-Tests](#11-best-practices-fuer-api-tests)
12. [CI/CD-Pipeline-Architektur](#12-cicd-pipeline-architektur)
13. [Fehlermanagement](#13-fehlermanagement)

---

## 1. Testobjekte und Architektur

### 1.1 Testobjekte

Die zu testenden Systeme (System Under Test, SUT) sind:

| Testobjekt               | Beschreibung                                                          | Typ        |
|--------------------------|-----------------------------------------------------------------------|------------|
| **Backend-Verbund**      | Mehrere zusammenwirkende Backend-Services (Microservice-Architektur)  | Backend    |
| **Frontend Logviewer**   | Webbasierte Oberflaeche zur Anzeige und Analyse von Log-Daten        | Frontend   |

### 1.2 Testinfrastruktur – Cucumber Test Service

Die automatisierten Tests werden nicht direkt in den Projekten der Testobjekte ausgefuehrt, sondern zentral ueber den **Cucumber Test Service** gesteuert. Dieser ist ein eigenstaendiger Spring-Boot-3-Microservice (Java 17), der als Testinfrastruktur in OpenShift betrieben wird.

**Funktionsweise:** Die CI/CD-Pipelines der Testobjekte (Backend-Verbund, Logviewer) rufen nach dem Deployment den Cucumber Test Service ueber seine REST-API auf. Der Service fuehrt die im Repository hinterlegten Cucumber-Tests (`src/main/resources/features`) gegen die deployten Testobjekte aus und stellt die Ergebnisse bereit.

### 1.3 Gesamtarchitektur

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  OpenShift Cluster                                                               │
│                                                                                 │
│  ┌──────────────────────────────────────────────┐                               │
│  │  Testobjekte (SUT)                            │                               │
│  │                                              │                               │
│  │  ┌──────────────────────────────────────┐    │                               │
│  │  │  Backend-Verbund                      │    │                               │
│  │  │  ┌────────┐ ┌────────┐ ┌────────┐   │    │                               │
│  │  │  │Service │ │Service │ │Service │   │    │                               │
│  │  │  │   A    │ │   B    │ │   C    │   │    │                               │
│  │  │  └───┬────┘ └───┬────┘ └───┬────┘   │    │                               │
│  │  │      └─────┬─────┘─────────┘         │    │                               │
│  │  │            │  REST APIs              │    │                               │
│  │  └────────────┼─────────────────────────┘    │                               │
│  │               │                              │                               │
│  │  ┌────────────┼─────────────────────────┐    │                               │
│  │  │  Frontend Logviewer                   │    │                               │
│  │  │            │                          │    │                               │
│  │  │  ┌────────┴────────┐                 │    │                               │
│  │  │  │  Web-Anwendung   │  ◄── Playwright │    │                               │
│  │  │  │  (Log-Anzeige,   │      greift     │    │                               │
│  │  │  │   Filterung,     │      hierauf zu │    │                               │
│  │  │  │   Analyse)       │                 │    │                               │
│  │  │  └─────────────────┘                 │    │                               │
│  │  └──────────────────────────────────────┘    │                               │
│  └──────────────────────────────────────────────┘                               │
│                          ▲               ▲                                       │
│                          │               │                                       │
│                    REST Assured      Playwright                                  │
│                    (API-Tests)      (UI-Tests)                                   │
│                          │               │                                       │
│  ┌───────────────────────┴───────────────┴───────────────────────────┐          │
│  │  Testinfrastruktur: Cucumber Test Service                         │          │
│  │  Namespace: test-automation                                       │          │
│  │                                                                   │          │
│  │  ┌─────────────────────────────────────────────────────────────┐  │          │
│  │  │  Pod: cucumber-test-service                                 │  │          │
│  │  │                                                             │  │          │
│  │  │  ┌───────────────────────┐   ┌──────────────────────────┐  │  │          │
│  │  │  │  REST API (Port 8080) │   │  Actuator (Port 8081)    │  │  │          │
│  │  │  │  /api/v1/test/*       │   │  /actuator/health        │  │  │          │
│  │  │  │                       │   │  /actuator/prometheus     │  │  │          │
│  │  │  │  ◄── Aufgerufen von   │   └──────────────────────────┘  │  │          │
│  │  │  │      CI/CD-Pipelines  │                                 │  │          │
│  │  │  │      der Testobjekte  │                                 │  │          │
│  │  │  └───────────┬───────────┘                                 │  │          │
│  │  │              │                                             │  │          │
│  │  │              ▼                                             │  │          │
│  │  │  ┌───────────────────────┐                                 │  │          │
│  │  │  │  CucumberRunner       │                                 │  │          │
│  │  │  │  (async, max 5 Runs)  │                                 │  │          │
│  │  │  │                       │                                 │  │          │
│  │  │  │  Feature-Files aus    │                                 │  │          │
│  │  │  │  src/main/resources/  │                                 │  │          │
│  │  │  │  features/            │                                 │  │          │
│  │  │  │                       │                                 │  │          │
│  │  │  │  ├─► REST Assured ────┼──► Backend-Verbund (SUT)        │  │          │
│  │  │  │  ├─► Playwright ──────┼──► Logviewer (SUT)              │  │          │
│  │  │  │  └─► axe-core ────────┼──► Logviewer Barrierefreiheit   │  │          │
│  │  │  └───────────────────────┘                                 │  │          │
│  │  │                                                             │  │          │
│  │  │  ┌──────────────────┐                                      │  │          │
│  │  │  │  PVC: 10 Gi       │                                      │  │          │
│  │  │  │  /app/test-results│  ◄── Allure, Cucumber, Axe Reports  │  │          │
│  │  │  └──────────────────┘                                      │  │          │
│  │  └─────────────────────────────────────────────────────────────┘  │          │
│  │                                                                   │          │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐    │          │
│  │  │  ConfigMap    │    │  Secret      │    │  Route (TLS)     │    │          │
│  │  │  SUT-URLs     │    │  Zephyr-Token│    │  HTTPS-Zugang    │    │          │
│  │  │  Browser-Typ  │    │  Jira-Token  │    │                  │    │          │
│  │  └──────────────┘    └──────────────┘    └──────────────────┘    │          │
│  └───────────────────────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 1.4 Ablauf eines automatisierten Testlaufs

```
CI/CD-Pipeline des Testobjekts (z. B. Backend-Verbund)
       │
       │  1. Build & Deploy des Testobjekts auf Zielumgebung
       │
       ▼
POST /api/v1/test/execute
  {
    "environment": "dev",
    "tags": ["@Backend", "@SmokeTest"],
    "browser": "chromium"
  }
       │
       │  2. Cucumber Test Service nimmt den Auftrag an (QUEUED)
       │
       ▼
GET /api/v1/test/status/{runId}    ◄── 3. Pipeline pollt Status
       │
       │  4. Tests laufen gegen das deployete Testobjekt
       │     (REST Assured → Backend-APIs, Playwright → Logviewer)
       │
       ▼
Status: COMPLETED / FAILED
       │
       │  5. Ergebnisse abrufbar
       │
       ├──► /reports/{runId}/allure-report/index.html
       ├──► /reports/{runId}/axe-result/index.html
       └──► /api/v1/test/report/{runId}  (Cucumber JSON)
                │
                │  6. Upload nach Zephyr Scale
                ▼
         Zephyr Scale (Jira)
```

### 1.5 Technologie-Stack

| Komponente                    | Technologie                  | Version  | Rolle                        |
|-------------------------------|------------------------------|----------|------------------------------|
| Testinfrastruktur-Framework   | Spring Boot                  | 3.2.2    | Cucumber Test Service        |
| BDD-Framework                 | Cucumber                     | 7.15.0   | Feature-Files und Glue-Code  |
| Test-Runner                   | JUnit 5 Platform Suite       | 5.10.1   | Testausfuehrung              |
| UI-Automatisierung            | Playwright                   | 1.41.0   | Tests gegen Logviewer        |
| API-Automatisierung           | REST Assured                 | 5.4.0    | Tests gegen Backend-Verbund  |
| Barrierefreiheit              | axe-core/playwright          | –        | WCAG-Pruefung des Logviewers |
| Reporting                     | Allure                       | 2.25.0   | Testergebnis-Reports         |
| Container-Basis               | Red Hat UBI9                 | 9.3      | Container-Image              |
| Orchestrierung                | OpenShift / Kubernetes       | –        | Deployment aller Systeme     |
| CI/CD-Pipeline                | Tekton Pipelines             | –        | Build, Deploy, Test-Trigger  |
| Testmanagement                | Zephyr Scale (geplant)       | –        | Testfallverwaltung, Reporting|

### 1.6 Schichtenmodell der Tests

```
┌────────────────────────────────────────────────────────────────────────┐
│  Schicht 1 – Unit-Tests (pro Testobjekt)                               │
│  Im jeweiligen Projekt des Testobjekts (Backend-Verbund, Logviewer)    │
│  JUnit / Jest / projektspezifisch                                      │
│  Ziel: Einzelne Klassen/Komponenten isoliert pruefen                   │
│  Ausfuehrung: Bei jedem Build, lokal + CI/CD                           │
├────────────────────────────────────────────────────────────────────────┤
│  Schicht 2 – Automatisierte Integrationstests                          │
│  Via Cucumber Test Service (REST-API-Aufruf aus CI/CD-Pipeline)        │
│  Cucumber + Playwright (Logviewer) / REST Assured (Backend-Verbund)    │
│  Ziel: Zusammenspiel der Komponenten und Schnittstellen pruefen        │
│  Ausfuehrung: CI/CD-Pipeline bei Push auf develop (Backend)            │
│               CI/CD-Pipeline bei Push auf Feature-/develop-Branch (FE) │
├────────────────────────────────────────────────────────────────────────┤
│  Schicht 3 – Manuelle Tests                                            │
│  Integrations- und Fehlerhandling-Tests                                │
│  Gesamtintegrationstest (End-to-End ueber alle Systeme)                │
│  Ziel: Abdeckung von Szenarien, die nicht automatisierbar sind         │
│  Ausfuehrung: Auf Testumgebung, vor Release                           │
├────────────────────────────────────────────────────────────────────────┤
│  Schicht 4 – Externe Tests (Abnahme)                                   │
│  Last-/Performance-Test, Penetrationstest                              │
│  Manueller Barrierefreiheitstest (sehende + blinde Nutzer)             │
│  Ziel: Nichtfunktionale Qualitaet sicherstellen                       │
│  Ausfuehrung: Auf Abnahmeumgebung, vor Produktivsetzung               │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Testeingangskriterien

Bevor eine Teststufe begonnen wird, muessen folgende Kriterien erfuellt sein:

### 2.1 Unit-Tests (pro Testobjekt)
- [ ] Code des Testobjekts kompiliert fehlerfrei
- [ ] Alle benoetigten Abhaengigkeiten sind aufgeloest
- [ ] Entwickler hat lokale Unit-Tests durchgefuehrt

### 2.2 Automatisierte Integrationstests (via Cucumber Test Service)
- [ ] Unit-Tests des Testobjekts sind zu 100 % bestanden
- [ ] Testobjekt ist auf der Zielumgebung deployed und erreichbar
- [ ] Cucumber Test Service ist verfuegbar und healthy (`/actuator/health`)
- [ ] Feature-Files und Step-Definitions fuer das Testobjekt sind aktuell
- [ ] Testdaten sind in definiertem Ausgangszustand
- [ ] Ziel-URLs (Backend-Verbund / Logviewer) sind in der ConfigMap hinterlegt

### 2.3 Manuelle Tests (Integration + Fehlerhandling)
- [ ] Automatisierte Integrationstests sind bestanden
- [ ] Testumgebung mit aktueller Version aller Testobjekte deployt
- [ ] Testfaelle sind im Testmanagement-Tool dokumentiert
- [ ] Zugang zur Testumgebung fuer alle Tester vorhanden

### 2.4 Gesamtintegrationstest
- [ ] Alle Einzelkomponenten-Tests (Backend-Verbund + Logviewer) sind bestanden
- [ ] Gesamtumgebung (alle Services des Backend-Verbunds + Logviewer) ist aufgebaut
- [ ] End-to-End-Testfaelle sind abgestimmt und freigegeben
- [ ] Schnittstellen zu Drittsystemen sind verfuegbar oder gemockt

### 2.5 Externe Tests (Abnahme)
- [ ] Gesamtintegrationstest bestanden
- [ ] Abnahmeumgebung ist produktionsnah konfiguriert
- [ ] Vertrag/Leistungsschein mit externem Testteam liegt vor
- [ ] Testinfrastruktur fuer L/P-Test bereitgestellt

---

## 3. Testausgangskriterien

### 3.1 Unit-Tests (pro Testobjekt)
- [x] 100 % der Unit-Tests bestanden
- [x] Code-Coverage >= 70 % (projektspezifisch)
- [x] Keine kritischen oder hohen Findings in statischer Analyse

### 3.2 Automatisierte Integrationstests
- [x] 100 % der Smoke-Tests bestanden
- [x] >= 95 % der Regressionstests bestanden
- [x] Alle Allure- und Cucumber-Reports generiert und archiviert
- [x] Barrierefreiheits-Scans (Logviewer) zeigen keine kritischen Verstoesse

### 3.3 Manuelle Tests
- [x] Alle geplanten Testfaelle durchgefuehrt
- [x] Keine offenen Fehler mit Prioritaet "Kritisch" oder "Hoch"
- [x] Fehlerhandling-Szenarien dokumentiert und geprueft
- [x] Testergebnisse im Testmanagement-Tool erfasst

### 3.4 Gesamtintegrationstest
- [x] Alle End-to-End-Szenarien erfolgreich durchlaufen
- [x] Backend-Verbund und Logviewer funktionieren im Zusammenspiel korrekt
- [x] Keine Regressionen gegenueber letztem Release

### 3.5 Externe Tests (Abnahme)
- [x] L/P-Test: Definierte Lastgrenzen werden eingehalten
- [x] Pentest: Keine kritischen oder hohen Schwachstellen offen
- [x] Barrierefreiheit: WCAG 2.1 Level AA Konformitaet (automatisiert + manuell)
- [x] Abnahmebericht liegt vor und ist unterschrieben

---

## 4. Zu testende Eigenschaften

### 4.1 Funktionale Eigenschaften – Backend-Verbund

| ID   | Eigenschaft                                   | Teststufe              | Art         |
|------|-----------------------------------------------|------------------------|-------------|
| B-01 | REST-API-Endpunkte aller Backend-Services     | Unit + Integration     | Automatisch |
| B-02 | Service-zu-Service-Kommunikation              | Integration            | Automatisch |
| B-03 | Datenverarbeitung und -persistenz             | Integration            | Automatisch |
| B-04 | Authentifizierung und Autorisierung           | Integration + Manuell  | Gemischt    |
| B-05 | Fehlerbehandlung und Resilience               | Integration + Manuell  | Gemischt    |
| B-06 | Log-Erzeugung und -Weiterleitung              | Integration            | Automatisch |
| B-07 | Konfigurationsmanagement (Profile/Umgebungen) | Manuell                | Manuell     |
| B-08 | Gesamtintegration aller Backend-Services      | Gesamtintegrationstest | Manuell     |

### 4.2 Funktionale Eigenschaften – Frontend Logviewer

| ID   | Eigenschaft                                   | Teststufe              | Art         |
|------|-----------------------------------------------|------------------------|-------------|
| L-01 | Log-Anzeige (Laden, Filtern, Sortieren)       | Integration            | Automatisch |
| L-02 | Suchfunktion und Filterlogik                  | Integration            | Automatisch |
| L-03 | Benutzeranmeldung / Session-Management        | Integration + Manuell  | Gemischt    |
| L-04 | Echtzeit-Aktualisierung der Log-Daten         | Manuell                | Manuell     |
| L-05 | Export-Funktionen                              | Integration + Manuell  | Gemischt    |
| L-06 | Fehlerdarstellung und Fehlermeldungen          | Manuell                | Manuell     |
| L-07 | Barrierefreiheitspruefung (axe-core, WCAG 2.1)| Integration            | Automatisch |
| L-08 | Gesamtintegration mit Backend-Verbund          | Gesamtintegrationstest | Manuell     |

### 4.3 Nichtfunktionale Eigenschaften (beide Testobjekte)

| ID    | Eigenschaft                        | Testobjekt       | Teststufe             | Art         |
|-------|------------------------------------|------------------|-----------------------|-------------|
| NF-01 | Performance / Lastverhalten        | Backend-Verbund  | Extern (L/P)          | Manuell     |
| NF-02 | Performance / Lastverhalten        | Logviewer        | Extern (L/P)          | Manuell     |
| NF-03 | Sicherheit / Penetrationstest      | Beide            | Extern (Pentest)      | Manuell     |
| NF-04 | Barrierefreiheit (WCAG 2.1 AA)     | Logviewer        | Automatisch + Extern  | Gemischt    |
| NF-05 | Container-Stabilitaet (OpenShift)  | Beide            | Integration           | Automatisch |
| NF-06 | Health-/Readiness-Probes           | Beide            | Integration           | Automatisch |

---

## 5. Testansatz und Aufgabenverteilung

### 5.1 Aufgaben der Entwicklung

| Aufgabe                                          | Beschreibung                                                                     |
|--------------------------------------------------|----------------------------------------------------------------------------------|
| Unit-Tests im jeweiligen Projekt                 | Jede neue Klasse/Methode im Testobjekt wird mit Unit-Tests abgedeckt            |
| Cucumber-Feature-Files erstellen                 | Szenarien in Gherkin fuer Backend-APIs und Logviewer-Workflows                   |
| Step-Definitions implementieren                  | Glue-Code im Cucumber Test Service (`org.example.steps`)                         |
| Page Objects fuer Logviewer pflegen              | Playwright-basierte Page Objects im Cucumber Test Service (`org.example.pages`)  |
| API-Steps fuer Backend-Verbund pflegen           | REST-Assured-Steps im Cucumber Test Service                                      |
| Lokale Tests vor Push ausfuehren                 | Unit-Tests im Testobjekt muessen bestehen                                        |
| Code-Reviews mit Testfokus                       | Reviewer prueft auch Testabdeckung und Testqualitaet                             |
| Bug-Fixing nach Testfeedback                     | Priorisierte Behebung gemaess Fehlermanagement (Abschnitt 13)                   |

### 5.2 Aufgaben des internen Testteams

| Aufgabe                                          | Beschreibung                                                                     |
|--------------------------------------------------|----------------------------------------------------------------------------------|
| Testfallermittlung und -dokumentation            | Ableitung aus Fachkonzepten, User Stories, Architekturbildern                    |
| Feature-Files reviewen und ergaenzen             | Qualitaetssicherung der automatisierten Testszenarien                            |
| Manuelle Integrationstests                       | Fehlerhandling, Randfaelle, komplexe Workflows im Logviewer und Backend          |
| Gesamtintegrationstest                           | End-to-End-Pruefung: Backend-Verbund + Logviewer im Zusammenspiel               |
| Testdatenmanagement                              | Bereitstellung und Pflege der Testdaten (siehe Abschnitt 8)                      |
| Testreporting und -dokumentation                 | Auswertung der Allure-/Cucumber-/Axe-Reports, manuelle Protokollierung           |
| Cucumber Test Service ueberwachen                | Sicherstellung der Verfuegbarkeit, Monitoring der Testlaufe                      |
| Fehlermanagement                                 | Erfassung, Nachverfolgung und Verifikation von Fehlern                           |

### 5.3 Aufgaben der externen Testteams

| Team / Dienstleister         | Aufgabe                                                     | Testobjekt          | Zeitpunkt            | Ergebnis                        |
|------------------------------|-------------------------------------------------------------|---------------------|----------------------|---------------------------------|
| **L/P-Testteam**             | Last- und Performancetest auf Abnahme                       | Backend-Verbund     | Vor Produktivsetzung | L/P-Testbericht mit Metriken    |
| **L/P-Testteam**             | Last- und Performancetest auf Abnahme                       | Logviewer           | Vor Produktivsetzung | L/P-Testbericht mit Metriken    |
| **Barrierefreiheits-Tester** | Manueller WCAG-2.1-Test fuer sehende User                  | Logviewer           | Vor Produktivsetzung | Pruefbericht mit Findings       |
| **Barrierefreiheits-Tester** | Manueller Test fuer nicht-sehende User (Screenreader, Tastaturnavigation) | Logviewer | Vor Produktivsetzung | Pruefbericht mit Findings |
| **Pentest-Dienstleister**    | Penetrationstest auf Abnahmeumgebung                        | Beide               | Vor Produktivsetzung | Pentest-Bericht (OWASP Top 10)  |

**Hinweis zu externen Tests:** Jeder externe Test erfolgt auf der Abnahmeumgebung mit produktionsnaher Konfiguration. Die Ergebnisse werden als gesonderter Leistungsschein/Pruefbericht abgenommen. Kritische und hohe Findings muessen vor Go-Live behoben sein.

---

## 6. Backend-Test mit Umgebungskonzept

### 6.1 Teststrategie Backend-Verbund

```
┌──────────────┐     ┌──────────────────────┐     ┌──────────────┐
│  Entwickler-  │     │  Develop-Branch CI    │     │  Test-        │
│  Arbeitsplatz │────►│                      │────►│  umgebung     │
│              │     │  1. Build & Deploy    │     │              │
│  Unit-Tests  │     │     Backend-Verbund   │     │  Manueller    │
│  (lokal)     │     │  2. POST an Cucumber  │     │  Integrations-│
│              │     │     Test Service      │     │  und Fehler-  │
│              │     │  3. Ergebnisse pruefen│     │  handling-Test │
└──────────────┘     └──────────────────────┘     └──────────────┘
     Lokal              Automatisch                   Manuell
```

### 6.2 Umgebungskonzept

| Umgebung             | Trigger                        | Tests                                                              | SUT-Profil |
|----------------------|--------------------------------|---------------------------------------------------------------------|------------|
| **Lokal**            | Manuell durch Entwickler       | Unit-Tests des Backend-Verbunds                                     | `dev`      |
| **CI (develop)**     | Push auf `develop`-Branch      | Unit-Tests + automatisierte Integrationstests via Cucumber Test Service | `dev`   |
| **Testumgebung**     | Deployment nach CI-Erfolg      | Manueller Integrations- und Fehlerhandling-Test                     | `staging`  |
| **Abnahmeumgebung**  | Manuelles Deployment           | Externe Tests (L/P, Pentest)                                        | `prod`     |

### 6.3 Automatisierte Backend-Tests (via Cucumber Test Service)

Die CI/CD-Pipeline des Backend-Verbunds ruft nach erfolgreichem Deployment den Cucumber Test Service auf:

```
Push auf develop
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Tekton Pipeline: backend-verbund-pipeline                       │
│                                                                 │
│  ┌────────┐  ┌────────┐  ┌─────────────┐  ┌────────────────┐  │
│  │ build  │  │ deploy │  │ trigger     │  │ wait & collect │  │
│  │ backend│─►│ to     │─►│ cucumber    │─►│ results        │  │
│  │ verbund│  │ target │  │ test service│  │ (poll status)  │  │
│  └────────┘  │ env    │  │ POST /api/  │  └───────┬────────┘  │
│              └────────┘  │ v1/test/    │          │            │
│                          │ execute     │          ▼            │
│                          │ tags:       │  ┌────────────────┐  │
│                          │ [@Backend]  │  │ upload to      │  │
│                          └─────────────┘  │ Zephyr Scale   │  │
│                                           └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Getestete Szenarien (Beispiele):**
- REST-API-Endpunkte aller Backend-Services (Statuscodes, Response-Body, JSON-Schema)
- Service-zu-Service-Kommunikation (Datenfluss zwischen Services A, B, C)
- Authentifizierung und Autorisierung (gueltige/ungueltige Tokens)
- Fehlerszenarien (ungueltige Requests, Timeout, nicht erreichbare Services)

### 6.4 Manueller Backend-Test (Testumgebung)

| Testbereich              | Beispiele                                                                    |
|--------------------------|------------------------------------------------------------------------------|
| Fehlerhandling           | Ungueltige Eingaben, Netzwerkfehler, Service-Ausfaelle im Verbund           |
| Grenzwertanalyse         | Maximale Payload-Groessen, viele gleichzeitige Requests, leere Eingaben      |
| Konfiguration            | Profilwechsel (dev/staging/prod), Umgebungsvariablen, Feature-Toggles        |
| Recovery                 | Verhalten nach Container-Neustart, Datenbank-Failover                        |
| Monitoring               | Health-Endpunkte, Metriken unter Last, Log-Erzeugung pruefen                |
| Datenintegritaet         | Konsistenz der Daten ueber mehrere Services hinweg                           |

---

## 7. Frontend-Test mit Umgebungskonzept

### 7.1 Teststrategie Frontend Logviewer

```
┌──────────────┐    ┌───────────────────┐    ┌──────────────────┐    ┌──────────────┐
│  Entwickler-  │    │  Feature-Branch    │    │  Develop-Branch  │    │  Test-        │
│  Arbeitsplatz │───►│  CI (dynamisch)    │───►│  CI (fest)       │───►│  umgebung     │
│              │    │                   │    │                  │    │              │
│  Unit-Tests  │    │ 1. Build & Deploy │    │ 1. Build & Deploy│    │  Manueller    │
│  (lokal)     │    │    Logviewer in   │    │    Logviewer auf │    │  Test         │
│              │    │    dynamischen NS │    │    feste Test-Env│    │              │
│              │    │ 2. Smoke-Tests    │    │ 2. Regressions-  │    │              │
│              │    │    via Cucumber   │    │    tests via     │    │              │
│              │    │    Test Service   │    │    Cucumber      │    │              │
│              │    │                   │    │    Test Service  │    │              │
└──────────────┘    └───────────────────┘    └──────────────────┘    └──────────────┘
     Lokal             Automatisch              Automatisch             Manuell
```

### 7.2 Umgebungskonzept

| Umgebung                    | Trigger                        | Tests                                                    | Lebensdauer        |
|-----------------------------|--------------------------------|----------------------------------------------------------|--------------------|
| **Lokal**                   | Manuell durch Entwickler       | Unit-Tests, einzelne Szenarien / Debugging               | Dauerhaft          |
| **Dynamisch (Feature)**     | Push auf Feature-Branch        | Smoke-Tests (`@SmokeTest`) via Cucumber Test Service     | Bis Branch gemergt |
| **Fest (Develop)**          | Push auf `develop`-Branch      | Regression (`@regression`) via Cucumber Test Service     | Dauerhaft          |
| **Testumgebung**            | Deployment nach CI-Erfolg      | Manueller Integrations- und Fehlerhandling-Test          | Dauerhaft          |
| **Abnahmeumgebung**         | Manuelles Deployment           | Externe Tests (L/P, Pentest, Barrierefreiheit)           | Dauerhaft          |

### 7.3 Dynamische Umgebungen (Feature-Branches)

Bei jedem Push auf einen Feature-Branch des Logviewers wird automatisch:
1. Ein Container-Image des Logviewers gebaut
2. Ein eigener Namespace oder Deployment in OpenShift erstellt
3. Eine temporaere Route zugewiesen
4. Der Cucumber Test Service aufgerufen mit der dynamischen URL als Ziel
5. Smoke-Tests (`@SmokeTest`) gegen die dynamische Umgebung ausgefuehrt
6. Bei Merge/Loesung des Branches wird die Umgebung abgebaut

```
Feature-Branch Push (Logviewer)
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│  Tekton Pipeline: logviewer-feature-pipeline                    │
│                                                                │
│  ┌────────┐  ┌──────────┐  ┌───────────┐  ┌────────────────┐ │
│  │ build  │  │ deploy   │  │ trigger   │  │ collect        │ │
│  │ log-   │─►│ to       │─►│ cucumber  │─►│ results &      │ │
│  │ viewer │  │ dynamic  │  │ test svc  │  │ cleanup on     │ │
│  │ image  │  │ NS       │  │ POST with │  │ branch delete  │ │
│  └────────┘  └──────────┘  │ @Smoke +  │  └────────────────┘ │
│                             │ dynamic   │                      │
│                             │ URL       │                      │
│                             └───────────┘                      │
└────────────────────────────────────────────────────────────────┘
```

### 7.4 Automatisierte Frontend-Tests (Cucumber + Playwright via Cucumber Test Service)

**Szenarien (Beispiele):**
- Login und Session-Management (erfolgreiche/fehlgeschlagene Anmeldung)
- Log-Anzeige (Laden, Paginierung, Sortierung)
- Suchfunktion und Filterlogik (nach Zeitraum, Severity, Service)
- Datenexport (CSV, JSON)
- Barrierefreiheitspruefung (axe-core) nach jedem relevanten Seitenaufruf

**Browser-Matrix:**

| Browser  | CI/CD  | Manuell |
|----------|--------|---------|
| Chromium | Ja     | Ja      |
| Firefox  | Optional| Ja     |
| WebKit   | Optional| Ja     |

### 7.5 Manueller Frontend-Test (Testumgebung)

| Testbereich              | Beispiele                                                                    |
|--------------------------|------------------------------------------------------------------------------|
| Visuelle Pruefung        | Layout, Responsiveness, Darstellung der Log-Tabelle bei vielen Eintraegen    |
| Usability                | Benutzerfreundlichkeit der Filter, Navigationsfluesse, Fehlermeldungen       |
| Echtzeit-Updates         | Log-Daten aktualisieren sich automatisch, WebSocket-Verbindungsabbrueche     |
| Browser-Kompatibilitaet  | Chrome, Firefox, Safari, Edge (aktuelle Versionen)                           |
| Mobile Darstellung       | Responsive Design auf gaengigen Viewports                                    |
| Fehlerszenarien          | Backend nicht erreichbar, leere Log-Daten, Timeout bei grossen Datenmengen   |
| Barrierefreiheit (visuell)| Kontraste, Schriftgroessen, Farbblindheit-Simulation                         |

---

## 8. Testdatenmanagement

### 8.1 Grundsaetze

- Testdaten sind **versioniert** und Teil des Cucumber-Test-Service-Repositories (Feature-Files mit Beispieltabellen)
- Jeder Testlauf arbeitet mit **definierten Ausgangsdaten** (keine Abhaengigkeit von Vorgaenger-Laeufen)
- Testdaten fuer den Backend-Verbund werden ueber **Fixtures, Seeding-Skripte oder Mocks** bereitgestellt
- Log-Daten fuer den Logviewer koennen ueber **Test-Log-Generatoren** im Backend erzeugt werden
- Sensible Testdaten (Tokens, Zugangsdaten) werden ausschliesslich ueber **OpenShift Secrets / Vault** verwaltet

### 8.2 Testdatenquellen

| Datenquelle                          | Verwendung                                      | Verwaltung                          |
|--------------------------------------|--------------------------------------------------|--------------------------------------|
| Cucumber-Examples (Feature-Files)    | Eingabedaten fuer UI- und API-Tests              | Git (Cucumber Test Service Repo)     |
| config.properties                    | URLs der Testobjekte, Pfade                      | Git (Umgebungsneutral)               |
| OpenShift ConfigMap                  | Umgebungsspezifische URLs der Testobjekte        | OpenShift (pro Umgebung)             |
| OpenShift Secrets / Vault            | API-Tokens, Zugangsdaten der Testobjekte         | Secret-Management                    |
| JavaFaker                            | Dynamische Testdaten (Namen, Zeitstempel, etc.)  | Zur Laufzeit generiert               |
| Log-Testdaten-Generator              | Vordefinierte Log-Eintraege fuer Logviewer-Tests | Skript / API im Backend-Verbund      |
| Datenbank-Seeding                    | Ausgangszustand der Backend-Datenbanken          | Migrations-Skripte / Fixtures        |

### 8.3 Testdaten-Lebenszyklus

```
Vor dem Test ──► 1. Testobjekte deployen (CI/CD-Pipeline)
               ► 2. Datenbank in Ausgangszustand bringen (Seeding)
               ► 3. Test-Log-Daten generieren (fuer Logviewer-Tests)
                        │
Waehrend des Tests ──► Cucumber Test Service fuehrt Tests aus
                      ► Testdaten werden gelesen / erzeugt / validiert
                        │
Nach dem Test ──► Ergebnisse im PVC gespeichert (isoliert pro RunId)
               ► Optional: Testdaten zuruecksetzen
```

---

## 9. Testreporting

### 9.1 Automatisiertes Reporting (via Cucumber Test Service)

Alle automatisierten Reports werden pro Testlauf (RunId) im Cucumber Test Service erzeugt und sind ueber HTTP abrufbar:

| Report-Typ            | Format      | Erzeugung                              | Zugang                                          |
|-----------------------|-------------|----------------------------------------|--------------------------------------------------|
| **Allure Report**     | HTML        | Automatisch nach Testlauf              | `/reports/{runId}/allure-report/index.html`       |
| **Cucumber Report**   | JSON + HTML | Automatisch nach Testlauf              | `/reports/{runId}/cucumber-reports/`              |
| **Axe Uebersicht**    | HTML        | Automatisch nach jedem Axe-Scan        | `/reports/{runId}/axe-result/index.html`          |
| **Axe Einzelbericht** | JSON + HTML | Pro gepruefter Seite im Logviewer      | `/reports/{runId}/axe-result/{scan}.html`         |
| **JUnit XML**         | XML         | Fuer CI/CD-Integration                 | Im Testlauf-Verzeichnis                           |
| **Screenshots**       | PNG         | Bei Testfehlern + nach Aktionen        | `/reports/{runId}/screenshots/`                   |

### 9.2 Zephyr Scale Integration (geplant)

Nach jedem automatisierten Testlauf werden die Ergebnisse automatisch an Zephyr Scale uebertragen:

```
Testlauf im Cucumber Test Service abgeschlossen
       │
       ▼
Tekton Task: upload-to-zephyr
       │
       ├──► Cucumber JSON aus /api/v1/test/report/{runId} lesen
       ├──► Zephyr Scale REST API aufrufen
       │    POST /testexecutions mit Bearer Token (Vault)
       └──► Testergebnisse sind in Jira sichtbar
```

**Mapping:**
- Cucumber-Tags (`@T-XXXX`) → Zephyr Scale Testfall-IDs
- Cucumber-Szenario-Status → Zephyr Execution-Status (Passed/Failed/Blocked)
- Allure-Report-URL → Verknuepfung im Zephyr-Testlauf
- Testobjekt (Backend-Verbund / Logviewer) → Zephyr-Testplan/Zyklus

### 9.3 Manuelles Reporting

| Aktivitaet                          | Tool                    | Verantwortlich     |
|-------------------------------------|-------------------------|--------------------|
| Manuelle Testergebnisse erfassen    | Zephyr Scale (Jira)     | Testteam           |
| Fehlermeldungen erstellen           | Jira                    | Testteam           |
| Testfortschritt kommunizieren       | Zephyr Scale Dashboard  | Testmanager        |
| Externe Testberichte archivieren    | Confluence / SharePoint | Testmanager        |

---

## 10. Testfallermittlung

### 10.1 Quellen fuer die Testfallermittlung

```
┌───────────────────┐     ┌───────────────────┐     ┌───────────────────┐
│  Fachkonzepte     │     │  User Stories      │     │  Architekturbilder│
│  (funktionale     │     │  (Akzeptanz-       │     │  (Schnittstellen, │
│   Anforderungen   │     │   kriterien)       │     │   Datenfluss      │
│   Backend-Verbund │     │   Logviewer +      │     │   Backend-Verbund │
│   + Logviewer)    │     │   Backend)         │     │   + Logviewer)    │
└────────┬──────────┘     └────────┬──────────┘     └────────┬──────────┘
         │                         │                          │
         └─────────────┬───────────┘──────────────────────────┘
                       │
                       ▼
            ┌─────────────────────┐
            │  Testfallableitung  │
            │                     │
            │  1. Positive Faelle │
            │  2. Negative Faelle │
            │  3. Grenzwerte      │
            │  4. Randfaelle      │
            │  5. Fehlerpfade     │
            └────────┬────────────┘
                     │
         ┌───────────┼───────────────┐
         ▼           ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
│  Automatisiert│ │  Manuell     │ │  Zephyr Scale    │
│  (Cucumber   │ │  (Integr. +  │ │  (alle Testfaelle│
│   Feature-   │ │   Fehler-    │ │   dokumentiert   │
│   Files im   │ │   handling + │ │   und verknuepft)│
│   Cucumber   │ │   Gesamt-    │ │                  │
│   Test Svc)  │ │   integration│ │                  │
└──────────────┘ └──────────────┘ └──────────────────┘
```

### 10.2 Frontend-Testfallermittlung (Logviewer)

| Quelle                     | Ableitungsmethode                                               | Beispiel                                                   |
|----------------------------|-----------------------------------------------------------------|------------------------------------------------------------|
| **Aktuelle Umsetzung**     | Bestehende UI-Workflows analysieren und in Gherkin abbilden     | Log-Tabelle → LogView.feature                              |
| **Fachkonzept**            | Geschaeftsregeln als Szenarien formulieren                      | "Benutzer kann Logs nach Severity filtern"                 |
| **User Stories**           | Akzeptanzkriterien 1:1 in Scenarios uebersetzen                 | AC: "Filterergebnis zeigt nur ERROR-Logs" → Then-Step      |
| **Architekturbilder**      | Seitenuebergaenge und Datenfluss testen                         | Navigation: Login → Dashboard → Log-Detail → Export        |
| **WCAG-Richtlinien**       | Barrierefreiheitsanforderungen als Axe-Scans einbetten          | Farbkontrast, Alt-Texte, Tastaturnavigation, Screenreader  |

### 10.3 Backend-Testfallermittlung (Backend-Verbund)

| Quelle                     | Ableitungsmethode                                               | Beispiel                                                   |
|----------------------------|-----------------------------------------------------------------|------------------------------------------------------------|
| **Aktuelle Umsetzung**     | Vorhandene Endpunkte und Datenmodelle analysieren               | GET /logs → Paginierung, Filterparameter testen            |
| **API-Spezifikation**      | Swagger/OpenAPI-Definition als Grundlage                        | Alle Endpunkte mit gueltigem/ungueltigem Request           |
| **Fachkonzept**            | Geschaeftslogik der einzelnen Services testen                   | "Log-Aggregation fasst Eintraege korrekt zusammen"         |
| **User Stories**           | Akzeptanzkriterien auf API-Ebene uebersetzen                    | "Logs koennen nach Zeitraum abgefragt werden" → GET /logs?from=&to= |
| **Architekturbilder**      | Service-Kommunikation und Fehlerverhalten testen                | Service A → Service B: Daten korrekt weitergeleitet        |

---

## 11. Best Practices fuer API-Tests

### 11.1 Teststruktur (Given-When-Then)

```gherkin
# EMPFOHLEN: Klare Trennung von Vorbedingung, Aktion und Erwartung
@Backend @T-1234
Scenario: Log-Eintraege nach Severity filtern
  Given API Basis-URL des Backend-Verbunds ist gesetzt
  When ich GET an "/api/logs?severity=ERROR" ausfuehre
  Then ist der Statuscode 200
  And enthaelt die Antwort nur Eintraege mit Severity "ERROR"
```

### 11.2 Grundsaetze

| Regel                                    | Beschreibung                                                          |
|------------------------------------------|-----------------------------------------------------------------------|
| **Unabhaengige Tests**                   | Jedes Szenario laeuft isoliert, keine Abhaengigkeit zwischen Tests    |
| **Idempotenz**                           | Tests koennen beliebig oft wiederholt werden ohne Seiteneffekte       |
| **Sprechende Namen**                     | Scenario-Titel beschreibt das erwartete Verhalten                     |
| **Status-Code-Pruefung zuerst**          | Immer zuerst den HTTP-Statuscode pruefen, dann den Body              |
| **Schema-Validierung**                   | JSON-Response gegen Schema validieren (REST Assured JSON Schema)      |
| **Negative Tests**                       | Mindestens 1 negativer Testfall pro Endpunkt (400, 401, 404, 500)    |
| **Allure-Annotations**                   | `@Allure.step` fuer jeden relevanten Schritt im Report               |
| **Request/Response loggen**              | AllureRestAssuredHook haengt HTTP-Details automatisch an              |
| **Timeouts definieren**                  | Explizite Timeouts setzen, nicht auf Defaults verlassen               |
| **Konfigurierbare Basis-URL**            | URLs nie hardcoden, immer ueber ConfigReader / Umgebungsvariable      |
| **Tags fuer Testobjekte**               | `@Backend` vs. `@Logviewer` zur Zuordnung der Tests                  |
| **Tags fuer Testmanagement**            | `@T-XXXX` fuer Zephyr-Scale-Verknuepfung                             |

### 11.3 Anti-Patterns (vermeiden)

| Anti-Pattern                   | Warum problematisch                                     | Besser                                     |
|--------------------------------|---------------------------------------------------------|--------------------------------------------|
| Testabhaengigkeiten            | Test B braucht Ergebnis von Test A                      | Eigenes Setup pro Szenario                 |
| Sleep/Wait                     | Fragil und verlangsamt Tests                            | Polling mit Timeout                        |
| Hardcodierte URLs              | Bricht bei Umgebungswechsel                             | `ConfigReader.get("apiURL")`               |
| Zu breite Assertions           | `body is not null` prueft fast nichts                   | Spezifische Felder und Werte pruefen       |
| Alles in einem Szenario        | Ein Szenario prueft 10 verschiedene Dinge               | Ein Szenario = ein Verhalten               |
| Fehlende Testobjekt-Tags       | Unklar welches SUT getestet wird                        | `@Backend` oder `@Logviewer` immer setzen  |

---

## 12. CI/CD-Pipeline-Architektur

### 12.1 Backend-Verbund-Pipeline (Tekton + OpenShift)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Tekton Pipeline: backend-verbund-pipeline                                       │
│                                                                                 │
│  Trigger: Push auf develop-Branch des Backend-Verbunds                           │
│                                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐  ┌──────────┐    │
│  │  git     │  │  build   │  │  deploy  │  │  trigger      │  │  report  │    │
│  │  clone   │─►│  backend │─►│  to      │─►│  cucumber     │─►│  upload  │    │
│  │  backend │  │  verbund │  │  target  │  │  test service │  │  (Zephyr)│    │
│  │  repo    │  │  image   │  │  env     │  │               │  │          │    │
│  └──────────┘  └──────────┘  └──────────┘  │  POST /api/v1/│  └──────────┘    │
│                                             │  test/execute  │                  │
│                                             │  tags:         │                  │
│                                             │  [@Backend]    │                  │
│                                             │               │                  │
│                                             │  Poll status   │                  │
│                                             │  until done    │                  │
│                                             └───────────────┘                  │
│                                                    │                            │
│                                                    ▼                            │
│                                             ┌──────────────┐                   │
│                                             │  Artefakte:   │                   │
│                                             │  - Allure     │                   │
│                                             │  - Cucumber   │                   │
│                                             │  - JUnit XML  │                   │
│                                             └──────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 12.2 Frontend-Logviewer-Pipeline (Tekton + OpenShift)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Tekton Pipeline: logviewer-pipeline                                             │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │  Trigger A: Push auf Feature-Branch des Logviewers                      │   │
│  │                                                                         │   │
│  │  ┌────────┐  ┌──────────┐  ┌───────────┐  ┌─────────────┐  ┌───────┐  │   │
│  │  │ build  │  │ deploy   │  │ trigger   │  │ poll status │  │ clean │  │   │
│  │  │ log-   │─►│ to       │─►│ cucumber  │─►│ & collect   │─►│ up on │  │   │
│  │  │ viewer │  │ dynamic  │  │ test svc  │  │ results     │  │ merge │  │   │
│  │  │ image  │  │ NS       │  │ @Smoke    │  │             │  │       │  │   │
│  │  │        │  │          │  │ +Logviewer │  │             │  │       │  │   │
│  │  └────────┘  └──────────┘  │ +dynamic  │  └─────────────┘  └───────┘  │   │
│  │                             │  URL      │                              │   │
│  │                             └───────────┘                              │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │  Trigger B: Push auf develop-Branch des Logviewers                      │   │
│  │                                                                         │   │
│  │  ┌────────┐  ┌──────────┐  ┌───────────┐  ┌─────────────┐  ┌───────┐  │   │
│  │  │ build  │  │ deploy   │  │ trigger   │  │ poll status │  │report │  │   │
│  │  │ log-   │─►│ to       │─►│ cucumber  │─►│ & collect   │─►│upload │  │   │
│  │  │ viewer │  │ feste    │  │ test svc  │  │ results     │  │Zephyr │  │   │
│  │  │ image  │  │ test-env │  │ @regress  │  │             │  │       │  │   │
│  │  │        │  │          │  │ +Logviewer │  │             │  │       │  │   │
│  │  └────────┘  └──────────┘  └───────────┘  └─────────────┘  └───────┘  │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 12.3 OpenShift-Umgebungen

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  OpenShift Cluster                                                            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │  Dynamische Namespaces (pro Feature-Branch des Logviewers)              │ │
│  │                                                                         │ │
│  │  ┌──────────────────────┐  ┌──────────────────────┐                    │ │
│  │  │  NS: feature-abc-123  │  │  NS: feature-xyz-456  │  ◄── Temporaer   │ │
│  │  │  Logviewer + Backend  │  │  Logviewer + Backend  │      (Branch-     │ │
│  │  │  (Smoke-Tests)        │  │  (Smoke-Tests)        │       Lebensdauer)│ │
│  │  └──────────────────────┘  └──────────────────────┘                    │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌──────────────────────┐                                                    │
│  │  NS: test-automation  │  ◄── Cucumber Test Service (permanent)            │
│  │  cucumber-test-service│                                                    │
│  │  + PVC fuer Ergebnisse│                                                    │
│  └──────────────────────┘                                                    │
│                                                                              │
│  ┌──────────────────────┐                                                    │
│  │  NS: develop          │  ◄── Feste Umgebung (develop-Branch)              │
│  │  Backend-Verbund      │      Regressionstests                             │
│  │  + Logviewer          │                                                    │
│  └──────────────────────┘                                                    │
│                                                                              │
│  ┌──────────────────────┐                                                    │
│  │  NS: test-env         │  ◄── Feste Umgebung (Manuelle Tests)              │
│  │  Backend-Verbund      │                                                    │
│  │  + Logviewer          │                                                    │
│  └──────────────────────┘                                                    │
│                                                                              │
│  ┌──────────────────────┐                                                    │
│  │  NS: acceptance       │  ◄── Abnahmeumgebung (Externe Tests)              │
│  │  Backend-Verbund      │      L/P, Pentest, Barrierefreiheit               │
│  │  + Logviewer          │                                                    │
│  └──────────────────────┘                                                    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 13. Fehlermanagement

### 13.1 Fehlerklassifizierung

| Prioritaet     | Beschreibung                                                    | Reaktionszeit     | Behebungszeit     |
|----------------|-----------------------------------------------------------------|-------------------|-------------------|
| **Kritisch**   | System unbenutzbar, Datenverlust, Sicherheitsluecke             | Sofort            | Innerhalb 4 Std   |
| **Hoch**       | Kernfunktion eingeschraenkt, kein Workaround                    | Innerhalb 2 Std   | Innerhalb 1 Tag   |
| **Mittel**     | Funktion eingeschraenkt, Workaround vorhanden                   | Innerhalb 1 Tag   | Innerhalb 3 Tage  |
| **Niedrig**    | Kosmetisch, geringe Auswirkung                                  | Innerhalb 2 Tage  | Naechstes Release  |

### 13.2 Fehler-Lebenszyklus

```
  Neu ──► In Analyse ──► In Bearbeitung ──► Geloest ──► Verifiziert ──► Geschlossen
   │                          │                              │
   │                          ▼                              ▼
   │                    Zurueckgewiesen               Wieder geoeffnet
   │                    (kein Fehler /                (Verifikation
   ▼                     Duplikat)                     fehlgeschlagen)
 Abgelehnt
```

### 13.3 Fehlererfassung

Jeder Fehler wird in **Jira** erfasst mit folgenden Pflichtfeldern:

| Feld                 | Beschreibung                                                     |
|----------------------|------------------------------------------------------------------|
| Zusammenfassung      | Kurzbeschreibung des Fehlers                                     |
| Betroffenes System   | Backend-Verbund (Service A/B/C) / Logviewer                      |
| Prioritaet           | Kritisch / Hoch / Mittel / Niedrig                               |
| Umgebung             | dynamisch / develop / test / acceptance                          |
| Schritte zur Reproduktion | Nummerierte Schritte                                        |
| Erwartetes Ergebnis  | Was haette passieren sollen                                      |
| Tatsaechliches Ergebnis | Was tatsaechlich passiert ist                                 |
| Screenshots / Logs   | Allure-Report-Link, Axe-Report-Link, Screenshots, Log-Auszuege  |
| Testfall-Referenz    | Zephyr Scale Testfall-ID (`T-XXXX`)                              |

### 13.4 Fehlerquellen und Zuordnung

| Fehlerquelle                          | Testobjekt          | Zustaendig fuer Behebung   | Zustaendig fuer Verifikation     |
|---------------------------------------|---------------------|----------------------------|----------------------------------|
| Unit-Test-Fehler                      | Jeweiliges Projekt  | Entwicklung des Projekts   | Entwicklung (automatisch)        |
| Automatisierter Integrationstest      | Backend-Verbund     | Backend-Entwicklung        | Testteam (via Cucumber Test Svc) |
| Automatisierter Integrationstest      | Logviewer           | Frontend-Entwicklung       | Testteam (via Cucumber Test Svc) |
| Automatisierter Barrierefreiheitstest | Logviewer           | Frontend-Entwicklung / UX  | Testteam (via Axe-Reports)       |
| Manueller Test                        | Beide               | Jeweilige Entwicklung      | Testteam                         |
| Gesamtintegrationstest                | Beide               | Jeweilige Entwicklung      | Testteam                         |
| L/P-Test                              | Beide               | Entwicklung / DevOps       | Externes Testteam                |
| Pentest                               | Beide               | Entwicklung / Security     | Externer Pentest-Dienstleister   |
| Manueller Barrierefreiheitstest       | Logviewer           | Frontend-Entwicklung / UX  | Externes Testteam                |

### 13.5 Eskalation

| Stufe | Bedingung                                               | Eskalation an        |
|-------|---------------------------------------------------------|----------------------|
| 1     | Fehler wird nicht innerhalb der Reaktionszeit bearbeitet | Teamleitung          |
| 2     | Behebungszeit ueberschritten                             | Projektleitung       |
| 3     | Release-Blocker ohne Loesung                             | Management / Steering|
