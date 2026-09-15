package com.speedscan.core.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

import androidx.compose.ui.res.stringResource
import com.speedscan.R

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    // ID desde recursos
    val bannerId = stringResource(id = R.string.admob_banner_id)

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = bannerId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
