package de.drehtuer.dinfinity

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * What a picked photo is called (`docs/tables.md`, "Your own photo").
 *
 * A content URI is a handle rather than a path, so the only name there is is
 * whichever one the provider publishes — and the cases worth testing are the
 * ones where it publishes none.
 */
@RunWith(RobolectricTestRunner::class)
class PhotoNamesTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  /** The one directory the app's own provider can see (`file_paths.xml`). */
  private val chosen = File(context.cacheDir, "collections")

  @Before
  fun clean() {
    chosen.deleteRecursively()
    chosen.mkdirs()
    // See CollectionSharingTest: FileProvider caches its roots per authority,
    // and Robolectric gives every test method a new data directory.
    FileProvider::class.java
      .getDeclaredField("sCache")
      .apply { isAccessible = true }
      .let { it.get(null) as MutableMap<*, *> }
      .clear()
  }

  @Test
  fun `a provider that publishes a name is asked for it`() {
    val uri = uriOf("oak_table.jpg")

    assertEquals("oak_table.jpg", PhotoNames.of(context.contentResolver, uri))
  }

  @Test
  fun `a uri nothing answers for falls back to its last path segment`() {
    val uri = Uri.parse("content://nothing.at.all/photos/felt.png")

    assertEquals("felt.png", PhotoNames.of(context.contentResolver, uri))
  }

  @Test
  fun `a uri with nothing in it at all is simply nameless`() {
    assertEquals("", PhotoNames.of(context.contentResolver, Uri.EMPTY))
  }

  private fun uriOf(name: String): Uri {
    val file = File(chosen, name).apply { writeBytes(byteArrayOf(1)) }
    return FileProvider.getUriForFile(context, "${context.packageName}.collections", file)
  }
}
