package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.InstalledSetRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the player has decided about the sets they have installed
 * (`docs/dice-sets.md`, design `5a`).
 *
 * **The disk is the list; this is the opinion.** Which packages exist is
 * `InstalledSets`' answer and is read off the `dicesets/` folder; nothing here
 * can add one or take one away. What this holds is the part a package cannot
 * know about itself — today, whether the player has switched it off.
 *
 * Switching a set off is deliberately not the same as removing it. A disabled
 * set keeps its folder, its `.meta.json` and every statistic ever recorded
 * against its dice; it is simply not offered. That is what makes it a
 * reversible decision, and it is why the screen offers both.
 */
class InstalledSetRepository(
  private val database: DInfinityDatabase,
) {
  /**
   * The sets the player has switched off. Anything not named here is on.
   *
   * The absence of a row means *enabled*, because that is what a set does the
   * moment it installs — so a fresh install needs no rows written for it, and
   * a package that arrives by some route the app never saw is usable straight
   * away rather than invisible until something remembers to register it.
   */
  val disabled: Flow<Set<String>> =
    database
      .installedSets()
      .all()
      .map { rows -> rows.filterNot(InstalledSetRow::enabled).map(InstalledSetRow::id).toSet() }

  /**
   * Switches [id] on or off.
   *
   * The bundled set is refused. It is the one every fallback resolves against
   * (`docs/dice-notation.md`), so a player who switched it off would have a
   * `d20` that no longer means anything — and, unlike every other set, there
   * would be no way to install it back. It is not in the `dicesets/` folder at
   * all, so this is a guard against a caller rather than against a player, and
   * it says so by doing nothing rather than by throwing.
   */
  suspend fun setEnabled(
    id: String,
    enabled: Boolean,
  ) {
    if (id == DiceSet.BUILTIN_ID) return
    database.installedSets().upsert(InstalledSetRow(id = id, enabled = enabled))
  }

  /** Whether [id] is switched on. A set nobody has an opinion about is. */
  suspend fun isEnabled(id: String): Boolean = database.installedSets().byId(id)?.enabled ?: true

  /**
   * Forgets [id] entirely, which is what uninstalling it means here
   * (`docs/architecture.md`: uninstall deletes the folder and the registry
   * row).
   *
   * Statistics are not touched. They are keyed by set id and die id rather
   * than by anything on disk, so a set removed today keeps the rolls it made
   * last week — which is the player's history, not the package's
   * (`docs/statistics.md`).
   */
  suspend fun forget(id: String) {
    database.installedSets().delete(id)
  }

  /**
   * Drops every opinion about a set that is no longer on disk.
   *
   * Folders vanish without the app being asked — a restore, a file manager, an
   * update that failed halfway. A row left behind would be worse than untidy:
   * it would switch a *new* package off the moment somebody installed one
   * under the same id, with nothing on any screen to explain why.
   *
   * @param present every id the `dicesets/` folder actually holds.
   */
  suspend fun keepOnly(present: Collection<String>) {
    database.installedSets().keepOnly(present.toList())
  }
}
