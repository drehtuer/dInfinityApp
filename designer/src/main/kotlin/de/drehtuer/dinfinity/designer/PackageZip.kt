package de.drehtuer.dinfinity.designer

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A package as the one file it is shared as (`docs/face-designer.md`,
 * "Export details": "a zip of the set folder, which can be uploaded to a git
 * repository as-is").
 *
 * A zip rather than a tarball because that is what a phone's share sheet, a
 * mail client and a forge's "upload files" all understand, and because it is
 * one of the two archives the installer already accepts — so a set exported
 * from one phone installs on the next one down the same path as anything
 * downloaded (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * The files sit at the root of the archive rather than inside a folder named
 * after the set: the installer looks for `diceset.toml` anywhere in the
 * archive, and a wrapper directory is one more name to get wrong.
 */
object PackageZip {
  /**
   * [files] as the bytes of a zip.
   *
   * Entries go in by name, and every one of them is stamped with the same
   * fixed time. Two exports of a drawing nobody has touched are then the same
   * file, which is what makes "has this changed since I uploaded it" a
   * question somebody can answer by looking — a clock in the archive would
   * make every export differ from the last for no reason anybody cares about.
   */
  fun of(files: Map<String, ByteArray>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      files.entries.sortedBy { it.key }.forEach { (path, contents) ->
        zip.putNextEntry(ZipEntry(path).apply { time = STAMP })
        zip.write(contents)
        zip.closeEntry()
      }
    }
    return bytes.toByteArray()
  }

  /**
   * The moment every entry claims to have been written.
   *
   * 1980-01-01, which is the earliest a zip can express: the format keeps its
   * times in the DOS encoding, and a zero would be written out as something
   * else and read back as something else again.
   */
  private const val STAMP: Long = 315_532_800_000L
}
