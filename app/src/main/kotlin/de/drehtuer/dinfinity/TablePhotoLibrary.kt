package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.designer.MineSets
import de.drehtuer.dinfinity.designer.PhotoResult
import de.drehtuer.dinfinity.designer.PhotoScaler
import de.drehtuer.dinfinity.designer.PhotoSource
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.feature.tables.PhotoOutcome
import de.drehtuer.dinfinity.feature.tables.PickedPhoto
import de.drehtuer.dinfinity.feature.tables.TablePhotos
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Making a table out of a photograph, joined up
 * (`docs/tables.md`, "Your own photo").
 *
 * The three halves the table picker cannot have, each of them the
 * Android-shaped end of something that is otherwise plain: the [scaler], which
 * is the only part that decodes pixels; [mine], which writes the personal
 * package and runs the validator over it; and [refresh], because the catalogue
 * the picker lists from is only re-read by `SetLibrary.all()` — a table added
 * and not re-read is a table that is on disk and in no list.
 *
 * [refresh] is a function rather than the library itself, for the reason
 * `SetsPresenter` takes a `download` rather than an HTTP client: what this
 * needs is one call, and taking the whole of `SetLibrary` would be taking a
 * Room database into a test that is about a photograph.
 *
 * In `app/` for the reason `PackageFileReading` is: this is where a content URI
 * has already become a way of opening a stream, and where every "the app's own
 * wiring" decision already lives (`docs/architecture.md`, Modules).
 *
 * **Nothing here decides whether a photo is acceptable.** It scales, writes,
 * and turns whatever the validator said into lines a screen can show. A photo
 * that is refused leaves the phone exactly as it was, because [MineSets.addPhoto]
 * takes it back out again — this class has nothing to undo.
 */
class TablePhotoLibrary(
  private val mine: MineSets,
  private val refresh: suspend () -> Unit,
  private val scaler: PhotoScaler,
  private val io: CoroutineDispatcher,
) : TablePhotos {
  override suspend fun add(
    photo: PickedPhoto,
    name: String,
  ): PhotoOutcome {
    val result =
      withContext(io) {
        val image = scaler.scaled(PhotoSource { photo.open() }) ?: return@withContext null
        mine.addPhoto(name, image)
      }
    if (result is PhotoResult.Added) refresh()
    return outcome(result)
  }

  override suspend fun remove(pin: TablePin) {
    if (pin.setId != DiceSet.PERSONAL_ID) return
    withContext(io) { mine.removePhoto(pin.tableId) }
    refresh()
  }

  /**
   * What the table picker is told.
   *
   * A rejection keeps the validator's own lines, formatted the way a refused
   * install formats them — `diceset.toml:14: error: …` — because they say
   * which file and which line, and a photo that made a package invalid is
   * exactly the case where that matters.
   */
  private fun outcome(result: PhotoResult?): PhotoOutcome =
    when (result) {
      null -> PhotoOutcome.Refused(listOf(UNREADABLE))
      is PhotoResult.Added -> PhotoOutcome.Added(TablePin(DiceSet.PERSONAL_ID, result.look.id))
      is PhotoResult.Rejected -> PhotoOutcome.Refused(result.report.map(ValidationMessage::toString))
      is PhotoResult.NoRoom -> PhotoOutcome.Refused(listOf(noRoom(result.kept)))
      PhotoResult.Unnamed -> PhotoOutcome.Refused(listOf(UNNAMED))
      PhotoResult.NotWritten -> PhotoOutcome.Refused(listOf(NOT_WRITTEN))
    }

  private fun noRoom(kept: Int): String = "there is room for $kept photo tables, and that many are already kept"

  private companion object {
    const val UNREADABLE =
      "that file is not a picture this app can read, or it is too large to be scaled down into one"
    const val UNNAMED = "a table needs a name"
    const val NOT_WRITTEN = "it could not be written to this phone's storage"
  }
}
