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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.media3.ui.PlayerView
import com.v2ray.ang.handler.YouTubeService
import com.v2ray.ang.handler.YouTubeFavorites
import com.v2ray.ang.handler.YouTubePlaylists
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
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

    fun runSearchFor(q0: String) {
        val q = q0.trim()
        if (q.isEmpty()) return
        scope.launch {
            loading = true; error = null
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.search(q) } }
            r.onSuccess { results = it }
                .onFailure { error = "Не удалось загрузить: ${it.message ?: it.javaClass.simpleName}. Включён ли VPN?" }
            loading = false
        }
    }
    fun runSearch() = runSearchFor(query)

    // Home feed: open on an "electronic music" search so the first screen is a
    // living wall of videos, not an empty box. Only on a cold open (no query,
    // no results yet) — a manual search or a preset won't be overwritten.
    LaunchedEffect(Unit) {
        if (query.isBlank() && results.isEmpty()) runSearchFor("electronic music")
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

    var tab by remember { mutableStateOf(0) } // 0 = поиск, 1 = плейлисты
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            var searchSort by remember { mutableStateOf(YtSort.DEFAULT) }
            var plSort by remember { mutableStateOf(YtSort.DEFAULT) }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                YtTabChip("Поиск", tab == 0) { tab = 0 }
                Spacer(Modifier.width(8.dp))
                YtTabChip("Плейлисты", tab == 1) { tab = 1; openPl = null; plTick++ }
                Spacer(Modifier.width(8.dp))
                YtTabChip("Загрузки", tab == 2) { tab = 2 }
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
                    YtPresetChip("🎵 Музыка") { query = "музыка"; runSearch() }
                    YtPresetChip("📰 Новости") { query = "новости"; runSearch() }
                    YtPresetChip("💻 Технологии") { query = "технологии"; runSearch() }
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
                if (dls.isEmpty()) {
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
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
                        items(dls, key = { it.id }) { e -> DownloadRow(e) }
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
    val player = remember(pbState.streamUrl, pbState.audioUrl) {
        // Locals: pbState is a delegated property, so its fields can't be
        // smart-cast to non-null after a `!= null` check.
        val streamUrl = pbState.streamUrl
        val audioUrl = pbState.audioUrl
        val ok = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
            .build()
        val dsf = OkHttpDataSource.Factory(ok)
        val video = ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(streamUrl))
        // Adaptive: video-only track + separate audio, merged for HD playback.
        val src = if (audioUrl != null) {
            val audio = ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(audioUrl))
            MergingMediaSource(video, audio)
        } else {
            video
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(src)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var playQual by remember { mutableStateOf<List<YouTubeService.DownloadOption>?>(null) }

    var fullscreen by remember { mutableStateOf(false) }
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

    if (fullscreen) {
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
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

@Composable
private fun DownloadRow(e: YouTubeDownloads.Entry) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(e.label, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        when (e.state) {
            YouTubeDownloads.State.RUNNING -> {
                if (e.total > 0) {
                    val frac = (e.done.toFloat() / e.total).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(),
                        color = VpnkaColors.Accent)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VpnkaColors.Accent)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(fmtBytes(e.done))
                        if (e.total > 0) append(" / ${fmtBytes(e.total)}")
                        if (e.speed > 0) append("  ·  ${fmtBytes(e.speed)}/с")
                    },
                    fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
                )
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
