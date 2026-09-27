package io.rotaskat.app.ui.eval

import android.content.Context
import android.content.Intent
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.common.counted
import io.rotaskat.app.ui.common.formatAmount
import io.rotaskat.app.ui.common.formatDate
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.shared.model.SessionStatus
import kotlinx.datetime.TimeZone

/**
 * Die Abrechnung als Text zum Weiterschicken.
 *
 * Die Abrechnung wird ohnehin in die Gruppe geschickt - bisher als Screenshot
 * oder abgetippt. Gerechnet wird hier nichts Neues: Stand und Zahlungen kommen
 * aus derselben [SessionState.settlement], die auch der Bildschirm zeigt.
 */
fun settlementShareText(
    state: SessionState,
    names: Map<Int, String>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String = buildString {
    val session = state.session
    fun name(seat: Int) = names[seat] ?: "Platz ${seat + 1}"

    val rounds = counted(state.liveRounds.size, "Runde", "Runden")
    val stand = if (session.status == SessionStatus.OPEN) "Zwischenstand" else "Endstand"
    appendLine("Skat am ${formatDate(session.startedAt, zone)} – $stand nach $rounds")
    appendLine(
        state.totals.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { (seat, half) -> "${name(seat)} ${formatPoints(half)}" },
    )

    val settlement = runCatching { state.settlement() }.getOrNull() ?: return@buildString
    appendLine()
    if (settlement.payments.isEmpty()) {
        append("Keine Zahlungen – alle stehen auf null.")
        return@buildString
    }
    appendLine("Zahlungen (${session.centsPerPoint} Cent je Punkt):")
    append(
        settlement.payments.joinToString("\n") { payment ->
            "${name(payment.from)} zahlt ${formatAmount(payment.cents)} an ${name(payment.to)}"
        },
    )
}

/** Oeffnet den System-Dialog zum Teilen. */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Abrechnung teilen"))
}
