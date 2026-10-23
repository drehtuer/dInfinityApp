package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DieShapeTest {
  @Test
  fun `the catalogue is the eight solids docs dice-sets names`() {
    assertEquals(
      listOf(
        "coin",
        "elongated-square-bipyramid",
        "cube",
        "octahedron",
        "pentagonal-trapezohedron",
        "dodecahedron",
        "enneagonal-trapezohedron",
        "icosahedron",
      ),
      DieShape.entries.map(DieShape::id),
    )
  }

  @Test
  fun `each solid has the face count its die name promises`() {
    assertEquals(2, DieShape.Coin.faceCount)
    assertEquals(4, DieShape.ElongatedSquareBipyramid.faceCount)
    assertEquals(6, DieShape.Cube.faceCount)
    assertEquals(8, DieShape.Octahedron.faceCount)
    assertEquals(10, DieShape.PentagonalTrapezohedron.faceCount)
    assertEquals(12, DieShape.Dodecahedron.faceCount)
    assertEquals(18, DieShape.EnneagonalTrapezohedron.faceCount)
    assertEquals(20, DieShape.Icosahedron.faceCount)
  }

  @Test
  fun `every solid is read from the face on top, the d4 too`() {
    assertEquals(DieShape.entries, DieShape.entries.filter { it.naturalRead == FaceRead.FaceUp })
    assertEquals(listOf(FaceRead.FaceUp), FaceRead.entries)
  }

  @Test
  fun `the tetrahedron is not a shape any more`() {
    // Refused by name: a set naming it is told what to write instead (decision 109).
    assertEquals(null, DieShape.ofId("tetrahedron"))
    assertEquals(DieShape.ElongatedSquareBipyramid, DieShape.ofId("elongated-square-bipyramid"))
  }

  @Test
  fun `a name from the catalogue resolves`() {
    assertEquals(DieShape.Icosahedron, DieShape.ofId("icosahedron"))
  }

  @Test
  fun `mesh is not in the catalogue in v1`() {
    assertNull(DieShape.ofId("mesh"))
  }

  @Test
  fun `a shape v1 does not ship does not resolve`() {
    assertNull(DieShape.ofId("rhombic-triacontahedron"))
  }

  @Test
  fun `a missing shape name does not resolve`() {
    assertNull(DieShape.ofId(null))
  }

  @Test
  fun `shape ids are lower-case slugs, because set files spell them out`() {
    val slug = Regex("[a-z][a-z-]*[a-z]")
    assertTrue(DieShape.entries.all { slug.matches(it.id) }, "shape ids must be slugs")
  }

  @Test
  fun `every read mode resolves from its file spelling`() {
    assertEquals(FaceRead.FaceUp, FaceRead.ofId("face-up"))
    assertEquals(null, FaceRead.ofId("vertex-up"))
  }

  @Test
  fun `an unknown read mode does not resolve`() {
    assertNull(FaceRead.ofId("edge-up"))
    assertNull(FaceRead.ofId(null))
  }

  @Test
  fun `shape ids are unique`() {
    assertEquals(DieShape.entries.size, DieShape.entries.distinctBy(DieShape::id).size)
    assertNotNull(DieShape.ofId("coin"))
  }
}
