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
 *
 * **Where a die comes to rest is here; how it gets there is [FallingIn].** A
 * die the picker adds is dropped onto the place this hands back and tumbles
 * into it, which is an animation and not a simulation — and it comes to rest
 * exactly where it would have been stood before there was a fall at all,
 * which is what keeps the board the same board every time.
 */
object RestingPlaces {
  /**
   * A place for each of [radiiMm], in the order they were asked for.
   *
   * Returns as many as there is floor for and no more: a formula the table
   * cannot hold is refused before it reaches here (`docs/tables.md`, "Capacity
   * rule"), but a rounding that leaves the last die without a spot should show
   * one die fewer rather than throw.
   *
   * @param among where the dice that are **already standing** are, and are to
   *   go on standing. Tapping the picker adds one die to a board that already
   *   has some on it, and the ones on it do not move: they are named here so
   *   the new one is given floor nobody is using, and no place is computed for
   *   them at all ([FallingIn]). Empty for a board built from nothing, which
   *   is the way this read before there was such a thing as adding one die.
   */
  fun of(
    geometry: TableGeometry,
    radiiMm: List<Double>,
    among: List<Vector3> = emptyList(),
  ): List<Vector3> {
    val places = mutableListOf<Vector3>()
    radiiMm.forEach { radius ->
      val point = ClearSpace.clearestPoint(geometry, radius, among + places) ?: return places
      // On the floor rather than at nought: a die drawn with its centre on the
      // table is a die half sunk into it.
      places += point.copy(z = radius)
    }
    return places
  }
}
