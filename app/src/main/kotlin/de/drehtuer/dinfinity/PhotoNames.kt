package de.drehtuer.dinfinity

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * What a picked file is called, as far as the system will say
 * (`docs/tables.md`, "Your own photo").
 *
 * A content URI is a handle rather than a path, so the only name there is is
 * whichever one the provider chooses to publish. It is asked for one thing
 * only: a suggestion to put in the name field, which the player then types
 * over. Nothing is decided by it and nothing is opened with it — the name is
 * text from another application, and the id the table ends up with is slugged
 * out of what is finally in the field (`PhotoTable.idOf`).
 *
 * In `app/` because a `ContentResolver` is reached through a context and
 * because `OpenableColumns` is a platform detail no feature module should
 * carry.
 */
object PhotoNames {
  /** The display name of [uri], or its last path segment when there is none. */
  fun of(
    resolver: ContentResolver,
    uri: Uri,
  ): String =
    runCatching {
      resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
      }
    }.getOrNull().orEmpty().ifEmpty { uri.lastPathSegment.orEmpty() }
}
