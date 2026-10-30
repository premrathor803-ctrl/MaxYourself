package com.elevate.launcher.ui.drawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Perf
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppEntry
import com.elevate.launcher.data.AppMeta
import com.elevate.launcher.ui.IconLoader
import com.elevate.launcher.ui.center.CenterIndex

interface DrawerHost {
    fun launchApp(e: AppEntry, from: View?)
    fun appMenu(anchor: View, e: AppEntry)
    fun openSettings()
    fun openCenter(section: String?)
    fun openHiddenApps()
    fun onDrawerState(open: Boolean)
}

sealed class Row {
    class Header(val text: String, val letter: Char?) : Row()
    class App(val e: AppEntry) : Row()
    class Web(val q: String) : Row()
    class Info(val text: String) : Row()
    class Link(val title: String, val sub: String, val section: String) : Row()
}

/** Full-screen app drawer shown on top of the home screen. */
class DrawerView(ctx: Context, private val host: DrawerHost) : FrameLayout(ctx) {
    var isOpen = false
        private set

    private val search = EditText(ctx)
    private val chips = LinearLayout(ctx)
    private val list = RecyclerView(ctx)
    private val index: IndexBar
    private val adapter = Adapter()
    private var group = "All"
    private var rows: List<Row> = emptyList()
    private val chipViews = LinkedHashMap<String, TextView>()
    private var gridMode = false

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(Palette.bg)

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        addView(col, LayoutParams(MATCH, MATCH))

        // search row
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        search.apply {
            hint = "Search apps…"
            setHintTextColor(Palette.dim)
            setTextColor(Palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = UiKit.rounded(Palette.surface, ctx.dpf(24f))
            setPadding(ctx.dp(18), 0, ctx.dp(18), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { rebuild() }
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                    val first = rows.firstOrNull { it is Row.App } as? Row.App
                    if (first != null) host.launchApp(first.e, null)
                    else { val w = rows.firstOrNull { it is Row.Web } as? Row.Web; if (w != null) com.elevate.launcher.core.Launch.webSearch(context, w.q) }
                    true
                } else false
            }
        }
        top.addView(search, lp(0, ctx.dp(48), 1f).apply { leftMargin = ctx.dp(16); rightMargin = ctx.dp(4) })
        val more = UiKit.text(ctx, "⋮", 22f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showMenu(it) }
        }
        top.addView(more, lp(ctx.dp(44), ctx.dp(48)).apply { rightMargin = ctx.dp(8) })
        col.addView(top, lp(MATCH, WRAP).apply { topMargin = ctx.dp(8) })

        // category chips
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        for (g in listOf("All") + AppCatalog.groups) {
            val tv = UiKit.text(ctx, g, 13f, Palette.text, true).apply {
                gravity = Gravity.CENTER
                setPadding(ctx.dp(14), 0, ctx.dp(14), 0)
                setOnClickListener { group = g; styleChips(); rebuild() }
            }
            chipViews[g] = tv
            chips.addView(tv, lp(WRAP, ctx.dp(34)).apply { rightMargin = ctx.dp(8) })
        }
        val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
        col.addView(hs, lp(MATCH, WRAP))
        styleChips()

        // list + index
        val frame = FrameLayout(ctx)
        list.adapter = adapter
        list.itemAnimator = null
        list.setHasFixedSize(true)
        frame.addView(list, LayoutParams(MATCH, MATCH))
        index = IndexBar(ctx) { letter -> scrollToLetter(letter) }
        frame.addView(index, LayoutParams(ctx.dp(22), MATCH, Gravity.END).apply { topMargin = ctx.dp(4); bottomMargin = ctx.dp(4) })
        col.addView(frame, lp(MATCH, 0, 1f))

        applyLayoutManager()
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
        AppCatalog.addListener { if (isOpen) rebuild() }
    }

    private fun styleChips() {
        val c = context
        for ((g, tv) in chipViews) {
            val sel = g == group
            tv.setTextColor(if (sel) Palette.onAccent else Palette.text)
            tv.background = UiKit.rounded(if (sel) Palette.accent else Palette.surface, c.dpf(17f))
        }
    }

    private fun applyLayoutManager() {
        gridMode = Prefs.drawerStyle == "grid"
        if (gridMode) {
            val cols = Prefs.drawerCols
            val glm = GridLayoutManager(context, cols)
            glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (rows.getOrNull(position) is Row.App) 1 else cols
            }
            list.layoutManager = glm
        } else {
            list.layoutManager = LinearLayoutManager(context)
        }
    }

    // ---------------------------------------------------------------- open / close
    fun open(focusSearch: Boolean) {
        if (isOpen) return
        isOpen = true
        host.onDrawerState(true)
        if (gridMode != (Prefs.drawerStyle == "grid") || (gridMode && (list.layoutManager as? GridLayoutManager)?.spanCount != Prefs.drawerCols)) applyLayoutManager()
        rebuild()
        visibility = View.VISIBLE
        if (Perf.animate) {
            translationY = height * 0.18f
            alpha = 0f
            animate().translationY(0f).alpha(1f).setDuration(Perf.duration(200)).start()
        } else {
            translationY = 0f; alpha = 1f
        }
        if (focusSearch) postDelayed({ if (isOpen) { search.requestFocus(); showKeyboard() } }, 150)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        host.onDrawerState(false)
        hideKeyboard()
        search.setText("")
        group = "All"
        styleChips()
        if (Perf.animate) {
            animate().alpha(0f).translationY(height * 0.12f).setDuration(Perf.duration(160))
                .withEndAction { if (!isOpen) visibility = View.GONE }.start()
        } else {
            visibility = View.GONE
        }
        list.scrollToPosition(0)
    }

    /** Clears an active search first; returns true if it consumed the back press. */
    fun handleBack(): Boolean {
        if (!isOpen) return false
        if (search.text.isNotEmpty()) { search.setText(""); return true }
        if (group != "All") { group = "All"; styleChips(); rebuild(); return true }
        close()
        return true
    }

    fun refresh() { if (isOpen) { applyLayoutManager(); rebuild() } }

    fun setQuery(q: String) { search.setText(q) }

    private fun showKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(context, anchor)
        m.menu.add(0, 1, 0, "Launcher settings")
        m.menu.add(0, 2, 1, "Open Center")
        m.menu.add(0, 3, 2, "Hidden apps")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> host.openSettings()
                2 -> host.openCenter(null)
                3 -> host.openHiddenApps()
            }
            true
        }
        m.show()
    }

    // ---------------------------------------------------------------- rows
    private fun letterOf(label: String): Char {
        val c = label.firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    private fun rebuild() {
        val q = search.text.toString().trim()
        val out = ArrayList<Row>()
        val vis = AppCatalog.visible()
        var showIndex = false
        if (q.isNotEmpty()) {
            val res = AppSearch.rank(q, vis)
            res.forEach { out.add(Row.App(it)) }
            if (res.isEmpty()) out.add(0, Row.Info("No apps match \"$q\""))
            for (l in CenterIndex.search(q)) out.add(Row.Link(l.title, l.sub, l.section))
            out.add(Row.Web(q))
        } else {
            val src = (if (group == "All") vis else vis.filter { it.group == group })
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayLabel })
            if (group == "All") {
                val pinned = AppMeta.pinnedKeys().mapNotNull { AppCatalog.get(it) }.filter { !AppMeta.isHidden(it.key) }
                if (pinned.isNotEmpty()) { out.add(Row.Header("Pinned", null)); pinned.forEach { out.add(Row.App(it)) } }
                val recent = AppMeta.recentKeys(5).mapNotNull { AppCatalog.get(it) }.filter { !AppMeta.isPinned(it.key) }
                if (recent.isNotEmpty()) { out.add(Row.Header("Recent", null)); recent.forEach { out.add(Row.App(it)) } }
            }
            var last: Char? = null
            for (e in src) {
                val l = letterOf(e.displayLabel)
                if (l != last) { out.add(Row.Header(l.toString(), l)); last = l }
                out.add(Row.App(e))
            }
            if (src.isEmpty()) out.add(Row.Info(if (!AppCatalog.loaded) "Loading apps…" else "No apps in this group"))
            showIndex = src.isNotEmpty() && group == "All"
        }
        rows = out
        adapter.notifyDataSetChanged()
        index.visibility = if (showIndex) View.VISIBLE else View.GONE
    }

    private fun scrollToLetter(letter: Char) {
        var target = -1
        for ((i, r) in rows.withIndex()) {
            if (r is Row.Header) {
                val l = r.letter ?: continue
                // '#' sorts last in the index but first among labels that do not start with a letter
                if (l == letter) { target = i; break }
                if (letter != '#' && l != '#' && l > letter && target == -1) { target = i; break }
            }
        }
        if (target >= 0) {
            val lm = list.layoutManager
            if (lm is LinearLayoutManager) lm.scrollToPositionWithOffset(target, 0)
        }
    }

    // ---------------------------------------------------------------- adapter
    private inner class VH(v: View, val icon: ImageView?, val label: TextView?, val sub: TextView? = null) : RecyclerView.ViewHolder(v)

    private inner class Adapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.Header -> 0
            is Row.App -> if (gridMode) 2 else 1
            is Row.Web -> 3
            is Row.Info -> 4
            is Row.Link -> 5
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
            val c = parent.context
            when (type) {
                0 -> {
                    val t = UiKit.text(c, "", 13f, Palette.accent, true).apply { setPadding(c.dp(20), c.dp(14), c.dp(20), c.dp(4)) }
                    t.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                    return VH(t, null, t)
                }
                1 -> {
                    val row = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(c.dp(20), 0, c.dp(36), 0)
                        layoutParams = RecyclerView.LayoutParams(MATCH, c.dp(58))
                        background = UiKit.pressable(0, 0f)
                    }
                    val iv = ImageView(c).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                    row.addView(iv, lp(c.dp(44), c.dp(44)))
                    val tv = UiKit.text(c, "", 16f, Palette.text).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                    row.addView(tv, lp(MATCH, WRAP).apply { leftMargin = c.dp(16) })
                    return VH(row, iv, tv)
                }
                2 -> {
                    val cell = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(c.dp(2), c.dp(10), c.dp(2), c.dp(6))
                        layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                        background = UiKit.pressable(0, 0f)
                    }
                    val iv = ImageView(c).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                    cell.addView(iv, lp(c.dp(52), c.dp(52)))
                    val tv = UiKit.text(c, "", 12f, Palette.text).apply {
                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                    }
                    cell.addView(tv, lp(MATCH, WRAP).apply { topMargin = c.dp(4) })
                    return VH(cell, iv, tv)
                }
                3, 5 -> {
                    val row = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(c.dp(20), c.dp(12), c.dp(36), c.dp(12))
                        layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                        background = UiKit.pressable(0, 0f)
                    }
                    val t = UiKit.text(c, "", 16f, Palette.text, true)
                    val s = UiKit.text(c, "", 12f, Palette.dim)
                    row.addView(t); row.addView(s)
                    return VH(row, null, t, s)
                }
                else -> {
                    val t = UiKit.text(c, "", 14f, Palette.dim).apply { gravity = Gravity.CENTER; setPadding(c.dp(24), c.dp(32), c.dp(24), c.dp(16)) }
                    t.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                    return VH(t, null, t)
                }
            }
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = rows[position]
            val ctx = h.itemView.context
            when (r) {
                is Row.Header -> h.label!!.text = r.text
                is Row.Info -> h.label!!.text = r.text
                is Row.App -> {
                    h.label!!.text = r.e.displayLabel
                    IconLoader.bindEntry(h.icon!!, ctx, r.e, ctx.dp(if (gridMode) 52 else 44))
                    h.itemView.setOnClickListener { host.launchApp(r.e, h.icon) }
                    h.itemView.setOnLongClickListener { host.appMenu(h.itemView, r.e); true }
                }
                is Row.Web -> {
                    h.label!!.text = "Search the web for \"${r.q}\""
                    h.sub!!.text = "Opens your browser"
                    h.itemView.setOnClickListener { com.elevate.launcher.core.Launch.webSearch(ctx, r.q) }
                }
                is Row.Link -> {
                    h.label!!.text = r.title
                    h.sub!!.text = r.sub
                    h.itemView.setOnClickListener { host.openCenter(r.section) }
                }
            }
        }
    }
}

/** A–Z strip on the right edge; touching or dragging jumps to a letter. */
class IndexBar(ctx: Context, private val onLetter: (Char) -> Unit) : View(ctx) {
    private val letters = ('A'..'Z').toList() + '#'
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = ctx.dpf(10f)
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = Palette.dim
        val step = height / letters.size.toFloat()
        for ((i, l) in letters.withIndex()) {
            canvas.drawText(l.toString(), width / 2f, step * i + step / 2f + paint.textSize / 3f, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val i = (e.y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
                onLetter(letters[i])
            }
        }
        return true
    }
}
