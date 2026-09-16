package de.drehtuer.dinfinity.feature.tables

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.designer.PhotoTable
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * A photo library that writes nothing, for the screen that cannot tell.
 *
 * The presenter's whole part in "use a photo" is asking and then listening, so
 * what a test needs is something that answers — and a real one would want a
 * decoder, a disk and the validator, which are `designer`'s and already have
 * their own tests (`MinePhotoTablesTest`).
 *
 * It keeps a list of what it was asked, because "the name that reached the
 * library is the name that was typed" is a claim worth making.
 */
internal class FakePhotos(
  /** The looks the personal package holds, which grows as photos are added. */
  private val kept: MutableList<TableLook> = mutableListOf(),
  private val refuse: List<String>? = null,
) : TablePhotos {
  val asked: MutableList<Pair<String, String>> = mutableListOf()
  val removed: MutableList<TablePin> = mutableListOf()

  /** The personal package as the catalogue would hand it back. */
  fun personal(): List<DiceSet> =
    if (kept.isEmpty()) {
      emptyList()
    } else {
      listOf(DiceSet(id = DiceSet.PERSONAL_ID, name = "My dice", version = "1.0.0", tables = kept.toList()))
    }

  override suspend fun add(
    photo: PickedPhoto,
    name: String,
  ): PhotoOutcome {
    // Opened, because the real one opens it — twice — and a fake that never
    // did would let a presenter hand over a photo nothing could read.
    photo.open()?.close()
    asked += photo.label to name
    refuse?.let { return PhotoOutcome.Refused(it) }
    val id = PhotoTable.idOf(name, kept.map(TableLook::id).toSet())
    kept += PhotoTable.lookOf(id, PhotoTable.nameOf(name))
    return PhotoOutcome.Added(TablePin(DiceSet.PERSONAL_ID, id))
  }

  override suspend fun remove(pin: TablePin) {
    removed += pin
    kept.removeAll { it.id == pin.tableId }
  }
}

/** A file the picker might have handed back. */
internal fun pickedPhoto(
  label: String = "oak_table.jpg",
  open: () -> InputStream? = { ByteArrayInputStream(ByteArray(1)) },
): PickedPhoto = PickedPhoto(label = label, open = open)
