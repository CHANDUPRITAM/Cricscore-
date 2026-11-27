package com.example.ui.components

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import java.io.File

object StartIoAdManager {
    private const val TAG = "StartIoAdManager"
    const val STARTAPP_APP_ID = "210732074"
    private const val RELOAD_BACKOFF_MS = 30_000L

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    @Volatile
    private var preloadedInningsAd: StartAppAd? = null

    @Volatile
    private var isPreloading = false

    @Volatile
    private var lastLoadFailureAtMs = 0L

    private fun ensureWebViewCacheDirs(context: Context) {
        runCatching {
            val codeCacheRoot = File(context.cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            File(codeCacheRoot, "wasm").mkdirs()
            File(codeCacheRoot, "js").mkdirs()
        }
    }

    fun ensureInitialized(context: Context) {
        val appContext = context.applicationContext ?: context
        ensureWebViewCacheDirs(appContext)

        if (isInitialized) {
            preloadInningsAd(appContext)
            return
        }
        if (isInitializing) return

        isInitializing = true
        runCatching {
            StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
            StartAppSDK.initParams(appContext, STARTAPP_APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback {
                    isInitialized = true
                    isInitializing = false
                    preloadInningsAd(appContext)
                }
                .init()
        }.onFailure { err ->
            isInitializing = false
            Log.w(TAG, "Start.io SDK initialization skipped/failed: ${err.message}")
        }
    }

    fun preloadInningsAd(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext ?: context
        if (!isInitialized) {
            ensureInitialized(appContext)
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (!force && lastLoadFailureAtMs > 0L && (now - lastLoadFailureAtMs) < RELOAD_BACKOFF_MS) {
            return
        }

        val adInstance = preloadedInningsAd
            ?: runCatching { StartAppAd(appContext) }.getOrNull()?.also { preloadedInningsAd = it }
            ?: return

        val alreadyReady = runCatching { adInstance.isReady }.getOrDefault(false)
        if (alreadyReady || isPreloading) return

        isPreloading = true
        runCatching {
            adInstance.loadAd(
                StartAppAd.AdMode.AUTOMATIC,
                object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        isPreloading = false
                        lastLoadFailureAtMs = 0L
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        isPreloading = false
                        lastLoadFailureAtMs = SystemClock.elapsedRealtime()
                        Log.w(TAG, "Innings ad unavailable (${ad?.errorMessage ?: "no fill"})")
                    }
                }
            )
        }.onFailure { err ->
            isPreloading = false
            lastLoadFailureAtMs = SystemClock.elapsedRealtime()
            Log.w(TAG, "Error preloading innings ad: ${err.message}")
        }
    }

    fun showInningsAdAndPreload(
        context: Context,
        onProceed: () -> Unit
    ) {
        val appContext = context.applicationContext ?: context
        ensureInitialized(appContext)

        val mainExecutor = ContextCompat.getMainExecutor(appContext)
        var proceeded = false
        fun finishOnce() {
            mainExecutor.execute {
                if (!proceeded) {
                    proceeded = true
                    onProceed()
                }
            }
        }

        val adToShow = preloadedInningsAd
        val isReady = adToShow != null && runCatching { adToShow.isReady }.getOrDefault(false)
        if (!isReady || adToShow == null) {
            preloadInningsAd(appContext, force = true)
            finishOnce()
            return
        }

        // Clear current reference and immediately pre-load the next innings ad
        preloadedInningsAd = null
        isPreloading = false
        lastLoadFailureAtMs = 0L
        preloadInningsAd(appContext, force = true)

        val didShow = runCatching {
            adToShow.showAd(
                object : AdDisplayListener {
                    override fun adHidden(ad: Ad) {
                        preloadInningsAd(appContext, force = true)
                        finishOnce()
                    }

                    override fun adDisplayed(ad: Ad) = Unit

                    override fun adClicked(ad: Ad) = Unit

                    override fun adNotDisplayed(ad: Ad) {
                        preloadInningsAd(appContext, force = true)
                        finishOnce()
                    }
                }
            )
        }.getOrDefault(false)

        if (!didShow) {
            preloadInningsAd(appContext, force = true)
            finishOnce()
        }
    }
}

@Composable
fun StartIoBannerAd(
    modifier: Modifier = Modifier,
    includeNavBarPadding: Boolean = false
) {
    val context = LocalContext.current
    val isInEditMode = LocalInspectionMode.current

    LaunchedEffect(context) {
        if (!isInEditMode) {
            StartIoAdManager.ensureInitialized(context)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (includeNavBarPadding) Modifier.navigationBarsPadding() else Modifier)
            .testTag("startio_bottom_banner_ad"),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isInEditMode) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx ->
                            val container = FrameLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            }
                            runCatching {
                                StartIoAdManager.ensureInitialized(ctx)
                                val banner = Banner(
                                    ctx,
                                    object : BannerListener {
                                        override fun onReceiveAd(view: View) = Unit
                                        override fun onFailedToReceiveAd(view: View?) {
                                            Log.d("StartIoBannerAd", "Banner load deferred/failed")
                                        }
                                        override fun onImpression(view: View) = Unit
                                        override fun onClick(view: View) = Unit
                                    }
                                ).apply {
                                    loadAd()
                                }
                                val bannerLp = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    Gravity.CENTER_HORIZONTAL
                                )
                                container.addView(banner, bannerLp)
                            }.onFailure { err ->
                                Log.w("StartIoBannerAd", "Unable to create Start.io Banner: ${err.message}")
                            }
                            container
                        }
                    )
                }
            }
        }
    }
}
