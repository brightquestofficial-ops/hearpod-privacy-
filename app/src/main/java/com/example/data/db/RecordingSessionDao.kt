package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RecordingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<RecordingSession>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): RecordingSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSession): Long

    @Update
    suspend fun updateSession(session: RecordingSession)

    @Delete
    suspend fun deleteSession(session: RecordingSession)

    @Query("DELETE FROM recording_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)
}
