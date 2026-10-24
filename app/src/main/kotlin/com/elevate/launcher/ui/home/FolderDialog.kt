package com.elevate.launcher.ui.home

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.Launch
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.HomeItem
import com.elevate.launcher.data.HomeRepo

/** Pop-up that shows the apps inside a folder. */
class FolderDialog(private val act: HomeActivity, private val folder: HomeItem) {
    private var dialog: AlertDialog? = null
    private val grid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val cols = 4
    private val cellW = (act.resources.displayMetrics.widthPixels * 0.80f / cols).toInt()
    private val cellH = act.dp(92)

    fun show() {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(14), act.dp(14), act.dp(14), act.dp(14))
        }
        val title = UiKit.text(act, folder.label, 20f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, act.dp(4), 0, act.dp(4))
        }
        title.setOnClickListener {
            Dialogs.prompt(act, "Folder name", folder.label) { name ->
                HomeRepo.renameFolder(folder, name)
                title.text = folder.label
            }
        }
        col.addView(title, lp(MATCH, WRAP))
        col.addView(UiKit.text(act, "Tap the name to rename. Long-press an app for more.", 11f, Palette.dim).apply {
            gravity = Gravity.CENTER
        }, lp(MATCH, WRAP).apply { bottomMargin = act.dp(8) })
        val scroll = ScrollView(act).apply { addView(grid) }
        col.addView(scroll, lp(MATCH, WRAP))
        fill()

        val d = AlertDialog.Builder(act).setView(col).create()
        d.window?.setBackgroundDrawable(UiKit.rounded(Palette.surface, act.dpf(26f)))
        d.setOnDismissListener { act.refreshHome() }
        dialog = d
        d.show()
    }

    private fun fill() {
        grid.removeAllViews()
        val kids = HomeRepo.folderItems(folder.id)
        var row: LinearLayout? = null
        for ((i, item) in kids.withIndex()) {
            if (i % cols == 0) {
                row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row, lp(MATCH, WRAP))
            }
            val v = HomeItemView(act, item, true, Palette.text, false)
            v.applySize(cellW, cellH)
            v.bindData()
            v.setOnClickListener {
                if (AppCatalog.loaded && AppCatalog.get(item.key) == null) {
                    act.toast("This app is no longer installed")
                } else if (Launch.app(act, item.pkg, item.cls, v.icon)) {
                    dialog?.dismiss()
                }
            }
            v.setOnLongClickListener { itemMenu(v, item); true }
            row!!.addView(v, lp(cellW, cellH))
        }
    }

    private fun itemMenu(v: View, item: HomeItem) {
        val m = PopupMenu(act, v)
        m.menu.add(0, 1, 0, "Move out of folder")
        m.menu.add(0, 2, 1, "App info")
        m.menu.add(0, 3, 2, "Uninstall")
        m.menu.add(0, 4, 3, "Remove from home")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> { if (!HomeRepo.moveToFirstFree(item)) act.toast("Home screen is full"); reload() }
                2 -> Launch.appInfo(act, item.pkg)
                3 -> Launch.uninstall(act, item.pkg)
                4 -> { HomeRepo.remove(item); reload() }
            }
            true
        }
        m.show()
    }

    private fun reload() {
        if (HomeRepo.find(folder.id) == null || HomeRepo.folderItems(folder.id).isEmpty()) {
            dialog?.dismiss()
            return
        }
        fill()
    }
}
