package de.drehtuer.dinfinity.feature.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.stats.DieSummary
import de.drehtuer.dinfinity.core.stats.Extremes
import de.drehtuer.dinfinity.core.stats.FaceBar
import de.drehtuer.dinfinity.core.stats.FaceHistogram
import de.drehtuer.dinfinity.core.stats.FaceTally
import de.drehtuer.dinfinity.data.DieStatisticsRepository
import de.drehtuer.dinfinity.data.StatisticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What every die has done (`design/dInfinity.dc.html`, option 1w;
 * `docs/statistics.md`).
 *
 * The list is every die ever thrown, most recently used first, and choosing
 * one opens its histogram. That order is deliberate: a player comes here about
 * a die they have just been rolling.
 *
 * A die's **values** come from the installed set, because a die labelled
 * `1,2,3,1,2,3` is a d3 and a histogram drawn against a sixth would show it as
 * twice as lucky as it is on every value. When the set has been uninstalled
 * since, the values are taken from what has actually come up and the screen
 * says the line is a guess — the alternative is either hiding the die's own
 * record or drawing it against a line that is wrong without saying so.
 *
 * @param catalog the installed sets, for the faces and the names.
 */
class StatsPresenter(
  private val statistics: DieStatisticsRepository,
  private val writer: StatisticsRepository,
  private val catalog: DiceCatalog,
  private val scope: CoroutineScope,
) {
  /** What the screen draws. */
  var state: StatsState by mutableStateOf(StatsState())
    private set

  private var watching: Job? = null

  init {
    scope.launch {
      statistics.dice.collect { dice ->
        state = state.copy(dice = dice.map(::rowOf), loaded = true)
        // The open die's own numbers change when a roll lands too.
        state.selected?.let { open -> select(open.setId, open.dieId) }
      }
    }
  }

  /** A die was chosen from the list. */
  fun select(
    setId: String,
    dieId: String,
  ) {
    val row = state.dice.firstOrNull { it.setId == setId && it.dieId == dieId } ?: return
    watching?.cancel()
    watching =
      scope.launch {
        statistics.faces(setId, dieId).collect { tallies ->
          state = state.copy(selected = detailOf(row, tallies))
        }
      }
  }

  /** Back to the list. */
  fun close() {
    watching?.cancel()
    watching = null
    state = state.copy(selected = null, confirming = null)
  }

  /** A reset was asked for, and has not been confirmed yet. */
  fun confirm(reset: Reset?) {
    state = state.copy(confirming = reset)
  }

  /**
   * Does what was confirmed.
   *
   * Nothing is reset without passing through here, and nothing reaches here
   * without a confirmation: forgetting a campaign's worth of natural 20s by
   * mis-tapping is not a thing that should be possible
   * (`docs/statistics.md`, "Export and reset").
   */
  fun reset() {
    val what = state.confirming ?: return
    state = state.copy(confirming = null)
    scope.launch {
      when (what) {
        is Reset.OneDie -> writer.resetDie(what.setId, what.dieId)
        Reset.Everything -> writer.resetEverything()
      }
      close()
    }
  }

  private fun rowOf(summary: DieSummary): DieRow {
    val die = catalog.set(summary.setId)?.dice?.firstOrNull { it.id == summary.dieId }
    return DieRow(
      summary = summary,
      name = die?.id ?: summary.dieId,
      // A set that has been uninstalled since still has a record, and the
      // record is the player's.
      values = die?.faces?.map(Face::value).orEmpty(),
      installed = die != null,
    )
  }

  private fun detailOf(
    row: DieRow,
    tallies: List<FaceTally>,
  ): DieDetail {
    // When nobody can say what the die's faces were, the values it has
    // actually shown are the best available guess — and the screen says so.
    val values = row.values.ifEmpty { tallies.map(FaceTally::faceValue) }
    return DieDetail(
      row = row,
      bars = FaceHistogram.of(values, tallies),
      extremes = FaceHistogram.extremes(values, tallies),
    )
  }
}

/** One die in the list. */
data class DieRow(
  val summary: DieSummary,
  val name: String,
  val values: List<Int> = emptyList(),
  /** False when the set it came from is not installed any more. */
  val installed: Boolean = true,
) {
  val setId: String get() = summary.setId
  val dieId: String get() = summary.dieId
}

/** One die, opened. */
data class DieDetail(
  val row: DieRow,
  val bars: List<FaceBar> = emptyList(),
  val extremes: Extremes = Extremes(),
) {
  val setId: String get() = row.setId
  val dieId: String get() = row.dieId

  /**
   * True when the fair line is a guess rather than a fact.
   *
   * The set is gone, so the only thing that can be said about the die's faces
   * is what has come up. A player should be told that before they read
   * anything into the shape.
   */
  val fairLineIsAGuess: Boolean get() = !row.installed
}

/** What a reset would forget. */
sealed interface Reset {
  data class OneDie(
    val setId: String,
    val dieId: String,
    val name: String,
  ) : Reset

  data object Everything : Reset
}

/**
 * What the statistics screen is showing.
 *
 * @param loaded false until the database has answered, so an empty list is not
 *   drawn as "you have never rolled anything".
 */
data class StatsState(
  val dice: List<DieRow> = emptyList(),
  val selected: DieDetail? = null,
  val confirming: Reset? = null,
  val loaded: Boolean = false,
) {
  /** True when nothing has ever been rolled, rather than nothing has arrived. */
  val empty: Boolean get() = loaded && dice.isEmpty()
}
