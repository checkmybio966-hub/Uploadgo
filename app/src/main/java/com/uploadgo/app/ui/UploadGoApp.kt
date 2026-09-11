package com.uploadgo.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uploadgo.app.R
import com.uploadgo.app.ui.screens.HistoryScreen
import com.uploadgo.app.ui.screens.HomeScreen
import com.uploadgo.app.ui.screens.ImageViewerScreen
import com.uploadgo.app.ui.screens.QueueScreen
import com.uploadgo.app.ui.screens.SettingsScreen
import com.uploadgo.app.ui.screens.VideoViewerScreen
import com.uploadgo.app.ui.screens.ZipPreviewScreen

object Routes {
    const val HOME = "home"
    const val QUEUE = "queue"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val IMAGE = "image/{itemId}"
    const val VIDEO = "video/{itemId}"
    const val ZIP = "zip/{zipId}"

    fun image(itemId: String) = "image/$itemId"
    fun video(itemId: String) = "video/$itemId"
    fun zip(zipId: String) = "zip/$zipId"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun UploadGoApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = listOf(
        BottomDestination(Routes.HOME, stringResource(R.string.tab_home), Icons.Rounded.Home),
        BottomDestination(Routes.QUEUE, stringResource(R.string.tab_queue), Icons.Rounded.Checklist),
        BottomDestination(Routes.HISTORY, stringResource(R.string.tab_history), Icons.Rounded.History),
    )
    val showBottomBar = currentRoute in destinations.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    navController.navigate(dest.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenImage = { navController.navigate(Routes.image(it)) },
                    onOpenVideo = { navController.navigate(Routes.video(it)) },
                    onOpenZip = { navController.navigate(Routes.zip(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.QUEUE) {
                QueueScreen(
                    onOpenHome = { navController.navigate(Routes.HOME) },
                    onOpenZip = { navController.navigate(Routes.zip(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.IMAGE,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("itemId")
                if (id != null) {
                    ImageViewerScreen(itemId = id, onBack = { navController.popBackStack() })
                }
            }
            composable(
                route = Routes.VIDEO,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("itemId")
                if (id != null) {
                    VideoViewerScreen(itemId = id, onBack = { navController.popBackStack() })
                }
            }
            composable(
                route = Routes.ZIP,
                arguments = listOf(navArgument("zipId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("zipId")
                if (id != null) {
                    ZipPreviewScreen(zipId = id, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
