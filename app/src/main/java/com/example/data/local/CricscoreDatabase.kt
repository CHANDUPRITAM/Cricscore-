package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TeamEntity::class,
        PlayerEntity::class,
        MatchEntity::class,
        BallEventEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class CricscoreDatabase : RoomDatabase() {
    abstract fun cricscoreDao(): CricscoreDao

    companion object {
        @Volatile
        private var INSTANCE: CricscoreDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ball_events ADD COLUMN penaltyRuns INTEGER NOT NULL DEFAULT -1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ball_events ADD COLUMN shotX REAL")
                db.execSQL("ALTER TABLE ball_events ADD COLUMN shotY REAL")
                db.execSQL("ALTER TABLE ball_events ADD COLUMN shotZone TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE teams ADD COLUMN photoBase64 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE players ADD COLUMN photoBase64 TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): CricscoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CricscoreDatabase::class.java,
                    "cricscore_offline.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
