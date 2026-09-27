---
titel: Überreizt - Gebot startet bei 18 statt über dem Spielwert, „Gewonnen“ bleibt aktiv
typ: bug
schwere: mittel
bereich: Rundeneingabe, RoundDraft
status: offen
gefunden: 2026-09-27
---

## Beobachtung

Beim Einschalten von „Ueberreizt“ ist unter „Gereizt bis“ immer **18**
vorausgewählt (`RoundDraft.bid = Scoring.MIN_BID`), egal wie hoch der Spielwert
ist. Beispiel: Kreuz mit 2 Ouvert (108), „Ueberreizt“ an → die Beschreibung
lautet „… = 108, ueberreizt auf 18 = 108“. Das ist keine Überreizung, lässt
sich aber so speichern.

Außerdem:

- Das Raster zeigt alle Reizwerte ab 18, auch die **unter oder gleich** dem
  Spielwert. Eine Überreizung ist nur mit einem Gebot oberhalb möglich.
- „Gewonnen“ und „Verloren“ zeigen beide denselben Verlust (z. B. −54) und
  speichern beide eine verlorene Runde. Zwei Buttons mit identischer Wirkung
  verwirren genau an der Stelle, an der schnell getippt wird.

Die Berechnung selbst stimmt: Karo mit 1, gereizt 20, ergibt 27 bzw. −54, wie
in SCORING.md beschrieben.

## Vorschlag

- Beim Einschalten den kleinsten Reizwert oberhalb des aktuellen Spielwerts
  vorauswählen und Werte ≤ Spielwert im Raster ausblenden oder deaktivieren.
- Ändert sich der Spielwert danach (Spitzen/Zusätze), das Gebot nachziehen,
  wenn es nicht mehr darüber liegt.
- `Scoring.validate()` um „overbid ⇒ bid > gameValue“ ergänzen, damit auch der
  Server solche Runden ablehnt.
- Bei aktiver Überreizung nur einen Button „Überreizt – verloren“ anzeigen.
