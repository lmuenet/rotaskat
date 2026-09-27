package io.rotaskat.app.ui.round

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.Suit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Die Herleitung neben dem Spielwert (F5).
 *
 * Bei einem ueberreizten Spiel wird die Herleitung um "… überreizt auf X = Y"
 * laenger (plus ggf. Kontra-Zusatz) und braucht eine dritte Zeile, sonst
 * schneidet `maxLines` sie ab.
 */
@RunWith(RobolectricTestRunner::class)
class RoundCommitBarTest {

    @get:Rule
    val compose = createComposeRule()

    private fun draft(overbid: Boolean): RoundDraft {
        val base = RoundDraft.forNextRound(
            roundId = "r0",
            seatCount = 4,
            dealerSeat = 0,
            config = TEST_CLUB.scoring,
        ).copy(declarerSeat = 1)
            .withGame(GamePick.Colour(Suit.CLUBS))
            .copy(matadors = 2, contra = ContraLevel.KONTRA)
        return if (overbid) base.withOverbid(true) else base
    }

    private fun maxLinesOf(draft: RoundDraft): Int {
        compose.setContent {
            RotaskatTheme {
                RoundCommitBar(draft = draft, onCommit = {})
            }
        }
        val derivation = checkNotNull(draft.derivation())
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(derivation, substring = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.maxLines
    }

    @Test
    fun `ohne Ueberreizung bleiben zwei Zeilen`() {
        assertEquals(2, maxLinesOf(draft(overbid = false)))
    }

    @Test
    fun `bei Ueberreizung gibt es eine dritte Zeile fuer die Herleitung`() {
        assertEquals(3, maxLinesOf(draft(overbid = true)))
    }
}
