package com.elevate.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable

/** Icon pack support (ADW/Nova/GO style packs). Filled in by the icon-pack stage. */
object IconPacks {
    fun load(ctx: Context, comp: ComponentName, override: String): Drawable? = null
}
