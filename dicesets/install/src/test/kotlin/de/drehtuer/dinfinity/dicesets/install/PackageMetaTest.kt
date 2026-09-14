package de.drehtuer.dinfinity.dicesets.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The note the app writes beside an installed package
 * (`docs/architecture.md`, "Storage layout").
 *
 * It is a file on a device the app does not own, so the tests are mostly about
 * what happens when it is not what was written: an editor, a backup restore or
 * a half-finished write can leave anything at all in it, and none of that is a
 * reason to lose a working package.
 */
class PackageMetaTest {
  @Test
  fun `what is written is what is read back`() {
    val meta =
      PackageMeta(
        source = "https://example.invalid/brass.zip",
        sha256 = "abc123",
        commit = "deadbeef",
        version = "2.1.0",
        installedAtEpochMs = 1_700_000_000_000L,
      )

    assertEquals(meta, PackageMeta.read(meta.asJson()))
  }

  @Test
  fun `a source with a quote, a backslash and a newline survives`() {
    // The reason this goes through a parser in both directions. Assembling
    // the JSON by hand handled quotes and backslashes and nothing else, so a
    // control character wrote a file that could not be read back.
    val awkward = "https://example.invalid/a\"b\\c\nd\te"

    val read = PackageMeta.read(PackageMeta(source = awkward).asJson())

    assertEquals(awkward, read.source)
  }

  @Test
  fun `an empty meta is written and read without inventing fields`() {
    assertEquals(PackageMeta.Unknown, PackageMeta.read(PackageMeta.Unknown.asJson()))
  }

  @Test
  fun `text that is not json at all is unknown rather than an exception`() {
    assertEquals(PackageMeta.Unknown, PackageMeta.read("{ this is not json"))
    assertEquals(PackageMeta.Unknown, PackageMeta.read(""))
    assertEquals(PackageMeta.Unknown, PackageMeta.read("[1, 2, 3]"))
  }

  @Test
  fun `fields an older version never wrote are absent, not guessed`() {
    val read = PackageMeta.read("""{"source": "brass.zip"}""")

    assertEquals("brass.zip", read.source)
    assertNull(read.sha256)
    assertNull(read.commit)
    assertNull(read.installedAtEpochMs)
  }

  @Test
  fun `a field of the wrong kind is ignored rather than coerced`() {
    // Quietly reading a number as its digits is how one field comes to mean
    // two things.
    val read = PackageMeta.read("""{"source": 42, "installedAt": "yesterday", "commit": {"a": 1}}""")

    assertNull("a number was read as a source", read.source)
    assertNull("a word was read as a time", read.installedAtEpochMs)
    assertNull("an object was read as a commit", read.commit)
  }

  @Test
  fun `a null is the same as not being there`() {
    val read = PackageMeta.read("""{"source": null, "version": "1.0.0"}""")

    assertNull(read.source)
    assertEquals("1.0.0", read.version)
  }
}
