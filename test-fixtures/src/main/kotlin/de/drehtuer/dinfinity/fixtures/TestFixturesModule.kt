package de.drehtuer.dinfinity.fixtures

/**
 * Marks `:test-fixtures` as present and wired into the build.
 *
 * It also records what this module may depend on, which matters more here than
 * elsewhere: fixtures are seen by every other module's tests, so anything this
 * module pulls in, every test everywhere pulls in with it.
 */
object TestFixturesModule {
  /** This module's Gradle path. */
  const val PATH: String = ":test-fixtures"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":core:model")
}
