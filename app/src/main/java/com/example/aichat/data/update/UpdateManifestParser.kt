package com.example.aichat.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.util.Locale

/** Parses and validates the small JSON document served by the update endpoint. */
object UpdateManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Expected document shape:
     *
     *     {
     *       "versionCode": 2,
     *       "versionName": "1.1",
     *       "downloadUrl": "https://host/app.apk",
     *       "sha256": "...64 hex characters...",
     *       "releaseNotes": "..."
     *     }
     *
     * Numeric values encoded as JSON strings are accepted as a convenience for
     * simple static hosting, while all other fields remain strictly validated.
     */
    fun parse(document: String, requireHttps: Boolean = true): AppUpdateInfo {
        val root = runCatching { json.parseToJsonElement(document) }
            .getOrElse { failure ->
                throw AppUpdateException(
                    UpdateErrorKind.INVALID_MANIFEST,
                    "更新清单不是有效的 JSON",
                    cause = failure,
                )
            }
        val objectValue = root as? JsonObject
            ?: throw invalid("更新清单必须是 JSON 对象")

        val versionCode = requiredPrimitive(objectValue, "versionCode", "version_code")
            .content
            .trim()
            .toLongOrNull()
            ?.takeIf { it > 0L }
            ?: throw invalid("更新清单中的 versionCode 无效")
        val versionName = requiredString(objectValue, "versionName", "version_name")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: throw invalid("更新清单中的 versionName 不能为空")
        val downloadUrl = requiredString(objectValue, "downloadUrl", "download_url")
            .trim()
            .also { validateUrl(it, requireHttps) }
        val sha256 = requiredString(objectValue, "sha256", "sha-256")
            .trim()
            .lowercase(Locale.ROOT)
            .also(::validateSha256)
        val releaseNotes = objectValue["releaseNotes"]
            ?.let { element ->
                if (element is JsonNull) "" else element.asText("releaseNotes")
            }
            ?.trim()
            .orEmpty()

        return AppUpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            downloadUrl = downloadUrl,
            sha256 = sha256,
            releaseNotes = releaseNotes,
        )
    }

    /** Validates an update object supplied by a source other than [parse]. */
    fun validate(info: AppUpdateInfo, requireHttps: Boolean = true): AppUpdateInfo {
        if (info.versionCode <= 0L) throw invalid("更新版本号无效")
        if (info.versionName.trim().isEmpty()) throw invalid("更新版本名称不能为空")
        validateUrl(info.downloadUrl, requireHttps)
        validateSha256(info.sha256)
        return info.copy(
            versionName = info.versionName.trim(),
            downloadUrl = info.downloadUrl.trim(),
            sha256 = info.sha256.trim().lowercase(Locale.ROOT),
            releaseNotes = info.releaseNotes.trim(),
        )
    }

    /** HTTPS is required for both the manifest and the APK download. */
    fun validateHttpsUrl(value: String): String {
        return validateUrl(value, requireHttps = true)
    }

    /**
     * Validates an absolute URL. The relaxed mode exists only for local tests;
     * production callers should keep the default HTTPS-only behavior.
     */
    fun validateUrl(value: String, requireHttps: Boolean = true): String {
        val trimmed = value.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri == null || !uri.isAbsolute ||
            (requireHttps && !uri.scheme.equals("https", ignoreCase = true)) ||
            (!requireHttps && !uri.scheme.equals("https", ignoreCase = true) &&
                !uri.scheme.equals("http", ignoreCase = true)) ||
            uri.host.isNullOrBlank() || uri.userInfo != null
        ) {
            throw AppUpdateException(
                UpdateErrorKind.INVALID_URL,
                if (requireHttps) "更新地址必须是有效的 HTTPS 地址" else "更新地址无效",
            )
        }
        return trimmed
    }

    fun validateSha256(value: String): String {
        val normalized = value.trim().lowercase(Locale.ROOT)
        if (!SHA256_PATTERN.matches(normalized)) {
            throw invalid("更新清单中的 sha256 必须是 64 位十六进制字符串")
        }
        return normalized
    }

    private fun requiredPrimitive(objectValue: JsonObject, vararg names: String): JsonPrimitive {
        val element = names.asSequence()
            .mapNotNull { objectValue[it] }
            .firstOrNull()
            ?: throw invalid("更新清单缺少字段：${names.first()}")
        return element as? JsonPrimitive ?: throw invalid("更新清单字段 ${names.first()} 类型无效")
    }

    private fun requiredString(objectValue: JsonObject, vararg names: String): String {
        val primitive = requiredPrimitive(objectValue, *names)
        if (!primitive.isString) throw invalid("更新清单字段 ${names.first()} 必须是字符串")
        return primitive.content
    }

    private fun JsonElement.asText(field: String): String? {
        val primitive = this as? JsonPrimitive
            ?: throw invalid("更新清单字段 $field 类型无效")
        if (!primitive.isString) throw invalid("更新清单字段 $field 必须是字符串")
        return primitive.content
    }

    private fun invalid(message: String): AppUpdateException =
        AppUpdateException(UpdateErrorKind.INVALID_MANIFEST, message)

    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
}
