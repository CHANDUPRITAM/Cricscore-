#!/bin/bash
set -e

git checkout da2d1b2 -- app/src/main/java/com/example/MainActivity.kt
rm -f app/src/main/java/com/example/UpdateChecker.kt app/src/main/java/com/example/SplashScreen.kt

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

cat << 'EOF' > app/src/main/java/com/example/SplashAndUpdate.kt
package com.example

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun SplashAndUpdateHost(content: @Composable () -> Unit) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var startAnim by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnim = true
        if (showSplash) {
            delay(3000L)
            showSplash = false
        }
    }

    val scale by animateFloatAsState(if (startAnim) 1f else 0.7f, tween(900, easing = FastOutSlowInEasing), label = "s")
    val alpha by animateFloatAsState(if (startAnim) 1f else 0f, tween(900), label = "a")

    Box(modifier = Modifier.fillMaxSize()) {
        if (!showSplash) {
            AutoUpdateChecker()
            content()
        }
        AnimatedVisibility(visible = showSplash, enter = fadeIn(tween(400)), exit = fadeOut(tween(600))) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), MaterialTheme.colorScheme.background))
                ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale).alpha(alpha)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), modifier = Modifier.size(116.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.SportsCricket, "Logo", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Cricscore", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Live Cricket Scoring & Broadcast", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Made by Chandu Pritam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).alpha(alpha))
            }
        }
    }
}

@Composable
fun AutoUpdateChecker() {
    val context = LocalContext.current
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val currentVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName?.trim() ?: "" } catch (_: Exception) { "" }
    }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            try {
                val conn = (URL("https://github.com").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Cricscore-App")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val tag = json.optString("tag_name", "").trim()
                    val assets = json.optJSONArray("assets")
                    val apkUrl = if (assets != null && assets.length() > 0) assets.getJSONObject(0).optString("browser_download_url", "") else "https://github.com"
                    if (tag.isNotEmpty() && currentVersion.isNotEmpty() && !tag.equals(currentVersion, ignoreCase = true)) Pair(tag, apkUrl) else null
                } else null
            } catch (_: Exception) { null }
        }
        if (result != null) {
            latestVersion = result.first
            downloadUrl = result.second
            showDialog = true
        }
    }

    if (showDialog && latestVersion != null && downloadUrl != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("नया अपडेट उपलब्ध है! 🚀") },
            text = { Text("ऐप का नया वर्ज़न ($latestVersion) आ गया है। अभी अपडेट करें।") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                }) { Text("Update Now") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("बाद में") } }
        )
    }
}
EOF

python3 -c '
import re
p = "app/src/main/java/com/example/MainActivity.kt"
s = open(p).read()
s_new, count = re.subn(r"AppContent\s*\((.*?)\)", r"SplashAndUpdateHost { AppContent(\1) }", s, count=1, flags=re.DOTALL)
assert count == 1, "Failed to inject SplashAndUpdateHost!"
open(p, "w").write(s_new)
'

sed -i "s/versionName = \".*\"/versionName = \"v1.0.$RUN_NUM\"/" app/build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $RUN_NUM/" app/build.gradle.kts
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Cricscore.apk
  
