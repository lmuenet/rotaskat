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

**Durchmarsch** (ein Spieler bekommt alle Stiche) wird wie ein gewonnener
Grand gewertet, also nach der normalen Verteilung: `+V` fuer ihn, `-V/2` fuer
die anderen. Der Wert steht in `ScoringConfig.durchmarschValue`.

Ob jeder **Schub** beim Schieberamsch die Augen verdoppelt, steht in
`ScoringConfig.pushDoubles` und ist derzeit auf `false` voreingestellt.

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

## Warum Rohdaten gespeichert werden

In der Datenbank steht nicht `punkte = -54`, sondern die Spielfakten:
Alleinspieler, Sitzordnung, Spielart, Spitzen, Stufen, gewonnen ja/nein,
Kontra/Re, Ramsch-Details. Die Punkte in `round_score` sind daraus abgeleitet.

Wenn sich also in Runde 400 herausstellt, dass eine Hausregel anders gemeint
war, wird `round_score` neu berechnet und die Historie stimmt wieder. Waeren
nur die Punkte gespeichert, waere die Rangliste unrettbar.

## Offene Punkte

- Wert eines Durchmarsch: aktuell 120, noch nicht bestaetigt.
- Verdoppeln Schuebe beim Schieberamsch die Augen? Aktuell nein.
- Augengleichstand im Ramsch: wer verliert, wenn zwei Spieler gleich viele
  Augen haben?
- Zaehlt Kontra/Re bei euch ueberhaupt, und gibt es Bock- oder Ramschrunden?
