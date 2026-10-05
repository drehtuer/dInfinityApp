package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * How much of the dice a glossy table shows back (`docs/physics-and-rendering.md`,
 * "The dice in a glossy table").
 *
 * **A planar reflection of the dice and nothing else.** The dice are drawn a
 * second time, small, by a camera standing under the floor — the main camera
 * turned over in the floor's plane — and the floor samples that picture where
 * it stands on the screen. What it does with it is glass's own arithmetic:
 * the share it reflects is the Fresnel term of a dielectric, four per cent
 * looking straight down and more at a glance, times [strength]; and where a
 * die is seen in it, the room the floor reflected there is hidden by as much.
 *
 * Not Filament's screen-space reflections, which reflect *whatever is on the
 * screen* — the walls included. A dark wall in a dark glass is a dark band
 * along the foot of every wall, which is the band the device sessions spent
 * three changes getting rid of; and in this scene's millimetres the blur
 * Filament derives from a surface's roughness comes out sharp, and its fade
 * for a ray that turns back towards the camera leaves a table seen from
 * straight above showing almost nothing (`docs/architecture.md`, decision 93).
 *
 * Nothing here touches a GPU: which look reflects, how strongly, how small the
 * picture is and where the camera under the floor stands are all arithmetic,
 * and are tested on a JVM. [FilamentStage] draws what this says.
 *
 * @param strength how much of a perfect glass reflection this table gives,
 *   nought to one. One is polished glass, which is still only the four per
 *   cent glass reflects; less is a surface whose gloss has started to go.
 */
data class Reflection(
  val strength: Double,
) {
  companion object {
    /**
     * The roughest table that shows the dice at all.
     *
     * Past this a reflection is spread so wide that what is left of it is the
     * sheen the room already puts on the table, and that is what Filament's
     * own lighting draws. Below it the strength rises in a straight line to
     * one at a perfect polish. Felt (0.9), the plain table (0.8) and oak
     * (0.55) are all well over, so they draw exactly what they drew before;
     * dark glass (0.1) is two-thirds of the way to a mirror-polish.
     */
    const val GLOSSIEST_MATTE: Double = 0.3

    /**
     * How many times smaller than the screen, on each side, the picture of the
     * dice is drawn.
     *
     * This is the blur, and it is most of what makes the reflection read as
     * glossy glass rather than a mirror: a die's reflection is drawn about a
     * quarter of its size and stretched back, so its edges are soft and the
     * numbers in it are a smudge that cannot compete with the real ones. It is
     * also the cost: a sixteenth of the pixels.
     */
    const val SHRINK: Int = 4

    /** The floor's height, which is the plane the camera is turned over in (`TrayMesh`). */
    const val FLOOR_MM: Double = 0.0

    /**
     * How [look] reflects the dice, or null for a table that does not.
     *
     * Read off the look's own roughness rather than a field of its own: how
     * glossy a surface is *is* how much it reflects, and a table format that
     * could say "rough, but a mirror" would be describing something nobody
     * can make. A look outside the limits is read as the clamped look it is
     * drawn as.
     */
    fun of(look: TableLook): Reflection? {
      val strength = strengthOf(look.clampedToLimits().roughness)
      return if (strength > 0.0) Reflection(strength) else null
    }

    /** How strongly a surface of [roughness] reflects: one when polished, nought from [GLOSSIEST_MATTE] up. */
    fun strengthOf(roughness: Double): Double = (1.0 - roughness / GLOSSIEST_MATTE).coerceIn(0.0, 1.0)

    /**
     * How big the picture of the dice is for a viewport of [width] by
     * [height]: [SHRINK] times smaller, rounded up so a sliver of a viewport
     * still has a pixel.
     */
    fun sizeOf(
      width: Int,
      height: Int,
    ): Pair<Int, Int> = shrunk(width) to shrunk(height)

    /**
     * Where the camera that takes that picture stands: [shot], turned over in
     * the floor's plane.
     *
     * Every point the main camera sees reflected in the floor, this one sees
     * directly — from below, looking up through a floor it does not draw. It
     * is a real camera, not a mirrored one: what it sees is the reflection
     * the other way round across the screen, so the floor reads it with its
     * horizontal coordinate turned round ([DiceMaterial.GLASS_SOURCE]).
     * Turning the camera itself over instead would turn every triangle inside
     * out with it.
     */
    fun mirrored(shot: CameraShot): CameraShot =
      shot.copy(
        position = turnedOver(shot.position),
        target = turnedOver(shot.target),
        up = Vector3(shot.up.x, shot.up.y, -shot.up.z),
      )

    private fun turnedOver(point: Vector3): Vector3 = Vector3(point.x, point.y, 2 * FLOOR_MM - point.z)

    private fun shrunk(pixels: Int): Int = maxOf(1, (pixels + SHRINK - 1) / SHRINK)
  }
}
