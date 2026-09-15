package de.drehtuer.dinfinity.feature.roll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What a fresh install already has (`design/dInfinity.dc.html`, option 9a).
 *
 * Three numbers rather than three parameters, which only pays if they compare
 * as one thing: the welcome is redrawn when *any* of them moves and left alone
 * when none does, and that is this type's equality doing the deciding.
 */
class WhatIsThereTest {
  @Test
  fun `a fresh install has the bundled set and nothing else`() {
    assertEquals(WhatIsThere(), WhatIsThere(sets = 0, savedRolls = 0, sessions = 0))
  }

  @Test
  fun `any one of the three moving is a different line`() {
    val fresh = WhatIsThere(sets = 1)

    assertNotEquals(fresh, fresh.copy(sets = 2))
    assertNotEquals(fresh, fresh.copy(savedRolls = 1))
    assertNotEquals(fresh, fresh.copy(sessions = 1))
  }

  @Test
  fun `and the same three are the same line`() {
    // The side that matters for drawing: the welcome is left alone when its
    // counts have not moved, which is this equality and nothing else.
    assertEquals(WhatIsThere(1, 2, 3), WhatIsThere(1, 2, 3))
    assertEquals(WhatIsThere(1, 2, 3).hashCode(), WhatIsThere(1, 2, 3).hashCode())
  }
}
