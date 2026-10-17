package com.kalu.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kalu.ai.ui.ChatScreen
import com.kalu.ai.ui.KaluTheme
import com.kalu.ai.ui.NotesScreen
import com.kalu.ai.ui.PermissionsScreen
import com.kalu.ai.ui.SettingsScreen

enum class Screen { CHAT, SETTINGS, NOTES, PERMISSIONS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaluTheme {
                val vm: ChatViewModel = viewModel()
                var screen by rememberSaveable { mutableStateOf(Screen.CHAT) }
                BackHandler(enabled = screen != Screen.CHAT) { screen = Screen.CHAT }
                when (screen) {
                    Screen.CHAT -> ChatScreen(
                        vm,
                        onNotes = { screen = Screen.NOTES },
                        onSettings = { screen = Screen.SETTINGS },
                        onPermissions = { screen = Screen.PERMISSIONS }
                    )
                    Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.CHAT }, onPermissions = { screen = Screen.PERMISSIONS })
                    Screen.NOTES -> NotesScreen(vm, onBack = { screen = Screen.CHAT })
                    Screen.PERMISSIONS -> PermissionsScreen(onBack = { screen = Screen.CHAT })
                }
            }
        }
    }
}
