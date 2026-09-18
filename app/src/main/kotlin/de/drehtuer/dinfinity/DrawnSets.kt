package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.MinePackage
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
 * 2. the personal package is rebuilt from every drawing and re-scanned, which
 *    is what [SetLibrary.all] does and the only thing that does it — this is
 *    the step the designer never took, so a player who drew and pressed Roll
 *    without ever visiting the sets list rolled against a catalogue in which
 *    `mine` did not exist;
 * 3. the die is named **in the set that now carries it**, so the tray resolves
 *    the die with the atlas on it rather than the plain one of that shape.
 *
 * **There is one writable set today.** `MinePackage.ID` is fixed to
 * [DiceSet.PERSONAL_ID] and `MineSets` is built on one folder, so "the sets
 * that can be written to" is a list of one. It is a list all the same, because
 * that is the question being answered and the second personal set should not
 * need the sheet rebuilt (`docs/TODO.md`, 4.6).
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
) : DesignerSets {
  override val writable: List<WritableSet> = listOf(WritableSet(DiceSet.PERSONAL_ID, MinePackage.NAME))

  override suspend fun save(
    setId: String,
    draft: Draft,
  ): SaveResult {
    val into = writable.firstOrNull { it.id == setId } ?: return SaveResult.Refused
    withContext(io) { store.save(draft) }
    // Reading the library is what builds the package: "My dice" is a view of
    // the drawings, written just before the folder is read and never at any
    // other time (`SetLibrary`, `MineSets.bringUpToDate`).
    library.all()
    val built = catalogue().set(setId)
    return when {
      built != null -> SaveResult.Saved(into, rollable = spellingOf(draft.die, catalogue(), preferred = setId))
      // No package and no drawings is not a failure — it is a phone with
      // nothing on it, and saying "could not be written" of that would be a
      // lie about a disk that worked perfectly.
      withContext(io) { store.known().isEmpty() } -> SaveResult.Blank
      else -> SaveResult.Refused
    }
  }
}
