package com.v2ray.ang.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.VpnService
import android.os.Build
import android.provider.Settings
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
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.server.*
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.handler.UpdatePrefetcher
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
        ExpiryReminder.schedule(this)

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
        openedTicket != null -> { openedTicket = null; true }
        showTickets -> { showTickets = false; true }
        showSupport -> { showSupport = false; true }
        showTopUp -> { showTopUp = false; true }
        showRecovery -> { showRecovery = false; true }
        showServerPicker -> { showServerPicker = false; true }
        showPlanPicker -> { showPlanPicker = false; true }
        openedPlan != null -> { openedPlan = null; true }
        showPlansList -> { showPlansList = false; true }
        showShop -> { showShop = false; true }
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
                "servers=$showServers shop=$showShop plans=$showPlansList " +
                "plan=${openedPlan != null} support=$showSupport " +
                "topup=$showTopUp recovery=$showRecovery",
        )
        return handled
    }

    private var showServers by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showSubscription by mutableStateOf(false)
    private var showShop by mutableStateOf(false)
    private var showSupport by mutableStateOf(false)
    private var showTickets by mutableStateOf(false)
    // Telegram link the user asked for while the tunnel was down. Held until
    // the VPN reports itself up, then opened.
    private var askVpnForTelegram by mutableStateOf(false)
    private var telegramLinkPending by mutableStateOf(false)
    private var openedTicket by mutableStateOf<VpnkaAccount.SupportTicket?>(null)
    private var showTopUp by mutableStateOf(false)
    private var showRecovery by mutableStateOf(false)
    private var showServerPicker by mutableStateOf(false)
    private var showPlanPicker by mutableStateOf(false)
    private var showPlansList by mutableStateOf(false)
    private var openedPlan by mutableStateOf<VpnkaAccount.Plan?>(null)

    /** Set by the post-payment link; consumed on the next composition. */
    private var vpnkaOpenProfileAfterPayment = false

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
        var tariffs by remember { mutableStateOf<List<VpnkaAccount.Tariff>>(emptyList()) }
        var shopLoading by remember { mutableStateOf(false) }
        var shopError by remember { mutableStateOf<String?>(null) }
        var buying by remember { mutableStateOf(false) }
        var supportMessages by remember {
            mutableStateOf<List<VpnkaAccount.SupportMessage>>(emptyList())
        }
        var supportLoading by remember { mutableStateOf(false) }
        var supportSending by remember { mutableStateOf(false) }
        var supportReload by remember { mutableIntStateOf(0) }
        var topUpBusy by remember { mutableStateOf(false) }
        var topUpError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(showSupport, supportReload) {
            if (showSupport) {
                supportLoading = true
                supportMessages = VpnkaAccount.fetchSupport()
                supportLoading = false
            }
        }

        var subs by remember { mutableStateOf(MmkvManager.vpnkaSubscriptions()) }

        LaunchedEffect(Unit) {
            if (vpnkaOpenProfileAfterPayment) {
                vpnkaOpenProfileAfterPayment = false
                showShop = false
                showSubscription = true
                subReload++
            }
        }

        LaunchedEffect(showShop) {
            if (showShop) {
                shopLoading = true
                tariffs = VpnkaAccount.fetchTariffs().orEmpty()
                shopLoading = false
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
        LaunchedEffect(showSubscription, subReload, signedIn) {
            if (signedIn) {
                subLoading = showSubscription
                val fetched = VpnkaAccount.fetchInfo()
                subInfo = fetched
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
                val plans = fetched?.subscriptions.orEmpty()
                    .filter { (it.daysLeft ?: 1) > 0 }
                    .mapNotNull { plan ->
                        val token = plan.groupToken ?: return@mapNotNull null
                        token to (plan.tariff ?: "VPNka")
                    }
                if (plans.isNotEmpty()) {
                    val switched = MmkvManager.syncSubscriptions(plans)
                    subs = MmkvManager.vpnkaSubscriptions()
                    if (switched != null) {
                        // Same path a manual refresh takes, so the user sees
                        // the familiar spinner and toasts rather than servers
                        // appearing out of nowhere.
                        importConfigViaSub()
                    }
                }
                subLoading = false
            }
        }
        var updateVersion by remember { mutableStateOf<String?>(null) }
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
                    // Start the download at the next Wi-Fi moment rather than
                    // making the user wait for it when they finally tap.
                    UpdatePrefetcher.requestPrefetchNow(this@MainActivity)
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
        val anyOverlay = showSupport || showTopUp || showRecovery ||
            showServerPicker || showPlanPicker || showPlansList ||
            openedPlan != null || showShop || showSubscription ||
            showSettings || showServers || showTickets || openedTicket != null
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
                onHistory = { showTickets = true },
                onBack = { showSupport = false },
            )
            return
        }

        if (showTopUp && !showServers) {
            VpnkaTopUpScreen(
                busy = topUpBusy,
                error = topUpError,
                onTopUp = { amount ->
                    topUpBusy = true
                    topUpError = null
                    lifecycleScope.launch {
                        val url = VpnkaAccount.topUp(amount)
                        topUpBusy = false
                        if (url != null) {
                            Utils.openUri(this@MainActivity, url)
                        } else {
                            topUpError = "Не удалось создать платёж — попробуйте позже"
                        }
                    }
                },
                onBack = { showTopUp = false },
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

        if (showShop && !showServers) {
            VpnkaShopScreen(
                loading = shopLoading,
                buying = buying,
                tariffs = tariffs,
                error = shopError,
                onBuy = { tariffId, method ->
                    buying = true
                    shopError = null
                    lifecycleScope.launch {
                        val result = VpnkaAccount.purchase(tariffId, method)
                        buying = false
                        result.fold(
                            onSuccess = { purchase ->
                                if (purchase.settled) {
                                    // Paid from balance: the subscription
                                    // exists now, so pull the profile and let
                                    // the sync above add its group.
                                    showShop = false
                                    subReload++
                                } else {
                                    // Card: the hosted page is a browser
                                    // journey. Nothing to do here but send
                                    // them there — the webhook settles it,
                                    // and reopening the profile picks it up.
                                    purchase.paymentUrl?.let { Utils.openUri(this@MainActivity, it) }
                                }
                            },
                            onFailure = { failure ->
                                shopError = when (failure) {
                                    is VpnkaAccount.NotEnoughBalanceException ->
                                        "Не хватает баланса — оплатите картой или пополните в боте"
                                    else -> "Не удалось оформить — попробуйте ещё раз"
                                }
                            },
                        )
                    }
                },
                onTopUp = { navigateTo("vpnka_month") },
                onRetry = { showShop = false; showShop = true },
                onBack = { showShop = false },
            )
            return
        }

        // Above the profile, not below it. Settings is opened from inside the
        // profile, and the profile block returns — so while it sat lower the
        // flag was set and the screen never changed. Placed here, back from
        // settings lands on the profile it was opened from.
        if (showSettings && !showServers) {
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
                onRoutingSettings = { navigateTo("routing_setting") },
                onCheckUpdate = { navigateTo("check_update") },
                onBack = { showSettings = false },
            )
            return
        }

        if (showSubscription && !showServers) {
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
                        val result = VpnkaAccount.signIn(code)
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
                                        "Код не подошёл. Он живёт 10 минут и " +
                                            "срабатывает один раз — возьмите новый в боте."
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
                        signedIn = false
                        subInfo = null
                    }
                },
                onGetCode = { navigateTo("vpnka_app_code") },
                onRenew = { showShop = true },
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
                onBack = { openedPlan = null },
            )
            return
        }

        if (showPlansList && !showServers) {
            VpnkaPlansListScreen(
                // Finished plans are history, not choices: leaving them in
                // the list invites someone to select one and conclude the
                // app is broken when no servers appear behind it.
                plans = subInfo?.subscriptions.orEmpty()
                    .filter { (it.daysLeft ?: 1) > 0 },
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
                    val guid = plan.groupToken?.let { MmkvManager.vpnkaGuidForToken(it) }
                    if (guid != null) {
                        mainViewModel.subscriptionIdChanged(guid)
                        // Switching reads servers from storage — and a plan
                        // whose group was never fetched has none, which is
                        // an empty list and «сервер не выбран» at the flower.
                        // Every plan except the one in use is in exactly
                        // that state the first time it is picked, so fetch
                        // instead of showing an empty screen and waiting for
                        // the user to guess that «обновить» is the answer.
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
                    showPlansList = false
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
                onChangeServer = { showServerPicker = true },
                // The launch check only lights the dot; the screen behind the
                // button does the real check, download and install, and it
                // already handles the install permission and FileProvider.
                onPerAppProxy = { navigateTo("per_app_proxy") },
                // The plan that runs out first is the one worth warning
                // about; a longer one behind it does not make the gap
                // any less of an outage.
                expiryDaysLeft = subInfo?.subscriptions.orEmpty()
                    .filter { !it.frozen }
                    .mapNotNull { it.daysLeft }
                    .minOrNull(),
                onRenew = { showShop = true },
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
            )
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
            "vpnka_app_code" -> {
                Utils.openUri(this, "https://t.me/vpnka_io_bot?start=appcode")
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

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser); return
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

    private fun importConfigViaSub() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    mainViewModel.updateConfigViaSubAll()
                }
                when {
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
    private fun openTelegramLinkGuarded() {
        if (mainViewModel.uiState.value.isRunning) {
            openTelegramLink()
        } else {
            askVpnForTelegram = true
        }
    }

    private fun openTelegramLink() {
        lifecycleScope.launch {
            val url = VpnkaAccount.telegramLinkUrl()
            if (url != null) Utils.openUri(this@MainActivity, url)
        }
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
