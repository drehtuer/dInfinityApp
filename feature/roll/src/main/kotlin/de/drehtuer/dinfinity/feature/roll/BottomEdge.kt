package de.drehtuer.dinfinity.feature.roll

/**
 * Which of the two pull-ups has the bottom edge of the roll screen
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * There are two of them now — the result ([PullUpResult]) and the saved rolls
 * ([PullUpSavedRolls]) — and they both come up from the same edge. Left to
 * themselves they would cover each other: the result arrives on its own as
 * soon as the dice have been read, and it would land on top of a strip of
 * saved rolls somebody was in the middle of reading.
 *
 * So the rule is written down once, here, as arithmetic a JVM test can ask
 * about rather than as two composables each remembering to shut the other:
 *
 * - **They are never both up.** Opening either parks the other, which is the
 *   same rule the two menus along the top keep and for the same reason.
 * - **Parked is not gone.** Either one pushed down still shows its grip, so
 *   the grips stack on the edge — the result's on the edge itself, the saved
 *   rolls' directly above it — and both stay one touch away.
 * - **A result that lands takes the edge.** Nobody should have to reach for
 *   the number they have just rolled, so [resultArrives] puts the sheet up
 *   and the saved rolls down, whatever the player had open.
 *
 * Which means the saved rolls give way to a result and not the other way
 * about: the result is the thing that cannot be got back without throwing the
 * dice again, and a strip of saved rolls is one pull away for ever.
 */
internal data class BottomEdge(
  /** Where the result sheet rests, or would rest if there were a result. */
  val result: SheetRest = SheetRest.Down,
  /** Where the saved rolls rest. Parked to start with: the felt is the screen. */
  val saved: SheetRest = SheetRest.Down,
) {
  /** The invariant, as something a test can read: never two sheets over one edge. */
  val apart: Boolean get() = result != SheetRest.Up || saved != SheetRest.Up

  /** The player moved the result sheet. Up with it, the saved rolls go down. */
  fun resultTo(rest: SheetRest): BottomEdge =
    BottomEdge(result = rest, saved = if (rest == SheetRest.Up) SheetRest.Down else saved)

  /** And the other way about. */
  fun savedTo(rest: SheetRest): BottomEdge =
    BottomEdge(result = if (rest == SheetRest.Up) SheetRest.Down else result, saved = rest)

  /** The dice have been read: the sheet comes up by itself. */
  fun resultArrives(): BottomEdge = resultTo(SheetRest.Up)

  /**
   * The roll has been put away, so there is no sheet to rest anywhere.
   *
   * The saved rolls are left exactly where the player left them. A strip that
   * sprang open every time a total went away would be a strip that opens
   * itself once per throw.
   */
  fun resultGone(): BottomEdge = copy(result = SheetRest.Down)
}
