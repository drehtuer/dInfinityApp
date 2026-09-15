package de.drehtuer.dinfinity.designer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import java.io.ByteArrayOutputStream

/**
 * The painter that actually puts pixels down (`docs/face-designer.md`,
 * "Export details").
 *
 * The only file in the export that touches an Android API, and it holds no
 * judgement at all: [Atlas] has already said how big the image is, which cell
 * each face occupies and where every point of every mark lands in it. What is
 * left is a clip, a path, a paint and a PNG encoder.
 *
 * Three things about it are worth knowing.
 *
 * **The paper is transparent, not white.** A cell nobody drew on is left
 * exactly as the bitmap was created, so the die's own colour shows through it
 * and its printed label with it (`docs/dice-sets.md`, "Textures"). That is why
 * the **eraser clears** here where it paints white on the canvas: on screen the
 * paper is white and the eraser is a white pen, and in the atlas the paper is
 * nothing at all.
 *
 * **The drawing is clipped to the face**, not to the cell. A mark may run off
 * the outline — a turned paste puts a square's corners outside a triangle on
 * purpose (`FaceTransform`) — and the part that is outside belongs to no face.
 *
 * **Anti-aliasing is on for the marks and off for the mask.** A line is a line
 * at any resolution and wants a soft edge; a clip is a decision about which
 * pixels are on the face, and `clipPath` has no soft edge to give it.
 */
class BitmapAtlas : AtlasPainter {
  /**
   * [plan] as a PNG, or null when the phone would not give up the memory.
   *
   * Caught rather than thrown, because the caller's answer to both is the
   * same: a die with no atlas is a die that prints its labels, and one
   * unlucky allocation should not cost somebody the other nineteen dice in
   * their package.
   */
  override fun png(plan: AtlasPlan): ByteArray? =
    runCatching {
      // ARGB_8888 by default, and every pixel starts at zero — which is what
      // makes a cell nobody drew on transparent rather than black.
      val bitmap = createBitmap(plan.width, plan.height)
      try {
        paint(plan, Canvas(bitmap))
        encode(bitmap)
      } finally {
        bitmap.recycle()
      }
    }.getOrNull()

  private fun paint(
    plan: AtlasPlan,
    canvas: Canvas,
  ) {
    plan.cells.forEach { cell ->
      canvas.withClip(mask(plan, cell)) {
        cell.placed().forEach { mark -> draw(mark, this, cell) }
      }
    }
  }

  /** The face's own outline, in the cell's place in the image. */
  private fun mask(
    plan: AtlasPlan,
    cell: AtlasCell,
  ): Path =
    Path().apply {
      if (plan.corners.isEmpty()) {
        // The coin, which is the one outline that is not a polygon.
        addOval(
          RectF(
            cell.left.toFloat(),
            cell.top.toFloat(),
            (cell.left + cell.size).toFloat(),
            (cell.top + cell.size).toFloat(),
          ),
          Path.Direction.CW,
        )
        return@apply
      }
      trace(plan.corners.map(cell::at))
      close()
    }

  private fun draw(
    mark: Mark,
    canvas: Canvas,
    cell: AtlasCell,
  ) {
    val path = Path().apply { trace(mark.dots) }
    when (mark) {
      is Fill -> {
        path.close()
        canvas.drawPath(path, fillPaint(mark.colorArgb))
      }

      is Stroke -> canvas.drawPath(path, strokePaint(mark, cell))
    }
  }

  private fun fillPaint(colorArgb: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = colorArgb
    }

  private fun strokePaint(
    stroke: Stroke,
    cell: AtlasCell,
  ): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      // A nib is a fraction of the canvas, so it is the same nib whatever
      // resolution the atlas is exported at — and never thinner than the one
      // pixel a line has to be to exist.
      strokeWidth = cell.pixels(stroke.width).coerceAtLeast(1f)
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
      if (stroke.erases) {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
      } else {
        color = stroke.colorArgb
      }
    }

  private fun Path.trace(dots: List<Dot>) {
    dots.forEachIndexed { index, dot ->
      if (index == 0) moveTo(dot.x, dot.y) else lineTo(dot.x, dot.y)
    }
  }

  private fun encode(bitmap: Bitmap): ByteArray? {
    val bytes = ByteArrayOutputStream()
    // PNG is lossless, so the quality argument is ignored; it is passed
    // because the signature demands one.
    val written = bitmap.compress(Bitmap.CompressFormat.PNG, LOSSLESS, bytes)
    return if (written) bytes.toByteArray() else null
  }

  private companion object {
    const val LOSSLESS = 100
  }
}
