package de.drehtuer.dinfinity.render.filament

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * The studio's folded cube kept on disk between launches
 * (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
 *
 * Decoding the panorama ([Radiance]) and folding it into six faces
 * ([StudioCube.faces]) is Kotlin running over half a million pixels, and on
 * the Pixel 10a it was most of the 558 ms the roll thread's first draw spent
 * building the room — on every cold start, which [MaterialCache] had just
 * brought down to 0.8 s. The faces depend only on the file and the face size,
 * so they are folded once and read back after: a launch that finds them reads
 * 4.7 MB straight into the buffer Filament's prefilter takes, with no
 * conversion at all.
 *
 * Meant to live in `codeCacheDir`, beside the materials, which Android empties
 * on every update. The key still names a hash of the panorama, the face size
 * and the fold's version, so a cache that survived an update would miss rather
 * than be wrong.
 *
 * **Anything that goes wrong with the disk folds instead.** A directory that
 * cannot be made, a file that cannot be read or written, a file of the wrong
 * length, a checksum that does not match — each is a miss, and the room is
 * built the slow way. A cache is never a reason for the tray to be lit wrong.
 *
 * @param dir where the faces are kept, or null for no cache at all — which is
 *   what a device test that measures the fold, or wants a clean engine, uses.
 */
class StudioCache(
  private val dir: File?,
) {
  /** Six faces, as [StudioCube.faces] lays them out, and whether they came off the disk. */
  class Faces(
    /** Native-order floats, positioned at nought, exactly the faces. */
    val buffer: ByteBuffer,
    /** True when read back rather than folded on this launch. */
    val fromDisk: Boolean,
  )

  /**
   * The faces of [hdr] at [size], read from disk or made by [fold] and kept;
   * null when [fold] makes none — a file that is not a panorama, which is
   * not worth remembering.
   */
  fun faces(
    hdr: ByteArray,
    size: Int = StudioCube.FACE_SIZE,
    fold: () -> FloatArray?,
  ): Faces? {
    val expected = bytesOf(size)
    val file = dir?.let { File(it, keyOf(hdr, size) + SUFFIX) }
    file?.let { read(it, expected) }?.let { return Faces(it, fromDisk = true) }
    val folded = fold() ?: return null
    require(folded.size * Float.SIZE_BYTES == expected) {
      "a $size-pixel cube is $expected bytes, not ${folded.size * Float.SIZE_BYTES}"
    }
    val buffer = ByteBuffer.allocateDirect(expected).order(ByteOrder.nativeOrder())
    buffer.asFloatBuffer().put(folded)
    file?.let { write(it, buffer.duplicate().order(ByteOrder.nativeOrder())) }
    return Faces(buffer, fromDisk = false)
  }

  /**
   * The faces in [file], if it holds exactly [expected] bytes of them and the
   * checksum after them agrees.
   */
  private fun read(
    file: File,
    expected: Int,
  ): ByteBuffer? =
    try {
      val whole = expected + CHECKSUM_BYTES
      if (file.isFile && file.length() == whole.toLong()) verified(bytesOf(file, whole), expected) else null
    } catch (_: IOException) {
      null
    }

  /** All [size] bytes of [file] in a direct buffer, flipped for reading, or null if it ends early. */
  private fun bytesOf(
    file: File,
    size: Int,
  ): ByteBuffer? =
    RandomAccessFile(file, "r").use { input ->
      val whole = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
      while (whole.hasRemaining()) {
        if (input.channel.read(whole) < 0) return null
      }
      whole.flip()
      whole
    }

  /** The first [expected] bytes of [whole], if the checksum written after them agrees. */
  private fun verified(
    whole: ByteBuffer?,
    expected: Int,
  ): ByteBuffer? {
    if (whole == null) return null
    val stored = whole.getLong(expected)
    whole.limit(expected)
    val faces = whole.slice().order(ByteOrder.nativeOrder())
    return faces.takeIf { checksumOf(it.duplicate()) == stored }
  }

  /**
   * Written beside the real name and renamed into place, so a process killed
   * half-way leaves a stray temporary file rather than half a cube.
   */
  private fun write(
    file: File,
    faces: ByteBuffer,
  ) {
    val partial = File(file.parentFile, file.name + PARTIAL)
    try {
      file.parentFile?.mkdirs()
      val trailer = ByteBuffer.allocate(CHECKSUM_BYTES).order(ByteOrder.nativeOrder())
      trailer.putLong(0, checksumOf(faces.duplicate()))
      RandomAccessFile(partial, "rw").use { output ->
        output.setLength(0)
        val channel = output.channel
        while (faces.hasRemaining()) channel.write(faces)
        while (trailer.hasRemaining()) channel.write(trailer)
      }
      if (!partial.renameTo(file)) discard(partial)
    } catch (_: IOException) {
      discard(partial)
    }
  }

  private fun discard(partial: File) {
    if (partial.exists() && !partial.delete()) partial.deleteOnExit()
  }

  companion object {
    /** No disk at all: the panorama is folded every time. */
    val NONE = StudioCache(dir = null)

    /**
     * Which fold the kept faces came from. Raised whenever [StudioCube] or
     * [StudioLight.direction] changes what a texel holds, so an old cube is
     * a miss instead of a room turned the wrong way.
     */
    const val FOLD_VERSION: Int = 1

    private const val SUFFIX = ".cube"
    private const val PARTIAL = ".partial"
    private const val HASH_CHARS = 16
    private const val CHECKSUM_BYTES = Long.SIZE_BYTES

    /** How many bytes the six faces of a [size]-pixel cube take as RGB floats. */
    fun bytesOf(size: Int): Int {
      require(size > 0) { "a cube $size pixels across has no faces" }
      return RoomLight.FACES * size * size * Radiance.CHANNELS * Float.SIZE_BYTES
    }

    /**
     * The name the faces of [hdr] at [size] are kept under: a hash of the
     * file, the size, [FOLD_VERSION] and the byte order they are written in.
     */
    fun keyOf(
      hdr: ByteArray,
      size: Int,
      order: ByteOrder = ByteOrder.nativeOrder(),
    ): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(hdr)
      val hash = digest.joinToString("") { "%02x".format(it) }.take(HASH_CHARS)
      val endian = if (order == ByteOrder.LITTLE_ENDIAN) "le" else "be"
      return "studio-$hash-$size-v$FOLD_VERSION-$endian"
    }

    /** A CRC-32 of what is left in [bytes]. */
    fun checksumOf(bytes: ByteBuffer): Long = CRC32().apply { update(bytes) }.value
  }
}
