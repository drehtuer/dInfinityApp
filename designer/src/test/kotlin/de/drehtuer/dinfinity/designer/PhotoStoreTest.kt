package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The photographs somebody has made tables of, as files
 * (`docs/tables.md`, "Your own photo").
 *
 * The store is the *record* the personal package is built from, which is why
 * these tests are mostly about what is left behind: a photo half-written, a
 * photo over the cap, a photo taken away again. A package built from a
 * half-written record is a package that stops validating, and it would take
 * the drawn dice with it.
 */
class PhotoStoreTest {
  @get:Rule
  val temporary: TemporaryFolder = TemporaryFolder()

  private lateinit var folder: File

  private fun store(limit: Int = PhotoTable.MAX_PHOTOS): PhotoStore {
    folder = temporary.newFolder("photos-${System.nanoTime()}")
    return PhotoStore(folder, limit = limit)
  }

  @Test
  fun `a photo written comes back with its name and its bytes`() {
    val photos = store()

    assertTrue(photos.add(TablePhoto("photo-oak", "Oak", Drawings.webp(64, 48))))

    assertEquals(listOf(TablePhoto("photo-oak", "Oak", Drawings.webp(64, 48))), photos.photos())
    assertEquals(setOf("photo-oak"), photos.ids())
  }

  @Test
  fun `photos come back in a fixed order, whatever the filesystem thinks`() {
    // The package is built from this list, and a package whose tables came out
    // in a different order on every reading would differ for no reason.
    val photos = store()
    listOf("photo-c", "photo-a", "photo-b").forEach { id ->
      photos.add(TablePhoto(id, id, Drawings.webp(8, 8)))
    }

    assertEquals(listOf("photo-a", "photo-b", "photo-c"), photos.photos().map(TablePhoto::id))
  }

  @Test
  fun `writing under an id that is already there replaces it`() {
    val photos = store()
    photos.add(TablePhoto("photo-oak", "Oak", Drawings.webp(8, 8)))

    assertTrue(photos.add(TablePhoto("photo-oak", "Oak again", Drawings.webp(16, 16))))

    assertEquals(1, photos.photos().size)
    assertEquals("Oak again", photos.photos().single().name)
  }

  @Test
  fun `the cap refuses the next one rather than dropping the oldest`() {
    // The opposite of what a draft does, and deliberately: a drawing nobody
    // has opened for months is a fair thing to drop, and the table somebody is
    // playing on tonight is not.
    val photos = store(limit = 2)
    photos.add(TablePhoto("photo-a", "A", Drawings.webp(8, 8)))
    photos.add(TablePhoto("photo-b", "B", Drawings.webp(8, 8)))

    assertFalse(photos.hasRoom())
    assertFalse(photos.add(TablePhoto("photo-c", "C", Drawings.webp(8, 8))))
    assertEquals(setOf("photo-a", "photo-b"), photos.ids())
  }

  @Test
  fun `a full store still takes a replacement for one it already has`() {
    val photos = store(limit = 1)
    photos.add(TablePhoto("photo-a", "A", Drawings.webp(8, 8)))

    assertTrue(photos.add(TablePhoto("photo-a", "A again", Drawings.webp(8, 8))))
  }

  @Test
  fun `forgetting one takes its picture and its name together`() {
    val photos = store()
    photos.add(TablePhoto("photo-oak", "Oak", Drawings.webp(8, 8)))

    photos.forget("photo-oak")

    assertTrue(photos.photos().isEmpty())
    assertTrue("something of the photo was left behind", folder.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun `forgetting one that is not there does nothing`() {
    val photos = store()
    photos.add(TablePhoto("photo-oak", "Oak", Drawings.webp(8, 8)))

    photos.forget("photo-elm")

    assertEquals(setOf("photo-oak"), photos.ids())
  }

  @Test
  fun `a photo whose name has gone is still a photo`() {
    // An unnamed look in the list is recoverable; a look that is not there is
    // not.
    val photos = store()
    photos.add(TablePhoto("photo-oak", "Oak", Drawings.webp(8, 8)))
    File(folder, "photo-oak.name").delete()

    assertEquals("photo-oak", photos.photos().single().name)
  }

  @Test
  fun `a picture with nothing in it is not a photo`() {
    val photos = store()
    folder.mkdirs()
    File(folder, "photo-empty.webp").writeBytes(ByteArray(0))

    assertTrue(photos.photos().isEmpty())
  }

  @Test
  fun `the stamp moves when the photos do, and not otherwise`() {
    val photos = store()
    val empty = photos.stamp()
    photos.add(TablePhoto("photo-oak", "Oak", Drawings.webp(8, 8)))
    val one = photos.stamp()

    assertNotEquals(empty, one)
    assertEquals("the stamp moved on its own", one, photos.stamp())

    photos.forget("photo-oak")
    assertEquals(empty, photos.stamp())
  }

  @Test
  fun `an id that is not a slug cannot name a file outside the folder`() {
    // It cannot happen — ids come out of `PhotoTable.idOf` — which is exactly
    // why it is worth checking here: the filesystem is the last place to find
    // out that it did.
    val photos = store()

    photos.add(TablePhoto("../escape", "Escape", Drawings.webp(8, 8)))

    assertTrue(
      "a photo was written outside its folder",
      temporary.root
        .listFiles()
        .orEmpty()
        .none { it.name.startsWith("escape") },
    )
    assertEquals(setOf("escape"), photos.ids())
  }

  @Test
  fun `a folder that cannot be written leaves nothing behind`() {
    val blocked = temporary.newFile("not-a-folder")
    val photos = PhotoStore(blocked)

    assertFalse(photos.add(TablePhoto("photo-oak", "Oak", Drawings.webp(8, 8))))
    assertTrue(photos.photos().isEmpty())
    assertTrue(blocked.isFile)
  }
}
