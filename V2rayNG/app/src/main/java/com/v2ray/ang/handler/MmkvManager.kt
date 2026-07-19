package com.v2ray.ang.handler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.CACHE_SUBSCRIPTION_ID
import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.AppConfig.PREF_IS_BOOTED
import com.v2ray.ang.AppConfig.PREF_ROUTING_RULESET
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

object MmkvManager {

    //region private

    private const val ID_MAIN = "MAIN"
    private const val ID_PROFILE_FULL_CONFIG = "PROFILE_FULL_CONFIG"
    private const val ID_SERVER_RAW = "SERVER_RAW"
    private const val ID_SERVER_AFF = "SERVER_AFF"
    private const val ID_SUB = "SUB"
    private const val ID_ASSET = "ASSET"
    private const val ID_SETTING = "SETTING"
    private const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    private const val KEY_ANG_CONFIGS = "ANG_CONFIGS"
    private const val KEY_SUB_SERVER_PREFIX = "SUB_SERVERS_"
    private const val KEY_SUB_IDS = "SUB_IDS"
    private const val KEY_WEBDAV_CONFIG = "WEBDAV_CONFIG"

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val profileFullStorage by lazy { MMKV.mmkvWithID(ID_PROFILE_FULL_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val assetStorage by lazy { MMKV.mmkvWithID(ID_ASSET, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    //endregion

    //region Server

    /**
     * Reads the legacy server list from KEY_ANG_CONFIGS for migration.
     * This method is for migration purposes only.
     *
     * @return The JSON string of legacy server list, or null if not exists.
     */
    fun readLegacyServerList(): String? {
        return mainStorage.decodeString(KEY_ANG_CONFIGS)
    }


    /**
     * Gets the selected server GUID.
     *
     * @return The selected server GUID.
     */
    fun getSelectServer(): String? {
        return mainStorage.decodeString(KEY_SELECTED_SERVER)
    }

    /**
     * Sets the selected server GUID.
     *
     * @param guid The server GUID.
     */
    fun setSelectServer(guid: String) {
        mainStorage.encode(KEY_SELECTED_SERVER, guid)
    }

    /**
     * Encodes the server list for a given subscription.
     * Saves to the subscription's serverList (including default subscription for ungrouped servers).
     *
     * @param serverList The list of server GUIDs.
     * @param subscriptionId The subscription ID.
     */
    fun encodeServerList(serverList: MutableList<String>, subscriptionId: String) {
        val subId = getSubscriptionId(subscriptionId)
        val key = "$KEY_SUB_SERVER_PREFIX$subId"
        mainStorage.encode(key, JsonUtil.toJson(serverList))
    }


    /**
     * Decodes the server list for a given subscription.
     * If subscriptionId is empty, returns ungrouped servers.
     * Otherwise, returns servers from the specified subscription's serverList.
     *
     * @param subscriptionId The subscription ID.
     * @return The list of server GUIDs.
     */
    fun decodeServerList(subscriptionId: String): MutableList<String> {
        val subId = getSubscriptionId(subscriptionId)
        val key = "$KEY_SUB_SERVER_PREFIX$subId"
        val json = mainStorage.decodeString(key)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }

    /**
     * Decodes all server list (merged from all subscriptions including default subscription).
     * Use this when you need the complete server list.
     *
     * @return The list of all server GUIDs.
     */
    fun decodeAllServerList(): MutableList<String> {
        val allServers = mutableListOf<String>()
        val subsList = decodeSubsList()

        // If DEFAULT_SUBSCRIPTION_ID is not in the subscriptions list, add its servers
        if (!subsList.contains(DEFAULT_SUBSCRIPTION_ID)) {
            allServers.addAll(decodeServerList(DEFAULT_SUBSCRIPTION_ID))
        }

        // Add servers from all subscriptions
        subsList.forEach { guid ->
            allServers.addAll(decodeServerList(guid))
        }

        return allServers
    }


    /**
     * Decodes the server configuration.
     *
     * @param guid The server GUID.
     * @return The server configuration.
     */
    fun decodeServerConfig(guid: String): ProfileItem? {
        if (guid.isBlank()) {
            return null
        }
        val json = profileFullStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ProfileItem::class.java)
    }


    /**
     * Encodes the server configuration.
     *
     * @param guid The server GUID.
     * @param config The server configuration.
     * @return The server GUID.
     */
    fun encodeServerConfig(guid: String, config: ProfileItem): String {
        val key = guid.ifBlank { Utils.getUuid() }
        profileFullStorage.encode(key, JsonUtil.toJson(config))

        // Use default subscription for servers without subscription
        val subId = getSubscriptionId(config.subscriptionId)
        val serverList = decodeServerList(subId)

        if (!serverList.contains(key)) {
            serverList.add(0, key)
            encodeServerList(serverList, subId)
            if (getSelectServer().isNullOrBlank()) {
                mainStorage.encode(KEY_SELECTED_SERVER, key)
            }
        }

        return key
    }

    /**
     * Encodes the server configuration directly without updating serverList.
     *
     * @param key The server GUID.
     * @param configJson The server configuration JSON string.
     */
    fun encodeProfileDirect(key: String, configJson: String) {
        profileFullStorage.encode(key, configJson)
    }

    /**
     * Removes the server configuration.
     *
     * @param guid The server GUID.
     */
    fun removeServer(guid: String) {
        if (guid.isBlank()) {
            return
        }

        // Get config to determine which subscription to update
        val config = decodeServerConfig(guid)
        val subId = getSubscriptionId(config?.subscriptionId)

        // Remove from appropriate server list
        val serverList = decodeServerList(subId)
        serverList.remove(guid)
        encodeServerList(serverList, subId)

        // Clean up storage
        if (getSelectServer() == guid) {
            mainStorage.remove(KEY_SELECTED_SERVER)
        }
        profileFullStorage.remove(guid)
        serverAffStorage.remove(guid)
    }

    /**
     * Removes the server configurations via subscription ID.
     *
     * @param subscriptionId The subscription ID.
     */
    fun removeServerViaSubid(subscriptionId: String?) {
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)

        // Remove all servers in the list
        serverList.forEach { guid ->
            if (getSelectServer() == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
        }

        serverList.clear()
        encodeServerList(serverList, subId)
    }

    /**
     * Decodes the server affiliation information.
     *
     * @param guid The server GUID.
     * @return The server affiliation information.
     */
    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverAffStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ServerAffiliationInfo::class.java)
    }

    /**
     * Encodes the server test delay in milliseconds.
     *
     * @param guid The server GUID.
     * @param testResult The test delay in milliseconds.
     */
    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    /**
     * Clears all test delay results.
     *
     * @param keys The list of server GUIDs.
     */
    fun clearAllTestDelayResults(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage.encode(key, JsonUtil.toJson(aff))
            }
        }
    }

    /**
     * Removes all server configurations.
     *
     * @return The number of server configurations removed.
     */
    fun removeAllServer(): Int {
        val count = profileFullStorage.allKeys()?.count() ?: 0
        profileFullStorage.clearAll()
        serverAffStorage.clearAll()
        serverRawStorage.clearAll()

        decodeSubscriptions().forEach { sub ->
            encodeServerList(mutableListOf(), sub.guid)
        }
        return count
    }

    /**
     * Removes invalid server configurations.
     *
     * @param guid The server GUID.
     * @return The number of server configurations removed.
     */
    fun removeInvalidServer(guid: String): Int {
        var count = 0
        if (guid.isNotEmpty()) {
            decodeServerAffiliationInfo(guid)?.let { aff ->
                if (aff.testDelayMillis < 0L) {
                    removeServer(guid)
                    count++
                }
            }
        } else {
            serverAffStorage.allKeys()?.forEach { key ->
                decodeServerAffiliationInfo(key)?.let { aff ->
                    if (aff.testDelayMillis < 0L) {
                        removeServer(key)
                        count++
                    }
                }
            }
        }
        return count
    }

    /**
     * Encodes the raw server configuration.
     *
     * @param guid The server GUID.
     * @param config The raw server configuration.
     */
    fun encodeServerRaw(guid: String, config: String) {
        serverRawStorage.encode(guid, config)
    }

    /**
     * Decodes the raw server configuration.
     *
     * @param guid The server GUID.
     * @return The raw server configuration.
     */
    fun decodeServerRaw(guid: String): String? {
        return serverRawStorage.decodeString(guid)
    }

    //endregion

    //region Subscriptions

    private fun getSubscriptionId(subscriptionId: String?): String {
        return subscriptionId?.ifEmpty { DEFAULT_SUBSCRIPTION_ID } ?: DEFAULT_SUBSCRIPTION_ID
    }

    /**
     * Initializes the subscription list.
     */
    private fun initSubsList() {
        val subsList = decodeSubsList()
        if (subsList.isNotEmpty()) {
            return
        }
        subStorage.allKeys()?.forEach { key ->
            subsList.add(key)
        }
        encodeSubsList(subsList)
    }

    /**
     * Decodes the subscriptions.
     *
     * @return The list of subscriptions.
     */
    fun decodeSubscriptions(): List<SubscriptionCache> {
        initSubsList()

        val subscriptions = mutableListOf<SubscriptionCache>()
        decodeSubsList().forEach { key ->
            val json = subStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java) ?: SubscriptionItem()
                subscriptions.add(SubscriptionCache(key, item))
            }
        }
        return subscriptions
    }

    /**
     * Removes the subscription.
     *
     * @param subid The subscription ID.
     */
    fun removeSubscription(subid: String) {
        subStorage.remove(subid)
        val subsList = decodeSubsList()
        subsList.remove(subid)
        encodeSubsList(subsList)

        removeServerViaSubid(subid)
    }

    /**
     * Encodes the subscription.
     *
     * @param guid The subscription GUID.
     * @param subItem The subscription item.
     */
    fun encodeSubscription(guid: String, subItem: SubscriptionItem) {
        val key = guid.ifBlank { Utils.getUuid() }
        subStorage.encode(key, JsonUtil.toJson(subItem))

        val subsList = decodeSubsList()
        if (!subsList.contains(key)) {
            subsList.add(key)
            encodeSubsList(subsList)
        }
    }

    /**
     * Decodes the subscription.
     *
     * @param subscriptionId The subscription ID.
     * @return The subscription item.
     */
    fun decodeSubscription(subscriptionId: String): SubscriptionItem? {
        val json = subStorage.decodeString(subscriptionId) ?: return null
        return JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java)
    }

    /**
     * Encodes the subscription list.
     *
     * @param subsList The list of subscription IDs.
     */
    fun encodeSubsList(subsList: MutableList<String>) {
        mainStorage.encode(KEY_SUB_IDS, JsonUtil.toJson(subsList))
    }

    /**
     * Decodes the subscription list.
     *
     * @return The list of subscription IDs.
     */
    fun decodeSubsList(): MutableList<String> {
        val json = mainStorage.decodeString(KEY_SUB_IDS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }

    //endregion

    //region Asset

    /**
     * Decodes the asset URLs.
     *
     * @return The list of asset URLs.
     */
    fun decodeAssetUrls(): List<AssetUrlCache> {
        val assetUrlItems = mutableListOf<AssetUrlCache>()
        assetStorage.allKeys()?.forEach { key ->
            val json = assetStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java) ?: AssetUrlItem()
                assetUrlItems.add(AssetUrlCache(key, item))
            }
        }
        return assetUrlItems.sortedBy { it.assetUrl.addedTime }
    }

    /**
     * Removes the asset URL.
     *
     * @param assetid The asset ID.
     */
    fun removeAssetUrl(assetid: String) {
        assetStorage.remove(assetid)
    }

    /**
     * Encodes the asset.
     *
     * @param assetid The asset ID.
     * @param assetItem The asset item.
     */
    fun encodeAsset(assetid: String, assetItem: AssetUrlItem) {
        val key = assetid.ifBlank { Utils.getUuid() }
        assetStorage.encode(key, JsonUtil.toJson(assetItem))
    }

    /**
     * Decodes the asset.
     *
     * @param assetid The asset ID.
     * @return The asset item.
     */
    fun decodeAsset(assetid: String): AssetUrlItem? {
        val json = assetStorage.decodeString(assetid) ?: return null
        return JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java)
    }

    //endregion

    //region Routing

    /**
     * Decodes the routing rulesets.
     *
     * @return The list of routing rulesets.
     */
    fun decodeRoutingRulesets(): MutableList<RulesetItem>? {
        val ruleset = settingsStorage.decodeString(PREF_ROUTING_RULESET)
        if (ruleset.isNullOrEmpty()) return null
        return JsonUtil.fromJsonSafe(ruleset, Array<RulesetItem>::class.java)?.toMutableList() ?: mutableListOf()
    }

    /**
     * Encodes the routing rulesets.
     *
     * @param rulesetList The list of routing rulesets.
     */
    fun encodeRoutingRulesets(rulesetList: MutableList<RulesetItem>?) {
        if (rulesetList.isNullOrEmpty())
            encodeSettings(PREF_ROUTING_RULESET, "")
        else
            encodeSettings(PREF_ROUTING_RULESET, JsonUtil.toJson(rulesetList))
    }

    //endregion

    //region settings
    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: String?): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Int): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Long): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Float): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Boolean): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: MutableSet<String>): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Decodes the settings string.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsString(key: String): String? {
        return settingsStorage.decodeString(key)
    }

    /**
     * Decodes the settings string.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsString(key: String, defaultValue: String?): String? {
        return settingsStorage.decodeString(key, defaultValue)
    }

    /**
     * Decodes the settings integer.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsInt(key: String, defaultValue: Int): Int {
        return settingsStorage.decodeInt(key, defaultValue)
    }

    /**
     * Decodes the settings long.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsLong(key: String, defaultValue: Long): Long {
        return settingsStorage.decodeLong(key, defaultValue)
    }

    /**
     * Decodes the settings float.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsFloat(key: String, defaultValue: Float): Float {
        return settingsStorage.decodeFloat(key, defaultValue)
    }

    /**
     * Decodes the settings boolean.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsBool(key: String): Boolean {
        return settingsStorage.decodeBool(key, false)
    }

    /**
     * Decodes the settings boolean.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsBool(key: String, defaultValue: Boolean): Boolean {
        return settingsStorage.decodeBool(key, defaultValue)
    }

    /**
     * Decodes the settings string set.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsStringSet(key: String): MutableSet<String>? {
        return settingsStorage.decodeStringSet(key)
    }


    /**
     * Encodes the start on boot setting.
     *
     * @param startOnBoot Whether to start on boot.
     */
    fun encodeStartOnBoot(startOnBoot: Boolean) {
        encodeSettings(PREF_IS_BOOTED, startOnBoot)
    }

    /**
     * Decodes the start on boot setting.
     *
     * @return Whether to start on boot.
     */
    fun decodeStartOnBoot(): Boolean {
        return decodeSettingsBool(PREF_IS_BOOTED, false)
    }

    //endregion

    //region WebDAV

    /**
     * Encodes the WebDAV config as JSON into storage.
     */
    fun encodeWebDavConfig(config: WebDavConfig): Boolean {
        return mainStorage.encode(KEY_WEBDAV_CONFIG, JsonUtil.toJson(config))
    }

    /**
     * Decodes the WebDAV config from storage.
     */
    fun decodeWebDavConfig(): WebDavConfig? {
        val json = mainStorage.decodeString(KEY_WEBDAV_CONFIG) ?: return null
        return JsonUtil.fromJsonSafe(json, WebDavConfig::class.java)
    }

    //endregion

    //region Compose helpers for Settings

    /**
     * MMKV-backed String state, auto-persists and notifies on change.
     */
    @Composable
    fun rememberMmkvString(
        key: String,
        default: String = ""
    ): MutableState<String> {
        val state = remember(key) {
            mutableStateOf(decodeSettingsString(key, default) ?: default)
        }

        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    /**
     * MMKV-backed Boolean state, auto-persists and notifies on change.
     */
    @Composable
    fun rememberMmkvBool(
        key: String,
        default: Boolean = false
    ): MutableState<Boolean> {
        val state = remember(key) {
            mutableStateOf(decodeSettingsBool(key, default))
        }

        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    //endregion

    //region VPNka install id

    /**
     * Stable per-install id, sent as the `Hwid` header when refreshing a
     * subscription so the VPNka backend can count devices reliably.
     *
     * Why a random UUID and not a device identifier: ANDROID_ID or anything
     * hardware-derived is a privacy problem and a Play-policy problem, and we
     * don't need it — the backend only has to recognise *this install* across
     * refreshes, not identify the phone.
     *
     * Why it matters: without this header the backend falls back to hashing
     * User-Agent plus IP prefix, which changes every time the user moves
     * between Wi-Fi and mobile data. Each change looked like a brand-new
     * device and burned a device slot — on a 1-device plan that locked people
     * out of their own subscription. A value that survives network changes and
     * app updates is the whole point.
     *
     * Generated once, then persisted; callers can treat it as constant.
     */
    fun getOrCreateInstallId(): String {
        settingsStorage.decodeString(KEY_VPNKA_INSTALL_ID)?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // Hyphen-free so it stays well within the backend's 128-char cap and
        // can't be confused with any structured id we might add later.
        val id = java.util.UUID.randomUUID().toString().replace("-", "")
        settingsStorage.encode(KEY_VPNKA_INSTALL_ID, id)
        return id
    }

    private const val KEY_VPNKA_INSTALL_ID = "vpnka_install_id"

    /**
     * URL the app ships with as its default subscription.
     *
     * Backed by `/qr/app`, which mints a 24h trial on first fetch and then
     * keeps returning the same one — keyed by the install id we send in the
     * `Hwid` header. A mint-per-hit URL would strand a throwaway trial user
     * in the panel on every refresh and swap the config under the user.
     */
    const val VPNKA_TRIAL_SUB_URL = "https://get.vpnka.io/qr/app"

    /**
     * Give an install with nothing to connect to the trial subscription, so
     * the app is usable before the user has heard of our Telegram bot.
     *
     * The condition is "no servers configured", not "first ever launch".
     * A one-shot flag looked tidier and was wrong in practice: an install
     * carrying a leftover empty subscription group — say from an earlier
     * build, or an import that failed — would be counted as "user already
     * has something" and left staring at an empty app forever. Checking for
     * actual servers means the app heals itself on the next launch instead.
     *
     * Re-seeding can't be farmed for free VPN: `/qr/app` keys the grant to
     * the install id we send in `Hwid` and returns the same one until it
     * expires, so deleting and re-adding the subscription gets the same 24h
     * back, not a new one. The abuse gate lives on the server, which is why
     * the client can afford to be this forgiving.
     *
     * @return true when the app has nothing to connect to and the caller
     *         should fetch — including the case where our subscription was
     *         already added but its download never succeeded. Returning
     *         false only once servers exist keeps a failed first fetch from
     *         stranding the user on an empty list forever.
     */
    private const val KEY_VPNKA_LAST_VERSION = "vpnka_last_run_version"

    /**
     * True on the first launch after the app version changed.
     *
     * What we get served depends on the version we announce: the backend
     * picks the config format by User-Agent, and 2.2.6.5 is the release that
     * started identifying as VPNka and therefore receiving the «Авто»
     * balancer. An install that updates but never re-fetches keeps the old
     * flat list and looks broken — the user is told the feature shipped and
     * cannot see it. So a version change forces one refresh.
     */
    fun consumeVersionChanged(currentVersion: String): Boolean {
        val previous = decodeSettingsString(KEY_VPNKA_LAST_VERSION)
        if (previous == currentVersion) return false
        encodeSettings(KEY_VPNKA_LAST_VERSION, currentVersion)
        // A fresh install isn't an update: ensureTrialSubscription already
        // fetches for it, and doing both would double the work on first run.
        return !previous.isNullOrBlank()
    }

    fun ensureTrialSubscription(): Boolean {
        // A real, working setup: their month, or a restored backup. Leave it.
        if (decodeAllServerList().isNotEmpty()) {
            return false
        }
        val alreadyOurs = decodeSubscriptions()
            .any { it.subscription.url == VPNKA_TRIAL_SUB_URL }
        if (!alreadyOurs) {
            // Own the guid instead of letting encodeSubscription mint one, so
            // we can point the group selector at it below.
            val guid = Utils.getUuid()
            encodeSubscription(
                guid,
                SubscriptionItem(
                    remarks = "VPNka · пробный доступ",
                    url = VPNKA_TRIAL_SUB_URL,
                    enabled = true,
                    autoUpdate = true,
                ),
            )
            // Open the app on our group. Upstream otherwise lands on
            // "Default" — its internal bucket for servers that belong to no
            // subscription — which on a fresh install is empty, so the first
            // thing a new user sees is an empty list next to the servers they
            // actually have.
            encodeSettings(CACHE_SUBSCRIPTION_ID, guid)
        }
        return true
    }

    private const val KEY_VPNKA_ACCOUNT_TOKEN = "vpnka_account_token"
    private const val VPNKA_SUB_PREFIX = "https://get.vpnka.io/sub/"

    /**
     * This install's account token, or null when signed out.
     *
     * Kept in the same MMKV store as everything else. That store is
     * app-private, so on a non-rooted phone it is as protected as the
     * subscription URL sitting next to it — and unlike that URL, a leak here
     * can be cut off from the bot without touching anyone else's device.
     */
    fun getAccountToken(): String? =
        settingsStorage.decodeString(KEY_VPNKA_ACCOUNT_TOKEN)?.takeIf { it.isNotBlank() }

    fun setAccountToken(token: String?) {
        if (token.isNullOrBlank()) {
            settingsStorage.remove(KEY_VPNKA_ACCOUNT_TOKEN)
        } else {
            settingsStorage.encode(KEY_VPNKA_ACCOUNT_TOKEN, token)
        }
    }

    /**
     * One v2rayNG subscription group per plan the account holds.
     *
     * A client can hold several subscriptions at once — a year on ten
     * devices next to a leftover month on one — and they are separate
     * things on the server, each with its own key and device count. Modelling
     * them as separate groups means switching between them is v2rayNG's own
     * group selector, which already knows how to fetch, list and remember a
     * selection. The alternative, one blended group, would hide from the user
     * which plan is actually carrying their traffic.
     *
     * Plans that vanish (expired, refunded) have their groups removed, so the
     * picker can't offer a subscription the server would refuse. The trial
     * group is dropped as soon as a real plan exists — it's a 24h grant the
     * account has outgrown, and leaving it in the list invites someone to
     * select it and conclude the app is broken.
     *
     * @return the guid to select, or null to leave the current selection be.
     */
    fun syncSubscriptions(plans: List<Pair<String, String>>): String? {
        if (plans.isEmpty()) return null

        val wanted = plans.associate { (token, label) ->
            (VPNKA_SUB_PREFIX + token) to label
        }
        val existing = decodeSubscriptions()

        // Drop what the account no longer has, plus the shipped trial.
        existing
            .filter {
                val url = it.subscription.url
                url == VPNKA_TRIAL_SUB_URL ||
                    (url.startsWith(VPNKA_SUB_PREFIX) && url !in wanted)
            }
            .forEach { removeSubscription(it.guid) }

        var firstGuid: String? = null
        for ((url, label) in wanted) {
            val already = decodeSubscriptions().firstOrNull { it.subscription.url == url }
            val guid = already?.guid ?: Utils.getUuid()
            encodeSubscription(
                guid,
                SubscriptionItem(
                    remarks = label,
                    url = url,
                    enabled = true,
                    autoUpdate = true,
                ),
            )
            if (firstGuid == null) firstGuid = guid
        }

        // Keep the user's choice if it still exists; otherwise fall to the
        // first plan, which the caller orders by expiry so it's the longest-
        // lived one rather than an arbitrary pick.
        val selected = decodeSettingsString(CACHE_SUBSCRIPTION_ID)
        val stillValid = selected != null &&
            decodeSubscriptions().any { it.guid == selected }
        if (!stillValid && firstGuid != null) {
            encodeSettings(CACHE_SUBSCRIPTION_ID, firstGuid)
            return firstGuid
        }
        return null
    }

    /** The subscription groups belonging to us, for the home picker. */
    fun vpnkaSubscriptions(): List<Pair<String, String>> =
        decodeSubscriptions()
            .filter {
                it.subscription.url.startsWith(VPNKA_SUB_PREFIX) ||
                    it.subscription.url == VPNKA_TRIAL_SUB_URL
            }
            .map { it.guid to it.subscription.remarks }

    fun selectedSubscriptionGuid(): String? = decodeSettingsString(CACHE_SUBSCRIPTION_ID)

    fun selectSubscription(guid: String) = encodeSettings(CACHE_SUBSCRIPTION_ID, guid)

    /**
     * Point the app at the subscription belonging to the account that just
     * signed in, replacing the trial it shipped with.
     *
     * This is what signing in is *for*, from the user's side: before it,
     * getting a paid subscription onto a phone meant copying a URL out of
     * the bot by hand. The trial group is dropped rather than left disabled
     * — it is a 24h grant the account has outgrown, and leaving it in the
     * picker only invites someone to select an expired profile and conclude
     * the app is broken.
     *
     * @return true when the subscription list changed and the caller should
     *         fetch.
     */
    fun adoptSubscription(subscriptionToken: String): Boolean {
        if (subscriptionToken.isBlank()) return false
        val url = VPNKA_SUB_PREFIX + subscriptionToken
        if (decodeSubscriptions().any { it.subscription.url == url }) return false

        decodeSubscriptions()
            .filter { it.subscription.url == VPNKA_TRIAL_SUB_URL }
            .forEach { removeSubscription(it.guid) }

        val guid = Utils.getUuid()
        encodeSubscription(
            guid,
            SubscriptionItem(
                remarks = "VPNka",
                url = url,
                enabled = true,
                autoUpdate = true,
            ),
        )
        encodeSettings(CACHE_SUBSCRIPTION_ID, guid)
        return true
    }

    //endregion
}
