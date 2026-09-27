package io.rotaskat.app.ui.eval

import io.rotaskat.app.data.TEST_CLUB
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Detailzeile der Rangliste ist die einzige Stelle, an der die Quote und
 * ihre Grundgesamtheit gemeinsam stehen. Kurz genug fuer eine Zeile, aber ohne
 * die Zahl in Klammern waere sie nur eine Behauptung.
 */
class LeaderboardRowTest {

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
}
