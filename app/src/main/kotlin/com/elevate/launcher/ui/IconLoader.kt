package com.elevate.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.RequiresApi
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Perf
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppEntry
import com.elevate.launcher.data.AppMeta
import com.elevate.launcher.data.HomeItem
import com.elevate.launcher.data.HomeRepo
import java.util.concurrent.Executors

/**
 * Loads app icons off the main thread into an LRU cache, applying the chosen icon shape or icon pack.
 * Views are tagged with the cache key so a recycled view never shows a stale icon.
 */
object IconLoader {
    private fun newCache() = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / Perf.cacheDivisor()).toInt().coerceIn(2048, 65536)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    @Volatile private var cache = newCache()
    @Volatile private var epoch = 0
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** Call when shape, icon pack or performance profile changes. */
    fun clear() {
        epoch++
        cache = newCache()
    }

    private fun appKey(key: String, size: Int) = "a:$key@$size#$epoch"

    private fun bind(iv: ImageView, k: String, producer: () -> Bitmap?) {
        iv.setTag(k)
        val hit = cache.get(k)
        if (hit != null) {
            iv.setImageBitmap(hit)
            return
        }
        iv.setImageDrawable(null)
        val c = cache
        pool.execute {
            val b = try { producer() } catch (t: Throwable) { null }
            if (b != null) {
                c.put(k, b)
                main.post { if (iv.tag == k) iv.setImageBitmap(b) }
            }
        }
    }

    // ------------------------------------------------------------ public binders
    fun bindEntry(iv: ImageView, ctx: Context, e: AppEntry, size: Int) {
        val app = ctx.applicationContext
        bind(iv, appKey(e.key, size)) { appBitmap(app, e.component, e.key, size) }
    }

    fun bindItem(iv: ImageView, ctx: Context, item: HomeItem, size: Int) {
        val app = ctx.applicationContext
        when (item.kind) {
            HomeRepo.KIND_CENTER -> bind(iv, "c@$size#$epoch#${Palette.accent}#${Prefs.iconShape}") { centerBitmap(size) }
            HomeRepo.KIND_FOLDER -> {
                val kids = HomeRepo.folderItems(item.id).take(4)
                val sig = kids.joinToString(",") { it.key }
                bind(iv, "f:$sig@$size#$epoch#${Palette.dark}") { folderBitmap(app, kids, size) }
            }
            else -> {
                val comp = ComponentName(item.pkg, item.cls)
                val present = AppCatalog.get(item.key) != null || !AppCatalog.loaded
                if (present) bind(iv, appKey(item.key, size)) { appBitmap(app, comp, item.key, size) }
                else bind(iv, "m@$size#$epoch") { missingBitmap(size) }
            }
        }
    }

    // ------------------------------------------------------------ rendering
    private fun shapePath(shape: String, s: Float): Path? {
        val p = Path()
        val r = RectF(0f, 0f, s, s)
        when (shape) {
            "circle" -> p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
            "squircle" -> p.addRoundRect(r, s * 0.32f, s * 0.32f, Path.Direction.CW)
            "rounded" -> p.addRoundRect(r, s * 0.18f, s * 0.18f, Path.Direction.CW)
            "square" -> p.addRoundRect(r, s * 0.05f, s * 0.05f, Path.Direction.CW)
            else -> return null
        }
        return p
    }

    private fun appBitmap(ctx: Context, comp: ComponentName, key: String, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val packIcon = IconPacks.load(ctx, comp, AppMeta.get(key)?.icon ?: "")
        if (packIcon != null) {
            packIcon.setBounds(0, 0, size, size)
            packIcon.draw(c)
            return bmp
        }
        val d: Drawable? = try { ctx.packageManager.getActivityIcon(comp) } catch (e: Exception) { null }
        if (d == null) return missingBitmap(size)
        val shape = Prefs.iconShape
        val path = shapePath(shape, size.toFloat())
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            drawAdaptive(c, d, size, path)
        } else if (path != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (Palette.dark) 0xFF2A2E38.toInt() else 0xFFFFFFFF.toInt() }
            c.drawPath(path, paint)
            val pad = (size * 0.17f).toInt()
            d.setBounds(pad, pad, size - pad, size - pad)
            d.draw(c)
        } else {
            d.setBounds(0, 0, size, size)
            d.draw(c)
        }
        return bmp
    }

    @RequiresApi(26)
    private fun drawAdaptive(c: Canvas, d: AdaptiveIconDrawable, size: Int, path: Path?) {
        if (path == null) {
            d.setBounds(0, 0, size, size)
            d.draw(c)
            return
        }
        c.save()
        c.clipPath(path)
        val inset = (size * 0.25f).toInt()
        d.background?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
        d.foreground?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
        c.restore()
    }

    private fun centerBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val path = shapePath(Prefs.iconShape, s) ?: shapePath("squircle", s)!!
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.accent })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.onAccent
            style = Paint.Style.STROKE
            strokeWidth = s * 0.085f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val line = Path().apply {
            moveTo(s * 0.27f, s * 0.70f); lineTo(s * 0.44f, s * 0.46f); lineTo(s * 0.55f, s * 0.60f); lineTo(s * 0.73f, s * 0.32f)
        }
        val head = Path().apply { moveTo(s * 0.58f, s * 0.31f); lineTo(s * 0.73f, s * 0.31f); lineTo(s * 0.73f, s * 0.46f) }
        c.drawPath(line, p)
        c.drawPath(head, p)
        return bmp
    }

    private fun folderBitmap(ctx: Context, kids: List<HomeItem>, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val back = shapePath(if (Prefs.iconShape == "system") "squircle" else Prefs.iconShape, s)!!
        c.drawPath(back, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (Palette.dark) 0x55FFFFFF else 0x44000000 })
        val cell = (s * 0.34f).toInt()
        val gap = (s * 0.08f)
        val start = (s - 2 * cell - gap) / 2f
        kids.forEachIndexed { i, k ->
            val b = cache.get(appKey(k.key, cell)) ?: appBitmap(ctx, ComponentName(k.pkg, k.cls), k.key, cell).also { cache.put(appKey(k.key, cell), it) }
            val x = start + (i % 2) * (cell + gap)
            val y = start + (i / 2) * (cell + gap)
            c.drawBitmap(b, x, y, null)
        }
        return bmp
    }

    private fun missingBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (Palette.dark) 0x55FFFFFF else 0x55000000
            style = Paint.Style.STROKE
            strokeWidth = s * 0.05f
        }
        c.drawCircle(s / 2, s / 2, s * 0.40f, paint)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.color; textSize = s * 0.42f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("?", s / 2, s / 2 + s * 0.15f, t)
        return bmp
    }

    /** Draws a simple preview for the settings screen (not cached). */
    fun shapePreview(shape: String, size: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val path = shapePath(shape, size.toFloat()) ?: shapePath("squircle", size.toFloat())!!
        Canvas(bmp).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return bmp
    }
}
