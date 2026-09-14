package de.drehtuer.dinfinity.feature.sets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the details screen is showing. */
data class SetDetailState(
  val row: SetRow? = null,
  val loaded: Boolean = false,
  val gone: Boolean = false,
) {
  /**
   * True once the package has been looked for and not found.
   *
   * A separate thing from `row == null`, which is also what "not looked yet"
   * looks like: the screen has to tell "this set was removed" from "still
   * reading the folder", and only one of them is worth a message.
   */
  val missing: Boolean get() = loaded && row == null
}

/**
 * One dice set, in detail (`design/dInfinity.dc.html`, options `6a` and `6b`;
 * `docs/dice-sets.md`).
 *
 * Opened with an id rather than handed a row, because the way here is a
 * navigation route and a route carries strings. That costs one folder read,
 * which is the same read the list did — and it buys a screen that survives the
 * back stack being restored, where a row passed in memory would not.
 *
 * **The report and the dice are the same screen.** A package that no longer
 * validates shows what is wrong with it where a valid one shows its dice
 * (`6b`), because they answer the same question — what is in this set — and
 * one of the answers is "nothing yet, and here is why".
 *
 * @param id the folder's name, which is how everything addresses a set.
 * @param onGone called once the set has been removed, so the screen showing it
 *   can be left. Where that goes is the navigation graph's. No default: a
 *   screen that removes a set and then stays is a page about a folder that is
 *   not there, and that is too easy to get by forgetting an argument.
 */
class SetDetailPresenter(
  private val id: String,
  private val library: SetLibrary,
  private val scope: CoroutineScope,
  private val onGone: () -> Unit,
) {
  /** What the screen draws. */
  var state: SetDetailState by mutableStateOf(SetDetailState())
    private set

  init {
    refresh()
  }

  /** Reads this one package again. */
  fun refresh() {
    scope.launch {
      state = SetDetailState(row = library.one(id), loaded = true)
    }
  }

  /**
   * Switches the set on or off.
   *
   * The folder and every statistic recorded against its dice are untouched,
   * which is what makes this the reversible half of what this screen offers.
   */
  fun setEnabled(enabled: Boolean) {
    val row = state.row ?: return
    scope.launch {
      library.setEnabled(row, enabled)
      refresh()
    }
  }

  /**
   * Takes the set off the phone, then leaves the screen.
   *
   * [onGone] rather than a flag the screen watches: a details screen for a set
   * that is not installed any more has nothing to show, and the honest thing
   * is to not be there.
   */
  fun remove() {
    val row = state.row?.takeUnless { it.bundled } ?: return
    scope.launch {
      library.remove(row)
      state = state.copy(row = null, gone = true)
      onGone()
    }
  }
}
