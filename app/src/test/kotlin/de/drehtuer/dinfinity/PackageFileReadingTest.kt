package de.drehtuer.dinfinity

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.dicesets.install.InstallLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Copying an archive somebody chose (`docs/dice-sets.md`, "Installing from a
 * URL or file").
 *
 * A content URI is a handle to something another application controls, so the
 * tests that matter are the ones where it does not behave: something larger
 * than a package may be, and something that is not there at all.
 */
@RunWith(RobolectricTestRunner::class)
class PackageFileReadingTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  /**
   * Where the archive is written so that a content URI can be minted for it.
   *
   * The app's `FileProvider` deliberately exposes one directory and it is this
   * one (`file_paths.xml`): a provider that could see the database is a
   * provider that will one day be asked for it. Nothing about *installing* uses
   * this provider — a picker hands back somebody else's URI — so this is a
   * test's way of producing one, not the app's.
   */
  private val chosen = File(context.cacheDir, "collections")

  private val into = File(context.cacheDir, "packages")

  @Before
  fun clean() {
    chosen.deleteRecursively()
    chosen.mkdirs()
    into.deleteRecursively()
    // See CollectionSharingTest: FileProvider caches its roots per authority,
    // and Robolectric gives every test method a new data directory.
    FileProvider::class.java
      .getDeclaredField("sCache")
      .apply { isAccessible = true }
      .let { it.get(null) as MutableMap<*, *> }
      .clear()
  }

  @Test
  fun `a file is copied, byte for byte`() {
    val bytes = ByteArray(1024) { (it % 256).toByte() }

    val result = PackageFileReading.copy(context.contentResolver, uriOf(bytes), into)

    assertTrue(result is PackageFileReading.Result.Copied)
    assertArrayEqualsish(bytes, (result as PackageFileReading.Result.Copied).file.readBytes())
  }

  @Test
  fun `an empty file is copied rather than refused`() {
    // It will not install — the extractor has the last word on that — but
    // "empty" is a thing for the validator to say, not for the copy.
    val result = PackageFileReading.copy(context.contentResolver, uriOf(ByteArray(0)), into)

    assertTrue(result is PackageFileReading.Result.Copied)
    assertEquals(0, (result as PackageFileReading.Result.Copied).file.length())
  }

  @Test
  fun `a file over the limit is refused, and nothing is left behind`() {
    // The cap a download gets. A file picked off the phone has not crossed the
    // network, but it is no more trustworthy for it: the limit is about what
    // the extractor is willing to open.
    val tooMuch = ByteArray((InstallLimits.MAX_DOWNLOAD_BYTES + 1).toInt())

    val result = PackageFileReading.copy(context.contentResolver, uriOf(tooMuch), into)

    assertTrue("something that big was accepted", result is PackageFileReading.Result.Failed)
    assertEquals("the oversized copy was kept", emptyList<File>(), into.listFiles().orEmpty().toList())
  }

  @Test
  fun `a file exactly at the limit is accepted`() {
    // The one byte past the limit is what tells these two apart, so both are
    // worth asserting.
    val exactly = ByteArray(InstallLimits.MAX_DOWNLOAD_BYTES.toInt())

    val result = PackageFileReading.copy(context.contentResolver, uriOf(exactly), into)

    assertTrue("a file exactly at the limit was refused", result is PackageFileReading.Result.Copied)
  }

  @Test
  fun `a uri nothing answers at is refused rather than thrown`() {
    val nowhere = Uri.parse("content://${context.packageName}.collections/nothing/at/all")

    val result = PackageFileReading.copy(context.contentResolver, nowhere, into)

    assertTrue(result is PackageFileReading.Result.Failed)
    assertFalse("a failure said nothing about itself", (result as PackageFileReading.Result.Failed).why.isEmpty())
  }

  @Test
  fun `two copies do not collide`() {
    // Two archives chosen one after the other, before the first is deleted.
    val first = PackageFileReading.copy(context.contentResolver, uriOf(byteArrayOf(1)), into)
    val second = PackageFileReading.copy(context.contentResolver, uriOf(byteArrayOf(2)), into)

    val one = (first as PackageFileReading.Result.Copied).file
    val two = (second as PackageFileReading.Result.Copied).file
    assertFalse("the second copy overwrote the first", one.path == two.path)
    assertEquals(1, one.readBytes().single().toInt())
    assertEquals(2, two.readBytes().single().toInt())
  }

  private fun assertArrayEqualsish(
    expected: ByteArray,
    actual: ByteArray,
  ) {
    assertEquals("a different number of bytes came out", expected.size, actual.size)
    assertTrue("the bytes changed on the way through", expected.contentEquals(actual))
  }

  private fun uriOf(bytes: ByteArray): Uri {
    val file = File(chosen, "package-${System.nanoTime()}.zip").apply { writeBytes(bytes) }
    return FileProvider.getUriForFile(context, "${context.packageName}.collections", file)
  }
}
