package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.RollHistoryRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The hand-written parts of the history row.
 *
 * A row carries a `ByteArray`, and a generated `equals` over one compares by
 * identity — which would make every row unequal to itself the moment Room read
 * it back as a new object. So it is written out, and so it is tested.
 */
@RunWith(RobolectricTestRunner::class)
class RollHistoryRowTest {
  @Test
  fun `two rows with the same contents are the same row`() {
    assertEquals(row(), row())
    assertEquals(row().hashCode(), row().hashCode())
  }

  @Test
  fun `a row is itself`() {
    val row = row()
    assertEquals(row, row)
  }

  @Test
  fun `rows whose inputs differ are different, even byte for byte`() {
    assertNotEquals(row(blob = byteArrayOf(1, 2)), row(blob = byteArrayOf(1, 3)))
    assertNotEquals(row(blob = byteArrayOf(1, 2)), row(blob = null))
  }

  @Test
  fun `rows differing anywhere else are different too`() {
    assertNotEquals(row(), row().copy(total = 2))
    assertNotEquals(row(), row().copy(formula = "1d6"))
    assertNotEquals(row(), row().copy(sessionId = "friday"))
    assertNotEquals(row(), row().copy(savedRollId = "fireball"))
    assertNotEquals(row(), row().copy(groupId = "thorin"))
    assertNotEquals(row(), row().copy(seed = 9))
    assertNotEquals(row(), row().copy(timestamp = 9))
    assertNotEquals(row(), row().copy(breakdownJson = "{}"))
    assertNotEquals(row(), row().copy(anomalies = 1))
    assertNotEquals(row(), row().copy(id = 7))
  }

  @Test
  fun `a row is not some other thing`() {
    assertNotEquals(row(), "not a row")
  }

  @Test
  fun `the app can open its own file and read from it`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val database = DInfinityDatabase.open(context, name = "test-open.db")
      // Room opens lazily, so asking it something is what proves it opened.
      assertEquals(0L, database.rollHistory().count())
      assertTrue(database.isOpen)
      database.close()
    }

  private fun row(blob: ByteArray? = byteArrayOf(1, 2)): RollHistoryRow =
    RollHistoryRow(
      id = 1,
      timestamp = 100,
      sessionId = "tuesday",
      formula = "1d20",
      total = 1,
      seed = 0,
      inputBlob = blob,
      breakdownJson = "[]",
    )
}
