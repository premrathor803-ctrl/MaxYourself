package com.elevate.launcher.gestures

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.elevate.launcher.core.toast

/** Torch on/off for a gesture. Needs no permission; says so politely when the phone has no flash. */
object Flashlight {
    @Volatile private var on = false
    private var camId: String? = null
    private var registered = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == camId) on = enabled
        }
    }

    fun toggle(ctx: Context) {
        try {
            val m = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (camId == null) {
                camId = m.cameraIdList.firstOrNull { id ->
                    val c = m.getCameraCharacteristics(id)
                    c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                }
            }
            val id = camId
            if (id == null) { ctx.toast("This phone has no flashlight"); return }
            if (!registered) { m.registerTorchCallback(callback, null); registered = true }
            val next = !on
            m.setTorchMode(id, next)
            on = next
        } catch (t: Throwable) {
            android.util.Log.w("Elevate", "flashlight failed", t)
            ctx.toast("The flashlight is not available right now")
        }
    }
}
