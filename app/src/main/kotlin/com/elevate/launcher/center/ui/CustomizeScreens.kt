package com.elevate.launcher.center.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.elevate.launcher.ElevateApp
import com.elevate.launcher.center.data.DashLayout
import com.elevate.launcher.core.ColorMath
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.Ds
import com.elevate.launcher.core.Ico
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Motion
import com.elevate.launcher.core.MotionMath
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.RingsView
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.XpBar
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

/** The choices being edited. Nothing here touches the real settings until Apply. */
class StyleDraft(
    var theme: String,
    var amoled: Boolean,
    var accent: String,
    var cardStyle: String,
    var layout: String,
    var order: List<String>,
    var hidden: Set<String>,
    var nav: String,
    var motion: String,
    var name: String
) {
    fun scheme(ctx: android.content.Context): Palette.Scheme {
        val night = when (theme) {
            "dark" -> true
            "light" -> false
            else -> Palette.isNight(ctx)
        }
        return Palette.scheme(night, amoled, accent)
    }

    /** True when something differs from what is saved. */
    fun changed(): Boolean {
        val saved = fromPrefs()
        return theme != saved.theme || amoled != saved.amoled || accent != saved.accent || cardStyle != saved.cardStyle ||
            layout != saved.layout || order != saved.order || hidden != saved.hidden || nav != saved.nav ||
            motion != saved.motion || name.trim() != saved.name
    }

    companion object {
        fun fromPrefs(): StyleDraft = StyleDraft(
            Prefs.theme, Prefs.amoled, Prefs.accent, Prefs.cardStyle, Prefs.dashLayout,
            DashLayout.fullOrder(Prefs.dashLayout, Prefs.dashOrder),
            DashLayout.hiddenSet(Prefs.dashLayout, Prefs.dashOrder, Prefs.dashHidden),
            Prefs.navStyle, Prefs.motion, Prefs.userName
        )

        fun defaults(): StyleDraft = StyleDraft(
            "dark", false, "electric", "soft", "balanced",
            DashLayout.fullOrder("balanced", ""), DashLayout.hiddenSet("balanced", "", ""), "pill", "full", ""
        )
    }
}

/**
 * Themes, accent colours, card styles, dashboard layout, navigation indicator and animation, with a preview that follows
 * every choice before anything is applied. The preview uses sample numbers and says so.
 */
class CustomizeScreen(host: CenterActivity) : Screen(host) {
    override val title = "Customization centre"

    private val draft = StyleDraft.fromPrefs()
    private var previewHolder: FrameLayout? = null
    private var applyBar: LinearLayout? = null
    private var cardsBox: LinearLayout? = null

    override fun build(): View {
        val col = host.vbox()
        col.para("Try things here first. The preview follows every choice, and nothing changes in the app until you tap Apply.", sp = 13.5f)

        previewHolder = FrameLayout(host)
        col.addView(previewHolder, lp(MATCH, WRAP).apply { bottomMargin = host.dp(6) })
        renderPreview(false)
        col.para("Preview with sample numbers. Your real dashboard uses your own records.", sp = 12f, bottom = 8)

        // ---- theme
        col.heading("Theme", 6)
        col.addView(host.chips(listOf("dark" to "Dark", "light" to "Light", "system" to "System"), draft.theme) {
            draft.theme = it; changed()
        }, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        col.addView(host.chips(listOf("off" to "Standard dark", "on" to "Pure black"), if (draft.amoled) "on" else "off") {
            draft.amoled = it == "on"; changed()
        }, lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
        col.para("Pure black saves a little battery on OLED screens. It only shows when the theme is dark.", sp = 12f)

        // ---- accent
        col.heading("Accent colour", 8)
        val names = Palette.accents.keys.toList()
        for (r in 0..1) {
            val row = host.hbox()
            for (k in 0..3) {
                val i = r * 4 + k
                if (i >= names.size) break
                row.addView(swatch(names[i]), lp(0, WRAP, 1f))
            }
            col.addView(row, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        }

        // ---- card style
        col.heading("Card style", 8)
        val styleRow = host.hbox()
        for ((k, label) in Ds.cardStyles) styleRow.addView(styleTile(k, label), lp(0, WRAP, 1f).apply { rightMargin = host.dp(6) })
        col.addView(styleRow, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })

        // ---- dashboard
        col.heading("Dashboard layout", 8)
        col.addView(host.chips(Ds.layouts.map { it.key to it.value }, draft.layout) {
            draft.layout = it
            draft.order = DashLayout.fullOrder(it, "")
            draft.hidden = DashLayout.hiddenSet(it, "", "")
            drawCards()
            changed()
        }, lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
        col.para("Balanced shows everything. Focus keeps today's habits and workout in front. Explorer puts the sections first. Then switch cards on or off and move them below.", sp = 12f)
        cardsBox = host.vbox()
        col.addView(cardsBox, lp(MATCH, WRAP))
        drawCards()

        // ---- navigation
        col.heading("Navigation indicator", 10)
        col.addView(host.chips(Ds.navStyles.map { it.key to it.value }, draft.nav) { draft.nav = it; changed() }, lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
        col.para("How the home screen shows which page you are on. Pill stretches the current page, Dots keeps equal dots, Bar draws one segment per page.", sp = 12f)

        // ---- animation
        col.heading("Animation", 8)
        col.addView(host.chips(Ds.motions.map { it.key to it.value }, draft.motion) { draft.motion = it; changed() }, lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
        col.para("Full: smooth transitions, progress rings and number counting. Reduced: shorter and fewer. Off: nothing moves. Android's own \"remove animations\" setting is always followed, and the Performance profile in Settings can lower this further.", sp = 12f)

        // ---- name
        col.heading("Your name (optional)", 8)
        val name = host.field("Used in the greeting", draft.name)
        name.setSingleLine(true)
        name.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { draft.name = s?.toString() ?: ""; changed(false) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        col.addView(name, lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
        col.para("Stays on this phone. Leave it empty for a plain greeting.", sp = 12f)

        // ---- apply
        applyBar = host.vbox()
        col.addView(applyBar, lp(MATCH, WRAP).apply { topMargin = host.dp(12) })
        drawApply()
        return col
    }

    // -------------------------------------------------------------------------------------------- controls
    private fun changed(redrawPreview: Boolean = true) {
        if (redrawPreview) renderPreview(true)
        drawApply()
    }

    private fun swatch(key: String): View {
        val color = Palette.accents.getValue(key)
        val sel = draft.accent == key
        val box = host.vbox()
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.isClickable = true
        Motion.press(box)
        box.contentDescription = Palette.accentNames[key] + (if (sel) ", selected" else "")
        val dot = FrameLayout(host)
        val partner = Palette.partnerOf(key, color)
        dot.background = Ds.gradient(color, partner, host.dpf(22f), false).apply {
            if (sel) setStroke(host.dp(3), Palette.text)
        }
        if (sel) {
            val check = Ico.view(host, "check", 22, Palette.onAccentFor(color))
            dot.addView(check, FrameLayout.LayoutParams(host.dp(22), host.dp(22), Gravity.CENTER))
        }
        box.addView(dot, lp(host.dp(44), host.dp(44)))
        box.addView(UiKit.text(host, Palette.accentNames[key] ?: key, 11.5f, if (sel) Palette.text else Palette.dim, sel), lp(WRAP, WRAP).apply { topMargin = host.dp(4) })
        box.setOnClickListener {
            if (draft.accent != key) { draft.accent = key; changed(); refreshSwatches() }
        }
        return box
    }

    /** Accent and card tiles are drawn with the draft colours, so they are rebuilt (the draft itself is kept). */
    private fun refreshSwatches() { host.refresh() }

    private fun styleTile(key: String, label: String): View {
        val s = draft.scheme(host)
        val sel = draft.cardStyle == key
        val box = host.vbox()
        box.isClickable = true
        Motion.press(box)
        box.contentDescription = label + " card style" + (if (sel) ", selected" else "")
        val frame = FrameLayout(host)
        frame.setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(8))
        frame.background = UiKit.rounded(s.bg, host.dpf(14f), if (sel) Palette.accent else Palette.border, host.dp(if (sel) 2 else 1))
        val mini = View(host)
        mini.background = Ds.cardBg(host, key, s, 10f)
        frame.addView(mini, FrameLayout.LayoutParams(MATCH, host.dp(40)))
        box.addView(frame, lp(MATCH, WRAP))
        box.addView(UiKit.text(host, label, 12f, if (sel) Palette.text else Palette.dim, sel).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = host.dp(4) })
        box.setOnClickListener {
            if (draft.cardStyle != key) { draft.cardStyle = key; changed(); refreshSwatches() }
        }
        return box
    }

    private fun drawCards() {
        val box = cardsBox ?: return
        box.removeAllViews()
        for ((i, id) in draft.order.withIndex()) {
            val on = id !in draft.hidden
            val row = host.hbox()
            row.background = UiKit.rounded(Palette.surface, host.dpf(Ds.R_SMALL), Palette.border, Math.max(1, host.dp(1)))
            row.setPadding(host.dp(14), host.dp(6), host.dp(6), host.dp(6))
            row.addView(UiKit.text(host, DashLayout.names[id] ?: id, 14f, if (on) Palette.text else Palette.dim, on), lp(0, WRAP, 1f))
            val sw = androidx.appcompat.widget.SwitchCompat(host)
            sw.isChecked = on
            sw.contentDescription = "Show " + (DashLayout.names[id] ?: id)
            sw.setOnCheckedChangeListener { _, checked ->
                val h = draft.hidden.toMutableSet()
                if (checked) h.remove(id) else h.add(id)
                if (h.size >= DashLayout.all.size) { host.toast("At least one card stays on"); sw.isChecked = true; return@setOnCheckedChangeListener }
                draft.hidden = h
                drawCards(); changed(false)
            }
            row.addView(sw)
            val up = UiKit.button(host, "↑", false, repeatable = true) { draft.order = DashLayout.move(draft.order, id, -1).split(','); drawCards(); changed(false) }
            val down = UiKit.button(host, "↓", false, repeatable = true) { draft.order = DashLayout.move(draft.order, id, 1).split(','); drawCards(); changed(false) }
            up.contentDescription = "Move " + (DashLayout.names[id] ?: id) + " up"
            down.contentDescription = "Move " + (DashLayout.names[id] ?: id) + " down"
            up.alpha = if (i == 0) 0.35f else 1f
            down.alpha = if (i == draft.order.lastIndex) 0.35f else 1f
            up.minHeight = host.dp(40); down.minHeight = host.dp(40)
            up.setPadding(host.dp(14), host.dp(6), host.dp(14), host.dp(6)); down.setPadding(host.dp(14), host.dp(6), host.dp(14), host.dp(6))
            row.addView(up, lp(WRAP, WRAP).apply { leftMargin = host.dp(4) })
            row.addView(down, lp(WRAP, WRAP).apply { leftMargin = host.dp(4) })
            box.addView(row, lp(MATCH, WRAP).apply { topMargin = host.dp(6) })
        }
    }

    private fun drawApply() {
        val bar = applyBar ?: return
        bar.removeAllViews()
        val dirty = draft.changed()
        if (dirty) bar.para("You have changes that are not applied yet.", sp = 12.5f, bottom = 6)
        val apply = UiKit.button(host, "Apply", true) { apply() }
        apply.alpha = if (dirty) 1f else 0.45f
        apply.isEnabled = dirty
        bar.addView(apply, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        bar.addView(UiKit.button(host, "Reset to the default look", false) {
            Dialogs.confirm(host, "Reset to the default look?", "Theme, colours, card style, dashboard layout, navigation and animation go back to the defaults. Your records are not touched.", "Reset") {
                val d = StyleDraft.defaults()
                save(d)
            }
        }, lp(MATCH, WRAP))
    }

    private fun apply() { save(draft) }

    private fun save(d: StyleDraft) {
        val (order, hidden) = DashLayout.toPrefs(d.layout, d.order, d.hidden)
        Prefs.putString("theme", d.theme)
        Prefs.putBool("amoled", d.amoled)
        Prefs.putString("accent", d.accent)
        Prefs.putString("card_style", d.cardStyle)
        Prefs.putString("dash_layout", d.layout)
        Prefs.putString("dash_order", order)
        Prefs.putString("dash_hidden", hidden)
        Prefs.putString("nav_style", d.nav)
        Prefs.putString("motion", d.motion)
        Prefs.userName = d.name
        Motion.refresh()
        ElevateApp.applyNightMode()
        // Colours are read when a screen is built, so the Center starts again and comes straight back to this screen.
        host.toast("Applied")
        host.restartAt("customize")
    }

    // -------------------------------------------------------------------------------------------- preview
    private fun renderPreview(animate: Boolean) {
        val holder = previewHolder ?: return
        holder.removeAllViews()
        val s = draft.scheme(host)
        val style = draft.cardStyle
        val root = LinearLayout(host)
        root.orientation = LinearLayout.VERTICAL
        root.background = UiKit.rounded(s.bg, host.dpf(22f), s.border, Math.max(1, host.dp(1)))
        root.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(14))
        root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        val top = host.hbox()
        top.addView(UiKit.display(host, "Good evening", 22f, s.text), lp(0, WRAP, 1f))
        top.addView(UiKit.pill(host, "LEVEL 3", ColorMath.withAlpha(s.accent, 0.2f), s.accentText, 10f))
        root.addView(top, lp(MATCH, WRAP).apply { bottomMargin = host.dp(12) })

        val hero = UiKit.styledCard(host, s, if (style == "flat" || style == "outline") style else "accent")
        val row = host.hbox()
        val rings = RingsView(host)
        rings.colors(s.divider, s.text, s.dim)
        rings.center("3/5", "habits")
        val level = MotionMath.level(draft.motion, Prefs.perf, if (Motion.systemAllows) 1f else 0f)
        rings.set(
            listOf(
                RingsView.Ring(0.6f, s.accent, s.accent2),
                RingsView.Ring(0.4f, s.accent2, ColorMath.mix(s.accent2, 0xFFFFFFFF.toInt(), 0.3f))
            ),
            animate && level > 0, null, MotionMath.duration(800L, level)
        )
        row.addView(rings, lp(host.dp(84), host.dp(84)))
        val right = host.vbox()
        right.addView(UiKit.display(host, "240 XP", 24f, s.text))
        val bar = XpBar(host)
        bar.colors(s.accent, s.accent2, s.divider)
        bar.set(0.6f, false)
        right.addView(bar, lp(MATCH, WRAP).apply { topMargin = host.dp(8) })
        right.addView(UiKit.text(host, "60 XP to Level 4", 11.5f, s.dim), lp(MATCH, WRAP).apply { topMargin = host.dp(5) })
        row.addView(right, lp(0, WRAP, 1f).apply { leftMargin = host.dp(14) })
        hero.addView(row)
        root.addView(hero, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })

        val cards = host.hbox()
        for ((n, sub) in listOf("Drink water" to "Done", "Posture break" to "To do")) {
            val c = UiKit.styledCard(host, s, style)
            c.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10))
            c.addView(UiKit.text(host, n, 13f, s.text, true))
            c.addView(UiKit.text(host, sub, 11.5f, if (sub == "Done") s.accentText else s.dim, true))
            cards.addView(c, lp(0, WRAP, 1f).apply { if (n == "Drink water") rightMargin = host.dp(5) else leftMargin = host.dp(5) })
        }
        root.addView(cards, lp(MATCH, WRAP).apply { bottomMargin = host.dp(12) })

        val buttons = host.hbox()
        val primary = UiKit.text(host, "Start", 13.5f, s.onAccent, true).apply {
            gravity = Gravity.CENTER
            background = Ds.primaryBg(host, s)
            setPadding(host.dp(16), host.dp(10), host.dp(16), host.dp(10))
        }
        val secondary = UiKit.text(host, "Details", 13.5f, s.text, true).apply {
            gravity = Gravity.CENTER
            background = Ds.secondaryBg(host, s)
            setPadding(host.dp(16), host.dp(10), host.dp(16), host.dp(10))
        }
        buttons.addView(primary, lp(0, WRAP, 1f))
        buttons.addView(secondary, lp(0, WRAP, 1f).apply { leftMargin = host.dp(8) })
        root.addView(buttons, lp(MATCH, WRAP).apply { bottomMargin = host.dp(12) })

        // the home screen page indicator in the chosen style, on page 2 of 3
        root.addView(indicatorPreview(s, draft.nav), lp(MATCH, host.dp(14)))
        holder.addView(root, FrameLayout.LayoutParams(MATCH, WRAP))
        if (animate) Motion.fadeIn(root, 160L)
    }

    private fun indicatorPreview(s: Palette.Scheme, style: String): View {
        val row = LinearLayout(host)
        row.gravity = Gravity.CENTER
        val dim = ColorMath.withAlpha(s.text, 0.3f)
        for (i in 0..2) {
            val sel = i == 1
            val v = View(host)
            val w: Int
            val h: Int
            val r: Float
            when (style) {
                "dots" -> { w = 7; h = 7; r = 4f }
                "bar" -> { w = 26; h = 3; r = 2f }
                else -> { w = if (sel) 20 else 7; h = 7; r = 4f }
            }
            v.background = UiKit.rounded(if (sel) s.accent else dim, host.dpf(r))
            row.addView(v, lp(host.dp(w), host.dp(h)).apply { leftMargin = host.dp(4); rightMargin = host.dp(4) })
        }
        return row
    }
}
