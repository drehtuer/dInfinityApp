package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.drehtuer.dinfinity.designer.MinePackage
import de.drehtuer.dinfinity.designer.PackageFile
import java.io.File

/**
 * Handing the personal dice set to whatever the author wants to do with it
 * (`docs/face-designer.md`, "Export details"; design `8c`).
 *
 * The third of the same arrangement, after [CollectionSharing] and
 * [NumbersSharing], and kept beside them rather than folded into them for the
 * reason [NumbersSharing] gives: what they carry differs, and a single
 * `share(anything)` would be a place where the wrong thing could be passed.
 * What they share is the rule — the copy goes into the **cache**, because it is
 * a copy made to be handed over, and a cache is the one place Android is
 * allowed to clean up behind us.
 *
 * It gets a **directory of its own** under the same provider rather than
 * joining one of theirs. The paths in `collection_paths.xml` are narrow on
 * purpose: a provider that can see the app's whole cache is a provider that
 * will one day be asked for a downloaded archive somebody is halfway through
 * installing. One more `cache-path` is the way to extend that; widening an
 * existing one is not.
 *
 * In `app/` rather than in `feature/sets`, because the FileProvider it needs is
 * declared in the application's manifest and its authority is the
 * application's id.
 */
object PackageSharing {
  /** Where the copies go. Cleared each time, so yesterday's export is not offered today. */
  private const val DIRECTORY = "packages"

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
    file: PackageFile,
  ): Intent {
    val directory = File(context.cacheDir, DIRECTORY)
    // Emptied rather than added to: the sheet offers one file, and a zip of
    // every die anybody ever drew is a copy of the drafts folder sitting in a
    // place nothing prunes.
    directory.deleteRecursively()
    directory.mkdirs()
    val written = File(directory, file.name)
    written.writeBytes(file.bytes)

    val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY, written)
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = MinePackage.MEDIA_TYPE
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
