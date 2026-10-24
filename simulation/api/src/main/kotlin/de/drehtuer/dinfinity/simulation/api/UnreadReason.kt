package de.drehtuer.dinfinity.simulation.api

/**
 * Why a die was left unread, and so is thrown again by the next shake
 * (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice").
 *
 * A reading of the throw and never an input to it: nothing decides anything
 * by it. It exists so that a share of dice re-thrown can be taken apart — a
 * long d4 leaning on its neighbour and a coin on its rim are different
 * problems with different cures, and one count of both says neither.
 *
 * @param id how the harness writes it, in its JSON and its table.
 */
enum class UnreadReason(
  val id: String,
) {
  /** Standing on another die, whatever it shows: what it stands on is about to be taken away. */
  OnAnotherDie("on another die"),

  /** Still moving when the throw stopped, so it showed nothing to anybody. */
  StillMoving("still moving"),

  /** At rest with no face up, leaning on another die. */
  CockedOnADie("cocked against a die"),

  /** At rest with no face up, leaning on a wall. */
  CockedOnAWall("cocked against a wall"),

  /** At rest with no face up and nothing to lean on: on an edge, a corner or a coin's rim. */
  CockedOnTheFloor("cocked on the floor"),
  ;

  companion object {
    /**
     * Why a die that could not be read could not be, from what touched it.
     *
     * In this order because the first that applies is the one a cure would
     * have to address: a die on another die is that whatever it shows, a die
     * still moving shows nothing yet, and only a die at rest is cocked
     * against what it leans on — another die before a wall, because a die
     * wedged between the two is a pile-up before it is a wall's.
     */
    fun of(
      onAnotherDie: Boolean,
      atRest: Boolean,
      touchingDie: Boolean,
      touchingWall: Boolean,
    ): UnreadReason =
      when {
        onAnotherDie -> OnAnotherDie
        !atRest -> StillMoving
        touchingDie -> CockedOnADie
        touchingWall -> CockedOnAWall
        else -> CockedOnTheFloor
      }

    /** The reason [id] names, or null when it names none. */
    fun ofId(id: String): UnreadReason? = entries.firstOrNull { it.id == id }
  }
}
