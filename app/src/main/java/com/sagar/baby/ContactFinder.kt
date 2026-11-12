package com.sagar.baby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.sagar.baby.core.Fuzzy
import com.sagar.baby.core.Translit

data class Contact(val name: String, val number: String)
data class ContactMatch(val contact: Contact, val score: Double)

/** Bola gaya naam (Hindi ya English) phone ke contacts me dhundho. */
class ContactFinder(private val context: Context) {

    private var cache: List<Pair<Contact, String>> = emptyList()
    private var cacheTime = 0L

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

    private fun load(): List<Pair<Contact, String>> {
        val now = System.currentTimeMillis()
        if (cache.isNotEmpty() && now - cacheTime < 60_000) return cache
        if (!hasPermission()) return emptyList()
        val list = ArrayList<Pair<Contact, String>>()
        val seen = HashSet<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                val key = name + "|" + number.filter { it.isDigit() }.takeLast(10)
                if (!seen.add(key)) continue
                list.add(Contact(name, number) to Translit.normPhrase(name))
            }
        }
        cache = list
        cacheTime = now
        return list
    }

    /** Sabse milte-julte contacts, best pehle. Ek hi naam ke kai number ho to pehla. */
    fun find(spoken: String): List<ContactMatch> {
        val digits = spoken.filter { it.isDigit() }
        if (digits.length >= 7) return listOf(ContactMatch(Contact(digits, digits), 1.0))

        val ns = Translit.normPhrase(spoken)
        if (ns.isEmpty()) return emptyList()
        val compact = ns.replace(" ", "")

        val scored = load().map { (contact, cn) ->
            val cCompact = cn.replace(" ", "")
            var s = Fuzzy.similarity(compact, cCompact)
            for (w in cn.split(" ")) s = maxOf(s, Fuzzy.similarity(compact, w) * 0.95)
            if (compact.length >= 3 && cCompact.startsWith(compact)) s = maxOf(s, 0.9)
            ContactMatch(contact, s)
        }
        return scored.sortedByDescending { it.score }
            .distinctBy { it.contact.name.lowercase() }
            .take(5)
    }

    fun clearCache() { cache = emptyList() }
}
