package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        val url = if (includePreRelease) {
            AppConfig.APP_API_URL
        } else {
            // A separate constant, not APP_API_URL + "/latest": on our
            // mirror these are two static files, and one can't be both a
            // file and a directory.
            AppConfig.APP_API_LATEST_URL
        }

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
                ?: throw IllegalStateException("Failed to get response")
        }

        val latestRelease = if (includePreRelease) {
            JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
                ?.firstOrNull()
                ?: throw IllegalStateException("No pre-release found")
        } else {
            JsonUtil.fromJsonSafe(response, GitHubRelease::class.java)
        }
        if (latestRelease == null) {
            return@withContext CheckUpdateResult(hasUpdate = false)
        }

        val latestVersion = latestRelease.tagName.removePrefix("v")
        LogUtil.i(
            AppConfig.TAG,
            "Found new version: $latestVersion (current: ${BuildConfig.VERSION_NAME})"
        )

        return@withContext if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
            val downloadUrl = getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS[0])
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = downloadUrl,
                isPreRelease = latestRelease.prerelease
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
    }

    // Общий разбор с ApkUpdateInstaller. Здесь был свой, с `toInt()`:
    // нечисловой кусок в версии («2.9.64.0-rc1») бросал исключение, и
    // фоновая задача обновления уходила в вечный повтор — после
    // четырнадцати дней ожидания Wi-Fi ещё и по мобильному трафику.
    private fun compareVersions(version1: String, version2: String): Int =
        VpnkaLogic.compareVersions(version1, version2)

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        val fDroid = "fdroid"
        val wantFDroid = BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)

        // Из нужной ветки сборки: у нас в релизе лежат ДВА файла с разными
        // package id, и перепутать их значит предложить человеку обновление,
        // которое не встанет поверх установленного.
        fun ofRightFlavour(assets: List<GitHubRelease.Asset>) =
            assets.firstOrNull { it.name.contains(fDroid) == wantFDroid }

        // 1. Точное совпадение по разрядности телефона — самый компактный файл.
        val byAbi = ofRightFlavour(release.assets.filter { it.name.contains(abi, true) })
        if (byAbi != null) {
            return byAbi.browserDownloadUrl
        }

        // 2. Иначе — универсальный. Ради этого он и собирается.
        //
        // Без этого шага 32-битные телефоны НЕ МОГЛИ обновиться вообще: в
        // релиз выкладываются только `universal` и `arm64-v8a`, поиск шёл
        // строго по `Build.SUPPORTED_ABIS[0]`, на armeabi-v7a список
        // оказывался пустым и обновление падало с «No compatible APK found».
        // Молча: человек просто годами сидел на старой версии.
        val universal = ofRightFlavour(release.assets.filter { it.name.contains("universal", true) })
        if (universal != null) {
            return universal.browserDownloadUrl
        }

        throw IllegalStateException("No compatible APK found for abi=$abi")
    }
}
