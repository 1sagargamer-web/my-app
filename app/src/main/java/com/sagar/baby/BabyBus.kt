package com.sagar.baby

import java.util.concurrent.CopyOnWriteArrayList

/** Background service aur screen ke beech khabar pahunchane wala (sab main thread par). */
object BabyBus {

    enum class Mode { OFF, IDLE, LISTENING, SPEAKING, PAUSED, MEDIA }

    interface Listener {
        fun onMode(mode: Mode, note: String) {}
        fun onPartial(text: String) {}
        fun onChat(user: Boolean, text: String) {}
        fun onLevel(db: Float) {}
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile var mode: Mode = Mode.OFF
        private set
    @Volatile var note: String = ""
        private set

    /** Baby ki screen abhi dikh rahi hai? (Nahi = background mode) */
    @Volatile var uiVisible = false

    /** Purani baat-cheet, taaki app dobara kholne par dikhe. */
    val history = ArrayList<Pair<Boolean, String>>()

    fun add(l: Listener) { listeners.add(l) }
    fun remove(l: Listener) { listeners.remove(l) }

    fun setMode(m: Mode, n: String = "") {
        mode = m
        note = n
        listeners.forEach { it.onMode(m, n) }
        Glow.setMode(m)
    }

    fun partial(text: String) = listeners.forEach { it.onPartial(text) }

    fun chat(user: Boolean, text: String) {
        history.add(user to text)
        while (history.size > 40) history.removeAt(0)
        listeners.forEach { it.onChat(user, text) }
    }

    fun level(db: Float) {
        listeners.forEach { it.onLevel(db) }
        Glow.setLevel(db)
    }
}
