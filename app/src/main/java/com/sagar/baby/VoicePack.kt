package com.sagar.baby

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Aapka awaaz pack: har fixed jawab ki ek recording.
 *
 * Recording 2 jagah se aa sakti hai:
 *  1. App ke "Awaaz Pack" screen se record / file chun kar (phone me save hoti hai)
 *  2. APK ke andar: app/src/main/assets/voice/<key>.mp3 (ya .m4a / .wav / .ogg)
 */
object VoicePack {

    class Phrase(val key: String, val text: String)

    val phrases: List<Phrase> = listOf(
        Phrase("ready", "Haan ji, bolo kya karna hai?"),
        Phrase("msg_sent", "Ho gaya! Maine message bhej diya. Ab aage bolo, kya karna hai?"),
        Phrase("msg_typed", "Message likh diya hai, bas send daba do."),
        Phrase("ask_who", "Kisko bhejna hai?"),
        Phrase("ask_text", "Kya message bhejna hai?"),
        Phrase("ask_call", "Kisko call karna hai?"),
        Phrase("calling", "Call laga diya."),
        Phrase("app_open", "Lo, khol diya!"),
        Phrase("searching", "Lo, dhoond diya!"),
        Phrase("camera", "Camera khol diya, smile karo!"),
        Phrase("photo_done", "Photo kheench li, gallery me save ho gayi."),
        Phrase("torch_on", "Torch jala di."),
        Phrase("torch_off", "Torch band kar di."),
        Phrase("volume_up", "Awaaz badha di."),
        Phrase("volume_down", "Awaaz kam kar di."),
        Phrase("alarm", "Alarm laga diya."),
        Phrase("timer", "Timer laga diya."),
        Phrase("no_contact", "Yeh contact nahi mila, naam ek baar phir bolo na."),
        Phrase("no_app", "Yeh app phone me nahi mila."),
        Phrase("no_chat", "Abhi koi WhatsApp chat khuli nahi hai. Kisko bhejna hai?"),
        Phrase("send_fail", "Message send nahi ho paya, ek baar check kar lo."),
        Phrase("not_understood", "Sorry, samajh nahi aaya. Ek baar phir bolo na."),
        Phrase("stop", "Theek hai, main chup. Zarurat ho to bula lena."),
        Phrase("greeting", "Main ekdum badhiya! Aap bolo, kya karna hai?"),
        Phrase("thanks", "Aapka swagat hai! Aur kuch?"),
        Phrase("who", "Main Baby hoon, aapki apni assistant. Jo bologe, woh ho jayega."),
        Phrase("cancel", "Theek hai, cancel kar diya."),
        Phrase("home", "Home screen pe aa gaye."),
    )

    private val exts = listOf("m4a", "mp3", "wav", "ogg", "aac", "amr", "3gp", "audio")

    fun dir(context: Context): File = File(context.filesDir, "voice").apply { mkdirs() }

    /** Phone me record / import ki hui file. */
    fun file(context: Context, key: String): File? =
        exts.map { File(dir(context), "$key.$it") }.firstOrNull { it.exists() && it.length() > 0 }

    /** APK ke andar wali file (assets/voice). */
    fun asset(context: Context, key: String): String? {
        val list = try { context.assets.list("voice")?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        return exts.map { "$key.$it" }.firstOrNull { it in list }?.let { "voice/$it" }
    }

    fun has(context: Context, key: String) = file(context, key) != null || asset(context, key) != null

    fun recordFile(context: Context, key: String): File {
        delete(context, key)
        return File(dir(context), "$key.m4a")
    }

    fun delete(context: Context, key: String) {
        exts.forEach { File(dir(context), "$key.$it").delete() }
    }

    /** Gallery / Files se chuni audio file ko is phrase ke liye copy karo. */
    fun import(context: Context, key: String, uri: Uri): Boolean = try {
        delete(context, key)
        val out = File(dir(context), "$key.audio")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        out.length() > 0
    } catch (_: Exception) {
        false
    }

    fun count(context: Context) = phrases.count { has(context, it.key) }
}
