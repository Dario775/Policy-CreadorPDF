package com.speedscan.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor() {

    private var interstitialAd: InterstitialAd? = null

    // IDs de prueba de AdMob
    fun loadInterstitial(context: Context) {
        val adRequest = AdRequest.Builder().build()
        // Cargar ID desde recursos centralizados
        val adUnitId = context.getString(com.speedscan.R.string.admob_interstitial_id)
        
        InterstitialAd.load(context, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                interstitialAd = null
            }

            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
            }
        })
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onDismissed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            onDismissed()
        }
    }
}
