package org.shirakawatyu.yamibo.novel.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.shirakawatyu.yamibo.novel.BuildConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long?,
    val releaseNotes: String,
    val apkUrl: String,
    val releasePageUrl: String
)

/** APK 下载进度：当前来源、第几个源、已下载/总字节。totalBytes <= 0 表示未知。 */
data class AppDownloadProgress(
    val sourceLabel: String,
    val sourceIndex: Int,
    val sourceCount: Int,
    val downloadedBytes: Long,
    val totalBytes: Long
)

sealed interface AppUpdateCheckResult {
    data object NoUpdate : AppUpdateCheckResult
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateCheckResult
    /** [isRateLimited] 为 true 表示 GitHub 明确返回 403/429（限流），而非网络不通。 */
    data class Failed(val reason: String, val isRateLimited: Boolean = false) : AppUpdateCheckResult
}

object AppUpdateManager {
    const val RELEASES_PAGE_URL =
        "https://github.com/KrelinnBios/YamiboPlus/releases"
    const val RELEASES_API_URL =
        "https://api.github.com/repos/KrelinnBios/YamiboPlus/releases?per_page=20"

    // 下载源回退顺序：先 GitHub 直链，连不上再依次试镜像。镜像是「前缀代理」——把完整
    // GitHub 直链接在镜像域名后即可（如 https://gh.llkk.cc/https://github.com/.../300-Plus.apk）。
    // 某镜像失效时改这里即可，无需动下载逻辑。
    internal val DOWNLOAD_MIRROR_PREFIXES = listOf(
        "https://ghproxy.net/",
        "https://gh-proxy.com/",
        "https://gh.llkk.cc/"
    )

    /** 构造下载候选链：GitHub 直链优先，其后是各镜像前缀拼接的同一直链。 */
    internal fun buildDownloadCandidates(apkUrl: String): List<String> {
        val candidates = mutableListOf(apkUrl)
        if (apkUrl.startsWith("https://github.com/", ignoreCase = true)) {
            DOWNLOAD_MIRROR_PREFIXES.forEach { prefix -> candidates.add(prefix + apkUrl) }
        }
        return candidates.distinct()
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val MAX_APK_SIZE_BYTES = 512L * 1024L * 1024L

    // 自动检查的最小间隔：GitHub 未认证接口限流约每小时 60 次（按 IP 计），频繁启动会触发 403。
    private val lastAutoCheckKey = longPreferencesKey("last_auto_update_check_ms")
    private const val AUTO_CHECK_MIN_INTERVAL_MS = 6L * 60 * 60 * 1000

    private val client by lazy {
        OkHttpClient.Builder()
            .dns(YamiboRetrofit.okHttpClient.dns)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .callTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // 多源下载专用客户端：让失效/卡死的镜像快速失败，避免逐个长时间等待。
    // 连接 8s（连不上的镜像很快放弃）、读取间隔 30s（连上却不吐数据的卡死也快放弃）、
    // 单次整体上限 4min（足够在慢网下载完整 APK）。真正可用的源不受影响。
    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .dns(YamiboRetrofit.okHttpClient.dns)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * 启动时的自动检查：带节流。距上次自动检查不足 [AUTO_CHECK_MIN_INTERVAL_MS] 时返回 null（跳过），
     * 避免频繁启动把 GitHub 未认证接口的限流额度用尽（403）。
     * 只有拿到确定结论（有更新/无更新）才记录时间戳；网络失败不占用节流窗口，下次启动会重试。
     * 例外：GitHub 明确返回限流（403/429）时也记录时间戳——此时重试只会加剧限流，等满一个窗口再试。
     * 失败由调用方静默处理；需要明确反馈时走用户手动的 [checkForUpdate]。
     */
    suspend fun checkForUpdateAuto(): AppUpdateCheckResult? = withContext(Dispatchers.IO) {
        val store = GlobalData.dataStore
        if (store != null) {
            val last = runCatching {
                store.data.firstOrNull()?.get(lastAutoCheckKey)
            }.getOrNull() ?: 0L
            if (System.currentTimeMillis() - last < AUTO_CHECK_MIN_INTERVAL_MS) {
                return@withContext null
            }
        }
        val result = checkForUpdate()
        if (store != null &&
            (result !is AppUpdateCheckResult.Failed || result.isRateLimited)
        ) {
            runCatching { store.edit { it[lastAutoCheckKey] = System.currentTimeMillis() } }
        }
        result
    }

    suspend fun checkForUpdate(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.APP_UPDATE_URL.trim().ifBlank { RELEASES_API_URL }
        val requestUrl = if ('?' in endpoint) {
            "$endpoint&_=${System.currentTimeMillis()}"
        } else {
            "$endpoint?_=${System.currentTimeMillis()}"
        }

        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "YamiboPlus/${BuildConfig.VERSION_NAME}")
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val rateLimited = response.code == 403 || response.code == 429
                    val reason = when {
                        rateLimited -> "GitHub 接口暂时不可用（${response.code}，通常是短时间内请求过于频繁触发限流），请稍后再试或前往 Releases 页面手动查看"
                        response.code == 404 -> "仓库暂无正式发布版本"
                        else -> "GitHub 返回 HTTP ${response.code}"
                    }
                    return@withContext AppUpdateCheckResult.Failed(reason, rateLimited)
                }

                val json = response.body?.string().orEmpty()
                val info = parseReleaseJson(json)
                    ?: return@withContext AppUpdateCheckResult.Failed(
                        "最新版本信息中没有可下载的 APK"
                    )

                if (compareVersions(info.versionName, BuildConfig.VERSION_NAME) <= 0) {
                    AppUpdateCheckResult.NoUpdate
                } else {
                    AppUpdateCheckResult.UpdateAvailable(info)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppUpdateCheckResult.Failed(error.message ?: "无法连接 GitHub")
        }
    }

    suspend fun downloadAndOpenInstaller(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (AppDownloadProgress) -> Unit = {}
    ): Result<Unit> {
        val appContext = context.applicationContext
        return try {
            val apkFile = withContext(Dispatchers.IO) {
                downloadAndValidateApk(appContext, info, onProgress)
            }
            withContext(Dispatchers.Main) {
                openInstaller(appContext, apkFile)
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun openReleasesPage(context: Context): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    internal fun parseReleaseJson(json: String): AppUpdateInfo? {
        val parsed = runCatching { JSON.parse(json) }.getOrNull() ?: return null

        return when (parsed) {
            is JSONArray -> {
                val releases = buildList {
                    for (index in 0 until parsed.size) {
                        val release = parsed.getJSONObject(index) ?: continue
                        parseReleaseObject(release)?.let(::add)
                    }
                }

                releases.maxWithOrNull { left, right ->
                    compareVersions(left.versionName, right.versionName)
                }
            }

            is JSONObject -> parseReleaseObject(parsed)

            else -> null
        }
    }

    private fun parseReleaseObject(root: JSONObject): AppUpdateInfo? {
        if (root.getBooleanValue("draft") || root.getBooleanValue("prerelease")) return null

        val latestVersion = (
            root.getString("tag_name")
                ?: root.getString("versionName")
                ?: root.getString("version")
            )
            ?.removePrefix("v")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null

        val assets = root.getJSONArray("assets")
        var apkUrl: String? = null
        var versionCode: Long? = null

        if (assets != null) {
            for (index in 0 until assets.size) {
                val asset = assets.getJSONObject(index) ?: continue
                val assetName = asset.getString("name").orEmpty()
                val contentType = asset.getString("content_type").orEmpty()

                if (assetName.endsWith(".apk", ignoreCase = true) ||
                    contentType.equals(APK_MIME_TYPE, ignoreCase = true)
                ) {
                    apkUrl = asset.getString("browser_download_url")
                    versionCode = asset.getLong("versionCode")
                    break
                }
            }
        }

        apkUrl = apkUrl
            ?: root.getString("downloadUrl")?.takeIf { it.endsWith(".apk", true) }
            ?: return null

        return AppUpdateInfo(
            versionName = latestVersion,
            versionCode = versionCode ?: root.getLong("versionCode"),
            releaseNotes = root.getString("body")
                ?: root.getString("releaseNotes")
                ?: "",
            apkUrl = apkUrl,
            releasePageUrl = root.getString("html_url")
                ?: root.getString("releaseUrl")
                ?: RELEASES_PAGE_URL
        )
    }

    private fun downloadAndValidateApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (AppDownloadProgress) -> Unit = {}
    ): File {
        val updateDir = File(context.externalCacheDir ?: context.cacheDir, "update")
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            throw IOException("无法创建更新缓存目录")
        }

        updateDir.listFiles()
            ?.filter {
                it.name.startsWith("300-Plus-") ||
                    it.name == "300-Plus.apk" ||
                    it.name.startsWith("300 Plus-") ||
                    it.name == "300 Plus.apk"
            }
            ?.forEach { it.delete() }

        val safeVersionName = info.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        // 缓存文件名内嵌版本号，便于排查“缓存里到底是哪一版”。GitHub 发布资产叫 300-Plus.apk。
        val targetFile = File(updateDir, "300-Plus-$safeVersionName.apk")
        val tempFile = File(updateDir, "${targetFile.name}.download")
        tempFile.delete()
        targetFile.delete()

        // 多源下载：GitHub 直链优先，失败按序回退镜像。每个源下载后都严格校验
        // （版本号必须正好等于弹窗展示版本、且高于当前版本、签名一致），任何一个源给到
        // 旧包/错包都会被拒绝并自动换下一个源，从根本上杜绝“显示新版却装到旧版”。
        val candidates = buildDownloadCandidates(info.apkUrl)
        var lastError: Exception? = null

        candidates.forEachIndexed { index, url ->
            tempFile.delete()
            val label = downloadSourceLabel(url, info.apkUrl)
            try {
                downloadApkTo(url, tempFile) { downloaded, total ->
                    onProgress(
                        AppDownloadProgress(
                            sourceLabel = label,
                            sourceIndex = index + 1,
                            sourceCount = candidates.size,
                            downloadedBytes = downloaded,
                            totalBytes = total
                        )
                    )
                }
                validateApk(context, tempFile, info)
            } catch (error: CancellationException) {
                tempFile.delete()
                throw error
            } catch (error: Exception) {
                lastError = error
                tempFile.delete()
                return@forEachIndexed
            }

            targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return targetFile
        }

        throw lastError ?: IOException("所有下载源均不可用")
    }

    /** 下载来源的可读名称：GitHub 直链或镜像域名。 */
    private fun downloadSourceLabel(candidateUrl: String, apkUrl: String): String {
        if (candidateUrl == apkUrl) return "GitHub 直链"
        val host = candidateUrl.removeSuffix(apkUrl)
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        return if (host.isBlank()) "镜像" else "镜像 $host"
    }

    /** 将单个下载源的 APK 写入临时文件；失败抛异常，由调用方决定是否换源。 */
    private fun downloadApkTo(
        apkUrl: String,
        tempFile: File,
        onBytes: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ) {
        val apkDownloadUrl = if ('?' in apkUrl) {
            "$apkUrl&_=${System.currentTimeMillis()}"
        } else {
            "$apkUrl?_=${System.currentTimeMillis()}"
        }

        val request = Request.Builder()
            .url(apkDownloadUrl)
            .header("Accept", APK_MIME_TYPE)
            .header("User-Agent", "YamiboPlus/${BuildConfig.VERSION_NAME}")
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APK 下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("APK 下载内容为空")
            val contentLength = body.contentLength()
            if (contentLength > MAX_APK_SIZE_BYTES) {
                throw IOException("APK 文件大小异常")
            }

            body.byteStream().use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    var lastReported = 0L
                    onBytes(0L, contentLength)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_APK_SIZE_BYTES) {
                            throw IOException("APK 文件大小异常")
                        }
                        output.write(buffer, 0, count)
                        // 每累计约 64KB 回报一次进度，避免过于频繁刷新 UI。
                        if (totalBytes - lastReported >= 64 * 1024) {
                            lastReported = totalBytes
                            onBytes(totalBytes, contentLength)
                        }
                    }
                    if (totalBytes <= 0L || contentLength > 0L && totalBytes != contentLength) {
                        throw IOException("APK 下载不完整")
                    }
                    onBytes(totalBytes, contentLength)
                }
            }
        }
    }

    private fun validateApk(context: Context, apkFile: File, info: AppUpdateInfo) {
        val packageInfoFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(packageInfoFlags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, packageInfoFlags)
        } ?: throw IOException("下载的文件不是有效 APK")

        if (packageInfo.packageName != context.packageName) {
            throw IOException("APK 包名不匹配")
        }

        val downloadedVersionName = packageInfo.versionName.orEmpty()
        if (compareVersions(downloadedVersionName, info.versionName) != 0) {
            throw IOException(
                "APK versionName 与发布信息不一致：下载到 v$downloadedVersionName，预期 v${info.versionName}"
            )
        }

        val downloadedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        if (downloadedVersionCode <= BuildConfig.VERSION_CODE.toLong()) {
            throw IOException("APK versionCode 未高于当前版本")
        }
        if (info.versionCode != null && downloadedVersionCode != info.versionCode) {
            throw IOException("APK versionCode 与发布信息不一致")
        }

        val installedPackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(packageInfoFlags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, packageInfoFlags)
        }
        val installedCertificates = signingCertificateDigests(installedPackageInfo)
        val downloadedCertificates = signingCertificateDigests(packageInfo)
        if (installedCertificates.isNotEmpty() &&
            downloadedCertificates.isNotEmpty() &&
            installedCertificates.intersect(downloadedCertificates).isEmpty()
        ) {
            throw IOException(
                "APK 签名与当前安装版本不一致，请卸载旧版后从下载页重新安装"
            )
        }
    }

    private fun signingCertificateDigests(
        packageInfo: android.content.pm.PackageInfo
    ): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().mapTo(linkedSetOf()) { signature ->
            digest.digest(signature.toByteArray()).joinToString("") { byte ->
                "%02x".format(byte)
            }
        }
    }

    private fun openInstaller(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            throw IOException("系统中没有可用的 APK 安装器")
        }
        context.startActivity(intent)
    }

    internal fun compareVersions(left: String, right: String): Int {
        val leftParts = left.removePrefix("v").split('.', '-', '_')
        val rightParts = right.removePrefix("v").split('.', '-', '_')
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val leftValue = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
            val rightValue = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
            if (leftValue != rightValue) return leftValue.compareTo(rightValue)
        }
        return 0
    }
}
