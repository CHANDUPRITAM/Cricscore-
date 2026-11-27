#!/bin/bash
set -e

if [ ! -f debug.keystore ]; then
  keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

# Create temporary dummy google-services.json if deleted for clean sale repo
if [ ! -f app/google-services.json ]; then
  cat << 'EOF' > app/google-services.json
{
  "project_info": {
    "project_number": "100000000000",
    "project_id": "cricscore-demo",
    "storage_bucket": "://appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:100000000000:android:0000000000000000",
        "android_client_info": {
          "package_name": "com.aistudio.cricscore.krmxvp"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "AIzaSyDummyKeyForCleanCompilation12345"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
EOF
fi

mkdir -p app/src/main/res/values
cat << 'EOF' > app/src/main/res/values/ci_strings.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string>
</resources>
EOF

# Fix CirclePhotoCropDialog.kt missing functions & type mismatch
python3 -c '
p = "app/src/main/java/com/example/ui/components/CirclePhotoCropDialog.kt"
import os
if os.path.exists(p):
    s = open(p).read()
    # Fix line 345 type mismatch (Any -> String) if present
    s = s.replace("cropAndCompressBitmapToBase64(", "cropAndCompressBitmapToBase64Safe(")
    helper = """
private fun loadPreviewBitmap(context: android.content.Context, uri: android.net.Uri): android.graphics.Bitmap? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val m = maxOf(info.size.width, info.size.height)
                if (m > 720) {
                    val sc = 720f / m
                    decoder.setTargetSize((info.size.width * sc).toInt().coerceAtLeast(1), (info.size.height * sc).toInt().coerceAtLeast(1))
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }
    } catch (_: Throwable) { null }
}

private fun cropAndCompressBitmapToBase64Safe(
    bitmap: android.graphics.Bitmap?,
    scale: Any? = 1f,
    offsetX: Any? = 0f,
    offsetY: Any? = 0f,
    viewportPx: Any? = 250f
): String? {
    val bmp = bitmap ?: return null
    return try {
        val sc = (scale as? Number)?.toFloat()?.coerceAtLeast(1f) ?: 1f
        val ox = (offsetX as? Number)?.toFloat() ?: 0f
        val oy = (offsetY as? Number)?.toFloat() ?: 0f
        val vp = (viewportPx as? Number)?.toFloat()?.coerceAtLeast(1f) ?: 250f

        val baseSide = minOf(bmp.width, bmp.height).toFloat()
        val cropSide = (baseSide / sc).toInt().coerceIn(1, minOf(bmp.width, bmp.height))
        val ratio = baseSide / vp
        val cx = (bmp.width / 2f) - (ox * ratio)
        val cy = (bmp.height / 2f) - (oy * ratio)
        val left = (cx - cropSide / 2f).toInt().coerceIn(0, (bmp.width - cropSide).coerceAtLeast(0))
        val top = (cy - cropSide / 2f).toInt().coerceIn(0, (bmp.height - cropSide).coerceAtLeast(0))

        val cropped = android.graphics.Bitmap.createBitmap(bmp, left, top, cropSide, cropSide)
        var dim = 160
        var bytes = ByteArray(0)
        while (dim >= 96) {
            val resized = android.graphics.Bitmap.createScaledBitmap(cropped, dim, dim, true)
            var q = 80
            while (q >= 20) {
                val out = java.io.ByteArrayOutputStream()
                resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, out)
                bytes = out.toByteArray()
                if (bytes.size <= 15000) break
                q -= 15
            }
            if (bytes.size <= 15000) break
            dim -= 32
        }
        "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (_: Throwable) { null }
}
"""
    if "fun loadPreviewBitmap" not in s:
        s = s + "\n" + helper
    open(p, "w").write(s)
'

grep -q "com.startapp:inapp-sdk" app/build.gradle.kts || sed -i 's/dependencies {/dependencies {\n    implementation("com.startapp:inapp-sdk:5.1.0")/' app/build.gradle.kts

sed -i "s/versionName = \".*\"/versionName = \"v1.0.$RUN_NUM\"/" app/build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $RUN_NUM/" app/build.gradle.kts
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Cricscore.apk
