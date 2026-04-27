package com.miyabi0619.radiofieldrecorder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query(
        """
        UPDATE sessions
        SET endedAt = :endedAt, status = :status
        WHERE id = :sessionId
        """,
    )
    suspend fun stopSession(
        sessionId: Long,
        endedAt: Long,
        status: SessionStatus = SessionStatus.STOPPED,
    )

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?
}

@Dao
interface ProbeTargetDao {
    @Insert
    suspend fun insert(target: ProbeTargetEntity): Long

    @Insert
    suspend fun insertAll(targets: List<ProbeTargetEntity>): List<Long>

    @Query("SELECT * FROM probe_targets WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getTargetsForSession(sessionId: Long): List<ProbeTargetEntity>
}

@Dao
interface WifiSampleDao {
    @Insert
    suspend fun insert(sample: WifiSampleEntity): Long

    @Query("SELECT * FROM wifi_samples WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getSamplesForSession(sessionId: Long): List<WifiSampleEntity>
}

@Dao
interface ProbeSampleDao {
    @Insert
    suspend fun insert(sample: ProbeSampleEntity): Long

    @Query("SELECT * FROM probe_samples WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getSamplesForSession(sessionId: Long): List<ProbeSampleEntity>
}

@Dao
interface EventMarkerDao {
    @Insert
    suspend fun insert(event: EventMarkerEntity): Long

    @Query("SELECT * FROM event_markers WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getEventsForSession(sessionId: Long): List<EventMarkerEntity>
}
