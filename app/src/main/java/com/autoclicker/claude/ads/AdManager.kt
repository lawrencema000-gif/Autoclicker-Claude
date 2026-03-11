package com.autoclicker.claude.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd

object AdManager : Application.ActivityLifecycleCallbacks {
    private const val TAG = "AdManager"
    private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-9489106590476826/5937155980"

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var isInitialized = false
    private var currentActivity: Activity? = null

    fun initialize(application: Application) {
        if (isInitialized) return
        application.registerActivityLifecycleCallbacks(this)
        MobileAds.initialize(application) {
            isInitialized = true
            Log.d(TAG, "AdMob initialized")
            loadAd(application)
        }
    }

    private fun loadAd(activity: Activity) {
        loadAdInternal(activity)
    }

    private fun loadAd(application: Application) {
        loadAdInternal(application as android.content.Context)
    }

    private fun loadAdInternal(context: android.content.Context) {
        if (isLoadingAd || appOpenAd != null) return
        isLoadingAd = true

        val request = AdRequest.Builder().build()
        AppOpenAd.load(context, APP_OPEN_AD_UNIT_ID, request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    Log.d(TAG, "App Open Ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    Log.d(TAG, "App Open Ad failed to load: ${error.message}")
                }
            }
        )
    }

    private fun showAdIfAvailable(activity: Activity) {
        if (isShowingAd) return

        val ad = appOpenAd
        if (ad == null) {
            loadAd(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }
        }

        ad.show(activity)
    }

    // ActivityLifecycleCallbacks — show ad when app comes to foreground
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        showAdIfAvailable(activity)
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
