package com.aurorashelf.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Log tag shared by every prefetch path. Never log page URLs or titles with it. */
internal const val PREFETCH_LOG_TAG = "Prefetch"

/**
 * Prefetch may only run when the user enabled it and the current network is not metered.
 * Kept as a pure function so the policy matrix can be unit tested on the JVM.
 */
internal fun shouldPrefetch(enabled: Boolean, isMetered: Boolean): Boolean = enabled && !isMetered

/**
 * Reads the persisted prefetch switch and the current network capabilities.
 *
 * Any failure to determine the network state (no active network, null capabilities, missing
 * permission) is treated as metered, so a prefetch can never spend unexpected mobile data.
 */
internal fun isPrefetchEnabled(context: Context): Boolean {
    if (!AppPreferences(context).videoPreloadEnabled) return false
    val isMetered = runCatching { currentNetworkIsMetered(context) }.getOrDefault(true)
    return shouldPrefetch(enabled = true, isMetered = isMetered)
}

private fun currentNetworkIsMetered(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val network = manager.activeNetwork ?: return true
    val capabilities = manager.getNetworkCapabilities(network) ?: return true
    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
