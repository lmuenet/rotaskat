# Scope

Die gemeinsame Zielvorstellung fuer Rotaskat. Entscheidungen hier sind
getroffen, nicht vorgeschlagen. Die Abrechnungsregeln stehen in
[SCORING.md](SCORING.md).

## Nutzungskontext

Eingabe passiert am Spieltisch, zwischen zwei Runden, oft einhaendig, in
einer Kneipe mit schlechtem Licht und schlechtem Empfang. Daraus folgt alles
Weitere: **Eingabegeschwindigkeit ist das wichtigste Qualitaetsmerkmal**, und
die App muss ohne Netz vollstaendig funktionieren.

## Getroffene Entscheidungen

### Erfassung

Ein Geraet fuehrt Buch. Die anderen Spieler sehen waehrend des Abends nichts;
nach dem Sync ist der Stand fuer alle abrufbar. Der Schreiber kann von Abend
zu Abend wechseln, das neue Geraet zieht die Historie dann vom Server.

Damit entfaellt jedes Konfliktmanagement. Der Server ist reiner Aggregator,
kein Merge-Punkt.

### Session und Rangliste

Eine Session ist ein Spielabend mit eigenem Endstand. Ranglisten gibt es
zweifach: **all-time** ueber alles, und **pro Saison**, damit ein frueher
Vorsprung sie nicht auf Jahre einbetoniert.

### Spieler

Fester Vereinskader, keine Gaeste. Wer nicht Mitglied ist, kann nicht erfasst
werden.

### Eingabemodell

Hybrid mit Schnellpfad. Das Tap-Budget fuer den Standardfall ist **vier
Taps**:

```
Alleinspieler  ->  Farbe/Grand  ->  Spitzen  ->  Gewonnen/Verloren
```

"Gewonnen" und "Verloren" sind gleichzeitig die Speichern-Buttons. Es gibt
keinen separaten Commit und keinen Bestaetigungsdialog; abgesichert wird das
ueber Undo statt ueber Rueckfragen.

Hand, Schneider, Schwarz, Ouvert, Kontra und Re liegen eingeklappt unter
"Zusaetze" mit einem Zaehler-Badge. Null ist ein Zwei-Tap-Sonderweg: vier
Kacheln mit 23/35/46/59, ohne Spitzen und Stufen.

Der **Spielwert wird berechnet, nie eingetippt**, und steht gross ueber den
Ergebnis-Buttons. Ein Reizwert allein ist mehrdeutig (36 ist Kreuz mit 2
genauso wie Karo mit 3) und wuerde das Kopfrechnen genau dorthin verlagern,
wo die klassische Fehlerquelle sitzt.

Weil `Modifiers.normalized()` Stufen impliziert (Ouvert erzwingt Hand und
Schwarz angesagt), muss die UI die mitgesetzten Schalter sofort sichtbar
umlegen. Sonst wirkt die berechnete Zahl falsch.

### Vierertisch

Der Geber sitzt aus. Die App rotiert nach jeder Runde automatisch weiter und
schlaegt den naechsten Aussetzenden vor; ein Tap korrigiert das. Der
Aussetzende bekommt 0 Punkte.

### Geld

Fester Cent-pro-Punkt-Satz am Verein. Am Ende jedes Abends rechnet die App
ab und zeigt, wer wem was schuldet, mit **minimaler Anzahl Zahlungen** statt
jeder mit jedem. Danach ist die Rechnung glatt, es gibt keinen laufenden
Kontostand.

## v1

Muss drin sein:

- Rundeneingabe mit laufendem Stand
- Letzte Runde korrigieren und Undo
- Geldabrechnung am Abendende
- Punkteverlauf als Diagramm ueber die Runden eines Abends
- Spieler-Statistiken
- All-time- und Saison-Rangliste
- Sync zum Server

Bewusst spaeter: Live-Mitlesen auf mehreren Geraeten, Gastspieler,
Turnier-/Zeitraumverwaltung, Export.

## Architekturregeln

**Rohdaten, keine Punkte.** Gespeichert werden die Spielfakten. `round_score`
ist abgeleitet und jederzeit neu berechenbar. Aendert sich eine Hausregel,
wird die Historie neu gerechnet statt unrettbar zu sein.

**Der Server rechnet nach.** Er verwirft die vom Client gelieferten Punkte
und berechnet sie mit `:shared` neu. Die Client-Werte werden als Pruefsumme
mitgeschickt und Abweichungen protokolliert. Das ist ein kostenloser
Kanarienvogel fuer Versionsdrift zwischen APK und Server.

**ScoringConfig wird pro Session eingefroren.** Sonst sieht der Spieler am
Tisch andere Zahlen als spaeter in der Rangliste, und das Vertrauen in die
App ist sofort weg.

**Runden sind unveraenderlich, das Log ist append-only.** Kein CRDT, kein
Last-Write-Wins auf Sessionebene: die Abrechnung ist eine Summe, und Summen
sind kommutativ. Der Konflikt, den ein CRDT loesen wuerde, existiert hier
nicht. Korrekturen sind neue Revisionen, Loeschen ist ein `deleted_at`-
Tombstone.

**Idempotenz ueber clientseitig erzeugte UUIDs** als Primaerschluessel,
zeitsortiert (UUIDv7), plus `ON CONFLICT (id) DO UPDATE WHERE
excluded.revision > round.revision`. Gleiche Revision mit abweichendem Inhalt
ist ein Konflikt und wird gemeldet, nicht still ueberschrieben.

**Delta-Pull ueber einen serverseitigen Sequenzzaehler**, nicht ueber
Zeitstempel. Handyuhren laufen auseinander.

**Halbe Punkte durchgehend ganzzahlig**, in Postgres `BIGINT`, nie `numeric`
oder `float`.

## Bekannte Fallen

**Die Nullsummen-Invariante ist overflow-blind.** Int rechnet modulo 2^32,
`2V - V - V` ergibt auch bei voelligem Unsinn exakt 0. Sie faengt
Verteilungsfehler, aber keine Ueberlaeufe. Deshalb braucht es zusaetzlich
Wertebereichspruefungen (siehe SCORING.md) und eine Referenzrechnung in Long.

**Weitere noetige Invarianten** ueber die Nullsumme hinaus: Sitzplatz-
Permutation liefert permutierte Ergebnisse (findet hartkodierte Indizes), der
Aussetzende bekommt immer exakt 0, `activeSeats` hat immer genau 3 Eintraege.

**Der groesste Zeitfresser am Tisch ist nicht die Eingabe**, sondern das
Aufwecken und Entsperren des Handys zwischen den Runden. `keepScreenOn`
waehrend einer laufenden Session spart mehr Sekunden als jede
Tap-Optimierung.

## Nachnutzbare Quellen

| Quelle | Lizenz | Wofuer |
|--------|--------|--------|
| [b0n541/jskat](https://github.com/b0n541/jskat) | Apache-2.0 | Der einzige oeffentliche, jahrelang gehaertete Testsatz fuer die Skatordnung. Testvektoren portieren, mit Attribution in einer NOTICE-Datei. Abgleichpunkt: JSkat zaehlt Schneider gespielt und Schneider angesagt bei Hand getrennt - genau da brechen Eigenimplementierungen. |
| [android/nowinandroid](https://github.com/android/nowinandroid) | Apache-2.0 | Modulstruktur, Compose/M3, Sync-Muster. Grenze: synchronisiert nur remote nach lokal, unser Write-Sync kommt dort nicht vor. |
| [r0adkll/sign-android-release](https://github.com/r0adkll/sign-android-release) | MIT | Signierte APK in Actions. Besser aber: Gradle-`signingConfig` mit Env-Variablen, dann verhaelt sich der lokale Release-Build identisch zur CI. |

Die ISkO des DSkV ist urheberrechtlich geschuetzt und wird nur nachgeschlagen,
nicht ins Repo kopiert. Sie kennt uebrigens gar keinen Ramsch - unsere
Ramsch-Abrechnung ist zwangslaeufig Hausregel und gehoert genau deshalb in
`ScoringConfig`.

## Teststrategie

Der Raum aller legalen Ansagen ist winzig, einige tausend Faelle. Er wird
**erschoepfend durchgezaehlt** statt zufaellig bestichprobt. Dazu eine
handgeprueft eingecheckte Referenztabelle der Skatordnung als CSV, die nie
automatisch regeneriert wird.

Property-Tests nur dort, wo der Raum gross ist: Rundenlisten, Sitzordnungen,
Serialisierungs-Roundtrip.

CI in zwei Jobs: ein schneller Push-Job unter zwei Minuten (`:shared:test`,
`:server:test`, Linting, ohne Android-Toolchain), und ein voller Job auf PR
und nachts (Room-Migrationstests via Robolectric, Testcontainers,
`assembleDebug`). Emulator-basierte Laeufe bleiben aussen vor.
