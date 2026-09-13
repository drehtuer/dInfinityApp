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
 */
object StandardDice {
  /** What is printed on a fudge die's faces. */
  private val FUDGE_LABELS = mapOf(-1 to "−", 0 to "0", 1 to "+")

  /** The d2: a coin, not a cube. A set without one gets a d6 in its place. */
  val d2: Die = Die.standard("d2", DieShape.Coin)

  /** The d4, read from the vertex pointing up, as every d4 is. */
  val d4: Die = Die.standard("d4", DieShape.Tetrahedron)

  val d6: Die = Die.standard("d6", DieShape.Cube)
  val d8: Die = Die.standard("d8", DieShape.Octahedron)

  /** Scores 1 to 10; printed 1…9, 0, which is what is moulded into a real one. */
  val d10: Die =
    Die(
      id = "d10",
      shape = DieShape.PentagonalTrapezohedron,
      faces = List(10) { Face(index = it, value = it + 1, label = "${(it + 1) % 10}") },
    )

  /** The tens half of a percentile pair: 00, 10, … 90. */
  val d10Tens: Die =
    Die(
      id = "d10-tens",
      shape = DieShape.PentagonalTrapezohedron,
      faces = List(10) { Face(index = it, value = it * 10, label = "%02d".format(it * 10)) },
    )

  val d12: Die = Die.standard("d12", DieShape.Dodecahedron)
  val d18: Die = Die.standard("d18", DieShape.EnneagonalTrapezohedron)
  val d20: Die = Die.standard("d20", DieShape.Icosahedron)

  /** The fudge die: a cube of two minuses, two blanks and two pluses. */
  val fudge: Die =
    Die(
      id = "df",
      shape = DieShape.Cube,
      faces =
        listOf(-1, -1, 0, 0, 1, 1).mapIndexed { index, value ->
          Face(index = index, value = value, label = FUDGE_LABELS.getValue(value))
        },
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
