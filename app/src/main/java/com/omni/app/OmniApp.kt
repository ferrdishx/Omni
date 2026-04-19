package com.omni.app

import android.app.Application
import com.omni.app.data.download.DownloadRepository

class OmniApp : Application() {
    lateinit var downloadRepository: DownloadRepository
        private set

    override fun onCreate() {
        super.onCreate()
        downloadRepository = DownloadRepository(this)
    }
}
