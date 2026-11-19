package com.aurorashelf.app.cast

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) enforces the `ACCESS_LOCAL_NETWORK` runtime permission before apps targeting
 * API 37 can discover or connect to LAN devices (SSDP multicast, DLNA HTTP SOAP, local media proxy).
 */
internal object LocalNetworkPermission {
    const val ANDROID_17_API_LEVEL = 37
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun requiresRuntimeGrant(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= ANDROID_17_API_LEVEL

    fun isGranted(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        if (!requiresRuntimeGrant(sdkInt)) return true
        return ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}
