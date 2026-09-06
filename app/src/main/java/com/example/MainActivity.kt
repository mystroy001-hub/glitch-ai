package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AppiumLogsScreen
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.VideoEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatAppiumViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val viewModel: ChatAppiumViewModel = viewModel()

                NavHost(navController = navController, startDestination = "chat") {
                    composable("chat") {
                        MainChatScreen(
                            viewModel = viewModel,
                            onNavigateToLogs = { navController.navigate("logs") },
                            onNavigateToEditor = { navController.navigate("editor") }
                        )
                    }
                    composable("logs") {
                        AppiumLogsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("editor") {
                        VideoEditorScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
