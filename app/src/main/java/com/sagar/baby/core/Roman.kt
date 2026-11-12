package com.sagar.baby.core

/**
 * Devanagari me aaye text ko padhne layak Roman Hinglish me badlo.
 * "मैं आ रहा हूँ" -> "main aa raha hoon", "गुड मॉर्निंग" -> "good morning".
 */
object Roman {

    /** Hindi lipi me bole gaye common words ki Roman spelling. */
    private val loanwords: Map<String, String> = mapOf(
        "मैसेज" to "message", "मेसेज" to "message", "कॉल" to "call", "फोन" to "phone", "फ़ोन" to "phone",
        "गुड" to "good", "मॉर्निंग" to "morning", "मोर्निंग" to "morning", "नाइट" to "night", "नाईट" to "night",
        "आफ्टरनून" to "afternoon", "इवनिंग" to "evening", "ओके" to "ok", "ओक" to "ok", "थैंक्स" to "thanks",
        "थैंक" to "thank", "यू" to "you", "सॉरी" to "sorry", "सोरी" to "sorry", "प्लीज" to "please",
        "प्लीज़" to "please", "हेलो" to "hello", "हैलो" to "hello", "हाय" to "hi", "बाय" to "bye",
        "लव" to "love", "बेबी" to "baby", "टाइम" to "time", "लेट" to "late", "ऑफिस" to "office",
        "मीटिंग" to "meeting", "पार्टी" to "party", "फ्री" to "free", "बिजी" to "busy", "बिज़ी" to "busy",
        "वीडियो" to "video", "फोटो" to "photo", "कॉलेज" to "college", "स्कूल" to "school", "डिनर" to "dinner",
        "लंच" to "lunch", "ब्रेकफास्ट" to "breakfast", "बर्थडे" to "birthday", "हैप्पी" to "happy",
        "मिस" to "miss", "डियर" to "dear", "ब्रो" to "bro", "भाई" to "bhai", "मम्मी" to "mummy", "पापा" to "papa",
        "क्लास" to "class", "टेस्ट" to "test", "एग्जाम" to "exam", "होम" to "home", "डन" to "done",
        "रेडी" to "ready", "वेट" to "wait", "कम" to "kam", "जस्ट" to "just", "सर" to "sir", "मैडम" to "madam",
        "पैसे" to "paise", "ऑनलाइन" to "online", "चार्ज" to "charge", "बैटरी" to "battery", "मोबाइल" to "mobile",
        "व्हाट्सएप" to "WhatsApp", "व्हाट्सऐप" to "WhatsApp", "यूट्यूब" to "YouTube", "इंस्टाग्राम" to "Instagram",
        "गूगल" to "Google", "ठीक" to "theek", "है" to "hai", "हैं" to "hain", "मैं" to "main", "में" to "mein",
        "नहीं" to "nahi", "हूँ" to "hoon", "हूं" to "hoon", "क्या" to "kya", "क्यों" to "kyun", "यह" to "yeh",
        "वह" to "woh", "और" to "aur", "तुम" to "tum", "आप" to "aap", "कि" to "ki", "की" to "ki", "जी" to "ji",
        "हाँ" to "haan", "हां" to "haan", "कहाँ" to "kahan", "कहां" to "kahan", "यहाँ" to "yahan", "वहाँ" to "wahan",
    )

    fun readable(text: String): String {
        val out = text.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { part ->
            if (part.isBlank() || part.none { Translit.isDevanagari(it) }) part else word(part)
        }
        return out.replace(Regex("\\s+"), " ").trim().replace("।", ".")
    }

    private fun word(w: String): String {
        // Viraam aadi alag karo
        val core = w.trimEnd('।', '॥', '?', '!', ',', '.')
        val tail = w.substring(core.length).replace("।", ".").replace("॥", ".")
        loanwords[core]?.let { return it + tail }
        var s = Translit.toLatin(core)
        // "khaana" -> "khana", "paani" -> "pani" (lekin "aaj", "baat", "kaam" waise hi)
        s = s.replace(Regex("(?<=.)aa(?=[^aeiou][aeiou])"), "a")
        s = s.replace(Regex("(?<=.)aa(?=[eiou])"), "a")
        if (s.length > 3) {
            if (s.endsWith("aa")) s = s.dropLast(1)
            else if (s.endsWith("ee")) s = s.dropLast(2) + "i"
            else if (s.endsWith("oo") && !s.endsWith("noo")) s = s.dropLast(2) + "u"
        }
        return s + tail
    }
}
