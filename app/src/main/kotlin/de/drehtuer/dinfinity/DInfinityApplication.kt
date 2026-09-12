package de.drehtuer.dinfinity

import android.app.Application
import de.drehtuer.dinfinity.data.SettingsRepository
import de.drehtuer.dinfinity.data.SettingsStorage

/**
 * Holds the few things that outlive an activity.
 *
 * A field on the Application rather than a dependency-injection framework:
 * there is one dependency so far, and a container that holds one object is
 * more machinery than the problem has. When the graph grows past what this can
 * carry honestly, it becomes a real container (`docs/TODO.md`).
 */
class DInfinityApplication : Application() {
  val settingsRepository: SettingsRepository by lazy { SettingsStorage.create(this) }
}
