package com.example.aichat.data.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.aichat.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Checks for and prepares signed APK updates without touching chat data.
 *
 * The manager deliberately does not start an Activity itself. A settings
 * screen can collect [download] and launch the [InstallPreparation] intent on
 * the main thread when the user explicitly chooses to install.
 */
class AppUpdateManager(
    context: Context,
    private val configStore: UpdateConfigStore = UpdateConfigStore(context),
    httpClient: OkHttpClient = defaultHttpClient(),
    private val currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
    /** Only set this in local tests; production must use HTTPS. */
    private val allowInsecureHttpForTests: Boolean = false,
) {
    private val appContext = context.applicationContext
    private val httpClient = httpClient.securedForUpdates(allowInsecureHttpForTests)

    /** Directory used for temporary and verified update APKs. */
    val updateDirectory: File
        get() = File(appContext.cacheDir, UPDATE_DIRECTORY_NAME)

    /**
     * Checks the configured manifest, returning a [Result] so a ViewModel can
     * display a readable error without catching implementation exceptions.
     * Pass [manifestUrl] to override the DataStore value for one request.
     */
    suspend fun checkForUpdate(manifestUrl: String? = null): Result<UpdateCheckResult> = try {
        Result.success(checkForUpdateOrThrow(manifestUrl))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure.asUpdateException())
    }

    /** Same operation as [checkForUpdate], throwing a typed exception on error. */
    suspend fun checkForUpdateOrThrow(manifestUrl: String? = null): UpdateCheckResult =
        withContext(Dispatchers.IO) {
        val url = resolveManifestUrl(manifestUrl)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        val call = httpClient.newCall(request)
        val cancellationSignal = call.cancellationSignal(coroutineContext[Job])
        try {
            val response = execute(call)
            response.use {
                ensureSecureUpdateResponse(it, allowInsecureHttpForTests)
                if (!it.isSuccessful) throw parseHttpError(it)
                val body = it.body ?: throw AppUpdateException(
                    UpdateErrorKind.NETWORK,
                    "更新服务没有返回内容",
                    it.code,
                )
                if (body.contentLength() > MAX_MANIFEST_BYTES) {
                    throw AppUpdateException(UpdateErrorKind.INVALID_MANIFEST, "更新清单过大")
                }
                val manifest = try {
                    readManifest(body)
                } catch (failure: AppUpdateException) {
                    throw failure
                } catch (failure: IOException) {
                    throw AppUpdateException(UpdateErrorKind.NETWORK, "无法读取更新清单", cause = failure)
                }
                val info = try {
                    UpdateManifestParser.parse(
                        manifest,
                        requireHttps = !allowInsecureHttpForTests,
                    )
                } catch (failure: AppUpdateException) {
                    throw failure
                } catch (failure: Throwable) {
                    throw AppUpdateException(
                        UpdateErrorKind.INVALID_MANIFEST,
                        "更新清单格式无效",
                        cause = failure,
                    )
                }
                if (info.versionCode <= currentVersionCode) {
                    return@withContext UpdateCheckResult.UpToDate(
                        currentVersionCode = currentVersionCode,
                        latestVersionCode = info.versionCode,
                        latestVersionName = info.versionName,
                    )
                }
                return@withContext UpdateCheckResult.Available(info)
            }
        } finally {
            // Keep cancellation wired up until the response body is fully consumed.
            cancellationSignal.complete()
        }
    }

    /** Alias useful to callers that prefer a shorter method name. */
    suspend fun check(manifestUrl: String? = null): UpdateCheckResult =
        checkForUpdateOrThrow(manifestUrl)

    /**
     * Downloads and verifies an APK, emitting a terminal [Ready] or [Failed]
     * state. Cancellation stops the underlying OkHttp call and removes the
     * partial file.
     */
    fun download(info: AppUpdateInfo): Flow<UpdateDownloadState> = channelFlow {
        send(UpdateDownloadState.Preparing)
        try {
            val verified = downloadApkOrThrow(info) { progress ->
                send(UpdateDownloadState.Downloading(progress))
            }
            send(UpdateDownloadState.Ready(verified))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            send(UpdateDownloadState.Failed(failure.asUpdateException()))
        }
    }

    /**
     * Downloads an update and returns a verified file. The callback is invoked
     * on a background dispatcher and can be used to update UI state.
     */
    suspend fun downloadApk(
        info: AppUpdateInfo,
        onProgress: suspend (UpdateDownloadProgress) -> Unit = {},
    ): Result<File> = try {
        Result.success(downloadApkOrThrow(info, onProgress))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure.asUpdateException())
    }

    /** Throwing variant used by [download] and callers that prefer exceptions. */
    suspend fun downloadApkOrThrow(
        info: AppUpdateInfo,
        onProgress: suspend (UpdateDownloadProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val validated = try {
            UpdateManifestParser.validate(info, requireHttps = !allowInsecureHttpForTests)
        } catch (failure: AppUpdateException) {
            throw failure
        } catch (failure: Throwable) {
            throw AppUpdateException(UpdateErrorKind.INVALID_MANIFEST, "更新信息无效", cause = failure)
        }
        val directory = updateDirectory.apply { mkdirs() }
        if (!directory.isDirectory) {
            throw AppUpdateException(UpdateErrorKind.IO, "无法创建更新缓存目录")
        }
        val target = File(directory, "ai-chat-${validated.versionCode}.apk")
        // Some Android PackageManager implementations only inspect files ending in .apk.
        val partial = File.createTempFile("ai-chat-update-", ".download.apk", directory)
        try {
            val request = Request.Builder()
                .url(validated.downloadUrl)
                .header("Accept", "application/vnd.android.package-archive")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val call = httpClient.newCall(request)
            val cancellationSignal = call.cancellationSignal(coroutineContext[Job])
            try {
                val response = execute(call)
                response.use {
                    ensureSecureUpdateResponse(it, allowInsecureHttpForTests)
                    if (!it.isSuccessful) throw parseHttpError(it)
                    val body = it.body ?: throw AppUpdateException(
                        UpdateErrorKind.NETWORK,
                        "更新服务没有返回 APK",
                        it.code,
                    )
                    val total = body.contentLength().takeIf { length -> length >= 0L }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    onProgress(UpdateDownloadProgress(downloadedBytes = 0L, totalBytes = total))
                    body.byteStream().use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                downloaded += read.toLong()
                                onProgress(
                                    UpdateDownloadProgress(
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                    ),
                                )
                            }
                            output.flush()
                        }
                    }
                    if (downloaded <= 0L) {
                        throw AppUpdateException(UpdateErrorKind.IO, "下载的 APK 为空")
                    }
                    val actualHash = digest.digest().toHexString()
                    if (!MessageDigest.isEqual(
                            actualHash.toByteArray(Charsets.US_ASCII),
                            validated.sha256.lowercase(Locale.ROOT).toByteArray(Charsets.US_ASCII),
                        )
                    ) {
                        throw AppUpdateException(
                            UpdateErrorKind.INTEGRITY,
                            "更新文件校验失败，已删除不完整文件",
                        )
                    }
                    validateDownloadedPackage(partial, validated)
                }
            } finally {
                // Keep cancellation wired up until the APK body is fully consumed.
                cancellationSignal.complete()
            }
            moveIntoPlace(partial, target)
            target
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppUpdateException) {
            throw failure
        } catch (failure: IOException) {
            if (!coroutineContext.isActive) {
                throw CancellationException("更新下载已取消", failure)
            }
            throw AppUpdateException(
                UpdateErrorKind.NETWORK,
                failure.message ?: "下载更新失败",
                cause = failure,
            )
        } catch (failure: Throwable) {
            throw AppUpdateException(UpdateErrorKind.IO, failure.message ?: "保存更新失败", cause = failure)
        } finally {
            // A verified file is moved above; this only removes partial data.
            partial.delete()
        }
    }

    /**
     * Builds the Android package-installer request for a verified APK. The
     * file must be inside this manager's cache update directory, which is also
     * the only directory exposed by the FileProvider below.
     */
    fun prepareInstall(apk: File): InstallPreparation {
        val verifiedFile = validateApkFile(apk)
        val installIntent = createInstallIntent(verifiedFile)
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            return InstallPreparation.PermissionRequired(
                apk = verifiedFile,
                settingsIntent = createUnknownSourcesSettingsIntent(),
                installIntent = installIntent,
            )
        }
        return InstallPreparation.Ready(verifiedFile, installIntent)
    }

    /** Creates an ACTION_VIEW intent consumed by Android's Package Installer. */
    fun createInstallIntent(apk: File): Intent {
        val verifiedFile = validateApkFile(apk)
        // Keep this public helper safe even when callers do not go through
        // prepareInstall first. Only a package-compatible, newer, signed APK
        // may be handed to the system installer.
        validateDownloadedPackage(verifiedFile, expected = null)
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            verifiedFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("APK update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Opens the per-app setting required on Android 8.0+ for sideloading. */
    fun createUnknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        )

    /** Removes only a downloaded update file; chat and provider data are untouched. */
    fun deleteDownloadedApk(apk: File): Boolean {
        val validated = validateApkFile(apk)
        return validated.delete()
    }

    private suspend fun resolveManifestUrl(override: String?): String {
        val candidate = override?.trim().orEmpty().ifEmpty {
            configStore.readManifestUrl().trim().ifEmpty {
                BuildConfig.UPDATE_MANIFEST_URL.trim()
            }
        }
        if (candidate.isEmpty()) {
            throw AppUpdateException(
                UpdateErrorKind.NOT_CONFIGURED,
                "请先在设置中填写更新清单地址",
            )
        }
        return UpdateManifestParser.validateUrl(
            candidate,
            requireHttps = !allowInsecureHttpForTests,
        )
    }

    private suspend fun execute(call: okhttp3.Call): Response {
        return try {
            call.execute()
        } catch (failure: AppUpdateException) {
            throw failure
        } catch (failure: IOException) {
            if (!coroutineContext.isActive) {
                throw CancellationException("更新检查已取消", failure)
            }
            throw AppUpdateException(UpdateErrorKind.NETWORK, failure.message ?: "网络连接失败", cause = failure)
        }
    }

    private fun okhttp3.Call.cancellationSignal(parent: Job?): kotlinx.coroutines.CompletableJob =
        Job(parent).also { signal ->
            signal.invokeOnCompletion { failure ->
                if (failure is CancellationException) cancel()
            }
        }

    private fun parseHttpError(response: Response): AppUpdateException {
        val kind = when (response.code) {
            401, 403 -> UpdateErrorKind.UNAUTHORIZED
            429 -> UpdateErrorKind.RATE_LIMITED
            in 500..599 -> UpdateErrorKind.SERVER
            else -> UpdateErrorKind.NETWORK
        }
        val message = when (response.code) {
            401, 403 -> "更新服务拒绝访问"
            429 -> "更新服务请求过于频繁，请稍后再试"
            in 500..599 -> "更新服务暂时不可用"
            else -> "更新服务请求失败（HTTP ${response.code}）"
        }
        return AppUpdateException(kind, message, response.code)
    }

    private fun validateApkFile(apk: File): File {
        val canonical = runCatching { apk.canonicalFile }.getOrElse {
            throw AppUpdateException(UpdateErrorKind.IO, "更新文件路径无效", cause = it)
        }
        val root = runCatching { updateDirectory.canonicalFile }.getOrElse {
            throw AppUpdateException(UpdateErrorKind.IO, "更新缓存目录无效", cause = it)
        }
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        if (!canonical.isFile || !canonical.path.startsWith(rootPath)) {
            throw AppUpdateException(UpdateErrorKind.IO, "更新文件不在应用缓存目录中")
        }
        return canonical
    }

    private fun readManifest(body: ResponseBody): String {
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > MAX_MANIFEST_BYTES) {
                    throw AppUpdateException(UpdateErrorKind.INVALID_MANIFEST, "更新清单过大")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(charset.name())
    }

    private fun validateDownloadedPackage(apk: File, expected: AppUpdateInfo?) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = appContext.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw AppUpdateException(UpdateErrorKind.INTEGRITY, "下载文件不是有效的 Android 安装包")
        if (archive.packageName != appContext.packageName) {
            throw AppUpdateException(UpdateErrorKind.INTEGRITY, "更新包的应用标识不匹配")
        }
        if (expected != null && archive.longVersionCode != expected.versionCode) {
            throw AppUpdateException(UpdateErrorKind.INTEGRITY, "更新包版本号与更新清单不匹配")
        }
        if (archive.longVersionCode <= currentVersionCode) {
            throw AppUpdateException(UpdateErrorKind.INTEGRITY, "更新包版本不高于当前版本")
        }
        val installed = appContext.packageManager.getPackageInfo(appContext.packageName, flags)
        if (!hasCompatibleUpdateSignature(installed.signingIdentity(), archive.signingIdentity())) {
            throw AppUpdateException(UpdateErrorKind.INTEGRITY, "更新包签名与当前应用不一致")
        }
    }

    private fun android.content.pm.PackageInfo.signingIdentity(): UpdateSigningIdentity {
        val info = signingInfo ?: return UpdateSigningIdentity(
            hasMultipleSigners = false,
            currentSignerDigests = emptySet(),
            signingHistoryDigests = emptySet(),
        )
        val current = info.apkContentsSigners.orEmpty().digestSet()
        val history = if (info.hasMultipleSigners()) {
            current
        } else {
            info.signingCertificateHistory.orEmpty().digestSet()
        }
        return UpdateSigningIdentity(
            hasMultipleSigners = info.hasMultipleSigners(),
            currentSignerDigests = current,
            signingHistoryDigests = history,
        )
    }

    private fun Array<out android.content.pm.Signature>.digestSet(): Set<String> =
        mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHexString()
        }

    private fun moveIntoPlace(partial: File, target: File) {
        if (target.exists() && !target.delete()) {
            throw AppUpdateException(UpdateErrorKind.IO, "无法替换旧的更新文件")
        }
        if (!partial.renameTo(target)) {
            try {
                partial.copyTo(target, overwrite = false)
                if (!partial.delete()) {
                    // The verified target is still safe; leaving a harmless temporary APK
                    // file is preferable to deleting the newly downloaded APK.
                }
            } catch (failure: IOException) {
                target.delete()
                throw AppUpdateException(UpdateErrorKind.IO, "无法保存更新文件", cause = failure)
            }
        }
    }

    private fun Throwable.asUpdateException(): AppUpdateException = when (this) {
        is AppUpdateException -> this
        is IOException -> AppUpdateException(UpdateErrorKind.NETWORK, message ?: "网络连接失败", cause = this)
        else -> AppUpdateException(UpdateErrorKind.IO, message ?: "更新操作失败", cause = this)
    }

    private companion object {
        const val UPDATE_DIRECTORY_NAME = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DOWNLOAD_BUFFER_SIZE = 32 * 1024
        const val MAX_MANIFEST_BYTES = 1024L * 1024L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        fun ByteArray.toHexString(): String = buildString(size * 2) {
            for (value in this@toHexString) {
                append(((value.toInt() ushr 4) and 0x0f).toString(16))
                append((value.toInt() and 0x0f).toString(16))
            }
        }
    }
}
