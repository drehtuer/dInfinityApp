package de.drehtuer.dinfinity.feature.saved

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.collection.CollectionProblem
import de.drehtuer.dinfinity.core.collection.CollectionReader
import de.drehtuer.dinfinity.core.collection.CollectionResult
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.CollectionImporter
import de.drehtuer.dinfinity.data.ImportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Taking a collection in (`design/dInfinity.dc.html`, options 9f and 9g).
 *
 * Two steps, and the order of them is the point. A file is **read** first, by
 * code that cannot write anything, and only a file that came back sound is
 * offered to the importer. So every way this can fail has already happened
 * before anything is at risk.
 *
 * @param installedSets the ids of the sets that are installed, so a formula
 *   naming one that is not can be flagged without being refused. A saved roll
 *   whose dice are gone is a warning on the list, not a reason to turn the
 *   collection away (`docs/dice-notation.md`).
 */
class ImportPresenter(
  private val importer: CollectionImporter,
  private val catalog: DiceCatalog,
  private val scope: CoroutineScope,
  private val unfiledName: String,
) {
  /** What the screen draws. */
  var state: ImportState by mutableStateOf(ImportState.Waiting)
    private set

  /**
   * A file was chosen, and this is what was in it.
   *
   * @param text the whole file. Reading it is the caller's, because a file
   *   arrives as a content URI and that is Android's business rather than a
   *   presenter's.
   */
  fun offer(text: String) {
    state = ImportState.Reading
    when (val read = CollectionReader.read(text, catalog.installed.map { it.id }.toSet())) {
      is CollectionResult.Rejected -> state = ImportState.Unreadable(read.errors)
      is CollectionResult.Loaded ->
        scope.launch {
          state =
            when (val result = importer.import(read.collection, unfiledName)) {
              is ImportResult.Refused -> ImportState.Clash(result.clash)
              is ImportResult.Imported ->
                ImportState.Imported(
                  groups = result.groups,
                  rolls = result.rolls,
                  name = read.collection.name,
                  warnings = read.warnings,
                )
            }
        }
    }
  }

  /** The file could not be opened at all — it was moved, or permission was withdrawn. */
  fun unopenable(why: String) {
    state = ImportState.Unopenable(why)
  }

  /** Back to the beginning, to try another file. */
  fun again() {
    state = ImportState.Waiting
  }
}

/**
 * What the import screen is showing.
 *
 * A sealed type rather than a bag of nullable fields, because the states are
 * genuinely exclusive: a file is either waiting to be chosen, being read,
 * refused for one of two quite different reasons, or in.
 */
sealed interface ImportState {
  /** Nothing chosen yet (design option 9f). */
  data object Waiting : ImportState

  /** A file is being read. Brief, but not instant for five hundred rolls. */
  data object Reading : ImportState

  /** The file could not be opened — moved, or the permission withdrawn. */
  data class Unopenable(
    val why: String,
  ) : ImportState

  /**
   * The file is not a collection this app can read, and this is everything
   * wrong with it.
   */
  data class Unreadable(
    val problems: List<CollectionProblem>,
  ) : ImportState

  /**
   * The file reads, but a group in it is already here (design option 6e).
   *
   * Its own state rather than another kind of problem, because it is the one
   * failure that is not about the file being wrong: the file is fine, and so
   * is what is already saved. One of the two names has to change.
   */
  data class Clash(
    val name: String,
  ) : ImportState

  /** It is in (design option 9g). */
  data class Imported(
    val groups: Int,
    val rolls: Int,
    val name: String,
    val warnings: List<CollectionProblem> = emptyList(),
  ) : ImportState
}
