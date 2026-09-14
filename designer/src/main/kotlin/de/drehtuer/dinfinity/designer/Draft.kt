package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die

/**
 * A point on a face, as a fraction of the canvas.
 *
 * Fractions rather than pixels, because a draft outlives the screen it was
 * drawn on: the canvas is whatever size the phone gave it, and the export is
 * 256 px per cell (`docs/face-designer.md`, "Export details"). A stroke stored
 * in pixels would be a stroke that moved when the phone was turned.
 */
data class Dot(
  val x: Float,
  val y: Float,
)

/**
 * One stroke of the pen, as a path rather than as pixels.
 *
 * Vectors because the drawing is re-rendered at export resolution and because
 * a draft survives process death (`docs/face-designer.md`, "Drawing tools").
 * A bitmap would be neither.
 *
 * @param colorArgb the ink.
 * @param width how wide the nib is, as a fraction of the canvas — so a stroke
 *   drawn on a small phone is the same stroke on a large one.
 * @param erases true for the eraser, which is a stroke like any other and is
 *   why it can be undone one step at a time.
 */
data class Stroke(
  val dots: List<Dot>,
  val colorArgb: Int,
  val width: Float,
  val erases: Boolean = false,
)

/**
 * What has been drawn on one cell, and what can be taken back.
 *
 * The two stacks hold **whole states rather than single strokes**, so that one
 * action is one step whatever it did. The first version stacked strokes, and
 * then clearing a face — which takes away many at once — could only be undone
 * one stroke at a time, which is not what "clear" means to the finger that
 * pressed it. A step is an action, and an action is a list.
 *
 * Drawing after an undo drops the redo, which is what every drawing program
 * does and what a finger expects: the branch you left is not waiting for you.
 */
data class FaceDrawing(
  val strokes: List<Stroke> = emptyList(),
  /** What the face looked like before each step, newest last. */
  val past: List<List<Stroke>> = emptyList(),
  /** What it looked like after each step that was taken back, newest last. */
  val future: List<List<Stroke>> = emptyList(),
) {
  /** True when there is nothing on this face yet. */
  val blank: Boolean get() = strokes.isEmpty()

  val canUndo: Boolean get() = past.isNotEmpty()
  val canRedo: Boolean get() = future.isNotEmpty()

  /** True when this face is at the limit and will not take another stroke. */
  val full: Boolean get() = strokes.size >= MAX_STROKES

  /**
   * Adds [stroke].
   *
   * At the limit the drawing is returned unchanged rather than throwing: a
   * finger is already on the glass, and the screen's job is to have warned
   * before this (`docs/face-designer.md`, "Constraints").
   */
  fun draw(stroke: Stroke): FaceDrawing = if (full) this else step(strokes + stroke)

  /** Takes everything off this face, in one step that can be taken back. */
  fun clear(): FaceDrawing = if (blank) this else step(emptyList())

  fun undo(): FaceDrawing =
    if (!canUndo) {
      this
    } else {
      FaceDrawing(
        strokes = past.last(),
        past = past.dropLast(1),
        future =
          future + listOf(strokes),
      )
    }

  fun redo(): FaceDrawing =
    if (!canRedo) {
      this
    } else {
      FaceDrawing(
        strokes = future.last(),
        past = past + listOf(strokes),
        future = future.dropLast(1),
      )
    }

  /** One action: what the face becomes, with what it was pushed behind it. */
  private fun step(to: List<Stroke>) = FaceDrawing(strokes = to, past = past + listOf(strokes), future = emptyList())

  companion object {
    /**
     * How many strokes one face may carry.
     *
     * A bound on storage and on export time rather than on anybody's
     * patience: two hundred strokes is a drawing, and the screen warns
     * before it (`docs/face-designer.md`, "Constraints").
     */
    const val MAX_STROKES: Int = 200
  }
}

/**
 * A die being drawn (`docs/face-designer.md`).
 *
 * The die is copied from the catalogue or from an installed set, so the face
 * *values* are inherited and what is being drawn is only what a face looks
 * like. Nothing here can change what the die scores.
 *
 * Held as a map rather than a list so an untouched face costs nothing: a d20
 * somebody has drawn one face of is one entry, not twenty.
 */
data class Draft(
  val die: Die,
  val faces: Map<Int, FaceDrawing> = emptyMap(),
) {
  /** What has been drawn on [cell], which is nothing until something has. */
  fun face(cell: Int): FaceDrawing = faces[cell] ?: FaceDrawing()

  /** The outline every cell of this die is masked into. */
  val outline: FaceOutline get() = FaceOutline.of(die.shape)

  /** How many cells there are to draw on. */
  val cells: Int get() = die.faces.size

  /** True when nothing has been drawn on any face. */
  val blank: Boolean get() = faces.values.all(FaceDrawing::blank)

  /** The guide for [cell] — one number, or a d4's three (`FaceGuide`). */
  fun guide(cell: Int): List<GuideMark> = FaceGuide.of(die, cell)

  /**
   * [change] applied to one cell.
   *
   * A cell outside the die is left alone rather than added: the map is keyed
   * by cell index and a key no face has would be a drawing nothing shows.
   */
  fun onFace(
    cell: Int,
    change: (FaceDrawing) -> FaceDrawing,
  ): Draft {
    if (cell !in die.faces.indices) return this
    return copy(faces = faces + (cell to change(face(cell))))
  }
}
