package de.drehtuer.dinfinity.feature.tables

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.designer.PhotoTable
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Use a photo", as the table picker sees it
 * (`docs/tables.md`, "Your own photo"; design option `1u`).
 *
 * Nothing here decodes or writes anything: the presenter's part is the sheet,
 * the name, and what it does with the answer. Whether a photo really becomes a
 * valid package is `designer`'s, and `MinePhotoTablesTest` asserts it against
 * the real validator.
 */
class TablePhotoPresenterTest {
  private val photos = FakePhotos()

  @Test
  fun `the control is absent when nothing was wired to make a photo a table`() {
    // A button that cannot work is worse than no button. Built without the
    // argument at all, because that is how a screen with no photo library is
    // wired — the parameter defaults to none rather than being passed null.
    val unwired =
      TablesPresenter(
        sets = { listOf(BuiltinDiceSet.set) },
        chosen = null,
        onChosen = {},
        scope = CoroutineScope(Dispatchers.Unconfined),
      )

    assertFalse(unwired.state.photosOffered)
    assertTrue(presenter().state.photosOffered)
  }

  @Test
  fun `a row is the player's own only when it says so`() {
    // The default, which is what every look from a downloaded package is.
    val row = TableChoice(pin = TablePin("brass", "bone"), look = BuiltinDiceSet.set.tables.first(), setName = "Brass")

    assertFalse(row.own)
  }

  @Test
  fun `the sheet opens and shuts, and shuts on nothing being wired`() {
    val presenter = presenter()

    presenter.usePhoto()
    assertEquals(PhotoDraft(), presenter.state.adding)

    presenter.dismissPhoto()
    assertNull(presenter.state.adding)

    val unwired = presenter(photos = null)
    unwired.usePhoto()
    assertNull("a sheet opened with nothing behind it", unwired.state.adding)
  }

  @Test
  fun `the file's own name is what the field starts from`() {
    val presenter = presenter()
    presenter.usePhoto()

    presenter.picked(pickedPhoto(label = "oak_table-02.jpg"))

    assertEquals("Oak table 02", presenter.state.adding?.name)
    assertEquals(
      "oak_table-02.jpg",
      presenter.state.adding
        ?.picked
        ?.label,
    )
  }

  @Test
  fun `choosing a second file does not undo a rename`() {
    val presenter = presenter()
    presenter.usePhoto()
    presenter.picked(pickedPhoto(label = "oak.jpg"))
    presenter.namePhoto("The good table")

    presenter.picked(pickedPhoto(label = "elm.jpg"))

    assertEquals("The good table", presenter.state.adding?.name)
  }

  @Test
  fun `a name is no longer than a table name may be`() {
    val presenter = presenter()
    presenter.usePhoto()

    presenter.namePhoto("z".repeat(500))

    assertEquals(
      PhotoTable.MAX_NAME_LENGTH,
      presenter.state.adding
        ?.name
        ?.length,
    )
  }

  @Test
  fun `nothing is ready until there is both a file and a name`() {
    val presenter = presenter()
    presenter.usePhoto()
    assertFalse(presenter.state.adding!!.ready)

    presenter.picked(pickedPhoto(label = "___.jpg"))
    assertFalse("a photo with no name was ready", presenter.state.adding!!.ready)

    presenter.namePhoto("Oak")
    assertTrue(presenter.state.adding!!.ready)
  }

  @Test
  fun `use this photo does nothing while it is not ready`() {
    val presenter = presenter()
    presenter.usePhoto()
    presenter.namePhoto("Oak")

    presenter.confirmPhoto()

    assertTrue("a photo was added with no file behind it", photos.asked.isEmpty())
  }

  @Test
  fun `a photo becomes one more row of the same list, and the one being played on`() {
    val chosen = mutableListOf<TablePin>()
    val presenter = presenter(onChosen = chosen::add)
    presenter.usePhoto()
    presenter.picked(pickedPhoto())
    presenter.namePhoto("Oak")

    presenter.confirmPhoto()

    val added = TablePin(DiceSet.PERSONAL_ID, "photo-oak")
    assertEquals(listOf("oak_table.jpg" to "Oak"), photos.asked)
    assertNull("the sheet stayed open after a photo landed", presenter.state.adding)
    assertTrue("the new table is not in the list", presenter.state.tables.any { it.pin == added })
    assertEquals(added, presenter.state.chosen)
    assertEquals("the new table was not remembered as the default", listOf(added), chosen)
  }

  @Test
  fun `a photo table is the player's own, and a packaged one is not`() {
    val presenter = presenter()
    presenter.usePhoto()
    presenter.picked(pickedPhoto())
    presenter.namePhoto("Oak")
    presenter.confirmPhoto()

    assertEquals(1, presenter.state.photos)
    assertTrue(
      presenter.state.tables
        .filterNot(TableChoice::own)
        .isNotEmpty(),
    )
  }

  @Test
  fun `a refusal stays in the sheet with every reason it came with`() {
    val refusing = FakePhotos(refuse = listOf("diceset.toml:11: error: it is 4096 wide", "and it is 4096 tall"))
    val presenter = presenter(photos = refusing)
    presenter.usePhoto()
    presenter.picked(pickedPhoto())
    presenter.namePhoto("Too big")

    presenter.confirmPhoto()

    val draft = presenter.state.adding
    assertEquals(2, draft?.refused?.size)
    assertEquals("Too big", draft?.name)
    assertFalse("the sheet was left looking busy", draft!!.working)
  }

  @Test
  fun `typing again clears the refusal, because it is about what was typed before`() {
    val presenter = presenter(photos = FakePhotos(refuse = listOf("no")))
    presenter.usePhoto()
    presenter.picked(pickedPhoto())
    presenter.namePhoto("Oak")
    presenter.confirmPhoto()

    presenter.namePhoto("Oak again")

    assertTrue(
      presenter.state.adding!!
        .refused
        .isEmpty(),
    )
  }

  @Test
  fun `removing a photo takes its row away and falls back to a table there is`() {
    val presenter = presenter()
    presenter.usePhoto()
    presenter.picked(pickedPhoto())
    presenter.namePhoto("Oak")
    presenter.confirmPhoto()
    val added = TablePin(DiceSet.PERSONAL_ID, "photo-oak")

    presenter.removePhoto(added)

    assertEquals(listOf(added), photos.removed)
    assertFalse("the removed table is still listed", presenter.state.tables.any { it.pin == added })
    assertEquals(
      presenter.state.tables
        .first()
        .pin,
      presenter.state.chosen,
    )
  }

  @Test
  fun `only a photo table can be removed from here`() {
    // Every other look belongs to a package, and a package is removed on the
    // screen that is about packages.
    val presenter = presenter()

    presenter.removePhoto(TablePin(BuiltinDiceSet.set.id, "oak"))

    assertTrue("a packaged look was removed from the table picker", photos.removed.isEmpty())
  }

  @Test
  fun `with nothing wired, removing does nothing`() {
    val presenter = presenter(photos = null)

    presenter.removePhoto(TablePin(DiceSet.PERSONAL_ID, "photo-oak"))

    assertTrue(photos.removed.isEmpty())
  }

  @Test
  fun `the room left is counted against what a package can hold`() {
    val presenter = presenter()
    assertTrue(presenter.state.roomForAPhoto)

    repeat(PhotoTable.MAX_PHOTOS) { index ->
      presenter.usePhoto()
      presenter.picked(pickedPhoto())
      presenter.namePhoto("Photo $index")
      presenter.confirmPhoto()
    }

    assertEquals(PhotoTable.MAX_PHOTOS, presenter.state.photos)
    assertFalse(presenter.state.roomForAPhoto)
  }

  private fun presenter(
    photos: FakePhotos? = this.photos,
    onChosen: (TablePin) -> Unit = {},
  ) = TablesPresenter(
    // Asked again after every change, the way the real one is: the catalogue
    // has been re-read by the time a photo comes back.
    sets = { listOf(BuiltinDiceSet.set) + photos?.personal().orEmpty() },
    chosen = null,
    onChosen = onChosen,
    // Unconfined, so a photo that is "added" has landed by the time the next
    // line asserts on it. Nothing here is about which thread anything is on.
    scope = CoroutineScope(Dispatchers.Unconfined),
    photos = photos,
  )
}
