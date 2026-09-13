package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.drehtuer.dinfinity.feature.saved.CollectionExport
import de.drehtuer.dinfinity.feature.saved.CollectionFile
import java.io.File

/**
 * Handing a collection to whatever the player wants to do with it
 * (`docs/dice-notation.md`, "Export and import": "via the share sheet").
 *
 * The share sheet rather than a file picker, because "export" is not one
 * action: it is mailing a stat block to a player, saving it into Files,
 * putting it in a chat, or pushing it to a repository the group keeps. One
 * sheet offers all of them, and the app does not have to have an opinion.
 *
 * The file goes into the cache rather than anywhere permanent. It is a copy
 * made to be handed over; what happens to it after that belongs to whatever
 * took it, and a cache is the one place Android is allowed to clean up behind
 * us.
 *
 * In `app/` rather than in `feature/saved`, because the FileProvider it needs
 * is declared in the application's manifest and its authority is the
 * application's id. A screen hands its file up the same way it hands up a
 * request to navigate; what the app does with it is the app's.
 */
object CollectionSharing {
  /** Where the copies go. Cleared each time, so yesterday's export is not offered today. */
  private const val DIRECTORY = "collections"

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
    file: CollectionFile,
  ): Intent {
    val directory = File(context.cacheDir, DIRECTORY)
    // Emptied rather than added to: the sheet offers one file, and a directory
    // that only grows is a directory of everything anybody ever exported.
    directory.deleteRecursively()
    directory.mkdirs()
    val written = File(directory, file.name)
    written.writeText(file.json)

    val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY, written)
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = CollectionExport.MEDIA_TYPE
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
