Feature: Erste API Prüfung
  Scenario: GET /posts/1 liefert 200 und id == 1
    Given API Basis-URL "https://jsonplaceholder.typicode.com"
    When ich GET an "/posts/1" ausführe
    Then ist der Statuscode 200
      And enthält das JSON-Feld "id" mit Wert 1