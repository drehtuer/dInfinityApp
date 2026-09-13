package de.drehtuer.dinfinity

import android.app.Application
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.SettingsRepository
import de.drehtuer.dinfinity.data.SettingsStorage
import de.drehtuer.dinfinity.data.db.DInfinityDatabase

/**
 * Holds the few things that outlive an activity.
 *
 * Fields on the Application rather than a dependency-injection framework: a
 * handful of things hang off it, each built once and lazily. The list is
 * growing, and `docs/TODO.md` says when it becomes a real container — the
 * point is a judgement about honesty, not a count.
 */
class DInfinityApplication : Application() {
  val settingsRepository: SettingsRepository by lazy { SettingsStorage.create(this) }

  /**
   * The one database file, opened once.
   *
   * On the application rather than per screen because it is a file: two
   * screens opening it separately would be two connections to the same
   * SQLite database, and Room's own advice is one instance for the process.
   */
  val database: DInfinityDatabase by lazy { DInfinityDatabase.open(this) }

  /** Saved rolls and their groups (`docs/dice-notation.md`). */
  val savedRolls: SavedRollRepository by lazy { SavedRollRepository(database) }

  /** The roll screen's engine and catalogue, named in one place (`RollWiring`). */
  val rolls: RollWiring by lazy { RollWiring(this) }
}
