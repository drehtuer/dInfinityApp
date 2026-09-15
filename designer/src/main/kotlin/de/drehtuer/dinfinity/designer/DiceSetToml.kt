package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.Face
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
 * Only the fields a drawn set actually has are written. There is no reason to
 * emit a `[defaults]` table full of the numbers the app would have used anyway
 * — a key nobody wrote is a key nobody has to keep true.
 */
object DiceSetToml {
  /** The schema version everything the app writes declares. */
  const val FORMAT: Int = 1

  /** The folder a package's atlases live in. */
  const val TEXTURES: String = "textures"

  /** Where a die's atlas goes inside the package. */
  fun texturePathOf(dieId: String): String = "$TEXTURES/$dieId.png"

  /** [set] as the text of its `diceset.toml`. */
  fun write(set: DiceSet): String =
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
      set.dice.forEach { die ->
        appendLine()
        appendDie(die)
      }
    }

  private fun StringBuilder.appendDie(die: Die) {
    appendLine("[[die]]")
    appendLine("id = ${quoted(die.id)}")
    appendLine("shape = ${quoted(die.shape.id)}")
    appendLine("faces = [${die.faces.joinToString(", ") { it.value.toString() }}]")
    // Only when a face says something its value cannot. `labels` defaults to
    // the face values as text, so writing them out again would be a line of
    // the file that can only ever repeat the line above it.
    if (die.faces.any { it.label != it.value.toString() }) {
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
