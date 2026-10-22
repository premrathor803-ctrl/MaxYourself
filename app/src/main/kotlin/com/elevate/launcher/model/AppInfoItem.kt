package com.elevate.launcher.model

import android.graphics.drawable.Drawable

data class AppInfoItem(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
    val category: String
)

/** Marker used in the app-drawer's mixed list so one adapter can render both headers and apps. */
sealed class DrawerRow {
    data class Header(val title: String) : DrawerRow()
    data class App(val item: AppInfoItem) : DrawerRow()
}
