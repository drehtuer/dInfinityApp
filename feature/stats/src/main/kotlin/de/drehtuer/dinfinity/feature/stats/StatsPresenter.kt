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
import de.drehtuer.dinfinity.core.stats.PooledDie
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
        state = state.copy(all = dice.map(::rowOf), loaded = true)
        // The open die's own numbers change when a roll lands too.
        state.selected?.let { open -> select(open.setId, open.dieId) }
      }
    }
  }

  /**
   * A die was chosen from the list.
   *
   * A rolled-up row has no set of its own, so what it watches is every die of
   * that many sides rather than one of them (design option `5c`).
   */
  fun select(
    setId: String,
    dieId: String,
  ) {
    val row = state.dice.firstOrNull { it.setId == setId && it.dieId == dieId } ?: return
    watching?.cancel()
    val tallies = if (row.acrossSets) statistics.facesForSides(row.summary.sides) else statistics.faces(setId, dieId)
    watching =
      scope.launch {
        tallies.collect { counted ->
          state = state.copy(selected = detailOf(row, counted))
        }
      }
  }

  /**
   * Show one set's dice, or every set's (design option `5b`).
   *
   * Choosing a set leaves the roll-up, because the two answer different
   * questions and a screen showing both would be showing neither.
   */
  fun filterBy(setId: String?) {
    state = state.copy(setFilter = setId, acrossSets = false, selected = null)
  }

  /** Roll every set's dice of each kind together, or stop (design option `5c`). */
  fun rollUp(across: Boolean) {
    state = state.copy(acrossSets = across, setFilter = null, selected = null)
  }

  /**
   * What every die has done, as a file (`docs/statistics.md`, "Export and
   * reset").
   *
   * The faces are read once, here, rather than watched: the screen only ever
   * loads the open die's histogram, and a file is about all of them.
   *
   * @param to what to do with the file. The screen hands it up to the
   *   application, which is where a share sheet lives.
   */
  fun export(
    format: ExportFormat,
    to: (ExportFile) -> Unit,
  ) {
    val dice = state.recorded
    scope.launch {
      to(
        DiceExport.of(
          dice = dice.map(DieRow::summary),
          faces = statistics.allFaces(),
          format = format,
          called = state.setFilter ?: "dice",
        ),
      )
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
    if (row.acrossSets) {
      val bars = FaceHistogram.ofPool(row.pool, tallies)
      return DieDetail(row = row, bars = bars, extremes = FaceHistogram.extremes(bars.map(FaceBar::value), tallies))
    }
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
  /**
   * The dice this row stands for, when it is a roll-up across sets.
   *
   * Empty for an ordinary row, which stands for one die. Non-empty is what
   * makes a row a pool, and what the pooled fair line is weighted by
   * (design option `5c`).
   */
  val pool: List<PooledDie> = emptyList(),
) {
  val setId: String get() = summary.setId
  val dieId: String get() = summary.dieId

  /** True when this row is every die of its kind rather than one of them. */
  val acrossSets: Boolean get() = pool.isNotEmpty()
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
   * For one die: its set is gone, so the only thing that can be said about its
   * faces is what has come up. For a roll-up: at least one of the dice in it
   * is from a set that is gone, so its throws are counted in the bars while
   * its faces are missing from the line.
   *
   * Either way a player should be told before they read anything into the
   * shape.
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
  val all: List<DieRow> = emptyList(),
  val selected: DieDetail? = null,
  val confirming: Reset? = null,
  val loaded: Boolean = false,
  val setFilter: String? = null,
  val acrossSets: Boolean = false,
) {
  /** Every set that has a record, in the order the list shows them (design `5b`). */
  val sets: List<String> get() = all.map(DieRow::setId).distinct().sorted()

  /**
   * The rows the list draws: filtered by set, and rolled up across sets when
   * that is what was asked for (design options `5b` and `5c`).
   *
   * Rolling up and filtering by set are deliberately exclusive. "All my d20s,
   * but only the brass ones" is a sentence, but it is the same thing as
   * looking at the brass d20 — and offering it would put two controls on
   * screen that cancel each other out.
   */
  val dice: List<DieRow> get() = if (acrossSets) pooled() else all.filter { setFilter == null || it.setId == setFilter }

  /**
   * The dice a file would carry: the real ones, and never the roll-up.
   *
   * A pooled row stands for every d20 in every set at once and so belongs to
   * no set — which is right on a screen and wrong in a file, where the set is
   * what makes a record checkable. The per-die rows are also the ones a
   * roll-up can be recomputed from; the reverse is not true, so the file keeps
   * the half that can give back the other (`docs/statistics.md`).
   *
   * A set filter *is* honoured, because that one hides dice rather than
   * merging them.
   */
  val recorded: List<DieRow> get() = all.filter { setFilter == null || it.setId == setFilter }

  /** True when nothing has ever been rolled, rather than nothing has arrived. */
  val empty: Boolean get() = loaded && all.isEmpty()

  /** True when a filter is hiding everything there is, which is not the same as having nothing. */
  val filteredToNothing: Boolean get() = loaded && all.isNotEmpty() && dice.isEmpty()

  /**
   * One row per die *type*, summed across every set that has one.
   *
   * Throws, totals and squares add up, so the mean and the variance of the
   * pool are the mean and the variance of everything thrown. **Streaks do
   * not.** A streak is a run within one die's own sequence, and two dice's
   * runs do not join end to end, so a rolled-up row claims none.
   */
  private fun pooled(): List<DieRow> =
    all
      .groupBy { it.summary.sides }
      .map { (sides, rows) -> rolledUp(sides, rows) }
      .sortedBy { it.summary.sides }
}

/** One die type, summed across the sets that define it (design option `5c`). */
private fun rolledUp(
  sides: Int,
  rows: List<DieRow>,
): DieRow =
  DieRow(
    summary =
      DieSummary(
        setId = "",
        dieId = "d$sides",
        sides = sides,
        throws = rows.sumOf { it.summary.throws },
        sum = rows.sumOf { it.summary.sum },
        sumOfSquares = rows.sumOf { it.summary.sumOfSquares },
        lastRolledAtEpochMs = rows.maxOf { it.summary.lastRolledAtEpochMs },
      ),
    name = "d$sides",
    // The fair line for a pool is weighted by how often each die was thrown,
    // so the values cannot be flattened into one list here (`FaceHistogram`).
    values = emptyList(),
    // Every contributor, not any: a pool with one die whose set has gone is a
    // pool whose fair line is built from the rest while the bars count all of
    // them. That is exactly what `fairLineIsAGuess` exists to say.
    installed = rows.all(DieRow::installed),
    pool = rows.map { PooledDie(values = it.values, throws = it.summary.throws) },
  )
