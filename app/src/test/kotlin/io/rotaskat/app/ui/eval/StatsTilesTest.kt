package io.rotaskat.app.ui.eval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.rotaskat.app.R
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.ui.common.formatPercent
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `statTiles` ist bewusst `@Composable`, weil die Kachelfarben (Gewinn/Verlust,
 * Kupfer/Elfenbein fuer die Farbsymbole) aus dem Theme kommen. Deshalb laufen
 * diese Tests unter Robolectric statt als reine JVM-Unittests.
 */
@RunWith(RobolectricTestRunner::class)
class StatsTilesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Gewinnquote allein ohne Alleinspiel zeigt Gedankenstrich`() {
        val stats = PlayerStats(
            player = TEST_CLUB.roster[0], halfPoints = 0, sessions = 1, rounds = 5,
            soloRounds = 0, soloWins = 0, declarations = emptyMap(), results = emptyList(),
        )
        var tiles: List<StatTileModel> = emptyList()
        compose.setContent {
            RotaskatTheme {
                tiles = statTiles(stats)
            }
        }
        val tile = tiles.first { it.label == "Gewinnquote allein" }
        assertEquals("–", tile.value)
        assertEquals("nie allein", tile.detail)
    }

    @Test
    fun `Gewinnquote allein mit duennem Sample zeigt Quote, Bruch und Warnung`() {
        val stats = PlayerStats(
            player = TEST_CLUB.roster[0], halfPoints = 0, sessions = 1, rounds = 5,
            soloRounds = 2, soloWins = 1, declarations = emptyMap(), results = emptyList(),
        )
        var tiles: List<StatTileModel> = emptyList()
        compose.setContent {
            RotaskatTheme {
                tiles = statTiles(stats)
            }
        }
        val tile = tiles.first { it.label == "Gewinnquote allein" }
        assertEquals(formatPercent(0.5), tile.value)
        assertEquals("1 von 2", tile.detail)
        assertEquals("Unter $THIN_SOLO_SAMPLE Alleinspielen wenig aussagekräftig", tile.warning)
    }

    @Test
    fun `Lieblingsspiel zeigt Name, Karosymbol und Anteil`() {
        val stats = PlayerStats(
            player = TEST_CLUB.roster[0], halfPoints = 0, sessions = 1, rounds = 5,
            soloRounds = 5, soloWins = 0,
            declarations = mapOf(GameKind.KREUZ to 3), results = emptyList(),
        )
        var tiles: List<StatTileModel> = emptyList()
        compose.setContent {
            RotaskatTheme {
                tiles = statTiles(stats)
            }
        }
        val tile = tiles.first { it.label == "Lieblingsspiel" }
        assertEquals("Kreuz", tile.value)
        assertEquals(R.drawable.ic_suit_clubs, tile.icon)
        assertEquals("3 von 5 Alleinspielen", tile.detail)
    }

    @Test
    fun `Lieblingsspiel ohne Alleinspiel zeigt Gedankenstrich ohne Symbol`() {
        val stats = PlayerStats(
            player = TEST_CLUB.roster[0], halfPoints = 0, sessions = 1, rounds = 5,
            soloRounds = 0, soloWins = 0, declarations = emptyMap(), results = emptyList(),
        )
        var tiles: List<StatTileModel> = emptyList()
        compose.setContent {
            RotaskatTheme {
                tiles = statTiles(stats)
            }
        }
        val tile = tiles.first { it.label == "Lieblingsspiel" }
        assertEquals("–", tile.value)
        assertNull(tile.icon)
    }

    @Test
    fun `StatTileGrid zeigt alle sechs Kennzahlbeschriftungen`() {
        val stats = PlayerStats(
            player = TEST_CLUB.roster[0], halfPoints = 72, sessions = 1, rounds = 5,
            soloRounds = 2, soloWins = 1,
            declarations = mapOf(GameKind.KREUZ to 2), results = emptyList(),
        )
        compose.setContent {
            RotaskatTheme {
                StatTileGrid(statTiles(stats))
            }
        }
        for (label in listOf(
            "Punkte", "Gewinnquote allein", "Ø je Runde",
            "Lieblingsspiel", "Bester Abend", "Schlechtester Abend",
        )) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }
}
