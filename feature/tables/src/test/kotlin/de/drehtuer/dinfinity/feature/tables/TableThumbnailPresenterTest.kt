package de.drehtuer.dinfinity.feature.tables

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Which looks are asked for a picture, when, and what happens to the pictures
 * (`docs/tables.md`, "Thumbnails").
 *
 * Robolectric rather than a plain JVM test only because an `ImageBitmap` is an
 * Android bitmap underneath. Nothing here draws one: the source is a fake, and
 * what is being asked is the screen's side of the bargain — that a row asks
 * once however often it is composed, that a look that goes takes its picture
 * with it, and that a build with nothing wired behind it simply never asks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TableThumbnailPresenterTest {
  private val oak = TablePin(BuiltinDiceSet.set.id, "oak")

  @Test
  fun `nothing is asked for until a row says it is on screen`() {
    // A LazyColumn composes what fits, and a picture costs a scene and a wait
    // on the GPU. Somebody with thirty looks pays for the six they can see.
    val source = FakeThumbnails()

    presenter(thumbnails = source)

    assertTrue("every look was drawn before anything was on screen", source.asked.isEmpty())
  }

  @Test
  fun `a row that is on screen gets its look drawn`() {
    val source = FakeThumbnails()
    val presenter = presenter(thumbnails = source)

    presenter.wants(oak)

    assertEquals(listOf(oak), source.asked)
  }

  @Test
  fun `and asking again costs nothing, because a row is composed many times`() {
    val source = FakeThumbnails()
    val presenter = presenter(thumbnails = source)

    repeat(5) { presenter.wants(oak) }

    assertEquals(listOf(oak), source.asked)
  }

  @Test
  fun `the picture lands against the look it is of`() {
    val source = FakeThumbnails()
    val presenter = presenter(thumbnails = source)
    presenter.wants(oak)

    val picture = source.draw(oak)

    assertEquals(picture, presenter.state.thumbnails[oak])
  }

  @Test
  fun `until then the look has no picture, which is the ordinary case`() {
    val source = FakeThumbnails()
    val presenter = presenter(thumbnails = source)

    presenter.wants(oak)

    assertNull(presenter.state.thumbnails[oak])
  }

  @Test
  fun `a device that never answers leaves every row without one`() {
    val source = FakeThumbnails(answer = false)
    val presenter = presenter(thumbnails = source)

    presenter.state.tables.forEach { presenter.wants(it.pin) }

    assertTrue(presenter.state.thumbnails.isEmpty())
    assertEquals(presenter.state.tables.size, source.asked.size)
  }

  @Test
  fun `with nothing wired to draw one, nothing is asked at all`() {
    val presenter = presenter(thumbnails = null)

    presenter.wants(oak)

    assertTrue(presenter.state.thumbnails.isEmpty())
  }

  @Test
  fun `a look that is not in the list is not asked about`() {
    // A pin from a package that has been removed since the row was composed.
    val source = FakeThumbnails()
    val presenter = presenter(thumbnails = source)

    presenter.wants(TablePin("gone", "nowhere"))

    assertTrue(source.asked.isEmpty())
  }

  @Test
  fun `a removed photo table takes its picture with it`() {
    val photos = FakePhotos()
    val source = FakeThumbnails()
    val presenter = presenter(photos = photos, thumbnails = source)
    presenter.usePhoto()
    presenter.picked(pickedPhoto(label = "meadow.jpg"))
    presenter.confirmPhoto()
    val own = TablePin(DiceSet.PERSONAL_ID, "photo-meadow")
    presenter.wants(own)
    source.draw(own)
    assertNotNull(presenter.state.thumbnails[own])

    presenter.removePhoto(own)

    assertNull("the picture of a table that is gone was kept", presenter.state.thumbnails[own])
  }

  @Test
  fun `and it is drawn afresh if another photo takes its id`() {
    val photos = FakePhotos()
    val source = FakeThumbnails()
    val presenter = presenter(photos = photos, thumbnails = source)
    presenter.usePhoto()
    presenter.picked(pickedPhoto(label = "meadow.jpg"))
    presenter.confirmPhoto()
    val own = TablePin(DiceSet.PERSONAL_ID, "photo-meadow")
    presenter.wants(own)
    presenter.removePhoto(own)

    presenter.usePhoto()
    presenter.picked(pickedPhoto(label = "meadow.jpg"))
    presenter.confirmPhoto()
    presenter.wants(own)

    assertEquals("the second photograph was shown the first one's picture", listOf(own, own), source.asked)
  }

  @Test
  fun `the pictures of looks that are still there are kept when the list changes`() {
    val photos = FakePhotos()
    val source = FakeThumbnails()
    val presenter = presenter(photos = photos, thumbnails = source)
    presenter.wants(oak)
    source.draw(oak)

    presenter.usePhoto()
    presenter.picked(pickedPhoto(label = "meadow.jpg"))
    presenter.confirmPhoto()

    assertNotNull("a bundled look lost its picture when a photo landed", presenter.state.thumbnails[oak])
  }

  @Test
  fun `a screen with no thumbnails is a screen that still lists every look`() {
    val presenter = presenter(thumbnails = null)

    assertFalse(presenter.state.empty)
    assertEquals(BuiltinDiceSet.set.tables.size, presenter.state.tables.size)
  }

  @Test
  fun `the box is taller than it is wide, because the tray's long side runs up the screen`() {
    assertTrue(TableThumbnailBox.HEIGHT > TableThumbnailBox.WIDTH)
  }

  @Test
  fun `the box in pixels follows the screen it is drawn on`() {
    assertEquals(44, TableThumbnailBox.widthPx(density = 1.0f))
    assertEquals(132, TableThumbnailBox.widthPx(density = 3.0f))
    assertEquals(64, TableThumbnailBox.heightPx(density = 1.0f))
    assertEquals(192, TableThumbnailBox.heightPx(density = 3.0f))
  }

  @Test
  fun `and it is never nothing, whatever a screen claims its density is`() {
    assertEquals(1, TableThumbnailBox.widthPx(density = 0.0f))
    assertEquals(1, TableThumbnailBox.heightPx(density = 0.001f))
  }

  private fun presenter(
    sets: List<DiceSet> = listOf(BuiltinDiceSet.set),
    photos: FakePhotos? = null,
    thumbnails: TableThumbnails? = FakeThumbnails(),
  ) = TablesPresenter(
    sets = { sets + photos?.personal().orEmpty() },
    chosen = null,
    onChosen = {},
    scope = CoroutineScope(Dispatchers.Unconfined),
    photos = photos,
    thumbnails = thumbnails,
  )
}
