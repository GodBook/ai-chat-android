package com.example.aichat.data.update

import android.content.Intent
import java.io.File
import java.io.IOException

/**
 * An update entry published by the application's update manifest.
 *
 * The SHA-256 value is normalized by [UpdateManifestParser] before it is
 * returned to callers, so consumers can compare it without worrying about
 * upper/lower-case hex characters.
 */
data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val releaseNotes: String = "",
)

sealed interface UpdateCheckResult {
    /** A newer APK is available for download. */
    data class Available(val update: AppUpdateInfo) : UpdateCheckResult

    /** The manifest was valid, but it does not contain a newer version. */
    data class UpToDate(
        val currentVersionCode: Long,
        val latestVersionCode: Long,
        val latestVersionName: String,
    ) : UpdateCheckResult
}

// Friendly aliases for callers that prefer the shorter names.
typealias UpdateAvailable = UpdateCheckResult.Available
typealias NoUpdate = UpdateCheckResult.UpToDate

enum class UpdateErrorKind {
    NOT_CONFIGURED,
    INVALID_URL,
    INVALID_MANIFEST,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    INTEGRITY,
    IO,
    INSTALL_PERMISSION,
}

/** A user-safe error raised while checking or preparing an update. */
class AppUpdateException(
    val kind: UpdateErrorKind,
    override val message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

typealias UpdateException = AppUpdateException

/** Progress emitted while an APK is being downloaded. */
data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
) {
    /** A value in [0, 1], or null when the server omitted Content-Length. */
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

sealed interface UpdateDownloadState {
    data object Preparing : UpdateDownloadState

    data class Downloading(val progress: UpdateDownloadProgress) : UpdateDownloadState

    data class Ready(val apk: File) : UpdateDownloadState

    data class Failed(val error: AppUpdateException) : UpdateDownloadState
}

/**
 * Result of [AppUpdateManager.prepareInstall].
 *
 * When [PermissionRequired] is returned, launch [settingsIntent] first. Once
 * the user enables "允许安装未知应用", call [AppUpdateManager.prepareInstall]
 * again and launch the returned install intent.
 */
sealed interface InstallPreparation {
    data class Ready(
        val apk: File,
        val intent: Intent,
    ) : InstallPreparation

    data class PermissionRequired(
        val apk: File,
        val settingsIntent: Intent,
        val installIntent: Intent,
    ) : InstallPreparation
}
