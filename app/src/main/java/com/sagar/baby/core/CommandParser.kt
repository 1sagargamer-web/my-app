package com.sagar.baby.core

enum class Platform { WHATSAPP, SMS, TELEGRAM, ANY }
enum class VolumeAction { UP, DOWN, MUTE, MAX }
enum class SettingsPage { WIFI, BLUETOOTH, LOCATION, DISPLAY, MAIN }

/** Screen par jo abhi khula hai uspe button dabana. */
enum class ScreenTarget { INSTALL, OPEN, PLAY_FIRST }

sealed class Command {
    data class OpenApp(val name: String) : Command()
    /** [autoplay] true = pehla result khud chala do (sirf YouTube me, accessibility se). */
    data class YouTubeSearch(val query: String, val autoplay: Boolean = false) : Command()
    data class WebSearch(val query: String) : Command()
    data class Navigate(val place: String) : Command()
    data class SendMessage(val contact: String?, val text: String?, val platform: Platform) : Command()
    data class Call(val contact: String?) : Command()
    data class TakePhoto(val front: Boolean, val autoCapture: Boolean) : Command()
    data class Torch(val on: Boolean) : Command()
    data class Volume(val action: VolumeAction) : Command()

    /** Play Store me app dhundo. [autoInstall] true = Install button bhi khud dabao. */
    data class InstallApp(val query: String, val autoInstall: Boolean) : Command()
    /** Jo screen par khula hai uspe Install / Open / pehla result dabao. */
    data class ScreenTap(val kind: ScreenTarget) : Command()

    /** hour 0-23. [explicitPeriod] false ho to "7 baje" jaisa - subah/shaam baad me tay hoga. hour = -1 matlab time nahi bola. */
    data class Alarm(val hour: Int, val minute: Int, val explicitPeriod: Boolean) : Command()
    data class Timer(val seconds: Int) : Command()
    data class OpenSettings(val page: SettingsPage) : Command()
    object TellTime : Command()
    object TellDate : Command()
    object Battery : Command()
    object GoHome : Command()
    object Help : Command()
    object Stop : Command()
    object Greeting : Command()
    object WhoAreYou : Command()
    object Joke : Command()
    object Thanks : Command()
    data class Unknown(val text: String) : Command()
}

/** Bola gaya ek word: original (jaisa bola) + normalized (match karne ke liye). */
class Tok(val raw: String, val n: String) {
    override fun toString() = raw
}

object CommandParser {

    private fun set(vararg words: String): Set<String> = words.map { Translit.norm(it) }.toSet()

    private val WAKE = set("baby", "bebi", "hey", "ok", "okay", "suno", "sun", "listen", "sagar")
    private val FILLER = set(
        "please", "plz", "pls", "plij", "jara", "zara", "jaldi", "ab", "abhi", "mera", "mere", "meri",
        "mujhe", "baby", "ok", "okay", "yaar", "ek", "tum", "aap", "suno", "jo", "the", "a", "my", "an",
    )
    private val OPEN = set(
        "kholo", "khol", "kholna", "kholiye", "kholdo", "kholde", "open", "opan", "chalu", "start", "launch", "shuru",
    )
    private val PLAY = set(
        "chalao", "chala", "chalado", "bajao", "baja", "bajado", "play", "lagao", "laga", "lagado", "sunao", "suna",
    )
    private val SHOW = set("dikhao", "dikha", "dikhado", "dekho", "dekhna", "show")
    private val SEARCH = set(
        "search", "sarch", "serch", "dhundo", "dhundho", "dhoondo", "dhundh", "khojo", "khoj", "find", "google",
    )
    private val SEND = set("bhejo", "bhej", "bhejdo", "bhejde", "bhejna", "bhejiye", "send", "sent")
    private val TELL = set("bolo", "bol", "kaho", "kah", "kahna", "likho", "likh", "batado", "bata", "batao")
    private val MSG = set(
        "message", "messages", "msg", "massage", "messege", "mesage", "maisej", "mesej", "mesaj", "text", "sandesh", "msj",
    )
    private val CALL = set("call", "kol", "phone", "fon", "dial", "calling")
    private val CALLV = set("karo", "kar", "kro", "lagao", "laga", "milao", "mila", "do", "kijiye", "karna", "kardo", "lagado")
    private val YE = set("ye", "yeh", "yah", "this", "yahi")
    private val KI = set("ki", "ke", "k", "that", "saying")
    private val POSTP = set("pe", "par", "pr", "pai", "me", "mein", "men", "se", "ka", "ki", "ke", "ko", "on", "in", "at", "via", "using", "per")
    private val TRAIL = set(
        "do", "de", "dena", "dijiye", "karo", "kar", "kardo", "kr", "kro", "karna", "jao", "ja", "hai", "h", "dijie", "please", "plz",
    )
    private val APPWORD = set("app", "application", "aap", "ap")
    private val GOOGLE = set("google", "gugal", "gugl", "gogal")
    private val CAM = set("camera", "kamera", "kaimra", "kaimara", "kemra", "kamra", "cam", "camra")
    private val PHOTO_NOUN = set("photo", "photos", "foto", "pic", "pik", "picture", "pikchar", "image", "tasvir", "tasveer", "snap", "fotu")
    private val CLICK = set(
        "click", "klik", "khicho", "khincho", "khich", "khinch", "kheecho", "khichna", "lo", "le", "lena", "lijiye", "khicho", "capture", "take",
    )
    private val SELFIE = set("selfie", "selfi", "selphi", "front", "aage", "samne", "saamne")
    private val SELFIE_ONLY = set("selfie", "selfi", "selphi")
    private val TORCH = set("torch", "tarch", "tourch", "flash", "flashlight", "flashlite", "batti", "tarc")
    private val LIGHT = set("light", "lite", "lait")
    private val ON = set("on", "jalao", "jala", "jalado", "chalu", "chalao", "kholo", "start", "jalaiye")
    private val OFF = set("off", "of", "band", "bandh", "bujhao", "bujha", "bujhado", "close")
    private val VOL = set("volume", "valum", "volum", "awaaz", "aawaz", "awaz", "aavaj", "awaj", "avaj", "sound", "aavaaz")
    private val UP = set("badhao", "badha", "badhado", "tej", "tez", "jyada", "zyada", "up", "high", "increase", "uncha", "unchi", "badao")
    private val DOWN = set("kam", "ghatao", "ghata", "dheere", "dhire", "dhimi", "low", "down", "decrease", "halka", "halki", "kamkaro")
    private val MUTE = set("mute", "mut", "silent")
    private val MAX = set("full", "max", "maximum", "puri", "poori", "pura", "poora")
    private val TIME = set("time", "taim", "samay", "tym")
    private val BAJE = set("baje", "bje", "bajje", "bajhe", "bajke", "bajkar", "baja")
    private val ASK = set("kitne", "kitna", "kya", "kaun", "kon", "konsa", "kaunsa", "what", "which", "how", "batao", "bata", "aaj", "today")
    private val DATE = set("date", "tarikh", "tareekh", "tarik")
    private val DIN = set("din", "day", "vaar", "var")
    private val BATTERY = set("battery", "betri", "baitri", "batri", "bettery", "charging", "charge", "baitari", "betari")
    private val ALARM = set("alarm", "alaram", "alarum", "alarme")
    private val TIMER = set("timer", "taimar", "taimer", "timar")
    private val INSTALL = set(
        "install", "instal", "instol", "installed", "installo", "download", "downlod", "downlad",
        "daunlod", "dawnlod", "dawnload", "daunload", "instaal",
    )
    private val GENERIC_APP = set("game", "gem", "gaym", "gameko", "app", "apps", "application", "wala", "waala")
    private val NAV = set("rasta", "raasta", "route", "navigate", "navigation", "direction", "directions", "rastaa")
    private val MAPS = set("map", "maps", "naksha")
    private val SONG = set("gana", "gaana", "gane", "gaane", "song", "songs", "music", "geet", "bhajan", "gaano", "ganna")
    private val WIFI = set("wifi", "vifi", "vaifai", "vaiphai", "vaifi", "vaiifaii", "wi-fi")
    private val BT = set("bluetooth", "blutooth", "bluetuth", "blututh", "bluthuth")
    private val LOC = set("location", "lokesan", "lokeshan", "gps")
    private val DISPLAY = set("brightness", "display", "roshni", "brightnes")
    private val SETTINGS = set("setting", "settings", "seting", "setings", "settin")
    private val HOME = set("home", "hom")
    private val GO = set("jao", "ja", "chalo", "chale", "go", "screen", "par", "pe")
    private val STOP = set("stop", "chup", "bas", "ruko", "ruk", "band", "bye", "alvida", "sojao", "so", "shant", "chupkaro")
    private val GREET = set("hello", "hi", "hii", "helo", "namaste", "namaskar", "hey", "hy", "hlo", "ram", "salam")
    private val HOWRU = set("kaise", "kese", "kaisi", "kesi", "how")
    private val WHO = set("kaun", "kon", "who", "naam", "name")
    private val YOU = set("tum", "tumhara", "tumhari", "tera", "teri", "aap", "aapka", "aapki", "your", "you", "tu")
    private val HELP = set("help", "madad", "commands", "command")
    private val CAN = set("sakti", "sakte", "sakta", "can", "sakti")
    private val JOKE = set("joke", "jokes", "chutkula", "chutkule", "majak", "mazak", "jok", "funny")
    private val THANKS = set("thanks", "thank", "thankyou", "shukriya", "dhanyavad", "dhanyawad", "thanku", "thnx", "tq")
    private val QUESTION = set("kya", "kaun", "kon", "kaise", "kitna", "kitne", "kab", "kahan", "kaha", "kyun", "kyu", "what", "who", "how", "when", "where", "why", "batao", "bataiye", "mausam", "weather")
    private val BARE = set("bare", "baare", "about")
    val YES = set("haan", "ha", "han", "haa", "yes", "yeah", "yep", "ok", "okay", "theek", "thik", "sahi", "bilkul", "ji", "hanji", "bhejo", "bhej", "kardo", "karo", "send", "hmm", "correct", "right")
    val NO = set("nahi", "nahin", "na", "no", "nope", "mat", "cancel", "rehne", "chhodo", "chodo", "galat", "wrong", "ruko", "mt", "nai")

    private val hindiNumbers: Map<String, Int> = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "tin" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "che" to 6, "chhah" to 6, "chah" to 6, "saat" to 7, "sat" to 7, "aath" to 8, "ath" to 8,
        "nau" to 9, "no" to 9, "das" to 10, "dus" to 10, "gyarah" to 11, "gyara" to 11, "barah" to 12, "bara" to 12,
        "pandrah" to 15, "bees" to 20, "bis" to 20, "pachis" to 25, "tees" to 30, "tis" to 30, "chalis" to 40,
        "pachas" to 50, "saath" to 60, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15,
        "twenty" to 20, "thirty" to 30,
    ).mapKeys { Translit.norm(it.key) }

    private val UNIT_HOUR = set("ghanta", "ghante", "ghante", "hour", "hours", "hr", "hrs")
    private val UNIT_MIN = set("minute", "minutes", "min", "mins", "mint", "minat", "minit")
    private val UNIT_SEC = set("second", "seconds", "sec", "secs", "sekand", "sekend")
    private val AM = set("subah", "subha", "savere", "morning", "am", "suba")
    private val NOON = set("dopahar", "dopahar", "dupahar", "afternoon", "dopehar")
    private val PM = set("shaam", "sham", "evening", "pm", "sanjh")
    private val NIGHT = set("raat", "rat", "night")

    // ---------------------------------------------------------------------------------------

    fun tokenize(text: String): List<Tok> =
        text.trim()
            .split(Regex("[\\s,!?;\"“”'()।॥]+|(?<!\\d)\\.|\\.(?!\\d)"))
            .filter { it.isNotBlank() }
            .map { Tok(it, Translit.norm(it)) }
            .filter { it.n.isNotEmpty() }

    fun isYes(text: String): Boolean {
        val t = tokenize(text)
        return t.isNotEmpty() && t.size <= 4 && t.none { it.n in NO } && t.any { it.n in YES }
    }

    fun isNo(text: String): Boolean {
        val t = tokenize(text)
        return t.isNotEmpty() && t.size <= 5 && t.any { it.n in NO }
    }

    /** Contact ka yeh naam matlab: jo WhatsApp chat abhi screen par khuli hai. */
    const val CURRENT_CHAT = "__current_chat__"

    private val THIS_CHAT = set("isko", "isse", "ise", "inko", "inhe", "isku", "iske", "isko", "ishko", "this", "him", "her")
    private val CONJ = set("aur", "or", "and", "phir", "fir", "then", "uske", "uskebad")

    /**
     * Ek baar me kai kaam: "WhatsApp kholo aur Rahul ko message karo ki hello" -> [OpenApp, SendMessage].
     * Message ke andar ka "aur" (ki ... ke baad) nahi toda jaata.
     */
    fun parseAll(text: String, strict: Boolean = false): List<Command> {
        val t = tokenize(text)
        val kiIdx = t.indexOfFirst { it.n in KI }
        val limit = if (kiIdx >= 0) kiIdx else t.size
        val cuts = (0 until limit).filter { t[it].n in CONJ && it > 0 && it < t.size - 1 }
        if (cuts.isNotEmpty()) {
            val segments = ArrayList<String>()
            var start = 0
            for (c in cuts) {
                segments.add(t.subList(start, c).joinToString(" ") { it.raw })
                start = c + 1
            }
            segments.add(t.subList(start, t.size).joinToString(" ") { it.raw })
            val cmds = segments.filter { it.isNotBlank() }.map { parse(it, strict) }
            if (cmds.size > 1 && cmds.none { it is Command.Unknown }) return merge(cmds)
        }
        return listOf(parse(text, strict))
    }

    private fun merge(cmds: List<Command>): List<Command> {
        val out = ArrayList<Command>()
        for (c in cmds) {
            val prev = out.lastOrNull()
            when {
                // "camera kholo aur photo lo" -> ek hi photo command
                prev is Command.TakePhoto && c is Command.TakePhoto -> {
                    out[out.size - 1] = Command.TakePhoto(prev.front || c.front, prev.autoCapture || c.autoCapture)
                }
                // "WhatsApp kholo aur Rahul ko message karo" -> seedha WhatsApp message
                prev is Command.OpenApp && c is Command.SendMessage && isPlatformName(prev.name) != null -> {
                    val pl = isPlatformName(prev.name)!!
                    out[out.size - 1] = c.copy(platform = if (c.platform == Platform.ANY) pl else c.platform)
                }
                // "YouTube kholo aur X song lagao" -> seedha YouTube search (search khud YouTube kholta hai)
                prev is Command.OpenApp && c is Command.YouTubeSearch && isYouTubeName(prev.name) ->
                    out[out.size - 1] = c
                // "Play Store kholo aur X install karo" -> seedha Play Store install
                prev is Command.OpenApp && c is Command.InstallApp && isStoreName(prev.name) ->
                    out[out.size - 1] = c
                // "Play Store kholo aur isko install karo" -> abhi screen pe nahi, pichhle install ko mila do
                prev is Command.InstallApp && c is Command.ScreenTap && c.kind == ScreenTarget.INSTALL ->
                    out[out.size - 1] = prev.copy(autoInstall = true)
                else -> out.add(c)
            }
        }
        return out
    }

    private fun isPlatformName(name: String): Platform? {
        val n = Translit.normPhrase(name).replace(" ", "")
        return when {
            isWhatsApp(n) -> Platform.WHATSAPP
            isTelegram(n) -> Platform.TELEGRAM
            n in set("message", "messages", "sms") -> Platform.SMS
            else -> null
        }
    }

    private fun isYouTubeName(name: String) = isYouTube(Translit.normPhrase(name).replace(" ", ""))
    private fun isStoreName(name: String) = hasPlayStore(tokenize(name))

    /** [strict] = background mode: aam baat-cheet ko Google search mat banao. */
    fun parse(text: String, strict: Boolean = false): Command {
        var t = tokenize(text)
        while (t.size > 1 && t[0].n in WAKE) t = t.drop(1)
        if (t.isEmpty()) return Command.Unknown(text)

        return parseMessageOrCall(t)
            ?: parseAlarmOrTimer(t)
            ?: parseTorch(t)
            ?: parseVolume(t)
            ?: parseCamera(t)
            ?: parseInstall(t)
            ?: parseYouTube(t)
            ?: parseSong(t)
            ?: parseNavigate(t)
            ?: parseSettings(t)
            ?: parseHome(t)
            ?: parseInfo(t)
            ?: parseSearch(t)
            ?: (if (t.has(JOKE)) Command.Joke else null)
            ?: parseOpenApp(t)
            ?: parseChat(t)
            ?: (if (strict) null else parseQuestion(t, text))
            ?: Command.Unknown(text)
    }

    // ---------------------------------------------------------------------------------------
    // Helpers

    private fun List<Tok>.has(s: Set<String>) = any { it.n in s }
    private fun List<Tok>.raw() = joinToString(" ") { it.raw }

    private fun isWhatsApp(n: String) =
        n in set("whatsapp", "watsap", "vatsap", "whatsap", "watsapp", "wp", "whatsaap", "vatsaep", "vhatsaep") ||
            Fuzzy.similarity(n, "vhatsap") >= 0.75 || Fuzzy.similarity(n, "vatsap") >= 0.75

    private fun isSms(n: String) = n == "sms" || n == "esms" || (n.length <= 9 && Translit.skeleton(n) == "sms")
    private fun isTelegram(n: String) = Fuzzy.similarity(n, "telegram") >= 0.75
    private fun isPlatformTok(t: Tok) = isWhatsApp(t.n) || isSms(t.n) || isTelegram(t.n)

    private fun isYouTube(n: String) =
        n in set("youtube", "yutub", "yutyub", "utub", "yt", "yutube", "utube") || Fuzzy.similarity(n, "yutube") >= 0.75

    private val STORE_WORD = set("store", "stor", "istor", "estore", "istore")
    private val PLAY_WORD = set("play", "ple", "play")

    private fun hasPlayStore(t: List<Tok>): Boolean {
        for (i in t.indices) {
            val n = t[i].n
            if (n in set("playstore", "plestor", "pleystor", "plestore", "playstor", "vending")) return true
            if (n in PLAY_WORD && t.getOrNull(i + 1)?.n in STORE_WORD) return true
            if (n in STORE_WORD && t.getOrNull(i - 1)?.n in PLAY_WORD) return true
        }
        return false
    }

    private fun detectPlatform(t: List<Tok>): Platform? {
        for (x in t) {
            if (isWhatsApp(x.n)) return Platform.WHATSAPP
            if (isSms(x.n)) return Platform.SMS
            if (isTelegram(x.n)) return Platform.TELEGRAM
        }
        return null
    }

    /** Dono kinaron se woh words hatao jo [drop] me hon. */
    private fun stripEdges(t: List<Tok>, drop: (Tok) -> Boolean): List<Tok> {
        var s = 0
        var e = t.size
        while (s < e && drop(t[s])) s++
        while (e > s && drop(t[e - 1])) e--
        return t.subList(s, e)
    }

    private fun stripLeft(t: List<Tok>, drop: (Tok) -> Boolean): List<Tok> {
        var s = 0
        while (s < t.size && drop(t[s])) s++
        return t.subList(s, t.size)
    }

    private fun stripRight(t: List<Tok>, drop: (Tok) -> Boolean): List<Tok> {
        var e = t.size
        while (e > 0 && drop(t[e - 1])) e--
        return t.subList(0, e)
    }

    // ---------------------------------------------------------------------------------------
    // Message / Call

    private fun cleanName(t: List<Tok>): String {
        val left = stripLeft(t) {
            it.n in FILLER || it.n in MSG || it.n in SEND || it.n in POSTP || it.n in TELL ||
                it.n in CALL || it.n in TRAIL || it.n in WAKE || isPlatformTok(it)
        }
        return stripRight(left) { it.n in POSTP }.raw()
    }

    /** "...hello bhej do" -> "hello" (aakhri bhejo/do hatao). */
    private fun cutTrailingSend(t: List<Tok>): List<Tok> {
        val lastSend = t.indexOfLast { it.n in SEND || it.n in TELL }
        if (lastSend >= 0 && t.subList(lastSend + 1, t.size).all { it.n in TRAIL }) {
            return stripRight(t.subList(0, lastSend)) { it.n in MSG || isPlatformTok(it) || it.n in POSTP }
        }
        return t
    }

    private fun parseMessageOrCall(t0: List<Tok>): Command? {
        val t = t0
        val thisIdx = t.indexOfFirst { it.n in THIS_CHAT }
        if (thisIdx in 0..1 && t.none { it.n == "ko" } &&
            (t.has(SEND) || t.has(MSG) || t.has(TELL) || t.any { isPlatformTok(it) })
        ) {
            val after = t.subList(thisIdx + 1, t.size)
            val kiIdx = after.indexOfFirst { it.n in KI }
            val content = if (kiIdx >= 0) {
                cutTrailingSend(after.subList(kiIdx + 1, after.size))
            } else {
                val left = stripLeft(after) {
                    isPlatformTok(it) || it.n in POSTP || it.n in MSG || it.n in SEND || it.n in TELL ||
                        it.n in FILLER || it.n in TRAIL || it.n in YE
                }
                stripRight(left) {
                    it.n in SEND || it.n in MSG || it.n in TRAIL || it.n in POSTP || it.n in TELL || isPlatformTok(it)
                }
            }
            val text = stripLeft(content) { it.n in YE }.raw()
            return Command.SendMessage(CURRENT_CHAT, text.ifBlank { null }, Platform.WHATSAPP)
        }
        var koIdx = t.indexOfFirst { it.n == "ko" }
        if (koIdx < 0) {
            // "9876543210 par message bhejo ..." - number ke baad "par/pe" ko "ko" jaisa maano
            val i = t.indexOfFirst { it.n in set("pe", "par", "pr", "per") }
            if (i >= 1 && t.subList(0, i).all { x -> x.n.all { it.isDigit() } } &&
                t.subList(0, i).sumOf { it.n.length } >= 7
            ) koIdx = i
        }

        if (koIdx >= 1) {
            val before = t.subList(0, koIdx)
            val after = t.subList(koIdx + 1, t.size)
            val name = cleanName(before)
            val kiIdx = after.indexOfFirst { it.n in KI }

            val callIdx = after.indexOfFirst { it.n in CALL }
            val sendIdx = after.indexOfFirst { it.n in SEND || it.n in MSG || isPlatformTok(it) }
            if (callIdx >= 0 && (kiIdx < 0 || callIdx < kiIdx) && (sendIdx < 0 || sendIdx > callIdx)) {
                val next = after.getOrNull(callIdx + 1)
                if (next == null || next.n in CALLV || next.n in TRAIL) return Command.Call(name.ifBlank { null })
            }

            val platform = detectPlatform(t)
            val hasSend = t.has(SEND)
            val hasMsg = t.has(MSG)
            val hasTell = after.has(TELL)
            if (hasSend || hasMsg || platform != null || hasTell) {
                val content: List<Tok> = if (kiIdx >= 0 && after.subList(0, kiIdx).all {
                        it.n in MSG || it.n in SEND || it.n in TELL || it.n in POSTP || it.n in TRAIL ||
                            it.n in FILLER || isPlatformTok(it)
                    }
                ) {
                    cutTrailingSend(after.subList(kiIdx + 1, after.size))
                } else {
                    val left = stripLeft(after) {
                        isPlatformTok(it) || it.n in POSTP || it.n in MSG || it.n in SEND || it.n in TELL ||
                            it.n in FILLER || it.n in TRAIL
                    }
                    stripRight(left) {
                        it.n in SEND || it.n in MSG || it.n in TRAIL || it.n in POSTP || it.n in TELL || isPlatformTok(it)
                    }
                }
                return Command.SendMessage(name.ifBlank { null }, content.raw().ifBlank { null }, platform ?: Platform.ANY)
            }
            return null
        }

        // English / "ko" ke bina
        val s = stripLeft(t) { it.n in FILLER || it.n in WAKE }
        if (s.isEmpty()) return null
        val first = s[0]

        if (first.n in CALL) {
            val rest = stripRight(stripLeft(s.drop(1)) { it.n in CALLV || it.n in TRAIL || it.n in POSTP || it.n == "to" }) {
                it.n in POSTP || it.n in TRAIL || isPlatformTok(it)
            }
            if (s.size == 1 || rest.isEmpty()) return if (s.size <= 3 && s.drop(1).all { it.n in CALLV || it.n in TRAIL }) Command.Call(null) else null
            return Command.Call(rest.raw())
        }

        val opensApp = s.has(OPEN) && !s.has(SEND) && !s.has(MSG)
        if (!opensApp && (first.n in SEND || first.n in MSG || (isPlatformTok(first) && s.size > 1 && !s.has(OPEN)))) {
            val platform = detectPlatform(s)
            val rest = stripLeft(s) { it.n in SEND || it.n in MSG || isPlatformTok(it) || it.n in POSTP || it.n in FILLER || it.n in TRAIL }
            val toIdx = rest.indexOfLast { it.n == "to" }
            if (toIdx >= 0) {
                val text = stripLeft(rest.subList(0, toIdx)) { it.n in MSG || it.n in FILLER }
                var nameToks = rest.subList(toIdx + 1, rest.size)
                var extra: List<Tok> = emptyList()
                val ki = nameToks.indexOfFirst { it.n in KI }
                if (ki >= 0) { extra = nameToks.subList(ki + 1, nameToks.size); nameToks = nameToks.subList(0, ki) }
                nameToks = stripRight(nameToks) { it.n in POSTP || isPlatformTok(it) || it.n in TRAIL }
                val txt = (text + extra).raw()
                return Command.SendMessage(nameToks.raw().ifBlank { null }, txt.ifBlank { null }, platform ?: Platform.ANY)
            }
            val cleaned = stripRight(rest) { it.n in TRAIL || it.n in SEND }
            if (cleaned.isEmpty()) return Command.SendMessage(null, null, platform ?: Platform.ANY)
            val name = cleaned[0].raw
            val text = stripLeft(cleaned.drop(1)) { it.n in KI }.raw()
            return Command.SendMessage(name, text.ifBlank { null }, platform ?: Platform.ANY)
        }
        return null
    }

    // ---------------------------------------------------------------------------------------
    // Alarm / Timer

    private val TIME_RE = Regex("\\d{1,2}[:.]\\d{2}")

    private class Num(val value: Double, val index: Int)

    private fun numbers(t: List<Tok>): List<Num> {
        val out = ArrayList<Num>()
        for ((i, x) in t.withIndex()) {
            val r = x.n
            if (TIME_RE.matches(x.raw)) continue
            if (r.all { it.isDigit() }) { out.add(Num(r.toDouble(), i)); continue }
            val hn = hindiNumbers[r] ?: continue
            // "do" ka matlab "2" bhi hai aur "de do" bhi - isliye sirf unit ke paas maano
            val next = t.getOrNull(i + 1)?.n
            val prev = t.getOrNull(i - 1)?.n
            val nearUnit = next != null && (next in BAJE || next in UNIT_HOUR || next in UNIT_MIN || next in UNIT_SEC)
            val afterMod = prev != null && prev in set("saade", "sade", "sava", "sawa", "paune", "pone")
            if (nearUnit || afterMod || r !in set("do", "no", "sat", "bara")) out.add(Num(hn.toDouble(), i))
        }
        return out
    }

    private fun parseAlarmOrTimer(t: List<Tok>): Command? {
        if (t.has(TIMER)) {
            var total = 0.0
            val nums = numbers(t)
            for (num in nums) {
                val unit = t.getOrNull(num.index + 1)?.n
                total += num.value * when {
                    unit != null && unit in UNIT_HOUR -> 3600
                    unit != null && unit in UNIT_SEC -> 1
                    else -> 60
                }
            }
            if (t.any { it.n in set("aadha", "adha", "half") } && t.has(UNIT_HOUR)) total += 1800
            if (t.any { it.n in set("dedh") }) total += if (t.has(UNIT_HOUR)) 5400 else 90
            return Command.Timer(total.toInt())
        }
        if (!t.has(ALARM)) return null

        var hour = -1
        var minute = 0
        // "7:30"
        t.firstOrNull { TIME_RE.matches(it.raw) }?.let {
            val p = it.raw.split(':', '.'); hour = p[0].toInt(); minute = p[1].toInt()
        }
        if (hour < 0) {
            val nums = numbers(t)
            if (nums.isNotEmpty()) {
                hour = nums[0].value.toInt()
                val second = nums.getOrNull(1)
                if (second != null && second.value < 60) minute = second.value.toInt()
            }
        }
        if (hour < 0) {
            if (t.any { it.n == "dedh" }) { hour = 1; minute = 30 }
            if (t.any { it.n == "dhai" }) { hour = 2; minute = 30 }
        }
        if (hour < 0) return Command.Alarm(-1, 0, false)

        if (t.any { it.n in set("saade", "sade") }) minute = 30
        if (t.any { it.n in set("sava", "sawa") }) minute = 15
        if (t.any { it.n in set("paune", "pone") }) { minute = 45; hour = (hour + 23) % 24 }

        var explicit = hour > 12
        when {
            t.has(AM) -> { explicit = true; if (hour == 12) hour = 0 }
            t.has(NOON) -> { explicit = true; if (hour < 12 && hour != 12) hour += 12; if (hour == 24) hour = 12 }
            t.has(PM) -> { explicit = true; if (hour < 12) hour += 12 }
            t.has(NIGHT) -> {
                explicit = true
                if (hour == 12) hour = 0 else if (hour in 6..11) hour += 12
            }
        }
        return Command.Alarm(hour.coerceIn(0, 23), minute.coerceIn(0, 59), explicit)
    }

    // ---------------------------------------------------------------------------------------

    private fun parseTorch(t: List<Tok>): Command? {
        if (t.has(TORCH) || (t.has(LIGHT) && (t.has(ON) || t.has(OFF)))) {
            return Command.Torch(on = !t.has(OFF))
        }
        return null
    }

    private fun parseVolume(t: List<Tok>): Command? {
        if (!t.has(VOL) && !(t.has(MUTE) && t.size <= 3)) return null
        val a = when {
            t.has(MUTE) || t.has(OFF) -> VolumeAction.MUTE
            t.has(MAX) -> VolumeAction.MAX
            t.has(DOWN) -> VolumeAction.DOWN
            else -> VolumeAction.UP
        }
        return Command.Volume(a)
    }

    private fun parseCamera(t: List<Tok>): Command? {
        val hasCam = t.has(CAM)
        val hasPhoto = t.has(PHOTO_NOUN)
        val hasSelfie = t.has(SELFIE_ONLY)
        if (!hasCam && !hasPhoto && !hasSelfie) return null
        val hasClick = t.any { it.n in CLICK && it.n !in OPEN }
        if (!hasCam && hasPhoto && !hasClick && !hasSelfie) {
            // "photos kholo" / "photo dikhao" -> gallery
            return if (t.has(OPEN) || t.has(SHOW)) Command.OpenApp("gallery") else null
        }
        val front = hasSelfie || t.any { it.n in SELFIE }
        val auto = hasSelfie || hasClick || (hasPhoto && !t.has(OPEN))
        return Command.TakePhoto(front = front, autoCapture = auto)
    }

    private val QUERY_NOISE: (Tok) -> Boolean = {
        it.n in POSTP || it.n in OPEN || it.n in PLAY || it.n in SEARCH || it.n in TRAIL || it.n in FILLER ||
            it.n in SHOW || it.n in APPWORD || it.n in WAKE || it.n in YE ||
            it.n in set("karke", "karo", "kariye", "mujhe", "par", "pe", "ko")
    }

    private fun parseInstall(t: List<Tok>): Command? {
        val hasInstall = t.has(INSTALL)
        val onStore = hasPlayStore(t)
        if (!hasInstall && !onStore) return null
        val name = stripEdges(t) {
            QUERY_NOISE(it) || it.n in INSTALL || it.n in GENERIC_APP || it.n in APPWORD ||
                it.n in PLAY_WORD || it.n in STORE_WORD || it.n in THIS_CHAT ||
                it.n in set("playstore", "plestor", "pleystor", "plestore", "playstor", "vending", "karo", "kardo", "do")
        }
        val nm = name.raw()
        return when {
            nm.isNotBlank() -> Command.InstallApp(nm, autoInstall = hasInstall)
            hasInstall -> Command.ScreenTap(ScreenTarget.INSTALL) // "ye game download karo"
            else -> Command.OpenApp("Play Store") // "Play Store kholo"
        }
    }

    /** "lagao/bajao/chalao/sunao" bola = pehla video khud chala do; "search/dhundo" = sirf dikhao. */
    private fun wantsAutoplay(t: List<Tok>) = (t.has(PLAY) || t.has(SONG)) && !t.has(SEARCH)

    private fun parseYouTube(t: List<Tok>): Command? {
        val idx = t.indexOfFirst { isYouTube(it.n) }
        if (idx < 0) return null
        val rest = t.filterIndexed { i, _ -> i != idx }
        val q = stripEdges(rest, QUERY_NOISE)
        if (q.isEmpty() || q.all { QUERY_NOISE(it) }) return Command.OpenApp("YouTube")
        return Command.YouTubeSearch(q.raw(), autoplay = wantsAutoplay(t))
    }

    private fun parseSong(t: List<Tok>): Command? {
        if (!t.has(SONG)) return null
        if (!t.has(PLAY) && !t.has(OPEN) && !t.has(SEARCH) && t.size > 2) return null
        val q = stripEdges(t, QUERY_NOISE)
        if (q.isEmpty() || q.all { it.n in SONG }) return Command.YouTubeSearch("hindi songs", autoplay = !t.has(SEARCH))
        return Command.YouTubeSearch(q.raw(), autoplay = wantsAutoplay(t))
    }

    private fun parseNavigate(t: List<Tok>): Command? {
        val hasNav = t.has(NAV) || t.any { it.n in set("lechalo", "pahuchao") }
        val hasMaps = t.has(MAPS)
        if (!hasNav && !hasMaps) return null
        val noise: (Tok) -> Boolean = {
            QUERY_NOISE(it) || it.n in MAPS || it.n in NAV || it.n in GOOGLE ||
                it.n in set("tak", "jana", "jaana", "hai", "kaise", "le", "chalo", "to", "for", "the", "batao", "bata", "kahan", "kidhar")
        }
        val q = stripEdges(t, noise)
        if (q.isEmpty()) return if (hasMaps) Command.OpenApp("Maps") else null
        return Command.Navigate(q.raw())
    }

    private fun parseSettings(t: List<Tok>): Command? {
        fun fuzzyHas(words: Set<String>) = t.any { tok -> tok.n in words || words.any { Fuzzy.similarity(tok.n, it) >= 0.8 } }
        return when {
            fuzzyHas(WIFI) -> Command.OpenSettings(SettingsPage.WIFI)
            fuzzyHas(BT) -> Command.OpenSettings(SettingsPage.BLUETOOTH)
            t.has(LOC) -> Command.OpenSettings(SettingsPage.LOCATION)
            t.has(DISPLAY) -> Command.OpenSettings(SettingsPage.DISPLAY)
            t.has(SETTINGS) -> Command.OpenSettings(SettingsPage.MAIN)
            else -> null
        }
    }

    private fun parseHome(t: List<Tok>): Command? =
        if (t.has(HOME) && (t.has(GO) || t.size == 1 || t.has(OPEN))) Command.GoHome else null

    private fun parseInfo(t: List<Tok>): Command? {
        if (t.has(BATTERY)) return Command.Battery
        if (t.has(TIME) || (t.has(BAJE) && t.has(ASK))) {
            return Command.TellTime
        }
        if (t.has(DATE) || (t.has(DIN) && t.has(ASK))) return Command.TellDate
        return null
    }

    private fun parseSearch(t: List<Tok>): Command? {
        val bareIdx = t.indexOfFirst { it.n in BARE }
        if (bareIdx > 0) {
            val q = stripEdges(t.subList(0, bareIdx), QUERY_NOISE)
            if (q.isNotEmpty()) return Command.WebSearch(q.raw())
        }
        if (!t.has(SEARCH) && !t.has(GOOGLE)) return null
        val q = stripEdges(t) { QUERY_NOISE(it) || it.n in GOOGLE }
        if (q.isEmpty()) return if (t.has(GOOGLE)) Command.OpenApp("Google") else null
        return Command.WebSearch(q.raw())
    }

    private val OPEN_ONLY = set("kholo", "khol", "open", "opan", "kholo", "kholdo", "kholde")

    private fun parseOpenApp(t: List<Tok>): Command? {
        val hasVerb = t.has(OPEN) || t.has(PLAY)
        val name = stripEdges(t) { QUERY_NOISE(it) || it.n in APPWORD }
        if (name.isEmpty()) {
            // Sirf "open karo" / "kholo" / "isko kholo" -> jo screen par hai usko kholo (jaise install ke baad)
            if (t.has(OPEN_ONLY) && t.size <= 3) return Command.ScreenTap(ScreenTarget.OPEN)
            return null
        }
        if (hasVerb) return Command.OpenApp(name.raw())
        return null
    }

    private fun parseChat(t: List<Tok>): Command? {
        if (t.has(JOKE)) return Command.Joke
        if (t.has(THANKS)) return Command.Thanks
        if (t.has(HELP) || (t.has(CAN) && t.any { it.n in set("kya", "what") })) return Command.Help
        if (t.has(WHO) && (t.has(YOU) || t.size <= 3)) return Command.WhoAreYou
        if (t.has(HOWRU) && t.size <= 4) return Command.Greeting
        if (t.size <= 3 && t.has(STOP)) return Command.Stop
        if (t.size <= 3 && t.has(GREET)) return Command.Greeting
        return null
    }

    private fun parseQuestion(t: List<Tok>, text: String): Command? {
        if (t.size >= 2 && t.has(QUESTION)) return Command.WebSearch(text.trim())
        return null
    }
}
