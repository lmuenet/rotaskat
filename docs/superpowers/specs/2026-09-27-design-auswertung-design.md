# Design "Kartentisch" - Teil 2: Auswertung, Einstellungen, Einstieg

Stand: 27.09.2026. Baut auf Teil 1 auf
(`docs/superpowers/specs/2026-09-27-design-kartentisch-design.md`), dessen
Grundwerte, Bausteine und Regeln unveraendert gelten. Offene Punkte stammen
aus `.issues/DESIGN-NOTIZEN.md` (Abschnitt "Stand nach Design-Runde Teil 1").
Keine Aenderung an Spiellogik, Datenschicht oder `:shared`.

## Ziel

Die Auswertung liest sich so schnell wie der Abend-Bildschirm: Zahlen
zuerst, Erklaerungen nur auf Nachfrage.

- Kein erklaerender Absatz steht mehr dauerhaft ueber einer Tabelle; was
  wirklich hilft, steht hinter einem ⓘ oder als kurze Warnzeile an der Zahl.
- Im Punkteverlauf ist jede Linie ohne Legende einem Spieler zuzuordnen.
- Alle Linienfarben erreichen 3:1 gegen den Grund.
- Einstellungen, Einstieg, Beitritt und "Neuer Abend" folgen demselben Look.
- Die geparkten Reste aus Teil 1 sind erledigt.

## Getroffene Entscheidungen

| Frage | Entscheidung |
|---|---|
| Erklaertexte | Kuerzen; was bleibt, hinter ein ⓘ neben der Abschnittsueberschrift (oeffnet ein Sheet) oder als Warnzeile in der Kachel |
| Punkteverlauf | Kraeftige, durchgezogene Linien, Name und Stand am Linienende statt Legende |
| Statistik | Zeitraum- und Spieler-Chips, darunter ein 2-spaltiges Raster aus Kennzahl-Kacheln |
| Zahlungen | Bleiben ein Satz ("Lars zahlt an Johannes"), kein Pfeil - ein Pfeil laesst sich am Tisch in beide Richtungen lesen (bestehende Entscheidung in `SettlementScreen.kt`) |

## Umfang

Punkteverlauf, Rangliste, Statistik, Abrechnung, Einstellungen, Einstieg
(Onboarding, "Ohne Verein", Beitritt), "Neuer Abend", sowie die Reste aus
Teil 1 (Abschnitt 7). Nicht im Umfang: Querformat, Tablet, grosse Schrift,
neue Funktionen.

## 1. Grundwerte

- `RotaskatScoreColors.neutral` wird warmgrau `#B5AC9C` (8,2:1 auf dem Grund)
  statt des violettstichigen `#B6AFBC`.
- Linienfarben (`RotaskatSeriesStyles`), in Sitzplatzreihenfolge:
  Gold `#F2C46B`, Himmelblau `#7FC4F0`, Flieder `#C8A8FF`, Elfenbein
  `#EDE6D8`. Keine Strichmuster mehr (`SeriesStyle.dash` entfaellt); der
  zweite Kanal ist die Beschriftung am Linienende. Gruen und Rot kommen
  weiterhin nicht vor.
- `PaletteContrastTest` bekommt: jede Linienfarbe gegen `background` >= 3:1,
  `neutral` gegen `background` >= 4,5:1.
- Kachelrand: bleibt `outlineVariant` (1,5:1). Klarstellung fuer beide
  Specs: Kacheln sind ueber Flaeche und Beschriftung erkennbar (WCAG 1.4.11
  verlangt fuer sie keinen 3:1-Rand); 3:1 gilt fuer den Auswahlrand,
  Farbsymbole, Linien und den gestrichelten Umriss.

## 2. Bausteine (`ui/common`, `ui/eval`)

### `SectionHeader(title, info = null)`

Abschnittsueberschrift in `RotaskatTextStyles.sectionLabel`/`labelMuted`
wie `SectionLabel`, rechts optional ein ⓘ (`Icons.Outlined.Info`, 20 dp,
Tap-Flaeche 48 dp, `contentDescription = "Erklaerung zu <title>"`). Tap
oeffnet ein `InfoSheet`: `ModalBottomSheet` mit Titel (`titleMedium`) und dem
Text (`bodyLarge`), voll aufgeklappt. Ersetzt `EvalSection(note = ...)`:
`EvalSection` bekommt statt `note` den Parameter `info: String?` und rendert
`SectionHeader`.

### `StatTile`

Ersetzt `StatCard`. Flaeche `surfaceContainer`, Rand 1 dp `outlineVariant`,
Ecken `cardCorner`. Oben das Label (`sectionLabel`, `labelMuted`), darunter
der Wert gross (`RotaskatTextStyles.scoreLarge`, Farbe nach Vorzeichen, wenn
es eine Punktzahl ist, sonst `onSurface`), optional ein Icon links vom Wert
(Farbsymbol), darunter die Detailzeile (`labelSmall`, `onSurfaceVariant`),
optional eine Warnzeile: `Icons.Filled.Warning` (14 dp, `primary`) plus
Text in `labelSmall`/`primary` ("Unter 5 Alleinspielen wenig
aussagekraeftig"). Kein Textzeichen ⚠ - Android zeichnet es als Emoji (siehe
Farbsymbole in Teil 1). Mindesthoehe
112 dp, damit Kacheln einer Reihe gleich hoch wirken (`Modifier.height(IntrinsicSize.Min)` in der Reihe).

### `ChipRow`

Eine waagerecht scrollende Reihe von `OptionTile`s (Hoehe `tapTarget`,
Mindestbreite 88 dp, Breite nach Text), `Modifier.selectableGroup()`.
Verwendet fuer Zeitraum (`PeriodSelector`) und Spielerwahl. Ersetzt das
3-spaltige Raster in `PeriodSelector`.

### Semantik

- `OptionGrid` setzt `Modifier.selectableGroup()` auf die Spalte.
- `OptionTile` bekommt `role: Role = Role.RadioButton`; die Spitzen-Kachel
  "mehr"/"weniger" nutzt `Role.Button` und meldet kein `selected`, solange sie
  nur aufklappt (sie ist `selected`, wenn der gewaehlte Wert > 4 ist - dann
  bleibt die Rolle RadioButton).

## 3. Punkteverlauf (`eval/PointsChart.kt`, `eval/ProgressScreen.kt`)

- Linien 3 dp, runde Enden und Ecken, Endpunkt als Kreis 4,5 dp.
- Beschriftung am Linienende: "<Name> <Stand>" in der Linienfarbe,
  `labelLarge` (Barlow 16 sp). Rechts vom Plot wird so viel Platz reserviert,
  wie die breiteste Beschriftung braucht (gemessen), hoechstens 45 % der
  Breite; laengere Namen werden mit Ellipse gekuerzt.
- Kollisionen: Die Wunsch-y-Position jeder Beschriftung ist die y-Position
  ihres Linienendes. Eine reine Funktion
  `labelPositions(desired: List<Float>, minGap: Float, top: Float, bottom: Float): List<Float>`
  verteilt sie so, dass zwei Beschriftungen mindestens `minGap` (=
  Zeilenhoehe) auseinanderliegen, die Reihenfolge der Wunschpositionen
  erhalten bleibt, alle innerhalb `[top, bottom]` liegen und die
  Gesamtverschiebung klein bleibt (Verfahren: sortieren, von oben nach unten
  nach unten schieben, dann vom unteren Rand her nach oben zurueckschieben).
  Ist eine Beschriftung verschoben, verbindet eine duenne Linie (1 dp, 50 %
  Deckkraft der Linienfarbe) Endpunkt und Beschriftung.
- x-Achse: unter den Rundennummern steht "Runde" (`labelSmall`,
  `onSurfaceVariant`), rechtsbuendig unter dem Plot.
- Raster wie bisher, Nulllinie kraeftiger.
- Semantik: Das Canvas bekommt `contentDescription`
  "Punkteverlauf ueber N Runden: Johannes +36, Alex +0, Lars -18, Niko -18"
  (nach Stand absteigend).
- `ChartLegend` entfaellt.
- Leerzustand: "Noch keine Runde gespielt."
- `ProgressScreen`: Abschnitt "Verlauf" ohne Erklaertext (die Achsen sind
  beschriftet), Abschnitt "Stand" mit Anzahl der Runden als ⓘ-freie
  Unterzeile wie bisher; Kopfzeilen-Aktion "Abrechnung" wird Icon `ic_euro`
  mit `contentDescription = "Abrechnung"`.

## 4. Rangliste (`eval/LeaderboardScreen.kt`, `common/Standings.kt`)

- Zeitraum als `ChipRow`.
- Abschnitt "Punkte" mit ⓘ: "Die Quote zaehlt nur Spiele als Alleinspieler.
  Die Zahl in Klammern ist die Grundlage - aus wenigen Spielen sagt sie wenig.
  Gezaehlt werden alle Runden, auch die des laufenden Abends, jede mit den
  Hausregeln ihres Abends."
- Die Notice unter der Tabelle entfaellt.
- `StandingsTable`: Rand 1 dp `outlineVariant`, Ecken `cardCorner`; Name in
  `RotaskatTextStyles.compact` (Barlow); Rang in `labelMuted`, der erste Rang
  in `primary`; Detailzeile mit " · " statt " - ": "1 Abend · 1 Runde ·
  allein 100 % (1/1)" bzw. "allein nie".
- Leerzustand: "Noch kein Abend in diesem Zeitraum."

## 5. Statistik (`eval/StatsScreen.kt`)

- Zeitraum als `ChipRow`, darunter Spieler als `ChipRow`.
- Darunter ein 2-spaltiges Raster aus `StatTile`s:
  1. Punkte - Wert `formatPoints`, Detail "3 Abende · 41 Runden"
  2. Gewinnquote allein - "100 %", Detail "1 von 1", Warnzeile unter
     `THIN_SOLO_SAMPLE` Spielen; ohne Alleinspiel Wert "–", Detail "nie allein"
  3. Ø je Runde - "+36,0", Detail "aus 1 Runde"
  4. Lieblingsspiel - Farbsymbol (bei Karo/Herz/Pik/Kreuz) + Name, Detail
     "3 von 5 Alleinspielen"; bei Gleichstand "Kreuz / Grand" ohne Symbol;
     ohne Alleinspiel "–"
  5. Bester Abend - Punkte, Detail "Sa 14.3. · 12 Runden"
  6. Schlechtester Abend - dito; bei genau einem Abend Warnzeile "nur ein
     Abend"
- ⓘ am Abschnitt "Kennzahlen": "Ueberreizt zaehlt als verloren. Beim
  Lieblingsspiel zaehlen nur angesagte Spiele, der Ramsch gehoert niemandem."
- Leerzustand: "Noch kein Abend in diesem Zeitraum."

## 6. Abrechnung (`eval/SettlementScreen.kt`)

- Kopfzeile: `Icons.Filled.Share` ("Teilen") und `ic_show_chart`
  ("Punkteverlauf") statt Textknoepfen.
- Laufender Abend: eine Zeile "Zwischenstand - der Abend laeuft noch."
  (`labelMuted`) statt der Notice.
- Abrechnung geht nicht auf: Notice bleibt, gekuerzt auf "Die Punkte gehen
  nicht auf null auf. Bitte eine Runde korrigieren."
- Zahlungen: Abschnitt mit ⓘ "So wenige Zahlungen wie moeglich - nicht
  jeder mit jedem." Jede Zahlung als Karte (Flaeche `surfaceContainer`, Rand,
  Ecken `cardCorner`): "Lars" (`titleMedium`) / "zahlt an Johannes"
  (`bodyMedium`, `onSurfaceVariant`), rechts der Betrag in `scoreLarge`,
  `primary`. Keine Zahlung: "Alles ausgeglichen." als Zeile.
- Salden: ⓘ "<n> Cent je Punkt, festgehalten beim Anpfiff. Plus bekommt,
  Minus zahlt." Tabelle im Stil von `StandingsTable`.
- Endstand: `StandingsTable`, Unterzeile "12 Runden".
- "Abend wieder oeffnen" / "Abend verwerfen": `OutlinedButton` mit Rand
  `outline`, Text `onSurface`, Hinweis darunter gekuerzt: "Zum Korrigieren
  einer Runde." bzw. "Ohne Runde - verschwindet aus der Uebersicht."

## 7. Einstellungen, Einstieg, Beitritt, Neuer Abend

Keine neuen Ablaeufe. Einheitlich:
- Abschnitte ueber `EvalSection(title, info)`; Notices werden zu kurzen
  Zeilen oder ⓘ.
- Karten: Flaeche `surfaceContainer`, Rand 1 dp `outlineVariant`, Ecken
  `cardCorner` - wie die Abend-Karte der Startseite.
- Hauptaktionen: gefuellter Knopf in `primary`, Hoehe `tapTarget`.
- Texte (ASCII hier, in der App mit Umlauten):
  - Einstieg: Untertitel "Punkte fuer eure Skatrunde."; Karte "Ohne Verein":
    "Spieler eintragen und loslegen. Alles bleibt auf dem Geraet - einem
    Verein koennt ihr spaeter beitreten, die Abende kommen mit."; Karte "Mit
    Verein": "Mit Einladungscode. Die Abende landen auf eurem Server, die
    Rangliste gilt fuer alle."; Fusszeile "Am Tisch braucht die App keinen
    Empfang."
  - Einstellungen "Ohne Verein": Zeile "Alles bleibt auf diesem Geraet."
    statt Notice; Verein: "Server: <url>" und "Alles abgeglichen." bzw.
    "3 Runden warten auf den Server." als Zeilen.
  - "Ohne Verein" (Einrichtung): Hinweis unter "Wer spielt mit" auf eine
    Zeile kuerzen.
- "Neuer Abend": Sitzring und Ablauf unveraendert; Texte kuerzen, Knopf
  "Abend starten" in `primary`.

## 8. Reste aus Teil 1

1. Untere Leiste: `AnimatedVisibility` mit `slideInVertically`/
   `slideOutVertically` + Fade, 150/100 ms, damit sie nicht im selben Frame
   wie der Routenwechsel springt.
2. `SessionTopBar`: solange der Abend laedt (`state == null`), keine
   Aktionen (kein €, kein Menue).
3. Sync-Hinweis auf der Startseite einzeilig: "3 Runden warten auf den
   Server."
4. Aufraeumen: `toLeaderboard`/`toStats` (ungenutzt), `selectedColor` in
   `OptionTile`, `sittingOut`/`onGain`/`onLoss` in `RotaskatScoreColors`,
   Doppelung der Tab-Routen (`BottomBar` leitet ihre Ziele aus
   `Routes.TOP_LEVEL` ab), die sechsfach wiederholte Gewinn/Verlust/neutral-
   Auswahl wird eine Funktion `RotaskatScoreColors.forValue(value: Long): Color`.
5. Harte Eckradien (12/14/18 dp) in `LastChangeRow`, `ExtrasSection`,
   `RoundRow`, `Notice`, `LiveSessionCard`-Knopf auf die Dimens-Token.

## 9. Tests und Verifikation

- Unit: `labelPositions` (Abstand, Reihenfolge, Grenzen, Minimalverschiebung
  bei ungestoertem Fall), Kontraste der Linienfarben und von `neutral`,
  `forValue`.
- Compose (Robolectric): `SectionHeader` oeffnet das InfoSheet mit dem Text;
  `StatTile` zeigt Wert, Detail, Warnung; `ChipRow` meldet `selected`;
  "mehr"-Kachel hat `Role.Button`; Canvas-`contentDescription` des
  Punkteverlaufs; `SessionTopBar` ohne Aktionen waehrend des Ladens.
- Bestehende Tests bleiben gruen (`SessionLayoutTest` insbesondere).
- Geraetetest auf dem Pixel (Daten vorher sichern, danach zurueckspielen):
  Punkteverlauf mit 4 Spielern und Gleichstand am Ende, Rangliste,
  Statistik, Abrechnung, Einstellungen, Einstieg nicht (nur bei frischer
  Installation - per Screenshot-Test im Emulator nicht moeglich; Pruefung
  per Compose-Test genuegt), Leiste beim Wechsel Abend <-> Startseite.
