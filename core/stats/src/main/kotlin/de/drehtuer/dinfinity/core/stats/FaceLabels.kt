package de.drehtuer.dinfinity.core.stats

import de.drehtuer.dinfinity.core.model.Face

/**
 * What a die's values are printed as, so a histogram can say `−`, `0`, `+`
 * where it used to say `-1`, `0`, `1` (`docs/statistics.md`, "Screens";
 * decision 73).
 *
 * The statistics count **values** and never labels — a `1,2,3,1,2,3` d6 is a
 * d3 — so a label is looked up for a value at read time, from the set that is
 * installed now. Nothing about it is stored: a set that is relabelled shows its
 * new labels over the old counts, and a set that is gone shows the value.
 *
 * One value, one label, or none at all. A value printed the same way on every
 * face that scores it reads as that print. A value printed **two different
 * ways** — two symbols on a die that score the same — has no one label that is
 * not a lie about the other face, so it reads as its number: the bar counts
 * the value, and the value is the one thing both faces agree on.
 */
class FaceLabels private constructor(
  private val printed: Map<Int, Set<String>>,
) {
  /**
   * How [value] is written: the die's own label, or [Face.printed] when the die
   * is not installed, has no face worth [value], or prints it more than one
   * way.
   */
  fun of(value: Int): String = printed[value]?.singleOrNull() ?: Face.printed(value)

  /**
   * These labels and [other]'s together, for a roll-up across sets (design
   * option `5c`).
   *
   * Two sets' d6s printed the same way keep their labels; a value the two
   * print differently reads as its number, for the same reason one die's two
   * different prints of a value do.
   */
  operator fun plus(other: FaceLabels): FaceLabels =
    FaceLabels(
      (printed.keys + other.printed.keys).associateWith { value ->
        printed[value].orEmpty() + other.printed[value].orEmpty()
      },
    )

  override fun equals(other: Any?): Boolean = other is FaceLabels && other.printed == printed

  override fun hashCode(): Int = printed.hashCode()

  override fun toString(): String = "FaceLabels($printed)"

  companion object {
    /** No labels: every value reads as its number. A die whose set is gone. */
    val None: FaceLabels = FaceLabels(emptyMap())

    /** The labels [faces] print, by the value each face scores. */
    fun of(faces: List<Face>): FaceLabels =
      FaceLabels(faces.groupBy(Face::value).mapValues { (_, same) -> same.map(Face::label).toSet() })
  }
}
