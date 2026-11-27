#!/bin/bash
set -e

if [ -f app/google-services.json ]; then
  cp app/google-services.json /tmp/google-services.json
fi

git checkout dc1aa77 -- app

if [ -f /tmp/google-services.json ]; then
  cp /tmp/google-services.json app/google-services.json
fi

if [ ! -f debug.keystore ]; then
  keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

mkdir -p app/src/main/res/values
cat << 'EOF' > app/src/main/res/values/ci_strings.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="default_web_client_id">placeholder_client_id</string>
</resources>
EOF

# 1. Add Start.io SDK safely
if ! grep -q "com.startapp:inapp-sdk" app/build.gradle.kts; then
  sed -i 's/dependencies {/dependencies {\n    implementation("com.startapp:inapp-sdk:5.1.0")/' app/build.gradle.kts
fi

# 2. Register Start.io App ID 210732074 in AndroidManifest.xml
python3 -c '
p = "app/src/main/AndroidManifest.xml"
s = open(p).read()
if "com.startapp.sdk.APPLICATION_ID" not in s:
    s = s.replace("</application>", "        <meta-data android:name=\"com.startapp.sdk.APPLICATION_ID\" android:value=\"210732074\" />\n    </application>", 1)
open(p, "w").write(s)
'

# 3. Create Update Checker + Reusable Start.io Banner for Home & Live Screens
cat << 'EOF' > app/src/main/java/com/example/StartIoAndUpdateHelper.kt
package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import kotlinx.coroutines.delay

object StartIoManager {
    private var initialized = false
    fun ensureInit(context: Context) {
        if (!initialized) {
            try {
                StartAppSDK.init(context.applicationContext, "210732074", false)
                StartAppAd.disableSplash()
                initialized = true
            } catch (_: Throwable) {}
        }
    }
}

@Composable
fun StartIoBannerOnly(withNavPadding: Boolean = false) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1500L)
        StartIoManager.ensureInit(context)
        ready = true
    }

    if (ready) {
        val mod = if (withNavPadding) {
            Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp)
        } else {
            Modifier.fillMaxWidth().height(52.dp)
        }
        AndroidView(
            modifier = mod,
            factory = { ctx ->
                Banner(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    try { loadAd() } catch (_: Throwable) {}
                }
            }
        )
    }
}

@Composable
fun GuaranteedUpdateAndAdBanner() {
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var serverVer by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("https://github.com") }

    val currentVer = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName?.trim().orEmpty()
        } catch (_: Exception) { "" }
    }

    LaunchedEffect(Unit) {
        delay(3200L)
        StartIoManager.ensureInit(context)
        try {
            Firebase.firestore.collection("app_config").document("version_info")
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        val v = (snap.getString("latestVersion") ?: snap.getString("latestversion") ?: "").trim()
                        val u = (snap.getString("downloadUrl") ?: snap.getString("downloadurl") ?: "").trim()
                        val cleanCurrent = currentVer.removePrefix("v").removePrefix("V")
                        val cleanServer = v.removePrefix("v").removePrefix("V")
                        if (cleanServer.isNotEmpty() && cleanCurrent.isNotEmpty() && cleanServer != cleanCurrent) {
                            serverVer = v
                            if (u.isNotEmpty()) serverUrl = u
                            showUpdateDialog = true
                        } else {
                            showUpdateDialog = false
                        }
                    }
                }
        } catch (_: Throwable) {}
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("नया अपडेट उपलब्ध है! 🚀") },
            text = { Text("ऐप का नया वर्ज़न ($serverVer) आ गया है (आपका वर्ज़न: $currentVer)। नए फीचर्स पाने के लिए अभी अपडेट करें।") },
            confirmButton = {
                Button(onClick = {
                    showUpdateDialog = false
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)))
                }) { Text("Update Now") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("बाद में") }
            }
        )
    }

    StartIoBannerOnly(withNavPadding = false)
}
EOF

# 4. Inject banner on Home Page AND wrap LiveScoringScreen + LiveSpectatorScreen with bottom banner
python3 -c '
def wrap_call(src, func_name, prefix, suffix):
    idx = 0
    while True:
        pos = src.find(func_name + "(", idx)
        if pos == -1:
            break
        depth = 0
        end = pos + len(func_name)
        while end < len(src):
            if src[end] == "(":
                depth += 1
            elif src[end] == ")":
                depth -= 1
                if depth == 0:
                    break
            end += 1
        call_str = src[pos:end+1]
        rep = prefix + call_str + suffix
        src = src[:pos] + rep + src[end+1:]
        idx = pos + len(rep)
    return src

p = "app/src/main/java/com/example/MainActivity.kt"
s = open(p).read()
assert "MadeByFooter(" in s, "MadeByFooter not found in MainActivity.kt"
s = s.replace("MadeByFooter(", "com.example.GuaranteedUpdateAndAdBanner()\n                    MadeByFooter(", 1)

pre = "Column(modifier = Modifier.fillMaxSize()) { Box(modifier = Modifier.weight(1f)) { "
suf = " }; com.example.StartIoBannerOnly(withNavPadding = true) }"
s = wrap_call(s, "LiveScoringScreen", pre, suf)
s = wrap_call(s, "LiveSpectatorScreen", pre, suf)

open(p, "w").write(s)
print("Successfully added banners to Home, LiveScoring, and LiveSpectator screens!")
'

sed -i "s/versionName = \".*\"/versionName = \"v1.0.$RUN_NUM\"/" app/build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $RUN_NUM/" app/build.gradle.kts
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Cricscore.apk
