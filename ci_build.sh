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

# 3. Create Reliable Update Checker + Preloaded Full-Screen Ad + Live Banners
cat << 'EOF' > app/src/main/java/com/example/StartIoAndUpdateHelper.kt
package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object StartIoManager {
    private var initialized = false
    private var cachedAd: StartAppAd? = null
    private var isAdReady = false

    fun ensureInit(context: Context) {
        if (!initialized) {
            try {
                StartAppSDK.init(context.applicationContext, "210732074", false)
                StartAppAd.disableSplash()
                initialized = true
                preloadFullAd(context)
            } catch (_: Throwable) {}
        }
    }

    fun preloadFullAd(context: Context) {
        try {
            val host = context.findActivity() ?: context.applicationContext
            val ad = StartAppAd(host)
            isAdReady = false
            ad.loadAd(object : AdEventListener {
                override fun onReceiveAd(p0: Ad) {
                    cachedAd = ad
                    isAdReady = true
                }
                override fun onFailedToReceiveAd(p0: Ad?) {
                    isAdReady = false
                }
            })
        } catch (_: Throwable) {}
    }

    fun showFullAdNowOrWhenLoaded(context: Context) {
        try {
            ensureInit(context)
            val host = context.findActivity() ?: context.applicationContext
            val readyAd = cachedAd
            if (readyAd != null && (isAdReady || readyAd.isReady)) {
                readyAd.showAd()
                isAdReady = false
                cachedAd = null
                preloadFullAd(host)
                return
            }
            val freshAd = StartAppAd(host)
            val requestedAt = System.currentTimeMillis()
            freshAd.loadAd(object : AdEventListener {
                override fun onReceiveAd(p0: Ad) {
                    if (System.currentTimeMillis() - requestedAt <= 6000L) {
                        try { freshAd.showAd() } catch (_: Throwable) {}
                        preloadFullAd(host)
                    } else {
                        cachedAd = freshAd
                        isAdReady = true
                    }
                }
                override fun onFailedToReceiveAd(p0: Ad?) {}
            })
        } catch (_: Throwable) {}
    }
}

@Composable
fun MatchAndInningsFullAdTrigger(matchKey: Any) {
    val context = LocalContext.current
    LaunchedEffect(matchKey) {
        StartIoManager.showFullAdNowOrWhenLoaded(context)
    }
}

@Composable
fun StartIoBannerOnly(withNavPadding: Boolean = false) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        StartIoManager.ensureInit(context)
        delay(800L)
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
fun GlobalUpdateChecker() {
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var serverVer by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("https://github.com") }

    val currentVer = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName?.trim().orEmpty()
        } catch (_: Exception) { "" }
    }

    fun checkVersionMatch(remoteVer: String, remoteUrl: String) {
        val cleanCur = currentVer.removePrefix("v").removePrefix("V").trim()
        val cleanRem = remoteVer.removePrefix("v").removePrefix("V").trim()
        if (cleanRem.isNotEmpty() && cleanCur.isNotEmpty() && cleanRem != cleanCur) {
            serverVer = remoteVer
            if (remoteUrl.isNotEmpty()) serverUrl = remoteUrl
            showUpdateDialog = true
        }
    }

    LaunchedEffect(Unit) {
        StartIoManager.ensureInit(context)

        // 1. Check Firebase Firestore real-time
        try {
            Firebase.firestore.collection("app_config").document("version_info")
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        val v = (snap.getString("latestVersion") ?: snap.getString("latestversion") ?: "").trim()
                        val u = (snap.getString("downloadUrl") ?: snap.getString("downloadurl") ?: "").trim()
                        checkVersionMatch(v, u)
                    }
                }
        } catch (_: Throwable) {}

        // 2. Also check GitHub Latest Release API as backup
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL("https://github.com").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Cricscore-App")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val tag = json.optString("tag_name", "").trim()
                    val assets = json.optJSONArray("assets")
                    val apkUrl = if (assets != null && assets.length() > 0) {
                        assets.getJSONObject(0).optString("browser_download_url", "")
                    } else {
                        "https://github.com"
                    }
                    withContext(Dispatchers.Main) {
                        checkVersionMatch(tag, apkUrl)
                    }
                }
            } catch (_: Throwable) {}
        }
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
}
EOF

# 4. Inject GlobalUpdateChecker in AppContent, Banners on Home + Live Screens, and Full Ad on Match & 2nd Innings Start
python3 -c '
import glob

def wrap_call(src, func_name, prefix, suffix):
