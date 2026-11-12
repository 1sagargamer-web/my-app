package com.sagar.baby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity(), VoiceListener.Callback {

    companion object {
        const val EXTRA_SENT = "sent"
        private const val REQ_PERMS = 10
    }

    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var partial: TextView
    private lateinit var chat: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var setupCard: View
    private lateinit var setupText: TextView
    private lateinit var btnPerms: MaterialButton
    private lateinit var btnAccess: MaterialButton
    private lateinit var switchContinuous: MaterialSwitch

    private lateinit var prefs: Prefs
    private lateinit var actions: DeviceActions
    private lateinit var assistant: Assistant
    private lateinit var voice: VoiceListener

    private val handler = Handler(Looper.getMainLooper())
    private val speaker get() = (application as BabyApp).speaker
    private var isResumedNow = false
    private var paused = false // "chup" bolne ya gola dabane par sunna ruk jaata hai
    private var errorStreak = 0
    private var languageFallbackTried = false

    private val listenRunnable = Runnable { startListening() }

    private val allPerms: Array<String>
        get() {
            val p = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.CALL_PHONE,
            )
            if (Build.VERSION.SDK_INT < 29) p.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return p.toTypedArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        orb = findViewById(R.id.orb)
        status = findViewById(R.id.status)
        partial = findViewById(R.id.partial)
        chat = findViewById(R.id.chat)
        chatScroll = findViewById(R.id.chatScroll)
        setupCard = findViewById(R.id.setupCard)
        setupText = findViewById(R.id.setupText)
        btnPerms = findViewById(R.id.btnPerms)
        btnAccess = findViewById(R.id.btnAccess)
        switchContinuous = findViewById(R.id.switchContinuous)

        prefs = Prefs(this)
        actions = DeviceActions(this)
        assistant = Assistant(this, actions, ContactFinder(this), prefs)
        voice = VoiceListener(this, this)

        switchContinuous.isChecked = prefs.continuous
        switchContinuous.setOnCheckedChangeListener { _, checked ->
            prefs.continuous = checked
            if (checked) { paused = false; scheduleListen(200) }
        }

        orb.setOnClickListener { onOrbTap() }
        status.setOnClickListener { onOrbTap() }
        findViewById<View>(R.id.btnHelp).setOnClickListener { showHelp() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
        btnPerms.setOnClickListener { requestAllPermissions() }
        btnAccess.setOnClickListener { showAccessibilityHelp() }

        if (savedInstanceState == null) {
            if (missingPerms().isNotEmpty()) requestAllPermissions()
            respond(Reply("नमस्ते! मैं Baby हूँ। बोलिए, क्या करना है?"))
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SENT, false) == true) {
            intent.removeExtra(EXTRA_SENT)
            respond(Reply("Message भेज दिया गया।"))
        }
    }

    override fun onResume() {
        super.onResume()
        isResumedNow = true
        updateSetupCard()
        if (prefs.continuous && !paused && !speaker.isSpeaking) scheduleListen(600)
        else if (!voice.isListening && !speaker.isSpeaking) setIdle()
    }

    override fun onPause() {
        super.onPause()
        isResumedNow = false
        handler.removeCallbacks(listenRunnable)
        voice.stop()
        orb.mode = OrbView.Mode.IDLE
    }

    override fun onDestroy() {
        voice.destroy()
        super.onDestroy()
    }

    // --- Sunna ------------------------------------------------------------------------------

    private fun onOrbTap() {
        if (voice.isListening) {
            voice.stop()
            paused = true
            setIdle()
        } else {
            paused = false
            speaker.stop()
            startListening()
        }
    }

    private fun scheduleListen(delay: Long) {
        handler.removeCallbacks(listenRunnable)
        handler.postDelayed(listenRunnable, delay)
    }

    private fun startListening() {
        if (!isResumedNow || voice.isListening || speaker.isSpeaking) return
        if (!actions.has(Manifest.permission.RECORD_AUDIO)) {
            status.text = "Mic ki permission chahiye (upar button dabaiye)"
            updateSetupCard()
            return
        }
        if (!voice.isAvailable) {
            status.text = "Google voice service nahi mili. Play Store se 'Google' app install/update karein."
            return
        }
        partial.text = ""
        voice.start(prefs.language)
        orb.mode = OrbView.Mode.LISTENING
        status.text = "🎤 Boliye..."
    }

    private fun setIdle() {
        orb.mode = OrbView.Mode.IDLE
        status.text = if (paused || !prefs.continuous) "Gole ko chhoo kar boliye" else "..."
    }

    private fun afterTurn(delay: Long = 300) {
        if (isResumedNow && prefs.continuous && !paused) scheduleListen(delay) else setIdle()
    }

    override fun onListeningStarted() {
        orb.mode = OrbView.Mode.LISTENING
        status.text = "🎤 Boliye..."
    }

    override fun onPartial(text: String) {
        partial.text = text
    }

    override fun onRms(db: Float) {
        orb.setLevel(db)
    }

    override fun onResults(results: List<String>) {
        errorStreak = 0
        partial.text = ""
        addBubble(results.first(), user = true)
        val reply = try {
            assistant.handle(results)
        } catch (e: Exception) {
            Reply("कुछ गड़बड़ हो गई: ${e.message}")
        }
        respond(reply)
    }

    override fun onListenError(code: Int) {
        orb.mode = OrbView.Mode.IDLE
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                errorStreak = 0
                afterTurn(250)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                status.text = "Mic ki permission chahiye"
                updateSetupCard()
            }
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                status.text = "Internet check kariye..."
                afterTurn(4000)
            }
            12, 13 -> { // language supported nahi / download nahi hui
                if (!languageFallbackTried) {
                    languageFallbackTried = true
                    prefs.language = "en-IN"
                }
                afterTurn(800)
            }
            else -> {
                errorStreak++
                voice.reset()
                afterTurn(minOf(600L * errorStreak, 5000L))
            }
        }
    }

    // --- Jawab ------------------------------------------------------------------------------

    private fun respond(reply: Reply) {
        handler.removeCallbacks(listenRunnable)
        if (voice.isListening) voice.stop()
        addBubble(reply.text, user = false)
        if (reply.stopListening) paused = true
        orb.mode = OrbView.Mode.SPEAKING
        status.text = "🔊"
        speaker.speak(reply.text) {
            if (orb.mode == OrbView.Mode.SPEAKING) orb.mode = OrbView.Mode.IDLE
            try { reply.afterSpeech?.invoke() } catch (_: Exception) { }
            afterTurn()
        }
        try {
            reply.action?.invoke()
        } catch (e: Exception) {
            addBubble("Error: ${e.message}", user = false)
        }
    }

    private fun addBubble(text: String, user: Boolean) {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setBackgroundResource(if (user) R.drawable.bubble_user else R.drawable.bubble_bot)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            maxWidth = (resources.displayMetrics.widthPixels * 0.78).toInt()
            setTextIsSelectable(true)
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (user) Gravity.END else Gravity.START
            topMargin = (6 * dp).toInt()
        }
        chat.addView(tv, lp)
        while (chat.childCount > 40) chat.removeViewAt(0)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // --- Setup / Permissions ----------------------------------------------------------------

    private fun missingPerms() = allPerms.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestAllPermissions() {
        val missing = missingPerms()
        if (missing.isEmpty()) return
        val permanentlyDenied = missing.any {
            !ActivityCompat.shouldShowRequestPermissionRationale(this, it) && prefsAskedBefore()
        }
        if (permanentlyDenied) {
            // Android dobara popup nahi dikhata - App settings kholo
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
        }
        getSharedPreferences("baby", MODE_PRIVATE).edit().putBoolean("askedPerms", true).apply()
    }

    private fun prefsAskedBefore() = getSharedPreferences("baby", MODE_PRIVATE).getBoolean("askedPerms", false)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateSetupCard()
        if (actions.has(Manifest.permission.RECORD_AUDIO)) afterTurn(400)
    }

    private fun updateSetupCard() {
        val missing = missingPerms()
        val names = missing.mapNotNull {
            when (it) {
                Manifest.permission.RECORD_AUDIO -> "Mic"
                Manifest.permission.CAMERA -> "Camera"
                Manifest.permission.READ_CONTACTS -> "Contacts"
                Manifest.permission.SEND_SMS -> "SMS"
                Manifest.permission.CALL_PHONE -> "Call"
                else -> null
            }
        }
        val accessOff = !AutoSend.isEnabled(this) && !prefs.hideAccessNag
        val lines = ArrayList<String>()
        if (names.isNotEmpty()) lines.add("⚠ Permission chahiye: " + names.joinToString(", "))
        if (accessOff) lines.add("💬 WhatsApp message khud bhejne ke liye 'Auto-send' chalu karein.")
        setupCard.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        setupText.text = lines.joinToString("\n")
        btnPerms.visibility = if (names.isNotEmpty()) View.VISIBLE else View.GONE
        btnAccess.visibility = if (accessOff) View.VISIBLE else View.GONE
    }

    private fun showAccessibilityHelp() {
        AlertDialog.Builder(this)
            .setTitle("WhatsApp Auto-send")
            .setMessage(
                "Isse Baby WhatsApp me message likh kar khud 'Send' daba degi.\n\n" +
                    "1. Neeche 'Settings kholo' dabaiye\n" +
                    "2. 'Installed apps' / 'Downloaded apps' me 'Baby AI – WhatsApp Auto Send' chuniye\n" +
                    "3. Use ON kariye aur 'Allow' dabaiye\n\n" +
                    "Agar ON na ho aur 'Restricted setting' likha aaye (Android 13+):\n" +
                    "Phone Settings → Apps → Baby AI → upar ⋮ (3 dots) → 'Allow restricted settings' → phir dobara step 1.",
            )
            .setPositiveButton("Settings kholo") { _, _ -> actions.openAccessibilitySettings() }
            .setNeutralButton("App info") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
            .setNegativeButton("Nahi chahiye") { _, _ -> prefs.hideAccessNag = true; updateSetupCard() }
            .show()
    }

    private fun showHelp() {
        val examples = listOf(
            "📱 Apps" to "\"YouTube kholo\"  •  \"Instagram open karo\"  •  \"Free Fire chalao\"",
            "▶️ YouTube / Gaane" to "\"YouTube pe Arijit Singh ke gaane chalao\"  •  \"Kesariya song bajao\"",
            "💬 Message" to "\"Rahul ko message bhejo ki main 5 baje aaunga\"  •  \"Mummy ko WhatsApp pe good night bhejo\"  •  \"Papa ko SMS bhejo ki ghar aa gaya\"",
            "📞 Call" to "\"Papa ko call karo\"  •  \"Mummy ko phone lagao\"",
            "📸 Camera" to "\"Camera kholo aur photo click karo\"  •  \"Selfie lo\"  •  \"Photo khicho\"",
            "🔦 Torch" to "\"Torch jalao\"  •  \"Torch band karo\"",
            "🔊 Awaaz" to "\"Awaaz badhao\"  •  \"Volume kam karo\"  •  \"Mute karo\"",
            "⏰ Alarm / Timer" to "\"Subah 7 baje ka alarm lagao\"  •  \"5 minute ka timer lagao\"",
            "🕒 Jaankari" to "\"Time kya hua\"  •  \"Aaj ki date\"  •  \"Battery kitni hai\"",
            "🌐 Search / Rasta" to "\"Google pe cricket score search karo\"  •  \"Delhi ka rasta dikhao\"",
            "⚙️ Settings" to "\"WiFi kholo\"  •  \"Bluetooth on karo\"  •  \"Home screen pe jao\"",
            "😄 Masti" to "\"Joke sunao\"  •  \"Tumhara naam kya hai\"  •  \"Chup\" (sunna band)",
        )
        val text = examples.joinToString("\n\n") { "${it.first}\n${it.second}" }
        AlertDialog.Builder(this)
            .setTitle("Baby se kya bol sakte ho")
            .setMessage(text)
            .setPositiveButton("Theek hai", null)
            .show()
    }

    private fun showSettings() {
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), 0)
        }
        val ccLabel = TextView(this).apply { text = "Country code (India = 91)" }
        val cc = EditText(this).apply {
            setText(prefs.countryCode)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val wa = MaterialSwitch(this).apply {
            text = "Message WhatsApp se bhejo (off = SMS)"
            isChecked = prefs.useWhatsApp
        }
        val en = MaterialSwitch(this).apply {
            text = "English me suno (off = Hindi/Hinglish)"
            isChecked = prefs.language == "en-IN"
        }
        box.addView(ccLabel)
        box.addView(cc)
        box.addView(wa)
        box.addView(en)
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                prefs.countryCode = cc.text.toString()
                prefs.useWhatsApp = wa.isChecked
                prefs.language = if (en.isChecked) "en-IN" else "hi-IN"
                prefs.hideAccessNag = false
                updateSetupCard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
