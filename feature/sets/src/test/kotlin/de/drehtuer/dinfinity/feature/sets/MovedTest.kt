package de.drehtuer.dinfinity.feature.sets

import de.drehtuer.dinfinity.dicesets.install.PackageMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether what a set was installed from has moved on
 * (`docs/dice-sets.md`, "Updates").
 *
 * The rule has one shape that is easy to get subtly wrong — **compare like
 * with like, and say nothing otherwise** — and getting it wrong sends somebody
 * to re-download a set that has not changed. That is what most of this file is
 * about.
 */
class MovedTest {
  @Test
  fun `a forge at a different commit has moved on`() {
    assertEquals(Moved.Yes, row(commit = "aaaa").moved(LatestCommit.At("bbbb")))
  }

  @Test
  fun `and one at the same commit has not`() {
    assertEquals(Moved.No, row(commit = "aaaa").moved(LatestCommit.At("aaaa")))
  }

  @Test
  fun `an archive whose etag has changed has moved on`() {
    // The plain-archive case: no commits to tell apart, so the file's own
    // identity is all there is.
    val installed = row(etag = "\"v1\"")

    assertEquals(Moved.Yes, installed.moved(LatestCommit.Stamped(etag = "\"v2\"", lastModified = null)))
  }

  @Test
  fun `and one whose etag is the same has not`() {
    val installed = row(etag = "\"v1\"")

    assertEquals(Moved.No, installed.moved(LatestCommit.Stamped(etag = "\"v1\"", lastModified = null)))
  }

  @Test
  fun `the etag wins over the date when both ends have both`() {
    // An `ETag` is the server's own statement about the bytes. A date is a
    // date, and a file rebuilt without changing is a file with a new one.
    val installed = row(etag = "\"v1\"", lastModified = "Mon, 01 Jan 2024 00:00:00 GMT")
    val rebuilt = LatestCommit.Stamped(etag = "\"v1\"", lastModified = "Tue, 02 Jan 2024 00:00:00 GMT")

    assertEquals(Moved.No, installed.moved(rebuilt))
  }

  @Test
  fun `the date is used when neither end has an etag`() {
    val installed = row(lastModified = "Mon, 01 Jan 2024 00:00:00 GMT")
    val later = LatestCommit.Stamped(etag = null, lastModified = "Tue, 02 Jan 2024 00:00:00 GMT")

    assertEquals(Moved.Yes, installed.moved(later))
    assertEquals(Moved.No, installed.moved(LatestCommit.Stamped(null, "Mon, 01 Jan 2024 00:00:00 GMT")))
  }

  @Test
  fun `a server that has stopped sending what was recorded says nothing, rather than saying yes`() {
    // The one that matters. A set installed with an `ETag`, against a server
    // that now sends only a date, is *unanswerable* — badging it would send
    // somebody to re-download a set that has not changed.
    val installed = row(etag = "\"v1\"")

    val answer = installed.moved(LatestCommit.Stamped(etag = null, lastModified = "Mon, 01 Jan 2024 00:00:00 GMT"))

    assertEquals(Moved.Unknown, answer)
  }

  @Test
  fun `and so does one that has started sending something the install never recorded`() {
    val installed = row(lastModified = "Mon, 01 Jan 2024 00:00:00 GMT")

    assertEquals(Moved.Unknown, installed.moved(LatestCommit.Stamped(etag = "\"v1\"", lastModified = null)))
  }

  @Test
  fun `a server that says neither says nothing`() {
    assertEquals(Moved.Unknown, row(etag = "\"v1\"").moved(LatestCommit.Stamped(null, null)))
  }

  @Test
  fun `a set installed before any of this was recorded is unanswerable`() {
    // Its `.meta.json` has a source and nothing to compare. Re-installing it
    // gives it one, which is the only honest way to fix it.
    assertEquals(Moved.Unknown, row().moved(LatestCommit.Stamped(etag = "\"v1\"", lastModified = null)))
    assertEquals(Moved.Unknown, row().moved(LatestCommit.At("aaaa")))
  }

  @Test
  fun `no answer at all is no answer`() {
    assertEquals(Moved.Unknown, row(commit = "aaaa").moved(LatestCommit.Unknown))
    assertEquals(Moved.Unknown, row(etag = "\"v1\"").moved(LatestCommit.Unknown))
  }

  @Test
  fun `a set with something to compare is worth asking about`() {
    // What decides whether the row is asked at all. A set installed from a
    // file has no source and is never asked.
    assertTrue(row(commit = "aaaa").checkable)
    assertTrue(row(etag = "\"v1\"").checkable)
    assertTrue(row(lastModified = "Mon, 01 Jan 2024 00:00:00 GMT").checkable)
    assertFalse("a set with nothing to compare was asked about", row().checkable)
    assertFalse(
      "a set installed from a file was asked about",
      rowOf(PackageMeta(commit = "aaaa")).checkable,
    )
  }

  private fun row(
    commit: String? = null,
    etag: String? = null,
    lastModified: String? = null,
  ): SetRow =
    rowOf(
      PackageMeta(
        source = "https://example.test/brass.zip",
        commit = commit,
        etag = etag,
        lastModified = lastModified,
      ),
    )

  /** A row that is only its `.meta.json`, which is all this rule looks at. */
  private fun rowOf(meta: PackageMeta): SetRow =
    SetRow(
      id = "brass",
      name = "Brass & Bone",
      set = null,
      report = emptyList(),
      meta = meta,
      folder = null,
      enabled = true,
    )
}
