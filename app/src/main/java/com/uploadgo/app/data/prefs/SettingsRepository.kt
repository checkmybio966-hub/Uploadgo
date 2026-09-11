package com.uploadgo.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "uploadgo_settings")

enum class ShareDefaultBehavior { SHARE_SELECTED, SHARE_ZIP_CONTENTS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val defaultBehavior: ShareDefaultBehavior = ShareDefaultBehavior.SHARE_ZIP_CONTENTS,
    val autoExtractZip: Boolean = true,
    val showUnsupported: Boolean = true,
    val deleteTempAfterShare: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Maximum files handed to the Sharesheet in a single batch. Values <= 0
     * mean "all at once". Smaller batches (e.g. 10) avoid errors in apps like
     * Telegram that reject very large single imports.
     */
    val shareBatchSize: Int = 10,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_BEHAVIOR = stringPreferencesKey("default_behavior")
        val AUTO_EXTRACT = booleanPreferencesKey("auto_extract_zip")
        val SHOW_UNSUPPORTED = booleanPreferencesKey("show_unsupported")
        val DELETE_TEMP = booleanPreferencesKey("delete_temp_after_share")
        val THEME = stringPreferencesKey("theme_mode")
        val SHARE_BATCH_SIZE = intPreferencesKey("share_batch_size")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            defaultBehavior = enumValueOrDefault(p[Keys.DEFAULT_BEHAVIOR], ShareDefaultBehavior.SHARE_ZIP_CONTENTS),
            autoExtractZip = p[Keys.AUTO_EXTRACT] ?: true,
            showUnsupported = p[Keys.SHOW_UNSUPPORTED] ?: true,
            deleteTempAfterShare = p[Keys.DELETE_TEMP] ?: true,
            themeMode = enumValueOrDefault(p[Keys.THEME], ThemeMode.SYSTEM),
            shareBatchSize = p[Keys.SHARE_BATCH_SIZE] ?: 10,
        )
    }

    suspend fun setDefaultBehavior(value: ShareDefaultBehavior) =
        context.dataStore.edit { it[Keys.DEFAULT_BEHAVIOR] = value.name }

    suspend fun setAutoExtractZip(value: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_EXTRACT] = value }

    suspend fun setShowUnsupported(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_UNSUPPORTED] = value }

    suspend fun setDeleteTempAfterShare(value: Boolean) =
        context.dataStore.edit { it[Keys.DELETE_TEMP] = value }

    suspend fun setThemeMode(value: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME] = value.name }

    suspend fun setShareBatchSize(value: Int) =
        context.dataStore.edit { it[Keys.SHARE_BATCH_SIZE] = value }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        if (name != null) runCatching { enumValueOf<T>(name) }.getOrDefault(default) else default
}
