package com.elevate.launcher.center.ui

import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.Photo
import com.elevate.launcher.center.data.PhotoRepo
import com.elevate.launcher.center.data.PhotoThumbs
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.Io
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** Blocks screenshots and the recent-apps preview while a photo screen is open (not during automated tests). */
private fun secure(host: CenterActivity, on: Boolean) {
    if (host.intent?.getStringExtra("ci_nosecure") != null) return
    if (on) host.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    else host.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

private fun photoLabel(p: Photo) = Days.pretty(p.day) + "  ·  " + PhotoRepo.kindLabel(p.kind)

/**
 * The photo journal: your own progress photos, kept only on this phone. Nothing here scores or rates anything.
 * It exists so you can see for yourself how posture, grooming or fitness changes over weeks.
 */
class PhotoJournalScreen(host: CenterActivity) : Screen(host) {
    override val title = "Photo journal"
    override val actionLabel: String? = "Add"
    private var filter = "all"

    override fun onAction() { addPhoto() }
    override fun onShow() { secure(host, true) }
    override fun onHide() { secure(host, false) }

    override fun build(): View {
        val col = host.vbox()
        col.noteCard("Private. Pictures are saved inside Elevate on this phone only, not in your gallery. They are never uploaded, never rated or scored, and are not part of backups. Screenshots are blocked on these screens.")

        val all = PhotoRepo.all()
        if (all.isEmpty()) {
            col.emptyState("No photos yet", "Take one now, or pick one from your gallery. Photos taken the same way each time are the easiest to compare.", "Add a photo") { addPhoto() }
            col.card {
                cardTitle("For photos you can compare")
                para("• Same spot, same light (a window works well), same distance\n• Same time of day, relaxed face or normal standing posture\n• A plain background, nothing covering the area you want to track\n• Every 2 to 4 weeks is plenty. Daily photos mostly show noise.", sp = 13f, bottom = 0)
            }
            return col
        }

        col.addView(host.chips(listOf("all" to "All") + PhotoRepo.kinds.take(3).map { it.first to it.second.replace("Face, ", "").replace(", full body", "") }, filter) {
            filter = it
            host.refresh()
        }, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })

        val shown = if (filter == "all") all else all.filter { it.kind == filter }
        if (shown.isEmpty()) {
            col.para("No photos in this group yet.")
        } else {
            val cell = (host.resources.displayMetrics.widthPixels - host.dp(32) - host.dp(12)) / 3
            var row: LinearLayout? = null
            for ((i, p) in shown.withIndex()) {
                if (i % 3 == 0) {
                    row = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
                    col.addView(row, lp(MATCH, WRAP).apply { bottomMargin = host.dp(6) })
                }
                row?.addView(thumb(p, cell), lp(cell, cell).apply { rightMargin = host.dp(6) })
            }
        }
        col.gap(8)
        if (all.size >= 2) col.actionButton("Compare two photos", false) { compare(all) }
        col.actionButton("Delete all journal photos", false) {
            Dialogs.confirm(host, "Delete all photos?", "Every photo in the journal and its picture file is removed from this phone. This cannot be undone.", "Delete all") {
                PhotoRepo.deleteAll(host)
                host.toast("Deleted")
                host.refresh()
            }
        }
        return col
    }

    private fun thumb(p: Photo, size: Int): View {
        val f = FrameLayout(host)
        f.background = UiKit.rounded(Palette.surface2, host.dpf(10f))
        val iv = ImageView(host).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = photoLabel(p) }
        PhotoThumbs.load(host, p, 360, iv)
        f.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
        val tag = UiKit.text(host, Days.pretty(p.day), 10.5f, 0xFFFFFFFF.toInt(), true).apply {
            setPadding(host.dp(6), host.dp(2), host.dp(6), host.dp(2))
            background = UiKit.rounded(0x99000000.toInt(), host.dpf(8f))
        }
        f.addView(tag, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { leftMargin = host.dp(4); bottomMargin = host.dp(4) })
        f.setOnClickListener { host.push(PhotoDetailScreen(host, p.id)) }
        return f
    }

    // ------------------------------------------------------------ adding
    private fun addPhoto() {
        Dialogs.list(host, "What is this photo of?", PhotoRepo.kinds.map { it.second }) { i ->
            val kind = PhotoRepo.kinds[i].first
            Dialogs.list(host, "Add photo", listOf("Take a photo now", "Choose from gallery")) { j ->
                if (j == 0) capture(kind) else pick(kind)
            }
        }
    }

    private fun capture(kind: String) {
        val dir = File(host.cacheDir, "capture")
        dir.mkdirs()
        val tmp = File(dir, "cap-" + System.currentTimeMillis() + ".jpg")
        val uri = try {
            FileProvider.getUriForFile(host, host.packageName + ".files", tmp)
        } catch (t: Throwable) {
            host.toast("Could not prepare the camera")
            return
        }
        try {
            host.takePicture(uri) { ok ->
                if (ok && tmp.exists() && tmp.length() > 0) {
                    save(kind, FileInputStream(tmp)) { tmp.delete() }
                } else {
                    tmp.delete()
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            host.toast("No camera app was found. Choose from gallery instead.")
        }
    }

    private fun pick(kind: String) {
        try {
            host.pickImage { uri ->
                if (uri != null) {
                    val ins = try { host.contentResolver.openInputStream(uri) } catch (t: Throwable) { null }
                    if (ins == null) host.toast("That picture could not be opened") else save(kind, ins) {}
                }
            }
        } catch (e: Exception) {
            host.toast("No gallery app was found")
        }
    }

    private fun save(kind: String, ins: InputStream, cleanup: () -> Unit) {
        Io.work {
            val id = try { PhotoRepo.saveFrom(host, ins, Days.today(), kind, "") } finally { try { ins.close() } catch (t: Throwable) { } ; cleanup() }
            Io.ui {
                if (id < 0) {
                    host.toast("That photo could not be saved")
                } else {
                    val tips = PhotoRepo.get(id)?.let { PhotoRepo.advice(host, it) } ?: emptyList()
                    host.refresh()
                    Dialogs.info(host, "Photo saved", "It stays on this phone only.\n\n" + tips.joinToString("\n"))
                }
            }
        }
    }

    private fun compare(all: List<Photo>) {
        val ordered = all.sortedWith(compareBy({ it.day }, { it.id }))
        val labels = ordered.map { photoLabel(it) }.toTypedArray()
        val checked = BooleanArray(ordered.size)
        AlertDialog.Builder(host)
            .setTitle("Pick two photos")
            .setMultiChoiceItems(labels, checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Compare") { _, _ ->
                val sel = ordered.filterIndexed { i, _ -> checked[i] }
                if (sel.size != 2) host.toast("Pick exactly two photos") else host.push(PhotoCompareScreen(host, sel[0].id, sel[1].id))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

/** One photo, large, with its note and plain technical hints. */
class PhotoDetailScreen(host: CenterActivity, private val id: Long) : Screen(host) {
    override val title = "Photo"
    override fun onShow() { secure(host, true) }
    override fun onHide() { secure(host, false) }

    override fun build(): View {
        val col = host.vbox()
        val p = PhotoRepo.get(id)
        if (p == null) {
            col.emptyState("Photo not found", "It may have been deleted.")
            return col
        }
        val iv = ImageView(host).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = UiKit.rounded(Palette.surface2, host.dpf(14f))
            contentDescription = photoLabel(p)
            minimumHeight = host.dp(240)
        }
        PhotoThumbs.load(host, p, 1200, iv)
        col.addView(iv, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
        col.heading(photoLabel(p), 4)
        col.para(if (p.note.isNotEmpty()) p.note else "No note yet.", dim = p.note.isEmpty())
        col.actionButton("Edit note", false) {
            Dialogs.prompt(host, "Note", p.note, "What was different today? (optional)") {
                PhotoRepo.setNote(p.id, it.trim().take(500))
                host.refresh()
            }
        }
        col.card {
            cardTitle("Picture quality")
            val tips = PhotoRepo.advice(host, p)
            para(tips.joinToString("\n"), sp = 13f, bottom = 4)
            para("These are only technical hints about the picture (light and sharpness). They say nothing about you.", sp = 12f, bottom = 0)
        }
        col.actionButton("Delete this photo", false) {
            Dialogs.confirm(host, "Delete this photo?", "The picture is removed from this phone. This cannot be undone.", "Delete") {
                PhotoRepo.delete(host, p.id)
                host.pop()
            }
        }
        return col
    }
}

/** Two photos side by side with the dates, so changes over time are easy to see by eye. */
class PhotoCompareScreen(host: CenterActivity, private val aId: Long, private val bId: Long) : Screen(host) {
    override val title = "Compare"
    override fun onShow() { secure(host, true) }
    override fun onHide() { secure(host, false) }

    override fun build(): View {
        val col = host.vbox()
        val a = PhotoRepo.get(aId)
        val b = PhotoRepo.get(bId)
        if (a == null || b == null) {
            col.emptyState("Photos not found", "One of them may have been deleted.")
            return col
        }
        val gap = Days.diff(a.day, b.day)
        col.para(if (gap == 0) "Taken the same day." else pluralize(Math.abs(gap), "day") + " apart.", sp = 14f)
        val row = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, p) in listOf(a, b).withIndex()) {
            val side = host.vbox()
            val iv = ImageView(host).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = UiKit.rounded(Palette.surface2, host.dpf(12f))
                minimumHeight = host.dp(200)
                contentDescription = photoLabel(p)
            }
            PhotoThumbs.load(host, p, 720, iv)
            side.addView(iv, lp(MATCH, WRAP))
            side.addView(UiKit.text(host, Days.pretty(p.day), 13f, Palette.text, true).apply { gravity = Gravity.CENTER; setPadding(0, host.dp(6), 0, 0) }, lp(MATCH, WRAP))
            side.addView(UiKit.text(host, PhotoRepo.kindLabel(p.kind), 12f, Palette.dim).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
            row.addView(side, lp(0, WRAP, 1f).apply { if (i == 0) rightMargin = host.dp(6) else leftMargin = host.dp(6) })
        }
        col.addView(row, lp(MATCH, WRAP).apply { bottomMargin = host.dp(12) })
        col.noteCard("Light, angle, distance and posture change how a photo looks. A difference between two pictures is not a measurement and not a score. If something worries you, talk to a doctor.")
        return col
    }
}
