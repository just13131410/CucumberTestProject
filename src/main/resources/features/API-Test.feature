@T-3511 @Backend @SmokeTest
Feature: Erste API Prüfung
  Scenario: GET /posts/1 liefert 200 und id == 1
    Given API Basis-URL ist gesetzt
    When ich GET an "/posts/1" ausführe
    Then ist der Statuscode 200
      And enthält das JSON-Feld "id" mit Wert 1

  @T-3512 @Backend @SmokeTest
  Scenario: Response.pdf und eingebettetes XML stimmen mit GET /posts/1 überein
    Given API Basis-URL ist gesetzt
    When ich GET an "/posts/1" ausführe
    Then ist der Statuscode 200
      And die Datei "Response.pdf" enthält die Felder der API-Antwort
      And das eingebettete XML in "Response.pdf" stimmt mit der API-Antwort überein