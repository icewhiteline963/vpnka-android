package com.v2ray.ang.handler

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * One-to-one AND group (mesh) end-to-end voice calls over WebRTC.
 *
 * The media (DTLS-SRTP) is encrypted peer-to-peer by WebRTC itself; on top of
 * that, every signaling frame (SDP offer/answer, ICE candidate, hangup) is
 * sealed to the peer's key and relayed blindly by the server over the messenger
 * WebSocket — the server never sees SDP or media. ICE servers (STUN, and TURN
 * when the backend hands out credentials) come from `/app/messenger/ice`.
 *
 * Group calls are a full mesh, not an SFU: every participant holds a separate
 * [PeerLeg] (its own PeerConnection) to every OTHER participant. An SFU would
 * need either server-side decryption (breaks the zero-knowledge design this
 * app is built around) or WebRTC Insertable Streams (a much larger lift) — at
 * the small group sizes this app targets, mesh is the pragmatic choice.
 *
 * A 1:1 call is simply a mesh of size 1: [legs] has exactly one entry and
 * [groupId] is blank. The call screen's 1:1 rendering reads [peerName]/[peerId],
 * which are thin views onto that single leg, so the ordinary call experience is
 * unchanged in shape.
 *
 * State is exposed as Compose-observable fields so the call UI reacts directly.
 */
object CallManager {

    enum class Phase { IDLE, OUTGOING, INCOMING, ACTIVE, ENDED }

    /** Per-participant connection state, shown in the group roster (and, for a
     *  1:1 call, folded into the single-peer status text the old screen used). */
    enum class LegPhase { CONNECTING, ACTIVE, ENDED }

    // Re-offer cadence while ringing: ~20 s total, which covers a socket that
    // is mid-reconnect (the client pings every 25 s) without ringing forever
    // at somebody whose phone is simply off.
    private const val OFFER_RETRY_MS = 2_000L
    private const val MAX_OFFER_TRIES = 10

    // 24.08.2026: если предложение звонка ушло, а трубку просто не берут,
    // фаза оставалась OUTGOING/INCOMING без ограничения по времени — «Звоним…»
    // висело вечно, всё это время держался режим разговора: открыт микрофон,
    // жив PeerConnection, заглушён звук системы. Столько же звонит обычный
    // телефон, дальше — отбой.
    private const val RING_TIMEOUT_MS = 60_000L

    /** Потолок участников группового звонка (считая меня). Mesh растёт как
     *  O(N²) ножек и TURN-трафика — сверх этого числа UI не даёт позвать. */
    const val MAX_GROUP_PARTICIPANTS = 6

    var phase by mutableStateOf(Phase.IDLE)
        private set
    /** "" — обычный 1:1 звонок; иначе — id общей сессии группового звонка. */
    var groupId by mutableStateOf("")
        private set
    var muted by mutableStateOf(false)
        private set
    var speaker by mutableStateOf(false)
        private set
    /** Wall-clock ms when the FIRST leg connected; 0 until then (for the timer). */
    var connectedAt by mutableStateOf(0L)
        private set
    /** Why the last call ended, when there is something to say (shown on the
     *  call screen). Empty for an ordinary hang-up. */
    var endReason by mutableStateOf("")
        private set
    /** Остальные участники звонка — и 1:1, и групповой экран читают отсюда.
     *  Для 1:1 в списке ровно один человек. */
    var roster by mutableStateOf<List<RosterUi>>(emptyList())
        private set
    /** Сколько ЕЩЁ человек, кроме звонящего, в приглашении в групповой звонок —
     *  для экрана «входящий»: до accept() их ещё нет в [roster] (ножки к ним
     *  открываются только после согласия), но назвать их число нужно сразу. */
    var pendingInviteOthers by mutableStateOf(0)
        private set

    /** Единственный собеседник 1:1 звонка (первый участник для группового —
     *  места кода, которые ещё не различают их, получают разумное значение). */
    val peerName: String get() = roster.firstOrNull()?.name ?: ""
    val peerId: Long get() = roster.firstOrNull()?.id ?: 0L

    data class RosterUi(
        val id: Long,
        val name: String,
        val legPhase: LegPhase,
        /** Имя того, через кого узнали ключ этого человека — пусто, если он и
         *  так был в контактах. Показываем как «Добавлен(а) через {имя}». */
        val introducedBy: String = "",
    )

    private val gson = Gson()
    private val main = Handler(Looper.getMainLooper())

    // --- общее на все ножки: один микрофон, одна аудио-сессия устройства ---
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var audioSource: AudioSource? = null
    private var localTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var savedAudioMode = AudioManager.MODE_NORMAL
    private var appContext: Context? = null
    private var iceServers: List<PeerConnection.IceServer> = emptyList()

    /** Кто позвал меня в групповой звонок (для «Добавлен(а) через…» и чтобы не
     *  открывать ножку обратно инициатору — она уже есть). 0 — я сам инициатор
     *  или это обычный 1:1 звонок. */
    private var groupInitiatorId: Long = 0L

    /** Публичные ключи участников группового звонка, которых я не знал как
     *  контакты — известны только на время ЭТОЙ сессии (ростер подписан
     *  инициатором, но это доверие через посредника, не постоянная запись
     *  адресной книги). Полностью очищается в [endSession]. */
    private val introducedKeys = HashMap<Long, IntroducedKey>()
    private data class IntroducedKey(val pubKey: String, val introducedByName: String)

    /** Одна ножка mesh-звонка — раньше это были глобальные скаляры CallManager
     *  (единственный [pc], единственный собеседник); теперь на каждого
     *  участника своя запись, и вся 1:1-логика (offer/answer/ICE/повтор/
     *  таймаут) применяется к ней без изменений по сути, просто параметрами. */
    private class PeerLeg(
        val peerId: Long,
        var peerName: String,
        var peerPubKey: String,
        val introducedByName: String = "",
    ) {
        var pc: PeerConnection? = null
        var callId: String = ""
        var legPhase: LegPhase = LegPhase.CONNECTING
        val pendingRemoteIce = ArrayList<IceCandidate>()
        var remoteReady = false
        var pendingOffer: SessionDescription? = null
        var offerJson: String = ""
        var offerTries = 0
        var lastOutgoing = false
        var answered = false
        var declined = false
        var connectedAt = 0L
        lateinit var offerRetryRunnable: Runnable
        lateinit var ringTimeoutRunnable: Runnable
        lateinit var dropIfStillDownRunnable: Runnable
    }

    private val legs = LinkedHashMap<Long, PeerLeg>()

    private fun newLeg(peerId: Long, peerName: String, peerPubKey: String, introducedByName: String = ""): PeerLeg {
        val leg = PeerLeg(peerId, peerName, peerPubKey, introducedByName)
        leg.offerRetryRunnable = Runnable { retryOffer(leg) }
        leg.ringTimeoutRunnable = Runnable { onRingTimeout(leg) }
        leg.dropIfStillDownRunnable = Runnable { onDropIfStillDown(leg) }
        legs[peerId] = leg
        publishRoster()
        return leg
    }

    private fun publishRoster() {
        roster = legs.values.map {
            RosterUi(id = it.peerId, name = it.peerName, legPhase = it.legPhase, introducedBy = it.introducedByName)
        }
    }

    /** Связь не вернулась за отведённое время у ЭТОЙ ножки — только тогда её
     *  кладём (остальные ножки звонка это не затрагивает). */
    private fun onDropIfStillDown(leg: PeerLeg) {
        val st = leg.pc?.connectionState()
        if (leg.legPhase == LegPhase.ACTIVE && st != PeerConnection.PeerConnectionState.CONNECTED) {
            cleanupLeg(leg, endReasonIfSole = "Связь прервалась")
        }
    }

    /** Wire the messenger's incoming call-signal callback to us. Call once. */
    fun attach() {
        Messenger.onCallSignal = { from, json -> onSignal(from, json) }
        Messenger.onCallMiss = { peer -> onPeerOffline(peer) }
    }

    // --- outgoing / incoming lifecycle ---

    /** Place a 1:1 call to a contact (caller side). */
    fun startCall(context: Context, contactId: Long, name: String) {
        if (phase != Phase.IDLE && phase != Phase.ENDED) return
        appContext = context.applicationContext
        endReason = ""
        groupId = ""
        groupInitiatorId = 0L
        setPhase(Phase.OUTGOING)
        val leg = newLeg(contactId, name, Messenger.contacts().firstOrNull { it.id == contactId }?.pubKey ?: "")
        leg.lastOutgoing = true
        armRingTimeout(leg)
        Thread {
            iceServers = loadIce()
            main.post {
                if (phase != Phase.OUTGOING || legs[contactId] !== leg) return@post
                createLegPeer(context, leg)
                createLegOffer(leg, group = "", roster = "", sig = "")
            }
        }.start()
    }

    /**
     * Начать групповой звонок. `participants` — контакты, каждый из которых
     * должен уже быть у меня в адресной книге (их ключи и есть то, что
     * позволяет представить их друг другу — см. класс-комментарий и §3 плана).
     */
    fun startGroupCall(context: Context, participants: List<Messenger.Contact>) {
        if (phase != Phase.IDLE && phase != Phase.ENDED) return
        if (participants.isEmpty() || participants.size + 1 > MAX_GROUP_PARTICIPANTS) return
        appContext = context.applicationContext
        endReason = ""
        val gid = java.util.UUID.randomUUID().toString()
        groupId = gid
        groupInitiatorId = 0L // я сам инициатор
        setPhase(Phase.OUTGOING)

        val myId = Messenger.myClientId()
        val rosterEntries = listOf(RosterEntry(myId, Messenger.myHandle(), Messenger.myPublicKey())) +
            participants.map { RosterEntry(it.id, it.name, it.pubKey) }
        val rosterJson = gson.toJson(rosterEntries)
        val sig = Messenger.signPayload(rosterJson)

        participants.forEach { c ->
            val leg = newLeg(c.id, c.name, c.pubKey)
            leg.lastOutgoing = true
            armRingTimeout(leg)
        }
        Thread {
            iceServers = loadIce()
            main.post {
                if (groupId != gid) return@post
                participants.forEach { c ->
                    val leg = legs[c.id] ?: return@forEach
                    createLegPeer(context, leg)
                    createLegOffer(leg, group = gid, roster = rosterJson, sig = sig)
                }
            }
        }.start()
    }

    /**
     * Accept a ringing call (callee side) — 1:1 или групповой, одна кнопка.
     * Для группового: приняв приглашение инициатора, СРАЗУ поднимаем свои
     * ножки к остальным участникам ростера (кроме тех, у кого id меньше
     * моего — та сторона откроет ножку сама, чтобы обе стороны одной пары не
     * слали offer друг другу одновременно, см. [connectToOtherParticipants]).
     */
    fun accept(context: Context) {
        if (phase != Phase.INCOMING) return
        val leg = legs[groupInitiatorId.takeIf { it != 0L } ?: peerId] ?: run { hangup(); return }
        if (leg.answered) return  // второе нажатие клало трубку на только что принятом звонке
        leg.answered = true
        appContext = context.applicationContext
        onCallCleared?.invoke()
        val offer = leg.pendingOffer ?: run { hangup(); return }
        Thread {
            iceServers = loadIce()
            main.post {
                if (phase != Phase.INCOMING || legs[leg.peerId] !== leg) return@post
                createLegPeer(context, leg)
                leg.pc?.setRemoteDescription(observerLog {
                    leg.remoteReady = true
                    flushPendingIce(leg)
                    createLegAnswer(leg)
                }, offer)
                leg.pendingOffer = null
                if (groupId.isNotBlank()) connectToOtherParticipants(context)
            }
        }.start()
    }

    /** Открыть ножки ко всем участникам ростера, кроме инициатора (та ножка
     *  уже есть) и кроме тех, у кого id МЕНЬШЕ моего — они сами позвонят мне,
     *  иначе одна и та же пара соединилась бы дважды (классическое glare). */
    private fun connectToOtherParticipants(context: Context) {
        val myId = Messenger.myClientId()
        val entries = pendingRosterEntries
        pendingRosterEntries = emptyList()
        val gid = groupId
        entries.filter { it.id != myId && it.id != groupInitiatorId && it.id > myId }
            .forEach { entry ->
                if (legs.containsKey(entry.id)) return@forEach
                // Ключ и провенанс уже записаны заранее — либо это контакт
                // (introducedKeys для него пуст, sendCallSignal сам найдёт
                // ключ в contacts()), либо запись положена при получении
                // приглашения (см. onOffer, ветка нового группового звонка).
                val pubKey = Messenger.contacts().firstOrNull { it.id == entry.id }?.pubKey
                    ?: introducedKeys[entry.id]?.pubKey ?: entry.pubKey
                val introducedBy = introducedKeys[entry.id]?.introducedByName ?: ""
                val leg = newLeg(entry.id, entry.name, pubKey, introducedBy)
                createLegPeer(context, leg)
                createLegOffer(leg, group = gid, roster = "", sig = "")
            }
    }

    /** Reject a ringing call without answering. */
    fun decline() {
        val id = groupInitiatorId.takeIf { it != 0L } ?: peerId
        val leg = legs[id]
        if (leg != null) {
            leg.declined = true
            signalLeg(leg, kind = "hangup")
        }
        endSession(Phase.ENDED)
    }

    /** Hang up — только МОИ ножки. В mesh-звонке остальные продолжают
     *  разговаривать друг с другом: «завершить для всех» здесь не бывает. */
    fun hangup() {
        legs.values.toList().forEach { signalLeg(it, kind = "hangup") }
        endSession(Phase.ENDED)
    }

    /** Убрать экран завершённого звонка, не дожидаясь автосброса. */
    fun reset() {
        if (phase == Phase.ENDED) {
            phase = Phase.IDLE
            // Причину тоже стираем: иначе она доживала до СЛЕДУЮЩЕГО звонка
            // и «Не отвечает» показывалось поверх только что начатого.
            endReason = ""
        }
    }

    fun toggleMute() {
        muted = !muted
        localTrack?.setEnabled(!muted)
    }

    fun toggleSpeaker() {
        speaker = !speaker
        audioManager?.isSpeakerphoneOn = speaker
    }

    // --- incoming signaling ---

    /** Ростер приглашения, которое сейчас звонит (INCOMING) — используется при
     *  [accept] для подъёма ножек к остальным. Пусто вне этого окна. */
    private var pendingRosterEntries: List<RosterEntry> = emptyList()

    private fun onSignal(from: Long, json: String) {
        val sig = try { gson.fromJson(json, CallSig::class.java) } catch (e: Exception) { return }
        when (sig.kind) {
            "offer" -> main.post { onOffer(from, sig) }
            "answer" -> main.post {
                val leg = legs[from] ?: return@post
                if (leg.legPhase == LegPhase.ENDED) return@post
                leg.pc?.setRemoteDescription(observerLog {
                    leg.remoteReady = true
                    flushPendingIce(leg)
                }, SessionDescription(SessionDescription.Type.ANSWER, sig.sdp))
            }
            "ice" -> main.post {
                val leg = legs[from] ?: return@post
                val cand = IceCandidate(sig.mid, sig.idx, sig.cand)
                if (leg.remoteReady) leg.pc?.addIceCandidate(cand) else leg.pendingRemoteIce.add(cand)
            }
            "hangup" -> main.post {
                val leg = legs[from] ?: return@post
                cleanupLeg(leg, endReasonIfSole = null)
            }
            "busy" -> main.post {
                val leg = legs[from] ?: return@post
                if (leg.legPhase == LegPhase.CONNECTING) cleanupLeg(leg, endReasonIfSole = null)
            }
        }
    }

    private fun onOffer(from: Long, sig: CallSig) {
        val existingLeg = legs[from]

        // Повторное предложение по ТОМУ ЖЕ звонку игнорируем: оно приходит до
        // десяти раз при недоставке, а пересоздание состояния сбрасывало
        // признак снятой трубки — второе нажатие «принять» создавало второй
        // PeerConnection поверх первого (открытый микрофон, который уже никто
        // не закроет), а принятый звонок мог записаться в журнал «пропущенным».
        if (existingLeg != null && sig.call == existingLeg.callId && existingLeg.legPhase != LegPhase.ENDED) {
            existingLeg.pendingOffer = SessionDescription(SessionDescription.Type.OFFER, sig.sdp)
            return
        }

        if (sig.group.isNotBlank() && sig.group == groupId) {
            // Ножка mesh-соединения к уже принятому мной групповому звонку —
            // от участника, до которого я ещё не достучался (или он медленнее
            // меня согласился). Раз я уже сказал «да» этому groupId, отдельный
            // рингинг не нужен — соединяемся молча.
            val fromContact = Messenger.contacts().firstOrNull { it.id == from }
            val introduced = introducedKeys[from]
            if (fromContact == null && introduced == null) return // не представлен и не контакт — отбой
            if (existingLeg != null && existingLeg.legPhase != LegPhase.ENDED) return // ножка уже есть
            val name = sig.name.ifBlank { fromContact?.name ?: "Контакт $from" }
            val leg = newLeg(from, name, fromContact?.pubKey ?: introduced!!.pubKey, introduced?.introducedByName ?: "")
            leg.callId = sig.call
            leg.pendingOffer = SessionDescription(SessionDescription.Type.OFFER, sig.sdp)
            val ctx = appContext ?: return
            createLegPeer(ctx, leg)
            leg.pc?.setRemoteDescription(observerLog {
                leg.remoteReady = true
                flushPendingIce(leg)
                createLegAnswer(leg)
            }, leg.pendingOffer)
            leg.pendingOffer = null
            return
        }

        // Новый звонок (1:1 или приглашение в новую группу) — только пока я
        // ничем не занят.
        if (phase == Phase.ACTIVE || phase == Phase.OUTGOING || phase == Phase.INCOMING) {
            Messenger.sendCallSignal(from, gson.toJson(CallSig(call = sig.call, kind = "busy")))
            // Звонок, пришедший во время разговора, тоже попадает в журнал:
            // вкладка называется журналом, а теряла ровно те звонки, которые
            // важнее всего увидеть потом.
            ChatPrefs.addCall(
                ChatPrefs.Call(
                    peerId = from,
                    name = sig.name.ifBlank { "Контакт $from" },
                    dir = "missed",
                    ts = System.currentTimeMillis(),
                    group = sig.group,
                ),
            )
            return
        }

        // Звонить может только тот, кто у нас в контактах.
        //
        // Раньше предложение принималось от кого угодно и показывало
        // присланное имя как есть: любой аккаунт мог звонить любому,
        // подставляя произвольную подпись, и телефон звонил минуту с открытым
        // микрофоном. Соединение всё равно не состоялось бы — сигналы уходят
        // только контактам, — то есть это был чистый способ донимать человека.
        val contact = Messenger.contacts().firstOrNull { it.id == from } ?: return

        var roster: List<RosterEntry> = emptyList()
        var others = 0
        if (sig.group.isNotBlank()) {
            // Приглашение в НОВЫЙ групповой звонок — ростер обязателен и
            // подписан инициатором. Любая нестыковка — отказ ЦЕЛИКОМ, без
            // попытки принять то, что распарсилось: недоверенный ростер мог
            // бы подсунуть чужие ключи под чужими именами.
            val entries = try {
                gson.fromJson<List<RosterEntry>>(sig.roster, object : TypeToken<List<RosterEntry>>() {}.type)
            } catch (e: Exception) { null }
            if (entries.isNullOrEmpty() ||
                entries.size > MAX_GROUP_PARTICIPANTS ||
                entries.any { it.id <= 0 || it.name.isBlank() || !isValidPubKey(it.pubKey) } ||
                !Messenger.verifyPayload(sig.roster, sig.sig, contact.pubKey)
            ) {
                return
            }
            roster = entries
            pendingRosterEntries = entries
            others = entries.count { it.id != from && it.id != Messenger.myClientId() }
            // Запоминаем ключи всех незнакомцев ростера СРАЗУ, а не только
            // тех, кому я сам открою ножку в connectToOtherParticipants: те, у
            // кого id меньше моего, по tie-break'у сами позвонят мне первыми
            // (см. onOffer, ветка «sig.group == groupId»), и без записи здесь
            // их офер отклонялся бы как «ни контакт, ни представлен».
            val myId = Messenger.myClientId()
            entries.filter { it.id != myId && Messenger.contacts().none { c -> c.id == it.id } }
                .forEach { introducedKeys[it.id] = IntroducedKey(it.pubKey, contact.name) }
        }

        endReason = ""
        groupId = sig.group
        groupInitiatorId = if (sig.group.isNotBlank()) from else 0L
        pendingInviteOthers = others
        setPhase(Phase.INCOMING)
        val leg = newLeg(from, sig.name.ifBlank { "Контакт $from" }, contact.pubKey)
        leg.callId = sig.call
        leg.pendingOffer = SessionDescription(SessionDescription.Type.OFFER, sig.sdp)
        armRingTimeout(leg)
        if (sig.group.isNotBlank()) {
            // Груз ICE-кредов заранее, не дожидаясь accept(): ножка mesh-
            // соединения от ДРУГОГО приглашённого может прийти раньше, чем я
            // решу принять приглашение инициатора (см. onOffer, ветка
            // «sig.group == groupId» — она использует iceServers сразу).
            Thread { iceServers = loadIce() }.start()
        }
        // The screen may be off and the app not on it — the background link
        // service turns this into a ringing notification.
        onIncomingCall?.invoke(from, leg.peerName, others)
    }

    private fun isValidPubKey(pubB64: String): Boolean = try {
        val kf = KeyFactory.getInstance("RSA")
        kf.generatePublic(X509EncodedKeySpec(android.util.Base64.decode(pubB64, android.util.Base64.NO_WRAP)))
        true
    } catch (e: Exception) { false }

    // --- WebRTC plumbing ---

    private fun ensureFactory(context: Context) {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions(),
        )
        val egl = EglBase.create()
        eglBase = egl
        val adm = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    /** Один и тот же локальный трек микрофона добавляется в КАЖДУЮ ножку —
     *  WebRTC Android SDK поддерживает один MediaStreamTrack в нескольких
     *  PeerConnection одновременно (не проверено раньше статическим чтением
     *  кода, т.к. раньше в приложении всегда была ровно одна ножка — первый
     *  живой групповой звонок и есть проверка этого допущения). */
    private fun ensureLocalTrack(context: Context) {
        if (localTrack != null) return
        val f = factory ?: return
        val src = f.createAudioSource(MediaConstraints())
        audioSource = src
        localTrack = f.createAudioTrack("audio0", src).apply { setEnabled(!muted) }
    }

    private fun createLegPeer(context: Context, leg: PeerLeg) {
        ensureFactory(context)
        startAudioSession(context)
        // 24.08.2026: политика ICE не задавалась, то есть работало значение по
        // умолчанию — ALL. Приложение исключено из собственного туннеля, поэтому
        // в кандидатах уходил РЕАЛЬНЫЙ адрес абонента, и любой, кто смог
        // позвонить, деанонимизировал клиента VPN. Пока есть TURN — гоняем
        // строго через него; без TURN звонок физически не состоится, но и
        // адрес не утечёт. Молча раскрывать IP на сервисе, который покупают
        // ради приватности, хуже, чем не дать позвонить.
        val hasTurn = iceServers.any { srv -> srv.urls.any { it.startsWith("turn") } }
        if (!hasTurn) {
            // Без ретранслятора звонок не поднимаем вовсе. NOHOST убирает
            // только host-кандидатов, srflx остаются — и это настоящий
            // публичный адрес человека.
            endReason = "Звонки временно недоступны"
            runCatching { signalLeg(leg, kind = "hangup") }
            cleanupLeg(leg, endReasonIfSole = null)
            return
        }
        ensureLocalTrack(context)
        val rtc = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
        leg.pc = factory?.createPeerConnection(rtc, pcObserverFor(leg)) ?: return
        localTrack?.let { leg.pc?.addTrack(it, listOf("stream0")) }
    }

    private fun pcObserverFor(leg: PeerLeg) = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            signalLeg(leg, kind = "ice", cand = c.sdp, mid = c.sdpMid ?: "", idx = c.sdpMLineIndex)
        }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            main.post {
                if (!legs.containsKey(leg.peerId) || legs[leg.peerId] !== leg) return@post
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        main.removeCallbacks(leg.dropIfStillDownRunnable)
                        if (leg.legPhase != LegPhase.ACTIVE) {
                            leg.legPhase = LegPhase.ACTIVE
                            leg.connectedAt = System.currentTimeMillis()
                            publishRoster()
                        }
                        if (phase != Phase.ACTIVE) {
                            phase = Phase.ACTIVE
                            connectedAt = System.currentTimeMillis()
                        }
                    }
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.CLOSED ->
                        if (leg.legPhase == LegPhase.ACTIVE) cleanupLeg(leg, endReasonIfSole = null)
                    // DISCONNECTED — состояние ПЕРЕХОДНОЕ: переход с Wi-Fi на
                    // мобильную сеть, короткая потеря пакетов. Обычно связь
                    // восстанавливается сама. Раньше отсюда сразу шёл отбой, и
                    // выход из дома на улицу гарантированно рвал разговор.
                    // Даём десять секунд.
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        if (leg.legPhase == LegPhase.ACTIVE) {
                            main.removeCallbacks(leg.dropIfStillDownRunnable)
                            main.postDelayed(leg.dropIfStillDownRunnable, 10_000)
                        }
                    }
                    else -> {}
                }
            }
        }
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
        override fun onSignalingChange(s: PeerConnection.SignalingState) {}
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onAddStream(s: org.webrtc.MediaStream) {}
        override fun onRemoveStream(s: org.webrtc.MediaStream) {}
        override fun onDataChannel(d: org.webrtc.DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
            // Remote audio track is played out automatically by the ADM.
            (transceiver.receiver.track() as? AudioTrack)?.setEnabled(true)
        }
        override fun onAddTrack(r: org.webrtc.RtpReceiver, s: Array<out org.webrtc.MediaStream>) {
            (r.track() as? AudioTrack)?.setEnabled(true)
        }
    }

    private fun createLegOffer(leg: PeerLeg, group: String, roster: String, sig: String) {
        leg.callId = java.util.UUID.randomUUID().toString()
        leg.pc?.createOffer(observerCreate { sdp ->
            leg.pc?.setLocalDescription(observerLog {}, sdp)
            signalLeg(
                leg, kind = "offer", sdp = sdp.description, name = Messenger.myHandle(),
                group = group, roster = roster, sig = sig,
            )
        }, audioConstraints())
    }

    private fun createLegAnswer(leg: PeerLeg) {
        leg.pc?.createAnswer(observerCreate { sdp ->
            leg.pc?.setLocalDescription(observerLog {}, sdp)
            signalLeg(leg, kind = "answer", sdp = sdp.description)
        }, audioConstraints())
    }

    private fun audioConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    // --- audio focus / routing ---

    private fun startAudioSession(context: Context) {
        if (audioManager != null) return // уже поднята предыдущей ножкой этой сессии
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        savedAudioMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = speaker
    }

    private fun stopAudioSession() {
        audioManager?.let {
            it.mode = savedAudioMode
            it.isSpeakerphoneOn = false
        }
        audioManager = null
    }

    // --- signaling helpers ---

    private fun signalLeg(
        leg: PeerLeg, kind: String, sdp: String = "", cand: String = "", mid: String = "", idx: Int = 0,
        name: String = "", group: String = "", roster: String = "", sig: String = "",
    ) {
        val payload = gson.toJson(
            CallSig(
                call = leg.callId, kind = kind, sdp = sdp, cand = cand, mid = mid, idx = idx, name = name,
                group = group, roster = roster, sig = sig,
            )
        )
        if (kind == "offer") { leg.offerJson = payload; leg.offerTries = 0 }
        val sent = Messenger.sendCallSignal(leg.peerId, payload, introducedKeys[leg.peerId]?.pubKey)
        // No socket at all (VPN down, or it died and has not reconnected yet).
        // Only the offer is worth retrying: the rest of the exchange happens on
        // a call that already reached the other side.
        if (!sent && kind == "offer") scheduleOfferRetry(leg)
    }

    // --- redelivery of the offer while we ring ---
    //
    // The relay has no queue: an offer sent while the callee's socket is dead
    // reaches nobody, and the server answers "callmiss". Their app reconnects
    // within a ping interval or two, so re-offering for a while turns a call
    // placed in that gap into a call that rings, instead of one that silently
    // never arrives.

    private fun onRingTimeout(leg: PeerLeg) {
        if (leg.legPhase == LegPhase.ENDED) return
        val soleAndOutgoing = phase == Phase.OUTGOING
        cleanupLeg(
            leg,
            endReasonIfSole = if (soleAndOutgoing) "Не отвечает" else "Пропущенный звонок",
        )
    }

    private fun armRingTimeout(leg: PeerLeg) {
        main.removeCallbacks(leg.ringTimeoutRunnable)
        main.postDelayed(leg.ringTimeoutRunnable, RING_TIMEOUT_MS)
    }

    private fun scheduleOfferRetry(leg: PeerLeg) {
        main.removeCallbacks(leg.offerRetryRunnable)
        main.postDelayed(leg.offerRetryRunnable, OFFER_RETRY_MS)
    }

    private fun retryOffer(leg: PeerLeg) {
        if (leg.legPhase == LegPhase.ENDED || leg.offerJson.isEmpty()) return
        if (leg.offerTries >= MAX_OFFER_TRIES) {
            cleanupLeg(leg, endReasonIfSole = "Собеседник не в сети")
            return
        }
        leg.offerTries++
        if (!Messenger.sendCallSignal(leg.peerId, leg.offerJson, introducedKeys[leg.peerId]?.pubKey)) {
            scheduleOfferRetry(leg)
        }
    }

    // --- background link service hooks ---
    //
    // Set by the service that holds the socket while the app is off screen. The
    // engine itself stays UI-agnostic: it only says "this started ringing" and
    // "there is nothing ringing any more". `othersCount` — сколько ЕЩЁ человек
    // в приглашении, кроме звонящего (0 для обычного 1:1 звонка).

    @Volatile var onIncomingCall: ((Long, String, Int) -> Unit)? = null
    @Volatile var onCallCleared: (() -> Unit)? = null

    /** Server could not hand our frame to anyone on the far end. */
    private fun onPeerOffline(peer: Long) = main.post {
        val leg = legs[peer] ?: return@post
        if (leg.lastOutgoing && leg.legPhase == LegPhase.CONNECTING) scheduleOfferRetry(leg)
    }

    private fun flushPendingIce(leg: PeerLeg) {
        leg.pendingRemoteIce.forEach { leg.pc?.addIceCandidate(it) }
        leg.pendingRemoteIce.clear()
    }

    private fun loadIce(): List<PeerConnection.IceServer> = try {
        kotlinx.coroutines.runBlocking { Messenger.fetchIceServers() }.map { s ->
            val b = PeerConnection.IceServer.builder(s.urls)
            if (!s.username.isNullOrEmpty()) b.setUsername(s.username)
            if (!s.credential.isNullOrEmpty()) b.setPassword(s.credential)
            b.createIceServer()
        }
    } catch (e: Exception) { emptyList() }

    // Запасного варианта нет НАМЕРЕННО.
    //
    // Раньше при недоступном списке серверов подставлялся публичный STUN
    // Google. Тогда политика становилась NOHOST, а она убирает только
    // host-кандидатов — srflx остаются, и это НАСТОЯЩИЙ публичный адрес
    // человека, потому что наше приложение исключено из собственного
    // туннеля. То есть звонок в «приватном» мессенджере раскрывал адрес и
    // собеседнику, и Google. Нет ретранслятора — нет звонка.

    private fun setPhase(p: Phase) {
        phase = p
    }

    /**
     * Закрыть ОДНУ ножку: её PeerConnection, запись в журнал, убрать из
     * ростера. Остальные ножки звонка не трогает — в mesh каждая независима.
     *
     * `endReasonIfSole` — текст для экрана, но ТОЛЬКО если это была последняя
     * ножка сессии (иначе человек продолжает разговор с остальными, и текст
     * вроде «Собеседник не в сети» про ОДНОГО из них был бы дезориентирующим
     * поверх ещё идущего разговора).
     */
    private fun cleanupLeg(leg: PeerLeg, endReasonIfSole: String?) {
        if (leg.legPhase == LegPhase.ENDED) return
        val talked = leg.connectedAt > 0L
        ChatPrefs.addCall(
            ChatPrefs.Call(
                peerId = leg.peerId,
                name = leg.peerName,
                dir = when {
                    // Отклонил сам — это не «пропустил». И снятая трубка,
                    // после которой связь не поднялась, тоже: человек ответил,
                    // а в журнале стояло «пропущенный».
                    leg.declined -> "declined"
                    phase == Phase.INCOMING && !talked && !leg.answered -> "missed"
                    leg.lastOutgoing -> "outgoing"
                    else -> "incoming"
                },
                ts = System.currentTimeMillis(),
                sec = if (talked) ((System.currentTimeMillis() - leg.connectedAt) / 1000).toInt() else 0,
                group = groupId,
                others = (legs.size - 1).coerceAtLeast(0),
            ),
        )
        leg.legPhase = LegPhase.ENDED
        main.removeCallbacks(leg.offerRetryRunnable)
        main.removeCallbacks(leg.ringTimeoutRunnable)
        main.removeCallbacks(leg.dropIfStillDownRunnable)
        try { leg.pc?.dispose() } catch (e: Exception) {}
        leg.pc = null
        leg.pendingRemoteIce.clear()
        legs.remove(leg.peerId)
        publishRoster()

        if (legs.isEmpty()) {
            if (endReasonIfSole != null) endReason = endReasonIfSole
            endSession(Phase.ENDED)
        }
    }

    /** Конец сессии целиком: все ножки уже должны быть закрыты (или их и не
     *  было) — здесь только общий сброс: аудио-устройство, ростер, groupId. */
    private fun endSession(end: Phase) {
        onCallCleared?.invoke()
        legs.values.toList().forEach { cleanupLeg(it, endReasonIfSole = null) }
        try { localTrack?.dispose() } catch (e: Exception) {}
        try { audioSource?.dispose() } catch (e: Exception) {}
        localTrack = null; audioSource = null
        stopAudioSession()
        muted = false; speaker = false; connectedAt = 0L
        groupId = ""; groupInitiatorId = 0L
        introducedKeys.clear()
        pendingRosterEntries = emptyList()
        pendingInviteOthers = 0
        phase = end
        // Auto-return to IDLE shortly after showing the ENDED state. When there
        // is a reason to read ("Собеседник не в сети"), hold it longer.
        val hold = if (endReason.isBlank()) 1500L else 3500L
        main.postDelayed({
            if (phase == Phase.ENDED) { phase = Phase.IDLE; endReason = "" }
        }, hold)
    }

    // --- SDP observer helpers ---

    private fun observerCreate(onOk: (SessionDescription) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = onOk(sdp)
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }

    private fun observerLog(onOk: () -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() = onOk()
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }

    data class CallSig(
        /** Id ОДНОЙ ножки (пары звонящих) — раньше был id звонка целиком. */
        val call: String = "",
        val kind: String = "",
        val sdp: String = "",
        val cand: String = "",
        val mid: String = "",
        val idx: Int = 0,
        val name: String = "",
        /** "" — обычный 1:1 звонок; иначе — id общей сессии группового звонка,
         *  одинаковый на всех его ножках. */
        val group: String = "",
        /** JSON [RosterEntry] — только в исходном приглашении (kind="offer" от
         *  инициатора новой группы). */
        val roster: String = "",
        /** Подпись инициатора над строкой [roster] (SHA256withRSA, см.
         *  [Messenger.signPayload]) — без нужды доверять серверу, что ростер
         *  подлинный. */
        val sig: String = "",
    )

    data class RosterEntry(val id: Long = 0, val name: String = "", val pubKey: String = "")
}
