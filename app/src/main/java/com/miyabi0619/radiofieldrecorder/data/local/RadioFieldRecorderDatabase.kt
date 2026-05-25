package com.miyabi0619.radiofieldrecorder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        ProbeTargetEntity::class,
        WifiSampleEntity::class,
        ProbeSampleEntity::class,
        EventMarkerEntity::class,
        DdsParticipantSampleEntity::class,
        DdsEndpointSampleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class RadioFieldRecorderDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun probeTargetDao(): ProbeTargetDao
    abstract fun wifiSampleDao(): WifiSampleDao
    abstract fun probeSampleDao(): ProbeSampleDao
    abstract fun eventMarkerDao(): EventMarkerDao
    abstract fun ddsParticipantSampleDao(): DdsParticipantSampleDao
    abstract fun ddsEndpointSampleDao(): DdsEndpointSampleDao

    companion object {
        const val DatabaseName = "radio_field_recorder.db"

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dds_participant_samples` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `participantGuid` TEXT NOT NULL,
                        `participantName` TEXT,
                        `status` TEXT NOT NULL,
                        `firstSeenAt` INTEGER NOT NULL,
                        `lastSeenAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dds_participant_samples_sessionId` ON `dds_participant_samples` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dds_participant_samples_sessionId_timestamp` ON `dds_participant_samples` (`sessionId`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dds_participant_samples_sessionId_participantGuid` ON `dds_participant_samples` (`sessionId`, `participantGuid`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dds_endpoint_samples` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `endpointGuid` TEXT NOT NULL,
                        `participantGuid` TEXT,
                        `topicName` TEXT NOT NULL,
                        `typeName` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `firstSeenAt` INTEGER NOT NULL,
                        `lastSeenAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dds_endpoint_samples_sessionId` ON `dds_endpoint_samples` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dds_endpoint_samples_sessionId_timestamp` ON `dds_endpoint_samples` (`sessionId`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dds_endpoint_samples_sessionId_endpointGuid` ON `dds_endpoint_samples` (`sessionId`, `endpointGuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dds_endpoint_samples_sessionId_topicName` ON `dds_endpoint_samples` (`sessionId`, `topicName`)")
            }
        }

        fun create(context: Context): RadioFieldRecorderDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                RadioFieldRecorderDatabase::class.java,
                DatabaseName,
            )
                .addMigrations(Migration1To2)
                .build()
    }
}
