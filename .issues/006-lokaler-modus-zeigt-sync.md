---
titel: Lokaler Modus zeigt „wartet auf Sync“ und „X Runden warten auf den Server“
typ: bug
schwere: mittel
bereich: Übersicht, Abend, Sync
status: offen
gefunden: 2026-09-27 (app_mode = LOCAL im DataStore)
---

## Beobachtung

Im Modus „Ohne Verein“ gibt es keinen Server. Trotzdem

- steht an jeder Runde „… - wartet auf Sync“,
- zeigt die Übersicht dauerhaft „7 Runden warten auf den Server. Gespielt und
  gerechnet wird trotzdem - der Sync holt das nach.“,
- plant jede neue Runde einen `SyncWorker` ein, der dann mit
  `NotJoinedException` still endet.

Für jemanden, der bewusst ohne Verein spielt, liest sich das wie ein Fehler,
der sich nie auflöst. Die Zahl zählt außerdem gelöschte Runden mit (7 statt 5
live, siehe #007).

## Ursache

`pendingSync` wird beim Speichern unabhängig vom Modus auf `true` gesetzt. Das
ist für die spätere Übernahme in einen Verein sinnvoll, die Oberfläche fragt
den Modus aber nicht ab.

## Vorschlag

- Die Sync-Hinweise in `OverviewScreen` und `RoundRow` nur bei
  `AppMode.CLUB` anzeigen.
- `SyncTrigger` im lokalen Modus nicht auslösen und
  `SyncWorker.schedulePeriodic` erst nach dem Beitritt registrieren.
- `observePendingSyncCount()` ohne Tombstones zählen oder in der Oberfläche
  getrennt ausweisen.
