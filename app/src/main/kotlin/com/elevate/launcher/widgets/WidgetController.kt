package com.elevate.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.Io

/**
 * Hosts Android widgets for the home screen. Adding one asks Android for permission (a system prompt), and some
 * widgets open their own set-up screen first. Widgets belong to other apps, so how they look and how much battery
 * they use is up to those apps; Elevate only shows them.
 */
class WidgetController(private val act: Activity, private val onChanged: (Boolean) -> Unit) {
    companion object {
        const val REQ_BIND = 7001
        const val REQ_CONFIG = 7002
        const val HOST_ID = 1024
        private const val PENDING = "widget_pending"
        private const val TAG = "ElevateWidgets"
    }

    private val manager: AppWidgetManager = AppWidgetManager.getInstance(act)
    private val host = AppWidgetHost(act.applicationContext, HOST_ID)
    private var listening = false

    fun start() {
        if (listening || WidgetStore.entries.isEmpty()) return
        try { host.startListening(); listening = true } catch (t: Throwable) { Log.w(TAG, "startListening failed", t) }
    }

    fun stop() {
        if (!listening) return
        try { host.stopListening() } catch (t: Throwable) { Log.w(TAG, "stopListening failed", t) }
        listening = false
    }

    // ------------------------------------------------------------------ the widget views
    fun infoOf(e: WidgetStore.Entry): AppWidgetProviderInfo? =
        try { manager.getAppWidgetInfo(e.appWidgetId) } catch (t: Throwable) { null }

    /** The live widget, or null if its app was removed or Android no longer knows it. */
    fun create(e: WidgetStore.Entry): AppWidgetHostView? {
        val info = infoOf(e) ?: return null
        return try { host.createView(act, e.appWidgetId, info) } catch (t: Throwable) { Log.w(TAG, "createView failed", t); null }
    }

    fun remove(e: WidgetStore.Entry) {
        try { host.deleteAppWidgetId(e.appWidgetId) } catch (t: Throwable) { Log.w(TAG, "delete failed", t) }
        WidgetStore.remove(e)
        if (WidgetStore.entries.isEmpty()) stop()
        onChanged(false)
    }

    // ------------------------------------------------------------------ adding
    private class Row(val info: AppWidgetProviderInfo, val label: String, val app: String, val size: String)

    private fun labelOf(info: AppWidgetProviderInfo): String =
        try { info.loadLabel(act.packageManager) ?: info.provider.shortClassName } catch (t: Throwable) { info.provider.shortClassName }

    private fun appOf(info: AppWidgetProviderInfo): String =
        try { info.provider.packageName.let { pkg -> act.packageManager.getApplicationInfo(pkg, 0).loadLabel(act.packageManager).toString() } }
        catch (t: Throwable) { info.provider.packageName }

    private fun cells(px: Int): Int {
        val dp = px / act.resources.displayMetrics.density
        return ((dp + 30f) / 70f).toInt().coerceAtLeast(1)
    }

    fun showPicker() {
        val list: List<AppWidgetProviderInfo> = try { manager.installedProviders } catch (t: Throwable) { emptyList() }
        val rows = list.map { Row(it, labelOf(it), appOf(it), "${cells(it.minWidth)}×${cells(it.minHeight)}") }
            .sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
        if (rows.isEmpty()) {
            Dialogs.info(act, "No widgets", "No app on this phone offers a widget. Elevate's own Today widget should appear here after a restart of the launcher.")
            return
        }
        val rv = RecyclerView(act).apply {
            layoutManager = LinearLayoutManager(act)
            adapter = PickerAdapter(rows)
        }
        val frame = FrameLayout(act).apply { addView(rv, FrameLayout.LayoutParams(MATCH, act.dp(420))) }
        val dlg = AlertDialog.Builder(act).setTitle("Add a widget").setView(frame).setNegativeButton("Cancel", null).create()
        picker = dlg
        dlg.show()
    }

    private var picker: AlertDialog? = null

    private inner class PickerAdapter(private val rows: List<Row>) : RecyclerView.Adapter<PickerAdapter.VH>() {
        inner class VH(val box: LinearLayout, val img: ImageView, val title: TextView, val sub: TextView) : RecyclerView.ViewHolder(box) {
            var token: Any? = null
        }

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val box = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(act.dp(14), act.dp(8), act.dp(14), act.dp(8))
                background = UiKit.pressable(0x00000000, 0f)
                layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            }
            val img = ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            box.addView(img, lp(act.dp(88), act.dp(58)))
            val texts = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            val title = UiKit.text(act, "", 15f, Palette.text, true)
            val sub = UiKit.text(act, "", 12f, Palette.dim)
            texts.addView(title)
            texts.addView(sub)
            box.addView(texts, lp(0, WRAP, 1f).apply { leftMargin = act.dp(12) })
            return VH(box, img, title, sub)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = rows[position]
            h.title.text = r.label
            h.sub.text = "${r.app}  ·  ${r.size}"
            h.img.setImageDrawable(null)
            h.token = r
            h.box.setOnClickListener { picker?.dismiss(); begin(r.info) }
            Io.work {
                val d = try { r.info.loadPreviewImage(act, 0) ?: r.info.loadIcon(act, 0) } catch (t: Throwable) { null }
                if (d != null) Io.ui { if (h.token === r) h.img.setImageDrawable(d) }
            }
        }
    }

    /** Starts adding the provider [cn] without the picker (used by tests and by the Today shortcut). */
    fun addProvider(cn: ComponentName) {
        val info = try { manager.installedProviders.firstOrNull { it.provider == cn } } catch (t: Throwable) { null }
        if (info == null) act.toast("That widget is not available") else begin(info)
    }

    @Suppress("DEPRECATION")
    private fun begin(info: AppWidgetProviderInfo) {
        val id = try { host.allocateAppWidgetId() } catch (t: Throwable) {
            act.toast("Widgets are not available on this phone")
            return
        }
        Prefs.putInt(PENDING, id)
        var ok = false
        try { ok = manager.bindAppWidgetIdIfAllowed(id, info.provider) } catch (t: Throwable) { Log.w(TAG, "bind failed", t) }
        if (ok) {
            afterBind(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                act.startActivityForResult(i, REQ_BIND)
            } catch (t: Throwable) {
                discard(id)
                act.toast("This phone does not allow adding widgets here")
            }
        }
    }

    private fun afterBind(id: Int) {
        val info = try { manager.getAppWidgetInfo(id) } catch (t: Throwable) { null }
        if (info?.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(act, id, 0, REQ_CONFIG, null)
                return
            } catch (t: Throwable) {
                Log.w(TAG, "configure screen could not start", t)
            }
        }
        finishAdd(id)
    }

    /** Call from the activity's onActivityResult. Returns true when the result belonged to the widget flow. */
    fun onResult(req: Int, res: Int, data: Intent?): Boolean {
        if (req != REQ_BIND && req != REQ_CONFIG) return false
        val fromIntent = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val id = if (fromIntent >= 0) fromIntent else Prefs.getInt(PENDING, -1)
        if (id < 0) return true
        if (res != Activity.RESULT_OK) { discard(id); return true }
        if (req == REQ_BIND) afterBind(id) else finishAdd(id)
        return true
    }

    private fun finishAdd(id: Int) {
        Prefs.putInt(PENDING, -1)
        val info = try { manager.getAppWidgetInfo(id) } catch (t: Throwable) { null }
        if (info == null) {
            discard(id)
            act.toast("That widget could not be added")
            return
        }
        val h = (info.minHeight / act.resources.displayMetrics.density).toInt().coerceIn(100, 400)
        WidgetStore.add(id, info.provider.flattenToString(), h)
        Prefs.widgetsPage = true
        start()
        onChanged(true)
    }

    private fun discard(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (t: Throwable) { Log.w(TAG, "delete failed", t) }
        Prefs.putInt(PENDING, -1)
    }
}
