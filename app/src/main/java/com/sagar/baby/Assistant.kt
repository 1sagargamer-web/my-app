package com.sagar.baby

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.sagar.baby.core.Command
import com.sagar.baby.core.CommandParser
import com.sagar.baby.core.Platform
import com.sagar.baby.core.Roman
import com.sagar.baby.core.SettingsPage
import com.sagar.baby.core.Translit
import com.sagar.baby.core.VolumeAction
import java.util.Calendar

/**
 * Baby ka jawab.
 * [clip] = voice pack ki recording ka naam (ho to aapki awaaz me bajegi).
 * [action] bolna shuru hote hi chalta hai, [afterSpeech] bolne ke baad. [speak] false = sirf screen par likho.
 */
class Reply(
    val text: String,
    val clip: String? = null,
    val action: (() -> Unit)? = null,
    val afterSpeech: (() -> Unit)? = null,
    val stopListening: Boolean = false,
    val speak: Boolean = true,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("baby", Context.MODE_PRIVATE)
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun setB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var babyOn: Boolean get() = b("babyOn", true); set(v) = setB("babyOn", v)
    var countryCode: String
        get() = sp.getString("cc", "91") ?: "91"
        set(v) = sp.edit().putString("cc", v.filter { it.isDigit() }.ifEmpty { "91" }).apply()
    var useWhatsApp: Boolean get() = b("wa", true); set(v) = setB("wa", v)
    var language: String
        get() = sp.getString("lang", "hi-IN") ?: "hi-IN"
        set(v) = sp.edit().putString("lang", v).apply()
    var listenDuringMedia: Boolean get() = b("media", false); set(v) = setB("media", v)
    var muteBeep: Boolean get() = b("muteBeep", true); set(v) = setB("muteBeep", v)
    var glow: Boolean get() = b("glow", true); set(v) = setB("glow", v)
    var greeted: Boolean get() = b("greeted", false); set(v) = setB("greeted", v)
}

class Assistant(
    private val context: Context,
    private val actions: DeviceActions,
    private val contacts: ContactFinder,
    private val prefs: Prefs,
) {

    /** Baad me aane wala jawab (jaise WhatsApp message sach me chala gaya). */
    var onAsyncReply: ((Reply) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    private sealed class Pending {
        class NeedText(val contact: Contact, val platform: Platform) : Pending()
        class NeedContact(val text: String?, val platform: Platform) : Pending()
        class NeedCallContact : Pending()
    }

    private var pending: Pending? = null
    private var pendingTime = 0L

    private val currentChat = Contact("is chat", CommandParser.CURRENT_CHAT)

    /**
     * [background] = Baby ki screen band hai. Tab samajh na aane wali baat par chup rehti hai (null).
     */
    fun handle(alternatives: List<String>, background: Boolean): Reply? {
        val text = alternatives.firstOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return null

        val p = pending
        if (p != null && System.currentTimeMillis() - pendingTime < 45_000) {
            pending = null
            handlePending(p, text)?.let { return it }
        }
        pending = null

        var cmds: List<Command> = emptyList()
        for (alt in alternatives) {
            val c = CommandParser.parseAll(alt, strict = background)
            if (c.none { it is Command.Unknown || it is Command.WebSearch }) { cmds = c; break }
        }
        if (cmds.isEmpty()) cmds = CommandParser.parseAll(text, strict = background)

        if (cmds.all { it is Command.Unknown }) {
            if (background) return null
            return unknown(text)
        }
        val replies = cmds.filter { it !is Command.Unknown }.map { execute(it) }
        return combine(replies)
    }

    /** Button se seedha command chalana (jaise "YouTube kholo"). */
    fun handleText(text: String): Reply = handle(listOf(text), background = false) ?: unknown(text)

    private fun combine(replies: List<Reply>): Reply {
        if (replies.size == 1) return replies[0]
        val spoken = replies.filter { it.speak }
        return Reply(
            text = replies.joinToString("  ") { it.text },
            clip = spoken.lastOrNull()?.clip?.takeIf { spoken.size == 1 },
            action = {
                replies.forEachIndexed { i, r ->
                    if (i == 0) r.action?.invoke() else main.postDelayed({ r.action?.invoke() }, 1300L * i)
                }
            },
            afterSpeech = { replies.forEach { it.afterSpeech?.invoke() } },
            stopListening = replies.any { it.stopListening },
            speak = spoken.isNotEmpty(),
        )
    }

    private fun ask(p: Pending, text: String, clip: String): Reply {
        pending = p
        pendingTime = System.currentTimeMillis()
        return Reply(text, clip)
    }

    // ---------------------------------------------------------------------------------------

    private fun handlePending(p: Pending, text: String): Reply? {
        val cancel = CommandParser.isNo(text) && text.split(" ").size <= 3
        if (cancel) return Reply("Theek hai, cancel kar diya.", "cancel")
        return when (p) {
            is Pending.NeedText -> deliver(p.contact, text, p.platform)
            is Pending.NeedContact -> messageTo(stripKo(text), p.text, p.platform)
            is Pending.NeedCallContact -> callTo(stripKo(text))
        }
    }

    private fun stripKo(s: String): String {
        val words = s.trim().split(Regex("\\s+")).toMutableList()
        while (words.size > 1 && Translit.norm(words.last()) in setOf("ko", "ke", "ka", "se", "pe", "par")) {
            words.removeAt(words.size - 1)
        }
        return words.joinToString(" ")
    }

    // ---------------------------------------------------------------------------------------

    private fun execute(cmd: Command): Reply = when (cmd) {
        is Command.OpenApp -> openApp(cmd.name)
        is Command.YouTubeSearch -> {
            val q = Roman.readable(cmd.query)
            Reply("YouTube pe \"$q\" dhoond diya!", "searching", { actions.youtubeSearch(q) })
        }
        is Command.WebSearch -> {
            val q = Roman.readable(cmd.query)
            Reply("Google pe \"$q\" dhoond diya!", "searching", { actions.webSearch(q) })
        }
        is Command.Navigate -> {
            val place = Roman.readable(cmd.place)
            Reply("$place ka rasta khol diya.", "app_open", { actions.navigate(place) })
        }
        is Command.SendMessage -> when {
            cmd.contact == null -> ask(Pending.NeedContact(cmd.text, cmd.platform), "Kisko bhejna hai?", "ask_who")
            else -> messageTo(cmd.contact, cmd.text, cmd.platform)
        }
        is Command.Call -> {
            if (cmd.contact == null) ask(Pending.NeedCallContact(), "Kisko call karna hai?", "ask_call")
            else callTo(cmd.contact)
        }
        is Command.TakePhoto -> takePhoto(cmd.front, cmd.autoCapture)
        is Command.Torch -> {
            val ok = actions.torch(cmd.on)
            when {
                !ok -> Reply("Is phone me torch nahi mili.")
                cmd.on -> Reply("Torch jala di.", "torch_on")
                else -> Reply("Torch band kar di.", "torch_off")
            }
        }
        is Command.Volume -> when (cmd.action) {
            VolumeAction.UP -> { actions.unmuteIfNeeded(); actions.volume(VolumeAction.UP); Reply("Awaaz badha di.", "volume_up") }
            VolumeAction.DOWN -> { actions.volume(VolumeAction.DOWN); Reply("Awaaz kam kar di.", "volume_down") }
            VolumeAction.MAX -> { actions.unmuteIfNeeded(); actions.volume(VolumeAction.MAX); Reply("Awaaz full kar di.", "volume_up") }
            VolumeAction.MUTE -> Reply("Theek hai, awaaz band kar rahe hain.", afterSpeech = { actions.volume(VolumeAction.MUTE) })
        }
        is Command.Alarm -> alarm(cmd)
        is Command.Timer -> {
            if (cmd.seconds <= 0) {
                Reply("Kitne minute ka timer? Jaise bolo '5 minute ka timer lagao'.")
            } else {
                val ok = actions.setTimer(cmd.seconds)
                if (ok) Reply("${duration(cmd.seconds)} ka timer laga diya.", "timer") else Reply("Timer wala app nahi mila.")
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
            Reply("$name settings khol di.", "app_open", { actions.openSettings(cmd.page) })
        }
        Command.TellTime -> {
            val c = Calendar.getInstance()
            val h = c.get(Calendar.HOUR_OF_DAY)
            val m = c.get(Calendar.MINUTE)
            if (m == 0) Reply("Abhi ${period(h)} ke ${h12(h)} baje hain.")
            else Reply("Abhi ${period(h)} ke ${h12(h)} baj kar $m minute hue hain.")
        }
        Command.TellDate -> {
            val c = Calendar.getInstance()
            val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            Reply(
                "Aaj ${days[c.get(Calendar.DAY_OF_WEEK) - 1]} hai, ${c.get(Calendar.DAY_OF_MONTH)} " +
                    "${months[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}.",
            )
        }
        Command.Battery -> {
            val (pct, charging) = actions.battery()
            Reply("Battery $pct percent hai" + if (charging) ", aur charge ho rahi hai." else ".")
        }
        Command.GoHome -> Reply("Home screen pe aa gaye.", "home", { actions.goHome() })
        Command.Help -> Reply(
            "Aap mujhse bol sakte ho: YouTube kholo, Rahul ko message bhejo ki main aa raha hoon, " +
                "isko message karo ki hello, Mummy ko call karo, photo kheencho, selfie lo, torch jalao, " +
                "subah 7 baje ka alarm lagao, awaaz badhao, battery kitni hai, ya Delhi ka rasta dikhao.",
        )
        Command.Stop -> Reply("Theek hai, main chup. Zarurat ho to bula lena.", "stop", stopListening = true)
        Command.Greeting -> Reply("Main ekdum badhiya! Aap bolo, kya karna hai?", "greeting")
        Command.WhoAreYou -> Reply("Main Baby hoon, aapki apni assistant. Jo bologe, woh ho jayega.", "who")
        Command.Joke -> Reply(JOKES.random())
        Command.Thanks -> Reply("Aapka swagat hai! Aur kuch?", "thanks")
        is Command.Unknown -> unknown(cmd.text)
    }

    private fun unknown(text: String): Reply {
        // Sirf app ka naam bola ho ("Instagram") to khol do
        if (text.trim().split(Regex("\\s+")).size <= 3) {
            actions.apps.find(text)?.let { app ->
                return Reply("${app.label} khol diya!", "app_open", { actions.launch(app) })
            }
        }
        return Reply("Sorry, samajh nahi aaya. Ek baar phir bolo na.", "not_understood")
    }

    // --- Apps / Camera ----------------------------------------------------------------------

    private fun openApp(name: String): Reply {
        val n = Translit.norm(name.replace(" ", ""))
        if (n in setOf("phone", "fon", "dialer", "dailar", "dialpad")) return Reply("Dialer khol diya.", "app_open", { actions.openDialer() })
        if (n in setOf("camera", "kamera", "kaimra", "kemra")) return takePhoto(front = false, auto = false)
        if (n in setOf("alarm", "alarms")) return Reply("Alarms khol diye.", "app_open", { actions.openAlarms() })
        val app = actions.apps.find(name)
            ?: return Reply("\"${Roman.readable(name)}\" naam ka app phone me nahi mila.", "no_app")
        return Reply("${app.label} khol diya!", "app_open", { actions.launch(app) })
    }

    private fun takePhoto(front: Boolean, auto: Boolean): Reply {
        if (!actions.has(Manifest.permission.CAMERA)) {
            actions.ensure(Manifest.permission.CAMERA)
            return Reply("Pehle camera ki permission de do, phir dobara bolo.")
        }
        val text = when {
            front && auto -> "Selfie camera khol diya, smile karo!"
            auto -> "Camera khol diya, smile karo! Teen second me photo aayegi."
            else -> "Camera khol diya."
        }
        return Reply(text, "camera", { actions.openCamera(front, auto) })
    }

    // --- Contacts / Messages / Calls --------------------------------------------------------

    private sealed class Found {
        object NoPermission : Found()
        object NotFound : Found()
        class Ok(val contact: Contact) : Found()
    }

    /** Sabse milta-julta contact seedha chuno - koi haan/na nahi. */
    private fun resolve(name: String): Found {
        val digits = name.filter { it.isDigit() }
        if (digits.length >= 7) return Found.Ok(Contact(digits, digits))
        if (!contacts.hasPermission()) {
            actions.ensure(Manifest.permission.READ_CONTACTS)
            return Found.NoPermission
        }
        val best = contacts.find(name).firstOrNull() ?: return Found.NotFound
        return if (best.score >= 0.6) Found.Ok(best.contact) else Found.NotFound
    }

    private fun messageTo(name: String, text: String?, platform: Platform): Reply {
        if (name == CommandParser.CURRENT_CHAT) {
            return if (text.isNullOrBlank()) {
                ask(Pending.NeedText(currentChat, Platform.WHATSAPP), "Kya message bhejna hai?", "ask_text")
            } else {
                deliver(currentChat, text, Platform.WHATSAPP)
            }
        }
        return when (val f = resolve(name)) {
            Found.NoPermission -> Reply("Pehle contacts ki permission de do, phir dobara bolo.")
            Found.NotFound -> Reply("\"${Roman.readable(name)}\" naam ka contact nahi mila, naam ek baar phir bolo na.", "no_contact")
            is Found.Ok ->
                if (text.isNullOrBlank()) {
                    ask(Pending.NeedText(f.contact, platform), "${f.contact.name} ko kya message bhejna hai?", "ask_text")
                } else {
                    deliver(f.contact, text, platform)
                }
        }
    }

    private fun sentReply(name: String) =
        Reply("Ho gaya! Maine $name ko message bhej diya. Ab aage bolo, kya karna hai?", "msg_sent")

    private fun deliver(contact: Contact, rawText: String, platform: Platform): Reply {
        val text = Roman.readable(rawText).replaceFirstChar { it.uppercase() }

        // Jo chat abhi khuli hai, usi me bhejo
        if (contact.number == CommandParser.CURRENT_CHAT) {
            val acc = AutoSendService.instance
                ?: return Reply("Iske liye app me 'Background Control' (Accessibility) ON karna hoga.")
            if (!acc.isWhatsAppChatOpen()) {
                return ask(Pending.NeedContact(rawText, Platform.WHATSAPP), "Abhi koi WhatsApp chat khuli nahi hai. Kisko bhejna hai?", "no_chat")
            }
            acc.sendInCurrentChat(text) { ok ->
                onAsyncReply?.invoke(
                    if (ok) Reply("Ho gaya! Maine message bhej diya. Ab aage bolo, kya karna hai?", "msg_sent")
                    else Reply("Message send nahi ho paya, ek baar check kar lo.", "send_fail"),
                )
            }
            return Reply("Is chat me bhej rahe hain: \"$text\"", speak = false)
        }

        var p = platform
        if (p == Platform.ANY) p = if (prefs.useWhatsApp) Platform.WHATSAPP else Platform.SMS
        if (p == Platform.WHATSAPP && actions.whatsappPackage() == null) p = Platform.SMS

        return when (p) {
            Platform.WHATSAPP -> {
                if (AutoSend.isEnabled(context)) {
                    AutoSend.arm { ok ->
                        onAsyncReply?.invoke(
                            if (ok) sentReply(contact.name)
                            else Reply("${contact.name} ko message send nahi ho paya, ek baar check kar lo.", "send_fail"),
                        )
                    }
                    Reply("${contact.name} ko WhatsApp pe bhej rahe hain: \"$text\"", speak = false, action = {
                        if (!actions.sendWhatsApp(contact.number, text, prefs.countryCode)) AutoSend.done(false)
                    })
                } else {
                    Reply(
                        "WhatsApp khol diya, message likh diya hai, bas send daba do. " +
                            "(Khud bhejne ke liye app me 'Background Control' ON karo)",
                        "msg_typed",
                        { actions.sendWhatsApp(contact.number, text, prefs.countryCode) },
                    )
                }
            }
            Platform.TELEGRAM -> Reply("Telegram khol diya, ${contact.name} ki chat chun lo.", "app_open", { actions.shareTelegram(text) })
            else -> when {
                !actions.has(Manifest.permission.SEND_SMS) -> {
                    actions.ensure(Manifest.permission.SEND_SMS)
                    Reply("Pehle SMS ki permission de do, phir dobara bolo.")
                }
                actions.sendSms(contact.number, text) ->
                    Reply("Ho gaya! Maine ${contact.name} ko SMS bhej diya: \"$text\". Ab aage bolo, kya karna hai?", "msg_sent")
                else -> Reply("SMS seedha nahi ja paya, SMS app khol diya hai.", "send_fail")
            }
        }
    }

    private fun callTo(name: String): Reply =
        when (val f = resolve(name)) {
            Found.NoPermission -> Reply("Pehle contacts ki permission de do, phir dobara bolo.")
            Found.NotFound -> Reply("\"${Roman.readable(name)}\" naam ka contact nahi mila, naam ek baar phir bolo na.", "no_contact")
            is Found.Ok -> Reply("${f.contact.name} ko call laga diya.", "calling", { actions.call(f.contact.number) })
        }

    // --- Alarm / Time helpers ---------------------------------------------------------------

    private fun alarm(cmd: Command.Alarm): Reply {
        if (cmd.hour < 0) return Reply("Kitne baje ka alarm? Jaise bolo 'subah 7 baje ka alarm lagao'.")
        var hour = cmd.hour
        if (!cmd.explicitPeriod) {
            val now = Calendar.getInstance()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val a = hour % 12
            hour = listOf(a, a + 12).firstOrNull { it * 60 + cmd.minute > nowMin } ?: a
        }
        if (!actions.setAlarm(hour, cmd.minute)) return Reply("Alarm wala app nahi mila.")
        val mm = if (cmd.minute == 0) "" else ":" + cmd.minute.toString().padStart(2, '0')
        return Reply("${period(hour).replaceFirstChar { it.uppercase() }} ${h12(hour)}$mm baje ka alarm laga diya.", "alarm")
    }

    private fun period(h: Int) = when (h) {
        in 4..11 -> "subah"
        in 12..15 -> "dopahar"
        in 16..19 -> "shaam"
        else -> "raat"
    }

    private fun h12(h: Int) = if (h % 12 == 0) 12 else h % 12

    private fun duration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add("$h ghante")
        if (m > 0) parts.add("$m minute")
        if (s > 0) parts.add("$s second")
        return parts.joinToString(" ")
    }

    companion object {
        private val JOKES = listOf(
            "Teacher: Batao, sabse zyada barf kahan girti hai? Student: Ji, gole wale ki dukaan pe!",
            "Papa: Beta, phone chhodo aur padhai karo. Beta: Papa, padhai bhi toh phone pe hi ho rahi hai, YouTube pe!",
            "Doctor: Aapko aaram ki zarurat hai. Mareez: Par main toh din bhar sota hoon! Doctor: Toh ab raat ko bhi so jaiye.",
            "Dost: Tu itna mobile kyun chalata hai? Main: Kyunki mobile mujhe nahi chalata!",
            "Pappu: Mummy, main bada hokar pilot banunga. Mummy: Pehle chhat se kapde toh utaar la!",
        )
    }
}
