package com.kalu.ai

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kalu.ai.ui.ChatScreen
import com.kalu.ai.ui.KaluTheme
import com.kalu.ai.ui.NotesScreen
import com.kalu.ai.ui.PermissionsScreen
import com.kalu.ai.ui.SettingsScreen

enum class Screen { CHAT, SETTINGS, NOTES, PERMISSIONS }

class MainActivity : ComponentActivity() {
    private var autoListen by mutableStateOf(false)
    private var unlocked by mutableStateOf(true)
    private var requesting = false
    private var stoppedAt = 0L
    private lateinit var prefs: Prefs

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        requesting = false
        if (r.resultCode == RESULT_OK) unlocked = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(applicationContext)
        unlocked = !prefs.appLock
        autoListen = intent?.getBooleanExtra("auto_listen", false) == true
        enableEdgeToEdge()
        setContent {
            KaluTheme {
                if (!unlocked) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔒 KALU AI locked hai")
                        Button(onClick = { requestUnlock() }) { Text("Unlock karo") }
                    }
                } else {
                    val vm: ChatViewModel = viewModel()
                    var screen by rememberSaveable { mutableStateOf(Screen.CHAT) }
                    BackHandler(enabled = screen != Screen.CHAT) { screen = Screen.CHAT }
                    when (screen) {
                        Screen.CHAT -> ChatScreen(
                            vm,
                            onNotes = { screen = Screen.NOTES },
                            onSettings = { screen = Screen.SETTINGS },
                            onPermissions = { screen = Screen.PERMISSIONS },
                            autoListen = autoListen,
                            onAutoListenHandled = { autoListen = false }
                        )
                        Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.CHAT }, onPermissions = { screen = Screen.PERMISSIONS })
                        Screen.NOTES -> NotesScreen(vm, onBack = { screen = Screen.CHAT })
                        Screen.PERMISSIONS -> PermissionsScreen(onBack = { screen = Screen.CHAT })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("auto_listen", false)) autoListen = true
    }

    override fun onStart() {
        super.onStart()
        if (prefs.appLock) {
            if (unlocked && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60_000) unlocked = false
            if (!unlocked) requestUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    /** Uses the phone's own PIN / pattern / fingerprint. If the phone has no screen lock, the app stays open. */
    private fun requestUnlock() {
        if (requesting) return
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) {
            unlocked = true
            return
        }
        val i = km.createConfirmDeviceCredentialIntent("KALU AI", "Unlock karne ke liye apna PIN, pattern ya fingerprint do")
        if (i == null) {
            unlocked = true
            return
        }
        requesting = true
        authLauncher.launch(i)
    }
}
