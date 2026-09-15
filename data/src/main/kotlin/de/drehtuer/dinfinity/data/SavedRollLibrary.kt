package de.drehtuer.dinfinity.data

/**
 * Saved rolls and the groups they live in, as a screen wants them
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * **The one place the two halves are joined**, in the sense `SetLibrary` is for
 * dice sets. [SavedRollRepository] knows about rolls and
 * [SavedRollGroupRepository] about the folders; neither knows about the other,
 * and a rule that relates them is written here once rather than in every screen
 * that needs both.
 *
 * It exists because the split has a cost and this is where it is paid. Three of
 * the five screens about saved rolls need both halves, and handing each of them
 * two repositories put two of them over the parameter count detekt allows —
 * which was the honest signal that the pair is a thing with a name rather than
 * two arguments that happen to travel together.
 */
class SavedRollLibrary(
  /** The rolls themselves. */
  val rolls: SavedRollRepository,
  /** The folders they live in. */
  val groups: SavedRollGroupRepository,
)
