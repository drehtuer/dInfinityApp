package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.feature.saved.Editing
import de.drehtuer.dinfinity.feature.saved.EditorPresenter
import de.drehtuer.dinfinity.feature.saved.GroupPresenter
import de.drehtuer.dinfinity.feature.saved.ImportPresenter
import de.drehtuer.dinfinity.feature.saved.SavedPresenter
import kotlinx.coroutines.CoroutineScope

/**
 * The four screens that are about saved rolls, put together in one place.
 *
 * The counterpart to [RollWiring], and here for the same reason: the activity
 * had turned into a list of constructor calls, and a list of constructor calls
 * is not what an activity is for. Each of these is built per visit, because a
 * presenter watches the database for as long as its scope lives.
 *
 * `docs/TODO.md` still asks for a real container on `DInfinityApplication`.
 * This is not it — it is the smaller thing that was actually needed, and the
 * shape the container will take when it arrives.
 *
 * @param scope the activity's scope; every presenter's watching ends with it.
 * @param unfiledName what the group a roll can always be put in is called. It
 *   is words on a screen, so it comes from the resources rather than the code.
 */
class SavedWiring(
  private val app: DInfinityApplication,
  private val catalog: DiceCatalog,
  private val scope: CoroutineScope,
  private val unfiledName: String,
) {
  /** The saved-rolls list, opened on [activeGroupId]. */
  fun list(
    activeGroupId: String,
    onActiveGroup: (String) -> Unit,
  ): SavedPresenter =
    SavedPresenter(
      repository = app.savedRolls,
      catalog = catalog,
      scope = scope,
      unfiledName = unfiledName,
      onActiveGroup = onActiveGroup,
      activeGroupId = activeGroupId,
    )

  /** The group sheet, which the list and the editor both open. */
  fun groups(): GroupPresenter = GroupPresenter(repository = app.savedRolls, scope = scope, unfiledName = unfiledName)

  /** Taking a collection in. */
  fun importing(): ImportPresenter =
    ImportPresenter(
      importer = app.collectionImporter,
      catalog = catalog,
      scope = scope,
      unfiledName = unfiledName,
    )

  /** Writing one saved roll down, or a new one when [editing] is null. */
  fun editor(
    opening: Editing,
    defaultGroupId: String,
  ): EditorPresenter =
    EditorPresenter(
      repository = app.savedRolls,
      catalog = catalog,
      scope = scope,
      opening = opening,
      defaultGroupId = defaultGroupId,
    )
}
