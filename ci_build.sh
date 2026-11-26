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

# 3. Create Start.io Banner with explicit 50.dp height & MATCH_PARENT width
cat << 'EOF' > app/src/main/java/com/example/StartIoHelper.kt
package com.example

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
fun SafeStartIoBanner() {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3200L)
        StartIoManager.ensureInit(context)
        ready = true
    }

    if (ready) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(52.dp),
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
EOF

# 4. Place SafeStartIoBanner directly above MadeByFooter inside MainActivity Column
python3 -c '
p = "app/src/main/java/com/example/MainActivity.kt"
s = open(p).read()
if "SafeStartIoBanner" not in s and "MadeByFooter(" in s:
    s = s.replace("MadeByFooter(", "SafeStartIoBanner()\n                    MadeByFooter(", 1)
    open(p, "w").write(s)
'

sed -i "s/versionName = \".*\"/versionName = \"v1.0.$RUN_NUM\"/" app/build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $RUN_NUM/" app/build.gradle.kts
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Cricscore.apk
