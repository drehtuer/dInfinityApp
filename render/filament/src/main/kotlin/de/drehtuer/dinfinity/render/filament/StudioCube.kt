package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.min

/**
 * The studio panorama folded into the cube a polished die reflects
 * (`docs/physics-and-rendering.md`, "Rendering (normal mode)";
 * `docs/architecture.md`, decision 89).
 *
 * [StudioLight] says where the room is; this turns the decoded file
 * ([Radiance]) into the six faces Filament's prefilter takes, on the same
 * mapping the irradiance was projected on. It is the step Filament's
 * `IBLPrefilterContext.EquirectangularToCubemap` does on the GPU, and it is
 * here rather than there because that class comes with a native library that
 * links gltfio's: 17.5 MB over four ABIs for one texture lookup a texel.
 *
 * **Nothing here touches Filament.** `FilamentEngine.studio` hands the result
 * to `Texture.generatePrefilterMipmap`.
 */
object StudioCube {
  /**
   * How big one face of the reflection cube is: a quarter of the 1k
   * panorama's width, which is every pixel it has around the horizon and no
   * more. A die's lacquer blurs what it mirrors well below this.
   */
  const val FACE_SIZE: Int = 256

  /** Every level down to a single pixel, `log2(256)` and one more, as [RoomLight.LEVELS] does. */
  const val LEVELS: Int = 9

  /**
   * How many directions Filament's prefilter samples per texel of each blurred
   * level. Its default is eight, which is plenty for [RoomLight]'s gradient and
   * leaves a bright window speckled in the middle roughnesses; thirty-two is
   * what Filament's own GPU prefilter takes, and on the CPU it is still tens of
   * milliseconds over the engine's worker threads (`StudioLightDeviceTest`
   * times it).
   */
  const val PREFILTER_SAMPLES: Int = 32

  /**
   * The brightest a texel is allowed to be: just under what
   * `R11F_G11F_B10F` can hold (65,024). Above it a texel is infinite, and one
   * infinite texel turns every blurred level around it white. The shipped
   * studio's brightest pixel is far below this; a file that is not is clamped,
   * as Filament's own folding shader does.
   */
  const val BRIGHTEST: Float = 65_000.0f

  /**
   * The six faces of the reflection cube, [size] pixels square, in Filament's
   * order and [RoomLight.faceOffsets]' layout: linear RGB floats, ready for
   * `generatePrefilterMipmap`.
   *
   * Each texel looks along [RoomLight.direction] — the cube mapping the GPU
   * samples with — and takes the [panorama] pixel that
   * [StudioLight.direction] says looks that way. So the cube is in **the same
   * frame as [StudioLight.IRRADIANCE]**: a surface that mirrors the window and
   * a surface lit by it agree on where the window is, and
   * [StudioLight.rotation] turns both together. That is also why the engine
   * hands it to the prefilter with `mirror` off: Filament's mirroring is for a
   * cube folded the way its own tools fold one, and this one is folded
   * already.
   */
  fun faces(
    panorama: Radiance.Image,
    size: Int = FACE_SIZE,
  ): FloatArray {
    val pixels = FloatArray(RoomLight.FACES * size * size * CHANNELS)
    var at = 0
    for (face in 0 until RoomLight.FACES) {
      for (y in 0 until size) {
        for (x in 0 until size) {
          sample(panorama, RoomLight.direction(face, x, y, size), pixels, at)
          at += CHANNELS
        }
      }
    }
    return pixels
  }

  /**
   * What [panorama] shows looking along [direction] — the inverse of
   * [StudioLight.direction] — blended between the four nearest pixels,
   * written as three floats into [into] at [at]. Wraps round the seam at the
   * back and stops at the poles.
   */
  fun sample(
    panorama: Radiance.Image,
    direction: Vector3,
    into: FloatArray,
    at: Int,
  ) {
    val latitude = asin(direction.y.coerceIn(-1.0, 1.0))
    val longitude = atan2(-direction.x, direction.z)
    val column = (longitude / PI + 1.0) * panorama.width / 2 - HALF
    val row = (1.0 - latitude * 2 / PI) * panorama.height / 2 - HALF
    val left = floor(column)
    val top = floor(row)
    val across = (column - left).toFloat()
    val down = (row - top).toFloat()
    val x0 = Math.floorMod(left.toInt(), panorama.width)
    val x1 = (x0 + 1) % panorama.width
    val y0 = top.toInt().coerceIn(0, panorama.height - 1)
    val y1 = min(top.toInt() + 1, panorama.height - 1).coerceAtLeast(0)
    for (channel in 0 until CHANNELS) {
      val upper = panorama.at(x0, y0, channel) * (1 - across) + panorama.at(x1, y0, channel) * across
      val lower = panorama.at(x0, y1, channel) * (1 - across) + panorama.at(x1, y1, channel) * across
      into[at + channel] = min(upper * (1 - down) + lower * down, BRIGHTEST)
    }
  }

  private const val CHANNELS = Radiance.CHANNELS
  private const val HALF = 0.5
}
