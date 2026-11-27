#!/bin/bash
set -e

[ -f app/google-services.json ] && cp app/google-services.json /tmp/gs.json
git checkout dc1aa77 -- app
[ -f /tmp/gs.json ] && cp /tmp/gs.json app/google-services.json

if [ ! -f debug.keystore ]; then
  keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

mkdir -p app/src/main/res/values
cat << 'EOF' > app/src/main/res/values/ci_strings.xml
<?xml version="1.0" encoding="utf-8"?>
<resources><string name="default_web_client_id">placeholder_client_id</string></resources>
EOF

grep -q "com.startapp:inapp-sdk" app/build.gradle.kts || sed -i 's/dependencies {/dependencies {\n    implementation("com.startapp:inapp-sdk:5.1.0")/' app/build.gradle.kts

python3 -c '
p = "app/src/main/AndroidManifest.xml"
s = open(p).read()
if "com.startapp.sdk.APPLICATION_ID" not in s:
    s = s.replace("</application>", "        <meta-data android:name=\"com.startapp.sdk.APPLICATION_ID\" android:value=\"210732074\" />\n    </application>", 1)
    open(p, "w").write(s)
'

cat << 'EOF' > app/src/main/java/com/example/StartIoAndUpdateHelper.kt
package com.example

import android.app.Activity
import android.content.*
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.adsbase.*
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.*
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.*

tailrec fun Context.findAct(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findAct()
    else -> null
}

object StartIoManager {
    private var initDone = false
    private var cachedAd: StartAppAd? = null
    private var ready = false
    private var hostRef: WeakReference<Context>? = null

    fun ensureInit(ctx: Context) {
        ctx.findAct()?.let { hostRef = WeakReference(it) } ?: run {
            if (hostRef?.get() == null) hostRef = WeakReference(ctx.applicationContext)
        }
        if (!initDone) {
            try {
                StartAppSDK.init(ctx.applicationContext, "210732074", false)
                StartAppAd.disableSplash()
                initDone = true
                preload(ctx)
            } catch (_: Throwable) {}
        }
    }

    fun preload(ctx: Context) {
        try {
            val host = ctx.findAct() ?: hostRef?.get() ?: ctx.applicationContext
            val ad = StartAppAd(host)
            ready = false
            ad.loadAd(object : AdEventListener {
                override fun onReceiveAd(p0: Ad) { cachedAd = ad; ready = true }
                override fun onFailedToReceiveAd(p0: Ad?) { ready = false }
            })
        } catch (_: Throwable) {}
    }

    fun showFullAdGlobal() {
        hostRef?.get()?.let { showFullAdNowOrWhenLoaded(it) }
    }

    fun showFullAdNowOrWhenLoaded(ctx: Context) {
        try {
            ensureInit(ctx)
            val host = ctx.findAct() ?: hostRef?.get() ?: ctx.applicationContext
            val a = cachedAd
            if (a != null && (ready || a.isReady)) {
                a.showAd(); ready = false; cachedAd = null; preload(host); return
            }
            val fresh = StartAppAd(host)
            val t0 = System.currentTimeMillis()
            fresh.loadAd(object : AdEventListener {
                override fun onReceiveAd(p0: Ad) {
                    if (System.currentTimeMillis() - t0 <= 6000L) {
                        try { fresh.showAd() } catch (_: Throwable) {}
                        preload(host)
                    } else { cachedAd = fresh; ready = true }
                }
                override fun onFailedToReceiveAd(p0: Ad?) {}
            })
        } catch (_: Throwable) {}
    }
}

@Composable
fun MatchAndInningsFullAdTrigger(k: Any) {
    val ctx = LocalContext.current
    LaunchedEffect(k) { StartIoManager.showFullAdNowOrWhenLoaded(ctx) }
}

@Composable
fun StartIoBannerOnly(navPad: Boolean = false) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { StartIoManager.ensureInit(ctx); delay(800L); show = true }
    if (show) {
        val m = if (navPad) Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp) else Modifier.fillMaxWidth().height(52.dp)
        AndroidView(modifier = m, factory = { c ->
            Banner(c).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                try { loadAd() } catch (_: Throwable) {}
            }
        })
    }
}

@Composable
fun GlobalUpdateChecker() {
    val ctx = LocalContext.current
    var showDlg by remember { mutableStateOf(false) }
    var sVer by remember { mutableStateOf("") }
    var sUrl by remember { mutableStateOf("https://github.com") }
    val curVer = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName?.trim().orEmpty() } catch (_: Exception) { "" } }

    fun check(v: String, u: String) {
        val c1 = curVer.removePrefix("v").removePrefix("V").trim()
        val c2 = v.removePrefix("v").removePrefix("V").trim()
        if (c2.isNotEmpty() && c1.isNotEmpty() && c2 != c1) {
            sVer = v; if (u.isNotEmpty()) sUrl = u; showDlg = true
        }
    }

    LaunchedEffect(Unit) {
        StartIoManager.ensureInit(ctx)
        try {
            Firebase.firestore.collection("app_config").document("version_info").addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    check((snap.getString("latestVersion") ?: snap.getString("latestversion") ?: "").trim(),
                          (snap.getString("downloadUrl") ?: snap.getString("downloadurl") ?: "").trim())
                }
            }
        } catch (_: Throwable) {}
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL("https://github.com").openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Cricscore-App")
                }
                if (conn.responseCode == 200) {
                    val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val assets = j.optJSONArray("assets")
                    val url = if (assets != null && assets.length() > 0) assets.getJSONObject(0).optString("browser_download_url", "") else ""
                    withContext(Dispatchers.Main) { check(j.optString("tag_name", "").trim(), url) }
                }
            } catch (_: Throwable) {}
        }
    }

    if (showDlg) {
        AlertDialog(
            onDismissRequest = { showDlg = false },
            title = { Text("नया अपडेट उपलब्ध है! 🚀") },
            text = { Text("नया वर्ज़न ($sVer) आ गया है (आपका वर्ज़न: $curVer)। अभी अपडेट करें।") },
            confirmButton = { Button(onClick = { showDlg = false; ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sUrl))) }) { Text("Update Now") } },
            dismissButton = { TextButton(onClick = { showDlg = false }) { Text("बाद में") } }
        )
    }
}
EOF

python3 -c '
import glob
def wrap(src, fn, pre, suf):
    i = 0
    while True:
        p = src.find(fn + "(", i)
        if p == -1: break
        d, e = 0, p + len(fn)
        while e < len(src):
            if src[e] == "(": d += 1
            elif src[e] == ")":
                d -= 1
                if d == 0: break
            e += 1
        rep = pre + src[p:e+1] + suf
        src = src[:p] + rep + src[e+1:]
        i = p + len(rep)
    return src

p = "app/src/main/java/com/example/MainActivity.kt"
s = open(p).read()
s = s.replace("val auth = Firebase.auth", "com.example.GlobalUpdateChecker()\n    val auth = Firebase.auth", 1)
s = s.replace("MadeByFooter(", "com.example.StartIoBannerOnly(navPad = false)\n                    MadeByFooter(", 1)
s = wrap(s, "LiveScoringScreen", "Column(Modifier.fillMaxSize()) { com.example.MatchAndInningsFullAdTrigger(matchId); Box(Modifier.weight(1f)) { ", " }; com.example.StartIoBannerOnly(navPad = true) }")
s = wrap(s, "LiveSpectatorScreen", "Column(Modifier.fillMaxSize()) { Box(Modifier.weight(1f)) { ", " }; com.example.StartIoBannerOnly(navPad = true) }")
open(p, "w").write(s)

for vp in glob.glob("app/src/main/java/**/LiveMatchViewModel.kt", recursive=True):
    vs = open(vp).read()
    for fn in ["startSecondInnings", "beginSecondInnings", "confirmSecondInnings", "startNextInnings"]:
        if "fun " + fn in vs and "showFullAdGlobal" not in vs:
            idx = vs.find("{", vs.find("fun " + fn))
            if idx != -1:
                vs = vs[:idx+1] + "\n        com.example.StartIoManager.showFullAdGlobal()" + vs[idx+1:]
                open(vp, "w").write(vs)
'

sed -i "s/versionName = \".*\"/versionName = \"v1.0.$RUN_NUM\"/" app/build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $RUN_NUM/" app/build.gradle.kts
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Cricscore.apk
