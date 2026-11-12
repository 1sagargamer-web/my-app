package com.sagar.baby

import android.Manifest
import android.content.Context
import com.sagar.baby.core.Command
import com.sagar.baby.core.CommandParser
import com.sagar.baby.core.Platform
import com.sagar.baby.core.SettingsPage
import com.sagar.baby.core.Translit
import com.sagar.baby.core.VolumeAction
import java.util.Calendar

/**
 * Baby ka jawab.
 * [action] bolna shuru hote hi chalta hai (jaise app kholna), [afterSpeech] bolne ke baad.
 */
class Reply(
    val text: String,
    val action: (() -> Unit)? = null,
    val afterSpeech: (() -> Unit)? = null,
    val stopListening: Boolean = false,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("baby", Context.MODE_PRIVATE)
    var continuous: Boolean
        get() = sp.getBoolean("continuous", true)
        set(v) = sp.edit().putBoolean("continuous", v).apply()
    var countryCode: String
        get() = sp.getString("cc", "91") ?: "91"
        set(v) = sp.edit().putString("cc", v.filter { it.isDigit() }.ifEmpty { "91" }).apply()
    var useWhatsApp: Boolean
        get() = sp.getBoolean("wa", true)
        set(v) = sp.edit().putBoolean("wa", v).apply()
    var hideAccessNag: Boolean
        get() = sp.getBoolean("hideAccess", false)
        set(v) = sp.edit().putBoolean("hideAccess", v).apply()
    var language: String
        get() = sp.getString("lang", "hi-IN") ?: "hi-IN"
        set(v) = sp.edit().putString("lang", v).apply()
}

class Assistant(
    private val context: Context,
    private val actions: DeviceActions,
    private val contacts: ContactFinder,
    private val prefs: Prefs,
) {

    private sealed class Pending {
        class NeedText(val contact: Contact, val platform: Platform) : Pending()
        class NeedContact(val text: String?, val platform: Platform) : Pending()
        class NeedCallContact : Pending()
        class Confirm(
            val contact: Contact, val others: List<Contact>, val text: String?,
            val platform: Platform, val isCall: Boolean,
        ) : Pending()
    }

    private var pending: Pending? = null

    fun handle(alternatives: List<String>): Reply {
        val text = alternatives.firstOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return Reply("कुछ सुनाई नहीं दिया, फिर से बोलिए।")
        val p = pending
        if (p != null) {
            pending = null
            handlePending(p, text, alternatives)?.let { return it }
        }
        val cmd = alternatives.asSequence()
            .map { CommandParser.parse(it) }
            .firstOrNull { it !is Command.Unknown && it !is Command.WebSearch }
            ?: CommandParser.parse(text)
        return execute(cmd)
    }

    // ---------------------------------------------------------------------------------------

    private fun handlePending(p: Pending, text: String, alternatives: List<String>): Reply? {
        val no = CommandParser.isNo(text)
        when (p) {
            is Pending.NeedText -> {
                if (no && text.split(" ").size <= 3) return Reply("ठीक है, message cancel कर दिया।")
                return deliver(p.contact, text, p.platform)
            }
            is Pending.NeedContact -> {
                if (no) return Reply("ठीक है, cancel कर दिया।")
                return messageTo(stripKo(text), p.text, p.platform)
            }
            is Pending.NeedCallContact -> {
                if (no) return Reply("ठीक है, call cancel।")
                return callTo(stripKo(text))
            }
            is Pending.Confirm -> {
                if (CommandParser.isYes(text)) {
                    return when {
                        p.isCall -> doCall(p.contact)
                        p.text == null -> askText(p.contact, p.platform)
                        else -> deliver(p.contact, p.text, p.platform)
                    }
                }
                if (no) {
                    val next = p.others.firstOrNull()
                    if (next != null) {
                        pending = Pending.Confirm(next, p.others.drop(1), p.text, p.platform, p.isCall)
                        return Reply("तो क्या ${next.name} को? हाँ या ना बोलिए।")
                    }
                    return Reply("ठीक है, कुछ नहीं भेजा गया।")
                }
                // कुछ और बोला -> नया command मानो
                return null
            }
        }
    }

    private fun stripKo(s: String): String {
        val words = s.trim().split(Regex("\\s+")).toMutableList()
        while (words.size > 1 && Translit.norm(words.last()) in setOf("ko", "ke", "ka", "se")) words.removeAt(words.size - 1)
        return words.joinToString(" ")
    }

    // ---------------------------------------------------------------------------------------

    private fun execute(cmd: Command): Reply = when (cmd) {
        is Command.OpenApp -> openApp(cmd.name)
        is Command.YouTubeSearch -> Reply("YouTube पर \"${cmd.query}\" खोजा जा रहा है।", { actions.youtubeSearch(cmd.query) })
        is Command.WebSearch -> Reply("Google पर \"${cmd.query}\" खोजा जा रहा है।", { actions.webSearch(cmd.query) })
        is Command.Navigate -> Reply("${cmd.place} का रास्ता दिखाया जा रहा है।", { actions.navigate(cmd.place) })
        is Command.SendMessage -> {
            if (cmd.contact == null) {
                pending = Pending.NeedContact(cmd.text, cmd.platform)
                Reply("किसको message भेजना है?")
            } else {
                messageTo(cmd.contact, cmd.text, cmd.platform)
            }
        }
        is Command.Call -> {
            if (cmd.contact == null) {
                pending = Pending.NeedCallContact()
                Reply("किसको call करना है?")
            } else {
                callTo(cmd.contact)
            }
        }
        is Command.TakePhoto -> takePhoto(cmd.front, cmd.autoCapture)
        is Command.Torch -> {
            val ok = actions.torch(cmd.on)
            Reply(if (!ok) "इस phone में torch नहीं मिली।" else if (cmd.on) "Torch जल गई।" else "Torch बंद हो गई।")
        }
        is Command.Volume -> when (cmd.action) {
            VolumeAction.UP -> { actions.unmuteIfNeeded(); actions.volume(VolumeAction.UP); Reply("आवाज़ बढ़ा दी गई।") }
            VolumeAction.DOWN -> { actions.volume(VolumeAction.DOWN); Reply("आवाज़ कम कर दी गई।") }
            VolumeAction.MAX -> { actions.unmuteIfNeeded(); actions.volume(VolumeAction.MAX); Reply("आवाज़ पूरी कर दी गई।") }
            VolumeAction.MUTE -> Reply("ठीक है, आवाज़ बंद की जा रही है।", afterSpeech = { actions.volume(VolumeAction.MUTE) })
        }
        is Command.Alarm -> alarm(cmd)
        is Command.Timer -> {
            if (cmd.seconds <= 0) {
                Reply("कितने मिनट का timer? जैसे बोलिए '5 मिनट का timer लगाओ'।")
            } else {
                val ok = actions.setTimer(cmd.seconds)
                Reply(if (ok) "${duration(cmd.seconds)} का timer लग गया।" else "Timer वाला app नहीं मिला।")
            }
        }
        is Command.OpenSettings -> {
            val name = when (cmd.page) {
                SettingsPage.WIFI -> "WiFi"
                SettingsPage.BLUETOOTH -> "Bluetooth"
                SettingsPage.LOCATION -> "Location"
                SettingsPage.DISPLAY -> "Display"
                SettingsPage.MAIN -> "Phone"
            }
            Reply("$name की settings खुल रही हैं।", { actions.openSettings(cmd.page) })
        }
        Command.TellTime -> {
            val c = Calendar.getInstance()
            val h = c.get(Calendar.HOUR_OF_DAY)
            val m = c.get(Calendar.MINUTE)
            if (m == 0) Reply("अभी ${period(h)} ${h12(h)} बजे हैं।") else Reply("अभी ${period(h)} ${h12(h)} बजकर $m मिनट हुए हैं।")
        }
        Command.TellDate -> {
            val c = Calendar.getInstance()
            val days = listOf("रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार")
            val months = listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
            Reply(
                "आज ${days[c.get(Calendar.DAY_OF_WEEK) - 1]}, ${c.get(Calendar.DAY_OF_MONTH)} " +
                    "${months[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)} है।",
            )
        }
        Command.Battery -> {
            val (pct, charging) = actions.battery()
            Reply("बैटरी $pct प्रतिशत है" + if (charging) ", और charge हो रही है।" else "।")
        }
        Command.GoHome -> Reply("Home screen पर चलते हैं।", { actions.goHome() })
        Command.Help -> Reply(
            "आप मुझसे बोल सकते हैं: YouTube खोलो, राहुल को message भेजो कि मैं आ रहा हूँ, " +
                "मम्मी को call करो, photo खींचो, selfie लो, torch जलाओ, सुबह 7 बजे का alarm लगाओ, " +
                "आवाज़ बढ़ाओ, battery कितनी है, या दिल्ली का रास्ता दिखाओ।",
        )
        Command.Stop -> Reply("ठीक है। ज़रूरत हो तो गोले को छूकर बुलाइए।", stopListening = true)
        Command.Greeting -> Reply(
            listOf("नमस्ते! बताइए, क्या करना है?", "मैं बढ़िया हूँ! आप बताइए, क्या काम है?", "हैलो! बोलिए, क्या करूँ?").random(),
        )
        Command.WhoAreYou -> Reply("मैं Baby हूँ, आपकी अपनी AI assistant। आप जो बोलें, वो काम हो जाएगा।")
        Command.Joke -> Reply(JOKES.random())
        Command.Thanks -> Reply(listOf("आपका स्वागत है! और कुछ?", "कोई बात नहीं! और बताइए?").random())
        is Command.Unknown -> unknown(cmd.text)
    }

    private fun unknown(text: String): Reply {
        // Sirf app ka naam bola ho ("Instagram") to khol do
        if (text.trim().split(Regex("\\s+")).size <= 3) {
            actions.apps.find(text)?.let { app -> return Reply("${app.label} खुल रहा है।", { actions.launch(app) }) }
        }
        return Reply("माफ़ कीजिए, समझ नहीं आया: \"$text\"। Help बोलकर देखिए क्या-क्या हो सकता है।")
    }

    // --- Apps / Camera ----------------------------------------------------------------------

    private fun openApp(name: String): Reply {
        val n = Translit.norm(name.replace(" ", ""))
        if (n in setOf("phone", "fon", "dialer", "dailar", "dialpad")) return Reply("Dialer खुल रहा है।", { actions.openDialer() })
        if (n in setOf("camera", "kamera", "kaimra", "kemra")) return takePhoto(front = false, auto = false)
        if (n in setOf("alarm", "alarms")) return Reply("Alarm खुल रहे हैं।", { actions.openAlarms() })
        val app = actions.apps.find(name) ?: return Reply("\"$name\" नाम का कोई app नहीं मिला।")
        return Reply("${app.label} खुल रहा है।", { actions.launch(app) })
    }

    private fun takePhoto(front: Boolean, auto: Boolean): Reply {
        if (!actions.has(Manifest.permission.CAMERA)) {
            actions.ensure(Manifest.permission.CAMERA)
            return Reply("Camera की permission दीजिए, फिर दोबारा बोलिए।")
        }
        val text = when {
            front && auto -> "Selfie camera खुल रहा है, स्माइल कीजिए!"
            auto -> "Camera खुल रहा है, तीन सेकंड में photo खिंचेगी।"
            else -> "Camera खुल रहा है।"
        }
        return Reply(text, { actions.openCamera(front, auto) })
    }

    // --- Contacts / Messages / Calls --------------------------------------------------------

    private sealed class Found {
        object NoPermission : Found()
        object NotFound : Found()
        class Sure(val contact: Contact) : Found()
        class Unsure(val contact: Contact, val others: List<Contact>) : Found()
    }

    private fun resolve(name: String): Found {
        val digits = name.filter { it.isDigit() }
        if (digits.length >= 7) return Found.Sure(Contact(digits, digits))
        if (!contacts.hasPermission()) {
            actions.ensure(Manifest.permission.READ_CONTACTS)
            return Found.NoPermission
        }
        val matches = contacts.find(name)
        val best = matches.firstOrNull() ?: return Found.NotFound
        if (best.score < 0.6) return Found.NotFound
        val close = matches.drop(1).filter { it.score >= 0.6 && it.score >= best.score - 0.04 }
        return if (best.score >= 0.85 && close.isEmpty()) {
            Found.Sure(best.contact)
        } else {
            Found.Unsure(best.contact, close.map { it.contact })
        }
    }

    private fun messageTo(name: String, text: String?, platform: Platform): Reply =
        when (val f = resolve(name)) {
            Found.NoPermission -> Reply("Contacts देखने की permission दीजिए, फिर दोबारा बोलिए।")
            Found.NotFound -> Reply("\"$name\" नाम का कोई contact नहीं मिला।")
            is Found.Unsure -> {
                pending = Pending.Confirm(f.contact, f.others, text, platform, isCall = false)
                Reply("क्या ${f.contact.name} को भेजना है? हाँ या ना बोलिए।")
            }
            is Found.Sure -> if (text.isNullOrBlank()) askText(f.contact, platform) else deliver(f.contact, text, platform)
        }

    private fun askText(contact: Contact, platform: Platform): Reply {
        pending = Pending.NeedText(contact, platform)
        return Reply("${contact.name} को क्या message भेजना है?")
    }

    private fun deliver(contact: Contact, text: String, platform: Platform): Reply {
        var p = platform
        if (p == Platform.ANY) p = if (prefs.useWhatsApp) Platform.WHATSAPP else Platform.SMS
        if (p == Platform.WHATSAPP && actions.whatsappPackage() == null) p = Platform.SMS

        return when (p) {
            Platform.WHATSAPP -> {
                val auto = AutoSend.isEnabled(context)
                val msg = if (auto) {
                    "${contact.name} को WhatsApp पर भेजा जा रहा है: $text"
                } else {
                    "${contact.name} का WhatsApp chat खुल रहा है, message लिखा हुआ है, बस send दबाइए।"
                }
                Reply(msg, { actions.sendWhatsApp(contact.number, text, prefs.countryCode, auto) })
            }
            Platform.TELEGRAM -> Reply("Telegram खुल रहा है, ${contact.name} की chat चुन लीजिए।", { actions.shareTelegram(text) })
            else -> {
                if (!actions.has(Manifest.permission.SEND_SMS)) {
                    actions.ensure(Manifest.permission.SEND_SMS)
                    Reply("SMS भेजने की permission दीजिए, फिर दोबारा बोलिए।")
                } else if (actions.sendSms(contact.number, text)) {
                    Reply("${contact.name} को SMS भेज दिया गया: $text")
                } else {
                    Reply("SMS सीधे नहीं जा सका, SMS app खोला गया है।")
                }
            }
        }
    }

    private fun callTo(name: String): Reply =
        when (val f = resolve(name)) {
            Found.NoPermission -> Reply("Contacts देखने की permission दीजिए, फिर दोबारा बोलिए।")
            Found.NotFound -> Reply("\"$name\" नाम का कोई contact नहीं मिला।")
            is Found.Unsure -> {
                pending = Pending.Confirm(f.contact, f.others, null, Platform.ANY, isCall = true)
                Reply("क्या ${f.contact.name} को call करना है? हाँ या ना बोलिए।")
            }
            is Found.Sure -> doCall(f.contact)
        }

    private fun doCall(contact: Contact): Reply =
        Reply("${contact.name} को call लगाया जा रहा है।", { actions.call(contact.number) })

    // --- Alarm / Time helpers ---------------------------------------------------------------

    private fun alarm(cmd: Command.Alarm): Reply {
        if (cmd.hour < 0) return Reply("किस टाइम का alarm लगाना है? जैसे बोलिए 'सुबह 7 बजे का alarm लगाओ'।")
        var hour = cmd.hour
        if (!cmd.explicitPeriod) {
            val now = Calendar.getInstance()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val a = hour % 12
            val candidates = listOf(a, a + 12)
            hour = candidates.firstOrNull { it * 60 + cmd.minute > nowMin } ?: a
        }
        val ok = actions.setAlarm(hour, cmd.minute)
        if (!ok) return Reply("Alarm वाला app नहीं मिला।")
        val mm = if (cmd.minute == 0) "" else ":" + cmd.minute.toString().padStart(2, '0')
        return Reply("${period(hour)} ${h12(hour)}$mm बजे का alarm लग गया।")
    }

    private fun period(h: Int) = when (h) {
        in 4..11 -> "सुबह"
        in 12..15 -> "दोपहर"
        in 16..19 -> "शाम"
        else -> "रात"
    }

    private fun h12(h: Int) = if (h % 12 == 0) 12 else h % 12

    private fun duration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add("$h घंटे")
        if (m > 0) parts.add("$m मिनट")
        if (s > 0) parts.add("$s सेकंड")
        return parts.joinToString(" ")
    }

    companion object {
        private val JOKES = listOf(
            "टीचर: बताओ, सबसे ज़्यादा बर्फ़ कहाँ गिरती है? स्टूडेंट: जी, गोले वाले की दुकान पर!",
            "पापा: बेटा, फ़ोन छोड़ो और पढ़ाई करो। बेटा: पापा, पढ़ाई भी तो फ़ोन पर ही हो रही है, YouTube पर!",
            "डॉक्टर: आपको आराम की ज़रूरत है। मरीज़: पर डॉक्टर साहब, मैं तो दिन भर सोता हूँ! डॉक्टर: तो अब रात को भी सोइए।",
            "पप्पू: मम्मी, मैं बड़ा होकर पायलट बनूँगा। मम्मी: पहले छत पर से कपड़े तो उतार ला, ऊँचाई से डर लगता है तुझे!",
            "दोस्त: तू इतना मोबाइल क्यों चलाता है? मैं: क्योंकि मोबाइल मुझे नहीं चलाता!",
        )
    }
}
