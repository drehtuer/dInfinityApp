package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.Stroke
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The designer's drafts, written off the thread that drew them
 * (`docs/face-designer.md`, "Drawing tools").
 *
 * What is worth asserting here is the seam and not the store, which has its
 * own tests: that saving does not happen on the caller's thread, that it
 * happens, and that reading is *not* moved — a draft that arrived a frame
 * after the canvas would be a canvas that flickers from blank to drawn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedDraftsTest {
  private val directory: File = Files.createTempDirectory("dinfinity-saved-drafts").toFile()
  private val store = DraftStore(directory)
  private val d6 = Die.standard(id = "d6", shape = DieShape.Cube)

  @After
  fun clean() {
    directory.deleteRecursively()
  }

  @Test
  fun `a drawing is not written on the thread it was drawn on`() =
    runTest {
      val scope = TestScope(StandardTestDispatcher(testScheduler))
      val drafts = SavedDrafts(store, scope)

      drafts.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })

      assertTrue("the finger waited for the disk", store.load(d6).blank)
    }

  @Test
  fun `and then it is`() =
    runTest {
      val scope = TestScope(StandardTestDispatcher(testScheduler))
      val drafts = SavedDrafts(store, scope)

      drafts.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })
      scope.advanceUntilIdle()

      assertFalse("the drawing never reached the disk", store.load(d6).blank)
    }

  @Test
  fun `reading is not moved, because a canvas that fills in a frame later flickers`() =
    runTest {
      val scope = TestScope(StandardTestDispatcher(testScheduler))
      store.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })

      val loaded = SavedDrafts(store, scope).load(d6)

      assertFalse("the screen would have opened blank on a die that had been drawn on", loaded.blank)
    }

  private fun stroke() = Stroke(dots = listOf(Dot(0.1f, 0.2f), Dot(0.3f, 0.4f)), colorArgb = INK, width = 0.02f)

  private companion object {
    const val INK = 0xFF000000.toInt()
  }
}
