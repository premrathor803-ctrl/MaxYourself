package com.elevate.launcher.ui.center

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp

/** The page to the left of the first home page: a quick look at today plus shortcuts into the Center. */
class CenterPanelView(ctx: Context, private val open: (String?) -> Unit) : ScrollView(ctx) {
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    init {
        isFillViewport = true
        setPadding(ctx.dp(18), ctx.dp(24), ctx.dp(18), ctx.dp(12))
        clipToPadding = false
        addView(col, LayoutParams(MATCH, WRAP))
        col.addView(UiKit.text(ctx, "Center", 30f, com.elevate.launcher.ui.home.HomeStyle.text, true))
        col.addView(UiKit.text(ctx, "Your daily self-improvement space", 14f, com.elevate.launcher.ui.home.HomeStyle.dim), lp(MATCH, WRAP).apply { bottomMargin = ctx.dp(16) })
        col.addView(UiKit.button(ctx, "Open Center") { open(null) })
    }
}
