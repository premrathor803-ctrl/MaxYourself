package com.elevate.launcher.center.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import com.elevate.launcher.data.Io
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

/** One progress photo in the private journal. The picture lives in Elevate's private storage, never in the gallery. */
class Photo(val id: Long, val day: String, val kind: String, val note: String, val file: String, val created: Long)

/**
 * The photo journal. Photos are copied into app-private storage, shrunk, and re-saved, which also removes any location
 * or camera data from them. Nothing here scores, rates or analyses a person's looks. The only "analysis" is a plain
 * technical hint about the picture itself (too dark, too bright, maybe blurry) so photos are easier to compare.
 */
object PhotoRepo {
    val kinds = listOf(
        "front" to "Face, front", "side" to "Face, side", "posture" to "Posture, full body",
        "body" to "Body", "other" to "Other"
    )

    fun kindLabel(k: String): String = kinds.firstOrNull { it.first == k }?.second ?: "Other"

    private const val COLS = "id, day, kind, note, file, created"
    private const val MAX_SIDE = 1600
    private const val MAX_SOURCE_BYTES = 40L * 1024 * 1024

    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "journal")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun fileOf(ctx: Context, p: Photo): File = File(dir(ctx), p.file)

    private fun read(c: Cursor) = Photo(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5))

    fun all(kind: String? = null): List<Photo> {
        val c = if (kind == null) Center.db.rawQuery("SELECT $COLS FROM photos ORDER BY day DESC, id DESC", null)
        else Center.db.rawQuery("SELECT $COLS FROM photos WHERE kind=? ORDER BY day DESC, id DESC", arrayOf(kind))
        val out = ArrayList<Photo>()
        c.use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }

    fun get(id: Long): Photo? {
        val c = Center.db.rawQuery("SELECT $COLS FROM photos WHERE id=?", arrayOf(id.toString()))
        return c.use { if (it.moveToFirst()) read(it) else null }
    }

    fun count(): Int {
        val c = Center.db.rawQuery("SELECT COUNT(*) FROM photos", null)
        return c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun setNote(id: Long, note: String) {
        val cv = ContentValues().apply { put("note", note) }
        Center.db.update("photos", cv, "id=?", arrayOf(id.toString()))
    }

    fun delete(ctx: Context, id: Long) {
        val p = get(id) ?: return
        File(dir(ctx), p.file).delete()
        Center.db.delete("photos", "id=?", arrayOf(id.toString()))
        PhotoThumbs.clear()
    }

    fun deleteAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
        Center.db.delete("photos", null, null)
        PhotoThumbs.clear()
    }

    /** Reads a picture from [src], shrinks and re-saves it as a private JPEG. Returns the new photo id, or -1 on failure. */
    fun saveFrom(ctx: Context, src: InputStream, day: String, kind: String, note: String): Long {
        val tmp = File(ctx.cacheDir, "import-" + System.nanoTime() + ".tmp")
        try {
            var total = 0L
            tmp.outputStream().use { out ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_SOURCE_BYTES) return -1L
                    out.write(buf, 0, n)
                }
            }
            val bmp = decodeUpright(tmp) ?: return -1L
            val name = UUID.randomUUID().toString() + ".jpg"
            File(dir(ctx), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            val cv = ContentValues().apply {
                put("day", day); put("kind", kind); put("note", note); put("file", name); put("created", System.currentTimeMillis())
            }
            return Center.db.insert("photos", null, cv)
        } catch (t: Throwable) {
            android.util.Log.w("Elevate", "photo import failed", t)
            return -1L
        } finally {
            tmp.delete()
        }
    }

    private fun decodeUpright(f: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(f.path, o2) ?: return null
        val big = max(bmp.width, bmp.height)
        if (big > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / big
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
        }
        val orient = try {
            ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (t: Throwable) { ExifInterface.ORIENTATION_NORMAL }
        val m = Matrix()
        when (orient) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /**
     * Technical hints about the picture itself so that photos taken on different days are easier to compare.
     * This says nothing about the person in the photo.
     */
    fun advice(ctx: Context, p: Photo): List<String> {
        val out = ArrayList<String>()
        val small = PhotoThumbs.decode(fileOf(ctx, p), 320) ?: return out
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val g = IntArray(w * h) { i ->
            val c = px[i]
            (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        var sum = 0L
        for (v in g) sum += v
        val mean = sum / g.size.coerceAtLeast(1)
        var s = 0.0
        var ss = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = (4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]).toDouble()
            s += lap; ss += lap * lap; n++
        }
        val variance = if (n > 0) ss / n - (s / n) * (s / n) else 0.0
        if (mean < 70) out.add("This picture looks dark. Even daylight from a window makes photos easier to compare later.")
        if (mean > 205) out.add("This picture looks very bright. Strong light can wash out detail.")
        if (variance < 25.0) out.add("This picture may be a little soft or blurry. Hold the phone steady or use the camera timer.")
        if (out.isEmpty()) out.add("Brightness and sharpness look fine for comparing.")
        return out
    }
}

/** Small in-memory cache of decoded photo thumbnails. */
object PhotoThumbs {
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 12).toInt().coerceIn(2048, 32768)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun clear() { cache.evictAll() }

    /** Decodes [f] so that its longer side is at least [maxSide] pixels where possible (never upscales). */
    fun decode(f: File, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(f.path, o2)
    }

    fun load(ctx: Context, p: Photo, maxSide: Int, into: ImageView) {
        val key = p.id.toString() + "@" + maxSide
        into.tag = key
        val hit = cache.get(key)
        if (hit != null) { into.setImageBitmap(hit); return }
        into.setImageDrawable(null)
        val file = PhotoRepo.fileOf(ctx, p)
        Io.work {
            val b = decode(file, maxSide)
            if (b != null) {
                cache.put(key, b)
                Io.ui { if (into.tag == key) into.setImageBitmap(b) }
            }
        }
    }
}
