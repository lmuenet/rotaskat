# Abrechnung

Die verbindliche Beschreibung der Rotaskat-Hausregeln. Die Implementierung
liegt in `shared/src/main/kotlin/io/rotaskat/shared/scoring/Scoring.kt`, die
Tests in `shared/src/test/kotlin/io/rotaskat/shared/scoring/ScoringTest.kt`.

## Halbe Punkte

Alle Punkte werden intern als **ganzzahlige halbe Punkte** gespeichert:

```
gespeicherter Wert = tatsaechliche Punkte * 2
```

Grund: die Gegenspieler bekommen den halben Spielwert. Bei ungeradem Wert
(etwa Null mit 23) waere das ein Bruch. In halben Punkten bleibt alles
ganzzahlig, die Nullsumme exakt, und es gibt ueber tausende Runden keinen
Rundungsdrift. Die Umrechnung passiert erst in der Anzeige.

## Spielwert

Der Spielwert `V` ist der offizielle Reizwert nach Skatordnung.

**Farbspiel und Grand**

```
V = Grundwert * Spielstufe
Spielstufe = Spitzen + 1 (fuer das Spiel) + Anzahl der Zusatzstufen
```

| Spiel | Grundwert |
|-------|-----------|
| Karo  | 9  |
| Herz  | 10 |
| Pik   | 11 |
| Kreuz | 12 |
| Grand | 24 |

Zusatzstufen, je +1: Hand, Schneider, Schneider angesagt, Schwarz,
Schwarz angesagt, Ouvert.

Die Skatordnung impliziert dabei einiges, was `Modifiers.normalized()`
automatisch setzt: Ouvert erzwingt Hand und Schwarz angesagt, Schwarz
angesagt erzwingt Schneider angesagt, Schneider angesagt erzwingt Hand und
Schneider. Ein Grand ouvert mit 4 kommt so auf Stufe 11 und damit auf 264.

**Nullspiele** haben feste Werte, unabhaengig von Spitzen:

| Variante          | Wert |
|-------------------|------|
| Null              | 23 |
| Null Hand         | 35 |
| Null Ouvert       | 46 |
| Null Hand Ouvert  | 59 |

**Kontra** verdoppelt `V`, **Re** vervierfacht es.

**Ueberreizt**: hat sich der Alleinspieler ueberreizt, gilt das Spiel immer
als verloren, und gerechnet wird mit dem kleinsten Vielfachen des Grundwerts,
das den Reizwert erreicht. Beispiel: Karo mit 1 ist 18 wert, gereizt wurde 20,
abgerechnet wird 27.

## Verteilung

Der Kern der Hausregel. Mit Spielwert `V`:

| Ausgang | Alleinspieler | Jeder Gegenspieler |
|---------|---------------|--------------------|
| gewonnen | `+V`  | `-V/2` |
| verloren | `-2V` | `+V`   |

In halben Punkten, also so wie gespeichert:

| Ausgang | Alleinspieler | Jeder Gegenspieler |
|---------|---------------|--------------------|
| gewonnen | `+2V` | `-V`  |
| verloren | `-4V` | `+2V` |

## Ramsch und Schieberamsch

Es gibt keinen Alleinspieler. Der Spieler mit den **meisten Augen** ist der
Verlierer. Mit seiner Augenzahl `A`:

| Rolle | Punkte | halbe Punkte |
|-------|--------|--------------|
| Verlierer      | `-A`   | `-2A` |
| Die anderen beiden | `+A/2` | `+A` |

Damit ist der Ramsch genauso nullsummig wie ein normales Spiel.

**Jungfrau** (ein Spieler hat keinen Stich bekommen) verdoppelt `A`.

**Jeder Schub** beim Schieberamsch verdoppelt die Augen: `A * 2^Schuebe`.
Umschaltbar ueber `ScoringConfig.pushDoubles`, voreingestellt auf `true`.
Die Anzahl der Schuebe ist auf `MAX_PUSHES` = 5 begrenzt. Das ist keine
Spielregel, sondern ein Ueberlaufschutz: `1 shl pushes` nutzt in Kotlin nur
die unteren 5 Bit, `1 shl 32` waere also wieder `1`. Ohne Grenze waere das
Ergebnis still falsch **und trotzdem nullsummig** und damit durch die
Invariante nicht zu entdecken.

**Durchmarsch** (ein Spieler bekommt alle Stiche) zaehlt 120, also alle Augen,
und wird nach der normalen Verteilung abgerechnet: `+120` fuer ihn, `-60` fuer
die anderen beiden. Umschaltbar ueber `ScoringConfig.durchmarschValue`.

**Augengleichstand**: haben zwei Spieler gleich viele Augen, entscheidet der
Tisch. Die App fragt in diesem Fall kurz nach, wer als Verlierer gilt, und
speichert die Antwort als `loserSeat`. Es gibt bewusst keine automatische
Regel dafuer.

## Vierertisch

Bei vier Spielern setzt einer aus. Der Aussetzende bekommt **0 Punkte**, die
Verteilung laeuft unveraendert zwischen den drei aktiven Spielern.

## Die zentrale Invariante

**Jede Runde summiert sich ueber alle Sitzplaetze zu genau 0.**

Das gilt fuer alle Spielarten:

```
gewonnen:    +2V -V -V          = 0
verloren:    -4V +2V +2V        = 0
Ramsch:      -2A +A +A          = 0
Durchmarsch: +2V -V -V          = 0
```

Die Invariante ist an drei Stellen abgesichert: als Property-Test ueber alle
Spielarten und Augenzahlen, als `RoundScore.sum()` im Code, und als
`DEFERRABLE` Constraint-Trigger auf `round_score` in der Datenbank. Sie ist
das wirksamste Netz gegen stille Rechenfehler in der All-Time-Rangliste.

## Geldabrechnung

Am Abendende werden die halben Punkte aller Spieler mit dem Cent-pro-Punkt-Satz
des Vereins in Cent umgerechnet. Die Implementierung liegt in
`shared/src/main/kotlin/io/rotaskat/shared/settlement/Settlement.kt`.

Gerechnet wird in ganzen Cent und in `Long`, nie in `Double`. Ein halber Punkt
ist bei ungeradem Satz ein halber Cent, deshalb laeuft die Zwischenrechnung in
**halben Cent** und wird erst am Ende abgerundet; die dabei entstehenden
Restcent werden in stabiler Sitzreihenfolge wieder verteilt. Wuerde stattdessen
jeder Spieler einzeln gerundet, ginge die Summe um ein paar Cent daneben und
die Zahlungen wuerden nicht mehr aufgehen.

Ausgeglichen wird mit **minimaler Anzahl Zahlungen**: der groesste Glaeubiger
bekommt vom groessten Schuldner, bis einer von beiden auf null ist. Jeder
Schritt streicht mindestens einen Spieler, es bleiben also hoechstens n-1
Zahlungen. Zwei Zusicherungen gelten immer: die Summe aller Salden ist 0, und
jeder Spieler landet durch die Zahlungen exakt auf seinem Saldo.

Ist die Summe der halben Punkte nicht 0, wird die Abrechnung abgelehnt statt
schief gerechnet - dann ist eine Runde kaputt.

## Vierertisch und Geber

`Round.dealerSeat` haelt fest, wer gegeben hat. Am Vierertisch ist das
derselbe Sitzplatz wie `sittingOutSeat`, und `validate()` besteht darauf:
sonst rotiert die App an einer anderen Stellung weiter, als abgerechnet
wurde. Die Rotation selbst steht in `TableRotation` und wandert einen Platz
weiter; ein Tap am Tisch setzt den Geber direkt.

## Warum Rohdaten gespeichert werden

In der Datenbank steht nicht `punkte = -54`, sondern die Spielfakten:
Alleinspieler, Sitzordnung, Spielart, Spitzen, Stufen, gewonnen ja/nein,
Kontra/Re, Ramsch-Details. Die Punkte in `round_score` sind daraus abgeleitet.

Wenn sich also in Runde 400 herausstellt, dass eine Hausregel anders gemeint
war, wird `round_score` neu berechnet und die Historie stimmt wieder. Waeren
nur die Punkte gespeichert, waere die Rangliste unrettbar.

## Wertebereiche

Damit still falsche Ergebnisse nicht in der Historie landen, prueft
`Scoring.validate()` die Rohdaten, bevor gerechnet wird:

| Feld | Bereich | Warum |
|------|---------|-------|
| `seatCount` | 3 oder 4 | Skat wird zu dritt gespielt |
| `bid` | 18 bis 264 | 18 ist das niedrigste Gebot, 264 der hoechste Spielwert (Grand ouvert mit 4) |
| `cardPoints` | 0 bis 120 | Augen einer Partei |
| `pushes` | 0 bis 5 | Ueberlaufschutz, siehe oben |
| `matadors` (Farbe) | 1 bis 11 | vier Buben plus sieben Trumpfkarten der Farbe |
| `matadors` (Grand) | 1 bis 4 | beim Grand sind nur die vier Buben Spitzen |

Die Obergrenze fuer `matadors` ist der zweite Ueberlaufschutz neben `pushes`:
ohne sie multipliziert `gameValue()` Grundwert mal Spielstufe in `Int`, und ein
gereichtes `matadors` in Millionenhoehe kippt den Spielwert ins Negative -
nullsummig und damit fuer jede Invariante unsichtbar. Sie ist zugleich eine
Regelaussage: einen Grand mit 7 gibt es nicht. Jede Multiplikation der
Abrechnung laeuft deshalb zusaetzlich als Referenzrechnung durch `Long` und
wird gegen den `Int`-Bereich geprueft, statt still zu wrappen.

Ohne die `bid`-Grenze konnte `overbidValue()` in eine Endlosschleife laufen:
frueher wurde in Schritten des Grundwerts hochgezaehlt, was bei einem sehr
grossen Gebot ins Int-Overflow lief und die Abbruchbedingung nie erfuellte.
Heute wird aufrundend dividiert, mit Long als Zwischenrechnung.

## Was es bewusst nicht gibt

Keine Bockrunden und keine Ramschrunden. Jede Runde zaehlt gleich, es gibt
keine Runden mit erhoehter Wertung nach bestimmten Ereignissen.

Keine feste Rundenzahl pro Abend. Gespielt wird, bis Schluss ist.
