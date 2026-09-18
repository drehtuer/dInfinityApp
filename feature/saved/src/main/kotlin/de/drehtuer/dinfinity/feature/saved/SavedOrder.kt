package de.drehtuer.dinfinity.feature.saved

/**
 * The arithmetic of a drag: a list, the thing being dragged, and where the
 * finger is, to the list that should now be drawn
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * Plain Kotlin, and here rather than inside the gesture, because this is the
 * only part of dragging a list that can be *wrong* — and the only part a JVM
 * test can reach. What Compose is left with is the gesture and the drawing:
 * where the pointer is, which row is under it, and asking for this answer.
 *
 * The row that moves is the one under the pointer rather than the one the drag
 * began on, which is what makes a list longer than a thumb work: the finger
 * arrives somewhere and the list makes room there, rather than the list
 * pretending the finger never left where it started.
 */
object SavedOrder {
  /**
   * [items] with [moving] taken out and put back at [onto].
   *
   * @param onto where the finger is, as an index into [items]. Out of range
   *   is clamped rather than refused — a finger dragged off the top of the
   *   list means the top, and off the bottom means the bottom, which is how
   *   somebody moves a row to an end without being able to aim at it.
   * @return a new list, or [items] itself when nothing moved: an item that is
   *   not in the list, and a drag that ends on the row it began on, both leave
   *   the list exactly as it was rather than making an equal copy of it.
   */
  fun <T> moved(
    items: List<T>,
    moving: T,
    onto: Int,
  ): List<T> {
    val from = items.indexOf(moving)
    if (from < 0) return items
    val to = onto.coerceIn(0, items.size - 1)
    if (to == from) return items
    val reordered = items.toMutableList()
    reordered.removeAt(from)
    reordered.add(to, moving)
    return reordered
  }
}
