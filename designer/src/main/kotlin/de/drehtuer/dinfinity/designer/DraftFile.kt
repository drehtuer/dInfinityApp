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
  /**
   * What [FORMAT] a reader can make sense of.
   *
   * Bumped when the shape changes — and **not** when it is merely extended. A
   * fill is a new kind of entry in the list that was already there, so every
   * draft written before fills existed reads exactly as it did, and a build
   * that predates them drops a fill it cannot draw rather than losing the
   * drawing around it. Bumping the number instead would blank every drawing on
   * the device to spare an older build one shape, which is not a trade.
   */
  const val FORMAT: Int = 1

  private val json = Json { ignoreUnknownKeys = true }

  /** [draft] as the text of its file. */
  fun write(draft: Draft): String {
    val faces =
      draft.faces
        .filterValues { it.marks.isNotEmpty() }
        .toSortedMap()
        .map { (cell, drawing) ->
          JsonObject(
            mapOf(
              CELL to JsonPrimitive(cell),
              STROKES to JsonArray(drawing.marks.map(::markOf)),
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
        val marks = cell?.let { (face[STROKES] as? JsonArray).orEmpty().mapNotNull(::markFrom) }
        if (cell == null || marks.isNullOrEmpty()) null else cell to FaceDrawing(marks = FaceDrawing.sunk(marks))
      }
    return Draft(die = die, faces = faces.toMap())
  }

  private fun root(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject

  /**
   * One mark.
   *
   * The dots are a flat list of numbers rather than a list of pairs: it is
   * half the file for the same drawing, and a stroke is a path rather than a
   * collection of objects. A fill is the same list standing for the boundary
   * of its region, told apart by [FILLS] — the one field a stroke never
   * carries, so a reader that does not know about fills drops them and keeps
   * the rest.
   */
  private fun markOf(mark: Mark): JsonObject =
    JsonObject(
      buildMap {
        put(COLOUR, JsonPrimitive(mark.colorArgb))
        when (mark) {
          is Stroke -> {
            put(WIDTH, JsonPrimitive(mark.width))
            put(ERASES, JsonPrimitive(mark.erases))
          }

          is Fill -> put(FILLS, JsonPrimitive(true))
        }
        put(DOTS, JsonArray(mark.dots.flatMap { listOf(JsonPrimitive(it.x), JsonPrimitive(it.y)) }))
      },
    )

  /**
   * One mark back, or null when it is not one.
   *
   * Everything is read first and judged afterwards, in one `when`, rather than
   * abandoned field by field: what makes a stroke and what makes a fill differ
   * by two fields, and the difference is easier to see written out than spread
   * across five early exits.
   */
  private fun markFrom(element: JsonElement): Mark? {
    val mark = element as? JsonObject ?: return null
    val colour = (mark[COLOUR] as? JsonPrimitive)?.content?.toIntOrNull()
    val fills = (mark[FILLS] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
    val dots = dotsOf(mark, least = if (fills) DOTS_OF_A_REGION else DOTS_OF_A_STROKE)
    val width = (mark[WIDTH] as? JsonPrimitive)?.content?.toFloatOrNull()
    return when {
      colour == null || dots == null -> null
      fills -> Fill(dots = dots, colorArgb = colour)
      width == null -> null
      else ->
        Stroke(
          dots = dots,
          colorArgb = colour,
          width = width,
          erases = (mark[ERASES] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
        )
    }
  }

  /**
   * The dots of one mark, or null when they are not dots.
   *
   * An odd count is half a point, and a `null` is something that was not a
   * number. Either way the path is not the path that was drawn.
   */
  private fun dotsOf(
    mark: JsonObject,
    least: Int,
  ): List<Dot>? {
    val numbers = (mark[DOTS] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
    if (numbers.size < least || numbers.size % 2 != 0 || numbers.any { it == null }) return null
    return numbers.filterNotNull().chunked(2) { Dot(x = it[0], y = it[1]) }
  }

  private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

  private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

  private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

  /** Two numbers to a dot, and a stroke of one dot is a tap rather than a line. */
  private const val DOTS_OF_A_STROKE = 4

  /** A region needs three corners before it is a region. */
  private const val DOTS_OF_A_REGION = 6

  private const val FORMAT_KEY = "format"
  private const val DIE = "die"
  private const val FACES = "faces"
  private const val CELL = "cell"
  private const val STROKES = "strokes"
  private const val COLOUR = "color"
  private const val WIDTH = "width"
  private const val ERASES = "erases"
  private const val FILLS = "fill"
  private const val DOTS = "dots"
}
