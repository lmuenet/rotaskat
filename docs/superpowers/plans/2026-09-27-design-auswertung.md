# Design "Kartentisch" Teil 2 - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auswertung (Punkteverlauf, Rangliste, Statistik, Abrechnung), Einstellungen, Einstieg und "Neuer Abend" im Kartentisch-Look; Erklaerungen hinter ⓘ; Reste aus Teil 1 erledigt.

**Architecture:** Erst Farben und gemeinsame Bausteine (`SectionHeader`/`InfoSheet`, `StatTile`, `ChipRow`, Semantik der Kacheln), dann je ein Task pro Bildschirmgruppe, zuletzt die Reste. Keine Aenderung an Spiellogik, Datenschicht oder `:shared`.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (BOM 2025.04, Material 3), Robolectric-UI-Tests im normalen Unit-Test-Lauf.

**Spec:** `docs/superpowers/specs/2026-09-27-design-auswertung-design.md` (baut auf `docs/superpowers/specs/2026-09-27-design-kartentisch-design.md` auf)

## Global Constraints

- Nur dunkles Theme; Gruen/Rot nur fuer Gewinn/Verlust; Kupfer nur an Farbsymbolen.
- Vorzeichen immer (`formatPoints`, `formatCents`), Farbe nur Zweitkanal.
- Tabellenziffern in jeder Textrolle; kein Nutzertext unter 14sp.
- Tap-Ziele: `tapTarget = 56.dp` Minimum.
- Keine Textzeichen ♦♥♠♣⚠ in der Oberflaeche - Android zeichnet sie als Emoji. Farbsymbole ueber `Suit.icon` (Drawables), Warnung ueber `Icons.Filled.Warning`.
- Icons nur aus `material-icons-core` oder `res/drawable/ic_*.xml`; keine neue Abhaengigkeit.
- Nutzertexte mit echten Umlauten; Kommentare, Bezeichner, Doku, Commit-Messages deutsch in ASCII (ae/oe/ue), Commit-Message ein Satz im Praesens.
- `SessionLayoutTest` bleibt gruen.
- Tests: `./gradlew :app:testDebugUnitTest` (Git Bash, Repo-Root); einzeln mit `--tests "<Klasse>"`.

## Review Focus

- **Zwei Linien enden auf demselben Stand** (Lars -18, Niko -18): Beschriftungen duerfen sich nicht ueberdecken -> `LabelPositionsTest` (Task 3).
- **Laengster Name am Linienende** ("Maximilian -120") darf den Plot nicht auf null schrumpfen -> Obergrenze 45 % der Breite, Ellipse (Task 3, Code).
- **Spieler ohne Alleinspiel** in der Statistik: Quote "–" statt "0 %" -> `StatsTilesTest` (Task 5).
- **Abend ohne Zahlungen** (alles ausgeglichen) -> Zeile "Alles ausgeglichen." (Task 6, Code).
- **Kopfzeile waehrend der Abend laedt**: keine Aktionen -> `SessionTopBarTest` (Task 8).

---

### Task 1: Farben - neutral, Linien, forValue

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/theme/Color.kt` (`RotaskatScoreColors`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/theme/SeriesStyles.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/PointsChart.kt` (nur `pathEffect`-Zeilen entfernen)
- Modify (Aufrufer von gain/loss/neutral-`when`): `common/Standings.kt:~131`, `eval/LiveSessionCard.kt:~168`, `eval/OverviewScreen.kt:~227`, `eval/SettlementScreen.kt:~318`, `session/LastChangeRow.kt:~110`, `session/Scoreboard.kt:~111`, `session/SessionScreen.kt:~495`
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/theme/PaletteContrastTest.kt`, neu `app/src/test/kotlin/io/rotaskat/app/ui/theme/ScoreColorsTest.kt`

**Interfaces:**
- Produces: `RotaskatScoreColors.forValue(value: Long): Color`; `SeriesStyle(color: Color)` ohne `dash`; `RotaskatScoreColors` ohne `onGain`, `onLoss`, `sittingOut`.

- [ ] **Step 1: Failing tests**

`ScoreColorsTest.kt`:

```kotlin
package io.rotaskat.app.ui.theme

import org.junit.Test
import kotlin.test.assertEquals

class ScoreColorsTest {

    private val colors = RotaskatScoreColorsDark

    @Test
    fun `Vorzeichen bestimmt die Farbe`() {
        assertEquals(colors.gain, colors.forValue(1))
        assertEquals(colors.loss, colors.forValue(-1))
        assertEquals(colors.neutral, colors.forValue(0))
    }
}
```

In `PaletteContrastTest` im 4,5-Block ergaenzen:

```kotlin
            "Null-Wert auf Grund" to (score.neutral to scheme.background),
```

und im 3,0-Block:

```kotlin
            "Linie Sitz 1" to (RotaskatSeriesStyles[0].color to scheme.background),
            "Linie Sitz 2" to (RotaskatSeriesStyles[1].color to scheme.background),
            "Linie Sitz 3" to (RotaskatSeriesStyles[2].color to scheme.background),
            "Linie Sitz 4" to (RotaskatSeriesStyles[3].color to scheme.background),
```

- [ ] **Step 2: RED** - `./gradlew :app:testDebugUnitTest --tests "io.rotaskat.app.ui.theme.*"` -> Compile-Fehler `forValue`.

- [ ] **Step 3: `Color.kt`** - in `RotaskatScoreColors` die Felder `onGain`, `onLoss`, `sittingOut` samt KDoc entfernen (keine Aufrufer), `neutral` in `RotaskatScoreColorsDark` auf `Color(0xFFB5AC9C)` setzen und im Datenklassen-Rumpf ergaenzen:

```kotlin
) {
    /** Die Farbe einer Punktzahl oder eines Betrags nach seinem Vorzeichen. */
    fun forValue(value: Long): Color = when {
        value > 0 -> gain
        value < 0 -> loss
        else -> neutral
    }
}
```

Den KDoc-Satz zu `neutral` ergaenzen: "Warmgrau, passend zum Anthrazit - nicht violett."

- [ ] **Step 4: `SeriesStyles.kt`**

```kotlin
/**
 * Wie eine Linie im Punkteverlauf gezeichnet wird.
 *
 * Nur die Farbe. Der zweite Kanal ist nicht mehr ein Strichmuster, sondern der
 * Name am Linienende (siehe `PointsChart`): eine Beschriftung ist bei
 * Rot-Gruen-Schwaeche, im Kneipenlicht und in Graustufen eindeutig, ein
 * gepunktetes Hellviolett war es im Geraetetest nicht.
 */
@Immutable
data class SeriesStyle(
    val color: Color,
)
```

und die Liste:

```kotlin
val RotaskatSeriesStyles: List<SeriesStyle> = listOf(
    SeriesStyle(Color(0xFFF2C46B)), // Gold
    SeriesStyle(Color(0xFF7FC4F0)), // Himmelblau
    SeriesStyle(Color(0xFFC8A8FF)), // Flieder
    SeriesStyle(Color(0xFFEDE6D8)), // Elfenbein
)
```

Den KDoc der Liste anpassen (keine Strichmuster mehr; jede Farbe >= 3:1 auf dem Grund, per `PaletteContrastTest`).

- [ ] **Step 5: `PointsChart.kt`** - die beiden `pathEffect = line.style.dash?.let { ... }`-Bloecke (in `drawPath` und in `ChartLegend`) ersatzlos entfernen; ungenutzte Imports (`PathEffect`) entfernen. Der Rest des Diagramms folgt in Task 3.

- [ ] **Step 6: Aufrufer** - in jeder der sieben genannten Dateien den Ausdruck `when { x > 0 -> c.gain; x < 0 -> c.loss; else -> c.neutral }` durch `c.forValue(x)` ersetzen (bei `Int` mit `.toLong()`). `grep -rn "else -> .*\.neutral" app/src/main/kotlin` muss danach leer sein.

- [ ] **Step 7: GREEN + volle Suite**, dann Commit: `Warmgraue Null, kraeftige Linienfarben und eine Farbwahl nach Vorzeichen`.

---

### Task 2: Bausteine - SectionHeader/InfoSheet, StatTile, ChipRow, Kachel-Semantik

**Files:**
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/common/SectionHeader.kt`
- Create: `app/src/main/kotlin/io/rotaskat/app/ui/common/ChipRow.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/EvalScaffold.kt` (`EvalSection`, `StatCard` -> `StatTile`, `PeriodSelector`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/common/Controls.kt` (`OptionTile`/`TileFrame`: `role`, ohne `selectedColor`; `OptionGrid`: `selectableGroup`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/round/RoundEntryPanel.kt` ("mehr"-Kachel)
- Modify (nur `note =` -> `subtitle =`): `eval/LeaderboardScreen.kt`, `eval/ProgressScreen.kt`, `eval/SettlementScreen.kt`, `settings/SettingsScreen.kt`
- Modify: `eval/StatsScreen.kt` (`StatCard` -> `StatTile`, gleiche Argumente)
- Test: neu `app/src/test/kotlin/io/rotaskat/app/ui/common/SectionHeaderTest.kt`, `.../common/ChipRowTest.kt`, `.../eval/StatTileTest.kt`; Erweiterung `.../common/OptionTileTest.kt`

**Interfaces:**
- Produces: `SectionHeader(title: String, modifier: Modifier = Modifier, info: String? = null)`, `InfoSheet(title: String, text: String, onDismiss: () -> Unit)`; `EvalSection(title, modifier, info: String? = null, subtitle: String? = null, content)`; `StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null, warning: String? = null, valueColor: Color = Color.Unspecified, @DrawableRes icon: Int? = null, iconTint: Color = Color.Unspecified)`; `ChipRow(options: List<T>, selected: T?, onSelect: (T) -> Unit, label: (T) -> String, modifier: Modifier = Modifier)`; `OptionTile(..., role: Role = Role.RadioButton)`.

- [ ] **Step 1: Failing tests**

`SectionHeaderTest.kt`:

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

/** Erklaerungen stehen nicht mehr dauerhaft ueber der Tabelle, sondern hinter dem ⓘ. */
@RunWith(RobolectricTestRunner::class)
class SectionHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Info-Symbol oeffnet die Erklaerung`() {
        compose.setContent { RotaskatTheme { SectionHeader(title = "Punkte", info = "Die Quote zählt nur Alleinspiele.") } }
        compose.onNodeWithText("Die Quote zählt nur Alleinspiele.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Erklärung zu Punkte").performClick()
        compose.onNodeWithText("Die Quote zählt nur Alleinspiele.").assertIsDisplayed()
    }

    @Test
    fun `ohne Erklaerung kein Info-Symbol`() {
        compose.setContent { RotaskatTheme { SectionHeader(title = "Stand") } }
        compose.onNodeWithContentDescription("Erklärung zu Stand").assertDoesNotExist()
    }
}
```

(Findet Robolectric den Sheet-Inhalt nicht, weil `ModalBottomSheet` in einem eigenen Fenster liegt, den ersten Test auf den Inhalt `InfoSheetContent(title, text)` umstellen - eine private->internal gemachte Composable, die `InfoSheet` rendert - und zusaetzlich pruefen, dass der Klick einen Zustand setzt. Im Bericht vermerken.)

`ChipRowTest.kt`:

```kotlin
package io.rotaskat.app.ui.common

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

@RunWith(RobolectricTestRunner::class)
class ChipRowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `gewaehlter Chip ist ausgewaehlt, Tap meldet die Auswahl`() {
        var picked: String? = null
        compose.setContent {
            RotaskatTheme {
                ChipRow(options = listOf("Alle Jahre", "2026"), selected = "2026", onSelect = { picked = it }, label = { it })
            }
        }
        compose.onNodeWithText("2026").assertIsSelected()
        compose.onNodeWithText("Alle Jahre").assertIsNotSelected().performClick()
        assertEquals("Alle Jahre", picked)
    }
}
```

`StatTileTest.kt`:

```kotlin
package io.rotaskat.app.ui.eval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatTileTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Kachel zeigt Label, Wert, Detail und Warnung`() {
        compose.setContent {
            RotaskatTheme {
                StatTile(label = "Gewinnquote allein", value = "100 %", detail = "1 von 1", warning = "Unter 5 Alleinspielen wenig aussagekräftig")
            }
        }
        compose.onNodeWithText("Gewinnquote allein").assertIsDisplayed()
        compose.onNodeWithText("100 %").assertIsDisplayed()
        compose.onNodeWithText("1 von 1").assertIsDisplayed()
        compose.onNodeWithText("Unter 5 Alleinspielen wenig aussagekräftig").assertIsDisplayed()
    }
}
```

In `OptionTileTest` ergaenzen:

```kotlin
    @Test
    fun `Kachel mit Rolle Button meldet keine Auswahl`() {
        compose.setContent {
            RotaskatTheme { OptionTile(label = "mehr", selected = false, onClick = {}, role = Role.Button) }
        }
        compose.onNodeWithText("mehr")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }
```

- [ ] **Step 2: RED** - Compile-Fehler fuer `SectionHeader`, `ChipRow`, `StatTile`, `role`.

- [ ] **Step 3: `SectionHeader.kt`**

```kotlin
package io.rotaskat.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors

/**
 * Ueberschrift eines Abschnitts, optional mit Erklaerung hinter einem ⓘ.
 *
 * Die Erklaerungen standen frueher als Absatz ueber jeder Tabelle und lasen
 * sich wie Entwickler-Kommentare. Wer die Zahl kennt, braucht sie nicht; wer
 * sie nicht kennt, findet sie mit einem Tap.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, info: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.accentColors.labelMuted
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = RotaskatTextStyles.sectionLabel,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        if (info != null) {
            IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Erklärung zu $title",
                    tint = muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    if (open && info != null) {
        InfoSheet(title = title, text = info, onDismiss = { open = false })
    }
}

/** Die Erklaerung zu einem Abschnitt, voll aufgeklappt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(title: String, text: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 4: `ChipRow.kt`**

```kotlin
package io.rotaskat.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatDimens

/**
 * Eine Reihe waehlbarer Kacheln, die bei Bedarf zur Seite scrollt.
 *
 * Fuer Zeitraeume und Spieler: wenige Optionen, eine davon aktiv. Im Raster
 * brauchten vier Spieler zwei Reihen und schoben die Zahlen nach unten.
 */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
    ) {
        for (option in options) {
            OptionTile(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                height = RotaskatDimens.tapTarget,
                modifier = Modifier.widthIn(min = 88.dp),
            )
        }
    }
}
```

- [ ] **Step 5: `Controls.kt`**
  1. `OptionTile`: Parameter `selectedColor` entfernen, `role: Role = Role.RadioButton` anhaengen und an `TileFrame` durchreichen. `TileFrame` ebenso ohne `selectedColor` (`fill`: `selected -> colors.primaryContainer`, `contentColor`: `selected -> colors.onPrimaryContainer`), mit `role: Role = Role.RadioButton`.
  2. In `TileFrame` die Interaktion nach Rolle waehlen:

```kotlin
                .then(
                    // Eine Auswahlkachel meldet "ausgewaehlt"; eine Kachel, die nur
                    // etwas aufklappt ("mehr"), ist ein Knopf und meldet nichts.
                    if (role == Role.RadioButton) {
                        Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
                    } else {
                        Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
                    },
                )
```

     (ersetzt den bisherigen `.selectable(...)`-Aufruf; Import `androidx.compose.foundation.clickable`).
  3. `OptionGrid`: `Column(modifier.fillMaxWidth().selectableGroup(), ...)` (Import `androidx.compose.foundation.selection.selectableGroup`).

- [ ] **Step 6: `RoundEntryPanel.kt` `MatadorPicker`** - die "mehr"/"weniger"-Kachel bekommt `role = if (draft.matadors > MatadorRow.last) Role.RadioButton else Role.Button` (Import `androidx.compose.ui.semantics.Role`).

- [ ] **Step 7: `EvalScaffold.kt`**
  1. `EvalSection` ersetzen:

```kotlin
/**
 * Ueberschrift plus Inhalt. [subtitle] ist eine kurze, immer sichtbare
 * Zeile (etwa "12 Runden"), [info] die Erklaerung hinter dem ⓘ.
 */
@Composable
fun EvalSection(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        Column {
            SectionHeader(title = title, info = info)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}
```

  2. `StatCard` ersetzen durch `StatTile`:

```kotlin
/**
 * Eine Kennzahl als Kachel: Label, grosser Wert, Grundgesamtheit, optional
 * eine Warnung.
 *
 * [detail] ist kein Beiwerk, sondern die Bedingung dafuer, dass [value]
 * etwas aussagt: eine Quote ohne die Anzahl dahinter ist eine Behauptung.
 * Die Warnung traegt ein Icon statt des Zeichens ⚠, das Android als Emoji
 * zeichnet.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    warning: String? = null,
    valueColor: Color = Color.Unspecified,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Color.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(RotaskatDimens.cardCorner),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.defaultMinSize(minHeight = 112.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = RotaskatTextStyles.sectionLabel,
                color = MaterialTheme.accentColors.labelMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = value,
                    style = RotaskatTextStyles.scoreLarge,
                    color = if (valueColor == Color.Unspecified) colors.onSurface else valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 2)
            }
            if (warning != null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(top = 2.dp).size(14.dp),
                    )
                    Text(warning, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
        }
    }
}
```

  3. `PeriodSelector` durch `ChipRow` umsetzen (KDoc anpassen: Reihe statt Raster):

```kotlin
@Composable
fun PeriodSelector(
    seasons: List<Int>,
    selected: Period,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = buildList {
        add(Period.AllTime)
        seasons.forEach { add(Period.Season(it)) }
    }
    ChipRow(options = options, selected = selected, onSelect = onSelect, label = { it.label }, modifier = modifier)
}
```

- [ ] **Step 8: Aufrufer** - `EvalSection(..., note = X)` wird ueberall `EvalSection(..., subtitle = X)` (Verhalten unveraendert; die Texte aendern die Bildschirm-Tasks). `StatCard(` wird `StatTile(` mit denselben benannten Argumenten.

- [ ] **Step 9: GREEN + volle Suite** (`SessionLayoutTest` gruen), Commit: `Abschnittsueberschrift mit Erklaerung, Kennzahl-Kachel, Chip-Reihe und Kachel-Semantik`.

---

### Task 3: Punkteverlauf

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/PointsChart.kt` (ganze Datei ausser `ChartScale`/`chartScale`/`NICE_STEPS`)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/ProgressScreen.kt`
- Test: neu `app/src/test/kotlin/io/rotaskat/app/ui/eval/LabelPositionsTest.kt`, neu `.../eval/PointsChartSemanticsTest.kt`

**Interfaces:**
- Consumes: `SeriesStyle(color)` (Task 1), `EvalSection(title, info, subtitle)` (Task 2), `R.drawable.ic_euro`.
- Produces: `labelPositions(desired: List<Float>, minGap: Float, top: Float, bottom: Float): List<Float>`, `chartSummary(series: List<ChartSeries>): String`.

- [ ] **Step 1: Failing tests**

`LabelPositionsTest.kt`:

```kotlin
package io.rotaskat.app.ui.eval

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabelPositionsTest {

    @Test
    fun `weit auseinander bleibt alles, wo es ist`() {
        assertEquals(listOf(10f, 100f, 200f), labelPositions(listOf(10f, 100f, 200f), minGap = 20f, top = 0f, bottom = 300f))
    }

    @Test
    fun `gleicher Stand wird auseinandergeschoben, Reihenfolge bleibt`() {
        val placed = labelPositions(listOf(150f, 150f), minGap = 20f, top = 0f, bottom = 300f)
        assertTrue(placed[1] - placed[0] >= 20f, "$placed")
    }

    @Test
    fun `am unteren Rand wird nach oben ausgewichen`() {
        val placed = labelPositions(listOf(300f, 300f, 300f), minGap = 20f, top = 0f, bottom = 300f)
        assertTrue(placed.all { it in 0f..300f }, "$placed")
        val sorted = placed.sorted()
        assertTrue(sorted[1] - sorted[0] >= 20f && sorted[2] - sorted[1] >= 20f, "$placed")
    }

    @Test
    fun `die Reihenfolge der Wunschpositionen bleibt erhalten`() {
        val placed = labelPositions(listOf(50f, 40f, 45f), minGap = 20f, top = 0f, bottom = 300f)
        // Index 1 (40) ist oben, dann Index 2 (45), dann Index 0 (50).
        assertTrue(placed[1] < placed[2] && placed[2] < placed[0], "$placed")
    }
}
```

`PointsChartSemanticsTest.kt`:

```kotlin
package io.rotaskat.app.ui.eval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.app.ui.theme.seriesStyleFor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PointsChartSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TalkBack liest den Stand statt eines stummen Bildes`() {
        val series = listOf(
            ChartSeries("Lars", seriesStyleFor(0), listOf(0L, -36L)),
            ChartSeries("Johannes", seriesStyleFor(1), listOf(0L, 72L)),
        )
        compose.setContent { RotaskatTheme { PointsChart(series = series) } }
        compose.onNodeWithContentDescription("Punkteverlauf über 1 Runde: Johannes +36, Lars -18").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: RED** - Compile-Fehler `labelPositions`.

- [ ] **Step 3: `PointsChart.kt`** - `ChartSeries` bleibt, `ChartLegend` wird geloescht, `PointsChart` wird ersetzt, zwei Funktionen kommen dazu:

```kotlin
/**
 * Der Punkteverlauf eines Abends.
 *
 * Selbst gezeichnet, ohne Diagrammbibliothek. Jede Linie traegt ihren Namen
 * und den Stand am Ende - eine Legende darunter zwang dazu, zwischen Farbe und
 * Name hin und her zu sehen, und eine Farbe allein ist bei Rot-Gruen-Schwaeche
 * keine Zuordnung.
 *
 * Keine Farbe steht in dieser Datei: Raster und Achsen kommen aus dem
 * Farbschema, die Linien aus [io.rotaskat.app.ui.theme.RotaskatSeriesStyles].
 */
@Composable
fun PointsChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
) {
    val values = series.flatMap { it.cumulative }
    val roundCount = (series.maxOfOrNull { it.cumulative.size } ?: 0) - 1
    if (series.isEmpty() || roundCount < 1) {
        Text(
            text = "Noch keine Runde gespielt.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val endStyle = MaterialTheme.typography.labelLarge
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.outline
    val scale = chartScale(values)
    val summary = chartSummary(series)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = summary },
    ) {
        val axisLabels = scale.lines.map { formatPoints(it) }
        val axisSizes = axisLabels.map { measurer.measure(AnnotatedString(it), axisStyle).size }
        val axisLabelHeight = axisSizes.maxOf { it.height }.toFloat()
        val axisWidth = axisSizes.maxOf { it.width }.toFloat()
        val axisTitle = measurer.measure(AnnotatedString("Runde"), axisStyle)

        // Beschriftungen am Linienende. Hoechstens 45 % der Breite, sonst
        // bliebe fuer das Diagramm selbst nichts - lange Namen werden gekuerzt.
        val maxEndWidth = (size.width * 0.45f).toInt()
        val endLayouts = series.map { line ->
            measurer.measure(
                text = AnnotatedString("${line.label} ${formatPoints(line.cumulative.last())}"),
                style = endStyle.copy(color = line.style.color),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxEndWidth),
            )
        }
        val endWidth = endLayouts.maxOf { it.size.width }.toFloat()
        val endHeight = endLayouts.maxOf { it.size.height }.toFloat()

        val leftPadding = axisWidth + 8.dp.toPx()
        val rightPadding = endWidth + 14.dp.toPx()
        val topPadding = maxOf(axisLabelHeight, endHeight) / 2f
        val bottomPadding = axisLabelHeight + 6.dp.toPx() + axisTitle.size.height + 2.dp.toPx()

        val plotWidth = size.width - leftPadding - rightPadding
        val plotHeight = size.height - topPadding - bottomPadding
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun x(index: Int): Float = leftPadding + plotWidth * index / roundCount.toFloat()
        fun y(halfPoints: Long): Float {
            val span = (scale.max - scale.min).toFloat()
            return topPadding + plotHeight * (scale.max - halfPoints) / span
        }

        for ((index, line) in scale.lines.withIndex()) {
            val yPosition = y(line)
            drawLine(
                color = if (line == 0L) zeroColor else gridColor,
                start = Offset(leftPadding, yPosition),
                end = Offset(leftPadding + plotWidth, yPosition),
                strokeWidth = if (line == 0L) 1.5.dp.toPx() else 1.dp.toPx(),
            )
            val measured = measurer.measure(AnnotatedString(axisLabels[index]), axisStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(leftPadding - 6.dp.toPx() - measured.size.width, yPosition - measured.size.height / 2f),
            )
        }

        // Nicht jede Runde beschriften: bei dreissig Runden stehen die Zahlen
        // sonst uebereinander.
        val stepWidth = measurer.measure(AnnotatedString("00"), axisStyle).size.width * 2.2f
        val labelStep = maxOf(1, ceil(roundCount * stepWidth / plotWidth).toInt())
        var round = 0
        while (round <= roundCount) {
            val measured = measurer.measure(AnnotatedString(round.toString()), axisStyle)
            drawLine(
                color = gridColor,
                start = Offset(x(round), topPadding),
                end = Offset(x(round), topPadding + plotHeight),
                strokeWidth = 1.dp.toPx(),
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (x(round) - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width),
                    y = topPadding + plotHeight + 6.dp.toPx(),
                ),
            )
            round += labelStep
        }
        drawText(
            textLayoutResult = axisTitle,
            topLeft = Offset(
                x = leftPadding + plotWidth - axisTitle.size.width,
                y = topPadding + plotHeight + 6.dp.toPx() + axisLabelHeight + 2.dp.toPx(),
            ),
        )

        for (line in series) {
            val path = Path()
            line.cumulative.forEachIndexed { index, value ->
                val point = Offset(x(index), y(value))
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = line.style.color,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val last = line.cumulative.lastIndex
            drawCircle(color = line.style.color, radius = 4.5.dp.toPx(), center = Offset(x(last), y(line.cumulative[last])))
        }

        val desired = series.map { y(it.cumulative.last()) }
        val placed = labelPositions(desired, minGap = endHeight, top = topPadding, bottom = topPadding + plotHeight)
        val labelX = leftPadding + plotWidth + 10.dp.toPx()
        series.forEachIndexed { index, line ->
            val endX = x(line.cumulative.lastIndex)
            if (kotlin.math.abs(placed[index] - desired[index]) > 1f) {
                drawLine(
                    color = line.style.color.copy(alpha = 0.5f),
                    start = Offset(endX + 4.5.dp.toPx(), desired[index]),
                    end = Offset(labelX - 2.dp.toPx(), placed[index]),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val layout = endLayouts[index]
            drawText(textLayoutResult = layout, topLeft = Offset(labelX, placed[index] - layout.size.height / 2f))
        }
    }
}

/** Was TalkBack statt des Bildes vorliest: der Stand, nach Punkten sortiert. */
internal fun chartSummary(series: List<ChartSeries>): String {
    val rounds = (series.maxOfOrNull { it.cumulative.size } ?: 1) - 1
    val standings = series
        .sortedByDescending { it.cumulative.last() }
        .joinToString(", ") { "${it.label} ${formatPoints(it.cumulative.last())}" }
    return "Punkteverlauf über ${counted(rounds, "Runde", "Runden")}: $standings"
}

/**
 * Verteilt Beschriftungen senkrecht, sodass keine zwei naeher als [minGap]
 * beieinanderliegen und alle zwischen [top] und [bottom] bleiben.
 *
 * Die Reihenfolge der Wunschpositionen bleibt erhalten - die oberste Linie hat
 * auch die oberste Beschriftung. Erst wird von oben nach unten Platz gemacht,
 * dann vom unteren Rand her zurueckgeschoben. Liegen die Wunschpositionen
 * weit genug auseinander, bewegt sich nichts.
 */
internal fun labelPositions(desired: List<Float>, minGap: Float, top: Float, bottom: Float): List<Float> {
    if (desired.isEmpty()) return emptyList()
    val order = desired.indices.sortedBy { desired[it] }
    val placed = FloatArray(desired.size)
    var previous = Float.NEGATIVE_INFINITY
    for (index in order) {
        val y = maxOf(desired[index].coerceIn(top, bottom), previous + minGap)
        placed[index] = y
        previous = y
    }
    var next = Float.POSITIVE_INFINITY
    for (index in order.reversed()) {
        val y = minOf(placed[index], next - minGap, bottom)
        placed[index] = y
        next = y
    }
    return placed.toList()
}
```

Imports ergaenzen/bereinigen: `androidx.compose.ui.graphics.StrokeCap`, `androidx.compose.ui.graphics.StrokeJoin`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.text.style.TextOverflow`, `androidx.compose.ui.unit.Constraints`, `io.rotaskat.app.ui.common.counted`; ungenutzte (`Column`, `Row`, `size`, `padding`, `PathEffect`, `Arrangement`, `Alignment`) entfernen.

(Reicht der Platz insgesamt nicht - mehr Beschriftungen als `(bottom - top) / minGap` -, darf die oberste ueber `top` hinaus geschoben werden; bei vier Spielern und 260 dp tritt das nicht auf.)

- [ ] **Step 4: `ProgressScreen.kt`**
  - Kopfzeilen-Aktion: `TextButton(... Text("Abrechnung"))` ersetzen durch

```kotlin
                IconButton(onClick = { actions.toSettlement(sessionId) }) {
                    Icon(painterResource(R.drawable.ic_euro), contentDescription = "Abrechnung")
                }
```

  - Abschnitt "Verlauf": `EvalSection(title = "Verlauf") { PointsChart(series = series) }` - ohne Erklaertext, ohne `ChartLegend`.
  - Abschnitt "Stand": `EvalSection(title = "Stand", subtitle = counted(current.liveRounds.size, "Runde", "Runden"))`.
  - Imports bereinigen (`TextButton`, `ChartLegend`).

- [ ] **Step 5: GREEN + volle Suite** (`PointsChartTest` bleibt gruen), Commit: `Punkteverlauf mit kraeftigen Linien und Namen am Linienende statt Legende`.

---

### Task 4: Rangliste und Punktetabelle

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/LeaderboardScreen.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/common/Standings.kt`
- Test: neu `app/src/test/kotlin/io/rotaskat/app/ui/eval/LeaderboardRowTest.kt`

**Interfaces:**
- Consumes: `EvalSection(info)`, `PeriodSelector` (ChipRow), `RotaskatScoreColors.forValue`.
- Produces: `internal fun PlayerStats.toRow(): StandingRow` (bisher private).

- [ ] **Step 1: Failing test** - `LeaderboardRowTest.kt` baut zwei `PlayerStats` (Konstruktor siehe `eval/Statistics.kt:81`; `Player` wie in `StatisticsTest`/`TestFixtures` - z. B. `TEST_CLUB.roster[0]`; `declarations = emptyMap()`, `results = emptyList()`) und prueft:

```kotlin
    @Test
    fun `Detailzeile mit Mittelpunkten und Quote als Bruch`() {
        val stats = PlayerStats(
            player = TEST_CLUB.roster[0], halfPoints = 72, sessions = 1, rounds = 1,
            soloRounds = 1, soloWins = 1, declarations = emptyMap(), results = emptyList(),
        )
        assertEquals("1 Abend · 1 Runde · allein 100 % (1/1)", stats.toRow().detail)
    }

    @Test
    fun `ohne Alleinspiel steht nie`() {
        val stats = PlayerStats(
            player = TEST_CLUB.roster[0], halfPoints = 0, sessions = 2, rounds = 5,
            soloRounds = 0, soloWins = 0, declarations = emptyMap(), results = emptyList(),
        )
        assertEquals("2 Abende · 5 Runden · allein nie", stats.toRow().detail)
    }
```

(`formatPercent` setzt ein geschuetztes Leerzeichen vor "%". Weicht `PlayerStats` in weiteren Pflichtfeldern ab, die Testdaten entsprechend ergaenzen.)

- [ ] **Step 2: RED** (private `toRow` / altes Format).

- [ ] **Step 3: `LeaderboardScreen.kt`**
  - `toRow()` `internal` machen; Detail: `"${counted(sessions, "Abend", "Abende")} · ${counted(rounds, "Runde", "Runden")} · allein "` + (`"nie"` oder `"${formatPercent(rate)} ($soloWins/$soloRounds)"`).
  - Leerzustand: `Notice("Noch kein Abend in diesem Zeitraum.")`.
  - `EvalSection(title = "Punkte", info = "Die Quote zählt nur Spiele als Alleinspieler. Die Zahl in Klammern ist die Grundlage – aus wenigen Spielen sagt die Quote wenig. Gezählt werden alle Runden, auch die des laufenden Abends, jede mit den Hausregeln ihres Abends.")`.
  - Die `Notice` unter der Tabelle entfernen.

- [ ] **Step 4: `Standings.kt` `StandingsTable`**
  - `Surface`: `shape = RoundedCornerShape(RotaskatDimens.cardCorner)`, `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`.
  - Rang: `color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.accentColors.labelMuted`, `style = RotaskatTextStyles.compact`.
  - Name: `style = RotaskatTextStyles.compact`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.
  - Punkte: `color = colors.forValue(row.halfPoints)` (aus Task 1).
  - KDoc des Composables um einen Satz ergaenzen: "Der erste Rang steht in Gold."

- [ ] **Step 5: GREEN + volle Suite**, Commit: `Rangliste mit Zeitraum-Chips, kurzer Detailzeile und Erklaerung hinter dem Info-Symbol`.

---

### Task 5: Statistik

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/StatsScreen.kt`
- Test: neu `app/src/test/kotlin/io/rotaskat/app/ui/eval/StatsTilesTest.kt`

**Interfaces:**
- Consumes: `StatTile`, `ChipRow`, `PeriodSelector`, `EvalSection(info)`, `Suit.icon` (`ui/common/Labels.kt`), `formatShortDate`.
- Produces: `internal data class StatTileModel(label: String, value: String, detail: String?, warning: String?, valueColor: Color = Color.Unspecified, @DrawableRes icon: Int? = null, iconTint: Color = Color.Unspecified)`, `@Composable internal fun statTiles(stats: PlayerStats): List<StatTileModel>` (Composable wegen Farben), `@Composable internal fun StatTileGrid(tiles: List<StatTileModel>)`.

- [ ] **Step 1: Failing test** - `StatsTilesTest.kt` (Robolectric, weil `statTiles` Farben aus dem Theme liest): baut `PlayerStats` wie in Task 4 und prueft in `setContent { RotaskatTheme { tiles = statTiles(stats) } }`:
  - Spieler mit `soloRounds = 0`: Kachel "Gewinnquote allein" hat `value == "–"` und `detail == "nie allein"`.
  - Spieler mit `soloRounds = 2, soloWins = 1`: `value == formatPercent(0.5)`, `detail == "1 von 2"`, `warning == "Unter $THIN_SOLO_SAMPLE Alleinspielen wenig aussagekräftig"`.
  - Lieblingsspiel bei `declarations = mapOf(GameKind.KREUZ to 3)`, `soloRounds = 5`: `value == "Kreuz"`, `icon == R.drawable.ic_suit_clubs`, `detail == "3 von 5 Alleinspielen"`.
  - Und ein Compose-Test: `StatTileGrid(statTiles(stats))` zeigt die Texte "Punkte", "Gewinnquote allein", "Ø je Runde", "Lieblingsspiel", "Bester Abend", "Schlechtester Abend".

- [ ] **Step 2: RED.**

- [ ] **Step 3: `StatsScreen.kt`**
  - Spielerwahl: `ChipRow(options = standings, selected = selected, onSelect = { selectedId = it.player.id }, label = { it.player.displayName })`.
  - Leerzustand: `Notice("Noch kein Abend in diesem Zeitraum.")`.
  - Statt `PlayerStatsCards(selected)`:

```kotlin
        EvalSection(
            title = "Kennzahlen",
            info = "Überreizt zählt als verloren. Beim Lieblingsspiel zählen nur angesagte Spiele – der Ramsch gehört niemandem.",
        ) {
            StatTileGrid(statTiles(selected))
        }
```

  - `PlayerStatsCards` ersetzen durch:

```kotlin
internal data class StatTileModel(
    val label: String,
    val value: String,
    val detail: String?,
    val warning: String? = null,
    val valueColor: Color = Color.Unspecified,
    @DrawableRes val icon: Int? = null,
    val iconTint: Color = Color.Unspecified,
)

/** Die sechs Kennzahlen eines Spielers, in fester Reihenfolge. */
@Composable
internal fun statTiles(stats: PlayerStats): List<StatTileModel> {
    val score = MaterialTheme.scoreColors
    val accent = MaterialTheme.accentColors
    val rate = stats.soloWinRate
    val favourites = stats.favouriteGames
    val average = stats.averageHalfPointsPerRound
    val best = stats.bestSession
    val worst = stats.worstSession
    val single = favourites.singleOrNull()
    val suit = when (single) {
        GameKind.KARO -> Suit.DIAMONDS
        GameKind.HERZ -> Suit.HEARTS
        GameKind.PIK -> Suit.SPADES
        GameKind.KREUZ -> Suit.CLUBS
        else -> null
    }
    return listOf(
        StatTileModel(
            label = "Punkte",
            value = formatPoints(stats.halfPoints),
            detail = "${counted(stats.sessions, "Abend", "Abende")} · ${counted(stats.rounds, "Runde", "Runden")}",
            valueColor = score.forValue(stats.halfPoints),
        ),
        StatTileModel(
            label = "Gewinnquote allein",
            value = if (rate == null) "–" else formatPercent(rate),
            detail = if (rate == null) "nie allein" else "${stats.soloWins} von ${stats.soloRounds}",
            warning = if (rate != null && stats.soloSampleIsThin) "Unter $THIN_SOLO_SAMPLE Alleinspielen wenig aussagekräftig" else null,
        ),
        StatTileModel(
            label = "Ø je Runde",
            value = if (average == null) "–" else formatAverage(average),
            detail = if (average == null) null else "aus ${counted(stats.rounds, "Runde", "Runden")}",
        ),
        StatTileModel(
            label = "Lieblingsspiel",
            value = if (favourites.isEmpty()) "–" else favourites.joinToString(" / ") { it.label },
            detail = if (favourites.isEmpty()) "nie allein" else "${stats.favouriteGameCount} von ${counted(stats.soloRounds, "Alleinspiel", "Alleinspielen")}",
            icon = suit?.icon,
            iconTint = when (suit) {
                Suit.DIAMONDS, Suit.HEARTS -> accent.suitRed
                Suit.SPADES, Suit.CLUBS -> accent.suitBlack
                null -> Color.Unspecified
            },
        ),
        StatTileModel(
            label = "Bester Abend",
            value = best?.let { formatPoints(it.halfPoints) } ?: "–",
            detail = best?.let { "${formatShortDate(it.startedAt)} · ${counted(it.rounds, "Runde", "Runden")}" },
            valueColor = best?.let { score.forValue(it.halfPoints) } ?: Color.Unspecified,
        ),
        StatTileModel(
            label = "Schlechtester Abend",
            value = worst?.let { formatPoints(it.halfPoints) } ?: "–",
            detail = worst?.let { "${formatShortDate(it.startedAt)} · ${counted(it.rounds, "Runde", "Runden")}" },
            warning = if (best != null && worst != null && stats.sessions == 1) "nur ein Abend" else null,
            valueColor = worst?.let { score.forValue(it.halfPoints) } ?: Color.Unspecified,
        ),
    )
}

/** Zwei Kacheln je Reihe, jede Reihe so hoch wie ihre hoechste Kachel. */
@Composable
internal fun StatTileGrid(tiles: List<StatTileModel>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        for (pair in tiles.chunked(2)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                for (tile in pair) {
                    StatTile(
                        label = tile.label,
                        value = tile.value,
                        detail = tile.detail,
                        warning = tile.warning,
                        valueColor = tile.valueColor,
                        icon = tile.icon,
                        iconTint = tile.iconTint,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
```

  (`halfPoints` von `bestSession`/`worstSession` ist `Long` - bei `Int` `.toLong()`; Feldnamen gegen `Statistics.kt` pruefen, insbesondere `favouriteGameCount`, `soloSampleIsThin`, `THIN_SOLO_SAMPLE`, `startedAt`, `rounds`.)

- [ ] **Step 4: GREEN + volle Suite**, Commit: `Statistik mit Spieler-Chips und Kennzahl-Kacheln`.

---

### Task 6: Abrechnung

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/SettlementScreen.kt`
- Test: neu `app/src/test/kotlin/io/rotaskat/app/ui/eval/SettlementHeaderTest.kt` (falls `SettlementScreen` wegen ViewModel nicht direkt testbar: die Kopfzeilen-Aktionen in `internal fun SettlementActions(onShare: () -> Unit, onHistory: () -> Unit)` auslagern und die testen)

- [ ] **Step 1: Failing test** - `SettlementActions` zeigt Icons mit `contentDescription` "Teilen" und "Punkteverlauf"; Klicks rufen die Callbacks.

- [ ] **Step 2: RED.**

- [ ] **Step 3: `SettlementScreen.kt`**
  - Kopfzeile: `actions = { if (current != null) SettlementActions(onShare = { shareText(...) }, onHistory = { actions.toHistory(sessionId) }) }` mit

```kotlin
@Composable
internal fun SettlementActions(onShare: () -> Unit, onHistory: () -> Unit) {
    IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Teilen") }
    IconButton(onClick = onHistory) { Icon(painterResource(R.drawable.ic_show_chart), contentDescription = "Punkteverlauf") }
}
```

    (`SettlementActions` als `RowScope`-Extension oder mit `Row` - wie es zu `actions: @Composable RowScope.() -> Unit` passt.)
  - Laufender Abend: statt `Notice(...)` ein `Text("Zwischenstand – der Abend läuft noch.", style = labelMedium, color = accentColors.labelMuted)`.
  - Geht nicht auf: `Notice("Die Punkte gehen nicht auf null auf. Bitte eine Runde korrigieren.")`.
  - Zahlungen: `EvalSection(title = "Zahlungen", info = "So wenige Zahlungen wie möglich – nicht jeder mit jedem.")`; keine Zahlung: `Text("Alles ausgeglichen.", style = bodyLarge, color = onSurfaceVariant)`.
  - `PaymentCard`: `color = surfaceContainer`, `border = BorderStroke(1.dp, outlineVariant)`, `shape = RoundedCornerShape(RotaskatDimens.cardCorner)`; Inhalt unveraendert (Satzform bleibt, KDoc bleibt).
  - Salden: `EvalSection(title = "Salden", info = "${state.session.centsPerPoint} Cent je Punkt, festgehalten beim Anpfiff. Plus bekommt, Minus zahlt.")`; `BalanceTable` bekommt denselben Rand/Ecken wie `StandingsTable`, Name in `RotaskatTextStyles.compact`, Betrag `colors.forValue(balance.cents)`.
  - Endstand: `EvalSection(title = "Endstand", subtitle = counted(...))`.
  - `SessionActionSection`: `OutlinedButton(border = BorderStroke(1.dp, colorScheme.outline), colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface), modifier = fillMaxWidth().heightIn(min = RotaskatDimens.tapTarget))`; Hinweise: "Zum Korrigieren einer Runde." bzw. "Ohne Runde – verschwindet aus der Übersicht."
  - Imports (`TextButton` entfaellt).

- [ ] **Step 4: GREEN + volle Suite** (`SettlementShareTest` bleibt gruen), Commit: `Abrechnung mit Icons in der Kopfzeile, Erklaerungen hinter dem Info-Symbol und Karten mit Rand`.

---

### Task 7: Einstellungen, Einstieg, Beitritt, Neuer Abend

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/onboarding/LocalSetupScreen.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/onboarding/JoinScreen.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/session/NewSessionScreen.kt`
- Test: neu `app/src/test/kotlin/io/rotaskat/app/ui/onboarding/OnboardingScreenTest.kt`

- [ ] **Step 1: Failing test** - `OnboardingScreen(onLocal, onJoin)` rendern: Texte "Punkte für eure Skatrunde.", "Ohne Verein", "Mit Verein", "Am Tisch braucht die App keinen Empfang." sind da; Klick auf "Ohne Verein" ruft `onLocal`.

- [ ] **Step 2: RED.**

- [ ] **Step 3: Texte und Look** (Zeilen- und Textangaben aus dem aktuellen Stand; jeweils den ganzen alten String ersetzen):
  - `OnboardingScreen.kt`: Titel "Rotaskat" in `displaySmall`; Untertitel "Punkte für eure Skatrunde."; "Ohne Verein": "Spieler eintragen und loslegen. Alles bleibt auf dem Gerät – einem Verein könnt ihr später beitreten, die Abende kommen mit."; "Mit Verein": "Mit Einladungscode. Die Abende landen auf eurem Server, die Rangliste gilt für alle."; Fusszeile "Am Tisch braucht die App keinen Empfang.". `ChoiceCard`: `color = surfaceContainer`, `border = BorderStroke(1.dp, outlineVariant)`, `shape = RoundedCornerShape(RotaskatDimens.cardCorner)`, Titel in `titleLarge`, rechts ein `Icons.AutoMirrored.Filled.ArrowForward` in `primary`.
  - `LocalSetupScreen.kt`: den Hinweis "Mindestens $MIN_PLAYERS. Wer nicht jeden Abend dabei ist, kann trotzdem ..." ersetzen durch "Mindestens $MIN_PLAYERS – wer fehlt, sitzt einfach nicht mit am Tisch."; Hauptknopf `Button` mit `heightIn(min = tapTarget)` (Farben kommen aus dem Theme).
  - `JoinScreen.kt`: Supporting-Texte bleiben; "Verwerfen löscht die lokalen Abende endgültig. ..." auf den ersten Satz kuerzen, sofern der Rest nur begruendet.
  - `SettingsScreen.kt`: `ClubSection` lokal: statt `Notice` eine Zeile "Alles bleibt auf diesem Gerät." (`bodyMedium`, `onSurfaceVariant`); Verein: Zeilen "Server: <url>" und "Alles abgeglichen." bzw. "<n> Runden warten auf den Server." statt Notice. Abschnitte mit `note =` (seit Task 2 `subtitle =`): "Der Kader wird auf dem Server gepflegt." bleibt `subtitle`; Geld-Hinweis "Gilt ab dem nächsten Abend. Laufende und beendete Abende behalten ihren Satz." wird `info`, der sichtbare `subtitle` wird "Gilt ab dem nächsten Abend.".
  - `NewSessionScreen.kt`: Knopf "Abend starten" mit `heightIn(min = tapTarget)`; lange Hilfetexte (falls vorhanden) auf einen Satz kuerzen.

- [ ] **Step 4: GREEN + volle Suite**, Commit: `Einstieg, Einstellungen und Neuer Abend im Kartentisch-Look mit kuerzeren Texten`.

---

### Task 8: Reste aus Teil 1

**Files:**
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/nav/RotaskatApp.kt` (Leiste animiert)
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/nav/BottomBar.kt`, `nav/Routes.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/session/SessionTopBar.kt`, `session/SessionScreen.kt`
- Modify: `app/src/main/kotlin/io/rotaskat/app/ui/eval/OverviewScreen.kt` (Sync-Hinweis)
- Modify (Eckradien): `session/LastChangeRow.kt`, `round/RoundEntryPanel.kt` (`ExtrasSection`), `session/SessionScreen.kt` (`RoundRow`), `eval/EvalScaffold.kt` (`Notice`), `eval/LiveSessionCard.kt` (Knopf)
- Modify: `docs/superpowers/specs/2026-09-27-design-kartentisch-design.md` (Kachelrand-Satz)
- Test: `app/src/test/kotlin/io/rotaskat/app/ui/session/SessionTopBarTest.kt`, `.../nav/BottomBarTest.kt`

- [ ] **Step 1: Failing tests**
  - `SessionTopBarTest`: neuer Test `waehrend des Ladens keine Aktionen` - `SessionTopBar(..., loading = true, ...)`: weder "Abrechnung" noch "Weitere Optionen" existieren.
  - `BottomBarTest`: bestehender Test bleibt; neuer Test: die Reihenfolge der Beschriftungen entspricht `Routes.TOP_LEVEL` (Abende, Rangliste, Statistik).

- [ ] **Step 2: RED.**

- [ ] **Step 3: Umsetzung**
  1. `SessionTopBar` bekommt `loading: Boolean = false`; `if (editing || loading) return@RotaskatTopBar` in `actions`. `SessionScreen` uebergibt `loading = state == null`.
  2. `RotaskatApp.kt`: `bottomBar = { AnimatedVisibility(visible = Routes.isTopLevel(currentRoute), enter = slideInVertically(tween(150)) { it } + fadeIn(tween(150)), exit = slideOutVertically(tween(100)) { it } + fadeOut(tween(100))) { RotaskatBottomBar(...) } }`. Waehrend die Leiste ausblendet, darf `currentRoute` schon nicht mehr top-level sein - `RotaskatBottomBar` bekommt dann die zuletzt gueltige Route: `val lastTopLevel = remember { mutableStateOf(Routes.HOME) }; if (Routes.isTopLevel(currentRoute)) lastTopLevel.value = currentRoute!!` und uebergibt `lastTopLevel.value`.
  3. `BottomBar.kt`: `TopLevelDestinations` aus `Routes.TOP_LEVEL` ableiten: `Routes.TOP_LEVEL.map { route -> when (route) { Routes.HOME -> TopLevelDestination(route, "Abende", R.drawable.ic_style); Routes.LEADERBOARD -> ...; Routes.STATS -> ...; else -> error("Unbekanntes Tab-Ziel $route") } }`.
  4. `Routes.kt`: `toLeaderboard()` und `toStats()` loeschen (keine Aufrufer).
  5. `OverviewScreen.kt`: Sync-Hinweis als einzeilige `Text`-Zeile "<n> Runden warten auf den Server." (`labelMedium`, `labelMuted`) statt `Notice`.
  6. Eckradien: `RoundedCornerShape(14.dp)`/`(12.dp)`/`(18.dp)` in den genannten Dateien auf `RotaskatDimens.tileCorner` (Zeilen/Knoepfe) bzw. `RotaskatDimens.cardCorner` (Flaechen) umstellen.
  7. Teil-1-Spec, Abschnitt "Ziel": den Satz "Text und Bedienelemente erfuellen WCAG AA (4,5:1 Text, 3:1 grosse Schrift und Bedienelement-Raender)." ergaenzen um "; Kacheln sind ueber Flaeche und Beschriftung erkennbar, der 3:1-Rand gilt fuer den Auswahlrand".

- [ ] **Step 4: GREEN + volle Suite**, Commit: `Leiste animiert, Kopfzeile ohne Aktionen beim Laden, Aufraeumen der Reste aus Teil 1`.

---

### Task 9: Geraetetest (Controller)

- [ ] App-Daten sichern (`adb exec-out run-as io.rotaskat.app.debug tar cf - databases files`), `./gradlew :app:installDebug`.
- [ ] Testabend mit 4 Spielern und ~6 Runden anlegen, sodass zwei Spieler gleich enden; Punkteverlauf, Rangliste, Statistik (Chips, Kacheln, ⓘ), Abrechnung (Icons, Karten), Einstellungen pruefen; Leiste beim Wechsel Abend <-> Startseite.
- [ ] Befunde an einen Fix-Subagenten, danach Daten zurueckspielen, `.issues/DESIGN-NOTIZEN.md` nachziehen.
