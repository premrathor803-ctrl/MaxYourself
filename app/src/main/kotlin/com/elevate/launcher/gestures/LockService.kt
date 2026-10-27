package com.elevate.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.toast

/**
 * Only used by the "Lock screen" gesture. Android has no ordinary permission for locking the screen from an app,
 * so this accessibility service exists for that single action. It listens to no events and reads nothing on screen.
 * It is off until the person turns it on in Android's Accessibility settings.
 */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    companion object {
        @Volatile var instance: LockService? = null

        fun lock(act: Activity) {
            if (Build.VERSION.SDK_INT < 28) { act.toast("Locking by gesture needs Android 9 or newer"); return }
            val s = instance
            if (s != null) {
                if (!s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) act.toast("Could not lock the screen")
                return
            }
            Dialogs.confirm(act, "Turn on the lock service?",
                "Android only lets an app lock the screen through an accessibility service. Elevate's one is used for locking only: it does not read your screen or watch what you do. Turn on \"Elevate screen lock\" on the next screen.",
                "Open settings") {
                try { act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) { act.toast("Open Settings > Accessibility") }
            }
        }
    }
}
