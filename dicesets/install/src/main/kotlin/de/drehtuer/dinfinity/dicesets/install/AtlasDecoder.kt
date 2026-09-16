package de.drehtuer.dinfinity.dicesets.install

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits
import de.drehtuer.dinfinity.dicesets.format.Severity
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage

/**
 * What came of asking for a die's artwork.
 *
 * Either a picture or a reason there is none, and lines to say about it
 * either way — the same shape the validator's report has, because it is the
 * same report one step later. `dicesets/format` checks a texture's *header*
 * from its bytes; the two things it cannot answer from bytes alone need a
 * decoder, and a decoder needs Android, so they are answered here
 * (`docs/dice-sets.md`, "Validation").
 *
 * A die whose atlas comes back [Unusable] is not a die that fails to draw: it
 * falls back to its printed labels, which is what a die with no artwork at all
 * looks like. That is the whole point of reporting rather than throwing.
 */
sealed interface AtlasDecode {
  /** Everything the decode has to say, in the order it found it. */
  val messages: List<ValidationMessage>

  /** The picture, and any warnings about it. */
  data class Drawn(
    val image: AtlasImage,
    override val messages: List<ValidationMessage> = emptyList(),
  ) : AtlasDecode

  /** No picture, and why not. */
  data class Unusable(
    override val messages: List<ValidationMessage>,
  ) : AtlasDecode

  /** The picture, or `null` — what a renderer asks, having no use for the rest. */
  val drawn: AtlasImage? get() = (this as? Drawn)?.image
}

/**
 * The one place a stranger's picture is turned into pixels
 * (`docs/dice-sets.md`, "Textures").
 *
 * Three rules, in this order, and the order is the whole of the safety:
 *
 * 1. **Bounds before pixels.** The width and the height are read with nothing
 *    allocated, so an image claiming to be thirty thousand pixels square is
 *    refused before a decoder has been asked to make room for it
 *    (`docs/architecture.md`, "Tech stack").
 * 2. **Nothing thrown.** Every way a hostile or merely broken file can end a
 *    decode — a null bitmap, an exception out of the platform decoder, an
 *    `OutOfMemoryError` from a file that lied about how it compresses — comes
 *    back as [AtlasDecode.Unusable] and the die prints its labels instead. A
 *    downloaded picture must not be able to take the roll screen with it.
 * 3. **Straight alpha.** Asked for explicitly, because Android premultiplies
 *    by default and the material blends artwork over the printed label by the
 *    artwork's own alpha ([AtlasImage]).
 *
 * The two platform calls are parameters for the reason [InstalledSets]'s
 * validator is one: what this class *decides* — which refusal a file earns,
 * in which order, and which faces are left to be printed — is plain Kotlin
 * that a JVM test can drive through every branch, and the only thing that
 * needs a real decoder is whether a real PNG comes back.
 *
 * @param bounds the size of an image with nothing decoded, or null when even
 *   that cannot be read.
 * @param pixels the image itself, or null when it will not decode after all.
 */
class AtlasDecoder(
  private val bounds: (ByteArray) -> Pair<Int, Int>? = { boundsOf(it) },
  private val pixels: (ByteArray) -> AtlasImage? = { pixelsOf(it) },
) {
  /**
   * [bytes] as a picture, checked.
   *
   * @param file what to call it in the report: `set/textures/d20.png`.
   * @param faces how many cells the atlas is cut into, or `null` when no die
   *   in the package wears it — in which case there is no grid to check the
   *   cells against and none is checked (`DiceSet.facesForTexture`).
   */
  fun decode(
    bytes: ByteArray,
    file: String,
    faces: Int? = null,
  ): AtlasDecode {
    val size = bounds(bytes)
    val limit = DiceSetLimits.MAX_TEXTURE_PIXELS
    // Written as one table rather than as a ladder of returns for the reason
    // `FileChecker` writes its refusals the same way: the order these are
    // asked in is the safety, and a table shows it at a glance.
    val refusal =
      when {
        size == null -> ValidationCode.TextureWillNotDecode to WILL_NOT_DECODE
        size.first > limit || size.second > limit ->
          ValidationCode.TextureTooLarge to
            "is ${size.first}×${size.second}; textures are at most $limit×$limit"
        else -> null
      }
    if (refusal != null) return refused(file, refusal.first, refusal.second)
    val image = pixels(bytes) ?: return refused(file, ValidationCode.TextureWillNotDecode, WILL_NOT_DECODE)
    return AtlasDecode.Drawn(image, cellWarnings(image, file, faces))
  }

  /** Which faces this atlas leaves for the app to print, as one warning. */
  private fun cellWarnings(
    image: AtlasImage,
    file: String,
    faces: Int?,
  ): List<ValidationMessage> {
    val empty = faces?.takeIf { it > 0 }?.let(image::emptyCells).orEmpty()
    if (empty.isEmpty()) return emptyList()
    return listOf(
      ValidationMessage(
        severity = Severity.Warning,
        code = ValidationCode.AtlasCellsEmpty,
        text =
          "nothing is drawn in ${empty.size} of $faces cells (${empty.joinToString()}), " +
            "so those faces are printed instead",
        file = file,
      ),
    )
  }

  private fun refused(
    file: String,
    code: ValidationCode,
    said: String,
  ): AtlasDecode.Unusable =
    AtlasDecode.Unusable(
      listOf(ValidationMessage(severity = Severity.Error, code = code, text = "'$file' $said", file = file)),
    )

  companion object {
    /** The decoder the app uses, over the platform's own. */
    val platform: AtlasDecoder = AtlasDecoder()

    private const val WILL_NOT_DECODE = "reads as a picture and does not decode as one"

    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** Where alpha sits in a pixel of [AtlasImage.pixels]. */
    private const val ALPHA = 3

    /**
     * The width and height, with nothing decoded, or `null` when the platform
     * decoder cannot even say that much.
     *
     * `inJustDecodeBounds` is what makes refusing an enormous image safe: it
     * fills the size in and allocates no pixels, so the decision is taken
     * before the allocation it is about.
     */
    fun boundsOf(bytes: ByteArray): Pair<Int, Int>? {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      val read = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
      read.getOrNull()?.recycle()
      if (read.isFailure) return null
      return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    /**
     * The picture itself, as straight-alpha RGBA, or `null` when it will not
     * decode after all.
     *
     * This is the check the header cannot make. A PNG with a sound `IHDR` and
     * a truncated image behind it gives up its size happily and has no pixels,
     * and the only thing that ever finds out is a decoder that tried.
     */
    fun pixelsOf(bytes: ByteArray): AtlasImage? {
      val options =
        BitmapFactory.Options().apply {
          inPreferredConfig = Bitmap.Config.ARGB_8888
          inPremultiplied = false
          inScaled = false
        }
      val bitmap =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull() ?: return null
      return try {
        runCatching { rgbaOf(bitmap) }.getOrNull()
      } finally {
        bitmap.recycle()
      }
    }

    /** The bitmap's pixels as red, green, blue and alpha, rows from the top. */
    private fun rgbaOf(bitmap: Bitmap): AtlasImage {
      val width = bitmap.width
      val height = bitmap.height
      val argb = IntArray(width * height)
      bitmap.getPixels(argb, 0, width, 0, 0, width, height)
      val pixels = ByteArray(argb.size * AtlasImage.CHANNELS)
      argb.forEachIndexed { index, colour ->
        val at = index * AtlasImage.CHANNELS
        pixels[at] = (colour shr RED_SHIFT).toByte()
        pixels[at + 1] = (colour shr GREEN_SHIFT).toByte()
        pixels[at + 2] = colour.toByte()
        pixels[at + ALPHA] = (colour shr ALPHA_SHIFT).toByte()
      }
      return AtlasImage(width, height, pixels)
    }
  }
}
