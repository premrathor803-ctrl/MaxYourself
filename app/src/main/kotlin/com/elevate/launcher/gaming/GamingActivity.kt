package com.elevate.launcher.gaming

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.elevate.launcher.center.ui.BarChart
import com.elevate.launcher.center.ui.chips
import com.elevate.launcher.center.ui.clockText
import com.elevate.launcher.center.ui.field
import com.elevate.launcher.center.ui.fieldLabel
import com.elevate.launcher.center.ui.formDialog
import com.elevate.launcher.center.ui.noteCard
import com.elevate.launcher.center.ui.pickDate
import com.elevate.launcher.center.ui.pickRow
import com.elevate.launcher.center.ui.pickTime
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.Launch
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.PageActivity
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppEntry
import com.elevate.launcher.data.Io
import com.elevate.launcher.ui.IconLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gaming mode: your games in one list, honest play-time tracking, per-game choices, and a few real numbers about the phone.
 * Everything here is read from your own data on this phone. It does not speed games up or close other apps.
 */
class GamingActivity : PageActivity() {
    override val pageTitle: String get() = "Gaming mode"

    /** What the History and Play time cards show. Read on a background thread, never on the main thread. */
    private class Stats(
        val days: List<Long>, val perDay: LongArray, val recent: List<GameSession>, val count: Int,
        val lastPlayed: Map<String, Long>, val totalMs: Map<String, Long>
    )

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val catalogListener: () -> Unit = { if (isBuilt() && !isFinishing) fillGames() }

    private var stats: Stats? = null
    private var statsError = false
    private var query = ""
    private var sort = "name"
    private var onlyFav = false
    private var showAllGames = false
    private var historyLimit = 15
    private var notice = ""
    private var lastLaunchAt = 0L

    private var gamesBox: LinearLayout? = null
    private var playBox: LinearLayout? = null
    private var historyBox: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val i = intent
        if (i?.getBooleanExtra("ci_gaming_on", false) == true && !Gaming.on) Gaming.start(applicationContext)
        super.onCreate(savedInstanceState)
        ciHooks(i)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ciHooks(intent)
    }

    override fun onStart() {
        super.onStart()
        AppCatalog.addListener(catalogListener)
    }

    override fun onStop() {
        AppCatalog.removeListener(catalogListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // coming back to Elevate ends any game that was opened from it
        Play.noteReturn(this)
        Gaming.pruneMissing()
        val n = Play.takeNotice()
        if (n.isNotEmpty()) notice = n
        if (isBuilt()) rebuild()
        loadStats()
    }

    // ------------------------------------------------------------------------------------------------ page
    private fun gap(bottom: Int = 14) = lp(MATCH, WRAP).apply { bottomMargin = dp(bottom) }

    override fun render(col: LinearLayout) {
        val on = Gaming.on

        if (notice.isNotEmpty()) {
            val nc = UiKit.card(this)
            nc.addView(UiKit.text(this, notice, 13f, Palette.text))
            nc.addView(UiKit.button(this, "Dismiss", false) { notice = ""; rebuild() }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
            col.addView(nc, gap())
        }

        // ---- status
        val status = UiKit.card(this)
        status.addView(UiKit.text(this, if (on) "Gaming mode is ON" else "Gaming mode is off", 20f, if (on) Palette.accent else Palette.text, true))
        val sub = if (on) {
            val t = Gaming.startedAt
            val mins = ((System.currentTimeMillis() - t) / 60000L).coerceAtLeast(0)
            "Since " + timeText(t) + "  ·  " + mins + " min so far. Elevate's reminders are paused."
        } else "Start it when you sit down to play. Elevate's habit and workout reminders pause until you leave."
        status.addView(UiKit.text(this, sub, 13f, Palette.dim).apply { setPadding(0, dp(4), 0, dp(10)) })
        status.addView(UiKit.button(this, if (on) "Leave Gaming mode" else "Start Gaming mode", !on) {
            if (on) Gaming.stop(this) else Gaming.start(this)
            rebuild()
        }, lp(MATCH, WRAP))
        col.addView(status, gap())

        // ---- games
        val gc = UiKit.card(this)
        gc.addView(UiKit.text(this, "Your games", 16f, Palette.text, true))
        gc.addView(UiKit.text(this, "Tap a game to open it. Tap ☆ to favourite it, ⋯ for its own settings.", 12.5f, Palette.dim).apply { setPadding(0, dp(2), 0, dp(8)) })
        val search = field("Search games", query)
        search.contentDescription = "Search your games"
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; fillGames() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        gc.addView(search, lp(MATCH, WRAP))
        gc.addView(chips(listOf("name" to "A–Z", "recent" to "Recent", "most" to "Most played"), sort) { sort = it; fillGames() },
            lp(MATCH, WRAP).apply { topMargin = dp(8) })
        gc.addView(chips(listOf("all" to "All games", "fav" to "★ Favourites"), if (onlyFav) "fav" else "all") { onlyFav = it == "fav"; fillGames() },
            lp(MATCH, WRAP).apply { topMargin = dp(6) })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gamesBox = box
        gc.addView(box, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        gc.addView(UiKit.button(this, "Choose which apps are games", false) { chooseGames() }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        col.addView(gc, gap())

        // ---- play time and history (filled once the numbers are read)
        playBox = UiKit.card(this).also { col.addView(it, gap()) }
        historyBox = UiKit.card(this).also { col.addView(it, gap()) }

        // ---- the phone, honestly
        val pc = UiKit.card(this)
        pc.addView(UiKit.text(this, "This phone right now", 16f, Palette.text, true))
        for (line in systemLines()) pc.addView(UiKit.text(this, line, 13.5f, Palette.text).apply { setPadding(0, dp(4), 0, 0) })
        pc.addView(UiKit.text(this, "Read once when this screen opened. It is not live, and Android manages memory by itself: Elevate does not \"clean\" it.", 12f, Palette.dim).apply { setPadding(0, dp(8), 0, dp(8)) })
        pc.addView(UiKit.button(this, "Read again", false) { rebuild() }, lp(MATCH, WRAP))
        col.addView(pc, gap())

        // ---- options
        val oc = UiKit.card(this)
        oc.addView(UiKit.text(this, "While Gaming mode is on", 16f, Palette.text, true))
        val mute = CheckBox(this).apply {
            isChecked = Gaming.muteOthers
            text = "Silence other notifications (Do Not Disturb, priority only)"
            setTextColor(Palette.text)
            setOnCheckedChangeListener { _, v ->
                Gaming.muteOthers = v
                if (v && !Gaming.dndAllowed(this@GamingActivity)) askDndAccess()
                if (Gaming.on) Gaming.refreshDnd(this@GamingActivity)
            }
        }
        oc.addView(mute, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        val dnd = if (Gaming.dndAllowed(this)) "Do Not Disturb access: allowed." else "Do Not Disturb access: not allowed yet. Android asks you to allow it once."
        oc.addView(UiKit.text(this, dnd, 12f, Palette.dim).apply { setPadding(dp(4), 0, 0, dp(8)) })
        val brk = if (Gaming.breakMin > 0) "Break reminder: every ${Gaming.breakMin} min" else "Break reminder: off"
        oc.addView(UiKit.button(this, brk, false) { chooseBreak() }, lp(MATCH, WRAP))
        col.addView(oc, gap())

        // ---- honesty
        col.addView(UiKit.text(this,
            "What Gaming mode does: pauses Elevate's own reminders, can quiet notifications, keeps your games in one list and saves how long you played.\n\n" +
                "What it does not do: make games faster, free memory, change graphics, show a live frame rate, or close other apps. Those claims are usually not true on a normal phone. Nothing runs in the background while you play.\n\n" +
                "How play time works: Elevate knows when you open a game from here or from your home screen, and when you come back to Elevate. Time spent in a game you opened some other way is not counted, and a saved end time is approximate.\n\n" +
                "Tip: add the \"Gaming mode\" tile to your Quick Settings panel (edit tiles) to switch it from anywhere.", 12.5f, Palette.dim))

        fillAll()
    }

    private fun fillAll() { fillGames(); fillPlay(); fillHistory() }

    // ------------------------------------------------------------------------------------------------ games list
    private fun fillGames() {
        val box = gamesBox ?: return
        box.removeAllViews()
        if (!AppCatalog.loaded) {
            box.addView(UiKit.text(this, "Apps are still loading…", 13f, Palette.dim).apply { setPadding(0, dp(4), 0, dp(6)) })
            return
        }
        val all = Gaming.games()
        if (all.isEmpty()) {
            box.addView(UiKit.text(this, "No games found yet. Use the button below to choose which of your apps are games.", 13f, Palette.dim).apply { setPadding(0, dp(4), 0, dp(6)) })
            return
        }
        val st = stats
        var list = all
        if (onlyFav) list = list.filter { GameStore.profile(it.key).favorite }
        val q = query.trim()
        if (q.isNotEmpty()) list = list.filter { it.displayLabel.contains(q, true) }
        list = when (sort) {
            "recent" -> list.sortedByDescending { st?.lastPlayed?.get(it.key) ?: 0L }
            "most" -> list.sortedByDescending { st?.totalMs?.get(it.key) ?: 0L }
            else -> list.sortedBy { it.displayLabel.lowercase() }
        }
        list = list.sortedByDescending { GameStore.profile(it.key).favorite }   // favourites first, keeps the order above

        if (list.isEmpty()) {
            val msg = when {
                q.isNotEmpty() -> "No game matches \"$q\"."
                onlyFav -> "No favourites yet. Tap ☆ next to a game to add one."
                else -> "Nothing to show."
            }
            box.addView(UiKit.text(this, msg, 13f, Palette.dim).apply { setPadding(0, dp(4), 0, dp(6)) })
            return
        }
        val limit = if (showAllGames) list.size else 40
        box.addView(UiKit.text(this, (if (list.size > limit) "Showing $limit of ${list.size} games" else "${list.size} game" + (if (list.size == 1) "" else "s")), 12f, Palette.dim).apply { setPadding(0, 0, 0, dp(2)) })
        for (e in list.take(limit)) box.addView(gameRow(e), lp(MATCH, WRAP))
        if (list.size > limit) {
            box.addView(UiKit.button(this, "Show all ${list.size} games", false) { showAllGames = true; fillGames() }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        }
    }

    private fun gameRow(e: AppEntry): LinearLayout {
        val p = GameStore.profile(e.key)
        val st = stats
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            contentDescription = "Open ${e.displayLabel}"
        }
        val iv = ImageView(this)
        IconLoader.bindEntry(iv, this, e, dp(44))
        row.addView(iv, lp(dp(44), dp(44)))

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(UiKit.text(this, e.displayLabel, 15f, Palette.text))
        val bits = ArrayList<String>()
        val last = st?.lastPlayed?.get(e.key)
        val total = st?.totalMs?.get(e.key) ?: 0L
        if (last != null) bits.add("Last " + dayLabel(last).lowercase() + "  ·  " + SessionMath.label(total) + " total")
        else if (st != null) bits.add("Not played from Elevate yet")
        if (p.dnd) bits.add("Do Not Disturb")
        if (p.remindMin > 0) bits.add("reminder ${p.remindMin} min")
        if (p.autoMode) bits.add("auto Gaming mode")
        if (!p.record) bits.add("time not saved")
        if (bits.isNotEmpty()) info.addView(UiKit.text(this, bits.joinToString("  ·  "), 11.5f, Palette.dim))
        row.addView(info, lp(0, WRAP, 1f).apply { leftMargin = dp(12) })

        val star = UiKit.text(this, if (p.favorite) "★" else "☆", 22f, if (p.favorite) Palette.accent else Palette.dim).apply {
            gravity = Gravity.CENTER
            contentDescription = if (p.favorite) "Remove ${e.displayLabel} from favourites" else "Add ${e.displayLabel} to favourites"
            setOnClickListener {
                GameStore.setProfile(e.key, GameProfile(!p.favorite, p.record, p.dnd, p.remindMin, p.autoMode))
                fillGames()
            }
        }
        row.addView(star, lp(dp(44), dp(44)))
        val more = UiKit.text(this, "⋯", 22f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Settings for ${e.displayLabel}"
            setOnClickListener { profileDialog(e) }
        }
        row.addView(more, lp(dp(44), dp(44)))
        row.setOnClickListener { launchGame(e, row) }
        return row
    }

    private fun launchGame(e: AppEntry, from: android.view.View) {
        val now = System.currentTimeMillis()
        if (now - lastLaunchAt < 900L) return      // a double tap must not open it twice
        lastLaunchAt = now
        if (AppCatalog.get(e.key) == null) {
            toast("${e.displayLabel} is no longer installed")
            Gaming.pruneMissing()
            fillGames()
            return
        }
        Launch.app(this, e.pkg, e.cls, from)
    }

    private fun chooseGames() {
        val all = AppCatalog.visible().sortedBy { it.displayLabel.lowercase() }
        if (all.isEmpty()) { toast("Apps are still loading, try again"); return }
        val chosen = Gaming.apps
        val current = if (chosen.isEmpty()) all.filter { it.group == AppCatalog.GAMES }.map { it.key }.toSet() else chosen
        val checked = BooleanArray(all.size) { all[it].key in current }
        AlertDialog.Builder(this)
            .setTitle("Which apps are games?")
            .setMultiChoiceItems(all.map { it.displayLabel }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Save") { _, _ ->
                Gaming.apps = all.filterIndexed { i, _ -> checked[i] }.map { it.key }.toSet()
                showAllGames = false
                fillGames()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------------------------------------ per-game settings
    private fun profileDialog(e: AppEntry) {
        val p = GameStore.profile(e.key)
        var fav = p.favorite
        var rec = p.record
        var dnd = p.dnd
        var remind = p.remindMin
        var auto = p.autoMode
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        fun check(label: String, note: String, init: Boolean, onChange: (Boolean) -> Unit) {
            val cb = CheckBox(this).apply {
                text = label
                isChecked = init
                setTextColor(Palette.text)
                setOnCheckedChangeListener { _, v -> onChange(v) }
            }
            content.addView(cb, lp(MATCH, WRAP).apply { topMargin = dp(6) })
            if (note.isNotEmpty()) content.addView(UiKit.text(this, note, 12f, Palette.dim).apply { setPadding(dp(4), 0, 0, dp(2)) })
        }
        check("Favourite", "", fav) { fav = it }
        check("Save play time for this game", "Time between opening it from Elevate and coming back to Elevate.", rec) { rec = it }
        check("Silence notifications while I play it",
            "Uses Do Not Disturb (priority only). Android asks you to allow Do Not Disturb access once. Elevate puts your old setting back when you come back to Elevate.", dnd) {
            dnd = it
            if (it && !Gaming.dndAllowed(this)) askDndAccess()
        }
        check("Turn on Gaming mode when I open it", "Pauses Elevate's own reminders. It switches off again when you come back.", auto) { auto = it }
        content.addView(UiKit.text(this, "Remind me while I play (minutes)", 13.5f, Palette.text, true).apply { setPadding(0, dp(12), 0, dp(6)) })
        content.addView(chips(listOf("0" to "Off", "30" to "30", "45" to "45", "60" to "60", "90" to "90"), remind.toString()) { remind = it.toInt() }, lp(MATCH, WRAP))
        content.addView(UiKit.text(this, "Shows a notification every so often while the game is open. Needs notifications allowed.", 12f, Palette.dim).apply { setPadding(dp(4), dp(6), 0, dp(8)) })

        val sv = ScrollView(this)
        sv.addView(content)
        AlertDialog.Builder(this)
            .setTitle(e.displayLabel)
            .setView(sv)
            .setPositiveButton("Save") { _, _ ->
                GameStore.setProfile(e.key, GameProfile(fav, rec, dnd, remind, auto))
                if (remind > 0) ensureNotifications()
                fillGames()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Remove from list") { _, _ -> removeFromList(e) }
            .show()
    }

    private fun removeFromList(e: AppEntry) {
        val chosen = Gaming.apps
        val next = if (chosen.isEmpty()) Gaming.games().map { it.key }.filter { it != e.key }.toSet() else chosen - e.key
        if (next.isEmpty()) {
            toast("At least one game stays in the list. Use \"Choose which apps are games\" to pick different apps.")
            return
        }
        Gaming.apps = next
        GameStore.forget(e.key)
        toast("${e.displayLabel} removed from your games")
        fillGames()
    }

    // ------------------------------------------------------------------------------------------------ play time
    private fun fillPlay() {
        val box = playBox ?: return
        box.removeAllViews()
        box.addView(UiKit.text(this, "Play time", 16f, Palette.text, true))
        val st = stats
        if (st == null) {
            box.addView(UiKit.text(this, if (statsError) "Could not read your play history." else "Loading…", 13f, Palette.dim).apply { setPadding(0, dp(6), 0, 0) })
            return
        }
        val today = st.perDay.lastOrNull() ?: 0L
        val week = st.perDay.sum()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(8)) }
        for ((v, c) in listOf(SessionMath.label(today) to "Today", SessionMath.label(week) to "Last 7 days", st.count.toString() to "Sessions saved")) {
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            cell.addView(UiKit.text(this, v, 17f, Palette.text, true).apply { gravity = Gravity.CENTER })
            cell.addView(UiKit.text(this, c, 11.5f, Palette.dim).apply { gravity = Gravity.CENTER })
            row.addView(cell, lp(0, WRAP, 1f))
        }
        box.addView(row, lp(MATCH, WRAP))
        if (week > 0L) {
            val chart = BarChart(this)
            val labels = st.days.map { SimpleDateFormat("EEE", Locale.getDefault()).format(Date(it)) }
            chart.set(st.perDay.map { Math.round(it / 60000f).toFloat() }, labels, null, " min", st.days.lastIndex)
            box.addView(chart, lp(MATCH, WRAP))
            box.addView(UiKit.text(this, "Minutes per day, last 7 days.", 12f, Palette.dim).apply { setPadding(0, dp(2), 0, dp(6)) })
        } else {
            box.addView(UiKit.text(this, "No play time saved in the last 7 days.", 13f, Palette.dim).apply { setPadding(0, 0, 0, dp(6)) })
        }
        box.noteCard("Tracked time starts when you open a game from Elevate and ends when you come back to Elevate, so the end is approximate. Sessions under 1 minute or over 8 hours are not saved. Times you add by hand are marked as such.", 0)
    }

    private fun fillHistory() {
        val box = historyBox ?: return
        box.removeAllViews()
        box.addView(UiKit.text(this, "History", 16f, Palette.text, true))
        val st = stats
        if (st == null) {
            box.addView(UiKit.text(this, if (statsError) "Could not read your play history." else "Loading…", 13f, Palette.dim).apply { setPadding(0, dp(6), 0, 0) })
            return
        }
        if (st.recent.isEmpty()) {
            box.addView(UiKit.text(this, "No sessions yet. Open a game from the list above and come back: Elevate saves the time in between.", 13f, Palette.dim).apply { setPadding(0, dp(6), 0, dp(6)) })
        } else {
            for (s in st.recent.take(historyLimit)) box.addView(sessionRow(s), lp(MATCH, WRAP))
            if (st.recent.size > historyLimit) {
                box.addView(UiKit.button(this, "Show more", false) { historyLimit += 30; fillHistory() }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
            } else if (st.count > st.recent.size) {
                box.addView(UiKit.text(this, "Showing the latest ${st.recent.size} of ${st.count} sessions.", 12f, Palette.dim).apply { setPadding(0, dp(6), 0, 0) })
            }
        }
        box.addView(UiKit.button(this, "Add a session by hand", false) { addManual() }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        if (st.count > 0) {
            box.addView(UiKit.button(this, "Delete all history", false) {
                Dialogs.confirm(this, "Delete all play history?", "All ${st.count} saved sessions will be removed from this phone. Your game list and settings stay.", "Delete all") {
                    Io.write { GameStore.deleteAll() }
                    loadStats()
                }
            }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        }
    }

    private fun sessionRow(s: GameSession): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(UiKit.text(this, s.name, 14.5f, Palette.text, true))
        info.addView(UiKit.text(this, dayLabel(s.start) + " " + timeText(s.start) + " – " + timeText(s.end) + "  ·  " + SessionMath.label(s.durationMs), 13f, Palette.dim))
        val badge = when {
            !s.confirmedStart -> "Added by hand"
            s.approximateEnd -> "Tracked from Elevate · end time approximate"
            else -> "Tracked from Elevate"
        }
        info.addView(UiKit.text(this, badge, 11.5f, Palette.dim))
        row.addView(info, lp(0, WRAP, 1f))
        val del = UiKit.text(this, "Delete", 13f, Palette.accent, true).apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(44)
            setPadding(dp(12), 0, dp(4), 0)
            contentDescription = "Delete this ${s.name} session"
            setOnClickListener {
                Dialogs.confirm(this@GamingActivity, "Delete this session?",
                    s.name + ", " + dayLabel(s.start) + " " + timeText(s.start) + ", " + SessionMath.label(s.durationMs) + ". This cannot be undone.", "Delete") {
                    Io.write { GameStore.delete(s.id) }
                    loadStats()
                }
            }
        }
        row.addView(del, lp(WRAP, dp(44)))
        return row
    }

    private fun addManual() {
        val games = Gaming.games()
        if (games.isEmpty()) { toast("Add a game to your list first"); return }
        var chosen = games.first()
        var date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var time = "20:00"
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(UiKit.text(this, "For a session Elevate did not see, for example a game you opened some other way. It is saved and shown as \"added by hand\".", 12.5f, Palette.dim))
        fun setValue(r: LinearLayout?, v: String) { (r?.getChildAt(1) as? TextView)?.text = "$v  ›" }
        var gameRow: LinearLayout? = null
        var dateRow: LinearLayout? = null
        var timeRow: LinearLayout? = null
        content.fieldLabel("Game")
        gameRow = content.pickRow("Game", chosen.displayLabel) {
            Dialogs.list(this, "Game", games.map { it.displayLabel }) { i -> chosen = games[i]; setValue(gameRow, chosen.displayLabel) }
        }
        content.fieldLabel("When did you start?")
        dateRow = content.pickRow("Date", date) { pickDate(date) { d -> date = d; setValue(dateRow, d) } }
        timeRow = content.pickRow("Start time", clockText(time)) { pickTime(time) { t -> time = t; setValue(timeRow, clockText(t)) } }
        content.fieldLabel("How many minutes did you play?")
        val dur = field("Minutes, 1 to 720", "", number = true)
        content.addView(dur, lp(MATCH, WRAP))
        formDialog("Add a session", content, "Add") {
            val m = dur.text.toString().trim().toIntOrNull()
            if (m == null || m < 1 || m > 720) { toast("Enter whole minutes between 1 and 720"); return@formDialog false }
            val start = try { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$date $time")?.time } catch (ex: Exception) { null }
            if (start == null) { toast("That date or time is not valid"); return@formDialog false }
            val end = start + m * 60_000L
            if (end > System.currentTimeMillis()) { toast("That session would end in the future"); return@formDialog false }
            val g = chosen
            Io.write {
                val clash = GameStore.overlapping(start, end)
                if (clash.isNotEmpty()) {
                    Io.ui { toast("That overlaps a saved session, so it was not added. Delete the other one first if it is wrong.") }
                } else {
                    GameStore.add(g.key, g.displayLabel, start, end, GameStore.KIND_MANUAL, GameStore.END_ENTERED)
                    Io.ui { toast("Session added"); loadStats() }
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------------------------------------ data
    /** Runs on the same single queue as database writes, so a session that was saved a moment ago is always included. */
    private fun loadStats() {
        val now = System.currentTimeMillis()
        Io.write {
            try {
                val days = SessionMath.lastDays(now, 7)
                val week = GameStore.overlapping(days.first(), now + 1)
                val per = SessionMath.perDay(week.map { Span(it.start, it.end) }, days)
                val totals = GameStore.totals()
                val st = Stats(days, per, GameStore.recent(200), GameStore.count(),
                    totals.mapValues { it.value.first }, totals.mapValues { it.value.second })
                Io.ui { if (!isFinishing && !isDestroyed) { stats = st; statsError = false; fillAll() } }
            } catch (t: Throwable) {
                android.util.Log.e("Elevate", "could not read play history", t)
                Io.ui { if (!isFinishing && !isDestroyed) { statsError = true; fillAll() } }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ small helpers
    private fun timeText(ms: Long): String = android.text.format.DateFormat.getTimeFormat(this).format(Date(ms))

    private fun dayLabel(ms: Long): String {
        val d0 = SessionMath.dayStart(System.currentTimeMillis())
        val y0 = SessionMath.dayStart(d0 - 1)
        return when {
            ms >= d0 -> "Today"
            ms >= y0 -> "Yesterday"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
        }
    }

    private fun gb(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

    /** Real numbers Android reports right now. Anything the phone does not report is left out, not guessed. */
    private fun systemLines(): List<String> {
        val out = ArrayList<String>()
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            out.add("Memory: " + gb(mi.availMem) + " free of " + gb(mi.totalMem))
        } catch (t: Throwable) { /* not reported */ }
        try {
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (b != null) {
                val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (level >= 0 && scale > 0) {
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    out.add("Battery: " + (level * 100 / scale) + "%" + (if (charging) ", charging" else "") +
                        (if (temp != Int.MIN_VALUE) String.format(Locale.US, ", %.1f °C", temp / 10.0) else ""))
                }
            }
        } catch (t: Throwable) { /* not reported */ }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val name = when (pm.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "normal"
                    PowerManager.THERMAL_STATUS_LIGHT -> "light"
                    PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                    PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                    else -> null
                }
                if (name != null) out.add("Heat level reported by Android: $name")
            } catch (t: Throwable) { /* not reported */ }
        }
        try {
            val sf = StatFs(Environment.getDataDirectory().path)
            out.add("Storage: " + gb(sf.availableBytes) + " free of " + gb(sf.totalBytes))
        } catch (t: Throwable) { /* not reported */ }
        if (out.isEmpty()) out.add("This phone did not report any details.")
        return out
    }

    // ------------------------------------------------------------------------------------------------ permissions and options
    private fun chooseBreak() {
        val labels = listOf("Off", "Every 30 minutes", "Every 45 minutes", "Every 60 minutes", "Every 90 minutes")
        val values = listOf(0, 30, 45, 60, 90)
        Dialogs.list(this, "Break reminder", labels) { i ->
            Gaming.breakMin = values[i]
            if (values[i] > 0) ensureNotifications()
            if (Gaming.on) Gaming.scheduleBreak(this)
            rebuild()
        }
    }

    /** Reminders are notifications. On newer Android versions the phone asks once; we explain first. */
    private fun ensureNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Dialogs.confirm(this, "Allow notifications?",
                "Reminders are shown as notifications. Android will ask once. If you say no, the reminder is still saved but nothing is shown.",
                "Continue") { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    private fun askDndAccess() {
        Dialogs.confirm(this, "Allow Do Not Disturb access?",
            "Android needs you to allow this once, on its own settings screen. Elevate changes Do Not Disturb only while Gaming mode is on with the option ticked, or while you play a game that asks for it, and puts your old setting back afterwards. This access does not let Elevate read your notifications.",
            "Open settings") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            catch (e: Exception) { toast("Open Settings > Apps > Special access > Do Not Disturb") }
        }
    }

    // ------------------------------------------------------------------------------------------------ emulator test hooks
    /** Used only by the automated emulator test; harmless otherwise. */
    private fun ciHooks(i: Intent?) {
        if (i == null) return
        val minMs = i.getLongExtra("ci_play_min_ms", -1L)
        if (minMs >= 0L) Play.minSavedMs = minMs
        val pkg = i.getStringExtra("ci_gaming_pkg")
        if (pkg != null) {
            Io.uiDelayed(2500L, Runnable {
                val e = AppCatalog.byPackage(pkg)
                if (e != null && !isFinishing) {
                    Gaming.apps = setOf(e.key)
                    fillGames()
                    if (i.getBooleanExtra("ci_gaming_launch", false)) Launch.app(this, e.pkg, e.cls)
                }
            })
        }
        if (i.getBooleanExtra("ci_gaming_seed", false)) {
            Io.uiDelayed(2500L, Runnable {
                val e = Gaming.games().firstOrNull() ?: AppCatalog.visible().firstOrNull()
                if (e != null) {
                    val now = System.currentTimeMillis()
                    val h = 3_600_000L
                    Io.write {
                        GameStore.add(e.key, e.displayLabel, now - 27 * h, now - 27 * h + 45 * 60_000L, GameStore.KIND_MANUAL, GameStore.END_ENTERED)
                        GameStore.add(e.key, e.displayLabel, now - 6 * h, now - 6 * h + 30 * 60_000L, GameStore.KIND_MANUAL, GameStore.END_ENTERED)
                        GameStore.add(e.key, e.displayLabel, now - 3 * h, now - 3 * h + 75 * 60_000L, GameStore.KIND_TRACKED, GameStore.END_RETURNED)
                    }
                    loadStats()
                }
            })
        }
        if (i.getBooleanExtra("ci_gaming_report", false)) {
            Io.uiDelayed(1500L, Runnable {
                Io.write {
                    val recent = GameStore.recent(200)
                    android.util.Log.i("ElevateCI", "game_sessions=" + GameStore.count() + " tracked=" + recent.count { it.kind == GameStore.KIND_TRACKED } +
                        " active=" + (Play.active != null))
                }
            })
        }
    }
}
