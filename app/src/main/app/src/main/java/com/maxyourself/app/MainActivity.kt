package com.maxyourself.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this)
        text.text = "MaxYourself"
        text.textSize = 28f
        text.setTextColor(Color.BLACK)
        text.gravity = Gravity.CENTER

        setContentView(text)
    }
}
