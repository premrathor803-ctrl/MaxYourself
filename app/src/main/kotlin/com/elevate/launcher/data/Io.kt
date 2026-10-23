package com.elevate.launcher.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/** Two tiny executors: one for database writes (ordered), one for general background work. */
object Io {
    private val writer = Executors.newSingleThreadExecutor()
    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun write(block: () -> Unit) {
        writer.execute {
            try { block() } catch (t: Throwable) { Log.e("Elevate", "db write failed", t) }
        }
    }

    fun work(block: () -> Unit) {
        worker.execute {
            try { block() } catch (t: Throwable) { Log.e("Elevate", "background work failed", t) }
        }
    }

    fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun uiDelayed(ms: Long, block: Runnable) { main.postDelayed(block, ms) }
    fun cancel(block: Runnable) { main.removeCallbacks(block) }
}
