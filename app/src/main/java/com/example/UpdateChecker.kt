package com.example

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AutoUpdateChecker() {
    val context = LocalContext.current
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://github.com")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tag = json.optString("tag_name", "")
                    val assets = json.optJSONArray("assets")
                    val apkUrl = if (assets != null && assets.length() > 0) {
                        assets.getJSONObject(0).optString("browser_download_url", "")
                    } else {
                        "https://github.com"
                    }

                    if (tag.isNotEmpty() && currentVersion.isNotEmpty() && tag != currentVersion) {
                        latestVersion = tag
                        downloadUrl = apkUrl
                        showDialog = true
                    }
                }
            } catch (_: Exception) {
                // नेटवर्क न होने पर बिना क्रैश हुए चुपचाप इग्नोर करेगा
            }
        }
    }

    if (showDialog && latestVersion != null && downloadUrl != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("नया अपडेट उपलब्ध है! 🚀") },
            text = { Text("ऐप का नया वर्ज़न ($latestVersion) आ गया है। नए फीचर्स पाने के लिए अभी अपडेट करें।") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    context.startActivity(intent)
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("बाद में")
                }
            }
        )
    }
}
