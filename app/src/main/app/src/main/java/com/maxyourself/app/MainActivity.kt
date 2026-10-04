package com.maxyourself.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 40, 24, 24)
        root.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "MaxYourself"
        title.textSize = 30f
        title.setTextColor(Color.BLACK)

        val subtitle = TextView(this)
        subtitle.text = "Become better every day"
        subtitle.textSize = 16f
        subtitle.setTextColor(Color.DKGRAY)
        subtitle.setPadding(0, 8, 0, 30)

        root.addView(title)
        root.addView(subtitle)

        val sections = arrayOf(
            "💪  Workout",
            "📈  Growth",
            "✅  Habits",
            "🧴  Grooming",
            "🎯  Goals",
            "⚙️  Settings"
        )

        for (section in sections) {
            val item = TextView(this)
            item.text = section
            item.textSize = 19f
            item.setTextColor(Color.BLACK)
            item.gravity = Gravity.CENTER_VERTICAL
            item.setPadding(20, 25, 20, 25)

            root.addView(item)
        }

        setContentView(root)
    }
}
