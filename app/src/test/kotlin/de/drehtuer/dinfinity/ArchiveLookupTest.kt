package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.dicesets.install.ArchiveStamps
import de.drehtuer.dinfinity.feature.sets.LatestCommit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Which question a set's update check asks, and what the answer becomes
 * (`docs/dice-sets.md`, "Updates").
 *
 * The rules about what may be asked and what may be read out of a reply are
 * `ArchiveStamps`' and `RefResolver`'s; what is worth asserting here is the
 * two things this layer decides — which of them to ask, and how their answers
 * map onto the one word a list of sets can show.
 */
class ArchiveLookupTest {
  @Test
  fun `a stamp becomes something to compare`() =
    runTest {
      val lookup = ArchiveLookup { ArchiveStamps.Result.Stamped("\"v1\"", "Mon, 01 Jan 2024 00:00:00 GMT") }

      val latest = lookup.latest("https://example.test/brass.zip")

      assertEquals(LatestCommit.Stamped("\"v1\"", "Mon, 01 Jan 2024 00:00:00 GMT"), latest)
    }

  @Test
  fun `and silence becomes nothing can be said`() =
    runTest {
      // A server that is down, one that sends neither header, and a link that
      // is not https are one sentence to somebody looking at a list.
      val lookup = ArchiveLookup { ArchiveStamps.Result.Silent }

      assertEquals(LatestCommit.Unknown, lookup.latest("https://example.test/brass.zip"))
    }

  @Test
  fun `a lookup with nothing handed to it still builds`() {
    // The default reaches a real server, so nothing here asks it anything.
    // Building one is what the activity does.
    assertNotNull(ArchiveLookup())
  }

  @Test
  fun `a set with a commit is asked about its commit`() =
    runTest {
      // What the install recorded decides it, not the link read a second time.
      val asked = mutableListOf<String>()

      val latest =
        latestOf(
          source = "https://codeberg.org/ada/brass",
          commit = "aaaa",
          commits = { source ->
            asked += "forge:$source"
            LatestCommit.At("bbbb")
          },
          stamps = { source ->
            asked += "archive:$source"
            LatestCommit.Unknown
          },
        )

      assertEquals(listOf("forge:https://codeberg.org/ada/brass"), asked)
      assertEquals(LatestCommit.At("bbbb"), latest)
    }

  @Test
  fun `and one without is asked about its file`() =
    runTest {
      val asked = mutableListOf<String>()

      val latest =
        latestOf(
          source = "https://example.test/brass.zip",
          commit = null,
          commits = { source ->
            asked += "forge:$source"
            LatestCommit.Unknown
          },
          stamps = { source ->
            asked += "archive:$source"
            LatestCommit.Stamped("\"v1\"", null)
          },
        )

      assertEquals(listOf("archive:https://example.test/brass.zip"), asked)
      assertEquals(LatestCommit.Stamped("\"v1\"", null), latest)
    }

  @Test
  fun `only one of the two is ever asked`() =
    runTest {
      // Two requests for one row would be two chances for a server to be slow,
      // and the second could only ever say less than the first.
      var calls = 0

      latestOf(
        source = "https://codeberg.org/ada/brass",
        commit = "aaaa",
        commits = {
          calls++
          LatestCommit.Unknown
        },
        stamps = {
          calls++
          LatestCommit.Unknown
        },
      )

      assertEquals(1, calls)
    }
}
