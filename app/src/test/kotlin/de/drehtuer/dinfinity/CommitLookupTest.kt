package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.dicesets.install.InstallSource
import de.drehtuer.dinfinity.dicesets.install.RefResolver
import de.drehtuer.dinfinity.feature.sets.LatestCommit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a forge's answer becomes (`docs/dice-sets.md`, "Updates").
 *
 * The asking is `RefResolver`'s and is tested where it lives, against a real
 * HTTPS server. What is left here is the **mapping**, and the mapping is the
 * part with an opinion in it: three answers become two, because to somebody
 * looking at a list of sets "this is not a forge" and "this forge did not
 * answer" are the same sentence.
 */
class CommitLookupTest {
  @Test
  fun `a commit is a commit`() =
    runTest {
      val lookup = CommitLookup { RefResolver.Result.Resolved("abc123") }

      assertEquals(LatestCommit.At("abc123"), lookup.latest(REPOSITORY))
    }

  @Test
  fun `a forge that did not answer says nothing can be said`() =
    runTest {
      val lookup = CommitLookup { RefResolver.Result.Failed("the forge could not be reached") }

      assertEquals(LatestCommit.Unknown, lookup.latest(REPOSITORY))
    }

  @Test
  fun `and so does a source that is not a forge`() =
    runTest {
      val lookup = CommitLookup { RefResolver.Result.NotAForge }

      assertEquals(LatestCommit.Unknown, lookup.latest(REPOSITORY))
    }

  @Test
  fun `a link that is not a source at all is never asked about`() =
    runTest {
      // The gate before the request: a `.txt` is not somewhere a set comes
      // from, so there is nothing to ask and nobody is asked.
      val asked = mutableListOf<InstallSource>()
      val lookup =
        CommitLookup { source ->
          asked += source
          RefResolver.Result.Resolved("abc123")
        }

      assertEquals(LatestCommit.Unknown, lookup.latest("https://example.org/dice/brass.txt"))
      assertTrue("a link that is not a source was still asked about: $asked", asked.isEmpty())
    }

  @Test
  fun `plain http is not a source either`() =
    runTest {
      val asked = mutableListOf<InstallSource>()
      val lookup =
        CommitLookup { source ->
          asked += source
          RefResolver.Result.Resolved("abc123")
        }

      assertEquals(LatestCommit.Unknown, lookup.latest("http://codeberg.org/ada/brass"))
      assertTrue("an http link reached the forge: $asked", asked.isEmpty())
    }

  @Test
  fun `the source handed to the forge is the one the link names`() =
    runTest {
      val asked = mutableListOf<InstallSource>()
      val lookup =
        CommitLookup { source ->
          asked += source
          RefResolver.Result.Resolved("abc123")
        }

      lookup.latest(REPOSITORY)

      assertEquals(listOf(InstallSource.Kind.Gitea), asked.map(InstallSource::kind))
    }

  private companion object {
    const val REPOSITORY = "https://codeberg.org/ada/brass-and-bone"
  }
}
