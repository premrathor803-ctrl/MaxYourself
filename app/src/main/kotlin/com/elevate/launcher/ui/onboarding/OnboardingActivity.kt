package com.elevate.launcher.ui.onboarding

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import com.elevate.launcher.ElevateApp
import com.elevate.launcher.core.BaseActivity
import com.elevate.launcher.core.DefaultHome
import com.elevate.launcher.core.Ds
import com.elevate.launcher.core.Ico
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.data.HomeRepo

/** First-launch setup: look, home layout, default launcher. Every step can be skipped. */
class OnboardingActivity : BaseActivity() {
    private var step = 0
    private val lastStep = 4
    private lateinit var body: LinearLayout
    private lateinit var nextBtn: android.widget.TextView
    private lateinit var backBtn: android.widget.TextView
    private lateinit var dots: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step") ?: 0

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(16))
        }
        scroll.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, lp(MATCH, 0, 1f))

        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(dots, lp(MATCH, dp(20)))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(8), dp(16), dp(16)) }
        backBtn = UiKit.button(this, "Back", false) { if (step > 0) { step--; render() } }
        nextBtn = UiKit.button(this, "Next", true) { if (step < lastStep) { step++; render() } else finishSetup() }
        nav.addView(backBtn, lp(0, WRAP, 1f).apply { rightMargin = dp(8) })
        nav.addView(nextBtn, lp(0, WRAP, 1f).apply { leftMargin = dp(8) })
        root.addView(nav, lp(MATCH, WRAP))

        setContentView(root)
        edgeToEdge(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step > 0) { step--; render() } else skip()
            }
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
    }

    private fun title(t: String) = UiKit.display(this, t, 32f, Palette.text).apply { setPadding(0, 0, 0, dp(12)) }

    /** A soft icon badge above each step's title, so the steps look like one family with the rest of the app. */
    private fun badge(kind: String) {
        body.addView(Ico.badge(this, kind, 64, Palette.accent), lp(dp(64), dp(64)).apply { bottomMargin = dp(20) })
    }
    private fun para(t: String) = UiKit.text(this, t, 15f, Palette.dim).apply { setLineSpacing(0f, 1.15f); setPadding(0, 0, 0, dp(14)) }

    private fun render() {
        body.removeAllViews()
        when (step) {
            0 -> {
                badge("bolt")
                body.addView(title("Welcome to Elevate"))
                body.addView(para("A light launcher for your phone, with a built-in Center for grooming basics, posture, home workouts, habits and goals."))
                body.addView(para("Everything stays on this phone. There is no account and nothing is uploaded."))
                body.addView(para("The Center shares general wellness information. It is not medical advice."))
            }
            1 -> {
                badge("palette")
                body.addView(title("Pick a look"))
                body.addView(para("You can change this any time in Settings."))
                val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for ((v, label) in listOf("dark" to "Dark", "system" to "System", "light" to "Light")) {
                    val sel = Prefs.theme == v
                    val b = UiKit.text(this, label, 14f, if (sel) Palette.onAccent else Palette.text, true).apply {
                        gravity = Gravity.CENTER
                        background = if (sel) Ds.primaryBg(this@OnboardingActivity, Palette.live(), 22f) else Ds.secondaryBg(this@OnboardingActivity, Palette.live(), 22f)
                        contentDescription = label + (if (sel) ", selected" else "")
                        setOnClickListener {
                            if (Prefs.theme != v) { Prefs.putString("theme", v); ElevateApp.applyNightMode(); recreate() }
                        }
                    }
                    modes.addView(b, lp(0, dp(44), 1f).apply { rightMargin = dp(6) })
                }
                body.addView(modes, lp(MATCH, WRAP).apply { bottomMargin = dp(18) })
                body.addView(UiKit.text(this, "Accent colour", 14f, Palette.dim).apply { setPadding(0, 0, 0, dp(8)) })
                val names = Palette.accents.keys.toList()
                for (r in 0..1) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    for (k in 0..3) {
                        val name = names.getOrNull(r * 4 + k) ?: break
                        val color = Palette.accents.getValue(name)
                        val sel = Prefs.accent == name
                        val dot = android.widget.FrameLayout(this).apply {
                            background = Ds.gradient(color, Palette.partnerOf(name, color), dpf(22f), false).apply { if (sel) setStroke(dp(3), Palette.text) }
                            contentDescription = (Palette.accentNames[name] ?: name) + (if (sel) ", selected" else "")
                            isClickable = true
                            setOnClickListener { if (Prefs.accent != name) { Prefs.putString("accent", name); recreate() } }
                            if (sel) addView(Ico.view(this@OnboardingActivity, "check", 22, Palette.onAccentFor(color)), android.widget.FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
                        }
                        row.addView(dot, lp(dp(44), dp(44)).apply { rightMargin = dp(12); bottomMargin = dp(10) })
                    }
                    body.addView(row)
                }
            }
            2 -> {
                badge("today")
                body.addView(title("Your home screen"))
                body.addView(para("Start with a few common apps in place, or begin with an empty page and add what you like."))
                val auto = Prefs.sp.getBoolean("onboard_autofill", true)
                body.addView(choice("Fill with common apps", "Phone, messages, camera, settings and more", auto) { Prefs.putBool("onboard_autofill", true); render() })
                body.addView(choice("Start empty", "Only the Center shortcut. Add apps from the drawer.", !auto) { Prefs.putBool("onboard_autofill", false); render() })
            }
            3 -> {
                badge("settings")
                body.addView(title("Home button"))
                if (DefaultHome.isDefault(this)) {
                    body.addView(para("Elevate is already your Home app."))
                } else {
                    body.addView(para("To open Elevate when you press Home, set it as your default Home app. This is optional: Elevate also works as a normal app."))
                    body.addView(UiKit.button(this, "Set as default Home app", true) { DefaultHome.request(this) }, lp(MATCH, WRAP))
                    body.addView(para("").apply { setPadding(0, 0, 0, dp(6)) })
                    body.addView(para("You can skip this and do it later in Settings."))
                }
            }
            else -> {
                badge("trophy")
                body.addView(title("All set"))
                body.addView(para("Swipe right on the home screen for the Center. Swipe up for all apps. Long-press an icon to move it, make folders or remove it."))
                body.addView(para("Settings has themes, grid size, gestures and performance options."))
            }
        }
        backBtn.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        nextBtn.text = if (step == lastStep) "Start" else if (step == 0) "Get started" else "Next"
        dots.removeAllViews()
        for (i in 0..lastStep) {
            val d = View(this).apply { background = UiKit.rounded(if (i == step) Palette.accent else Palette.surface2, dpf(4f)) }
            dots.addView(d, lp(if (i == step) dp(18) else dp(8), dp(8)).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
    }

    private fun choice(title: String, sub: String, selected: Boolean, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = UiKit.pressable(Palette.surface, dpf(16f), if (selected) Palette.accent else 0, if (selected) dp(2) else 0)
            addView(UiKit.text(this@OnboardingActivity, title, 16f, Palette.text, true))
            addView(UiKit.text(this@OnboardingActivity, sub, 13f, Palette.dim))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        }

    private fun skip() {
        Prefs.onboarded = true
        finish()
    }

    private fun finishSetup() {
        Prefs.onboarded = true
        if (!Prefs.sp.getBoolean("onboard_autofill", true)) {
            Prefs.seeded = true
            if (HomeRepo.items.none { it.kind == HomeRepo.KIND_CENTER }) {
                HomeRepo.add(HomeRepo.KIND_CENTER, "", "", "Center", HomeRepo.PAGE, 0, 0)
            }
        }
        finish()
    }
}
