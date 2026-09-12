package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.TableLight
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableSound
import org.tomlj.TomlTable

/**
 * Reads one `[[table]]` entry into a [TableLook] (`docs/tables.md`).
 *
 * There is no mesh field and never will be: the tray *is* the screen, and a
 * fixed box is what keeps the physics predictable and the capacity limit
 * meaningful. Sound and lighting are names from the app's own lists rather
 * than files, so a package can change how the tray looks without shipping an
 * audio decoder's worth of attack surface.
 */
internal class TableLookReader(
  private val fields: TomlFields,
  private val material: MaterialReader,
  private val files: FileChecker,
  private val report: Reporter,
) {
  /** The table look [table] describes, or `null` when something in it is wrong. */
  fun read(
    entry: TomlTable,
    index: Int,
  ): TableLook? {
    val where = "table ${index + 1}"
    fields.unknownKeys(entry, KNOWN, where)
    val id = slug(entry, where) ?: return null
    val named = "table '$id'"
    val name = fields.string(entry, "name", named, required = true) ?: return null
    val default = TableLook(id = id, name = name)
    return TableLook(
      id = id,
      name = name,
      floorTexturePath = files.texture(entry, "floor_texture", named, faces = 0),
      floorTiling = tiling(entry, "floor_tiling", named),
      wallTexturePath = files.texture(entry, "wall_texture", named, faces = 0),
      wallTiling = tiling(entry, "wall_tiling", named),
      floorColorArgb = material.colour(entry, "floor_color", named) ?: default.floorColorArgb,
      wallColorArgb = material.colour(entry, "wall_color", named) ?: default.wallColorArgb,
      roughness = material.bounded(entry, "roughness", named, UNIT) ?: default.roughness,
      metallic = material.bounded(entry, "metallic", named, UNIT) ?: default.metallic,
      friction = material.bounded(entry, "friction", named, TableLook.FrictionRange) ?: default.friction,
      restitution = material.bounded(entry, "restitution", named, TableLook.RestitutionRange) ?: default.restitution,
      sound = preset(entry, "sound", named, TableSound.entries, TableSound::id) ?: default.sound,
      light = preset(entry, "light", named, TableLight.entries, TableLight::id) ?: default.light,
    )
  }

  private fun slug(
    entry: TomlTable,
    where: String,
  ): String? {
    val id = fields.string(entry, "id", where, required = true) ?: return null
    if (Slug.isValid(id, DiceSetLimits.TABLE_ID_LENGTH)) return id
    report.error(
      ValidationCode.BadSlug,
      "'$id' is not ${Slug.describe(DiceSetLimits.TABLE_ID_LENGTH)}",
      fields.lineOf(entry, "id"),
    )
    return null
  }

  /** `[3, 6]` — how often a texture repeats across the short side and the long one. */
  private fun tiling(
    entry: TomlTable,
    key: String,
    where: String,
  ): TableLook.Tiling {
    val written = fields.integers(entry, key, where) ?: return TableLook.Tiling()
    val line = fields.lineOf(entry, key)
    if (written.size != 2) {
      report.error(ValidationCode.WrongType, "$where's '$key' has to be two numbers, like [3, 6]", line)
      return TableLook.Tiling()
    }
    return TableLook.Tiling(
      acrossShortSide = repeats(written[0], key, where, line),
      acrossLongSide = repeats(written[1], key, where, line),
    )
  }

  private fun repeats(
    value: Long,
    key: String,
    where: String,
    line: Int?,
  ): Int {
    val clamped = value.coerceIn(DiceSetLimits.TILING.first.toLong(), DiceSetLimits.TILING.last.toLong())
    if (clamped != value) {
      report.warn(
        ValidationCode.Clamped,
        "$where's '$key' repeats $value times, which is outside ${DiceSetLimits.TILING}, and counts as $clamped",
        line,
      )
    }
    return clamped.toInt()
  }

  /** One of the app's own named presets, which is the only thing a package may name. */
  private fun <T> preset(
    entry: TomlTable,
    key: String,
    where: String,
    options: List<T>,
    id: (T) -> String,
  ): T? {
    val name = fields.string(entry, key, where) ?: return null
    val found = options.firstOrNull { id(it) == name }
    if (found != null) return found
    report.error(
      ValidationCode.UnknownPreset,
      "$where has no '$key' called '$name'; there is ${options.joinToString(", ") { "'${id(it)}'" }}",
      fields.lineOf(entry, key),
    )
    return null
  }

  private companion object {
    val UNIT = 0.0..1.0
    val KNOWN =
      setOf(
        "id",
        "name",
        "floor_texture",
        "floor_tiling",
        "wall_texture",
        "wall_tiling",
        "floor_color",
        "wall_color",
        "roughness",
        "metallic",
        "friction",
        "restitution",
        "sound",
        "light",
      )
  }
}
