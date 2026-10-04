package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ReferencedFile
import de.drehtuer.dinfinity.dicesets.format.Severity
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage

/**
 * A die's artwork, fetched from the package it belongs to
 * (`docs/dice-sets.md`, "Textures").
 *
 * The renderer knows a die has a texture and knows the texture's path is
 * relative to *its set's folder*; it does not know where that folder is, and
 * has no business knowing. This is the piece in between: an id and a path go
 * in, a decoded picture or a reason there is none comes back.
 *
 * **Nothing here trusts what it is handed.** The set id is matched against the
 * folders that are actually there rather than joined onto a path, exactly as
 * [InstalledSets.find] does and for the same reason; the path goes through
 * [ReferencedFile] before anything is opened, so it is relative, has no `..`
 * in it and ends in an extension on the allowlist; the file is read through
 * [PackageFiles], which refuses anything whose canonical path leaves the
 * folder; the size is checked before the bytes are decoded; and the decode
 * itself cannot throw ([AtlasDecoder]).
 *
 * A package that no longer validates has no artwork. A [InstalledPackage.Broken]
 * one is a package whose `diceset.toml` the app will not read, and reading its
 * pictures anyway would be trusting half a package.
 *
 * **The bundled package is answered from where it is, not from a folder.** It
 * is never installed to disk — it is a resource in the APK, read and validated
 * on every launch by the same validator ([bundled]) — so its id is matched
 * first and its files are read through the [PackageFiles] it was validated
 * from. Everything after that is the same road: the same [ReferencedFile], the
 * same size cap, the same decoder. A package on disk calling itself by the
 * bundled id is not reached, which is the rule `SetLibrary` already keeps for
 * the set itself.
 *
 * @param installed the folders on disk.
 * @param bundled the package the app ships, or `null` for none.
 * @param decode how bytes become pixels. A parameter only so that a test can
 *   run the path without a decoder, which on the JVM there is not one of.
 */
class InstalledArtwork(
  private val installed: InstalledSets,
  private val bundled: BundledPackage? = null,
  private val decode: (ByteArray, String, Int?) -> AtlasDecode = AtlasDecoder.platform::decode,
) {
  /**
   * The package the app ships: the set it validated to, and the files it was
   * validated from.
   */
  class BundledPackage(
    val set: DiceSet,
    val files: PackageFiles,
  )

  /**
   * The atlas [texturePath] names inside the package called [setId].
   *
   * Never throws and never returns half an answer: either a picture, or the
   * lines saying why there is not one, which the die answers by printing its
   * labels instead.
   */
  fun read(
    setId: String,
    texturePath: String,
  ): AtlasDecode {
    val named = "$setId/$texturePath"
    val (set, files) =
      packageOf(setId)
        ?: return refused(named, ValidationCode.ReferencedFileMissing, "is not in a package that is installed")
    val reference =
      ReferencedFile.parse(texturePath, DiceSetLimits.IMAGE_EXTENSIONS)
        ?: return refused(named, ValidationCode.BadFileReference, "is not a picture inside the package")
    return opened(set, files, reference, named)
  }

  /**
   * The set called [setId] and the files it was validated from, or `null`
   * when nothing usable goes by that name: the bundled package first, then a
   * folder that still validates.
   */
  private fun packageOf(setId: String): Pair<DiceSet, PackageFiles>? {
    if (bundled != null && setId == bundled.set.id) return bundled.set to bundled.files
    val ready = installed.find(setId) as? InstalledPackage.Ready ?: return null
    return ready.set to PackageFiles.of(ready.folder)
  }

  /**
   * Which version of the atlas [texturePath] in [setId] is on disk — its size
   * and when it last changed — or `null` when there is none.
   *
   * A renderer keeps what it has decoded, and a package can be rewritten
   * under it: the face designer rebuilds "My dice" every time *Roll it* is
   * pressed, under the same id and the same path. Comparing this with what it
   * was when the picture was decoded is how the renderer knows to decode it
   * again (`docs/dice-sets.md`, "Textures"). Nothing is read or validated
   * here, so it is cheap enough to ask for every die; the path still goes
   * through [ReferencedFile] and [PackageFiles] first, as [read]'s does.
   */
  fun stamp(
    setId: String,
    texturePath: String,
  ): String? {
    val reference = ReferencedFile.parse(texturePath, DiceSetLimits.IMAGE_EXTENSIONS) ?: return null
    // The bundled package changes only with the APK, and an APK update is a
    // new process with an empty cache, so one stamp for its whole life is the
    // truth. Its size is still asked, so a path it does not have stays a miss.
    val files =
      if (bundled != null && setId == bundled.set.id) {
        bundled.files
      } else {
        installed.folderOf(setId)?.let(PackageFiles::of) ?: return null
      }
    return files.size(reference.path)?.let { size -> "$size@${files.modified(reference.path) ?: BUNDLED_STAMP}" }
  }

  /**
   * The file itself, once it is known which package and which path it is.
   *
   * The size is asked for before the bytes are, and both before a decoder is:
   * a texture over the cap is refused without ever being read, which is the
   * same order `FileChecker` keeps during validation.
   */
  private fun opened(
    set: DiceSet,
    files: PackageFiles,
    reference: ReferencedFile,
    named: String,
  ): AtlasDecode {
    val size = files.size(reference.path)
    val refusal =
      when {
        size == null -> ValidationCode.ReferencedFileMissing to "is not here"
        size > DiceSetLimits.MAX_TEXTURE_BYTES ->
          ValidationCode.FileTooLarge to
            "is $size bytes; a texture is at most ${DiceSetLimits.MAX_TEXTURE_MIB} MiB"
        else -> null
      }
    if (refusal != null) return refused(named, refusal.first, refusal.second)
    val bytes = files.read(reference.path) ?: return refused(named, ValidationCode.ReferencedFileMissing, "is not here")
    return decode(bytes, named, set.facesForTexture(reference.path))
  }

  private fun refused(
    file: String,
    code: ValidationCode,
    said: String,
  ): AtlasDecode.Unusable =
    AtlasDecode.Unusable(
      listOf(ValidationMessage(severity = Severity.Error, code = code, text = "'$file' $said", file = file)),
    )

  private companion object {
    /** What the bundled package's files are stamped with: they are the APK's. */
    const val BUNDLED_STAMP = "bundled"
  }
}
