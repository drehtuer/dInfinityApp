package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.feature.designer.DesignerPresenter
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.saved.Editing
import de.drehtuer.dinfinity.feature.saved.EditorPresenter
import de.drehtuer.dinfinity.feature.saved.GroupPresenter
import de.drehtuer.dinfinity.feature.saved.ImportPresenter
import de.drehtuer.dinfinity.feature.saved.SavedPresenter
import de.drehtuer.dinfinity.feature.sets.SetDetailPresenter
import de.drehtuer.dinfinity.feature.sets.SetsPresenter
import de.drehtuer.dinfinity.feature.stats.HistoryPresenter
import de.drehtuer.dinfinity.feature.stats.SavedStatsPresenter
import de.drehtuer.dinfinity.feature.stats.SessionsPresenter
import de.drehtuer.dinfinity.feature.stats.StatsPresenter
import de.drehtuer.dinfinity.feature.tables.TablesPresenter

/**
 * How to build every screen's state — all of them, in one place
 * (`docs/TODO.md`, 4.10).
 *
 * **This exists so that a screen cannot be built and then never plugged in.**
 * It happened: the sessions screen was finished, tested and unreachable for a
 * whole step, because `MainActivity` never passed its presenter and the app
 * drew a placeholder instead — which is exactly what a placeholder is supposed
 * to look like, so nothing looked wrong.
 *
 * Every field is required and none has a default. Adding a screen means adding
 * a field here, and then the activity does not compile until it says how to
 * build one. That is the whole point: the failure moves from "a placeholder
 * nobody notices" to "the build stops".
 *
 * `DInfinityApp` still takes this as **nullable**, because drawing placeholders
 * is a real mode rather than an oversight: a Robolectric test of the
 * navigation graph has no GPU and no physics engine, and the graph's own rules
 * are worth testing without any of this. What that mode is not allowed to be
 * is *partial* — half a set of screens is how the last one went missing.
 *
 * A factory per screen rather than an instance, because a presenter belongs to
 * a visit: it owns a scope, and some of them own a physics world
 * (`docs/architecture.md`, decision 49).
 */
data class Presenters(
  /** The tray. Owns a roll, and in the drawing case a scene and a world. */
  val roll: () -> RollPresenter,
  /** The outcome graph, built from its arguments and nothing else. */
  val graph: () -> GraphMachine,
  /** The saved-roll list, and the strip on the tray. */
  val savedRolls: () -> SavedPresenter,
  /** The saved-roll editor, on an existing roll or a new one ([Editing]). */
  val savedRollEditor: (Editing) -> EditorPresenter,
  /** The group sheet, which is the same sheet from the list and the editor. */
  val savedGroups: () -> GroupPresenter,
  /** A collection arriving from a file. */
  val collectionImport: () -> ImportPresenter,
  /** Past rolls. */
  val history: () -> HistoryPresenter,
  /** What every die has done. */
  val statistics: () -> StatsPresenter,
  /** The buckets rolls are filed under. */
  val sessions: () -> SessionsPresenter,
  /** What each saved roll has come to, against what it should. */
  val savedStatistics: () -> SavedStatsPresenter,
  /** What is installed. */
  val diceSets: () -> SetsPresenter,
  /** Which table the dice are thrown onto (`docs/tables.md`). */
  val tables: () -> TablesPresenter,
  /** Drawing the faces of a die (`docs/face-designer.md`). */
  val faceDesigner: () -> DesignerPresenter,
  /**
   * One set's details.
   *
   * Takes the id and what to do when the set turns out to be gone, because a
   * set can be removed from the screen that is showing it.
   */
  val diceSet: (String, () -> Unit) -> SetDetailPresenter,
)
