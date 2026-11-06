package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent
import de.drehtuer.dinfinity.designer.MinePackage
import de.drehtuer.dinfinity.designer.PackageFile

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

  /**
   * Writes [file] into the cache and opens the share sheet on it.
   *
   * @return the intent that was started, for a test to look at. Nothing in the
   *   app reads it.
   */
  fun share(
    context: Context,
    file: PackageFile,
  ): Intent = ShareSheet.offer(context, DIRECTORY, file.name, MinePackage.MEDIA_TYPE) { it.writeBytes(file.bytes) }
}
