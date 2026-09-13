package de.drehtuer.dinfinity.dicesets.install

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.File

/**
 * Archives written the way an attacker would write them.
 *
 * Every hostile case in `docs/dice-sets.md` and `SECURITY.md` is built here —
 * a path that climbs out, a symbolic link, a file called `../../etc/passwd`, a
 * thing that expands to far more than it weighs — because the only way to know
 * the extractor refuses them is to hand it one and watch.
 */
internal object Archives {
  /** The smallest dice set that installs. */
  const val MINIMAL_TOML: String = """
format = 1

[set]
id = "fixture-set"
name = "Fixture"
version = "1.0.0"

[[die]]
id = "d6"
shape = "cube"
faces = [1, 2, 3, 4, 5, 6]
"""

  /** A zip of the given entries, each a path and its contents. */
  fun zip(
    into: File,
    entries: Map<String, ByteArray>,
    symlinks: Set<String> = emptySet(),
  ): File {
    val file = File(into, "package-${System.nanoTime()}.zip")
    ZipArchiveOutputStream(file).use { out ->
      entries.forEach { (path, bytes) ->
        val entry = ZipArchiveEntry(path)
        if (path in symlinks) entry.unixMode = SYMLINK_MODE
        entry.size = bytes.size.toLong()
        out.putArchiveEntry(entry)
        out.write(bytes)
        out.closeArchiveEntry()
      }
    }
    return file
  }

  /** A gzipped tar, which is what every git forge hands out. */
  fun tarGz(
    into: File,
    entries: Map<String, ByteArray>,
    symlinks: Map<String, String> = emptyMap(),
  ): File {
    val file = File(into, "package-${System.nanoTime()}.tar.gz")
    TarArchiveOutputStream(GzipCompressorOutputStream(file.outputStream())).use { out ->
      out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
      entries.forEach { (path, bytes) ->
        val entry = TarArchiveEntry(path)
        entry.size = bytes.size.toLong()
        out.putArchiveEntry(entry)
        out.write(bytes)
        out.closeArchiveEntry()
      }
      symlinks.forEach { (path, target) ->
        val entry = TarArchiveEntry(path, TarArchiveEntry.LF_SYMLINK)
        entry.linkName = target
        out.putArchiveEntry(entry)
        out.closeArchiveEntry()
      }
    }
    return file
  }

  /** A perfectly ordinary package: one set file, in a folder, as a forge would send it. */
  fun wellFormed(
    into: File,
    prefix: String = "brass-and-bone-abc123/",
  ): File = tarGz(into, mapOf("${prefix}diceset.toml" to MINIMAL_TOML.encodeToByteArray()))

  /** Bytes that compress to nothing and expand to [megabytes]. */
  fun compressible(megabytes: Int): ByteArray = ByteArray(megabytes * ONE_MIB)

  private const val ONE_MIB = 1024 * 1024

  /** The unix mode bits that say "this is a symbolic link". */
  private const val SYMLINK_MODE = 0xA000 or 0x1FF
}
