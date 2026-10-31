package com.elevate.launcher.center.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.elevate.launcher.center.content.Plan
import com.elevate.launcher.center.content.PlanStep
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.ResumeState
import com.elevate.launcher.center.data.SessionEx
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

/**
 * Guided workout: one exercise at a time, with optional timers and rest. Progress is saved after every exercise
 * so a workout can be resumed. A session is written to history only when it is finished or the person chooses
 * to end it and keep what they did.
 */
class PlayerScreen(host: CenterActivity, private val plan: Plan, resume: ResumeState?) : Screen(host) {
    private enum class Phase { INTRO, WORK, REST, FINISH }

    override val title = plan.name
    override val scrolls = false
    override val actionLabel: String? get() = if (phase == Phase.FINISH) null else "End"
    override fun onAction() { confirmEnd() }

    private val steps: List<PlanStep> = plan.steps
    private val states = LinkedHashMap<String, String>()
    private var idx = 0
    private var setNo = 1
    private var side = 1
    private var phase = Phase.INTRO
    private var startedMs = System.currentTimeMillis()
    private var savedId = -1L
    private var restNext = ""

    // timer
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunning = false
    private var paused = false
    private var deadline = 0L
    private var remainingMs = 0L
    private var totalMs = 1L
    private var lastBeepSec = -1L
    private var tone: ToneGenerator? = null

    // views that change while a timer runs
    private var timeText: TextView? = null
    private var timeBar: ProgressBar2? = null
    private var pauseBtn: TextView? = null
    private var overallBar: ProgressBar2? = null
    private var headText: TextView? = null
    private lateinit var bodyBox: LinearLayout
    private lateinit var controls: LinearLayout

    init {
        for (s in steps) states[s.key] = "pending"
        if (resume != null && resume.planKey == plan.key) {
            for ((k, v) in resume.states) if (states.containsKey(k)) states[k] = v
            idx = resume.index.coerceIn(0, steps.size - 1)
            startedMs = resume.startedMs
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!timerRunning || paused) return
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                timerRunning = false
                timeText?.text = fmtClock(0)
                timeBar?.progress = 1f
                beep(true)
                onTimerEnd()
                return
            }
            timeText?.text = fmtClock((left + 999) / 1000)
            timeBar?.progress = 1f - left.toFloat() / totalMs.toFloat()
            val sec = (left + 999) / 1000
            if (sec <= 3 && sec != lastBeepSec) { lastBeepSec = sec; beep(false) }
            handler.postDelayed(this, 200)
        }
    }

    // ------------------------------------------------------------ screen lifecycle
    override fun build(): View {
        val root = host.vbox()
        val header = host.vbox()
        header.setPadding(host.dp(16), host.dp(2), host.dp(16), host.dp(6))
        val ht = UiKit.text(host, "", 13f, Palette.dim, true)
        headText = ht
        header.addView(ht, lp(MATCH, WRAP).apply { bottomMargin = host.dp(6) })
        val ob = ProgressBar2(host)
        overallBar = ob
        header.addView(ob, lp(MATCH, WRAP))
        root.addView(header, lp(MATCH, WRAP))

        val sv = ScrollView(host)
        bodyBox = host.vbox()
        bodyBox.setPadding(host.dp(16), host.dp(8), host.dp(16), host.dp(16))
        sv.addView(bodyBox, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(sv, lp(MATCH, 0, 1f))

        controls = host.vbox()
        controls.setPadding(host.dp(16), host.dp(8), host.dp(16), host.dp(12))
        root.addView(controls, lp(MATCH, WRAP))

        if (phase != Phase.FINISH) saveProgress()
        render()
        return root
    }

    override fun onShow() {
        host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onHide() {
        handler.removeCallbacks(ticker)
        host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try { tone?.release() } catch (e: Exception) { }
        tone = null
    }

    override fun onPauseScreen() {
        if (timerRunning && !paused) pauseTimer()
    }

    override fun onBack(): Boolean {
        if (phase == Phase.FINISH) return false
        confirmEnd()
        return true
    }

    // ------------------------------------------------------------ timer
    private fun startTimer(ms: Long) {
        handler.removeCallbacks(ticker)
        totalMs = ms
        deadline = SystemClock.elapsedRealtime() + ms
        timerRunning = true
        paused = false
        lastBeepSec = -1
        handler.post(ticker)
    }

    private fun stopTimer() {
        handler.removeCallbacks(ticker)
        timerRunning = false
        paused = false
    }

    private fun pauseTimer() {
        if (!timerRunning || paused) return
        remainingMs = Math.max(0L, deadline - SystemClock.elapsedRealtime())
        paused = true
        handler.removeCallbacks(ticker)
        pauseBtn?.text = "Resume"
    }

    private fun resumeTimer() {
        if (!timerRunning || !paused) return
        deadline = SystemClock.elapsedRealtime() + remainingMs
        paused = false
        pauseBtn?.text = "Pause"
        handler.post(ticker)
    }

    private fun addTime(ms: Long) {
        if (!timerRunning) return
        if (paused) remainingMs += ms else deadline += ms
        totalMs += ms
    }

    private fun beep(long: Boolean) {
        if (!Center.audioCues) return
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            tone?.startTone(if (long) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, if (long) 300 else 120)
        } catch (e: Exception) { }
    }

    private fun onTimerEnd() {
        when (phase) {
            Phase.WORK -> workDone()
            Phase.REST -> afterRest()
            else -> { }
        }
    }

    // ------------------------------------------------------------ flow
    private val step: PlanStep get() = steps[idx]

    private fun saveProgress() {
        WorkoutRepo.saveResume(ResumeState(plan.key, startedMs, idx, LinkedHashMap(states)))
    }

    private fun startWork() {
        phase = Phase.WORK
        side = 1
        stopTimer()
        val e = step.ex
        if (e.timed && Center.useTimers) startTimer(step.amount * 1000L)
        render()
    }

    /** Called when a timer ends or the person taps "Set done". */
    private fun workDone() {
        stopTimer()
        val e = step.ex
        if (e.timed && e.perSide && side == 1 && Center.useTimers) {
            side = 2
            startTimer(step.amount * 1000L)
            render()
            return
        }
        side = 1
        if (setNo < step.sets) {
            setNo++
            startRest(step.rest, "Next: set $setNo of ${step.sets} · ${e.name}")
        } else {
            finishExercise("done")
        }
    }

    private fun startRest(seconds: Int, next: String) {
        phase = Phase.REST
        restNext = next
        stopTimer()
        startTimer(seconds * 1000L)
        render()
    }

    private fun afterRest() {
        stopTimer()
        if (idx < steps.size && step.sets >= setNo && setNo > 1) startWork() else { phase = Phase.INTRO; render() }
    }

    private fun finishExercise(result: String) {
        stopTimer()
        states[step.key] = result
        if (idx >= steps.size - 1) {
            finishWorkout()
            return
        }
        val justFinished = step
        idx++
        setNo = 1
        side = 1
        saveProgress()
        if (result == "done") {
            val seconds = Math.min(justFinished.rest, 20)
            startRest(seconds, "Next: ${step.ex.name}")
        } else {
            phase = Phase.INTRO
            render()
        }
    }

    private fun skipExercise() {
        finishExercise("skipped")
    }

    private fun exerciseList(): List<SessionEx> =
        steps.map {
            val st = states[it.key] ?: "pending"
            SessionEx(it.key, it.ex.name, if (st == "done" || st == "skipped") st else "not_reached")
        }

    /** Reached the end with at least half of the exercises actually done. Mostly-skipped sessions are saved as "ended early". */
    private fun completedEnough(list: List<SessionEx>): Boolean = list.isNotEmpty() && list.count { it.state == "done" } * 2 >= list.size

    private fun finishWorkout() {
        phase = Phase.FINISH
        stopTimer()
        val list = exerciseList()
        val status = if (completedEnough(list)) "completed" else "ended"
        savedId = WorkoutRepo.saveSession(plan.key, plan.name, plan.category, startedMs, System.currentTimeMillis(), status, list)
        WorkoutRepo.clearResume()
        render()
    }

    private fun confirmEnd() {
        val anyDone = states.values.any { it == "done" }
        val labels = ArrayList<String>()
        val acts = ArrayList<Int>()
        if (anyDone) { labels.add("Save what I did and finish"); acts.add(0) }
        labels.add("Discard this workout"); acts.add(1)
        labels.add("Keep going"); acts.add(2)
        AlertDialog.Builder(host)
            .setTitle("End this workout?")
            .setItems(labels.toTypedArray()) { _, which ->
                when (acts[which]) {
                    0 -> {
                        stopTimer()
                        val list = exerciseList()
                        WorkoutRepo.saveSession(plan.key, plan.name, plan.category, startedMs, System.currentTimeMillis(), "ended", list)
                        WorkoutRepo.clearResume()
                        host.toast("Saved to your history")
                        host.pop()
                    }
                    1 -> {
                        stopTimer()
                        WorkoutRepo.clearResume()
                        host.pop()
                    }
                    else -> { }
                }
            }
            .show()
    }

    // ------------------------------------------------------------ drawing
    private fun sectionLabel(): String = when {
        idx < plan.warm.size -> "Warm-up"
        idx < plan.warm.size + plan.main.size -> "Main"
        else -> "Cool-down"
    }

    private fun render() {
        timeText = null
        timeBar = null
        pauseBtn = null
        bodyBox.removeAllViews()
        bodyBox.gravity = Gravity.NO_GRAVITY
        controls.removeAllViews()
        val finished = states.values.count { it == "done" || it == "skipped" }
        overallBar?.progress = if (phase == Phase.FINISH) 1f else finished.toFloat() / steps.size
        headText?.text = if (phase == Phase.FINISH) "Finished" else "Exercise ${idx + 1} of ${steps.size} · ${sectionLabel()}"
        when (phase) {
            Phase.INTRO -> renderIntro()
            Phase.WORK -> renderWork()
            Phase.REST -> renderRest()
            Phase.FINISH -> renderFinish()
        }
        host.refreshAction()
    }

    private fun targetText(): String {
        val e = step.ex
        val unit = if (e.timed) "${step.amount} sec" else "${step.amount} reps"
        return unit + (if (e.perSide) " each side" else "")
    }

    private fun renderIntro() {
        val e = step.ex
        bodyBox.addView(UiKit.text(host, e.name, 26f, Palette.text, true), lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
        bodyBox.para("Set 1 of ${step.sets} · ${targetText()}", dim = false, sp = 15f, bottom = 8)
        bodyBox.para(e.purpose, sp = 13.5f)
        bodyBox.poseView(e.key, e.name, 200, 10)
        bodyBox.card {
            cardTitle("Start position")
            para(e.startPos, dim = false, sp = 13.5f, bottom = 8)
            cardTitle("Steps")
            e.steps.forEachIndexed { i, s -> para("${i + 1}. $s", dim = false, sp = 13.5f, bottom = 3) }
            gap(6)
            cardTitle("Watch for")
            for (m in e.mistakes.take(2)) para("• $m", dim = false, sp = 13.5f, bottom = 3)
        }
        bodyBox.para("Easier: ${e.easier}", sp = 12.5f, bottom = 4)
        bodyBox.para("Harder: ${e.harder}", sp = 12.5f, bottom = 4)
        bodyBox.noteCard(e.safety)

        controls.addView(UiKit.button(host, "Start", true) { startWork() }, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        controls.addView(UiKit.button(host, "Skip this exercise", false) { skipExercise() }, lp(MATCH, WRAP))
    }

    private fun renderWork() {
        val e = step.ex
        bodyBox.gravity = Gravity.CENTER_HORIZONTAL
        val name = UiKit.text(host, e.name, 24f, Palette.text, true)
        name.gravity = Gravity.CENTER
        bodyBox.addView(name, lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
        val setLine = UiKit.text(host, "Set $setNo of ${step.sets}", 15f, Palette.dim)
        setLine.gravity = Gravity.CENTER
        bodyBox.addView(setLine, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
        bodyBox.poseView(e.key, e.name, 150, 10)

        if (e.timed && Center.useTimers) {
            if (e.perSide) {
                val sideLine = UiKit.text(host, if (side == 1) "First side" else "Switch sides: second side", 16f, Palette.accentText, true)
                sideLine.gravity = Gravity.CENTER
                bodyBox.addView(sideLine, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
            }
            val t = UiKit.text(host, fmtClock(step.amount.toLong()), 64f, Palette.text, true)
            t.gravity = Gravity.CENTER
            timeText = t
            bodyBox.addView(t, lp(MATCH, WRAP).apply { bottomMargin = host.dp(12) })
            val b = ProgressBar2(host)
            timeBar = b
            bodyBox.addView(b, lp(MATCH, WRAP).apply { bottomMargin = host.dp(20) })
        } else {
            val big = UiKit.text(host, step.amount.toString(), 72f, Palette.text, true)
            big.gravity = Gravity.CENTER
            bodyBox.addView(big, lp(MATCH, WRAP))
            val unit = UiKit.text(host, (if (e.timed) "seconds" else "reps") + (if (e.perSide) " each side" else ""), 16f, Palette.dim)
            unit.gravity = Gravity.CENTER
            bodyBox.addView(unit, lp(MATCH, WRAP).apply { bottomMargin = host.dp(20) })
            if (e.timed) {
                val hint = UiKit.text(host, "Timers are off. Hold at your own pace and tap Set done when finished.", 13f, Palette.dim)
                hint.gravity = Gravity.CENTER
                bodyBox.addView(hint, lp(MATCH, WRAP).apply { bottomMargin = host.dp(12) })
            }
        }
        val cue = UiKit.text(host, e.steps.first(), 14f, Palette.dim)
        cue.gravity = Gravity.CENTER
        cue.setLineSpacing(0f, 1.15f)
        bodyBox.addView(cue, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })

        val timed = e.timed && Center.useTimers
        if (timed) {
            val row = host.hbox()
            val pb = UiKit.button(host, if (paused) "Resume" else "Pause", false) {
                if (paused) resumeTimer() else pauseTimer()
            }
            pauseBtn = pb
            row.addView(pb, lp(0, WRAP, 1f))
            row.addView(UiKit.button(host, "Done early", true) { workDone() }, lp(0, WRAP, 1f).apply { leftMargin = host.dp(8) })
            controls.addView(row, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        } else {
            controls.addView(UiKit.button(host, "Set done", true) { workDone() }, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        }
        controls.addView(UiKit.button(host, "Skip this exercise", false) { skipExercise() }, lp(MATCH, WRAP))
    }

    private fun renderRest() {
        bodyBox.gravity = Gravity.CENTER_HORIZONTAL
        val h = UiKit.text(host, "Rest", 24f, Palette.text, true)
        h.gravity = Gravity.CENTER
        bodyBox.addView(h, lp(MATCH, WRAP).apply { bottomMargin = host.dp(16) })
        val t = UiKit.text(host, fmtClock(0), 64f, Palette.text, true)
        t.gravity = Gravity.CENTER
        timeText = t
        bodyBox.addView(t, lp(MATCH, WRAP).apply { bottomMargin = host.dp(12) })
        val b = ProgressBar2(host)
        timeBar = b
        bodyBox.addView(b, lp(MATCH, WRAP).apply { bottomMargin = host.dp(20) })
        val n = UiKit.text(host, restNext, 15f, Palette.dim)
        n.gravity = Gravity.CENTER
        bodyBox.addView(n, lp(MATCH, WRAP))
        bodyBox.para("Breathe slowly. Shake out your arms and legs if they feel tight.", sp = 13f).gravity = Gravity.CENTER

        val row = host.hbox()
        val pb = UiKit.button(host, if (paused) "Resume" else "Pause", false) {
            if (paused) resumeTimer() else pauseTimer()
        }
        pauseBtn = pb
        row.addView(pb, lp(0, WRAP, 1f))
        row.addView(UiKit.button(host, "+10 sec", false) { addTime(10000) }, lp(0, WRAP, 1f).apply { leftMargin = host.dp(8) })
        controls.addView(row, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        controls.addView(UiKit.button(host, "Skip rest", true) { stopTimer(); afterRest() }, lp(MATCH, WRAP))
        // show the starting number straight away
        timeText?.text = fmtClock((Math.max(0L, deadline - SystemClock.elapsedRealtime()) + 999) / 1000)
    }

    private fun renderFinish() {
        val list = exerciseList()
        val done = list.count { it.state == "done" }
        val skipped = list.count { it.state == "skipped" }
        val minutes = Math.max(1, ((System.currentTimeMillis() - startedMs) / 60000L).toInt())
        bodyBox.gravity = Gravity.NO_GRAVITY
        val enough = completedEnough(list)
        bodyBox.addView(UiKit.text(host, if (enough) "Workout complete" else "Session saved", 26f, Palette.text, true), lp(MATCH, WRAP).apply { bottomMargin = host.dp(6) })
        bodyBox.para(
            if (enough) "${plan.name} is saved in your history."
            else "Most exercises were skipped, so this is saved as ended early and does not count as a completed workout.",
            sp = 14f
        )
        bodyBox.card {
            statRow(listOf("$minutes min" to "Time", done.toString() to "Done", skipped.toString() to "Skipped"))
            para("These numbers come straight from what you tapped during this session.", sp = 12f, bottom = 0)
        }
        bodyBox.heading("How did it feel?", 6)
        var diff = 0
        val notes = host.field("Notes for next time (optional)", "", multiline = true)
        bodyBox.addView(host.chips((1..5).map { it.toString() to it.toString() }, "") { diff = it.toInt() }, lp(MATCH, WRAP))
        bodyBox.para("1 = too easy, 3 = just right, 5 = too hard", sp = 12f)
        bodyBox.addView(notes, lp(MATCH, WRAP))
        controls.addView(UiKit.button(host, "Save and finish", true) {
            if (diff > 0 || notes.text.isNotBlank()) WorkoutRepo.setFeedback(savedId, diff, notes.text.toString().trim())
            host.pop()
        }, lp(MATCH, WRAP))
    }
}
