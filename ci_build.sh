#!/bin/bash
set -e

# Restore 100% working v1.0.20 source code (commit 6490c86)
git checkout 6490c86 -- app/src

# Remove any leftover files
rm -f app/src/main/java/com/example/StartIoBanner.kt

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

sed -i "s/versionName = \".*\"/versionName = \"v1.0.$RUN_NUM\"/" app/build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $RUN_NUM/" app/build.gradle.kts
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Cricscore.apk
