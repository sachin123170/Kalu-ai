# KALU AI v1.0.0

Android personal AI assistant: friendly male best-friend persona in Hinglish. It is an AI app and never pretends to be human.
Kotlin + Jetpack Compose + Material 3. Package: `com.kalu.ai`.

> **Honesty note:** the author could not compile or run this on a device. Everything below is implemented in code, but treat each feature as "needs your first test" until you confirm it. See "Test checklist".

## Versions
JDK 17 | Gradle 8.7 | AGP 8.5.2 | Kotlin 1.9.24 | Compose compiler 1.5.14 | Compose BOM 2024.06.00 | Material3 1.2.x | activity-compose 1.9.0 | lifecycle 2.8.3 | core-ktx 1.13.1 | coroutines 1.8.1 | compileSdk/targetSdk 34 | minSdk 26

## File tree
```
kalu-ai/
  settings.gradle.kts  build.gradle.kts  gradle.properties  termux-build.sh  README.md
  .github/workflows/build.yml
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/res/ (values/, drawable/, mipmap-anydpi-v26/, xml/accessibility_service_config.xml)
  app/src/main/java/com/kalu/ai/
    MainActivity.kt        navigation (chat / settings / notes / permissions)
    ChatViewModel.kt       brain: routes commands, calls Gemini, voice, confirmations
    CommandRouter.kt       offline Hindi/Hinglish/English command parser
    GeminiClient.kt        REST client, model list, error handling
    Prefs.kt               settings + Keystore-encrypted API key
    Persona.kt             KALU persona + modes (Chat/Code/Study/Translate/Summarize)
    VoiceController.kt     speech-to-text, text-to-speech, continuous mode
    SystemActions.kt       app launcher, YouTube intent, alarm/timer intents
    KaluAccess.kt          screen-control state, Stop notification
    KaluAccessibilityService.kt  back/home/recents/scroll/click/read screen
    NotesStore.kt  ChatStore.kt  ChatMessage.kt  OfflineBrain.kt
    ui/ ChatScreen, SettingsScreen, NotesScreen, PermissionsScreen, AiOrb, Theme
```

## Build option A: Termux (what you asked for)
Native Android builds in Termux are not officially supported. `termux-build.sh` tries: Termux's own ARM `aapt2`, JDK 17, Gradle 8.7, SDK platform 34. It needs about 4 GB RAM and 3 GB storage.
```
pkg update && pkg install -y git
termux-setup-storage            # allow storage, so APK can be copied to Downloads
# copy/unzip the project so it is at ~/kalu-ai , then:
cd ~/kalu-ai
bash termux-build.sh
```
Output: `app/build/outputs/apk/debug/app-debug.apk`, copied to `Downloads/KALU-AI.apk`.
Install: open the APK from the Files app, allow "Install unknown apps", Install. (Or `termux-open app/build/outputs/apk/debug/app-debug.apk`.)

## Build option B: GitHub Actions (most reliable from a phone)
```
cd ~/kalu-ai
git init && git add . && git commit -m "KALU AI"
git branch -M main
git remote add origin https://github.com/YOUR_USER/kalu-ai.git
git push -u origin main
```
GitHub > Actions > "Build debug APK" > download artifact `kalu-ai-debug-apk` > extract `app-debug.apk` > install.

## First run
1. Settings > paste your Gemini API key from aistudio.google.com > Save key.
2. Tap "Models laao" to list models your key can use, pick one, tap "Test connection".
3. Permissions guide: allow Mic and Notifications. Accessibility is optional.

## What you can say
- `open WhatsApp`, `instagram kholo`, `find app calculator`
- `play arijit singh songs`, `youtube par lofi chalao` (opens YouTube search; you tap a result. Ads/Premium are not bypassed)
- `7:30 baje alarm`, `shaam 6 baje alarm`, `10 minute ka timer` (opens Clock app pre-filled; you confirm)
- `note: doodh lana hai`, `mere notes`
- Screen control (needs Accessibility + switch ON): `scroll down`, `back`, `home`, `recents`, `click Search`, `screen padho`
- Anything else goes to Gemini. Modes: Code (copy/export buttons on code blocks), Study, Translate, Summarize.

## Screen control: how it really works
KALU's own screen is the active window while you type, so screen commands wait 4 seconds: say the command, then switch to the target app. If KALU is still in front, it refuses and says so. A notification with a Stop button and the in-app banner show while control is ON. Clicking buttons named like send/pay/delete/buy asks you to confirm first. Password fields are never read.

## Known limits (not faked)
- YouTube: opens search results; cannot auto-play the first result without the YouTube Data API.
- Wake phrase: only while the app is open and Continuous mode is ON. No always-on background hotword.
- Alarms/timers: Android only lets apps pre-fill the Clock app; you confirm there.
- Generated code is never executed by the app. Export saves via Android's file picker (`.txt` name; rename if needed).
- Speech recognition and Hindi voices depend on the phone's Google Speech Services / TTS voice data.
- Sending messages, deleting files, purchases: not implemented, so nothing can happen without you doing it yourself.
- Gemini model names change over time; use "Models laao" if a model returns 404.
- Production note: use a backend to hold the Gemini key, not a key stored in a client app.

## Test checklist
1. Chat without a key: "hello", "time kya hai" work offline.
2. Save key, Test connection shows Connected; ask a question.
3. Mic: tap 🎤, speak Hindi; text appears and sends.
4. "open youtube", "play lofi on youtube".
5. "7:30 baje alarm" opens Clock.
6. Enable Accessibility, Screen control ON, open Chrome, say "scroll down" within 4 s.
7. Code mode: ask for a Python function, Copy and Export.
8. Settings: clear history, delete key, wipe all data.

## Troubleshooting
- `SDK location not found`: create `local.properties` with `sdk.dir=/path/to/sdk`.
- `Killed` during build: not enough RAM. Close apps, or use option B.
- `aapt2 ... Syntax error: "(" unexpected` / `Exec format error`: Gradle downloaded the x86 aapt2. Check `~/.gradle/gradle.properties` has `android.aapt2FromMavenOverride`.
- `Could not load native-platform`: ensure `systemProp.org.gradle.native=false` is in `~/.gradle/gradle.properties`.
- `Unsupported class file major version`: wrong JDK, use 17.
- Kotlin/Compose version mismatch: keep Kotlin 1.9.24 with compiler extension 1.5.14.
- Mic button says no recognition service: install/enable "Speech Recognition & Synthesis from Google".
- Gemini 400/403: key wrong or API not enabled. 404: model name. 429: quota.
- "App not installed": uninstall older KALU AI first; make sure you installed the .apk, not the zip.

## Floating mic bubble (v1.1)
Chat screen > chip "🫧 Bubble". Allow "Display over other apps" and Mic, then tap the chip again. A bubble floats over other apps; tap it, speak, and KALU acts (open apps, YouTube, alarm, notes, screen control, or asks Gemini and speaks the answer in 1-3 sentences). It listens only after you tap; the bubble turns green while listening. Stop from the notification. Sensitive screen clicks (send/pay/delete...) are refused from the bubble and must be confirmed inside the app. Bubble chats are not saved to history and have no memory.

## Messaging (v1.3)
- `Rahul ko sms bhejo: kal milte hain` : looks up the contact, asks you to confirm, then sends the SMS (needs Contacts + SMS permission).
- `Rahul ko whatsapp karo: kal milte hain` : confirms, opens the WhatsApp chat with the text filled in, and if Screen control is ON presses Send after 4 seconds. Otherwise you press Send. A 10-digit number without country code is assumed to be Indian (+91).
- Any app (WhatsApp, Instagram...): open the chat, then `type: kal milte hain` (types into the text box) and `send karo` (presses Send; always confirmed). From the bubble, confirmation is by voice: tap the bubble and say "haan" or "nahi".
- Nothing is ever sent without your confirmation. Apps change their screens, so Send may not be found; KALU then says so.

## Hindi voice (v1.4)
Speech recognition often returns Devanagari. `Translit.kt` maps common command words (को, भेजो, एसएमएस, व्हाट्सएप, खोलो...) to Latin so voice commands match, and transliterates names so contacts saved in English are still found. The message body stays in the script you spoke.

## Feelings-like behaviour (v1.5)
- Settings > "Dost ka mood": Balanced, Caring, Funny, Serious, Motivational. Changes how KALU talks.
- KALU guesses how your message feels (happy, sad, tired, stressed, angry) from keywords, in the chat and in the bubble. The guess is used only for that reply: it is NOT saved, and it is sent to Gemini only as a one-line hint.
- The orb changes colour with the guess and the spoken reply gets a slower/softer or brighter tone. A small emoji appears next to "Tumhara AI dost".
- KALU says honestly that it is an AI without human feelings if asked, and responds with care and points to trusted people or helplines if you seem in serious distress.
