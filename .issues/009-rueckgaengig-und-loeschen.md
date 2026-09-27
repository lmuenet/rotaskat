---
titel: Rückgängig-Leiste - kurz, verdeckt die Ergebnisbuttons, nach dem Löschen bleibt die Korrektur aktiv
typ: UX
schwere: mittel
bereich: Abend, Snackbar, Undo
status: offen
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
