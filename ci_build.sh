#!/bin/bash
set -e

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

# 1. Add Start.io SDK dependency safely
if ! grep -q "com.startapp:inapp-sdk" app/build.gradle.kts; then
  sed -i 's/dependencies {/dependencies {\n    implementation("com.startapp:inapp-sdk:5.1.0")/' app/build.gradle.kts
fi

# 2. Initialize Start.io SDK with App ID 210732074 inside MainActivity
python3 -c '
p = "app/src/main/java/com/example/MainActivity.kt"
s = open(p).read()
if "StartAppSDK" not in s:
    s = s.replace("import android.os.Bundle", "import android.os.Bundle\nimport com.startapp.sdk.adsbase.StartAppAd\nimport com.startapp.sdk.adsbase.StartAppSDK", 1)
    s = s.replace("super.onCreate(savedInstanceState)", "super.onCreate(savedInstanceState)\n        StartAppSDK.init(this, \"210732074\", true)\n        StartAppAd.disableSplash()", 1)
    open(p, "w").write(s)
'

# 3. Create reusable Start.io Banner Composable & attach to MadeByFooter so it appears across screens
cat << 'EOF' > app/src/main/java/com/example/StartIoBanner.kt
package com.example

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.startapp.sdk.ads.banner.Banner

@Composable
fun StartIoBannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { ctx ->
            Banner(ctx).apply {
                loadAd()
            }
        }
    )
}
EOF

python3 -c '
import glob
for path in glob.glob("app/src/main/java/**/MadeByFooter.kt", recursive=True):
    s = open(path).read()
    if "StartIoBannerAd" not in s and "MadeByFooter(" in s:
        s = "import com.example.StartIoBannerAd\n" + s if not s.startswith("package ") else s.replace("\n", "\nimport com.example.StartIoBannerAd\n", 1)
        idx = s.find("{", s.find("fun MadeByFooter"))
        if idx != -1:
            s = s[:idx+1] + "\n    StartIoBannerAd()\n" + s[idx+1:]
            open(path, "w").write(s)
'

sed -i "s/versionName = \".*\"/versionName = \"v1.0.$RUN_NUM\"/" app/build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $RUN_NUM/" app/build.gradle.kts
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Cricscore.apk
