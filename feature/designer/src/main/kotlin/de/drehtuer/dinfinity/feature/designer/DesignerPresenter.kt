package de.drehtuer.dinfinity.feature.designer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.FaceDrawing
import de.drehtuer.dinfinity.designer.GuideMark
import de.drehtuer.dinfinity.designer.Stroke

/**
 * How wide each nib draws, as a fraction of the canvas — so a stroke drawn on
 * a small phone is the same stroke on a large one, and the same stroke again
 * at export resolution.
 */
private const val FINE = 0.008f
private const val MEDIUM = 0.02f
private const val BROAD = 0.05f

/** Wider than the broad pen, because an eraser people have to be accurate with is a bad eraser. */
private const val ERASER = 0.06f

/** What the pen is doing (`docs/face-designer.md`, "Drawing tools"). */
enum class Nib(
  /** How wide it draws, as a fraction of the canvas. */
  val width: Float,
) {
  Fine(FINE),
  Medium(MEDIUM),
  Broad(BROAD),

  /**
   * The eraser.
   *
   * A nib rather than a mode, because it is a stroke like any other — which is
   * what lets it be undone one step at a time instead of being a hole somebody
   * has to redraw around.
   */
  Eraser(ERASER),
  ;

  val erases: Boolean get() = this == Eraser
}

/** What the designer is showing. */
data class DesignerState(
  val draft: Draft,
  val cell: Int = 0,
  val nib: Nib = Nib.Medium,
  val colorArgb: Int = INK,
  /** The faint number under the drawing, which can be turned off. */
  val guideShown: Boolean = true,
) {
  /** The drawing on the face in front of the player. */
  val face: FaceDrawing get() = draft.face(cell)

  /** The numbers under it — one, or a d4's three (`docs/dice-sets.md`, "The d4"). */
  val guide: List<GuideMark> get() = if (guideShown) draft.guide(cell) else emptyList()

  val canUndo: Boolean get() = face.canUndo
  val canRedo: Boolean get() = face.canRedo

  /**
   * True when this face is close enough to the limit to say so.
   *
   * The warning comes before the refusal, because a drawing that simply stops
   * taking strokes reads as a broken screen (`docs/face-designer.md`,
   * "Constraints").
   */
  val nearlyFull: Boolean get() = face.strokes.size >= FaceDrawing.MAX_STROKES - ROOM_TO_WARN

  val full: Boolean get() = face.full

  companion object {
    /** Black, which is what a pen is until somebody says otherwise. */
    const val INK: Int = 0xFF000000.toInt()

    /** How many strokes of warning somebody gets before the face stops taking them. */
    const val ROOM_TO_WARN: Int = 20
  }
}

/**
 * Drawing the faces of a die (`design/dInfinity.dc.html`, options `1v`, `4c`
 * and `8d`; `docs/face-designer.md`).
 *
 * The die is **copied**, so the face values are inherited and what is being
 * drawn is only what a face looks like. Nothing here can change what the die
 * scores — which is why the guide can be trusted as a guide.
 *
 * A stroke arrives already in fractions of the canvas. The screen knows how
 * big it is and this does not: a draft outlives the screen it was drawn on and
 * is re-rendered at export resolution, so a stroke in pixels would be a stroke
 * that moved when the phone was turned.
 */
class DesignerPresenter(
  die: Die,
) {
  /** What the screen draws. */
  var state: DesignerState by mutableStateOf(DesignerState(draft = Draft(die = die)))
    private set

  /** A face was chosen, from the strip or by swiping. */
  fun show(cell: Int) {
    if (cell !in 0 until state.draft.cells) return
    state = state.copy(cell = cell)
  }

  /** The pen was changed, or the eraser picked up. */
  fun use(nib: Nib) {
    state = state.copy(nib = nib)
  }

  /** A colour was chosen. The eraser is put down, because a coloured eraser is not a thing. */
  fun ink(colorArgb: Int) {
    state = state.copy(colorArgb = colorArgb, nib = if (state.nib.erases) Nib.Medium else state.nib)
  }

  /** The guide was turned on or off. */
  fun showGuide(shown: Boolean) {
    state = state.copy(guideShown = shown)
  }

  /**
   * A finger finished a stroke.
   *
   * Taken whole rather than point by point: a half-drawn line is the screen's
   * business until the finger lifts, and a model that recorded every sample
   * would have an undo step per pixel.
   *
   * A stroke of fewer than two dots is a tap, and a tap is not a mark.
   */
  fun drew(dots: List<Dot>) {
    if (dots.size < 2) return
    val stroke =
      Stroke(
        dots = dots,
        colorArgb = state.colorArgb,
        width = state.nib.width,
        erases = state.nib.erases,
      )
    state = state.copy(draft = state.draft.onFace(state.cell) { it.draw(stroke) })
  }

  /** Back one step on this face. */
  fun undo() {
    state = state.copy(draft = state.draft.onFace(state.cell) { it.undo() })
  }

  /** Forward one step on this face. */
  fun redo() {
    state = state.copy(draft = state.draft.onFace(state.cell) { it.redo() })
  }

  /** Takes this face back to blank, in one step that can be undone. */
  fun clear() {
    state = state.copy(draft = state.draft.onFace(state.cell) { it.clear() })
  }
}
