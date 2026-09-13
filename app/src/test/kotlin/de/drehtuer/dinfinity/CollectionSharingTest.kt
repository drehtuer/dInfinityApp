package de.drehtuer.dinfinity

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.feature.saved.CollectionFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Handing an exported collection to another app
 * (`docs/dice-notation.md`, "Export and import": "via the share sheet").
 *
 * In `app` rather than in `feature/saved` because the FileProvider this leans
 * on is declared in the application's manifest and its authority is the
 * application's id — a test of it anywhere else would be testing a provider
 * that does not exist on a phone.
 */
@RunWith(RobolectricTestRunner::class)
class CollectionSharingTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private val collection = File(context.cacheDir, "collections")

  @Before
  fun clean() {
    collection.deleteRecursively()
    forgetTheProviderRoots()
  }

  /**
   * Makes `FileProvider` read `collection_paths.xml` again.
   *
   * It keeps the roots it worked out in a static map keyed by authority, which
   * is right on a phone — the cache directory does not move while the process
   * lives. Robolectric gives every test method its own temporary data
   * directory, so the second test in this class would otherwise be handed the
   * first one's roots and refuse a file that is perfectly well inside its own.
   *
   * Reaching into a private field is not something to do lightly. It is done
   * here because the alternative is one enormous test, and because what it
   * works around is an artifact of the test runner rather than anything about
   * the app.
   */
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
    val intent = CollectionSharing.share(context, file())

    assertEquals(Intent.ACTION_CHOOSER, intent.action)
    val started = shadowOf(context as Application).nextStartedActivity
    assertNotNull("no share sheet was opened", started)
    assertEquals(Intent.ACTION_CHOOSER, started.action)
  }

  @Test
  fun `the file that is shared is the file that was asked for`() {
    CollectionSharing.share(context, file(name = "thorin.dinfinity.json", json = """{"format":1}"""))

    val written = File(collection, "thorin.dinfinity.json")
    assertTrue("the collection was not written", written.exists())
    assertEquals("""{"format":1}""", written.readText())
  }

  @Test
  fun `the other app is given a content URI it is allowed to read`() {
    // Without the grant the app on the other side gets a URI it cannot open,
    // which fails silently and looks like an empty file.
    val intent = CollectionSharing.share(context, file())
    val send = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

    val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)!!
    assertEquals("content", uri.scheme)
    assertEquals("${context.packageName}.collections", uri.authority)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }

  @Test
  fun `yesterday's export is not still sitting there to be offered today`() {
    // The directory is emptied rather than added to: the sheet offers one
    // file, and a directory that only grows is a directory of everything
    // anybody ever exported.
    collection.mkdirs()
    File(collection, "old.dinfinity.json").writeText("{}")

    CollectionSharing.share(context, file(name = "new.dinfinity.json"))

    assertEquals(listOf("new.dinfinity.json"), collection.list()?.toList())
  }

  @Test
  fun `the provider can see nothing but the directory those copies go in`() {
    // A file-sharing provider that can reach the database is one that will be
    // asked for it. `collection_paths.xml` says cache/collections and nothing
    // else, and this is what proves it.
    val elsewhere = File(context.cacheDir, "elsewhere").apply { mkdirs() }
    val secret = File(elsewhere, "dinfinity.db").apply { writeText("not yours") }

    val refused =
      runCatching {
        androidx.core.content.FileProvider.getUriForFile(
          context,
          "${context.packageName}.collections",
          secret,
        )
      }

    assertTrue("the provider handed out a file outside its directory", refused.isFailure)
  }

  private fun file(
    name: String = "everything.dinfinity.json",
    json: String = """{"format":1,"name":"Everything","groups":[],"rolls":[]}""",
  ) = CollectionFile(name = name, json = json)
}
