package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent
import de.drehtuer.dinfinity.feature.stats.ExportFile

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

  /**
   * Writes [file] into the cache and opens the share sheet on it.
   *
   * @return the intent that was started, for a test to look at. Nothing in the
   *   app reads it.
   */
  fun share(
    context: Context,
    file: ExportFile,
  ): Intent = ShareSheet.offer(context, DIRECTORY, file.name, file.mediaType) { it.writeText(file.text) }
}
