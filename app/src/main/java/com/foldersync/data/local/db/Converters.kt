package com.foldersync.data.local.db

import androidx.room.TypeConverter
import com.foldersync.domain.model.SyncAction
import com.foldersync.domain.model.SyncStatus

class Converters {

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromSyncAction(action: SyncAction): String = action.name

    @TypeConverter
    fun toSyncAction(value: String): SyncAction = SyncAction.valueOf(value)
}
