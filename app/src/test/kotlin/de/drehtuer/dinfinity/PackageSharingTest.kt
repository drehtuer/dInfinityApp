package de.drehtuer.dinfinity

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.designer.PackageFile
import org.junit.Assert.assertArrayEquals
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
 * Handing the exported dice set to another app (design `8c`;
 * `docs/face-designer.md`, "Export details").
 *
 * The same arrangement `CollectionSharingTest` checks, over a directory of its
 * own — which is the part worth a test of its own, because the whole point of
 * the narrow provider paths is that a new export cannot quietly widen them.
 */
@RunWith(RobolectricTestRunner::class)
class PackageSharingTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private val packages = File(context.cacheDir, "packages")

  @Before
  fun clean() {
    packages.deleteRecursively()
    forgetTheProviderRoots()
  }

  /**
   * Makes `FileProvider` read `collection_paths.xml` again.
   *
   * The same workaround, for the same reason, that `CollectionSharingTest`
   * explains: the provider caches its roots per authority, and Robolectric
   * gives every test method a fresh data directory underneath it.
   */
  private fun forgetTheProviderRoots() {
    val cache =
      FileProvider::class.java
        .getDeclaredField("sCache")
        .apply { isAccessible = true }
        .get(null) as MutableMap<*, *>
    cache.clear()
  }

  @Test
  fun `sharing writes the zip and opens a chooser on it`() {
    val intent = PackageSharing.share(context, file())

    assertEquals(Intent.ACTION_CHOOSER, intent.action)
    val started = shadowOf(context as Application).nextStartedActivity
    assertNotNull("no share sheet was opened", started)
    assertEquals(Intent.ACTION_CHOOSER, started.action)
  }

  @Test
  fun `the bytes that are shared are the bytes that were asked for`() {
    PackageSharing.share(context, file(bytes = byteArrayOf(0x50, 0x4B, 3, 4, 9)))

    val written = File(packages, "my-dice.zip")
    assertTrue("the package was not written", written.exists())
    assertArrayEquals(byteArrayOf(0x50, 0x4B, 3, 4, 9), written.readBytes())
  }

  @Test
  fun `it travels as a zip, over a URI the other app may read`() {
    val intent = PackageSharing.share(context, file())
    val send = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

    assertEquals("application/zip", send.type)
    val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)!!
    assertEquals("content", uri.scheme)
    assertEquals("${context.packageName}.collections", uri.authority)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }

  @Test
  fun `yesterday's export is not still sitting there to be offered today`() {
    packages.mkdirs()
    File(packages, "old.zip").writeText("{}")

    PackageSharing.share(context, file())

    assertEquals(listOf("my-dice.zip"), packages.list()?.toList())
  }

  @Test
  fun `the provider still cannot see a downloaded archive somebody is installing`() {
    // The reason each export has a directory of its own rather than a widened
    // one: an install works through the cache too, and what is in there came
    // from a stranger.
    val downloads = File(context.cacheDir, "sets").apply { mkdirs() }
    val archive = File(downloads, "stranger.zip").apply { writeText("not yours to hand out") }

    val refused =
      runCatching { FileProvider.getUriForFile(context, "${context.packageName}.collections", archive) }

    assertTrue("the provider handed out a file outside its directories", refused.isFailure)
  }

  private fun file(
    name: String = "my-dice.zip",
    bytes: ByteArray = byteArrayOf(0x50, 0x4B, 5, 6),
  ) = PackageFile(name = name, bytes = bytes)
}
