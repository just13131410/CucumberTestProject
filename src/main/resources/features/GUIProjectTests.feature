# language: de
@e2e @GUI
Funktionalität: Vorgangs-Dashboard

  Grundlegende E2E-Tests für die Angular-Anwendung, einschließlich Login,
  Tabellen-Konfiguration und Filterung von Vorgängen.

  @smoketest
  Szenario: Erfolgreicher Login und Anzeige der Tabelle
    Gegeben sei ich öffne die Login-Seite
    Wenn ich mich mit Benutzername "admin" und Passwort "admin" einlogge
    Dann sollte ich das Dashboard sehen
    Und die Tabelle sollte mindestens eine Zeile mit Daten enthalten

  Szenario: Filterung nach UUID (Freitext)
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich im Filter für die UUID "123e4567" eingebe
    Und auf den Button "Suche starten" klicke
    Dann sollte die Tabelle genau 1 Zeile anzeigen

  Szenario: Filterung nach Datum (Kalender)
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich im Filter für das Datum "2026-04-01" auswähle
    Und auf den Button "Suche starten" klicke
    Dann sollte die Tabelle Zeilen vom "2026-04-01" anzeigen

  Szenario: Kombinierte Filterung (Typ und Status)
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich im Filter für den Typ "Bestellung" eingebe
    Und ich im Filter für den Status "In Bearbeitung" eingebe
    Und auf den Button "Suche starten" klicke
    Dann sollten in der Tabelle nur Zeilen mit dem Typ "Bestellung" und Status "In Bearbeitung" angezeigt werden

  Szenario: Spalten ein- und ausblenden
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich das Spalten-Konfigurations-Menü öffne
    Und die Spalte "UUID" deaktiviere
    Dann sollte die Spalte "UUID" nicht mehr in der Tabelle sichtbar sein

  @smoketest
  Szenario: Detailansicht eines Vorgangs öffnen
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich in der ersten Zeile auf den Detail-Button klicke
    Dann sollte ein Dialog mit dem Titel "Vorgangsdetails" erscheinen
    Und der Dialog sollte die UUID des Vorgangs anzeigen

  @smoketest
  Szenario: Sicherheit - Direkter Zugriff ohne Login
    Gegeben sei ich bin nicht eingeloggt
    Wenn ich versuche die URL "/dashboard" direkt aufzurufen
    Dann sollte ich zur Login-Seite weitergeleitet werden

  Szenario: Standard-Paginierung beim Laden
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Dann sollte die Tabelle genau 25 Zeilen anzeigen
    Und der Paginator sollte "1 – 25 von 120" anzeigen

  Szenario: Wechsel der Seitengröße auf 50
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich die Seitengröße auf 50 ändere
    Dann sollte die Tabelle genau 50 Zeilen anzeigen
    Und der Paginator sollte "1 – 50 von 120" anzeigen

  Szenario: Blättern zur nächsten Seite
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich auf die nächste Seite blättere
    Dann sollte der Paginator "26 – 50 von 120" anzeigen

  Szenario: Paginierung wird bei Suche zurückgesetzt
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Und ich blättere auf die nächste Seite
    Wenn ich im Filter für den Typ "Rechnung" eingebe
    Und auf den Button "Suche starten" klicke
    Dann sollte der Paginator wieder bei der ersten Seite beginnen

  @negativ
  Szenario: Filterung nach UUID ohne Treffer zeigt leere Tabelle
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich im Filter für die UUID "00000000-nicht-vorhanden" eingebe
    Und auf den Button "Suche starten" klicke
    Dann sollte die Tabelle 0 Zeilen anzeigen
    Und eine Meldung "Keine Ergebnisse gefunden" sollte sichtbar sein

  @negativ
  Szenario: Kombinierte Filterung ohne Treffer zeigt leere Tabelle
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich im Filter für den Typ "NichtExistierenderTyp" eingebe
    Und ich im Filter für den Status "NichtExistierenderStatus" eingebe
    Und auf den Button "Suche starten" klicke
    Dann sollte die Tabelle 0 Zeilen anzeigen
    Und eine Meldung "Keine Ergebnisse gefunden" sollte sichtbar sein

  @negativ
  Szenario: Filter zurücksetzen nach leerer Suche lädt alle Einträge
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich im Filter für die UUID "00000000-nicht-vorhanden" eingebe
    Und auf den Button "Suche starten" klicke
    Und die Tabelle 0 Zeilen anzeigt
    Und ich den Filter zurücksetze
    Dann sollte die Tabelle wieder mindestens eine Zeile mit Daten enthalten

  @negativ
  Szenario: Massenbearbeitung von Vorgängen
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich mehrere Vorgänge auswähle
    Und auf den Button "Massenänderung Status" klicke
    Und den Status "Abgeschlossen" auswähle
    Dann sollten alle ausgewählten Vorgänge den Status "Abgeschlossen" erhalten
  @negativ
  Szenario: Export von Vorgangsdaten nach Excel
    Gegeben sei ich bin auf dem Dashboard eingeloggt
    Wenn ich Filter für den Zeitraum "01.04.2026 - 02.04.2026" setze
    Und auf den Button "Daten exportieren" klicke
    Dann sollte eine Excel-Datei mit den gefilterten Daten generiert werden
  @negativ
  Szenario: Prüfung der Audit-Logs für sensible Änderungen
    Gegeben sei ich bin als Administrator eingeloggt
    Wenn ich einen kritischen Wert in einem Vorgang ändere
    Dann sollte ein entsprechender Eintrag im Audit-Log erstellt werden
