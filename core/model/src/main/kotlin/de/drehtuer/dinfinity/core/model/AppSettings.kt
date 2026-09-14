package de.drehtuer.dinfinity.core.model

/**
 * Everything the player can change in Settings that the rest of the app reads.
 *
 * One value object rather than a preference per reader: a screen observes the
 * whole thing and recomposes once, and adding a setting does not mean adding
 * another stream to plumb through.
 */
data class AppSettings(
  val accentColor: AccentColor = AccentColor.Default,
  /**
   * Light, dark, or whatever the phone is doing
   * (`design/dInfinity.dc.html`, option 1q).
   */
  val appearance: Appearance = Appearance.System,
  /**
   * Whether shaking the phone throws the dice
   * (`docs/physics-and-rendering.md`, "Shake input").
   *
   * On by default, because it is the thing that makes this a dice app rather
   * than a number generator. Off means the sensors are never registered at
   * all, which is also the only setting here that saves any power.
   */
  val shakeToRoll: Boolean = true,
  /**
   * Which way division rounds unless a throw says otherwise
   * (`docs/dice-notation.md`, "Division rounding").
   *
   * Down by default, which is what most game rules say. The result sheet can
   * re-round the throw in front of you, and that override is not remembered.
   */
  val rounding: Rounding = Rounding.Default,
  /**
   * Roll without drawing the dice (`design/dInfinity.dc.html`, option 1z).
   *
   * Off by default and **only ever changed here**: a roll that silently
   * stopped rendering because the battery dipped would be a surprise in the
   * middle of a game (`docs/architecture.md`, decision 16). It is the same
   * simulation either way, so the same seed gives the same faces — what is
   * saved is the drawing.
   */
  val powerSaving: Boolean = false,
  /**
   * Whether the player has been past the first-launch screen
   * (`design/dInfinity.dc.html`, option 9a).
   *
   * Not a setting and not shown in Settings — it lives here because it is the
   * one other thing the app remembers between launches, and a second
   * repository for one boolean would be a second file to keep in step.
   */
  val welcomeSeen: Boolean = false,
  /**
   * The group of saved rolls the app is currently in
   * (`docs/dice-notation.md`, "Saved rolls").
   *
   * A preference in the sense that matters: it outlives the screen that
   * changed it, and it is what the home strip and the default statistics
   * session follow.
   */
  val activeGroupId: String = SavedRollGroup.UNFILED_ID,
  /**
   * The session new rolls are filed under (`docs/statistics.md`, per session).
   *
   * A preference in the sense that matters: it outlives the screen that chose
   * it, the roll screen reads it on every throw, and the history reads it to
   * know whether its session headings mean anything.
   */
  val activeSessionId: String = DEFAULT_SESSION_ID,
) {
  companion object {
    /**
     * The session a roll belongs to when nothing else says.
     *
     * The same id the sessions table's first row carries, so the rolls made
     * before there were sessions belong to the first session rather than to
     * nothing. `SessionRepository.DEFAULT_ID` is the other end of it; the
     * model cannot see `data`, so the string is written twice and asserted
     * equal in `RollRecordingTest`.
     */
    const val DEFAULT_SESSION_ID: String = "default"
  }
}
