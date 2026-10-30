package com.elevate.launcher.privacy

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.elevate.launcher.core.BaseActivity
import com.elevate.launcher.core.Launch
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppMeta
import com.elevate.launcher.data.HomeRepo
import com.elevate.launcher.ui.IconLoader

/**
 * The private list of apps hidden from Elevate. Opening it requires an unlocked session (set by the calculator
 * after the right code, or directly when no code has been set). The session ends when the screen is left.
 */
class HiddenAppsActivity : BaseActivity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Auth.init(this)
        if (!Auth.sessionValid()) {
            finish()
            return
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Keep the list out of screenshots and the recent-apps preview.
        if (intent.getStringExtra("ci_nosecure") == null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(8), dp(4))
        }
        val back = UiKit.text(this, "‹", 32f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        bar.addView(back, lp(dp(48), dp(48)))
        bar.addView(UiKit.text(this, "Hidden apps", 20f, Palette.text, true), lp(0, WRAP, 1f))
        bar.addView(UiKit.text(this, "Lock", 14f, Palette.accent, true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { Auth.lock(); finish() }
        })
        root.addView(bar, lp(MATCH, WRAP))
        val sv = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        sv.addView(list, lp(MATCH, WRAP))
        root.addView(sv, lp(MATCH, 0, 1f))
        setContentView(root)
        edgeToEdge(root)
        render()
    }

    override fun onStart() {
        super.onStart()
        if (!Auth.sessionValid()) finish()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        Auth.touch()
    }

    override fun onStop() {
        // Leaving this screen (home, another app, screen off) locks it again.
        Auth.lock()
        super.onStop()
    }

    private fun note(text: String) {
        val t = UiKit.text(this, text, 13f, Palette.dim)
        t.setLineSpacing(0f, 1.15f)
        t.background = UiKit.rounded(Palette.surface2, dpf(14f))
        t.setPadding(dp(14), dp(10), dp(14), dp(10))
        list.addView(t, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
    }

    private fun heading(text: String) {
        list.addView(UiKit.text(this, text, 17f, Palette.text, true), lp(MATCH, WRAP).apply { topMargin = dp(12); bottomMargin = dp(8) })
    }

    private fun render() {
        list.removeAllViews()
        note("These apps are hidden from Elevate's drawer, search and home screen. They are not hidden from Android: they still show in system Settings, in other launchers and in phone-wide search, and they stay installed.")

        val keys = AppMeta.hiddenKeys()
        val present = keys.mapNotNull { AppCatalog.get(it) }.sortedBy { it.displayLabel.lowercase() }
        val missing = keys.filter { AppCatalog.get(it) == null }

        heading("Hidden (${present.size})")
        if (present.isEmpty()) {
            note("No apps are hidden. Use \"Hide apps\" below, or long-press an app in the drawer and choose Hide in Elevate.")
        }
        for (e in present) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.pressable(Palette.surface, dpf(16f))
                setPadding(dp(12), dp(8), dp(8), dp(8))
                setOnClickListener { Launch.app(this@HiddenAppsActivity, e.pkg, e.cls, this) }
            }
            val icon = ImageView(this).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            IconLoader.bindEntry(icon, this, e, dp(40))
            row.addView(icon, lp(dp(40), dp(40)))
            row.addView(UiKit.text(this, e.displayLabel, 15f, Palette.text, true), lp(0, WRAP, 1f).apply { leftMargin = dp(12) })
            row.addView(UiKit.button(this, "Show again", false) {
                AppMeta.setHidden(e.key, false)
                toast("${e.displayLabel} shows in the drawer again")
                render()
            })
            list.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(8) })
        }

        if (missing.isNotEmpty()) {
            heading("No longer installed")
            for (k in missing) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiKit.rounded(Palette.surface, dpf(16f))
                    setPadding(dp(14), dp(8), dp(8), dp(8))
                }
                row.addView(UiKit.text(this, k.substringBefore('/'), 13f, Palette.dim), lp(0, WRAP, 1f))
                row.addView(UiKit.button(this, "Remove", false) {
                    AppMeta.setHidden(k, false)
                    render()
                })
                list.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(8) })
            }
        }

        list.addView(UiKit.button(this, "Hide apps", true) { pickToHide() }, lp(MATCH, WRAP).apply { topMargin = dp(8); bottomMargin = dp(8) })
        list.addView(UiKit.button(this, "Privacy settings", false) {
            startActivity(android.content.Intent(this, PrivacyActivity::class.java))
        }, lp(MATCH, WRAP))
    }

    private fun pickToHide() {
        val apps = AppCatalog.visible()
        if (apps.isEmpty()) { toast("Apps are still loading, try again"); return }
        val labels = apps.map { it.displayLabel }.toTypedArray()
        val checked = BooleanArray(apps.size)
        AlertDialog.Builder(this)
            .setTitle("Hide from Elevate")
            .setMultiChoiceItems(labels, checked) { _, which, on -> checked[which] = on }
            .setPositiveButton("Hide selected") { _, _ ->
                var n = 0
                for (i in apps.indices) if (checked[i]) {
                    AppMeta.setHidden(apps[i].key, true)
                    HomeRepo.removeByKey(apps[i].key)
                    n++
                }
                if (n > 0) toast("Hidden: $n")
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
