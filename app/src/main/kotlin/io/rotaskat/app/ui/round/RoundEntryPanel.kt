package io.rotaskat.app.ui.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.common.CommitButton
import io.rotaskat.app.ui.common.CountBadge
import io.rotaskat.app.ui.common.LocalHaptics
import io.rotaskat.app.ui.common.OptionGrid
import io.rotaskat.app.ui.common.OptionTile
import io.rotaskat.app.ui.common.ScaleSelector
import io.rotaskat.app.ui.common.SectionLabel
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.label
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.scoreColors
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.NullVariant
import io.rotaskat.shared.model.Suit

/**
 * Die Rundeneingabe.
 *
 * Das harte Budget fuer den Standardfall sind VIER Taps, und die Reihenfolge
 * ist die der muendlichen Ansage - "Kreuz mit zwei, Hand, gewonnen":
 *
 *     Alleinspieler  ->  Spielart  ->  Spitzen  ->  Gewonnen/Verloren
 *
 * Daraus folgen die drei Entscheidungen, die diesen Bildschirm ausmachen:
 *
 *  - Es wird NICHT mit Gewonnen/Verloren begonnen. Am Tisch wird zuerst
 *    angesagt und dann gespielt; eine andere Reihenfolge zwingt zum Umdenken.
 *  - "Gewonnen" und "Verloren" sind selbst die Speichern-Knoepfe. Kein
 *    getrennter Commit, kein Bestaetigungsdialog. Bei dreissig Runden pro Abend
 *    wird jede Rueckfrage blind weggetippt, damit ist sie kein Schutz mehr,
 *    sondern nur noch ein Tap. Abgesichert wird ueber Undo.
 *  - Der Geber und damit der Aussetzende rotieren automatisch weiter. Gefragt
 *    wird nicht, korrigiert werden kann immer.
 *
 * Dieses Panel enthaelt ausschliesslich die scrollende EINGABE. Spielwert und
 * Ergebnisknoepfe stehen in [RoundCommitBar] und liegen fest unter dem
 * Scrollbereich - siehe die Begruendung dort.
 */
@Composable
fun RoundEntryPanel(
    draft: RoundDraft,
    seatNames: Map<Int, String>,
    onDraftChange: ((RoundDraft) -> RoundDraft) -> Unit,
    onDealerChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var extrasExpanded by rememberSaveable { mutableStateOf(false) }
    var dealerExpanded by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHaptics.current

    fun pick(transform: (RoundDraft) -> RoundDraft) {
        haptics.select()
        onDraftChange(transform)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RotaskatDimens.sectionSpacing),
    ) {

        DealerRow(
            draft = draft,
            seatNames = seatNames,
            expanded = dealerExpanded,
            onToggle = { dealerExpanded = !dealerExpanded },
            onDealerChange = {
                haptics.select()
                onDealerChange(it)
                dealerExpanded = false
            },
        )

        Column {
            SectionLabel("Alleinspieler")
            OptionGrid(columns = if (draft.seatCount == 4) 2 else 3, itemCount = draft.seatCount) { seat ->
                OptionTile(
                    label = seatNames[seat] ?: "Platz ${seat + 1}",
                    // Der Aussetzende bleibt sichtbar, damit die Sitzordnung
                    // stimmt, ist aber nicht antippbar. Damit sind zwei Fehler
                    // aus validate() strukturell unerreichbar: der Aussetzende
                    // als Alleinspieler und der Aussetzende als Ramsch-Verlierer.
                    enabled = seat != draft.sittingOutSeat,
                    secondaryLabel = if (seat == draft.sittingOutSeat) "sitzt aus" else null,
                    selected = draft.declarerSeat == seat && !draft.isRamsch,
                    onClick = { pick { it.copy(declarerSeat = seat, game = it.game.takeIf { g -> g != GamePick.Ramsch }) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column {
            SectionLabel("Spielart")
            GamePicker(draft) { pick(it) }
        }

        if (draft.isNull) {
            Column {
                SectionLabel("Nullspiel")
                // Zwei-Tap-Sonderweg: die vier festen Werte stehen auf den
                // Kacheln, Spitzen und Stufen verschwinden. Ein Nullspiel hat
                // keine Spielstufe, ein Spitzen-Feld waere dort schlicht falsch.
                OptionGrid(columns = 2, itemCount = NullVariant.entries.size) { index ->
                    val variant = NullVariant.entries[index]
                    OptionTile(
                        label = variant.label,
                        secondaryLabel = variant.gameValue.toString(),
                        selected = draft.nullVariant == variant,
                        onClick = { pick { it.copy(nullVariant = variant) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (draft.game != null && !draft.isNull && !draft.isRamsch) {
            Column {
                SectionLabel("Spitzen")
                // Die Skala haengt an der Spielart: beim Grand gibt es vier
                // Buben und damit vier Kacheln. Eine gemeinsame Reihe 1..11
                // bot dort sieben Spiele an, die es nicht gibt.
                //
                // Die Spaltenzahl bleibt trotzdem 6: die vier Grand-Kacheln
                // liegen damit genau dort, wo beim Farbspiel auch die 1 bis 4
                // liegen, und der Rest der Zeile bleibt leer statt die Position
                // zu verschieben.
                val options = draft.matadorOptions
                OptionGrid(columns = 6, itemCount = options.size) { index ->
                    val value = options[index]
                    OptionTile(
                        label = "$value",
                        selected = draft.matadors == value,
                        onClick = { pick { it.copy(matadors = value) } },
                        height = RotaskatDimens.tapTarget,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (draft.isRamsch) {
            RamschPanel(
                draft = draft,
                seatNames = seatNames,
                onChange = { change -> pick { it.copy(ramsch = change(it.ramsch)) } },
            )
        } else if (draft.game != null) {
            ExtrasSection(
                draft = draft,
                expanded = extrasExpanded,
                onToggle = { extrasExpanded = !extrasExpanded },
                onChange = { pick(it) },
            )
        }
    }
}

/**
 * Spielwert und Ergebnisknoepfe, fest unter der scrollenden Eingabe.
 *
 * Sie stehen bewusst NICHT im Scrollbereich. Ein Vierertisch mit gewaehltem
 * Farbspiel bringt es auf rund tausend dp Eingabe; auf einem 360x800-Geraet
 * laegen "Gewonnen" und "Verloren" damit mehrere hundert dp unter der Falz,
 * und der Spitzen-Block waechst nach dem Tap auf die Spielart auch noch
 * dazwischen. Der vierte Tap des Vier-Tap-Pfads kostete dann jedes Mal eine
 * Wischgeste, und die groessten Flaechen des Bildschirms waeren im
 * Normalzustand gar nicht sichtbar - womit die Begruendung "wird ohne
 * Hinsehen getroffen" hinfaellig waere.
 */
@Composable
fun RoundCommitBar(
    draft: RoundDraft,
    onCommit: (won: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onCancelEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
    ) {
        GameValueDisplay(draft)

        CommitRow(draft = draft, onCommit = onCommit)

        if (draft.editing) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onCancelEdit != null) {
                    TextButton(onClick = onCancelEdit, modifier = Modifier.weight(1f)) {
                        Text("Korrektur verwerfen")
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text("Runde loeschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Wer gibt und damit aussetzt.
 *
 * Steht ganz oben, weil es der einzige Wert ist, den die App selbst gesetzt hat.
 * Er ist eine Anzeige mit Korrekturmoeglichkeit, keine Frage - ein Tap auf
 * "aendern" oeffnet die Auswahl, sonst bleibt sie aus dem Weg.
 */
@Composable
private fun DealerRow(
    draft: RoundDraft,
    seatNames: Map<Int, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDealerChange: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (draft.seatCount == 4) {
                    "Es gibt ${seatNames[draft.dealerSeat] ?: "Platz ${draft.dealerSeat + 1}"} und setzt aus"
                } else {
                    "Es gibt ${seatNames[draft.dealerSeat] ?: "Platz ${draft.dealerSeat + 1}"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!draft.editing) {
                TextButton(onClick = onToggle) { Text(if (expanded) "fertig" else "aendern") }
            }
        }
        if (expanded && !draft.editing) {
            OptionGrid(columns = draft.seatCount, itemCount = draft.seatCount) { seat ->
                OptionTile(
                    label = seatNames[seat] ?: "Platz ${seat + 1}",
                    selected = draft.dealerSeat == seat,
                    onClick = { onDealerChange(seat) },
                    height = RotaskatDimens.tapTarget,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GamePicker(draft: RoundDraft, onPick: ((RoundDraft) -> RoundDraft) -> Unit) {
    val picks = buildList<Pair<GamePick, String>> {
        Suit.entries.forEach { add(GamePick.Colour(it) to it.label) }
        add(GamePick.Grand to "Grand")
        add(GamePick.Null to "Null")
        add(GamePick.Ramsch to "Ramsch")
    }
    OptionGrid(columns = 3, itemCount = picks.size) { index ->
        val (pick, label) = picks[index]
        OptionTile(
            label = label,
            selected = draft.game == pick,
            // Der Wechsel raeumt auf, was zur neuen Ansage nicht passt - der
            // Ramsch den Alleinspieler, das Nullspiel das "ueberreizt", der
            // Grand eine zu hohe Spitzenzahl. Die Regeln stehen in
            // RoundDraft.withGame und damit dort, wo sie ohne Bildschirm
            // nachrechenbar sind.
            onClick = { onPick { current -> current.withGame(pick) } },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Die Zusaetze.
 *
 * Zwei geordnete Skalen statt sechs Schaltern - die Begruendung steht bei
 * [Announcement]. Eingeklappt mit Zaehler-Badge, weil sie in den allermeisten
 * Runden gar nicht gebraucht werden und den Vier-Tap-Pfad sonst optisch
 * zuschuetten wuerden.
 */
@Composable
private fun ExtrasSection(
    draft: RoundDraft,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: ((RoundDraft) -> RoundDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                CountBadge(draft.extrasCount) {
                    Text("Zusaetze", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    text = if (expanded) "zuklappen" else "aufklappen",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (expanded) ExtrasControls(draft, onChange)
    }
}

@Composable
private fun ExtrasControls(draft: RoundDraft, onChange: ((RoundDraft) -> RoundDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        if (!draft.isNull) {
            SectionLabel("Ansage")
            ScaleSelector(
                options = Announcement.entries,
                selectedIndex = draft.announcement.ordinal,
                onSelect = { index ->
                    onChange { it.copy(announcement = Announcement.entries[index]) }
                },
                label = { it.label },
            )

            SectionLabel("Ergebnis")
            ScaleSelector(
                options = Achieved.entries,
                // Nicht die getippte, sondern die WIRKSAME Stufe ist markiert.
                // Sie legt sich beim Ansagen sofort sichtbar mit um, sonst wirkt
                // der berechnete Spielwert falsch.
                selectedIndex = draft.effectiveAchieved.ordinal,
                floorIndex = draft.achievedFloor.ordinal,
                onSelect = { index -> onChange { it.copy(achieved = Achieved.entries[index]) } },
                label = { it.label },
                columns = 3,
            )
        }

        SectionLabel("Kontra")
        ScaleSelector(
            options = ContraLevel.entries,
            selectedIndex = draft.contra.ordinal,
            onSelect = { index -> onChange { it.copy(contra = ContraLevel.entries[index]) } },
            label = { it.label },
            columns = 3,
        )

        if (!draft.isNull) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    text = "Ueberreizt",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.overbid,
                    onCheckedChange = { checked -> onChange { it.copy(overbid = checked) } },
                )
            }
            if (draft.overbid) {
                // Ueberreizt gilt immer als verloren, egal wie die Stiche lagen.
                // Der Reizwert kommt aus der Liste der echten Reizstufen: ein
                // frei getippter Wert, den es nicht gibt, waere still falsch.
                SectionLabel("Gereizt bis")
                OptionGrid(columns = 6, itemCount = RoundDraft.BIDS.size) { index ->
                    val value = RoundDraft.BIDS[index]
                    OptionTile(
                        label = "$value",
                        selected = draft.bid == value,
                        onClick = { onChange { it.copy(bid = value) } },
                        height = RotaskatDimens.tapTarget,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Der Spielwert, gross und live.
 *
 * Er wird NIEMALS eingetippt. Ein Reizwert allein ist mehrdeutig - 36 ist Kreuz
 * mit 2 genauso wie Karo mit 3 -, und ein Eingabefeld wuerde das Kopfrechnen
 * genau dorthin verlagern, wo am Tisch die Fehler entstehen. Umgekehrt ist die
 * live gerechnete Zahl die unabhaengige Zweitrechnung: wer im Kopf auf etwas
 * anderes kommt, sieht die Abweichung, bevor die Runde gespeichert ist. Die
 * Herleitung darunter macht sie gegen die Ansage pruefbar.
 */
@Composable
private fun GameValueDisplay(draft: RoundDraft) {
    val value = draft.gameValue
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 12.dp),
        ) {
            Text(
                text = value?.toString() ?: "-",
                style = RotaskatTextStyles.gameValue,
                color = if (value != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = draft.derivation() ?: "Alleinspieler und Spielart waehlen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
            color = colors.lossContainer,
            onContentColor = MaterialTheme.colorScheme.onSurface,
            enabled = ready,
            onClick = { onCommit(false) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CommitButton(
            label = "Gewonnen",
            detail = draft.declarerHalfPoints(won = true)?.let { formatPoints(it) },
            color = colors.gain,
            onContentColor = colors.onGain,
            enabled = ready,
            onClick = { onCommit(true) },
            modifier = Modifier.weight(1f),
        )
        CommitButton(
            label = "Verloren",
            detail = draft.declarerHalfPoints(won = false)?.let { formatPoints(it) },
            color = colors.loss,
            onContentColor = colors.onLoss,
            enabled = ready,
            onClick = { onCommit(false) },
            modifier = Modifier.weight(1f),
        )
    }
}
