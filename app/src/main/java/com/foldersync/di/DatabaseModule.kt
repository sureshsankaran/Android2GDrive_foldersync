package com.foldersync.di

import android.content.Context
import androidx.room.Room
import com.foldersync.data.local.db.AppDatabase
import com.foldersync.data.local.db.SyncFileDao
import com.foldersync.data.local.db.SyncHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSyncFileDao(database: AppDatabase): SyncFileDao {
        return database.syncFileDao()
    }

    @Provides
    @Singleton
    fun provideSyncHistoryDao(database: AppDatabase): SyncHistoryDao {
        return database.syncHistoryDao()
    }
}
