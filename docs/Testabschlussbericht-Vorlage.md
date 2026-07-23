# Testabschlussbericht – Release {{RELEASE_VERSION}}

> **Confluence-Hinweis:** Diese Vorlage ist Markdown und kann via *Insert → Markup → Markdown* in Confluence importiert werden. Platzhalter `{{...}}` ersetzen. Wo „**Jira-Makro**" steht, in Confluence das entsprechende Makro (Jira Issues / Jira Filter) einfügen.

| | |
|---|---|
| **Projekt** | {{PROJEKTNAME}} |
| **Release / Version** | {{RELEASE_VERSION}} (Build {{BUILD_NR}}, Git-Tag {{GIT_TAG}}) |
| **Berichtszeitraum** | {{START_DATUM}} – {{END_DATUM}} |
| **Testmanager** | {{TESTMANAGER}} |
| **Erstellt am** | {{ERSTELLT_AM}} |
| **Dokumentstatus** | Entwurf / In Review / Final |
| **Verteiler** | PO, Fachbereich, Entwicklung, IT-Sicherheit, Betrieb |

---

## 1. Management Summary

> **Eine Seite. Keine Details. Entscheidungsrelevant.**

**Gesamtbewertung:** 🟢 Go / 🟡 Go mit Auflagen / 🔴 No-Go

**Kernaussagen:**
- Es wurden {{ANZ_TESTFAELLE_GESAMT}} Testfälle über {{ANZ_TESTSTUFEN}} Teststufen ausgeführt.
- Automatisierte API- und Frontend-Tests (DEV): **{{PASS_RATE_AUTO}} %** bestanden.
- Manuelle System-, Integrations- und E2E-Tests (TEST): **{{PASS_RATE_MANUELL}} %** bestanden.
- Penetrationstest (ABNAHME): {{ANZ_PENTEST_FINDINGS}} Findings, davon {{ANZ_PENTEST_OFFEN}} offen.
- Barrierefreiheit (TEST): WCAG-Konformitätsstufe {{WCAG_LEVEL}} erreicht / nicht erreicht.
- Offene Bugs zum Berichtszeitpunkt: **{{ANZ_BUGS_OFFEN}}** (davon {{ANZ_BLOCKER}} Blocker, {{ANZ_CRITICAL}} Critical).
- **Empfehlung:** {{EMPFEHLUNG_KURZ}}

---

## 2. Testgegenstand & Scope

### 2.1 Getestete Komponenten
| Komponente | Version | Repository / Artefakt |
|---|---|---|
| {{KOMPONENTE_1}} | {{VERSION_1}} | {{REPO_1}} |
| {{KOMPONENTE_2}} | {{VERSION_2}} | {{REPO_2}} |

### 2.2 In Scope
- {{SCOPE_IN_1}}
- {{SCOPE_IN_2}}

### 2.3 Out of Scope
- {{SCOPE_OUT_1}} – Begründung: {{BEGRUENDUNG_1}}

### 2.4 Referenzdokumente
- Testkonzept: [Testkonzept.md](Testkonzept.md)
- API-Benutzeranleitung: [API-Benutzeranleitung.md](API-Benutzeranleitung.md)
- Testautomatisierung: [Testautomatisierung-Benutzeranleitung.md](Testautomatisierung-Benutzeranleitung.md)

---

## 3. Teststufen, Testarten & Umgebungen

| # | Teststufe | Testart | Umgebung | Tool / Verfahren | Verantwortlich | Status |
|---|---|---|---|---|---|---|
| 1 | Integration | API-Tests automatisiert | DEV | REST Assured + Cucumber | {{VERANTW_1}} | ✅ / ⚠️ / ❌ |
| 2 | Integration | Frontend-Tests automatisiert | DEV | Playwright + Cucumber | {{VERANTW_2}} | ✅ / ⚠️ / ❌ |
| 3 | Systemtest | Backend manuell | TEST | manuelle Durchführung | {{VERANTW_3}} | ✅ / ⚠️ / ❌ |
| 4 | Systemintegrationstest | Fachliche Gesamtintegration manuell | TEST | manuelle Durchführung | {{VERANTW_4}} | ✅ / ⚠️ / ❌ |
| 5 | E2E | Mit Kommunikationspartnern | TEST | koordiniert/manuell | {{VERANTW_5}} | ✅ / ⚠️ / ❌ |
| 6 | Sicherheit | Penetrationstest | ABNAHME | extern ({{PENTEST_ANBIETER}}) | {{VERANTW_6}} | ✅ / ⚠️ / ❌ |
| 7 | Nicht-funktional | Barrierefreiheit (WCAG 2.1 / BITV 2.0) | TEST | Axe + manuelle Prüfung | {{VERANTW_7}} | ✅ / ⚠️ / ❌ |

---

## 4. Testumgebungen & Testdaten

| Umgebung | Verwendung | Stand / Datenbasis | Einschränkungen |
|---|---|---|---|
| DEV | Automatisierte API- und Frontend-Tests | Synthetische Testdaten / Mocks | Kommunikationspartner gemockt |
| TEST | Manuelle Backend-, Integration-, E2E-, A11y-Tests | Anonymisierte Produktionsdaten (Stand {{DATEN_STAND}}) | {{EINSCHRAENKUNG_TEST}} |
| ABNAHME | Penetrationstest | Vollständige Konfiguration | Eingeschränktes Zeitfenster |

---

## 5. Testergebnisse je Teststufe

### 5.1 Automatisierte API-Tests (DEV)
| Kennzahl | Wert |
|---|---|
| Geplant | {{API_PLAN}} |
| Ausgeführt | {{API_AUSGEF}} |
| Bestanden | {{API_PASS}} |
| Fehlgeschlagen | {{API_FAIL}} |
| Blockiert | {{API_BLOCKED}} |
| Code Coverage (JaCoCo) | {{COVERAGE}} % |

**Reports:** [Allure-Report]({{ALLURE_URL}}) · [Cucumber-Report]({{CUCUMBER_URL}})

### 5.2 Automatisierte Frontend-Tests (DEV)
| Kennzahl | Wert |
|---|---|
| Geplant | {{FE_PLAN}} |
| Ausgeführt | {{FE_AUSGEF}} |
| Bestanden | {{FE_PASS}} |
| Fehlgeschlagen | {{FE_FAIL}} |
| Browser-Matrix | Chromium / Firefox / WebKit |

**Reports:** [Allure-Report]({{ALLURE_URL_FE}}) · Screenshots im PVC unter `/app/test-results/{{RUN_ID}}/screenshots`

### 5.3 Manuelle Backend-Tests (TEST)
| Kennzahl | Wert |
|---|---|
| Geplant | {{BE_PLAN}} |
| Ausgeführt | {{BE_AUSGEF}} |
| Bestanden | {{BE_PASS}} |
| Fehlgeschlagen | {{BE_FAIL}} |

**Auffälligkeiten:** {{BE_NOTES}}

### 5.4 Manuelle Gesamtintegrationstests (TEST)
| Kennzahl | Wert |
|---|---|
| Geplant | {{INT_PLAN}} |
| Ausgeführt | {{INT_AUSGEF}} |
| Bestanden | {{INT_PASS}} |
| Fehlgeschlagen | {{INT_FAIL}} |

**Fachliche Strecken:** {{FACHLICHE_STRECKEN}}

### 5.5 E2E-Tests mit Kommunikationspartnern (TEST)
| Partner | Schnittstelle | Testzeitraum | Status | Notizen |
|---|---|---|---|---|
| {{PARTNER_1}} | {{IF_1}} | {{ZEITRAUM_1}} | ✅ / ⚠️ / ❌ | {{NOTE_1}} |
| {{PARTNER_2}} | {{IF_2}} | {{ZEITRAUM_2}} | ✅ / ⚠️ / ❌ | {{NOTE_2}} |

### 5.6 Penetrationstest (ABNAHME)
- **Durchführender:** {{PENTEST_ANBIETER}}
- **Zeitraum:** {{PENTEST_ZEITRAUM}}
- **Methodik:** OWASP Top 10 / OWASP ASVS Level {{ASVS_LEVEL}}
- **Findings gesamt:** {{ANZ_PENTEST_FINDINGS}} (Critical: {{PT_CRIT}} / High: {{PT_HIGH}} / Medium: {{PT_MED}} / Low: {{PT_LOW}})
- **Offen:** {{ANZ_PENTEST_OFFEN}}
- **Vollständiger Bericht:** Anhang B

### 5.7 Barrierefreiheitstests (TEST)
- **Standard:** WCAG 2.1 Level {{WCAG_LEVEL}} / BITV 2.0
- **Automatisiert (Axe):** {{AXE_VIOLATIONS}} Verstöße über {{AXE_PAGES}} Seiten
- **Manuelle Prüfung:** Screenreader (NVDA / JAWS), Tastaturnavigation, Kontraste
- **Vollständiger Bericht:** Anhang C

---

## 6. Fehlerstatistik (aus Jira)

> **Confluence:** Hier das **Jira-Makro** mit folgendem JQL einbetten:
> `project = {{JIRA_PROJEKT}} AND fixVersion = "{{RELEASE_VERSION}}" AND issuetype = Bug`

### 6.1 Gesamtüberblick
| Status | Anzahl |
|---|---|
| Neu eröffnet im Zeitraum | {{BUG_NEU}} |
| Geschlossen / Fixed | {{BUG_FIXED}} |
| Offen zum Stichtag | {{BUG_OFFEN}} |

### 6.2 Nach Schweregrad (offene Bugs)
| Schweregrad | Anzahl | Anteil |
|---|---|---|
| Blocker | {{BLOCKER}} | {{BLOCKER_PCT}} % |
| Critical | {{CRITICAL}} | {{CRITICAL_PCT}} % |
| Major | {{MAJOR}} | {{MAJOR_PCT}} % |
| Minor | {{MINOR}} | {{MINOR_PCT}} % |

### 6.3 Nach Teststufe (offene Bugs)
| Teststufe | Anzahl |
|---|---|
| API / Frontend (DEV) | {{BUG_DEV}} |
| Backend manuell (TEST) | {{BUG_BE_MAN}} |
| Integration (TEST) | {{BUG_INT}} |
| E2E (TEST) | {{BUG_E2E}} |
| Pentest (ABN) | {{BUG_PT}} |
| Barrierefreiheit (TEST) | {{BUG_A11Y}} |

### 6.4 Offene Bugs zum Berichtszeitpunkt
> **Confluence:** Jira-Filter-Makro mit JQL: `project = {{JIRA_PROJEKT}} AND fixVersion = "{{RELEASE_VERSION}}" AND status != Done AND issuetype = Bug ORDER BY priority DESC`

| Jira-ID | Titel | Schwere | Komponente | Workaround | Entscheidung |
|---|---|---|---|---|---|
| {{JIRA-1}} | {{TITEL_1}} | Blocker | {{KOMP_1}} | {{WA_1}} | Fix vor Release / Akzeptiert / Folge-Release |
| {{JIRA-2}} | {{TITEL_2}} | Critical | {{KOMP_2}} | {{WA_2}} | Fix vor Release / Akzeptiert / Folge-Release |

### 6.5 Bug-Trend (Find- vs. Fix-Rate)
> **Confluence:** Diagramm-Makro oder Screenshot aus Jira-Dashboard einfügen.

---

## 7. Anforderungsabdeckung / Traceability

> **Confluence:** Jira-Filter-Makro mit User Stories des Release einbetten:
> `project = {{JIRA_PROJEKT}} AND fixVersion = "{{RELEASE_VERSION}}" AND issuetype = Story`

| Story | Titel | Testfälle (Tag `@T-XXXX`) | Status | Abdeckung |
|---|---|---|---|---|
| {{STORY-1}} | {{S_TITEL_1}} | @T-1001, @T-1002 | ✅ | 100 % |
| {{STORY-2}} | {{S_TITEL_2}} | @T-1010 | ⚠️ | 50 % (1 von 2 Akzeptanzkriterien getestet) |

**Stories ohne Testabdeckung:** {{STORIES_OHNE_TESTS}} → Risiko siehe Abschnitt 9.

---

## 8. Abweichungen vom Testplan

| Geplant | Tatsächlich | Begründung | Risiko |
|---|---|---|---|
| {{PLAN_1}} | {{IST_1}} | {{GRUND_1}} | {{RISIKO_1}} |

---

## 9. Risikobewertung & Restrisiken

| ID | Risiko | Eintritts­wahrscheinlich­keit | Auswirkung | Mitigation | Risiko­träger |
|---|---|---|---|---|---|
| R1 | {{RISIKO_BESCHR_1}} | niedrig / mittel / hoch | niedrig / mittel / hoch | {{MITIG_1}} | {{TRAEGER_1}} |
| R2 | Offene Pentest-Findings ({{ANZ_PENTEST_OFFEN}}) | mittel | hoch | Fix-Plan in {{FOLGE_RELEASE}} | IT-Sicherheit |
| R3 | WCAG-Verstöße ({{AXE_VIOLATIONS}}) | hoch | mittel | Behebung im Folge-Sprint | Fachbereich |

---

## 10. Abnahmekriterien – Erfüllungsnachweis

| # | Kriterium (aus Testkonzept) | Erfüllt? | Beleg |
|---|---|---|---|
| 1 | Alle Smoke-Tests grün | ✅ / ⚠️ / ❌ | Allure-Report §5.1 |
| 2 | Keine offenen Blocker-Bugs | ✅ / ⚠️ / ❌ | Jira-Filter §6.4 |
| 3 | Code Coverage ≥ 70 % | ✅ / ⚠️ / ❌ | JaCoCo-Report |
| 4 | Pentest ohne offene Critical Findings | ✅ / ⚠️ / ❌ | Anhang B |
| 5 | WCAG 2.1 Level AA erreicht | ✅ / ⚠️ / ❌ | Anhang C |
| 6 | E2E mit allen Kommunikationspartnern erfolgreich | ✅ / ⚠️ / ❌ | §5.5 |

---

## 11. Empfehlung & Freigabeentscheidung

**Empfehlung des Testmanagers:**
{{EMPFEHLUNG_LANG}}

**Auflagen für die Freigabe:**
1. {{AUFLAGE_1}}
2. {{AUFLAGE_2}}

**Freigaben:**

| Rolle | Name | Datum | Unterschrift |
|---|---|---|---|
| Testmanager | {{TESTMANAGER}} | | |
| Product Owner | {{PO}} | | |
| Fachbereich | {{FACHBEREICH}} | | |
| IT-Sicherheit | {{SECURITY}} | | |
| Betrieb | {{BETRIEB}} | | |

---

## 12. Lessons Learned

### Was lief gut
- {{GUT_1}}
- {{GUT_2}}

### Verbesserungspotenzial
- {{VERBESSERUNG_1}}
- {{VERBESSERUNG_2}}

### Maßnahmen für den nächsten Release
| # | Maßnahme | Verantwortlich | Termin |
|---|---|---|---|
| 1 | {{MASSNAHME_1}} | {{VER_1}} | {{TERMIN_1}} |

---

## Anhänge

| Anhang | Inhalt | Link |
|---|---|---|
| A | Allure-Report (alle Stufen) | {{ALLURE_URL}} |
| B | Penetrationstest-Bericht | {{PENTEST_URL}} |
| C | Barrierefreiheits-Bericht (Axe + manuelle Prüfung) | {{A11Y_URL}} |
| D | Jira-Export offener Bugs (CSV) | {{JIRA_CSV_URL}} |
| E | Testkonzept | [Testkonzept.md](Testkonzept.md) |
| F | Protokoll E2E-Tests mit Kommunikationspartnern | {{E2E_PROTOKOLL_URL}} |

---

*Vorlage basierend auf ISO/IEC/IEEE 29119-3 (Test Completion Report), angepasst auf Projekt {{PROJEKTNAME}}.*
