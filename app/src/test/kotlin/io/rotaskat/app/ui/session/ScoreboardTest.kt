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
