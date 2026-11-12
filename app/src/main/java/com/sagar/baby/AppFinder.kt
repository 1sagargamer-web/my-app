package com.sagar.baby

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.sagar.baby.core.Fuzzy
import com.sagar.baby.core.Translit

data class AppInfo(val label: String, val pkg: String)

/** Bola gaya naam se phone ka app dhundho ("यूट्यूब", "insta", "free fire"...). */
class AppFinder(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    private val aliases: Map<String, List<String>> = mapOf(
        listOf("youtube", "yutyub", "yutub", "utub", "yt") to listOf("com.google.android.youtube"),
        listOf("whatsapp", "vatsap", "vhatsap", "watsap", "vatsaep", "vhatsaep") to listOf("com.whatsapp", "com.whatsapp.w4b"),
        listOf("insta", "instagram", "instagraam") to listOf("com.instagram.android", "com.instagram.lite"),
        listOf("fb", "facebook", "fesbuk", "faisbuk") to listOf("com.facebook.katana", "com.facebook.lite"),
        listOf("chrome", "krom", "browser", "broser", "internet") to listOf("com.android.chrome"),
        listOf("gmail", "email", "mail", "jimail") to listOf("com.google.android.gm"),
        listOf("maps", "map", "googlemaps", "naksha") to listOf("com.google.android.apps.maps"),
        listOf("playstore", "store", "plestor") to listOf("com.android.vending"),
        listOf("gallery", "galari", "gailari", "photos", "photo", "foto", "fotos", "tasvir") to listOf(
            "com.google.android.apps.photos", "com.miui.gallery", "com.sec.android.gallery3d",
            "com.coloros.gallery3d", "com.oneplus.gallery", "com.vivo.gallery", "com.android.gallery3d",
        ),
        listOf("calculator", "kalkuletar", "calci", "kalkulator") to listOf(
            "com.google.android.calculator", "com.miui.calculator", "com.sec.android.app.popupcalculator",
            "com.coloros.calculator", "com.oneplus.calculator", "com.android.calculator2",
        ),
        listOf("clock", "ghadi", "ghari", "alarm") to listOf(
            "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
            "com.coloros.alarmclock",
        ),
        listOf("calendar", "kalendar", "calender") to listOf("com.google.android.calendar", "com.samsung.android.calendar"),
        listOf("telegram") to listOf("org.telegram.messenger"),
        listOf("snapchat", "snap") to listOf("com.snapchat.android"),
        listOf("spotify") to listOf("com.spotify.music"),
        listOf("google", "gugal", "gugl") to listOf("com.google.android.googlequicksearchbox"),
        listOf("phonepe", "fonpe", "fonpay") to listOf("com.phonepe.app"),
        listOf("paytm", "petiem") to listOf("net.one97.paytm"),
        listOf("gpay", "googlepay", "gugalpe") to listOf("com.google.android.apps.nbu.paisa.user"),
        listOf("messages", "message", "sms", "maisej") to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
        listOf("contacts", "contact", "kontekt") to listOf("com.google.android.contacts", "com.samsung.android.app.contacts"),
        listOf("files", "filemanager", "fail") to listOf("com.google.android.apps.nbu.files"),
        listOf("ytmusic", "youtubemusic") to listOf("com.google.android.apps.youtube.music"),
    ).flatMap { (keys, pkgs) -> keys.map { Translit.norm(it) to pkgs } }.toMap()

    private fun launchable(): List<Pair<AppInfo, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .filter { it.pkg != context.packageName }
            .map { it to Translit.normPhrase(it.label) }
    }

    fun isInstalled(pkg: String): Boolean = pm.getLaunchIntentForPackage(pkg) != null

    fun find(spoken: String): AppInfo? {
        val n = Translit.normPhrase(spoken)
        val compact = n.replace(" ", "")
        if (compact.isEmpty()) return null
        val apps = launchable()

        // 1) Jaane-pehchaane naam
        val aliasKey = aliases.keys.maxByOrNull { Fuzzy.similarity(compact, it) }
        if (aliasKey != null && Fuzzy.similarity(compact, aliasKey) >= 0.85) {
            for (pkg in aliases[aliasKey]!!) {
                apps.firstOrNull { it.first.pkg == pkg }?.let { return it.first }
            }
        }

        // 2) Phone ke app labels se fuzzy match
        var best: AppInfo? = null
        var bestScore = 0.0
        for ((app, label) in apps) {
            val lc = label.replace(" ", "")
            var s = Fuzzy.similarity(compact, lc)
            for (w in label.split(" ")) s = maxOf(s, Fuzzy.similarity(compact, w) * 0.9)
            if (compact.length >= 4 && lc.contains(compact)) s = maxOf(s, 0.85)
            if (s > bestScore) { bestScore = s; best = app }
        }
        return if (bestScore >= 0.68) best else null
    }
}
