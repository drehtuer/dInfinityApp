package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.NewSet
import de.drehtuer.dinfinity.designer.PersonalSets
import de.drehtuer.dinfinity.feature.designer.DesignerSets
import de.drehtuer.dinfinity.feature.designer.SaveResult
import de.drehtuer.dinfinity.feature.designer.WritableSet
import de.drehtuer.dinfinity.feature.sets.SetLibrary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Turning the drawings into an installed dice set, which is what **Save to
 * set** and **Roll it** both need (`docs/face-designer.md`, "Save to set").
 *
 * It lives in `:app` for the reason `SavedDrafts` does: it is the platform
 * half of something `feature/designer` should not have to carry — a disk, a
 * dispatcher, and the library that knows which packages are on the phone.
 *
 * **The order is the whole of it**, and getting it wrong is what made a
 * designed die roll as a plain one:
 *
 * 1. the draft on the canvas is written to disk **synchronously**, because
 *    every other write goes to a background scope and the package is built
 *    from the files rather than from what the screen is holding;
 * 2. the personal packages are rebuilt from their drawings and re-scanned, which
 *    is what [SetLibrary.all] does and the only thing that does it — this is
 *    the step the designer never took, so a player who drew and pressed Roll
 *    without ever visiting the sets list rolled against a catalogue in which
 *    `mine` did not exist;
 * 3. the die is named **in the set that now carries it**, so the tray resolves
 *    the die with the atlas on it rather than the plain one of that shape.
 *
 * **Every personal set is writable**: "My dice", and every set somebody named
 * in the sheet ([PersonalSets]). A drawing saved into a named set is written
 * into that set's own drafts as well as the designer's, so the set keeps the
 * drawing it was given while the canvas goes on being the canvas
 * (`docs/architecture.md`, decision 79).
 */
internal class DrawnSets(
  private val library: SetLibrary,
  private val store: DraftStore,
  /**
   * What a formula resolves against, read **after** the rebuild rather than
   * captured: the whole point of the rebuild is that it changes the answer.
   */
  private val catalogue: () -> DiceCatalog,
  private val io: CoroutineDispatcher,
  /** Every personal set, and where a new one is made. */
  private val personal: PersonalSets,
) : DesignerSets {
  /** Read every time, because naming a set in the sheet is what adds one. */
  override val writable: List<WritableSet>
    get() = personal.all().map { WritableSet(it.id, it.name) }

  override suspend fun save(
    setId: String,
    draft: Draft,
  ): SaveResult {
    val into = writable.firstOrNull { it.id == setId } ?: return SaveResult.Refused
    val set = personal.find(setId) ?: return SaveResult.Refused
    withContext(io) {
      store.save(draft)
      // "My dice" is built from the designer's own drafts, so the write above
      // is the whole of it; a named set keeps a copy of its own.
      if (setId != DiceSet.PERSONAL_ID) set.keep(draft)
    }
    // Reading the library is what builds the package: a personal set is a
    // view of its drawings, written just before the folder is read and never
    // at any other time (`SetLibrary`, `MineSets.bringUpToDate`).
    library.all()
    val built = catalogue().set(setId)
    return when {
      built != null && (built.dice.isNotEmpty() || built.tables.isNotEmpty()) ->
        SaveResult.Saved(into, rollable = spellingOf(draft.die, catalogue(), preferred = setId))
      // No package and no drawings is not a failure — it is a set with
      // nothing in it, and saying "could not be written" of that would be a
      // lie about a disk that worked perfectly.
      withContext(io) { !set.drawn() } -> SaveResult.Blank
      else -> SaveResult.Refused
    }
  }

  override suspend fun create(
    name: String,
    draft: Draft,
  ): SaveResult =
    when (val made = withContext(io) { personal.create(name) }) {
      is NewSet.Made -> save(made.id, draft)
      else -> SaveResult.NotMade(made)
    }
}
