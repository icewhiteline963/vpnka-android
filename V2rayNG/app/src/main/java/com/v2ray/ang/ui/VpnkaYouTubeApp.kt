package com.v2ray.ang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.v2ray.ang.handler.YouTubeService
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * YouTube без рекламы и аккаунта Google (NewPipeExtractor). Метаданные и сам
 * видеопоток идут через локальный VPN-прокси — как и остальные приложения
 * SmartDesk. Нет VPN → прокси недоступен → просто ошибка (fail-closed).
 */
@Composable
fun YouTubeApp() {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeService.Video>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<YouTubeService.Playback?>(null) }

    playing?.let { pb ->
        YouTubePlayerScreen(pb, onBack = { playing = null })
        return
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            loading = true; error = null
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.search(q) } }
            r.onSuccess { results = it }
                .onFailure { error = "Не удалось загрузить: ${it.message ?: it.javaClass.simpleName}. Включён ли VPN?" }
            loading = false
        }
    }

    fun open(video: YouTubeService.Video) {
        scope.launch {
            resolving = true; error = null
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.resolve(video.url) } }
            r.onSuccess { playing = it }
                .onFailure { error = "Видео недоступно: ${it.message ?: it.javaClass.simpleName}" }
            resolving = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Поиск на YouTube", color = VpnkaColors.TextMuted) },
                    leadingIcon = { Text("🔎", fontSize = 13.sp) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = VpnkaColors.TextStrong),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VpnkaColors.TextStrong, unfocusedTextColor = VpnkaColors.TextStrong,
                        cursorColor = VpnkaColors.Accent, focusedBorderColor = VpnkaColors.Accent,
                        unfocusedBorderColor = VpnkaColors.CardServer,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                        .background(VpnkaColors.Accent)
                        .clickable { runSearch() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) { Text("Найти", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = Color.White) }
            }

            when {
                loading -> CenterBox { CircularProgressIndicator(color = VpnkaColors.Accent) }
                error != null && results.isEmpty() -> CenterBox {
                    Text(error!!, color = VpnkaColors.TextMuted, fontFamily = VpnkaFonts.manrope600,
                        textAlign = TextAlign.Center)
                }
                results.isEmpty() -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▶️", fontSize = 44.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Найдите видео на YouTube", fontFamily = VpnkaFonts.nunito800,
                            fontSize = 17.sp, color = VpnkaColors.TextStrong)
                        Spacer(Modifier.height(6.dp))
                        Text("Без рекламы и аккаунта Google. Всё — через VPN.",
                            fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted,
                            textAlign = TextAlign.Center)
                    }
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results) { v -> VideoRow(v, onClick = { open(v) }) }
                }
            }
        }

        if (resolving) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("Готовим видео…", color = Color.White, fontFamily = VpnkaFonts.manrope600)
                }
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun VideoRow(v: YouTubeService.Video, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(88.dp, 56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) { Text("▶", fontSize = 22.sp, color = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(v.title, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    if (v.uploader.isNotBlank()) append(v.uploader)
                    if (v.durationSec > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(fmtDuration(v.durationSec))
                    }
                },
                fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Non-propagating opt-in: enables use of @UnstableApi Media3 symbols inside this
// function without marking the function itself unstable (which would force every
// caller to opt in too). Must be androidx.annotation.OptIn — UnstableApi is built
// on androidx.annotation.RequiresOptIn, not kotlin.RequiresOptIn.
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun YouTubePlayerScreen(pb: YouTubeService.Playback, onBack: () -> Unit) {
    val context = LocalContext.current
    val port = remember { SettingsManager.getHttpPort() }
    val player = remember(pb.streamUrl) {
        val ok = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
            .build()
        val dsf = OkHttpDataSource.Factory(ok)
        val src = ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(pb.streamUrl))
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(src)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 24.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.width(6.dp))
            Text("YouTube", fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
        }
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        )
        Text(
            pb.title,
            fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
        )
    }
}

private fun fmtDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
