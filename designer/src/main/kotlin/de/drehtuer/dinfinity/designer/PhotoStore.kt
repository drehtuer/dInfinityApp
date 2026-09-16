package de.drehtuer.dinfinity.designer

import java.io.File

/**
 * The photographs somebody has made tables out of, as files in a folder
 * (`docs/tables.md`, "Your own photo").
 *
 * The same shape as [DraftStore], and for the same reason: the personal
 * package is **built, not accumulated**, so something other than the package
 * has to be the record. A drawing's record is its draft; a photo table's
 * record is the downsized picture kept here, and the `mine/` folder is a view
 * of both together ([MineSets]).
 *
 * Two files per photo — the picture and the name somebody typed — rather than
 * one file with both in it. A picture is bytes and a name is text, and the
 * alternative is a container format written and parsed for the sake of one
 * line of UTF-8.
 *
 * The picture is written **first and renamed into place last**, so a process
 * that dies halfway leaves a `.part` file that nothing reads rather than a
 * table whose texture is half a photograph.
 */
class PhotoStore(
  private val directory: File,
  /** How many photos this store keeps. [PhotoTable.MAX_PHOTOS], except in a test. */
  val limit: Int = PhotoTable.MAX_PHOTOS,
) {
  /**
   * Every photo, by id.
   *
   * Sorted, because the personal package is built from this list and a package
   * whose `[[table]]` entries come out in whatever order the filesystem felt
   * like would differ from one reading to the next for no reason.
   */
  fun photos(): List<TablePhoto> = ids().sorted().mapNotNull(::read)

  /** The ids in the folder, which is what [PhotoTable.idOf] must not collide with. */
  fun ids(): Set<String> =
    directory
      .listFiles()
      .orEmpty()
      .filter { it.isFile && it.name.endsWith(IMAGE) }
      .map { it.name.removeSuffix(IMAGE) }
      .toSet()

  /** Whether there is room for one more (`PhotoTable.MAX_PHOTOS`). */
  fun hasRoom(): Boolean = ids().size < limit

  /**
   * A number that changes when the photos do — the same trick, and the same
   * arithmetic, as [DraftStore.stamp].
   */
  fun stamp(): Long =
    files().fold(0L) { total, file -> total + file.name.hashCode() + file.lastModified() + file.length() }

  /**
   * Writes [photo] down, replacing any photo already under that id.
   *
   * `false` when there was no room or the disk refused, and in both cases
   * nothing of the new photo is left behind — a half-added table is exactly
   * the state that must not exist, since the next reading would build the
   * package out of it.
   */
  fun add(photo: TablePhoto): Boolean {
    if (photo.id !in ids() && !hasRoom()) return false
    directory.mkdirs()
    val written =
      runCatching {
        writeThrough(fileFor(photo.id, IMAGE), photo.image)
        writeThrough(fileFor(photo.id, LABEL), photo.name.encodeToByteArray())
      }.isSuccess
    if (!written) forget(photo.id)
    return written
  }

  /** Takes a photo table off the phone, picture and name together. */
  fun forget(id: String) {
    listOf(IMAGE, LABEL).forEach { suffix ->
      val file = fileFor(id, suffix)
      file.delete()
      File(directory, file.name + PARTIAL).delete()
    }
  }

  /** The photo under [id], or null when its picture will not read back. */
  private fun read(id: String): TablePhoto? {
    val bytes = runCatching { fileFor(id, IMAGE).readBytes() }.getOrNull() ?: return null
    if (bytes.isEmpty()) return null
    // A name that has gone missing falls back to the id rather than losing the
    // table: an unnamed look in the list is recoverable, a look that is not
    // there is not.
    val name = runCatching { fileFor(id, LABEL).readText() }.getOrNull()?.let(PhotoTable::nameOf).orEmpty()
    return TablePhoto(id = id, name = name.ifEmpty { id }, image = bytes)
  }

  private fun writeThrough(
    file: File,
    bytes: ByteArray,
  ) {
    val partial = File(directory, file.name + PARTIAL)
    partial.writeBytes(bytes)
    if (!partial.renameTo(file)) {
      partial.delete()
      error("could not write ${file.name}")
    }
  }

  private fun files(): List<File> = directory.listFiles().orEmpty().filter(File::isFile)

  /**
   * One of a photo's two files: its picture, or its name.
   *
   * The id came out of [PhotoTable.idOf] and is a slug, so it is already a
   * file name. It is filtered here all the same, because the filesystem is the
   * last place to discover that it was not.
   */
  private fun fileFor(
    id: String,
    suffix: String,
  ): File = File(directory, id.filter { it in ALLOWED }.ifEmpty { PhotoTable.UNNAMED } + suffix)

  companion object {
    /** The folder these live in, under the app's own files, beside the drafts. */
    const val DIRECTORY: String = "table-photos"

    private const val IMAGE = ".${PhotoTable.EXTENSION}"
    private const val LABEL = ".name"
    private const val PARTIAL = ".part"
    private val ALLOWED = ('a'..'z') + ('0'..'9') + '-'
  }
}
