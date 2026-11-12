package com.sagar.baby.core

/**
 * Hindi (Devanagari) ko Roman me badalna aur Hinglish words ko ek jaisa (normalize) karna,
 * taaki "यूट्यूब", "YouTube" aur "utub" sab ek hi cheez samjhe jaayein.
 */
object Translit {

    private val consonants: Map<Char, String> = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
        'क़' to "k", 'ख़' to "kh", 'ग़' to "g", 'ज़' to "z",
        'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f", 'य़' to "y",
    )

    private val nuktaForms: Map<String, String> = mapOf(
        "k" to "k", "kh" to "kh", "g" to "g", "j" to "z", "d" to "r", "dh" to "rh", "ph" to "f", "y" to "y",
    )

    private val vowels: Map<Char, String> = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ऋ' to "ri", 'ऍ' to "e", 'ए' to "e", 'ऐ' to "ai", 'ऑ' to "o", 'ओ' to "o", 'औ' to "au",
    )

    private val matras: Map<Char, String> = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'ॅ' to "e", 'े' to "e", 'ै' to "ai", 'ॉ' to "o", 'ो' to "o", 'ौ' to "au",
    )

    private const val HALANT = '्'
    private const val NUKTA = '़'

    private class Unit(var cons: String?, var vowel: String, var inherent: Boolean, var coda: String = "")

    fun isDevanagari(c: Char) = c in 'ऀ'..'ॿ'

    /** Devanagari text ko Roman me badlo. Baaki characters waise hi rehte hain. */
    fun toLatin(text: String): String {
        val out = StringBuilder()
        val word = ArrayList<Unit>()

        fun flush() {
            if (word.isEmpty()) return
            applySchwaDeletion(word)
            for (u in word) {
                out.append(u.cons ?: "").append(u.vowel).append(u.coda)
            }
            word.clear()
        }

        for (c in text) {
            when {
                consonants.containsKey(c) -> word.add(Unit(consonants[c], "a", true))
                matras.containsKey(c) -> {
                    val last = word.lastOrNull()
                    if (last != null && last.cons != null && last.inherent) {
                        last.vowel = matras[c]!!; last.inherent = false
                    } else {
                        word.add(Unit(null, matras[c]!!, false))
                    }
                }
                c == HALANT -> word.lastOrNull()?.let { it.vowel = ""; it.inherent = false }
                c == NUKTA -> word.lastOrNull()?.let { u -> u.cons?.let { u.cons = nuktaForms[it] ?: it } }
                c == 'ं' || c == 'ँ' -> {
                    val last = word.lastOrNull()
                    if (last != null) last.coda += "n" else word.add(Unit(null, "n", false))
                }
                c == 'ः' -> word.lastOrNull()?.let { it.coda += "h" }
                vowels.containsKey(c) -> word.add(Unit(null, vowels[c]!!, false))
                c in '०'..'९' -> { flush(); out.append('0' + (c - '०')) }
                c == '।' || c == '॥' -> { flush(); out.append(' ') }
                isDevanagari(c) -> { /* ignore other marks */ }
                else -> { flush(); out.append(c) }
            }
        }
        flush()
        return out.toString()
    }

    /** Hindi me likhte waqt bich ka aur aakhri 'a' bola nahi jaata: "कितने" -> "kitne", "समय" -> "samay". */
    private fun applySchwaDeletion(w: List<Unit>) {
        val n = w.size
        if (n > 1) {
            val last = w[n - 1]
            if (last.inherent && last.cons != null && last.coda.isEmpty()) last.vowel = ""
        }
        var i = n - 2
        while (i >= 1) {
            val u = w[i]
            val prev = w[i - 1]
            val next = w[i + 1]
            if (u.inherent && u.vowel == "a" && u.coda.isEmpty() &&
                prev.vowel.isNotEmpty() && next.cons != null && next.vowel.isNotEmpty()
            ) {
                u.vowel = ""
                i -= 2
            } else {
                i -= 1
            }
        }
    }

    /** Ek word ko normalize karo: lowercase, Roman, aur spelling ke farq hatao. */
    fun norm(token: String): String {
        var s = toLatin(token.lowercase())
        s = s.filter { it.isLetterOrDigit() }
        if (s.isEmpty()) return s
        s = s.replace('w', 'v').replace('z', 'j').replace('q', 'k').replace("x", "ks")
        s = s.replace("ph", "f").replace("ck", "k")
        s = s.replace("ee", "i").replace("oo", "u").replace("ou", "u")
        s = collapse(s)
        if (s.length > 2 && s.endsWith("ie")) s = s.dropLast(1)
        if (s.length > 2 && s.endsWith("ey")) s = s.dropLast(2) + "i"
        return s
    }

    /** Pura vakya normalize karo (har word alag). */
    fun normPhrase(text: String): String =
        text.split(Regex("[\\s,.!?;:\"'()\\-_/।॥]+")).map { norm(it) }.filter { it.isNotEmpty() }.joinToString(" ")

    private fun collapse(s: String): String {
        val sb = StringBuilder()
        for (c in s) if (sb.isEmpty() || sb.last() != c || c.isDigit()) sb.append(c)
        return sb.toString()
    }

    /** Sirf "consonant ka dhaancha" - vowels/h/y hata kar. "youtube" aur "yutyub" dono -> "tb". */
    fun skeleton(n: String): String = collapse(n.filter { it !in "aeiouyh" })
}

object Fuzzy {
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 0.0 (bilkul alag) se 1.0 (same) tak. Dono inputs pehle se normalized hone chahiye. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val lev = 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
        val sa = Translit.skeleton(a)
        val sb = Translit.skeleton(b)
        if (sa.isNotEmpty() && sa == sb) {
            if (sa.length >= 3 || lev >= 0.5) return maxOf(lev, 0.8)
        }
        return lev
    }
}
