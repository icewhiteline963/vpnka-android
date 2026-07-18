package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Download an update APK and hand it to Android's package installer.
 *
 * Upstream's update dialog opens the download URL in a browser, which for us
 * means: leave the app, find the file in Downloads, tap it, answer the
 * unknown-sources prompt. Most people don't finish that, so fixes we ship
 * never reach the phones that need them. This does the same thing in one tap.
 *
 * We can only offer, never install: Android always shows its own confirmation
 * and refuses anything not signed with the key already on the device.
 */
object ApkUpdateInstaller {

    /**
     * Where the APK may come from. The URL arrives from a manifest we fetch
     * over the network, so it is not automatically ours — a hijacked DNS
     * answer or a tampered mirror could point it anywhere. Android's
     * signature check is the real backstop (an APK signed with another key
     * cannot update this one), but there is no reason to download a stranger's
     * file at all.
     */
    private const val ALLOWED_HOST_SUFFIX = "vpnka.io"

    /** Bounded so a redirect loop or a wrong URL can't fill the user's disk. */
    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    class UpdateError(message: String) : Exception(message)

    /** Version whose APK is sitting in the cache, ready to install. */
    private const val KEY_READY_VERSION = "vpnka_update_ready_version"

    private fun cacheFile(context: Context): File =
        File(File(context.cacheDir, "update"), "vpnka-update.apk")

    /**
     * An already-downloaded update, or null.
     *
     * This is what turns "tap, then wait out 32 MB" into "tap, install" —
     * the prefetcher does the waiting in the background, on Wi-Fi, before
     * the user ever opens the update screen.
     */
    fun readyUpdate(context: Context): Pair<String, File>? {
        val version = MmkvManager.decodeSettingsString(KEY_READY_VERSION)
            ?.takeIf { it.isNotBlank() } ?: return null
        val file = cacheFile(context)
        if (!file.exists() || file.length() == 0L) {
            // Cache was cleared under us (Android does this freely) — forget
            // the claim rather than offering an install that would fail.
            MmkvManager.encodeSettings(KEY_READY_VERSION, "")
            return null
        }
        return version to file
    }

    fun markReady(version: String) {
        MmkvManager.encodeSettings(KEY_READY_VERSION, version)
    }

    /** After a successful install, or when the file is stale. */
    fun clearReady(context: Context) {
        MmkvManager.encodeSettings(KEY_READY_VERSION, "")
        runCatching { cacheFile(context).delete() }
    }

    /**
     * True when Android will let us launch the installer at all.
     *
     * From Oreo on, "install unknown apps" is granted per-app, and the user
     * has to do it in Settings — we can't prompt inline.
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user grants us that permission. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    /**
     * Fetch [url] into the cache and return the file.
     *
     * @param onProgress 0..100, or -1 while the total size is unknown (the
     *        mirror may answer without Content-Length).
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val host = runCatching { URL(url).host }.getOrNull()?.lowercase()
            ?: throw UpdateError("Некорректная ссылка на обновление")
        val ours = host == ALLOWED_HOST_SUFFIX || host.endsWith(".$ALLOWED_HOST_SUFFIX")
        if (!ours || !url.startsWith("https://")) {
            LogUtil.w(AppConfig.TAG, "Refusing update download from $host")
            throw UpdateError("Обновление не с нашего сервера — скачивание отменено")
        }

        // Its own directory so the cleanup below can't touch anything else
        // that happens to live in the cache.
        val target = cacheFile(context)
        target.parentFile?.mkdirs()
        target.parentFile?.listFiles()?.forEach { it.delete() }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // No read timeout: this is tens of megabytes over a Russian
            // mobile link, and a slow download is not a failed one.
            .readTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw UpdateError("Сервер ответил ${resp.code}")
            }
            val body = resp.body ?: throw UpdateError("Пустой ответ сервера")
            val total = body.contentLength()
            if (total > MAX_APK_BYTES) {
                throw UpdateError("Файл слишком большой")
            }

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        written += read
                        if (written > MAX_APK_BYTES) {
                            throw UpdateError("Файл слишком большой")
                        }
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt()
                            // Report only on change: a callback per 64 KB
                            // would spend more time recomposing than copying.
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        } else {
                            onProgress(-1)
                        }
                    }
                }
            }
        }

        // A mirror that answers a missing file with an HTML error page would
        // otherwise reach the installer as a "corrupt package" — a confusing
        // way to learn the download was wrong. APKs are zips: check the magic.
        val magic = ByteArray(2)
        target.inputStream().use { it.read(magic) }
        if (magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte()) {
            target.delete()
            throw UpdateError("Скачан не APK — попробуйте позже")
        }

        target
    }

    /**
     * Open Android's installer for [apk].
     *
     * The system takes it from here: it shows what's being installed, checks
     * the signature against the installed app, and asks the user. If they
     * decline, nothing happens — there is no way for us to force it, by design.
     */
    fun promptInstall(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${BuildConfig.APPLICATION_ID}.cache", apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
