package de.drehtuer.dinfinity.feature.sets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.dicesets.install.InstalledPackage
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageMeta
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What a row's one line under the name is about.
 *
 * An enum rather than a string chosen inside the composable, because the
 * *order* is a rule and rules are worth testing without a screen: a package
 * that will not load says so before anything else, since nothing else about it
 * matters until that is fixed, and "switched off" comes before "built in"
 * because it is the one the player just did.
 */
enum class SetStatus {
  /** Did not pass validation on this reading. The remedy is an update (`6b`). */
  Broken,

  /** The player switched it off. The remedy is a tap (`5a`). */
  Off,

  /** The set that ships inside the app. */
  Bundled,

  /** Installed, on, and valid: the line says how many dice it has. */
  Ready,
}

/**
 * One row of the dice-set list (`design/dInfinity.dc.html`, option `5a`).
 *
 * A view of a package rather than the package itself, for one reason worth
 * naming: the **bundled set has no folder**. It lives inside the APK, it cannot
 * be removed and it cannot be switched off, and a row shape that insisted on a
 * folder would have to invent one for it. So a folder is what a row may not
 * have, and [bundled] is what says why.
 *
 * It carries the set and the report rather than counts taken from them, because
 * the details screen wants both and re-reading the disk on a tap would be a
 * validation pass per row (`6a`, `6b`).
 */
data class SetRow(
  val id: String,
  val name: String,
  val set: DiceSet?,
  val report: List<ValidationMessage>,
  val meta: PackageMeta,
  val folder: File?,
  val enabled: Boolean,
  val bundled: Boolean = false,
) {
  /** True when the package did not pass validation on this reading (`6b`). */
  val broken: Boolean get() = set == null

  /** How many things went wrong. The list itself belongs to the details screen (`6b`). */
  val problems: Int get() = report.size

  /** How many dice it defines. A broken package defines nothing usable. */
  val dice: Int get() = set?.dice?.size ?: 0

  /** The version it declares, where it is readable enough to declare one. */
  val version: String? get() = set?.version ?: meta.version

  /**
   * The one thing the row says under the name.
   *
   * Ordered by what the player can do something about; see [SetStatus].
   */
  val status: SetStatus
    get() =
      when {
        broken -> SetStatus.Broken
        !enabled -> SetStatus.Off
        bundled -> SetStatus.Bundled
        else -> SetStatus.Ready
      }

  /**
   * Whether its dice are actually available to a formula.
   *
   * A set can fail to be usable two ways, and the row says which: switched off
   * by the player, or no longer valid. They are not the same thing and the
   * remedies are opposite — one is a tap, the other is an update.
   */
  val usable: Boolean get() = enabled && !broken

  companion object {
    /** A package read off the disk, with the player's opinion of it. */
    fun of(
      pack: InstalledPackage,
      enabled: Boolean,
    ): SetRow =
      SetRow(
        id = pack.id,
        name = (pack as? InstalledPackage.Ready)?.set?.name ?: pack.id,
        set = (pack as? InstalledPackage.Ready)?.set,
        report = (pack as? InstalledPackage.Broken)?.report.orEmpty(),
        meta = pack.meta,
        folder = pack.folder,
        enabled = enabled,
      )

    /** The set that ships inside the app: no folder, always on, never removable. */
    fun bundled(set: DiceSet): SetRow =
      SetRow(
        id = set.id,
        name = set.name,
        set = set,
        report = emptyList(),
        meta = PackageMeta.Unknown,
        folder = null,
        enabled = true,
        bundled = true,
      )
  }
}

/** What the dice-set screen is showing. */
data class SetsState(
  val sets: List<SetRow> = emptyList(),
  val acting: SetRow? = null,
  val loaded: Boolean = false,
) {
  /**
   * True once the disk has been read and found to hold nothing.
   *
   * The bundled row is always there, so "nothing installed" is one row rather
   * than none — and saying so needs [loaded], or the message would flash up on
   * every visit before the first reading finishes.
   *
   * It adds a note rather than replacing the list: the bundled set is a row
   * like any other and there is always something to show.
   */
  val empty: Boolean get() = loaded && sets.size <= 1
}

/**
 * The dice sets that are installed, and what may be done to them
 * (`docs/dice-sets.md`; `design/dInfinity.dc.html`, option `5a`).
 *
 * Two sources, joined here and nowhere else: [InstalledSets] says what is on
 * disk and whether it still validates, and [InstalledSetRepository] says what
 * the player has switched off. Neither knows about the other, which is what
 * lets a folder appear or vanish without the app being asked and still be
 * right on the next reading.
 *
 * **The bundled set is handed in rather than reached for**, so this module
 * never learns that `dicesets:builtin` exists — the same reason the roll screen
 * is handed a catalogue instead of building one.
 *
 * Reading the folder means validating every package in it, which is TOML
 * parsing and image headers over real files. That is [io]'s job, not the main
 * thread's (`docs/architecture.md`, "Threading").
 *
 * @param bundled the set that ships inside the app.
 * @param io where the disk is touched.
 */
class SetsPresenter(
  private val bundled: DiceSet,
  private val installed: InstalledSets,
  private val registry: InstalledSetRepository,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher = Dispatchers.IO,
) {
  /** What the screen draws. */
  var state: SetsState by mutableStateOf(SetsState())
    private set

  init {
    refresh()
  }

  /**
   * Reads the disk again.
   *
   * Called when the screen opens and after anything that changes what is on
   * it. There is no watching: a `dicesets/` folder does not change while
   * nobody is installing anything, and a file observer over a tree of packages
   * would cost more than the tap it saves.
   */
  fun refresh() {
    scope.launch {
      val packages = withContext(io) { installed.scan() }
      // Every reading is also the moment the registry is reconciled with the
      // disk. A row about a folder that has gone would switch a *new* package
      // off the moment somebody installed one under the same id.
      registry.keepOnly(packages.map(InstalledPackage::id))
      val off = registry.disabled()
      state = state.copy(sets = rows(packages, off), loaded = true)
    }
  }

  /** A long press. The sheet that offers disable and remove opens on this (`5a`). */
  fun act(on: SetRow?) {
    // The bundled set has nothing the sheet could offer, so it does not open.
    state = state.copy(acting = on?.takeUnless { it.bundled })
  }

  /**
   * Switches a set on or off.
   *
   * The folder is untouched, and so is every statistic recorded against the
   * set's dice. That is what makes this the reversible half of the sheet.
   */
  fun setEnabled(
    row: SetRow,
    enabled: Boolean,
  ) {
    if (row.bundled) return
    state = state.copy(acting = null)
    scope.launch {
      registry.setEnabled(row.id, enabled)
      refresh()
    }
  }

  /**
   * Takes a set off the phone: the folder, then the registry row.
   *
   * The folder first, because a row about a folder that is still there is a
   * set the player was told had gone and has not; the other order leaves a row
   * that the next reading prunes anyway.
   *
   * Statistics are kept. They are keyed by set id and die id rather than by
   * anything on disk, so the rolls this set made last week stay the player's
   * (`docs/statistics.md`).
   */
  fun remove(row: SetRow) {
    if (row.bundled) return
    state = state.copy(acting = null)
    scope.launch {
      withContext(io) { installed.remove(row.id) }
      registry.forget(row.id)
      refresh()
    }
  }

  /**
   * The bundled set first, then everything else by name.
   *
   * By name rather than by id, because the name is what the row shows: two
   * sets whose folders sort one way and whose names sort the other would read
   * as an unsorted list.
   */
  private fun rows(
    packages: List<InstalledPackage>,
    off: Set<String>,
  ): List<SetRow> =
    listOf(SetRow.bundled(bundled)) +
      packages
        .map { pack -> SetRow.of(pack, enabled = pack.id !in off) }
        .sortedBy { it.name.lowercase() }
}
