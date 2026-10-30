package com.elevate.launcher.core

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppMeta

/** Starting apps and system screens, with friendly handling when an app has gone away. */
object Launch {
    private var lastKey = ""
    private var lastAt = 0L

    fun app(ctx: Context, pkg: String, cls: String, from: View? = null): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastKey == "$pkg/$cls" && now - lastAt < 800L) return true   // a double tap is one launch
        lastKey = "$pkg/$cls"
        lastAt = now
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(pkg, cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            if (from != null && Perf.animate) {
                val o = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height)
                ctx.startActivity(i, o.toBundle())
            } else {
                ctx.startActivity(i)
            }
            AppMeta.touch("$pkg/$cls")
            com.elevate.launcher.gaming.Play.onLaunched(ctx, "$pkg/$cls")
            true
        } catch (e: Exception) {
            ctx.toast("This app is no longer available")
            AppCatalog.reload(ctx)
            false
        }
    }

    fun appInfo(ctx: Context, pkg: String) = safe(ctx) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun uninstall(ctx: Context, pkg: String) = safe(ctx) {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun systemSettings(ctx: Context) = safe(ctx) {
        ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun wallpaperPicker(ctx: Context) = safe(ctx) {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun webSearch(ctx: Context, q: String) = safe(ctx) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Pull down the notification shade. Works without extra permissions on most devices; otherwise we say so. */
    fun notifications(ctx: Context) {
        try {
            val sb = ctx.getSystemService("statusbar")
            sb!!.javaClass.getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Exception) {
            ctx.toast("This phone does not allow opening the notification panel from the launcher")
        }
    }

    private inline fun safe(ctx: Context, block: () -> Unit) {
        try { block() } catch (e: Exception) { ctx.toast("Could not open that screen") }
    }
}
