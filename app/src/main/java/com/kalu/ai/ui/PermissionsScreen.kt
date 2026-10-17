package com.kalu.ai.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kalu.ai.KaluAccess

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }
    val connected by KaluAccess.serviceConnected.collectAsState()

    val micOk = remember(tick) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val notifNeeded = Build.VERSION.SDK_INT >= 33
    val notifOk = remember(tick) {
        !notifNeeded || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val a11yEnabled = remember(tick, connected) { KaluAccess.isEnabledInSettings(ctx) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Permissions guide", style = MaterialTheme.typography.titleLarge)
        }

        PermCard(
            "🎤 Microphone",
            "Sirf voice commands ke liye. Mic tabhi chalta hai jab tum mic dabao ya Continuous mode ON karo, aur status dikhta rehta hai. App hide hote hi band.",
            if (micOk) "✅ Mili hui hai" else "❌ Abhi nahi mili",
            if (micOk) null else "Allow karo"
        ) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

        PermCard(
            "🔔 Notifications",
            "Screen control ON hone par 'Stop' button wali notification dikhane ke liye.",
            if (!notifNeeded) "✅ Is Android version par zaroorat nahi" else if (notifOk) "✅ Mili hui hai" else "❌ Abhi nahi mili",
            if (notifOk) null else "Allow karo"
        ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

        PermCard(
            "🖐 Accessibility (screen control)",
            "Optional. Isse KALU tumhare bolne par back, home, scroll, tap kar sakta hai. Ise Android Settings mein tumhe khud ON karna padta hai: Settings, Accessibility, Installed apps, KALU AI. Phir app mein 'Screen control' switch bhi ON karna padta hai. Sensitive buttons (send, pay, delete...) se pehle confirmation poochta hai. Password fields kabhi nahi padhta.",
            if (connected) "✅ Service chal rahi hai" else if (a11yEnabled) "⏳ Enabled, connect ho rahi hai" else "⚪ OFF",
            "Accessibility settings kholo"
        ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        PermCard(
            "🫧 Floating mic bubble",
            "Dusre apps ke upar ek chhota mic bubble dikhta hai. Bubble par tap karke bolo, KALU sunega aur kaam karega. Mic tabhi chalta hai jab tum bubble tap karo; sunte waqt bubble hara ho jata hai. 'Display over other apps' permission lagti hai. Band karne ke liye notification mein Stop dabao.",
            if (overlayOk) "✅ Permission mili hui hai" else "❌ Abhi nahi mili",
            if (overlayOk) null else "Permission do"
        ) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        PermCard(
            "⏰ Alarm / Timer",
            "Koi popup permission nahi. KALU Clock app ko pre-filled alarm ke saath kholta hai, confirm tum karte ho.",
            "ℹ️ Automatic", null
        ) {}

        PermCard(
            "🌐 Internet",
            "Gemini aur voice recognition ke liye. Sirf wahi text jaata hai jo tum chat mein bhejte ho (plus memory ke exchanges).",
            "ℹ️ Automatic", null
        ) {}

        Text(
            "Jo KALU nahi karta: chhupke mic/screen record, passwords padhna, ads ya paywall bypass, tumhari marzi ke bina message bhejna ya kuch kharidna.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermCard(title: String, desc: String, status: String, action: String?, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodyMedium)
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (action != null) Button(onClick = onAction) { Text(action) }
        }
    }
}
