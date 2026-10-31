package io.github.caendeith.awakr.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.caendeith.awakr.data.db.dao.AlarmDao
import io.github.caendeith.awakr.data.db.dao.AlarmLogDao
import io.github.caendeith.awakr.data.db.dao.PluginStateDao
import io.github.caendeith.awakr.data.db.entity.AlarmEntity
import io.github.caendeith.awakr.data.db.entity.AlarmLogEntity
import io.github.caendeith.awakr.data.db.entity.PluginStateEntity

@Database(
    entities = [AlarmEntity::class, AlarmLogEntity::class, PluginStateEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AwakrDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmLogDao(): AlarmLogDao
    abstract fun pluginStateDao(): PluginStateDao

    companion object {
        const val NAME: String = "awakr.db"

        fun build(context: Context): AwakrDatabase =
            Room.databaseBuilder(context.applicationContext, AwakrDatabase::class.java, NAME)
                .addMigrations(*AwakrMigrations.ALL)
                .build()
    }
}
