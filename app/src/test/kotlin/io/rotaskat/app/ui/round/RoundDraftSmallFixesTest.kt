package io.rotaskat.app.ui.round

import io.rotaskat.app.data.suitRound
import io.rotaskat.shared.model.NullVariant
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Die kleinen Unstimmigkeiten aus dem ersten Geraetetest (Issue 010). */
class RoundDraftSmallFixesTest {

    private fun draft() = RoundDraft.forNextRound("r1", 4, 3, ScoringConfig())

    /** Null ist laut SCOPE.md ein Zwei-Tap-Sonderweg: Alleinspieler, Null, fertig. */
    @Test
    fun `Null waehlt die einfache Null vor`() {
        val d = draft().copy(declarerSeat = 0).withGame(GamePick.Null)

        assertEquals(NullVariant.NULL, d.nullVariant)
        assertTrue(d.readyForResult)
        assertEquals(23, d.gameValue)
    }

    @Test
    fun `eine bereits gewaehlte Null-Variante bleibt beim erneuten Tippen stehen`() {
        val d = draft().copy(declarerSeat = 0).withGame(GamePick.Null)
            .copy(nullVariant = NullVariant.NULL_OUVERT)
            .withGame(GamePick.Null)

        assertEquals(NullVariant.NULL_OUVERT, d.nullVariant)
    }

    /** In der Korrektur war nicht zu sehen, was urspruenglich gespeichert war. */
    @Test
    fun `die Korrektur kennt den gespeicherten Ausgang`() {
        val lost = RoundDraft.fromRound(
            suitRound("r", dealerSeat = 3, declarerSeat = 0, won = false, suit = Suit.HEARTS),
            ScoringConfig(),
        )
        assertEquals(false, lost.originalWon)

        assertNull(draft().originalWon, "Eine neue Runde hat keinen bisherigen Ausgang")
    }
}
