package com.uploadgo.app

import android.content.Context
import com.uploadgo.app.data.HistoryRepository
import com.uploadgo.app.data.SessionStore
import com.uploadgo.app.data.ShareService
import com.uploadgo.app.data.db.AppDatabase
import com.uploadgo.app.data.media.MediaRepository
import com.uploadgo.app.data.prefs.SettingsRepository
import com.uploadgo.app.data.share.ShareManager
import com.uploadgo.app.data.temp.TempFileManager
import com.uploadgo.app.data.zip.ZipRepository

/**
 * Simple process-scoped service locator. Everything is wired once in
 * [UploadGoApp.onCreate] and survives activity recreation.
 */
object AppGraph {

    lateinit var mediaRepository: MediaRepository
        private set
    lateinit var zipRepository: ZipRepository
        private set
    lateinit var tempFileManager: TempFileManager
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var historyRepository: HistoryRepository
        private set
    lateinit var shareManager: ShareManager
        private set
    lateinit var sessionStore: SessionStore
        private set
    lateinit var shareService: ShareService
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        mediaRepository = MediaRepository(appContext)
        zipRepository = ZipRepository(appContext)
        tempFileManager = TempFileManager(appContext)
        settingsRepository = SettingsRepository(appContext)
        historyRepository = HistoryRepository(AppDatabase.get(appContext).historyDao())
        shareManager = ShareManager(appContext)
        sessionStore = SessionStore(zipRepository, tempFileManager, historyRepository)
        shareService = ShareService(sessionStore, shareManager, historyRepository, tempFileManager, settingsRepository)
    }
}
