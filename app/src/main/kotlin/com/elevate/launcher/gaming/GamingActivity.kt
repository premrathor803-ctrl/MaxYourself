package com.elevate.launcher.gaming

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.elevate.launcher.ui.IconLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Set up and switch Gaming mode, and launch the chosen games from one distraction-free list. */
class GamingActivity : PageActivity() {
    override val pageTitle: String get() = "Gaming mode"

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent?.getBooleanExtra("ci_gaming_on", false) == true && !Gaming.on) Gaming.start(applicationContext)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (isBuilt()) rebuild()
    }

    override fun render(col: LinearLayout) {
        val on = Gaming.on

        // ---- status
        val status = UiKit.card(this)
        status.addView(UiKit.text(this, if (on) "Gaming mode is ON" else "Gaming mode is off", 20f, if (on) Palette.accent else Palette.text, true))
        val sub = if (on) {
            val t = Gaming.startedAt
            val mins = ((System.currentTimeMillis() - t) / 60000L).coerceAtLeast(0)
            "Since " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t)) + "  ·  " + mins + " min so far. Elevate's reminders are paused."
        } else "Start it when you sit down to play. Elevate's habit and workout reminders pause until you leave."
        status.addView(UiKit.text(this, sub, 13f, Palette.dim).apply { setPadding(0, dp(4), 0, dp(10)) })
        status.addView(UiKit.button(this, if (on) "Leave Gaming mode" else "Start Gaming mode", !on) {
            if (on) Gaming.stop(this) else Gaming.start(this)
            rebuild()
        }, lp(MATCH, WRAP))
        col.addView(status, lp(MATCH, WRAP).apply { bottomMargin = dp(14) })

        // ---- games
        val games = Gaming.games()
        val gc = UiKit.card(this)
        gc.addView(UiKit.text(this, "Your games", 16f, Palette.text, true))
        if (games.isEmpty()) {
            gc.addView(UiKit.text(this, if (AppCatalog.loaded) "No games found yet. Choose which apps count as games." else "Apps are still loading…", 13f, Palette.dim).apply { setPadding(0, dp(4), 0, dp(6)) })
        } else {
            for (e in games.take(30)) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                val iv = ImageView(this)
                IconLoader.bindEntry(iv, this, e, dp(44))
                row.addView(iv, lp(dp(44), dp(44)))
                row.addView(UiKit.text(this, e.displayLabel, 15f, Palette.text), lp(0, WRAP, 1f).apply { leftMargin = dp(12) })
                row.setOnClickListener { Launch.app(this, e.pkg, e.cls, row) }
                gc.addView(row, lp(MATCH, WRAP))
            }
        }
        gc.addView(UiKit.button(this, "Choose games", false) { chooseGames() }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        col.addView(gc, lp(MATCH, WRAP).apply { bottomMargin = dp(14) })

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
            }
        }
        oc.addView(mute, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        val dnd = if (Gaming.dndAllowed(this)) "Do Not Disturb access: allowed." else "Do Not Disturb access: not allowed yet. Android asks you to allow it once."
        oc.addView(UiKit.text(this, dnd, 12f, Palette.dim).apply { setPadding(dp(4), 0, 0, dp(8)) })
        val brk = if (Gaming.breakMin > 0) "Break reminder: every ${Gaming.breakMin} min" else "Break reminder: off"
        oc.addView(UiKit.button(this, brk, false) { chooseBreak() }, lp(MATCH, WRAP))
        col.addView(oc, lp(MATCH, WRAP).apply { bottomMargin = dp(14) })

        // ---- honesty
        col.addView(UiKit.text(this, "What this does not do: it does not make games run faster, free memory or change graphics. Those claims are usually not true on a normal phone. It only pauses Elevate's reminders, can quiet notifications, and keeps your games in one place.\n\nTip: add the \"Gaming mode\" tile to your Quick Settings panel (edit tiles) to switch it from anywhere.", 12.5f, Palette.dim))
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
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseBreak() {
        val labels = listOf("Off", "Every 30 minutes", "Every 45 minutes", "Every 60 minutes", "Every 90 minutes")
        val values = listOf(0, 30, 45, 60, 90)
        Dialogs.list(this, "Break reminder", labels) { i ->
            Gaming.breakMin = values[i]
            if (values[i] > 0 && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            if (Gaming.on) Gaming.scheduleBreak(this)
            rebuild()
        }
    }

    private fun askDndAccess() {
        Dialogs.confirm(this, "Allow Do Not Disturb access?",
            "Android needs you to allow this once, on its own settings screen. Elevate only changes Do Not Disturb while Gaming mode is on, and puts it back when you leave.",
            "Open settings") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            catch (e: Exception) { toast("Open Settings > Apps > Special access > Do Not Disturb") }
        }
    }
}
