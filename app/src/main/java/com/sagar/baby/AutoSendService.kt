package com.sagar.baby

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** WhatsApp ka message khud bhejne ka intezaam. */
object AutoSend {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var until = 0L
    private var callback: ((Boolean) -> Unit)? = null
    private val timeout = Runnable { done(false) }

    fun arm(onResult: (Boolean) -> Unit) {
        main.removeCallbacks(timeout)
        callback = onResult
        until = System.currentTimeMillis() + 15_000
        main.postDelayed(timeout, 15_000)
    }

    fun isPending() = System.currentTimeMillis() < until

    fun done(ok: Boolean) {
        main.removeCallbacks(timeout)
        until = 0L
        val cb = callback
        callback = null
        cb?.let { main.post { it(ok) } }
    }

    fun isEnabled(context: Context): Boolean {
        if (AutoSendService.instance != null) return true
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = "${context.packageName}/${AutoSendService::class.java.name}"
        val meShort = "${context.packageName}/.${AutoSendService::class.java.simpleName}"
        return enabled.split(':').any { it.equals(me, true) || it.equals(meShort, true) }
    }
}

class AutoSendService : AccessibilityService() {

    companion object {
        @Volatile var instance: AutoSendService? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Glow.refresh(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Glow.refresh(applicationContext)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AutoSend.isPending()) return
        val pkg = event.packageName?.toString() ?: return
        if (!pkg.startsWith("com.whatsapp")) return
        val root = rootInActiveWindow ?: return
        val send = findSend(root, pkg) ?: return
        if (click(send)) AutoSend.done(true)
    }

    /** Is waqt screen par kaunsa app hai. */
    fun currentPackage(): String? = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }

    fun isWhatsAppChatOpen(): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        val pkg = root.packageName?.toString() ?: return false
        if (!pkg.startsWith("com.whatsapp")) return false
        return findEntry(root, pkg) != null
    }

    /** Jo WhatsApp chat khuli hai usme [text] likh kar bhej do. */
    fun sendInCurrentChat(text: String, onResult: (Boolean) -> Unit) {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (root == null || pkg == null || !pkg.startsWith("com.whatsapp")) { onResult(false); return }
        val entry = findEntry(root, pkg) ?: run { onResult(false); return }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        entry.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!entry.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { onResult(false); return }
        // Text aate hi mic button "Send" ban jaata hai
        main.postDelayed({
            val r = rootInActiveWindow
            val send = r?.let { findSend(it, pkg) }
            onResult(send != null && click(send))
        }, 450)
    }

    /** Background se bhi app kholne ke liye (accessibility ko Android rokta nahi). */
    fun launch(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Jo screen par khula hai uspe ek button dabana. [labels] = jin shabdon wala button dhoondhna hai.
     * Thodi der ruk kar try karta hai (page load hone ka waqt deta hai). [onResult] true = mil gaya aur daba diya.
     */
    fun tapButton(labels: Set<String>, tries: Int = 10, onResult: ((Boolean) -> Unit)? = null) {
        var left = tries
        val r = object : Runnable {
            override fun run() {
                val node = rootInActiveWindow?.let { findByText(it, labels) }
                if (node != null && click(node)) { onResult?.invoke(true); return }
                if (--left > 0) main.postDelayed(this, 600) else onResult?.invoke(false)
            }
        }
        main.post(r)
    }

    /** YouTube/kisi list ka sabse pehla video/result dabana. */
    fun tapFirstResult(pkg: String, tries: Int = 10, onResult: ((Boolean) -> Unit)? = null) {
        var left = tries
        val r = object : Runnable {
            override fun run() {
                val root = rootInActiveWindow
                val node = root?.let { firstVideo(it, pkg) }
                if (node != null && click(node)) { onResult?.invoke(true); return }
                if (--left > 0) main.postDelayed(this, 700) else onResult?.invoke(false)
            }
        }
        main.postDelayed(r, 800)
    }

    private fun findByText(root: AccessibilityNodeInfo, labels: Set<String>): AccessibilityNodeInfo? =
        bfs(root) { n ->
            if (!n.isVisibleToUser) return@bfs false
            val t = n.text?.toString()?.trim()?.lowercase()
            val d = n.contentDescription?.toString()?.trim()?.lowercase()
            (t != null && labels.any { t == it || t.startsWith("$it ") }) ||
                (d != null && labels.any { d == it || d.startsWith("$it ") })
        }

    private fun firstVideo(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        // YouTube ke video thumbnails
        listOf("$pkg:id/thumbnail", "$pkg:id/video_thumbnail", "$pkg:id/image").forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser && it.height() > 100 }?.let { return it }
        }
        // Backup: pehla bada clickable jisme "video" / play jaisa desc ho, upar ki taraf
        return bfs(root) { n ->
            n.isVisibleToUser && n.isClickable && n.height() > 150 &&
                (n.contentDescription?.contains("video", true) == true || n.contentDescription?.contains("play", true) == true)
        }
    }

    private fun AccessibilityNodeInfo.height(): Int {
        val r = android.graphics.Rect()
        getBoundsInScreen(r)
        return r.height()
    }

    private fun findEntry(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("$pkg:id/entry").firstOrNull { it.isVisibleToUser }?.let { return it }
        return bfs(root) { it.isEditable && it.isVisibleToUser }
    }

    private fun findSend(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull { it.isVisibleToUser }?.let { return it }
        val labels = setOf("send", "भेजें", "भेजे", "enviar", "পাঠান", "पाठवा", "அனுப்பு")
        return bfs(root) {
            val d = it.contentDescription?.toString()?.trim()?.lowercase()
            d != null && d in labels && it.isVisibleToUser
        }
    }

    private fun bfs(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 500) {
            val n = queue.removeFirst()
            visited++
            if (match(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    override fun onInterrupt() {}
}
