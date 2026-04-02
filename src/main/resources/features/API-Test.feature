@Backend
Feature: GitHub API - Authentifizierung und Autorisierung

  @T-3511 @SmokeTest
  Scenario: Öffentliche Resource ohne Token ist erreichbar
    Given API Basis-URL ist gesetzt
    When ich GET an "/rate_limit" ausführe ohne Authentifizierung
    Then ist der Statuscode 200
    And das JSON-Feld "rate.limit" ist vorhanden

  @T-3512 @SmokeTest
  Scenario: Geschützte Resource ohne Token liefert 401
    Given API Basis-URL ist gesetzt
    When ich GET an "/user" ausführe ohne Authentifizierung
    Then ist der Statuscode 401
    And enthält das JSON-Feld "message" mit String-Wert "Requires authentication"

  @T-3513 @SmokeTest
  Scenario: Ungültiger Token liefert 401
    Given API Basis-URL ist gesetzt
    When ich GET an "/user" ausführe mit ungültigem Token
    Then ist der Statuscode 401

  @T-3514 @regression @requires-auth
  Scenario: Authentifizierter Zugriff auf Benutzerprofil liefert 200
    Given API Basis-URL ist gesetzt
    And ein gültiger API-Token ist konfiguriert
    When ich GET an "/user" ausführe mit Bearer-Token
    Then ist der Statuscode 200
    And das JSON-Feld "login" ist nicht leer

  @T-3515 @regression @requires-auth
  Scenario: Autorisierter Zugriff auf eigene Repositories liefert Liste
    Given API Basis-URL ist gesetzt
    And ein gültiger API-Token ist konfiguriert
    When ich GET an "/user/repos" ausführe mit Bearer-Token
    Then ist der Statuscode 200
    And die Response ist eine JSON-Liste

  @T-3516 @regression @requires-auth
  Scenario: Zugriff auf öffentliches Repository ist ohne Token möglich
    Given API Basis-URL ist gesetzt
    When ich GET an "/repos/octocat/Hello-World" ausführe ohne Authentifizierung
    Then ist der Statuscode 200
    And das JSON-Feld "full_name" ist nicht leer

  @T-3517 @SmokeTest
  Scenario: Response.pdf und eingebettetes XML stimmen mit Response.json überein
    Given die Testdatei "Response.json" ist geladen
    # pdfDatei erzeugt
    Then die PDF-Datei "Response.pdf" enthält alle Felder aus "Response.json"
    And das eingebettete XML in "Response.pdf" stimmt mit "Response.json" überein
