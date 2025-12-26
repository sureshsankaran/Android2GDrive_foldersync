package com.foldersync.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.foldersync.data.local.entity.SyncFileEntity
import com.foldersync.data.local.entity.SyncHistoryEntity

@Database(
    entities = [
        SyncFileEntity::class,
        SyncHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun syncFileDao(): SyncFileDao
    abstract fun syncHistoryDao(): SyncHistoryDao

    companion object {
        const val DATABASE_NAME = "foldersync_db"
    }
}
