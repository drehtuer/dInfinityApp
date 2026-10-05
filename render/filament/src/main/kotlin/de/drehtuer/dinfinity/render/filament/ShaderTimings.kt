package de.drehtuer.dinfinity.render.filament

import java.io.File
import java.io.IOException

/**
 * How long each dice material takes to compile on this phone, in milliseconds
 * — what the "Preparing the dice" bar is measured against ([ShaderWork]).
 *
 * Starts from the Pixel 10a's figures ([DEFAULTS]) and learns this phone's
 * own: every compile that finishes replaces its variant's figure with what it
 * actually took, so the bar on the next update is this phone's rather than the
 * reference device's.
 */
data class ShaderTimings(
  private val millis: Map<DiceMaterial.Variant, Long> = emptyMap(),
) {
  /** How long [variant] is expected to take. */
  fun estimateOf(variant: DiceMaterial.Variant): Long = millis[variant] ?: DEFAULTS.getValue(variant)

  /**
   * The same, with [variant] measured at [took] milliseconds. A figure outside
   * [PLAUSIBLE] is not a measurement of the compiler — a process paused under
   * a debugger, a clock that jumped — and is not learnt.
   */
  fun with(
    variant: DiceMaterial.Variant,
    took: Long,
  ): ShaderTimings = if (took in PLAUSIBLE) ShaderTimings(millis + (variant to took)) else this

  /** One `key=millis` line per learnt figure, in [DiceMaterial.Variant] order. */
  fun encode(): String =
    DiceMaterial.Variant.entries
      .mapNotNull { variant -> millis[variant]?.let { "${variant.key}=$it\n" } }
      .joinToString("")

  companion object {
    /**
     * The Pixel 10a's figures (`docs/physics-and-rendering.md`, "Preparing the
     * dice"). The opaque and resin materials were measured there — about 2.4 s
     * and 2 s; the glass and table ones have not been timed alone and are
     * assumed to cost what resin does, which is the material they are closest
     * to in size. One compile on any phone replaces the guess.
     */
    val DEFAULTS: Map<DiceMaterial.Variant, Long> =
      mapOf(
        DiceMaterial.Variant.OPAQUE to 2_400L,
        DiceMaterial.Variant.RESIN to 2_000L,
        DiceMaterial.Variant.GLASS to 2_000L,
        DiceMaterial.Variant.TABLE to 2_000L,
      )

    /** A tenth of a second to two minutes: anything else was not the compiler. */
    val PLAUSIBLE: LongRange = 100L..120_000L

    /**
     * Reads what [encode] wrote. **Anything it does not understand is
     * skipped**, line by line — an unknown variant, a number that is not one, a
     * figure outside [PLAUSIBLE] — so a garbled file costs at most the figures
     * on its garbled lines, which fall back to [DEFAULTS].
     */
    fun decode(text: String): ShaderTimings {
      val byKey = DiceMaterial.Variant.entries.associateBy { it.key }
      val read =
        text.lineSequence().mapNotNull { line ->
          val (key, value) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
          val variant = byKey[key.trim()] ?: return@mapNotNull null
          val millis = value.trim().toLongOrNull()?.takeIf { it in PLAUSIBLE } ?: return@mapNotNull null
          variant to millis
        }
      return ShaderTimings(read.toMap())
    }
  }
}

/**
 * Where [ShaderTimings] are kept between launches.
 *
 * **Not beside the compiled materials.** Those live in `codeCacheDir`, which
 * Android empties on every update ([MaterialCache]) — and an update is exactly
 * when the figure is wanted. This file belongs somewhere that survives one and
 * that a backup does not carry to a different phone, which is
 * `noBackupFilesDir` (`docs/architecture.md`, "Storage layout").
 *
 * Like the material cache, **nothing about the disk is allowed to matter**: a
 * file that is missing, unreadable or garbled reads as the defaults, and one
 * that cannot be written is simply not written.
 *
 * @param file where the figures are kept, or null to keep nothing.
 */
class ShaderTimingStore(
  private val file: File?,
) {
  /** What was kept, or the defaults. */
  fun load(): ShaderTimings =
    try {
      file?.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.let(ShaderTimings::decode) ?: ShaderTimings()
    } catch (_: IOException) {
      ShaderTimings()
    }

  /**
   * Keeps [timings], written beside the real name and renamed into place so
   * a process killed half-way leaves the old figures rather than half of new
   * ones.
   */
  fun save(timings: ShaderTimings) {
    val target = file ?: return
    val partial = File(target.parentFile, target.name + PARTIAL)
    try {
      target.parentFile?.mkdirs()
      partial.writeText(timings.encode(), Charsets.UTF_8)
      if (!partial.renameTo(target)) partial.delete()
    } catch (_: IOException) {
      partial.delete()
    }
  }

  companion object {
    /** Keeps nothing: every estimate is the Pixel 10a's. */
    val NONE = ShaderTimingStore(file = null)

    private const val PARTIAL = ".partial"
  }
}
