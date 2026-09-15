package com.speedscan.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    fun gatherConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        val requestParameters = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            requestParameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    refreshState()
                    onComplete(_canRequestAds.value)
                }
            },
            {
                refreshState()
                onComplete(_canRequestAds.value)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity, onDismissed: (String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            refreshState()
            onDismissed(error?.message)
        }
    }

    private fun refreshState() {
        _canRequestAds.value = consentInformation.canRequestAds()
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}
