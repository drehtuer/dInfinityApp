package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What "My dice" is made of, between one sitting and the next
 * (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
 * them").
 *
 * The third record the personal package is built from, and the tests are the
 * ones the other two have: it survives being written and read, a file that is
 * nonsense is the defaults rather than an error, and the stamp moves when the
 * numbers do — because that is what makes the package be rebuilt.
 */
class PhysicalsTest {
  @get:Rule
  val temporary: TemporaryFolder = TemporaryFolder()

  private fun store(): PhysicalStore = PhysicalStore(File(temporary.newFolder(), PhysicalStore.FILE_NAME))

  @Test
  fun `nothing set is what the app would have used anyway`() {
    assertEquals(DieMaterial(), store().material())
  }

  @Test
  fun `what was set is what comes back`() {
    val store = store()

    store.set(DieMaterial(sizeMm = 20.0, density = 2.4, translucency = 0.35))

    val back = store.material()
    assertEquals(20.0, back.sizeMm, 1e-12)
    assertEquals(2.4, back.density, 1e-12)
    assertEquals(0.35, back.translucency, 1e-12)
  }

  @Test
  fun `only the three a person sets are kept`() {
    val store = store()

    // A colour is not this record's to hold: a drawn die's colour is the
    // installing set's business (`MinePackage`).
    store.set(DieMaterial(colorArgb = 0x00FF0000, friction = 0.9, sizeMm = 18.0))

    assertEquals(DieMaterial().colorArgb, store.material().colorArgb)
    assertEquals(DieMaterial().friction, store.material().friction, 1e-12)
    assertEquals(18.0, store.material().sizeMm, 1e-12)
  }

  @Test
  fun `a number outside what a die can be is clamped rather than kept`() {
    val store = store()

    store.set(DieMaterial(sizeMm = 400.0, density = 99.0))

    assertEquals(DieMaterial.SizeMmRange.endInclusive, store.material().sizeMm, 1e-12)
    assertEquals(DieMaterial.DensityRange.endInclusive, store.material().density, 1e-12)
  }

  @Test
  fun `a file that will not read back is the defaults, not a package that cannot be built`() {
    val file = File(temporary.newFolder(), PhysicalStore.FILE_NAME)
    file.writeText("this is not a weight\ndensity = ???\n")

    assertEquals(DieMaterial(), PhysicalStore(file).material())
  }

  @Test
  fun `the stamp moves when the numbers do and stands still when they do not`() {
    val store = store()
    val before = store.stamp()

    store.set(DieMaterial(density = 1.3))
    val after = store.stamp()
    store.set(DieMaterial(density = 1.3))

    assertNotEquals(before, after)
    assertEquals(after, store.stamp())
  }

  @Test
  fun `one tap after another is two different stamps`() {
    // The case a modification time would miss: two writes in the same
    // millisecond, of texts the same length.
    val store = store()

    store.set(DieMaterial(density = 1.3))
    val first = store.stamp()
    store.set(DieMaterial(density = 1.4))

    assertNotEquals(first, store.stamp())
  }

  @Test
  fun `the record that cannot be written is the defaults and takes no writes`() {
    Physicals.NONE.set(DieMaterial(density = 4.0))

    assertEquals(DieMaterial(), Physicals.NONE.material())
    assertEquals(0L, Physicals.NONE.stamp())
  }
}
