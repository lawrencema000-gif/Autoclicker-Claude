package com.autoclicker.claude.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.autoclicker.claude.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd

object AdManager : Application.ActivityLifecycleCallbacks {
    private const val TAG = "AdManager"

    // Production ad unit IDs
    private const val APP_OPEN_AD_UNIT_PROD = "ca-app-pub-9489106590476826/5937155980"

    // Google's official test ad unit IDs (always show test ads in debug)
    private const val APP_OPEN_AD_UNIT_TEST = "ca-app-pub-3940256099942544/9257395921"

    private val appOpenAdUnitId: String
        get() = if (BuildConfig.DEBUG) APP_OPEN_AD_UNIT_TEST else APP_OPEN_AD_UNIT_PROD

    // Banner ad unit IDs (accessed by BannerAd composable)
    const val BANNER_AD_UNIT_PROD = "ca-app-pub-9489106590476826/1583041258"
    const val BANNER_AD_UNIT_TEST = "ca-app-pub-3940256099942544/6300978111"
    val bannerAdUnitId: String
        get() = if (BuildConfig.DEBUG) BANNER_AD_UNIT_TEST else BANNER_AD_UNIT_PROD

    private const val AD_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes between app open ads

    // App Open ads expire ~4 hours after load; don't show a stale one.
    private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1000L

    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var isLoadingAppOpen = false
    private var isShowingAd = false
    private var lastAdShownTime = 0L
    private var hasStartedOnce = false
    private var suppressNext = false
    var isInitialized = false
        private set
    private var currentActivity: Activity? = null

    /** Call right before launching an external Settings/permission intent so the
     *  return to the app doesn't trigger an app-open ad mid-onboarding. */
    fun suppressNextAppOpenAd() { suppressNext = true }

    fun initialize(application: Application) {
        if (isInitialized) return
        application.registerActivityLifecycleCallbacks(this)
        MobileAds.initialize(application) {
            isInitialized = true
            Log.d(TAG, "AdMob initialized")
            loadAppOpenAd(application)
        }
    }

    private fun loadAppOpenAd(context: android.content.Context) {
        if (isLoadingAppOpen || appOpenAd != null) return
        isLoadingAppOpen = true

        AppOpenAd.load(context, appOpenAdUnitId, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenLoadedAt = System.currentTimeMillis()
                    isLoadingAppOpen = false
                    Log.d(TAG, "App Open Ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAppOpen = false
                    Log.w(TAG, "App Open Ad failed: ${error.message}")
                }
            }
        )
    }

    private fun showAppOpenIfAvailable(activity: Activity) {
        if (isShowingAd) return
        val ad = appOpenAd ?: run { loadAppOpenAd(activity); return }
        // Discard a stale (expired) ad instead of showing it.
        if (System.currentTimeMillis() - appOpenLoadedAt >= AD_EXPIRY_MS) {
            appOpenAd = null
            loadAppOpenAd(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAppOpenAd(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAppOpenAd(activity)
            }
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                lastAdShownTime = System.currentTimeMillis()
            }
        }
        ad.show(activity)
    }

    // ActivityLifecycleCallbacks — show app open ad when app comes to foreground
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity

        // Never show on the very first foreground (cold start): the user is
        // arriving at the app, and the ad often isn't loaded yet anyway.
        if (!hasStartedOnce) {
            hasStartedOnce = true
            return
        }

        // Skip the ad when returning from a Settings/permission screen we launched
        // (onboarding: accessibility, battery, OEM autostart) — showing an ad there
        // is jarring and interrupts setup.
        if (suppressNext) {
            suppressNext = false
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastAdShownTime >= AD_COOLDOWN_MS) {
            showAppOpenIfAvailable(activity)
        }
    }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }
}
