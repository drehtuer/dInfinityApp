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
 * One thing on a face: a line of the pen, or a region the bucket coloured in.
 *
 * A sealed pair rather than one class with a flag, because the two carry
 * different things — a stroke has a nib width and can be the eraser, a fill has
 * neither — and a combination that cannot be drawn is better made impossible
 * than documented. Both are lists of dots in fractions of the canvas, which is
 * what lets a paste turn or mirror either of them with the same arithmetic
 * (`FaceTransform`).
 */
sealed interface Mark {
  /** The ink. */
  val colorArgb: Int

  /** The path, or the boundary of the region, in fractions of the canvas. */
  val dots: List<Dot>

  /** The same mark with its dots somewhere else — what a turn and a mirror are made of. */
  fun at(dots: List<Dot>): Mark
}

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
  override val dots: List<Dot>,
  override val colorArgb: Int,
  val width: Float,
  val erases: Boolean = false,
) : Mark {
  override fun at(dots: List<Dot>): Stroke = copy(dots = dots)
}

/**
 * A region of one colour, which is what the bucket leaves behind
 * (`docs/face-designer.md`, "Drawing tools").
 *
 * A vector drawing has no pixels to flood, so a fill is a *shape* added to the
 * drawing rather than a change to the ones already there: the boundary of the
 * closed stroke it was dropped inside, or the canvas itself when it was
 * dropped on bare paper (`FaceFill`).
 *
 * @param dots the boundary, in fractions of the canvas. It closes itself — the
 *   last dot joins the first — so a region is stored once rather than once and
 *   a half.
 */
data class Fill(
  override val dots: List<Dot>,
  override val colorArgb: Int,
) : Mark {
  override fun at(dots: List<Dot>): Fill = copy(dots = dots)
}

/**
 * What has been drawn on one cell, and what can be taken back.
 *
 * The two stacks hold **whole states rather than single marks**, so that one
 * action is one step whatever it did. The first version stacked strokes, and
 * then clearing a face — which takes away many at once — could only be undone
 * one stroke at a time, which is not what "clear" means to the finger that
 * pressed it. A step is an action, and an action is a list.
 *
 * Drawing after an undo drops the redo, which is what every drawing program
 * does and what a finger expects: the branch you left is not waiting for you.
 *
 * **Fills sink under the strokes** (`docs/face-designer.md`, "The fill
 * bucket"). The list is kept with every [Fill] at the front, oldest first, and
 * every [Stroke] behind them, each in the order it arrived, so a later fill is
 * over an earlier fill and under all the ink. A bucket colours the paper, not
 * the line: a fill that landed on top would hide the drawing it was aimed at,
 * and the way to cover ink is the eraser.
 */
data class FaceDrawing(
  val marks: List<Mark> = emptyList(),
  /** What the face looked like before each step, newest last. */
  val past: List<List<Mark>> = emptyList(),
  /** What it looked like after each step that was taken back, newest last. */
  val future: List<List<Mark>> = emptyList(),
) {
  /** True when there is nothing on this face yet. */
  val blank: Boolean get() = marks.isEmpty()

  val canUndo: Boolean get() = past.isNotEmpty()
  val canRedo: Boolean get() = future.isNotEmpty()

  /** True when this face is at the limit and will not take another mark. */
  val full: Boolean get() = marks.size >= MAX_MARKS

  /**
   * Adds [mark].
   *
   * At the limit the drawing is returned unchanged rather than throwing: a
   * finger is already on the glass, and the screen's job is to have warned
   * before this (`docs/face-designer.md`, "Constraints").
   */
  fun draw(mark: Mark): FaceDrawing = if (full) this else step(sunk(marks + mark))

  /**
   * Adds all of [pasted], in one step (`docs/face-designer.md`, "Copy and
   * paste").
   *
   * It **merges** rather than replaces: the drawing already on the face stays
   * and the copy lands over it, which is what the border-on-every-face case
   * wants. A face that should be replaced is cleared first — two presses, both
   * of them undoable — whereas a paste that replaced would take work away that
   * nobody asked it to.
   *
   * A paste that would carry the face past the limit is **refused whole**: a
   * paste is one action, and half of what was copied is not what was copied.
   * Nothing is pasted from an empty clipboard either, so an idle press cannot
   * fill the undo stack with steps that changed nothing.
   */
  fun paste(pasted: List<Mark>): FaceDrawing =
    if (pasted.isEmpty() || marks.size + pasted.size > MAX_MARKS) this else step(sunk(marks + pasted))

  /** Takes everything off this face, in one step that can be taken back. */
  fun clear(): FaceDrawing = if (blank) this else step(emptyList())

  fun undo(): FaceDrawing =
    if (!canUndo) {
      this
    } else {
      FaceDrawing(
        marks = past.last(),
        past = past.dropLast(1),
        future =
          future + listOf(marks),
      )
    }

  fun redo(): FaceDrawing =
    if (!canRedo) {
      this
    } else {
      FaceDrawing(
        marks = future.last(),
        past = past + listOf(marks),
        future = future.dropLast(1),
      )
    }

  /** One action: what the face becomes, with what it was pushed behind it. */
  private fun step(to: List<Mark>) = FaceDrawing(marks = to, past = past + listOf(marks), future = emptyList())

  companion object {
    /**
     * How many marks one face may carry.
     *
     * A bound on storage and on export time rather than on anybody's
     * patience: two hundred strokes is a drawing, and the screen warns
     * before it (`docs/face-designer.md`, "Constraints").
     */
    const val MAX_MARKS: Int = 200

    /**
     * [marks] with the fills at the front, each group in the order it arrived.
     *
     * A stable partition rather than a sort: what it decides is only whether a
     * mark is paper or ink, and two fills keep the order they were made in.
     */
    fun sunk(marks: List<Mark>): List<Mark> = marks.filterIsInstance<Fill>() + marks.filterNot { it is Fill }
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
