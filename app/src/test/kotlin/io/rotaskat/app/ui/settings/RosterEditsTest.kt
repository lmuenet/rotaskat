package io.rotaskat.app.ui.settings

import io.rotaskat.app.data.TEST_CLUB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Der Kader ohne Verein. Wer beim Einrichten nur vier Namen eingetragen hatte,
 * konnte vorher keinen fuenften mehr anlegen.
 */
class RosterEditsTest {

    @Test
    fun `ein neuer Spieler kommt ans Ende des Kaders`() {
        val edit = assertIs<RosterEdit.Ok>(TEST_CLUB.addPlayer("  Emil ", newId = "p4"))

        assertEquals("Emil", edit.club.roster.last().displayName)
        assertEquals("p4", edit.club.roster.last().id)
        assertEquals(5, edit.club.roster.size)
    }

    @Test
    fun `leere und doppelte Namen werden abgelehnt`() {
        assertIs<RosterEdit.Rejected>(TEST_CLUB.addPlayer("   ", newId = "p4"))
        assertIs<RosterEdit.Rejected>(TEST_CLUB.addPlayer("anna", newId = "p4"))
    }

    @Test
    fun `umbenennen behaelt die Id - die Abende zeigen danach den neuen Namen`() {
        val edit = assertIs<RosterEdit.Ok>(TEST_CLUB.renamePlayer("p1", "Berthold"))

        assertEquals("Berthold", edit.club.roster.first { it.id == "p1" }.displayName)
        assertIs<RosterEdit.Rejected>(TEST_CLUB.renamePlayer("p1", "Carl"))
        assertIs<RosterEdit.Ok>(TEST_CLUB.renamePlayer("p1", "Bert"), "Der eigene Name ist kein Duplikat")
    }

    @Test
    fun `entfernen geht nur, solange der Spieler nie am Tisch sass`() {
        assertIs<RosterEdit.Rejected>(TEST_CLUB.removePlayer("p1", seated = setOf("p1")))

        val edit = assertIs<RosterEdit.Ok>(TEST_CLUB.removePlayer("p1", seated = setOf("p0")))
        assertEquals(listOf("p0", "p2", "p3"), edit.club.roster.map { it.id })
    }

    /** Ohne mindestens einen Spieler wuerde saveClub den Kader gar nicht anfassen. */
    @Test
    fun `der letzte Spieler bleibt`() {
        val single = TEST_CLUB.copy(roster = TEST_CLUB.roster.take(1))
        assertIs<RosterEdit.Rejected>(single.removePlayer("p0", seated = emptySet()))
    }

    @Test
    fun `der Cent-Satz ist nicht negativ`() {
        assertEquals(25, assertIs<RosterEdit.Ok>(TEST_CLUB.withCentsPerPoint(25)).club.centsPerPoint)
        assertIs<RosterEdit.Rejected>(TEST_CLUB.withCentsPerPoint(-1))
    }
}
