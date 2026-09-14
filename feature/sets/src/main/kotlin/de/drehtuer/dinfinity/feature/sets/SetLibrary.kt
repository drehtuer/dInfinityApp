package de.drehtuer.dinfinity.feature.sets

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.dicesets.install.InstalledPackage
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The dice sets, as a screen wants them (`docs/dice-sets.md`).
 *
 * **The one place the two halves are joined.** [InstalledSets] says what is on
 * disk and whether it still validates; [InstalledSetRepository] says what the
 * player has switched off. Neither knows about the other, and the rule that
 * relates them — a package with no row is enabled — is written here once
 * rather than in every screen that needs a list.
 *
 * The bundled set is handed in rather than reached for, so `feature/sets` never
 * learns that `dicesets:builtin` exists. It is the only set with no folder, and
 * every path that would touch one refuses it first.
 *
 * Reading means validating every package: TOML parsing and image headers over
 * real files. That is [io]'s job and not the caller's to remember, which is why
 * the dispatcher lives here and not in the presenters
 * (`docs/architecture.md`, "Threading"). It has no default: which thread the
 * disk is touched on is a wiring decision, and it belongs where the other
 * wiring decisions are rather than hidden in a parameter list.
 */
class SetLibrary(
  private val bundled: DiceSet,
  private val installed: InstalledSets,
  private val registry: InstalledSetRepository,
  private val io: CoroutineDispatcher,
) {
  /**
   * Every set, the bundled one first and the rest by name.
   *
   * By name rather than by id, because the name is what a row shows: two sets
   * whose folders sort one way and whose names sort the other would read as an
   * unsorted list.
   *
   * Reading is also when the registry is reconciled with the disk — the only
   * moment both are in hand. A row about a folder that has gone would switch a
   * *new* package off the moment somebody installed one under the same id.
   */
  suspend fun all(): List<SetRow> {
    val packages = withContext(io) { installed.scan() }
    registry.keepOnly(packages.map(InstalledPackage::id))
    val off = registry.disabled()
    return listOf(SetRow.bundled(bundled)) +
      packages
        .map { pack -> SetRow.of(pack, enabled = pack.id !in off) }
        .sortedBy { it.name.lowercase() }
  }

  /** The one set called [id], or null when nothing is installed under that name. */
  suspend fun one(id: String): SetRow? {
    if (id == bundled.id) return SetRow.bundled(bundled)
    val pack = withContext(io) { installed.find(id) } ?: return null
    return SetRow.of(pack, enabled = pack.id !in registry.disabled())
  }

  /**
   * Switches a set on or off.
   *
   * The folder is untouched, and so is every statistic recorded against the
   * set's dice. That is what makes it the reversible one of the two things a
   * player can do to a set.
   */
  suspend fun setEnabled(
    row: SetRow,
    enabled: Boolean,
  ) {
    if (row.bundled) return
    registry.setEnabled(row.id, enabled)
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
  suspend fun remove(row: SetRow) {
    if (row.bundled) return
    withContext(io) { installed.remove(row.id) }
    registry.forget(row.id)
  }
}
