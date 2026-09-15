package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The short list of licences an export may go out under
 * (`design/dInfinity.dc.html`, option `8c`).
 */
class SetLicenseTest {
  @Test
  fun `the prototype's five licences are all offered, in its order`() {
    // The design's dropdown, which is the specification for this screen.
    assertEquals(
      listOf("CC0-1.0", "CC-BY-4.0", "CC-BY-SA-4.0", "MIT", "GPL-2.0-or-later"),
      SetLicense.entries.map(SetLicense::id).take(5),
    )
  }

  @Test
  fun `all rights reserved is offered too, and is not an SPDX name`() {
    // SPDX has no identifier for "I am keeping this", and writing the sentence
    // into a field of identifiers would be a name nothing could match. The
    // `LicenseRef-` form is what SPDX reserves for exactly this.
    assertEquals("LicenseRef-All-Rights-Reserved", SetLicense.AllRightsReserved.id)
    assertEquals("All rights reserved", SetLicense.AllRightsReserved.label)
  }

  @Test
  fun `no two licences share an id or a label`() {
    val ids = SetLicense.entries.map(SetLicense::id)
    val labels = SetLicense.entries.map(SetLicense::label)

    assertEquals(SetLicense.entries.size, ids.toSet().size)
    assertEquals(SetLicense.entries.size, labels.toSet().size)
  }

  @Test
  fun `a licence written into a package is read back as the one that was chosen`() {
    SetLicense.entries.forEach { license -> assertEquals(license, SetLicense.ofId(license.id)) }
  }

  @Test
  fun `unspecified is not a licence, which is what makes it a gate`() {
    assertNull(SetLicense.ofId(SetLicense.UNSPECIFIED))
    assertNull(SetLicense.ofId(null))
    assertNull(SetLicense.ofId("free to use i guess"))
    assertFalse(SetLicense.chosen(SetLicense.UNSPECIFIED))
    assertTrue(SetLicense.chosen("MIT"))
  }
}
