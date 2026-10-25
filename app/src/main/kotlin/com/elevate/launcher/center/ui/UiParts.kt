package com.elevate.launcher.center.ui

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp

// ---------------------------------------------------------------------------------------------
// Small layout helpers used by every Center screen. They are extension functions so a screen can be
// written as a short list of "heading / para / card / button" calls.
// ---------------------------------------------------------------------------------------------

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

private fun LinearLayout.gapLp(bottom: Int = 8, top: Int = 0): LinearLayout.LayoutParams =
    lp(MATCH, WRAP).apply { bottomMargin = context.dp(bottom); topMargin = context.dp(top) }

fun LinearLayout.heading(text: String, top: Int = 14): TextView {
    val t = UiKit.text(context, text, 17f, Palette.text, true)
    addView(t, gapLp(8, top))
    return t
}

fun LinearLayout.para(text: String, dim: Boolean = true, sp: Float = 14f, bottom: Int = 8): TextView {
    val t = UiKit.text(context, text, sp, if (dim) Palette.dim else Palette.text)
    t.setLineSpacing(0f, 1.15f)
    addView(t, gapLp(bottom))
    return t
}

fun LinearLayout.gap(h: Int) { addView(UiKit.spacer(context, h)) }

fun LinearLayout.card(bottom: Int = 10, build: LinearLayout.() -> Unit): LinearLayout {
    val c = UiKit.card(context)
    c.build()
    addView(c, gapLp(bottom))
    return c
}

fun LinearLayout.cardTitle(text: String): TextView {
    val t = UiKit.text(context, text, 15f, Palette.text, true)
    addView(t, gapLp(4))
    return t
}

/** A tappable row card: title, optional sub text, and a chevron. */
fun LinearLayout.tapCard(title: String, sub: String = "", trailing: String = "›", onClick: () -> Unit): LinearLayout {
    val ctx = context
    val c = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = UiKit.pressable(Palette.surface, ctx.dpf(16f))
        setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(12), ctx.dp(12))
        minimumHeight = ctx.dp(56)
        setOnClickListener { onClick() }
    }
    val col = ctx.vbox()
    col.addView(UiKit.text(ctx, title, 15f, Palette.text, true))
    if (sub.isNotEmpty()) col.addView(UiKit.text(ctx, sub, 12.5f, Palette.dim))
    c.addView(col, lp(0, WRAP, 1f))
    if (trailing.isNotEmpty()) c.addView(UiKit.text(ctx, trailing, 18f, Palette.dim, true))
    addView(c, gapLp(8))
    return c
}

/** Two tiles side by side, used for the dashboard shortcuts. */
fun LinearLayout.tileRow(a: Triple<String, String, () -> Unit>, b: Triple<String, String, () -> Unit>?) {
    val ctx = context
    val row = ctx.hbox()
    fun tile(t: Triple<String, String, () -> Unit>): LinearLayout {
        val c = ctx.vbox()
        c.background = UiKit.pressable(Palette.surface, ctx.dpf(16f))
        c.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        c.minimumHeight = ctx.dp(72)
        c.setOnClickListener { t.third() }
        c.addView(UiKit.text(ctx, t.first, 15f, Palette.text, true))
        c.addView(UiKit.text(ctx, t.second, 12f, Palette.dim))
        return c
    }
    row.addView(tile(a), lp(0, WRAP, 1f).apply { rightMargin = ctx.dp(5) })
    if (b != null) row.addView(tile(b), lp(0, WRAP, 1f).apply { leftMargin = ctx.dp(5) })
    else row.addView(View(ctx), lp(0, WRAP, 1f).apply { leftMargin = ctx.dp(5) })
    addView(row, gapLp(10))
}

fun LinearLayout.actionButton(label: String, filled: Boolean = true, onClick: () -> Unit): TextView {
    val b = UiKit.button(context, label, filled, onClick)
    addView(b, gapLp(8))
    return b
}

fun LinearLayout.emptyState(title: String, msg: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    card {
        gravity = Gravity.CENTER_HORIZONTAL
        val t = UiKit.text(context, title, 16f, Palette.text, true)
        t.gravity = Gravity.CENTER
        addView(t, lp(MATCH, WRAP).apply { bottomMargin = context.dp(6) })
        val m = UiKit.text(context, msg, 13.5f, Palette.dim)
        m.gravity = Gravity.CENTER
        m.setLineSpacing(0f, 1.15f)
        addView(m, lp(MATCH, WRAP).apply { bottomMargin = context.dp(if (actionLabel != null) 12 else 0) })
        if (actionLabel != null && action != null) addView(UiKit.button(context, actionLabel, true, action))
    }
}

/** A quiet information box for honest notes ("this is an estimate", "not medical advice"). */
fun LinearLayout.noteCard(text: String, bottom: Int = 10): TextView {
    val t = UiKit.text(context, text, 13f, Palette.dim)
    t.setLineSpacing(0f, 1.15f)
    t.background = UiKit.rounded(Palette.surface2, context.dpf(14f))
    t.setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(10))
    addView(t, gapLp(bottom))
    return t
}

fun LinearLayout.divider() {
    val v = View(context)
    v.setBackgroundColor(Palette.divider)
    addView(v, lp(MATCH, 1).apply { topMargin = context.dp(6); bottomMargin = context.dp(6) })
}

fun LinearLayout.fieldLabel(text: String) {
    addView(UiKit.text(context, text, 12.5f, Palette.dim, true), lp(MATCH, WRAP).apply { topMargin = context.dp(10); bottomMargin = context.dp(4) })
}

/** A labelled row that shows a value and opens a picker when tapped. */
fun LinearLayout.pickRow(label: String, value: String, onClick: () -> Unit): LinearLayout {
    val ctx = context
    val r = ctx.hbox()
    r.background = UiKit.pressable(Palette.surface2, ctx.dpf(12f))
    r.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
    r.minimumHeight = ctx.dp(48)
    r.setOnClickListener { onClick() }
    r.addView(UiKit.text(ctx, label, 14f, Palette.dim), lp(0, WRAP, 1f))
    r.addView(UiKit.text(ctx, "$value  ›", 14f, Palette.text, true))
    addView(r, gapLp(8))
    return r
}

fun Context.field(hint: String, initial: String = "", multiline: Boolean = false, number: Boolean = false): EditText =
    EditText(this).apply {
        this.hint = hint
        setText(initial)
        setTextColor(Palette.text)
        setHintTextColor(Palette.dim)
        textSize = 15f
        background = UiKit.rounded(Palette.surface2, dpf(12f))
        setPadding(dp(14), dp(12), dp(14), dp(12))
        inputType = when {
            number -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        if (multiline) {
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        } else {
            setSingleLine(true)
        }
    }

/** A row of equal-width choices. Exactly one is selected. */
fun Context.chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit): LinearLayout {
    val row = hbox()
    val views = ArrayList<Pair<String, TextView>>()
    var cur = selected
    fun paint() {
        for ((k, v) in views) {
            val on = k == cur
            v.background = UiKit.rounded(if (on) Palette.accent else Palette.surface2, dpf(12f))
            v.setTextColor(if (on) Palette.onAccent else Palette.text)
        }
    }
    for ((k, label) in options) {
        val tv = UiKit.text(this, label, 13f, Palette.text, true)
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(6), dp(11), dp(6), dp(11))
        tv.setOnClickListener { cur = k; paint(); onSelect(k) }
        views.add(k to tv)
        row.addView(tv, lp(0, WRAP, 1f).apply { rightMargin = dp(6) })
    }
    paint()
    return row
}

/** Seven toggles, Monday to Sunday. [mask] bit 0 is Monday. */
fun Context.dayChips(mask: Int, onChange: (Int) -> Unit): LinearLayout {
    val row = hbox()
    var cur = mask
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    val views = ArrayList<TextView>()
    fun paint() {
        for (i in 0..6) {
            val on = (cur shr i) and 1 == 1
            views[i].background = UiKit.rounded(if (on) Palette.accent else Palette.surface2, dpf(12f))
            views[i].setTextColor(if (on) Palette.onAccent else Palette.text)
        }
    }
    for (i in 0..6) {
        val tv = UiKit.text(this, letters[i], 14f, Palette.text, true)
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, dp(11), 0, dp(11))
        tv.setOnClickListener { cur = cur xor (1 shl i); paint(); onChange(cur) }
        views.add(tv)
        row.addView(tv, lp(0, WRAP, 1f).apply { rightMargin = dp(5) })
    }
    paint()
    return row
}

/**
 * Shows a dialog with [content]. [onSave] returns true when the input was valid, which closes the dialog;
 * returning false keeps it open so the person can fix the input.
 */
fun Context.formDialog(title: String, content: View, okLabel: String = "Save", onSave: () -> Boolean) {
    val sv = ScrollView(this)
    sv.setPadding(dp(20), dp(8), dp(20), 0)
    sv.addView(content)
    val d = AlertDialog.Builder(this)
        .setTitle(title)
        .setView(sv)
        .setPositiveButton(okLabel, null)
        .setNegativeButton("Cancel", null)
        .create()
    d.show()
    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (onSave()) d.dismiss() }
}

fun pluralize(n: Int, one: String, many: String = one + "s"): String = if (n == 1) "1 $one" else "$n $many"

fun fmtNum(v: Double): String =
    if (v == Math.floor(v) && Math.abs(v) < 1e9) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

fun fmtClock(totalSeconds: Long): String {
    val s = Math.max(0L, totalSeconds)
    return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
}

/** Row of big numbers with small captions, e.g. "3  Current run". */
fun LinearLayout.statRow(items: List<Pair<String, String>>, bottom: Int = 8) {
    val ctx = context
    val row = ctx.hbox()
    for ((value, caption) in items) {
        val col = ctx.vbox()
        col.gravity = Gravity.CENTER_HORIZONTAL
        val v = UiKit.text(ctx, value, 22f, Palette.text, true)
        v.gravity = Gravity.CENTER
        val c = UiKit.text(ctx, caption, 11.5f, Palette.dim)
        c.gravity = Gravity.CENTER
        col.addView(v)
        col.addView(c)
        row.addView(col, lp(0, WRAP, 1f))
    }
    addView(row, lp(MATCH, WRAP).apply { bottomMargin = ctx.dp(bottom) })
}

/** Opens a date picker (no future dates). [initial] and the result are "yyyy-MM-dd". */
fun Context.pickDate(initial: String, onPick: (String) -> Unit) {
    val cal = com.elevate.launcher.center.data.Days.cal(initial)
    val d = android.app.DatePickerDialog(this, { _, y, m, day ->
        onPick(String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, day))
    }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
    d.datePicker.maxDate = System.currentTimeMillis()
    d.show()
}

/** Opens a time picker. [initial] and the result are "HH:mm" in 24-hour form. */
fun Context.pickTime(initial: String, onPick: (String) -> Unit) {
    val p = initial.split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: 22
    val m = p.getOrNull(1)?.toIntOrNull() ?: 0
    android.app.TimePickerDialog(this, { _, hh, mm ->
        onPick(String.format(java.util.Locale.US, "%02d:%02d", hh, mm))
    }, h, m, android.text.format.DateFormat.is24HourFormat(this)).show()
}

/** "23:30" -> "11:30 pm" style text for display. */
fun clockText(t: String): String {
    val p = t.split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: return t
    val m = p.getOrNull(1)?.toIntOrNull() ?: return t
    val suffix = if (h >= 12) "pm" else "am"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(java.util.Locale.US, "%d:%02d %s", h12, m, suffix)
}
