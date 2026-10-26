package com.elevate.launcher.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.AppEntry
import com.elevate.launcher.data.AppMeta
import com.elevate.launcher.data.Io

/** Dialogs for choosing an icon pack and for changing the icon of a single app. */
object IconChooser {

    /** Pick the icon pack used for all apps. [onChanged] runs after the choice is stored. */
    fun pickPack(act: Activity, onChanged: () -> Unit) {
        val packs = IconPacks.discover(act)
        if (packs.isEmpty()) {
            Dialogs.info(
                act, "No icon packs found",
                "Elevate reads icon packs made for ADW and Nova style launchers. Install one from a source you trust, then come back here. Elevate cannot download packs for you, and it has only been checked against the common pack format, not against every pack."
            )
            if (Prefs.iconPack.isNotEmpty()) {
                Dialogs.confirm(act, "Use normal icons again?", "The icon pack you chose earlier is no longer listed.", "Use normal icons") {
                    store("")
                    onChanged()
                }
            }
            return
        }
        val labels = ArrayList<String>()
        labels.add("None: each app's own icon")
        for (p in packs) labels.add(p.label)
        Dialogs.list(act, "Icon pack", labels) { i ->
            if (i == 0) {
                store("")
                onChanged()
                act.toast("Using each app's own icon")
            } else {
                store(packs[i - 1].pkg)
                onChanged()
                preview(act, packs[i - 1].pkg, packs[i - 1].label)
            }
        }
    }

    private fun store(pkg: String) {
        Prefs.putString("icon_pack", pkg)
        IconPacks.invalidate()
        IconLoader.clear()
    }

    /** Reads the pack in the background, then shows a few of its icons and how many of your apps it covers. */
    fun preview(act: Activity, pkg: String, label: String) {
        act.toast("Reading $label…")
        Io.work {
            val cov = try { IconPacks.coverage(act, pkg) } catch (t: Throwable) { null }
            val names = if (cov != null) try { IconPacks.sampleNames(act, pkg, 12) } catch (t: Throwable) { emptyList() } else emptyList()
            Io.ui {
                if (act.isFinishing) return@ui
                if (cov == null) {
                    store("")
                    Dialogs.info(act, "Could not read this pack", "$label does not have an icon list that Elevate understands, so normal icons are used.")
                } else {
                    val box = LinearLayout(act)
                    box.orientation = LinearLayout.VERTICAL
                    box.setPadding(act.dp(20), act.dp(8), act.dp(20), 0)
                    box.addView(
                        UiKit.text(act, "${cov.first} of your ${cov.second} apps have an icon in this pack. The others keep their normal icon.", 14f, Palette.text),
                        lp(MATCH, WRAP).apply { bottomMargin = act.dp(12) }
                    )
                    if (names.isNotEmpty()) box.addView(grid(act, pkg, names) { }, lp(MATCH, WRAP))
                    AlertDialog.Builder(act).setTitle(label).setView(ScrollView(act).also { it.addView(box) }).setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun grid(act: Activity, pkg: String, names: List<String>, onPick: (String) -> Unit): LinearLayout {
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        var i = 0
        while (i < names.size) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in 0 until 4) {
                val cell = ImageView(act)
                cell.scaleType = ImageView.ScaleType.FIT_CENTER
                cell.setPadding(act.dp(6), act.dp(6), act.dp(6), act.dp(6))
                val n = names.getOrNull(i + k)
                if (n != null) {
                    val d: Drawable? = try { IconPacks.drawable(act, pkg, n) } catch (t: Throwable) { null }
                    cell.setImageDrawable(d)
                    cell.contentDescription = n
                    cell.setOnClickListener { onPick(n) }
                }
                row.addView(cell, lp(0, act.dp(64), 1f))
            }
            col.addView(row, lp(MATCH, WRAP))
            i += 4
        }
        return col
    }

    // ------------------------------------------------------------ per-app
    fun show(act: Activity, e: AppEntry, onDone: () -> Unit) {
        val pack = Prefs.iconPack
        val packOk = pack.isNotEmpty() && IconPacks.installed(act, pack)
        val current = AppMeta.get(e.key)?.icon ?: ""
        val items = ArrayList<String>()
        val acts = ArrayList<Int>()
        items.add("Use the app's original icon"); acts.add(0)
        if (packOk) { items.add("Choose from " + IconPacks.labelOf(act, pack)); acts.add(1) }
        if (current.isNotEmpty()) { items.add("Restore default"); acts.add(2) }
        if (!packOk) { items.add("Choose from an icon pack (pick a pack in Settings first)"); acts.add(3) }
        Dialogs.list(act, "Icon for " + e.displayLabel, items) { i ->
            when (acts[i]) {
                0 -> { AppMeta.setIcon(e.key, "default"); IconLoader.clear(); onDone() }
                1 -> choose(act, e, pack, onDone)
                2 -> { AppMeta.setIcon(e.key, ""); IconLoader.clear(); onDone() }
                else -> act.toast("Open Settings > Appearance > Icon pack")
            }
        }
    }

    private fun choose(act: Activity, e: AppEntry, pack: String, onDone: () -> Unit) {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(act.dp(16), act.dp(8), act.dp(16), 0)
        val search = EditText(act)
        search.hint = "Search icon names"
        search.isSingleLine = true
        search.background = UiKit.rounded(Palette.surface2, act.dpf(12f))
        search.setPadding(act.dp(14), act.dp(10), act.dp(14), act.dp(10))
        search.setTextColor(Palette.text)
        search.setHintTextColor(Palette.dim)
        root.addView(search, lp(MATCH, WRAP).apply { bottomMargin = act.dp(8) })
        val results = LinearLayout(act)
        results.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(act)
        sv.addView(results)
        root.addView(sv, lp(MATCH, act.dp(320)))
        val dialog = AlertDialog.Builder(act).setTitle("Icon for " + e.displayLabel).setView(root).setNegativeButton("Cancel", null).create()
        fun fill() {
            results.removeAllViews()
            val names = try { IconPacks.search(act, pack, search.text.toString(), 40) } catch (t: Throwable) { emptyList() }
            if (names.isEmpty()) {
                val t = UiKit.text(act, "No icons match.", 14f, Palette.dim)
                t.gravity = Gravity.CENTER
                results.addView(t, lp(MATCH, WRAP).apply { topMargin = act.dp(16) })
                return
            }
            results.addView(grid(act, pack, names) { n ->
                AppMeta.setIcon(e.key, "pack:$pack:$n")
                IconLoader.clear()
                dialog.dismiss()
                onDone()
            }, lp(MATCH, WRAP))
        }
        search.doAfterTextChanged { fill() }
        dialog.show()
        fill()
    }
}
