package de.drehtuer.dinfinity.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check that keeps a screen from quietly arguing with the design system.
 *
 * Both rules are the system's own, and both are things this app actually did:
 * every screen had drifted to a rounded corner the system says it does not
 * have, and several had a palette entry written out as a number.
 */
class FollowsTheDesignSystemTest {
  @Test
  fun `a rounded corner is an offence, because the system has none`() {
    val found = FollowsTheDesignSystem.offences("val shape = RoundedCornerShape(10.dp)")

    assertEquals(1, found.size)
    assertEquals(1, found.single().line)
    assertTrue(found.single().why.contains("rounded corner"))
  }

  @Test
  fun `a zero corner is what the system asks for and is left alone`() {
    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences("RoundedCornerShape(0.dp)"))
  }

  @Test
  fun `the theme's own radius token is not a number and passes`() {
    // It *is* zero, and reading it is the point — a screen that writes the
    // token has done the right thing however the token is spelled.
    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences("RoundedCornerShape(ModernistTokens.radius)"))
  }

  @Test
  fun `a colour written as a number is an offence`() {
    val found = FollowsTheDesignSystem.offences("val grey = Color(0xFF9B9797)")

    assertEquals(1, found.size)
    assertTrue(found.single().why.contains("palette"))
  }

  @Test
  fun `a colour read from the theme is what was wanted`() {
    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences("val c = MaterialTheme.colorScheme.primary"))
  }

  @Test
  fun `a line that says why is taken at its word`() {
    // The bargain: an exception is stated next to the value, so it is a line in
    // a diff rather than an entry in a list nobody reads.
    val excused = "Color(0xFFEC3013) // design-system-exception: this is the palette"

    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences(excused))
  }

  @Test
  fun `every offence is reported, on the line it is on`() {
    val source =
      """
      val a = RoundedCornerShape(6.dp)
      val fine = 0
      val b = Color(0xFF112233)
      """.trimIndent()

    val found = FollowsTheDesignSystem.offences(source)

    assertEquals(listOf(1, 3), found.map { it.line })
  }

  @Test
  fun `spacing is not checked, because a hairline is not the scale and is still right`() {
    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences("Modifier.height(1.dp).padding(2.dp)"))
  }
}
