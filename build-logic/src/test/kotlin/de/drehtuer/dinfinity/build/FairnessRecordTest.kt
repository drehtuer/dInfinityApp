package de.drehtuer.dinfinity.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The release's fairness gate: the physics as one number, and what a record has to say about it. */
class FairnessRecordTest {
  @get:Rule
  val folder = TemporaryFolder()

  private fun file(
    path: String,
    text: String,
  ): File = File(folder.root, path).apply {
    parentFile.mkdirs()
    writeText(text)
  }

  @Test
  fun `the fingerprint is the files, not the order they are listed in or the line endings they were saved with`() {
    val a = file("a/one.kt", "val x = 1\n")
    val b = file("b/two.kt", "val y = 2\n")
    val one = PhysicsFingerprint.of(folder.root, listOf(a, b))

    assertEquals(one, PhysicsFingerprint.of(folder.root, listOf(b, a)))
    b.writeText("val y = 2\r\n")
    assertEquals(one, PhysicsFingerprint.of(folder.root, listOf(a, b)))
    assertEquals(64, one.length)
  }

  @Test
  fun `any edit to the physics, a comment included, or a file renamed, is a new fingerprint`() {
    val a = file("a/one.kt", "val x = 1\n")
    val before = PhysicsFingerprint.of(folder.root, listOf(a))

    a.writeText("val x = 1 // still one\n")
    assertNotEquals(before, PhysicsFingerprint.of(folder.root, listOf(a)))
    val moved = file("c/one.kt", "val x = 1\n")
    assertNotEquals(before, PhysicsFingerprint.of(folder.root, listOf(moved)))
  }

  @Test
  fun `a passing hundred-thousand-roll run of this physics clears the release`() {
    assertEquals(emptyList<String>(), passing().problemsFor("abc"))
  }

  @Test
  fun `a run of other physics, a failed run, a short run or a rounded one each stop it, and say why`() {
    fun problems(change: Map<String, String>) = passing().copy(fields = passing().fields + change).problemsFor("abc")

    assertTrue(problems(mapOf("fingerprint" to "old")).single().contains("physics changed"))
    assertTrue(problems(mapOf("verdict" to "fail")).single().contains("did not pass"))
    assertTrue(problems(mapOf("rolls" to "200")).single().contains("200"))
    assertTrue(problems(mapOf("rolls" to "many")).single().contains("no"))
    assertTrue(problems(mapOf("edge rounding" to "0.06")).single().contains("0.06"))
    assertTrue(passing().copy(shapes = emptyList()).problemsFor("abc").single().contains("no shapes"))
    assertEquals(5, FairnessRecord(emptyMap(), emptyList()).problemsFor("abc").size)
  }

  @Test
  fun `a record reads back as it was written, comments and damaged lines skipped`() {
    val record = passing()
    assertEquals(record, FairnessRecord.parse(record.format()))
    assertTrue(record.format().startsWith("#"))

    val damaged = FairnessRecord.parse("# a note\nnot a line\nverdict: pass\n: nothing\nshape: coin: n=1\n")
    assertEquals(mapOf("verdict" to "pass"), damaged.fields)
    assertEquals(listOf("coin: n=1"), damaged.shapes)
  }

  private fun passing() =
    FairnessRecord(
      fields =
        linkedMapOf(
          "fingerprint" to "abc",
          "rolls" to "100000",
          "verdict" to "pass",
          "edge rounding" to "default",
        ),
      shapes = listOf("coin: n=100000 chi2=0.80 limit=10.83"),
    )
}
