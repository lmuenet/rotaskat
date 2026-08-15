package io.rotaskat.app.data.db

import androidx.room.TypeConverter
import io.rotaskat.shared.api.RotaskatJson
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.scoring.ScoringConfig
import kotlinx.datetime.Instant

/**
 * Uebersetzt die kotlinx-Typen des geteilten Modells auf SQLite-Spalten.
 *
 * Bewusst mit denselben Json-Einstellungen wie die Leitung ([RotaskatJson]).
 * Der Server bildet seinen Inhalts-Hash ueber genau diese Serialisierung; wuerde
 * die lokale Kopie anders kodieren, waere jede aus der Datenbank gelesene Runde
 * beim naechsten Push ein Konflikt.
 */
class RotaskatConverters {

    /**
     * Zeitstempel als ISO-Text, nicht als Millisekunden.
     *
     * Millisekunden waeren kompakter, wuerden aber die Nanosekunden eines
     * `Clock.System.now()` abschneiden. Der serverseitige Inhalts-Hash haengt am
     * serialisierten `startedAt`: eine frisch angelegte Session haette dann in
     * der App einen anderen Hash als dieselbe Session nach einem Neustart, und
     * der Sync wuerde sie mit gleicher Revision und anderem Inhalt als 409
     * zurueckweisen.
     */
    @TypeConverter
    fun instantToText(value: Instant): String = value.toString()

    @TypeConverter
    fun textToInstant(value: String): Instant = Instant.parse(value)

    /**
     * Die Runde als JSON-Nutzlast.
     *
     * `Declaration` ist ein sealed interface mit vier Auspraegungen und
     * `Modifiers` haelt sechs Schalter. Relational ausgefaltet zwaenge jede neue
     * Hausregel zu einer Room- UND einer Flyway-Migration. Als Nutzlast ist sie
     * eine Serialisierungsfrage mit Vorgabewerten - und exakt dasselbe Format,
     * das ohnehin ueber die Leitung geht.
     */
    @TypeConverter
    fun roundToJson(value: Round): String = RotaskatJson.encodeToString(Round.serializer(), value)

    @TypeConverter
    fun jsonToRound(value: String): Round = RotaskatJson.decodeFromString(Round.serializer(), value)

    @TypeConverter
    fun scoringToJson(value: ScoringConfig): String =
        RotaskatJson.encodeToString(ScoringConfig.serializer(), value)

    @TypeConverter
    fun jsonToScoring(value: String): ScoringConfig =
        RotaskatJson.decodeFromString(ScoringConfig.serializer(), value)
}
