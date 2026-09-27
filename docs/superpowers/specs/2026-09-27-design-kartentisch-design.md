# Design "Kartentisch" - Teil 1: Designsystem, Navigation, Abend, Startseite

Stand: 27.09.2026. Grundlage: `.issues/DESIGN-NOTIZEN.md` (Geraetetest) und die
Abstimmung im Brainstorming. Verbindlich bleiben `docs/SCOPE.md` und
`docs/SCORING.md`; diese Spec aendert keine Spiellogik.

## Ziel

Die App bekommt eine eigene Handschrift, die aus dem Spiel kommt, und wird am
Tisch schneller lesbar. Messbar heisst das:

- Auswahl (Alleinspieler, Spielart, Spitzen) ist aus einem Meter Entfernung bei
  schlechtem Licht auf einen Blick erkennbar.
- Namen bis 10 Zeichen stehen im Stand und in der Undo-Zeile ungekuerzt.
- Der Vier-Tap-Pfad passt weiterhin ohne Scrollen auf das Pixel 10 Pro.
- Kein Tap landet waehrend eines Bildschirmwechsels auf dem alten Bildschirm.
- TalkBack liest den Auswahlzustand aller Kacheln vor.
- Text und Bedienelemente erfuellen WCAG AA (4,5:1 Text, 3:1 grosse Schrift und
  Bedienelement-Raender).

## Getroffene Entscheidungen

| Frage | Entscheidung |
|---|---|
| Charakter | "Kartentisch": aus dem Spiel abgeleitet, Farbsymbole als Motiv, warmes Anthrazit, Gold bleibt Leitfarbe |
| Hell/Dunkel | Nur dunkel, wie in `Theme.kt` begruendet. Kein Kneipenmodus in diesem Teil |
| Farbsymbole | Zweifarbig ohne Rot: Kupfer fuer Karo/Herz, Elfenbein fuer Pik/Kreuz. Rot bleibt Verlust |
| Schrift | Barlow Semi Condensed fuer Namen, Zahlen, Titel, Kacheln; Roboto fuer Fliesstext |
| Kachel-Stil | Variante A "Kartenecke": dunkle Kachel, Auswahl = Goldrand + Goldschimmer + Haekchen |
| Spielart-Kachel | Symbol genau einmal, gross; darunter klein der Name |
| Ergebnisbuttons | Satte dunkle Gruen-/Rotflaechen; deaktiviert als gestrichelter Umriss |
| Navigation | Untere Leiste Abende / Rangliste / Statistik; Zahnrad fuer Einstellungen auf der Startseite |
| Abend beenden | Im Menue, abgesetzt und in Gold |

Mockups: `.superpowers/brainstorm/*/content/kartentisch-varianten.html`
(Variante A) und `startseite-menue.html` (lokal, nicht im Repo).

## Umfang

**In diesem Teil:** Grundwerte (Farben, Schrift, Formen), die gemeinsamen
Bausteine in `ui/common`, Kopfzeile und Icons, Navigationsgeruest mit unterer
Leiste und Uebergaengen, Abend-Bildschirm (Eingabe und Korrektur), Startseite.

**Nicht in diesem Teil (Teil 2 "Auswertung"):** Diagramm, Rangliste, Statistik,
Abrechnung, Verlauf, Einstellungen, Einstieg, Kuerzen der Erklaertexte,
Querformat, Tablet. Diese Bildschirme erben Grundwerte, Bausteine und
Kopfzeile automatisch und werden hier nur so weit angefasst, wie es das
Navigationsgeruest erzwingt (Textbutton "Zurueck" -> Pfeil, auf Rangliste und
Statistik kein Zurueck mehr, weil sie Ziele der unteren Leiste sind).

## 1. Grundwerte

### Farben (`ui/theme/Color.kt`)

Die violettstichigen Material-Neutraltoene werden durch warmes Anthrazit
ersetzt. Die Begruendungen in den Kommentaren (kein reines Schwarz, keine
Dynamic Color, Rot/Gruen reserviert) bleiben und werden ergaenzt.

| Rolle | Wert | Verwendung |
|---|---|---|
| background, surface | `#16140F` | Grundflaeche |
| surfaceContainerLowest | `#100E0A` | |
| surfaceContainerLow | `#1E1B16` | untere Leiste |
| surfaceContainer | `#221F19` | Kacheln, Karten, Zusaetze |
| surfaceContainerHigh | `#2E2A23` | Menue, Dialoge, Undo-Zeile |
| surfaceContainerHighest | `#39342C` | |
| onSurface / onBackground | `#EDE6D8` | Elfenbein, Haupttext |
| onSurfaceVariant | `#C9BFAE` | Namen im Stand, Nebentext |
| outlineVariant | `#3A352C` | Kachelrand |
| outline | `#8F8676` | |
| Abschnittslabel (neues Token `labelMuted`) | `#9E9483` | "ALLEINSPIELER", Hinweise |
| primary | `#F2C46B` | Gold: Auswahlrand, Hauptaktion, "gibt" |
| onPrimary | `#2A1F00` | |
| primaryContainer | `#3A2F14` | Goldschimmer der gewaehlten Kachel |
| onPrimaryContainer | `#FFE7B0` | Text der gewaehlten Kachel |

Neue feste Tokens neben dem Schema, wie `RotaskatScoreColors`:

- `RotaskatSuitColors`: `red = #DB8350` (Kupfer, Karo/Herz),
  `black = #EDE6D8` (Elfenbein, Pik/Kreuz). Regel im Kommentar: Kupfer steht
  nie an einer Zahl; es gibt keine Stelle, an der Kupfer und Verlust-Rot
  dieselbe Bedeutung tragen koennten.
- `RotaskatScoreColors` bekommt fuer die Ergebnisbuttons neue Flaechen:
  `gainContainer = #1E5A37` / `onGainContainer = #E3F7E8`,
  `lossContainer = #6E2320` / `onLossContainer = #FFE3E0`. Die Textfarben
  `gain = #6FD08C` und `loss = #FF8A80` bleiben.
- `disabledOutline = #5A5244` fuer den gestrichelten Umriss.

Alle Paare werden bei der Umsetzung gegen WCAG AA nachgemessen; wo ein Wert
knapp scheitert, wird er aufgehellt, nicht der Grenzwert gesenkt.

### Schrift (`ui/theme/Type.kt`, `res/font/`)

- Barlow Semi Condensed in Medium (500), SemiBold (600), Bold (700) als TTF aus
  dem Google-Fonts-Repo (`ofl/barlowsemicondensed`) nach `res/font/`.
  Lizenz SIL OFL 1.1: Lizenztext nach `app/src/main/assets/licenses/`,
  Eintrag in `NOTICE`.
- Barlow bekommt: alle `display*`, `headline*`, `title*`, `labelLarge`
  (Kachelbeschriftung) sowie `RotaskatTextStyles.gameValue`, `scoreLarge`,
  `scoreMedium`. Roboto bleibt fuer `body*`, `labelMedium`, `labelSmall`.
- Tabellenziffern bleiben ueberall (`tnum`). Barlow liefert das Feature
  (beim Planen im Schriftfile geprueft); seine Standardziffern sind
  proportional, das Feature ist also Pflicht.
- Groessen: Stand-Namen 16sp Medium, Stand-Punkte 26sp Bold, Spielwert 60sp
  Bold, Kachel 18sp SemiBold. Die 16sp-Untergrenze fuer Fliesstext bleibt,
  und kein Nutzertext liegt unter 14sp. Kleinere Werte in den Mockups kamen
  aus der verkleinerten Darstellung und gelten nicht.

### Formen und Masse (`ui/theme/Dimens.kt`)

Tap-Ziele bleiben unveraendert (56 / 64 / 76 dp) - sie funktionieren laut
Geraetetest. Neu: `tileCorner = 12.dp`, `commitCorner = 16.dp`,
`cardCorner = 16.dp`, `selectedBorder = 2.dp`.

## 2. Bausteine (`ui/common/`)

### `OptionTile`

- Nicht gewaehlt: `surfaceContainer`, 1 dp Rand `outlineVariant`, Text
  `onSurface`.
- Gewaehlt: `primaryContainer`, 2 dp Rand `primary`, Text
  `onPrimaryContainer`, kleines Haekchen oben rechts in `primary`.
  Drei Kanaele (Flaeche, Rand, Haekchen) - Farbe ist nie der einzige.
- Deaktiviert: wie bisher gedimmt, Rand `outlineVariant` mit 50 % Alpha.
- Semantik: `Modifier.selectable(selected, role = Role.RadioButton)` statt
  `Surface(onClick)`, damit TalkBack "ausgewaehlt" vorliest. (Eine
  Mehrfachauswahl aus Kacheln gibt es nicht; die Zusaetze sind Skalen und
  ein Switch.)
- Der Parameter `selectedColor` bleibt fuer bestehende Aufrufer erhalten.

### `SuitTile` (neu)

Eine `OptionTile`-Variante fuer Karo/Herz/Pik/Kreuz: Symbol (♦ ♥ ♠ ♣) 26sp in
der Suit-Farbe mittig, darunter der Name in 14sp `labelMuted` (gewaehlt:
`onPrimaryContainer`). TalkBack liest nur den Namen ("Kreuz"). Hoehe wie die
anderen Spielart-Kacheln (64 dp). Grand, Null, Ramsch bleiben Textkacheln.

### `CommitButton`

- Aktiv: Flaeche `gainContainer` bzw. `lossContainer`, Text
  `onGainContainer`/`onLossContainer`, Label ("Gewonnen") in 18sp SemiBold,
  Punkte ("+36") darunter in 24sp Bold.
- Deaktiviert: transparent, 1,5 dp gestrichelter Rand `disabledOutline`,
  Label in `labelMuted`, Punkte als "+ ?" / "- ?". Die Buttons behalten ihre
  Hoehe, damit das Layout beim Aktivwerden nicht springt.
- "bisher"-Markierung im Korrekturmodus bleibt (3 dp Rand `onSurface`).
- Der Aufrufer in `RoundEntryPanel.kt` uebergibt die Container-Farben statt
  der Textfarben.

### Wertanzeige

Spielwert 60sp Bold, Zahl links und Herleitung rechts daneben wie bisher
(untereinander kostete es wieder Hoehe). Leerzustand: "–" in `outlineVariant`
und daneben der Hinweis, was fehlt ("Alleinspieler und Spielart waehlen").
Herleitung kurz, hoechstens zwei Zeilen: `Kreuz mit 2 · Ouvert = 12 × 9`; die
mitgesetzten Stufen stehen bei den Zusaetzen (die schalten ohnehin sichtbar
um, siehe SCOPE "Eingabemodell"), nicht in der Herleitung.

### `RotaskatTopBar` (neu)

Duenne Huelle um `TopAppBar`: Zurueck-Pfeil (optional), Titel in Barlow
SemiBold 20sp, optional Untertitel, Aktionen als Icons und ein Menue ⋮.
Ersetzt alle `TextButton("Zurueck")` in der App.

### Icons

Pfeil, Menue, Haken und Zahnrad kommen aus `material-icons-core` (schon
eingebunden). Die uebrigen als einzelne Vector-Drawables aus den Material
Icons (Apache 2.0, Eintrag in `NOTICE`) in `res/drawable/`: `style`
(Abende, Geber-Marke), `emoji_events` (Rangliste), `bar_chart` (Statistik),
`show_chart` (Punkteverlauf), `euro_symbol` (Abrechnung), `swap_horiz`
(Geber), `flag` (Abend beenden), `undo`. Keine Abhaengigkeit auf
`material-icons-extended`.

### Undo-Zeile (`session/LastChangeRow.kt`)

Flaeche `surfaceContainerHigh`, feste Hoehe 52 dp in beiden Zustaenden
("Gespeichert" mit Knopf, "Zurueckgenommen" ohne) - das Layout darunter
springt nicht mehr. Knopf als Icon `undo` plus Text "Rueckgaengig". Der
Name steht in Barlow, damit "Gespeichert: Johannes · Kreuz mit 2" passt.

### Snackbar

Dunkel (`surfaceContainerHigh`, Text `onSurface`) statt hell. Sie erscheint
nicht ueber den Ergebnisbuttons, sondern oberhalb der Eingabe (Anker unter
dem Stand).

## 3. Navigation (`ui/nav/`)

### Untere Leiste

- Ziele: Abende (`HOME`), Rangliste (`LEADERBOARD`), Statistik (`STATS`).
  Ein `Scaffold` in `RotaskatNavHost` zeigt die `NavigationBar` nur, wenn die
  aktuelle Route eines dieser drei Ziele ist. Auf dem Abend, in der Korrektur,
  in Einstellungen, Abrechnung, Verlauf und im Einstieg ist sie nicht da.
- Wechsel mit `popUpTo(HOME) { saveState = true }`, `launchSingleTop`,
  `restoreState` - die uebliche Tab-Navigation ohne wachsenden Backstack.
- Aktives Ziel: Pill in `primaryContainer`, Icon `primary`, Label `onSurface`;
  inaktiv `labelMuted`. Leiste in `surfaceContainerLow`.
- Rangliste und Statistik verlieren ihren Zurueck-Knopf (`EvalScaffold`
  bekommt `onBack: (() -> Unit)?`).

### Uebergaenge

- `NavHost` bekommt explizite Transitions: Enter `fadeIn(150ms)` +
  `slideInHorizontally` um 1/12 der Breite, Exit `fadeOut(100ms)`;
  Pop spiegelbildlich. Zwischen den drei Tab-Zielen nur Fade, kein Versatz.
- Eingabesperre: eine Huelle `LeavingGuard { }` um jeden Bildschirm im
  `composable { }`-Block (Receiver `AnimatedContentScope`) verschluckt alle
  Pointer-Events, sobald `transition.targetState != EnterExitState.Visible`.
  Damit kann ein Tap kurz nach "Zurueck" keinen Dialog des verlassenen
  Bildschirms mehr oeffnen.

## 4. Abend-Bildschirm (`session/SessionScreen.kt`, `round/RoundEntryPanel.kt`)

### Kopfzeile

- Immer Zurueck-Pfeil (zur Startseite bzw. aus der Korrektur zurueck).
- Titel: Datum und Rundennummer der naechsten Runde, z. B. "Fr 26.9. ·
  Runde 9"; beendet "Fr 26.9. · beendet"; in der Korrektur "Runde 5
  korrigieren".
- Menue ⋮ bei laufendem Abend:
  1. Punkteverlauf (-> `HISTORY`)
  2. Zwischenstand abrechnen (-> `SETTLEMENT`, die Abrechnung kennt den
     offenen Abend bereits)
  3. Geber aendern
  4. Trennlinie, dann "Abend beenden …" bzw. "Abend verwerfen …" in
     `primary` mit Icon `flag`. Der bestehende Bestaetigungsdialog bleibt.
- Bei beendetem Abend: Icon `euro_symbol` (Abrechnung) direkt in der Kopfzeile,
  im Menue Punkteverlauf.
- Im Mockup stand "Alle Runden" im Menue. Die Rundenliste steht aber schon
  unter der Eingabe; der Eintrag heisst deshalb "Punkteverlauf" und fuehrt
  dorthin, wo bisher "Verlauf" hinfuehrte.

### Stand (`Scoreboard`)

- Eine Zeile, eine Spalte je Sitz, ohne umgebende Karte; darunter eine
  Trennlinie `surfaceContainerHigh`.
- Pro Spalte: Name (Barlow Medium 16sp, `onSurfaceVariant`), darunter Punkte
  (26sp Bold, Gewinn/Verlust/neutral wie bisher, Vorzeichen immer).
- Geber: Name in `primary` (Gold) mit kleinem Kartensymbol (`style`, 14 dp)
  in der Namenszeile; am Vierertisch (setzt aus) zusaetzlich 60 % Deckkraft.
  Keine dritte Zeile: sie kostete ~18 dp, und genau die fehlten im
  Geraetetest fuer die Spitzen. Ohne "· gibt" kuerzt sich der Name nicht
  mehr. TalkBack liest "Lars, +0, gibt".
- Im Korrekturmodus zeigt die Marke den Geber der korrigierten Runde, nicht
  die aktuelle Rotation.
- Die Geber-Spalte ist antippbar und oeffnet dasselbe Sheet wie "Geber
  aendern" im Menue (siehe unten). Das haelt die Korrektur des Gebers bei
  zwei Taps (bisher: "aendern" + Name), wie SCOPE "Vierertisch" es verlangt.

### Geber aendern

Ein `ModalBottomSheet` "Wer gibt?" mit dem `SeatRing` (Sitzordnung wie am
Tisch) statt einer zweiten Namensreihe. Tap auf einen Sitz setzt den Geber
und schliesst das Sheet. Die bisherige aufklappbare Geberreihe
(`DealerHeader`) entfaellt; "Alleinspieler" steht als normales
Abschnittslabel.

### Eingabe

- Abschnittslabels ("Alleinspieler", "Spielart", "Spitzen") in 14sp Medium
  `labelMuted`, Laufweite +0,06em, ohne Grossbuchstaben (Tests und TalkBack
  arbeiten mit dem Wortlaut).
- Alleinspieler: `OptionTile`s (ohne Geber am Vierertisch, wie bisher).
- Spielart: vier `SuitTile`s in einer Reihe, darunter Grand / Null / Ramsch.
- Spitzen: 1 / 2 / 3 / 4 / "mehr" wie bisher (Positionen fest).
- Zusaetze: eine Zeile in `surfaceContainer` mit Zaehler-Badge; Inhalt
  unveraendert.
- Wertanzeige und `CommitButton`s wie in Abschnitt 2, Gewonnen breiter als
  Verloren (Verhaeltnis 5:4).
- Nach dem Umbau muss der Vier-Tap-Pfad inklusive Undo-Zeile weiterhin ohne
  Scrollen passen (Pixel 10 Pro, Standard-Schriftgroesse).

## 5. Startseite (`eval/OverviewScreen.kt`)

- Kopfzeile: "Rotaskat" in Barlow SemiBold 22sp (`titleLarge`, wie alle Titel), rechts Icon `settings`. Die
  Textbuttons "Rangliste" / "Statistik" entfallen (untere Leiste).
- Laufender Abend (falls vorhanden) als Karte `surfaceContainer`, Ecken 16 dp:
  - Kopf: "♣ Abend laeuft" als Abschnittslabel in `primary`, rechts "Runde 9 · seit 19:40" in
    `labelMuted`.
  - Fuehrung: "Anna fuehrt" (Barlow 22sp) links, "+48" rechts (30sp Bold,
    Gewinnfarbe). Bei Gleichstand "Gleichstand" ohne Namen.
  - Darunter die uebrigen Spieler einzeilig in absteigender Reihenfolge.
  - Voller Button "Weiterspielen" in `primary`/`onPrimary`.
- "Fruehere Abende" als Abschnittslabel, darunter eine Liste ohne Karten:
  Datum (Barlow SemiBold) · "24 Runden · zu viert" (`labelMuted`) · Sieger mit
  Punkten rechts. Tap fuehrt wie bisher zur Abrechnung.
- "+ Neuer Abend" als Extended FAB in Gold, nur wenn kein Abend laeuft. Laeuft
  einer, ist "Weiterspielen" die Hauptaktion; ein zweiter offener Abend waere
  sonst moeglich, weil weder NewSessionScreen noch startSession ihn
  verhindern.
- Sync-Hinweis (nur Vereinsmodus) bleibt, als einzeiliger Hinweis unter der
  Liste.

## 6. Barrierefreiheit

- Alle Auswahlkacheln melden `selected` und ihre Rolle (siehe `OptionTile`).
- `SuitTile` hat eine Beschreibung ("Kreuz"), nicht das Symbolzeichen.
- Icon-Buttons haben `contentDescription` ("Zurueck", "Weitere Optionen",
  "Einstellungen").
- Stand-Spalte des Gebers: `contentDescription` "Lars, +0, gibt", Klick-Label "Geber aendern".
- Kontraste nach WCAG AA, gemessen mit `design:accessibility-review`.

## 7. Tests und Verifikation

- Bestehende Tests: `./gradlew :app:testDebugUnitTest` bleibt gruen.
- Neue Compose-Tests (Robolectric, wie die bestehenden App-Tests):
  - `OptionTile` meldet `isSelected()` und `Role.RadioButton`.
  - `CommitButton` deaktiviert: nicht klickbar, zeigt "+ ?".
  - Untere Leiste erscheint auf HOME/LEADERBOARD/STATS und nicht auf dem
    Abend.
  - Stand zeigt im Korrekturmodus den Geber der Runde.
- Kontrasttest als Unit-Test: fuer jedes Text/Flaechen-Paar der Palette
  Kontrastverhaeltnis >= 4,5 (bzw. >= 3 fuer Symbole und Raender).
- Geraetetest auf dem Pixel 10 Pro nach `.issues/HANDOFF.md` (App-Daten vorher
  sichern): Vier-Tap-Pfad ohne Scrollen, Taps waehrend Uebergaengen, Namen bis
  10 Zeichen ungekuerzt, TalkBack liest Auswahl, Screenshots jedes
  umgebauten Bildschirms.

## 8. Risiken

- **Hoehe des Abend-Bildschirms**: Die Spielart-Kacheln werden durch das
  Symbol nicht hoeher als 64 dp; entfaellt die Geberzeile, gewinnt der
  Bildschirm eher Platz. Falls es trotzdem nicht passt, zuerst Abstaende
  (`sectionSpacing`) verkleinern, nicht die Tap-Ziele.
- **Tab-Navigation und bestehende Routen**: `toLeaderboard()`/`toStats()` in
  `RotaskatNavActions` werden auf Tab-Semantik umgestellt; andere Aufrufer
  gibt es nicht.
