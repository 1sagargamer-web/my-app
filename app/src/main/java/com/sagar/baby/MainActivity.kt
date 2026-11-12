package com.sagar.baby

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity(), BabyBus.Listener {

    companion object {
        const val EXTRA_ASK_PERMS = "askPerms"
        private const val REQ_PERMS = 10
    }

    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var partial: TextView
    private lateinit var chat: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var setupCard: View
    private lateinit var setupText: TextView
    private lateinit var setupButtons: LinearLayout
    private lateinit var switchOn: MaterialSwitch
    private lateinit var prefs: Prefs
    private var dotAnim: ObjectAnimator? = null
    private var ignoreSwitch = false

    private val dp get() = resources.displayMetrics.density

    private val allPerms: Array<String>
        get() {
            val p = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.CALL_PHONE,
            )
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 29) p.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return p.toTypedArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        orb = findViewById(R.id.orb)
        status = findViewById(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        partial = findViewById(R.id.partial)
        chat = findViewById(R.id.chat)
        chatScroll = findViewById(R.id.chatScroll)
        setupCard = findViewById(R.id.setupCard)
        setupText = findViewById(R.id.setupText)
        setupButtons = findViewById(R.id.setupButtons)
        switchOn = findViewById(R.id.switchOn)

        styleTitle()
        buildChips()

        switchOn.setOnCheckedChangeListener { _, checked ->
            if (ignoreSwitch) return@setOnCheckedChangeListener
            prefs.babyOn = checked
            if (checked) turnOn() else BabyService.stop(this)
        }
        orb.setOnClickListener { onOrbTap() }
        findViewById<View>(R.id.btnHelp).setOnClickListener { showHelp() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.btnVoice).setOnClickListener { startActivity(Intent(this, VoicePackActivity::class.java)) }

        dotAnim = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 1f, 0.25f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        if (savedInstanceState == null && (missingPerms().isNotEmpty() || intent.getBooleanExtra(EXTRA_ASK_PERMS, false))) {
            requestAllPermissions()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ASK_PERMS, false)) requestAllPermissions()
    }

    override fun onStart() {
        super.onStart()
        BabyBus.uiVisible = true
        BabyBus.add(this)
        chat.removeAllViews()
        BabyBus.history.forEach { addBubble(it.first, it.second, scroll = false) }
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        if (prefs.babyOn) turnOn()
        onMode(BabyBus.mode, BabyBus.note)
        updateSetupCard()
    }

    override fun onStop() {
        BabyBus.uiVisible = false
        BabyBus.remove(this)
        super.onStop()
    }

    override fun onDestroy() {
        dotAnim?.cancel()
        super.onDestroy()
    }

    private fun turnOn() {
        if (!has(Manifest.permission.RECORD_AUDIO)) {
            status.text = "Mic ki permission chahiye"
            return
        }
        BabyService.start(this)
    }

    private fun onOrbTap() {
        val s = BabyService.instance
        when {
            s == null -> { prefs.babyOn = true; setSwitch(true); turnOn() }
            s.paused -> s.togglePause()
            BabyBus.mode == BabyBus.Mode.LISTENING -> s.togglePause()
            else -> s.listenNow()
        }
    }

    private fun setSwitch(v: Boolean) {
        ignoreSwitch = true
        switchOn.isChecked = v
        ignoreSwitch = false
    }

    // --- BabyBus ----------------------------------------------------------------------------

    override fun onMode(mode: BabyBus.Mode, note: String) {
        val (text, color) = when (mode) {
            BabyBus.Mode.OFF -> "Baby OFF hai · gole ko dabao" to 0xFF6B6B8A.toInt()
            BabyBus.Mode.IDLE -> (note.ifBlank { "Taiyaar hai ✨" }) to 0xFFB388FF.toInt()
            BabyBus.Mode.LISTENING -> "Sun rahi hai… boliye 🎧" to 0xFF00E5FF.toInt()
            BabyBus.Mode.SPEAKING -> "Bol rahi hai 🔊" to 0xFFFF4FD8.toInt()
            BabyBus.Mode.PAUSED -> "⏸ ${note.ifBlank { "Ruki hui hai" }} · gola dabao" to 0xFFFFB74D.toInt()
            BabyBus.Mode.MEDIA -> "🎵 $note" to 0xFFFFB74D.toInt()
        }
        status.text = text
        statusDot.backgroundTintList = ColorStateList.valueOf(color)
        orb.mode = when (mode) {
            BabyBus.Mode.LISTENING -> OrbView.Mode.LISTENING
            BabyBus.Mode.SPEAKING -> OrbView.Mode.SPEAKING
            else -> OrbView.Mode.IDLE
        }
        setSwitch(mode != BabyBus.Mode.OFF || prefs.babyOn)
        if (mode != BabyBus.Mode.LISTENING) partial.text = ""
    }

    override fun onPartial(text: String) { partial.text = text }
    override fun onLevel(db: Float) { orb.setLevel(db) }
    override fun onChat(user: Boolean, text: String) { addBubble(user, text, scroll = true) }

    // --- UI ---------------------------------------------------------------------------------

    private fun styleTitle() {
        val t = findViewById<TextView>(R.id.title)
        t.post {
            val w = t.paint.measureText(t.text.toString())
            t.paint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(0xFF00E5FF.toInt(), 0xFFB388FF.toInt(), 0xFFFF4FD8.toInt()), null, Shader.TileMode.CLAMP,
            )
            t.invalidate()
        }
    }

    private fun buildChips() {
        val box = findViewById<LinearLayout>(R.id.chips)
        val items = listOf(
            "▶️  YouTube" to "YouTube kholo",
            "💬  WhatsApp" to "WhatsApp kholo",
            "🤳  Selfie" to "selfie lo",
            "📸  Photo" to "photo khicho",
            "🔦  Torch" to "torch jalao",
            "🌑  Torch off" to "torch band karo",
            "🕒  Time" to "time kya hua",
            "🔋  Battery" to "battery kitni hai",
            "😄  Joke" to "joke sunao",
        )
        for ((label, cmd) in items) {
            val tv = TextView(this).apply {
                text = label
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setBackgroundResource(R.drawable.chip_bg)
                setPadding((16 * dp).toInt(), (9 * dp).toInt(), (16 * dp).toInt(), (9 * dp).toInt())
                setOnClickListener {
                    val s = BabyService.instance
                    if (s == null) Toast.makeText(context, "Pehle Baby ON karo", Toast.LENGTH_SHORT).show() else s.runText(cmd)
                }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = (8 * dp).toInt()
            box.addView(tv, lp)
        }
    }

    private fun addBubble(user: Boolean, text: String, scroll: Boolean) {
        val tv = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFFFFFFFF.toInt())
            setLineSpacing(2 * dp, 1f)
            setBackgroundResource(if (user) R.drawable.bubble_user else R.drawable.bubble_bot)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            maxWidth = (resources.displayMetrics.widthPixels * 0.74).toInt()
            setTextIsSelectable(true)
            alpha = 0f
            translationY = 12 * dp
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (user) Gravity.END else Gravity.START
            topMargin = (8 * dp).toInt()
        }
        chat.addView(tv, lp)
        tv.animate().alpha(1f).translationY(0f).setDuration(if (scroll) 260 else 0).start()
        while (chat.childCount > 40) chat.removeViewAt(0)
        if (scroll) chatScroll.post { chatScroll.smoothScrollTo(0, chat.bottom) }
    }

    // --- Setup / Permissions ----------------------------------------------------------------

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun missingPerms() = allPerms.filter { !has(it) }

    private fun requestAllPermissions() {
        val missing = missingPerms()
        if (missing.isEmpty()) return
        val sp = getSharedPreferences("baby", MODE_PRIVATE)
        val askedBefore = sp.getBoolean("askedPerms", false)
        val blocked = askedBefore && missing.none { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
        if (blocked) {
            // Android dobara popup nahi dikhata - App settings kholo
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
        }
        sp.edit().putBoolean("askedPerms", true).apply()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateSetupCard()
        if (prefs.babyOn) turnOn()
    }

    private fun ignoringBattery(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun updateSetupCard() {
        setupButtons.removeAllViews()
        val lines = ArrayList<String>()
        val names = missingPerms().mapNotNull {
            when (it) {
                Manifest.permission.RECORD_AUDIO -> "Mic"
                Manifest.permission.CAMERA -> "Camera"
                Manifest.permission.READ_CONTACTS -> "Contacts"
                Manifest.permission.SEND_SMS -> "SMS"
                Manifest.permission.CALL_PHONE -> "Call"
                Manifest.permission.POST_NOTIFICATIONS -> "Notification"
                else -> null
            }
        }
        if (names.isNotEmpty()) {
            lines.add("⚠️ Permission chahiye: " + names.joinToString(", "))
            addSetupButton("Permission do") { requestAllPermissions() }
        }
        val acc = AutoSend.isEnabled(this)
        if (!acc) {
            lines.add("🛡️ 'Background Control' ON karo: WhatsApp message khud jayega, kisi bhi app ke upar se kaam hoga, aur camera ke paas light chamkegi.")
            addSetupButton("Background Control ON karo") { showAccessibilityHelp() }
            if (!Settings.canDrawOverlays(this)) {
                addSetupButton("Ya: 'Display over other apps' do") {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        }
        if (!ignoringBattery()) {
            lines.add("🔋 Battery saver Baby ko band kar sakta hai.")
            addSetupButton("Battery restriction hatao") { askBattery() }
        }
        setupCard.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        setupText.text = lines.joinToString("\n")
    }

    private fun addSetupButton(text: String, onClick: () -> Unit) {
        val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            isAllCaps = false
            setTextColor(0xFFFFE0B2.toInt())
            strokeColor = ColorStateList.valueOf(0x66FFB74D)
            cornerRadius = (14 * dp).toInt()
            setOnClickListener { onClick() }
        }
        setupButtons.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    @SuppressLint("BatteryLife")
    private fun askBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun showAccessibilityHelp() {
        AlertDialog.Builder(this)
            .setTitle("Background Control")
            .setMessage(
                "Isse Baby:\n• WhatsApp message khud Send karegi\n• 'isko message karo' bolne par khuli chat me bhejegi\n" +
                    "• Kisi bhi app ke upar se apps/camera kholegi\n• Camera ke paas premium light dikhayegi\n\n" +
                    "1. 'Settings kholo' dabao\n2. 'Installed apps' / 'Downloaded apps' me 'Baby AI – Background Control' chuno\n" +
                    "3. ON karo aur 'Allow' dabao\n\n" +
                    "'Restricted setting' likha aaye to (Android 13+):\nApp info → upar ⋮ (3 dots) → 'Allow restricted settings' → phir step 1.",
            )
            .setPositiveButton("Settings kholo") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNeutralButton("App info") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
            .setNegativeButton("Baad me", null)
            .show()
    }

    private fun showHelp() {
        val examples = listOf(
            "📱 Apps" to "\"YouTube kholo\" • \"Instagram\" • \"Free Fire chalao\"",
            "💬 WhatsApp" to "\"Rahul ko message bhejo ki main 5 baje aaunga\"\n\"WhatsApp kholo aur Mummy ko good night bhejo\"\nKhuli chat me: \"Isko message karo ki hello\"",
            "✉️ SMS" to "\"Papa ko SMS bhejo ki ghar aa gaya\"",
            "📞 Call" to "\"Papa ko call karo\" • \"Mummy ko phone lagao\"",
            "📸 Camera" to "\"Camera kholo aur photo click karo\" • \"Selfie lo\"",
            "▶️ YouTube" to "\"YouTube pe Arijit Singh ke gaane chalao\"",
            "🔦 Torch / 🔊 Awaaz" to "\"Torch jalao\" • \"Awaaz badhao\" • \"Mute karo\"",
            "⏰ Alarm" to "\"Subah 7 baje ka alarm lagao\" • \"5 minute ka timer\"",
            "🕒 Jaankari" to "\"Time kya hua\" • \"Aaj ki date\" • \"Battery kitni hai\"",
            "🌐 Search / Rasta" to "\"Google pe cricket score search karo\" • \"Delhi ka rasta dikhao\"",
            "⏸ Rokna" to "\"Chup\" bolo, ya notification me 'Roko'",
        )
        AlertDialog.Builder(this)
            .setTitle("Baby se kya bol sakte ho")
            .setMessage(examples.joinToString("\n\n") { "${it.first}\n${it.second}" })
            .setPositiveButton("Theek hai", null)
            .show()
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
        }
        fun label(t: String) = TextView(this).apply {
            text = t
            setTextColor(ContextCompat.getColor(context, R.color.text_dim))
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }
        fun sw(t: String, v: Boolean) = MaterialSwitch(this).apply { text = t; isChecked = v }

        val cc = EditText(this).apply { setText(prefs.countryCode); inputType = InputType.TYPE_CLASS_NUMBER }
        val wa = sw("Message WhatsApp se bhejo (off = SMS)", prefs.useWhatsApp)
        val media = sw("Video/gaana chalte waqt bhi suno", prefs.listenDuringMedia)
        val beep = sw("Mic ki 'beep' awaaz band rakho", prefs.muteBeep)
        val glow = sw("Camera ke paas chamakti light", prefs.glow)
        val en = sw("Sirf English me suno", prefs.language == "en-IN")

        val sp = getSharedPreferences("baby", MODE_PRIVATE)
        fun seek(value: Float) = SeekBar(this).apply { max = 150; progress = ((value - 0.5f) * 100).toInt().coerceIn(0, 150) }
        val pitch = seek(sp.getFloat("ttsPitch", 1f))
        val rate = seek(sp.getFloat("ttsRate", 1f))
        val voiceBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Phone ki awaaz chuno (jab recording na ho)"
            isAllCaps = false
            setOnClickListener { pickVoice() }
        }

        box.addView(label("Country code (India = 91)")); box.addView(cc)
        listOf(wa, media, beep, glow, en).forEach { box.addView(it) }
        box.addView(label("Awaaz ki pitch (moti ← → patli)")); box.addView(pitch)
        box.addView(label("Bolne ki speed")); box.addView(rate)
        box.addView(voiceBtn)

        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                prefs.countryCode = cc.text.toString()
                prefs.useWhatsApp = wa.isChecked
                prefs.listenDuringMedia = media.isChecked
                prefs.muteBeep = beep.isChecked
                prefs.glow = glow.isChecked
                prefs.language = if (en.isChecked) "en-IN" else "hi-IN"
                sp.edit()
                    .putFloat("ttsPitch", 0.5f + pitch.progress / 100f)
                    .putFloat("ttsRate", 0.5f + rate.progress / 100f)
                    .apply()
                val speaker = (application as BabyApp).speaker
                speaker.applyVoiceSettings()
                Glow.refresh(this)
                speaker.say("Theek hai, settings save ho gayi.", null)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickVoice() {
        val speaker = (application as BabyApp).speaker
        val voices = speaker.indianVoices()
        if (voices.isEmpty()) {
            Toast.makeText(this, "Koi Indian awaaz nahi mili. Settings → Text-to-speech me Google voice download karo.", Toast.LENGTH_LONG).show()
            return
        }
        val sp = getSharedPreferences("baby", MODE_PRIVATE)
        val current = voices.indexOfFirst { it.name == sp.getString("ttsVoice", null) }
        val names = voices.mapIndexed { i, v -> "Awaaz ${i + 1}  (${v.locale.displayLanguage}, ${v.name.substringAfterLast('-').take(10)})" }
        AlertDialog.Builder(this)
            .setTitle("Awaaz chuno (sunke dekho)")
            .setSingleChoiceItems(names.toTypedArray(), current) { _, which ->
                sp.edit().putString("ttsVoice", voices[which].name).apply()
                speaker.applyVoiceSettings()
                speaker.speak("Haan ji, bolo kya karna hai?")
            }
            .setPositiveButton("Done", null)
            .show()
    }
}
