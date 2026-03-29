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
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager : Application.ActivityLifecycleCallbacks {
    private const val TAG = "AdManager"

    // Production ad unit IDs
    private const val APP_OPEN_AD_UNIT_PROD = "ca-app-pub-9489106590476826/5937155980"
    private const val INTERSTITIAL_AD_UNIT_PROD = "ca-app-pub-9489106590476826/7261830147"

    // Google's official test ad unit IDs (always show test ads)
    private const val APP_OPEN_AD_UNIT_TEST = "ca-app-pub-3940256099942544/9257395921"
    private const val INTERSTITIAL_AD_UNIT_TEST = "ca-app-pub-3940256099942544/1033173712"

    private val appOpenAdUnitId: String
        get() = if (BuildConfig.DEBUG) APP_OPEN_AD_UNIT_TEST else APP_OPEN_AD_UNIT_PROD
    private val interstitialAdUnitId: String
        get() = if (BuildConfig.DEBUG) INTERSTITIAL_AD_UNIT_TEST else INTERSTITIAL_AD_UNIT_PROD

    // Banner ad unit IDs (accessed by BannerAd composable)
    const val BANNER_AD_UNIT_PROD = "ca-app-pub-9489106590476826/1583041258"
    const val BANNER_AD_UNIT_TEST = "ca-app-pub-3940256099942544/6300978111"
    val bannerAdUnitId: String
        get() = if (BuildConfig.DEBUG) BANNER_AD_UNIT_TEST else BANNER_AD_UNIT_PROD

    private var appOpenAd: AppOpenAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingAppOpen = false
    private var isLoadingInterstitial = false
    private var isShowingAd = false
    var isInitialized = false
        private set
    private var currentActivity: Activity? = null

    fun initialize(application: Application) {
        if (isInitialized) return
        application.registerActivityLifecycleCallbacks(this)
        MobileAds.initialize(application) {
            isInitialized = true
            Log.d(TAG, "AdMob initialized")
            loadAppOpenAd(application)
            loadInterstitialAd(application)
        }
    }

    // ===== App Open Ad =====

    private fun loadAppOpenAd(context: android.content.Context) {
        if (isLoadingAppOpen || appOpenAd != null) return
        isLoadingAppOpen = true

        AppOpenAd.load(context, appOpenAdUnitId, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAppOpen = false
                    Log.d(TAG, "App Open Ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAppOpen = false
                    Log.d(TAG, "App Open Ad failed: ${error.message}")
                }
            }
        )
    }

    private fun showAppOpenIfAvailable(activity: Activity) {
        if (isShowingAd) return
        val ad = appOpenAd ?: run { loadAppOpenAd(activity); return }

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
            }
        }
        ad.show(activity)
    }

    // ===== Interstitial Ad =====

    private fun loadInterstitialAd(context: android.content.Context) {
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true

        InterstitialAd.load(context, interstitialAdUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                    Log.d(TAG, "Interstitial Ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    Log.d(TAG, "Interstitial Ad failed: ${error.message}")
                }
            }
        )
    }

    /**
     * Show interstitial ad (e.g., when a session stops).
     * Returns true if an ad was shown, false if none available.
     */
    fun showInterstitial(activity: Activity): Boolean {
        val ad = interstitialAd ?: run {
            loadInterstitialAd(activity)
            return false
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                isShowingAd = false
                loadInterstitialAd(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                isShowingAd = false
                loadInterstitialAd(activity)
            }
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }
        }

        isShowingAd = true
        ad.show(activity)
        return true
    }

    // ===== Lifecycle Callbacks =====

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        showAppOpenIfAvailable(activity)
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
