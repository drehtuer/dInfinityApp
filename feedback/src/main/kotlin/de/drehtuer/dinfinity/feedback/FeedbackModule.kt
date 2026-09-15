package de.drehtuer.dinfinity.feedback

/**
 * Marks `:feedback` as present and wired into the build.
 *
 * Almost nothing in this module is Android-shaped, for the same reason almost
 * nothing in `:input:shake` is: what to play, how loud, at what pitch and when
 * are all decisions, and they are plain Kotlin in [ImpactTrack], [ImpactVoice],
 * [HapticTick] and [ImpactWaveform]. Only [SystemBuzzer] and [PcmSpeaker] touch
 * an Android API, and neither of them decides anything
 * (`docs/architecture.md`, decision 40).
 */
object FeedbackModule {
  /** This module's Gradle path. */
  const val PATH: String = ":feedback"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":simulation:api")
}
