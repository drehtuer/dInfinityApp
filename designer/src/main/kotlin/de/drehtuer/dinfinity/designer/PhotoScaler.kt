package de.drehtuer.dinfinity.designer

import java.io.InputStream

/**
 * A picture somebody chose, as a stream that can be opened more than once.
 *
 * More than once is the point. A photograph is read **twice**: first for its
 * header alone, to find out how big it claims to be, and then — only if that
 * answer was sane — for its pixels, subsampled to a size already decided
 * ([PhotoScaling]). A single stream would have to be rewound, and a content
 * provider's stream is not a thing that rewinds.
 *
 * `null` from [open] is a source that is no longer there, which is an ordinary
 * outcome: the picker hands back a handle to another application's file and
 * that application may have moved it since.
 */
fun interface PhotoSource {
  /** A fresh stream over the picture, or null when it cannot be opened. */
  fun open(): InputStream?
}

/**
 * Turning a chosen picture into the bytes of a table texture.
 *
 * The seam [AtlasPainter] draws, in the other direction and for the same
 * reason (`docs/architecture.md`, decision 55): everything that *decides* is
 * [PhotoScaling], in plain Kotlin a JVM test can assert on, and what is left
 * behind this interface is a decode, a scale and an encoder — things that can
 * fail but cannot be wrong.
 */
fun interface PhotoScaler {
  /**
   * [source] as WebP bytes small enough to be a texture, or null.
   *
   * Null for a picture that is not one, that will not open, that the phone
   * would not give up the memory for, or that would not encode small enough
   * even at [PhotoScaling.SMALLEST_SIDE]. The caller's answer to all of those
   * is the same — the photo cannot be a table — so they are not told apart.
   */
  fun scaled(source: PhotoSource): ByteArray?

  companion object {
    /** Scales nothing. The fallback, and what a test uses when the pixels are not the point. */
    val NONE: PhotoScaler = PhotoScaler { null }
  }
}
