package com.sagar.baby

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/** Baby ne is phone par kya-kya kiya — sab yahin dikhta hai, kahin bheja nahi jaata. */
class HistoryActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_main)
            fitsSystemWindows = true
            setPadding((18 * dp).toInt(), (12 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt())
        }
        root.addView(TextView(this).apply {
            text = "🕓 History"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = "Baby ne is phone par jo kiya, sirf yahin dikhta hai. Yeh kahin bheja nahi jaata."
            setTextColor(ContextCompat.getColor(context, R.color.text_dim))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val entries = History.all(this)
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Abhi kuch nahi hua."
                setTextColor(ContextCompat.getColor(context, R.color.text_dim))
                gravity = Gravity.CENTER
                setPadding(0, (40 * dp).toInt(), 0, 0)
            })
        }
        for (e in entries) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundResource(R.drawable.glass)
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            }
            card.addView(TextView(this).apply {
                text = e.icon.ifBlank { "•" }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(0, 0, (12 * dp).toInt(), 0)
            })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply {
                text = e.title
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
            if (e.detail.isNotBlank()) {
                col.addView(TextView(this).apply {
                    text = e.detail
                    setTextColor(0xFFB8B8E0.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                })
            }
            col.addView(TextView(this).apply {
                text = History.timeText(e.time)
                setTextColor(ContextCompat.getColor(context, R.color.text_dim))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
            card.addView(col)
            list.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            })
        }

        val scroll = ScrollView(this).apply { addView(list); isVerticalScrollBarEnabled = false }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "🗑 Saara mitado"
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setMessage("Puri history mitani hai?")
                    .setPositiveButton("Haan") { _, _ -> History.clear(this@HistoryActivity); recreate() }
                    .setNegativeButton("Nahi", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }
}
