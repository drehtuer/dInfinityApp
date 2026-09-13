package de.drehtuer.dinfinity

import android.app.Application
import de.drehtuer.dinfinity.data.SettingsRepository
import de.drehtuer.dinfinity.data.SettingsStorage

/**
 * Holds the few things that outlive an activity.
 *
 * Fields on the Application rather than a dependency-injection framework: two
 * things hang off it, each built once and lazily, and a container for two
 * objects is more machinery than the problem has. When the graph grows past
 * what this can carry honestly, it becomes a real container (`docs/TODO.md`).
 */
class DInfinityApplication : Application() {
  val settingsRepository: SettingsRepository by lazy { SettingsStorage.create(this) }

  /** The roll screen's engine and catalogue, named in one place (`RollWiring`). */
  val rolls: RollWiring by lazy { RollWiring(this) }
}
