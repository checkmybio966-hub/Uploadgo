package com.uploadgo.app.util

/**
 * App-wide constants. The FileProvider authority must match the
 * `${applicationId}.fileprovider` entry declared in the AndroidManifest.
 */
object AppConstants {
    const val FILE_PROVIDER_AUTHORITY = "com.uploadgo.app.fileprovider"

    /** How long extracted ZIP contents remain available after being shared. */
    const val SHARE_GRACE_MILLIS = 5 * 60 * 1000L

    /** Extracted files older than this are removed on app start. */
    const val STALE_TEMP_MILLIS = 24 * 60 * 60 * 1000L
}
