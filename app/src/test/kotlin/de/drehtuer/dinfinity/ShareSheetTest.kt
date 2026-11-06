package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The plumbing under the three typed `share`s, each of which has its own test as well. */
@RunWith(RobolectricTestRunner::class)
class ShareSheetTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private val folder get() = File(context.cacheDir, DIRECTORY)

  @Before
  fun clean() {
    folder.deleteRecursively()
    // The same Robolectric workaround `CollectionSharingTest` documents.
    val cache =
      FileProvider::class.java
        .getDeclaredField("sCache")
        .apply { isAccessible = true }
        .get(null) as MutableMap<*, *>
    cache.clear()
  }

  private fun offer(name: String = "a.txt") =
    ShareSheet.offer(context, DIRECTORY, name, "text/plain") { it.writeText("hello") }

  private fun sent(chooser: Intent): Intent = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

  @Test
  fun `what is written is the one file in the folder`() {
    offer()

    assertEquals("hello", File(folder, "a.txt").readText())
  }

  @Test
  fun `the last copy is gone when the next is offered`() {
    offer("yesterday.txt")
    offer("today.txt")

    assertFalse(File(folder, "yesterday.txt").exists())
    assertTrue(File(folder, "today.txt").exists())
  }

  @Test
  fun `the other app is told what it is and may read it`() {
    val send = sent(offer())

    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals("text/plain", send.type)
    assertEquals("a.txt", send.getStringExtra(Intent.EXTRA_TITLE))
    val file = send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
    assertEquals(context.packageName + ".collections", file.authority)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }

  @Test
  fun `the sheet can be opened from outside an activity`() {
    val chooser = offer()

    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }

  private companion object {
    // A directory `collection_paths.xml` really names, or the provider refuses.
    const val DIRECTORY = "exports"
  }
}
