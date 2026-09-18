package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape

/**
 * How a die is numbered: opposite faces add up (`docs/dice-sets.md`,
 * "Numbering").
 *
 * A moulded die puts 2 across from 5 on a d6 and 1 across from 20 on a d20, so
 * that whatever is under the table is what the face on top is missing and the
 * weight of the ink is spread evenly. The numbering therefore follows the
 * **pairing** rather than the face order: which face is across from which is
 * geometry ([ShapeGeometry.oppositesOf]), and the order the catalogue fixes is
 * only where the counting starts.
 *
 * It is here rather than in `core/model` because it is a fact about the solid
 * rather than about a die, and `simulation/api` is what owns the solids. The
 * bundled package carries the answer written out in its `faces` arrays, and a
 * test in `dicesets/builtin` holds those arrays to this — a set file is data
 * and cannot compute anything, but it can be checked against what does.
 */
object FaceNumbering {
  /**
   * [values] laid out in [shape]'s face order so that opposite faces take a
   * smallest and a largest together.
   *
   * The lowest value goes on the first face that has not been numbered yet
   * and the highest on the face across from it, then the next lowest and the
   * next highest, and so on. For the run `1..n` that is exactly "every
   * opposite pair sums to `n + 1`"; for a Fudge die's `−1, −1, 0, 0, 1, 1` it
   * is a minus across from a plus and a blank across from a blank, which is
   * what a real Fudge die is; and for a tens d10 it is `0` across from `90`.
   *
   * **A shape with no opposites keeps the order it was given.** A tetrahedron
   * is read from its corners and no two of them face opposite ways, so a d4
   * is 1–4 at its corners and there is nothing to pair.
   *
   * @param values what the die carries, in any order — they are sorted here,
   *   because "smallest against largest" is a statement about the multiset
   *   rather than about the order somebody happened to write it in.
   */
  fun paired(
    shape: DieShape,
    values: List<Int>,
  ): List<Int> {
    val opposites = ShapeGeometry.oppositesOf(shape)
    require(values.size == opposites.size) {
      "a $shape has ${opposites.size} faces, not ${values.size}"
    }
    val sorted = values.sorted()
    val numbered = MutableList<Int?>(values.size) { null }
    var low = 0
    var high = values.size - 1
    opposites.indices.forEach { face ->
      if (numbered[face] != null) return@forEach
      numbered[face] = sorted[low++]
      val across = opposites[face]
      if (across != null && across != face) numbered[across] = sorted[high--]
    }
    return numbered.map { requireNotNull(it) { "face numbering left a face blank on a $shape" } }
  }

  /**
   * The run `1..n` laid out in [shape]'s face order — an ordinary die.
   *
   * The common case of [paired] given a name, because "a d20 numbered the way
   * a d20 is numbered" is what almost every caller means.
   */
  fun plain(shape: DieShape): List<Int> = paired(shape, (1..shape.faceCount).toList())
}
