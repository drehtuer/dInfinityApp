package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent

/**
 * Handing a piece of text to whatever the phone shares text with.
 *
 * One caller: the developer screen's anomaly log
 * (`docs/physics-and-rendering.md`, "Debug tooling"). It is kept apart from
 * [NumbersSharing] and [CollectionSharing] deliberately, and the difference is
 * not the mechanism — it is **what is in it**. Those two carry a record and a
 * collection, neither of which has a seed in it and neither of which could
 * grow one (`docs/architecture.md`, decision 13). This one carries seeds, and
 * a `share(anything)` that could be handed either would be the place the two
 * got mixed up.
 *
 * No `FileProvider` and no file: a bug report is pasted, not attached, and a
 * copy on disk of a log that lives in memory would outlive the run it
 * describes.
 */
object TextSharing {
  /**
   * Opens the share sheet on [text].
   *
   * @return the intent that was started, for a test to look at. Nothing in the
   *   app reads it.
   */
  fun share(
    context: Context,
    text: String,
    title: String,
  ): Intent {
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = MEDIA_TYPE
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title)
      }
    val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
    return chooser
  }

  /** Plain text, because that is what a log pasted into an issue is. */
  const val MEDIA_TYPE: String = "text/plain"
}
