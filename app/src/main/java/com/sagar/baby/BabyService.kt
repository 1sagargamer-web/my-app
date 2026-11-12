package com.sagar.baby

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sagar.baby.core.Roman

/**
 * Baby ka dil: background me chalta rehta hai, har baat sunta hai aur kaam karta hai -
 * chahe aap WhatsApp me ho, YouTube me ho ya home screen par.
 */
class BabyService : Service(), VoiceListener.Callback {

    companion object {
        const val ACTION_STOP = "com.sagar.baby.STOP"
        const val ACTION_LISTEN = "com.sagar.baby.LISTEN"
        const val ACTION_PAUSE = "com.sagar.baby.PAUSE"
        private const val CHANNEL = "baby_listen"
        private const val NOTIF_ID = 7

        @Volatile var instance: BabyService? = null
            private set

        fun start(ctx: Context) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, BabyService::class.java))
            } catch (_: Exception) { }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BabyService::class.java))
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var voice: VoiceListener
    private lateinit var assistant: Assistant
    private lateinit var audio: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val speaker get() = (application as BabyApp).speaker

    var paused = false
        private set
    private var forceOnce = false
    private var micHold = false
    private var errorStreak = 0
    private var languageFallbackTried = false
    private var mutedStreams: List<Int> = emptyList()

    private val loop = Runnable { tick() }
    private val unmuteRunnable = Runnable { unmuteBeep() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        restoreStreamsFromCrash()

        createChannel()
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            // Android ne background se mic service shuru karne nahi di - app kholne par chalegi
            stopSelf()
            return
        }

        instance = this
        voice = VoiceListener(this, this)
        assistant = Assistant(this, DeviceActions(this), ContactFinder(this), prefs)
        assistant.onAsyncReply = { respond(it) }

        BabyBus.setMode(BabyBus.Mode.IDLE)
        Glow.refresh(this)
        respond(Reply("Haan ji, bolo kya karna hai?", "ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (instance == null) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.babyOn = false
                stopSelf()
            }
            ACTION_LISTEN -> listenNow()
            ACTION_PAUSE -> togglePause()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (instance === this) {
            voice.destroy()
            speaker.stop()
        }
        unmuteBeep()
        instance = null
        BabyBus.setMode(BabyBus.Mode.OFF)
        Glow.hide()
        super.onDestroy()
    }

    // --- Controls (screen / notification se) ------------------------------------------------

    /** Abhi turant suno (gaana chal raha ho tab bhi). */
    fun listenNow() {
        paused = false
        forceOnce = true
        speaker.stop()
        if (voice.isListening) voice.stop()
        schedule(50)
        updateNotification()
    }

    fun togglePause() {
        paused = !paused
        if (paused) {
            voice.stop()
            unmuteBeep()
            BabyBus.setMode(BabyBus.Mode.PAUSED, "Ruki hui hai")
        } else {
            schedule(100)
        }
        updateNotification()
    }

    /** Voice pack record karte waqt mic chhod do. */
    fun holdMic(hold: Boolean) {
        micHold = hold
        if (hold) { voice.stop(); speaker.stop(); BabyBus.setMode(BabyBus.Mode.PAUSED, "Recording chal rahi hai") }
        else schedule(400)
    }

    /** Screen ke button se command (bina bole). */
    fun runText(text: String) {
        BabyBus.chat(true, text)
        respond(assistant.handleText(text))
    }

    // --- Sunne ka loop ----------------------------------------------------------------------

    private fun schedule(ms: Long) {
        handler.removeCallbacks(loop)
        handler.postDelayed(loop, ms)
    }

    private fun tick() {
        if (paused || micHold) return
        if (speaker.isSpeaking) { schedule(400); return }
        if (voice.isListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            BabyBus.setMode(BabyBus.Mode.PAUSED, "Mic ki permission chahiye")
            return
        }
        val m = audio.mode
        if (m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE) {
            BabyBus.setMode(BabyBus.Mode.MEDIA, "Call chal rahi hai")
            schedule(2000)
            return
        }
        if (audio.isMusicActive && !prefs.listenDuringMedia && !forceOnce) {
            BabyBus.setMode(BabyBus.Mode.MEDIA, "Video/gaana chal raha hai - notification me 'Bolo' dabao")
            schedule(1500)
            return
        }
        if (!voice.isAvailable) {
            BabyBus.setMode(BabyBus.Mode.PAUSED, "Google voice service nahi mili (Google app update karo)")
            schedule(10_000)
            return
        }
        forceOnce = false
        muteBeep()
        voice.start(prefs.language)
        BabyBus.setMode(BabyBus.Mode.LISTENING)
    }

    override fun onListeningStarted() {
        BabyBus.setMode(BabyBus.Mode.LISTENING)
        handler.removeCallbacks(unmuteRunnable)
        handler.postDelayed(unmuteRunnable, 450)
    }

    override fun onPartial(text: String) = BabyBus.partial(Roman.readable(text))

    override fun onRms(db: Float) = BabyBus.level(db)

    override fun onResults(results: List<String>) {
        unmuteBeep()
        errorStreak = 0
        BabyBus.partial("")
        val background = !BabyBus.uiVisible
        val reply = try {
            assistant.handle(results, background)
        } catch (e: Exception) {
            Reply("Kuch gadbad ho gayi: ${e.message}")
        }
        if (reply == null) {
            // Background me samajh na aane wali baat - chup-chaap aage suno
            BabyBus.setMode(BabyBus.Mode.IDLE)
            schedule(150)
            return
        }
        BabyBus.chat(true, Roman.readable(results.first()))
        respond(reply)
    }

    override fun onListenError(code: Int) {
        unmuteBeep()
        if (paused || micHold) return
        BabyBus.setMode(BabyBus.Mode.IDLE)
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                errorStreak = 0
                schedule(150)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                BabyBus.setMode(BabyBus.Mode.PAUSED, "Mic ki permission chahiye")
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                BabyBus.setMode(BabyBus.Mode.IDLE, "Internet check karo...")
                schedule(4000)
            }
            12, 13 -> {
                if (!languageFallbackTried) { languageFallbackTried = true; prefs.language = "en-IN" }
                schedule(800)
            }
            else -> {
                errorStreak++
                voice.reset()
                schedule(minOf(500L * errorStreak, 5000L))
            }
        }
    }

    // --- Jawab ------------------------------------------------------------------------------

    fun respond(reply: Reply) {
        handler.removeCallbacks(loop)
        if (voice.isListening) voice.stop()
        unmuteBeep()
        BabyBus.chat(false, reply.text)
        if (reply.stopListening) {
            paused = true
            updateNotification()
        }
        if (reply.speak) {
            BabyBus.setMode(BabyBus.Mode.SPEAKING)
            speaker.say(reply.text, reply.clip) {
                try { reply.afterSpeech?.invoke() } catch (_: Exception) { }
                if (paused) BabyBus.setMode(BabyBus.Mode.PAUSED, "Ruki hui hai") else BabyBus.setMode(BabyBus.Mode.IDLE)
                schedule(250)
            }
        } else {
            schedule(300)
        }
        try { reply.action?.invoke() } catch (e: Exception) { BabyBus.chat(false, "Error: ${e.message}") }
    }

    // --- "Beep" ki awaaz band (Google mic har baar beep karta hai) ---------------------------

    private fun muteBeep() {
        if (!prefs.muteBeep || mutedStreams.isNotEmpty()) return
        val streams = mutableListOf(AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION)
        if (!audio.isMusicActive) streams.add(AudioManager.STREAM_MUSIC)
        mutedStreams = streams.filter { s ->
            try {
                if (!audio.isStreamMute(s)) { audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0); true } else false
            } catch (_: Exception) { false }
        }
        getSharedPreferences("baby", MODE_PRIVATE).edit().putString("muted", mutedStreams.joinToString(",")).apply()
        handler.removeCallbacks(unmuteRunnable)
        handler.postDelayed(unmuteRunnable, 1500)
    }

    private fun unmuteBeep() {
        handler.removeCallbacks(unmuteRunnable)
        if (mutedStreams.isEmpty()) return
        mutedStreams.forEach { s -> try { audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) { } }
        mutedStreams = emptyList()
        getSharedPreferences("baby", MODE_PRIVATE).edit().remove("muted").apply()
    }

    /** Agar pichhli baar app beech me band hua tha to awaaz wapas chalu karo. */
    private fun restoreStreamsFromCrash() {
        val sp = getSharedPreferences("baby", MODE_PRIVATE)
        val s = sp.getString("muted", null) ?: return
        s.split(",").mapNotNull { it.toIntOrNull() }.forEach {
            try { audio.adjustStreamVolume(it, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) { }
        }
        sp.edit().remove("muted").apply()
    }

    // --- Notification -----------------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Baby background", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Baby background me sun rahi hai"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun pi(action: String, req: Int): PendingIntent =
        PendingIntent.getService(
            this, req, Intent(this, BabyService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_baby)
            .setContentTitle(if (paused) "Baby ruki hui hai" else "Baby ON hai ✨")
            .setContentText(if (paused) "'Chalu' dabao aur phir bolo" else "Bolo, kya karna hai - Baby sun rahi hai")
            .setColor(0xFF7C4DFF.toInt())
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "🎤 Bolo", pi(ACTION_LISTEN, 1))
            .addAction(0, if (paused) "▶ Chalu" else "⏸ Roko", pi(ACTION_PAUSE, 2))
            .addAction(0, "✕ Band", pi(ACTION_STOP, 3))
            .build()
    }

    private fun updateNotification() {
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) { }
    }
}
