package com.foldersync.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.foldersync.ui.navigation.AppNavGraph
import com.foldersync.ui.theme.FolderSyncTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FolderSyncTheme {
                AppNavGraph()
            }
        }
    }
}
