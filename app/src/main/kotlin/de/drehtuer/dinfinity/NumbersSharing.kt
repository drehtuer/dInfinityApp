package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.drehtuer.dinfinity.feature.stats.ExportFile
import java.io.File

/**
 * Handing a file of numbers to whatever the player wants to do with it
 * (`docs/statistics.md`, "Export and reset").
 *
 * The same arrangement as [CollectionSharing], for the same reasons, and
 * deliberately not folded into it: the two share a provider and a shape, but
 * what they carry is a collection in one case and a record in the other, and a
 * single `share(anything)` would be a place where the wrong thing could be
 * passed. What they do share is the rule — the copy goes into the cache,
 * because it is a copy made to be handed over, and a cache is the one place
 * Android is allowed to clean up behind us.
 *
 * In `app/` rather than in `feature/stats`, because the FileProvider it needs
 * is declared in the application's manifest and its authority is the
 * application's id.
 */
object NumbersSharing {
  /** Where the copies go. Cleared each time, so yesterday's export is not offered today. */
  private const val DIRECTORY = "exports"

  /** Matches `collection_paths.xml`; the authority is the app's, with this suffix. */
  private const val AUTHORITY = ".collections"

  /**
   * Writes [file] into the cache and opens the share sheet on it.
   *
   * @return the intent that was started, for a test to look at. Nothing in the
   *   app reads it.
   */
  fun share(
    context: Context,
    file: ExportFile,
  ): Intent {
    val directory = File(context.cacheDir, DIRECTORY)
    // Emptied rather than added to: the sheet offers one file, and a directory
    // that only grows is a directory of every roll anybody ever exported —
    // which is the history again, in the one place nothing prunes.
    directory.deleteRecursively()
    directory.mkdirs()
    val written = File(directory, file.name)
    written.writeText(file.text)

    val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY, written)
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = file.mediaType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, file.name)
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
