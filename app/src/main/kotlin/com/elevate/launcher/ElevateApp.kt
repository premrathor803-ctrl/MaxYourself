package com.elevate.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.privacy.Auth
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.Store
import com.elevate.launcher.ui.IconLoader

class ElevateApp : Application() {
    private val main = Handler(Looper.getMainLooper())
    private val reloadRunnable = Runnable { AppCatalog.reload(this) }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        applyNightMode()
        Store.init(this)
        Center.init(this)
        Auth.init(this)
        AppCatalog.reload(this)

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // Debounce: installs/updates send several broadcasts in a row.
                main.removeCallbacks(reloadRunnable)
                main.postDelayed(reloadRunnable, 600)
            }
        }, f, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) IconLoader.clear()
    }

    companion object {
        fun applyNightMode() {
            AppCompatDelegate.setDefaultNightMode(
                when (Prefs.theme) {
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
