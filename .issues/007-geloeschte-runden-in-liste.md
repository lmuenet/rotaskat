---
titel: Gelöschte und zurückgenommene Runden bleiben in der Liste stehen, Nummerierung bekommt Lücken
typ: UX
schwere: mittel
bereich: Abend (RoundHistory)
status: offen
gefunden: 2026-09-27
---

## Beobachtung

- Eine per „Rückgängig“ zurückgenommene Runde verschwindet nicht, sondern bleibt
  als „4 · Herz mit 1 · Alex - gewonnen - geloescht - wartet auf Sync · -“ in
  der Liste stehen. Dasselbe gilt für Runden, die über „Runde loeschen“ entfernt
  wurden.
- Die nächste Runde bekommt trotzdem die nächste Nummer: Nach dem Rückgängig
  von Runde 4 heißt die neue Runde 5. Die Überschrift sagt „Runden (5)“,
  darunter stehen 7 Zeilen mit Nummern bis 7.
- Beim „Rückgängig“ direkt nach dem Speichern ist das besonders irritierend.
  Aus Nutzersicht wurde die Runde nie gespielt.

Der Tombstone ist für den Sync richtig (SCOPE.md: „Loeschen ist ein
`deleted_at`-Tombstone“), gehört aber nicht in die Anzeige am Tisch.

## Vorschlag

- `RoundHistory` zeigt nur `liveRounds`. Gelöschte Runden höchstens
  eingeklappt unter „1 gelöschte Runde“ anzeigen.
- Die Anzeige-Nummer aus der Position in `liveRounds` ableiten, nicht aus
  `sequence`. `sequence` bleibt als interne Reihenfolge erhalten.
- Ein Undo direkt nach dem Speichern, bevor die Runde synchronisiert wurde,
  kann die Runde lokal ganz entfernen statt einen Tombstone anzulegen.
  Dabei den Sync-Vertrag prüfen.
