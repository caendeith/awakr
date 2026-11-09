package io.github.caendeith.awakr.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations, written by hand so the upgrade path stays explicit.
 *
 * v2 adds the sound choice, the manual order, the "skip the next one" marker and soft deletion,
 * and renames the QR mission to the broader code mission. v3 adds the mission chain.
 */
internal object AwakrMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN sound_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE alarms ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE alarms ADD COLUMN skip_next_at INTEGER")
            db.execSQL("ALTER TABLE alarms ADD COLUMN deleted_at INTEGER")
            db.execSQL("UPDATE alarms SET mission_plugin_id = 'code' WHERE mission_plugin_id = 'qr'")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN mission_chain TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /** v4 adds the ring screen background choice and the sleep sessions imported from Health Connect. */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN background_id TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sleep_sessions` (" +
                    "`id` TEXT NOT NULL, " +
                    "`start_time` INTEGER NOT NULL, " +
                    "`end_time` INTEGER NOT NULL, " +
                    "`stages` TEXT NOT NULL DEFAULT '', " +
                    "`hr_min` INTEGER, " +
                    "`hr_avg` INTEGER, " +
                    "`hr_max` INTEGER, " +
                    "`spo2_avg` REAL, " +
                    "`source` TEXT NOT NULL DEFAULT '', " +
                    "`imported_at` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_start_time` ON `sleep_sessions` (`start_time`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
