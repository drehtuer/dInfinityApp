package de.drehtuer.dinfinity.fixtures

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.TableLight
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableSound

/**
 * The standard dice, as a [DiceSet], for tests in every module.
 *
 * It stands in for the bundled package until `:dicesets:builtin` ships the
 * real `diceset.toml`, and it stays afterwards as the set tests build their
 * own variations from — a test that wants a set with no d12 says
 * `StandardDice.set(without = "d12")` rather than writing ten dice out again.
 *
 * The d10 is worth a look. Its faces score 1 to 10 but are *labelled* the way
 * a real one is printed, `1`…`9` and then `0`, so the units half of a d100
 * pair reads exactly what the player sees (`docs/dice-notation.md`,
 * "d100 and d%").
 *
 * **The values are not 1, 2, 3, … down the face order.** Every die here is
 * numbered the way the bundled package numbers it, with opposite faces adding
 * up (`docs/dice-sets.md`, "Numbering"), which is why each list is written out
 * rather than counted. The lists are geometry and could be derived — they come
 * from `simulation/api`'s `FaceNumbering` — but this module is what
 * `:simulation:api` tests against, so it cannot depend on it. What keeps them
 * honest is `BuiltinDiceSetTest`, which holds the shipped package to
 * `FaceNumbering` and this fixture to the shipped package; a hand edit here
 * that drifts is a failing test rather than a quiet lie.
 */
object StandardDice {
  /** What is printed on a fudge die's faces. */
  private val FUDGE_LABELS = mapOf(-1 to "−", 0 to "0", 1 to "+")

  /** The d2: a coin, not a cube. A set without one gets a d6 in its place. */
  val d2: Die = paired("d2", DieShape.Coin, listOf(1, 2))

  /** The d4, read from the vertex pointing up, as every d4 is. A tetrahedron has no opposites to pair. */
  val d4: Die = paired("d4", DieShape.Tetrahedron, listOf(1, 2, 3, 4))

  val d6: Die = paired("d6", DieShape.Cube, listOf(1, 2, 3, 5, 4, 6))
  val d8: Die = paired("d8", DieShape.Octahedron, listOf(1, 2, 3, 4, 5, 7, 6, 8))

  /** Scores 1 to 10; printed 1…9, 0, which is what is moulded into a real one. */
  val d10: Die =
    paired("d10", DieShape.PentagonalTrapezohedron, listOf(1, 2, 3, 4, 5, 7, 6, 8, 9, 10)) { "${it % 10}" }

  /** The tens half of a percentile pair: 00, 10, … 90. */
  val d10Tens: Die =
    paired("d10-tens", DieShape.PentagonalTrapezohedron, listOf(0, 10, 20, 30, 40, 60, 50, 70, 80, 90)) {
      "%02d".format(it)
    }

  val d12: Die = paired("d12", DieShape.Dodecahedron, listOf(1, 2, 3, 4, 5, 6, 9, 8, 7, 11, 10, 12))
  val d18: Die =
    paired(
      "d18",
      DieShape.EnneagonalTrapezohedron,
      listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 10, 12, 13, 14, 15, 16, 17, 18),
    )
  val d20: Die =
    paired(
      "d20",
      DieShape.Icosahedron,
      listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 13, 12, 11, 16, 15, 14, 18, 17, 19, 20),
    )

  /**
   * The fudge die: a cube of two minuses, two blanks and two pluses, paired
   * like the rest — a minus across from a plus, a blank across from a blank.
   */
  val fudge: Die = paired("df", DieShape.Cube, listOf(-1, -1, 0, 1, 0, 1)) { FUDGE_LABELS.getValue(it) }

  /**
   * A die carrying [values] in the catalogue's face order, labelled by [label].
   *
   * One place the face list is built, so a fixture die cannot come out with
   * its indices and its values disagreeing.
   */
  private fun paired(
    id: String,
    shape: DieShape,
    values: List<Int>,
    label: (Int) -> String = Int::toString,
  ): Die =
    Die(
      id = id,
      shape = shape,
      faces = values.mapIndexed { index, value -> Face(index = index, value = value, label = label(value)) },
    )

  /** Every standard die, in the order the dice picker shows them. */
  val all: List<Die> = listOf(d2, d4, d6, d8, d10, d10Tens, d12, d18, d20, fudge)

  /** The table looks the bundled package ships (`docs/tables.md`). */
  val tables: List<TableLook> =
    listOf(
      TableLook(id = "felt-green", name = "Green felt", floorColorArgb = 0xFF1F5E3A.toInt()),
      TableLook(id = "felt-black", name = "Black felt", floorColorArgb = 0xFF1A1A1A.toInt()),
      TableLook(id = "oak", name = "Oak", floorColorArgb = 0xFF5A3A1E.toInt(), sound = TableSound.Wood),
      TableLook(
        id = "dark-glass",
        name = "Dark glass",
        floorColorArgb = 0xFF20262B.toInt(),
        roughness = 0.1,
        sound = TableSound.Glass,
        light = TableLight.Cool,
      ),
      TableLook(id = "plain", name = "Plain", light = TableLight.Dim),
    )

  /**
   * The set, [as] a given id and [without] the dice named.
   *
   * Leaving dice out is how the fallback rules are tested: a set with no d12
   * still has to roll `1d20 + 1d12` by taking the d12 from the bundled set
   * (`docs/dice-notation.md`, "Evaluation").
   */
  fun set(
    id: String = DiceSet.BUILTIN_ID,
    without: Set<String> = emptySet(),
    name: String = "Standard",
  ): DiceSet =
    DiceSet(
      id = id,
      name = name,
      version = "1.0.0",
      license = "GPL-2.0-or-later",
      dice = all.filterNot { it.id in without },
      tables = if (id == DiceSet.BUILTIN_ID) tables else emptyList(),
    )
}
