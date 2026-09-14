package de.drehtuer.dinfinity

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.feature.stats.ExportFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Handing a file of numbers to another app
 * (`docs/statistics.md`, "Export and reset").
 *
 * In `app` rather than in `feature/stats` for the reason [CollectionSharingTest]
 * is: the FileProvider this leans on is declared in the application's manifest
 * and its authority is the application's id.
 */
@RunWith(RobolectricTestRunner::class)
class NumbersSharingTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private val exports = File(context.cacheDir, "exports")

  @Before
  fun clean() {
    exports.deleteRecursively()
    forgetTheProviderRoots()
  }

  /** The same Robolectric workaround [CollectionSharingTest] documents. */
  private fun forgetTheProviderRoots() {
    val cache =
      androidx.core.content.FileProvider::class.java
        .getDeclaredField("sCache")
        .apply { isAccessible = true }
        .get(null) as MutableMap<*, *>
    cache.clear()
  }

  @Test
  fun `sharing writes the file and opens a chooser on it`() {
    val intent = NumbersSharing.share(context, file())

    assertEquals(Intent.ACTION_CHOOSER, intent.action)
    val started = shadowOf(context as Application).nextStartedActivity
    assertNotNull("no share sheet was opened", started)
    assertEquals(Intent.ACTION_CHOOSER, started.action)
  }

  @Test
  fun `the file that is shared is the file that was asked for`() {
    NumbersSharing.share(context, file(name = "tuesday-campaign.csv", text = "when,total\n1970,7\n"))

    val written = File(exports, "tuesday-campaign.csv")
    assertTrue("the export was not written", written.exists())
    assertEquals("when,total\n1970,7\n", written.readText())
  }

  @Test
  fun `the format the player chose is what the other app is told it is`() {
    // A CSV announced as JSON opens in the wrong thing, or in nothing.
    val csv = NumbersSharing.share(context, file(name = "rolls.csv", mediaType = "text/csv"))
    val json = NumbersSharing.share(context, file(name = "rolls.json", mediaType = "application/json"))

    assertEquals("text/csv", csv.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!.type)
    assertEquals("application/json", json.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!.type)
  }

  @Test
  fun `the other app is given a content URI it is allowed to read`() {
    val intent = NumbersSharing.share(context, file())
    val send = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

    val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)!!
    assertEquals("content", uri.scheme)
    assertEquals("${context.packageName}.collections", uri.authority)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }

  @Test
  fun `yesterday's export is not still sitting there to be offered today`() {
    // Worse here than for a collection: a directory of every export anybody
    // ever made is the history again, in the one place nothing prunes it.
    exports.mkdirs()
    File(exports, "old.csv").writeText("stale")

    NumbersSharing.share(context, file(name = "new.csv"))

    assertEquals(listOf("new.csv"), exports.list()?.toList())
  }

  @Test
  fun `an export does not land where a collection is offered from`() {
    // Two directories, one provider. They are separate so that exporting the
    // history does not clear a collection out from under a share sheet that
    // is still open on it.
    val collections = File(context.cacheDir, "collections").apply { mkdirs() }
    File(collections, "thorin.dinfinity.json").writeText("{}")

    NumbersSharing.share(context, file(name = "rolls.csv"))

    assertTrue("the collection was cleared away", File(collections, "thorin.dinfinity.json").exists())
    assertFalse("the export landed in the collections directory", File(collections, "rolls.csv").exists())
  }

  private fun file(
    name: String = "rolls.csv",
    text: String = "when,session,formula,total\n",
    mediaType: String = "text/csv",
  ) = ExportFile(name = name, text = text, mediaType = mediaType)
}
