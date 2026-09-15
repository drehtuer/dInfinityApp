package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One draft as text, and back (`docs/face-designer.md`, "Drawing tools").
 *
 * Through the JSON DOM with every field taken by hand, the way
 * `core/collection` and `dicesets/format` read their files. A draft is the
 * app's own and not a stranger's, so there is no report and no list of
 * problems: anything that does not read is simply not a draft, and the
 * drawing somebody starts is a blank one. The reason for reading it this way
 * anyway is the other one — a deserializer's idea of the file is the class
 * shape of the day, and a draft has to survive the class changing under it.
 *
 * **What is stored is the strokes, not the undo stack.** Undo is unlimited
 * *within a session*, which is what `docs/face-designer.md` promises, and a
 * history restored from disk would be a history of a sitting that ended —
 * pressing undo and watching a drawing rewind past the point you opened it is
 * not what that button means.
 *
 * **The die is stored as its id**, not copied whole. A draft is a drawing *on*
 * a die and the die belongs to a dice set; carrying a copy would let a draft
 * go on describing a die whose set has been changed under it. A draft whose
 * die is not installed is not offered — and its file is kept rather than
 * deleted, so re-installing the package brings the drawing back, which is the
 * rule the default set and the default table already follow
 * (`docs/dice-sets.md`).
 */
object DraftFile {
  /** What [FORMAT] a reader can make sense of. Bumped when the shape changes. */
  const val FORMAT: Int = 1

  private val json = Json { ignoreUnknownKeys = true }

  /** [draft] as the text of its file. */
  fun write(draft: Draft): String {
    val faces =
      draft.faces
        .filterValues { it.strokes.isNotEmpty() }
        .toSortedMap()
        .map { (cell, drawing) ->
          JsonObject(
            mapOf(
              CELL to JsonPrimitive(cell),
              STROKES to JsonArray(drawing.strokes.map(::strokeOf)),
            ),
          )
        }
    return json.encodeToString(
      JsonElement.serializer(),
      JsonObject(
        mapOf(
          FORMAT_KEY to JsonPrimitive(FORMAT),
          DIE to JsonPrimitive(draft.die.id),
          FACES to JsonArray(faces),
        ),
      ),
    )
  }

  /** Which die [text] is a drawing on, or null when it is not a draft at all. */
  fun dieOf(text: String): String? = root(text)?.get(DIE)?.string()

  /**
   * [text] as a drawing on [die], or null when it is not one.
   *
   * [die] is passed in rather than built from the file, so a draft can only
   * ever be read back onto the die it was drawn on. A cell the die does not
   * have is dropped rather than refused: a die whose set gained a face is a
   * die somebody can go on drawing, and the alternative is losing the whole
   * drawing over one cell.
   */
  fun read(
    text: String,
    die: Die,
  ): Draft? {
    val root = root(text)?.takeIf { it.int(FORMAT_KEY) == FORMAT && it[DIE]?.string() == die.id } ?: return null
    val faces =
      (root[FACES] as? JsonArray).orEmpty().mapNotNull { face ->
        val cell = (face as? JsonObject)?.int(CELL)?.takeIf { it in die.faces.indices }
        val strokes = cell?.let { (face[STROKES] as? JsonArray).orEmpty().mapNotNull(::strokeFrom) }
        if (cell == null || strokes.isNullOrEmpty()) null else cell to FaceDrawing(strokes = strokes)
      }
    return Draft(die = die, faces = faces.toMap())
  }

  private fun root(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject

  /**
   * One stroke.
   *
   * The dots are a flat list of numbers rather than a list of pairs: it is
   * half the file for the same drawing, and a stroke is a path rather than a
   * collection of objects.
   */
  private fun strokeOf(stroke: Stroke): JsonObject =
    JsonObject(
      mapOf(
        COLOUR to JsonPrimitive(stroke.colorArgb),
        WIDTH to JsonPrimitive(stroke.width),
        ERASES to JsonPrimitive(stroke.erases),
        DOTS to JsonArray(stroke.dots.flatMap { listOf(JsonPrimitive(it.x), JsonPrimitive(it.y)) }),
      ),
    )

  private fun strokeFrom(element: JsonElement): Stroke? {
    val stroke = element as? JsonObject ?: return null
    val numbers = (stroke[DOTS] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
    // An odd count is half a point, and a `null` is something that was not a
    // number. Either way the path is not the path that was drawn.
    val path = numbers.size >= DOTS_PER_STROKE && numbers.size % 2 == 0 && numbers.none { it == null }
    val colour = (stroke[COLOUR] as? JsonPrimitive)?.content?.toIntOrNull()
    val width = (stroke[WIDTH] as? JsonPrimitive)?.content?.toFloatOrNull()
    if (!path || colour == null || width == null) return null
    return Stroke(
      dots = numbers.filterNotNull().chunked(2) { Dot(x = it[0], y = it[1]) },
      colorArgb = colour,
      width = width,
      erases = (stroke[ERASES] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
    )
  }

  private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

  private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

  private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

  /** Two numbers to a dot, and a stroke of one dot is a tap rather than a mark. */
  private const val DOTS_PER_STROKE = 4

  private const val FORMAT_KEY = "format"
  private const val DIE = "die"
  private const val FACES = "faces"
  private const val CELL = "cell"
  private const val STROKES = "strokes"
  private const val COLOUR = "color"
  private const val WIDTH = "width"
  private const val ERASES = "erases"
  private const val DOTS = "dots"
}
