package de.drehtuer.dinfinity.dicesets.format

import org.tomlj.TomlTable

/**
 * Checks a file a set points at, in the order that keeps the dangerous parts
 * last (`docs/dice-sets.md`, "Textures").
 *
 * The path is checked before anything is opened; the size before anything is
 * read; the dimensions, out of the header, before anything is decoded. An
 * image claiming to be thirty thousand pixels square never reaches a decoder,
 * because the decoder is the part with the attack surface.
 *
 * It also keeps the running total of texture bytes, since a package is capped
 * as a whole and not only file by file.
 */
internal class FileChecker(
  private val files: PackageFiles,
  private val fields: TomlFields,
  private val report: Reporter,
) {
  private var textureBytes = 0L

  /**
   * The texture [key] names, or `null` when it is absent or unusable.
   *
   * A problem is reported and `null` comes back; the caller carries on so the
   * rest of the report is still produced.
   */
  fun texture(
    table: TomlTable,
    key: String,
    where: String,
    faces: Int,
  ): String? {
    val raw = fields.string(table, key, where) ?: return null
    val line = fields.lineOf(table, key)
    val reference = reference(raw, where, line)
    val bytes = reference?.let { readable(it, where, line) }
    if (reference == null || bytes == null) return null
    checkImage(bytes, reference, where, line, faces)
    return reference.path
  }

  private fun reference(
    raw: String,
    where: String,
    line: Int?,
  ): ReferencedFile? {
    val refusal = ReferencedFile.reasonToRefuse(raw, DiceSetLimits.IMAGE_EXTENSIONS)
    if (refusal != null) {
      report.error(ValidationCode.BadFileReference, "$where: $refusal", line)
      return null
    }
    return ReferencedFile.parse(raw, DiceSetLimits.IMAGE_EXTENSIONS)
  }

  /**
   * The bytes of [reference], or `null` with a reason reported.
   *
   * The size is asked for before the bytes are, and the package's running
   * total is kept here: a package is capped as a whole as well as file by
   * file, so a hundred textures of 3 MiB each is refused even though not one
   * of them is (`docs/dice-sets.md`, "Textures").
   */
  private fun readable(
    reference: ReferencedFile,
    where: String,
    line: Int?,
  ): ByteArray? {
    val size = files.size(reference.path)
    textureBytes += size ?: 0L
    val refusal =
      when {
        size == null ->
          ValidationCode.ReferencedFileMissing to "$where points at '$reference', which is not here"
        size > DiceSetLimits.MAX_TEXTURE_BYTES ->
          ValidationCode.FileTooLarge to
            "'$reference' is $size bytes; a texture is at most ${DiceSetLimits.MAX_TEXTURE_MIB} MiB"
        textureBytes > DiceSetLimits.MAX_PACKAGE_TEXTURE_BYTES ->
          ValidationCode.FileTooLarge to
            "the package's textures come to more than ${DiceSetLimits.MAX_PACKAGE_TEXTURE_MIB} MiB together"
        else -> null
      }
    if (refusal != null) report.error(refusal.first, refusal.second, line)
    return if (refusal == null) files.read(reference.path) else null
  }

  private fun checkImage(
    bytes: ByteArray,
    reference: ReferencedFile,
    where: String,
    line: Int?,
    faces: Int,
  ) {
    val size = ImageHeader.sizeOf(bytes)
    if (size == null) {
      val kind = reference.extension
      report.error(ValidationCode.TextureUnreadable, "'$reference' is not a $kind the app can read", line)
      return
    }
    val limit = DiceSetLimits.MAX_TEXTURE_PIXELS
    if (size.width > limit || size.height > limit) {
      report.error(
        ValidationCode.TextureTooLarge,
        "'$reference' is ${size.width}×${size.height}; textures are at most $limit×$limit",
        line,
      )
      return
    }
    checkCells(size, reference, where, line, faces)
  }

  /**
   * A face has to be drawn upright in a square cell, so an atlas whose cells
   * would come out oblong is worth saying something about. It still installs:
   * it will look wrong, and that is the author's business, not a safety one
   * (`docs/dice-sets.md`, rule 3).
   */
  private fun checkCells(
    size: ImageHeader.Size,
    reference: ReferencedFile,
    where: String,
    line: Int?,
    faces: Int,
  ) {
    if (faces <= 0) return
    val grid = ShapeAtlas.gridFor(faces)
    val cellWidth = size.width.toDouble() / grid.columns
    val cellHeight = size.height.toDouble() / grid.rows
    if (cellWidth != cellHeight) {
      report.warn(
        ValidationCode.AtlasNotSquare,
        "$where's '$reference' is ${size.width}×${size.height}, which does not divide into " +
          "${grid.columns}×${grid.rows} square cells",
        line,
      )
    }
  }
}
