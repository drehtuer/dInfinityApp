package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.Drafts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The designer's drafts, written off the thread that drew them
 * (`docs/face-designer.md`, "Drawing tools").
 *
 * The presenter saves after every stroke, every undo and every clear, because
 * a draft that is only written when somebody remembers to is a draft that is
 * lost when the phone decides the app has been in the background long enough.
 * Every one of those is a file write, and a file write on the thread a finger
 * is drawing on is a stutter in the line.
 *
 * So the write is launched and not waited for, the way a finished roll is
 * (`RollRecording`): the drawing is on screen the moment the finger lifts, and
 * the disk catches up. Writes go to the same scope in order, so the last one
 * launched is the last one to land.
 *
 * Reading is not moved. It happens once, when the screen opens, and a draft
 * that arrived a frame after the canvas would be a canvas that flickers from
 * blank to drawn — which is the same reason the saved-rolls strip is not drawn
 * until the database has answered.
 *
 * It lives in `:app` for the reason the app's other platform halves do: which
 * thread the disk is touched on is a wiring decision, and `designer/` should
 * not have to carry one (`docs/architecture.md`, "Threading").
 */
class SavedDrafts(
  private val store: DraftStore,
  private val scope: CoroutineScope,
) : Drafts {
  override fun load(die: Die): Draft = store.load(die)

  override fun save(draft: Draft) {
    scope.launch { store.save(draft) }
  }
}
