package com.sagar.baby

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.io.File

/** Apni awaaz me Baby ke jawab record karo ya files chuno. */
class VoicePackActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var countText: TextView
    private val dp get() = resources.displayMetrics.density
    private val speaker get() = (application as BabyApp).speaker

    private var recorder: MediaRecorder? = null
    private var recordingKey: String? = null
    private var importKey: String? = null

    private val pickOne = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = importKey ?: return@registerForActivityResult
        if (uri != null) {
            val ok = VoicePack.import(this, key, uri)
            Toast.makeText(this, if (ok) "Lag gayi ✅" else "File nahi chali", Toast.LENGTH_SHORT).show()
            render()
        }
    }

    private val pickMany = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        var matched = 0
        for (uri in uris) {
            val name = displayName(uri)?.substringBeforeLast('.')?.trim()?.lowercase() ?: continue
            val phrase = VoicePack.phrases.firstOrNull { it.key == name } ?: continue
            if (VoicePack.import(this, phrase.key, uri)) matched++
        }
        Toast.makeText(this, "$matched files lag gayi (naam se match karke)", Toast.LENGTH_LONG).show()
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_main)
            fitsSystemWindows = true
            setPadding((18 * dp).toInt(), (12 * dp).toInt(), (18 * dp).toInt(), 0)
        }
        root.addView(TextView(this).apply {
            text = "🎙️ Awaaz Pack"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = "Har line ko apni awaaz me record karo. Baby wahi jawab aapki awaaz me degi. " +
                "Jo record nahi hogi, woh phone ki awaaz me bolegi."
            setTextColor(ContextCompat.getColor(context, R.color.text_dim))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        })
        countText = TextView(this).apply {
            setTextColor(0xFF00E5FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        root.addView(countText)

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt()) }
        tools.addView(button("📂 Saari files ek saath") { pickMany.launch(arrayOf("audio/*")) }, weightLp())
        tools.addView(button("📋 File ke naam copy") { copyNames() }, weightLp(start = 8))
        root.addView(tools)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list); isVerticalScrollBarEnabled = false }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        render()
    }

    override fun onPause() {
        super.onPause()
        stopRecording()
    }

    private fun weightLp(start: Int = 0) =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (start * dp).toInt() }

    private fun button(text: String, onClick: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { onClick() }
        }

    private fun render() {
        list.removeAllViews()
        countText.text = "${VoicePack.count(this)} / ${VoicePack.phrases.size} lines aapki awaaz me"
        for (p in VoicePack.phrases) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.glass)
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            }
            card.addView(TextView(this).apply {
                text = "“${p.text}”"
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
            val src = when {
                VoicePack.file(this, p.key) != null -> "✅ Aapki awaaz"
                VoicePack.asset(this, p.key) != null -> "📦 Pack me hai"
                else -> "🤖 Phone ki awaaz"
            }
            card.addView(TextView(this).apply {
                text = "$src   ·   file: ${p.key}.mp3"
                setTextColor(ContextCompat.getColor(context, R.color.text_dim))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, (2 * dp).toInt(), 0, (4 * dp).toInt())
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val rec = button(if (recordingKey == p.key) "■ Stop" else "⏺ Record") {
                if (recordingKey == p.key) stopRecording() else startRecording(p.key)
            }
            if (recordingKey == p.key) rec.setTextColor(0xFFFF5252.toInt())
            row.addView(rec, weightLp())
            row.addView(button("▶ Suno") { speaker.say(p.text, p.key) }, weightLp(6))
            row.addView(button("📁") { importKey = p.key; pickOne.launch(arrayOf("audio/*")) }, weightLp(6))
            row.addView(button("🗑") { VoicePack.delete(this, p.key); render() }, weightLp(6))
            card.addView(row)
            list.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * dp).toInt()
            })
        }
    }

    private fun startRecording(key: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 3)
            return
        }
        stopRecording()
        speaker.stop()
        BabyService.instance?.holdMic(true)
        val out: File = VoicePack.recordFile(this, key)
        try {
            val r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordingKey = key
            Toast.makeText(this, "Boliye… phir ■ Stop dabaiye", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            out.delete()
            BabyService.instance?.holdMic(false)
            Toast.makeText(this, "Recording nahi ho payi: ${e.message}", Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun stopRecording() {
        val r = recorder ?: return
        val key = recordingKey
        try { r.stop() } catch (_: Exception) { key?.let { VoicePack.delete(this, it) } }
        try { r.release() } catch (_: Exception) { }
        recorder = null
        recordingKey = null
        BabyService.instance?.holdMic(false)
        render()
    }

    private fun copyNames() {
        val text = VoicePack.phrases.joinToString("\n") { "${it.key}.mp3  →  ${it.text}" }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Baby voice files", text))
        Toast.makeText(this, "File ke naam copy ho gaye", Toast.LENGTH_SHORT).show()
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
}
