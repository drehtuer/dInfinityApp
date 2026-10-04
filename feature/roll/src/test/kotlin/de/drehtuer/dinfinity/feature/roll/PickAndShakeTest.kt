package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.filament.TrayPick
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A finger on a die, and the shake that throws it
 * (`docs/physics-and-rendering.md`, "Picking a die up and throwing it again";
 * `docs/architecture.md`, decisions 68 and 76).
 *
 * The finger is a real tap on the tray, at the point the camera draws the die
 * — worked out with the same `TrayPick` the screen reads the finger with — and
 * the shake is a [TestHand], by the same calls the sensors make. What comes
 * back from the throw is a fake roll that lands where it is told, which is the
 * whole of what the physics is asked here.
 */
@RunWith(RobolectricTestRunner::class)
class PickAndShakeTest {
  @get:Rule
  val compose = createComposeRule()

  @get:Rule
  val shaking = ShakingHand()

  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `a tap on a die after the roll lands picks it, rings it and says so`() {
    val presenter = onScreen(DirectTray(), PassingRolls(listOf(threeDown, oneMore)))
    compose.setContent { RollScreen(presenter = presenter) }
    typeFormula("3d6")
    shake()
    lookAtTheFelt()

    tapOn(1)

    assertEquals(setOf(1), presenter.picked)
    compose.onNodeWithTag(RollTestTags.PICKED).assertExists()
    compose
      .onNodeWithTag(RollTestTags.TRAY)
      .assertContentDescriptionEquals("Dice tray, the dice have landed on $firstTotal, 1 die picked; shake to throw it")
    assertTrue(
      "a pick was not announced as it happened",
      compose
        .onNodeWithTag(RollTestTags.TRAY)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.LiveRegion) != null,
    )
  }

  @Test
  fun `a second tap on the same die puts it back`() {
    val presenter = onScreen(DirectTray(), PassingRolls(listOf(threeDown, oneMore)))
    compose.setContent { RollScreen(presenter = presenter) }
    typeFormula("3d6")
    shake()
    lookAtTheFelt()

    tapOn(1)
    tapOn(1)

    assertTrue(presenter.picked.isEmpty())
    compose.onNodeWithTag(RollTestTags.PICKED).assertDoesNotExist()
    compose
      .onNodeWithTag(RollTestTags.TRAY)
      .assertContentDescriptionEquals("Dice tray, the dice have landed on $firstTotal")
  }

  @Test
  fun `the shake throws the picked die alone, and the total comes back with the old face struck through`() {
    val rolls = PassingRolls(listOf(threeDown, oneMore))
    val written = mutableListOf<FinishedThrow>()
    val presenter = presenter(rolls) { written += it }
    compose.setContent { RollScreen(presenter = presenter) }
    typeFormula("3d6")
    shake()
    lookAtTheFelt()
    tapOn(1)

    assertTrue("the shake did not throw the picked die", shake())

    assertEquals(
      "the shake threw more than the picked die",
      1,
      rolls.started
        .last()
        .dice.size,
    )
    assertEquals(
      "the dice lying on the table were not carried",
      3,
      rolls.started
        .last()
        .among.size,
    )
    val settled = presenter.state as RollState.Settled
    assertEquals(4, settled.result.dice.size)
    assertFalse("the replaced face still counts", settled.result.dice[1].kept)
    assertEquals(
      "a throw by hand was written down as a new roll",
      listOf(emptySet(), setOf(1)),
      written.map { it.thrownAgain },
    )
    assertTrue("a pick survived the throw it was for", presenter.picked.isEmpty())
  }

  @Test
  fun `a shake with nothing picked throws the whole roll again`() {
    val rolls = PassingRolls(listOf(threeDown))
    compose.setContent { RollScreen(presenter = onScreen(DirectTray(), rolls)) }
    typeFormula("3d6")
    shake()

    shake()

    assertEquals(2, rolls.started.size)
    assertEquals(
      3,
      rolls.started
        .last()
        .dice.size,
    )
    assertTrue(
      rolls.started
        .last()
        .among
        .isEmpty(),
    )
  }

  @Test
  fun `a finger that wanders is the camera's business, not a pick`() {
    val presenter = onScreen(DirectTray(), PassingRolls(listOf(threeDown)))
    compose.setContent { RollScreen(presenter = presenter) }
    typeFormula("3d6")
    shake()
    lookAtTheFelt()
    val (at, size) = whereIs(presenter, 1)

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      swipe(at, at + Offset(size.x / 3, 0f))
    }

    assertTrue("a drag picked a die up", presenter.picked.isEmpty())
  }

  @Test
  fun `a tap before anything has landed picks nothing`() {
    val presenter = onScreen(DirectTray(), PassingRolls(listOf(threeDown)))
    compose.setContent { RollScreen(presenter = presenter) }
    typeFormula("3d6")

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput { click(center) }

    assertTrue(presenter.picked.isEmpty())
    assertFalse(presenter.touch(HALF, HALF, aspectRatio = 0.0))
  }

  @Test
  fun `a touch on the bare floor picks nothing`() {
    val presenter = onScreen(DirectTray(), PassingRolls(listOf(threeDown)))
    presenter.type("3d6")
    presenter.roll()

    // The near end of the tray, well clear of the three dice along its middle.
    assertFalse(presenter.touch(HALF, NEAR_EDGE, PORTRAIT))
    assertTrue(presenter.marks(PORTRAIT).isEmpty())
  }

  @Test
  fun `each picked die has a ring where it is drawn, and none without a picture to draw on`() {
    val presenter = onScreen(DirectTray(), PassingRolls(listOf(threeDown)))
    presenter.type("3d6")
    presenter.roll()
    val pick = TrayPick.through(geometry, PORTRAIT, TrayView.Whole)

    presenter.pick(2)
    presenter.pick(0)

    assertEquals(
      listOf(0, 2).map { pick.markOf(presenter.onTheTable[it]) },
      presenter.marks(PORTRAIT),
    )
    assertTrue(presenter.marks(0.0).isEmpty())
    assertFalse("a refused pick reported a change", presenter.pick(9))
  }

  @Test
  fun `typing over a roll with a die picked puts the die back`() {
    val presenter = onScreen(DirectTray(), PassingRolls(listOf(threeDown)))
    presenter.type("3d6")
    presenter.roll()
    presenter.pick(0)

    presenter.type("2d6")

    assertTrue(presenter.picked.isEmpty())
    assertTrue(presenter.onTheTable.isEmpty())
    assertNull(presenter.marks(PORTRAIT).firstOrNull())
  }

  /**
   * Pushes the result sheet down, which is what a player does to see the
   * dice: a total arrives with its breakdown up, over most of the felt.
   */
  private fun lookAtTheFelt() {
    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).performClick()
    compose.waitForIdle()
  }

  /** Taps the tray where the die at [position] on the table is drawn. */
  private fun tapOn(position: Int) {
    val presenter = presenterOnScreen ?: error("no screen")
    val (at, _) = whereIs(presenter, position)
    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput { click(at) }
    compose.waitForIdle()
  }

  /** Where on the tray node the die at [position] is drawn, and how big the node is. */
  private fun whereIs(
    presenter: RollPresenter,
    position: Int,
  ): Pair<Offset, Offset> {
    val size = compose.onNodeWithTag(RollTestTags.TRAY).fetchSemanticsNode().size
    val ratio = size.width.toDouble() / size.height
    val mark =
      requireNotNull(TrayPick.through(geometry, ratio, presenter.looking).markOf(presenter.onTheTable[position]))
    val at = Offset((mark.acrossFraction * size.width).toFloat(), (mark.downFraction * size.height).toFloat())
    return at to Offset(size.width.toFloat(), size.height.toFloat())
  }

  private var presenterOnScreen: RollPresenter? = null

  private fun onScreen(
    tray: DirectTray,
    rolls: PassingRolls,
  ): RollPresenter = rollPresenter(tray = tray, rolls = rolls, catalog = catalog).also { presenterOnScreen = it }

  private fun presenter(
    rolls: PassingRolls,
    recorder: ThrowRecorder,
  ): RollPresenter =
    RollPresenter(
      machine =
        RollMachine(
          catalog = catalog,
          geometry = geometry,
          look = { TableLook(id = "plain", name = "Plain") },
          outside = Outside(seeds = { 1L }, clock = { 0L }),
        ),
      driver = DirectTray(),
      rolls = rolls,
      recorder = recorder,
      toTheScreen = { it() },
    ).also { presenterOnScreen = it }

  /**
   * Types [text] and shuts the drawer again, as a player does before reaching
   * for the felt: an open drawer takes a touch on the tray for itself.
   */
  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.waitForIdle()
  }

  private fun shake(): Boolean {
    val threw = compose.runOnUiThread { shaking.hand.shake() }
    compose.waitForIdle()
    return threw
  }

  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  /** Three dice down along the middle of the tray, a hand's width apart. */
  private val threeDown =
    SimulationOutcome(
      faces = mapOf(0 to 0, 1 to 1, 2 to 2),
      restingAt =
        (0..2).associateWith { at ->
          RestingPlace(Vector3((at - 1) * APART_MM, 0.0, REST_HEIGHT_MM), Quaternion.Identity)
        },
    )

  /** The one die a hand threw again, landing clear of the three. */
  private val oneMore =
    SimulationOutcome(
      faces = mapOf(0 to 5),
      restingAt = mapOf(0 to RestingPlace(Vector3(0.0, APART_MM, REST_HEIGHT_MM), Quaternion.Identity)),
    )

  private val firstTotal: Long
    get() =
      BuiltinDiceSet.set.dice
        .first { it.id == "d6" }
        .let { d6 -> (0..2).sumOf { d6.valueAt(it) }.toLong() }

  private companion object {
    const val APART_MM = 40.0
    const val REST_HEIGHT_MM = 8.0
    const val HALF = 0.5
    const val NEAR_EDGE = 0.97
    const val PORTRAIT = 1080.0 / 2400.0
  }
}
