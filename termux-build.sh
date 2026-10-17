#!/data/data/com.termux/files/usr/bin/bash
# KALU AI: Termux build helper.  UNTESTED on real devices by the author.
# Usage:  cd ~/kalu-ai && bash termux-build.sh
# First run downloads ~1 GB (JDK, Android SDK platform, Gradle, dependencies). Use Wi-Fi.
set -e

say() { echo -e "\n\033[1;34m== $* ==\033[0m"; }
die() { echo -e "\033[1;31mERROR:\033[0m $*"; exit 1; }

[ -n "$PREFIX" ] || die "Ye script Termux ke andar chalao."
[ -f settings.gradle.kts ] || die "Project folder (kalu-ai) ke andar chalao: cd ~/kalu-ai"

say "1/7 Phone check"
echo "CPU: $(uname -m)"
MEM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
echo "RAM: $((MEM_KB/1024)) MB"
if [ "$MEM_KB" -lt 3500000 ]; then
  echo "WARNING: 4 GB se kam RAM. Build beech mein 'Killed' ho sakta hai. GitHub Actions wala tareeka (README) behtar hai."
fi

say "2/7 Packages"
pkg update -y
pkg install -y openjdk-17 wget unzip git
pkg install -y aapt2 || die "Termux ka 'aapt2' package install nahi hua. GitHub Actions wala tareeka use karo."
command -v aapt2 >/dev/null || die "aapt2 nahi mila."
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
[ -x "$JAVA_HOME/bin/java" ] || die "JDK 17 nahi mila at $JAVA_HOME"
echo "Java: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "aapt2: $(aapt2 version 2>&1 | head -1)"

say "3/7 Android SDK (platform 34)"
SDK="$HOME/android-sdk"
mkdir -p "$SDK/cmdline-tools"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  wget -q --show-progress https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O "$HOME/cmdtools.zip"
  unzip -q "$HOME/cmdtools.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f "$HOME/cmdtools.zip"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-34" "build-tools;34.0.0" \
  || die "sdkmanager fail hua. Internet check karo."
echo "sdk.dir=$SDK" > local.properties

say "4/7 Gradle 8.7"
if [ ! -x "$HOME/gradle-8.7/bin/gradle" ]; then
  wget -q --show-progress https://services.gradle.org/distributions/gradle-8.7-bin.zip -O "$HOME/gradle.zip"
  unzip -q "$HOME/gradle.zip" -d "$HOME"
  rm -f "$HOME/gradle.zip"
fi

say "5/7 Gradle settings for Termux"
mkdir -p "$HOME/.gradle"
GP="$HOME/.gradle/gradle.properties"
touch "$GP"
sed -i '/^# KALU-BEGIN/,/^# KALU-END/d' "$GP"
cat >> "$GP" <<PROPS
# KALU-BEGIN
android.aapt2FromMavenOverride=$PREFIX/bin/aapt2
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
systemProp.org.gradle.native=false
org.gradle.vfs.watch=false
kotlin.daemon.jvm.options=-Xmx1024m
# KALU-END
PROPS

say "6/7 Building debug APK (pehli baar 10 se 25 minute lag sakte hain)"
"$HOME/gradle-8.7/bin/gradle" assembleDebug --no-daemon -Pkotlin.compiler.execution.strategy=in-process \
  || die "Build fail hua. Upar ka pehla 'e: ' ya 'error:' wala line copy karke bhejo."

say "7/7 APK"
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || die "APK nahi bani."
if [ -d "$HOME/storage/downloads" ]; then
  cp "$APK" "$HOME/storage/downloads/KALU-AI.apk"
  echo "APK yahan hai: Downloads/KALU-AI.apk"
else
  cp "$APK" "$HOME/KALU-AI.apk"
  echo "APK yahan hai: $HOME/KALU-AI.apk"
  echo "Downloads mein copy karne ke liye pehle chalao: termux-setup-storage"
fi
echo "Install: file manager se KALU-AI.apk kholo, ya 'termux-open $APK' chalao."
