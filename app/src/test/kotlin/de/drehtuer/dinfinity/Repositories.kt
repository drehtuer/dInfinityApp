package de.drehtuer.dinfinity

import okio.Buffer
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.ByteArrayOutputStream

/**
 * Repositories as a forge hands them out, and as an attacker would.
 *
 * A forge answers "give me this ref" with a gzipped tar in which everything is
 * wrapped one folder deep — `repo-<sha>/` — so that is what these write. The
 * point of building the real thing rather than stubbing the extractor is that
 * the wrapper, the paths and the compression are exactly the parts an import
 * from a repository has to get right (`docs/dice-notation.md`).
 */
internal object Repositories {
  /** The smallest collection that imports. */
  const val THORIN: String = """
    {
      "format": 1,
      "name": "Thorin",
      "groups": [{ "id": "thorin", "name": "Thorin", "icon": "x", "parent": null }],
      "rolls": [{ "group": "thorin", "name": "Longsword", "formula": "1d20 + 7" }]
    }
  """

  /** What the app itself would name an exported collection. */
  const val COLLECTION: String = "thorin.dinfinity.json"

  /**
   * A repository of [files], wrapped the way a forge wraps one.
   *
   * @param prefix the wrapper folder, or `""` for an archive with none — which
   *   is what somebody zipping the contents of a directory produces.
   */
  fun tarGz(
    files: Map<String, String>,
    prefix: String = "monsters-deadbeef/",
  ): Buffer = bytes(files.mapKeys { (path, _) -> prefix + path }.mapValues { (_, text) -> text.encodeToByteArray() })

  /** A repository holding one ordinary collection at its root. */
  fun wellFormed(
    collection: String = THORIN,
    named: String = COLLECTION,
  ): Buffer = tarGz(mapOf(named to collection))

  /** Entries written verbatim, so a path can be as hostile as it likes. */
  fun bytes(entries: Map<String, ByteArray>): Buffer {
    val raw = ByteArrayOutputStream()
    TarArchiveOutputStream(GzipCompressorOutputStream(raw)).use { out ->
      out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
      entries.forEach { (path, content) ->
        val entry = TarArchiveEntry(path)
        entry.size = content.size.toLong()
        out.putArchiveEntry(entry)
        out.write(content)
        out.closeArchiveEntry()
      }
    }
    return Buffer().write(raw.toByteArray())
  }

  /** Bytes that compress to nothing and expand to [megabytes], which is the bomb. */
  fun bomb(megabytes: Int): ByteArray = ByteArray(megabytes * ONE_MIB)

  private const val ONE_MIB = 1024 * 1024
}
