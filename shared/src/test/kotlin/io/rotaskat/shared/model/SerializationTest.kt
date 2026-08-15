package io.rotaskat.shared.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.rotaskat.shared.scoring.GameValueTable
import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.scoring.ScoringConfig
import io.rotaskat.shared.property
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rohdaten gehen als JSON zum Server und in die Room-Datenbank. Geht dabei
 * ein Feld verloren, faellt es erst Monate spaeter in der Rangliste auf -
 * deshalb der Roundtrip ueber den gesamten Ansageraum statt ueber ein
 * Beispiel.
 */
class SerializationTest {

    private val json = Json

    @Test
    fun `Jede Ansage ueberlebt den Roundtrip`() = property {
        val all: List<Declaration> = GameValueTable.allDeclarations() + RamschGame
        checkAll(500, Arb.of(all)) { declaration ->
            val text = json.encodeToString<Declaration>(declaration)
            assertEquals(declaration, json.decodeFromString<Declaration>(text))
        }
    }

    @Test
    fun `Alle Declaration-Varianten sind polymorph unterscheidbar`() {
        // Der Typdiskriminator muss stabil bleiben: er steht so in der
        // Datenbank und in bereits synchronisierten Runden.
        val samples = mapOf(
            "suit" to SuitGame(Suit.CLUBS, 2, Modifiers(hand = true)),
            "grand" to GrandGame(4, Modifiers(ouvert = true)),
            "null" to NullGame(NullVariant.NULL_HAND_OUVERT),
            "ramsch" to RamschGame,
        )
        for ((discriminator, declaration) in samples) {
            val text = json.encodeToString<Declaration>(declaration)
            assertTrue(text.contains("\"type\":\"$discriminator\""), "Diskriminator fehlt in $text")
            assertEquals(declaration, json.decodeFromString<Declaration>(text))
        }
    }

    @Test
    fun `Eine Runde ueberlebt den Roundtrip samt Ramschdetails und Geber`() {
        val rounds = listOf(
            Round(
                id = "r1",
                seatCount = 4,
                declarerSeat = 2,
                sittingOutSeat = 3,
                declaration = SuitGame(Suit.SPADES, 3, Modifiers(schneiderAnnounced = true)),
                won = true,
                contra = ContraLevel.KONTRA,
                bid = 55,
                overbid = true,
                dealerSeat = 3,
            ),
            Round(
                id = "r2",
                seatCount = 3,
                declarerSeat = null,
                sittingOutSeat = null,
                declaration = RamschGame,
                ramsch = RamschResult(loserSeat = 1, cardPoints = 84, jungfrau = true, pushes = 2),
                dealerSeat = 2,
            ),
        )
        for (round in rounds) {
            val restored = json.decodeFromString<Round>(json.encodeToString(round))
            assertEquals(round, restored)
            assertEquals(Scoring.score(round).halfPoints, Scoring.score(restored).halfPoints)
        }
    }

    @Test
    fun `Verein und Session ueberleben den Roundtrip`() {
        val club = Club(
            id = "c1",
            name = "Skatrunde Hinterzimmer",
            scoring = ScoringConfig(jungfrauDoubles = false, durchmarschValue = 144),
            centsPerPoint = 7,
            roster = listOf(Player("p1", "Anna"), Player("p2", "Bert", active = false)),
        )
        assertEquals(club, json.decodeFromString<Club>(json.encodeToString(club)))

        val session = Session.startedFor(
            id = "s1",
            club = club,
            seatCount = 4,
            seats = mapOf(0 to "p1", 1 to "p2", 2 to "p3", 3 to "p4"),
            startedAt = Instant.parse("2026-01-09T19:30:00Z"),
            dealerSeat = 2,
        ).copy(status = SessionStatus.CLOSED, endedAt = Instant.parse("2026-01-09T23:55:00Z"))

        val restored = json.decodeFromString<Session>(json.encodeToString(session))
        assertEquals(session, restored)
        assertEquals(club.scoring, restored.scoring)
        assertEquals(7, restored.centsPerPoint)
    }
}
