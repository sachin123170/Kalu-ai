@file:OptIn(ExperimentalMaterial3Api::class)

package com.kalu.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kalu.ai.ChatViewModel
import com.kalu.ai.GeminiClient
import com.kalu.ai.GeminiResult
import kotlinx.coroutines.launch

private val DEFAULT_MODELS = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "gemini-2.0-flash")

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit, onPermissions: () -> Unit) {
    val p = vm.prefs
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(!p.secure.getApiKey().isNullOrBlank()) }
    var model by remember { mutableStateOf(p.model) }
    var models by remember { mutableStateOf(DEFAULT_MODELS) }
    var menu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf(p.style) }
    var language by remember { mutableStateOf(p.language) }
    var creator by remember { mutableStateOf(p.creatorName) }
    var mood by remember { mutableStateOf(p.mood) }
    var speak by remember { mutableStateOf(p.speakReplies) }
    var wakeOn by remember { mutableStateOf(p.wakeEnabled) }
    var wakePhrase by remember { mutableStateOf(p.wakePhrase) }
    var saveHistory by remember { mutableStateOf(p.saveHistory) }
    var memory by remember { mutableStateOf(p.memoryTurns.toFloat()) }
    var confirmWipe by remember { mutableStateOf(false) }

    fun currentKey(): String = keyInput.trim().ifBlank { p.secure.getApiKey().orEmpty() }

    fun testConnection() {
        val key = currentKey()
        if (key.isBlank()) {
            status = "Pehle API key daalo."
            return
        }
        busy = true
        status = "Test ho raha hai..."
        scope.launch {
            val r = GeminiClient.generate(key, model, "Reply with the single word OK.", listOf("user" to "ping"))
            status = when (r) {
                is GeminiResult.Ok -> "✅ Connected! Model ne jawab diya."
                is GeminiResult.Err -> "❌ ${r.message}"
            }
            busy = false
        }
    }

    fun fetchModels() {
        val key = currentKey()
        if (key.isBlank()) {
            status = "Pehle API key daalo."
            return
        }
        busy = true
        status = "Models la raha hoon..."
        scope.launch {
            val r = GeminiClient.listModels(key)
            if (r.error != null) status = "❌ ${r.error}"
            else {
                models = r.models.ifEmpty { DEFAULT_MODELS }
                status = "✅ ${r.models.size} models mile. ▼ se chuno."
            }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        SectionTitle("Gemini dimaag")
        Text(
            if (hasKey) "🔒 API key secure storage (Android Keystore) mein saved hai." else "API key abhi saved nahi hai. aistudio.google.com se apni key banao.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = keyInput.isNotBlank(), onClick = {
                p.secure.putApiKey(keyInput)
                keyInput = ""
                hasKey = true
                status = "Key save ho gayi."
            }) { Text("Save key") }
            OutlinedButton(enabled = hasKey, onClick = {
                p.secure.putApiKey("")
                hasKey = false
                status = "Key delete ho gayi."
            }) { Text("Delete key") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; p.model = it },
                modifier = Modifier.weight(1f),
                label = { Text("Model") },
                singleLine = true
            )
            Box {
                TextButton(onClick = { menu = true }) { Text("▼") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    models.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = {
                            model = it; p.model = it; menu = false
                        })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { fetchModels() }) { Text("Models laao") }
            Button(enabled = !busy, onClick = { testConnection() }) { Text("Test connection") }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Note: Ye app tumhari apni key seedha Google ko bhejta hai. Production app mein key kabhi app ke andar nahi rakhte, apna secure backend use karte hain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionTitle("Style aur language")
        OutlinedTextField(
            value = creator,
            onValueChange = { creator = it; p.creatorName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tumhara naam (KALU ko batao kisne banaya)") },
            singleLine = true
        )
        ChipRow(listOf("friendly" to "Friendly", "short" to "Short", "detailed" to "Detailed"), style) { style = it; p.style = it }
        ChipRow(listOf("hinglish" to "Hinglish", "hindi" to "Hindi", "english" to "English"), language) { language = it; p.language = it }
        Text("Dost ka mood", style = MaterialTheme.typography.bodyMedium)
        ChipRow(
            listOf("balanced" to "Balanced", "caring" to "Caring", "funny" to "Funny", "serious" to "Serious", "motivational" to "Motivational"),
            mood
        ) { mood = it; p.mood = it }

        SectionTitle("Voice")
        SwitchRow("Jawab bolke sunao (TTS)", speak) { speak = it; p.speakReplies = it }
        SwitchRow("Wake phrase (sirf Continuous mode mein)", wakeOn) { wakeOn = it; p.wakeEnabled = it }
        if (wakeOn) {
            OutlinedTextField(
                value = wakePhrase,
                onValueChange = { wakePhrase = it; p.wakePhrase = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Wake phrase") },
                singleLine = true
            )
        }
        Text(
            "Limit: wake phrase tabhi chalta hai jab app khula ho aur Continuous mode ON ho. Background mein hamesha sunne wala hotword Android par is app mein nahi hai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionTitle("Privacy")
        SwitchRow("Chat history is phone par save karo", saveHistory) { saveHistory = it; p.saveHistory = it }
        Text("Gemini ko pichhli baatein (memory): ${memory.toInt()} exchanges", style = MaterialTheme.typography.bodyMedium)
        Slider(value = memory, onValueChange = { memory = it }, onValueChangeFinished = { p.memoryTurns = memory.toInt() }, valueRange = 0f..20f, steps = 19)
        OutlinedButton(onClick = onPermissions) { Text("Permissions guide") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(onClick = { vm.clearHistory(); status = "Chat history clear." }) { Text("History clear") }
            OutlinedButton(onClick = { vm.clearNotes(); status = "Notes clear." }) { Text("Notes clear") }
        }
        Button(onClick = { confirmWipe = true }) { Text("Saara data delete karo") }
        Text(
            "KALU mic ya screen ko chhupke record nahi karta. Mic sirf tab chalta hai jab status bar mein dikhe, aur app hide hote hi band ho jata hai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmWipe) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Saara data delete?") },
            text = { Text("Chat, notes, settings aur API key sab delete ho jayenge.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllData()
                    confirmWipe = false
                    onBack()
                }) { Text("Haan, delete") }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(label) })
        }
    }
}
