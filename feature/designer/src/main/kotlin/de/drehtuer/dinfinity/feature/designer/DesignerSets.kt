package de.drehtuer.dinfinity.feature.designer

import de.drehtuer.dinfinity.designer.Draft

/**
 * A set the designer may write a drawing into
 * (`docs/face-designer.md`, "Save to set").
 *
 * **Writable is a small word for a real rule**: a package somebody installed
 * belongs to whoever wrote it, and the app never edits one. What is writable
 * is a set this phone built — "My dice" today, and any other personal set once
 * there can be more than one (`docs/TODO.md`, 4.6).
 */
data class WritableSet(
  val id: String,
  val name: String,
)

/**
 * What came of saving (`docs/face-designer.md`, "Save to set").
 *
 * A refusal is a *reason* rather than a silent nothing, for the reason every
 * other refusal in this app is: the save was asked for on purpose and the
 * person is owed an answer.
 */
sealed interface SaveResult {
  /**
   * The drawings are in [set], and the die that was on the canvas is now
   * [rollable] — the set-qualified formula that throws **the drawing** rather
   * than the plain die of that shape, or null for a die notation cannot name
   * (`docs/architecture.md`, decision 31).
   */
  data class Saved(
    val set: WritableSet,
    val rollable: String?,
  ) : SaveResult

  /** Nothing has been drawn, so there is no set to write. */
  data object Blank : SaveResult

  /** The package could not be written, or did not validate. Nothing was left half-done. */
  data object Refused : SaveResult
}

/**
 * Where the drawings become a dice set.
 *
 * An interface for the reason [de.drehtuer.dinfinity.designer.Drafts] is one:
 * building the personal package rasterises every drawn face and rescans a
 * folder, and *which thread that happens on* is a wiring decision rather than
 * something the presenter should carry (`docs/architecture.md`, "Threading").
 * It is also the seam that lets the whole of "Save to set" and the whole of
 * "Roll it naming the drawing" be tested without a disk.
 *
 * **The draft goes in with the call.** The presenter holds the drawing that is
 * on the canvas and the store is written to in the background after every
 * stroke, so handing the draft over is what makes "save and then build" one
 * ordered thing instead of a race with the last stroke's write.
 */
interface DesignerSets {
  /**
   * The sets a drawing may be saved into, the first of them the one the sheet
   * opens on. Empty means there is nowhere to save and no Save is offered —
   * which is what a designer with nothing wired behind it is.
   */
  val writable: List<WritableSet>

  /** Writes [draft] down and builds [setId] out of every drawing, or says why not. */
  suspend fun save(
    setId: String,
    draft: Draft,
  ): SaveResult

  companion object {
    /** Nowhere to save. What a test uses, and what a screen with no library behind it gets. */
    val NONE: DesignerSets =
      object : DesignerSets {
        override val writable: List<WritableSet> = emptyList()

        override suspend fun save(
          setId: String,
          draft: Draft,
        ): SaveResult = SaveResult.Blank
      }
  }
}
