package com.elevate.launcher.core

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * A simple full-screen page: back arrow, title, and a scrolling column the subclass fills in [render].
 * Call [rebuild] after something changed to redraw the page while keeping the scroll position.
 */
abstract class PageActivity : BaseActivity() {
    protected lateinit var col: LinearLayout
    private lateinit var scroll: ScrollView
    protected abstract val pageTitle: String
    protected abstract fun render(col: LinearLayout)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(8), dp(4))
        }
        bar.addView(UiKit.text(this, "‹", 32f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back"
            setOnClickListener { finish() }
        }, lp(dp(48), dp(48)))
        bar.addView(UiKit.text(this, pageTitle, 20f, Palette.text, true).apply { isSingleLine = true }, lp(0, WRAP, 1f))
        root.addView(bar, lp(MATCH, WRAP))
        scroll = ScrollView(this)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, lp(MATCH, 0, 1f))
        setContentView(root)
        edgeToEdge(root)
        render(col)
    }

    fun rebuild() {
        val y = scroll.scrollY
        col.removeAllViews()
        render(col)
        scroll.post { scroll.scrollTo(0, y) }
    }
}
