package com.elevate.launcher.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.elevate.launcher.core.Launch
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.data.HomeItem
import com.elevate.launcher.data.HomeRepo
import com.elevate.launcher.data.Io

/**
 * Drag & drop for home icons. HomeActivity forwards every touch event here once a long-press has started a drag,
 * so the pager underneath never sees them. Dropping on another icon makes/extends a folder; the bar at the top
 * removes the icon or opens app info / uninstall.
 */
class DragController(private val act: HomeActivity, private val root: FrameLayout) {
    var active = false
        private set
    /** True until HomeActivity has sent the child views an ACTION_CANCEL for the press that started the drag. */
    var needCancel = false

    private var item: HomeItem? = null
    private var src: HomeItemView? = null
    private var shadow: ImageView? = null
    private var bar: LinearLayout? = null
    private val barViews = ArrayList<TextView>()
    private val barActions = ArrayList<Int>()
    private var hover = -1
    private var startX = 0f
    private var startY = 0f
    private var moved = false
    private var edgeDir = 0
    private var lastGrid: CellGrid? = null
    private val slop = ViewConfiguration.get(act).scaledTouchSlop * 2
    private val edgeRunnable = Runnable { onEdgeDwell() }

    private class Zone(val grid: CellGrid, val area: String, val page: Int, val pos: Int)

    fun start(v: HomeItemView, it: HomeItem, x: Float, y: Float) {
        if (active) return
        active = true
        needCancel = true
        item = it
        src = v
        startX = x; startY = y
        moved = false
        hover = -1

        val w = v.width.coerceAtLeast(1)
        val h = v.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        shadow = ImageView(act).apply {
            setImageBitmap(bmp)
            visibility = View.INVISIBLE
            scaleX = 1.12f; scaleY = 1.12f
            elevation = act.dpf(8f)
        }
        root.addView(shadow, FrameLayout.LayoutParams(w, h))
        positionShadow(x, y)

        buildBar(it)
        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    // ------------------------------------------------------------ drop-target bar
    private fun buildBar(it: HomeItem) {
        barViews.clear(); barActions.clear()
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiKit.rounded(if (Palette.dark) 0xEE1B1E26.toInt() else 0xEEFFFFFF.toInt(), act.dpf(20f))
            visibility = View.INVISIBLE
            elevation = act.dpf(6f)
        }
        fun add(action: Int, label: String) {
            val t = UiKit.text(act, label, 13f, Palette.text, true).apply {
                gravity = Gravity.CENTER
                background = UiKit.rounded(0, act.dpf(16f))
            }
            barViews.add(t); barActions.add(action)
            row.addView(t, lp(0, MATCH, 1f).apply { setMargins(act.dp(4), act.dp(4), act.dp(4), act.dp(4)) })
        }
        add(1, "Remove")
        if (it.isApp) { add(2, "App info"); add(3, "Uninstall") }
        val lpBar = FrameLayout.LayoutParams(MATCH, act.dp(60)).apply {
            gravity = Gravity.TOP
            setMargins(act.dp(12), act.insetTop + act.dp(8), act.dp(12), 0)
        }
        root.addView(row, lpBar)
        bar = row
    }

    private fun barRectBottom(): Int {
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        return loc[1] + act.insetTop + act.dp(8) + act.dp(60)
    }

    private fun barIndexAt(rx: Float, ry: Float): Int {
        if (ry > barRectBottom() || barViews.isEmpty()) return -1
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val w = root.width - act.dp(24)
        val i = ((rx - loc[0] - act.dp(12)) / (w / barViews.size.toFloat())).toInt()
        return i.coerceIn(0, barViews.size - 1)
    }

    // ------------------------------------------------------------ events
    fun onTouch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> move(ev.rawX, ev.rawY)
            MotionEvent.ACTION_UP -> up(ev.rawX, ev.rawY)
            MotionEvent.ACTION_CANCEL -> { finish(); act.refreshHome() }
        }
    }

    private fun positionShadow(rx: Float, ry: Float) {
        val s = shadow ?: return
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        s.x = rx - loc[0] - s.layoutParams.width / 2f
        s.y = ry - loc[1] - s.layoutParams.height / 2f
    }

    private fun move(rx: Float, ry: Float) {
        if (!moved) {
            val dx = rx - startX; val dy = ry - startY
            if (dx * dx + dy * dy < slop * slop) return
            moved = true
            shadow?.visibility = View.VISIBLE
            bar?.visibility = View.VISIBLE
            src?.visibility = View.INVISIBLE
        }
        positionShadow(rx, ry)

        // bar hover
        val bi = barIndexAt(rx, ry)
        if (bi != hover) {
            hover = bi
            barViews.forEachIndexed { i, t ->
                t.background = UiKit.rounded(if (i == hover) Palette.accent else 0, act.dpf(16f))
                t.setTextColor(if (i == hover) Palette.onAccent else Palette.text)
            }
        }

        // cell highlight
        val z = if (hover >= 0) null else zoneAt(rx, ry)
        if (lastGrid != null && lastGrid !== z?.grid) lastGrid?.highlight = -1
        z?.grid?.highlight = z?.pos ?: -1
        lastGrid = z?.grid

        // page flip near the screen edges
        val sw = act.resources.displayMetrics.widthPixels
        val dir = if (rx < act.dp(30)) -1 else if (rx > sw - act.dp(30)) 1 else 0
        if (dir != edgeDir) {
            edgeDir = dir
            Io.cancel(edgeRunnable)
            if (dir != 0) Io.uiDelayed(650, edgeRunnable)
        }
    }

    private fun onEdgeDwell() {
        if (!active || edgeDir == 0) return
        act.flipPage(edgeDir)
        Io.uiDelayed(900, edgeRunnable)
    }

    private fun zoneAt(rx: Float, ry: Float): Zone? {
        val loc = IntArray(2)
        val dock = act.dockView()
        dock.getLocationOnScreen(loc)
        val dp = dock.posAt(rx - loc[0], ry - loc[1])
        if (dp >= 0) return Zone(dock, HomeRepo.DOCK, 0, dp)
        val g = act.currentGrid() ?: return null
        g.getLocationOnScreen(loc)
        val p = g.posAt(rx - loc[0], ry - loc[1])
        if (p >= 0) return Zone(g, HomeRepo.PAGE, g.pageIndex, p)
        return null
    }

    private fun up(rx: Float, ry: Float) {
        val it = item
        val v = src
        if (it == null) { finish(); return }
        if (!moved) {
            finish()
            act.showItemMenu(v, it)
            return
        }
        val action = if (hover >= 0) barActions[hover] else 0
        val z = if (action == 0) zoneAt(rx, ry) else null
        finish()
        when (action) {
            1 -> HomeRepo.remove(it)
            2 -> Launch.appInfo(act, it.pkg)
            3 -> Launch.uninstall(act, it.pkg)
            else -> if (z != null) drop(it, z)
        }
        act.refreshHome()
    }

    private fun drop(it: HomeItem, z: Zone) {
        val occupant = HomeRepo.at(z.area, z.page, z.pos)?.takeIf { o -> o.id != it.id }
        val oldArea = it.area; val oldPage = it.page; val oldPos = it.pos
        val wasLoose = it.area != HomeRepo.FOLDER
        when {
            occupant == null -> HomeRepo.place(it, z.area, z.page, z.pos)
            occupant.isFolder && !it.isFolder -> HomeRepo.mergeIntoFolder(occupant, it)
            occupant.isApp && it.isApp -> HomeRepo.mergeIntoFolder(occupant, it)
            else -> {
                // swap places (folders, Center shortcut)
                HomeRepo.place(it, z.area, z.page, z.pos)
                if (wasLoose) HomeRepo.place(occupant, oldArea, oldPage, oldPos)
                else {
                    val free = HomeRepo.firstFree(HomeRepo.PAGE, 0, com.elevate.launcher.core.Prefs.cols * com.elevate.launcher.core.Prefs.rows)
                    if (free != null) HomeRepo.place(occupant, HomeRepo.PAGE, 0, free)
                }
            }
        }
    }

    private fun finish() {
        active = false
        needCancel = false
        Io.cancel(edgeRunnable)
        edgeDir = 0
        shadow?.let { root.removeView(it) }
        bar?.let { root.removeView(it) }
        shadow = null; bar = null
        lastGrid?.highlight = -1
        lastGrid = null
        src?.visibility = View.VISIBLE
        src = null
        item = null
        hover = -1
    }
}
