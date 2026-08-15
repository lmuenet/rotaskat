package io.rotaskat.server.routes

import io.rotaskat.server.repo.StoredRound
import io.rotaskat.server.repo.StoredSession
import io.rotaskat.shared.api.BalanceDto
import io.rotaskat.shared.api.PaymentDto
import io.rotaskat.shared.api.RoundEnvelope
import io.rotaskat.shared.api.SessionEnvelope
import io.rotaskat.shared.api.SettlementDto
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.scoring.RoundScore
import io.rotaskat.shared.settlement.Settlements

/**
 * Ausgeliefert wird immer der SERVERSEITIG nachgerechnete Wert. Das Feld
 * `clientHalfPoints` bleibt beim Lesen leer - es ist eine Pruefsumme des
 * Uploads, keine Information, die zurueckfliessen soll.
 */
fun StoredRound.toEnvelope(): RoundEnvelope = RoundEnvelope(
    round = round,
    sessionId = sessionId,
    sequence = sequence,
    revision = revision,
    deletedAt = deletedAt,
    halfPoints = halfPoints,
    gameValue = gameValue,
    updatedSeq = updatedSeq,
)

fun StoredSession.toEnvelope(): SessionEnvelope = SessionEnvelope(
    session = session,
    revision = revision,
    deletedAt = deletedAt,
    updatedSeq = updatedSeq,
)

/**
 * Summe der halben Punkte je Sitzplatz. Tombstones zaehlen nicht mit, sonst
 * wuerde eine zurueckgenommene Runde weiter in der Abrechnung stehen.
 */
fun sessionTotals(session: Session, rounds: List<StoredRound>): Map<Int, Long> {
    val scores = rounds.filter { it.deletedAt == null }.map { RoundScore(it.halfPoints, it.gameValue) }
    val accumulated = Settlements.accumulateHalfPoints(scores)
    // Alle Sitzplaetze fuehren, auch die ohne einzige Runde: eine fehlende
    // Zeile in der Abendabrechnung liest sich wie ein vergessener Spieler.
    return (0 until session.seatCount).associateWith { accumulated[it] ?: 0L }
}

/**
 * Die Geldabrechnung des Abends. Liefert null, wenn die Salden nicht
 * nullsummig sind - dann ist etwas an den Rohdaten kaputt, und eine falsche
 * Zahlungsliste waere schlimmer als gar keine.
 */
fun settlementOf(session: Session, totals: Map<Int, Long>): SettlementDto? {
    if (totals.values.sum() != 0L) return null

    val settlement = Settlements.settle(totals, session.centsPerPoint)
    return SettlementDto(
        centsPerPoint = session.centsPerPoint,
        balances = settlement.balances.map { BalanceDto(it.player, session.seats[it.player], it.cents) },
        payments = settlement.payments.map {
            PaymentDto(
                fromSeat = it.from,
                toSeat = it.to,
                fromPlayerId = session.seats[it.from],
                toPlayerId = session.seats[it.to],
                cents = it.cents,
            )
        },
    )
}
