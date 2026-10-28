package com.elevate.launcher.core

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.io.InputStream

/**
 * The font used for Elevate's own screens. Besides the built-in Android families, the person can import one .ttf or .otf
 * file. Elevate does not ship or download any fonts, only keeps a private copy of the one that was chosen. Android's own
 * Settings and system dialogs keep their normal font, because an app cannot change those.
 */
object Fonts {
    private const val FILE = "custom_font.bin"
    private const val MAX_BYTES = 8L * 1024 * 1024

    private var app: Context? = null
    private var cacheKey = ""
    private var regular: Typeface = Typeface.DEFAULT
    private var bold: Typeface = Typeface.DEFAULT_BOLD

    fun init(ctx: Context) { app = ctx.applicationContext }

    val mode: String get() = Prefs.sp.getString("font", "system") ?: "system"

    private fun customFile(): File? = app?.let { File(it.filesDir, FILE) }

    fun hasCustom(): Boolean = customFile()?.let { it.isFile && it.length() > 0 } == true

    /** Changes whenever the chosen font changes, so screens know to rebuild. */
    fun signature(): String = mode + ":" + (if (mode == "custom") (customFile()?.lastModified() ?: 0L) else 0L)

    @Synchronized
    fun face(isBold: Boolean): Typeface {
        val key = signature()
        if (key != cacheKey) rebuild(key)
        return if (isBold) bold else regular
    }

    private fun rebuild(key: String) {
        cacheKey = key
        var r: Typeface = Typeface.DEFAULT
        var b: Typeface = Typeface.DEFAULT_BOLD
        when (mode) {
            "serif" -> { r = Typeface.SERIF; b = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
            "mono" -> { r = Typeface.MONOSPACE; b = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
            "condensed" -> {
                r = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                b = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            }
            "custom" -> try {
                val f = customFile()
                if (f != null && hasCustom()) {
                    val t = Typeface.createFromFile(f)
                    r = t
                    b = Typeface.create(t, Typeface.BOLD)
                }
            } catch (e: Throwable) {
                // an unreadable file just means the normal font is used
            }
        }
        regular = r
        bold = b
    }

    /**
     * Checks that [src] really is a TrueType or OpenType font, then keeps a private copy and switches to it.
     * Returns null when it worked, otherwise a plain-language reason.
     */
    fun import(src: InputStream): String? {
        val ctx = app ?: return "Not ready yet. Try again."
        val tmp = File(ctx.cacheDir, "font-import.tmp")
        try {
            var total = 0L
            tmp.outputStream().use { out ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return "That file is too big for a font (the limit is 8 MB)."
                    out.write(buf, 0, n)
                }
            }
            val head = ByteArray(4)
            val got = tmp.inputStream().use { it.read(head) }
            if (got < 4) return "That file is not a font."
            val tag = String(head, Charsets.ISO_8859_1)
            val plain = head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte()
            if (!(plain || tag == "OTTO" || tag == "true" || tag == "ttcf")) {
                return "That file is not a TrueType or OpenType font (.ttf or .otf)."
            }
            try {
                Typeface.createFromFile(tmp)
            } catch (e: Throwable) {
                return "Android could not read that font."
            }
            val dest = File(ctx.filesDir, FILE)
            tmp.copyTo(dest, overwrite = true)
            Prefs.putString("font", "custom")
            return null
        } catch (e: Throwable) {
            return "Could not read that file."
        } finally {
            tmp.delete()
        }
    }

    fun removeCustom() {
        customFile()?.delete()
        if (mode == "custom") Prefs.putString("font", "system")
    }
}
