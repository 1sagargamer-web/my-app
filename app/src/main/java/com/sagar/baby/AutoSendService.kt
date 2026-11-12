package com.sagar.baby

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Jab Baby WhatsApp me message likh kar kholti hai, yeh service "Send" button dabati hai. */
object AutoSend {
    @Volatile private var until = 0L
    fun arm() { until = System.currentTimeMillis() + 20_000 }
    fun isPending() = System.currentTimeMillis() < until
    fun clear() { until = 0L }

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = "${context.packageName}/${AutoSendService::class.java.name}"
        val meShort = "${context.packageName}/.${AutoSendService::class.java.simpleName}"
        return enabled.split(':').any { it.equals(me, true) || it.equals(meShort, true) }
    }
}

class AutoSendService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AutoSend.isPending()) return
        val pkg = event.packageName?.toString() ?: return
        if (!pkg.startsWith("com.whatsapp")) return
        val root = rootInActiveWindow ?: return
        val send = findSend(root, pkg) ?: return
        if (click(send)) {
            AutoSend.clear()
            // Thodi der baad Baby wapas aa jaaye taaki aage sun sake
            main.postDelayed({
                try {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            .putExtra(MainActivity.EXTRA_SENT, true),
                    )
                } catch (_: Exception) { }
            }, 1200)
        }
    }

    private fun findSend(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull { it.isVisibleToUser }?.let { return it }
        // Backup: "Send" naam wala button
        val labels = setOf("send", "भेजें", "भेजे", "enviar", "পাঠান", "पाठवा", "அனுப்பு")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val n = queue.removeFirst()
            visited++
            val desc = n.contentDescription?.toString()?.trim()?.lowercase()
            if (desc != null && desc in labels && n.isVisibleToUser) return n
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
