package com.elevate.launcher.center.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.elevate.launcher.center.content.Articles
import com.elevate.launcher.center.content.Exercises
import com.elevate.launcher.center.content.Plans
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.PhotoRepo
import com.elevate.launcher.core.BaseActivity
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Perf
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp

/** One screen inside the Center. Screens are rebuilt from the database each time they are shown. */
abstract class Screen(val host: CenterActivity) {
    abstract val title: String
    open val scrolls: Boolean = true

    /** Optional text action shown at the top right. */
    open val actionLabel: String? = null
    open fun onAction() {}

    abstract fun build(): View
    open fun onShow() {}
    open fun onHide() {}
    open fun onPauseScreen() {}
    open fun onResumeScreen() {}

    /** Return true if the back press was handled by the screen itself. */
    open fun onBack(): Boolean = false
}

class CenterActivity : BaseActivity() {
    private val stack = ArrayList<Screen>()
    private lateinit var titleView: TextView
    private lateinit var actionView: TextView
    private lateinit var body: FrameLayout
    private var scroll: ScrollView? = null
    private var started = false
    private var permCallback: ((Boolean) -> Unit)? = null
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val cb = permCallback
        permCallback = null
        cb?.invoke(ok)
    }

    private var photoCb: ((Boolean) -> Unit)? = null
    private val takePic = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val cb = photoCb
        photoCb = null
        cb?.invoke(ok)
    }
    private var pickCb: ((android.net.Uri?) -> Unit)? = null
    private val pickImg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = pickCb
        pickCb = null
        cb?.invoke(uri)
    }

    /** Opens the camera app and saves the picture to [uri] (a FileProvider address). Throws if no camera app exists. */
    fun takePicture(uri: android.net.Uri, done: (Boolean) -> Unit) {
        photoCb = done
        takePic.launch(uri)
    }

    /** Opens the system picture chooser. No storage permission is needed. */
    fun pickImage(done: (android.net.Uri?) -> Unit) {
        pickCb = done
        pickImg.launch("image/*")
    }

    /** Asks for the notification permission (Android 13 and newer) only when the person turns reminders on. */
    fun askNotificationPermission(done: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) { done(true); return }
        permCallback = done
        notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Center.init(this)

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
            setOnClickListener { back() }
        }
        titleView = UiKit.text(this, "Center", 20f, Palette.text, true).apply { isSingleLine = true }
        actionView = UiKit.text(this, "", 14f, Palette.accent, true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { stack.lastOrNull()?.onAction() }
            visibility = View.GONE
        }
        bar.addView(back, lp(dp(48), dp(48)))
        bar.addView(titleView, lp(0, WRAP, 1f))
        bar.addView(actionView, lp(WRAP, WRAP))
        body = FrameLayout(this)
        root.addView(bar, lp(MATCH, WRAP))
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)
        edgeToEdge(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { back() }
        })

        intent.getStringExtra("ci_height_unit")?.let { Center.heightUnit = it }
        stack.add(DashboardScreen(this))
        com.elevate.launcher.data.Io.work { com.elevate.launcher.reminders.Reminders.reschedule(applicationContext) }
        if (intent.getBooleanExtra("ci_seed", false)) CenterSeed.run()
        ciReminderTest()
        ciPhotos()
        val section = intent.getStringExtra(EXTRA_SECTION)
        if (section != null && section != "home") openSection(section, false)
        show()
        started = true
        ciGrowthHooks()
    }

    /** Hooks used only by the automated emulator test; harmless otherwise. */
    private fun ciGrowthHooks() {
        val sdp = intent.getIntExtra("ci_scroll_dp", 0)
        if (sdp > 0) body.postDelayed({ scroll?.scrollTo(0, dp(sdp)) }, 1500)
        if (intent.getBooleanExtra("ci_growth_selftest", false)) GrowthSelfTest.run(applicationContext)
        if (intent.getBooleanExtra("ci_height_add", false)) {
            body.postDelayed({ HeightEditor.open(this, null, com.elevate.launcher.center.data.MeasureRepo.list("height")) { refresh() } }, 1800)
        }
    }

    override fun onResume() {
        super.onResume()
        if (started) stack.lastOrNull()?.onResumeScreen()
    }

    override fun onPause() {
        stack.lastOrNull()?.onPauseScreen()
        com.elevate.launcher.widgets.TodayWidget.refresh(this)
        super.onPause()
    }

    override fun onDestroy() {
        stack.lastOrNull()?.onHide()
        super.onDestroy()
    }

    // ------------------------------------------------------------ navigation
    /** Set for a push or a pop, so the next [show] fades in. A refresh after an edit does not fade. */
    private var fadeNext = false

    fun push(s: Screen) {
        stack.lastOrNull()?.onHide()
        stack.add(s)
        fadeNext = true
        show()
    }

    fun pop() {
        if (stack.size <= 1) { finish(); return }
        stack.last().onHide()
        stack.removeAt(stack.size - 1)
        fadeNext = true
        show()
    }

    fun back() {
        val top = stack.lastOrNull() ?: run { finish(); return }
        if (top.onBack()) return
        pop()
    }

    /** Rebuild the screen on top (after data changed) while keeping the scroll position. */
    fun refresh() {
        val y = scroll?.scrollY ?: 0
        stack.lastOrNull()?.onHide()
        show()
        scroll?.post { scroll?.scrollTo(0, y) }
    }

    /** Re-reads the top screen's action label (used by screens whose action changes while they are open). */
    fun refreshAction() {
        val s = stack.lastOrNull() ?: return
        val label = s.actionLabel
        if (label == null) {
            actionView.visibility = View.GONE
        } else {
            actionView.text = label
            actionView.visibility = View.VISIBLE
        }
    }

    private fun show() {
        val s = stack.last()
        titleView.text = s.title
        val label = s.actionLabel
        if (label == null) {
            actionView.visibility = View.GONE
        } else {
            actionView.text = label
            actionView.visibility = View.VISIBLE
        }
        body.removeAllViews()
        scroll = null
        val content = s.build()
        if (s.scrolls) {
            val sv = ScrollView(this)
            sv.isFillViewport = false
            val pad = LinearLayout(this)
            pad.orientation = LinearLayout.VERTICAL
            pad.setPadding(dp(16), dp(4), dp(16), dp(28))
            pad.addView(content, lp(MATCH, WRAP))
            sv.addView(pad, FrameLayout.LayoutParams(MATCH, WRAP))
            body.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
            scroll = sv
        } else {
            body.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        body.animate().cancel()
        if (fadeNext && Perf.animate) {
            body.alpha = 0f
            body.animate().alpha(1f).setDuration(Perf.duration(140L)).start()
        } else {
            body.alpha = 1f
        }
        fadeNext = false
        s.onShow()
    }

    /** Test hook for the automated emulator run: puts three generated pictures in the journal (once). */
    private fun ciPhotos() {
        if (!intent.getBooleanExtra("ci_photo_import", false) || PhotoRepo.count() > 0) return
        val specs = listOf(Triple("front", 0, 0xFF3B6EA8.toInt()), Triple("side", -9, 0xFF2E8B57.toInt()), Triple("posture", -21, 0xFFB5651D.toInt()))
        for ((kind, back, colour) in specs) {
            val bmp = android.graphics.Bitmap.createBitmap(480, 640, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp)
            cv.drawColor(colour)
            val p = android.graphics.Paint().apply { color = 0xFFFFFFFF.toInt(); textSize = 64f; isAntiAlias = true }
            cv.drawText("TEST " + kind, 40f, 320f, p)
            val bos = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, bos)
            PhotoRepo.saveFrom(this, java.io.ByteArrayInputStream(bos.toByteArray()), Days.add(Days.today(), back), kind, if (back == 0) "Generated test picture" else "")
        }
    }

    /** Test hook for the automated emulator run: turns reminders on and sets one habit to remind a minute or two from now. */
    private fun ciReminderTest() {
        val mins = intent.getIntExtra("ci_reminder_in", 0)
        if (mins <= 0) return
        var h = com.elevate.launcher.center.data.HabitRepo.all().firstOrNull()
        if (h == null) {
            val id = com.elevate.launcher.center.data.HabitRepo.insert(
                com.elevate.launcher.center.data.Habit(0, "Reminder test habit", "", "General", "anytime", "daily", 127,
                    com.elevate.launcher.center.data.Days.today(), "", 0)
            )
            h = com.elevate.launcher.center.data.HabitRepo.get(id)
        }
        if (h == null) return
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.MINUTE, mins)
        h.remind = String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
        com.elevate.launcher.center.data.HabitRepo.update(h)
        com.elevate.launcher.reminders.Reminders.turnOn(this)
    }

    /**
     * Opens a named section on top of the dashboard. Understands "habits", "workouts", "grooming", "posture",
     * "goals", "progress", "learn", "settings", "height" (or "growth"), "movement", "activity", "heightlist", "growthsummary", "sleep" and deep links "article:key", "exercise:key", "plan:key".
     */
    fun openSection(section: String, render: Boolean = true) {
        val screen: Screen? = when {
            section == "habits" -> HabitsScreen(this)
            section == "workouts" -> WorkoutsScreen(this)
            section == "grooming" -> GroomingScreen(this)
            section == "posture" -> PostureScreen(this)
            section == "goals" -> GoalsScreen(this)
            section == "progress" -> ProgressScreen(this)
            section == "learn" -> LearnScreen(this)
            section == "settings" -> CenterSettingsScreen(this)
            section == "height" || section == "growth" -> GrowthScreen(this)
            section == "movement" -> MovementLibraryScreen(this)
            section == "activity" -> ActivityScreen(this)
            section == "heightlist" -> HeightHistoryScreen(this)
            section == "growthsummary" -> GrowthSummaryScreen(this)
            section == "sleep" -> SleepScreen(this)
            section == "library" -> ExerciseLibraryScreen(this, null)
            section == "history" -> HistoryScreen(this)
            section == "glossary" -> GlossaryScreen(this)
            section == "care" -> CareLogScreen(this)
            section == "reminders" -> RemindersScreen(this)
            section == "photos" -> PhotoJournalScreen(this)
            section == "photo:first" -> PhotoRepo.all().firstOrNull()?.let { PhotoDetailScreen(this, it.id) }
            section.startsWith("photo:") -> section.removePrefix("photo:").toLongOrNull()?.let { PhotoDetailScreen(this, it) }
            section == "photocompare:first" -> PhotoRepo.all().let { l -> if (l.size >= 2) PhotoCompareScreen(this, l[l.size - 1].id, l[0].id) else null }
            section.startsWith("article:") -> Articles.get(section.removePrefix("article:"))?.let { ArticleScreen(this, it) }
            section.startsWith("poses:") -> section.removePrefix("poses:").toIntOrNull()?.let { PoseGalleryScreen(this, it) }
            section.startsWith("exercise:") -> Exercises.get(section.removePrefix("exercise:"))?.let { ExerciseDetailScreen(this, it) }
            section.startsWith("player:") -> Plans.get(section.removePrefix("player:"))?.let { PlayerScreen(this, it, null) }
            section.startsWith("plan:") -> Plans.get(section.removePrefix("plan:"))?.let { PlanDetailScreen(this, it) }
            else -> null
        }
        if (screen == null) return
        if (render) push(screen) else stack.add(screen)
    }

    companion object {
        const val EXTRA_SECTION = "section"

        fun open(ctx: Context, section: String? = null) {
            val i = Intent(ctx, CenterActivity::class.java)
            if (section != null) i.putExtra(EXTRA_SECTION, section)
            if (ctx !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
