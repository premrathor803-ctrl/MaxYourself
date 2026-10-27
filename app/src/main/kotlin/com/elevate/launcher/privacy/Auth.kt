package com.elevate.launcher.privacy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Access code for the private "hidden apps" list.
 *
 * What this protects: the list screen inside Elevate. It does NOT protect the hidden apps themselves, which stay
 * installed and visible to Android, other launchers and system search.
 *
 * The code is never stored. Only a salted PBKDF2 hash is kept, in its own preferences file, so "Reset launcher
 * settings" does not remove it. Repeated wrong guesses trigger growing delays. Nothing here writes the code to logs.
 */
object Auth {
    enum class Result { OK, WRONG, LOCKED }

    const val MIN_LEN = 4
    const val MAX_LEN = 8
    private const val ITERATIONS = 20000
    private const val SESSION_MS = 5 * 60 * 1000L
    private const val FREE_TRIES = 5
    const val ALIAS = "com.elevate.launcher.CalculatorAlias"

    private var sp: SharedPreferences? = null
    @Volatile private var sessionUntil = 0L

    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext.getSharedPreferences("privacy", Context.MODE_PRIVATE)
    }

    private fun prefs(ctx: Context): SharedPreferences {
        init(ctx)
        return sp!!
    }

    fun hasCode(ctx: Context): Boolean = prefs(ctx).contains("hash")

    /** How many digits the code has (so only entries of that length count as an attempt). */
    fun codeLength(ctx: Context): Int = prefs(ctx).getInt("len", 0)

    private fun hash(code: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, iterations, 160)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    fun validCode(code: String): Boolean = code.length in MIN_LEN..MAX_LEN && code.all { it in '0'..'9' }

    fun setCode(ctx: Context, code: String): Boolean {
        if (!validCode(code)) return false
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val h = hash(code, salt, ITERATIONS)
        prefs(ctx).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(h, Base64.NO_WRAP))
            .putInt("iter", ITERATIONS)
            .putInt("len", code.length)
            .putInt("fails", 0)
            .putLong("locked_until", 0L)
            .apply()
        return true
    }

    fun clearCode(ctx: Context) {
        prefs(ctx).edit().remove("salt").remove("hash").remove("iter").remove("len").remove("fails").remove("locked_until").remove("bio").apply()
        lock()
    }

    // ------------------------------------------------------------ optional fingerprint / face
    /** Fingerprint or face as a second way in. Only ever on while a code exists; the code stays the master key. */
    fun bioOn(ctx: Context): Boolean = hasCode(ctx) && prefs(ctx).getBoolean("bio", false)
    fun setBio(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("bio", v).apply() }

    /** Seconds left in a lock-out, or 0. */
    fun lockedSeconds(ctx: Context): Int {
        val left = prefs(ctx).getLong("locked_until", 0L) - System.currentTimeMillis()
        return if (left <= 0) 0 else ((left + 999) / 1000).toInt()
    }

    fun verify(ctx: Context, code: String): Result {
        val p = prefs(ctx)
        if (!p.contains("hash")) return Result.WRONG
        if (lockedSeconds(ctx) > 0) return Result.LOCKED
        val ok = try {
            val salt = Base64.decode(p.getString("salt", ""), Base64.NO_WRAP)
            val want = Base64.decode(p.getString("hash", ""), Base64.NO_WRAP)
            MessageDigest.isEqual(hash(code, salt, p.getInt("iter", ITERATIONS)), want)
        } catch (e: Exception) {
            false
        }
        if (ok) {
            p.edit().putInt("fails", 0).putLong("locked_until", 0L).apply()
            return Result.OK
        }
        val fails = p.getInt("fails", 0) + 1
        val e = p.edit().putInt("fails", fails)
        if (fails >= FREE_TRIES) {
            val steps = Math.min(fails - FREE_TRIES, 5)
            val delaySec = Math.min(900L, 30L shl steps)
            e.putLong("locked_until", System.currentTimeMillis() + delaySec * 1000L)
        }
        e.apply()
        return Result.WRONG
    }

    // ------------------------------------------------------------ in-memory session
    fun unlockSession() { sessionUntil = SystemClock.elapsedRealtime() + SESSION_MS }
    fun touch() { if (sessionValid()) sessionUntil = SystemClock.elapsedRealtime() + SESSION_MS }
    fun sessionValid(): Boolean = SystemClock.elapsedRealtime() < sessionUntil
    fun lock() { sessionUntil = 0L }

    /** Opens the hidden-apps list: through the calculator when a code is set, directly when none is. */
    fun openHiddenApps(ctx: Context) {
        val i = if (hasCode(ctx)) {
            Intent(ctx, CalculatorActivity::class.java)
        } else {
            unlockSession()
            Intent(ctx, HiddenAppsActivity::class.java)
        }
        if (ctx !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    // ------------------------------------------------------------ optional "Calculator" icon
    fun calculatorIconOn(ctx: Context): Boolean {
        val state = ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx.packageName, ALIAS))
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    fun setCalculatorIcon(ctx: Context, on: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx.packageName, ALIAS),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
