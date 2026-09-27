---
titel: Überreizt - Gebot startet bei 18 statt über dem Spielwert, „Gewonnen“ bleibt aktiv
typ: bug
schwere: mittel
bereich: Rundeneingabe, RoundDraft
status: erledigt
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

## Umsetzung

- Beim Einschalten steht der kleinste Reizwert über dem Spielwert, das Raster
  zeigt nur Werte darüber, das Gebot wird nachgezogen, wenn Spitzen oder
  Zusätze den Spielwert heben (`RoundDraft.withOverbid`, `withBidAboveValue`).
- Bei Überreizung gibt es nur noch den Button „Überreizt – verloren“.
- **Nicht** umgesetzt: die Regel in `Scoring.validate()`. `Scoring.score()`
  prüft `validate()` per `require` bei jedem Lesen. Bereits gespeicherte
  Runden mit Gebot ≤ Spielwert (im Gerätetest ist so eine entstanden) würden
  dann beim Öffnen des Abends eine Exception werfen, und der Server würde sie
  beim Sync ablehnen. Abgesichert ist es stattdessen in der Eingabe
  (`readyForResult`). Falls die Regel serverseitig gewünscht ist, braucht es
  vorher eine Migration bzw. eine Validierung nur für neue Revisionen.
