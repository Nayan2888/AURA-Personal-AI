package ai.aura.personal.core.research

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Allows research only when explicit consent exists and Android currently
 * reports an available network with INTERNET capability.
 */
class AndroidResearchAccessController(
    context: Context,
    private val consentStore: ResearchConsentStore = ResearchConsentStore(context)
) : ResearchAccessController {

    private val connectivityManager = context.applicationContext.getSystemService(
        ConnectivityManager::class.java
    )

    override fun isResearchAllowed(): Boolean {
        if (!consentStore.isGranted()) return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
