package com.elevate.launcher.widgets

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.Widgets
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.ui.home.HomeStyle
import androidx.appcompat.app.AlertDialog
import android.appwidget.AppWidgetHostView

/** The last page of the home pager: a scrolling column of widgets, each with a small menu to resize, move or remove it. */
class WidgetsPageView(ctx: Context, private val ctrl: WidgetController) : ScrollView(ctx) {
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    init {
        isFillViewport = true
        clipToPadding = false
        setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(12))
        addView(col, LayoutParams(MATCH, WRAP))
    }

    fun bind() {
        col.removeAllViews()
        val ctx = context
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(UiKit.text(ctx, "Widgets", 24f, HomeStyle.text, true), lp(0, WRAP, 1f))
        head.addView(UiKit.button(ctx, "+ Add widget", true) { ctrl.showPicker() }, lp(WRAP, ctx.dp(40)))
        col.addView(head, lp(MATCH, WRAP).apply { bottomMargin = ctx.dp(10); leftMargin = ctx.dp(6); rightMargin = ctx.dp(2) })

        if (WidgetStore.entries.isEmpty()) {
            col.addView(UiKit.text(ctx, "No widgets yet. Tap Add widget to pick one from your apps, or Elevate's own Today widget (habits and workout at a glance).", 14f, HomeStyle.dim),
                lp(MATCH, WRAP).apply { leftMargin = ctx.dp(6); rightMargin = ctx.dp(6); topMargin = ctx.dp(6) })
        }
        for (e in ArrayList(WidgetStore.entries)) col.addView(card(e), lp(MATCH, ctx.dp(e.heightDp)).apply { bottomMargin = ctx.dp(10) })

        col.addView(UiKit.text(ctx, "Widgets come from other apps and use their battery and memory. Remove ones you do not need. You can turn this page off in Settings > Home screen.", 11.5f, HomeStyle.dim),
            lp(MATCH, WRAP).apply { topMargin = ctx.dp(6); leftMargin = ctx.dp(6); rightMargin = ctx.dp(6) })
    }

    private fun card(e: WidgetStore.Entry): View {
        val ctx = context
        val frame = FrameLayout(ctx)
        val live: AppWidgetHostView? = ctrl.create(e)
        if (live != null) {
            frame.addView(live, FrameLayout.LayoutParams(MATCH, MATCH))
            frame.post {
                val w = (frame.width / resources.displayMetrics.density).toInt()
                if (w > 0) try { live.updateAppWidgetSize(Bundle(), w, e.heightDp, w, e.heightDp) } catch (t: Throwable) { }
            }
            live.setOnLongClickListener { menu(frame, e); true }
        } else {
            frame.background = UiKit.rounded(if (Prefs.wallpaper) 0x66000000 else Palette.surface, ctx.dpf(18f))
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8)) }
            box.addView(UiKit.text(ctx, "This widget is not available. Its app may have been removed.", 14f, HomeStyle.text).apply { gravity = Gravity.CENTER })
            box.addView(UiKit.button(ctx, "Remove", false) { ctrl.remove(e) }, lp(WRAP, WRAP).apply { topMargin = ctx.dp(8) })
            frame.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val more = UiKit.text(ctx, "⋮", 18f, 0xFFFFFFFF.toInt(), true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Widget options"
            background = UiKit.rounded(0x88000000.toInt(), ctx.dpf(18f))
            setOnClickListener { menu(this, e) }
        }
        frame.addView(more, FrameLayout.LayoutParams(ctx.dp(36), ctx.dp(36), Gravity.TOP or Gravity.END).apply { topMargin = ctx.dp(6); rightMargin = ctx.dp(6) })
        return frame
    }

    private fun menu(anchor: View, e: WidgetStore.Entry) {
        val m = PopupMenu(context, anchor)
        m.menu.add(0, 1, 0, "Resize")
        m.menu.add(0, 2, 1, "Move up")
        m.menu.add(0, 3, 2, "Move down")
        m.menu.add(0, 4, 3, "Remove widget")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> resize(e)
                2 -> { WidgetStore.move(e, -1); bind() }
                3 -> { WidgetStore.move(e, 1); bind() }
                4 -> Dialogs.confirm(context, "Remove widget?", "It is taken off this page. The app it belongs to stays installed.", "Remove") { ctrl.remove(e) }
            }
            true
        }
        m.show()
    }

    private fun resize(e: WidgetStore.Entry) {
        var h = e.heightDp
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(22), context.dp(8), context.dp(22), 0) }
        box.addView(Widgets.stepper(context, "Height", h, WidgetStore.MIN_HEIGHT, WidgetStore.MAX_HEIGHT, 20, { "$it dp" }) { h = it })
        box.addView(UiKit.text(context, "Width always fills the page.", 12f, Palette.dim))
        AlertDialog.Builder(context)
            .setTitle("Resize widget")
            .setView(box)
            .setPositiveButton("Apply") { _, _ -> WidgetStore.setHeight(e, h); bind() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
