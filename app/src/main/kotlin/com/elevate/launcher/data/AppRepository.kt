package com.elevate.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.elevate.launcher.R
import com.elevate.launcher.model.AppInfoItem
import com.elevate.launcher.model.DrawerRow

/**
 * Reads launchable apps via PackageManager and groups them into light, native categories
 * (ApplicationInfo.category, API 26+) so there's no heavy on-device ML involved — keeps this
 * fast on low-end / Android 7+ devices.
 */
class AppRepository(private val context: Context) {

    fun loadGroupedApps(): List<DrawerRow> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(mainIntent, 0)

        val items = resolved.mapNotNull { ri ->
            val activityInfo = ri.activityInfo ?: return@mapNotNull null
            if (activityInfo.packageName == context.packageName) return@mapNotNull null
            val label = ri.loadLabel(pm)?.toString() ?: activityInfo.packageName
            val icon = try {
                ri.loadIcon(pm)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            AppInfoItem(
                label = label,
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                icon = icon,
                category = categoryLabel(activityInfo.applicationInfo)
            )
        }.sortedBy { it.label.lowercase() }

        val grouped = items.groupBy { it.category }
        val orderedCategoryNames = listOf(
            context.getString(R.string.category_social),
            context.getString(R.string.category_productivity),
            context.getString(R.string.category_game),
            context.getString(R.string.category_video),
            context.getString(R.string.category_audio),
            context.getString(R.string.category_image),
            context.getString(R.string.category_news),
            context.getString(R.string.category_maps),
            context.getString(R.string.category_other)
        )

        val rows = mutableListOf<DrawerRow>()
        for (categoryName in orderedCategoryNames) {
            val apps = grouped[categoryName] ?: continue
            if (apps.isEmpty()) continue
            rows += DrawerRow.Header(categoryName)
            rows += apps.map { DrawerRow.App(it) }
        }
        return rows
    }

    private fun categoryLabel(appInfo: ApplicationInfo): String {
        // ApplicationInfo.category requires API 26; older devices / unset values fall back to "Other".
        val category = if (android.os.Build.VERSION.SDK_INT >= 26) appInfo.category else -1
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> context.getString(R.string.category_game)
            ApplicationInfo.CATEGORY_AUDIO -> context.getString(R.string.category_audio)
            ApplicationInfo.CATEGORY_VIDEO -> context.getString(R.string.category_video)
            ApplicationInfo.CATEGORY_IMAGE -> context.getString(R.string.category_image)
            ApplicationInfo.CATEGORY_SOCIAL -> context.getString(R.string.category_social)
            ApplicationInfo.CATEGORY_NEWS -> context.getString(R.string.category_news)
            ApplicationInfo.CATEGORY_MAPS -> context.getString(R.string.category_maps)
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> context.getString(R.string.category_productivity)
            else -> context.getString(R.string.category_other)
        }
    }
}
