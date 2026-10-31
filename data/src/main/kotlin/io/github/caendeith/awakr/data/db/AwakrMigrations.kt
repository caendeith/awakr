package io.github.caendeith.awakr.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations, written by hand so the upgrade path stays explicit.
 *
 * v2 adds the sound choice, the manual order, the "skip the next one" marker and soft deletion,
 * and renames the QR mission to the broader code mission.
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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
