package com.sagar.baby

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.sagar.baby.core.SettingsPage
import com.sagar.baby.core.VolumeAction

/** Phone par asli kaam karne wale functions (background service se bhi chalte hain). */
class DeviceActions(private val ctx: Context) {

    val apps = AppFinder(ctx)

    fun has(perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    /** Permission nahi hai to Baby ki screen khol kar maango. true = pehle se hai. */
    fun ensure(perm: String): Boolean {
        if (has(perm)) return true
        start(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ASK_PERMS, true))
        return false
    }

    /**
     * Activity kholo. Background se kholne ke liye Accessibility service ka context use hota hai
     * (Android 10+ normal background service ko app kholne nahi deta).
     */
    private fun start(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AutoSendService.instance?.let { if (it.launch(intent)) return true }
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    // --- Apps -------------------------------------------------------------------------------

    fun launch(app: AppInfo): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return false
        return start(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openDialer(): Boolean = start(Intent(Intent.ACTION_DIAL))

    fun youtubeSearch(query: String): Boolean {
        val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(yt)) return true
        return start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))))
    }

    fun webSearch(query: String): Boolean {
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (start(i)) return true
        return start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
    }

    fun navigate(place: String): Boolean {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
            .setPackage("com.google.android.apps.maps")
        if (start(nav)) return true
        return start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
    }

    fun openSettings(page: SettingsPage): Boolean {
        val action = when (page) {
            SettingsPage.WIFI -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            SettingsPage.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsPage.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            SettingsPage.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsPage.MAIN -> Settings.ACTION_SETTINGS
        }
        return start(Intent(action)) || start(Intent(Settings.ACTION_SETTINGS))
    }

    fun goHome(): Boolean = start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openAccessibilitySettings() = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    // --- Messages & Calls -------------------------------------------------------------------

    fun whatsappPackage(): String? = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { apps.isInstalled(it) }

    /** Number ko international format me (WhatsApp ke liye): 98765 43210 -> 919876543210 */
    fun intlNumber(number: String, countryCode: String): String {
        val plus = number.trim().startsWith("+")
        var d = number.filter { it.isDigit() }
        if (plus) return d
        if (d.startsWith("00")) return d.drop(2)
        if (d.startsWith("0") && d.length == 11) d = d.drop(1)
        if (d.length == 10) return countryCode + d
        return d
    }

    /** WhatsApp chat khol kar message likh do. Auto-send chalu ho to service khud "Send" dabayegi. */
    fun sendWhatsApp(number: String, text: String, countryCode: String): Boolean {
        val pkg = whatsappPackage() ?: return false
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=${intlNumber(number, countryCode)}&text=${Uri.encode(text)}")
        return start(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
    }

    /** SMS seedha bhej do. false = permission nahi / fail. */
    fun sendSms(number: String, text: String): Boolean {
        if (!ensure(android.Manifest.permission.SEND_SMS)) return false
        return try {
            val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(number, null, parts, null, null)
            true
        } catch (e: Exception) {
            // Backup: SMS app me likh kar khol do
            start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", text))
            false
        }
    }

    fun shareTelegram(text: String): Boolean {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            .setPackage("org.telegram.messenger")
        return start(i)
    }

    /** Call lagao (permission na ho to dialer khol do). */
    fun call(number: String): Boolean {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        if (has(android.Manifest.permission.CALL_PHONE)) {
            if (start(Intent(Intent.ACTION_CALL, uri))) return true
        } else {
            ensure(android.Manifest.permission.CALL_PHONE)
        }
        return start(Intent(Intent.ACTION_DIAL, uri))
    }

    // --- Camera / Torch ---------------------------------------------------------------------

    fun openCamera(front: Boolean, autoCapture: Boolean): Boolean =
        start(
            Intent(ctx, CameraActivity::class.java)
                .putExtra(CameraActivity.EXTRA_FRONT, front)
                .putExtra(CameraActivity.EXTRA_AUTO, autoCapture),
        )

    fun torch(on: Boolean): Boolean = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) false else { cm.setTorchMode(id, on); true }
    } catch (e: Exception) {
        false
    }

    // --- Volume / Alarm / Info --------------------------------------------------------------

    fun volume(action: VolumeAction) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val s = AudioManager.STREAM_MUSIC
        when (action) {
            VolumeAction.UP -> repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
            VolumeAction.DOWN -> repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
            VolumeAction.MAX -> am.setStreamVolume(s, am.getStreamMaxVolume(s), AudioManager.FLAG_SHOW_UI)
            VolumeAction.MUTE -> am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun unmuteIfNeeded() {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.isStreamMute(AudioManager.STREAM_MUSIC)) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
    }

    fun setAlarm(hour: Int, minute: Int): Boolean = start(
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Baby AI")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
    )

    fun setTimer(seconds: Int): Boolean = start(
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Baby AI")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
    )

    fun openAlarms(): Boolean = start(Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun battery(): Pair<Int, Boolean> {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to bm.isCharging
    }
}
