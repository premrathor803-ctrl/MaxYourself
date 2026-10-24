package com.elevate.launcher.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.HomeItem
import com.elevate.launcher.ui.IconLoader
import kotlin.math.min

/** Text colour used on the home screen: white over a wallpaper, themed otherwise. */
object HomeStyle {
    val text: Int get() = if (Prefs.wallpaper) Color.WHITE else Palette.text
    val dim: Int get() = if (Prefs.wallpaper) 0xCCFFFFFF.toInt() else Palette.dim
}

/** One icon + label on the home screen / dock / folder. */
class HomeItemView(
    ctx: Context,
    var item: HomeItem,
    private val showLabel: Boolean,
    textColor: Int = HomeStyle.text,
    shadowed: Boolean = Prefs.wallpaper
) : LinearLayout(ctx) {
    val icon = ImageView(ctx)
    val label = TextView(ctx)
    var iconPx = 0
        private set

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(icon, LayoutParams(ctx.dp(48), ctx.dp(48)))
        label.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(textColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            if (shadowed) setShadowLayer(5f, 0f, 1f, 0xAA000000.toInt())
            visibility = if (showLabel) View.VISIBLE else View.GONE
        }
        addView(label, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(3) })
        setPadding(ctx.dp(2), ctx.dp(4), ctx.dp(2), ctx.dp(2))
    }

    fun applySize(cellW: Int, cellH: Int) {
        val cap = context.dpf(66f)
        val frac = if (showLabel) 0.56f else 0.70f
        val base = min(min(cellW * 0.64f, cellH * frac), cap)
        val px = (base * Prefs.iconScale / 100f).toInt().coerceAtLeast(context.dp(24))
        if (px != iconPx) {
            iconPx = px
            val p = icon.layoutParams
            p.width = px; p.height = px
            icon.layoutParams = p
            rebindIcon()
        }
    }

    fun rebindIcon() {
        if (iconPx > 0) IconLoader.bindItem(icon, context, item, iconPx)
    }

    fun bindData() {
        label.text = item.label
        val missing = item.isApp && AppCatalog.loaded && AppCatalog.get(item.key) == null
        alpha = if (missing) 0.55f else 1f
        val meta = com.elevate.launcher.data.AppMeta.get(item.key)
        if (item.isApp && meta != null && meta.label.isNotBlank()) label.text = meta.label
        rebindIcon()
    }
}

/**
 * A fixed cols x rows grid of cells. Children are HomeItemViews placed by their item's `pos`
 * (row * cols + col). Used for home pages (several rows) and for the dock (one row).
 */
class CellGrid(ctx: Context) : ViewGroup(ctx) {
    var cols = 4
    var rows = 5
    /** Page number, or -1 for the dock. */
    var pageIndex = -1
    var highlight = -1
        set(v) { if (field != v) { field = v; invalidate() } }
    /** Shows faint cell outlines while the home screen is in edit mode. */
    var showGrid = false
        set(v) { if (field != v) { field = v; invalidate() } }

    private val hl = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        clipChildren = false
    }

    fun setGrid(c: Int, r: Int) {
        if (c != cols || r != rows) { cols = c; rows = r; requestLayout() }
    }

    private fun cellW() = ((width - paddingLeft - paddingRight) / cols.toFloat())
    private fun cellH() = ((height - paddingTop - paddingBottom) / rows.toFloat())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = (w - paddingLeft - paddingRight) / cols
        val ch = (h - paddingTop - paddingBottom) / rows
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c is HomeItemView) c.applySize(cw, ch)
            c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = cellW(); val ch = cellH()
        for (i in 0 until childCount) {
            val c = getChildAt(i) as? HomeItemView ?: continue
            val pos = c.item.pos
            val col = pos % cols; val row = pos / cols
            val x = paddingLeft + (col * cw).toInt()
            val y = paddingTop + (row * ch).toInt()
            c.layout(x, y, x + cw.toInt(), y + ch.toInt())
        }
    }

    /** Cell index under a point given in this view's own coordinates, or -1 if outside the grid. */
    fun posAt(x: Float, y: Float): Int {
        if (width == 0 || height == 0) return -1
        val cx = x - paddingLeft; val cy = y - paddingTop
        if (cx < 0 || cy < 0 || cx >= width - paddingLeft - paddingRight || cy >= height - paddingTop - paddingBottom) return -1
        val col = (cx / cellW()).toInt().coerceIn(0, cols - 1)
        val row = (cy / cellH()).toInt().coerceIn(0, rows - 1)
        return row * cols + col
    }

    fun viewForPos(pos: Int): HomeItemView? {
        for (i in 0 until childCount) {
            val c = getChildAt(i) as? HomeItemView ?: continue
            if (c.item.pos == pos) return c
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        val cw = cellW(); val ch = cellH()
        if (showGrid) {
            hl.style = Paint.Style.STROKE
            hl.strokeWidth = context.dpf(1f)
            hl.color = 0x55FFFFFF
            val inset = context.dpf(5f)
            for (r in 0 until rows) for (c in 0 until cols) {
                val x = paddingLeft + c * cw; val y = paddingTop + r * ch
                canvas.drawRoundRect(RectF(x + inset, y + inset, x + cw - inset, y + ch - inset), context.dpf(14f), context.dpf(14f), hl)
            }
            hl.style = Paint.Style.FILL
        }
        if (highlight < 0) return
        val col = highlight % cols; val row = highlight / cols
        val x = paddingLeft + col * cw; val y = paddingTop + row * ch
        hl.color = (Palette.accent and 0x00FFFFFF) or 0x55000000
        val inset = context.dpf(4f)
        canvas.drawRoundRect(RectF(x + inset, y + inset, x + cw - inset, y + ch - inset), context.dpf(16f), context.dpf(16f), hl)
    }
}
