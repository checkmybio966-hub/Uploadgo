package com.uploadgo.app

import android.app.Application

class UploadGoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // Remove extracted ZIP contents older than the retention window.
        AppGraph.tempFileManager.cleanupStale()
    }
}
