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
  fun theFacesAreTheSizeTheDriverWillAskFor() {
    assertEquals(BASE * BASE * BYTES_PER_PIXEL * RoomLight.FACES, RoomLight.faces().size * Float.SIZE_BYTES)
  }

  @Test
  fun aDirectBufferOffersEveryByteItWasGiven() {
    val pixels = RoomLight.faces()
    val buffer = ByteBuffer.allocateDirect(pixels.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    buffer.asFloatBuffer().put(pixels)
    assertEquals(pixels.size * Float.SIZE_BYTES, buffer.remaining())
  }

  @Test
  fun everyLevelOfTheCubemapIsPrefilteredFromTheSharpOne() {
    // The way the stage does it, and the way that works: one level by hand,
    // the other five by Filament. Uploading level one by hand is what this
    // Filament's JNI refuses ([RoomLight.LEVELS]).
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
          .format(Texture.InternalFormat.R11F_G11F_B10F)
          .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
          .build(engine)
      val pixels = RoomLight.faces()
      val buffer = ByteBuffer.allocateDirect(pixels.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
      buffer.asFloatBuffer().put(pixels)
      texture.generatePrefilterMipmap(
        engine,
        Texture.PixelBufferDescriptor(buffer, Texture.Format.RGB, Texture.Type.FLOAT),
        RoomLight.faceOffsets(),
        Texture.PrefilterOptions(),
      )
      assertEquals(RoomLight.LEVELS, texture.levels)
      engine.destroyTexture(texture)
    } finally {
      engine.destroy()
    }
  }

  private companion object {
    const val BASE = 32

    /** Three floats of four bytes each. */
    const val BYTES_PER_PIXEL = 12
  }
}
