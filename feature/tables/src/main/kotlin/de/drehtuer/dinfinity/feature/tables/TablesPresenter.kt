package de.drehtuer.dinfinity.feature.tables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.designer.PhotoTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One look a roll could happen on, and where it came from.
 *
 * The set is carried because **tables are global**: every look from every
 * installed package is offered, whatever dice set it shipped with
 * (`docs/tables.md`, "Selecting a table"). Two packages may each ship a
 * `green-felt`, so the name alone does not say which one this is.
 *
 * @param own whether this look is the player's own photograph, which is the
 *   one kind of row that can be taken away from here (`docs/tables.md`, "Your
 *   own photo"). A look from a downloaded package is removed by removing the
 *   package, on the screen that is about packages.
 */
data class TableChoice(
  val pin: TablePin,
  val look: TableLook,
  /** The package's own name, for telling two `green-felt`s apart. */
  val setName: String,
  val own: Boolean = false,
)

/**
 * The "use a photo" sheet, while it is open
 * (`design/dInfinity.dc.html`, option `1u`).
 *
 * A state rather than four fields on [TablesState], so that "the sheet is not
 * open" is one `null` instead of a combination of flags that could disagree.
 */
data class PhotoDraft(
  /** What the picker handed back, or null while nothing has been chosen. */
  val picked: PickedPhoto? = null,
  /** What it will be called. Typed over the suggestion the file name made. */
  val name: String = "",
  /** True while the photo is being scaled, written and validated. */
  val working: Boolean = false,
  /**
   * Why the last attempt was refused, in the validator's own words.
   *
   * Kept on the draft rather than replacing it, so the photo and the name are
   * still there to fix — a refusal that closed the sheet would make somebody
   * choose the file again to find out what was wrong with it.
   */
  val refused: List<String> = emptyList(),
) {
  /** Whether **Use this photo** does anything yet. */
  val ready: Boolean get() = picked != null && PhotoTable.named(name) && !working
}

/** What the table picker is showing. */
data class TablesState(
  val tables: List<TableChoice> = emptyList(),
  val chosen: TablePin? = null,
  /**
   * The looks that have a picture of themselves yet, by pin.
   *
   * Absent is the ordinary case rather than the failure: a row is drawn the
   * moment it is on screen and its picture arrives afterwards, if it arrives —
   * a device that cannot draw one keeps its swatch and nobody is told
   * (`docs/tables.md`, "Thumbnails"). Keyed by the pin rather than the look's
   * id, because two packages may each ship a `green-felt`.
   */
  val thumbnails: Map<TablePin, ImageBitmap> = emptyMap(),
  /**
   * Whether this screen can make a table out of a photograph.
   *
   * False when nothing was wired to do it, which is what a Robolectric test of
   * the navigation graph is. The control is then absent rather than present
   * and dead: a button that cannot work is worse than no button
   * (`docs/architecture.md`, "Every screen is wired in one place").
   */
  val photosOffered: Boolean = false,
  /** The sheet, or null when it is shut. */
  val adding: PhotoDraft? = null,
) {
  /**
   * True when nothing is installed that ships a table at all.
   *
   * It should not happen — the bundled package ships five — so it is drawn as
   * a state rather than assumed away: a package that stopped validating takes
   * its tables with it, and the bundled one is revalidated on every launch
   * like any other (`docs/dice-sets.md`).
   *
   * No `loaded` beside it, unlike the presenters that collect a flow. This one
   * is built from a list it is handed, in its own constructor, so there is no
   * moment where the screen is showing nothing because nothing has arrived —
   * and a flag that can never be false is a flag that tells a reader something
   * untrue.
   */
  val empty: Boolean get() = tables.isEmpty()

  /** Whether more than one package supplies a table, which is when the set is worth naming. */
  val manyPackages: Boolean get() = tables.map { it.pin.setId }.distinct().size > 1

  /** How many of the looks are the player's own photographs. */
  val photos: Int get() = tables.count(TableChoice::own)

  /** Whether another photo would fit (`PhotoTable.MAX_PHOTOS`). */
  val roomForAPhoto: Boolean get() = photos < PhotoTable.MAX_PHOTOS
}

/**
 * Which table the dice are thrown onto
 * (`design/dInfinity.dc.html`, option `1u`; `docs/tables.md`).
 *
 * **Every look from every installed package, in one list.** A dice set never
 * brings its own table along and never overrides the chosen one: a roll that
 * mixes two sets' dice happens on the one selected table, like reaching into
 * two bags over the same tray.
 *
 * What is *not* here is the pin precedence. A saved roll and a group can each
 * pin a table, and this screen chooses the app default they fall back to — the
 * bottom of that order and the only one of the three that is a setting
 * (`docs/tables.md`). Where the other two are chosen is the saved-roll editor,
 * which already has the field.
 *
 * A photograph becomes one more look in the same list, and by the same route
 * any other package takes: it is written into the personal package and that
 * package is validated. Nothing about the row it produces is special, which is
 * the point — see [TablePhotos].
 *
 * @param sets the installed packages, usually the catalogue's. Handed in
 *   rather than reached for, because which packages are usable is the
 *   application's to know (`docs/architecture.md`, Modules). Asked again after
 *   a photo lands, because the catalogue has been re-read by then.
 * @param onChosen what to remember. The choice is a setting, and settings
 *   belong to `:app`.
 * @param photos how a photograph becomes a table, or null where nothing can.
 * @param scope where the disk work is launched. A photo is decoded, written
 *   and validated, and none of that belongs on the thread Compose draws on.
 * @param thumbnails where a picture of a table comes from, or null where none
 *   can be drawn — power-saving mode, and a test of this screen that has no
 *   business opening a graphics engine. The swatch is then what every row
 *   shows, which is what it was built to be (`docs/tables.md`).
 */
class TablesPresenter(
  private val sets: () -> List<DiceSet>,
  chosen: TablePin?,
  private val onChosen: (TablePin) -> Unit,
  private val scope: CoroutineScope,
  private val photos: TablePhotos? = null,
  private val thumbnails: TableThumbnails? = null,
) {
  /**
   * The looks a picture has already been asked for, drawn or not.
   *
   * A row asks as it comes on screen and asks again on every recomposition,
   * which is a great many times; this is what makes the second ask free. It is
   * not the cache — that is behind [TableThumbnails], where the pictures are
   * and where they outlive this screen — it is only the memory that the
   * question has been put.
   */
  private val asked = mutableSetOf<TablePin>()

  /** What the screen draws. */
  var state: TablesState by mutableStateOf(TablesState())
    private set

  init {
    val tables = tablesOf(sets())
    state =
      TablesState(
        tables = tables,
        // A choice pointing at a package that is no longer installed is not a
        // choice: it is shown as the one the roll screen would actually use,
        // which is the first look there is. The setting itself is left alone,
        // because the package may come back tomorrow — the same rule the
        // default dice set follows.
        chosen = settled(tables, chosen),
        photosOffered = photos != null,
      )
  }

  /**
   * A row is on screen: draw its table, if anything can.
   *
   * Asked by the row rather than for the whole list at once, because a
   * `LazyColumn` composes what fits and a picture costs a scene and a wait on
   * the GPU. Somebody with thirty installed looks pays for the six they can
   * see, and for the next six when they scroll to them
   * (`docs/tables.md`, "Thumbnails").
   *
   * Safe to call from a composition and safe to call again: the second ask for
   * a pin does nothing at all, so a recomposition costs a set lookup.
   */
  fun wants(pin: TablePin) {
    val source = thumbnails ?: return
    val look = state.tables.firstOrNull { it.pin == pin }?.look ?: return
    if (!asked.add(pin)) return
    source.of(pin, look) { picture ->
      state = state.copy(thumbnails = state.thumbnails + (pin to picture))
    }
  }

  /** A look was tapped. */
  fun choose(pin: TablePin) {
    if (state.tables.none { it.pin == pin }) return
    state = state.copy(chosen = pin)
    onChosen(pin)
  }

  /** Opens the sheet that turns a photograph into a table. */
  fun usePhoto() {
    if (photos == null) return
    state = state.copy(adding = PhotoDraft())
  }

  /** Shuts it, throwing the draft away. */
  fun dismissPhoto() {
    state = state.copy(adding = null)
  }

  /**
   * A file came back from the picker.
   *
   * The name field is filled from the file's own name, and only while nobody
   * has typed into it: choosing a second file after renaming the first should
   * not silently undo the rename.
   */
  fun picked(photo: PickedPhoto) {
    val draft = state.adding ?: return
    state =
      state.copy(
        adding =
          draft.copy(
            picked = photo,
            name = draft.name.ifBlank { PhotoTable.suggestionFrom(photo.label) },
            refused = emptyList(),
          ),
      )
  }

  /** The name field was typed into. */
  fun namePhoto(text: String) {
    val draft = state.adding ?: return
    state = state.copy(adding = draft.copy(name = text.take(PhotoTable.MAX_NAME_LENGTH), refused = emptyList()))
  }

  /**
   * **Use this photo**: scale it, write it into the personal package, validate
   * that package, and list what came of it.
   *
   * The new table is chosen as well as added, because somebody who has just
   * made a table meant to play on it — which is what the prototype's upload
   * sheet does, and what makes the trip worth the taps.
   */
  fun confirmPhoto() {
    val draft = state.adding ?: return
    val library = photos ?: return
    val photo = draft.picked.takeIf { draft.ready } ?: return
    state = state.copy(adding = draft.copy(working = true, refused = emptyList()))
    scope.launch {
      when (val outcome = library.add(photo, draft.name)) {
        is PhotoOutcome.Added -> landed(outcome.pin)
        is PhotoOutcome.Refused ->
          state = state.copy(adding = state.adding?.copy(working = false, refused = outcome.reasons))
      }
    }
  }

  /**
   * Takes one of the player's own photo tables away.
   *
   * Only a photo table: every other look belongs to a package, and a package
   * is removed on the screen that is about packages. Choosing what to fall
   * back to is [settled]'s job — the same rule that handles a package being
   * uninstalled while its look was the chosen one.
   */
  fun removePhoto(pin: TablePin) {
    val library = photos ?: return
    if (state.tables.none { it.pin == pin && it.own }) return
    scope.launch {
      library.remove(pin)
      refreshed(keeping = state.chosen?.takeIf { it != pin })
    }
  }

  /** The photo is in the package; re-read the list and play on it. */
  private fun landed(pin: TablePin) {
    refreshed(keeping = pin, shutTheSheet = true)
    onChosen(pin)
  }

  /**
   * The list again, with [keeping] still chosen if it is still there.
   *
   * A look that has gone takes its picture with it, and takes the memory that
   * it was ever asked for — so a table made from a second photograph under the
   * same id is drawn afresh rather than shown the first one's picture.
   */
  private fun refreshed(
    keeping: TablePin?,
    shutTheSheet: Boolean = false,
  ) {
    val tables = tablesOf(sets())
    val there = tables.map(TableChoice::pin).toSet()
    asked.retainAll(there)
    state =
      state.copy(
        tables = tables,
        chosen = settled(tables, keeping),
        thumbnails = state.thumbnails.filterKeys(there::contains),
        adding = if (shutTheSheet) null else state.adding,
      )
  }
}

/**
 * Every look [installed] supplies, as rows.
 *
 * Outside the presenter because it decides nothing about the screen: it is a
 * list of packages turned into a list of rows, and a plain function is the
 * thing a test can hold still (`docs/TODO.md`, "Coverage").
 */
private fun tablesOf(installed: List<DiceSet>): List<TableChoice> =
  installed.flatMap { set ->
    set.tables.map { look ->
      TableChoice(
        pin = TablePin(set.id, look.id),
        look = look,
        setName = set.name,
        own = set.id == DiceSet.PERSONAL_ID,
      )
    }
  }

/** [wanted] if it is a look there is, and otherwise the one the tray would really use. */
private fun settled(
  tables: List<TableChoice>,
  wanted: TablePin?,
): TablePin? = wanted?.takeIf { pin -> tables.any { it.pin == pin } } ?: tables.firstOrNull()?.pin
