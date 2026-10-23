package com.elevate.launcher.ui.home

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
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
import com.elevate.launcher.ui.center.CenterPanelView
import com.elevate.launcher.ui.drawer.DrawerHost
import com.elevate.launcher.ui.drawer.DrawerView
import kotlin.math.abs

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
    private lateinit var signature: String
    private val catalogListener: () -> Unit = { onCatalogChanged() }

    override val opaqueWindow: Boolean get() = !Prefs.wallpaper

    // ------------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (Prefs.wallpaper) R.style.Theme_Elevate_Wallpaper else R.style.Theme_Elevate)
        super.onCreate(savedInstanceState)
        signature = prefsSignature()
        HomeRepo.ensureGrid()
        buildUi()
        AppCatalog.addListener(catalogListener)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
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
        refreshHome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (drawer.isOpen) drawer.close()
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
        if (i.hasExtra("ci_page")) pager.setCurrentItem(i.getIntExtra("ci_page", 1), false)
        if (i.getBooleanExtra("ci_open_drawer", false)) drawer.open(false)
        i.getStringExtra("ci_search")?.let { drawer.open(false); drawer.setQuery(it) }
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
            val g = GradientDrawable().apply {
                if (i == 0) {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(2f)
                    setColor(if (sel) HomeStyle.text else 0x66FFFFFF)
                } else {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(3f)
                    setColor(if (sel) HomeStyle.text else (HomeStyle.text and 0x00FFFFFF) or 0x55000000)
                }
            }
            d.background = g
            val w = if (sel) dp(16) else dp(6)
            dots.addView(d, lp(w, dp(6)).apply { leftMargin = dp(3); rightMargin = dp(3) })
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
        grid.removeAllViews()
        for (item in HomeRepo.pageItems(page)) grid.addView(makeItemView(item, Prefs.showLabels))
    }

    private fun bindDock() {
        dock.setGrid(Prefs.dockCount, 1)
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
        if (!Prefs.seeded && AppCatalog.loaded) {
            HomeRepo.seedDefaults(this)
            Prefs.seeded = true
        }
        refreshHome()
        drawer.refresh()
    }

    // ------------------------------------------------------------------ item actions
    private fun onItemClick(v: HomeItemView, item: HomeItem) {
        when (item.kind) {
            HomeRepo.KIND_CENTER -> openCenter(null)
            HomeRepo.KIND_FOLDER -> toast("Folders are coming in the next build")
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
        val m = PopupMenu(this, v)
        if (item.isApp) {
            m.menu.add(0, 1, 0, "App info")
            m.menu.add(0, 2, 1, "Uninstall")
        }
        m.menu.add(0, 3, 2, "Remove from home")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> Launch.appInfo(this, item.pkg)
                2 -> Launch.uninstall(this, item.pkg)
                3 -> { HomeRepo.remove(item); refreshHome() }
            }
            true
        }
        m.show()
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
                6 -> Launch.appInfo(this, e.pkg)
                7 -> Launch.uninstall(this, e.pkg)
            }
            true
        }
        m.show()
    }

    override fun openSettings() { toast("Settings arrive in the next build") }

    override fun openCenter(section: String?) { toast("Center arrives in a later build") }

    override fun openHiddenApps() { toast("Hidden apps arrive in a later build") }

    override fun onDrawerState(open: Boolean) {
        // The drawer has a solid themed background, so status/nav icons must follow the theme while it is open.
        setLightIcons(if (open) !Palette.dark else (if (Prefs.wallpaper) false else !Palette.dark))
    }

    fun openDrawer(focusSearch: Boolean) { drawer.open(focusSearch) }

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
        if (::pager.isInitialized && !drawer.isOpen && pager.currentItem != 0) detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun currentGrid(): CellGrid? {
        val rv = pager.getChildAt(0) as? RecyclerView ?: return null
        return rv.findViewHolderForAdapterPosition(pager.currentItem)?.itemView as? CellGrid
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
            a.startsWith("app:") -> {
                val e = AppCatalog.get(a.removePrefix("app:"))
                if (e != null) Launch.app(this, e.pkg, e.cls) else toast("That app is not installed")
            }
        }
    }

    private fun showHomeMenu() {
        val opts = ArrayList<String>()
        opts.add("Wallpaper")
        opts.add("Launcher settings")
        val hasCenter = HomeRepo.items.any { it.kind == HomeRepo.KIND_CENTER }
        if (!hasCenter) opts.add("Add Center shortcut")
        Dialogs.list(this, "Home screen", opts) { i ->
            when (opts[i]) {
                "Wallpaper" -> Launch.wallpaperPicker(this)
                "Launcher settings" -> openSettings()
                "Add Center shortcut" -> {
                    val cap = Prefs.cols * Prefs.rows
                    val page = (pager.currentItem - 1).coerceAtLeast(0)
                    val pos = HomeRepo.firstFree(HomeRepo.PAGE, page, cap)
                    if (pos == null) toast("This page is full") else { HomeRepo.add(HomeRepo.KIND_CENTER, "", "", "Center", HomeRepo.PAGE, page, pos); refreshHome() }
                }
            }
        }
    }

    private fun handleBack() {
        if (drawer.handleBack()) return
        if (pager.currentItem != 1) pager.setCurrentItem(1, Perf.animate)
    }
}
