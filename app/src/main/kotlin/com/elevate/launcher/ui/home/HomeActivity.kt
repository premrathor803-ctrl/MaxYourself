package com.elevate.launcher.ui.home

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.elevate.launcher.R
import com.elevate.launcher.core.BaseActivity
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.Launch
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Perf
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.Widgets
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppEntry
import com.elevate.launcher.data.AppMeta
import com.elevate.launcher.data.HomeItem
import com.elevate.launcher.data.HomeRepo
import com.elevate.launcher.ui.IconLoader
import com.elevate.launcher.center.ui.CenterActivity
import com.elevate.launcher.ui.center.CenterPanelView
import com.elevate.launcher.ui.drawer.DrawerHost
import com.elevate.launcher.ui.drawer.DrawerView
import com.elevate.launcher.ui.onboarding.OnboardingActivity
import com.elevate.launcher.ui.settings.SettingsActivity
import kotlin.math.abs
import kotlin.math.max

/**
 * The home screen. Works as a normal app (launcher icon) and as the default Home.
 * Page 0 of the pager is the Center panel; pages 1..N are icon grids.
 */
class HomeActivity : BaseActivity(), DrawerHost {
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var clockBox: LinearLayout
    private lateinit var searchPill: TextView
    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var dock: CellGrid
    private lateinit var drawer: DrawerView
    private lateinit var drag: DragController
    private lateinit var signature: String
    private var editing = false
    private var editBar: LinearLayout? = null
    private var lastRawX = 0f
    private var lastRawY = 0f
    private val catalogListener: () -> Unit = { onCatalogChanged() }

    override val opaqueWindow: Boolean get() = !Prefs.wallpaper

    val insetTop: Int get() = content.paddingTop

    // ------------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (Prefs.wallpaper) R.style.Theme_Elevate_Wallpaper else R.style.Theme_Elevate)
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("ci_skip_onboarding", false) == true) Prefs.onboarded = true
        signature = prefsSignature()
        HomeRepo.ensureGrid()
        buildUi()
        AppCatalog.addListener(catalogListener)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
        if (!Prefs.onboarded) startActivity(Intent(this, OnboardingActivity::class.java))
        if (AppCatalog.loaded) onCatalogChanged()
        handleExtras(intent)
    }

    override fun onDestroy() {
        AppCatalog.removeListener(catalogListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (prefsSignature() != signature) {
            IconLoader.clear()
            recreate()
            return
        }
        AppCatalog.refreshIfStale(this)
        maybeSeed()
        refreshHome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (drawer.isOpen) drawer.close()
            else if (editing) exitEdit()
            else if (pager.currentItem != 1) pager.setCurrentItem(1, Perf.animate)
        }
        handleExtras(intent)
    }

    private fun prefsSignature(): String = listOf(
        Prefs.theme, Prefs.amoled, Prefs.accent, Prefs.wallpaper, Prefs.perf, Prefs.iconShape, Prefs.cols, Prefs.rows,
        Prefs.dockCount, Prefs.iconScale, Prefs.spacing, Prefs.showLabels, Prefs.showClock, Prefs.showSearch,
        Prefs.drawerStyle, Prefs.drawerCols, Prefs.iconPack
    ).joinToString("|")

    /** Hooks used by the automated emulator test; harmless otherwise. */
    private fun handleExtras(i: Intent?) {
        if (i == null) return
        if (i.getBooleanExtra("ci_skip_onboarding", false)) Prefs.onboarded = true
        if (i.getBooleanExtra("ci_exit_edit", false) && editing) exitEdit()
        if (i.hasExtra("ci_page")) pager.setCurrentItem(i.getIntExtra("ci_page", 1), false)
        if (i.getBooleanExtra("ci_open_drawer", false)) drawer.open(false)
        i.getStringExtra("ci_search")?.let { drawer.open(false); drawer.setQuery(it) }
        if (i.getBooleanExtra("ci_edit", false)) enterEdit()
        if (i.getBooleanExtra("ci_folder", false)) openFirstFolder()
        i.getStringExtra("ci_action")?.let { runAction(it) }
    }

    private fun maybeSeed() {
        if (!Prefs.seeded && Prefs.onboarded && AppCatalog.loaded) {
            HomeRepo.seedDefaults(this)
            Prefs.seeded = true
        }
    }

    // ------------------------------------------------------------------ UI construction
    private fun buildUi() {
        val wall = Prefs.wallpaper
        root = FrameLayout(this)
        if (wall) root.setBackgroundColor(0x38000000)
        setContentView(root)
        setupWindow(lightIcons = if (wall) false else !Palette.dark)

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        padForInsets(content)

        // header: clock + date
        clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(18), 0, dp(6))
        }
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(HomeStyle.text)
            textSize = 52f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
            if (wall) setShadowLayer(8f, 0f, 2f, 0x88000000.toInt())
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextColor(HomeStyle.dim)
            textSize = 15f
            if (wall) setShadowLayer(6f, 0f, 1f, 0x88000000.toInt())
        }
        clockBox.addView(clock, lp(WRAP, WRAP))
        clockBox.addView(date, lp(WRAP, WRAP))
        content.addView(clockBox, lp(MATCH, WRAP))
        clockBox.visibility = if (Prefs.showClock) View.VISIBLE else View.GONE

        // search pill
        searchPill = UiKit.text(this, "Search apps…", 15f, HomeStyle.dim).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            background = UiKit.pressable(if (wall) 0x40000000 else Palette.surface, dpf(24f))
            setOnClickListener { openDrawer(true) }
        }
        content.addView(searchPill, lp(MATCH, dp(46)).apply { leftMargin = dp(20); rightMargin = dp(20); topMargin = dp(6); bottomMargin = dp(4) })
        searchPill.visibility = if (Prefs.showSearch) View.VISIBLE else View.GONE

        // pages
        pager = ViewPager2(this).apply {
            adapter = PageAdapter()
            offscreenPageLimit = 1
            setCurrentItem(1, false)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) { updateDots() }
            })
        }
        content.addView(pager, lp(MATCH, 0, 1f))

        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        content.addView(dots, lp(MATCH, dp(18)))

        // dock
        dock = CellGrid(this).apply {
            pageIndex = -1
            setGrid(Prefs.dockCount, 1)
            setPadding(dp(8), 0, dp(8), 0)
            background = UiKit.rounded(if (wall) 0x33000000 else Palette.surface, dpf(28f))
        }
        content.addView(dock, lp(MATCH, dp(84)).apply { leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(8) })

        // drawer overlay (full screen, handles its own insets)
        drawer = DrawerView(this, this)
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))
        padForInsets(drawer)

        drag = DragController(this, root)
        updateDots()
        bindDock()
    }

    private fun updateDots() {
        dots.removeAllViews()
        val n = 1 + Prefs.pages
        val cur = if (::pager.isInitialized) pager.currentItem else 1
        for (i in 0 until n) {
            val sel = i == cur
            val d = View(this)
            d.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(if (i == 0) 2f else 3f)
                setColor(if (sel) HomeStyle.text else (HomeStyle.text and 0x00FFFFFF) or 0x55000000)
            }
            dots.addView(d, lp(if (sel) dp(16) else dp(6), dp(6)).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
    }

    // ------------------------------------------------------------------ pages
    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 1 + Prefs.pages
        override fun getItemViewType(position: Int) = if (position == 0) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v: View = if (viewType == 0) CenterPanelView(this@HomeActivity) { openCenter(it) } else CellGrid(this@HomeActivity)
            v.layoutParams = RecyclerView.LayoutParams(MATCH, MATCH)
            return object : RecyclerView.ViewHolder(v) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView
            if (v is CellGrid) bindPage(v, position - 1)
        }
    }

    private fun sidePadding(): Int = when (Prefs.spacing) { "compact" -> dp(2); "spacious" -> dp(22); else -> dp(10) }

    private fun bindPage(grid: CellGrid, page: Int) {
        grid.pageIndex = page
        grid.setGrid(Prefs.cols, Prefs.rows)
        val pad = sidePadding()
        grid.setPadding(pad, dp(4), pad, dp(4))
        grid.showGrid = editing
        grid.removeAllViews()
        for (item in HomeRepo.pageItems(page)) grid.addView(makeItemView(item, Prefs.showLabels))
    }

    private fun bindDock() {
        dock.setGrid(Prefs.dockCount, 1)
        dock.showGrid = editing
        dock.removeAllViews()
        for (item in HomeRepo.dockItems()) if (item.pos < Prefs.dockCount) dock.addView(makeItemView(item, false))
    }

    private fun makeItemView(item: HomeItem, showLabel: Boolean): HomeItemView {
        val v = HomeItemView(this, item, showLabel)
        v.bindData()
        v.setOnClickListener { onItemClick(v, item) }
        v.setOnLongClickListener { onItemLongPress(v, item); true }
        return v
    }

    fun refreshHome() {
        if (!::pager.isInitialized) return
        val adapter = pager.adapter ?: return
        if (adapter.itemCount != 1 + Prefs.pages) adapter.notifyDataSetChanged()
        val rv = pager.getChildAt(0) as? RecyclerView
        if (rv != null) for (i in 0 until rv.childCount) { val c = rv.getChildAt(i); if (c is CellGrid) bindPage(c, c.pageIndex) }
        bindDock()
        updateDots()
    }

    private fun onCatalogChanged() {
        maybeSeed()
        refreshHome()
        drawer.refresh()
    }

    fun dockView(): CellGrid = dock

    fun currentGrid(): CellGrid? {
        val rv = pager.getChildAt(0) as? RecyclerView ?: return null
        return rv.findViewHolderForAdapterPosition(pager.currentItem)?.itemView as? CellGrid
    }

    /** Move to the next/previous home page while dragging; adds a page at the far right edge. */
    fun flipPage(dir: Int) {
        val target = pager.currentItem + dir
        if (target < 1) return
        if (target > Prefs.pages) {
            if (!HomeRepo.addPage()) return
            pager.adapter?.notifyDataSetChanged()
            updateDots()
        }
        pager.setCurrentItem(target, true)
    }

    // ------------------------------------------------------------------ item actions
    private fun onItemClick(v: HomeItemView, item: HomeItem) {
        if (editing) return
        when (item.kind) {
            HomeRepo.KIND_CENTER -> openCenter(null)
            HomeRepo.KIND_FOLDER -> FolderDialog(this, item).show()
            else -> {
                if (AppCatalog.loaded && AppCatalog.get(item.key) == null) {
                    Dialogs.confirm(this, item.label, "This app is not installed any more. Remove it from the home screen?", "Remove") {
                        HomeRepo.remove(item); refreshHome()
                    }
                } else {
                    Launch.app(this, item.pkg, item.cls, v.icon)
                }
            }
        }
    }

    private fun onItemLongPress(v: HomeItemView, item: HomeItem) {
        if (drawer.isOpen) return
        drag.start(v, item, lastRawX, lastRawY)
    }

    /** Menu shown when an icon is long-pressed and released without dragging. */
    fun showItemMenu(v: View?, item: HomeItem) {
        val m = PopupMenu(this, v ?: content)
        if (item.isApp) {
            m.menu.add(0, 1, 0, "Rename")
            m.menu.add(0, 2, 1, "App info")
            m.menu.add(0, 3, 2, "Uninstall")
            m.menu.add(0, 7, 3, "Change icon")
            m.menu.add(0, 4, 4, "Remove from home")
        } else if (item.isFolder) {
            m.menu.add(0, 5, 0, "Rename folder")
            m.menu.add(0, 4, 1, "Remove folder (apps stay installed)")
        } else {
            m.menu.add(0, 4, 0, "Remove from home")
        }
        m.menu.add(0, 6, 9, "Edit home screen")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> Dialogs.prompt(this, "Rename app", AppMeta.get(item.key)?.label?.ifBlank { null } ?: item.label) { name ->
                    AppMeta.setLabel(item.key, name); refreshHome(); drawer.refresh()
                }
                2 -> Launch.appInfo(this, item.pkg)
                3 -> Launch.uninstall(this, item.pkg)
                4 -> { HomeRepo.remove(item); refreshHome() }
                5 -> Dialogs.prompt(this, "Folder name", item.label) { name -> HomeRepo.renameFolder(item, name); refreshHome() }
                6 -> enterEdit()
                7 -> {
                    val entry = AppCatalog.get(item.key)
                    if (entry == null) toast("This app is not installed")
                    else com.elevate.launcher.ui.IconChooser.show(this, entry) { refreshHome(); drawer.refresh() }
                }
            }
            true
        }
        m.show()
    }

    private fun openFirstFolder() {
        val f = HomeRepo.items.firstOrNull { it.isFolder } ?: return
        FolderDialog(this, f).show()
    }

    // ------------------------------------------------------------------ DrawerHost
    override fun launchApp(e: AppEntry, from: View?) {
        if (Launch.app(this, e.pkg, e.cls, from)) closeDrawerSoon()
    }

    private fun closeDrawerSoon() { drawer.postDelayed({ drawer.close() }, 250) }

    override fun appMenu(anchor: View, e: AppEntry) {
        val m = PopupMenu(this, anchor)
        val pinned = AppMeta.isPinned(e.key)
        m.menu.add(0, 1, 0, if (pinned) "Unpin from top of drawer" else "Pin to top of drawer")
        m.menu.add(0, 2, 1, "Add to home screen")
        m.menu.add(0, 3, 2, "Add to dock")
        m.menu.add(0, 4, 3, "Rename")
        m.menu.add(0, 5, 4, "Hide in Elevate")
        m.menu.add(0, 8, 5, "Change icon")
        m.menu.add(0, 6, 5, "App info")
        m.menu.add(0, 7, 6, "Uninstall")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> { AppMeta.setPinned(e.key, !pinned); drawer.refresh() }
                2 -> {
                    if (HomeRepo.addAppToFirstFree(e) == null) toast("Home screen is full") else { toast("Added to home screen"); refreshHome() }
                }
                3 -> {
                    if (HomeRepo.addAppToDock(e) == null) toast("Dock is full") else { toast("Added to dock"); refreshHome() }
                }
                4 -> Dialogs.prompt(this, "Rename app", e.displayLabel) { name ->
                    AppMeta.setLabel(e.key, name)
                    drawer.refresh(); refreshHome()
                }
                5 -> {
                    AppMeta.setHidden(e.key, true)
                    HomeRepo.removeByKey(e.key)
                    refreshHome(); drawer.refresh()
                    toast("Hidden in Elevate only. Open the drawer menu > Hidden apps to bring it back.")
                }
                8 -> com.elevate.launcher.ui.IconChooser.show(this, e) { refreshHome(); drawer.refresh() }
                6 -> Launch.appInfo(this, e.pkg)
                7 -> Launch.uninstall(this, e.pkg)
            }
            true
        }
        m.show()
    }

    override fun openSettings() { startActivity(Intent(this, SettingsActivity::class.java)) }

    override fun openCenter(section: String?) { CenterActivity.open(this, section) }

    override fun openHiddenApps() { com.elevate.launcher.privacy.Auth.openHiddenApps(this) }

    override fun onDrawerState(open: Boolean) {
        // The drawer has a solid themed background, so status/nav icons must follow the theme while it is open.
        setLightIcons(if (open) !Palette.dark else (if (Prefs.wallpaper) false else !Palette.dark))
    }

    fun openDrawer(focusSearch: Boolean) { drawer.open(focusSearch) }

    // ------------------------------------------------------------------ edit mode
    private fun enterEdit() {
        if (editing) return
        if (drawer.isOpen) drawer.close()
        editing = true
        showEditBar()
        refreshHome()
    }

    private fun exitEdit() {
        if (!editing) return
        editing = false
        editBar?.let { root.removeView(it) }
        editBar = null
        refreshHome()
    }

    private fun chip(label: String, onClick: () -> Unit): TextView =
        UiKit.text(this, label, 13f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            background = UiKit.pressable(Palette.surface2, dpf(18f))
            setOnClickListener { onClick() }
        }

    private fun showEditBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(if (Palette.dark) 0xF01B1E26.toInt() else 0xF0FFFFFF.toInt(), dpf(20f))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            elevation = dpf(6f)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(UiKit.text(this, "Edit home screen", 16f, Palette.text, true), lp(0, WRAP, 1f))
        top.addView(UiKit.button(this, "Done", true) { exitEdit() }, lp(WRAP, dp(40)))
        bar.addView(top, lp(MATCH, WRAP))
        bar.addView(UiKit.text(this, "Long-press and drag icons. Drop one on another to make a folder.", 11f, Palette.dim),
            lp(MATCH, WRAP).apply { bottomMargin = dp(6) })

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(label: String, f: () -> Unit) { chips.addView(chip(label, f), lp(WRAP, dp(36)).apply { rightMargin = dp(8) }) }
        add("+ Page") { addPage() }
        add("Delete page") { deletePage() }
        add("◀ Move page") { movePage(-1) }
        add("Move page ▶") { movePage(1) }
        add("Grid & icons") { showGridDialog() }
        add("Wallpaper") { Launch.wallpaperPicker(this) }
        add("Settings") { openSettings() }
        add("Reset") { resetLayout() }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
        bar.addView(hs, lp(MATCH, WRAP))

        val p = FrameLayout.LayoutParams(MATCH, WRAP).apply {
            gravity = Gravity.TOP
            setMargins(dp(10), insetTop + dp(6), dp(10), 0)
        }
        root.addView(bar, p)
        editBar = bar
    }

    private fun addPage() {
        if (!HomeRepo.addPage()) { toast("Maximum ${HomeRepo.MAX_PAGES} pages"); return }
        pager.adapter?.notifyDataSetChanged()
        pager.setCurrentItem(Prefs.pages, true)
        updateDots()
    }

    private fun deletePage() {
        val cur = pager.currentItem
        if (cur == 0) { toast("Open a home page first"); return }
        if (Prefs.pages <= 1) { toast("Keep at least one page"); return }
        val p = cur - 1
        val n = HomeRepo.pageItems(p).size
        val msg = if (n > 0) "$n icon(s) on this page will be removed from the home screen. Your apps stay installed." else "Delete this empty page?"
        Dialogs.confirm(this, "Delete page ${p + 1}", msg, "Delete") {
            HomeRepo.removePage(p)
            pager.adapter?.notifyDataSetChanged()
            pager.setCurrentItem(max(1, cur - 1), false)
            refreshHome()
        }
    }

    private fun movePage(dir: Int) {
        val cur = pager.currentItem
        if (cur == 0) { toast("Open a home page first"); return }
        val from = cur - 1
        val to = from + dir
        if (to < 0 || to >= Prefs.pages) return
        HomeRepo.swapPages(from, to)
        refreshHome()
        pager.setCurrentItem(cur + dir, true)
    }

    private fun showGridDialog() {
        var cols = Prefs.cols
        var rows = Prefs.rows
        var dockN = Prefs.dockCount
        var scale = Prefs.iconScale
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        col.addView(Widgets.stepper(this, "Columns", cols, 3, 6) { cols = it })
        col.addView(Widgets.stepper(this, "Rows", rows, 3, 7) { rows = it })
        col.addView(Widgets.stepper(this, "Dock icons", dockN, 3, 6) { dockN = it })
        col.addView(Widgets.stepper(this, "Icon size", scale, 70, 130, 15, { "$it%" }) { scale = it })
        AlertDialog.Builder(this)
            .setTitle("Grid & icons")
            .setView(col)
            .setPositiveButton("Apply") { _, _ ->
                Prefs.putString("cols", cols.toString())
                Prefs.putString("rows", rows.toString())
                Prefs.putString("dock_count", dockN.toString())
                Prefs.putString("icon_scale", scale.toString())
                HomeRepo.ensureGrid()
                IconLoader.clear()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetLayout() {
        Dialogs.confirm(this, "Reset home screen", "Icons, folders and pages go back to the default arrangement. Your apps stay installed.", "Reset") {
            HomeRepo.reset(this)
            pager.adapter?.notifyDataSetChanged()
            pager.setCurrentItem(1, false)
            refreshHome()
        }
    }

    // ------------------------------------------------------------------ gestures
    private val detector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isEmptyAt(e.rawX, e.rawY)) { runAction(Prefs.gestureDouble); return true }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (isEmptyAt(e.rawX, e.rawY)) runAction(Prefs.gestureLong)
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dy) > abs(dx) * 1.4f && abs(dy) > dp(80) && abs(vy) > 600f) {
                    runAction(if (dy < 0) Prefs.gestureUp else Prefs.gestureDown)
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!::drag.isInitialized) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_MOVE) {
            lastRawX = ev.rawX; lastRawY = ev.rawY
        }
        if (drag.active) {
            if (drag.needCancel) {
                drag.needCancel = false
                val c = MotionEvent.obtain(ev)
                c.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(c)
                c.recycle()
            }
            drag.onTouch(ev)
            return true
        }
        if (!editing && !drawer.isOpen && pager.currentItem != 0) detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun isEmptyAt(rawX: Float, rawY: Float): Boolean {
        val loc = IntArray(2)
        val g = currentGrid()
        if (g != null) {
            g.getLocationOnScreen(loc)
            val pos = g.posAt(rawX - loc[0], rawY - loc[1])
            if (pos >= 0) return g.viewForPos(pos) == null
        }
        dock.getLocationOnScreen(loc)
        val dp = dock.posAt(rawX - loc[0], rawY - loc[1])
        if (dp >= 0) return dock.viewForPos(dp) == null
        return true
    }

    fun runAction(a: String) {
        when {
            a == "drawer" -> openDrawer(false)
            a == "search" -> openDrawer(true)
            a == "notifications" -> Launch.notifications(this)
            a == "center" -> openCenter(null)
            a == "settings" -> openSettings()
            a == "menu" -> showHomeMenu()
            a == "edit" -> enterEdit()
            a.startsWith("app:") -> {
                val e = AppCatalog.get(a.removePrefix("app:"))
                if (e != null) Launch.app(this, e.pkg, e.cls) else toast("That app is not installed")
            }
        }
    }

    private fun showHomeMenu() {
        val opts = ArrayList<String>()
        opts.add("Edit home screen")
        opts.add("Wallpaper")
        opts.add("Launcher settings")
        val hasCenter = HomeRepo.items.any { it.kind == HomeRepo.KIND_CENTER }
        if (!hasCenter) opts.add("Add Center shortcut")
        Dialogs.list(this, "Home screen", opts) { i ->
            when (opts[i]) {
                "Edit home screen" -> enterEdit()
                "Wallpaper" -> Launch.wallpaperPicker(this)
                "Launcher settings" -> openSettings()
                "Add Center shortcut" -> {
                    val cap = Prefs.cols * Prefs.rows
                    val page = (pager.currentItem - 1).coerceAtLeast(0)
                    val pos = HomeRepo.firstFree(HomeRepo.PAGE, page, cap)
                    if (pos == null) toast("This page is full")
                    else { HomeRepo.add(HomeRepo.KIND_CENTER, "", "", "Center", HomeRepo.PAGE, page, pos); refreshHome() }
                }
            }
        }
    }

    private fun handleBack() {
        if (drawer.handleBack()) return
        if (editing) { exitEdit(); return }
        if (pager.currentItem != 1) pager.setCurrentItem(1, Perf.animate)
    }
}
