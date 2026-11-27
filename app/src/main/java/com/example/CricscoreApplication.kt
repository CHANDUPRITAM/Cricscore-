package com.example

import android.app.Application
import android.content.Context
import android.os.Build
import android.webkit.WebView
import java.io.File

class CricscoreApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        configureWebViewStorage(base)
    }

    private fun configureWebViewStorage(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val processName = getProcessName()
                if (!processName.isNullOrBlank() && processName != context.packageName) {
                    WebView.setDataDirectorySuffix(processName.replace(':', '_'))
                }
            }
        }
        runCatching {
            val codeCacheRoot = File(context.cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            File(codeCacheRoot, "wasm").mkdirs()
            File(codeCacheRoot, "js").mkdirs()
        }
    }
}
