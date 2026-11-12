package com.sagar.baby

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Baby ka camera: bolne par khulta hai, 3-2-1 gin kar khud photo kheenchta hai. */
class CameraActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FRONT = "front"
        const val EXTRA_AUTO = "auto"
    }

    private lateinit var preview: PreviewView
    private lateinit var countdown: TextView
    private lateinit var flash: View
    private lateinit var thumb: ImageView
    private lateinit var hint: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val sound = MediaActionSound()
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var front = false
    private var auto = false
    private var autoDone = false
    private var counting = false
    private var lastUri: Uri? = null

    private val speaker get() = (application as BabyApp).speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        preview = findViewById(R.id.preview)
        countdown = findViewById(R.id.countdown)
        flash = findViewById(R.id.flash)
        thumb = findViewById(R.id.thumb)
        hint = findViewById(R.id.hint)

        front = intent.getBooleanExtra(EXTRA_FRONT, false)
        auto = intent.getBooleanExtra(EXTRA_AUTO, false)
        autoDone = savedInstanceState?.getBoolean("autoDone") ?: false

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCapture).setOnClickListener { if (!counting) startCountdown(1) }
        findViewById<View>(R.id.btnSwitch).setOnClickListener { front = !front; bindCamera() }
        thumb.setOnClickListener {
            lastUri?.let { uri ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: Exception) { }
            }
        }
        sound.load(MediaActionSound.SHUTTER_CLICK)

        val needed = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < 29) needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) bindCamera() else ActivityCompat.requestPermissions(this, missing.toTypedArray(), 5)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("autoDone", autoDone)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
        } else {
            Toast.makeText(this, "Camera permission chahiye", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val ic = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = ic
                var selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (!p.hasCamera(selector)) {
                    front = !front
                    selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                }
                p.unbindAll()
                p.bindToLifecycle(this, selector, pv, ic)
                if (auto && !autoDone) {
                    autoDone = true
                    handler.postDelayed({ startCountdown(3) }, 900)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Camera nahi khul paya: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCountdown(from: Int) {
        counting = true
        if (from > 1) {
            hint.text = "Smile please 😊"
            hint.visibility = View.VISIBLE
        }
        var n = from
        val tick = object : Runnable {
            override fun run() {
                if (n > 0) {
                    countdown.text = n.toString()
                    countdown.alpha = 1f
                    countdown.animate().alpha(0.2f).setDuration(900).start()
                    n--
                    handler.postDelayed(this, 1000)
                } else {
                    countdown.text = ""
                    hint.visibility = View.GONE
                    capture()
                }
            }
        }
        if (from == 1) { countdown.text = ""; capture() } else handler.post(tick)
    }

    private fun capture() {
        val ic = imageCapture ?: run { counting = false; return }
        val name = "Baby_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BabyAI")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
        ).build()

        sound.play(MediaActionSound.SHUTTER_CLICK)
        flash.alpha = 0.9f
        flash.animate().alpha(0f).setDuration(350).start()

        ic.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                counting = false
                lastUri = output.savedUri
                output.savedUri?.let { thumb.setImageURI(it) }
                speaker.say("Photo kheench li, gallery me save ho gayi.", "photo_done")
                History.log(this@CameraActivity, if (front) "🤳" else "📸", if (front) "Selfie li" else "Photo li", name)
                if (auto) handler.postDelayed({ if (!isFinishing) finish() }, 2600)
            }

            override fun onError(exception: ImageCaptureException) {
                counting = false
                speaker.speak("Sorry, photo nahi kheench paayi.")
                Toast.makeText(this@CameraActivity, "Photo error: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sound.release()
        super.onDestroy()
    }
}
