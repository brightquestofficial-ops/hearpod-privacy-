package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.RecordingSession

@Database(entities = [RecordingSession::class], version = 1, exportSchema = false)
abstract class HearPodDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao

    companion object {
        @Volatile
        private var INSTANCE: HearPodDatabase? = null

        fun getDatabase(context: Context): HearPodDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HearPodDatabase::class.java,
                    "hearpod_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
