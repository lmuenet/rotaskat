---
titel: Rückgängig-Leiste - kurz, verdeckt die Ergebnisbuttons, nach dem Löschen bleibt die Korrektur aktiv
typ: UX
schwere: mittel
bereich: Abend, Snackbar, Undo
status: erledigt
gefunden: 2026-09-27
---

## Beobachtung

Undo ist laut SCOPE.md **das** Sicherheitsnetz, weil es keine Rückfragen gibt.
Auf dem Gerät gilt dafür:

- Die Leiste steht mit `SnackbarDuration.Short` nur etwa 4 Sekunden da. Am Tisch
  schaut man nach dem Tap auf die Karten, nicht aufs Handy. Wer den Fehler
  beim nächsten Blick bemerkt, hat kein Undo mehr und muss über die Rundenliste
  korrigieren.
- Die Leiste liegt direkt über „Gewonnen“/„Verloren“ und ist hell auf dunklem
  Grund. „Rückgängig“ liegt genau dort, wo der nächste „Verloren“-Tap landen
  würde. Der Kommentar im Code kennt das Risiko, gelöst ist es nur durch die
  kurze Dauer.
- Die Meldung sagt nur „Gewonnen gespeichert“, nicht wer und was. Man kann
  nicht prüfen, ob die richtige Runde gespeichert wurde.
- Nach „Runde loeschen“ im Korrekturmodus bleibt der Korrekturbildschirm bis zum
  Ablauf der Leiste stehen, mit aktivem Speichern-Button für die gerade
  gelöschte Runde (`SessionScreen`: `if (editRoundId != null) actions.back()`
  erst nach der Snackbar).

## Vorschlag

- Undo nicht an eine flüchtige Snackbar binden, sondern an eine dauerhafte
  Zeile „Letzte Runde: Johannes · Kreuz mit 2 · +36 [Rückgängig]“ oberhalb der
  Rundenliste, die bis zur nächsten Runde stehen bleibt.
- Falls die Snackbar bleibt: `Long`, oberhalb der Ergebnisleiste
  positionieren, Inhalt mit Spieler und Punkten.
- Nach dem Löschen sofort zurücknavigieren und das Undo im Abend-Bildschirm
  anbieten.

## Umsetzung

- Keine Undo-Snackbar mehr. Über der Eingabe steht dauerhaft eine Zeile
  „Gespeichert: Anna · Kreuz mit 2 · gewonnen  +36  [Rückgängig]“ (bzw.
  „Geändert“, „Gelöscht“, nach dem Undo „Zurückgenommen“), bis die nächste
  Änderung sie ersetzt. Sie liegt weit weg von den Ergebnisbuttons.
- Korrektur und Löschen führen sofort zurück in den Abend; das Undo steht dort.
  Dafür teilen sich Abend- und Korrektur-ViewModel ein prozessweites
  `SessionUndoLog` (nur im Speicher).
- Nebenbei (#010): Während des Speicherns wird der Entwurf nicht mehr von der
  neuen Rotation umgehängt, Entwurf und Anzeige wechseln in einem Schritt;
  Doppeltaps während des Speicherns werden ignoriert.
