# Design "Kartentisch" Teil 1 - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die App bekommt das Designsystem "Kartentisch" (Palette, Schrift, Kacheln, Ergebnisbuttons, Kopfzeile, Icons), eine untere Navigationsleiste mit kurzen, eingabesicheren Uebergaengen sowie einen ueberarbeiteten Abend-Bildschirm und eine neue Startseite.

**Architecture:** Erst die Grundwerte in `ui/theme` und die gemeinsamen Bausteine in `ui/common`, dann Navigation in `ui/nav`, zuletzt die zwei Bildschirme. Alle anderen Bildschirme erben Palette, Schrift, Kacheln und Kopfzeile automatisch. Keine Aenderung an Spiellogik, Datenschicht oder `:shared` - einzige Ausnahme ist der Text der Herleitung in `RoundDraft.derivation()`.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (BOM 2025.04.00, Material 3), Navigation Compose 2.8.9, Robolectric 4.14 + `compose-ui-test-junit4` fuer UI-Tests im normalen Unit-Test-Lauf.

**Spec:** `docs/superpowers/specs/2026-09-27-design-kartentisch-design.md`

## Global Constraints

- Nur dunkles Theme, kein Dynamic Color (`ui/theme/Theme.kt`).
- Gruen und Rot nur fuer Gewinn/Verlust. Kupfer (`#DB8350`) nur an Farbsymbolen, nie an einer Zahl.
- Vorzeichen immer mitschreiben (`formatPoints`), Farbe ist Zweitkanal.
- Tabellenziffern (`fontFeatureSettings = "tnum"`) in jeder Textrolle.
- Kein Nutzertext unter 14sp; Fliesstext mindestens 16sp.
- Tap-Ziele unveraendert: `tapTarget = 56.dp`, `bigTapTarget = 64.dp`, `commitButton = 76.dp`.
- Vier-Tap-Pfad ohne Scrollen: `SessionLayoutTest` (360x800dp) muss gruen bleiben.
- Nutzertexte mit echten Umlauten; Kommentare, Bezeichner, Doku und Commit-Messages in ASCII (ae/oe/ue), deutsch, ein Satz im Praesens.
- Keine Abhaengigkeit auf `material-icons-extended`. Icons aus `material-icons-core` (`Icons.AutoMirrored.Filled.ArrowBack`, `Icons.Filled.MoreVert`, `Icons.Filled.Check`, `Icons.Filled.Settings`), alle uebrigen als Vector-Drawable in `res/drawable/`.
- WCAG AA: Text >= 4,5:1, Symbole und Auswahlrand >= 3:1 gegen ihren Grund.
- Tests: `./gradlew :app:testDebugUnitTest` (aus dem Repo-Root, Git Bash). Einzelne Klasse: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.theme.PaletteContrastTest"`.
- Commits nur mit Co-Author-Zeile `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Abweichungen von der Spec (beim Planen entschieden)

Die Spec wird im selben Commit wie dieser Plan nachgezogen.

1. **Mindestgroesse 14sp statt 10-12sp.** Die Pixelwerte der Spec kamen aus dem verkleinerten Mockup; `Type.kt` und `SectionLabel` verlangen seit dem Geraetetest mindestens 14sp.
2. **Abschnittslabels ohne Grossbuchstaben.** `SessionLayoutTest` und TalkBack arbeiten mit dem Wortlaut ("Spitzen"); die Gewichtung kommt aus Farbe, Gewicht und Laufweite.
3. **Geber-Marke ohne dritte Zeile.** Eine Zeile "GIBT" unter den Punkten kostet ~18dp und genau die fehlten im Geraetetest fuer die Spitzen. Stattdessen: Name des Gebers in Gold plus kleines Kartensymbol (`ic_style`) in der Namenszeile; am Vierertisch zusaetzlich 55 % Deckkraft.
4. **Wertanzeige bleibt nebeneinander** (Zahl links, Herleitung rechts, maximal zwei Zeilen). Untereinander kostete sie wieder Hoehe, siehe Kommentar in `GameValueDisplay`.
5. **Icons:** Pfeil, Menue, Haken, Zahnrad aus `material-icons-core` (schon vorhanden); fuer "Abrechnung" `euro_symbol` statt `payments` (im Icon-Repo nicht vorhanden).
6. **Kein `toggleable`:** Es gibt keine Mehrfachauswahl aus `OptionTile`s (Zusaetze sind Skalen und ein Switch).
7. **Barlow liefert `tnum`** (im Binaerfile geprueft) - der Rueckfall auf Roboto entfaellt.

## Review Focus

- **Tap waehrend eines Uebergangs** (z. B. Zahnrad direkt nach "Zurueck"): darf keine Aktion des verlassenen Bildschirms ausloesen -> `LeavingGuardTest` (Task 6).
- **Korrektur einer Runde mit anderem Geber:** der Stand markiert den Geber der korrigierten Runde, nicht die aktuelle Rotation -> Test in `ScoreboardTest` (Task 8).
- **Leerer Abend beenden:** Menue zeigt "Abend verwerfen …" statt "Abend beenden …" -> Test in `SessionTopBarTest` (Task 9).
- **Gleichstand an der Spitze:** Startseite schreibt "Gleichstand" statt einen willkuerlichen Namen -> Test in `LiveSessionCardTest` (Task 10).
- **Deaktivierte Ergebnisbuttons:** bleiben sichtbar, sind nicht klickbar und zeigen "+ ?" -> Test in `CommitButtonTest` (Task 4).

---

## File Structure

| Datei | Verantwortung |
|---|---|
| `app/src/main/kotlin/io/rotaskat/app/ui/theme/Color.kt` | Palette, Score- und Akzentfarben (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/theme/Theme.kt` | stellt Akzentfarben bereit (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/theme/Type.kt` | Barlow + Rollen + eigene Textstile (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/theme/Dimens.kt` | Ecken und Rahmen (aendern) |
| `app/src/main/res/font/barlow_semi_condensed_*.ttf` | Schriftdateien (neu) |
| `app/src/main/assets/licenses/OFL-BarlowSemiCondensed.txt` | Lizenztext (neu) |
| `NOTICE` | Fremdmaterial (aendern) |
| `app/src/main/res/drawable/ic_*.xml` | 8 Icons (neu) |
| `app/src/main/kotlin/io/rotaskat/app/ui/common/Controls.kt` | `OptionTile`, `SuitTile`, `SectionLabel`, `CommitButton` (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/common/Labels.kt` | `Suit.symbol` (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/common/EvalFormat.kt` | `formatShortDate`, `formatTime` (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/common/TopBar.kt` | `RotaskatTopBar` (neu) |
| `app/src/main/kotlin/io/rotaskat/app/ui/nav/Routes.kt` | Tab-Ziele, `toTopLevel` (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/nav/BottomBar.kt` | `RotaskatBottomBar` (neu) |
| `app/src/main/kotlin/io/rotaskat/app/ui/nav/Transitions.kt` | Uebergaenge und `LeavingGuard` (neu) |
| `app/src/main/kotlin/io/rotaskat/app/ui/nav/RotaskatApp.kt` | Scaffold mit Leiste, Uebergaenge (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/round/RoundDraft.kt` | kurze Herleitung (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/round/RoundEntryPanel.kt` | Spielart mit `SuitTile`, ohne Geberreihe, neue Buttons (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/session/Scoreboard.kt` | Stand (neu, aus `SessionScreen.kt` herausgezogen) |
| `app/src/main/kotlin/io/rotaskat/app/ui/session/DealerSheet.kt` | "Wer gibt?" mit Sitzring (neu) |
| `app/src/main/kotlin/io/rotaskat/app/ui/session/SessionTopBar.kt` | Kopfzeile + Menue des Abends (neu) |
| `app/src/main/kotlin/io/rotaskat/app/ui/session/SessionScreen.kt` | verdrahtet Kopfzeile, Sheet, Snackbar (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/session/LastChangeRow.kt` | feste Hoehe, Icon (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/eval/OverviewScreen.kt` | Startseite (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/eval/LiveSessionCard.kt` | Karte des laufenden Abends (neu) |
| `app/src/main/kotlin/io/rotaskat/app/ui/eval/EvalScaffold.kt`, `LeaderboardScreen.kt`, `StatsScreen.kt` | Kopfzeile, kein Zurueck auf Tabs (aendern) |
| `app/src/main/kotlin/io/rotaskat/app/ui/onboarding/JoinScreen.kt`, `LocalSetupScreen.kt`, `session/NewSessionScreen.kt` | Zurueck-Pfeil (aendern) |

---

### Task 1: Palette und Kontrasttest

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/theme/Color.kt` (ganze Datei)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/theme/Theme.kt`
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/theme/PaletteContrastTest.kt`

**Interfaces:**
- Produces: `RotaskatAccentColors(suitRed, suitBlack, labelMuted, disabledOutline)`, `RotaskatAccentColorsDark`, `LocalAccentColors`, `MaterialTheme.accentColors`; `RotaskatScoreColors` bekommt `onGainContainer`, `onLossContainer`.

- [ ] **Step 1: Failing test schreiben**

```kotlin
package io.rotaskat.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

/**
 * WCAG AA fuer die Palette: Text 4,5:1, Symbole und Raender 3:1.
 *
 * Gemessen wird gegen den Grund, auf dem die Farbe tatsaechlich steht. Ein
 * Wert, der knapp scheitert, wird aufgehellt - der Grenzwert bleibt.
 */
class PaletteContrastTest {

    private val scheme = RotaskatColorScheme
    private val score = RotaskatScoreColorsDark
    private val accent = RotaskatAccentColorsDark

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return max(la, lb).toDouble() / min(la, lb).toDouble()
    }

    private fun assertContrast(minimum: Double, pairs: Map<String, Pair<Color, Color>>) {
        for ((name, pair) in pairs) {
            val value = ratio(pair.first, pair.second)
            assertTrue(value >= minimum, "$name: ${"%.2f".format(value)} < $minimum")
        }
    }

    @Test
    fun `Text erreicht 4,5 zu 1`() = assertContrast(
        4.5,
        mapOf(
            "Haupttext auf Grund" to (scheme.onSurface to scheme.background),
            "Nebentext auf Grund" to (scheme.onSurfaceVariant to scheme.background),
            "Nebentext auf Kachel" to (scheme.onSurfaceVariant to scheme.surfaceContainer),
            "Label auf Grund" to (accent.labelMuted to scheme.background),
            "Label auf Kachel" to (accent.labelMuted to scheme.surfaceContainer),
            "Label auf Leiste" to (accent.labelMuted to scheme.surfaceContainerLow),
            "gewaehlte Kachel" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "Gewonnen-Button" to (score.onGainContainer to score.gainContainer),
            "Verloren-Button" to (score.onLossContainer to score.lossContainer),
            "Gewinn auf Grund" to (score.gain to scheme.background),
            "Verlust auf Grund" to (score.loss to scheme.background),
            "Gold-Button" to (scheme.onPrimary to scheme.primary),
            "Menue" to (scheme.onSurface to scheme.surfaceContainerHigh),
            "Gold im Menue" to (scheme.primary to scheme.surfaceContainerHigh),
        ),
    )

    @Test
    fun `Symbole und Raender erreichen 3 zu 1`() = assertContrast(
        3.0,
        mapOf(
            "Auswahlrand auf Kachel" to (scheme.primary to scheme.surfaceContainer),
            "Karo/Herz auf Kachel" to (accent.suitRed to scheme.surfaceContainer),
            "Pik/Kreuz auf Kachel" to (accent.suitBlack to scheme.surfaceContainer),
            "deaktivierter Umriss" to (accent.disabledOutline to scheme.background),
        ),
    )
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.theme.PaletteContrastTest"`
Expected: Compile-Fehler `Unresolved reference: RotaskatAccentColorsDark` / `onGainContainer`.

- [ ] **Step 3: `Color.kt` ersetzen**

```kotlin
package io.rotaskat.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Die Palette der App: "Kartentisch".
 *
 * Es gibt bewusst nur ein dunkles Schema und keinen Hellmodus. Gespielt wird in
 * einer Kneipe mit schlechtem Licht; eine helle Flaeche auf dem Tisch blendet
 * die ganze Runde und zwingt das Auge nach jedem Blick auf die Karten neu zur
 * Anpassung. Der Hellmodus waere kein Zugewinn, sondern eine Fehlbedienung mit
 * Umschalter davor.
 *
 * Die Grundflaeche ist warmes Anthrazit und bewusst NICHT reines Schwarz: auf
 * OLED-Displays laesst ein #000000-Grund Kanten und Textraender sichtbar
 * schmieren, und die Erhoehung einer Kachel gegen den Hintergrund waere nicht
 * mehr darstellbar. Warm statt der violettstichigen Material-Neutraltoene, weil
 * Gold und Elfenbein darauf wie Karten auf einem Tisch stehen statt wie
 * Leuchtschrift auf einem Bildschirm.
 */
private val Surface = Color(0xFF16140F)

// Warmes Gold als Leitfarbe. Bewusst weder gruen noch rot: beide Toene sind fuer
// Gewinn und Verlust reserviert und duerfen an keiner anderen Stelle der
// Oberflaeche auftauchen, sonst verliert das Signal seine Bedeutung.
private val Gold = Color(0xFFF2C46B)

// Elfenbein: Haupttext und die "schwarzen" Farben Pik und Kreuz.
private val Ivory = Color(0xFFEDE6D8)

/**
 * Das Farbschema. Handverlesen statt aus Dynamic Color abgeleitet - siehe
 * [RotaskatScoreColors] fuer die Begruendung, die fuer die Leitfarbe genauso
 * gilt: der Wiedererkennungswert einer App, die je nach Hintergrundbild anders
 * aussieht, ist null.
 */
val RotaskatColorScheme: ColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2A1F00),
    // Der Goldschimmer einer gewaehlten Kachel. Dunkel genug, dass der
    // Goldrand darauf der staerkere Kanal bleibt.
    primaryContainer = Color(0xFF3A2F14),
    onPrimaryContainer = Color(0xFFFFE7B0),
    inversePrimary = Color(0xFF6F5B00),

    secondary = Color(0xFFCFC5B4),
    onSecondary = Color(0xFF36302A),
    secondaryContainer = Color(0xFF4D463D),
    onSecondaryContainer = Color(0xFFECE1CF),

    // Kuehler Akzent fuer alles Beilaeufige - Badges, Zaehler, Hinweise.
    tertiary = Color(0xFF9FCBE8),
    onTertiary = Color(0xFF003548),
    tertiaryContainer = Color(0xFF1F4C63),
    onTertiaryContainer = Color(0xFFC9E6FF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Surface,
    onBackground = Ivory,
    surface = Surface,
    onSurface = Ivory,
    surfaceVariant = Color(0xFF39342C),
    onSurfaceVariant = Color(0xFFC9BFAE),
    surfaceTint = Gold,

    surfaceContainerLowest = Color(0xFF100E0A),
    // Die untere Navigationsleiste.
    surfaceContainerLow = Color(0xFF1E1B16),
    // Kacheln, Karten, die Zusaetze-Zeile.
    surfaceContainer = Color(0xFF221F19),
    // Menue, Dialoge, Sheet, Undo-Zeile.
    surfaceContainerHigh = Color(0xFF2E2A23),
    surfaceContainerHighest = Color(0xFF39342C),

    outline = Color(0xFF8F8676),
    // Der Rand einer nicht gewaehlten Kachel.
    outlineVariant = Color(0xFF3A352C),

    inverseSurface = Ivory,
    inverseOnSurface = Color(0xFF2E2A23),
    scrim = Color(0xFF000000),
)

/**
 * Gewinn und Verlust als feste Tokens.
 *
 * Sie stehen absichtlich neben dem [ColorScheme] und nicht darin: aus einem
 * Dynamic-Color-Schema abgeleitet waeren sie je nach Hintergrundbild des
 * Nutzers mal gruen, mal beige, mal violett. Eine Punktzahl, deren Farbe vom
 * Wallpaper abhaengt, ist als Signal wertlos.
 *
 * Die Farbe ist ohnehin nur der Zweitkanal. Traeger der Information ist das
 * immer mitgeschriebene Vorzeichen - siehe `formatPoints` -, damit die Tabelle
 * auch bei Rot-Gruen-Schwaeche und in der Kneipenbeleuchtung lesbar bleibt.
 *
 * [gainContainer] und [lossContainer] sind die Flaechen von "Gewonnen" und
 * "Verloren": satt und dunkel statt pastellig, damit sie neben dem Gold nicht
 * wie Fremdkoerper wirken und trotzdem die groessten Flaechen des Bildschirms
 * bleiben.
 */
@Immutable
data class RotaskatScoreColors(
    val gain: Color,
    val onGain: Color,
    val gainContainer: Color,
    val onGainContainer: Color,
    val loss: Color,
    val onLoss: Color,
    val lossContainer: Color,
    val onLossContainer: Color,
    /** Genau null Punkte. Bewusst weder gruen noch rot. */
    val neutral: Color,
    /** Der Aussetzende: sichtbar vorhanden, aber ohne Beteiligung. */
    val sittingOut: Color,
)

val RotaskatScoreColorsDark = RotaskatScoreColors(
    gain = Color(0xFF6FD08C),
    onGain = Color(0xFF00391B),
    gainContainer = Color(0xFF1E5A37),
    onGainContainer = Color(0xFFE3F7E8),
    loss = Color(0xFFFF8A80),
    onLoss = Color(0xFF5C0007),
    lossContainer = Color(0xFF6E2320),
    onLossContainer = Color(0xFFFFE3E0),
    neutral = Color(0xFFB6AFBC),
    sittingOut = Color(0xFF7A737F),
)

val LocalScoreColors = staticCompositionLocalOf { RotaskatScoreColorsDark }

/**
 * Farben, die weder zum Material-Schema noch zu Gewinn/Verlust gehoeren.
 *
 * [suitRed] ist Kupfer, nicht Rot: Karo und Herz sollen sich von Pik und Kreuz
 * so unterscheiden wie auf dem Kartenblatt, ohne dass die Farbe des Verlusts
 * eine zweite Bedeutung bekommt. Kupfer steht deshalb nie an einer Zahl.
 */
@Immutable
data class RotaskatAccentColors(
    /** Karo und Herz. */
    val suitRed: Color,
    /** Pik und Kreuz. */
    val suitBlack: Color,
    /** Abschnittslabels, Hinweise, inaktive Ziele der unteren Leiste. */
    val labelMuted: Color,
    /** Gestrichelter Umriss eines Ergebnisbuttons, der noch nicht bereit ist. */
    val disabledOutline: Color,
)

val RotaskatAccentColorsDark = RotaskatAccentColors(
    suitRed = Color(0xFFDB8350),
    suitBlack = Ivory,
    labelMuted = Color(0xFF9E9483),
    disabledOutline = Color(0xFF7A7060),
)

val LocalAccentColors = staticCompositionLocalOf { RotaskatAccentColorsDark }
```

- [ ] **Step 4: `Theme.kt` anpassen**

Im `CompositionLocalProvider` die Akzentfarben mitgeben und eine Kurzform anlegen:

```kotlin
    CompositionLocalProvider(
        LocalScoreColors provides RotaskatScoreColorsDark,
        LocalAccentColors provides RotaskatAccentColorsDark,
    ) {
```

Am Dateiende ergaenzen:

```kotlin
/** Kurzform fuer Farbsymbole, Labels und den deaktivierten Umriss. */
val MaterialTheme.accentColors: RotaskatAccentColors
    @Composable
    get() = LocalAccentColors.current
```

- [ ] **Step 5: Test laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.theme.PaletteContrastTest"`
Expected: PASS (beide Tests). Scheitert ein Paar, den Vordergrundwert aufhellen, nicht den Grenzwert senken.

- [ ] **Step 6: Gesamten App-Testlauf pruefen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. (`onGain`/`onLoss` bleiben erhalten, bestehende Aufrufer kompilieren weiter.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/io/rotaskat/app/ui/theme/Color.kt app/src/main/kotlin/io/rotaskat/app/ui/theme/Theme.kt app/src/test/kotlin/io/rotaskat/app/ui/theme/PaletteContrastTest.kt
git commit -m "Palette auf warmes Anthrazit umstellen und Kontraste per Test absichern" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Schrift Barlow Semi Condensed

**Files:**
- Create: `app/src/main/res/font/barlow_semi_condensed_medium.ttf`, `..._semibold.ttf`, `..._bold.ttf`
- Create: `app/src/main/assets/licenses/OFL-BarlowSemiCondensed.txt`
- Modify: `NOTICE`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/theme/Type.kt` (ganze Datei)
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/theme/TypographyTest.kt`

**Interfaces:**
- Produces: `BarlowSemiCondensed: FontFamily`; `RotaskatTextStyles.gameValue`, `.scoreLarge`, `.scoreMedium`, `.standName`, `.commitPoints`, `.sectionLabel`, `.compact`.

- [ ] **Step 1: Failing test schreiben**

```kotlin
package io.rotaskat.app.ui.theme

import androidx.compose.ui.text.TextStyle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Welche Rolle welche Schrift bekommt, und dass keine die Tabellenziffern verliert. */
class TypographyTest {

    private val t = RotaskatTypography

    @Test
    fun `Titel und Kacheln stehen in Barlow`() {
        for (style in listOf(t.titleLarge, t.titleMedium, t.titleSmall, t.labelLarge, t.headlineSmall)) {
            assertEquals(BarlowSemiCondensed, style.fontFamily)
        }
    }

    @Test
    fun `Fliesstext bleibt Roboto`() {
        for (style in listOf(t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelMedium, t.labelSmall)) {
            assertNotEquals(BarlowSemiCondensed, style.fontFamily)
        }
    }

    @Test
    fun `jede Rolle hat Tabellenziffern und mindestens 14sp`() {
        val all: List<TextStyle> = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
            RotaskatTextStyles.gameValue, RotaskatTextStyles.scoreLarge,
            RotaskatTextStyles.scoreMedium, RotaskatTextStyles.standName,
            RotaskatTextStyles.commitPoints, RotaskatTextStyles.sectionLabel,
            RotaskatTextStyles.compact,
        )
        for (style in all) {
            assertEquals("tnum", style.fontFeatureSettings)
            assertTrue(style.fontSize.value >= 14f, "${style.fontSize} < 14sp")
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.theme.TypographyTest"`
Expected: Compile-Fehler `Unresolved reference: BarlowSemiCondensed`.

- [ ] **Step 3: Schriftdateien und Lizenz holen**

```bash
mkdir -p app/src/main/res/font app/src/main/assets/licenses
base=https://github.com/google/fonts/raw/main/ofl/barlowsemicondensed
curl -sSLf -o app/src/main/res/font/barlow_semi_condensed_medium.ttf   $base/BarlowSemiCondensed-Medium.ttf
curl -sSLf -o app/src/main/res/font/barlow_semi_condensed_semibold.ttf $base/BarlowSemiCondensed-SemiBold.ttf
curl -sSLf -o app/src/main/res/font/barlow_semi_condensed_bold.ttf     $base/BarlowSemiCondensed-Bold.ttf
curl -sSLf -o app/src/main/assets/licenses/OFL-BarlowSemiCondensed.txt $base/OFL.txt
for f in app/src/main/res/font/*.ttf; do printf "%s tnum=" "$f"; grep -c -a tnum "$f"; done
```

Expected: drei Dateien je ~100 KB, jede mit `tnum=1`, und eine OFL.txt, die mit "Copyright" beginnt.

- [ ] **Step 4: `NOTICE` ergaenzen**

Den Absatz "Diese Datei fuehrt uebernommenen Fremdcode ... noch leer, weil bisher nichts uebernommen wurde." ersetzen durch:

```text
Diese Datei fuehrt uebernommenes Fremdmaterial und dessen Herkunft auf.

Barlow Semi Condensed (https://github.com/google/fonts/tree/main/ofl/barlowsemicondensed)
    Copyright 2017 The Barlow Project Authors, SIL Open Font License 1.1.
    Dateien: app/src/main/res/font/barlow_semi_condensed_{medium,semibold,bold}.ttf
    Lizenztext: app/src/main/assets/licenses/OFL-BarlowSemiCondensed.txt

Material Icons (https://github.com/google/material-design-icons), Apache License 2.0
    Pfaddaten der Icons in app/src/main/res/drawable/ic_*.xml.

Geplant ist:
```

(Der bestehende JSkat-Absatz bleibt darunter stehen.)

- [ ] **Step 5: `Type.kt` ersetzen**

```kotlin
package io.rotaskat.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.rotaskat.app.R

/**
 * OpenType-Feature fuer dicktengleiche Ziffern.
 *
 * Ohne sie ist die "1" schmaler als die "8", und eine Punktetabelle springt bei
 * jeder Runde seitlich hin und her. Am Tisch wird die Spalte im Vorbeigehen
 * abgelesen; wandert sie, muss jedes Mal neu gesucht werden. Barlow hat
 * standardmaessig proportionale Ziffern - ohne dieses Feature waere es dort
 * genauso.
 */
private const val TABULAR_FIGURES = "tnum"

/**
 * Die Schrift fuer Namen, Zahlen, Titel und Kacheln.
 *
 * Halbschmal, damit Namen bis zehn Zeichen im Stand ganz stehen: bei vier
 * Spalten auf einem 360dp-Telefon bleiben je Spalte rund 80dp, und in Roboto
 * wurde dort "Johannes" gekuerzt. Fliesstext bleibt Roboto - laengere Saetze
 * liest man in einer schmalen Schrift schlechter.
 */
val BarlowSemiCondensed = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
)

private fun TextStyle.tabular(size: Int? = null): TextStyle = copy(
    fontFeatureSettings = TABULAR_FIGURES,
    fontSize = size?.sp ?: fontSize,
    lineHeight = if (size != null) (size * 1.35f).sp else lineHeight,
)

private fun TextStyle.barlow(weight: FontWeight, size: Int? = null): TextStyle =
    tabular(size).copy(fontFamily = BarlowSemiCondensed, fontWeight = weight)

/**
 * Die Typografie der App.
 *
 * Abweichungen vom Material-Vorgabesatz, alle aus dem Nutzungskontext: jede
 * Rolle bekommt Tabellenziffern, die Fliesstext-Rollen liegen bei mindestens
 * 16sp statt bei 14sp, und nichts liegt unter 14sp. Gelesen wird schraeg von
 * der Seite, aus etwa einem Meter Entfernung, bei Kneipenlicht - 14sp ist dort
 * geraten, nicht gelesen.
 */
val RotaskatTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.barlow(FontWeight.Bold),
        displayMedium = displayMedium.barlow(FontWeight.Bold),
        displaySmall = displaySmall.barlow(FontWeight.Bold),
        headlineLarge = headlineLarge.barlow(FontWeight.Bold),
        headlineMedium = headlineMedium.barlow(FontWeight.Bold),
        headlineSmall = headlineSmall.barlow(FontWeight.Bold),
        titleLarge = titleLarge.barlow(FontWeight.SemiBold, 22),
        titleMedium = titleMedium.barlow(FontWeight.SemiBold, 18),
        // Die Beschriftung der Auswahlkacheln.
        titleSmall = titleSmall.barlow(FontWeight.SemiBold, 18),
        bodyLarge = bodyLarge.tabular(17),
        bodyMedium = bodyMedium.tabular(16),
        bodySmall = bodySmall.tabular(15),
        labelLarge = labelLarge.barlow(FontWeight.SemiBold, 16),
        labelMedium = labelMedium.tabular(15),
        labelSmall = labelSmall.tabular(14),
    )
}

/**
 * Rollen, die es im Material-Satz nicht gibt.
 *
 * Der Spielwert ist die einzige Zahl auf dem Bildschirm, die vor dem Speichern
 * gegengelesen wird. Er ist deshalb absichtlich groesser als jede Ueberschrift.
 */
object RotaskatTextStyles {

    val gameValue = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 60.sp,
        lineHeight = 62.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Punkte im Stand. */
    val scoreLarge = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Punkte in Listen und in der Undo-Zeile. */
    val scoreMedium = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Namen im Stand. */
    val standName = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Die Punkte auf "Gewonnen" und "Verloren". */
    val commitPoints = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /**
     * Abschnittslabels ("Alleinspieler", "Spitzen"). Klein, gesperrt und
     * gedaempft, damit sie ordnen, ohne mit den Kacheln zu konkurrieren.
     */
    val sectionLabel = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.06.em,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Eine Zeile mit Name und Spiel, z. B. in der Undo-Zeile. */
    val compact = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR_FIGURES,
    )
}
```

- [ ] **Step 6: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.theme.TypographyTest" --tests "io.rotaskat.app.ui.session.SessionLayoutTest"`
Expected: PASS. Scheitert `SessionLayoutTest` wegen des groesseren Spielwerts (60sp statt 56sp), `gameValue` auf `fontSize = 56.sp, lineHeight = 58.sp` zuruecknehmen und die Spec-Zeile "Spielwert 60sp" entsprechend anpassen.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/font app/src/main/assets/licenses NOTICE app/src/main/kotlin/io/rotaskat/app/ui/theme/Type.kt app/src/test/kotlin/io/rotaskat/app/ui/theme/TypographyTest.kt
git commit -m "Barlow Semi Condensed fuer Namen, Zahlen und Titel einfuehren" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Auswahlkacheln, Farbkacheln, Abschnittslabels

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/theme/Dimens.kt` (Ende des `object`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/common/Controls.kt` (`OptionTile`, `SectionLabel`; neu `SuitTile`, `TileFrame`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/common/Labels.kt` (`Suit.symbol`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/round/RoundEntryPanel.kt:358-382` (`GamePicker`)
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/common/OptionTileTest.kt`

**Interfaces:**
- Consumes: `MaterialTheme.accentColors` (Task 1), `RotaskatTextStyles.sectionLabel` (Task 2).
- Produces: `SuitTile(suit: Suit, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)`; `OptionTile` behaelt seine Signatur; `val Suit.symbol: String`; `RotaskatDimens.tileCorner`, `.commitCorner`, `.cardCorner`, `.selectedBorder`.

- [ ] **Step 1: Failing test schreiben**

```kotlin
package io.rotaskat.app.ui.common

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Row
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Suit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Die Auswahlkacheln muessen TalkBack sagen, was gewaehlt ist. Im
 * Geraetetest meldeten sie weder "ausgewaehlt" noch eine Rolle.
 */
@RunWith(RobolectricTestRunner::class)
class OptionTileTest {

    @get:Rule
    val compose = createComposeRule()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun `gewaehlte Kachel meldet ausgewaehlt und Rolle`() {
        compose.setContent {
            RotaskatTheme {
                Row {
                    OptionTile(label = "Anna", selected = true, onClick = {})
                    OptionTile(label = "Ben", selected = false, onClick = {})
                }
            }
        }
        compose.onNodeWithText("Anna").assertIsSelected().assert(radio)
        compose.onNodeWithText("Ben").assertIsNotSelected().assert(radio)
    }

    @Test
    fun `deaktivierte Kachel ist nicht antippbar`() {
        var clicks = 0
        compose.setContent {
            RotaskatTheme {
                OptionTile(label = "Lars", selected = false, enabled = false, onClick = { clicks++ })
            }
        }
        compose.onNodeWithText("Lars").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `Farbkachel liest den Namen, nicht das Symbol`() {
        var picked: Suit? = null
        compose.setContent {
            RotaskatTheme {
                SuitTile(suit = Suit.CLUBS, selected = true, onClick = { picked = Suit.CLUBS })
            }
        }
        compose.onNodeWithText("Kreuz").assertIsSelected().assert(radio).performClick()
        compose.onAllNodesWithText("♣").assertCountEquals(0)
        assertEquals(Suit.CLUBS, picked)
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.common.OptionTileTest"`
Expected: Compile-Fehler `Unresolved reference: SuitTile`.

- [ ] **Step 3: `Dimens.kt` erweitern** (vor der schliessenden Klammer von `RotaskatDimens`)

```kotlin
    /** Ecken der Auswahlkacheln. */
    val tileCorner = 12.dp

    /** Ecken von "Gewonnen" und "Verloren". */
    val commitCorner = 16.dp

    /** Ecken von Karten, etwa des laufenden Abends auf der Startseite. */
    val cardCorner = 16.dp

    /** Rand einer gewaehlten Kachel. */
    val selectedBorder = 2.dp
```

- [ ] **Step 4: `Labels.kt` ergaenzen** (unter dem `Suit.label`-Block)

```kotlin
/** Das Farbsymbol, wie es auf der Karte steht. */
val Suit.symbol: String
    get() = when (this) {
        Suit.DIAMONDS -> "♦"
        Suit.HEARTS -> "♥"
        Suit.SPADES -> "♠"
        Suit.CLUBS -> "♣"
    }
```

- [ ] **Step 5: `OptionTile` und `SectionLabel` in `Controls.kt` ersetzen, `SuitTile` und `TileFrame` hinzufuegen**

`OptionTile` (inklusive KDoc-Absatz ueber die Rahmenbegruendung) ersetzen durch:

```kotlin
@Composable
fun OptionTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondaryLabel: String? = null,
    height: Dp = RotaskatDimens.bigTapTarget,
    selectedColor: Color? = null,
) {
    TileFrame(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        height = height,
        selectedColor = selectedColor,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (secondaryLabel != null) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Karo, Herz, Pik oder Kreuz: das Symbol gross, darunter klein der Name.
 *
 * Das Symbol erkennt man schneller als das Wort, gerade bei schlechtem Licht.
 * Der Name bleibt fuer Neulinge und fuer TalkBack stehen; vorgelesen wird nur
 * er, nicht das Zeichen "♣".
 */
@Composable
fun SuitTile(
    suit: Suit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.accentColors
    val symbolColor = when (suit) {
        Suit.DIAMONDS, Suit.HEARTS -> accent.suitRed
        Suit.SPADES, Suit.CLUBS -> accent.suitBlack
    }
    TileFrame(selected = selected, enabled = true, onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = suit.symbol,
                color = symbolColor,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                text = suit.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else accent.labelMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * Flaeche, Rand und Auswahlzustand aller Kacheln.
 *
 * Die Auswahl hat drei Kanaele: Goldschimmer, Goldrand und Haekchen. Im
 * Kneipenlicht ist ein Farbunterschied allein zu wenig, um eine getroffene
 * Auswahl im Vorbeisehen zu erkennen.
 *
 * `selectable` statt `Surface(onClick)`, damit TalkBack "ausgewaehlt" und die
 * Rolle vorliest - im Geraetetest meldeten die Kacheln beides nicht.
 */
@Composable
private fun TileFrame(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = RotaskatDimens.bigTapTarget,
    selectedColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(RotaskatDimens.tileCorner)
    val fill = when {
        !enabled -> colors.surfaceContainerLowest
        selected -> selectedColor ?: colors.primaryContainer
        else -> colors.surfaceContainer
    }
    val contentColor = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> selectedColor?.let { colors.surface } ?: colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val border = when {
        selected -> BorderStroke(RotaskatDimens.selectedBorder, colors.primary)
        !enabled -> BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, colors.outlineVariant)
    }
    Surface(
        modifier = modifier.height(height),
        shape = shape,
        color = fill,
        contentColor = contentColor,
        border = border,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp)
                        .size(14.dp),
                )
            }
        }
    }
}
```

`SectionLabel` ersetzen durch:

```kotlin
/** Ueberschrift eines Abschnitts. Klein, gesperrt, gedaempft - aber nie unter 14sp. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = RotaskatTextStyles.sectionLabel,
        color = MaterialTheme.accentColors.labelMuted,
        modifier = modifier.padding(bottom = 6.dp),
    )
}
```

Imports in `Controls.kt` ergaenzen:

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors
import io.rotaskat.shared.model.Suit
```

- [ ] **Step 6: `GamePicker` in `RoundEntryPanel.kt` umstellen**

```kotlin
@Composable
private fun GamePicker(draft: RoundDraft, onPick: ((RoundDraft) -> RoundDraft) -> Unit) {
    val others = listOf(GamePick.Grand to "Grand", GamePick.Null to "Null", GamePick.Ramsch to "Ramsch")
    // Vier Spalten: die vier Farben als Symbolkacheln in der ersten Reihe,
    // Grand, Null und Ramsch in der zweiten. Drei Spalten brauchten eine
    // dritte Reihe.
    OptionGrid(columns = 4, itemCount = Suit.entries.size + others.size) { index ->
        // Der Wechsel raeumt auf, was zur neuen Ansage nicht passt - der
        // Ramsch den Alleinspieler, das Nullspiel das "ueberreizt", der
        // Grand eine zu hohe Spitzenzahl. Die Regeln stehen in
        // RoundDraft.withGame und damit dort, wo sie ohne Bildschirm
        // nachrechenbar sind.
        if (index < Suit.entries.size) {
            val suit = Suit.entries[index]
            val pick = GamePick.Colour(suit)
            SuitTile(
                suit = suit,
                selected = draft.game == pick,
                onClick = { onPick { current -> current.withGame(pick) } },
                modifier = Modifier.weight(1f),
            )
        } else {
            val (pick, label) = others[index - Suit.entries.size]
            OptionTile(
                label = label,
                selected = draft.game == pick,
                onClick = { onPick { current -> current.withGame(pick) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
```

Import ergaenzen: `import io.rotaskat.app.ui.common.SuitTile`.

- [ ] **Step 7: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.common.OptionTileTest" --tests "io.rotaskat.app.ui.session.SessionLayoutTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/io/rotaskat/app/ui/theme/Dimens.kt app/src/main/kotlin/io/rotaskat/app/ui/common/Controls.kt app/src/main/kotlin/io/rotaskat/app/ui/common/Labels.kt app/src/main/kotlin/io/rotaskat/app/ui/round/RoundEntryPanel.kt app/src/test/kotlin/io/rotaskat/app/ui/common/OptionTileTest.kt
git commit -m "Kacheln mit Goldrand, Haekchen und Auswahlzustand fuer TalkBack, Farben als Symbol" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Ergebnisbuttons und Wertanzeige

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/common/Controls.kt` (`CommitButton`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/round/RoundEntryPanel.kt:527-626` (`GameValueDisplay`, `CommitRow`)
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/common/CommitButtonTest.kt`

**Interfaces:**
- Consumes: `RotaskatScoreColors.gainContainer/onGainContainer/lossContainer/onLossContainer`, `MaterialTheme.accentColors.disabledOutline/labelMuted` (Task 1), `RotaskatTextStyles.commitPoints` (Task 2), `RotaskatDimens.commitCorner` (Task 3).
- Produces: `CommitButton(label: String, container: Color, onContainer: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null, placeholder: String = "+ ?", previous: Boolean = false)`.

- [ ] **Step 1: Failing test schreiben**

```kotlin
package io.rotaskat.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Deaktiviert waren die Ergebnisbuttons im Geraetetest fast unsichtbar - man
 * sah nicht, dass dort gleich etwas kommt.
 */
@RunWith(RobolectricTestRunner::class)
class CommitButtonTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `nicht bereit bleibt sichtbar, zeigt Platzhalter und ist nicht klickbar`() {
        var clicks = 0
        compose.setContent {
            RotaskatTheme {
                CommitButton(
                    label = "Gewonnen",
                    container = Color.Green,
                    onContainer = Color.White,
                    enabled = false,
                    onClick = { clicks++ },
                )
            }
        }
        compose.onNodeWithText("+ ?").assertIsDisplayed()
        compose.onNodeWithText("Gewonnen").assertIsDisplayed().assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `bereit zeigt die Punkte und speichert`() {
        var clicks = 0
        compose.setContent {
            RotaskatTheme {
                CommitButton(
                    label = "Gewonnen",
                    container = Color.Green,
                    onContainer = Color.White,
                    enabled = true,
                    detail = "+36",
                    onClick = { clicks++ },
                )
            }
        }
        compose.onNodeWithText("+36").assertIsDisplayed()
        compose.onNodeWithText("Gewonnen").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `in der Korrektur steht der bisherige Ausgang dabei`() {
        compose.setContent {
            RotaskatTheme {
                CommitButton(
                    label = "Verloren",
                    container = Color.Red,
                    onContainer = Color.White,
                    enabled = true,
                    detail = "-72",
                    previous = true,
                    onClick = {},
                )
            }
        }
        compose.onNodeWithText("-72 · bisher").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.common.CommitButtonTest"`
Expected: Compile-Fehler `No parameter with name 'container' found`.

- [ ] **Step 3: `CommitButton` ersetzen**

```kotlin
/**
 * Die grossen Ergebnisflaechen. Sie sind gleichzeitig die Speichern-Buttons -
 * einen zusaetzlichen Commit gibt es bewusst nicht, siehe [OptionTile].
 *
 * Solange die Runde nicht vollstaendig ist, bleibt der Button als
 * gestrichelter Umriss mit [placeholder] stehen: gleiche Hoehe, gleiche Stelle.
 * Ein Button, der erst beim letzten Tap auftaucht, laesst das Layout springen
 * und wird dann daneben getippt.
 */
@Composable
fun CommitButton(
    label: String,
    container: Color,
    onContainer: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    placeholder: String = "+ ?",
    /** Der Ausgang, der vor der Korrektur gespeichert war. */
    previous: Boolean = false,
) {
    val accent = MaterialTheme.accentColors
    val shape = RoundedCornerShape(RotaskatDimens.commitCorner)
    val dashed = if (enabled) {
        Modifier
    } else {
        Modifier.drawBehind {
            val stroke = 1.5.dp.toPx()
            drawRoundRect(
                color = accent.disabledOutline,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(RotaskatDimens.commitCorner.toPx()),
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                ),
            )
        }
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = RotaskatDimens.commitButton)
            .then(dashed),
        shape = shape,
        border = if (previous) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        color = if (enabled) container else Color.Transparent,
        contentColor = if (enabled) onContainer else accent.labelMuted,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                // Was der Alleinspieler bei diesem Ausgang bekaeme, schon vor
                // dem Tap. Die zweite Gelegenheit, einen falsch getippten
                // Spielwert zu bemerken.
                val points = when {
                    !enabled -> placeholder
                    detail == null -> null
                    previous -> "$detail · bisher"
                    else -> detail
                }
                if (points != null) {
                    Text(text = points, style = RotaskatTextStyles.commitPoints)
                }
            }
        }
    }
}
```

Imports in `Controls.kt` ergaenzen:

```kotlin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
```

- [ ] **Step 4: `GameValueDisplay` und `CommitRow` in `RoundEntryPanel.kt` ersetzen**

```kotlin
@Composable
private fun GameValueDisplay(draft: RoundDraft) {
    val value = draft.displayedGameValue
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = value?.toString() ?: "–",
            style = RotaskatTextStyles.gameValue,
            color = if (value != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            textAlign = TextAlign.Center,
            // Feste Breite fuer drei Ziffern: die Herleitung springt sonst
            // bei jedem Wechsel zwischen 18 und 108 zur Seite.
            modifier = Modifier.widthIn(min = 96.dp),
        )
        Text(
            text = draft.derivation() ?: draft.missingHint(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CommitRow(draft: RoundDraft, onCommit: (Boolean) -> Unit) {
    val colors = MaterialTheme.scoreColors
    val ready = draft.readyForResult

    if (draft.isRamsch) {
        // Der Ramsch kennt kein Gewonnen und kein Verloren - der Verlierer steht
        // schon in den Augen. Eine einzelne Flaeche bleibt trotzdem der
        // Speichern-Knopf, es gibt auch hier keine Rueckfrage.
        CommitButton(
            label = "Ramsch eintragen",
            container = colors.lossContainer,
            onContainer = colors.onLossContainer,
            enabled = ready,
            placeholder = "Augen eintragen",
            onClick = { onCommit(false) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    if (draft.effectiveOverbid) {
        // Ueberreizt ist immer verloren, egal wie die Stiche lagen. Zwei Knoepfe
        // mit derselben Wirkung und demselben Minus verwirrten genau dort, wo
        // schnell getippt wird.
        CommitButton(
            label = "Überreizt – verloren",
            detail = draft.declarerHalfPoints(won = false)?.let { formatPoints(it) },
            container = colors.lossContainer,
            onContainer = colors.onLossContainer,
            enabled = ready,
            placeholder = "- ?",
            onClick = { onCommit(false) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Gewonnen ist breiter: es ist der haeufigere Ausgang und liegt damit
        // dort, wo der Daumen ohne Hinsehen landet.
        CommitButton(
            label = "Gewonnen",
            detail = draft.declarerHalfPoints(won = true)?.let { formatPoints(it) },
            container = colors.gainContainer,
            onContainer = colors.onGainContainer,
            enabled = ready,
            placeholder = "+ ?",
            onClick = { onCommit(true) },
            previous = draft.originalWon == true,
            modifier = Modifier.weight(5f),
        )
        CommitButton(
            label = "Verloren",
            detail = draft.declarerHalfPoints(won = false)?.let { formatPoints(it) },
            container = colors.lossContainer,
            onContainer = colors.onLossContainer,
            enabled = ready,
            placeholder = "- ?",
            onClick = { onCommit(false) },
            previous = draft.originalWon == false,
            modifier = Modifier.weight(4f),
        )
    }
}
```

Den KDoc-Absatz ueber `GameValueDisplay` ("Zahl und Herleitung stehen nebeneinander ...") unveraendert lassen; `Surface`/`RoundedCornerShape`-Imports entfernen, falls sie danach ungenutzt sind (der Compiler warnt, `ExtrasSection` nutzt sie weiter).

- [ ] **Step 5: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.common.CommitButtonTest" --tests "io.rotaskat.app.ui.session.SessionLayoutTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/io/rotaskat/app/ui/common/Controls.kt app/src/main/kotlin/io/rotaskat/app/ui/round/RoundEntryPanel.kt app/src/test/kotlin/io/rotaskat/app/ui/common/CommitButtonTest.kt
git commit -m "Ergebnisbuttons als satte Flaechen, vor der Auswahl als gestrichelter Umriss" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Kurze Herleitung des Spielwerts

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/round/RoundDraft.kt:518-551` (`suitDerivation`, `ramschDerivation`, `contraSuffix`)
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/round/RoundDraftDerivationTest.kt`

**Interfaces:**
- Consumes: bestehende `RoundDraft`-Felder `announcement`, `effectiveAchieved`, `achievedFloor`, `contra`, `matadors`.
- Produces: `RoundDraft.derivation()` mit neuem Format (Signatur unveraendert).

- [ ] **Step 1: Failing test schreiben**

```kotlin
package io.rotaskat.app.ui.round

import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.Suit
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Die Herleitung neben dem Spielwert. Bei Ouvert wurde sie im Geraetetest nach
 * drei Zeilen abgeschnitten - ausgerechnet "= 12 x 9 = 108" fehlte. Die
 * mitgesetzten Stufen stehen bei den Zusaetzen, hier steht nur die Ansage.
 */
class RoundDraftDerivationTest {

    private fun draft(game: GamePick, matadors: Int) = RoundDraft.forNextRound(
        roundId = "r0",
        seatCount = 3,
        dealerSeat = 0,
        config = TEST_CLUB.scoring,
    ).copy(declarerSeat = 1).withGame(game).copy(matadors = matadors)

    @Test
    fun `Farbspiel ohne Zusaetze`() {
        assertEquals("Kreuz mit 2 = 12 × 3", draft(GamePick.Colour(Suit.CLUBS), 2).derivation())
    }

    @Test
    fun `Ouvert nennt nur die Ansage`() {
        val d = draft(GamePick.Colour(Suit.CLUBS), 2).copy(announcement = Announcement.OUVERT)
        assertEquals("Kreuz mit 2 · Ouvert = 12 × 9", d.derivation())
    }

    @Test
    fun `Hand mit erreichtem Schneider nennt beides`() {
        val d = draft(GamePick.Colour(Suit.SPADES), 1)
            .copy(announcement = Announcement.HAND, achieved = Achieved.SCHNEIDER)
        assertEquals("Pik mit 1 · Hand · Schneider = 11 × 4", d.derivation())
    }

    @Test
    fun `Kontra haengt den Faktor und das Ergebnis an`() {
        val d = draft(GamePick.Colour(Suit.CLUBS), 2).copy(contra = ContraLevel.KONTRA)
        assertEquals("Kreuz mit 2 = 12 × 3 × 2 (Kontra) = 72", d.derivation())
    }

    @Test
    fun `Grand`() {
        assertEquals("Grand mit 1 = 24 × 2", draft(GamePick.Grand, 1).derivation())
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.round.RoundDraftDerivationTest"`
Expected: FAIL, z. B. `expected: <Kreuz mit 2 = 12 × 3> but was: <Kreuz mit 2 = 12 x 3 = 36>`.

- [ ] **Step 3: Implementieren**

In `RoundDraft.kt` `suitDerivation` ersetzen und `shortLevelLabels` direkt darunter einfuegen:

```kotlin
    private fun suitDerivation(name: String, base: Int, matadors: Int): String {
        val level = Scoring.gameLevel(matadors, modifiers)
        val levels = shortLevelLabels().joinToString("") { " · $it" }
        // Ohne "= 36" am Ende: die Zahl steht gross daneben. Die Herleitung
        // soll pruefbar machen, wie sie zustande kommt, nicht sie wiederholen.
        val regular = "$name mit $matadors$levels = $base × $level"
        if (!effectiveOverbid) return regular + contraSuffix()
        // Ueberreizt: der regulaere Wert traegt nicht mehr, gerechnet wird mit
        // dem kleinsten Vielfachen des Grundwerts, das den Reizwert erreicht.
        val declaration = declaration ?: return regular
        val overbidValue = Scoring.overbidValue(declaration, bid)
        return "$regular, überreizt auf $bid = $overbidValue" + contraSuffix()
    }

    /**
     * Die Stufen in Kurzform: die Ansage und, falls hoeher, das Erreichte.
     *
     * Was `normalized()` mitsetzt - Ouvert bringt Hand, Schneider und Schwarz
     * angesagt mit -, steht bei den Zusaetzen sichtbar umgelegt und steckt in
     * der Stufenzahl. Es hier noch einmal aufzuzaehlen, liess die Herleitung bei
     * Ouvert auf drei Zeilen wachsen.
     */
    private fun shortLevelLabels(): List<String> = buildList {
        if (announcement != Announcement.NONE) add(announcement.label)
        if (effectiveAchieved > achievedFloor) {
            add(effectiveAchieved.label.replaceFirstChar { it.uppercase() })
        }
    }
```

In `ramschDerivation` die zwei Faktoren auf das Malzeichen umstellen:

```kotlin
            if (config.jungfrauDoubles && ramsch.jungfrau) add("× 2 (Jungfrau)")
            if (config.pushDoubles && ramsch.pushes > 0) {
                add("× ${1 shl ramsch.pushes} (${ramsch.pushes} Schub)")
            }
```

`contraSuffix` ersetzen:

```kotlin
    private fun contraSuffix(): String = when (contra) {
        ContraLevel.NONE -> ""
        else -> " × ${contra.multiplier} (${contra.label}) = ${gameValue ?: 0}"
    }
```

Ist `modifierLabels` danach in `RoundDraft.kt` ungenutzt, den Import entfernen (die Funktion selbst bleibt in `Labels.kt`, sie wird fuer die Rundenliste gebraucht).

- [ ] **Step 4: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.round.*"`
Expected: PASS (neue Tests und alle bestehenden `round`-Tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/rotaskat/app/ui/round/RoundDraft.kt app/src/test/kotlin/io/rotaskat/app/ui/round/RoundDraftDerivationTest.kt
git commit -m "Herleitung des Spielwerts auf Ansage und Rechenweg kuerzen" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Icons, Kopfzeile mit Zurueck-Pfeil

**Files:**
- Create: `app/src/main/res/drawable/ic_undo.xml`, `ic_bar_chart.xml`, `ic_show_chart.xml`, `ic_swap_horiz.xml`, `ic_flag.xml`, `ic_emoji_events.xml`, `ic_style.xml`, `ic_euro.xml`
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/common/TopBar.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/EvalScaffold.kt:44-67`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/onboarding/JoinScreen.kt:82-91`, `onboarding/LocalSetupScreen.kt:75-78`, `session/NewSessionScreen.kt:82-85`
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/common/TopBarTest.kt`

**Interfaces:**
- Produces: `RotaskatTopBar(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})`; Drawables `R.drawable.ic_undo`, `ic_bar_chart`, `ic_show_chart`, `ic_swap_horiz`, `ic_flag`, `ic_emoji_events`, `ic_style`, `ic_euro`.

- [ ] **Step 1: Failing test schreiben**

```kotlin
package io.rotaskat.app.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TopBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Zurueck ist ein Pfeil mit Beschriftung fuer TalkBack`() {
        var backs = 0
        compose.setContent {
            RotaskatTheme { RotaskatTopBar(title = "Neuer Abend", onBack = { backs++ }) }
        }
        compose.onNodeWithText("Neuer Abend").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `ohne onBack gibt es keinen Pfeil`() {
        compose.setContent { RotaskatTheme { RotaskatTopBar(title = "Rangliste") } }
        compose.onNodeWithContentDescription("Zurück").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.common.TopBarTest"`
Expected: Compile-Fehler `Unresolved reference: RotaskatTopBar`.

- [ ] **Step 3: Drawables anlegen**

Jede Datei nach diesem Muster, nur `pathData` wechselt (Pfade aus `google/material-design-icons`, `src/<kategorie>/<name>/materialicons/24px.svg`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="PFAD" />
</vector>
```

| Datei | pathData |
|---|---|
| `ic_undo.xml` | `M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z` |
| `ic_bar_chart.xml` | `M4,9h4v11H4zM16,13h4v7h-4zM10,4h4v16h-4z` |
| `ic_show_chart.xml` | `M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z` |
| `ic_swap_horiz.xml` | `M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z` |
| `ic_flag.xml` | `M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z` |
| `ic_emoji_events.xml` | `M19,5h-2V3H7v2H5C3.9,5,3,5.9,3,7v1c0,2.55,1.92,4.63,4.39,4.94c0.63,1.5,1.98,2.63,3.61,2.96V19H7v2h10v-2h-4v-3.1 c1.63-0.33,2.98-1.46,3.61-2.96C19.08,12.63,21,10.55,21,8V7C21,5.9,20.1,5,19,5z M5,8V7h2v3.82C5.84,10.4,5,9.3,5,8z M19,8 c0,1.3-0.84,2.4-2,2.82V7h2V8z` |
| `ic_style.xml` | `M2.53 19.65l1.34.56v-9.03l-2.43 5.86c-.41 1.02.08 2.19 1.09 2.61zm19.5-3.7L17.07 3.98c-.31-.75-1.04-1.21-1.81-1.23-.26 0-.53.04-.79.15L7.1 5.95c-.75.31-1.21 1.03-1.23 1.8-.01.27.04.54.15.8l4.96 11.97c.31.76 1.05 1.22 1.83 1.23.26 0 .52-.05.77-.15l7.36-3.05c1.02-.42 1.51-1.59 1.09-2.6zM7.88 8.75c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm-2 11c0 1.1.9 2 2 2h1.45l-3.45-8.34v6.34z` |
| `ic_euro.xml` | `M15 18.5c-2.51 0-4.68-1.42-5.76-3.5H15v-2H8.58c-.05-.33-.08-.66-.08-1s.03-.67.08-1H15V9H9.24C10.32 6.92 12.5 5.5 15 5.5c1.61 0 3.09.59 4.23 1.57L21 5.3C19.41 3.87 17.3 3 15 3c-3.92 0-7.24 2.51-8.48 6H3v2h3.06c-.04.33-.06.66-.06 1 0 .34.02.67.06 1H3v2h3.52c1.24 3.49 4.56 6 8.48 6 2.31 0 4.41-.87 6-2.3l-1.78-1.77c-1.13.98-2.6 1.57-4.22 1.57z` |

- [ ] **Step 4: `TopBar.kt` anlegen**

```kotlin
package io.rotaskat.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Die Kopfzeile aller Bildschirme.
 *
 * Zurueck ist ein Pfeil, weitere Aktionen sind Symbole oder stehen im Menue -
 * wie auf Android ueblich. Die frueheren Textknoepfe ("Zurueck", "Rangliste",
 * "Abend beenden") kosteten Platz und machten die folgenreichste Aktion zur
 * am besten erreichbaren.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotaskatTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        modifier = modifier,
    )
}
```

- [ ] **Step 5: Aufrufer umstellen**

`EvalScaffold.kt`, im `Scaffold` den `topBar`-Block ersetzen:

```kotlin
        topBar = {
            RotaskatTopBar(title = title, subtitle = subtitle, onBack = onBack, actions = actions)
        },
```

`LocalSetupScreen.kt`:

```kotlin
        topBar = { RotaskatTopBar(title = "Ohne Verein", onBack = onBack) },
```

`NewSessionScreen.kt`:

```kotlin
        topBar = { RotaskatTopBar(title = "Neuer Abend", onBack = onBack) },
```

`JoinScreen.kt`:

```kotlin
        topBar = {
            RotaskatTopBar(
                title = "Verein beitreten",
                onBack = { if (state is JoinUiState.ChoosePlayer) viewModel.backToEntry() else onBack() },
            )
        },
```

In allen vier Dateien `import io.rotaskat.app.ui.common.RotaskatTopBar` ergaenzen und nun ungenutzte Imports (`TopAppBar`, `TextButton`, `ExperimentalMaterial3Api`, `Column` in `EvalScaffold`) entfernen, sofern der Compiler sie als ungenutzt meldet. `@OptIn(ExperimentalMaterial3Api::class)` an den Screen-Funktionen entfernen, wenn nichts Experimentelles mehr darin steht.

- [ ] **Step 6: Tests laufen lassen und Rest suchen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.common.TopBarTest"` -> PASS.
Run: `grep -rn 'Text("Zurück")' app/src/main/kotlin` -> keine Treffer.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/drawable app/src/main/kotlin/io/rotaskat/app/ui/common/TopBar.kt app/src/main/kotlin/io/rotaskat/app/ui/eval/EvalScaffold.kt app/src/main/kotlin/io/rotaskat/app/ui/onboarding app/src/main/kotlin/io/rotaskat/app/ui/session/NewSessionScreen.kt app/src/test/kotlin/io/rotaskat/app/ui/common/TopBarTest.kt
git commit -m "Einheitliche Kopfzeile mit Zurueck-Pfeil statt Textknopf" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Untere Leiste, Uebergaenge, Eingabesperre

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/nav/Routes.kt` (`Routes.TOP_LEVEL`, `Routes.isTopLevel`, `RotaskatNavActions.toTopLevel`, `toLeaderboard`, `toStats`)
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/nav/BottomBar.kt`
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/nav/Transitions.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/nav/RotaskatApp.kt` (`RotaskatNavHost`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/LeaderboardScreen.kt:40`, `eval/StatsScreen.kt:54`, `eval/OverviewScreen.kt:69-84`
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/nav/BottomBarTest.kt`, `app/src/test/kotlin/io/rotaskat/app/ui/nav/LeavingGuardTest.kt`

**Interfaces:**
- Consumes: Drawables aus Task 6, `MaterialTheme.accentColors` (Task 1).
- Produces: `Routes.TOP_LEVEL: List<String>`, `Routes.isTopLevel(route: String?): Boolean`, `RotaskatNavActions.toTopLevel(route: String)`, `RotaskatBottomBar(currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier)`, `AnimatedVisibilityScope.LeavingGuard(content: @Composable () -> Unit)`.

- [ ] **Step 1: Failing tests schreiben**

`BottomBarTest.kt`:

```kotlin
package io.rotaskat.app.ui.nav

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BottomBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `die Leiste steht nur auf Abende, Rangliste und Statistik`() {
        assertTrue(Routes.isTopLevel(Routes.HOME))
        assertTrue(Routes.isTopLevel(Routes.LEADERBOARD))
        assertTrue(Routes.isTopLevel(Routes.STATS))
        assertFalse(Routes.isTopLevel(Routes.SESSION_PATTERN))
        assertFalse(Routes.isTopLevel(Routes.SETTINGS))
        assertFalse(Routes.isTopLevel(Routes.ONBOARDING))
        assertFalse(Routes.isTopLevel(null))
    }

    @Test
    fun `aktives Ziel ist gewaehlt, ein Tap meldet das neue Ziel`() {
        var selected: String? = null
        compose.setContent {
            RotaskatTheme { RotaskatBottomBar(currentRoute = Routes.LEADERBOARD, onSelect = { selected = it }) }
        }
        compose.onNodeWithText("Rangliste").assertIsSelected()
        compose.onNodeWithText("Abende").assertIsNotSelected()
        compose.onNodeWithText("Statistik").performClick()
        assertEquals(Routes.STATS, selected)
    }

    @Test
    fun `ein Tap auf das aktive Ziel navigiert nicht erneut`() {
        var selected: String? = null
        compose.setContent {
            RotaskatTheme { RotaskatBottomBar(currentRoute = Routes.HOME, onSelect = { selected = it }) }
        }
        compose.onNodeWithText("Abende").performClick()
        assertEquals(null, selected)
    }
}
```

`LeavingGuardTest.kt`:

```kotlin
package io.rotaskat.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Im Geraetetest oeffnete ein Tap aufs Zahnrad kurz nach "Zurueck" noch den
 * Dialog "Abend beenden?" des verlassenen Abends.
 */
@RunWith(RobolectricTestRunner::class)
class LeavingGuardTest {

    @get:Rule
    val compose = createComposeRule()

    private var visible by mutableStateOf(true)
    private var clicks = 0

    private fun show() {
        compose.setContent {
            AnimatedVisibility(visible = visible, exit = fadeOut(tween(1_000))) {
                LeavingGuard {
                    Box(Modifier.size(200.dp).testTag("screen").clickable { clicks++ })
                }
            }
        }
    }

    @Test
    fun `ein Tap auf den sichtbaren Bildschirm kommt an`() {
        show()
        compose.onNodeWithTag("screen").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `ein Tap auf den Bildschirm, der gerade verschwindet, kommt nicht an`() {
        show()
        compose.mainClock.autoAdvance = false
        visible = false
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("screen").performClick()
        assertEquals(0, clicks)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, sie muessen fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.nav.*"`
Expected: Compile-Fehler `Unresolved reference: isTopLevel` / `RotaskatBottomBar` / `LeavingGuard`.

- [ ] **Step 3: `Routes.kt` erweitern**

Im `object Routes` unter `SETTINGS`:

```kotlin
    /**
     * Die Ziele der unteren Leiste. Nur auf ihnen steht die Leiste - im Abend
     * braucht die Eingabe den ganzen Platz, und in Unterseiten fuehrt der
     * Zurueck-Pfeil.
     */
    val TOP_LEVEL: List<String> = listOf(HOME, LEADERBOARD, STATS)

    fun isTopLevel(route: String?): Boolean = route in TOP_LEVEL
```

In `RotaskatNavActions` `toLeaderboard` und `toStats` ersetzen und `toTopLevel` ergaenzen:

```kotlin
    /**
     * Wechsel zwischen den Zielen der unteren Leiste. Der Stapel waechst dabei
     * nicht: zurueck fuehrt von jedem Ziel in die Uebersicht der Abende, und
     * Scrollstand und Zeitraum eines Ziels bleiben beim Wiederkommen erhalten.
     */
    fun toTopLevel(route: String) = navController.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun toLeaderboard() = toTopLevel(Routes.LEADERBOARD)

    fun toStats() = toTopLevel(Routes.STATS)
```

- [ ] **Step 4: `BottomBar.kt` anlegen**

```kotlin
package io.rotaskat.app.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.rotaskat.app.R
import io.rotaskat.app.ui.theme.accentColors

private data class TopLevelDestination(
    val route: String,
    val label: String,
    @DrawableRes val icon: Int,
)

private val TopLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, "Abende", R.drawable.ic_style),
    TopLevelDestination(Routes.LEADERBOARD, "Rangliste", R.drawable.ic_emoji_events),
    TopLevelDestination(Routes.STATS, "Statistik", R.drawable.ic_bar_chart),
)

/**
 * Die untere Leiste: Abende, Rangliste, Statistik.
 *
 * Unten statt als Textknoepfe in der Kopfzeile, weil sie dort mit dem Daumen
 * erreichbar ist und die Kopfzeile fuer den Titel frei bleibt.
 */
@Composable
fun RotaskatBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val muted = MaterialTheme.accentColors.labelMuted
    NavigationBar(containerColor = colors.surfaceContainerLow, modifier = modifier) {
        for (destination in TopLevelDestinations) {
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination.route) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = muted,
                    unselectedTextColor = muted,
                ),
            )
        }
    }
}
```

- [ ] **Step 5: `Transitions.kt` anlegen**

```kotlin
package io.rotaskat.app.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Die Bildschirmwechsel.
 *
 * Kurz: 150ms hinein, 100ms hinaus. Die Crossfades der Vorgabe dauerten im
 * Geraetetest gut eine halbe Sekunde, das fuehlte sich traege an und liess
 * Taps auf dem alten Bildschirm landen. Zwischen den Zielen der unteren Leiste
 * nur ein Fade, sonst ein kleiner Versatz in Laufrichtung.
 */
internal object RotaskatTransitions {

    private const val ENTER_MS = 150
    private const val EXIT_MS = 100

    private fun lateral(from: String?, to: String?): Boolean =
        !(Routes.isTopLevel(from) && Routes.isTopLevel(to))

    fun enter(from: String?, to: String?): EnterTransition =
        if (lateral(from, to)) {
            fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { it / 12 }
        } else {
            fadeIn(tween(ENTER_MS))
        }

    fun popEnter(from: String?, to: String?): EnterTransition =
        if (lateral(from, to)) {
            fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { -it / 12 }
        } else {
            fadeIn(tween(ENTER_MS))
        }

    fun exit(): ExitTransition = fadeOut(tween(EXIT_MS))
}

/**
 * Sperrt einen Bildschirm fuer Eingaben, sobald er verlassen wird.
 *
 * Ein Tap kurz nach "Zurueck" traf sonst noch den alten Bildschirm - im
 * Geraetetest oeffnete das den Dialog "Abend beenden?" eines Abends, den man
 * gerade verlassen hatte. Die Events werden im ersten Durchlauf verbraucht,
 * bevor ein Knopf darunter sie sieht.
 */
@Composable
internal fun AnimatedVisibilityScope.LeavingGuard(content: @Composable () -> Unit) {
    val leaving = transition.targetState != EnterExitState.Visible
    val guard = if (leaving) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else {
        Modifier
    }
    Box(Modifier.fillMaxSize().then(guard)) { content() }
}
```

- [ ] **Step 6: `RotaskatNavHost` umbauen**

In `RotaskatApp.kt` den Rumpf von `RotaskatNavHost` ab `NavHost(` ersetzen. Die Leiste sitzt in einem aeusseren `Scaffold`; dessen Innenabstand wird verbraucht, damit die inneren `Scaffold`s der Bildschirme den Abstand fuer die Gestenleiste nicht ein zweites Mal addieren.

```kotlin
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (Routes.isTopLevel(currentRoute)) {
                RotaskatBottomBar(currentRoute = currentRoute, onSelect = actions::toTopLevel)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = {
                RotaskatTransitions.enter(initialState.destination.route, targetState.destination.route)
            },
            exitTransition = { RotaskatTransitions.exit() },
            popEnterTransition = {
                RotaskatTransitions.popEnter(initialState.destination.route, targetState.destination.route)
            },
            popExitTransition = { RotaskatTransitions.exit() },
        ) {
            guarded(Routes.ONBOARDING) {
                OnboardingScreen(
                    onLocal = { actions.toLocalSetup() },
                    onJoin = { actions.toJoin() },
                )
            }
            guarded(Routes.LOCAL_SETUP) {
                LocalSetupScreen(onDone = { actions.toHome() }, onBack = { actions.back() })
            }
            guarded(Routes.JOIN) {
                JoinScreen(onDone = { actions.toHome() }, onBack = { actions.back() })
            }
            guarded(Routes.NEW_SESSION) {
                NewSessionScreen(
                    onStarted = { sessionId -> actions.toSession(sessionId, replace = true) },
                    onBack = { actions.back() },
                )
            }
            guarded(Routes.HOME) { OverviewScreen(actions = actions) }
            guarded(Routes.SESSION_PATTERN, listOf(sessionArg)) { entry ->
                val sessionId = entry.sessionId() ?: return@guarded
                SessionScreen(sessionId = sessionId, actions = actions)
            }
            guarded(Routes.ROUND_EDIT_PATTERN, listOf(sessionArg, roundArg)) { entry ->
                val sessionId = entry.sessionId() ?: return@guarded
                val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID) ?: return@guarded
                SessionScreen(sessionId = sessionId, actions = actions, editRoundId = roundId)
            }
            guarded(Routes.SETTLEMENT_PATTERN, listOf(sessionArg)) { entry ->
                val sessionId = entry.sessionId() ?: return@guarded
                SettlementScreen(sessionId = sessionId, actions = actions)
            }
            guarded(Routes.HISTORY_PATTERN, listOf(sessionArg)) { entry ->
                val sessionId = entry.sessionId() ?: return@guarded
                ProgressScreen(sessionId = sessionId, actions = actions)
            }
            guarded(Routes.LEADERBOARD) { LeaderboardScreen(actions = actions) }
            guarded(Routes.STATS) { StatsScreen(actions = actions) }
            guarded(Routes.SETTINGS) { SettingsScreen(actions = actions) }
        }
    }
}

private val sessionArg = navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }
private val roundArg = navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType }

/** Ein Ziel, dessen Bildschirm waehrend des Verlassens keine Taps mehr annimmt. */
private fun NavGraphBuilder.guarded(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route = route, arguments = arguments) { entry ->
    LeavingGuard { content(entry) }
}
```

Imports ergaenzen:

```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.currentBackStackEntryAsState
```

Die bestehende private Funktion `NavBackStackEntry.sessionId()` bleibt; ihr Typ-Praefix `androidx.navigation.NavBackStackEntry` kann auf `NavBackStackEntry` gekuerzt werden.

- [ ] **Step 7: Tab-Bildschirme ohne Zurueck, Startseite ohne Textknoepfe**

`LeaderboardScreen.kt` und `StatsScreen.kt`: `onBack = { actions.back() },` ersetzen durch `onBack = null,`.

`OverviewScreen.kt`, `topBar`-Block ersetzen (die Startseite selbst wird in Task 10 neu gebaut; hier nur, damit keine doppelte Navigation stehen bleibt):

```kotlin
        topBar = {
            RotaskatTopBar(
                title = "Rotaskat",
                actions = {
                    IconButton(onClick = { actions.toSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
```

Import `io.rotaskat.app.ui.common.RotaskatTopBar` ergaenzen, `TopAppBar`/`TextButton` entfernen.

- [ ] **Step 8: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (inklusive `BottomBarTest`, `LeavingGuardTest`).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/io/rotaskat/app/ui/nav app/src/main/kotlin/io/rotaskat/app/ui/eval/LeaderboardScreen.kt app/src/main/kotlin/io/rotaskat/app/ui/eval/StatsScreen.kt app/src/main/kotlin/io/rotaskat/app/ui/eval/OverviewScreen.kt app/src/test/kotlin/io/rotaskat/app/ui/nav
git commit -m "Untere Leiste fuer Abende, Rangliste und Statistik, kurze Uebergaenge ohne verirrte Taps" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Stand mit Geber-Marke und Geberwahl im Sitzring

**Files:**
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/session/Scoreboard.kt` (Stand aus `SessionScreen.kt:355-417` herausziehen und neu bauen)
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/session/DealerSheet.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/session/SessionScreen.kt` (`SessionBody`-Signatur, Aufruf, Sheet)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/round/RoundEntryPanel.kt` (`DealerHeader` entfernen, Parameter `onDealerChange` entfernen)
- Modify: `app/src/test/kotlin/io/rotaskat/app/ui/session/SessionLayoutTest.kt` (Parameter)
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/session/ScoreboardTest.kt`

**Interfaces:**
- Consumes: `OptionTile` (Task 3), `SeatRing` (bestehend, `ui/common/SeatRing.kt`), `RotaskatTextStyles.standName` (Task 2), `R.drawable.ic_style` (Task 6).
- Produces: `Scoreboard(state: SessionState, names: Map<Int, String>, dealerSeat: Int?, onDealerClick: (() -> Unit)?, modifier: Modifier = Modifier)`; `DealerPicker(seatCount: Int, names: Map<Int, String>, dealerSeat: Int, onPick: (Int) -> Unit)`; `DealerSheet(seatCount: Int, names: Map<Int, String>, dealerSeat: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit)`; `SessionBody(..., onDealerClick: () -> Unit, ...)` statt `onDealerChange`; `RoundEntryPanel` ohne `onDealerChange`.

- [ ] **Step 1: Failing test schreiben**

```kotlin
package io.rotaskat.app.ui.session

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.ui.round.RoundDraft
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Session
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class ScoreboardTest {

    @get:Rule
    val compose = createComposeRule()

    private val names = mapOf(0 to "Anna", 1 to "Ben", 2 to "Johannes", 3 to "Lars")

    private fun state(dealerSeat: Int) = SessionState(
        Session(
            id = "s",
            clubId = TEST_CLUB.id,
            seatCount = 4,
            seats = (0 until 4).associateWith { "p$it" },
            startedAt = T0,
            scoring = TEST_CLUB.scoring,
            dealerSeat = dealerSeat,
        ),
        emptyList(),
        mapOf(0 to 96L, 1 to -24L, 2 to 6L, 3 to 0L),
    )

    @Test
    fun `der Geber ist markiert und oeffnet per Tap die Geberwahl`() {
        var opened = 0
        compose.setContent {
            RotaskatTheme {
                Scoreboard(state = state(3), names = names, dealerSeat = 3, onDealerClick = { opened++ })
            }
        }
        compose.onNodeWithContentDescription("Lars, +0, gibt", substring = true).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `in der Korrektur markiert der Stand den Geber der Runde, nicht die Rotation`() {
        val draft = RoundDraft.forNextRound(
            roundId = "r0",
            seatCount = 4,
            dealerSeat = 1,
            config = TEST_CLUB.scoring,
        ).copy(editing = true)
        compose.setContent {
            RotaskatTheme {
                SessionBody(
                    state = state(dealerSeat = 3),
                    draft = draft,
                    names = names,
                    editing = true,
                    scrollState = rememberScrollState(),
                    onDraftChange = {},
                    onDealerClick = {},
                    onCommit = {},
                    onEditRound = {},
                    onCancelEdit = {},
                    onDelete = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Ben, -12, gibt", substring = true).assertExists()
        compose.onNodeWithContentDescription("Lars, +0, gibt", substring = true).assertDoesNotExist()
    }

    @Test
    fun `der Sitzring waehlt den Geber mit einem Tap`() {
        var picked: Int? = null
        compose.setContent {
            RotaskatTheme {
                DealerPicker(seatCount = 4, names = names, dealerSeat = 3, onPick = { picked = it })
            }
        }
        compose.onNodeWithText("Lars").assertIsSelected()
        compose.onNodeWithText("Ben").performClick()
        assertEquals(1, picked)
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.session.ScoreboardTest"`
Expected: Compile-Fehler (`Scoreboard` ist privat / hat keine Parameter `dealerSeat`, `onDealerClick` unbekannt, `DealerPicker` fehlt).

- [ ] **Step 3: `Scoreboard.kt` anlegen**

```kotlin
package io.rotaskat.app.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rotaskat.app.R
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.scoreColors

/**
 * Der Stand.
 *
 * Punkte immer mit Vorzeichen und in Tabellenziffern; die Farbe ist der
 * Zweitkanal, nie der einzige Traeger. Wer aussetzt, steht mit dabei - eine
 * Spalte, die verschwindet und wiederkommt, laesst die Tabelle bei jedem Blick
 * anders aussehen.
 *
 * Der Geber steht in Gold mit einem Kartensymbol in der Namenszeile statt mit
 * "· gibt" dahinter: das Wort kuerzte im Geraetetest den Namen ("Joha… · gibt"),
 * und eine eigene dritte Zeile kostete genau den Platz, der fuer die Spitzen
 * fehlte. Am Vierertisch ist die Spalte zusaetzlich gedimmt - der Geber setzt
 * dort aus. Ein Tap auf den Geber oeffnet die Geberwahl; das sind zwei Taps bis
 * zur Korrektur, wie vorher mit der aufklappbaren Reihe.
 *
 * [dealerSeat] `null` heisst: keine Marke (beendeter Abend).
 */
@Composable
internal fun Scoreboard(
    state: SessionState,
    names: Map<Int, String>,
    dealerSeat: Int?,
    onDealerClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.scoreColors
    val gold = MaterialTheme.colorScheme.primary
    val seatCount = state.session.seatCount
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            for (seat in 0 until seatCount) {
                val half = state.totals[seat] ?: 0L
                val name = names[seat] ?: "Platz ${seat + 1}"
                val points = formatPoints(half)
                val isDealer = seat == dealerSeat
                val sitsOut = isDealer && seatCount == 4
                val interaction = if (isDealer && onDealerClick != null) {
                    Modifier.clickable(onClickLabel = "Geber ändern", onClick = onDealerClick)
                } else {
                    Modifier
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .then(interaction)
                        .clearAndSetSemantics {
                            contentDescription = if (isDealer) "$name, $points, gibt" else "$name, $points"
                            if (isDealer && onDealerClick != null) {
                                onClick(label = "Geber ändern") { onDealerClick(); true }
                            }
                        }
                        .alpha(if (sitsOut) 0.55f else 1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = name,
                            style = RotaskatTextStyles.standName,
                            color = if (isDealer) gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isDealer) {
                            Icon(
                                painter = painterResource(R.drawable.ic_style),
                                contentDescription = null,
                                tint = gold,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = points,
                        style = RotaskatTextStyles.scoreLarge,
                        color = when {
                            half > 0 -> colors.gain
                            half < 0 -> colors.loss
                            else -> colors.neutral
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}
```

- [ ] **Step 4: `DealerSheet.kt` anlegen**

```kotlin
package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.common.OptionTile
import io.rotaskat.app.ui.common.SeatRing
import io.rotaskat.app.ui.theme.RotaskatDimens

/**
 * "Wer gibt?" als Sheet mit dem Sitzring.
 *
 * Frueher klappte die Geberwahl als zweite, fast identische Namensreihe direkt
 * ueber der Alleinspieler-Reihe auf und war mit ihr verwechselbar. Der
 * Sitzring zeigt die Plaetze so, wie sie am Tisch liegen - dieselbe Form wie
 * beim Anlegen des Abends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DealerSheet(
    seatCount: Int,
    names: Map<Int, String>,
    dealerSeat: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        ) {
            Text("Wer gibt?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            DealerPicker(seatCount = seatCount, names = names, dealerSeat = dealerSeat, onPick = onPick)
        }
    }
}

/** Der Inhalt des Sheets, ohne Sheet - damit er sich ohne Popup testen laesst. */
@Composable
internal fun DealerPicker(
    seatCount: Int,
    names: Map<Int, String>,
    dealerSeat: Int,
    onPick: (Int) -> Unit,
) {
    SeatRing(seatCount = seatCount) { seat ->
        OptionTile(
            label = names[seat] ?: "Platz ${seat + 1}",
            selected = seat == dealerSeat,
            onClick = { onPick(seat) },
            height = RotaskatDimens.seatSlotSize,
            modifier = Modifier.size(RotaskatDimens.seatSlotSize),
        )
    }
}
```

- [ ] **Step 5: `SessionScreen.kt` umbauen**

1. Die private Funktion `Scoreboard` samt KDoc (Zeilen 355-417) loeschen.
2. In `SessionBody` den Parameter `onDealerChange: (Int) -> Unit` ersetzen durch `onDealerClick: () -> Unit`, und den `Scoreboard`-Aufruf ersetzen:

```kotlin
        // Nach dem Abend gibt niemand mehr - die Marke waere dort nur Rauschen.
        // In der Korrektur zaehlt der Geber der Runde, nicht die Rotation.
        val dealerSeat = when {
            state.session.status != SessionStatus.OPEN -> null
            editing -> draft.dealerSeat
            else -> state.session.dealerSeat
        }
        Scoreboard(
            state = state,
            names = names,
            dealerSeat = dealerSeat,
            onDealerClick = if (editing) null else onDealerClick,
        )
```

3. Im `RoundEntryPanel`-Aufruf in `SessionBody` die Zeile `onDealerChange = onDealerChange,` entfernen.
4. In `SessionScreen` einen Zustand fuer das Sheet anlegen (neben `confirmEnd`):

```kotlin
    var dealerSheet by rememberSaveable { mutableStateOf(false) }
```

5. Im `SessionBody`-Aufruf `onDealerChange = viewModel::setDealer,` ersetzen durch `onDealerClick = { dealerSheet = true },`.
6. Unter dem `if (confirmEnd) { ... }`-Block:

```kotlin
    val sheetDraft = draft
    val sheetState = state
    if (dealerSheet && sheetDraft != null && sheetState != null) {
        DealerSheet(
            seatCount = sheetState.session.seatCount,
            names = seatNames(sheetState.session, roster),
            dealerSeat = sheetDraft.dealerSeat,
            onPick = { seat ->
                haptics.select()
                viewModel.setDealer(seat)
                dealerSheet = false
            },
            onDismiss = { dealerSheet = false },
        )
    }
```

7. Nicht mehr benutzte Imports (`RoundedCornerShape`, `Surface` falls nur im Stand genutzt, `TextAlign`) entfernen, sofern der Compiler sie meldet.

- [ ] **Step 6: `RoundEntryPanel.kt` bereinigen**

1. Parameter `onDealerChange: (Int) -> Unit,` aus `RoundEntryPanel` entfernen, ebenso `var dealerExpanded`.
2. Den Block ab `// Der Geber steht im Kopf der Spielerauswahl ...` bis zum Ende des `DealerHeader(...)`-Aufrufs ersetzen durch:

```kotlin
            SectionLabel("Alleinspieler")
```

3. Die Funktion `DealerHeader` samt KDoc loeschen.
4. Im KDoc von `RoundEntryPanel` den Satz zum Geber anpassen: "Der Geber und damit der Aussetzende rotieren automatisch weiter. Korrigiert wird ueber den Stand oben (Tap auf den Geber) oder das Menue."

- [ ] **Step 7: `SessionLayoutTest` nachziehen**

In `show(...)`: `onDealerChange = {},` ersetzen durch `onDealerClick = {},`.

- [ ] **Step 8: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.session.*"`
Expected: PASS (inklusive `SessionLayoutTest` - durch die weggefallene Geberzeile ist eher mehr Platz da).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/io/rotaskat/app/ui/session app/src/main/kotlin/io/rotaskat/app/ui/round/RoundEntryPanel.kt app/src/test/kotlin/io/rotaskat/app/ui/session
git commit -m "Geber im Stand markieren und im Sitzring waehlen statt in einer zweiten Namensreihe" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Kopfzeile und Menue des Abends, Undo-Zeile, Snackbar

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/common/EvalFormat.kt` (`formatShortDate`, `formatTime`)
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/session/SessionTopBar.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/session/SessionScreen.kt` (`topBar`, Snackbar, `sessionTitle`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/session/LastChangeRow.kt:62-104`
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/common/EvalFormatTest.kt`, `app/src/test/kotlin/io/rotaskat/app/ui/session/SessionTopBarTest.kt`, `app/src/test/kotlin/io/rotaskat/app/ui/session/LastChangeRowTest.kt`

**Interfaces:**
- Consumes: `RotaskatTopBar`, Drawables (Task 6), `RotaskatTextStyles.compact` (Task 2).
- Produces: `formatShortDate(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String` ("Sa 14.3."), `formatTime(instant: Instant, zone: TimeZone = ...): String` ("19:30"), `sessionTitle(state: SessionState?, editRoundId: String?, zone: TimeZone = ...): String`, `SessionTopBar(title: String, open: Boolean, editing: Boolean, empty: Boolean, onBack: () -> Unit, onHistory: () -> Unit, onSettlement: () -> Unit, onChangeDealer: () -> Unit, onEnd: () -> Unit)`, `LastChangeTags.ROW`.

- [ ] **Step 1: Failing tests schreiben**

`EvalFormatTest.kt`:

```kotlin
package io.rotaskat.app.ui.common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.test.assertEquals

class EvalFormatTest {

    private val saturdayEvening = Instant.parse("2026-03-14T19:30:00Z")

    @Test
    fun `kurzes Datum mit Wochentag`() {
        assertEquals("Sa 14.3.", formatShortDate(saturdayEvening, TimeZone.UTC))
    }

    @Test
    fun `Uhrzeit ohne Sekunden`() {
        assertEquals("19:30", formatTime(saturdayEvening, TimeZone.UTC))
    }
}
```

`SessionTopBarTest.kt`:

```kotlin
package io.rotaskat.app.ui.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.SessionStatus
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SessionTopBarTest {

    @get:Rule
    val compose = createComposeRule()

    private var ended = 0
    private var settlement = 0

    private fun show(open: Boolean, editing: Boolean = false, empty: Boolean = false) {
        compose.setContent {
            RotaskatTheme {
                SessionTopBar(
                    title = "Sa 14.3. · Runde 9",
                    open = open,
                    editing = editing,
                    empty = empty,
                    onBack = {},
                    onHistory = {},
                    onSettlement = { settlement++ },
                    onChangeDealer = {},
                    onEnd = { ended++ },
                )
            }
        }
    }

    @Test
    fun `Abend beenden liegt im Menue`() {
        show(open = true)
        compose.onNodeWithText("Abend beenden …").assertDoesNotExist()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Geber ändern").assertExists()
        compose.onNodeWithText("Abend beenden …").performClick()
        assertEquals(1, ended)
    }

    @Test
    fun `ein leerer Abend wird verworfen statt beendet`() {
        show(open = true, empty = true)
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Abend verwerfen …").assertExists()
        compose.onNodeWithText("Abend beenden …").assertDoesNotExist()
    }

    @Test
    fun `beendeter Abend zeigt die Abrechnung direkt`() {
        show(open = false)
        compose.onNodeWithContentDescription("Abrechnung").performClick()
        assertEquals(1, settlement)
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Abend beenden …").assertDoesNotExist()
    }

    @Test
    fun `in der Korrektur gibt es kein Menue`() {
        show(open = true, editing = true)
        compose.onNodeWithContentDescription("Weitere Optionen").assertDoesNotExist()
    }

    @Test
    fun `Titel nennt Datum und naechste Runde`() {
        val state = SessionState(
            Session(id = "s", clubId = TEST_CLUB.id, seatCount = 3, startedAt = T0, scoring = TEST_CLUB.scoring),
            emptyList(),
            emptyMap(),
        )
        assertEquals("Sa 14.3. · Runde 1", sessionTitle(state, editRoundId = null, zone = TimeZone.UTC))
        val closed = state.copy(session = state.session.copy(status = SessionStatus.CLOSED))
        assertEquals("Sa 14.3. · beendet", sessionTitle(closed, editRoundId = null, zone = TimeZone.UTC))
        assertEquals("Runde korrigieren", sessionTitle(state, editRoundId = "fehlt", zone = TimeZone.UTC))
    }
}
```

`LastChangeRowTest.kt`:

```kotlin
package io.rotaskat.app.ui.session

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.data.suitRound
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Session
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Nach "Zurueckgenommen" wurde die Zeile ohne Knopf flacher, und das Layout darunter sprang. */
@RunWith(RobolectricTestRunner::class)
class LastChangeRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val state = SessionState(
        Session(id = "s", clubId = TEST_CLUB.id, seatCount = 4, startedAt = T0, scoring = TEST_CLUB.scoring),
        emptyList(),
        emptyMap(),
    )
    private val names = mapOf(0 to "Anna", 1 to "Ben", 2 to "Johannes", 3 to "Lars")
    private val round = suitRound("r-1", dealerSeat = 3, declarerSeat = 2, matadors = 2)

    private fun show(undone: Boolean) {
        val change = LastChange(LastChange.Kind.SAVED, round, UndoToken.Remove(round.id), undone = undone)
        compose.setContent {
            RotaskatTheme { LastChangeRow(change = change, state = state, names = names, onUndo = {}) }
        }
    }

    @Test
    fun `gespeichert hat die feste Hoehe`() {
        show(undone = false)
        compose.onNodeWithTag(LastChangeTags.ROW).assertHeightIsEqualTo(52.dp)
    }

    @Test
    fun `zurueckgenommen hat dieselbe Hoehe`() {
        show(undone = true)
        compose.onNodeWithTag(LastChangeTags.ROW).assertHeightIsEqualTo(52.dp)
    }
}
```

(`LastChange(kind, round, undo, undone: Boolean = false)` steht in `session/SessionUndoLog.kt:26`.)

- [ ] **Step 2: Tests laufen lassen, sie muessen fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.common.EvalFormatTest" --tests "io.rotaskat.app.ui.session.SessionTopBarTest" --tests "io.rotaskat.app.ui.session.LastChangeRowTest"`
Expected: Compile-Fehler (`formatShortDate`, `SessionTopBar`, `sessionTitle`, `LastChangeTags` fehlen).

- [ ] **Step 3: `EvalFormat.kt` ergaenzen** (unter `formatDate`)

```kotlin
private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** Kurzes Datum mit Wochentag fuer Kopfzeilen und Listen: "Sa 14.3.". */
fun formatShortDate(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = instant.toLocalDateTime(zone).date
    return "${WEEKDAYS[date.dayOfWeek.ordinal]} ${date.dayOfMonth}.${date.monthNumber}."
}

/** Uhrzeit ohne Sekunden: "19:30". */
fun formatTime(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = instant.toLocalDateTime(zone).time
    return String.format(Locale.GERMANY, "%02d:%02d", time.hour, time.minute)
}
```

(`kotlinx.datetime.DayOfWeek` ist `java.time.DayOfWeek` mit `MONDAY` an Ordinalposition 0.)

- [ ] **Step 4: `SessionTopBar.kt` anlegen**

```kotlin
package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import io.rotaskat.app.R
import io.rotaskat.app.ui.common.RotaskatTopBar

/**
 * Kopfzeile des Abends.
 *
 * "Abend beenden" steht im Menue, abgesetzt ganz unten und in Gold statt Rot:
 * als gleichwertiger Textknopf oben rechts war die folgenreichste Aktion die
 * am besten erreichbare. Rot bleibt den Punkten vorbehalten. Der
 * Bestaetigungsdialog dahinter bleibt.
 */
@Composable
internal fun SessionTopBar(
    title: String,
    open: Boolean,
    editing: Boolean,
    empty: Boolean,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onSettlement: () -> Unit,
    onChangeDealer: () -> Unit,
    onEnd: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val gold = MaterialTheme.colorScheme.primary
    RotaskatTopBar(
        title = title,
        onBack = onBack,
        actions = {
            if (editing) return@RotaskatTopBar
            if (!open) {
                IconButton(onClick = onSettlement) {
                    Icon(painterResource(R.drawable.ic_euro), contentDescription = "Abrechnung")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen")
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("Punkteverlauf") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_show_chart), contentDescription = null) },
                        onClick = { menu = false; onHistory() },
                    )
                    if (open) {
                        DropdownMenuItem(
                            text = { Text("Zwischenstand abrechnen") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_euro), contentDescription = null) },
                            onClick = { menu = false; onSettlement() },
                        )
                        DropdownMenuItem(
                            text = { Text("Geber ändern") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_swap_horiz), contentDescription = null) },
                            onClick = { menu = false; onChangeDealer() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (empty) "Abend verwerfen …" else "Abend beenden …", color = gold) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_flag), contentDescription = null, tint = gold)
                            },
                            onClick = { menu = false; onEnd() },
                        )
                    }
                }
            }
        },
    )
}
```

- [ ] **Step 5: `SessionScreen.kt` verdrahten**

1. Unter `SessionTags` die Titel-Funktion ergaenzen:

```kotlin
/**
 * Titel des Abends: Datum und die Runde, die gerade eingegeben wird. In der
 * Korrektur die Nummer der korrigierten Runde, gezaehlt wie in der Rundenliste.
 */
internal fun sessionTitle(
    state: SessionState?,
    editRoundId: String?,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (state == null) return "Abend"
    if (editRoundId != null) {
        val number = state.liveRounds.indexOfFirst { it.id == editRoundId } + 1
        return if (number > 0) "Runde $number korrigieren" else "Runde korrigieren"
    }
    val date = formatShortDate(state.session.startedAt, zone)
    return if (state.session.status == SessionStatus.OPEN) {
        "$date · Runde ${state.liveRounds.size + 1}"
    } else {
        "$date · beendet"
    }
}
```

2. Im `Scaffold` den `topBar`-Block ersetzen:

```kotlin
        topBar = {
            SessionTopBar(
                title = sessionTitle(state, editRoundId),
                open = state?.session?.status == SessionStatus.OPEN,
                editing = editRoundId != null,
                empty = empty,
                onBack = { actions.back() },
                onHistory = { actions.toHistory(sessionId) },
                onSettlement = { actions.toSettlement(sessionId) },
                onChangeDealer = { dealerSheet = true },
                onEnd = { confirmEnd = true },
            )
        },
```

3. Die Snackbar dunkel und oben ueber der Eingabe statt ueber den Ergebnisknoepfen: im `Scaffold` die Zeile `snackbarHost = { SnackbarHost(snackbarHostState) },` entfernen und `SessionBody` einen Slot geben. In `SessionBody` Parameter ergaenzen:

```kotlin
    snackbar: @Composable () -> Unit = {},
```

und den scrollenden Bereich in eine `Box` legen (die `weight(1f)` wandert vom Scroll-`Column` auf die `Box`):

```kotlin
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SessionTags.SCROLL_AREA)
                    .verticalScroll(scrollState)
                    .padding(top = RotaskatDimens.itemSpacing, bottom = RotaskatDimens.sectionSpacing),
                verticalArrangement = Arrangement.spacedBy(RotaskatDimens.sectionSpacing),
            ) {
                // ... unveraenderter Inhalt ...
            }
            // Fehlermeldungen oben ueber der Eingabe: unten lagen sie genau
            // ueber "Gewonnen" und "Verloren".
            Box(Modifier.align(Alignment.TopCenter)) { snackbar() }
        }
```

Im Aufruf von `SessionBody` in `SessionScreen`:

```kotlin
            snackbar = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
```

4. Imports ergaenzen: `androidx.compose.material3.Snackbar`, `io.rotaskat.app.ui.common.formatShortDate`, `kotlinx.datetime.TimeZone`; `TopAppBar`/`TextButton` nur entfernen, wenn ungenutzt (`TextButton` bleibt fuer den Dialog).

- [ ] **Step 6: `LastChangeRow.kt` anpassen**

Den `Surface`-Block ersetzen:

```kotlin
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Feste Hoehe in beiden Zustaenden: nach "Zurueckgenommen" fehlt der
        // Knopf, und die Zeile wurde flacher - das Layout darunter sprang.
        modifier = modifier.fillMaxWidth().height(52.dp).testTag(LastChangeTags.ROW),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
        ) {
            // Eine Zeile: die Zeile steht ueber der Eingabe und kostet damit
            // genau den Platz, den der Vier-Tap-Pfad braucht.
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(headline)
                        append(": ")
                    }
                    append(description)
                },
                style = RotaskatTextStyles.compact,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showPoints && half != null) {
                Text(
                    text = formatPoints(half),
                    style = RotaskatTextStyles.scoreMedium,
                    color = when {
                        half > 0 -> colors.gain
                        half < 0 -> colors.loss
                        else -> colors.neutral
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (!change.undone) {
                TextButton(onClick = onUndo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Rückgängig")
                }
            }
        }
    }
}

/** Test-Tag der Zeile. */
internal object LastChangeTags {
    const val ROW = "last-change"
}
```

Imports ergaenzen: `androidx.compose.foundation.layout.Spacer`, `height`, `size`, `width`, `androidx.compose.material3.Icon`, `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.res.painterResource`, `io.rotaskat.app.R`.

- [ ] **Step 7: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (inklusive `SessionLayoutTest` mit "Rückgängig").

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/io/rotaskat/app/ui/common/EvalFormat.kt app/src/main/kotlin/io/rotaskat/app/ui/session app/src/test/kotlin/io/rotaskat/app/ui/common/EvalFormatTest.kt app/src/test/kotlin/io/rotaskat/app/ui/session
git commit -m "Abend mit Titel, Menue und abgesetztem Abend beenden, Undo-Zeile mit fester Hoehe" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Startseite

**Files:**
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/eval/LiveSessionCard.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/OverviewScreen.kt` (Liste, FAB, `SessionCard` -> `PastSessionRow`)
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/eval/LiveSessionCardTest.kt`

**Interfaces:**
- Consumes: `formatShortDate`, `formatTime` (Task 9), `SectionLabel` (Task 3), `RotaskatDimens.cardCorner` (Task 3), `MaterialTheme.accentColors` (Task 1).
- Produces: `LiveSessionCard(state: SessionState, names: Map<Int, String>, onContinue: () -> Unit, modifier: Modifier = Modifier)`, `leaderLine(ranking: List<Pair<String, Long>>): String`.

- [ ] **Step 1: Failing test schreiben**

```kotlin
package io.rotaskat.app.ui.eval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Session
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Auf der alten Startseite stand die Fuehrungszahl (+50) ohne Namen da; wer
 * fuehrt, musste man unten in der Zeile suchen.
 */
@RunWith(RobolectricTestRunner::class)
class LiveSessionCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val names = mapOf(0 to "Anna", 1 to "Ben", 2 to "Johannes", 3 to "Lars")

    private fun state(totals: Map<Int, Long>) = SessionState(
        Session(
            id = "s",
            clubId = TEST_CLUB.id,
            seatCount = totals.size,
            seats = totals.keys.associateWith { "p$it" },
            startedAt = T0,
            scoring = TEST_CLUB.scoring,
        ),
        emptyList(),
        totals,
    )

    @Test
    fun `die Fuehrung steht mit Namen neben der Zahl`() {
        var continued = 0
        compose.setContent {
            RotaskatTheme {
                LiveSessionCard(
                    state = state(mapOf(0 to 96L, 1 to -24L, 2 to 6L, 3 to 0L)),
                    names = names,
                    onContinue = { continued++ },
                )
            }
        }
        compose.onNodeWithText("Anna führt").assertIsDisplayed()
        compose.onNodeWithText("+48").assertIsDisplayed()
        compose.onNodeWithText("Johannes +3 · Lars +0 · Ben -12").assertIsDisplayed()
        compose.onNodeWithText("Weiterspielen").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun `bei Gleichstand an der Spitze steht kein Name`() {
        assertEquals("Gleichstand", leaderLine(listOf("Anna" to 10L, "Ben" to 10L, "Lars" to -20L)))
        assertEquals("Anna führt", leaderLine(listOf("Anna" to 12L, "Ben" to 10L)))
        assertEquals("Noch keine Runde", leaderLine(listOf("Anna" to 0L, "Ben" to 0L, "Lars" to 0L)))
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.eval.LiveSessionCardTest"`
Expected: Compile-Fehler `Unresolved reference: LiveSessionCard`.

- [ ] **Step 3: `LiveSessionCard.kt` anlegen**

```kotlin
package io.rotaskat.app.ui.eval

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.formatTime
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors
import io.rotaskat.app.ui.theme.scoreColors

/**
 * Wer vorne liegt, in Worten. Bei Gleichstand an der Spitze kein Name - ein
 * willkuerlich herausgegriffener Spieler waere eine falsche Aussage.
 */
internal fun leaderLine(ranking: List<Pair<String, Long>>): String {
    val first = ranking.getOrNull(0) ?: return "Noch keine Runde"
    if (ranking.all { it.second == 0L }) return "Noch keine Runde"
    val second = ranking.getOrNull(1)
    return if (second != null && second.second == first.second) "Gleichstand" else "${first.first} führt"
}

/**
 * Der laufende Abend als Karte oben auf der Startseite.
 *
 * Die Fuehrungszahl steht neben dem Namen, nicht allein: frueher stand "+50"
 * ohne Namen rechts, und wer fuehrt, stand klein in der Zeile darunter.
 */
@Composable
internal fun LiveSessionCard(
    state: SessionState,
    names: Map<Int, String>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val score = MaterialTheme.scoreColors
    val accent = MaterialTheme.accentColors
    val ranking = (0 until state.session.seatCount)
        .map { seat -> (names[seat] ?: "Platz ${seat + 1}") to (state.totals[seat] ?: 0L) }
        .sortedByDescending { it.second }
    val leader = ranking.first()

    Surface(
        shape = RoundedCornerShape(RotaskatDimens.cardCorner),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "♣ Abend läuft",
                    style = RotaskatTextStyles.sectionLabel,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Runde ${state.liveRounds.size + 1} · seit ${formatTime(state.session.startedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.labelMuted,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    text = leaderLine(ranking),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatPoints(leader.second),
                    style = RotaskatTextStyles.scoreLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    color = when {
                        leader.second > 0 -> score.gain
                        leader.second < 0 -> score.loss
                        else -> score.neutral
                    },
                )
            }
            Text(
                text = ranking.drop(1).joinToString(" · ") { "${it.first} ${formatPoints(it.second)}" },
                style = RotaskatTextStyles.compact,
                color = colors.onSurfaceVariant,
                maxLines = 2,
            )
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = RotaskatDimens.tapTarget),
            ) {
                Text("Weiterspielen", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
```

- [ ] **Step 4: `OverviewScreen.kt` umbauen**

1. Den `Scaffold` um einen FAB ergaenzen (nach `topBar`):

```kotlin
        floatingActionButton = {
            val running = states.any { it.session.status == SessionStatus.OPEN }
            // Laeuft ein Abend, ist "Weiterspielen" die Hauptaktion und der neue
            // Abend nur umrandet. Laeuft keiner, ist er die Hauptaktion.
            ExtendedFloatingActionButton(
                onClick = { actions.toNewSession() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Neuer Abend") },
                containerColor = if (running) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                contentColor = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                modifier = if (running) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
        },
```

2. Den Inhalt des `LazyColumn` ersetzen:

```kotlin
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Unten Platz fuer den FAB, damit der letzte Abend nicht darunter liegt.
            contentPadding = PaddingValues(
                start = RotaskatDimens.screenPadding,
                end = RotaskatDimens.screenPadding,
                top = RotaskatDimens.itemSpacing,
                bottom = 96.dp,
            ),
        ) {
            if (open != null) {
                item(key = open.session.id) {
                    LiveSessionCard(
                        state = open,
                        names = seatNames(open.session, roster),
                        onContinue = { actions.toSession(open.session.id) },
                    )
                }
            }

            item(key = "closed-heading") {
                SectionLabel(
                    text = if (closed.isEmpty()) "Noch keine abgeschlossenen Abende" else "Frühere Abende",
                    modifier = Modifier.padding(top = RotaskatDimens.sectionSpacing),
                )
            }

            items(closed, key = { it.session.id }) { state ->
                PastSessionRow(
                    state = state,
                    roster = roster,
                    onClick = { actions.toSettlement(state.session.id) },
                )
            }

            if (pending > 0) {
                item(key = "pending") {
                    Notice(
                        "$pending ${if (pending == 1) "Runde wartet" else "Runden warten"} auf den " +
                            "Server. Gespielt und gerechnet wird trotzdem - der Sync holt das nach.",
                        modifier = Modifier.padding(top = RotaskatDimens.itemSpacing),
                    )
                }
            }
        }
```

3. `SectionHeading` loeschen und `SessionCard` (samt KDoc) ersetzen durch:

```kotlin
/**
 * Ein frueherer Abend: Datum, Umfang, Sieger.
 *
 * Eine ruhige Zeile statt einer Karte - der Endstand steht schon hier, nicht
 * erst im Detail: wer die Historie durchblaettert, sucht meistens genau ihn.
 */
@Composable
private fun PastSessionRow(
    state: SessionState,
    roster: List<Player>,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.scoreColors
    val names = seatNames(state.session, roster)
    val winner = (0 until state.session.seatCount)
        .map { seat -> (names[seat] ?: "Platz ${seat + 1}") to (state.totals[seat] ?: 0L) }
        .maxByOrNull { it.second }
    val rounds = state.liveRounds.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = RotaskatDimens.tapTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatShortDate(state.session.startedAt),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$rounds ${if (rounds == 1) "Runde" else "Runden"} · " +
                        if (state.session.seatCount == 4) "zu viert" else "zu dritt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.accentColors.labelMuted,
                )
            }
            if (winner != null) {
                Text(
                    text = winner.first + " ",
                    style = RotaskatTextStyles.compact,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatPoints(winner.second),
                    style = RotaskatTextStyles.scoreMedium,
                    color = when {
                        winner.second > 0 -> colors.gain
                        winner.second < 0 -> colors.loss
                        else -> colors.neutral
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
}
```

4. Imports ergaenzen: `androidx.compose.foundation.border`, `androidx.compose.foundation.clickable`, `androidx.compose.material.icons.filled.Add`, `androidx.compose.material3.ExtendedFloatingActionButton`, `androidx.compose.material3.HorizontalDivider`, `io.rotaskat.app.ui.common.SectionLabel`, `io.rotaskat.app.ui.common.formatShortDate`, `io.rotaskat.app.ui.theme.accentColors`. Nicht mehr benutzte entfernen (`Button`, `Surface`, `formatDate`, `Arrangement`, sofern gemeldet).

- [ ] **Step 5: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/io/rotaskat/app/ui/eval/LiveSessionCard.kt app/src/main/kotlin/io/rotaskat/app/ui/eval/OverviewScreen.kt app/src/test/kotlin/io/rotaskat/app/ui/eval/LiveSessionCardTest.kt
git commit -m "Startseite mit Karte des laufenden Abends, Liste frueherer Abende und Neuer-Abend-Knopf" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Pruefung auf dem Geraet und Notizen

**Files:**
- Modify: `.issues/DESIGN-NOTIZEN.md` (Abschnitt "Stand nach Teil 1" anhaengen)

- [ ] **Step 1: Vollstaendiger Build und Tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, alle Tests gruen.

- [ ] **Step 2: Barrierefreiheit pruefen**

Skill `design:accessibility-review` auf den Abend-Bildschirm und die Startseite anwenden (Kontraste aus `PaletteContrastTest`, Tap-Ziele, TalkBack-Beschriftungen). Befunde, die Teil 1 betreffen, sofort beheben (mit Test), alle anderen in `.issues/DESIGN-NOTIZEN.md` eintragen.

- [ ] **Step 3: App-Daten sichern und installieren** (Vorgehen aus `.issues/HANDOFF.md`, Git Bash)

```bash
adb mdns services            # Port des Pixel 10 Pro ablesen
adb connect 192.168.178.44:<port>
MSYS_NO_PATHCONV=1 adb exec-out run-as io.rotaskat.app.debug tar cf - databases files > ~/rotaskat-backup/appdata-$(date +%F-%H%M).tar
./gradlew :app:installDebug
```

- [ ] **Step 4: Auf dem Geraet pruefen (mobile-mcp)** und je Punkt einen Screenshot sichern:
  1. Vierertisch, Vier-Tap-Pfad (Alleinspieler -> Kreuz -> mit 2 -> Gewonnen) ohne Scrollen, Undo-Zeile sichtbar.
  2. Namen bis 10 Zeichen im Stand und in der Undo-Zeile ungekuerzt.
  3. Tap auf die Geber-Spalte oeffnet "Wer gibt?", ein Tap setzt den Geber.
  4. Menue: "Abend beenden …" oeffnet den Dialog; "Weiterspielen" schliesst ihn.
  5. Zurueck vom Abend und sofort aufs Zahnrad tippen: Einstellungen oeffnen sich, kein Dialog des Abends.
  6. Untere Leiste: Abende -> Rangliste -> Statistik -> Zurueck landet auf Abende.
  7. Ouvert: Herleitung "Kreuz mit 2 · Ouvert = 12 × 9" vollstaendig.
  8. TalkBack: Kachel "Kreuz" wird als "ausgewaehlt" vorgelesen.

- [ ] **Step 5: App-Daten zuruecksichern** (falls beim Test Runden angelegt wurden; Vorgehen aus HANDOFF.md)

- [ ] **Step 6: Notizen nachziehen**

In `.issues/DESIGN-NOTIZEN.md` einen Abschnitt `## Stand nach Design-Runde Teil 1 (<Datum>)` anhaengen: welche Befunde erledigt sind (Farbsymbole, Auswahlzustand, Ergebnisbuttons, Snackbar, Textbuttons, Zurueck-Pfeil, Abend beenden, Uebergaenge, abgeschnittene Namen, Herleitung, Geberauswahl, Undo-Zeile, Korrekturmodus-Geber, TalkBack `selected`) und was fuer Teil 2 offen ist (Diagramm, erklaerende Absaetze, Uebersichtskarten der Auswertung, Querformat, Tablet, Befunde aus Step 2/4).

- [ ] **Step 7: Commit**

```bash
git add .issues/DESIGN-NOTIZEN.md
git commit -m "Design-Notizen um den Stand nach Teil 1 ergaenzen" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
