package io.rotaskat.shared.settlement

import io.rotaskat.shared.scoring.RoundScore

/**
 * Saldo eines Spielers am Abendende, in ganzen Cent.
 * Positiv heisst: bekommt Geld. Negativ: zahlt.
 */
data class Balance<P>(
    val player: P,
    val cents: Long,
)

/** Eine einzelne Zahlung. [cents] ist immer positiv. */
data class Payment<P>(
    val from: P,
    val to: P,
    val cents: Long,
)

/**
 * Das Ergebnis eines Abends: die Saldenliste und die Zahlungen, die sie
 * glattstellen. Abgeleiteter Wert wie [RoundScore], also bewusst nicht
 * serialisierbar - gespeichert werden die Rohdaten.
 */
data class Settlement<P>(
    val balances: List<Balance<P>>,
    val payments: List<Payment<P>>,
) {
    /**
     * Was jeder Spieler durch die Zahlungen tatsaechlich bekommt oder zahlt.
     * Muss Spieler fuer Spieler dem Saldo entsprechen - genau das ist die
     * Zusicherung der Abrechnung.
     */
    fun netTransfers(): Map<P, Long> {
        val net = balances.associate { it.player to 0L }.toMutableMap()
        for (payment in payments) {
            net[payment.from] = (net[payment.from] ?: 0L) - payment.cents
            net[payment.to] = (net[payment.to] ?: 0L) + payment.cents
        }
        return net
    }
}

/**
 * Die Geldabrechnung eines Spielabends.
 *
 * Gerechnet wird durchgehend in ganzen Cent und in [Long]. Long nicht aus
 * Groessenwahn, sondern weil die Nullsummen-Invariante overflow-blind ist:
 * ein Int-Ueberlauf bleibt nullsummig und faellt deshalb nirgends auf.
 * Der Abend selbst passt bequem in Int, die Saison-Summe nicht mehr
 * zwangslaeufig.
 */
object Settlements {

    /**
     * Summiert die halben Punkte aller Runden je Sitzplatz auf.
     *
     * Bewusst die Referenzrechnung in Long: die Einzelrunde rechnet in Int,
     * die Aufsummierung ueber einen Abend oder eine Saison soll nicht still
     * ueberlaufen.
     */
    fun accumulateHalfPoints(scores: Iterable<RoundScore>): Map<Int, Long> {
        val totals = linkedMapOf<Int, Long>()
        for (score in scores) {
            for ((seat, halfPoints) in score.halfPoints.entries.sortedBy { it.key }) {
                totals[seat] = (totals[seat] ?: 0L) + halfPoints
            }
        }
        return totals
    }

    /**
     * Rechnet halbe Punkte in Cent-Salden um.
     *
     * Ein halber Punkt ist `centsPerPoint / 2` Cent und damit bei ungeradem
     * Satz kein ganzer Cent. Statt jeden Spieler einzeln zu runden - was die
     * Summe um ein paar Cent verfehlen wuerde und die Zahlungen nicht mehr
     * aufgehen liesse - wird zuerst exakt in HALBEN Cent gerechnet, dann
     * abgerundet und der so entstandene Rest in stabiler Reihenfolge wieder
     * verteilt. Damit bleibt die Summe exakt erhalten und dieselbe Eingabe
     * ergibt immer dieselbe Abrechnung.
     */
    fun <P> balances(halfPoints: Map<P, Long>, centsPerPoint: Int): List<Balance<P>> {
        require(centsPerPoint >= 0) { "centsPerPoint darf nicht negativ sein, ist $centsPerPoint" }

        val cents = LinkedHashMap<P, Long>(halfPoints.size)
        val withRemainder = mutableListOf<P>()
        for ((player, points) in halfPoints) {
            val halfCents = Math.multiplyExact(points, centsPerPoint.toLong())
            val floored = Math.floorDiv(halfCents, 2L)
            cents[player] = floored
            if (halfCents != floored * 2L) withRemainder += player
        }

        // Nach dem Abrunden fehlt je Rest ein halber Cent. Bei nullsummiger
        // Eingabe ist die Anzahl der Reste gerade, und die Haelfte davon als
        // ganzer Cent gleicht die Summe exakt wieder aus.
        for (index in 0 until withRemainder.size / 2) {
            val player = withRemainder[index]
            cents[player] = cents.getValue(player) + 1
        }

        return cents.map { (player, value) -> Balance(player, value) }
    }

    /**
     * Zahlungen, die alle Salden glattstellen.
     *
     * Greedy: der groesste Glaeubiger bekommt vom groessten Schuldner, bis
     * einer von beiden auf null ist. Jeder Schritt streicht mindestens einen
     * Spieler, es bleiben also hoechstens n-1 Zahlungen. Das exakte Minimum
     * waere ein Teilmengenproblem; bei drei bis vier Spielern am Tisch findet
     * das Verfahren passende Paare ohnehin sofort, und der Unterschied waere
     * hoechstens eine Zahlung.
     */
    fun <P> payments(balances: List<Balance<P>>): List<Payment<P>> {
        val total = balances.sumOf { it.cents }
        require(total == 0L) {
            "Die Salden eines Abends muessen sich zu 0 summieren, Summe ist $total"
        }

        val players = balances.map { it.player }
        val open = balances.map { it.cents }.toLongArray()
        val payments = mutableListOf<Payment<P>>()

        while (true) {
            var creditor = -1
            var debtor = -1
            for (i in open.indices) {
                // Strikt groesser: bei Gleichstand gewinnt der fruehere
                // Sitzplatz, damit die Zahlungsliste reproduzierbar ist.
                if (open[i] > 0 && (creditor == -1 || open[i] > open[creditor])) creditor = i
                if (open[i] < 0 && (debtor == -1 || open[i] < open[debtor])) debtor = i
            }
            if (creditor == -1 || debtor == -1) break

            val amount = minOf(open[creditor], -open[debtor])
            payments += Payment(players[debtor], players[creditor], amount)
            open[creditor] -= amount
            open[debtor] += amount
        }

        return payments
    }

    /** Salden und Zahlungen eines Abends in einem Schritt. */
    fun <P> settle(halfPoints: Map<P, Long>, centsPerPoint: Int): Settlement<P> {
        val balances = balances(halfPoints, centsPerPoint)
        return Settlement(balances, payments(balances))
    }

    /** Abrechnung direkt aus den Rundenergebnissen eines Abends. */
    fun settleRounds(scores: Iterable<RoundScore>, centsPerPoint: Int): Settlement<Int> =
        settle(accumulateHalfPoints(scores), centsPerPoint)
}
