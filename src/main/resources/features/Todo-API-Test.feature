@Backend @API-Test
Feature: Todo API - Authentifizierung und CRUD-Operationen

  Background:
    Given die Todo-API Basis-URL ist gesetzt

  @T-4001 @SmokeTest
  Scenario: Login mit gültigen Zugangsdaten liefert JWT-Token
    When ich mich mit Benutzername "admin" und Passwort "password123" einlogge
    Then ist der Statuscode 200
    And das JSON-Feld "token" ist vorhanden
    And das JSON-Feld "user.username" ist nicht leer

  @T-4002 @SmokeTest
  Scenario: Login mit ungültigem Passwort liefert 401
    When ich mich mit Benutzername "admin" und Passwort "falschesPasswort" einlogge
    Then ist der Statuscode 401

  @T-4003 @SmokeTest
  Scenario: Login mit unbekanntem Benutzer liefert 401
    When ich mich mit Benutzername "unbekannt" und Passwort "password123" einlogge
    Then ist der Statuscode 401

  @T-4004 @SmokeTest
  Scenario: Zugriff auf Todos ohne Token liefert 401
    When ich die Todo-Liste ohne Token abrufe
    Then ist der Statuscode 401

  @T-4005 @regression
  Scenario: Authentifizierter Benutzer kann Todos abrufen
    Given ich bin als "admin" mit Passwort "password123" in der Todo-API eingeloggt
    When ich GET an "/api/todos" ausführe mit JWT-Token
    Then ist der Statuscode 200
    And die Response ist eine JSON-Liste

  @T-4006 @regression
  Scenario: Authentifizierter Benutzer kann eigenes Profil abrufen
    Given ich bin als "admin" mit Passwort "password123" in der Todo-API eingeloggt
    When ich GET an "/api/auth/me" ausführe mit JWT-Token
    Then ist der Statuscode 200
    And enthält das JSON-Feld "username" mit String-Wert "admin"
    And das JSON-Feld "email" ist nicht leer

  @T-4007 @regression
  Scenario: Neues Todo anlegen
    Given ich bin als "admin" mit Passwort "password123" in der Todo-API eingeloggt
    When ich ein neues Todo mit Titel "Cucumber Test Todo" anlege
    Then ist der Statuscode 201
    And das JSON-Feld "id" ist vorhanden
    And enthält das JSON-Feld "title" mit String-Wert "Cucumber Test Todo"

  @T-4008 @regression
  Scenario: Todo ohne Titel liefert 400
    Given ich bin als "admin" mit Passwort "password123" in der Todo-API eingeloggt
    When ich ein neues Todo ohne Titel anlege
    Then ist der Statuscode 400

  @T-4009 @regression
  Scenario: Todo aktualisieren
    Given ich bin als "admin" mit Passwort "password123" in der Todo-API eingeloggt
    And ich ein neues Todo mit Titel "Altes Todo" angelegt habe
    When ich das Todo mit dem Titel "Aktualisiertes Todo" aktualisiere
    Then ist der Statuscode 200
    And enthält das JSON-Feld "title" mit String-Wert "Aktualisiertes Todo"

  @T-4010 @regression
  Scenario: Todo löschen
    Given ich bin als "admin" mit Passwort "password123" in der Todo-API eingeloggt
    And ich ein neues Todo mit Titel "Zu löschen" angelegt habe
    When ich das zuletzt angelegte Todo lösche
    Then ist der Statuscode 204
