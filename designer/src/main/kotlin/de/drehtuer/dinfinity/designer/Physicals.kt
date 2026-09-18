package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieMaterial
import java.io.File

/**
 * What "My dice" is made of, as the person whose phone it is has set it
 * (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
 * them").
 *
 * The third record the personal package is built from, beside the drafts and
 * the photos, and it exists for the same reason they do: **the package is
 * built, not accumulated** ([MineSets]). The three numbers cannot live in the
 * `diceset.toml` in `dicesets/mine/`, because that file is rewritten from the
 * records the next time a drawing changes and a value kept only there would
 * disappear the next time somebody drew a line.
 *
 * Only the three a person can set are read from it — `size_mm`, `density` and
 * `translucency`. The rest of a [DieMaterial] is not a decision this makes: a
 * drawn die's colour is the *installing* set's business, which is why
 * [MinePackage] does not carry one across.
 *
 * An interface so the presenter can be tested without a disk, and so which
 * thread the disk is touched on stays a wiring decision — the same seam
 * [Drafts] draws for the same reason.
 */
interface Physicals {
  /** The three numbers the package declares, or the defaults until somebody moves one. */
  fun material(): DieMaterial

  /** Writes them down. */
  fun set(material: DieMaterial)

  /**
   * A number that changes when they do, so the package is rebuilt.
   *
   * The same trick as [DraftStore.stamp] and [PhotoStore.stamp], and for the
   * same reason: [MineSets] rebuilds when its records have moved on, and a
   * weight somebody changed is a record that moved on.
   */
  fun stamp(): Long

  companion object {
    /** The defaults, which nothing can change. What a test uses, and the fallback. */
    val NONE: Physicals =
      object : Physicals {
        override fun material(): DieMaterial = DieMaterial()

        override fun set(material: DieMaterial) = Unit

        override fun stamp(): Long = 0
      }
  }
}

/**
 * The three numbers as one small file under the app's own files, beside the
 * drafts and the photographs.
 *
 * `key = value` a line, which is neither TOML nor a serialiser: there are
 * three doubles in it, every one of them is clamped on the way in *and* on the
 * way out, and a line that says anything else is skipped. A file that will not
 * read back is the defaults rather than an error, because the worst it can
 * cost is a weight somebody sets again — and a package that refuses to build
 * because a byte flipped would cost them every die they have drawn.
 *
 * Written to a neighbouring file and renamed over the real one, so a process
 * that dies halfway leaves the old numbers rather than half of the new ones.
 */
class PhysicalStore(
  private val file: File,
) : Physicals {
  override fun material(): DieMaterial {
    val text = runCatching { file.readText() }.getOrNull() ?: return DieMaterial()
    val values =
      text
        .lineSequence()
        .mapNotNull { line ->
          val key = line.substringBefore(SEPARATOR, missingDelimiterValue = "").trim()
          val value = line.substringAfter(SEPARATOR, missingDelimiterValue = "").trim().toDoubleOrNull()
          if (key.isEmpty() || value == null) null else key to value
        }.toMap()
    return DieMaterial(
      sizeMm = values[SIZE_MM] ?: DieMaterial().sizeMm,
      density = values[DENSITY] ?: DieMaterial().density,
      translucency = values[TRANSLUCENCY] ?: DieMaterial().translucency,
    ).clampedToLimits()
  }

  override fun set(material: DieMaterial) {
    val clamped = material.clampedToLimits()
    val text =
      buildString {
        appendLine("$SIZE_MM $SEPARATOR ${clamped.sizeMm}")
        appendLine("$DENSITY $SEPARATOR ${clamped.density}")
        appendLine("$TRANSLUCENCY $SEPARATOR ${clamped.translucency}")
      }
    file.parentFile?.mkdirs()
    val partial = File(file.parentFile, file.name + PARTIAL)
    runCatching {
      partial.writeText(text)
      if (!partial.renameTo(file)) partial.delete()
    }.onFailure { partial.delete() }
  }

  /**
   * The contents, not the timestamp.
   *
   * A modification time is a second wide on some filesystems and a length does
   * not move when `1.2` becomes `1.3`, so the obvious stamp would miss exactly
   * the change this is here to catch: one tap, immediately after another.
   */
  override fun stamp(): Long = runCatching { file.readText() }.getOrNull()?.hashCode()?.toLong() ?: 0

  companion object {
    /** What the file is called, under the app's own files beside the drafts. */
    const val FILE_NAME: String = "mine-physical.txt"

    private const val SEPARATOR = "="
    private const val SIZE_MM = "size_mm"
    private const val DENSITY = "density"
    private const val TRANSLUCENCY = "translucency"
    private const val PARTIAL = ".part"
  }
}
