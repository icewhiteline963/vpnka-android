package com.v2ray.ang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.media3.ui.PlayerView
import androidx.media3.session.SessionToken
import androidx.media3.session.MediaController
import com.v2ray.ang.handler.YouTubeService
import com.v2ray.ang.handler.YouTubeFavorites
import com.v2ray.ang.handler.YouTubePlaylists
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.DeviceStorage
import com.v2ray.ang.handler.YouTubeHistory
import com.v2ray.ang.handler.YouTubeLater
import com.v2ray.ang.handler.YouTubeMarks
import com.v2ray.ang.handler.YouTubeThumbs
import com.v2ray.ang.handler.YouTubeNowPlaying
import com.v2ray.ang.service.VpnkaMediaService
import kotlinx.coroutines.delay
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

    fun runSearchFor(q0: String) {
        val q = q0.trim()
        if (q.isEmpty()) return
        YouTubeHistory.rememberQuery(q)
        scope.launch {
            loading = true; error = null
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.search(q) } }
            r.onSuccess { results = it }
                .onFailure { error = "Не удалось загрузить: ${it.message ?: it.javaClass.simpleName}. Включён ли VPN?" }
            loading = false
        }
    }
    fun runSearch() = runSearchFor(query)

    // Главная открывается ПОСЛЕДНИМ запросом человека, а не «electronic
    // music».
    //
    // Зашитая строка означала, что русскоязычный человек, открыв наш
    // YouTube, видел стену англоязычной электроники — приложение начинало
    // разговор с того, что ему неинтересно. Первый запуск, когда истории
    // ещё нет, открывается нейтральной подборкой.
    LaunchedEffect(Unit) {
        if (query.isBlank() && results.isEmpty()) {
            val last = YouTubeHistory.lastQuery()
            query = last ?: ""
            runSearchFor(last ?: "популярное")
        }
    }

    fun open(videoUrl: String) {
        scope.launch {
            resolving = true; error = null
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.resolve(videoUrl) } }
            r.onSuccess { playing = it }
                .onFailure { error = "Видео недоступно: ${it.message ?: it.javaClass.simpleName}" }
            resolving = false
        }
    }

    // 0 = поиск, 1 = плейлисты, 2 = загрузки, 3 = позже. Нижняя панель
    // рабочего стола умеет открыть «Видео» сразу на загрузках.
    var tab by remember { mutableStateOf(SmartDeskChrome.consumePendingYtTab() ?: 0) }
    var laterTick by remember { mutableStateOf(0) }
    var plTick by remember { mutableStateOf(0) }
    var openPl by remember { mutableStateOf<String?>(null) }
    var addTo by remember { mutableStateOf<YouTubePlaylists.Item?>(null) }
    var newPlDialog by remember { mutableStateOf(false) }
    var renamePl by remember { mutableStateOf<YouTubePlaylists.Playlist?>(null) }
    val context = LocalContext.current

    // Storage permission for a playlist «Скачать всё» on pre-Android-10.
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val a = pendingAction; pendingAction = null
        if (granted) a?.invoke()
    }
    fun withStorage(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) action() else { pendingAction = action; permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    }
    fun downloadPlaylist(pl: YouTubePlaylists.Playlist) = withStorage {
        pl.videos.forEach { v -> YouTubeDownloads.enqueueVideoByUrl(context, v.url, v.title) }
        Toast.makeText(context, "В загрузках: ${pl.videos.size}", Toast.LENGTH_SHORT).show()
    }

    // «Назад» внутри приложения: сначала закрываем открытое, и только когда
    // закрывать нечего — отдаём столу, чтобы он закрыл YouTube.
    SmartDeskBackHandler {
        when {
            playing != null -> { playing = null; true }
            openPl != null -> { openPl = null; true }
            tab != 0 -> { tab = 0; true }
            else -> false
        }
    }

    // Плеер поверх списка — но ПОСЛЕ объявления всего состояния.
    //
    // Раньше здесь стоял ранний `return`, и вкладка, открытый плейлист и
    // сортировки объявлялись ниже него: пока играл ролик, их группа не
    // выполнялась и значения терялись. Человек включал трек из плейлиста,
    // выходил из плеера — и оказывался на вкладке «Поиск» с чужой лентой
    // англоязычной электроники. Последовательно слушать плейлист было
    // невозможно.
    playing?.let { pb ->
        YouTubePlayerScreen(pb, onBack = { playing = null })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            var searchSort by remember { mutableStateOf(YtSort.DEFAULT) }
            var plSort by remember { mutableStateOf(YtSort.DEFAULT) }

            NowPlayingBar(onOpen = { pb -> playing = pb })

            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                YtTabChip("Поиск", tab == 0) { tab = 0 }
                Spacer(Modifier.width(8.dp))
                YtTabChip("Плейлисты", tab == 1) { tab = 1; openPl = null; plTick++ }
                Spacer(Modifier.width(8.dp))
                val activeDls = YouTubeDownloads.entries.count {
                    it.state == YouTubeDownloads.State.RUNNING ||
                        it.state == YouTubeDownloads.State.QUEUED
                }
                YtTabChip(
                    if (activeDls > 0) "Загрузки · $activeDls" else "Загрузки",
                    tab == 2,
                ) { tab = 2 }
            }

            if (tab == 0) {
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

                // Quick category presets — one tap runs a curated search.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Недавние запросы — они полезнее выдуманных рубрик:
                    // человек возвращается к своему, а не к нашему.
                    val recent = remember(results) { YouTubeHistory.recentQueries() }
                    if (recent.isEmpty()) {
                        YtPresetChip("🎵 Музыка") { query = "музыка"; runSearch() }
                        YtPresetChip("📰 Новости") { query = "новости"; runSearch() }
                        YtPresetChip("💻 Технологии") { query = "технологии"; runSearch() }
                    } else {
                        recent.forEach { r ->
                            YtPresetChip(r) { query = r; runSearch() }
                        }
                    }
                }

                if (results.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        YtSortChip(searchSort, searchSortOptions) { searchSort = it }
                    }
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
                        items(sortVideos(results, searchSort)) { v ->
                            VideoRow(v, onClick = { open(v.url) },
                                onAdd = { addTo = YouTubePlaylists.Item(it.url, it.title, it.uploader, it.durationSec) })
                        }
                    }
                }
            } else if (tab == 1) {
                val pls = remember(plTick) { YouTubePlaylists.all() }
                val current = openPl?.let { id -> pls.firstOrNull { it.id == id } }
                if (current == null) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(VpnkaColors.Accent).clickable { newPlDialog = true }.padding(14.dp),
                            ) { Text("＋ Новый плейлист", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = Color.White) }
                            Spacer(Modifier.height(10.dp))
                        }
                        items(pls, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { openPl = pl.id }.padding(vertical = 12.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (pl.id == YouTubePlaylists.FAV_ID) "★" else "🗂", fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp,
                                        color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${pl.videos.size} видео", fontFamily = VpnkaFonts.manrope600,
                                        fontSize = 12.sp, color = VpnkaColors.TextMuted)
                                }
                                Text("›", fontSize = 20.sp, color = VpnkaColors.TextMuted)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("‹", fontSize = 24.sp, color = VpnkaColors.TextStrong,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { openPl = null }
                                .padding(horizontal = 6.dp, vertical = 2.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(current.name, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp,
                            color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        var plMenu by remember { mutableStateOf(false) }
                        Box {
                            Text("⋮", fontSize = 22.sp, color = VpnkaColors.TextStrong,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { plMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp))
                            DropdownMenu(expanded = plMenu, onDismissRequest = { plMenu = false }) {
                                DropdownMenuItem(text = { Text("⬇ Скачать всё") }, enabled = current.videos.isNotEmpty(),
                                    onClick = { plMenu = false; downloadPlaylist(current) })
                                if (current.id != YouTubePlaylists.FAV_ID) {
                                    DropdownMenuItem(text = { Text("✎ Переименовать") }, onClick = { plMenu = false; renamePl = current })
                                    DropdownMenuItem(text = { Text("🗑 Удалить плейлист") },
                                        onClick = { plMenu = false; YouTubePlaylists.delete(current.id); openPl = null; plTick++ })
                                }
                            }
                        }
                    }
                    if (current.videos.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            YtSortChip(plSort, playlistSortOptions) { plSort = it }
                        }
                    }
                    if (current.videos.isEmpty()) {
                        CenterBox {
                            Text("Пусто. Добавьте видео кнопкой «＋» на карточке видео.",
                                color = VpnkaColors.TextMuted, fontFamily = VpnkaFonts.manrope600, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(sortItems(current.videos, plSort), key = { it.url }) { itv ->
                                VideoRow(
                                    YouTubeService.Video(itv.url, itv.title, itv.uploader, itv.durationSec, null),
                                    onClick = { open(itv.url) },
                                    onAdd = { addTo = YouTubePlaylists.Item(itv.url, itv.title, itv.uploader, itv.durationSec) },
                                    onRemove = { YouTubePlaylists.remove(current.id, itv.url); plTick++ },
                                )
                            }
                        }
                    }
                }
            } else {
                // tab == 2: downloads
                val dls = YouTubeDownloads.entries
                val later = remember(laterTick) { YouTubeLater.all() }
                var wifiOnly by remember { mutableStateOf(YouTubeLater.wifiOnly) }
                var nightOnly by remember { mutableStateOf(YouTubeLater.nightOnly) }
                var dlFilter by remember { mutableStateOf("Все") }
                val storage = remember(laterTick, dls.size) { DeviceStorage.read(context) }

                // Блок памяти — первым, как в макете. Человек, который качает
                // видео, упирается в место раньше всего остального, а узнаёт
                // об этом обычно из невнятной ошибки посреди загрузки.
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Память",
                            fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp,
                            color = VpnkaColors.TextStrong, modifier = Modifier.weight(1f),
                        )
                        Text(
                            "свободно ${DeviceStorage.fmt(storage.freeBytes)} из " +
                                DeviceStorage.fmt(storage.totalBytes),
                            fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                            color = VpnkaColors.TextMuted,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val used = if (storage.totalBytes > 0) {
                        ((storage.totalBytes - storage.freeBytes).toFloat() / storage.totalBytes)
                            .coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { used },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = VpnkaColors.Accent,
                        trackColor = VpnkaColors.CardServer,
                    )
                }
                Spacer(Modifier.height(10.dp))

                // Правила очереди — здесь, а не в дальних настройках: решение
                // «качать сейчас или дождаться» принимают ровно на этом экране.
                // Оба правила теперь ДЕЙСТВУЮТ: галка «только по Wi-Fi» раньше
                // записывалась и нигде не читалась.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    YtTabChip(
                        if (wifiOnly) "Только Wi-Fi" else "Любая сеть",
                        selected = wifiOnly,
                    ) {
                        wifiOnly = !wifiOnly
                        YouTubeLater.wifiOnly = wifiOnly
                    }
                    Spacer(Modifier.width(6.dp))
                    YtTabChip(
                        if (nightOnly) "Только ночью" else "В любое время",
                        selected = nightOnly,
                    ) {
                        nightOnly = !nightOnly
                        YouTubeLater.nightOnly = nightOnly
                    }
                }
                Text(
                    buildString {
                        append(if (wifiOnly) "Очередь дождётся домашней сети" else "Будет качать по мобильному — это ваш трафик")
                        if (nightOnly) append(" · старт с 00:00 до 07:00")
                    },
                    fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                    color = VpnkaColors.TextMuted,
                )

                // График скорости — суммарной по идущим загрузкам.
                val running = dls.filter { it.state == YouTubeDownloads.State.RUNNING }
                if (running.isNotEmpty()) {
                    val speeds = remember { mutableStateListOf<Long>() }
                    val nowSpeed = running.sumOf { it.speed }
                    LaunchedEffect(Unit) {
                        while (true) {
                            speeds.add(
                                YouTubeDownloads.entries
                                    .filter { it.state == YouTubeDownloads.State.RUNNING }
                                    .sumOf { it.speed },
                            )
                            if (speeds.size > 60) speeds.removeAt(0)
                            delay(1000)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Скорость", fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
                            color = VpnkaColors.TextStrong, modifier = Modifier.weight(1f),
                        )
                        Text(
                            fmtSpeed(nowSpeed), fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                            color = VpnkaColors.TextMuted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SpeedGraph(speeds)
                }

                Spacer(Modifier.height(12.dp))

                // Очередь «скачать позже» — над списком загрузок.
                //
                // Отмеченное лежит здесь, пока человек сам не решит качать:
                // трафик идёт через наши ноды и на мобильном стоит ему денег,
                // поэтому «взять с собой» и «качать сейчас» — разные решения.
                if (later.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Скачать позже · ${later.size}",
                            fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp,
                            color = VpnkaColors.TextStrong, modifier = Modifier.weight(1f),
                        )
                        DlAction("Скачать всё") {
                            withStorage {
                                later.forEach { i ->
                                    YouTubeDownloads.enqueueVideoByUrl(
                                        context, i.url, i.title, i.quality,
                                    )
                                }
                                YouTubeLater.clear()
                                laterTick++
                                Toast.makeText(
                                    context, "В загрузках: ${later.size}", Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        DlAction("Очистить") { YouTubeLater.clear(); laterTick++ }
                    }
                    later.forEach { i ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                i.title, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                                color = VpnkaColors.TextMuted, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            listOf("480p", "720p", "1080p", "♪").forEach { q ->
                                val on = i.quality == q
                                Text(
                                    q,
                                    fontFamily = VpnkaFonts.manrope600, fontSize = 10.sp,
                                    color = if (on) VpnkaColors.Accent else VpnkaColors.TextFaint,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .clickable {
                                            YouTubeLater.setQuality(i.url, if (on) "" else q)
                                            laterTick++
                                        }
                                        .padding(horizontal = 5.dp, vertical = 3.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            DlAction("⬇") {
                                withStorage {
                                    YouTubeDownloads.enqueueVideoByUrl(
                                        context, i.url, i.title, i.quality,
                                    )
                                    YouTubeLater.remove(i.url)
                                    laterTick++
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            DlAction("🗑") { YouTubeLater.remove(i.url); laterTick++ }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (dls.isEmpty() && later.isEmpty()) {
                    CenterBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⬇", fontSize = 44.sp, color = VpnkaColors.TextMuted)
                            Spacer(Modifier.height(10.dp))
                            Text("Загрузок пока нет", fontFamily = VpnkaFonts.nunito800,
                                fontSize = 17.sp, color = VpnkaColors.TextStrong)
                            Spacer(Modifier.height(6.dp))
                            Text("Скачанные видео появятся здесь и в системной папке «Загрузки».",
                                fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted,
                                textAlign = TextAlign.Center)
                        }
                    }
                } else if (dls.isNotEmpty()) {
                    // Полки по виду файла — они же папки на диске:
                    // Загрузки/VPNka/Видео, /Файлы, /Субтитры.
                    val kinds = listOf("Все") + dls.map { it.kind }.distinct()
                    if (kinds.size > 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            kinds.forEach { k ->
                                YtTabChip(k, selected = dlFilter == k) { dlFilter = k }
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                    val shown = if (dlFilter == "Все") dls else dls.filter { it.kind == dlFilter }
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
                        items(shown, key = { it.id }) { e -> DownloadRow(e) }
                    }
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

        addTo?.let { item ->
            AddToPlaylistDialog(item) { addTo = null; plTick++ }
        }

        if (newPlDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { newPlDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) { YouTubePlaylists.create(name); plTick++ }
                        newPlDialog = false
                    }) { Text("Создать") }
                },
                dismissButton = { TextButton(onClick = { newPlDialog = false }) { Text("Отмена") } },
                title = { Text("Новый плейлист", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
                text = {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        placeholder = { Text("Название", color = VpnkaColors.TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VpnkaColors.TextStrong, unfocusedTextColor = VpnkaColors.TextStrong,
                            cursorColor = VpnkaColors.Accent),
                    )
                },
                containerColor = VpnkaColors.BgOffCentre,
            )
        }

        renamePl?.let { pl ->
            var name by remember(pl.id) { mutableStateOf(pl.name) }
            AlertDialog(
                onDismissRequest = { renamePl = null },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) { YouTubePlaylists.rename(pl.id, name); plTick++ }
                        renamePl = null
                    }) { Text("Сохранить") }
                },
                dismissButton = { TextButton(onClick = { renamePl = null }) { Text("Отмена") } },
                title = { Text("Переименовать", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
                text = {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VpnkaColors.TextStrong, unfocusedTextColor = VpnkaColors.TextStrong,
                            cursorColor = VpnkaColors.Accent),
                    )
                },
                containerColor = VpnkaColors.BgOffCentre,
            )
        }
    }
}

/**
 * Что играет прямо сейчас — и чем это остановить.
 *
 * Плеер переехал в фоновую службу, и выход с экрана перестал быть тишиной.
 * Но обратного пути не осталось: любой выход оставлял звук идти, а
 * вернувшись в YouTube, человек видел обычный поиск — ни паузы, ни
 * продолжения. Единственным способом выключить наш же звук была системная
 * шторка Android. Получилось «Vanced наоборот»: фон есть, управления нет.
 */
@Composable
internal fun NowPlayingBar(onOpen: (YouTubeService.Playback) -> Unit) {
    val current = YouTubeNowPlaying.current ?: return
    val context = LocalContext.current
    var ctl by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val token = SessionToken(
            context,
            ComponentName(context, VpnkaMediaService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        var listener: Player.Listener? = null
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            ctl = c
            if (c != null) {
                playing = c.isPlaying
                listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                }.also { c.addListener(it) }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            listener?.let { l -> ctl?.removeListener(l) }
            MediaController.releaseFuture(future)
            ctl = null
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VpnkaColors.CardServer)
            .clickable { onOpen(current) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("♪", fontSize = 16.sp, color = VpnkaColors.Accent)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Сейчас играет", fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                color = VpnkaColors.TextMuted,
            )
            Text(
                current.title, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
                color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        DlAction(if (playing) "⏸" else "▶") {
            ctl?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        Spacer(Modifier.width(6.dp))
        DlAction("⏹") {
            ctl?.run { pause(); clearMediaItems() }
            YouTubeNowPlaying.current = null
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

private enum class YtSort(val label: String) {
    DEFAULT("Без сортировки"),
    DUR_DESC("Дольше сначала"),
    DUR_ASC("Короче сначала"),
    ADDED_NEW("Сначала новые"),
    ADDED_OLD("Сначала старые"),
}
private val searchSortOptions = listOf(YtSort.DEFAULT, YtSort.DUR_DESC, YtSort.DUR_ASC)
private val playlistSortOptions =
    listOf(YtSort.DEFAULT, YtSort.DUR_DESC, YtSort.DUR_ASC, YtSort.ADDED_NEW, YtSort.ADDED_OLD)

private fun sortVideos(list: List<YouTubeService.Video>, s: YtSort): List<YouTubeService.Video> = when (s) {
    YtSort.DUR_DESC -> list.sortedByDescending { it.durationSec }
    YtSort.DUR_ASC -> list.sortedBy { it.durationSec }
    else -> list
}
private fun sortItems(list: List<YouTubePlaylists.Item>, s: YtSort): List<YouTubePlaylists.Item> = when (s) {
    YtSort.DUR_DESC -> list.sortedByDescending { it.durationSec }
    YtSort.DUR_ASC -> list.sortedBy { it.durationSec }
    YtSort.ADDED_NEW -> list.sortedByDescending { it.addedAt }
    YtSort.ADDED_OLD -> list.sortedBy { it.addedAt }
    else -> list
}

@Composable
private fun YtSortChip(current: YtSort, options: List<YtSort>, onPick: (YtSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(VpnkaColors.CardServer)
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("⇅  ${current.label}", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextStrong)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o.label) }, onClick = { open = false; onPick(o) })
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(item: YouTubePlaylists.Item, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val lists = remember { YouTubePlaylists.all() }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
        title = { Text("Добавить в плейлист", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                lists.forEach { pl ->
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable {
                                YouTubePlaylists.add(pl.id, item)
                                Toast.makeText(ctx, "Добавлено: ${pl.name}", Toast.LENGTH_SHORT).show()
                                onClose()
                            }.padding(vertical = 12.dp, horizontal = 6.dp),
                    ) {
                        Text("${pl.name}  ·  ${pl.videos.size}", color = VpnkaColors.TextStrong,
                            fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (creating) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it }, singleLine = true,
                        placeholder = { Text("Название", color = VpnkaColors.TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VpnkaColors.TextStrong, unfocusedTextColor = VpnkaColors.TextStrong,
                            cursorColor = VpnkaColors.Accent),
                    )
                    Box(
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                            .background(VpnkaColors.Accent)
                            .clickable {
                                if (newName.isNotBlank()) {
                                    val id = YouTubePlaylists.create(newName)
                                    YouTubePlaylists.add(id, item)
                                    Toast.makeText(ctx, "Создан плейлист", Toast.LENGTH_SHORT).show()
                                    onClose()
                                }
                            }.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { Text("Создать и добавить", color = Color.White, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp) }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { creating = true }.padding(vertical = 10.dp, horizontal = 6.dp),
                    ) { Text("＋ Новый плейлист", color = VpnkaColors.Accent, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp) }
                }
            }
        },
        containerColor = VpnkaColors.BgOffCentre,
    )
}

@Composable
private fun YtPresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(VpnkaColors.CardServer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp, color = VpnkaColors.TextStrong)
    }
}

@Composable
private fun YtTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) VpnkaColors.Accent else VpnkaColors.CardServer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
            color = if (selected) Color.White else VpnkaColors.TextStrong)
    }
}

/**
 * Обложка видео. Пока грузится — штриховка, как в макете.
 *
 * Адрес обложки извлекался и раньше, но нигде не использовался: лента была
 * стеной одинаковых серых прямоугольников с «▶». По такой ленте нельзя
 * выбирать глазами — приходится читать заголовки. Для видеоприложения это
 * первое, что бросается в глаза.
 */
/** Пустое состояние вкладки — объяснение, а не пустота. */
@Composable
private fun YtHint(text: String) {
    Text(
        text,
        fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp),
    )
}

@Composable
private fun YtThumb(url: String?, modifier: Modifier) {
    var img by remember(url) { mutableStateOf(url?.let { YouTubeThumbs.cached(it) }) }
    LaunchedEffect(url) {
        if (img == null && url != null) img = YouTubeThumbs.load(url)
    }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                0f to VpnkaColors.CardServer,
                0.5f to VpnkaColors.CardSettings,
                1f to VpnkaColors.CardServer,
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = img
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text("▶", fontSize = 20.sp, color = VpnkaColors.TextFaint)
        }
    }
}

/** Бейдж поверх обложки — длительность, качество. */
@Composable
private fun YtBadge(text: String, color: Color = VpnkaColors.TextStrong) {
    Text(
        text,
        fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp, color = color,
        modifier = Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(VpnkaColors.BgOffEdge.copy(alpha = 0.85f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun VideoRow(
    v: YouTubeService.Video,
    onClick: () -> Unit,
    onFavChanged: (() -> Unit)? = null,
    onAdd: ((YouTubeService.Video) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    var fav by remember(v.url) { mutableStateOf(YouTubeFavorites.isFav(v.url)) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(9.dp))) {
            YtThumb(v.thumb, Modifier.matchParentSize())
            if (v.durationSec > 0) {
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    YtBadge(fmtDuration(v.durationSec))
                }
            }
        }
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
        // «Позже» прямо в ленте — чтобы отложить, не открывая видео. По
        // макету это одно из трёх действий карточки; из них два уже жили
        // внутри плеера, а до плеера ещё надо дойти.
        var inLater by remember(v.url) { mutableStateOf(YouTubeLater.has(v.url)) }
        Text(
            if (inLater) "⏱✓" else "⏱",
            fontSize = 18.sp,
            color = if (inLater) VpnkaColors.Amber else VpnkaColors.TextMuted,
            modifier = Modifier.clip(CircleShape).clickable {
                if (inLater) YouTubeLater.remove(v.url)
                else YouTubeLater.add(v.url, v.title, v.uploader)
                inLater = !inLater
            }.padding(8.dp),
        )
        if (onAdd != null) {
            Text("＋", fontSize = 22.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.clip(CircleShape).clickable { onAdd(v) }.padding(8.dp))
        }
        Text(
            if (fav) "★" else "☆",
            fontSize = 22.sp,
            color = if (fav) VpnkaColors.Accent else VpnkaColors.TextMuted,
            modifier = Modifier.clip(CircleShape).clickable {
                fav = YouTubeFavorites.toggle(
                    YouTubeFavorites.Fav(v.url, v.title, v.uploader, v.durationSec)
                )
                onFavChanged?.invoke()
            }.padding(8.dp),
        )
        if (onRemove != null) {
            Text("🗑", fontSize = 18.sp, color = VpnkaColors.TextMuted,
                modifier = Modifier.clip(CircleShape).clickable { onRemove() }.padding(8.dp))
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
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val port = remember { SettingsManager.getHttpPort() }
    // Mutable so the quality picker can swap the stream in place; the player is
    // keyed on the current stream URL, so choosing a quality rebuilds it.
    var pbState by remember(pb.pageUrl) { mutableStateOf(pb) }
    // Плеер живёт в СЛУЖБЕ, а экран лишь подключается к нему контроллером.
    // Раньше ExoPlayer создавался прямо здесь и умирал вместе с экраном:
    // свернул приложение — звук кончился. Ровно за это люди ставили Vanced.
    var player by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(
            context,
            ComponentName(context, VpnkaMediaService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { player = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            // Отпускаем ТОЛЬКО контроллер: плеер остаётся в службе и играет
            // дальше. В этом и смысл — уход с экрана больше не тишина.
            MediaController.releaseFuture(future)
            player = null
        }
    }

    // Новый адрес потока (в том числе после смены качества) отдаём службе.
    // Адрес звуковой дорожки едет в extras: это Bundle, он переживает
    // передачу между процессами, а обычный tag — нет.
    LaunchedEffect(player, pbState.streamUrl, pbState.audioUrl) {
        val c = player ?: return@LaunchedEffect
        // Если в службе УЖЕ играет ровно это — не трогаем.
        //
        // Иначе каждый вход на экран сбрасывал позицию и запускал заново:
        // вышел на 40-й минуте лекции, вернулся — снова ноль, да ещё и
        // играет, хотя ставил на паузу. Именно ради этого плеер и переехал
        // в службу, и было бы глупо ломать это здесь.
        val already = c.currentMediaItem
            ?.localConfiguration?.uri?.toString() == pbState.streamUrl
        if (already) return@LaunchedEffect

        val extras = Bundle().apply {
            pbState.audioUrl?.let { putString(VpnkaMediaService.EXTRA_AUDIO_URL, it) }
        }
        val item = MediaItem.Builder()
            .setUri(pbState.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(pbState.title)
                    .build()
            )
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setExtras(extras).build()
            )
            .build()
        c.setMediaItem(item)
        c.prepare()
        // Продолжаем с места, где остановились. Первые и последние полминуты
        // не в счёт — заглянул и закрыл это не «смотрел», а досмотренное
        // должно начинаться сначала, а не с титров.
        val saved = YouTubeHistory.position(pb.pageUrl)
        if (saved > 0) c.seekTo(saved * 1000)
        c.playWhenReady = true
        YouTubeNowPlaying.current = pbState
    }

    // Запоминаем позицию при уходе с экрана — и только её, без досылки на
    // сервер: где человек остановился, знать никому, кроме него, не нужно.
    DisposableEffect(pb.pageUrl) {
        onDispose {
            val c = player
            if (c != null && c.duration > 0) {
                YouTubeHistory.savePosition(
                    pb.pageUrl, c.currentPosition / 1000, c.duration / 1000,
                )
            }
        }
    }
    var playQual by remember { mutableStateOf<List<YouTubeService.DownloadOption>?>(null) }

    // Картинка-в-картинке: окно системы поверх других приложений. Плеер уже
    // в службе, поэтому переживает сворачивание сам — здесь только просьба
    // к системе показать маленькое окно.
    val enterPip: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val act = context as? android.app.Activity
            runCatching {
                act?.enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                )
            }
        }
    }

    var fullscreen by remember { mutableStateOf(false) }
    // «Только звук»: видео-дорожка выключается, и плеер перестаёт её качать.
    // Это не украшение — трафик идёт через НАШИ ноды с их лимитами, так что
    // экономия здесь наша прямая, а не только пользовательская.
    var audioOnly by remember { mutableStateOf(false) }
    // Отметка «взять с собой» — решение отдельное от «качать сейчас»:
    // трафик идёт через наши ноды и на мобильном стоит человеку денег.
    var laterTick by remember { mutableStateOf(0) }
    val inLater = remember(laterTick, pb.pageUrl) { YouTubeLater.has(pb.pageUrl) }
    var speed by remember { mutableStateOf(1f) }
    var ptab by remember { mutableStateOf(0) }
    var chapters by remember(pb.pageUrl) { mutableStateOf<List<YouTubeService.Chapter>?>(null) }
    var cues by remember(pb.pageUrl) { mutableStateOf<List<YouTubeService.Cue>?>(null) }
    var marksTick by remember { mutableStateOf(0) }
    var noteAt by remember { mutableStateOf<Long?>(null) }
    val marks = remember(marksTick, pb.pageUrl) { YouTubeMarks.marks(pb.pageUrl) }
    val notes = remember(marksTick, pb.pageUrl) { YouTubeMarks.notes(pb.pageUrl) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }

    // Позиция нужна закладкам и заметкам, а подсветка строки транскрипта —
    // каждую секунду. Опрашиваем раз в 500 мс: слушателя позиции у Media3
    // нет, а чаще незачем.
    LaunchedEffect(player) {
        val c = player ?: return@LaunchedEffect
        while (true) {
            isPlaying = c.isPlaying
            position = c.currentPosition
            kotlinx.coroutines.delay(500)
        }
    }
    var qualities by remember { mutableStateOf<List<YouTubeService.DownloadOption>?>(null) }
    var subs by remember { mutableStateOf<List<YouTubeService.SubtitleOption>?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Held while we wait for a storage-permission grant on pre-Android-10.
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var fav by remember(pb.pageUrl) { mutableStateOf(YouTubeFavorites.isFav(pb.pageUrl)) }
    var addToPlayer by remember { mutableStateOf<YouTubePlaylists.Item?>(null) }

    // One PlayerView, re-parented between the inline slot and the fullscreen
    // Dialog so playback survives the switch (same trick as the browser WebView).
    val playerView = remember {
        PlayerView(context).apply {
            this.player = player
            useController = true
            setFullscreenButtonClickListener { fullscreen = !fullscreen }
        }
    }
    val attach: (Context) -> PlayerView = {
        (playerView.parent as? ViewGroup)?.removeView(playerView)
        playerView
    }
    // The PlayerView is a stable single instance; when a quality change rebuilds
    // `player`, re-point the view at the new one (else it shows the released one).
    LaunchedEffect(player) { playerView.player = player }

    LaunchedEffect(player, audioOnly) {
        val c = player ?: return@LaunchedEffect
        c.trackSelectionParameters = c.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
    }

    fun openPlayQuality() {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.videoStreams(pb.pageUrl) } }
            busy = false
            r.onSuccess { if (it.isEmpty()) Toast.makeText(context, "Нет форматов", Toast.LENGTH_SHORT).show() else playQual = it }
                .onFailure { Toast.makeText(context, "Не удалось: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    // Landscape while fullscreen; MainActivity declares configChanges so this
    // rotation does NOT recreate the activity (which would kill the player).
    DisposableEffect(fullscreen) {
        activity?.requestedOrientation =
            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {}
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val a = pendingAction
        pendingAction = null
        if (granted && a != null) a()
        else if (!granted) Toast.makeText(context, "Без доступа к хранилищу скачивание невозможно", Toast.LENGTH_LONG).show()
    }

    // Runs a save action, requesting storage permission first on pre-Android-10.
    fun withStorage(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAction = action
            qualities = null; subs = null
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun runDownload(opt: YouTubeService.DownloadOption) {
        qualities = null
        YouTubeDownloads.enqueueVideo(context, opt, pb.title)
        Toast.makeText(context, "Добавлено в загрузки", Toast.LENGTH_SHORT).show()
    }

    fun runSubtitle(sub: YouTubeService.SubtitleOption) {
        subs = null
        YouTubeDownloads.enqueueSubtitle(context, sub, pb.title)
        Toast.makeText(context, "Добавлено в загрузки", Toast.LENGTH_SHORT).show()
    }

    fun openQualities() {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.videoStreams(pb.pageUrl) } }
            busy = false
            r.onSuccess { if (it.isEmpty()) Toast.makeText(context, "Нет форматов", Toast.LENGTH_SHORT).show() else qualities = it }
                .onFailure { Toast.makeText(context, "Не удалось: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun downloadAudio() {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.audioDownload(pb.pageUrl) } }
            busy = false
            r.onSuccess { if (it == null) Toast.makeText(context, "Нет аудио", Toast.LENGTH_SHORT).show() else withStorage { runDownload(it) } }
                .onFailure { Toast.makeText(context, "Не удалось: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun openSubs() {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.subtitles(pb.pageUrl) } }
            busy = false
            r.onSuccess { if (it.isEmpty()) Toast.makeText(context, "Субтитров нет", Toast.LENGTH_SHORT).show() else subs = it }
                .onFailure { Toast.makeText(context, "Не удалось: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    // В маленьком окне поверх других приложений рисуем ТОЛЬКО видео.
    //
    // Система уменьшает всю активность целиком, поэтому без этой ветки в
    // окошко 16:9 попадали шапка «‹ YouTube», заголовок и лента кнопок —
    // нечитаемая каша вместо картинки. Кнопка обещала одно, давала другое.
    // Из полноэкранного просмотра «назад» должна выходить в обычный вид, а
    // не выбрасывать на рабочий стол.
    SmartDeskBackHandler {
        if (fullscreen) { fullscreen = false; true } else false
    }

    val inPip = (context as? MainActivity)?.inPip == true
    LaunchedEffect(inPip) { playerView.useController = !inPip }

    if (inPip) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = attach, modifier = Modifier.fillMaxSize())
        }
    } else if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            val view = LocalView.current
            DisposableEffect(Unit) {
                val w = (view.parent as? DialogWindowProvider)?.window
                if (w != null) {
                    WindowCompat.setDecorFitsSystemWindows(w, false)
                    val c = WindowCompat.getInsetsController(w, view)
                    c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(WindowInsetsCompat.Type.systemBars())
                }
                onDispose {}
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = attach,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    } else {
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
                factory = attach,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            )
            Text(
                pb.title,
                fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            )

            // Транспорт: −10 · кадр назад · пауза · кадр вперёд · +10.
            //
            // Покадровый шаг по макету — 1/25 секунды и ОБЯЗАТЕЛЬНО с
            // остановкой: шагать вперёд у идущего видео бессмысленно, кадр
            // тут же уедет.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YtActionChip("−10", enabled = true) {
                    player?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }
                }
                YtActionChip("⟨|", enabled = true) {
                    player?.let { it.pause(); it.seekTo((it.currentPosition - 40).coerceAtLeast(0)) }
                }
                YtActionChip(if (isPlaying) "‖" else "▸", enabled = true) {
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                }
                YtActionChip("|⟩", enabled = true) {
                    player?.let { it.pause(); it.seekTo(it.currentPosition + 40) }
                }
                YtActionChip("+10", enabled = true) {
                    player?.let { it.seekTo(it.currentPosition + 10_000) }
                }
            }

            // Скорость воспроизведения — «до 4×» из макета, для лекций.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(1f, 1.5f, 1.75f, 2f, 3f, 4f).forEach { sp ->
                    val label = if (sp == 1f) "1×" else
                        ("%.2f".format(sp).trimEnd('0').trimEnd('.').replace('.', ',') + "×")
                    YtTabChip(label, kotlin.math.abs(speed - sp) < 0.01f) {
                        speed = sp
                        player?.setPlaybackSpeed(sp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YtActionChip(
                    if (inLater) "⏱  В очереди ✓" else "⏱  Скачать позже",
                    enabled = true,
                ) {
                    if (inLater) {
                        YouTubeLater.remove(pb.pageUrl)
                        Toast.makeText(context, "Убрано из очереди", Toast.LENGTH_SHORT).show()
                    } else {
                        YouTubeLater.add(pb.pageUrl, pb.title)
                        Toast.makeText(context, "Скачаем позже — в «Загрузках»", Toast.LENGTH_SHORT).show()
                    }
                    laterTick++
                }
                Spacer(Modifier.width(8.dp))
                YtActionChip(if (audioOnly) "🎧  Только звук ✓" else "🎧  Только звук", enabled = true) {
                    audioOnly = !audioOnly
                }
                Spacer(Modifier.width(8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    YtActionChip("⧉  В окне", enabled = true) { enterPip() }
                    Spacer(Modifier.width(8.dp))
                }
                YtActionChip(if (busy) "…" else "⚙  Качество", enabled = !busy) { openPlayQuality() }
                Spacer(Modifier.width(8.dp))
                YtActionChip("⬇  Видео", enabled = !busy) { openQualities() }
                Spacer(Modifier.width(8.dp))
                YtActionChip("🎵  Аудио", enabled = !busy) { downloadAudio() }
                Spacer(Modifier.width(8.dp))
                YtActionChip("📝  Субтитры", enabled = !busy) { openSubs() }
                Spacer(Modifier.width(8.dp))
                YtActionChip("＋  Плейлист", enabled = true) {
                    addToPlayer = YouTubePlaylists.Item(pb.pageUrl, pb.title)
                }
                Spacer(Modifier.width(8.dp))
                YtActionChip(if (fav) "★" else "☆", enabled = true) {
                    fav = YouTubeFavorites.toggle(YouTubeFavorites.Fav(pb.pageUrl, pb.title, "", 0L))
                    Toast.makeText(context, if (fav) "Добавлено в избранное" else "Убрано из избранного", Toast.LENGTH_SHORT).show()
                }
            }

            // Закладка и заметка — на ТЕКУЩЕМ таймкоде, как в макете.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                YtActionChip("◆  Закладка", enabled = true) {
                    YouTubeMarks.addMark(pb.pageUrl, position / 1000, pb.title)
                    marksTick++
                    ptab = 3
                    Toast.makeText(context, "Закладка на ${fmtDuration(position / 1000)}", Toast.LENGTH_SHORT).show()
                }
                YtActionChip("✎  Заметка", enabled = true) {
                    noteAt = position / 1000
                }
            }

            // Вкладки содержимого. Главы и транскрипт грузятся лениво —
            // это ещё один поход в сеть, и делать его до того, как человек
            // открыл вкладку, незачем.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                YtTabChip("Главы" + (chapters?.size?.let { " $it" } ?: ""), ptab == 0) { ptab = 0 }
                YtTabChip("Транскрипт", ptab == 1) { ptab = 1 }
                YtTabChip("Заметки ${notes.size}", ptab == 2) { ptab = 2 }
                YtTabChip("Закладки ${marks.size}", ptab == 3) { ptab = 3 }
            }

            when (ptab) {
                0 -> {
                    LaunchedEffect(pb.pageUrl) {
                        if (chapters == null) {
                            chapters = withContext(Dispatchers.IO) {
                                runCatching { YouTubeService.chapters(pb.pageUrl) }.getOrDefault(emptyList())
                            }
                        }
                    }
                    val ch = chapters
                    when {
                        ch == null -> CenterBox { CircularProgressIndicator(color = VpnkaColors.Accent) }
                        ch.isEmpty() -> YtHint("Автор не разметил главы у этого видео.")
                        else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(ch) { c ->
                                val now = position / 1000
                                val idx = ch.indexOf(c)
                                val until = ch.getOrNull(idx + 1)?.startSec ?: Long.MAX_VALUE
                                val active = now >= c.startSec && now < until
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { player?.seekTo(c.startSec * 1000) }
                                        .background(
                                            if (active) VpnkaColors.CardServer else Color.Transparent
                                        )
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        fmtDuration(c.startSec),
                                        fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                                        color = VpnkaColors.Accent,
                                        modifier = Modifier.width(52.dp),
                                    )
                                    Text(
                                        c.title + if (active) "  · сейчас" else "",
                                        fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                                        color = if (active) VpnkaColors.TextStrong else VpnkaColors.TextMuted,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LaunchedEffect(pb.pageUrl) {
                        if (cues == null) {
                            cues = withContext(Dispatchers.IO) {
                                runCatching { YouTubeService.transcript(pb.pageUrl) }.getOrDefault(emptyList())
                            }
                        }
                    }
                    val cs = cues
                    when {
                        cs == null -> CenterBox { CircularProgressIndicator(color = VpnkaColors.Accent) }
                        cs.isEmpty() -> YtHint("У этого видео нет субтитров — транскрипт брать неоткуда.")
                        else -> {
                            var q by remember(pb.pageUrl) { mutableStateOf("") }
                            OutlinedTextField(
                                value = q, onValueChange = { q = it }, singleLine = true,
                                placeholder = { Text("Искать в транскрипте", color = VpnkaColors.TextMuted) },
                                textStyle = androidx.compose.material3.LocalTextStyle.current
                                    .copy(color = VpnkaColors.TextStrong),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = VpnkaColors.TextStrong,
                                    unfocusedTextColor = VpnkaColors.TextStrong,
                                    cursorColor = VpnkaColors.Accent,
                                    focusedBorderColor = VpnkaColors.Accent,
                                    unfocusedBorderColor = VpnkaColors.CardServer,
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                            )
                            val shown = if (q.isBlank()) cs
                                else cs.filter { it.text.contains(q, ignoreCase = true) }
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(shown) { c ->
                                    val now = position / 1000
                                    val active = q.isBlank() && kotlin.math.abs(now - c.atSec) <= 14
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { player?.seekTo(c.atSec * 1000) }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                    ) {
                                        Text(
                                            fmtDuration(c.atSec),
                                            fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                                            color = VpnkaColors.Accent, modifier = Modifier.width(48.dp),
                                        )
                                        Text(
                                            c.text,
                                            fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                                            color = if (active) VpnkaColors.TextStrong else VpnkaColors.TextFaint,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (notes.isEmpty()) {
                        YtHint("Заметок нет. Нажмите «✎ Заметка» на нужном моменте.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(notes, key = { it.id }) { n ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { player?.seekTo(n.atSec * 1000) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        fmtDuration(n.atSec),
                                        fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                                        color = VpnkaColors.Accent, modifier = Modifier.width(48.dp),
                                    )
                                    Text(
                                        n.text, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                                        color = VpnkaColors.TextStrong, modifier = Modifier.weight(1f),
                                    )
                                    DlAction("✕") { YouTubeMarks.removeNote(n.id); marksTick++ }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (marks.isEmpty()) {
                        YtHint("Закладок нет. Нажмите «◆ Закладка» на нужном моменте.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(marks) { m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { player?.seekTo(m.atSec * 1000) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        fmtDuration(m.atSec),
                                        fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                                        color = VpnkaColors.Amber, modifier = Modifier.weight(1f),
                                    )
                                    DlAction("✕") { YouTubeMarks.removeMark(m.url, m.atSec); marksTick++ }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val opts = qualities
    if (opts != null && opts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { qualities = null },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { qualities = null }) { Text("Отмена") } },
            title = { Text("Выберите качество", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    opts.forEach { opt ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { withStorage { runDownload(opt) } }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                        ) {
                            Text("${opt.label}  ·  ${opt.ext.uppercase()}",
                                color = VpnkaColors.TextStrong, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp)
                        }
                    }
                }
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    noteAt?.let { at ->
        var text by remember(at) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteAt = null },
            title = {
                Text(
                    "Заметка на ${fmtDuration(at)}",
                    fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong,
                )
            },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Что записать?", color = VpnkaColors.TextMuted) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current
                        .copy(color = VpnkaColors.TextStrong),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VpnkaColors.TextStrong,
                        unfocusedTextColor = VpnkaColors.TextStrong,
                        cursorColor = VpnkaColors.Accent,
                        focusedBorderColor = VpnkaColors.Accent,
                        unfocusedBorderColor = VpnkaColors.CardServer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        YouTubeMarks.addNote(pb.pageUrl, at, pb.title, text.trim())
                        marksTick++
                        ptab = 2
                    }
                    noteAt = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { noteAt = null }) { Text("Отмена") } },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    val pqOpts = playQual
    if (pqOpts != null && pqOpts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { playQual = null },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { playQual = null }) { Text("Отмена") } },
            title = { Text("Качество воспроизведения", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    pqOpts.forEach { opt ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    // Swap the stream in place → the keyed player rebuilds at this quality.
                                    pbState = YouTubeService.Playback(pb.title, opt.videoUrl, pb.pageUrl, opt.audioUrl)
                                    playQual = null
                                }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                        ) {
                            Text(opt.label, color = VpnkaColors.TextStrong, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp)
                        }
                    }
                }
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    val subOpts = subs
    if (subOpts != null && subOpts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { subs = null },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { subs = null }) { Text("Отмена") } },
            title = { Text("Язык субтитров", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    subOpts.forEach { s ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { withStorage { runSubtitle(s) } }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                        ) {
                            Text("${s.label}  ·  ${s.ext.uppercase()}",
                                color = VpnkaColors.TextStrong, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp)
                        }
                    }
                }
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    addToPlayer?.let { item ->
        AddToPlaylistDialog(item) { addToPlayer = null }
    }
}

@Composable
private fun YtActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(VpnkaColors.CardServer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(label, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong)
    }
}

private fun fmtDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Скорость за последнюю минуту — столбиками, без осей и подписей:
 *  здесь важна форма (ровно/рвано), а не точные числа. */
@Composable
private fun SpeedGraph(samples: List<Long>) {
    val peak = (samples.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(VpnkaColors.CardServer)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        samples.takeLast(60).forEach { v ->
            val frac = (v.toFloat() / peak).coerceIn(0.02f, 1f)
            Box(
                modifier = Modifier.weight(1f).padding(horizontal = 0.5.dp)
                    .fillMaxHeight(frac)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VpnkaColors.Accent.copy(alpha = 0.75f)),
            )
        }
    }
}

private fun fmtSpeed(bps: Long): String = if (bps <= 0) "—" else "${fmtBytes(bps)}/с"

@Composable
private fun DownloadRow(e: YouTubeDownloads.Entry) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(e.label, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        when (e.state) {
            YouTubeDownloads.State.QUEUED -> {
                // «Ждёт» и «качается» — разные вещи. Одновременно работают
                // две загрузки, а бегущую полосу рисовали все: после
                // «Скачать всё» на тридцати роликах человек видел тридцать
                // одинаковых полос, из которых работали две.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.waitReason ?: "В очереди",
                        fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.TextMuted, modifier = Modifier.weight(1f),
                    )
                    // Из ожидания всегда есть выход: правило, которое нельзя
                    // обойти, в нужный момент становится ловушкой.
                    if (e.waitReason != null) {
                        DlAction("Сейчас") { YouTubeDownloads.forceNow(e) }
                        Spacer(Modifier.width(6.dp))
                    }
                    DlAction("Отменить") { YouTubeDownloads.cancel(e) }
                }
            }
            YouTubeDownloads.State.RUNNING -> {
                if (e.total > 0) {
                    val frac = (e.done.toFloat() / e.total).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(),
                        color = VpnkaColors.Accent)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VpnkaColors.Accent)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(fmtBytes(e.done))
                            if (e.total > 0) append(" / ${fmtBytes(e.total)}")
                            if (e.speed > 0) append("  ·  ${fmtBytes(e.speed)}/с")
                        },
                        fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.TextMuted, modifier = Modifier.weight(1f),
                    )
                    // Начатую загрузку надо уметь бросить. Раньше её было
                    // нечем остановить: файл качался до конца, даже если
                    // человек передумал, — а качается он через наши ноды.
                    DlAction("Отменить") { YouTubeDownloads.cancel(e) }
                }
            }
            YouTubeDownloads.State.CANCELLED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Отменено", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.TextMuted, modifier = Modifier.weight(1f))
                    if (e.sourceUrl != null) {
                        DlAction("Повторить") { YouTubeDownloads.retry(ctx, e) }
                        Spacer(Modifier.width(6.dp))
                    }
                    DlAction("🗑") { YouTubeDownloads.removeFromList(e) }
                }
            }
            YouTubeDownloads.State.DONE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Готово", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.Green, modifier = Modifier.weight(1f))
                    DlAction("Открыть") { openDownload(ctx, e) }
                    Spacer(Modifier.width(6.dp))
                    DlAction("Поделиться") { shareDownload(ctx, e) }
                    Spacer(Modifier.width(6.dp))
                    DlAction("🗑") { YouTubeDownloads.deleteFile(ctx, e) }
                }
            }
            YouTubeDownloads.State.FAILED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ошибка: ${e.error ?: ""}", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.Warning, modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // Перекачать сорвавшийся файл не должно значить «найди
                    // видео заново и выбери качество снова» — и адрес, и
                    // качество у нас уже записаны.
                    if (e.sourceUrl != null) {
                        DlAction("Повторить") { YouTubeDownloads.retry(ctx, e) }
                        Spacer(Modifier.width(6.dp))
                    }
                    DlAction("🗑") { YouTubeDownloads.removeFromList(e) }
                }
            }
        }
    }
}

@Composable
private fun DlAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(VpnkaColors.CardServer)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp, color = VpnkaColors.TextStrong)
    }
}

private fun openDownload(ctx: android.content.Context, e: YouTubeDownloads.Entry) {
    val uri = e.uri ?: return
    runCatching {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, e.mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }.onFailure { Toast.makeText(ctx, "Нет приложения для открытия", Toast.LENGTH_SHORT).show() }
}

private fun shareDownload(ctx: android.content.Context, e: YouTubeDownloads.Entry) {
    val uri = e.uri ?: return
    runCatching {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = e.mime
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            android.content.Intent.createChooser(send, "Поделиться")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Toast.makeText(ctx, "Не удалось поделиться", Toast.LENGTH_SHORT).show() }
}

private fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return "%.0f КБ".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f МБ".format(mb)
    return "%.2f ГБ".format(mb / 1024.0)
}
