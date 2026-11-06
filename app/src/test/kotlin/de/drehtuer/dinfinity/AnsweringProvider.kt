package de.drehtuer.dinfinity

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.robolectric.Robolectric

/**
 * Another application's provider, behaving however a test needs it to.
 *
 * A content URI is a handle to something the app does not control, and the
 * interesting answers are the ones `FileProvider` never gives: a stream that
 * is not there, a query with nothing in it. `ContentResolver.openInputStream`
 * really does hand back null when a provider has no file to give.
 */
internal class AnsweringProvider : ContentProvider() {
  /** What a query comes to. Null is what the contract allows a provider to say. */
  var answer: () -> Cursor? = { null }

  override fun onCreate(): Boolean = true

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? = answer()

  /** No file, and no exception to say why. */
  override fun openFile(
    uri: Uri,
    mode: String,
  ): ParcelFileDescriptor? = null

  override fun getType(uri: Uri): String? = null

  override fun insert(
    uri: Uri,
    values: ContentValues?,
  ): Uri? = null

  override fun delete(
    uri: Uri,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0

  companion object {
    const val AUTHORITY = "de.drehtuer.dinfinity.test.answering"

    /** Registers one under [AUTHORITY], for the resolver to find. */
    fun installed(): AnsweringProvider =
      Robolectric
        .buildContentProvider(AnsweringProvider::class.java)
        .create(AUTHORITY)
        .get()

    fun uriOf(path: String): Uri = Uri.parse("content://$AUTHORITY/$path")
  }
}
