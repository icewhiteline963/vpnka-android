package com.v2ray.ang.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import android.app.PendingIntent
import android.content.Intent
import com.v2ray.ang.R
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.v2ray.ang.handler.DownloadRecords
import com.v2ray.ang.handler.MmkvManager
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
            r.onSuccess { results = it; error = null }
                .onFailure {
                    // Прошлую выдачу ОЧИЩАЕМ.
                    //
                    // Ошибка показывалась только при пустом списке, поэтому
                    // при отвалившемся туннеле человек искал «собаки» и
                    // молча оставался на выдаче про кошек — ни признака, что
                    // запрос не прошёл, ни причины.
                    results = emptyList()
                    error = "Не удалось загрузить: " +
                        "${it.message ?: it.javaClass.simpleName}. Включён ли VPN?"
                }
            loading = false
        }
    }
    /** Открыть главную — подборку YouTube, а не чей-то прошлый запрос. */
    fun loadHome() {
        scope.launch {
            loading = true; error = null
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.trending() } }
            r.onSuccess { results = it }
                .onFailure {
                    // Киоск может быть недоступен (YouTube меняет разметку
                    // чаще, чем выходит extractor). Тогда не оставляем
                    // человека с пустым экраном — показываем нейтральную
                    // подборку поиском.
                    val f = withContext(Dispatchers.IO) {
                        runCatching { YouTubeService.search("популярное") }
                    }
                    f.onSuccess { v -> results = v }
                        .onFailure { e ->
                            error = "Не удалось загрузить: " +
                                "${e.message ?: e.javaClass.simpleName}. Включён ли VPN?"
                        }
                }
            loading = false
        }
    }

    // Пустой запрос — это «покажи главную», а не «ничего не делай».
    //
    // Раньше Enter на пустом поле молчал: человек стирал прошлый запрос,
    // жал ввод и оставался на его выдаче. Теперь возвращает ленту — тот же
    // список, с которого приложение открывается.
    fun runSearch() {
        if (query.isBlank()) loadHome() else runSearchFor(query)
    }

    // Открываем ГЛАВНУЮ, а не последний запрос.
    //
    // Сначала здесь было зашито «electronic music» — русскоязычный человек
    // видел стену англоязычной электроники. Потом подставлялся последний
    // запрос; но человек заходит «посмотреть, что нового», а получал
    // вчерашний поиск, который уже закрыл, да ещё и в поле ввода. Личной
    // ленты у нас быть не может (аккаунта нет), поэтому показываем то же,
    // что YouTube показывает гостю, — подборку «В тренде».
    //
    // Прошлые запросы никуда не делись: они подсказками под полем поиска.
    LaunchedEffect(Unit) {
        if (query.isBlank() && results.isEmpty()) loadHome()
    }

    fun open(videoUrl: String) {
        scope.launch {
            resolving = true; error = null
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeService.resolve(videoUrl) } }
            r.onSuccess {
                playing = it
                // Запоминаем НАЗВАНИЕ, а не только адрес: по картам позиций
                // и досмотренного список не покажешь — там одни ссылки.
                // Запись истории — на фоне: это чтение MMKV, разбор и
                // повторная сериализация до двух сотен объектов, и делать
                // это на главном потоке при каждом открытии ролика значит
                // подвешивать экран ровно в момент нажатия.
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        YouTubeHistory.rememberSeen(it.pageUrl, it.title, it.uploader)
                    }
                }
            }
                .onFailure { error = "Видео недоступно: ${it.message ?: it.javaClass.simpleName}" }
            resolving = false
        }
    }

    // Полки: 0 = главная лента и поиск, 1 = плейлисты, 2 = загрузки,
    // 3 = избранное, 4 = история просмотров. «Позже» — не отдельная полка,
    // а раздел внутри загрузок. Ярлык с рабочего стола умеет открыть
    // «Видео» сразу на нужной полке.
    var tab by remember { mutableStateOf(SmartDeskChrome.consumePendingYtTab() ?: 0) }
    var laterTick by remember { mutableStateOf(0) }
    var plTick by remember { mutableStateOf(0) }
    var favTick by remember { mutableStateOf(0) }
    var seenTick by remember { mutableStateOf(0) }
    var openPl by remember { mutableStateOf<String?>(null) }
    // Рассказываем нижней панели, на какой мы полке: без этого она считала
    // текущим «Видео» всегда — и из «Загрузок» кнопка «Видео» не работала,
    // потому что панель не переходит по текущему пункту.
    LaunchedEffect(tab) { SmartDeskChrome.ytTab = tab }
    DisposableEffect(Unit) { onDispose { SmartDeskChrome.ytTab = 0 } }

    // Просьба открыть вкладку может прийти, когда «Видео» УЖЕ на экране —
    // тогда новой композиции нет, и одного чтения при рождении мало.
    LaunchedEffect(SmartDeskChrome.pendingYtTab) {
        // Плеер НЕ закрываем: подсказка «Добавлено в загрузки · Открыть»
        // всплывает поверх видео, и нажатие на неё гасило ролик, который
        // человек в этот момент смотрит. Вкладку под плеером переключаем —
        // он её и увидит, когда выйдет.
        SmartDeskChrome.consumePendingYtTab()?.let { tab = it; openPl = null }
    }
    // Мини-плеер с рабочего стола: открываем ИМЕННО играющий ролик.
    LaunchedEffect(SmartDeskChrome.pendingPlayback) {
        SmartDeskChrome.consumePendingPlayback()?.let { playing = it }
    }
    var addTo by remember { mutableStateOf<YouTubePlaylists.Item?>(null) }
    var newPlDialog by remember { mutableStateOf(false) }
    var renamePl by remember { mutableStateOf<YouTubePlaylists.Playlist?>(null) }
    val context = LocalContext.current
    // Восстановление списка и уборка времянок — при открытии ПРИЛОЖЕНИЯ.
    //
    // Раньше это висело на вкладке «Загрузки»: кто туда не заходил, носил на
    // диске времянки размером с ролик неограниченно долго, а счётчик в шапке
    // не знал о восстановленных строках.
    LaunchedEffect(Unit) {
        // Оба действия — на фоне: восстановление читает MMKV и разбирает до
        // трёхсот записей, а это теперь на пути открытия приложения.
        withContext(Dispatchers.IO) {
            // Страховка второго слоя: что бы ни лежало в списке загрузок,
            // открытие «Видео» не должно ронять приложение. Исключение в
            // корутине LaunchedEffect убивает процесс целиком — человек
            // видит, что приложение просто исчезло.
            runCatching { YouTubeDownloads.restore() }
            runCatching { YouTubeService.sweepTemp(context) }
        }
    }

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
        SmartDeskToast.show("В загрузках: ${pl.videos.size}", "Открыть", "downloads")
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

    // Сортировки — ДО раннего возврата, вместе с остальным состоянием.
    //
    // Они объявлялись внутри столбца, то есть ниже `return` на плеер: пока
    // играл ролик, их группа не выполнялась и выбор терялся. Ровно та же
    // поломка, что была у вкладки и открытого плейлиста, — их подняли, а
    // сортировки пропустили.
    var searchSort by remember { mutableStateOf(YtSort.DEFAULT) }
    var plSort by remember { mutableStateOf(YtSort.DEFAULT) }

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

    // Своей нижней панели у «Видео» больше нет.
    //
    // Полки переехали чипами в шапку, а внизу теперь общая панель
    // супер-приложения (Видео · Чаты · Браузер · Загрузки · Главная) — из
    // «Видео» стало можно уйти в чаты, не возвращаясь на главный экран.
    // Двух панелей друг над другом мы уже наелись.

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 14.dp)

        ) {

            // Поиск — ТОЛЬКО на главной.
            //
            // В плейлистах, истории и загрузках искать нечего: строка стояла
            // там по инерции и отнимала верх экрана у самих списков.
            val activeDls = YouTubeDownloads.entries.count {
                it.state == YouTubeDownloads.State.RUNNING ||
                    it.state == YouTubeDownloads.State.QUEUED
            }
            // Подсказки под полем нужны, пока искать ещё нечего.
            val showHints = tab == 0 && results.isEmpty() && !loading

            if (tab == 0) {

            // Поиск — САМА верхняя строка, а не значок в её углу.
            //
            // Был значок-лупа: чтобы начать искать, требовалось два действия
            // — раскрыть поле и только потом попасть в него. Поле на всю
            // ширину убирает первое: нажатие сразу ставит курсор и поднимает
            // клавиатуру. Место под это есть — названия экрана в строке нет,
            // человек только что сам открыл «Видео» с рабочего стола.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Поле СВОЁ, а не готовое: у OutlinedTextField высота
                // жёстко 56 dp, и строка выходила вдвое толще всего
                // остального на экране. Здесь — 30 dp и своя рамка.
                Row(
                    modifier = Modifier.weight(1f)
                        // 34, а не 30: при 30 текст сидел вплотную к нижней
                        // грани — сверху отступ был, снизу нет.
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(VpnkaColors.CardServer)
                        .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(17.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔎", fontSize = 12.sp)
                    Spacer(Modifier.width(7.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; if (tab != 0) tab = 0 },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = VpnkaColors.TextStrong,
                            fontSize = 13.sp,
                            fontFamily = VpnkaFonts.manrope700,
                        ),
                        cursorBrush = SolidColor(VpnkaColors.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        // Подписи в пустом поле НЕТ.
                        //
                        // «Поиск на YouTube» не помещалось в половину строки
                        // и обрывалось на «Поиск на» — обрубок объяснял
                        // меньше, чем значок лупы слева, и мешал больше.
                        decorationBox = { inner -> inner() },
                        modifier = Modifier.weight(1f)
                            // Курсор в поле означает «ищу»: если человек стоял
                            // на «Загрузках», возвращаем его на ленту, иначе
                            // набор уходил бы в пустоту.
                            .onFocusChanged { if (it.isFocused && tab != 0) tab = 0 },
                    )
                    if (query.isNotEmpty()) {
                        Text(
                            "✕", fontSize = 13.sp, color = VpnkaColors.TextMuted,
                            modifier = Modifier.clip(CircleShape)
                                .clickable { query = ""; loadHome() }
                                .padding(horizontal = 4.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        "➤", fontSize = 13.sp, color = VpnkaColors.Accent,
                        modifier = Modifier.clip(CircleShape)
                            .clickable { runSearch() }
                            .padding(horizontal = 3.dp),
                    )
                }
                // Правая половина строки — то, что раньше стояло ПОД полем.
                //
                // Поиск занимал всю ширину, а сортировка висела отдельной
                // строкой ниже: две строки подряд ради одного чипа. Теперь
                // поле — ровно левая половина, а сортировка и счётчик
                // загрузок встают справа на том же уровне.
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Сортировка — у самого правого края, счётчик загрузок
                    // левее её: так у строки есть край, а не два предмета,
                    // висящих где придётся.
                    Spacer(Modifier.weight(1f))
                    if (activeDls > 0) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(9.dp))
                                .background(VpnkaColors.CardServer)
                                .clickable { tab = 2 }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("↓", fontSize = 11.sp, color = VpnkaColors.Accent)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                activeDls.toString(), fontFamily = VpnkaFonts.manrope700,
                                fontSize = 11.sp, color = VpnkaColors.TextStrong,
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                    }
                    if (tab == 0 && results.isNotEmpty()) {
                        YtSortChip(searchSort, searchSortOptions) { searchSort = it }
                    }
                }
            }
            }

            // Полки — чипами в шапке.
            //
            // Раньше они были нижней панелью «Видео», но внизу теперь общая
            // панель супер-приложения, и две полосы друг над другом мы уже
            // проходили. Наверху им и место: это выбор того, ЧТО показать в
            // списке, а не переход в другой раздел.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                YtTabChip("Лента", tab == 0) { tab = 0 }
                YtTabChip("Избранное", tab == 3) { tab = if (tab == 3) 0 else 3; favTick++ }
                YtTabChip("Плейлисты", tab == 1) {
                    tab = if (tab == 1) 0 else 1; openPl = null; plTick++
                }
                YtTabChip("История", tab == 4) { tab = if (tab == 4) 0 else 4; seenTick++ }
                YtTabChip("Загрузки", tab == 2) { tab = if (tab == 2) 0 else 2 }
            }

            // Мини-плеер — ПОД поиском, а не над ним.
            //
            // Стоял первым в столбце и накрывал строку поиска: пока играет
            // ролик, набрать новый запрос было некуда.
            NowPlayingBar(onOpen = { pb -> playing = pb })

            if (tab == 0) {
                if (showHints) {
                // Само поле уехало в верхнюю строку — здесь остались только
                // подсказки под ним.
                //
                // Подсказки живут вместе с полем: без него это просто ряд
                // кнопок непонятно к чему.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
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
            } else if (tab == 3) {
                // Избранное — тот же список, что копит «★» под видео, просто
                // показанный своей полкой.
                val favs = remember(plTick, favTick) { YouTubeFavorites.all() }
                if (favs.isEmpty()) {
                    YtEmptyCard(
                        "Здесь пусто",
                        "Отмечайте ролики звёздочкой под видео — они соберутся тут.",
                        null, null,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(favs, key = { it.url }) { f ->
                            VideoRow(
                                YouTubeService.Video(f.url, f.title, f.uploader, f.durationSec, null),
                                onClick = { open(f.url) },
                                onFavChanged = { favTick++ },
                                onAdd = {
                                    addTo = YouTubePlaylists.Item(
                                        it.url, it.title, it.uploader, it.durationSec,
                                    )
                                },
                            )
                        }
                    }
                }
            } else if (tab == 4) {
                // История просмотров. Пишется при открытии ролика вместе с
                // названием — по картам позиций и досмотренного показывать
                // было бы нечего, кроме ссылок.
                val seen = remember(seenTick) { YouTubeHistory.seen() }
                if (seen.isEmpty()) {
                    YtEmptyCard(
                        "Пока пусто",
                        "Здесь появятся ролики, которые вы открывали.",
                        null, null,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                YtTabChip("Очистить", selected = false) {
                                    YouTubeHistory.clearSeen(); seenTick++
                                }
                            }
                        }
                        items(seen, key = { it.url }) { h ->
                            VideoRow(
                                YouTubeService.Video(h.url, h.title, h.uploader, 0L, null),
                                onClick = { open(h.url) },
                                onAdd = {
                                    addTo = YouTubePlaylists.Item(it.url, it.title, it.uploader, 0L)
                                },
                                onRemove = { YouTubeHistory.forgetSeen(h.url); seenTick++ },
                            )
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
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                    .background(VpnkaColors.Accent).clickable { newPlDialog = true }.padding(14.dp),
                            ) { Text("＋ Новый плейлист", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.OnAccent) }
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

                // Вся полка «Загрузки» ПРОКРУЧИВАЕТСЯ.
                //
                // Она рисовалась обычным столбцом, а очередь «позже» и список
                // загрузок выводятся целиком, не лениво: после «скачать
                // позже» на паре десятков роликов блок памяти, правила
                // очереди и сами загрузки выдавливались за нижнюю грань, и
                // добраться до них было нечем.
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
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

                // Уборка просмотренного. Автоматика выключена по умолчанию и
                // трогает ТОЛЬКО то, что скачали мы сами и что человек
                // досмотрел до конца: сама удалять чужие файлы программа не
                // должна. Ручная кнопка есть всегда — она честнее таймера.
                var recTick by remember { mutableStateOf(0) }
                var autoDel by remember { mutableStateOf(DownloadRecords.autoDelete) }
                val watchedRecs = remember(recTick, autoDel, dls.size) {
                    // Порог ВСЕГДА 14 дней. С нулём кнопка сносила всё просмотренное
                    // любой давности — включая досмотренное минуту назад, — а
                    // подпись рядом обещала «через 14 дн. после просмотра».
                    DownloadRecords.watchedOlderThan(DownloadRecords.AUTO_DAYS)
                }
                LaunchedEffect(autoDel) {
                    if (autoDel) {
                        DownloadRecords.watchedOlderThan(DownloadRecords.AUTO_DAYS)
                            .forEach { DownloadRecords.delete(context, it) }
                        recTick++
                    }
                }
                // Блок показываем ВСЕГДА: раньше он появлялся, только когда
                // уже было что убирать, и включить автоуборку заранее было
                // невозможно — настройка пряталась от того, кто её ищет.
                run {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        YtTabChip(
                            if (autoDel) "Удалять просмотренное" else "Просмотренное не трогать",
                            selected = autoDel,
                        ) {
                            autoDel = !autoDel
                            DownloadRecords.autoDelete = autoDel
                        }
                        Spacer(Modifier.width(8.dp))
                        if (watchedRecs.isNotEmpty()) {
                            val size = watchedRecs.sumOf { it.bytes }
                            Text(
                                "${watchedRecs.size} файл. · ${fmtBytes(size)}",
                                fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                                color = VpnkaColors.TextMuted, modifier = Modifier.weight(1f),
                            )
                            DlAction("Удалить") {
                                var freed = 0L
                                watchedRecs.forEach { freed += DownloadRecords.delete(context, it) }
                                recTick++
                                SmartDeskToast.show("Освободилось ${fmtBytes(freed)}")
                            }
                        } else {
                            Text(
                                "Через ${DownloadRecords.AUTO_DAYS} дн. после просмотра",
                                fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                                color = VpnkaColors.TextMuted,
                            )
                        }
                    }
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
                                    // Из очереди «позже» вычёркиваем не здесь,
                                    // а когда загрузка реально началась.
                                    YouTubeDownloads.enqueueVideoByUrl(
                                        context, i.url, i.title, i.quality,
                                        clearLater = i.url,
                                    )
                                }
                                laterTick++
                                SmartDeskToast.show(
                                    "В загрузках: ${later.size}", "Открыть", "downloads",
                                )
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
                                        clearLater = i.url,
                                    )
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
                    // Обычный столбец, а не ленивый список.
                    //
                    // Ленивый нельзя вложить в прокручиваемый родитель — он
                    // меряется бесконечной высотой и роняет разметку. А
                    // прокрутка здесь нужнее ленивости: загрузок десятки, а
                    // не тысячи, зато выше них живут блок памяти и правила
                    // очереди, и без прокрутки они выдавливали список за
                    // нижнюю грань.
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        shown.forEach { e -> key(e.id) { DownloadRow(e) } }
                    }
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
            .clip(RoundedCornerShape(11.dp))
            .background(VpnkaColors.CardServer)
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(11.dp))
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
            // Сначала запомнить место, потом гасить.
            //
            // Служба сохраняет позицию сама, но читает её у
            // YouTubeNowPlaying, а очистка очереди обнуляет и его, и
            // длительность: остановка самой очевидной кнопкой теряла место, на
            // котором человек закончил слушать.
            ctl?.let { c ->
                if (c.duration > 0) {
                    YouTubeHistory.savePosition(
                        current.pageUrl, c.currentPosition / 1000, c.duration / 1000,
                    )
                }
                c.pause(); c.clearMediaItems()
            }
            // Строку убираем ТОЛЬКО если было чем остановить.
            //
            // `current = null` стоял снаружи, поэтому при неподключённом
            // контроллере (окно подключения, отказ службы) нажатие прятало
            // единственное управление, а звук шёл дальше — остановить его в
            // приложении становилось нечем, только системной шторкой. Ради
            // этого случая строка и заводилась.
            if (ctl != null) {
                YouTubeNowPlaying.current = null
                YouTubeNowPlaying.stalled = false
            } else {
                SmartDeskToast.show("Плеер ещё подключается — попробуйте ещё раз")
            }
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
        // Форма и высота — как у соседних чипов (радиус 7, кегль 11, 11×6):
        // со своими 12/12×8 он стоял в одном ряду с ними и выбивался.
        Box(
            modifier = Modifier.clip(RoundedCornerShape(7.dp))
                .background(VpnkaColors.CardServer)
                .clickable { open = true }
                .padding(horizontal = 11.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "⇅  ${current.label}", fontFamily = VpnkaFonts.nunito800,
                fontSize = 11.sp, color = VpnkaColors.TextMuted, maxLines = 1,
            )
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
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(VpnkaColors.CardServer)
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp, color = VpnkaColors.TextStrong)
    }
}

@Composable
private fun YtTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // Форма чипа из макета: радиус 7, кегль 11, выбранный — залит акцентом с
    // тёмными чернилами, остальные — плёнка с приглушённым текстом. Прежние
    // 12/13/16×9 делали из полок ряд крупных кнопок, спорящих с содержимым.
    Box(
        modifier = Modifier.clip(RoundedCornerShape(7.dp))
            .background(if (selected) VpnkaColors.Accent else VpnkaColors.CardServer)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(label, fontFamily = VpnkaFonts.nunito800, fontSize = 11.sp,
            color = if (selected) VpnkaColors.OnAccent else VpnkaColors.TextMuted)
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
    var inLater by remember(v.url) { mutableStateOf(YouTubeLater.has(v.url)) }
    val context = LocalContext.current

    // Карточка по макету: широкая обложка, под ней аватар канала с названием,
    // ещё ниже — действия. Прежняя строка с обложкой 112×63 слева умещала
    // больше роликов на экран, но по такой ленте не выбирают глазами: кадр
    // размером с ноготь ничего не показывает, и приходится читать заголовки.
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(198.dp)
                .clip(RoundedCornerShape(13.dp))
                .clickable(onClick = onClick),
        ) {
            YtThumb(v.thumb, Modifier.matchParentSize())
            if (v.durationSec > 0) {
                // У самой плашки уже есть свой отступ от края — второй тут
                // отодвинул бы её на полтора сантиметра внутрь кадра.
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    YtBadge(fmtDuration(v.durationSec))
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            // Кружок канала — в макете он есть, и по нему лента читается как
            // лента авторов, а не как список ссылок.
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(VpnkaColors.AccentLight, VpnkaColors.Accent),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    v.uploader.trim().take(1).uppercase().ifBlank { "•" },
                    fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
                    color = VpnkaColors.OnAccent,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    v.title, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp,
                    lineHeight = 19.sp, color = VpnkaColors.TextStrong,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (v.uploader.isNotBlank()) append(v.uploader)
                        if (v.durationSec > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(fmtDuration(v.durationSec))
                        }
                    },
                    fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                    color = VpnkaColors.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        // Действия — с отступом под аватар, ровно как в макете.
        Row(modifier = Modifier.fillMaxWidth().padding(start = 45.dp)) {
            YtTabChip("↓ Скачать", selected = false) {
                YouTubeDownloads.enqueueVideoByUrl(context, v.url, v.title)
                SmartDeskToast.show("Добавлено в загрузки", "Открыть", "downloads")
            }
            Spacer(Modifier.width(7.dp))
            YtTabChip(if (inLater) "⏱ В очереди" else "⏱ Позже", selected = inLater) {
                if (inLater) YouTubeLater.remove(v.url)
                else YouTubeLater.add(v.url, v.title, v.uploader)
                inLater = !inLater
            }
            Spacer(Modifier.width(7.dp))
            YtTabChip(if (fav) "★" else "☆", selected = fav) {
                fav = YouTubeFavorites.toggle(
                    YouTubeFavorites.Fav(v.url, v.title, v.uploader, v.durationSec)
                )
                onFavChanged?.invoke()
            }
            if (onAdd != null) {
                Spacer(Modifier.width(7.dp))
                YtTabChip("＋", selected = false) { onAdd(v) }
            }
            if (onRemove != null) {
                Spacer(Modifier.width(7.dp))
                YtTabChip("🗑", selected = false) { onRemove() }
            }
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
    // Приёмник команд из маленького окна живёт дольше одной композиции —
    // держим ссылку на текущий плеер в коробке, а не захватываем значение.
    val playerRef = remember { mutableStateOf<MediaController?>(null) }
    // Фоновое воспроизведение — ПО УМОЛЧАНИЮ ВЫКЛЮЧЕНО.
    //
    // Плеер живёт в службе и переживал уход с экрана всегда: человек
    // выходил из ролика, а звук продолжал идти, и остановить его можно было
    // только через мини-плеер или шторку Android. Для музыки это нужно, для
    // случайного ролика — нет, поэтому теперь спрашиваем явно.
    var bgPlay by remember {
        mutableStateOf(MmkvManager.decodeSettingsString("yt_background_play") == "1")
    }
    LaunchedEffect(bgPlay) {
        MmkvManager.encodeSettings("yt_background_play", if (bgPlay) "1" else "0")
    }
    // Выключатель называется «В фоне» — значит он про ФОН.
    //
    // Остановка висела на разрушении композиции, а оно происходит только при
    // уходе с экрана внутри приложения. Кнопка «Домой», список недавних,
    // переход в другое приложение композицию не разрушают — и звук
    // продолжал идти при выключенном переключателе. То есть он управлял не
    // фоном, а внутренней навигацией.
    val lifecycleOwner = LocalLifecycleOwner.current
    val bgPlayNow by rememberUpdatedState(bgPlay)
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !bgPlayNow) {
                runCatching {
                    playerRef.value?.pause()
                    playerRef.value?.stop()
                    playerRef.value?.clearMediaItems()
                }
                YouTubeNowPlaying.current = null
                YouTubeNowPlaying.stalled = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
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
            // Играет ли дальше — решает переключатель «В фоне».
            //
            // Раньше плеер оставался в службе ВСЕГДА: человек выходил из
            // ролика, звук продолжал идти, и остановить его можно было только
            // мини-плеером или шторкой Android. Для музыки это нужно, для
            // случайного ролика — нет, поэтому по умолчанию выключено.
            if (!bgPlay) {
                runCatching {
                    player?.pause()
                    player?.stop()
                    // И убираем элемент: `stop()` оставляет его вместе с
                    // позицией, и следующий ролик подхватывал чужое место.
                    player?.clearMediaItems()
                }
                YouTubeNowPlaying.current = null
                // Признак обрыва принадлежал прошлому ролику: иначе на
                // исправно играющем следующем висело «воспроизведение
                // прервалось».
                YouTubeNowPlaying.stalled = false
            }
            MediaController.releaseFuture(future)
            player = null
        }
    }

    // Новый адрес потока (в том числе после смены качества) отдаём службе.
    // Адрес звуковой дорожки едет в extras: это Bundle, он переживает
    // передачу между процессами, а обычный tag — нет.
    LaunchedEffect(player, pbState.streamUrl, pbState.audioUrl) {
        val c = player ?: return@LaunchedEffect
        // Что именно сейчас в службе — спрашиваем У СЕБЯ, а не у плеера.
        //
        // Раньше сравнивался `currentMediaItem.localConfiguration.uri`, но
        // через границу медиасессии это поле не передаётся, да и адреса
        // потоков googlevideo подписаны и на каждый разбор новые. Значит
        // «уже играет ровно это» не срабатывало НИКОГДА: каждый вход
        // пересобирал элемент и включал воспроизведение — вернулся к
        // поставленному на паузу ролику, а он заиграл сам и перекачал поток.
        val nowPage = YouTubeNowPlaying.current?.pageUrl
        val already = nowPage == pb.pageUrl &&
            YouTubeNowPlaying.current?.streamUrl == pbState.streamUrl &&
            c.currentMediaItem != null
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
        // Позицию продолжаем ТОЛЬКО у того же ролика.
        //
        // Брали `c.currentPosition` без разбора, а это позиция ТОГО ролика,
        // что играл до сих пор: открываешь новый — он стартует с сороковой
        // минуты предыдущего, а если новый короче, перемотка улетает за
        // конец и он «заканчивается» сразу. Своя позиция нужна при смене
        // качества и пересборке протухшего потока — там страница та же.
        val resumeFrom = if (nowPage == pb.pageUrl) c.currentPosition else 0L
        c.setMediaItem(item)
        c.prepare()
        // Продолжаем с ТЕКУЩЕГО места, если оно есть, иначе с сохранённого.
        //
        // При смене качества и при пересборке протухшего потока плеер
        // строится заново, а позиция бралась только из истории — её при
        // первом просмотре нет вовсе, и лекция начиналась с начала. Текущую
        // снимаем ДО setMediaItem: после него она уже ноль.
        // Новый поток — новый счёт: прошлый обрыв к нему отношения не имеет.
        YouTubeNowPlaying.stalled = false
        val saved = YouTubeHistory.position(pb.pageUrl)
        when {
            resumeFrom > 1000L -> c.seekTo(resumeFrom)
            saved > 0 -> c.seekTo(saved * 1000)
        }
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
    var fullscreen by remember { mutableStateOf(false) }
    // «Только звук»: видео-дорожка выключается, и плеер перестаёт её качать.
    // Это не украшение — трафик идёт через НАШИ ноды с их лимитами, так что
    // экономия здесь наша прямая, а не только пользовательская.
    // Настройка живёт между заходами: выключил видео ради трафика, вышел,
    // вернулся — и оно включалось обратно само.
    var audioOnly by remember {
        mutableStateOf(MmkvManager.decodeSettingsString("yt_audio_only") == "1")
    }
    LaunchedEffect(audioOnly) {
        MmkvManager.encodeSettings("yt_audio_only", if (audioOnly) "1" else "0")
    }
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
    // «Хотим играть» отдельно от «играет»: между ними буферизация.
    var wantsPlay by remember { mutableStateOf(true) }
    var stalled by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }

    // Кнопки в маленьком окне.
    //
    // Система рисует их сама — но только те, что мы объявим в параметрах, и
    // каждая должна вести на PendingIntent. Внутри окна наш Compose-оверлей
    // не показывается вовсе (там рисуется одно видео), поэтому до сих пор
    // маленькое окно было немым: остановить или отмотать можно было, только
    // развернув приложение обратно.
    val pipReceiver = remember {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val p = playerRef.value ?: return
                when (i?.getIntExtra(PIP_EXTRA, 0)) {
                    PIP_PLAY -> if (p.playWhenReady) p.pause() else p.play()
                    PIP_BACK -> p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                    PIP_FWD -> p.seekTo(p.currentPosition + 10_000)
                }
            }
        }
    }
    DisposableEffect(Unit) {
        // ContextCompat делает правильное на всех уровнях. Голый
        // registerReceiver до API 33 считает приёмник ЭКСПОРТИРОВАННЫМ:
        // любое приложение на телефоне могло слать нам эти команды и
        // управлять чужим воспроизведением.
        val f = android.content.IntentFilter(PIP_ACTION)
        ContextCompat.registerReceiver(
            context, pipReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(pipReceiver) } }
    }

    fun pipParams(playing: Boolean): android.app.PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        fun act(code: Int, icon: Int, title: String) = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(context, icon),
            title, title,
            PendingIntent.getBroadcast(
                context, code,
                Intent(PIP_ACTION).setPackage(context.packageName).putExtra(PIP_EXTRA, code),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .setActions(
                listOf(
                    act(PIP_BACK, R.drawable.ic_pip_back10, "−10 с"),
                    act(
                        PIP_PLAY,
                        if (playing) R.drawable.ic_pip_pause else R.drawable.ic_play_24dp,
                        if (playing) "Пауза" else "Играть",
                    ),
                    act(PIP_FWD, R.drawable.ic_pip_fwd10, "+10 с"),
                ),
            )
            .build()
    }

    // Значок «пауза/играть» в окне должен меняться вместе с состоянием —
    // иначе он застынет в том виде, в каком окно открыли.
    val inPip = (context as? MainActivity)?.inPip == true
    LaunchedEffect(wantsPlay, inPip) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && inPip) {
            runCatching {
                (context as? android.app.Activity)?.setPictureInPictureParams(pipParams(wantsPlay)!!)
            }
        }
    }

    val enterPip: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val act = context as? android.app.Activity
            runCatching { act?.enterPictureInPictureMode(pipParams(wantsPlay)!!) }
        }
    }


    // Позиция нужна закладкам и заметкам, а подсветка строки транскрипта —
    var buffering by remember { mutableStateOf(false) }
    var bufferedAheadMs by remember { mutableStateOf(0L) }
    var bufferRate by remember { mutableStateOf(0f) }
    // каждую секунду. Опрашиваем раз в 500 мс: слушателя позиции у Media3
    // нет, а чаще незачем.
    LaunchedEffect(player) {
        val c = player ?: return@LaunchedEffect
        // Скорость держит служба, а не экран: вернувшись в плеер, человек
        // раньше видел подсвеченную «1×» при фактических 2×.
        speed = c.playbackParameters.speed
        var prevBufMs = 0L
        var prevAt = System.currentTimeMillis()
        while (true) {
            isPlaying = c.isPlaying
            wantsPlay = c.playWhenReady
            position = c.currentPosition
            // Загрузка: сколько секунд ролика уже лежит впереди и с какой
            // скоростью прибавляется.
            //
            // На большом ролике первые секунды экран просто замирал: кадра
            // нет, полосы нет, и понять «грузится или повисло» нельзя. Теперь
            // видно и то, и другое. Скорость меряем по приросту буфера, а не
            // по счётчику сети: нам важно, доедет ли видео, а не сколько
            // байт прошло мимо.
            buffering = c.playbackState == androidx.media3.common.Player.STATE_BUFFERING
            val bufMs = (c.bufferedPosition - c.currentPosition).coerceAtLeast(0L)
            bufferedAheadMs = bufMs
            val now = System.currentTimeMillis()
            val dt = (now - prevAt).coerceAtLeast(1L)
            // Прирост буфера за секунду реального времени: 2.0 значит «за
            // секунду ожидания прибавилось две секунды ролика».
            bufferRate = ((bufMs - prevBufMs).toFloat() / dt).coerceIn(0f, 99f)
            prevBufMs = bufMs
            prevAt = now
            // Плеер встал: ссылка на поток протухла (они привязаны ко времени
            // и к адресу выхода) или оборвалась сеть. Раньше об этом не
            // говорилось никак — кадр замирал, кнопка рисовала «играет».
            // Признак приходит от службы: сама она о стопоре знает точно, а
            // экран мог бы разве что угадать по мгновенному состоянию.
            stalled = YouTubeNowPlaying.stalled
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
    LaunchedEffect(player) { playerView.player = player; playerRef.value = player }

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
        YouTubeDownloads.enqueueVideo(context, opt, pb.title, pb.pageUrl)
        SmartDeskToast.show("Добавлено в загрузки", "Открыть", "downloads")
    }

    fun runSubtitle(sub: YouTubeService.SubtitleOption) {
        subs = null
        YouTubeDownloads.enqueueSubtitle(context, sub, pb.title)
        SmartDeskToast.show("Добавлено в загрузки", "Открыть", "downloads")
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

    // Встроенный контроллер PlayerView нужен РОВНО в полноэкранном режиме:
    // свой оверлей мы рисуем только в обычном виде, а в маленьком окне поверх
    // приложений управление не показываем вовсе.
    //
    // Раньше здесь стояли два эффекта в разных ветках, и порядок слотов решал,
    // какой отработает последним: в полноэкранном не оставалось НИКАКОГО
    // управления — ни паузы, ни перемотки, ни даже кнопки выхода. Один эффект
    // с явными ключами убирает эту зависимость от порядка.
    LaunchedEffect(inPip, fullscreen, player) {
        playerView.useController = fullscreen && !inPip
    }

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
        // Шапка приложения прячется: своя у плеера уже есть, а две подряд
        // («‹ Видео» и «‹ YouTube») занимали строку впустую.
        DisposableEffect(Unit) {
            SmartDeskChrome.barHidden = true
            onDispose { SmartDeskChrome.barHidden = false }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // Одна шапка: «‹», название экрана и домен источника мелким моно.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                        .background(VpnkaColors.CardServer)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("‹", fontSize = 15.sp, color = VpnkaColors.TextStrong) }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Плеер", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(13.dp).clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF0033)),
                            contentAlignment = Alignment.Center,
                        ) { Text("▶", fontSize = 6.sp, color = Color.White) }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "youtube.com", fontFamily = VpnkaFonts.manrope600, fontSize = 10.sp,
                            color = VpnkaColors.TextFaint,
                        )
                    }
                }
            }

            // Кадр с управлением ПОВЕРХ него. Тап по кадру прячет и
            // показывает оверлей — рядов кнопок под видео больше нет.
            var uiVisible by remember { mutableStateOf(true) }
            // Счётчик касаний по оверлею — он же ключ таймера автоскрытия.
            // Без него панель гасла посреди перемотки: таймер отсчитывал от
            // появления и не знал, что человек в этот момент ею пользуется.
            var uiTick by remember { mutableIntStateOf(0) }
            LaunchedEffect(uiVisible, isPlaying, uiTick) {
                if (uiVisible && isPlaying) { kotlinx.coroutines.delay(3500); uiVisible = false }
            }
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { uiVisible = !uiVisible },
            ) {
                AndroidView(factory = attach, modifier = Modifier.fillMaxSize())
                if (uiVisible) {
                    // Затемнение под управлением: белые цифры на светлом кадре
                    // иначе не читаются.
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val spLabel = if (speed == 1f) "1×" else
                            ("%.2f".format(speed).trimEnd('0').trimEnd('.').replace('.', ',') + "×")
                        // Просто показание, не кнопка: скорость меняется
                        // плашками внизу, а нажатие сюда раньше не делало
                        // ничего вообще.
                        OverlayPill(spLabel, onClick = null)
                        Spacer(Modifier.weight(1f))
                        OverlayPill(if (busy) "…" else "качество") { openPlayQuality() }
                        Spacer(Modifier.width(6.dp))
                        // На Android 7 маленького окна нет вовсе — кнопка
                        // там молча ничего не делала.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            OverlayPill("⤢") { enterPip() }
                            Spacer(Modifier.width(6.dp))
                        }
                        OverlayPill("⛶") { fullscreen = true }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable {
                                    uiTick++
                                    player?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("−10", fontFamily = VpnkaFonts.manrope700, fontSize = 11.sp, color = Color.White) }
                        Box(
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                                .background(VpnkaColors.Accent)
                                .clickable {
                                uiTick++
                                player?.let { if (it.playWhenReady) it.pause() else it.play() }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Значок следует за НАМЕРЕНИЕМ (playWhenReady), а не за
                            // фактическим isPlaying: при просадке сети плеер
                            // буферизует, isPlaying=false — и кнопка врала, что
                            // видео на паузе, хотя никто её не ставил.
                            Text(
                                if (wantsPlay) "‖" else "▶", fontSize = 19.sp,
                                color = VpnkaColors.OnAccent,
                            )
                        }
                        Box(
                            modifier = Modifier.size(46.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable {
                                uiTick++
                                player?.let { it.seekTo(it.currentPosition + 10_000) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Text("+10", fontFamily = VpnkaFonts.manrope700, fontSize = 11.sp, color = Color.White) }
                    }

                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        val dur = player?.duration?.takeIf { it > 0 } ?: 0L
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                fmtDuration(position / 1000), fontFamily = VpnkaFonts.manrope600,
                                fontSize = 10.sp, color = Color.White,
                            )
                            Spacer(Modifier.weight(1f))
                            if (dur > 0) {
                                Text(
                                    "−" + fmtDuration(((dur - position) / 1000).coerceAtLeast(0)),
                                    fontFamily = VpnkaFonts.manrope600, fontSize = 10.sp, color = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        // Полоса времени: и показывает, и перематывает. Тап по
                        // ней — переход, протяжка — перемотка на ходу.
                        // Пока тянут — рисуем СВОЮ долю и молчим; seekTo уходит
                        // один раз, когда палец отпустили.
                        //
                        // Иначе на каждое движение пальца улетал seekTo: сотня
                        // вызовов в секунду, и каждый для потокового видео —
                        // новый запрос с Range через наши ноды. Полоса при этом
                        // дёргалась: позицию мы опрашиваем раз в полсекунды, и
                        // она отскакивала назад под пальцем.
                        var scrub by remember { mutableStateOf<Float?>(null) }
                        val frac = scrub
                            ?: if (dur > 0) (position.toFloat() / dur).coerceIn(0f, 1f) else 0f
                        Box(
                            modifier = Modifier.fillMaxWidth().height(14.dp)
                                .pointerInput(dur) {
                                    if (dur <= 0) return@pointerInput
                                    detectTapGestures { off ->
                                        uiTick++
                                        player?.seekTo((off.x / size.width * dur).toLong().coerceIn(0, dur))
                                    }
                                }
                                .pointerInput(dur) {
                                    if (dur <= 0) return@pointerInput
                                    detectHorizontalDragGestures(
                                        onDragStart = { off ->
                                            uiTick++
                                            scrub = (off.x / size.width).coerceIn(0f, 1f)
                                        },
                                        onDragEnd = {
                                            scrub?.let { f ->
                                                player?.seekTo((f * dur).toLong().coerceIn(0, dur))
                                            }
                                            scrub = null
                                            uiTick++
                                        },
                                        onDragCancel = { scrub = null },
                                    ) { change, _ ->
                                        scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                                    }
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.28f)),
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(frac).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(VpnkaColors.Accent),
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            listOf(1f, 1.5f, 1.75f, 2f, 3f, 4f).forEach { sp ->
                                val label = if (sp == 1f) "1×" else
                                    ("%.2f".format(sp).trimEnd('0').trimEnd('.').replace('.', ',') + "×")
                                val on = kotlin.math.abs(speed - sp) < 0.01f
                                Box(
                                    modifier = Modifier.weight(1f).height(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (on) VpnkaColors.Accent else Color.Black.copy(alpha = 0.55f),
                                        )
                                        .clickable { uiTick++; speed = sp; player?.setPlaybackSpeed(sp) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label, fontFamily = VpnkaFonts.manrope700, fontSize = 10.sp,
                                        color = if (on) VpnkaColors.OnAccent else Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (stalled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(VpnkaColors.Warning.copy(alpha = 0.16f))
                        .clickable {
                            stalled = false
                            YouTubeNowPlaying.stalled = false
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    runCatching { YouTubeService.resolve(pb.pageUrl) }
                                }
                                r.getOrNull()?.let { pbState = it }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Воспроизведение прервалось — нажмите, чтобы продолжить",
                        fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.TextStrong,
                    )
                }
            }
            Text(
                cleanTitle(pb.title),
                fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, lineHeight = 21.sp,
                color = VpnkaColors.TextStrong, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 6.dp),
            )

            // Просмотры и оценки.
            //
            // Дизлайки YouTube публично не отдаёт с 2021-го, поэтому их почти
            // всегда нет: показывать «0» было бы враньём — молчим, когда
            // значение неизвестно.
            val stats = buildList {
                if (pb.views >= 0) add("${fmtCount(pb.views)} просмотров")
                if (pb.likes >= 0) add("👍 ${fmtCount(pb.likes)}")
                if (pb.dislikes >= 0) add("👎 ${fmtCount(pb.dislikes)}")
            }
            // Автор и оценки — РАЗНЫМИ строками.
            //
            // В одну строку они не влезали: имя канала бывает длинным, и
            // просмотры с лайками обрезались многоточием — то есть пропадало
            // именно то, ради чего строку и заводили.
            if (pb.uploader.isNotBlank()) {
                Text(
                    pb.uploader,
                    fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                    color = VpnkaColors.TextMuted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (stats.isNotEmpty()) {
                Text(
                    stats.joinToString(" · "),
                    fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                    color = VpnkaColors.TextMuted, maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 3.dp),
                )
            }

            // Полоса загрузки.
            //
            // На большом ролике первые секунды экран замирал: кадра нет,
            // полосы нет, и понять «грузится или повисло» нельзя. Показываем,
            // сколько ролика уже лежит впереди и как быстро прибавляется —
            // «1,8× » значит, что за секунду ожидания загружается почти две
            // секунды видео, то есть догонит; «0,3×» — что не догонит.
            // Показываем, только пока РЕАЛЬНО грузится.
            //
            // Условие «впереди меньше трёх секунд» само по себе истинно и в
            // конце ролика (там впереди ноль), и в последние секунды любого
            // воспроизведения, и пока контроллер ещё не подключился — из-за
            // чего крутилка «Загрузка» оставалась под досмотренным роликом
            // навсегда. Нехватка буфера считается поводом, только когда
            // человек ждёт: плеер хочет играть, но не играет.
            val starving = wantsPlay && !isPlaying && bufferedAheadMs < 3000
            if (player != null && (buffering || starving)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = VpnkaColors.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        buildString {
                            append("Загрузка")
                            if (bufferedAheadMs > 0) {
                                append(" · готово ")
                                append(fmtDuration(bufferedAheadMs / 1000))
                            }
                            if (bufferRate > 0.05f) {
                                append(" · ")
                                append(String.format(java.util.Locale.US, "%.1f", bufferRate))
                                append("×")
                            }
                        },
                        fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                        color = VpnkaColors.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Действия: ПЯТЬ на виду, остальное — за «Ещё».
            //
            // Был один ряд из девяти одинаковых чипов с прокруткой вбок.
            // Половина действий пряталась за краем, добраться до них можно
            // было только листанием вслепую, а сам ряд читался как лента
            // текста — из-за чего и выбивался из остального экрана.
            //
            // Теперь на виду то, что делают чаще всего, — иконка и короткая
            // подпись под ней, все пять равной ширины и без прокрутки. Редкое
            // (очередь, закладка, заметка, звук файлом, субтитры) уходит в
            // список за «Ещё»: там у каждого пункта есть место на понятное
            // название, а не на сокращение в одну строку.
            var moreOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // «Скачать» и «Скачать позже» — соседи: это одно и то же
                // решение, отложенное на потом. Разносить их по разным
                // местам значит заставлять искать второе, когда первое
                // не подошло.
                YtQuickAction("⬇", "Скачать", enabled = !busy) { openQualities() }
                YtQuickAction(
                    "⏱", if (inLater) "В очереди" else "Позже", active = inLater,
                ) {
                    if (inLater) {
                        YouTubeLater.remove(pb.pageUrl)
                        SmartDeskToast.show("Убрано из очереди")
                    } else {
                        YouTubeLater.add(pb.pageUrl, pb.title)
                        SmartDeskToast.show("Скачаем позже", "Открыть", "downloads")
                    }
                    laterTick++
                }
                YtQuickAction(
                    "♪", "Только звук", active = audioOnly,
                ) { audioOnly = !audioOnly }
                YtQuickAction(
                    if (fav) "★" else "☆", if (fav) "В избранном" else "В избранное",
                    active = fav,
                ) {
                    fav = YouTubeFavorites.toggle(
                        YouTubeFavorites.Fav(pb.pageUrl, pb.title, "", 0L),
                    )
                }
                YtQuickAction("🔊", "В фоне", active = bgPlay) { bgPlay = !bgPlay }
                YtQuickAction("⋯", "Ещё", active = moreOpen) { moreOpen = true }
            }

            if (moreOpen) {
                AlertDialog(
                    onDismissRequest = { moreOpen = false },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { moreOpen = false }) { Text("Закрыть") }
                    },
                    containerColor = VpnkaColors.BgOffMid,
                    title = {
                        Text(
                            "Ещё", fontFamily = VpnkaFonts.nunito800,
                            color = VpnkaColors.TextStrong,
                        )
                    },
                    text = {
                        Column {
                            YtMoreItem("＋", "Добавить в плейлист") {
                                addToPlayer = YouTubePlaylists.Item(pb.pageUrl, pb.title)
                                moreOpen = false
                            }
                            YtMoreItem("◆", "Закладка на ${fmtDuration(position / 1000)}") {
                                YouTubeMarks.addMark(pb.pageUrl, position / 1000, pb.title)
                                marksTick++; ptab = 3
                                SmartDeskToast.show("Закладка на ${fmtDuration(position / 1000)}")
                                moreOpen = false
                            }
                            YtMoreItem("✎", "Заметка к этому месту") {
                                noteAt = position / 1000; moreOpen = false
                            }
                            YtMoreItem("🎵", "Скачать только звук", enabled = !busy) {
                                downloadAudio(); moreOpen = false
                            }
                            YtMoreItem("📝", "Субтитры", enabled = !busy) {
                                openSubs(); moreOpen = false
                            }
                        }
                    },
                )
            }

            // Вкладки содержимого. Главы и транскрипт грузятся лениво —
            // это ещё один поход в сеть, и делать его до того, как человек
            // открыл вкладку, незачем.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    YtPlayerTab("Главы" + (chapters?.size?.let { " $it" } ?: ""), ptab == 0) { ptab = 0 }
                    YtPlayerTab("Транскрипт", ptab == 1) { ptab = 1 }
                    YtPlayerTab("Заметки ${notes.size}", ptab == 2) { ptab = 2 }
                    YtPlayerTab("Закладки ${marks.size}", ptab == 3) { ptab = 3 }
                }
                YtEdgeFade(Modifier.align(Alignment.CenterEnd))
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(VpnkaColors.Hairline))

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
                        ch.isEmpty() -> YtEmptyCard(
                            "Автор не разметил главы",
                            "Поставьте свои метки — они сохранятся вместе с видео и будут доступны офлайн.",
                            "＋ Добавить метку",
                        ) {
                            YouTubeMarks.addMark(pb.pageUrl, position / 1000, pb.title)
                            marksTick++; ptab = 3
                        }
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
                        cs.isEmpty() -> YtEmptyCard(
                            "Транскрипта нет",
                            "У этого видео нет субтитров, а расшифровывать звук мы не умеем — брать текст неоткуда.",
                            null, null,
                        )
                        else -> {
                            var q by remember(pb.pageUrl) { mutableStateOf("") }
                            OutlinedTextField(
                                value = q, onValueChange = { q = it }, singleLine = true,
                                placeholder = { Text("Искать в транскрипте", color = VpnkaColors.TextMuted) },
                                textStyle = androidx.compose.material3.LocalTextStyle.current
                                    .copy(color = VpnkaColors.TextStrong),
                                shape = RoundedCornerShape(14.dp),
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
                        YtEmptyCard(
                            "Заметок нет",
                            "Заметка привязывается к секунде, на которой вы её оставили, и открывается прямо оттуда.",
                            "✎ Записать сейчас",
                        ) { noteAt = position / 1000 }
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
                        YtEmptyCard(
                            "Закладок нет",
                            "Закладка запоминает момент, чтобы вернуться к нему одним касанием.",
                            "◆ Поставить здесь",
                        ) {
                            YouTubeMarks.addMark(pb.pageUrl, position / 1000, pb.title)
                            marksTick++
                        }
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

/** Пилюля управления поверх кадра: тёмная подложка, мелкий моно. */
// Команды из маленького окна: система шлёт их широковещательно, поэтому им
// нужны стабильные имя и коды.
private const val PIP_ACTION = "com.v2ray.ang.PIP_ACTION"
private const val PIP_EXTRA = "what"
private const val PIP_PLAY = 1
private const val PIP_BACK = 2
private const val PIP_FWD = 3

@Composable
private fun OverlayPill(label: String, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, fontFamily = VpnkaFonts.manrope700, fontSize = 10.sp, color = Color.White)
    }
}

/** Вкладка внутри плеера: активная — подложкой-плёнкой, без заливки акцентом. */
@Composable
private fun YtPlayerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) VpnkaColors.CardServer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label, fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp, maxLines = 1,
            color = if (selected) VpnkaColors.TextStrong else VpnkaColors.TextFaint,
        )
    }
}

/**
 * Действие под видео: значок в кружке и короткая подпись под ним.
 *
 * Пять таких в ряд занимают ширину экрана без прокрутки — в отличие от
 * прежнего ряда чипов, где половина действий пряталась за краем. Подпись
 * ставим под значком, а не рядом: так каждая кнопка узкая, и в строку их
 * помещается впятеро больше.
 */
@Composable
private fun YtQuickAction(
    icon: String,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // 56, а не 64: кнопок стало шесть, и на узком экране ряд
        // переставал помещаться целиком.
        modifier = Modifier.width(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape)
                .background(if (active) VpnkaColors.Accent else VpnkaColors.CardServer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                icon, fontSize = 16.sp,
                color = when {
                    !enabled -> VpnkaColors.TextFaint
                    active -> VpnkaColors.OnAccent
                    else -> VpnkaColors.TextStrong
                },
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label, fontFamily = VpnkaFonts.manrope700, fontSize = 10.sp,
            lineHeight = 12.sp, textAlign = TextAlign.Center, maxLines = 2,
            color = if (enabled) VpnkaColors.TextMuted else VpnkaColors.TextFaint,
        )
    }
}

/** Строка списка «Ещё»: значок, название во всю ширину, крупная область нажатия. */
@Composable
private fun YtMoreItem(
    icon: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape)
                .background(VpnkaColors.CardServer),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 14.sp, color = VpnkaColors.TextStrong)
        }
        Spacer(Modifier.width(11.dp))
        Text(
            label, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
            color = if (enabled) VpnkaColors.TextStrong else VpnkaColors.TextFaint,
        )
    }
}

/** Затухание к правому краю: видно, что ряд продолжается за экраном. */
@Composable
private fun YtEdgeFade(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.width(26.dp).fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    listOf(VpnkaColors.BgOffMid.copy(alpha = 0f), VpnkaColors.BgOffMid),
                ),
            ),
    )
}

/** Пустое состояние — карточка с пунктиром, объяснением и действием. */
@Composable
private fun YtEmptyCard(title: String, body: String, action: String?, onAction: (() -> Unit)?) {
    val hairline = VpnkaColors.Hairline
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
            .clip(RoundedCornerShape(14.dp))
            // Рамка ПУНКТИРНАЯ, как в эталоне: сплошная читается как готовый
            // блок, пунктир — как место, которое ещё предстоит заполнить.
            .drawBehind {
                val r = 14.dp.toPx()
                drawRoundRect(
                    color = hairline,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx()), 0f,
                        ),
                    ),
                )
            }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
            color = VpnkaColors.TextStrong, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            body, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, lineHeight = 18.sp,
            color = VpnkaColors.TextMuted, textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(13.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(11.dp))
                    .background(VpnkaColors.Accent)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 15.dp, vertical = 10.dp),
            ) {
                Text(action, fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp, color = VpnkaColors.OnAccent)
            }
        }
    }
}

/**
 * Название ролика как предложение.
 *
 * YouTube отдаёт заголовки с разметкой и капсом: «**СРОЧНО** _смотреть_
 * ВСЕМ». Звёздочки и подчёркивания убираем, сплошной капс приводим к обычному
 * виду — иначе лента кричит.
 */
private fun cleanTitle(raw: String): String {
    var t = raw.replace(Regex("[*_`#~]+"), "").replace(Regex("\\s+"), " ").trim()
    val letters = t.filter { it.isLetter() }
    if (letters.length >= 8 && letters.none { it.isLowerCase() }) {
        t = t.lowercase().replaceFirstChar { it.uppercase() }
    }
    return t
}

/** Короткое число: 1,2 млн вместо 1234567. */
private fun fmtCount(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    n < 1_000_000 -> String.format(java.util.Locale.US, "%.1f тыс.", n / 1_000.0)
        .replace(".0 ", " ").replace('.', ',')
    else -> String.format(java.util.Locale.US, "%.1f млн", n / 1_000_000.0)
        .replace(".0 ", " ").replace('.', ',')
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
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(8.dp))
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
        modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp, color = VpnkaColors.TextStrong)
    }
}

/**
 * Отдать файл другому приложению.
 *
 * На Android 8-9 скачанное сохраняется обычным файлом, и `file://` в чужом
 * приложении роняет нас `FileUriExposedException` — исключение съедалось, а
 * человек видел «нет приложения для открытия». Поэтому такие адреса
 * переводим на наш провайдер.
 */
private fun shareableUri(ctx: android.content.Context, uri: android.net.Uri): android.net.Uri =
    if (uri.scheme != "file") uri
    else runCatching {
        androidx.core.content.FileProvider.getUriForFile(
            ctx, ctx.packageName + ".cache", java.io.File(uri.path!!),
        )
    }.getOrDefault(uri)

private fun openDownload(ctx: android.content.Context, e: YouTubeDownloads.Entry) {
    val uri = e.uri?.let { shareableUri(ctx, it) } ?: return
    runCatching {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, e.mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }.onFailure { Toast.makeText(ctx, "Нет приложения для открытия", Toast.LENGTH_SHORT).show() }
}

private fun shareDownload(ctx: android.content.Context, e: YouTubeDownloads.Entry) {
    val uri = e.uri?.let { shareableUri(ctx, it) } ?: return
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
