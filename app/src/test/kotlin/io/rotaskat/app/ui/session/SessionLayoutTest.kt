package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.ui.round.Announcement
import io.rotaskat.app.ui.round.GamePick
import io.rotaskat.app.ui.round.RoundDraft
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.Suit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Das Platzbudget des Vier-Tap-Pfads.
 *
 * Auf dem ersten echten Geraet (Pixel 10 Pro, 411x918dp, Standard-Schrift)
 * lagen die Spitzen unter der Falz. Ohne Scrollen blieb dann der Vorgabewert
 * "mit 1" stehen - genau dort, wo ein falscher Spielwert am wahrscheinlichsten
 * ist, weil man ihn nicht sieht.
 *
 * Gerechnet wird mit einem typischen Telefon von 360x800dp, abzueglich
 * Statusleiste, Kopfzeile und Gestenleiste. Passt es dort, passt es auf dem
 * Pixel erst recht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SessionLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `am Vierertisch stehen die Spitzen ohne Scrollen im Bild`() {
        show(seatCount = 4, draft(seatCount = 4))
        assertVisibleInScrollArea("Spitzen", "1", "4")
    }

    @Test
    fun `am Dreiertisch stehen die Spitzen ohne Scrollen im Bild`() {
        show(seatCount = 3, draft(seatCount = 3))
        assertVisibleInScrollArea("Spitzen", "1", "4")
    }

    /** Die lange Herleitung liess die Wertkarte frueher auf vier Zeilen wachsen. */
    @Test
    fun `eine lange Herleitung verdraengt die Spitzen nicht`() {
        show(seatCount = 4, draft(seatCount = 4).copy(matadors = 2, announcement = Announcement.OUVERT))
        assertVisibleInScrollArea("Spitzen", "1", "4")
    }

    private fun draft(seatCount: Int) = RoundDraft.forNextRound(
        roundId = "r0",
        seatCount = seatCount,
        dealerSeat = 0,
        config = TEST_CLUB.scoring,
    ).copy(declarerSeat = 1).withGame(GamePick.Colour(Suit.CLUBS))

    private fun show(seatCount: Int, draft: RoundDraft) {
        val session = Session(
            id = "s",
            clubId = TEST_CLUB.id,
            seatCount = seatCount,
            seats = (0 until seatCount).associateWith { "p$it" },
            startedAt = T0,
            scoring = TEST_CLUB.scoring,
            dealerSeat = 0,
        )
        val state = SessionState(session, emptyList(), (0 until seatCount).associateWith { 0L })
        val names = TEST_CLUB.roster.take(seatCount).withIndex().associate { (seat, player) ->
            seat to player.displayName
        }
        compose.setContent {
            RotaskatTheme {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(STATUS_BAR + TOP_APP_BAR))
                    SessionBody(
                        state = state,
                        draft = draft,
                        names = names,
                        editing = false,
                        scrollState = rememberScrollState(),
                        onDraftChange = {},
                        onDealerChange = {},
                        onCommit = {},
                        onEditRound = {},
                        onCancelEdit = {},
                        onDelete = {},
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.height(GESTURE_BAR))
                }
            }
        }
    }

    private fun assertVisibleInScrollArea(vararg texts: String) {
        // Ungeclippt: boundsInRoot schneidet einen Knoten unterhalb der Falz auf
        // den sichtbaren Rand zurecht, und der Test waere immer gruen.
        val area = compose.onNodeWithTag(SessionTags.SCROLL_AREA).fetchSemanticsNode()
        val areaBottom = area.positionInRoot.y + area.size.height
        for (text in texts) {
            val node = compose.onNodeWithText(text).fetchSemanticsNode()
            val bottom = node.positionInRoot.y + node.size.height
            assertTrue(
                bottom <= areaBottom,
                "\"$text\" endet bei $bottom px, der sichtbare Bereich schon bei $areaBottom px",
            )
        }
    }

    private companion object {
        val STATUS_BAR = 48.dp
        val TOP_APP_BAR = 64.dp
        val GESTURE_BAR = 24.dp
    }
}
