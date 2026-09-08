package com.aurorashelf.app

import android.app.Application
import com.aurorashelf.app.data.OfflineCacheManager

class AuroraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Recreate Media3's persistent download manager as soon as the app process starts.
        OfflineCacheManager.get(this)
    }
}
