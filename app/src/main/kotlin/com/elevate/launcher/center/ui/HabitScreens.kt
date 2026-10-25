package com.elevate.launcher.center.ui

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.elevate.launcher.center.content.Starters
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.Habit
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

private fun stateKind(s: HState): Int = when (s) {
    HState.COMPLETED -> 1
    HState.SKIPPED -> 2
    HState.NOT_COMPLETED -> 3
    HState.NOT_SCHEDULED -> 4
    HState.PENDING -> 0
}

private fun stateWords(s: HState, day: String): String {
    val isToday = day == Days.today()
    return when (s) {
        HState.COMPLETED -> "Completed"
        HState.SKIPPED -> "Skipped"
        HState.NOT_COMPLETED -> "Not completed"
        HState.NOT_SCHEDULED -> "Not scheduled"
        HState.PENDING -> if (isToday) "Still to do today" else "Upcoming"
    }
}

fun stateOfDay(h: Habit, day: String): HState =
    HabitRepo.stateOf(h, day, HabitRepo.logs(h.id, day, day), Days.today(), WorkoutRepo.completedDays(day, day))

/** Dialog to change what is recorded for one habit on one day. */
fun habitStateDialog(host: CenterActivity, h: Habit, day: String, onChanged: () -> Unit) {
    val items = ArrayList<String>()
    val acts = ArrayList<Int>()
    if (day <= Days.today()) {
        items.add("Mark completed"); acts.add(1)
        items.add("Mark skipped (does not break your run)"); acts.add(2)
        items.add("Clear (back to not recorded)"); acts.add(0)
    }
    if (items.isEmpty()) { host.toast("This day has not happened yet"); return }
    Dialogs.list(host, h.name + " · " + Days.pretty(day), items) { i ->
        HabitRepo.setState(h.id, day, acts[i])
        onChanged()
    }
}

/** One habit on one day: status circle, name and a short description. */
fun LinearLayout.habitRow(h: Habit, day: String, onChanged: () -> Unit, onOpen: () -> Unit) {
    val ctx = context
    val st = stateOfDay(h, day)
    val row = ctx.hbox()
    row.background = UiKit.pressable(Palette.surface, ctx.dpf(16f))
    row.setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(14), ctx.dp(6))
    row.minimumHeight = ctx.dp(56)
    row.setOnClickListener { onOpen() }
    row.setOnLongClickListener { habitStateDialog(ctx as CenterActivity, h, day, onChanged); true }

    val dot = StateDot(ctx)
    dot.kind = stateKind(st)
    dot.contentDescription = stateWords(st, day)
    val dotBox = ctx.hbox()
    dotBox.gravity = Gravity.CENTER
    dotBox.setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
    dotBox.addView(dot)
    dotBox.setOnClickListener {
        if (h.method == 1 && st != HState.COMPLETED) {
            ctx.toast("Counts automatically when you finish a workout. Press and hold to mark it by hand.")
        } else if (st == HState.COMPLETED && h.method == 1 && HabitRepo.logs(h.id, day, day)[day] != HabitRepo.LOG_DONE) {
            ctx.toast("Done through a workout. Press and hold to change it.")
        } else {
            if (day <= Days.today()) {
                HabitRepo.setState(h.id, day, if (st == HState.COMPLETED) 0 else HabitRepo.LOG_DONE)
                onChanged()
            }
        }
    }
    row.addView(dotBox)

    val col = ctx.vbox()
    col.addView(UiKit.text(ctx, h.name, 15f, Palette.text, true))
    val slot = HabitRepo.slots.firstOrNull { it.first == h.slot }?.second ?: ""
    val bits = ArrayList<String>()
    if (h.slot != "anytime") bits.add(slot)
    if (h.target.isNotEmpty()) bits.add(h.target)
    bits.add(stateWords(st, day))
    col.addView(UiKit.text(ctx, bits.joinToString(" · "), 12.5f, Palette.dim))
    row.addView(col, lp(0, WRAP, 1f).apply { leftMargin = ctx.dp(2) })
    row.addView(UiKit.text(ctx, "›", 18f, Palette.dim, true))
    addView(row, lp(MATCH, WRAP).apply { bottomMargin = ctx.dp(8) })
}

/** One-tap starter habits. Nothing is added without a tap. */
fun LinearLayout.starterList(limit: Int = 99, onAdded: () -> Unit) {
    var shown = 0
    for (s in Starters.all) {
        if (shown >= limit) break
        if (HabitRepo.hasName(s.name)) continue
        shown++
        tapCard(s.name, s.descr, "Add") {
            HabitRepo.insert(Habit(0, s.name, s.descr, s.category, s.slot, s.freq, s.days, Days.today(), s.target, s.method))
            context.toast("Added \"${s.name}\"")
            onAdded()
        }
    }
}

// =============================================================================================
class HabitsScreen(host: CenterActivity) : Screen(host) {
    override val title = "Habits"
    override val actionLabel: String? = "New"
    override fun onAction() { host.push(HabitFormScreen(host, null)) }

    override fun build(): View {
        val col = host.vbox()
        val habits = HabitRepo.all()
        if (habits.isEmpty()) {
            col.emptyState(
                "No habits yet",
                "Habits are small daily or weekly actions you tick off. Pick a starter below or make your own.",
                "Create a habit"
            ) { host.push(HabitFormScreen(host, null)) }
            col.heading("Starter ideas")
            col.starterList { host.refresh() }
        } else {
            val today = Days.today()
            val due = habits.filter { HabitRepo.scheduled(it, today) }
            val done = due.count { stateOfDay(it, today) == HState.COMPLETED }
            col.para("Today: $done of ${due.size} done. Tap the circle to tick a habit. Press and hold for more options.", sp = 13.5f)
            val groups = listOf("morning" to "Morning", "anytime" to "Any time", "evening" to "Evening")
            for ((slot, label) in groups) {
                val list = habits.filter { it.slot == slot }
                if (list.isEmpty()) continue
                col.heading(label, 10)
                for (h in list) col.habitRow(h, today, { host.refresh() }) { host.push(HabitDetailScreen(host, h.id)) }
            }
            val starters = Starters.all.filter { !HabitRepo.hasName(it.name) }
            if (starters.isNotEmpty()) {
                col.heading("More starter ideas")
                col.starterList(3) { host.refresh() }
            }
        }
        val archived = HabitRepo.all(true).filter { it.archived }
        if (archived.isNotEmpty()) {
            col.heading("Archived")
            for (h in archived) col.tapCard(h.name, "Archived. History is kept.", "Open") { host.push(HabitDetailScreen(host, h.id)) }
        }
        col.noteCard("Your habits and history stay on this phone. This version has no reminders, so check the Center when you pick up your phone.")
        return col
    }
}

// =============================================================================================
class HabitDetailScreen(host: CenterActivity, private val id: Long) : Screen(host) {
    override val title: String get() = HabitRepo.get(id)?.name ?: "Habit"
    override val actionLabel: String? = "Edit"
    override fun onAction() { HabitRepo.get(id)?.let { host.push(HabitFormScreen(host, it)) } }

    override fun build(): View {
        val col = host.vbox()
        val h = HabitRepo.get(id)
        if (h == null) {
            col.emptyState("This habit is gone", "It may have been deleted.", "Back") { host.pop() }
            return col
        }
        val st = HabitRepo.stats(h)
        col.card {
            statRow(listOf(
                st.currentRun.toString() to "Current run",
                st.bestRun.toString() to "Best run",
                (if (st.scheduled30 == 0) "–" else "${st.done30}/${st.scheduled30}") to "Last 30 days"
            ))
            val pct = if (st.scheduled30 == 0) "No scheduled days to measure yet."
            else "Completion over the last 30 days: ${Math.round(st.done30 * 100f / st.scheduled30)}% (calculated from your ticks)."
            para(pct, sp = 12.5f, bottom = 0)
        }
        col.noteCard("How runs are counted: a run is the number of scheduled days in a row you completed. Skipped days and days this habit is not scheduled do not break a run. A scheduled day that is not completed ends the current run, but your best run and your history stay. Today does not break anything until the day is over.")

        col.heading("Last five weeks")
        col.addView(calendar(h), lp(MATCH, WRAP).apply { bottomMargin = host.dp(6) })
        col.para("Tap a day to change it. Filled circle: completed. Ring with a dash: skipped. Red ring with a cross: not completed. Faint: not scheduled. Outlined: today, not done yet.", sp = 12f)

        col.heading("Details")
        col.card {
            para("Schedule: " + HabitRepo.describeSchedule(h), dim = false, bottom = 4)
            para("Category: ${h.category}", dim = false, bottom = 4)
            para("Time of day: " + (HabitRepo.slots.firstOrNull { it.first == h.slot }?.second ?: "Any time"), dim = false, bottom = 4)
            if (h.target.isNotEmpty()) para("Target: ${h.target}", dim = false, bottom = 4)
            para(
                if (h.method == 1) "Counts automatically on days you finish a workout. You can also mark it yourself."
                else "You tick this one off yourself.", dim = false, bottom = 4
            )
            para("Started ${Days.pretty(h.startDay)}", bottom = 0)
            if (h.descr.isNotEmpty()) { gap(6); para(h.descr, bottom = 0) }
        }
        col.gap(4)
        col.actionButton(if (h.archived) "Restore habit" else "Archive habit (keeps history)", false) {
            h.archived = !h.archived
            HabitRepo.update(h)
            host.refresh()
        }
        col.actionButton("Delete habit and its history", false) {
            Dialogs.confirm(host, "Delete \"${h.name}\"?", "This removes the habit and every day recorded for it. Archive it instead if you want to keep the history.", "Delete") {
                HabitRepo.delete(h.id)
                host.pop()
            }
        }
        return col
    }

    private fun calendar(h: Habit): View {
        val today = Days.today()
        val start = Days.weekStart(Days.add(today, -28))
        val end = Days.add(Days.weekStart(today), 6)
        val logs = HabitRepo.logs(h.id, start, today)
        val wd = if (h.method == 1) WorkoutRepo.completedDays(start, today) else emptySet()
        val box = host.vbox()
        val head = host.hbox()
        for (l in listOf("M", "T", "W", "T", "F", "S", "S")) {
            val t = UiKit.text(host, l, 12f, Palette.dim, true)
            t.gravity = Gravity.CENTER
            head.addView(t, lp(0, WRAP, 1f))
        }
        box.addView(head, lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
        val days = Days.range(start, end)
        var i = 0
        while (i < days.size) {
            val row = host.hbox()
            for (k in 0..6) {
                val d = days[i + k]
                val cell = TextView(host)
                cell.gravity = Gravity.CENTER
                cell.textSize = 13f
                val dayNum = d.substring(8).trimStart('0')
                if (d > today) {
                    cell.text = ""
                } else {
                    cell.text = if (d == today) dayNum else dayNum
                    val s = HabitRepo.stateOf(h, d, logs, today, wd)
                    val bg = GradientDrawable()
                    bg.cornerRadius = host.dpf(10f)
                    when (s) {
                        HState.COMPLETED -> { bg.setColor(Palette.accent); cell.setTextColor(Palette.onAccent) }
                        HState.SKIPPED -> { bg.setColor(0); bg.setStroke(host.dp(2), Palette.dim); cell.setTextColor(Palette.dim); cell.text = "$dayNum–" }
                        HState.NOT_COMPLETED -> { bg.setColor(0); bg.setStroke(host.dp(2), Palette.danger); cell.setTextColor(Palette.danger); cell.text = "$dayNum×" }
                        HState.NOT_SCHEDULED -> { bg.setColor(0); cell.setTextColor((Palette.dim and 0x00FFFFFF) or 0x66000000) }
                        HState.PENDING -> { bg.setColor(0); bg.setStroke(host.dp(2), Palette.accent); cell.setTextColor(Palette.text) }
                    }
                    cell.background = bg
                    cell.contentDescription = Days.pretty(d) + ": " + stateWords(s, d)
                    cell.setOnClickListener { habitStateDialog(host, h, d) { host.refresh() } }
                }
                row.addView(cell, lp(0, host.dp(38), 1f).apply { setMargins(host.dp(2), host.dp(2), host.dp(2), host.dp(2)) })
            }
            box.addView(row, lp(MATCH, WRAP))
            i += 7
        }
        return box
    }
}

// =============================================================================================
class HabitFormScreen(host: CenterActivity, private val existing: Habit?) : Screen(host) {
    override val title = if (existing == null) "New habit" else "Edit habit"

    private var category = existing?.category ?: "General"
    private var slot = existing?.slot ?: "anytime"
    private var freq = existing?.freq ?: "daily"
    private var mask = existing?.days ?: 0b0011111
    private var method = existing?.method ?: 0

    override fun build(): View {
        val col = host.vbox()
        val name = host.field("e.g. Evening skincare", existing?.name ?: "")
        col.fieldLabel("Name")
        col.addView(name, lp(MATCH, WRAP))
        col.fieldLabel("Notes (optional)")
        val descr = host.field("Anything that helps you remember", existing?.descr ?: "", multiline = true)
        col.addView(descr, lp(MATCH, WRAP))

        col.fieldLabel("Category")
        var catRow: LinearLayout? = null
        catRow = col.pickRow("Category", category) {
            Dialogs.list(host, "Category", HabitRepo.categories) { i ->
                category = HabitRepo.categories[i]
                (catRow?.getChildAt(1) as? TextView)?.text = "$category  ›"
            }
        }

        col.fieldLabel("Time of day")
        col.addView(host.chips(HabitRepo.slots, slot) { slot = it }, lp(MATCH, WRAP))

        col.fieldLabel("How often")
        val days = host.dayChips(mask) { mask = it }
        days.visibility = if (freq == "custom") View.VISIBLE else View.GONE
        col.addView(host.chips(listOf("daily" to "Every day", "weekdays" to "Weekdays", "custom" to "Pick days"), freq) {
            freq = it
            days.visibility = if (it == "custom") View.VISIBLE else View.GONE
        }, lp(MATCH, WRAP))
        col.gap(8)
        col.addView(days, lp(MATCH, WRAP))

        col.fieldLabel("Target (optional)")
        val target = host.field("e.g. 10 min or 4 glasses", existing?.target ?: "")
        col.addView(target, lp(MATCH, WRAP))

        col.fieldLabel("How it gets ticked")
        col.addView(host.chips(listOf("0" to "I tick it", "1" to "From workouts"), method.toString()) { method = it.toInt() }, lp(MATCH, WRAP))
        col.gap(4)
        col.para("\"From workouts\" counts the habit as done on days you finish a workout in the Center. You can still tick it yourself.", sp = 12f)

        col.gap(12)
        col.actionButton("Save habit") {
            val n = name.text.toString().trim()
            if (n.isEmpty()) { host.toast("Give the habit a name"); return@actionButton }
            if (freq == "custom" && mask == 0) { host.toast("Pick at least one day"); return@actionButton }
            val e = existing
            if (e == null) {
                HabitRepo.insert(Habit(0, n, descr.text.toString().trim(), category, slot, freq, mask, Days.today(), target.text.toString().trim(), method))
            } else {
                e.name = n; e.descr = descr.text.toString().trim(); e.category = category; e.slot = slot
                e.freq = freq; e.days = mask; e.target = target.text.toString().trim(); e.method = method
                HabitRepo.update(e)
            }
            host.pop()
        }
        return col
    }
}
