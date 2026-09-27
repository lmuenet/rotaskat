---
titel: Beendeter Abend - Runden sind antippbar und führen in einen leeren Korrekturbildschirm
typ: bug
schwere: mittel
bereich: Abend, Navigation
status: offen
gefunden: 2026-09-27
---

## Beobachtung

Nach „Abend beenden“ bleibt die Rundenliste antippbar. Ein Tap öffnet
„Runde korrigieren“. Dort stehen nur der Stand und der Satz „Dieser Abend ist
beendet. Neue Runden gibt es nicht mehr.“, keine Eingabe, kein Löschen. Der
Bildschirm ist eine Sackgasse.

Dazu passend:

- Im beendeten Abend steht bei einem Spieler noch „gibt“. Das ist nach dem
  Abend bedeutungslos.
- Nach „Beenden“ bleibt die App auf dem Abend stehen. Der nächste Schritt ist
  fast immer die Abrechnung, dafür muss man erst oben rechts „Abrechnung“
  antippen.
- Ein Tippfehler, der erst beim Abrechnen auffällt, lässt sich nicht mehr
  korrigieren: Einen Weg, den Abend wieder zu öffnen, gibt es nicht. Der
  Bestätigungsdialog warnt zwar, am Tisch fällt so etwas aber typischerweise erst
  beim Bezahlen auf.

## Reproduktion

1. Abend mit einigen Runden beenden.
2. Im beendeten Abend eine Runde antippen → leerer Bildschirm „Runde korrigieren“.

## Vorschlag

- In `RoundRow` bei `SessionStatus.CLOSED` `enabled = false` setzen, alternativ
  eine reine Detailansicht der Runde öffnen.
- „gibt“ im Scoreboard nur bei offenem Abend anzeigen.
- Nach `endSession()` direkt `actions.toSettlement(sessionId)` aufrufen und den
  Abend im Back-Stack ersetzen.
- Entscheiden, ob „Abend wieder öffnen“ erlaubt sein soll, etwa nur am selben
  Tag oder nur, solange nicht synchronisiert wurde. Wenn nein, das im Dialog
  deutlicher sagen.
