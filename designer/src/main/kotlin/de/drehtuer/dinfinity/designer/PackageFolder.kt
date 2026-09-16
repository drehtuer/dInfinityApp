package de.drehtuer.dinfinity.designer

import java.io.File

/**
 * Where an installed package lives, and how a new version of it gets there
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * Its own type rather than three methods on [MineSets], because it is the one
 * part of writing the personal package that is about *files* rather than about
 * drawings and photographs — and because the rule it enforces is worth having
 * a name for: **a half-written package is the state that must not exist.**
 *
 * So the files go into a staging folder first and are swapped in afterwards,
 * and the old one is put back if the swap does not happen. The staging and
 * holding folders begin with a dot, so a reading that catches them mid-swap
 * does not mistake either for a package.
 */
internal class PackageFolder(
  private val root: File,
  private val id: String,
) {
  /** Where the package is when it is installed. */
  val folder: File get() = File(root, id)

  /** Puts [files] there, or leaves whatever was there before. */
  fun install(files: Map<String, ByteArray>): Boolean {
    val staging = File(root, "$DOT$id$WRITING")
    val previous = File(root, "$DOT$id$PREVIOUS")
    staging.deleteRecursively()
    previous.deleteRecursively()
    val written =
      runCatching {
        files.forEach { (path, bytes) ->
          val file = File(staging, path)
          file.parentFile?.mkdirs()
          file.writeBytes(bytes)
        }
      }.isSuccess
    if (!written) {
      staging.deleteRecursively()
      return false
    }
    return swap(staging, previous)
  }

  /** Takes the package off the disk, which is what having nothing to put in it means. */
  fun remove() {
    folder.deleteRecursively()
  }

  private fun swap(
    staging: File,
    previous: File,
  ): Boolean {
    val installed = folder
    if (installed.isDirectory && !installed.renameTo(previous)) {
      staging.deleteRecursively()
      return false
    }
    if (staging.renameTo(installed)) {
      previous.deleteRecursively()
      return true
    }
    previous.renameTo(installed)
    staging.deleteRecursively()
    return false
  }

  private companion object {
    const val DOT = "."
    const val WRITING = ".writing"
    const val PREVIOUS = ".previous"
  }
}
