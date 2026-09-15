package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import java.io.File

/**
 * Which folder inside an unpacked archive holds the thing that was wanted
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * An archive from a forge is not the package: it wraps everything one folder
 * deep in `repo-<sha>/`, and a repository may keep what matters in a subfolder
 * besides. Somebody has to say which folder counts, and it is the *only* thing
 * that differs between unpacking a dice set and unpacking a saved-roll
 * collection — everything hostile about an archive is hostile in the same way
 * either way, which is why this is a parameter of [SafeExtractor] rather than
 * a second extractor (`docs/architecture.md`, "Importing").
 *
 * It runs after the archive is unpacked and before anything is moved anywhere
 * real, so a [Found.Missing] costs the caller nothing: the folder it looked in
 * is deleted and the app is exactly as it was.
 */
fun interface PackageRoot {
  /**
   * The folder inside [destination] to go on with.
   *
   * @param subfolder the folder inside the archive the URL named, or null.
   */
  fun of(
    destination: File,
    subfolder: String?,
  ): Found

  /** What looking came to. */
  sealed interface Found {
    /** The folder the package's own files are in. */
    data class Folder(
      val folder: File,
    ) : Found

    /**
     * There is nothing of the kind in the archive, and this says so in a
     * sentence somebody can act on.
     */
    data class Missing(
      val reason: RejectionReason,
      val detail: String,
    ) : Found
  }

  companion object {
    /**
     * A dice set: the folder holding `diceset.toml`.
     *
     * Found wherever it is, rather than at the root, because a forge tarball
     * wraps everything one folder deep and a repository is entitled to keep
     * its sets in `sets/`. A URL that named a subfolder narrows it to the one
     * folder whose path ends that way.
     */
    val DiceSet: PackageRoot =
      PackageRoot { destination, subfolder ->
        val wanted = subfolder?.trim('/')
        destination
          .walkTopDown()
          .filter { it.isFile && it.name == DiceSetValidator.DICE_SET_FILE }
          .mapNotNull { it.parentFile }
          .firstOrNull { folder -> wanted == null || folder.invariantPath().endsWith(wanted) }
          ?.let(Found::Folder)
          ?: Found.Missing(
            RejectionReason.NotInTheArchive,
            "no ${DiceSetValidator.DICE_SET_FILE} in the archive",
          )
      }
  }
}

/** A path with forward slashes in it, whatever this filesystem separates with. */
internal fun File.invariantPath(): String = path.replace(File.separatorChar, '/')
