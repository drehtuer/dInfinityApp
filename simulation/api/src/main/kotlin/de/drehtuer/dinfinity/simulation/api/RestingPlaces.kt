package de.drehtuer.dinfinity.simulation.api

/**
 * Where dice sit on the table before anybody throws them
 * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * Tapping a saved roll puts its dice **on the table** rather than throwing
 * them: the board is cleared, the dice are laid out, and the throw is the
 * shake that comes next. More can be added from the picker and the formula can
 * be edited, and the board follows along — so what a player is looking at
 * before they shake is what they are about to throw
 * (`docs/TODO.md`, Step 4.1).
 *
 * **Nothing here is a roll.** No body is created, no step is taken and no face
 * is read; these are places to draw a die, and the faces they are drawn
 * showing are not a result and are never scored. A die's value comes from the
 * simulation and from nowhere else (`.claude/CLAUDE.md`).
 *
 * They are laid out the way an added die is dropped — each die takes the
 * clearest spot left, so the next one goes somewhere else — which is why they
 * do not overlap and why the arrangement is the same every time for the same
 * dice. A player who taps the same saved roll twice sees the same table.
 */
object RestingPlaces {
  /**
   * A place for each of [radiiMm], in the order they were asked for.
   *
   * Returns as many as there is floor for and no more: a formula the table
   * cannot hold is refused before it reaches here (`docs/tables.md`, "Capacity
   * rule"), but a rounding that leaves the last die without a spot should show
   * one die fewer rather than throw.
   */
  fun of(
    geometry: TableGeometry,
    radiiMm: List<Double>,
  ): List<Vector3> {
    val places = mutableListOf<Vector3>()
    radiiMm.forEach { radius ->
      val point = ClearSpace.clearestPoint(geometry, radius, places) ?: return places
      // On the floor rather than at nought: a die drawn with its centre on the
      // table is a die half sunk into it.
      places += point.copy(z = radius)
    }
    return places
  }
}
