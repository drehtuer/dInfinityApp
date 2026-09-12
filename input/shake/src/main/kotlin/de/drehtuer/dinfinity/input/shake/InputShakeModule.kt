package de.drehtuer.dinfinity.input.shake

/**
 * Marks `:input:shake` as present and wired into the build.
 *
 * The module is a skeleton: plan Step 1 builds the graph, and the step that
 * owns this module fills it with real types. Until then this object and its
 * test are what prove the module compiles, runs its tests, and can see the
 * modules it depends on — a graph that only compiles proves nothing.
 */
object InputShakeModule {
    /** This module's Gradle path. */
    const val PATH: String = ":input:shake"

    /** The Gradle paths this module declares a dependency on. */
    val DEPENDS_ON: List<String> = listOf(":simulation:api")
}
