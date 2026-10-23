package com.elevate.launcher.core

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Checks / requests the default-Home role. The launcher also works fine when it is not the default. */
object DefaultHome {
    const val REQ = 4401

    fun isDefault(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == ctx.packageName
    }

    fun request(act: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = act.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ)
                    return
                }
            }
            act.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                act.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e2: Exception) {
                act.toast("Open Settings > Apps > Default apps > Home app")
            }
        }
    }
}
