package com.elevate.launcher.center.ui

import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.ScrollView
import com.elevate.launcher.center.data.Activity
import com.elevate.launcher.center.data.ActivityRepo
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.GrowthMath
import com.elevate.launcher.center.data.Measure
import com.elevate.launcher.center.data.MeasureRepo
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.HeightUnits
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.Io

/** Add or edit one height reading, in centimetres or in feet and inches, with checks that catch typing slips. */
object HeightEditor {
    private fun fmt1(v: Double) = String.format(java.util.Locale.US, "%.1f", v)

    /**
     * [all] is the current list of readings (used to spot a duplicate or a value far from its neighbour).
     * [done] runs on the main thread after the reading was saved.
     */
    fun open(host: CenterActivity, existing: Measure?, all: List<Measure>, done: () -> Unit) {
        val inch = Center.heightUnit == "ftin"
        val box = host.vbox()
        var day = existing?.day ?: Days.today()

        var cmField: android.widget.EditText? = null
        var ftField: android.widget.EditText? = null
        var inField: android.widget.EditText? = null
        if (inch) {
            val fi = if (existing != null) HeightUnits.cmToFeetInches(existing.value) else null
            ftField = host.field("Feet", fi?.first?.toString() ?: "", number = true)
            inField = host.field("Inches", fi?.second?.let { fmt1(it) } ?: "", number = true)
            box.fieldLabel("Height in feet and inches (inches from 0 to 11.9)")
            val r = host.hbox()
            r.addView(ftField, lp(0, WRAP, 1f))
            r.addView(inField, lp(0, WRAP, 1f).apply { leftMargin = host.dp(12) })
            box.addView(r, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        } else {
            cmField = host.field("e.g. 172.5", if (existing != null) fmt1(existing.value) else "", number = true)
            box.fieldLabel("Height in centimetres (cm)")
            box.addView(cmField, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        }
        var dateRow: LinearLayout? = null
        dateRow = box.pickRow("Date measured", Days.pretty(day)) {
            host.pickDate(day) { d -> day = d; (dateRow?.getChildAt(1) as? TextView)?.text = Days.pretty(d) + "  ›" }
        }
        box.fieldLabel("Note (optional), e.g. morning, no shoes")
        val note = host.field("Note", existing?.note ?: "")
        box.addView(note, lp(MATCH, WRAP))
        box.para("Tip: measure at the same time of day, without shoes, against a wall. Readings can differ by 1 to 2 cm through the day.", sp = 12f, bottom = 0)

        val sv = ScrollView(host)
        sv.setPadding(host.dp(20), host.dp(8), host.dp(20), 0)
        sv.addView(box)
        val dialog = AlertDialog.Builder(host)
            .setTitle(if (existing == null) "Add measurement" else "Edit measurement")
            .setView(sv)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()

        var saving = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (saving) return@setOnClickListener
            val cm: Double? = if (inch) parseFeetInches(host, ftField!!.text.toString(), inField!!.text.toString())
            else parseCm(host, cmField!!.text.toString())
            if (cm == null) return@setOnClickListener
            if (day > Days.today()) { host.toast("The date cannot be in the future"); return@setOnClickListener }

            val points = all.map { GrowthMath.Point(it.id, it.day, it.value) }
            val ignore = existing?.id ?: -1L
            if (GrowthMath.isDuplicate(points, day, cm, ignore)) {
                host.toast("That reading is already recorded for this date")
                return@setOnClickListener
            }
            val text = note.text.toString().trim().take(120)
            val commit = {
                saving = true
                Io.write {
                    if (existing == null) MeasureRepo.add("height", cm, day, text)
                    else {
                        existing.value = cm; existing.day = day; existing.note = text
                        MeasureRepo.update(existing)
                    }
                    Io.ui { host.toast(if (existing == null) "Measurement saved" else "Measurement updated"); done() }
                }
                dialog.dismiss()
            }
            val odd = GrowthMath.suspiciousNeighbour(points, day, cm, ignore)
            if (odd != null) {
                Dialogs.confirm(
                    host, "Check this value",
                    HeightUnits.format(cm, inch) + " is " + GrowthMath.signed(cm - odd.cm, inch).removePrefix("+").removePrefix("-") +
                        " away from your reading of " + HeightUnits.format(odd.cm, inch) + " on " + Days.pretty(odd.day) + ". Is it right?",
                    "Save anyway"
                ) { commit() }
            } else commit()
        }
    }

    /** Whole centimetres or decimals, with a comma or a point. Shows a message and returns null when it is not usable. */
    fun parseCm(host: CenterActivity, text: String): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull()
        if (v == null || v.isNaN() || v.isInfinite()) { host.toast("Enter your height in centimetres, for example 172.5"); return null }
        if (!HeightUnits.isPlausibleCm(v)) {
            host.toast("Enter a height between ${HeightUnits.MIN_CM.toInt()} and ${HeightUnits.MAX_CM.toInt()} cm")
            return null
        }
        return v
    }

    fun parseFeetInches(host: CenterActivity, feet: String, inches: String): Double? {
        val f = feet.trim().replace(',', '.').toDoubleOrNull()
        val i = if (inches.isBlank()) 0.0 else inches.trim().replace(',', '.').toDoubleOrNull()
        if (f == null || i == null || f.isNaN() || i.isNaN() || f != Math.floor(f) || f < 0) {
            host.toast("Enter whole feet, and inches as a number, for example 5 and 7.5")
            return null
        }
        if (i < 0.0 || i >= 12.0) { host.toast("Inches must be from 0 to 11.9. Add a whole foot to the feet box instead."); return null }
        val cm = HeightUnits.feetInchesToCm(f, i)
        if (!HeightUnits.isPlausibleCm(cm)) { host.toast("That height does not look realistic. Check the feet and inches."); return null }
        return cm
    }
}

/** Log a piece of physical activity by hand (or fix one). */
object ActivityEditor {
    private val kinds = listOf("Walk", "Run", "Cycling", "Swimming", "Football or cricket", "Other sport", "Gym or strength", "Stretching", "Other")

    fun open(host: CenterActivity, existing: Activity?, done: () -> Unit) {
        val box = host.vbox()
        var day = existing?.day ?: Days.today()
        var kind = existing?.label?.takeIf { it in kinds } ?: (if (existing != null) "Other" else "Walk")
        val custom = host.field("What was it? (optional)", if (existing != null && existing.label !in kinds) existing.label else "")
        val minutes = host.field("Minutes, 1 to 600", if (existing != null && existing.minutes > 0) existing.minutes.toString() else "", number = true)
        val note = host.field("Note (optional)", existing?.note ?: "")

        box.fieldLabel("Type")
        var kindRow: LinearLayout? = null
        kindRow = box.pickRow("Activity", kind) {
            Dialogs.list(host, "Activity", kinds) { i -> kind = kinds[i]; (kindRow?.getChildAt(1) as? TextView)?.text = kind + "  ›" }
        }
        box.addView(custom, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        box.fieldLabel("How long?")
        box.addView(minutes, lp(MATCH, WRAP))
        var dateRow: LinearLayout? = null
        dateRow = box.pickRow("Date", Days.pretty(day)) {
            host.pickDate(day) { d -> day = d; (dateRow?.getChildAt(1) as? TextView)?.text = Days.pretty(d) + "  ›" }
        }
        box.fieldLabel("Note")
        box.addView(note, lp(MATCH, WRAP))

        host.formDialog(if (existing == null) "Log activity" else "Edit activity", box) {
            val m = minutes.text.toString().trim().toIntOrNull()
            if (m == null || m < 1 || m > 600) { host.toast("Enter whole minutes from 1 to 600"); return@formDialog false }
            if (day > Days.today()) { host.toast("The date cannot be in the future"); return@formDialog false }
            val label = custom.text.toString().trim().take(40).ifEmpty { kind }
            val n = note.text.toString().trim().take(120)
            Io.write {
                if (existing == null) ActivityRepo.add(Activity(0, day, ActivityRepo.KIND_ACTIVITY, "", label, m, n))
                else {
                    existing.day = day; existing.label = label; existing.minutes = m; existing.note = n
                    ActivityRepo.update(existing)
                }
                Io.ui { host.toast("Saved"); done() }
            }
            true
        }
    }
}
