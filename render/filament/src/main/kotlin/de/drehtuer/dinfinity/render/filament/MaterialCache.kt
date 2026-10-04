package de.drehtuer.dinfinity.render.filament

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Compiled dice materials kept on disk between launches.
 *
 * Compiling the material on the device (`docs/architecture.md`, decision 46)
 * took the Pixel 10a about eight seconds of a black tray on *every* cold start:
 * nine-tenths of the CPU in those seconds was `libfilamat`. The packet it
 * produces depends only on the material source, which variant it is and the
 * backend it was compiled for — so it is compiled once and read back after.
 *
 * Meant to live in the app's `codeCacheDir`, which Android empties whenever
 * the app is updated: a new APK may carry a new Filament, whose packets the
 * old ones need not match, and that directory is the one the platform already
 * invalidates for exactly that reason. The key still names the source's hash,
 * so a cache that somehow survived an update would miss rather than be wrong.
 *
 * **Anything that goes wrong with the disk compiles instead.** A cache is
 * never a reason for the tray not to appear: a directory that cannot be made, a
 * file that cannot be read or written, an empty file — each is a miss.
 *
 * @param dir where packets are kept, or null for no cache at all — which is
 *   what a device test that times compilation, or wants a clean engine, uses.
 */
class MaterialCache(
  private val dir: File?,
) {
  /**
   * The packet for [key], read from disk or made by [compile] and kept.
   *
   * The returned buffer is positioned at zero and holds exactly the packet.
   */
  fun packet(
    key: String,
    compile: () -> ByteBuffer,
  ): ByteBuffer {
    val file = dir?.let { File(it, "$key$SUFFIX") } ?: return compile()
    read(file)?.let { return it }
    val made = compile()
    write(file, made.duplicate())
    return made
  }

  private fun read(file: File): ByteBuffer? =
    try {
      file.takeIf { it.isFile }?.readBytes()?.takeIf { it.isNotEmpty() }?.let { bytes ->
        ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.flip() }
      }
    } catch (_: IOException) {
      null
    }

  /**
   * Written beside the real name and renamed into place, so a process killed
   * half-way leaves a stray temporary file rather than half a material.
   */
  private fun write(
    file: File,
    packet: ByteBuffer,
  ) {
    val bytes = ByteArray(packet.remaining()).also { packet.get(it) }
    val partial = File(file.parentFile, file.name + PARTIAL)
    try {
      file.parentFile?.mkdirs()
      partial.writeBytes(bytes)
      if (!partial.renameTo(file)) discard(partial)
    } catch (_: IOException) {
      discard(partial)
    }
  }

  /**
   * A temporary file that did not become the packet. One that cannot be
   * deleted now is deleted when the process ends, so a failed write never
   * leaves litter behind for longer than one run.
   */
  private fun discard(partial: File) {
    if (partial.exists() && !partial.delete()) partial.deleteOnExit()
  }

  companion object {
    /** No disk at all: every packet is compiled. */
    val NONE = MaterialCache(dir = null)

    private const val SUFFIX = ".filamat"
    private const val PARTIAL = ".partial"
    private const val HASH_CHARS = 16

    /**
     * The name a packet is kept under: what it was compiled from, for which
     * backend, and which variant of the dice material it is
     * (`DiceMaterial.Variant`).
     */
    fun keyOf(
      source: String,
      backend: String,
      variant: String,
    ): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
      val hash = digest.joinToString("") { "%02x".format(it) }.take(HASH_CHARS)
      return "dice-${variant.lowercase()}-${backend.lowercase()}-$hash"
    }
  }
}
