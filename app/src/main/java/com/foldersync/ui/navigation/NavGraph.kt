package com.foldersync.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.foldersync.ui.screens.ConflictResolutionScreen
import com.foldersync.ui.screens.DriveFolderSelectScreen
import com.foldersync.ui.screens.FolderSelectScreen
import com.foldersync.ui.screens.HomeScreen
import com.foldersync.ui.screens.HomeViewModel
import com.foldersync.ui.screens.SettingsScreen
import com.foldersync.ui.screens.SettingsViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object FolderSelect : Screen("folder_select")
    data object DriveFolderSelect : Screen("drive_folder_select")
    data object ConflictResolution : Screen("conflict_resolution")
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onSelectFolders = { navController.navigate(Screen.FolderSelect.route) },
                onDriveFolderSelect = { navController.navigate(Screen.DriveFolderSelect.route) },
                onOpenConflicts = { navController.navigate(Screen.ConflictResolution.route) }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.FolderSelect.route) {
            FolderSelectScreen(
                onFolderSelected = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DriveFolderSelect.route) {
            DriveFolderSelectScreen(
                onFolderSelected = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ConflictResolution.route) {
            ConflictResolutionScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
