package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * What [CollectionSharing], [NumbersSharing] and [PackageSharing] all do once
 * they know what they are carrying: put one copy in the cache and open the share
 * sheet on it.
 *
 * Each of them keeps its own typed `share`, for the reason [NumbersSharing]
 * gives. This takes a directory and bytes to write, and is not a way in for
 * anything else.
 */
internal object ShareSheet {
  /** Matches `collection_paths.xml`; the authority is the app's, with this suffix. */
  private const val AUTHORITY = ".collections"

  /**
   * Empties [directory] in the cache, has [write] fill a file called [name] in
   * it, and opens the share sheet on that file. Emptied rather than added to:
   * the sheet offers one file, and a directory that only grows keeps a copy of
   * everything anybody ever handed over in a place nothing prunes.
   *
   * @param directory one of the `cache-path`s in `collection_paths.xml`.
   * @return the intent that was started, for a test to look at.
   */
  fun offer(
    context: Context,
    directory: String,
    name: String,
    mediaType: String,
    write: (File) -> Unit,
  ): Intent {
    val folder = File(context.cacheDir, directory)
    folder.deleteRecursively()
    folder.mkdirs()
    val written = File(folder, name)
    write(written)

    val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY, written)
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = mediaType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, name)
        // Without this the app on the other side gets a URI it may not read.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser =
      Intent
        .createChooser(send, null)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
    return chooser
  }
}
