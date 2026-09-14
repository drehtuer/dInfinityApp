package de.drehtuer.dinfinity.dicesets.install

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Where a package came from, written beside it when it was installed
 * (`docs/architecture.md`, "Storage layout").
 *
 * This is the app's own note to itself, not part of the package: an author
 * never writes one, and one found inside a downloaded archive is overwritten
 * by the install rather than believed. What it is for is the details screen
 * (`docs/dice-sets.md`, design `6a`) — which link this came from and which
 * commit — and the update check, which has nothing to compare against without
 * it.
 *
 * **Read through a parser and written through one.** It is a file on a device
 * the app does not own: an editor, a backup restore or a half-finished write
 * can leave anything at all in it, so [read] answers [Unknown] rather than
 * throwing, and every field is optional because any of them may be missing
 * from a file written by an older version. Building the JSON by hand is the
 * other half of the same mistake — the escaping is easy to get nearly right,
 * and a source URL with a control character in it would produce a file that
 * this reader could not read back.
 */
data class PackageMeta(
  val source: String? = null,
  val sha256: String? = null,
  val commit: String? = null,
  val version: String? = null,
  val installedAtEpochMs: Long? = null,
) {
  /** This, as the file's contents. */
  fun asJson(): String =
    JSON.encodeToString(
      JsonObject.serializer(),
      buildJsonObject {
        source?.let { put(SOURCE, JsonPrimitive(it)) }
        sha256?.let { put(SHA256, JsonPrimitive(it)) }
        commit?.let { put(COMMIT, JsonPrimitive(it)) }
        version?.let { put(VERSION, JsonPrimitive(it)) }
        installedAtEpochMs?.let { put(INSTALLED_AT, JsonPrimitive(it)) }
      },
    )

  companion object {
    /** Nothing is known about where this package came from. */
    val Unknown: PackageMeta = PackageMeta()

    /** The file's name inside a package folder. */
    const val FILE_NAME: String = ".meta.json"

    private const val SOURCE = "source"
    private const val SHA256 = "sha256"
    private const val COMMIT = "commit"
    private const val VERSION = "version"
    private const val INSTALLED_AT = "installedAt"

    private val JSON = Json { prettyPrint = true }

    /**
     * [text] as a meta, or [Unknown] if it is not one.
     *
     * Anything unreadable answers [Unknown]: a set whose note to itself has
     * been corrupted is still a set, and refusing to list it because the app
     * cannot remember where it came from would lose the player a working
     * package over a detail the screen prints in grey.
     */
    fun read(text: String): PackageMeta {
      val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return Unknown
      return PackageMeta(
        source = root.text(SOURCE),
        sha256 = root.text(SHA256),
        commit = root.text(COMMIT),
        version = root.text(VERSION),
        installedAtEpochMs = root[INSTALLED_AT]?.asPrimitive()?.longOrNull,
      )
    }

    /**
     * A string field, or null when it is absent or is not a string.
     *
     * A number where a string was expected is not read as its digits: this
     * file is data, and quietly coercing whatever is there is how a field
     * comes to mean two things.
     */
    private fun JsonObject.text(name: String): String? = this[name]?.asPrimitive()?.takeIf { it.isString }?.content

    private fun kotlinx.serialization.json.JsonElement.asPrimitive(): JsonPrimitive? =
      runCatching { jsonPrimitive }.getOrNull()
  }
}
