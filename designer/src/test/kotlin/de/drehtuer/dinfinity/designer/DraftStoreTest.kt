package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Drafts on disk (`docs/face-designer.md`, "Drawing tools" and "Constraints").
 *
 * Against real files rather than a fake filesystem: what is worth asserting
 * here is about *files* — that one die's drawing is one file, that a write
 * that fails leaves the last one intact, and that fifty is fifty.
 */
class DraftStoreTest {
  private val directory: File = Files.createTempDirectory("dinfinity-drafts").toFile()
  private val store = DraftStore(directory)
  private val d6 = Die.standard(id = "d6", shape = DieShape.Cube)
  private val d4 = Die.standard(id = "d4", shape = DieShape.Tetrahedron)

  @After
  fun clean() {
    directory.deleteRecursively()
  }

  @Test
  fun `a drawing put down is the drawing picked up`() {
    val drawn = Draft(die = d6).onFace(1) { it.draw(stroke()) }

    store.save(drawn)

    assertEquals(drawn.face(1).marks, store.load(d6).face(1).marks)
  }

  @Test
  fun `a die nobody has drawn on opens blank rather than failing`() {
    assertTrue(store.load(d6).blank)
  }

  @Test
  fun `each die keeps its own drawing`() {
    store.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })

    assertTrue("the d4 opened on the d6's drawing", store.load(d4).blank)
    assertFalse(store.load(d6).blank)
  }

  @Test
  fun `saving a blank drawing takes the draft away rather than keeping an empty one`() {
    // Otherwise opening the designer and leaving it would push somebody's
    // oldest real drawing over the limit.
    store.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })

    store.save(Draft(die = d6))

    assertTrue(store.load(d6).blank)
    assertEquals(emptyList<String>(), store.known())
  }

  @Test
  fun `a file that is not a draft is a blank canvas, not a crash`() {
    store.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })
    File(directory, "d6.json").writeText("half a file, cut off at the")

    assertTrue(store.load(d6).blank)
  }

  @Test
  fun `fifty drafts is fifty, and the one nobody touched for longest goes`() {
    // `docs/face-designer.md` caps them to bound storage and export time.
    val store = DraftStore(directory, limit = SMALL)
    val dice = (0 until SMALL + 1).map { Die.standard(id = "d$it-x", shape = DieShape.Cube) }
    dice.forEachIndexed { index, die ->
      store.save(Draft(die = die).onFace(0) { it.draw(stroke()) })
      // Stamped by hand: a filesystem whose timestamps are a whole second
      // apart cannot tell four saves in one millisecond apart, and this test
      // is about the order rather than about the clock.
      File(directory, "${die.id}.json").setLastModified(STAMP + index * A_MINUTE)
    }

    assertEquals(SMALL, store.known().size)
    assertTrue("the oldest drawing outlived the cap", store.load(dice.first()).blank)
    assertFalse("the newest drawing was dropped", store.load(dice.last()).blank)
  }

  @Test
  fun `the drawing just saved is never the one dropped`() {
    // A filesystem that stamps every file in the same second can call the file
    // just written the oldest of fifty. Losing the drawing somebody is looking
    // at is the one outcome this must not have.
    val store = DraftStore(directory, limit = SMALL)
    val dice = (0 until SMALL + 1).map { Die.standard(id = "d$it-x", shape = DieShape.Cube) }
    dice.forEach { die ->
      store.save(Draft(die = die).onFace(0) { it.draw(stroke()) })
      File(directory, "${die.id}.json").setLastModified(STAMP)
    }

    assertFalse("the drawing being worked on was dropped to make room", store.load(dice.last()).blank)
  }

  @Test
  fun `a draft can be taken away`() {
    store.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })

    assertTrue("taking the drawing away said it was still there", store.forget("d6"))
    assertTrue(store.load(d6).blank)
  }

  @Test
  fun `a die that never had a drawing is already forgotten`() {
    // `delete` answers false for a file it could not remove and for one that
    // was never there, and only the first of those is a failure.
    assertTrue("a die with no drawing was reported as still having one", store.forget("d6"))
  }

  @Test
  fun `a drawing that will not go is not called gone`() {
    // A file the filesystem refuses to remove. Saying it was forgotten would be
    // a lie the very next `load` exposes, so the answer is whether it is there
    // now rather than whether this call is what removed it.
    stuck("d6.json")

    assertFalse("a drawing that is still on disk was reported gone", store.forget("d6"))
  }

  @Test
  fun `a write that cannot be made leaves the last drawing where it was`() {
    // The half-written file goes to a neighbour and is renamed over the real
    // one, so a write that throws must take the neighbour with it and leave
    // the real one alone — even when the neighbour will not delete either.
    store.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })
    val kept = store.load(d6).face(0).marks
    stuck("d6.json.part")

    store.save(Draft(die = d6).onFace(0) { it.draw(stroke()).draw(stroke()) })

    assertEquals("the drawing that was there was lost to a write that failed", kept, store.load(d6).face(0).marks)
  }

  @Test
  fun `a folder that does not exist yet is made rather than refused`() {
    // A fresh install, where nothing has ever been drawn.
    val fresh = DraftStore(File(directory, "never/been/here"))

    fresh.save(Draft(die = d6).onFace(0) { it.draw(stroke()) })

    assertFalse(fresh.load(d6).blank)
  }

  @Test
  fun `an id that is not a slug cannot name a file outside the folder`() {
    // Die ids are slugs and nothing else can reach here — but a name that
    // reaches the filesystem is the last place to find out otherwise.
    val odd = Die.standard(id = "d6", shape = DieShape.Cube).copy(id = "../../d6")

    store.save(Draft(die = odd).onFace(0) { it.draw(stroke()) })

    assertEquals(listOf("d6.json"), directory.listFiles().orEmpty().map { it.name })
  }

  @Test
  fun `drafts that are not kept anywhere are a designer whose work lasts one sitting`() {
    // The default, and what a test uses. It is a real answer rather than a
    // stub: a screen given no folder draws, and loses the drawing when it
    // closes, rather than failing.
    val drawn = Draft(die = d6).onFace(0) { it.draw(stroke()) }

    Drafts.NONE.save(drawn)

    assertTrue("something was kept by the drafts that keep nothing", Drafts.NONE.load(d6).blank)
  }

  /**
   * Puts something at [name] that the filesystem will not delete or write over.
   *
   * A non-empty directory, which `delete` refuses for anybody — a read-only
   * folder would not do: these run as root in the container, and root removes
   * what it likes.
   */
  private fun stuck(name: String) {
    val blocked = File(directory, name)
    blocked.mkdirs()
    File(blocked, "in-the-way").writeText("x")
  }

  private fun stroke() = Stroke(dots = listOf(Dot(0.1f, 0.2f), Dot(0.3f, 0.4f)), colorArgb = INK, width = 0.02f)

  private companion object {
    const val INK = 0xFF000000.toInt()

    /** Enough to show the cap without writing fifty files in a test. */
    const val SMALL = 3
    const val STAMP = 1_700_000_000_000L
    const val A_MINUTE = 60_000L
  }
}
