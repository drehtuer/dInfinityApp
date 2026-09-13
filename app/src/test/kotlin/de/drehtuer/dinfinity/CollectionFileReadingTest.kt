package de.drehtuer.dinfinity

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.collection.CollectionLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Reading a file somebody chose
 * (`docs/dice-notation.md`, "Export and import").
 *
 * A content URI is a handle to something another application controls, so the
 * tests that matter are the ones where it does not behave: a file bigger than
 * a collection may be, and one that is not there at all.
 */
@RunWith(RobolectricTestRunner::class)
class CollectionFileReadingTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private val directory = File(context.cacheDir, "collections")

  @Before
  fun clean() {
    directory.deleteRecursively()
    directory.mkdirs()
    // See CollectionSharingTest: FileProvider caches its roots per authority,
    // and Robolectric gives every test method a new data directory.
    FileProvider::class.java
      .getDeclaredField("sCache")
      .apply { isAccessible = true }
      .let { it.get(null) as MutableMap<*, *> }
      .clear()
  }

  @Test
  fun `a file reads`() {
    val result = CollectionFileReading.read(context.contentResolver, uriOf("""{"format":1}"""))

    assertEquals(CollectionFileReading.Result.Read("""{"format":1}"""), result)
  }

  @Test
  fun `text that is not ASCII survives the trip`() {
    val json = """{"name":"⚔️ Thorin — 日本語"}"""

    val result = CollectionFileReading.read(context.contentResolver, uriOf(json))

    assertEquals(CollectionFileReading.Result.Read(json), result)
  }

  @Test
  fun `a file exactly at the limit reads`() {
    val text = "x".repeat(CollectionLimits.MAX_BYTES)

    val result = CollectionFileReading.read(context.contentResolver, uriOf(text))

    assertTrue("a file at the limit was refused", result is CollectionFileReading.Result.Read)
  }

  @Test
  fun `a file one byte over the limit is refused`() {
    // The one extra byte read is what tells a file at the limit from one over
    // it. Without it the two are indistinguishable.
    val text = "x".repeat(CollectionLimits.MAX_BYTES + 1)

    val result = CollectionFileReading.read(context.contentResolver, uriOf(text))

    assertTrue("a file over the limit was read", result is CollectionFileReading.Result.Failed)
  }

  @Test
  fun `a file that is not there fails rather than throwing`() {
    val gone = Uri.parse("content://${context.packageName}.collections/collections/never-existed.json")

    val result = CollectionFileReading.read(context.contentResolver, gone)

    assertTrue("a missing file did not fail cleanly", result is CollectionFileReading.Result.Failed)
  }

  @Test
  fun `a URI nothing answers at fails rather than throwing`() {
    val nowhere = Uri.parse("content://de.drehtuer.nobody/anything")

    val result = CollectionFileReading.read(context.contentResolver, nowhere)

    assertTrue(result is CollectionFileReading.Result.Failed)
  }

  private fun uriOf(text: String): Uri {
    val file = File(directory, "chosen.json").apply { writeText(text) }
    return FileProvider.getUriForFile(context, "${context.packageName}.collections", file)
  }
}
