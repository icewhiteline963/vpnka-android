package com.v2ray.ang.ui

import android.os.Bundle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.LocalAppSnackbar
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.ApkUpdateInstaller
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CheckUpdateActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        CheckUpdateScreen(onBackClick = { finish() })
    }
}

@Composable
fun CheckUpdateScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalAppSnackbar.current

    var isLoading by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<CheckUpdateResult?>(null) }
    // Downloading an APK over a Russian mobile link is tens of seconds of
    // apparent nothing. Without visible progress people tap again, or leave.
    var downloading by remember { mutableStateOf(false) }
    var downloadPercent by remember { mutableIntStateOf(-1) }

    val versionText = "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})"

    fun checkForUpdates(includePreRelease: Boolean) {
        snackbar.showInfo(context, (R.string.update_checking_for_update))
        isLoading = true
        scope.launch {
            try {
                val result = UpdateCheckerManager.checkForUpdate(includePreRelease)
                if (result.hasUpdate) {
                    updateResult = result
                    showUpdateDialog = true
                } else {
                    snackbar.showSuccess(context, R.string.update_already_latest_version)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to check for updates: ${e.message}")
                if (e.message == null) {
                    snackbar.showError(context, R.string.toast_failure)
                } else {
                    snackbar.showError(e.message.orEmpty())
                }
            } finally {
                isLoading = false
            }
        }
    }

    // Stable only. The pre-release switch offered a choice nobody using
    // this app wants to make, and picking wrong meant an untested build on
    // the one connection someone has to reach anything.
    LaunchedEffect(Unit) { checkForUpdates(false) }

    VpnkaPage(title = "Обновление", onBack = onBackClick) {
        VpnkaCard {
            Text(
                text = "Установленная версия",
                fontSize = 13.sp,
                color = VpnkaColors.TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = versionText,
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 17.sp,
                color = VpnkaColors.TextStrong,
            )
        }
        Spacer(Modifier.height(16.dp))
        VpnkaPrimaryButton(
            text = if (isLoading) "Проверяю…" else "Проверить обновление",
            onClick = { checkForUpdates(false) },
            enabled = !isLoading,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Обновления скачиваются с нашего сервера, а не из Google " +
                "Play — так они доходят и без VPN.",
            fontSize = 13.sp,
            color = VpnkaColors.TextMuted,
        )
    }

    if (downloading) {
        AlertDialog(
            // No dismiss: the download is already running and cancelling the
            // dialog wouldn't stop it, so an X here would just lie.
            onDismissRequest = {},
            title = { Text(stringResource(R.string.vpnka_update_downloading)) },
            text = {
                Text(
                    if (downloadPercent >= 0) "$downloadPercent%"
                    else stringResource(R.string.vpnka_update_downloading_wait)
                )
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (showUpdateDialog && updateResult != null) {
        val result = updateResult!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(stringResource(R.string.update_new_version_found, result.latestVersion ?: "")) },
            text = { Text(result.releaseNotes ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    val url = result.downloadUrl
                    if (url == null) {
                        context.toastError(R.string.toast_failure)
                        return@TextButton
                    }
                    if (!ApkUpdateInstaller.canInstall(context)) {
                        // Per-app permission since Oreo, and only the user can
                        // grant it — so send them to the exact screen rather
                        // than failing silently at the end of a download.
                        context.toast(R.string.vpnka_update_allow_install)
                        context.startActivity(
                            ApkUpdateInstaller.installPermissionIntent(context)
                        )
                        return@TextButton
                    }
                    // Already staged by the background prefetcher: skip
                    // straight to the installer. This is the whole point of
                    // prefetching — the wait happened on Wi-Fi, hours ago.
                    val staged = ApkUpdateInstaller.readyUpdate(context)
                    if (staged != null && staged.first == result.latestVersion) {
                        ApkUpdateInstaller.promptInstall(context, staged.second)
                        return@TextButton
                    }
                    downloading = true
                    downloadPercent = -1
                    scope.launch {
                        try {
                            val apk = ApkUpdateInstaller.download(context, url) { p ->
                                downloadPercent = p
                            }
                            // Remember it: if the user dismisses Android's
                            // install prompt and comes back, they shouldn't
                            // pay for the download twice.
                            result.latestVersion?.let { ApkUpdateInstaller.markReady(it) }
                            ApkUpdateInstaller.promptInstall(context, apk)
                        } catch (e: Exception) {
                            // Show what actually went wrong: "не удалось" with
                            // no reason is the kind of message people just
                            // retry forever.
                            context.toastError(
                                e.message ?: "Не удалось скачать обновление"
                            )
                        } finally {
                            downloading = false
                        }
                    }
                }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
