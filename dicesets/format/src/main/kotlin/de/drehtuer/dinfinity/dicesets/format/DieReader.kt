package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import org.tomlj.TomlTable

/** Reads one `[[die]]` entry into a [Die], or reports why it cannot. */
internal class DieReader(
  private val fields: TomlFields,
  private val material: MaterialReader,
  private val files: FileChecker,
  private val report: Reporter,
) {
  /** The die [table] describes, or `null` when something in it is wrong. */
  fun read(
    table: TomlTable,
    index: Int,
    defaults: DieMaterial,
  ): Die? {
    val where = "die ${index + 1}"
    fields.unknownKeys(table, KNOWN + material.keys, where)
    val id = slug(table, where)
    val shape = shape(table, where)
    val named = id?.let { "die '$it'" } ?: where
    val faces = shape?.let { faces(table, it, named) }
    if (id == null || shape == null || faces == null) return null
    return Die(
      id = id,
      shape = shape,
      faces = faces,
      read = readMode(table, shape, named),
      texturePath = files.texture(table, "texture", named, shape.faceCount),
      material = material.read(table, named, defaults),
    )
  }

  private fun slug(
    table: TomlTable,
    where: String,
  ): String? {
    val id = fields.string(table, "id", where, required = true) ?: return null
    if (Slug.isValid(id, DiceSetLimits.DIE_ID_LENGTH)) return id
    report.error(
      ValidationCode.BadSlug,
      "'$id' is not ${Slug.describe(DiceSetLimits.DIE_ID_LENGTH)}",
      line(table, "id"),
    )
    return null
  }

  private fun shape(
    table: TomlTable,
    where: String,
  ): DieShape? {
    val name = fields.string(table, "shape", where, required = true) ?: return null
    val shape = DieShape.ofId(name)
    if (shape != null) return shape
    val hint =
      if (name == "mesh") {
        "author-supplied meshes are designed but not in this version"
      } else {
        "the shapes are ${DieShape.entries.joinToString(", ") { it.id }}"
      }
    report.error(ValidationCode.UnknownShape, "$where has no shape called '$name': $hint", line(table, "shape"))
    return null
  }

  private fun faces(
    table: TomlTable,
    shape: DieShape,
    where: String,
  ): List<Face>? {
    val values = fields.integers(table, "faces", where, required = true) ?: return null
    val outside = values.firstOrNull { it !in Face.ValueRange.first.toLong()..Face.ValueRange.last.toLong() }
    val problem =
      when {
        values.size != shape.faceCount ->
          ValidationCode.FaceCountMismatch to
            "$where is a ${shape.id} and needs ${shape.faceCount} faces, not ${values.size}"
        outside != null ->
          ValidationCode.FaceValueOutOfRange to
            "$where has a face worth $outside; values run ${Face.ValueRange.first} to ${Face.ValueRange.last}"
        else -> null
      }
    if (problem != null) {
      report.error(problem.first, problem.second, line(table, "faces"))
      return null
    }
    val labels = labels(table, values.size, where)
    return values.mapIndexed { index, value ->
      // The same default the face designer's "fill with numbers" uses, so a
      // set file with no `labels` and one the designer wrote come out the same
      // die (`Face.printed`).
      Face(index = index, value = value.toInt(), label = labels?.getOrNull(index) ?: Face.printed(value.toInt()))
    }
  }

  /** `labels`, each cut down to what a face can actually show. */
  private fun labels(
    table: TomlTable,
    faces: Int,
    where: String,
  ): List<String>? {
    val written = fields.strings(table, "labels", where) ?: return null
    val line = line(table, "labels")
    if (written.size != faces) {
      report.error(ValidationCode.FaceCountMismatch, "$where has ${written.size} labels for $faces faces", line)
      return null
    }
    return written.map { label ->
      if (label.length <= DiceSetLimits.MAX_LABEL_LENGTH) {
        label
      } else {
        report.warn(
          ValidationCode.LabelTruncated,
          "$where's label \"$label\" is longer than ${DiceSetLimits.MAX_LABEL_LENGTH} characters and is cut short",
          line,
        )
        label.take(DiceSetLimits.MAX_LABEL_LENGTH)
      }
    }
  }

  private fun readMode(
    table: TomlTable,
    shape: DieShape,
    where: String,
  ): FaceRead {
    val name = fields.string(table, "read", where) ?: return shape.naturalRead
    val mode = FaceRead.ofId(name)
    if (mode != null) return mode
    report.error(
      ValidationCode.UnknownPreset,
      "$where reads '$name'; a die is read ${FaceRead.entries.joinToString(" or ") { "'${it.id}'" }}",
      line(table, "read"),
    )
    return shape.naturalRead
  }

  private fun line(
    table: TomlTable,
    key: String,
  ): Int? = fields.lineOf(table, key)

  private companion object {
    val KNOWN = setOf("id", "shape", "faces", "labels", "read", "texture")
  }
}
