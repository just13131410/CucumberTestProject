# Testabschlussbericht – Release 2.4.0

> **Beispielausgabe** zur Demonstration der Vorlage `Testabschlussbericht-Vorlage.md`. Alle Zahlen, Namen und Jira-IDs sind fiktiv.

| | |
|---|---|
| **Projekt** | SauceDemo Test-Automation Service |
| **Release / Version** | 2.4.0 (Build 1287, Git-Tag `v2.4.0`) |
| **Berichtszeitraum** | 2026-05-04 – 2026-05-29 |
| **Testmanager** | Alexej Kunz |
| **Erstellt am** | 2026-06-03 |
| **Dokumentstatus** | Final |
| **Verteiler** | PO, Fachbereich Vertrieb, Entwicklung, IT-Sicherheit, Betrieb |

---

## 1. Management Summary

**Gesamtbewertung:** 🟡 **Go mit Auflagen**

**Kernaussagen:**
- Es wurden **412 Testfälle** über **7 Teststufen** ausgeführt.
- Automatisierte API- und Frontend-Tests (DEV): **98,2 %** bestanden (243 / 247).
- Manuelle System-, Integrations- und E2E-Tests (TEST): **94,7 %** bestanden (108 / 114).
- Penetrationstest (ABNAHME): **9 Findings**, davon **2 offen** (1× High, 1× Medium).
- Barrierefreiheit (TEST): WCAG 2.1 Level **AA mit 4 verbleibenden Verstößen** (Level A erreicht).
- Offene Bugs zum Berichtszeitpunkt: **11** (davon 0 Blocker, 2 Critical, 5 Major, 4 Minor).
- **Empfehlung:** Freigabe für Produktion **nach Behebung der 2 Critical-Bugs und des High-Findings aus dem Pentest**. Die verbleibenden WCAG-Verstöße werden im Folge-Sprint 2.4.1 behoben (akzeptiert vom Fachbereich).

---

## 2. Testgegenstand & Scope

### 2.1 Getestete Komponenten
| Komponente | Version | Repository / Artefakt |
|---|---|---|
| CucumberTestProject (Test-Service) | 2.4.0 | `gitlab.intern/qa/cucumber-test-project` |
| SauceDemo Frontend (SUT) | 3.8.1 | extern (`saucedemo.com`) |
| JSONPlaceholder API (SUT-Stub) | – | extern (`jsonplaceholder.typicode.com`) |

### 2.2 In Scope
- Login-, Warenkorb- und Checkout-Flows (UI)
- Produkt- und User-Endpunkte der REST-API
- Accessibility-Scans aller Hauptseiten
- E2E-Bestellprozess mit Zahlungsdienstleister-Mock und Versandpartner

### 2.3 Out of Scope
- Lasttests (>100 parallele User) – Begründung: kein neues Skalierungsfeature im Release
- Mobile-Browser (iOS Safari, Android Chrome) – Begründung: separate Mobile-Roadmap Q3/2026

### 2.4 Referenzdokumente
- Testkonzept: [Testkonzept.md](Testkonzept.md)
- API-Benutzeranleitung: [API-Benutzeranleitung.md](API-Benutzeranleitung.md)
- Testautomatisierung: [Testautomatisierung-Benutzeranleitung.md](Testautomatisierung-Benutzeranleitung.md)

---

## 3. Teststufen, Testarten & Umgebungen

| # | Teststufe | Testart | Umgebung | Tool / Verfahren | Verantwortlich | Status |
|---|---|---|---|---|---|---|
| 1 | Integration | API-Tests automatisiert | DEV | REST Assured + Cucumber | QA-Team / CI | ✅ |
| 2 | Integration | Frontend-Tests automatisiert | DEV | Playwright + Cucumber | QA-Team / CI | ✅ |
| 3 | Systemtest | Backend manuell | TEST | manuelle Durchführung | M. Schneider | ✅ |
| 4 | Systemintegrationstest | Fachliche Gesamtintegration manuell | TEST | manuelle Durchführung | Fachbereich Vertrieb | ⚠️ |
| 5 | E2E | Mit Kommunikationspartnern | TEST | koordiniert/manuell | A. Kunz | ✅ |
| 6 | Sicherheit | Penetrationstest | ABNAHME | extern (SecureCode GmbH) | IT-Sicherheit | ⚠️ |
| 7 | Nicht-funktional | Barrierefreiheit (WCAG 2.1 / BITV 2.0) | TEST | Axe + manuelle Prüfung (NVDA) | T. Bauer | ⚠️ |

---

## 4. Testumgebungen & Testdaten

| Umgebung | Verwendung | Stand / Datenbasis | Einschränkungen |
|---|---|---|---|
| DEV | Automatisierte API- und Frontend-Tests | Synthetische Testdaten / Mocks | Zahlungsdienstleister und Versandpartner gemockt |
| TEST | Manuelle Backend-, Integration-, E2E-, A11y-Tests | Anonymisierte Produktionsdaten (Stand 2026-05-01) | Versandpartner-Sandbox nur Mo–Fr 8–18 Uhr verfügbar |
| ABNAHME | Penetrationstest | Vollständige produktionsnahe Konfiguration | Testzeitfenster 2026-05-25 – 2026-05-29 (5 PT) |

---

## 5. Testergebnisse je Teststufe

### 5.1 Automatisierte API-Tests (DEV)
| Kennzahl | Wert |
|---|---|
| Geplant | 132 |
| Ausgeführt | 132 |
| Bestanden | 130 |
| Fehlgeschlagen | 2 |
| Blockiert | 0 |
| Code Coverage (JaCoCo) | 76,4 % |

**Reports:** [Allure-Report](https://reports.intern/allure/2.4.0/api/) · [Cucumber-Report](https://reports.intern/cucumber/2.4.0/api/)

### 5.2 Automatisierte Frontend-Tests (DEV)
| Kennzahl | Wert |
|---|---|
| Geplant | 115 |
| Ausgeführt | 115 |
| Bestanden | 113 |
| Fehlgeschlagen | 2 |
| Browser-Matrix | Chromium ✅ / Firefox ✅ / WebKit ⚠️ (1 Flaky-Test) |

**Reports:** [Allure-Report](https://reports.intern/allure/2.4.0/frontend/) · Screenshots im PVC unter `/app/test-results/3f9b-…/screenshots`

### 5.3 Manuelle Backend-Tests (TEST)
| Kennzahl | Wert |
|---|---|
| Geplant | 38 |
| Ausgeführt | 38 |
| Bestanden | 36 |
| Fehlgeschlagen | 2 |

**Auffälligkeiten:** Beim Zurücksetzen des Warenkorbs nach Sitzungs-Timeout bleibt der DB-Eintrag für 5 Minuten bestehen (BUG-1083, Major).

### 5.4 Manuelle Gesamtintegrationstests (TEST)
| Kennzahl | Wert |
|---|---|
| Geplant | 42 |
| Ausgeführt | 40 |
| Bestanden | 37 |
| Fehlgeschlagen | 3 |

**Fachliche Strecken:** Bestellung Standard, Bestellung mit Gutschein, Stornierung vor Versand, Retoure nach Versand. **Strecke „Retoure mit Teilerstattung" konnte nicht abgeschlossen werden** (Versandpartner-Sandbox am 27.05. nicht verfügbar) – siehe Abweichung §8.

### 5.5 E2E-Tests mit Kommunikationspartnern (TEST)
| Partner | Schnittstelle | Testzeitraum | Status | Notizen |
|---|---|---|---|---|
| PayPay AG (Zahlung) | REST / OAuth2 | 2026-05-12 – 2026-05-14 | ✅ | Alle 8 Szenarien grün |
| DHL Express (Versand) | SOAP | 2026-05-19 – 2026-05-22 | ✅ | 1 Timeout bei Sammelabholung → Retry-Logik bestätigt |
| TaxCloud GmbH (Steuerberechnung) | REST | 2026-05-20 | ⚠️ | EU-Reverse-Charge nicht getestet (kein Stammdatensatz) |

### 5.6 Penetrationstest (ABNAHME)
- **Durchführender:** SecureCode GmbH
- **Zeitraum:** 2026-05-25 – 2026-05-29 (5 PT)
- **Methodik:** OWASP Top 10 (2021) / OWASP ASVS Level 2
- **Findings gesamt:** 9 (Critical: 0 / High: 2 / Medium: 4 / Low: 3)
- **Offen:** 2 (1× High: fehlende Rate-Limitierung auf `/api/v1/test/execute`; 1× Medium: verbose Stacktrace im Error-Response)
- **Vollständiger Bericht:** Anhang B

### 5.7 Barrierefreiheitstests (TEST)
- **Standard:** WCAG 2.1 Level **AA** / BITV 2.0
- **Automatisiert (Axe):** 4 Verstöße über 17 geprüfte Seiten
  - 2× unzureichender Kontrast (Checkout-Button, Footer-Links)
  - 1× fehlendes `alt`-Attribut (Produktbild-Karussell)
  - 1× Formularfeld ohne assoziiertes `<label>` (Gutschein-Eingabe)
- **Manuelle Prüfung:** NVDA + Tastaturnavigation – Login- und Checkout-Flow nutzbar, **Filter-Sidebar nicht vollständig per Tastatur erreichbar**
- **Vollständiger Bericht:** Anhang C

---

## 6. Fehlerstatistik (aus Jira)

> **Confluence:** Jira-Makro mit JQL `project = SAUCE AND fixVersion = "2.4.0" AND issuetype = Bug`

### 6.1 Gesamtüberblick
| Status | Anzahl |
|---|---|
| Neu eröffnet im Zeitraum | 47 |
| Geschlossen / Fixed | 36 |
| Offen zum Stichtag | 11 |

### 6.2 Nach Schweregrad (offene Bugs)
| Schweregrad | Anzahl | Anteil |
|---|---|---|
| Blocker | 0 | 0 % |
| Critical | 2 | 18 % |
| Major | 5 | 46 % |
| Minor | 4 | 36 % |

### 6.3 Nach Teststufe (offene Bugs)
| Teststufe | Anzahl |
|---|---|
| API / Frontend (DEV) | 2 |
| Backend manuell (TEST) | 1 |
| Integration (TEST) | 2 |
| E2E (TEST) | 1 |
| Pentest (ABN) | 2 |
| Barrierefreiheit (TEST) | 3 |

### 6.4 Offene Bugs zum Berichtszeitpunkt

| Jira-ID | Titel | Schwere | Komponente | Workaround | Entscheidung |
|---|---|---|---|---|---|
| SAUCE-2145 | Checkout schlägt bei Gutscheinwert = Gesamtsumme fehl | Critical | Checkout-Service | manueller Rabatt durch Support | **Fix vor Release** |
| SAUCE-2150 | Pentest: fehlende Rate-Limitierung auf `/api/v1/test/execute` | Critical | API-Gateway | WAF-Regel (temporär) | **Fix vor Release** |
| SAUCE-2148 | DB-Eintrag Warenkorb bleibt 5 min nach Timeout | Major | Cart-Service | – | Folge-Release 2.4.1 |
| SAUCE-2151 | Pentest: verbose Stacktrace in Error-Response | Major | Error-Handler | – | Folge-Release 2.4.1 |
| SAUCE-2152 | TaxCloud: EU-Reverse-Charge nicht abdeckbar | Major | Steuer-Service | manuelle Rechnungsstellung | Folge-Release 2.5.0 |
| SAUCE-2147 | WebKit-Flaky: Login-Button nicht klickbar (1 von 20) | Major | UI/Test | Retry | Folge-Release 2.4.1 |
| SAUCE-2153 | Retoure mit Teilerstattung nicht getestet | Major | Integration | manuell durch Buchhaltung | nachholen 2026-06-10 |
| SAUCE-2154 | A11y: Kontrast Checkout-Button < 4.5:1 | Minor | UI | – | Folge-Release 2.4.1 |
| SAUCE-2155 | A11y: alt-Attribut Produktkarussell | Minor | UI | – | Folge-Release 2.4.1 |
| SAUCE-2156 | A11y: Label fehlt bei Gutschein-Eingabe | Minor | UI | – | Folge-Release 2.4.1 |
| SAUCE-2157 | A11y: Filter-Sidebar nicht voll tastaturbedienbar | Minor | UI | Suche/Filter via Header | Folge-Release 2.4.1 |

### 6.5 Bug-Trend (Find- vs. Fix-Rate)
> **Confluence:** Jira-Chart-Makro „Created vs. Resolved" einfügen. Aktueller Stand: Burndown geschlossen am 2026-05-28, danach nur noch Pentest-Findings.

---

## 7. Anforderungsabdeckung / Traceability

> **Confluence:** Jira-Filter `project = SAUCE AND fixVersion = "2.4.0" AND issuetype = Story`

| Story | Titel | Testfälle (Tag `@T-XXXX`) | Status | Abdeckung |
|---|---|---|---|---|
| SAUCE-1980 | Gutschein-Eingabe im Checkout | @T-1101, @T-1102, @T-1103 | ✅ | 100 % |
| SAUCE-1981 | Steuerberechnung über TaxCloud | @T-1110, @T-1111 | ⚠️ | 50 % (EU-Reverse-Charge offen, SAUCE-2152) |
| SAUCE-1982 | Retouren-Self-Service | @T-1120, @T-1121, @T-1122 | ⚠️ | 67 % (Teilerstattung offen, SAUCE-2153) |
| SAUCE-1983 | Rate-Limiting API | @T-1130 | ✅ | 100 % (Pentest deckte Lücke auf, jetzt gefixt) |
| SAUCE-1984 | Verbesserte Barrierefreiheit Checkout | @T-1140, @T-1141 | ⚠️ | 75 % (4 Verstöße verbleibend) |

**Stories ohne Testabdeckung:** keine.

---

## 8. Abweichungen vom Testplan

| Geplant | Tatsächlich | Begründung | Risiko |
|---|---|---|---|
| Retoure mit Teilerstattung E2E | nicht durchgeführt | Versandpartner-Sandbox am Testtag 2026-05-27 ausgefallen | R3 – siehe §9 |
| EU-Reverse-Charge-Szenario | nicht durchgeführt | kein Stammdatensatz vorhanden | R2 – siehe §9 |
| WebKit auf macOS-Runner | teilweise | nur Linux-WebKit verfügbar (1 Flaky-Test) | gering – Workaround via Retry |

---

## 9. Risikobewertung & Restrisiken

| ID | Risiko | Eintritts­wahrscheinlich­keit | Auswirkung | Mitigation | Risiko­träger |
|---|---|---|---|---|---|
| R1 | Verbose Stacktraces ermöglichen Info-Disclosure | mittel | mittel | WAF-Regel + Fix in 2.4.1 | IT-Sicherheit |
| R2 | EU-Reverse-Charge-Berechnung fehlerhaft | niedrig | hoch | manuelle Buchhaltungsprüfung, Fix in 2.5.0 | Fachbereich Vertrieb |
| R3 | Teilerstattung erzeugt inkorrekte Belege | mittel | mittel | Nachtest 2026-06-10, bis dahin Buchhaltung manuell | Fachbereich Buchhaltung |
| R4 | 4 verbleibende WCAG-Verstöße | hoch | mittel | Fix in 2.4.1 (KW 24) | PO + Fachbereich |
| R5 | WebKit-Flaky-Test maskiert echten Bug | niedrig | niedrig | Monitoring auf Pass-Rate, Investigation bei <90 % | QA-Team |

---

## 10. Abnahmekriterien – Erfüllungsnachweis

| # | Kriterium (aus Testkonzept) | Erfüllt? | Beleg |
|---|---|---|---|
| 1 | Alle Smoke-Tests grün | ✅ | Allure-Report §5.1 / §5.2 |
| 2 | Keine offenen Blocker-Bugs | ✅ | Jira-Filter §6.4 |
| 3 | Code Coverage ≥ 70 % | ✅ | JaCoCo 76,4 % |
| 4 | Pentest ohne offene Critical Findings | ✅ | Anhang B (0 Critical) |
| 5 | Pentest ohne offene High Findings | ⚠️ | 1× High offen (SAUCE-2150) – **Fix vor Release Pflicht** |
| 6 | WCAG 2.1 Level AA erreicht | ⚠️ | 4 Verstöße verbleibend – Akzeptanz durch Fachbereich, Fix in 2.4.1 |
| 7 | E2E mit allen Kommunikationspartnern erfolgreich | ⚠️ | TaxCloud Teilabdeckung |

---

## 11. Empfehlung & Freigabeentscheidung

**Empfehlung des Testmanagers:**

Die Software ist funktional in einem produktionsreifen Zustand. **Freigabe wird empfohlen unter folgenden Auflagen.** Die verbleibenden Barrierefreiheits- und Buchhaltungsrisiken werden vom Fachbereich akzeptiert und sind im Folge-Release 2.4.1 (geplant 2026-06-15) eingeplant.

**Auflagen für die Freigabe:**
1. SAUCE-2145 (Checkout-Gutschein-Bug, Critical) muss vor Deployment behoben sein.
2. SAUCE-2150 (Pentest High Finding, Rate-Limiting) muss vor Deployment behoben sein.
3. WAF-Regel für SAUCE-2151 (verbose Stacktrace) muss bis Fix in 2.4.1 aktiv bleiben.
4. Nachtest „Retoure mit Teilerstattung" bis 2026-06-10 dokumentieren.

**Freigaben:**

| Rolle | Name | Datum | Unterschrift |
|---|---|---|---|
| Testmanager | Alexej Kunz | 2026-06-03 | _________ |
| Product Owner | S. Lehmann | | _________ |
| Fachbereich Vertrieb | K. Vogt | | _________ |
| IT-Sicherheit | Dr. R. Becker | | _________ |
| Betrieb | M. Hoffmann | | _________ |

---

## 12. Lessons Learned

### Was lief gut
- Automatisierte API- und Frontend-Suite stabil, CI-Durchlaufzeit unter 12 Minuten.
- Frühzeitige Koordination mit PayPay AG und DHL ermöglichte vollständige E2E-Abdeckung in Woche 2.
- Allure-Reports werden vom PO aktiv konsumiert – kein Workshop mehr nötig.

### Verbesserungspotenzial
- Versandpartner-Sandbox sollte 24/7 verfügbar sein oder als Mock-Fallback bereitstehen.
- TaxCloud-Stammdaten für EU-Szenarien müssen vor Testbeginn vorliegen (Bestellung 4 Wochen Vorlauf).
- WebKit-Tests auf echtem macOS-Runner statt Linux-WebKit, um Flakiness auszuschließen.
- Pentest sollte 1 Woche früher beginnen, damit Fixes vor Abnahmedatum möglich sind.

### Maßnahmen für den nächsten Release
| # | Maßnahme | Verantwortlich | Termin |
|---|---|---|---|
| 1 | Mock-Fallback für Versandpartner-Sandbox aufbauen | QA-Team | 2026-06-30 |
| 2 | TaxCloud-Teststammdaten beantragen | Fachbereich | 2026-06-10 |
| 3 | macOS-CI-Runner evaluieren | DevOps | 2026-07-15 |
| 4 | Pentest-Slot 2.5.0 frühzeitig (KW 30) buchen | Testmanager | 2026-06-15 |

---

## Anhänge

| Anhang | Inhalt | Link |
|---|---|---|
| A | Allure-Report (alle Stufen) | https://reports.intern/allure/2.4.0/ |
| B | Penetrationstest-Bericht SecureCode GmbH | https://confluence.intern/SAUCE/pentest-2.4.0 |
| C | Barrierefreiheits-Bericht (Axe + NVDA-Protokoll) | https://confluence.intern/SAUCE/a11y-2.4.0 |
| D | Jira-Export offener Bugs (CSV) | https://jira.intern/issues/?filter=14782 |
| E | Testkonzept | [Testkonzept.md](Testkonzept.md) |
| F | Protokoll E2E mit Kommunikationspartnern | https://confluence.intern/SAUCE/e2e-protokoll-2.4.0 |

---

*Beispielausgabe – generiert auf Basis der Vorlage `Testabschlussbericht-Vorlage.md` (ISO/IEC/IEEE 29119-3).*
