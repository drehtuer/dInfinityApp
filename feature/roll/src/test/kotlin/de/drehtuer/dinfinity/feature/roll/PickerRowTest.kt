package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import de.drehtuer.dinfinity.core.notation.PickableDie
import de.drehtuer.dinfinity.core.notation.Sides
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The dice picker row (`design/dInfinity.dc.html`, option 1h).
 *
 * What the row itself has to get right is small — which die was pressed, and
 * whether it was pressed or held. What each of those *means* is
 * `DicePicker`'s, and is tested on the JVM without a phone in the picture
 * (`docs/dice-notation.md`, "Picking dice without typing").
 */
@RunWith(RobolectricTestRunner::class)
class PickerRowTest {
  @get:Rule
  val compose = createComposeRule()

  private val d6 = PickableDie("d6", Sides.Numeric(6))
  private val d20 = PickableDie("d20", Sides.Numeric(20))

  @Test
  fun `every die the set offers gets a button`() {
    show()

    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.pickerDie("d20")).assertIsDisplayed()
  }

  @Test
  fun `a tap asks for one more of that die`() {
    val added = mutableListOf<PickableDie>()
    show(onAdd = added::add)

    compose.onNodeWithTag(RollTestTags.pickerDie("d20")).performClick()

    assertEquals(listOf(d20), added)
  }

  @Test
  fun `a long press asks for one fewer`() {
    val removed = mutableListOf<PickableDie>()
    show(onRemove = removed::add)

    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performTouchInput { longClick() }

    assertEquals(listOf(d6), removed)
  }

  @Test
  fun `a long press is not also a tap`() {
    // The two do opposite things, so a press that counted as both would add a
    // die and take it away again — which looks exactly like nothing happening.
    val added = mutableListOf<PickableDie>()
    val removed = mutableListOf<PickableDie>()
    show(onAdd = added::add, onRemove = removed::add)

    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performTouchInput { longClick() }

    assertEquals(emptyList<PickableDie>(), added)
    assertEquals(listOf(d6), removed)
  }

  @Test
  fun `the badge says how many of that die the formula asks for`() {
    show(counts = mapOf(d6 to 3))

    compose.onNodeWithTag(RollTestTags.pickerCount("d6"), useUnmergedTree = true).assertTextEquals("3")
  }

  @Test
  fun `a die the formula does not ask for wears no badge`() {
    // A row of zeroes is a row of noise: the badge is what says, at a glance,
    // which dice are in the throw.
    show(counts = mapOf(d6 to 3))

    compose.onNodeWithTag(RollTestTags.pickerCount("d20"), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `a set with no standard dice in it shows no row at all`() {
    compose.setContent { PickerRow(dice = emptyList(), counts = emptyMap(), onAdd = {}, onRemove = {}) }

    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun `every die in the catalogue has a silhouette, and so does one that is not`() {
    // The picture is what a player picks a die out of a row by, so every die a
    // set can offer needs one. A die the catalogue has no picture for is drawn
    // as a cube rather than as a gap: it is the shape people read as "a die"
    // when they cannot tell which one.
    val row =
      listOf(2, 4, 6, 8, 10, 12, 18, 20, UNKNOWN_SIDES).map { PickableDie("d$it", Sides.Numeric(it)) } +
        PickableDie("d%", Sides.Percentile) +
        PickableDie("dF", Sides.Fudge)

    compose.setContent { PickerRow(dice = row, counts = emptyMap(), onAdd = {}, onRemove = {}) }

    row.forEach { die ->
      compose.onNodeWithTag(RollTestTags.pickerDie(die.notation)).assertExists()
    }
  }

  private fun show(
    counts: Map<PickableDie, Int> = emptyMap(),
    onAdd: (PickableDie) -> Unit = {},
    onRemove: (PickableDie) -> Unit = {},
  ) {
    compose.setContent {
      PickerRow(dice = listOf(d6, d20), counts = counts, onAdd = onAdd, onRemove = onRemove)
    }
  }

  private companion object {
    /** A d30, which the catalogue has no picture for (`docs/TODO.md`, After v1). */
    const val UNKNOWN_SIDES = 30
  }
}
