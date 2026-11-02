package com.photocompressorlite.myapp
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.*
data class Photo(val uri: Uri, val name: String, val bytes: Long)
data class CompressionOptions(val target: Long = 200 * 1024L, val quality: Int = 90, val format: String = "JPEG", val flatten: Boolean = false)
data class PhotoResult(val photo: Photo, val path: String? = null, val bytes: Long = 0, val error: String? = null, val targetMet: Boolean = false)
data class HistoryEntry(val time: Long, val count: Int, val original: Long, val compressed: Long)
object CompressionMath {
    fun savedPercent(original: Long, compressed: Long): Double = if (original <= 0) 0.0 else (original - compressed).toDouble() / original * 100
    fun nextDimension(current: Int): Int = max(1, (current * .8).toInt())
    fun sampleSize(width: Int, height: Int, maxSide: Int = 2560): Int {
        var sample = 1
        while (max(width, height) / sample > maxSide) sample *= 2
        return sample
    }
}
fun sizeText(bytes: Long): String = if (bytes >= 1024 * 1024) "%.2f MB".format(bytes / 1048576.0) else "%.1f KB".format(bytes / 1024.0)
class PhotoRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val theme get() = prefs.getString("theme", "System")!!
    val defaultTarget get() = prefs.getLong("target", 200 * 1024L)
    val defaultFormat get() = prefs.getString("format", "JPEG")!!
    fun preference(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    fun target(value: Long) {
        prefs.edit().putLong("target", value).apply()
    }
    fun history(): List<HistoryEntry> = runCatching {
        val a = JSONArray(prefs.getString("history", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            HistoryEntry(o.getLong("time"), o.getInt("count"), o.getLong("original"), o.getLong("compressed"))
        }
    }.getOrDefault(emptyList())
    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }
    fun record(results: List<PhotoResult>) {
        val ok = results.filter {
            it.path != null
        }
        if (ok.isEmpty()) return
        val entries = (listOf(HistoryEntry(System.currentTimeMillis(), ok.size, ok.sumOf {
            it.photo.bytes
        }, ok.sumOf {
            it.bytes
        })) + history()).take(20)
        val a = JSONArray()
        entries.forEach {
            a.put(JSONObject().put("time", it.time).put("count", it.count).put("original", it.original).put("compressed", it.compressed))
        }
        prefs.edit().putString("history", a.toString()).apply()
    }
    suspend fun readPhoto(uri: Uri): Photo = withContext(Dispatchers.IO) {
        var name = "Photo"
        var bytes = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            c -> if (c.moveToFirst()) {
                name = c.getString(0) ?: name
                bytes = if (c.isNull(1)) 0 else c.getLong(1)
            }
        }
        if (bytes == 0L) context.contentResolver.openInputStream(uri)?.use {
            input -> val buffer = ByteArray(8192)
            while (true) {
                ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                bytes += n
            }
        }
        Photo(uri, name, bytes)
    }
    suspend fun compress(photo: Photo, options: CompressionOptions): PhotoResult = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        var output: File? = null
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(photo.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "This image format cannot be decoded."
            }
            ensureActive()
            val decode = BitmapFactory.Options().apply {
                inSampleSize = CompressionMath.sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = resolver.openInputStream(photo.uri)?.use {
                BitmapFactory.decodeStream(it, null, decode)
            } ?: error("Photo is no longer available. Please select it again.")
            val orientation = resolver.openInputStream(photo.uri)?.use {
                runCatching {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                }.getOrDefault(1)
            } ?: 1
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }
                    6 -> setRotate(90f)
                    7 -> {
                        setRotate(-90f)
                        postScale(-1f, 1f)
                    }
                    8 -> setRotate(-90f)
                }
            }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                if (rotated !== bitmap) bitmap!!.recycle()
                bitmap = rotated
            }
            if (options.format == "JPEG" && bitmap!!.hasAlpha()) {
                // A decoder may mark an opaque PNG as alpha-capable. Inspect pixels before requiring consent.
                val row = IntArray(bitmap!!.width)
                var transparent = false
                for (y in 0 until bitmap!!.height) {
                    ensureActive()
                    bitmap!!.getPixels(row, 0, row.size, 0, y, row.size, 1)
                    if (row.any {
                        Color.alpha(it) < 255
                    }) {
                        transparent = true
                        break
                    }
                }
                require(!transparent || options.flatten) {
                    "Transparency detected. Choose WebP or allow a white JPEG background."
                }
                val opaque = Bitmap.createBitmap(bitmap!!.width, bitmap!!.height, Bitmap.Config.ARGB_8888)
                Canvas(opaque).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap!!, 0f, 0f, null)
                }
                bitmap!!.recycle()
                bitmap = opaque
            }
            val folder = File(context.filesDir, "results").apply {
                mkdirs()
            }
            output = File(folder, "compressed_${UUID.randomUUID()}.${if (options.format == "JPEG") "jpg" else "webp"}")
            val format = if (options.format == "JPEG") Bitmap.CompressFormat.JPEG else if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            fun encode(q: Int) {
                output!!.outputStream().use {
                    require(bitmap!!.compress(format, q, it)) {
                        "Could not encode image."
                    }
                }
            }
            var reached = false
            for (round in 0..20) {
                ensureActive()
                var low = 10
                var high = options.quality
                var best = -1
                while (low <= high) {
                    ensureActive()
                    val q = (low + high) / 2
                    encode(q)
                    if (output!!.length() <= options.target) {
                        best = q
                        low = q + 1
                    } else high = q - 1
                }
                if (best >= 0) {
                    encode(best)
                    reached = output!!.length() <= options.target
                    break
                }
                encode(10)
                if (bitmap!!.width <= 64 || bitmap!!.height <= 64 || round == 20) break
                val scaled = Bitmap.createScaledBitmap(bitmap!!, CompressionMath.nextDimension(bitmap!!.width), CompressionMath.nextDimension(bitmap!!.height), true)
                bitmap!!.recycle()
                bitmap = scaled
            }
            ensureActive()
            PhotoResult(photo, output!!.absolutePath, output!!.length(), targetMet = reached)
        } catch (cancelled: CancellationException) {
            output?.delete()
            throw cancelled
        }
        catch (e: Exception) {
            output?.delete()
            PhotoResult(photo, error = e.message ?: "Unable to compress photo.")
        }
        finally {
            bitmap?.recycle()
        }
    }
    fun shareUri(result: PhotoResult): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(result.path!!))
    suspend fun save(result: PhotoResult): Uri = withContext(Dispatchers.IO) {
        val file = File(result.path!!)
        val webp = file.extension == "webp"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, if (webp) "image/webp" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Photo Compressor Lite")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION") val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Photo Compressor Lite").apply {
                    mkdirs()
                }
                put(MediaStore.Images.Media.DATA, File(dir, file.name).absolutePath)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create saved photo.")
        try {
            context.contentResolver.openOutputStream(uri)?.use {
                stream -> file.inputStream().use {
                    it.copyTo(stream)
                }
            } ?: error("Cannot write photo.")
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}
