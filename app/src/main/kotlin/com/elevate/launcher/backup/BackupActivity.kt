package com.elevate.launcher.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.PageActivity
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.Io
import com.elevate.launcher.ui.home.HomeActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings, "Your data", "Backup and restore". Everything happens on this phone; the file goes where the person puts it. */
class BackupActivity : PageActivity() {
    override val pageTitle: String get() = "Backup and restore"

    private var optHidden = false
    private var optCenterSettings = true
    private var optCenterRecords = false
    private var busy = false

    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }

    override fun render(col: LinearLayout) {
        col.addView(UiKit.text(this, "Save your home screen and settings to a file, and bring them back later or on another phone. The file stays where you save it. Elevate never uploads it.", 14f, Palette.dim).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        // ---- make a backup
        val make = UiKit.card(this)
        make.addView(UiKit.text(this, "Make a backup", 16f, Palette.text, true))
        make.addView(UiKit.text(this, "Always included: home layout, folders, dock, pages, launcher settings, icon choices and custom app names.", 13f, Palette.dim).apply { setPadding(0, dp(4), 0, dp(8)) })
        make.addView(option("Center settings", "Reminders, training days and other Center options.", optCenterSettings) { optCenterSettings = it })
        make.addView(option("Center records", "Habits, workouts, measurements, goals, sleep and care log. These are personal, so keep the file somewhere safe.", optCenterRecords) { optCenterRecords = it })
        make.addView(option("List of hidden apps", "Anyone who can open the file can read which apps you hide.", optHidden) { optHidden = it })
        make.addView(UiKit.text(this, "Never included: your access code, photos, widgets and an imported font file (add those again after restoring).", 13f, Palette.dim).apply { setPadding(0, dp(8), 0, dp(10)) })
        make.addView(UiKit.button(this, "Create backup file", true) { startCreate() }, lp(MATCH, WRAP))
        col.addView(make, lp(MATCH, WRAP).apply { bottomMargin = dp(14) })

        // ---- restore
        val rest = UiKit.card(this)
        rest.addView(UiKit.text(this, "Restore from a backup", 16f, Palette.text, true))
        rest.addView(UiKit.text(this, "You choose a file, see what is in it, and pick what to restore. Nothing changes until you confirm. Apps in the backup that are not installed on this phone are left out.", 13f, Palette.dim).apply { setPadding(0, dp(4), 0, dp(10)) })
        rest.addView(UiKit.button(this, "Choose a backup file", false) { startOpen() }, lp(MATCH, WRAP))
        col.addView(rest, lp(MATCH, WRAP).apply { bottomMargin = dp(14) })

        col.addView(UiKit.text(this, "Not medical advice, and not a cloud service: a backup is just a text file you keep yourself.", 12f, Palette.dim))
    }

    private fun option(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val cb = CheckBox(this).apply {
            isChecked = checked
            contentDescription = title
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(cb, lp(WRAP, WRAP))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(UiKit.text(this, title, 15f, Palette.text))
        texts.addView(UiKit.text(this, sub, 12f, Palette.dim))
        row.addView(texts, lp(0, WRAP, 1f).apply { leftMargin = dp(6) })
        row.setOnClickListener { cb.toggle() }
        return row
    }

    // ------------------------------------------------------------------ create
    private fun startCreate() {
        if (busy) return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        try {
            createDoc.launch("elevate-backup-$stamp.json")
        } catch (e: Exception) {
            Dialogs.info(this, "No file picker", "This phone has no app that can save files here. Install a file manager, then try again.")
        }
    }

    private fun writeBackup(uri: Uri) {
        busy = true
        val o = Backup.Options(optHidden, optCenterSettings, optCenterRecords)
        val app = applicationContext
        Io.work {
            val msg = try {
                val text = Backup.create(app, o)
                val bytes = text.toByteArray(Charsets.UTF_8)
                app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IllegalStateException("cannot open the file")
                "Saved. ${bytes.size / 1024 + 1} KB.\n\nKeep this file somewhere safe. To use it, choose it under \"Restore from a backup\"."
            } catch (t: Throwable) {
                Log.e("Elevate", "backup failed", t)
                "The backup could not be saved. Try another place, or free some space."
            }
            Io.ui { busy = false; if (!isFinishing) Dialogs.info(this, "Backup", msg) }
        }
    }

    // ------------------------------------------------------------------ restore
    private fun startOpen() {
        if (busy) return
        try {
            openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
        } catch (e: Exception) {
            Dialogs.info(this, "No file picker", "This phone has no app that can open files here.")
        }
    }

    private fun readBackup(uri: Uri) {
        busy = true
        val app = applicationContext
        Io.work {
            var text: String? = null
            var err: String? = null
            try {
                app.contentResolver.openInputStream(uri)?.use { ins ->
                    val buf = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val n = ins.read(chunk)
                        if (n < 0) break
                        buf.write(chunk, 0, n)
                        if (buf.size() > Backup.MAX_BYTES) { err = "This file is too big to be an Elevate backup."; break }
                    }
                    if (err == null) text = buf.toString("UTF-8")
                    Unit
                } ?: run { err = "The file could not be opened." }
            } catch (t: Throwable) {
                Log.e("Elevate", "reading backup failed", t)
                err = "The file could not be read."
            }
            val t = text
            val result = if (t != null) Backup.inspect(t) else (null to err)
            Io.ui {
                busy = false
                if (isFinishing) return@ui
                val p = result.first
                if (p == null || t == null) Dialogs.info(this, "Cannot restore", result.second ?: "This file cannot be used.")
                else chooseParts(t, p)
            }
        }
    }

    private fun chooseParts(text: String, p: Backup.Preview) {
        val labels = ArrayList<String>()
        val keys = ArrayList<String>()
        if (p.hasLayout) { labels.add("Home layout and launcher settings (${p.items} items)"); keys.add("layout") }
        if (p.hasLayout && p.hasHidden) { labels.add("List of hidden apps"); keys.add("hidden") }
        if (p.hasCenterSettings) { labels.add("Center settings"); keys.add("csettings") }
        if (p.hasCenterRecords) { labels.add("Center records (${p.habits} habits, ${p.sessions} workouts). Replaces what is on this phone"); keys.add("crecords") }
        if (labels.isEmpty()) { Dialogs.info(this, "Nothing to restore", "This backup has no parts that can be restored."); return }
        val checked = BooleanArray(labels.size) { true }
        val whenMade = if (p.created > 0) SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(p.created)) else "unknown date"
        val dlg = AlertDialog.Builder(this)
            .setTitle("Restore from backup")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Restore") { _, _ ->
                val sel = keys.filterIndexed { i, _ -> checked[i] }.toSet()
                if (sel.isEmpty()) toast("Nothing selected")
                else confirmRestore(text, sel)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.setMessage("Made on $whenMade")
        dlg.show()
    }

    private fun confirmRestore(text: String, sel: Set<String>) {
        val layout = "layout" in sel
        Dialogs.confirm(this, "Replace what is on this phone?",
            "The parts you picked replace the current ones. " + (if (layout) "Your home screen will be rearranged. " else "") + "This cannot be undone, so make a backup first if you are not sure.",
            "Restore now") { runRestore(text, sel) }
    }

    private fun runRestore(text: String, sel: Set<String>) {
        busy = true
        val parts = Backup.Parts("layout" in sel, "hidden" in sel, "csettings" in sel, "crecords" in sel)
        val app: Context = applicationContext
        Io.work {
            Backup.restore(app, text, parts) { rep ->
                busy = false
                Prefs.putInt("restore_stamp", Prefs.getInt("restore_stamp", 0) + 1)
                if (isFinishing) return@restore
                AlertDialog.Builder(this)
                    .setTitle(if (rep.ok) "Restored" else "Restore problem")
                    .setMessage(rep.message)
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ ->
                        val i = Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(i)
                        finish()
                    }
                    .show()
            }
        }
    }

    // ------------------------------------------------------------------ CI self-test (harmless otherwise)
    override fun onResume() {
        super.onResume()
        if (intent?.getBooleanExtra("ci_selftest", false) == true) {
            intent.removeExtra("ci_selftest")
            ciSelfTest()
        }
    }

    private fun ciSelfTest() {
        val app = applicationContext
        Io.work {
            val out = StringBuilder()
            try {
                val all = Backup.create(app, Backup.Options(hidden = false, centerSettings = true, centerRecords = true))
                val (pv, err) = Backup.inspect(all)
                out.append("create: ${all.length} chars; inspect: ").append(pv?.let { "items=${it.items} habits=${it.habits} sessions=${it.sessions}" } ?: err).append('\n')
                val bad1 = Backup.inspect("{\"format\":\"nope\"}").second
                val bad2 = Backup.inspect("not json at all").second
                val future = Backup.inspect("{\"format\":\"${Backup.FORMAT}\",\"version\":99}").second
                out.append("rejects: ${bad1 != null} ${bad2 != null} ${future != null}\n")
                val noCode = !all.contains("hash") && !all.contains("salt")
                out.append("no access-code data in file: $noCode\n")
                val finished = java.util.concurrent.atomic.AtomicBoolean(false)
                Backup.restore(app, all, Backup.Parts(true, false, true, true)) { rep ->
                    out.append("restore: ok=${rep.ok} ${rep.message.replace('\n', ';')}\n")
                    finished.set(true)
                }
                var waited = 0
                while (!finished.get() && waited < 100) { Thread.sleep(100); waited++ }
                val again = Backup.create(app, Backup.Options(hidden = false, centerSettings = true, centerRecords = true))
                val (pv2, _) = Backup.inspect(again)
                out.append("after restore: items=${pv2?.items} habits=${pv2?.habits} sessions=${pv2?.sessions}\n")
                out.append("round trip same size: ${again.length == all.length}\n")
            } catch (t: Throwable) {
                out.append("SELFTEST ERROR: ").append(t.toString()).append('\n')
            }
            Log.i("ElevateCI", "backup selftest\n$out")
            Io.ui {
                if (!isFinishing) Dialogs.info(this, "Backup self-test", out.toString())
            }
        }
    }
}
