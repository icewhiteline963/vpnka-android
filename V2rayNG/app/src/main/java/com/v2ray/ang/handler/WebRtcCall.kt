package com.v2ray.ang.handler

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
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
 * One-to-one end-to-end voice calls over WebRTC.
 *
 * The media (DTLS-SRTP) is encrypted peer-to-peer by WebRTC itself; on top of
 * that, every signaling frame (SDP offer/answer, ICE candidate, hangup) is
 * sealed to the peer's key and relayed blindly by the server over the messenger
 * WebSocket — the server never sees SDP or media. ICE servers (STUN, and TURN
 * when the backend hands out credentials) come from `/app/messenger/ice`.
 *
 * State is exposed as Compose-observable fields so the call UI reacts directly.
 */
object CallManager {

    enum class Phase { IDLE, OUTGOING, INCOMING, ACTIVE, ENDED }

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

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var peerId by mutableStateOf(0L)
        private set
    var peerName by mutableStateOf("")
        private set
    var muted by mutableStateOf(false)
        private set
    var speaker by mutableStateOf(false)
        private set
    /** Wall-clock ms when the call connected; 0 until ACTIVE (for the timer). */
    var connectedAt by mutableStateOf(0L)
        private set
    /** Why the last call ended, when there is something to say (shown on the
     *  call screen). Empty for an ordinary hang-up. */
    var endReason by mutableStateOf("")
        private set

    private val gson = Gson()
    private val main = Handler(Looper.getMainLooper())

    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var savedAudioMode = AudioManager.MODE_NORMAL

    private var callId: String = ""
    private var iceServers: List<PeerConnection.IceServer> = emptyList()
    // ICE candidates that arrive before the remote description is set are held
    // and flushed once setRemoteDescription completes (WebRTC rejects them
    // otherwise).
    private val pendingRemoteIce = ArrayList<IceCandidate>()
    private var remoteReady = false
    private var appContext: Context? = null

    /** Wire the messenger's incoming call-signal callback to us. Call once. */
    fun attach() {
        Messenger.onCallSignal = { from, json -> onSignal(from, json) }
        Messenger.onCallMiss = { peer -> onPeerOffline(peer) }
    }

    // --- outgoing / incoming lifecycle ---

    /** Place a call to a contact (caller side). */
    fun startCall(context: Context, contactId: Long, name: String) {
        if (phase != Phase.IDLE && phase != Phase.ENDED) return
        appContext = context.applicationContext
        callId = java.util.UUID.randomUUID().toString()
        endReason = ""
        offerJson = ""
        offerTries = 0
        setState(Phase.OUTGOING, contactId, name)
        Thread {
            iceServers = loadIce()
            main.post {
                if (phase != Phase.OUTGOING) return@post
                createPeer(context, caller = true)
                createOffer()
            }
        }.start()
    }

    /** Accept a ringing call (callee side). The offer is already stored. */
    fun accept(context: Context) {
        if (phase != Phase.INCOMING) return
        appContext = context.applicationContext
        onCallCleared?.invoke()
        val offer = pendingOffer ?: run { hangup(); return }
        Thread {
            iceServers = loadIce()
            main.post {
                if (phase != Phase.INCOMING) return@post
                createPeer(context, caller = false)
                pc?.setRemoteDescription(observerLog("setRemote(offer)") {
                    remoteReady = true
                    flushPendingIce()
                    createAnswer()
                }, offer)
                pendingOffer = null
            }
        }.start()
    }

    /** Reject a ringing call without answering. */
    fun decline() {
        if (peerId != 0L) signal(kind = "hangup")
        cleanup(Phase.ENDED)
    }

    /** Hang up an active/outgoing call. */
    fun hangup() {
        if (peerId != 0L) signal(kind = "hangup")
        cleanup(Phase.ENDED)
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

    private var pendingOffer: SessionDescription? = null

    private fun onSignal(from: Long, json: String) {
        val sig = try { gson.fromJson(json, CallSig::class.java) } catch (e: Exception) { return }
        when (sig.kind) {
            "offer" -> main.post {
                // Busy: already on/placing a call → tell them, ignore.
                if (phase == Phase.ACTIVE || phase == Phase.OUTGOING ||
                    (phase == Phase.INCOMING && from != peerId)
                ) {
                    Messenger.sendCallSignal(from, gson.toJson(CallSig(call = sig.call, kind = "busy")))
                    return@post
                }
                callId = sig.call
                pendingOffer = SessionDescription(SessionDescription.Type.OFFER, sig.sdp)
                setState(Phase.INCOMING, from, sig.name.ifBlank { "Контакт $from" })
                // The screen may be off and the app not on it — the background
                // link service turns this into a ringing notification.
                onIncomingCall?.invoke(from, peerName)
            }
            "answer" -> main.post {
                if (phase != Phase.OUTGOING || from != peerId) return@post
                pc?.setRemoteDescription(observerLog("setRemote(answer)") {
                    remoteReady = true
                    flushPendingIce()
                }, SessionDescription(SessionDescription.Type.ANSWER, sig.sdp))
            }
            "ice" -> main.post {
                if (from != peerId) return@post
                val cand = IceCandidate(sig.mid, sig.idx, sig.cand)
                if (remoteReady) pc?.addIceCandidate(cand) else pendingRemoteIce.add(cand)
            }
            "hangup" -> main.post { if (from == peerId) cleanup(Phase.ENDED) }
            "busy" -> main.post { if (from == peerId && phase == Phase.OUTGOING) cleanup(Phase.ENDED) }
        }
    }

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

    private fun createPeer(context: Context, caller: Boolean) {
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
        val rtc = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceTransportsType =
                if (hasTurn) PeerConnection.IceTransportsType.RELAY
                else PeerConnection.IceTransportsType.NOHOST
        }
        pc = factory?.createPeerConnection(rtc, pcObserver) ?: return
        val f = factory ?: return
        val src = f.createAudioSource(MediaConstraints())
        audioSource = src
        val track = f.createAudioTrack("audio0", src).apply { setEnabled(true) }
        localTrack = track
        pc?.addTrack(track, listOf("stream0"))
    }

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            signal(kind = "ice", cand = c.sdp, mid = c.sdpMid ?: "", idx = c.sdpMLineIndex)
        }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            main.post {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED ->
                        if (phase != Phase.ACTIVE) { phase = Phase.ACTIVE; connectedAt = System.currentTimeMillis() }
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.CLOSED,
                    PeerConnection.PeerConnectionState.DISCONNECTED -> if (phase == Phase.ACTIVE) cleanup(Phase.ENDED)
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

    private fun createOffer() {
        pc?.createOffer(observerCreate { sdp ->
            pc?.setLocalDescription(observerLog("setLocal(offer)") {}, sdp)
            signal(kind = "offer", sdp = sdp.description, name = Messenger.myHandle())
        }, audioConstraints())
    }

    private fun createAnswer() {
        pc?.createAnswer(observerCreate { sdp ->
            pc?.setLocalDescription(observerLog("setLocal(answer)") {}, sdp)
            signal(kind = "answer", sdp = sdp.description)
        }, audioConstraints())
    }

    private fun audioConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    // --- audio focus / routing ---

    private fun startAudioSession(context: Context) {
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

    private fun signal(
        kind: String, sdp: String = "", cand: String = "", mid: String = "", idx: Int = 0, name: String = "",
    ) {
        val to = peerId
        if (to == 0L) return
        val payload = gson.toJson(
            CallSig(call = callId, kind = kind, sdp = sdp, cand = cand, mid = mid, idx = idx, name = name)
        )
        if (kind == "offer") { offerJson = payload; offerTries = 0 }
        val sent = Messenger.sendCallSignal(to, payload)
        // No socket at all (VPN down, or it died and has not reconnected yet).
        // Only the offer is worth retrying: the rest of the exchange happens on
        // a call that already reached the other side.
        if (!sent && kind == "offer") scheduleOfferRetry()
    }

    // --- redelivery of the offer while we ring ---
    //
    // The relay has no queue: an offer sent while the callee's socket is dead
    // reaches nobody, and the server answers "callmiss". Their app reconnects
    // within a ping interval or two, so re-offering for a while turns a call
    // placed in that gap into a call that rings, instead of one that silently
    // never arrives.

    private var offerJson: String = ""
    private var offerTries = 0
    private val offerRetry = Runnable { retryOffer() }
    private val ringTimeout = Runnable {
        if (phase == Phase.OUTGOING || phase == Phase.INCOMING) {
            endReason = if (phase == Phase.OUTGOING) "Не отвечает" else "Пропущенный звонок"
            cleanup(Phase.ENDED)
        }
    }

    private fun scheduleOfferRetry() {
        main.removeCallbacks(offerRetry)
        main.postDelayed(offerRetry, OFFER_RETRY_MS)
    }

    private fun retryOffer() {
        if (phase != Phase.OUTGOING || peerId == 0L || offerJson.isEmpty()) return
        if (offerTries >= MAX_OFFER_TRIES) {
            endReason = "Собеседник не в сети"
            cleanup(Phase.ENDED)
            return
        }
        offerTries++
        if (!Messenger.sendCallSignal(peerId, offerJson)) scheduleOfferRetry()
    }

    // --- background link service hooks ---
    //
    // Set by the service that holds the socket while the app is off screen. The
    // engine itself stays UI-agnostic: it only says "this started ringing" and
    // "there is nothing ringing any more".

    @Volatile var onIncomingCall: ((Long, String) -> Unit)? = null
    @Volatile var onCallCleared: (() -> Unit)? = null

    /** Server could not hand our frame to anyone on the far end. */
    private fun onPeerOffline(peer: Long) = main.post {
        if (phase == Phase.OUTGOING && peer == peerId) scheduleOfferRetry()
    }

    private fun flushPendingIce() {
        pendingRemoteIce.forEach { pc?.addIceCandidate(it) }
        pendingRemoteIce.clear()
    }

    private fun loadIce(): List<PeerConnection.IceServer> = try {
        kotlinx.coroutines.runBlocking { Messenger.fetchIceServers() }.map { s ->
            val b = PeerConnection.IceServer.builder(s.urls)
            if (!s.username.isNullOrEmpty()) b.setUsername(s.username)
            if (!s.credential.isNullOrEmpty()) b.setPassword(s.credential)
            b.createIceServer()
        }.ifEmpty { defaultIce() }
    } catch (e: Exception) { defaultIce() }

    private fun defaultIce() = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    )

    private fun setState(p: Phase, id: Long, name: String) {
        phase = p; peerId = id; peerName = name
        main.removeCallbacks(ringTimeout)
        if (p == Phase.OUTGOING || p == Phase.INCOMING) {
            main.postDelayed(ringTimeout, RING_TIMEOUT_MS)
        }
    }

    private fun cleanup(end: Phase) {
        onCallCleared?.invoke()
        main.removeCallbacks(offerRetry)
        main.removeCallbacks(ringTimeout)
        offerJson = ""; offerTries = 0
        try { pc?.dispose() } catch (e: Exception) {}
        try { localTrack?.dispose() } catch (e: Exception) {}
        try { audioSource?.dispose() } catch (e: Exception) {}
        pc = null; localTrack = null; audioSource = null
        pendingRemoteIce.clear(); remoteReady = false; pendingOffer = null
        stopAudioSession()
        muted = false; speaker = false; connectedAt = 0L
        phase = end
        peerId = 0L; peerName = ""
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

    private fun observerLog(tag: String, onOk: () -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() = onOk()
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }

    data class CallSig(
        val call: String = "",
        val kind: String = "",
        val sdp: String = "",
        val cand: String = "",
        val mid: String = "",
        val idx: Int = 0,
        val name: String = "",
    )
}
