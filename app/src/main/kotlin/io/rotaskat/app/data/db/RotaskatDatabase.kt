package io.rotaskat.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [
        ClubEntity::class,
        PlayerEntity::class,
        SessionEntity::class,
        SessionSeatEntity::class,
        RoundEntity::class,
        SyncStateEntity::class,
    ],
    version = RotaskatDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(RotaskatConverters::class)
abstract class RotaskatDatabase : RoomDatabase() {

    abstract fun clubDao(): ClubDao

    abstract fun playerDao(): PlayerDao

    abstract fun sessionDao(): SessionDao

    abstract fun sessionSeatDao(): SessionSeatDao

    abstract fun roundDao(): RoundDao

    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val VERSION = 1

        const val FILE_NAME = "rotaskat.db"

        /**
         * Die Migrationskette, luecken- und ueberschneidungsfrei von 1 bis
         * [VERSION]. Solange sie leer ist, gibt es nur die Version 1.
         *
         * Es gibt bewusst KEIN `fallbackToDestructiveMigration`. Ein Abend, der
         * noch nicht synchronisiert ist, waere danach weg, und genau das ist der
         * Zustand, in dem die App den groessten Teil ihrer Laufzeit verbringt:
         * die Kneipe hat kein Netz. Lieber ein Absturz beim Update als eine
         * stillschweigend geleerte Datenbank.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun open(context: Context): RotaskatDatabase =
            Room.databaseBuilder(context.applicationContext, RotaskatDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        /** Nur fuer Tests. */
        fun inMemory(context: Context): RotaskatDatabase =
            Room.inMemoryDatabaseBuilder(context, RotaskatDatabase::class.java)
                .addMigrations(*MIGRATIONS)
                .allowMainThreadQueries()
                .build()
    }
}
