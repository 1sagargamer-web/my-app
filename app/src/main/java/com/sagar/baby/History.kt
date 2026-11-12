package com.sagar.baby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Baby ne kya-kya kiya, uska record — SIRF isi phone par.
 * Kahin bheja nahi jaata; phone ka maalik khud History screen me dekh sakta hai aur mita sakta hai.
 */
object History {

    data class Entry(val time: Long, val icon: String, val title: String, val detail: String)

    private const val MAX = 300
    private val lock = Any()
    private var cache: ArrayList<Entry>? = null

    private fun file(c: Context) = File(c.filesDir, "history.json")

    private fun load(c: Context): ArrayList<Entry> {
        cache?.let { return it }
        val list = ArrayList<Entry>()
        try {
            val f = file(c)
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Entry(o.getLong("t"), o.optString("i"), o.optString("ti"), o.optString("d")))
                }
            }
        } catch (_: Exception) { }
        cache = list
        return list
    }

    fun log(c: Context, icon: String, title: String, detail: String = "") {
        synchronized(lock) {
            val list = load(c)
            list.add(Entry(System.currentTimeMillis(), icon, title, detail))
            while (list.size > MAX) list.removeAt(0)
            save(c, list)
        }
    }

    private fun save(c: Context, list: List<Entry>) {
        try {
            val arr = JSONArray()
            for (e in list) {
                arr.put(JSONObject().put("t", e.time).put("i", e.icon).put("ti", e.title).put("d", e.detail))
            }
            file(c).writeText(arr.toString())
        } catch (_: Exception) { }
    }

    fun all(c: Context): List<Entry> = synchronized(lock) { load(c).reversed() }

    fun clear(c: Context) {
        synchronized(lock) {
            cache = ArrayList()
            try { file(c).delete() } catch (_: Exception) { }
        }
    }

    private val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
    fun timeText(t: Long): String = fmt.format(Date(t))
}
