@file:OptIn(ExperimentalMaterial3Api::class)

package com.kalu.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kalu.ai.ChatMessage
import com.kalu.ai.ChatViewModel
import com.kalu.ai.KaluAccess
import com.kalu.ai.Mode
import com.kalu.ai.VoiceState
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onNotes: () -> Unit,
    onSettings: () -> Unit,
    onPermissions: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var afterMicPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val armed by KaluAccess.armed.collectAsState()
    val connected by KaluAccess.serviceConnected.collectAsState()
    val messages = vm.messages

    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.onAppBackground() }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterMicPermission?.invoke() else vm.notice = "Mic permission nahi mili."
        afterMicPermission = null
    }
    fun withMic(action: () -> Unit) {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (ok) action() else {
            afterMicPermission = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(exportText.toByteArray(Charsets.UTF_8)) }
                vm.notice = "File save ho gayi."
            } catch (e: Exception) {
                vm.notice = "File save nahi ho payi."
            }
        }
    }

    LaunchedEffect(messages.size, vm.typing) {
        val count = messages.size + if (vm.typing) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LaunchedEffect(vm.notice) {
        if (vm.notice != null) {
            delay(4000)
            vm.notice = null
        }
    }

    fun submit() {
        if (input.isNotBlank()) {
            vm.send(input)
            input = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiOrb(size = 44.dp, thinking = vm.typing || vm.voiceState != VoiceState.IDLE)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text("KALU AI", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        vm.typing -> "soch raha hoon..."
                        vm.voiceState == VoiceState.LISTENING -> "sun raha hoon..."
                        vm.voiceState == VoiceState.SPEAKING -> "bol raha hoon..."
                        else -> "Tumhara AI dost"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onNotes) { Text("📝") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
            IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.Delete, "Clear history") }
        }

        // Notice banner
        vm.notice?.let {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clickable { vm.notice = null },
                shape = RoundedCornerShape(10.dp)
            ) { Text(it, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) }
        }

        // Screen control status
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    !connected -> "Screen control: Accessibility OFF"
                    armed -> "🟢 Screen control ON"
                    else -> "⚪ Screen control OFF"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (armed) Color(0xFF6FE38A) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!connected) {
                TextButton(onClick = onPermissions) { Text("Setup") }
            } else {
                if (armed) TextButton(onClick = { vm.stopControl() }) { Text("⏹ Stop") }
                Switch(checked = armed, onCheckedChange = { vm.setArmed(it) })
            }
        }

        // Voice status + mode chips
        if (vm.voiceState != VoiceState.IDLE || vm.continuous) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (vm.voiceState == VoiceState.SPEAKING) "🔊 Bol raha hoon" else if (vm.voiceState == VoiceState.LISTENING) "🎙 Mic ON: sun raha hoon" else "🎙 Continuous mode ON",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.stopVoice() }) { Text("⏹ Stop voice") }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = vm.continuous,
                onClick = { if (vm.continuous) vm.toggleContinuous() else withMic { vm.toggleContinuous() } },
                label = { Text("🎙 Continuous") }
            )
            Mode.values().forEach { m ->
                FilterChip(selected = vm.mode == m, onClick = { vm.mode = m }, label = { Text(m.label) })
            }
        }

        // Messages
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Bubble(msg) { code ->
                    exportText = code
                    exportLauncher.launch("kalu_code.txt")
                }
            }
            if (vm.typing) item(key = "typing") { TypingIndicator() }
        }

        // Input
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("KALU se kuch bhi bolo...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            TextButton(onClick = { withMic { vm.micTapped() } }) {
                Text(if (vm.voiceState == VoiceState.LISTENING) "⏹" else "🎤", fontSize = 22.sp)
            }
            IconButton(
                onClick = { submit() },
                enabled = input.isNotBlank() && !vm.typing,
                modifier = Modifier.size(46.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("History clear karein?") },
            text = { Text("Saari baatein is phone se delete ho jayengi.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Haan, clear karo") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }

    vm.pending?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.dismissPending() },
            title = { Text("Pakka?") },
            text = { Text(p.message) },
            confirmButton = { TextButton(onClick = { vm.confirmPending() }) { Text("Haan, karo") } },
            dismissButton = { TextButton(onClick = { vm.dismissPending() }) { Text("Nahi") } }
        )
    }
}

private sealed class Block {
    data class Txt(val s: String) : Block()
    data class Code(val lang: String, val code: String) : Block()
}

private fun parseBlocks(text: String): List<Block> {
    val out = mutableListOf<Block>()
    text.split("```").forEachIndexed { i, p ->
        if (i % 2 == 0) {
            if (p.isNotBlank()) out.add(Block.Txt(p.trim()))
        } else {
            val nl = p.indexOf('\n')
            val first = if (nl >= 0) p.substring(0, nl).trim() else ""
            val isLang = nl >= 0 && first.length <= 15 && !first.contains(' ')
            val code = (if (isLang) p.substring(nl + 1) else p).trimEnd()
            out.add(Block.Code(if (isLang) first else "", code))
        }
    }
    return out
}

@Composable
private fun Bubble(msg: ChatMessage, onExport: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (msg.fromUser) scheme.primaryContainer else scheme.surfaceVariant
    val fg = if (msg.fromUser) scheme.onPrimaryContainer else scheme.onSurface
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bg,
            contentColor = fg,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (msg.fromUser) 18.dp else 4.dp,
                bottomEnd = if (msg.fromUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (msg.fromUser) {
                    SelectionContainer { Text(msg.text, style = MaterialTheme.typography.bodyLarge) }
                } else {
                    parseBlocks(msg.text).forEach { b ->
                        when (b) {
                            is Block.Txt -> SelectionContainer {
                                Text(b.s.replace("**", ""), style = MaterialTheme.typography.bodyLarge)
                            }
                            is Block.Code -> Surface(color = Color(0xFF070B14), shape = RoundedCornerShape(10.dp)) {
                                Column {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            b.lang.ifEmpty { "code" },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = scheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(b.code)) }) { Text("Copy") }
                                        TextButton(onClick = { onExport(b.code) }) { Text("Export") }
                                    }
                                    Box(
                                        Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(b.code, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFD6E4FF))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until 3) {
                    val a by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(i * 160)
                        ),
                        label = "dot$i"
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(a)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}
