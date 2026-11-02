package com.photocompressorlite.myapp
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun input(name: String, transparent: Boolean = false): Photo {
        val file = File(context.cacheDir, name)
        val image = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        image.eraseColor(if (transparent) Color.TRANSPARENT else Color.rgb(60, 130, 210))
        file.outputStream().use {
            image.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
        }
        image.recycle()
        return Photo(Uri.fromFile(file), file.name, file.length())
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
    @Test fun homeSelectEmptyStateAndSettings() {
        compose.onNodeWithText("Select Photos").performClick()
        compose.onNodeWithText("Continue (0 photos)").assertIsNotEnabled()
        compose.onNodeWithText("Your photos belong here").assertExists()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("Light").performClick()
    }
    @Test fun selectedPhotoThroughCompressionAndSave() {
        val photo = input("flow.jpg")
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.onNodeWithText("Select Photos").performClick()
        compose.runOnIdle {
            vm.add(listOf(photo.uri))
        }
        compose.waitUntil(10000) {
            vm.state.value.selected.size == 1 && !vm.state.value.loading
        }
        capture("selected.png")
        compose.onNodeWithText("Continue (1 photos)").performClick()
        compose.onNodeWithText("Compress Photos").performClick()
        compose.waitUntil(15000) {
            vm.state.value.screen == "results"
        }
        compose.onNodeWithText("Compression Complete").assertExists()
        capture("results.png")
        compose.onNodeWithText("Save Photos").performClick()
        compose.waitUntil(10000) {
            vm.state.value.saved.size == 1
        }
        compose.onNodeWithText("Photos Saved").assertExists()
    }
    @Test fun compressionProducesDecodableOutputAndSecureUri() = runBlocking {
        val repo = PhotoRepository(context)
        val photo = input("test.jpg")
        val result = repo.compress(photo, CompressionOptions(target = 50 * 1024))
        assertNull(result.error)
        assertNotNull(result.path)
        assertTrue(result.bytes > 0)
        assertEquals(File(result.path!!).length(), result.bytes)
        assertEquals(result.bytes <= 50 * 1024, result.targetMet)
        assertEquals("content", repo.shareUri(result).scheme)
        assertTrue(File(photo.uri.path!!).exists())
        val bitmap = android.graphics.BitmapFactory.decodeFile(result.path)
        assertNotNull(bitmap)
        bitmap.recycle()
    }
    @Test fun transparencyRequiresConsentAndWebpKeepsAlpha() = runBlocking {
        val repo = PhotoRepository(context)
        val photo = input("alpha.png", true)
        assertNotNull(repo.compress(photo, CompressionOptions()).error)
        val jpeg = repo.compress(photo, CompressionOptions(flatten = true))
        assertNull(jpeg.error)
        val webp = repo.compress(photo, CompressionOptions(format = "WebP"))
        assertNull(webp.error)
        val bitmap = android.graphics.BitmapFactory.decodeFile(webp.path)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        bitmap.recycle()
    }
    @Test fun orientationIsAppliedAndMetadataRemoved() = runBlocking {
        val photo = input("rotated.jpg")
        ExifInterface(photo.uri.path!!).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, "6")
            setAttribute(ExifInterface.TAG_ARTIST, "Private metadata")
            saveAttributes()
        }
        val result = PhotoRepository(context).compress(photo, CompressionOptions())
        assertNull(result.error)
        val bitmap = android.graphics.BitmapFactory.decodeFile(result.path)
        assertEquals(480, bitmap.width)
        assertEquals(640, bitmap.height)
        bitmap.recycle()
        assertNull(ExifInterface(result.path!!).getAttribute(ExifInterface.TAG_ARTIST))
    }
    @Test fun cancelledCompressionLeavesNoPartialOutput() = runBlocking {
        val photo = input("cancel.jpg")
        val repo = PhotoRepository(context)
        val directory = File(context.filesDir, "results").apply { mkdirs() }
        val before = directory.listFiles()!!.map { it.name }.toSet()
        val job = launch(Dispatchers.IO) { repo.compress(photo, CompressionOptions(target = 1)) }
        // Cancel after the output file appears, while the target search is running.
        withTimeout(10000) { while (directory.listFiles()!!.none { it.name !in before } && job.isActive) delay(1) }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(before, directory.listFiles()!!.map { it.name }.toSet())
    }
    @Test fun impossibleTargetIsReportedHonestly() = runBlocking {
        val result = PhotoRepository(context).compress(input("tiny-target.jpg"), CompressionOptions(target = 1))
        assertNull(result.error)
        assertTrue(result.bytes > 1)
        assertFalse(result.targetMet)
    }
    @Test fun invalidInputReportsFailure() = runBlocking {
        val result = PhotoRepository(context).compress(Photo(Uri.fromFile(File(context.cacheDir, "missing")), "missing", 0), CompressionOptions())
        assertNotNull(result.error)
        assertNull(result.path)
    }
}



