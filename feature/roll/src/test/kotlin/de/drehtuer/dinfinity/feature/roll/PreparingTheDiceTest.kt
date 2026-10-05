package de.drehtuer.dinfinity.feature.roll

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.drehtuer.dinfinity.render.filament.DiceMaterial
import de.drehtuer.dinfinity.render.filament.ShaderWork
import de.drehtuer.dinfinity.render.filament.monotonicMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "Preparing the dice" over the tray while a material compiles
 * (`docs/physics-and-rendering.md`, "Preparing the dice"; decision 95).
 *
 * The clock is the test's, so what the plate says at a given moment of a
 * compile is a fact rather than a race.
 */
@RunWith(RobolectricTestRunner::class)
class PreparingTheDiceTest {
  @get:Rule
  val compose = createComposeRule()

  private var now = START
  private var work: ShaderWork by mutableStateOf(ShaderWork.Idle)

  private fun show() {
    compose.setContent { PreparingTheDice(work = work, clock = { now }) }
  }

  private fun compiling(
    variant: DiceMaterial.Variant = DiceMaterial.Variant.OPAQUE,
    estimate: Long = ESTIMATE,
  ) = ShaderWork.Compiling(variant, startedAtMillis = START, estimateMillis = estimate)

  @Test
  fun `nothing compiling draws nothing`() {
    show()

    compose.onNodeWithTag(RollTestTags.PREPARING).assertDoesNotExist()
  }

  @Test
  fun `a compile says what it prepares, why, how far it has got and about how long is left`() {
    now = START + ESTIMATE / 2
    work = compiling()
    show()

    compose.onNodeWithTag(RollTestTags.PREPARING_TITLE, useUnmergedTree = true).assertTextEquals("Preparing the dice")
    compose.onNodeWithTag(RollTestTags.PREPARING_LEFT).assertTextEquals("About 2 seconds left")
    assertEquals(0.5f, progressOf().current, 0.001f)
  }

  @Test
  fun `the last second is one second, not none`() {
    now = START + ESTIMATE - 1
    work = compiling()
    show()

    compose.onNodeWithTag(RollTestTags.PREPARING_LEFT).assertTextEquals("About 1 second left")
  }

  @Test
  fun `a compile slower than its estimate waits short of full and stops counting`() {
    now = START + ESTIMATE * 3
    work = compiling()
    show()

    compose.onNodeWithTag(RollTestTags.PREPARING_LEFT).assertTextEquals("Almost done")
    assertEquals(ShaderWork.MOST, progressOf().current, 0.001f)
  }

  @Test
  fun `the plate goes when the compile does`() {
    work = compiling()
    show()
    compose.onNodeWithTag(RollTestTags.PREPARING).assertExists()

    work = ShaderWork.Idle

    compose.waitForIdle()
    compose.onNodeWithTag(RollTestTags.PREPARING).assertDoesNotExist()
  }

  @Test
  fun `the bar moves on its own while the compile runs`() {
    compose.mainClock.autoAdvance = false
    work = compiling()
    show()
    compose.mainClock.advanceTimeByFrame()
    assertEquals(0f, progressOf().current, 0.001f)

    now = START + ESTIMATE / 4
    compose.mainClock.advanceTimeBy(FRAMES)

    assertEquals(0.25f, progressOf().current, 0.001f)
  }

  @Test
  fun `each material is named for what the player sees`() {
    work = compiling(DiceMaterial.Variant.OPAQUE)
    show()
    val titles =
      DiceMaterial.Variant.entries.associateWith { variant ->
        work = compiling(variant)
        compose.waitForIdle()
        compose
          .onNodeWithTag(RollTestTags.PREPARING_TITLE, useUnmergedTree = true)
          .fetchSemanticsNode()
          .config[SemanticsProperties.Text]
          .joinToString()
      }

    assertEquals(
      mapOf(
        DiceMaterial.Variant.OPAQUE to "Preparing the dice",
        DiceMaterial.Variant.RESIN to "Preparing the translucent dice",
        DiceMaterial.Variant.GLASS to "Preparing the glass table",
        DiceMaterial.Variant.TABLE to "Preparing the table",
      ),
      titles,
    )
  }

  @Test
  fun `a screen reader hears the notice arrive once and finds the bar a progress bar`() {
    work = compiling()
    show()

    val notice =
      SemanticsMatcher("is a polite live region") {
        it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite
      }
    compose
      .onNode(notice, useUnmergedTree = true)
      .assert(
        SemanticsMatcher("names the work and why") {
          it.config.getOrNull(SemanticsProperties.ContentDescription)?.single() ==
            "Preparing the dice. Compiling the shaders for this phone. This happens once after an install or update."
        },
      )
    compose
      .onNodeWithTag(RollTestTags.PREPARING_BAR)
      .assert(
        SemanticsMatcher("only the notice is live") { it.config.getOrNull(SemanticsProperties.LiveRegion) == null },
      )
  }

  @Test
  fun `the bar is told to a screen reader in whole per cent`() {
    assertEquals(0.33f, percentOf(0.3333f), 0.0001f)
    assertEquals(0.95f, percentOf(0.949f), 0.0001f)
    assertEquals(0f, percentOf(0f), 0.0001f)
  }

  @Test
  fun `the roll screen shows the plate while its tray compiles, and not otherwise`() {
    val tray = CompilingTray()
    compose.setContent { RollScreen(presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0)))) }
    compose.onNodeWithTag(RollTestTags.PREPARING).assertDoesNotExist()

    tray.compiling.value = ShaderWork.Compiling(DiceMaterial.Variant.RESIN, monotonicMillis(), ESTIMATE)

    compose.onNodeWithTag(RollTestTags.PREPARING).assertExists()
    compose
      .onNodeWithTag(
        RollTestTags.PREPARING_TITLE,
        useUnmergedTree = true,
      ).assertTextEquals("Preparing the translucent dice")

    tray.compiling.value = ShaderWork.Idle
    compose.waitForIdle()
    compose.onNodeWithTag(RollTestTags.PREPARING).assertDoesNotExist()
  }

  @Test
  fun `a tray that does not draw never prepares anything`() {
    // Power-saving mode opens no engine, so there is nothing to compile; the
    // default a tray inherits says so.
    val tray = UndrawnTray()
    compose.setContent { RollScreen(presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0)))) }

    assertEquals(ShaderWork.Idle, tray.shaders.value)
    compose.onNodeWithTag(RollTestTags.PREPARING).assertDoesNotExist()
  }

  private fun progressOf() =
    requireNotNull(
      compose
        .onNodeWithTag(RollTestTags.PREPARING_BAR)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.ProgressBarRangeInfo),
    ) { "the bar is not a progress bar to a screen reader" }

  /** A tray whose far side is compiling whatever the test says. */
  private class CompilingTray : DirectTray() {
    val compiling = MutableStateFlow<ShaderWork>(ShaderWork.Idle)
    override val shaders: StateFlow<ShaderWork> get() = compiling
  }

  private companion object {
    const val START = 1_000_000L
    const val ESTIMATE = 2_400L

    /** A few frames: enough for the plate to read the clock again. */
    const val FRAMES = 100L
  }
}
