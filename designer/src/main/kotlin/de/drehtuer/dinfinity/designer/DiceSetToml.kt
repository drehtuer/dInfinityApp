package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DiePhysical
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.TableLook
import java.util.Locale

/**
 * A set the app made, written out as the file everybody else reads
 * (`docs/dice-sets.md`, "`diceset.toml`").
 *
 * Written by hand rather than by a serializer, and that is less of a gamble
 * than it looks: what comes out of here goes straight back through
 * `DiceSetValidator` before anybody is offered it, so a mistake in this file is
 * caught by the same code that catches a stranger's mistakes rather than by a
 * reviewer. The app's own output is not a privileged path.
 *
 * Only the fields a drawn set actually has are written. A `[defaults]` table
 * full of the numbers the app would have used anyway is a key nobody has to
 * keep true, so the three physical ones appear only once somebody has moved
 * them off the defaults (`docs/dice-sets.md`, "Weight, translucency and size,
 * as a person sets them").
 */
object DiceSetToml {
  /** The schema version everything the app writes declares. */
  const val FORMAT: Int = 1

  /** The folder a package's atlases live in. */
  const val TEXTURES: String = "textures"

  /** Where a die's atlas goes inside the package. */
  fun texturePathOf(dieId: String): String = "$TEXTURES/$dieId.png"

  /**
   * [set] as the text of its `diceset.toml`, with [defaults] as the material
   * every die of it inherits.
   */
  fun write(
    set: DiceSet,
    defaults: DieMaterial = DieMaterial(),
  ): String =
    buildString {
      appendLine("format = $FORMAT")
      appendLine()
      appendLine("[set]")
      appendLine("id = ${quoted(set.id)}")
      appendLine("name = ${quoted(set.name)}")
      appendLine("version = ${quoted(set.version)}")
      set.author?.let { appendLine("author = ${quoted(it)}") }
      set.license?.let { appendLine("license = ${quoted(it)}") }
      set.description?.let { appendLine("description = ${quoted(it)}") }
      appendDefaults(defaults)
      set.dice.forEach { die ->
        appendLine()
        appendDie(die)
      }
      set.tables.forEach { look ->
        appendLine()
        appendTable(look)
      }
    }

  /**
   * One `[[table]]` entry (`docs/tables.md`, "Table looks").
   *
   * Written the way [appendDie] is: **only what differs from what the reader
   * would have used anyway.** A photo table sets four things — its texture,
   * that the texture does not repeat, that the floor is not tinted, and its
   * name — and the file says exactly those. Every key that would merely repeat
   * `TableLook`'s own default is a key somebody would later have to keep in
   * step with it.
   */
  private fun StringBuilder.appendTable(look: TableLook) {
    val default = TableLook(id = look.id, name = look.name)
    appendLine("[[table]]")
    appendLine("id = ${quoted(look.id)}")
    appendLine("name = ${quoted(look.name)}")
    look.floorTexturePath?.let { appendLine("floor_texture = ${quoted(it)}") }
    look.wallTexturePath?.let { appendLine("wall_texture = ${quoted(it)}") }
    tiling("floor_tiling", look.floorTiling, default.floorTiling)
    tiling("wall_tiling", look.wallTiling, default.wallTiling)
    colour("floor_color", look.floorColorArgb, default.floorColorArgb)
    colour("wall_color", look.wallColorArgb, default.wallColorArgb)
    number("roughness", look.roughness, default.roughness)
    number("metallic", look.metallic, default.metallic)
    number("friction", look.friction, default.friction)
    number("restitution", look.restitution, default.restitution)
    if (look.sound != default.sound) appendLine("sound = ${quoted(look.sound.id)}")
    if (look.light != default.light) appendLine("light = ${quoted(look.light.id)}")
  }

  private fun StringBuilder.tiling(
    key: String,
    value: TableLook.Tiling,
    default: TableLook.Tiling,
  ) {
    if (value == default) return
    appendLine("$key = [${value.acrossShortSide}, ${value.acrossLongSide}]")
  }

  /** `#aarrggbb`, always eight digits: a colour with a hidden alpha is a surprise. */
  private fun StringBuilder.colour(
    key: String,
    value: Int,
    default: Int,
  ) {
    if (value == default) return
    appendLine("$key = ${quoted("#" + String.format(Locale.ROOT, "%08x", value))}")
  }

  private fun StringBuilder.number(
    key: String,
    value: Double,
    default: Double,
  ) {
    if (value == default) return
    // `Double.toString` rather than a formatter: it always writes a `.`, where
    // a formatter on a phone set to German would write a decimal comma, and a
    // decimal comma is not TOML.
    appendLine("$key = $value")
  }

  /**
   * The `[defaults]` table, and only the keys that are not what a reader would
   * have used anyway.
   *
   * Three of them can be here, because three of them are what a person sets on
   * the details screen. `translucency` goes out as the **per cent** a set file
   * is written in rather than as the fraction the model holds, which is the
   * one place the two scales meet on the way out (`MaterialReader` is where
   * they meet on the way in).
   */
  private fun StringBuilder.appendDefaults(defaults: DieMaterial) {
    val standard = DieMaterial()
    if (defaults.sizeMm == standard.sizeMm &&
      defaults.density == standard.density &&
      defaults.translucency == standard.translucency
    ) {
      return
    }
    appendLine()
    appendLine("[defaults]")
    number("size_mm", defaults.sizeMm, standard.sizeMm)
    number("density", defaults.density, standard.density)
    number(
      "translucency",
      DiePhysical.translucencyPercentOf(defaults.translucency),
      DiePhysical.translucencyPercentOf(standard.translucency),
    )
  }

  private fun StringBuilder.appendDie(die: Die) {
    appendLine("[[die]]")
    appendLine("id = ${quoted(die.id)}")
    appendLine("shape = ${quoted(die.shape.id)}")
    appendLine("faces = [${die.faces.joinToString(", ") { it.value.toString() }}]")
    // Only when a face says something its value cannot. `labels` defaults to
    // the face values as text, so writing them out again would be a line of
    // the file that can only ever repeat the line above it. The comparison is
    // against the *reader's* default ([Face.printed]) and not against
    // `toString`, or a negative face would carry a `labels` line saying
    // exactly what leaving it out already says.
    if (die.faces.any { it.label != Face.printed(it.value) }) {
      appendLine("labels = [${die.faces.joinToString(", ") { quoted(it.label.take(Face.MAX_LABEL_LENGTH)) }}]")
    }
    // Only when it is not what the solid does anyway: a `read` that repeats
    // the catalogue is a field somebody would have to keep in step with it.
    if (die.read != die.shape.naturalRead) appendLine("read = ${quoted(die.read.id)}")
    die.texturePath?.let { appendLine("texture = ${quoted(it)}") }
  }

  /**
   * [text] as a TOML basic string.
   *
   * The two characters that would end the string or start an escape, and then
   * every control character as `\uXXXX`. A name typed into a phone can hold
   * anything at all, and a newline in the middle of a quoted string is a set
   * file that does not parse — which would be this object handing the
   * validator a file it wrote itself and cannot read back.
   */
  private fun quoted(text: String): String =
    buildString {
      append('"')
      text.forEach { character ->
        when {
          character == '\\' -> append("\\\\")
          character == '"' -> append("\\\"")
          character.isControl() -> append(String.format(Locale.ROOT, "\\u%04X", character.code))
          else -> append(character)
        }
      }
      append('"')
    }

  private fun Char.isControl(): Boolean = code < ' '.code || code == DELETE

  private const val DELETE = 0x7F
}
