package de.drehtuer.dinfinity.render.filament

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The room, uploaded to a real driver.
 *
 * A cubemap is the one texture in this app that is neither an atlas nor a
 * single pixel, and Filament checks the buffer it is handed against the region
 * it is told to fill. Getting that wrong throws from inside the driver with a
 * message about sizes, which is a long way from the arithmetic that produced
 * them — so this asserts the arithmetic *here*, on the device, and then does
 * the upload.
 */
@RunWith(AndroidJUnit4::class)
class RoomLightUploadTest {
  @Test
  fun theLevelsAreTheSizeTheDriverWillAskFor() {
    assertEquals(BASE * BASE * BYTES_PER_PIXEL * RoomLight.FACES, RoomLight.level(0).size)
    assertEquals(HALF * HALF * BYTES_PER_PIXEL * RoomLight.FACES, RoomLight.level(1).size)
  }

  @Test
  fun aDirectBufferOffersEveryByteItWasGiven() {
    val pixels = RoomLight.level(1)
    val buffer = ByteBuffer.allocateDirect(pixels.size).order(ByteOrder.nativeOrder())
    buffer.put(pixels)
    buffer.rewind()
    assertEquals(pixels.size, buffer.remaining())
  }

  @Test
  fun everyLevelOfTheCubemapUploads() {
    FilamentStage.ready()
    val engine = Engine.create()
    try {
      val texture =
        Texture
          .Builder()
          .width(RoomLight.SIZE)
          .height(RoomLight.SIZE)
          .depth(RoomLight.FACES)
          .levels(RoomLight.LEVELS)
          .format(Texture.InternalFormat.RGBA8)
          .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
          .build(engine)
      for (level in 0 until RoomLight.LEVELS) {
        val side = (RoomLight.SIZE shr level).coerceAtLeast(1)
        val pixels = RoomLight.level(level = level)
        val buffer = ByteBuffer.allocateDirect(pixels.size).order(ByteOrder.nativeOrder())
        buffer.put(pixels)
        buffer.rewind()
        texture.setImage(
          engine,
          level,
          0,
          0,
          0,
          side,
          side,
          RoomLight.FACES,
          Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE),
        )
      }
      engine.destroyTexture(texture)
    } finally {
      engine.destroy()
    }
  }

  private companion object {
    const val BASE = 32
    const val HALF = 16
    const val BYTES_PER_PIXEL = 4
  }
}
