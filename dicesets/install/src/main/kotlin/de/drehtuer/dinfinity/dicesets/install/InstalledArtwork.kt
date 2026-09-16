package de.drehtuer.dinfinity.dicesets.install

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
 * @param installed the folders on disk.
 * @param decode how bytes become pixels. A parameter only so that a test can
 *   run the path without a decoder, which on the JVM there is not one of.
 */
class InstalledArtwork(
  private val installed: InstalledSets,
  private val decode: (ByteArray, String, Int?) -> AtlasDecode = AtlasDecoder.platform::decode,
) {
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
    val ready =
      installed.find(setId) as? InstalledPackage.Ready
        ?: return refused(named, ValidationCode.ReferencedFileMissing, "is not in a package that is installed")
    val reference =
      ReferencedFile.parse(texturePath, DiceSetLimits.IMAGE_EXTENSIONS)
        ?: return refused(named, ValidationCode.BadFileReference, "is not a picture inside the package")
    return opened(ready, reference, named)
  }

  /**
   * The file itself, once it is known which package and which path it is.
   *
   * The size is asked for before the bytes are, and both before a decoder is:
   * a texture over the cap is refused without ever being read, which is the
   * same order `FileChecker` keeps during validation.
   */
  private fun opened(
    ready: InstalledPackage.Ready,
    reference: ReferencedFile,
    named: String,
  ): AtlasDecode {
    val files = PackageFiles.of(ready.folder)
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
    return decode(bytes, named, ready.set.facesForTexture(reference.path))
  }

  private fun refused(
    file: String,
    code: ValidationCode,
    said: String,
  ): AtlasDecode.Unusable =
    AtlasDecode.Unusable(
      listOf(ValidationMessage(severity = Severity.Error, code = code, text = "'$file' $said", file = file)),
    )
}
