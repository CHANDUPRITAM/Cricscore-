#!/bin/bash
set -e

if [ ! -f debug.keystore ]; then
  keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

mkdir -p app/src/main/res/values
cat << 'EOF' > app/src/main/res/values/ci_strings.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string>
</resources>
EOF

# Add Start.io SDK dependency placeholder for buyers
grep -q "com.startapp:inapp-sdk" app/build.gradle.kts || sed -i 's/dependencies {/dependencies {\n    implementation("com.startapp:inapp-sdk:5.1.0")/' app/build.gradle.kts

# Register placeholder Start.io App ID in AndroidManifest.xml
python3 -c '
p = "app/src/main/AndroidManifest.xml"
s = open(p).read()
if "com.startapp.sdk.APPLICATION_ID" not in s:
    s = s.replace("</application>", "        <meta-data android:name=\"com.startapp.sdk.APPLICATION_ID\" android:value=\"YOUR_STARTIO_APP_ID\" />\n    </application>", 1)
    open(p, "w").write(s)
'

sed -i "s/versionName = \".*\"/versionName = \"v1.0.$RUN_NUM\"/" app/build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $RUN_NUM/" app/build.gradle.kts
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Cricscore.apk
