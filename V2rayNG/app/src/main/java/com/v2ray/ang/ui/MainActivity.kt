package com.v2ray.ang.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.core.app.NotificationManagerCompat
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.SelectListDialog
import com.v2ray.ang.compose.LocalDarkTheme
import com.v2ray.ang.compose.QRCodeDialog
import com.v2ray.ang.compose.ReorderableGridItem
import com.v2ray.ang.compose.ReorderableListItem
import com.v2ray.ang.compose.colorConfigType
import com.v2ray.ang.compose.colorFabActive
import com.v2ray.ang.compose.colorFabInactiveDark
import com.v2ray.ang.compose.colorFabInactiveLight
import com.v2ray.ang.compose.colorPing
import com.v2ray.ang.compose.colorPingRed
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.util.QRCodeDecoder
import androidx.compose.ui.graphics.asImageBitmap
import com.v2ray.ang.handler.ExpiryReminder
import com.v2ray.ang.handler.MessengerNotifier
import com.v2ray.ang.handler.SupportNotifier
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.server.*
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.handler.ApkUpdateInstaller
import com.v2ray.ang.handler.UpdatePrefetcher
import com.v2ray.ang.handler.VpnkaLogic
import com.v2ray.ang.handler.PowerSaveHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.v2ray.ang.handler.VpnkaAccount
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import kotlin.math.abs

class MainActivity : HelperBaseComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    // Launcher for profile editor activities (ServerActivity, ServerCustomConfigActivity, etc.)
    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
                ?: return@registerForActivityResult

            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) {
                return@registerForActivityResult
            }

            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE,
                false
            )

            mainViewModel.setupGroupTab(forceRefresh = true)

            if (restartService && mainViewModel.uiState.value.isRunning) {
                restartV2Ray()
            }
        }

    // Launcher for settings, subscription, routing, etc. (non-editor sever pages)
    private val settingsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val restartService = SettingsChangeManager.consumeRestartService()
            val refreshGroups = SettingsChangeManager.consumeSetupGroupTab()

            mainViewModel.refreshUiSettings()

            if (refreshGroups) {
                mainViewModel.setupGroupTab(forceRefresh = true)
            }

            if (restartService && mainViewModel.uiState.value.isRunning) {
                restartV2Ray()
            }
        }

    companion object {
        /** Intent extra: which screen to open on launch. */
        const val EXTRA_OPEN = "vpnka_open"
        const val OPEN_SUPPORT = "support"
        const val OPEN_MESSENGER = "messenger"
        /** Нажали уведомление «доступно обновление» — показать предложение
         *  установить, не считаясь с получасовой паузой: человек пришёл
         *  именно за этим. */
        const val OPEN_UPDATE = "update"
        /** A ringing-call notification was tapped: go straight to the call. */
        const val OPEN_CALL = "call"
        /** A home-screen shortcut: `EXTRA_OPEN = "desk:<appId>"` opens SmartDesk
         *  straight on that app (see «Добавить на рабочий стол»). */
        const val OPEN_DESK_PREFIX = "desk:"
        /** Intent extra: chat (peer client id) to open in the messenger. */
        const val EXTRA_CHAT = "vpnka_chat"
        /** Set once the review prompt has been raised, so it never repeats. */
        const val KEY_REVIEW_PROMPTED = "vpnka_review_prompted"

        /** Счёт, оплату которого мы ждём. Пусто — значит ждать нечего и
         *  сервер не опрашивается вообще. */
        const val KEY_PENDING_PAYMENT = "vpnka_pending_payment"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_UPDATE) forceUpdatePrompt = true
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_SUPPORT) showSupport = true
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_MESSENGER) openMessengerFromIntent(intent)
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_CALL) openCallFromIntent()
        openDeskFromIntent(intent)
    }

    /**
     * Убрать с дороги всё, что рисуется ПОВЕРХ рабочего стола.
     *
     * Экраны выбираются цепочкой ранних возвратов, и стол в ней последний:
     * если открыт магазин, поддержка или настройки, просьба «покажи стол»
     * ничего не меняла — уведомление о звонке или сообщении не открывало
     * ничего. Гасим верхние экраны, иначе просьба до стола не доходит.
     */
    private fun clearOverlaysForDesk() {
        showShop = false
        showTopUp = false
        showSupport = false
        showTickets = false
        showSettings = false
        showSubscription = false
        showRecovery = false
        showPlansList = false
        // Список закрывался не весь.
        //
        // Уведомление о сообщении или нажатие ярлыка открывали рабочий стол,
        // но открытая переписка поддержки, экран уведомлений, карточка
        // тарифа и выбор сервера оставались сверху — человек жал на
        // уведомление и попадал не туда. Особенно «Серверы»: под ним рабочий
        // стол вообще не рисуется.
        openedTicket = null
        openedPlan = null
        showNotificationSettings = false
        showPlanPicker = false
        showServerPicker = false
        showServers = false
    }

    /** A home-screen app shortcut was tapped: open SmartDesk on that app. */
    private fun openDeskFromIntent(intent: Intent) {
        val open = intent.getStringExtra(EXTRA_OPEN) ?: return
        if (!open.startsWith(OPEN_DESK_PREFIX)) return
        com.v2ray.ang.ui.SmartDeskChrome.pendingAppId = open.removePrefix(OPEN_DESK_PREFIX)
        clearOverlaysForDesk()
        showSmartDesk = true
    }

    /** A ringing-call notification was tapped. The engine is already holding
     *  the offer in this process, and the messenger draws the call screen for
     *  as long as it rings — so opening Messages is all it takes. */
    private fun openCallFromIntent() {
        com.v2ray.ang.ui.SmartDeskChrome.pendingAppId = "messages"
        clearOverlaysForDesk()
        showSmartDesk = true
    }

    /** A messenger notification was tapped: open SmartDesk on that chat. */
    private fun openMessengerFromIntent(intent: Intent) {
        val chat = intent.getLongExtra(EXTRA_CHAT, 0L)
        if (chat != 0L) com.v2ray.ang.handler.Messenger.requestOpenChat(chat)
        clearOverlaysForDesk()
        showSmartDesk = true
    }

    /**
     * Проверяем, не оплатился ли счёт, пока нас не было на экране.
     *
     * Оплата по СБП уводит человека в банковское приложение, и наша
     * активность сворачивается — вместе с любым циклом, который её
     * переживать не умеет. Поэтому настоящая проверка живёт ЗДЕСЬ: при
     * каждом возвращении в приложение.
     *
     * Опрашиваем НЕ постоянно: только пока в памяти лежит незавершённый
     * счёт. Как только он оплачен или окончательно умер — ключ стирается, и
     * запросов больше нет. Окно ограничено, чтобы не крутиться вечно, если
     * человек просто закрыл страницу оплаты.
     */
    /**
     * Стереть ключ незакрытого счёта — но только если там всё ещё НАШ счёт.
     *
     * Ключ один, а циклов ожидания может быть несколько: человек успел
     * завести второй счёт, пока первый ещё висел. Безусловное стирание
     * затирало бы указатель на новый счёт, и его оплату никто бы не отследил.
     */
    private fun clearPendingPayment(pid: Long) {
        val now = MmkvManager.decodeSettingsString(KEY_PENDING_PAYMENT)
        if (VpnkaLogic.pendingIsOurs(now, pid)) {
            MmkvManager.encodeSettings(KEY_PENDING_PAYMENT, "")
        }
    }

    /** Живой цикл ожидания оплаты — чтобы не плодить по одному на каждый выход на экран. */
    private var paymentWatch: Job? = null

    override fun onResume() {
        super.onResume()
        foregroundTick++
        val stored = MmkvManager.decodeSettingsString(KEY_PENDING_PAYMENT)
        val pid = VpnkaLogic.pendingId(stored) ?: return
        // Предельный срок жизни ключа. Ответ «ещё платят» мы получаем и когда
        // связи нет, и когда сервер отвечает непонятно, — без крайнего срока
        // такой счёт опрашивался бы вечно, каждые три секунды, при каждом
        // открытии приложения.
        if (VpnkaLogic.pendingExpired(
                stored, System.currentTimeMillis(), 24 * 60 * 60 * 1000L
            )
        ) {
            clearPendingPayment(pid)
            return
        }
        // Предыдущий цикл больше не нужен: он опрашивает тот же счёт и в конце
        // покажет свой собственный тост. lifecycleScope живёт до onDestroy, а
        // не до onPause, поэтому сами они не умирают.
        paymentWatch?.cancel()
        paymentWatch = lifecycleScope.launch {
            val deadline = System.currentTimeMillis() + 90_000L
            // Деньги учтены, но ключ ещё не выдан. Такое состояние живёт
            // считанные секунды, и в нём сервер ещё не может назвать
            // подписку — ждём именно её, а не просто «оплачено».
            var settledSeen = false
            while (System.currentTimeMillis() < deadline) {
                val st = VpnkaAccount.paymentState(pid)
                when (st.state) {
                    "settled" -> {
                        settledSeen = true
                        if (st.groupToken != null) {
                            clearPendingPayment(pid)
                            toast("Оплачено — подписка активна")
                            preferGroupToken = st.groupToken
                            selectNewestOnSync = true
                            subRefreshRequest++
                            return@launch
                        }
                        delay(3000)
                    }
                    // Счёт закрыт без денег: ждать больше нечего, иначе
                    // будем опрашивать до скончания века.
                    "dead" -> {
                        clearPendingPayment(pid)
                        return@launch
                    }
                    // Ещё платит или связи нет — подождём и спросим снова.
                    else -> delay(3000)
                }
            }
            // Окно вышло. Если деньги мы всё-таки видели, а имени подписки
            // так и не дождались — сообщаем и обновляемся вслепую, это
            // лучше, чем молчать. Ключ стираем: платёж состоялся.
            if (settledSeen) {
                clearPendingPayment(pid)
                toast("Оплачено — подписка активна")
                selectNewestOnSync = true
                subRefreshRequest++
            }
            // Счёт всё ещё жив: ключ НЕ стираем — спросим при следующем
            // возвращении в приложение.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.initialize()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}

        // Nothing to connect to: AngApplication has ensured the trial
        // subscription exists, but an entry with no servers behind it is
        // still an empty screen. Fetch it now so the user's first sight of
        // the app is a working server list. Reuses the normal update path,
        // so it shows the same spinner and toasts as a manual refresh.
        // Back, owned by the activity. Registered once, in priority order,
        // and disabled on the main screen so leaving the app there is still
        // the system's job.
        // Before anything draws: the palette is read during composition, and
        // applying it later would show the light screen first and repaint.
        VpnkaColors.dark = MmkvManager.isDarkTheme()
        // Палитра «Поток» идёт вместе с тёмной темой — она и есть тёмная
        // тема приложения. Светлая остаётся прежней.
        VpnkaColors.flow = VpnkaColors.dark
        ExpiryReminder.schedule(this)
        SupportNotifier.schedule(this)
        MessengerNotifier.schedule(this)
        // Holds the messenger socket while the app is off screen, so a call
        // rings instead of never arriving. No-op when switched off.
        com.v2ray.ang.service.VpnkaLinkService.start(this)

        // Launched by tapping a "поддержка ответила" notification: open the
        // chat rather than the home screen. onNewIntent handles the same for
        // an app that was already running.
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_UPDATE) {
            forceUpdatePrompt = true
            foregroundTick++
        }
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_SUPPORT) showSupport = true
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_MESSENGER) intent?.let { openMessengerFromIntent(it) }
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_CALL) openCallFromIntent()
        intent?.let { openDeskFromIntent(it) }

        onBackPressedDispatcher.addCallback(this) {
            if (!closeTopVpnkaScreen()) {
                // Nothing of ours is open: hand the press back to the
                // system so it closes the app as it always would.
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        // And again, straight at the platform.
        //
        // The androidx dispatcher above should be enough, and once the
        // manifest declared enableOnBackInvokedCallback the system did stop
        // reporting us as opted out — but the gesture still left the app.
        // Something between the OS and androidx wasn't delivering, so this
        // registration removes the middle entirely. Registered last, so on
        // API 33+ it is the one the system calls; below that the androidx
        // path above still runs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                // Nothing open means the press was meant for the system, and
                // at this level "close the app" is ours to do.
                if (!closeTopVpnkaScreen()) moveTaskToBack(true)
            }
        }

        if (AngApplication.vpnkaNeedsTrialFetch) {
            AngApplication.vpnkaNeedsTrialFetch = false
            importConfigViaSub()
        }

        // Reopened by the post-payment link. The subscription is settled by
        // the webhook rather than by the link, so the honest thing is to
        // re-read the profile — which is also exactly what someone who just
        // paid opens the app to see.
        if (AngApplication.vpnkaJustPaid) {
            AngApplication.vpnkaJustPaid = false
            vpnkaOpenProfileAfterPayment = true
        }
    }

    // Which overlay is open, owned by the activity rather than by the
    // composition.
    //
    // These were `rememberSaveable` inside ScreenContent, and back kept
    // reaching the system and closing the app. Two rounds of fixing the
    // Compose-side handler didn't change that, so the dependency on
    // composition is gone: the activity owns the state and answers back
    // through its own dispatcher, which is registered once in onCreate and
    // cannot be missed by a recomposition.
    /**
     * Closes the innermost screen of ours that is open.
     *
     * Returns false when none is, which is the caller's cue to let the press
     * mean what it means everywhere else — leave the app. Shared by both back
     * registrations so the two can never disagree about what is on top.
     */
    private fun closeTopVpnkaScreen(): Boolean = logBack(when {
        // SmartDesk steps back internally first (overlay/app → desktop); only
        // from the bare desktop does back close the whole surface.
        // Условие ТО ЖЕ, что и при отрисовке (`showSmartDesk && !showServers`).
        //
        // Иначе при открытых «Серверах» стол на экране не виден, а «назад»
        // всё равно уходила внутрь него: нажатие пропадало впустую, экран не
        // менялся.
        showSmartDesk && !showServers -> {
            if (smartDeskBack?.invoke() != true) showSmartDesk = false; true
        }
        openedTicket != null -> { openedTicket = null; true }
        showTickets -> { showTickets = false; true }
        showSupport -> { showSupport = false; true }
        showRecovery -> { showRecovery = false; true }
        showServerPicker -> { showServerPicker = false; true }
        showPlanPicker -> { showPlanPicker = false; true }
        openedPlan != null -> { openedPlan = null; true }
        showShop -> { showShop = false; true }
        showTopUp -> { showTopUp = false; true }
        showPlansList -> { showPlansList = false; true }
        showNotificationSettings -> { showNotificationSettings = false; true }
        showSettings -> { showSettings = false; true }
        showSubscription -> { showSubscription = false; true }
        showServers -> { showServers = false; true }
        else -> false
    })

    /**
     * Records what back did, so a logcat can say which of two things happened.
     *
     * Five fixes went in blind because "the gesture closes the app" cannot
     * tell apart *the press never arrived* from *nothing of ours was open*.
     * On the main screen leaving IS correct, and every capture so far was
     * taken there. One line here settles it.
     */
    private fun logBack(handled: Boolean): Boolean {
        android.util.Log.i(
            "VPNKA_BACK",
            "handled=$handled sub=$showSubscription settings=$showSettings " +
                "servers=$showServers plans=$showPlansList " +
                "plan=${openedPlan != null} support=$showSupport " +
                "recovery=$showRecovery",
        )
        return handled
    }

    private var showServers by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showNotificationSettings by mutableStateOf(false)
    private var showSubscription by mutableStateOf(false)
    // Mirrors subInfo.telegramLinked so code outside the composition can
    // ask the question. `goBuyOrLink` runs from a click handler that has no
    // access to composable state, and sending an unlinked user to the shop
    // is the one mistake that costs them money.
    private var vpnkaTelegramLinked by mutableStateOf(false)
    private var showSupport by mutableStateOf(false)
    private var showTickets by mutableStateOf(false)
    // Telegram link the user asked for while the tunnel was down. Held until
    // the VPN reports itself up, then opened.
    private var askVpnForTelegram by mutableStateOf(false)

    /** Включение отложено до приезда подписки — см. [startV2Ray]. */
    private var pendingStartAfterImport = false
    private var telegramLinkPending by mutableStateOf(false)
    private var openedTicket by mutableStateOf<VpnkaAccount.SupportTicket?>(null)
    private var showRecovery by mutableStateOf(false)
    // The rating sheet, and the server's own words when it was raised by a
    // `review_request` notice rather than by the home-screen row.
    private var showReview by mutableStateOf(false)
    private var reviewPrompt by mutableStateOf<String?>(null)
    private var reviewSending by mutableStateOf(false)
    private var showServerPicker by mutableStateOf(false)
    private var showPlanPicker by mutableStateOf(false)
    private var showPlansList by mutableStateOf(false)
    private var showShop by mutableStateOf(false)
    private var showTopUp by mutableStateOf(false)
    // SmartDesk full-screen surface + the reachability state behind its dot.
    private var showSmartDesk by mutableStateOf(false)

    /** Получать ли тестовые сборки. Читается из хранилища при первом обращении. */
    private var betaChannel by mutableStateOf(MmkvManager.betaChannel())
    private var smartDeskOnline by mutableStateOf(false)
    // Privacy toggle: hide the SmartDesk entry; a 5-tap corner gesture reveals it.
    private var smartDeskHidden by mutableStateOf(MmkvManager.decodeSettingsBool("vpnka_smartdesk_hidden"))
    // SmartDesk's internal back (pop overlay/app → desktop). Returns true if it
    // handled the press; back closes the whole surface only when it returns false.
    private var smartDeskBack: (() -> Boolean)? = null
    // Bumped when the zero-knowledge vault is unlocked, to re-evaluate the gate.
    private var vaultTick by mutableStateOf(0)
    // Set right before a profile refresh that follows claiming/buying a plan,
    // so the sync activates the newly-acquired subscription (its radio).
    private var selectNewestOnSync by mutableStateOf(false)

    /**
     * Токен подписки, которую только что оплатили, — прямо со слов сервера.
     *
     * Догадка «выбрать самую долгоживущую» здесь не работает: у владельца
     * годового тарифа купленный сверх него месяц долгоживущим не является
     * никогда, и покупка выглядела как несостоявшаяся. Точный ответ есть у
     * сервера, он и приходит сюда.
     */
    private var preferGroupToken by mutableStateOf<String?>(null)

    /**
     * Счётчик выходов приложения на экран.
     *
     * Обновление предлагаем не только при холодном старте: человек может
     * неделями не закрывать приложение, а «при старте» для него не
     * наступает никогда. Экран открыт — значит, момент подходящий.
     */
    private var foregroundTick by mutableStateOf(0)

    /** Пришли по уведомлению об обновлении — паузу показа игнорируем. */
    private var forceUpdatePrompt by mutableStateOf(false)

    /**
     * Мы в маленьком окне поверх других приложений.
     *
     * Система уменьшает ВСЮ активность, а не один плеер. Без этого признака
     * в окошко 16:9 попадали шапка, заголовок и лента кнопок — кнопка
     * обещала картинку-в-картинке, а давала уменьшенный снимок интерфейса.
     */
    var inPip by mutableStateOf(false)
        private set

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }
    private var openedPlan by mutableStateOf<VpnkaAccount.Plan?>(null)

    /** Set by the post-payment link; consumed on the next composition. */
    private var vpnkaOpenProfileAfterPayment = false

    /** Просьба перечитать подписки, которую можно подать ИЗВНЕ композиции.
     *
     *  Обычный `subReload` живёт внутри экрана, и из `onResume` до него не
     *  дотянуться — а именно оттуда приходит новость об оплате. */
    private var subRefreshRequest by mutableStateOf(0)

    @Composable
    override fun ScreenContent() {
        // Our one-button screen is what the app opens on; upstream's full
        // server UI lives behind the long-press escape hatch below. Wrapping
        // rather than editing MainScreen keeps their releases mergeable as-is.
        var subInfo by remember { mutableStateOf<VpnkaAccount.Info?>(null) }
        var subLoading by remember { mutableStateOf(false) }
        var subReload by remember { mutableIntStateOf(0) }
        var signedIn by remember { mutableStateOf(VpnkaAccount.isSignedIn()) }
        var signingIn by remember { mutableStateOf(false) }
        var signInError by remember { mutableStateOf<String?>(null) }
        var supportMessages by remember {
            mutableStateOf<List<VpnkaAccount.SupportMessage>>(emptyList())
        }
        var supportLoading by remember { mutableStateOf(false) }
        var supportSending by remember { mutableStateOf(false) }
        var supportReload by remember { mutableIntStateOf(0) }

        LaunchedEffect(showSupport, supportReload) {
            if (showSupport) {
                supportLoading = true
                supportMessages = VpnkaAccount.fetchSupport()
                supportLoading = false
            }
        }

        var subs by remember { mutableStateOf(MmkvManager.vpnkaSubscriptions()) }

        // Light the SmartDesk dot: ping once the profile says the feature is on,
        // and re-ping whenever the surface opens so it reflects "right now".
        LaunchedEffect(subInfo?.smartDeskEnabled, showSmartDesk) {
            if (subInfo?.smartDeskEnabled == true) {
                smartDeskOnline = VpnkaAccount.smartDeskOnline()
                // Служба связи стартует в onCreate, когда профиля ещё нет и
                // разрешение на SmartDesk неизвестно. Профиль пришёл —
                // поднимаем сейчас, иначе звонки не звонили бы до
                // следующего запуска приложения.
                com.v2ray.ang.service.VpnkaLinkService.start(this@MainActivity)
            }
        }

        LaunchedEffect(Unit) {
            if (vpnkaOpenProfileAfterPayment) {
                vpnkaOpenProfileAfterPayment = false
                showSubscription = true
                subReload++
            }
        }

        // Fetch only while the screen is open, and again on retry. Polling
        // it in the background would spend requests on a card nobody is
        // looking at — days-left doesn't change while you watch it.
        // Not gated on the profile screen being open. It used to be, and that
        // was the bug: someone who updates the app and goes straight to the
        // connect button never opens «Профиль», so the plans were never
        // synced — the shipped trial stayed in the list and stayed selected
        // while their paid subscription sat unused.
        LaunchedEffect(showSubscription, subReload, signedIn, subRefreshRequest) {
            if (signedIn) {
                subLoading = showSubscription
                val fetched = VpnkaAccount.fetchInfo()
                subInfo = fetched
                vpnkaTelegramLinked = fetched?.telegramLinked == true
                // A null answer with a token still stored is just a network
                // failure; a null answer *and* no token means the backend
                // told us the session is gone — the user revoked this device
                // from the bot. Reflect that instead of leaving them on a
                // screen that will never load.
                signedIn = VpnkaAccount.isSignedIn()

                // Signing in is also how a paid subscription reaches the
                // phone: the profile carries the subscription token, so we
                // can swap the shipped trial for the real thing instead of
                // asking the user to copy a URL out of the bot.
                // One group per plan. The profile lists them newest-expiry
                // first, so the fallback selection lands on the longest-lived
                // subscription rather than an arbitrary one.
                // Finished plans are excluded here, not just hidden in the
                // list: a group built for an expired plan serves nothing,
                // and leaving it in the picker is how someone selects a
                // subscription and finds no servers behind it.
                val live = fetched?.subscriptions.orEmpty()
                    .filter { (it.daysLeft ?: 0) >= 0 }
                    // Longest-lived first: syncSubscriptions treats the first as
                    // the one to fall back to / activate, and a just-acquired
                    // month or plan is the one with the most days left.
                    .sortedByDescending { it.daysLeft ?: 0 }
                // Одинаково названные планы — не редкость: два купленных
                // месяца зовутся одним и тем же тарифом, и в списке их не
                // отличить друг от друга. Различаем датой окончания, но
                // только когда имя действительно повторяется — у тех, у кого
                // план один, подпись остаётся прежней.
                val labels = VpnkaLogic.planLabels(
                    live.map { (it.tariff ?: "VPNka") to it.expiresAt }
                )
                val plans = live.mapIndexedNotNull { i, plan ->
                    val token = plan.groupToken ?: return@mapIndexedNotNull null
                    token to (labels.getOrNull(i) ?: plan.tariff ?: "VPNka")
                }
                if (plans.isNotEmpty()) {
                    val switched = MmkvManager.syncSubscriptions(
                        plans,
                        preferNewest = selectNewestOnSync,
                        preferToken = preferGroupToken,
                    )
                    subs = MmkvManager.vpnkaSubscriptions()
                    // The groups the viewmodel knows about are now out of
                    // date — sync may have created one per plan. Everything
                    // that selects a plan goes through the viewmodel, so
                    // leaving it with the old list is what made the first tap
                    // on a newly-appeared subscription do nothing.
                    mainViewModel.setupGroupTab(forceRefresh = true)
                    if (switched != null) {
                        // The picker reads uiState.selectedGroupId first, so
                        // MMKV alone isn't enough — the viewmodel has to be
                        // told. This holds for ANY switch, not only a plan
                        // just bought: signing out forgets the active pick, so
                        // the next sign-in has sync activate the longest-lived
                        // plan — and while this line was gated on
                        // `selectNewestOnSync`, the app came back with the
                        // subscription stored but nothing selected on screen.
                        mainViewModel.subscriptionIdChanged(switched)
                        // Same path a manual refresh takes, so the user sees
                        // the familiar spinner and toasts rather than servers
                        // appearing out of nowhere.
                        importConfigViaSub()
                    }
                    selectNewestOnSync = false
                    // Токен сбрасываем, только если названная сервером
                    // подписка реально была в профиле и попала в список.
                    // Безусловный сброс возвращал исходный дефект: профиль
                    // не успел показать покупку — точный ответ выброшен,
                    // выбирается «самая долгоживущая», и у владельца
                    // годового тарифа месяц снова не появляется.
                    if (preferGroupToken != null &&
                        plans.any { it.first == preferGroupToken }
                    ) {
                        preferGroupToken = null
                    }
                }
                subLoading = false
            }
        }
        var updateVersion by remember { mutableStateOf<String?>(null) }
        // Уже скачанное обновление, ждущее одного касания.
        var stagedUpdate by remember {
            mutableStateOf<Pair<String, java.io.File>?>(null)
        }

        // Читаем с ДИСКА, а не из сети, и на каждом выходе на экран.
        //
        // Раньше плашка про обновление показывалась только если проверка
        // манифеста прошла прямо сейчас. Файл мог месяц лежать готовым, но
        // без связи с зеркалом на экране было пусто — скачанное обновление
        // оказывалось невидимым ровно для тех, у кого со связью хуже всех.
        var showUpdatePrompt by remember { mutableStateOf(false) }
        LaunchedEffect(foregroundTick) {
            // Манифест перечитываем и здесь, не чаще раза в шесть часов.
            // Раньше это делалось только при ХОЛОДНОМ старте — а человек без
            // Wi-Fi, который приложение не закрывает, до холодного старта не
            // доходит никогда, и четырнадцатидневный отсчёт до загрузки по
            // мобильному у него не начинался вовсе.
            runCatching {
                withContext(Dispatchers.IO) {
                    UpdatePrefetcher.checkIfDue(this@MainActivity)
                }
            }
            // Разрешение на SmartDesk могло вернуться после случайного отказа
            // сервера — служба связи сама об этом не узнает, её эффект висит
            // на значении из профиля, которое не менялось. Старт идемпотентен.
            if (VpnkaAccount.smartDeskAllowed()) {
                com.v2ray.ang.service.VpnkaLinkService.start(this@MainActivity)
            }
            val staged = ApkUpdateInstaller.readyUpdate(this@MainActivity)
            stagedUpdate = staged
            if (staged != null) {
                updateVersion = staged.first
                // Пауза между показами не действует, когда человек сам
                // нажал уведомление: он пришёл именно за установкой.
                if (forceUpdatePrompt || ApkUpdateInstaller.promptDue(staged.first)) {
                    forceUpdatePrompt = false
                    showUpdatePrompt = true
                }
            }
        }
        var claimingFreeMonth by remember { mutableStateOf(false) }
        var askBattery by remember { mutableStateOf(PowerSaveHelper.shouldPrompt(this)) }

        // Check on every launch. It's a few hundred bytes of JSON, so it can
        // run on any network — unlike the APK itself, which stays Wi-Fi-only
        // because it would otherwise cross our own nodes at 32 MB a head.
        LaunchedEffect(Unit) {
            runCatching {
                withContext(Dispatchers.IO) {
                    UpdateCheckerManager.checkForUpdate(includePreRelease = false)
                }
            }.onSuccess { result ->
                if (result.hasUpdate) {
                    updateVersion = result.latestVersion
                    // Качаем заранее, но не любой ценой: noteAvailable
                    // помнит, с какого дня мы ждём Wi-Fi, и через две
                    // недели разрешает мобильный трафик — иначе тот, у кого
                    // Wi-Fi не бывает, не обновится никогда.
                    result.latestVersion?.let {
                        UpdatePrefetcher.noteAvailable(this@MainActivity, it)
                    }
                }
            }
        }
        val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

        // Inner screens read this to paint themselves green while the
        // tunnel is up, the same as the main screen.
        VpnkaColors.connected = uiState.isRunning

        // The link was asked for before the VPN was up. Open it the moment
        // it is — waiting for the user to tap again would lose the thread
        // of what they were doing.
        LaunchedEffect(uiState.isRunning) {
            if (telegramLinkPending && uiState.isRunning) {
                telegramLinkPending = false
                openTelegramLink()
            }
        }

        // One-time "please rate us" prompt. The server queues a
        // `review_request` notice (POST /admin/clients/app-notify); the app
        // raises it once, on a launch, and never again.
        //
        // The "never again" is a local flag rather than the server's read
        // marker on purpose: /app/notifications/read clears *every* unread
        // notice, so using it here would silently swallow a support reply
        // the person hasn't seen yet. The server keeps the notice unread;
        // nothing else reads that kind, and a reinstall asking once more is
        // a fair price for not eating support mail.
        LaunchedEffect(Unit) {
            if (MmkvManager.decodeSettingsBool(KEY_REVIEW_PROMPTED)) {
                return@LaunchedEffect
            }
            if (MmkvManager.getAccountToken() == null) return@LaunchedEffect
            val notice = runCatching { VpnkaAccount.fetchNotices() }
                .getOrNull()
                ?.firstOrNull { it.kind == "review_request" && !it.read }
                ?: return@LaunchedEffect
            MmkvManager.encodeSettings(KEY_REVIEW_PROMPTED, true)
            reviewPrompt = notice.body
            showReview = true
        }

        if (showReview) {
            VpnkaReviewDialog(
                prompt = reviewPrompt,
                sending = reviewSending,
                onDismiss = {
                    showReview = false
                    reviewSending = false
                },
                onSubmit = { stars, comment ->
                    reviewSending = true
                    lifecycleScope.launch {
                        val ok = VpnkaAccount.submitReview(stars, comment)
                        reviewSending = false
                        if (ok) {
                            showReview = false
                            toast("Спасибо! Отзыв отправлен")
                        } else {
                            // Sheet stays open with the text intact — a
                            // failed send must not eat what they wrote.
                            toast("Не удалось отправить, попробуйте позже")
                        }
                    }
                },
            )
        }

        if (askVpnForTelegram) {
            AlertDialog(
                onDismissRequest = { askVpnForTelegram = false },
                title = { Text("Включить VPN?") },
                text = {
                    Text(
                        "Telegram у большинства провайдеров заблокирован — " +
                            "без VPN ссылка не откроется. Включим и сразу " +
                            "перейдём в бота."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        askVpnForTelegram = false
                        telegramLinkPending = true
                        handleFabAction()
                    }) { Text("Включить") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        askVpnForTelegram = false
                        // Their call: some networks pass Telegram fine, and
                        // refusing to open the link would be worse than a
                        // page that might not load.
                        openTelegramLink()
                    }) { Text("Открыть без VPN") }
                },
            )
        }

        // Which plan is active comes from the viewmodel, not from a second
        // copy kept here. Two sources of truth for this is what put the
        // server list and the config on different subscriptions.
        val selectedSub = uiState.selectedGroupId.ifBlank {
            MmkvManager.selectedSubscriptionGuid()
        }

        val servers by mainViewModel
            .serversForGroup(uiState.selectedGroupId)
            .collectAsStateWithLifecycle()

        // Готовое обновление: файл уже на диске, остаётся одно касание.
        //
        // Молча поставить его нельзя ни при каких условиях — Android
        // разрешает тихую установку только системным приложениям и
        // устройствам под управлением организации. Наше ставится сбоку,
        // поэтому потолок здесь честный: «скачано заранее, одно касание».
        stagedUpdate?.let { (version, apk) ->
            if (showUpdatePrompt) {
                val allowed = ApkUpdateInstaller.canInstall(this@MainActivity)
                AlertDialog(
                    onDismissRequest = {
                        ApkUpdateInstaller.markPrompted(version)
                        showUpdatePrompt = false
                    },
                    title = { Text("Обновление $version готово") },
                    text = {
                        Text(
                            if (allowed) {
                                "Файл уже скачан — установка займёт несколько " +
                                    "секунд и не потратит трафик."
                            } else {
                                // Разрешение спрашиваем ЗАРАНЕЕ, а не в тот
                                // момент, когда человек уже нажал «установить»
                                // и ждёт результата: выдаётся оно только
                                // вручную в настройках, и просить о походе
                                // туда посреди установки — верный способ
                                // потерять человека на полпути.
                                "Файл уже скачан. Android ставит приложения " +
                                    "не из магазина только с вашего разрешения " +
                                    "— выдайте его один раз, и обновления " +
                                    "будут ставиться в одно касание."
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showUpdatePrompt = false
                            if (allowed) {
                                ApkUpdateInstaller.markPrompted(version)
                                ApkUpdateInstaller.promptInstall(
                                    this@MainActivity, apk
                                )
                            } else {
                                // Пометку «уже предлагали» здесь НЕ ставим:
                                // человек уходит выдавать разрешение и через
                                // несколько секунд вернётся. С пометкой его
                                // встречала бы получасовая тишина — то есть
                                // шаг «спросить заранее» приводил ровно к
                                // тому провалу, который мы им и убирали.
                                startActivity(
                                    ApkUpdateInstaller.installPermissionIntent(
                                        this@MainActivity
                                    )
                                )
                            }
                        }) { Text(if (allowed) "Установить" else "Разрешить") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            ApkUpdateInstaller.markPrompted(version)
                            showUpdatePrompt = false
                        }) {
                            Text("Позже")
                        }
                    },
                )
            }
        }

        if (askBattery) {
            AlertDialog(
                onDismissRequest = {
                    PowerSaveHelper.markPrompted()
                    askBattery = false
                },
                title = { Text("Чтобы VPN не отключался") },
                text = {
                    Text(
                        "Android усыпляет приложения в фоне — соединение может " +
                            "обрываться через несколько минут после блокировки " +
                            "экрана. Разрешите работу без ограничений батареи."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        PowerSaveHelper.markPrompted()
                        askBattery = false
                        PowerSaveHelper.openExemptionRequest(this@MainActivity)
                    }) { Text("Разрешить") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        PowerSaveHelper.markPrompted()
                        askBattery = false
                    }) { Text("Позже") }
                },
            )
        }

        // A second back handler, registered during composition.
        //
        // The activity already registers one in onCreate. That should be
        // enough, and by every reading of the code it is — but the gesture
        // still closed the app, while the on-screen ‹ button worked, which
        // says the press never reached the dispatcher rather than that the
        // state was wrong.
        //
        // Compose registers this one later, and Android dispatches to the
        // most recently added enabled callback first. Belt and braces: if
        // either mechanism is delivered, back stays inside the app.
        val anyOverlay = showSupport || showRecovery ||
            showServerPicker || showPlanPicker || showPlansList ||
            showShop || showTopUp ||
            openedPlan != null || showSubscription ||
            showSettings || showNotificationSettings ||
            showServers || showTickets || openedTicket != null
        BackHandler(enabled = anyOverlay) { closeTopVpnkaScreen() }

        openedTicket?.let { ticket ->
            var thread by remember(ticket.id) {
                mutableStateOf<List<VpnkaAccount.SupportMessage>>(emptyList())
            }
            var threadLoading by remember(ticket.id) { mutableStateOf(true) }
            LaunchedEffect(ticket.id) {
                thread = VpnkaAccount.fetchTicket(ticket.id)
                threadLoading = false
            }
            VpnkaTicketThreadScreen(
                subject = ticket.subject,
                loading = threadLoading,
                messages = thread,
                onBack = { openedTicket = null },
            )
            return
        }

        if (showTickets && !showServers) {
            var tickets by remember { mutableStateOf<List<VpnkaAccount.SupportTicket>>(emptyList()) }
            var ticketsLoading by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                tickets = VpnkaAccount.fetchTickets()
                ticketsLoading = false
            }
            VpnkaTicketsScreen(
                loading = ticketsLoading,
                tickets = tickets,
                onOpen = { openedTicket = it },
                onBack = { showTickets = false },
            )
            return
        }

        if (showSupport && !showServers) {
            VpnkaSupportScreen(
                loading = supportLoading,
                sending = supportSending,
                messages = supportMessages,
                onSend = { text ->
                    supportSending = true
                    lifecycleScope.launch {
                        VpnkaAccount.sendSupport(text)
                        supportSending = false
                        supportReload++
                    }
                },
                onSendImage = { bytes, mime ->
                    supportSending = true
                    lifecycleScope.launch {
                        val ok = VpnkaAccount.sendSupportImage(bytes, mime)
                        supportSending = false
                        if (ok) supportReload++ else toast("Не удалось отправить скриншот")
                    }
                },
                onHistory = { showTickets = true },
                onBack = { showSupport = false },
            )
            return
        }

        if (showRecovery && !showServers) {
            VpnkaRecoveryScreen(
                code = MmkvManager.getRecoveryCode(),
                onBack = { showRecovery = false },
            )
            return
        }

        // Above the profile, not below it. Settings is opened from inside the
        // profile, and the profile block returns — so while it sat lower the
        // flag was set and the screen never changed. Placed here, back from
        // settings lands on the profile it was opened from.
        if (showSettings && !showNotificationSettings && !showServers) {
            VpnkaSettingsScreen(
                onPerAppProxy = { navigateTo("per_app_proxy") },
                batteryExempt = PowerSaveHelper.isExempt(this),
                onFixBattery = { PowerSaveHelper.openExemptionRequest(this) },
                notificationsEnabled = NotificationManagerCompat.from(this)
                    .areNotificationsEnabled(),
                onFixNotifications = {
                    // The runtime prompt only appears once; after that only
                    // the system screen can turn them back on, so go there
                    // directly rather than firing a request that no longer
                    // shows anything.
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            packageName,
                        )
                    )
                },
                onCheckUpdate = { navigateTo("check_update") },
                onNotificationSettings = { showNotificationSettings = true },
                smartDeskEligible = subInfo?.smartDeskEnabled == true,
                smartDeskHidden = smartDeskHidden,
                onSmartDeskHiddenChange = { hidden ->
                    smartDeskHidden = hidden
                    MmkvManager.encodeSettings("vpnka_smartdesk_hidden", hidden)
                },
                betaChannel = betaChannel,
                onBetaChannelChange = { on ->
                    betaChannel = on
                    MmkvManager.setBetaChannel(on)
                },
                onBack = { showSettings = false },
            )
            return
        }

        // Above the settings screen it is opened from — same reason the settings
        // block sits above the profile. Back from here lands on settings.
        if (showNotificationSettings && !showServers) {
            val info = subInfo
            var inApp by remember(info) { mutableStateOf(info?.notifyExpiryInApp ?: true) }
            var inTg by remember(info) { mutableStateOf(info?.notifyExpiryInTelegram ?: true) }
            var email by remember(info) { mutableStateOf(info?.email ?: "") }
            var saving by remember { mutableStateOf(false) }
            VpnkaNotificationsScreen(
                inApp = inApp,
                inTelegram = inTg,
                telegramLinked = info?.telegramLinked == true,
                email = email,
                saving = saving,
                onInApp = { inApp = it },
                onInTelegram = { inTg = it },
                onEmail = { email = it },
                onSave = {
                    saving = true
                    lifecycleScope.launch {
                        val ok = VpnkaAccount.saveSettings(inApp, inTg, email.trim())
                        saving = false
                        if (ok) {
                            // Reflect the saved values locally so re-opening the
                            // screen (or the profile) shows them without a refetch.
                            subInfo = info?.copy(
                                notifyExpiryInApp = inApp,
                                notifyExpiryInTelegram = inTg,
                                email = email.trim().ifEmpty { null },
                            )
                            Toast.makeText(
                                this@MainActivity,
                                "Сохранено",
                                Toast.LENGTH_SHORT,
                            ).show()
                            showNotificationSettings = false
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Не удалось сохранить",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                onBack = { showNotificationSettings = false },
            )
            return
        }

        // !showShop: the shop opens from this profile and its block sits below,
        // so without this guard the profile keeps rendering and «Купить в
        // приложении» does nothing (same shadowing the settings screens had).
        if (showSubscription && !showShop && !showTopUp && !showServers) {
            VpnkaSubscriptionScreen(
                loading = subLoading,
                signedIn = signedIn,
                telegramLinked = subInfo?.telegramLinked == true,
                signingIn = signingIn,
                signInError = signInError,
                info = subInfo,
                onSignIn = { code ->
                    signingIn = true
                    signInError = null
                    lifecycleScope.launch {
                        // Шестизначный — вход из бота, шестнадцатизначный —
                        // восстановление. Разделяем по длине: у человека в
                        // руках всегда что-то одно, и спрашивать «какой это
                        // код» значило бы перекладывать на него нашу задачу.
                        val result = if (code.length == 16) {
                            VpnkaAccount.recover(code)
                        } else {
                            VpnkaAccount.signIn(code)
                        }
                        signingIn = false
                        result.fold(
                            onSuccess = {
                                signedIn = true
                                subReload++
                            },
                            onFailure = { error ->
                                signInError =
                                    if (error is VpnkaAccount.InvalidCodeException) {
                                        // Wrong, expired and already-used are one
                                        // answer from the server on purpose, so
                                        // the message covers all three.
                                        if (code.length == 16) {
                                            "Код восстановления не подошёл. " +
                                                "Проверьте, что переписали все 16 знаков."
                                        } else {
                                            "Код не подошёл. Он живёт 10 минут и " +
                                                "срабатывает один раз — возьмите новый в боте."
                                        }
                                    } else {
                                        "Не удалось войти — проверьте интернет"
                                    }
                            },
                        )
                    }
                },
                onSignOut = {
                    lifecycleScope.launch {
                        VpnkaAccount.signOut()
                        subInfo = null
                        vpnkaTelegramLinked = false
                        // signOut already dropped the account's subscriptions
                        // and servers from storage; now flush what the UI still
                        // holds in memory, or the connect screen keeps showing
                        // the old server/plan and the paid card order.
                        subs = MmkvManager.vpnkaSubscriptions()
                        openedPlan = null
                        showShop = false
                        showTopUp = false
                        showRecovery = false
                        showSupport = false
                        showSubscription = false
                        selectNewestOnSync = false
                        // Logout leaves no account, and register() only runs at
                        // app start — so «Месяц бесплатно» (which needs a
                        // profile) vanished and the user was stranded on an
                        // empty home screen. Re-register a fresh anonymous
                        // account right here, as on first launch, so they land
                        // back as a new user who can claim a month. Clear any
                        // stale revoked flag first or register() refuses.
                        MmkvManager.setSessionRevoked(false)
                        VpnkaAccount.register()
                        // …and give that fresh account the 24-hour trial,
                        // exactly as a first launch would. Signing out wipes
                        // every subscription including the trial, but the
                        // only code that re-seeds it runs in
                        // AngApplication.onCreate — which does not happen
                        // again while the process lives. Without this the
                        // person is left with no servers at all: the connect
                        // button falls through to upstream's «выберите файл
                        // конфигурации» until they force-quit the app. The
                        // profile sync below cannot cover it either — it only
                        // syncs when the account already has plans, and a
                        // brand-new anonymous account has none.
                        if (MmkvManager.ensureTrialSubscription()) {
                            importConfigViaSub()
                        }
                        signedIn = VpnkaAccount.isSignedIn()
                        subReload++
                        mainViewModel.setupGroupTab(forceRefresh = true)
                        mainViewModel.reloadServerList()
                    }
                },
                onGetCode = { navigateTo("vpnka_app_code") },
                onRenew = { navigateTo("vpnka_buy") },
                onBuyInApp = { showShop = true },
                onSupport = { showSupport = true },
                onTopUp = { showTopUp = true },
                onShowRecovery = { showRecovery = true },
                onOpenSettings = { showSettings = true },
                onLinkTelegram = { openTelegramLinkGuarded() },
                onRetry = { subReload++ },
                onBack = { showSubscription = false },
            )
            return
        }

        openedPlan?.let { plan ->
            var devices by remember(plan.groupToken) {
                mutableStateOf<List<VpnkaAccount.Device>>(emptyList())
            }
            var devicesLoading by remember(plan.groupToken) { mutableStateOf(true) }
            var deviceReload by remember(plan.groupToken) { mutableIntStateOf(0) }
            val qr = remember(plan.groupToken) {
                plan.groupToken?.let { token ->
                    // The plan's own URL, not the account-wide one: scanning
                    // this on another phone should add this subscription and
                    // nothing else.
                    QRCodeDecoder.createQRCode(VpnkaAccount.subscriptionUrl(token), 600)
                        ?.asImageBitmap()
                }
            }

            LaunchedEffect(plan.groupToken, deviceReload) {
                val token = plan.groupToken
                if (token != null) {
                    devicesLoading = true
                    devices = VpnkaAccount.fetchDevices(token)
                    devicesLoading = false
                } else {
                    devicesLoading = false
                }
            }

            VpnkaPlanDetailScreen(
                plan = plan,
                devices = devices,
                devicesLoading = devicesLoading,
                qr = qr,
                onCopySubscription = {
                    plan.groupToken?.let { token ->
                        Utils.setClipboard(
                            this@MainActivity,
                            VpnkaAccount.subscriptionUrl(token),
                        )
                        toast("Ссылка на подписку скопирована")
                    }
                },
                onShareSubscription = {
                    plan.groupToken?.let { token ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                VpnkaAccount.subscriptionUrl(token),
                            )
                        }
                        startActivity(
                            Intent.createChooser(send, "Поделиться подпиской")
                        )
                    }
                },
                onRevokeDevice = { id ->
                    val token = plan.groupToken ?: return@VpnkaPlanDetailScreen
                    lifecycleScope.launch {
                        VpnkaAccount.revokeDevice(token, id)
                        deviceReload++
                        // The slot count on the card comes from the profile,
                        // so it has to be re-read or it keeps showing the
                        // device we just removed.
                        subReload++
                    }
                },
                onRenameDevice = { id, name ->
                    val token = plan.groupToken ?: return@VpnkaPlanDetailScreen
                    lifecycleScope.launch {
                        val ok = VpnkaAccount.renameDevice(token, id, name)
                        if (ok) deviceReload++ else toast("Не удалось переименовать")
                    }
                },
                onBack = { openedPlan = null },
            )
            return
        }

        if (showShop && !showServers) {
            var tariffs by remember { mutableStateOf<List<VpnkaAccount.Tariff>>(emptyList()) }
            var shopLoading by remember { mutableStateOf(true) }
            var buyingId by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(showShop) {
                shopLoading = true
                tariffs = VpnkaAccount.fetchTariffs()
                shopLoading = false
            }
            VpnkaShopScreen(
                tariffs = tariffs,
                loading = shopLoading,
                buyingId = buyingId,
                onBuyBalance = { id ->
                    buyingId = id
                    lifecycleScope.launch {
                        when (val r = VpnkaAccount.purchase(id, "balance")) {
                            is VpnkaAccount.PurchaseResult.Settled -> {
                                toast("Оплачено — подписка активна")
                                // Активируем ИМЕННО купленное.
                                //
                                // Сервер называет адрес купленной подписки, а
                                // мы его выбрасывали и просили «самую
                                // долгоживущую». У владельца годового тарифа
                                // купленный сверх него месяц так и не
                                // становился активным — та самая жалоба,
                                // которую для оплаты картой уже закрыли.
                                preferGroupToken = r.subscriptionUrl
                                    ?.substringAfterLast("/g/")
                                    ?.takeIf { it.isNotBlank() }
                                selectNewestOnSync = preferGroupToken == null
                                subReload++
                                showShop = false
                            }
                            is VpnkaAccount.PurchaseResult.Failed -> toast(r.message)
                            else -> {}
                        }
                        buyingId = null
                    }
                },
                onBuyCard = { id ->
                    buyingId = id
                    lifecycleScope.launch {
                        when (val r = VpnkaAccount.purchase(id, "card")) {
                            // The processor's page; paying there returns to
                            // /paid/app. Same invoice the bot creates.
                            is VpnkaAccount.PurchaseResult.PayByCard -> {
                                Utils.openUri(this@MainActivity, r.url)
                                // Страница оплаты открывается снаружи и
                                // обратно ничего не сообщает — поэтому ждём
                                // сами. Раньше приложение после этого шага
                                // не спрашивало НИЧЕГО, и оплаченная подписка
                                // появлялась только при следующем обновлении
                                // профиля: «оплата прошла, а в приложении
                                // ничего не произошло».
                                val pid = r.paymentId
                                if (pid != null) {
                                    // Запоминаем счёт НА ДИСКЕ: оплата по СБП
                                    // уводит в банковское приложение, нашу
                                    // активность система сворачивает, и цикл
                                    // ниже вместе с ней умирает. Так и вышло
                                    // 29.08: приложение успело спросить семь
                                    // раз за 21 секунду, а деньги дошли на
                                    // 27-й — и новость никто не услышал.
                                    // Настоящая проверка идёт в onResume.
                                    MmkvManager.encodeSettings(
                                        KEY_PENDING_PAYMENT,
                                        VpnkaLogic.formatPending(
                                            pid, System.currentTimeMillis()
                                        ),
                                    )
                                    lifecycleScope.launch {
                                        val deadline = System.currentTimeMillis() +
                                            20 * 60 * 1000L
                                        while (System.currentTimeMillis() < deadline) {
                                            delay(3000)
                                            val st = VpnkaAccount.paymentState(pid)
                                            if (st.state == "settled" &&
                                                st.groupToken != null
                                            ) {
                                                clearPendingPayment(pid)
                                                toast("Оплачено — подписка активна")
                                                preferGroupToken = st.groupToken
                                                selectNewestOnSync = true
                                                subReload++
                                                showShop = false
                                                break
                                            }
                                            // Счёт закрыт без денег — ждать
                                            // нечего. Без этой ветки цикл
                                            // добивал все двадцать минут,
                                            // до четырёхсот запросов по
                                            // заведомо мёртвому счёту.
                                            if (st.state == "dead") {
                                                clearPendingPayment(pid)
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                            is VpnkaAccount.PurchaseResult.Failed -> toast(r.message)
                            else -> {}
                        }
                        buyingId = null
                    }
                },
                onBack = { showShop = false },
            )
            return
        }

        if (showTopUp && !showServers) {
            var submitting by remember { mutableStateOf(false) }
            VpnkaTopUpScreen(
                balanceRub = subInfo?.balanceRub,
                submitting = submitting,
                onPay = { amt ->
                    submitting = true
                    lifecycleScope.launch {
                        val url = VpnkaAccount.topUp(amt)
                        submitting = false
                        if (url != null) {
                            // RuKassa page (СБП/card); returns to /paid/app.
                            Utils.openUri(this@MainActivity, url)
                            showTopUp = false
                        } else {
                            toast("Не удалось создать платёж")
                        }
                    }
                },
                onBack = { showTopUp = false },
            )
            return
        }

        if (showPlansList && !showServers) {
            VpnkaPlansListScreen(
                // Finished plans are history, not choices: leaving them in
                // the list invites someone to select one and conclude the
                // app is broken when no servers appear behind it.
                plans = subInfo?.subscriptions.orEmpty()
                    .filter { (it.daysLeft ?: 0) >= 0 },
                activeToken = MmkvManager.vpnkaTokenForGuid(selectedSub),
                trialHoursLeft = subInfo?.trialHoursLeft,
                // Same door as «Подключить Telegram»: the month is granted
                // by the bot on arrival, and the link carries the token that
                // ties it to this install.
                onGetFreeMonth = { openTelegramLinkGuarded() },
                // Switching the plan switches the local subscription group
                // the server list is drawn from; the effect watching that
                // list then moves the selection to a server that exists in
                // it. Going through the viewmodel keeps the group and the
                // config on the same subscription — two sources of truth
                // here is what once sent traffic through one plan while the
                // screen named another.
                onSelectPlan = { plan ->
                    // The radio on the plan row: make this subscription the
                    // one the traffic runs through.
                    val guid = plan.groupToken?.let { MmkvManager.vpnkaGuidForToken(it) }
                    if (guid != null) {
                        mainViewModel.subscriptionIdChanged(guid)
                        // A plan whose group was never fetched has no servers
                        // in storage — an empty list and «сервер не выбран» at
                        // the flower. Fetch instead of showing an empty screen.
                        if (MmkvManager.decodeServerList(guid).isEmpty()) {
                            toast("Загружаю серверы подписки…")
                            importConfigViaSub()
                        } else {
                            toast("Активная подписка: ${plan.tariff ?: "выбрана"}")
                        }
                    }
                },
                onOpenPlan = { openedPlan = it },
                onBuy = {
                    // Our in-app shop, not the Telegram bot. Back from the shop
                    // returns to this list (its block sits above it).
                    showShop = true
                },
                onBack = { showPlansList = false },
            )
            return
        }

        if (showPlanPicker && !showServers) {
            VpnkaPlansScreen(
                subscriptions = subs.map { (guid, name) -> VpnkaSubOption(guid, name) },
                selectedGuid = selectedSub,
                onSelect = { guid ->
                    // The viewmodel's own switch, not just the storage key.
                    // Writing the key alone left uiState.selectedGroupId on
                    // the previous plan, so the server list stayed with the
                    // old subscription while the app believed it was on the
                    // new one — pick a server there and you connect through
                    // the wrong profile.
                    mainViewModel.subscriptionIdChanged(guid)
                    showPlanPicker = false
                    // A different plan means a different set of servers, so
                    // the list has to be refetched rather than reused.
                    importConfigViaSub()
                },
                onBack = { showPlanPicker = false },
            )
            return
        }

        if (showServerPicker && !showServers) {
            val pickerOptions = servers.map {
                VpnkaServerOption(
                    guid = it.guid,
                    name = it.profile.remarks.ifBlank { "Сервер" },
                    delay = it.testDelayString,
                )
            }
            VpnkaServersScreen(
                servers = pickerOptions,
                selectedGuid = uiState.selectedGuid,
                isLoading = uiState.isLoading,
                isTesting = uiState.isTesting,
                onSelectServer = {
                    setSelectServer(it, byUser = true)
                    showServerPicker = false
                },
                onRefresh = ::importConfigViaSub,
                onSpeedTest = mainViewModel::testAllRealPing,
                onBack = { showServerPicker = false },
            )
            return
        }

        if (showSmartDesk && !showServers) {
            // Zero-knowledge gate: the cloud opens only once the vault is
            // unlocked (create a passphrase first time, enter it on a fresh
            // device). Once unlocked the key is cached, so this is skipped.
            val vaultReady = remember(vaultTick) { com.v2ray.ang.handler.Vault.isUnlocked() }
            if (!vaultReady) {
                VpnkaVaultGate(
                    onUnlocked = { vaultTick++ },
                    onBack = { showSmartDesk = false },
                )
            } else {
                VpnkaSmartDeskScreen(
                    // «На связи» must reflect the live tunnel: SmartDesk apps all
                    // egress through the VPN, so with the VPN off they're offline
                    // regardless of the cached feature-reachability ping.
                    online = smartDeskOnline && uiState.isRunning,
                    onBack = { showSmartDesk = false },
                    onToggleVpn = { handleFabAction() },
                    // Страна и задержка в шапке рабочего стола — как в макете.
                    serverName = servers.firstOrNull { it.guid == uiState.selectedGuid }
                        ?.profile?.remarks?.ifBlank { "" } ?: "",
                    serverDelay = servers.firstOrNull { it.guid == uiState.selectedGuid }
                        ?.testDelayString?.takeIf { it.isNotBlank() } ?: "",
                    setBackHandler = { smartDeskBack = it },
                )
            }
            return
        }

        if (!showServers) {
            val options = servers.map {
                VpnkaServerOption(
                    guid = it.guid,
                    name = it.profile.remarks.ifBlank { "Сервер" },
                    delay = it.testDelayString,
                )
            }

            // Land on «Авто» rather than whatever the import happened to
            // select last: it's the balancer, and it's the right answer for
            // almost everyone. Only when nothing is selected yet — never
            // override a choice the user made.
            LaunchedEffect(options) {
                // A selection that isn't in the current list is stale, not a
                // choice — it points at a server from a subscription the user
                // has switched away from, or from a group that no longer
                // exists. Only a guid still on screen counts as something the
                // user picked, and only that is left alone.
                val chosen = uiState.selectedGuid
                val stillListed = options.any { it.guid == chosen }
                val auto = options.firstOrNull { it.name.contains("Авто") }
                when {
                    !stillListed && options.isNotEmpty() ->
                        setSelectServer((auto ?: options.first()).guid)

                    // Still listed, but never actually chosen by anyone: an
                    // automatic pick from a day when «Авто» was missing stays
                    // valid forever, so nothing revisits it and the app keeps
                    // opening on whichever city it grabbed back then. Move to
                    // «Авто» now that it exists; a real choice is left alone.
                    auto != null && chosen != auto.guid &&
                        !MmkvManager.wasServerPickedByUser() ->
                        setSelectServer(auto.guid)
                }
            }

            val activeToken = MmkvManager.vpnkaTokenForGuid(selectedSub)
            val activePlan = subInfo?.subscriptions.orEmpty()
                .firstOrNull { it.groupToken != null && it.groupToken == activeToken }

            Box(modifier = Modifier.fillMaxSize()) {
            VpnkaConnectScreen(
                isRunning = uiState.isRunning,
                isLoading = uiState.isLoading,
                // Real subscription, not the handoff's «Премиум · 214 дней»:
                // the plan the user actually holds and the days actually
                // left, or a plain word when there is no purchase yet.
                trialHoursLeft = subInfo?.takeIf { !it.active }?.trialHoursLeft,
                subscriptionName = subs.firstOrNull { it.first == selectedSub }?.second
                    ?: subs.firstOrNull()?.second,
                canSwitchSubscription = subs.size > 1,
                // Paid = holds at least one non-trial, non-frozen plan. Drives
                // the home-screen card order (server first when paid).
                paidSubscription = subInfo?.subscriptions.orEmpty()
                    .any { !it.frozen && !it.isTrial },
                // Бесплатный месяц продлевается сколько угодно раз, но пока
                // текущий идёт, забрать следующий нельзя — сервер отдаёт его
                // только в последние сутки. Поэтому карточку прячем на всё
                // время действия и возвращаем за сутки до конца: показать
                // раньше значит предложить кнопку, которая ответит отказом.
                freeMonthEnabled = subInfo?.freeMonthEnabled == true &&
                    run {
                        val trialDays = subInfo?.subscriptions.orEmpty()
                            .filter { !it.frozen && it.isTrial }
                            .mapNotNull { it.daysLeft }
                        // По самому дальнему пробному: вопрос «можно ли взять
                        // следующий месяц» — про тот, что кончится последним.
                        trialDays.isEmpty() || (trialDays.maxOrNull() ?: 0) <= 1
                    },
                freeMonthWaiting = subInfo?.subscriptions.orEmpty()
                    .filter { !it.frozen && it.isTrial }
                    .mapNotNull { it.daysLeft }
                    .maxOrNull()?.let { it <= 1 } == true,
                claimingFreeMonth = claimingFreeMonth,
                onClaimFreeMonth = {
                    // No Telegram behind the account → this card is the
                    // invitation to link, not the claim. The month belongs to
                    // an identified account: without one a reinstall would
                    // repeat it forever, so a bare install keeps the 24-hour
                    // first-run trial. The server refuses the claim in that
                    // state anyway — this is what stops the person meeting
                    // that refusal instead of an explanation.
                    if (!vpnkaTelegramLinked) {
                        openTelegramLinkGuarded()
                    } else {
                        claimingFreeMonth = true
                        lifecycleScope.launch {
                            when (val r = VpnkaAccount.claimFreeMonth()) {
                                is VpnkaAccount.FreeMonthResult.Issued -> {
                                    toast("Готово! Бесплатный месяц активирован")
                                    selectNewestOnSync = true
                                    subReload++
                                }
                                is VpnkaAccount.FreeMonthResult.Already -> {
                                    val d = r.days
                                    toast(
                                        if (d != null)
                                            "Месяц уже активен. Новый можно получить через $d дн."
                                        else "Месяц уже активен"
                                    )
                                }
                                is VpnkaAccount.FreeMonthResult.TelegramRequired -> {
                                    // Only reachable if the link was lost
                                    // between rendering the card and the tap.
                                    toast("Сначала подключите Telegram")
                                    openTelegramLinkGuarded()
                                }
                                is VpnkaAccount.FreeMonthResult.Failed ->
                                    toast("Не удалось получить месяц, попробуйте позже")
                            }
                            claimingFreeMonth = false
                        }
                    }
                },
                onChangeSubscription = { showPlansList = true },
                serverName = options.firstOrNull { it.guid == uiState.selectedGuid }
                    ?.name ?: "Выбрать сервер",
                serverDelay = options.firstOrNull { it.guid == uiState.selectedGuid }
                    ?.delay?.takeIf { it.isNotBlank() } ?: "нажмите «Сменить»",
                sessionSeconds = uiState.sessionSeconds,
                downBytes = uiState.downBytes,
                upBytes = uiState.upBytes,
                onToggle = ::handleFabAction,
                onOpenProfile = { showSubscription = true },
                telegramLinked = subInfo?.telegramLinked == true,
                onChangeServer = { showServerPicker = true },
                // The launch check only lights the dot; the screen behind the
                // button does the real check, download and install, and it
                // already handles the install permission and FileProvider.
                onPerAppProxy = { navigateTo("per_app_proxy") },
                // Hidden by the privacy toggle → the button disappears; the
                // 5-tap corner gesture below is the only way back in.
                smartDeskEnabled = subInfo?.smartDeskEnabled == true && !smartDeskHidden,
                // Green dot only while the tunnel is actually up.
                smartDeskOnline = smartDeskOnline && uiState.isRunning,
                onSmartDesk = { showSmartDesk = true },
                onYouTube = {
                    // Открываем стол сразу на «Видео»: человек просил
                    // качалку, а не рабочий стол.
                    com.v2ray.ang.ui.SmartDeskChrome.pendingAppId = "youtube"
                    showSmartDesk = true
                },
                onOpenDeskApp = { id ->
                    // Значок на главном ведёт прямо в приложение, а не на
                    // стол: стол теперь и есть главный экран.
                    com.v2ray.ang.ui.SmartDeskChrome.pendingAppId = id
                    showSmartDesk = true
                },
                // Предупреждаем о том дне, когда доступ кончится СОВСЕМ, то
                // есть по самому дальнему плану.
                //
                // Раньше брался ближайший — и человек, только что получивший
                // новый бесплатный месяц, читал «подписка кончается завтра»
                // про прежний, уже заменённый. Полоса подписана «подписка
                // кончается», а кончается она тогда, когда истечёт последняя;
                // пока действует хоть одна, ВПН работает.
                expiryDaysLeft = subInfo?.subscriptions.orEmpty()
                    .filter { !it.frozen }
                    .mapNotNull { it.daysLeft }
                    .maxOrNull(),
                // Отдельная строка: план, который кончается раньше других,
                // когда за ним ещё есть живой. Это не потеря связи, а потеря
                // того, что давал именно он, — и говорить об этом надо своими
                // словами, а не общей полосой «подписка кончается».
                endingSoonPlan = subInfo?.subscriptions.orEmpty()
                    .filter { !it.frozen && it.daysLeft != null }
                    .let { live ->
                        if (live.size < 2) return@let null
                        val soonest = live.minByOrNull { it.daysLeft ?: 0 } ?: return@let null
                        val last = live.maxOfOrNull { it.daysLeft ?: 0 } ?: return@let null
                        val days = soonest.daysLeft ?: return@let null
                        // Молчим, пока запас есть, когда кончаются все разом
                        // и когда уже говорит тревожная полоса выше.
                        //
                        // Без последнего условия выходила пара строк, спорящих
                        // друг с другом: красное «кончается меньше чем через
                        // сутки» и тут же спокойное «связь не прервётся» — а
                        // назавтра переставало работать всё.
                        if (days > 7 || days >= last || last <= 3) return@let null
                        (soonest.tariff?.takeIf { it.isNotBlank() } ?: "Подписка") to days
                    },
                onRenew = { goBuyOrLink() },
                // The plan the traffic is actually on, not merely the first
                // one: the row names that subscription, so the numbers under
                // it have to describe the same one.
                activeDaysLeft = activePlan?.daysLeft,
                activeDevicesUsed = activePlan?.devicesUsed,
                activeDevicesLimit = activePlan?.devicesLimit,
                updateVersion = updateVersion,
                onCheckUpdate = {
                    startActivity(
                        Intent(this@MainActivity, CheckUpdateActivity::class.java)
                    )
                },
                onLeaveReview = {
                    reviewPrompt = null
                    showReview = true
                },
            )
                // Hidden SmartDesk: 5 quick taps in the bottom-right corner
                // reveal it. Active only while the entry is hidden.
                if (subInfo?.smartDeskEnabled == true && smartDeskHidden) {
                    SmartDeskCornerReveal(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onReveal = { showSmartDesk = true },
                    )
                }
            }
            return
        }


        MainScreen(
            mainViewModel = mainViewModel,
            onFabClick = ::handleFabAction,
            onTestClick = ::handleLayoutTestClick,
            onNavigate = ::navigateTo,
            onImportManually = ::importManually,
            onImportQRcode = ::importQRcode,
            onImportClipboard = ::importClipboard,
            onImportLocal = ::importConfigLocal,
            onSubUpdate = ::importConfigViaSub,
            onExportAll = ::exportAll,
            onRealPingAll = mainViewModel::testAllRealPing,
            onRestartService = ::restartV2Ray,
            onDelAllConfig = ::delAllConfig,
            onDelDuplicateConfig = ::delDuplicateConfig,
            onDelInvalidConfig = ::delInvalidConfig,
            onSortByTestResults = ::sortByTestResults,
            onEditServer = ::editServer,
            onRemoveServer = ::removeServer,
            onSelectServer = ::setSelectServer,
            onShareQRCode = ::getShareQRCodeBitmap,
            onShareClipboard = ::shareToClipboard,
            onShareFullContent = ::shareFullContentAsync,
            onSubscriptionIdChanged = mainViewModel::subscriptionIdChanged,
            onLocateSelectedServer = mainViewModel::triggerLocateSelectedServer,
            shareMethodEntries = resources.getStringArray(R.array.share_method).toList(),
            shareMethodMoreEntries = resources.getStringArray(R.array.share_method_more).toList()
        )
    }

    fun getShareQRCodeBitmap(guid: String): Bitmap? = AngConfigManager.share2QRCode(guid)
    fun shareToClipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(this, guid) == 0

    fun shareFullContentAsync(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            withContext(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            }
        }
    }

    private fun navigateTo(destination: String) {
        val intent = when (destination) {
            "sub_setting" -> Intent(this, SubSettingActivity::class.java)
            "per_app_proxy" -> Intent(this, PerAppProxyActivity::class.java)
            "routing_setting" -> Intent(this, RoutingSettingActivity::class.java)
            "user_asset" -> Intent(this, UserAssetActivity::class.java)
            "settings" -> Intent(this, SettingsActivity::class.java)
            "logcat" -> Intent(this, LogcatActivity::class.java)
            "check_update" -> Intent(this, CheckUpdateActivity::class.java)
            "backup_restore" -> Intent(this, BackupActivity::class.java)
            "about" -> Intent(this, AboutActivity::class.java)
            "promotion" -> {
                Utils.openUri(
                    this,
                    "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}"
                )
                return
            }
            // The trial the app ships with lasts a day; the real month comes
            // from the bot, where there's a Telegram account to attach it to
            // (and our existing abuse checks). `?start=app` tells the bot the
            // user arrived from here, so it can offer the return link that
            // drops the new subscription straight back into this app.
            "vpnka_support" -> {
                // Support lives in the bot: tickets, agent shifts and
                // routing already work there. A second chat here would mean
                // duplicating message storage and delivery, and operators
                // watching two places.
                Utils.openUri(this, "https://t.me/vpnka_io_bot?start=support")
                return
            }
            "vpnka_month" -> {
                Utils.openUri(this, "https://t.me/vpnka_io_bot?start=app")
                return
            }
            // Straight to the card that mints a sign-in code, so the user
            // doesn't have to find «Профиль» in the bot's menu while holding
            // a half-filled code field open in the app.
            // Buying and topping up happen in the bot, not here.
            //
            // Routed through `goBuyOrLink` rather than opened directly: money
            // spent in the bot lands on the Telegram account, so someone who
            // has not linked yet would pay and then find the app still on an
            // unpaid account. Linking first is the missing step, not a
            // refusal.
            //
            // Not a UI preference: Google Play requires its own billing for
            // anything sold inside an app, and Play stopped paying out to
            // Russian accounts entirely at the end of 2024 — so a shop in
            // the app is both a policy violation and a dead end for the
            // money. The bot already holds tariffs, balance, promo codes,
            // the referral discount and RuKassa; one place to buy is one
            // place to keep correct.
            //
            // The payload lands the user on the matching card rather than
            // the bot's home screen: they tapped «купить», and making them
            // find it again is where people give up.
            "vpnka_buy" -> {
                openBotDeepLink("buy")
                return
            }
            "vpnka_topup" -> {
                openBotDeepLink("topup")
                return
            }
            "vpnka_app_code" -> {
                openBotDeepLink("appcode")
                return
            }
            else -> return
        }
        settingsActivityLauncher.launch(intent)
    }

    private fun handleFabAction() {
        if (mainViewModel.uiState.value.isRunning) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
        } else {
            startV2Ray()
        }
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.uiState.value.isRunning) mainViewModel.testCurrentServerRealPing()
    }

    /**
     * Профиль выбирается САМ.
     *
     * «Выберите профиль» — ответ из v2rayNG, где список серверов человек
     * набирает руками. У нас его набирает подписка, и выбирать там не из
     * чего: на свежей установке в списке либо «Авто» и города, либо вообще
     * пусто, потому что подписку ещё не забрали. Особенно обидно это
     * выглядело на кнопке «Подключить Телеграм»: приложение само
     * предлагало включить ВПН, человек соглашался — и упирался в надпись
     * про выбор, которого он сделать не может.
     *
     * @param afterImport true — мы уже сходили за подпиской; второй раз
     *        не идём, иначе на пустом ответе получится вечный круг.
     */
    private fun startV2Ray(afterImport: Boolean = false) {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            val guid = autoPickServerGuid()
            when {
                guid != null -> setSelectServer(guid)
                !afterImport -> {
                    // Списка нет вовсе — тянем подписку и стартуем, когда
                    // она приедет.
                    pendingStartAfterImport = true
                    importConfigViaSub(silent = true)
                    return
                }
                else -> { toast(R.string.title_file_chooser); return }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        CoreServiceManager.startVService(this)
    }

    private fun restartV2Ray() {
        if (mainViewModel.uiState.value.isRunning) CoreServiceManager.stopVService(this)
        lifecycleScope.launch { delay(500); startV2Ray() }
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            EConfigType.VMESS.value -> Intent(this, ServerVmessActivity::class.java)
            EConfigType.VLESS.value -> Intent(this, ServerVlessActivity::class.java)
            EConfigType.SHADOWSOCKS.value -> Intent(this, ServerShadowsocksActivity::class.java)
            EConfigType.SOCKS.value -> Intent(this, ServerSocksActivity::class.java)
            EConfigType.HTTP.value -> Intent(this, ServerHttpActivity::class.java)
            EConfigType.TROJAN.value -> Intent(this, ServerTrojanActivity::class.java)
            EConfigType.WIREGUARD.value -> Intent(this, ServerWireguardActivity::class.java)
            EConfigType.HYSTERIA2.value -> Intent(this, ServerHysteria2Activity::class.java)
            else -> Intent(this, ServerHttpActivity::class.java).apply {
                putExtra("createConfigType", createConfigType)
            }
        }.apply {
            putExtra("subscriptionId", mainViewModel.subscriptionId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) importBatchConfig(scanResult)
        }
    }

    private fun importClipboard() {
        try {
            importBatchConfig(Utils.getClipboard(this))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
    }

    private fun importBatchConfig(server: String?) {
        mainViewModel.setLoading(true)
        lifecycleScope.launch {
            try {
                val (count, countSub) = withContext(Dispatchers.IO) {
                    AngConfigManager.importBatchConfig(
                        server,
                        mainViewModel.subscriptionId,
                        true
                    )
                }
                when {
                    count > 0 -> {
                        toast(getString(R.string.title_import_config_count, count))
                        mainViewModel.setupGroupTab(forceRefresh = true)
                    }
                    countSub > 0 -> mainViewModel.setupGroupTab(forceRefresh = true)
                    else -> toastError(R.string.toast_failure)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                toastError(R.string.toast_failure)
            } finally {
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri)
                    .use { input -> importBatchConfig(input?.bufferedReader()?.readText()) }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
            }
        }
    }

    private fun importConfigViaSub(silent: Boolean = false) {
        mainViewModel.setLoading(true)
        // Флаг снимаем СРАЗУ: иначе неудачная попытка оставила бы его
        // висеть, и следующее обновление подписки — хоть по кнопке, хоть
        // по расписанию — молча включило бы ВПН само.
        val startWhenReady = pendingStartAfterImport
        pendingStartAfterImport = false
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    mainViewModel.updateConfigViaSubAll()
                }
                // Сервер прислал причину вместо серверов («Лимит устройств
                // 3/3») — показываем её словами, а не счётчиком «0 конфигов».
                val notice = result.notice.orEmpty()
                // Тихий заход — это наш собственный поход за подпиской перед
                // подключением, а не нажатие «обновить». Итог вроде
                // «обновлено профилей 0» человек в этот момент читает как
                // ответ на «подключить Телеграм» — то есть как ошибку, хотя
                // он ничего не обновлял. Молчим обо всём, кроме причины
                // отказа: она объясняет, почему подключения не будет.
                when {
                    silent && notice.isNotBlank() -> toast(notice)
                    silent -> Unit
                    notice.isNotBlank() -> toast(notice)
                    result.successCount + result.failureCount + result.skipCount == 0 ->
                        toast(R.string.title_update_subscription_no_subscription)
                    result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                        toast(getString(R.string.title_update_config_count, result.configCount))
                    else ->
                        toast(
                            getString(
                                R.string.title_update_subscription_result,
                                result.configCount,
                                result.successCount,
                                result.failureCount,
                                result.skipCount
                            )
                        )
                }
                if (result.configCount > 0) {
                    mainViewModel.setupGroupTab(forceRefresh = true)
                    mainViewModel.refreshSelectedGuid()
                }
                // Подписку тянули РАДИ подключения — доводим начатое, а не
                // возвращаем человека к кнопке, которую он уже нажал.
                if (startWhenReady) {
                    if (result.configCount > 0) startV2Ray(afterImport = true)
                    // Молчание здесь читается как «кнопка не работает»:
                    // человек нажал подключение, мы сходили за подпиской и
                    // ничего не нашли — об этом надо сказать словами.
                    else if (notice.isBlank()) toast("Серверы для подключения ещё не выданы")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                toastError(R.string.toast_failure)
            } finally {
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun exportAll() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch {
            try {
                val ret = withContext(Dispatchers.IO) {
                    mainViewModel.exportAllServer()
                }
                if (ret > 0) toast(getString(R.string.title_export_config_count, ret))
                else toastError(R.string.toast_failure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Export failed", e)
                toastError(R.string.toast_failure)
            } finally {
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun delAllConfig() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch {
            try {
                val ret = withContext(Dispatchers.IO) {
                    mainViewModel.removeAllServer()
                }
                mainViewModel.setupGroupTab(forceRefresh = true)
                toast(getString(R.string.title_del_config_count, ret))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                toastError(R.string.toast_failure)
            } finally {
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun delDuplicateConfig() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch {
            try {
                val ret = withContext(Dispatchers.IO) {
                    mainViewModel.removeDuplicateServer()
                }
                mainViewModel.setupGroupTab(forceRefresh = true)
                toast(getString(R.string.title_del_duplicate_config_count, ret))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                toastError(R.string.toast_failure)
            } finally {
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun delInvalidConfig() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch {
            try {
                val ret = withContext(Dispatchers.IO) {
                    mainViewModel.removeInvalidServer()
                }
                mainViewModel.setupGroupTab(forceRefresh = true)
                toast(getString(R.string.title_del_config_count, ret))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                toastError(R.string.toast_failure)
            } finally {
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun sortByTestResults() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    mainViewModel.sortByTestResults()
                }
                mainViewModel.setupGroupTab(forceRefresh = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                toastError(R.string.toast_failure)
            } finally {
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", mainViewModel.subscriptionId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun removeServer(guid: String) {
        if (guid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed); return
        }
        mainViewModel.removeServerAndRefresh(guid)
    }

    /**
     * Open the bot on the link that identifies this install.
     *
     * One method rather than a copy at each call site: linking an account,
     * claiming the free month and renewing are the same trip through the
     * same token, and three copies would drift.
     */
    /**
     * Open the bot, making sure it can actually be reached.
     *
     * Telegram is blocked on the networks this app exists for, so tapping
     * «Подключить Telegram» with the tunnel down opened a browser that
     * timed out — and the failure looked like ours. Ask first, then open.
     */
    /**
     * «Купить» from anywhere that a signed-out user can also reach.
     *
     * A purchase made in the bot is credited to the Telegram account. Send
     * an unlinked user straight there and they pay real money onto an
     * account this app is not signed into — the subscription exists, and
     * they cannot see it. So the link comes first; the shop is one tap
     * further, and that tap is the one that makes the payment land.
     */
    private fun goBuyOrLink() {
        if (vpnkaTelegramLinked) navigateTo("vpnka_buy") else openTelegramLinkGuarded()
    }

    private fun openTelegramLinkGuarded() {
        if (mainViewModel.uiState.value.isRunning) {
            openTelegramLink()
        } else {
            askVpnForTelegram = true
        }
    }

    /**
     * Открыть бота по ссылке привязки — НЕ ЧАЩЕ ОДНОГО РАЗА.
     *
     * Каждый заход сюда просит у сервера новый одноразовый токен, а бот на
     * каждый `/start link_…` отвечает сообщением: у кого аккаунт в
     * Телеграме уже есть — новым кодом для входа. За две минуты на проде
     * набралось 17 запросов токена и девять таких сообщений: человек видел
     * пачку разных кодов и дважды открывшегося бота, и какой код настоящий
     * — понять уже нельзя.
     *
     * Откуда повторы: пока ссылка едет (а едет она через только что
     * поднятый туннель, то есть небыстро), экран ничем не занят, и второе
     * касание карточки выглядит для человека единственным разумным
     * действием. Плюс OkHttp сам повторяет POST, если соединение оборвалось
     * до ответа, — а токен на сервере при этом уже выписан.
     *
     * Поэтому: пока запрос в полёте — второе касание игнорируем, и ещё
     * пять секунд после открытия Телеграма тоже: за это время Телеграм
     * успевает выйти на передний план, и «ничего не произошло» человеку
     * уже не кажется.
     */
    private var telegramLinkBusy = false
    private var telegramLinkOpenedAt = 0L

    private fun openTelegramLink() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (telegramLinkBusy || now - telegramLinkOpenedAt < 5_000) return
        telegramLinkBusy = true
        lifecycleScope.launch {
            try {
                val url = VpnkaAccount.telegramLinkUrl()
                if (url == null) {
                    // Ссылку не выдали — молча ничего не делать нельзя:
                    // человек нажал и ждёт.
                    toast("Не удалось открыть Telegram, попробуйте ещё раз")
                    return@launch
                }
                telegramLinkOpenedAt = android.os.SystemClock.elapsedRealtime()
                openTelegramUrl(url)
            } finally {
                telegramLinkBusy = false
            }
        }
    }

    /** Open a t.me/... https link IN the Telegram app (tg://resolve), falling
     *  back to the browser only when Telegram isn't installed — otherwise the
     *  login code lands in a browser tab instead of the bot chat. */
    private fun openTelegramUrl(httpsUrl: String) {
        val uri = runCatching { android.net.Uri.parse(httpsUrl) }.getOrNull()
        val domain = uri?.lastPathSegment
        if (domain != null) {
            val start = uri.getQueryParameter("start") ?: uri.getQueryParameter("startapp")
            val tg = "tg://resolve?domain=$domain" + if (!start.isNullOrEmpty()) "&start=$start" else ""
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(tg)))
                return
            } catch (e: android.content.ActivityNotFoundException) {
                // Telegram not installed — fall through to the browser link.
            }
        }
        Utils.openUri(this, httpsUrl)
    }

    /**
     * Open the bot at a start-param IN Telegram, not a browser.
     *
     * A t.me/... link (via Utils.openUri) is just https, so Android hands it
     * to whatever claims https — and on a phone where Telegram hasn't taken
     * over t.me that's the browser, which is not where «Купить подписку»
     * should land. tg://resolve names the Telegram app directly; only when it
     * isn't installed do we fall back to the https link so the button still
     * works.
     */
    private fun openBotDeepLink(start: String) {
        val tg = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("tg://resolve?domain=vpnka_io_bot&start=$start"),
        )
        try {
            startActivity(tg)
        } catch (e: android.content.ActivityNotFoundException) {
            Utils.openUri(this, "https://t.me/vpnka_io_bot?start=$start")
        }
    }

    /**
     * Какой профиль включить, если человек ещё ничего не выбирал.
     *
     * «Авто» — балансировщик, правильный ответ почти для всех; если его в
     * списке нет, годится первый живой. Смотрим и выбранную подписку, и
     * общий список: на первом запуске группа ещё не выставлена.
     */
    private fun autoPickServerGuid(): String? {
        val inGroup = MmkvManager.decodeServerList(
            mainViewModel.uiState.value.selectedGroupId
        )
        val guids = if (inGroup.isNotEmpty()) inGroup else MmkvManager.decodeAllServerList()
        if (guids.isEmpty()) return null
        val auto = guids.firstOrNull {
            MmkvManager.decodeServerConfig(it)?.remarks?.contains("Авто") == true
        }
        return auto ?: guids.firstOrNull()
    }

    private fun setSelectServer(guid: String, byUser: Boolean = false) {
        MmkvManager.setServerPickedByUser(byUser)
        // A guid the picker offered but storage cannot decode is stale, and
        // worth refreshing over. But the selection still has to happen:
        // this same method is what auto-picks a server at startup, and
        // refusing there left nothing selected at all — the core then said
        // «сервер не выбран» and the flower did nothing, which is worse
        // than the failed connection this was meant to prevent.
        //
        // So: select regardless, and kick off a refresh. When it lands, the
        // list changes, and the effect that watches it picks a live server
        // if this one is gone.
        if (MmkvManager.decodeServerConfig(guid) == null) {
            android.util.Log.e(
                "VPNKA_BACK",
                "stale server guid=$guid group=${mainViewModel.uiState.value.selectedGroupId}",
            )
            toast("Список серверов устарел, обновляю…")
            importConfigViaSub()
        }
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            if (mainViewModel.uiState.value.isRunning) restartV2Ray()
        }
    }

    /**
     * Back, and the reason six fixes before this one missed.
     *
     * Upstream swallowed KEYCODE_BACK here and went straight to
     * moveTaskToBack — before onBackPressed, before either dispatcher. On a
     * device where the gesture arrives as a key event (ColorOS routes it
     * that way, since it gates the predictive-back path behind a system
     * setting the manifest cannot reach) every handler downstream was dead
     * code. The on-screen ‹ button kept working because a click is not a
     * key event, which is exactly what made this look like a state bug.
     *
     * Our screens get first refusal; leaving the app stays the fallback.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (closeTopVpnkaScreen()) return true
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun MainDialogs(
    showDelAllConfirm: Boolean,
    onDismissDelAll: () -> Unit,
    onConfirmDelAll: () -> Unit,
    showDelDuplicateConfirm: Boolean,
    onDismissDelDuplicate: () -> Unit,
    onConfirmDelDuplicate: () -> Unit,
    showDelInvalidConfirm: Boolean,
    onDismissDelInvalid: () -> Unit,
    onConfirmDelInvalid: () -> Unit,
    showRemoveConfirm: String?,
    onDismissRemove: () -> Unit,
    onConfirmRemove: (String) -> Unit,
) {
    if (showDelAllConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = onConfirmDelAll,
            onDismiss = onDismissDelAll
        )
    }
    if (showDelDuplicateConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = onConfirmDelDuplicate,
            onDismiss = onDismissDelDuplicate
        )
    }
    if (showDelInvalidConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_invalid_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = onConfirmDelInvalid,
            onDismiss = onDismissDelInvalid
        )
    }
    if (showRemoveConfirm != null) {
        val guid = showRemoveConfirm
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = { onConfirmRemove(guid) },
            onDismiss = onDismissRemove
        )
    }
}

@Composable
private fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onTestClick: () -> Unit,
    onFabClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppDivider()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(64.dp)
                    .clickable(onClick = onTestClick),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = displayText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        FloatingActionButton(
            onClick = onFabClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp)
                .offset(y = (-28).dp)
                .navigationBarsPadding(),
            containerColor = if (isRunning) colorFabActive
            else if (isDarkTheme) colorFabInactiveDark
            else colorFabInactiveLight
        ) {
            Icon(
                painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                else painterResource(R.drawable.ic_play_24dp),
                contentDescription = if (isRunning) "Stop" else "Start",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return

    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)

    if (target == current) return

    val distance = abs(target - current)

    when {
        distance == 1 && animateAdjacentPage -> animateScrollToPage(target)
        animateAdjacentPage -> {
            val adjacent = if (target > current) target - 1 else target + 1
            scrollToPage(adjacent)
            yield()
            animateScrollToPage(target)
        }
        else -> scrollToPage(target)
    }
}

@Composable
private fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex.coerceIn(0, groups.lastIndex),
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 16.dp,
        minTabWidth = 56.dp,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier
                    .tabIndicatorOffset(
                        selectedTabIndex = selectedTabIndex.coerceIn(0, groups.lastIndex),
                        matchContentSize = true
                    )
                    .clip(RoundedCornerShape(3.dp)),
                width = Dp.Unspecified,
                color = colorFabActive
            )
        },
        divider = {}
    ) {
        groups.forEachIndexed { index, group ->
            GroupTabItem(
                group = group,
                selected = index == selectedTabIndex,
                serverFlowProvider = {
                    mainViewModel.serversForGroup(group.id)
                },
                onClick = { onTabClick(index) }
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlowProvider: () -> StateFlow<List<ServersCache>>,
    onClick: () -> Unit
) {
    val serverFlow = remember(group.id) {
        serverFlowProvider()
    }
    val servers by serverFlow.collectAsStateWithLifecycle()

    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            val text = if (group.id.isEmpty()) {
                group.remarks
            } else {
                "${group.remarks} (${servers.size})"
            }
            Text(
                text = text,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    doubleColumnDisplay: Boolean,
    confirmRemove: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val serverFlow = remember(groupId) {
        mainViewModel.serversForGroup(groupId)
    }
    val servers by serverFlow.collectAsStateWithLifecycle()

    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()

    ServerListPage(
        servers = servers,
        selectedGuid = selectedGuid,
        canReorder = canReorder,
        doubleColumnDisplay = doubleColumnDisplay,
        subscriptionId = groupId,
        confirmRemove = confirmRemove,
        groupId = groupId,
        lazyListStates = lazyListStates,
        lazyGridStates = lazyGridStates,
        onSelectServer = onSelectServer,
        onEditServer = onEditServer,
        onShareServer = onShareServer,
        onMoreServer = onMoreServer,
        onRemoveServer = onRemoveServer,
        onSwapServer = mainViewModel::swapServer,
        contentPadding = contentPadding
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onFabClick: () -> Unit,
    onTestClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onImportManually: (Int) -> Unit,
    onImportQRcode: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportLocal: () -> Unit,
    onSubUpdate: () -> Unit,
    onExportAll: () -> Unit,
    onRealPingAll: () -> Unit,
    onRestartService: () -> Unit,
    onDelAllConfig: () -> Unit,
    onDelDuplicateConfig: () -> Unit,
    onDelInvalidConfig: () -> Unit,
    onSortByTestResults: () -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onShareQRCode: (String) -> Bitmap?,
    onShareClipboard: (String) -> Boolean,
    onShareFullContent: (String) -> Unit,
    onSubscriptionIdChanged: (String) -> Unit,
    onLocateSelectedServer: () -> Unit,
    shareMethodEntries: List<String>,
    shareMethodMoreEntries: List<String>
) {
    val context = LocalContext.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading = uiState.isLoading
    val isRunning = uiState.isRunning
    val displayText = uiState.statusText
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    var showQRCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    val drawerScrollState = rememberScrollState()
    val importMenuScrollState = rememberScrollState()
    val moreMenuScrollState = rememberScrollState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight = LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp

    var locateInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    val latestDoubleColumnDisplay by rememberUpdatedState(doubleColumnDisplay)

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)
    val latestLocateInProgress by rememberUpdatedState(locateInProgress)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (!latestLocateInProgress && page in currentGroups.indices) {
                    onSubscriptionIdChanged(currentGroups[page].id)
                }
            }
    }

    LaunchedEffect(mainViewModel, pagerState) {
        mainViewModel.locateEvent.collect { target ->
            if (target.groupIndex !in 0 until pagerState.pageCount) return@collect

            locateInProgress = true
            try {
                if (pagerState.settledPage != target.groupIndex) {
                    pagerState.navigateToPageOptimized(
                        targetPage = target.groupIndex,
                        animateAdjacentPage = false
                    )
                }
                onSubscriptionIdChanged(target.groupId)

                repeat(10) {
                    val ready = if (latestDoubleColumnDisplay) {
                        lazyGridStates[target.groupId] != null
                    } else {
                        lazyListStates[target.groupId] != null
                    }
                    if (ready) return@repeat
                    delay(16L)
                }

                if (latestDoubleColumnDisplay) {
                    lazyGridStates[target.groupId]?.let { gridState ->
                        gridState.scrollToItem(
                            index = target.itemPosition,
                            scrollOffset = -gridState.layoutInfo.viewportSize.height / 3
                        )
                    }
                } else {
                    lazyListStates[target.groupId]?.let { listState ->
                        listState.scrollToItem(
                            index = target.itemPosition,
                            scrollOffset = -listState.layoutInfo.viewportSize.height / 3
                        )
                    }
                }
            } finally {
                delay(32L)
                locateInProgress = false
            }
        }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onDelAllConfig() },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onDelDuplicateConfig() },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onDelInvalidConfig() },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onRemoveServer(guid) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        val isCustom = profile.configType.isComplexType()
        val (shareOptions, skip) = if (more) {
            val options = if (isCustom) shareMethodMoreEntries.takeLast(3) else shareMethodMoreEntries
            options to if (isCustom) 2 else 0
        } else {
            val options = if (isCustom) shareMethodEntries.takeLast(1) else shareMethodEntries
            options to if (isCustom) 2 else 0
        }
        SelectListDialog(
            options = shareOptions,
            onSelected = { index, _ ->
                shareTarget = null
                when (index + skip) {
                    0 -> showQRCodeBitmap = onShareQRCode(guid)
                    1 -> onShareClipboard(guid)
                    2 -> onShareFullContent(guid)
                    3 -> onEditServer(guid, profile)
                    4 -> onRemoveServer(guid)
                }
            },
            onDismiss = { shareTarget = null }
        )
    }
    if (showQRCodeBitmap != null) {
        QRCodeDialog(bitmap = showQRCodeBitmap, onDismiss = { showQRCodeBitmap = null })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .navigationBarsPadding(),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(drawerScrollState)
                        .verticalScrollbar(drawerScrollState)
                        .padding(bottom = 80.dp)
                ) {
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = FontFamily(Font(R.font.montserrat_thin)),
                                    fontWeight = FontWeight.Thin
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    // First item on purpose: the shipped trial runs out after
                    // a day, and this is where the user goes to keep working.
                    // Buried three items down it would be found after the
                    // connection had already stopped.
                    DrawerMenuItem(
                        icon = painterResource(R.drawable.ic_promotion_24dp),
                        label = stringResource(R.string.vpnka_get_month),
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigate("vpnka_month")
                        }
                    )
                    AppDivider(modifier = Modifier.padding(vertical = 4.dp))
                    listOf(
                        Triple(
                            R.drawable.ic_subscriptions_24dp,
                            R.string.title_sub_setting,
                            "sub_setting"
                        ),
                        Triple(
                            R.drawable.ic_per_apps_24dp,
                            R.string.per_app_proxy_settings,
                            "per_app_proxy"
                        ),
                        Triple(
                            R.drawable.ic_routing_24dp,
                            R.string.routing_settings_title,
                            "routing_setting"
                        ),
                        Triple(
                            R.drawable.ic_file_24dp,
                            R.string.title_user_asset_setting,
                            "user_asset"
                        ),
                        Triple(R.drawable.ic_settings_24dp, R.string.title_settings, "settings"),
                    ).forEach { (iconRes, labelRes, route) ->
                        DrawerMenuItem(
                            icon = painterResource(iconRes),
                            label = stringResource(labelRes),
                            onClick = { scope.launch { drawerState.close() }; onNavigate(route) }
                        )
                    }
                    AppDivider(modifier = Modifier.padding(vertical = 4.dp))
                    listOf(
                        Triple(
                            R.drawable.ic_promotion_24dp,
                            R.string.title_pref_promotion,
                            "promotion"
                        ),
                        Triple(R.drawable.ic_logcat_24dp, R.string.title_logcat, "logcat"),
                        Triple(
                            R.drawable.ic_check_update_24dp,
                            R.string.update_check_for_update,
                            "check_update"
                        ),
                        Triple(
                            R.drawable.ic_restore_24dp,
                            R.string.title_configuration_backup_restore,
                            "backup_restore"
                        ),
                        Triple(R.drawable.ic_about_24dp, R.string.title_about, "about"),
                    ).forEach { (iconRes, labelRes, route) ->
                        DrawerMenuItem(
                            icon = painterResource(iconRes),
                            label = stringResource(labelRes),
                            onClick = { scope.launch { drawerState.close() }; onNavigate(route) }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.title_server),
                    onBackClick = {},
                    isLoading = isLoading,
                    isSearchActive = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        mainViewModel.filterConfig(query)
                    },
                    onSearchClose = {
                        searchQuery = ""
                        mainViewModel.filterConfig("")
                        showSearch = false
                    },
                    searchPlaceholder = stringResource(R.string.menu_item_search),
                    navigationIcon = {
                        if (showSearch) {
                            IconButton(onClick = {
                                searchQuery = ""
                                mainViewModel.filterConfig("")
                                showSearch = false
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_arrow_back_24dp),
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    painterResource(R.drawable.ic_menu_24dp),
                                    contentDescription = "Menu"
                                )
                            }
                        }
                    },
                    actions = {
                        if (!showSearch) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_search_24dp),
                                    contentDescription = "filter"
                                )
                            }
                        }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showImportMenu = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_add_24dp),
                                    contentDescription = "Add"
                                )
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false },
                                scrollState = importMenuScrollState,
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .heightIn(max = maxMenuHeight)
                                    .verticalScrollbar(importMenuScrollState)
                            ) {
                                listOf(
                                    R.string.menu_item_import_config_qrcode to {
                                        showImportMenu = false; onImportQRcode()
                                    },
                                    R.string.menu_item_import_config_clipboard to {
                                        showImportMenu = false; onImportClipboard()
                                    },
                                    R.string.menu_item_import_config_local to {
                                        showImportMenu = false; onImportLocal()
                                    },
                                    R.string.menu_item_import_config_policy_group to {
                                        showImportMenu = false; onImportManually(EConfigType.POLICYGROUP.value)
                                    },
                                    R.string.menu_item_import_config_proxy_chain to {
                                        showImportMenu = false; onImportManually(EConfigType.PROXYCHAIN.value)
                                    },
                                    R.string.menu_item_import_config_manually_vmess to {
                                        showImportMenu = false; onImportManually(EConfigType.VMESS.value)
                                    },
                                    R.string.menu_item_import_config_manually_vless to {
                                        showImportMenu = false; onImportManually(EConfigType.VLESS.value)
                                    },
                                    R.string.menu_item_import_config_manually_ss to {
                                        showImportMenu = false; onImportManually(EConfigType.SHADOWSOCKS.value)
                                    },
                                    R.string.menu_item_import_config_manually_socks to {
                                        showImportMenu = false; onImportManually(EConfigType.SOCKS.value)
                                    },
                                    R.string.menu_item_import_config_manually_http to {
                                        showImportMenu = false; onImportManually(EConfigType.HTTP.value)
                                    },
                                    R.string.menu_item_import_config_manually_trojan to {
                                        showImportMenu = false; onImportManually(EConfigType.TROJAN.value)
                                    },
                                    R.string.menu_item_import_config_manually_wireguard to {
                                        showImportMenu = false; onImportManually(EConfigType.WIREGUARD.value)
                                    },
                                    R.string.menu_item_import_config_manually_hysteria2 to {
                                        showImportMenu = false; onImportManually(EConfigType.HYSTERIA2.value)
                                    },
                                ).forEach { (stringRes, action) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(stringRes)) },
                                        onClick = action
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_more_vert_24dp),
                                    contentDescription = null
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                scrollState = moreMenuScrollState,
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .heightIn(max = maxMenuHeight)
                                    .verticalScrollbar(moreMenuScrollState)
                            ) {
                                listOf(
                                    R.string.title_service_restart to {
                                        showMenu = false; onRestartService()
                                    },
                                    R.string.title_del_all_config to {
                                        showMenu = false; showDelAllConfirm = true
                                    },
                                    R.string.title_del_duplicate_config to {
                                        showMenu = false; showDelDuplicateConfirm = true
                                    },
                                    R.string.title_del_invalid_config to {
                                        showMenu = false; showDelInvalidConfirm = true
                                    },
                                    R.string.title_export_all to {
                                        showMenu = false; onExportAll()
                                    },
                                    R.string.title_real_ping_all_server to {
                                        showMenu = false; onRealPingAll()
                                    },
                                    R.string.title_locate_selected_config to {
                                        showMenu = false; onLocateSelectedServer()
                                    },
                                    R.string.title_sort_by_test_results to {
                                        showMenu = false; onSortByTestResults()
                                    },
                                    R.string.title_sub_update to {
                                        showMenu = false; onSubUpdate()
                                    },
                                ).forEach { (stringRes, action) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(stringRes)) },
                                        onClick = action
                                    )
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                MainBottomBar(
                    displayText = displayText,
                    isRunning = isRunning,
                    isDarkTheme = isDarkTheme,
                    onTestClick = onTestClick,
                    onFabClick = onFabClick
                )
            },
            floatingActionButton = {},
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current

            if (groups.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (groups.size > 1) {
                        GroupTabBar(
                            groups = groups,
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                            mainViewModel = mainViewModel,
                            onTabClick = { targetIndex ->
                                scope.launch {
                                    pagerState.navigateToPageOptimized(
                                        targetPage = targetIndex,
                                        animateAdjacentPage = true
                                    )
                                }
                            }
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1,
                        key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                    ) { page ->
                        val group = groups.getOrNull(page) ?: return@HorizontalPager

                        GroupPagerPage(
                            groupId = group.id,
                            mainViewModel = mainViewModel,
                            selectedGuid = selectedGuid,
                            doubleColumnDisplay = doubleColumnDisplay,
                            confirmRemove = confirmRemove,
                            searchQuery = searchQuery,
                            lazyListStates = lazyListStates,
                            lazyGridStates = lazyGridStates,
                            onSelectServer = onSelectServer,
                            onEditServer = onEditServer,
                            onShareServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, false)
                            },
                            onMoreServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, true)
                            },
                            onRemoveServer = { guid ->
                                if (confirmRemove) showRemoveConfirm = guid
                                else onRemoveServer(guid)
                            },
                            contentPadding = PaddingValues(
                                start = 0.dp,
                                top = 0.dp,
                                end = 0.dp,
                                bottom = 80.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerListPage(
    servers: List<ServersCache>,
    selectedGuid: String?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    subscriptionId: String,
    confirmRemove: Boolean,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    onSwapServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onSwapServer(from.index, to.index)
            }
        } else null

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = servers, key = { _, item -> item.guid }) { _, serverCache ->
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        serverCache = serverCache,
                        selectedGuid = selectedGuid,
                        subscriptionId = subscriptionId,
                        doubleColumnDisplay = true,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareServer = onShareServer,
                        onMoreServer = onMoreServer,
                        onRemoveServer = onRemoveServer
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = serverCache.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onSwapServer(from.index, to.index)
            }
        } else null

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = servers, key = { _, item -> item.guid }) { _, serverCache ->
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = serverCache.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerItemRow(
                                serverCache = serverCache,
                                selectedGuid = selectedGuid,
                                subscriptionId = subscriptionId,
                                onSelectServer = onSelectServer,
                                onEditServer = onEditServer,
                                onShareServer = onShareServer,
                                onMoreServer = onMoreServer,
                                onRemoveServer = onRemoveServer
                            )
                        }
                        AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                } else {
                    ServerItemRow(
                        serverCache = serverCache,
                        selectedGuid = selectedGuid,
                        subscriptionId = subscriptionId,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareServer = onShareServer,
                        onMoreServer = onMoreServer,
                        onRemoveServer = onRemoveServer
                    )
                    AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ServerItemRow(
    serverCache: ServersCache,
    selectedGuid: String?,
    subscriptionId: String,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val subRemarks = if (subscriptionId.isEmpty()) {
        MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            ?.toString() ?: ""
    } else ""

    ServerListItem(
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = getProtocolDescription(profile),
        testResult = serverCache.testDelayString,
        testDelayMillis = serverCache.testDelayMillis,
        isSelected = serverCache.guid == selectedGuid,
        subscriptionRemarks = subRemarks,
        doubleColumnDisplay = false,
        onClick = { onSelectServer(serverCache.guid) },
        onShare = { onShareServer(serverCache.guid, profile) },
        onEdit = { onEditServer(serverCache.guid, profile) },
        onRemove = { onRemoveServer(serverCache.guid) },
        onMore = { onMoreServer(serverCache.guid, profile) }
    )
}

@Composable
private fun ServerItemColumn(
    serverCache: ServersCache,
    selectedGuid: String?,
    subscriptionId: String,
    doubleColumnDisplay: Boolean,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val subRemarks = if (subscriptionId.isEmpty()) {
        MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()?.toString() ?: ""
    } else ""

    Column {
        ServerListItem(
            remarks = profile.remarks,
            statistics = profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile),
            typeDescription = getProtocolDescription(profile),
            testResult = serverCache.testDelayString,
            testDelayMillis = serverCache.testDelayMillis,
            isSelected = serverCache.guid == selectedGuid,
            subscriptionRemarks = subRemarks,
            doubleColumnDisplay = doubleColumnDisplay,
            onClick = { onSelectServer(serverCache.guid) },
            onEdit = { onEditServer(serverCache.guid, profile) },
            onShare = { onShareServer(serverCache.guid, profile) },
            onRemove = { onRemoveServer(serverCache.guid) },
            onMore = { onMoreServer(serverCache.guid, profile) }
        )
        AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
fun ServerListItem(
    remarks: String,
    statistics: String,
    typeDescription: String,
    testResult: String,
    testDelayMillis: Long,
    isSelected: Boolean,
    subscriptionRemarks: String,
    doubleColumnDisplay: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).then(dragModifier)
    ) {
        Box(Modifier.width(10.dp).fillMaxHeight()) {
            if (isSelected) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.width(4.dp).fillMaxHeight().padding(vertical = 10.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }

        Column(Modifier.weight(1f).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(remarks, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (doubleColumnDisplay) {
                    IconButton(onClick = onMore, Modifier.size(36.dp)) {
                        Icon(painterResource(R.drawable.ic_more_vert_24dp), null, Modifier.size(24.dp))
                    }
                } else {
                    IconButton(onClick = onShare, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_share_24dp), null, Modifier.size(24.dp)) }
                    IconButton(onClick = onEdit, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_edit_24dp), null, Modifier.size(24.dp)) }
                    IconButton(onClick = onRemove, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_delete_24dp), null, Modifier.size(24.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (subscriptionRemarks.isNotBlank()) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), Alignment.Center) {
                        Text(subscriptionRemarks.take(1).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(statistics, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(typeDescription, style = MaterialTheme.typography.bodySmall, color = colorConfigType, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(testResult, style = MaterialTheme.typography.bodySmall, color = if (testDelayMillis < 0L) colorPingRed else colorPing, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Invisible bottom-corner target: 5 quick taps reveal a hidden SmartDesk. */
@Composable
private fun SmartDeskCornerReveal(modifier: Modifier = Modifier, onReveal: () -> Unit) {
    var taps by remember { mutableStateOf(0) }
    var lastAt by remember { mutableStateOf(0L) }
    Box(
        modifier = modifier
            .size(72.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastAt < 900L) taps + 1 else 1
                    lastAt = now
                    if (taps >= 5) { taps = 0; onReveal() }
                }
            },
    )
}

private fun getProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { net ->
        if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) parts.add(net)
    }
    profile.security?.let { sec ->
        if (sec.isNotBlank()) {
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                parts.add("$sec insecure")
            } else {
                parts.add(sec)
            }
        }
    }
    return parts.joinToString(" / ")
}
