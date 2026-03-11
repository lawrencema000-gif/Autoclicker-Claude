package com.autoclicker.claude.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"

    // TODO: Replace with your real ad unit IDs before publishing
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111" // Test banner
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // Test interstitial

    private var interstitialAd: InterstitialAd? = null
    private var isInitialized = false
    private var adShowCount = 0

    fun initialize(context: Context) {
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            Log.d(TAG, "AdMob initialized")
            loadInterstitial(context)
        }
    }

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.d(TAG, "Interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Show interstitial ad every 3rd action to avoid being too aggressive.
     * Returns true if ad was shown.
     */
    fun showInterstitialIfReady(activity: Activity, onDismissed: () -> Unit = {}): Boolean {
        adShowCount++
        // Show every 3rd time to balance UX and revenue
        if (adShowCount % 3 != 0) {
            onDismissed()
            return false
        }

        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
            loadInterstitial(activity)
            return false
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial(activity)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadInterstitial(activity)
                onDismissed()
            }
        }

        ad.show(activity)
        return true
    }
}
