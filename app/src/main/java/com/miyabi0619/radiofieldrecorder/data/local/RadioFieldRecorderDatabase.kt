package com.miyabi0619.radiofieldrecorder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        ProbeTargetEntity::class,
        WifiSampleEntity::class,
        ProbeSampleEntity::class,
        EventMarkerEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RadioFieldRecorderDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun probeTargetDao(): ProbeTargetDao
    abstract fun wifiSampleDao(): WifiSampleDao
    abstract fun probeSampleDao(): ProbeSampleDao
    abstract fun eventMarkerDao(): EventMarkerDao

    companion object {
        const val DatabaseName = "radio_field_recorder.db"

        fun create(context: Context): RadioFieldRecorderDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                RadioFieldRecorderDatabase::class.java,
                DatabaseName,
            ).build()
    }
}
