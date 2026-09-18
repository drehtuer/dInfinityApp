package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.DieAtRest
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * What the renderer decides, asked where it is decided.
 *
 * None of this needs a GPU, which is the point: a device can show that a frame
 * was drawn, but only a test like this can say that the dice in it were the
 * right size, that the camera moved to them when they settled, and that a
 * second roll did not arrive on top of the first
 * (`docs/architecture.md`, decision 40).
 */
class FilamentDiceRendererTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")
  private val stage = FakeStage()
  private val renderer = FilamentDiceRenderer(stage)

  @Test
  fun `a throw puts a tray, its rim and every die in the scene`() {
    renderer.begin(spec(), geometry, look)

    assertTrue("a scene with no lights in it is a black picture", stage.lit)
    assertEquals("a floor, its walls, its rim and three dice", TRAY_PARTS + 3, stage.added.size)
  }

  @Test
  fun `each die is drawn at the size the capacity rule picked`() {
    val scale = 0.6
    renderer.begin(spec(scale), geometry, look)

    spec(scale).dice.forEachIndexed { index, instance ->
      val reach = instance.die.material.boundingRadiusMm * scale
      val drawn =
        stage.added[TRAY_PARTS + index]
          .first.positions
          .take(3)
      val corner = Vector3(drawn[0].toDouble(), drawn[1].toDouble(), drawn[2].toDouble())

      assertEquals("${instance.die.id} is drawn the wrong size", reach, corner.length, TOLERANCE)
    }
  }

  @Test
  fun `a die with no artwork is given its numbers to print`() {
    renderer.begin(spec(), geometry, look)

    spec().dice.indices.forEach { index ->
      val parameters = stage.added[TRAY_PARTS + index].second
      assertTrue("a plain die was drawn blank", parameters.numbered)
    }
    assertFalse("the tray prints nothing", stage.added[0].second.numbered)
  }

  @Test
  fun `the same die's numbers are built once, however many are thrown`() {
    // `20d20` is twenty of the same die. Turning the same labels into the same
    // distance field twenty times is a pause between pressing Roll and the
    // dice appearing.
    val twenty = StandardDice.d20
    val many =
      spec().copy(
        dice =
          List(20) {
            DieInstance(index = it, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = twenty)
          },
      )

    renderer.begin(many, geometry, look)

    val fields = (0 until 20).map { stage.added[TRAY_PARTS + it].second.numbers }
    assertEquals("the field was rebuilt per body", 1, fields.distinct().size)
  }

  @Test
  fun `a die with artwork names it by package and path, and is printed as well`() {
    // An atlas may leave a face's cell clear, and that face carries its label
    // (`docs/dice-sets.md`, "Textures"), so both are handed to the material
    // and the artwork's alpha decides between them per pixel.
    val painted = StandardDice.d6.copy(texturePath = "textures/d6.png")
    val throwSpec =
      spec().copy(
        dice = listOf(DieInstance(index = 0, groupId = 0, setId = "brass", requestedSetId = "brass", die = painted)),
      )

    renderer.begin(throwSpec, geometry, look)

    val parameters = stage.added[TRAY_PARTS].second
    assertTrue(parameters.textured)
    assertEquals(
      "a path with no package names two sets' pictures at once",
      AtlasKey.of("brass", "textures/d6.png"),
      parameters.texturePath,
    )
    assertTrue("a clear cell has nothing to show through to", parameters.numbered)
  }

  @Test
  fun `a die already down wears its own package's artwork`() {
    // A die an explosion landed among came from some set, and the throw that
    // draws it is not that set's: the resting die carries its own.
    val painted = StandardDice.d6.copy(texturePath = "textures/d6.png")
    val resting = DieAtRest(painted, "brass", RestingPlace(Vector3(0.0, 0.0, 8.0), Quaternion.Identity))

    renderer.begin(added(listOf(resting)), geometry, look)

    assertEquals(AtlasKey.of("brass", "textures/d6.png"), stage.added[TRAY_PARTS].second.texturePath)
  }

  @Test
  fun `the tray takes the table's colours and the rim takes no texture`() {
    val felt =
      look.copy(
        floorTexturePath = "tables/felt.png",
        wallTexturePath = "tables/oak.png",
        floorColorArgb = 0xFF1F5E3A.toInt(),
      )

    renderer.begin(spec(), geometry, felt)

    assertEquals(Colour.of(felt.floorColorArgb), stage.added[0].second.colour)
    assertEquals("tables/felt.png", stage.added[0].second.texturePath)
    assertEquals("tables/oak.png", stage.added[1].second.texturePath)
    assertFalse("six millimetres of rim is not where anybody looks", stage.added[2].second.textured)
  }

  @Test
  fun `no part of the tray throws a shadow, and every die does`() {
    // The device session's complaint: the rim's band across the top of the
    // wall cast a stripe down its own felt. What a shadow is for here is
    // saying a die is on the table rather than over it, and the furniture
    // says nothing (`docs/physics-and-rendering.md`, "What is drawn over the
    // table").
    renderer.begin(spec(), geometry, look)

    assertEquals(
      "the floor, the wall or the rim was still casting",
      List(TRAY_PARTS) { false },
      stage.casting.take(TRAY_PARTS),
    )
    assertEquals(
      "a die stopped casting its shadow with the tray",
      List(spec().dice.size) { true },
      stage.casting.drop(TRAY_PARTS),
    )
  }

  @Test
  fun `and the tray is still shadowless once it has been rebuilt`() {
    // A second throw — and a die an explosion adds — throws the scene away
    // and builds it again, so the flag has to travel with the mesh rather
    // than be something the first build happened to do.
    renderer.begin(spec(), geometry, look)

    renderer.begin(spec(), geometry, look)

    assertEquals(List(TRAY_PARTS) { false }, stage.casting.take(TRAY_PARTS))
    assertTrue("a die was rebuilt shadowless", stage.casting.drop(TRAY_PARTS).all { it })
  }

  @Test
  fun `a roll starts with the whole tray in shot`() {
    renderer.begin(spec(), geometry, look)

    assertEquals(TrayCamera.framingTheTray(geometry, ASPECT), stage.shots.single())
  }

  @Test
  fun `a renderer told to look straight down looks straight down`() {
    // The player's **Table view**, carried as far as the one line that aims
    // the camera. Everything else about the scene is the same: this is a
    // camera, not a projection (`docs/physics-and-rendering.md`).
    val flatStage = FakeStage()
    val flat = FilamentDiceRenderer(flatStage, TableView.StraightDown)

    flat.begin(spec(), geometry, look)
    renderer.begin(spec(), geometry, look)

    assertEquals(
      TrayCamera.framingTheTray(geometry, ASPECT, tiltDegrees = TrayCamera.NO_TILT_DEGREES),
      flatStage.shots.single(),
    )
    assertTrue("the two positions took the same shot", flatStage.shots.single() != stage.shots.single())
    assertEquals("the same table, with the same things in it", stage.added.size, flatStage.added.size)
    assertTrue("a scene with no lights in it is a black picture", flatStage.lit)
  }

  @Test
  fun `looking closer keeps the lean the player chose`() {
    // Pinching in is the player moving, not the setting changing: the shot is
    // still the straight-down one, closer.
    val flatStage = FakeStage()
    val flat = FilamentDiceRenderer(flatStage, TableView.StraightDown)
    val closer = TrayView(zoom = 2.0).within(geometry)
    flat.begin(spec(), geometry, look)

    flat.look(closer)

    assertEquals(
      TrayCamera.framingTheTray(geometry, ASPECT, closer, TrayCamera.NO_TILT_DEGREES),
      flatStage.shots.last(),
    )
  }

  @Test
  fun `every die is put where the frame says, blended`() {
    renderer.begin(spec(), geometry, look)
    val moving =
      RenderFrame(
        previous = List(3) { at(it, Vector3.Zero) },
        current = List(3) { at(it, Vector3(10.0, 0.0, 0.0)) },
        interpolation = 0.5,
      )

    renderer.show(moving)

    assertEquals("a frame was not drawn", 1, stage.frames)
    stage.placed.values.forEach {
      assertEquals("a die halfway between two steps is halfway", 5.0f, it[TRANSLATION_X], FLOAT_TOLERANCE)
    }
  }

  @Test
  fun `settling leaves the camera where it was, with the whole tray in shot`() {
    // A camera that closes in on the dice when they land takes the table away
    // with it, and a player cannot then tell four dice from two, or see that
    // one has finished against the far wall. Looking closer is a thing the
    // player does, by pinching (`docs/TODO.md`, Step 4.1).
    renderer.begin(spec(), geometry, look)
    val framing = stage.shots.single()

    renderer.settled(RenderFrame.still(List(3) { at(it, Vector3(it * 40.0 - 40.0, 0.0, 8.0)) }))

    assertEquals("the camera moved when the dice stopped", listOf(framing), stage.shots)
    assertEquals(TrayCamera.framingTheTray(geometry, ASPECT), framing)
  }

  @Test
  fun `a die the frame stops mentioning is taken out of the scene`() {
    // A counted die is off the table and its floor is free, so the next throw
    // may land exactly where it was standing. Leaving it drawn there would put
    // two dice in one place (`docs/TODO.md`, Step 5.5).
    renderer.begin(spec(), geometry, look)
    renderer.show(RenderFrame.still(List(3) { at(it, Vector3.Zero) }))
    val onTheTable = stage.placed.keys.toSet()

    // The middle die has been counted: the frame no longer carries it.
    renderer.show(RenderFrame.still(listOf(at(0, Vector3.Zero), at(2, Vector3.Zero))))

    assertEquals("one die was counted, so one die leaves the scene", 1, stage.taken.size)
    assertEquals(
      "the die taken out of the scene was not the one the frame dropped",
      onTheTable - stage.placed.keys,
      stage.taken.toSet(),
    )
  }

  @Test
  fun `a die already taken out is not taken out again`() {
    renderer.begin(spec(), geometry, look)
    renderer.show(RenderFrame.still(List(3) { at(it, Vector3.Zero) }))
    val fewer = RenderFrame.still(listOf(at(0, Vector3.Zero), at(2, Vector3.Zero)))

    renderer.show(fewer)
    renderer.show(fewer)
    renderer.show(fewer)

    assertEquals("the same die left the scene more than once", 1, stage.taken.size)
  }

  @Test
  fun `a die the stage would not take is not moved either`() {
    // `add` hands back "no entity" for a mesh with nothing in it, and nought
    // is not an entity anything may be done to.
    stage.refuse = GpuMesh.of(DieMesh.of(StandardDice.d6.shape).faces, scale = radiusOf(StandardDice.d6))
    renderer.begin(spec(), geometry, look)

    renderer.show(RenderFrame.still(List(3) { at(it, Vector3.Zero) }))

    assertFalse("the stage refused this die and it was moved anyway", stage.placed.containsKey(Stage.NOTHING))
  }

  @Test
  fun `a second roll does not land on top of the first`() {
    renderer.begin(spec(), geometry, look)
    val first = stage.added.size

    renderer.begin(spec(), geometry, look)

    assertEquals("one roll's dice were left in the scene for the next", first, stage.added.size)
    assertEquals(2, stage.clears)
  }

  @Test
  fun `the end of a roll takes it out of the scene`() {
    renderer.begin(spec(), geometry, look)

    renderer.end()

    assertTrue(stage.added.isEmpty())
    // And nothing is left pointing at entities that have gone.
    renderer.show(RenderFrame.still(List(3) { at(it, Vector3.Zero) }))
    assertTrue(stage.placed.isEmpty())
  }

  @Test
  fun `a throw with no dice in it still draws the tray`() {
    renderer.begin(spec().copy(dice = emptyList()), geometry, look)

    assertEquals(TRAY_PARTS, stage.added.size)
    renderer.settled(RenderFrame.still(emptyList()))
    assertEquals(
      "with nothing to look at, the tray is what is framed",
      TrayCamera.framingTheTray(geometry, ASPECT),
      stage.shots.last(),
    )
  }

  private fun at(
    index: Int,
    position: Vector3,
  ): BodyTransform =
    BodyTransform(
      index = index,
      position = position,
      orientation = Quaternion.about(Vector3(0.0, 0.0, 1.0), PI / 4),
    )

  private fun radiusOf(die: de.drehtuer.dinfinity.core.model.Die): Double = die.material.boundingRadiusMm

  @Test
  fun `a die an explosion adds is drawn among the dice that set it off`() {
    // They are not in the throw's world — their faces are read and they are
    // finished — but they are still on the table, and a tray that showed only
    // the new die would be a tray that had swept the roll away
    // (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
    // adds").
    val down = listOf(at(Vector3(-40.0, 0.0, 8.0)), at(Vector3(40.0, 0.0, 8.0)))

    renderer.begin(added(down), geometry, look)

    assertEquals("the dice already down were swept off the table", TRAY_PARTS + 3, stage.added.size)
    assertEquals("a settled die was not put back where it stopped", 2, stage.placed.size)
    assertEquals(-40.0f, stage.placed.getValue(TRAY_PARTS + 1)[TRANSLATION_X], FLOAT_TOLERANCE)
    assertEquals(40.0f, stage.placed.getValue(TRAY_PARTS + 2)[TRANSLATION_X], FLOAT_TOLERANCE)
  }

  @Test
  fun `a settled die is placed once and never moved again`() {
    // Nothing touches a die that has come to rest, and that includes the
    // picture of one (`.claude/CLAUDE.md`).
    val down = listOf(at(Vector3(-40.0, 0.0, 8.0)))
    renderer.begin(added(down), geometry, look)
    val where = stage.placed.getValue(TRAY_PARTS + 1)

    renderer.show(RenderFrame.still(listOf(BodyTransform(0, Vector3(90.0, 0.0, 8.0), Quaternion.Identity))))

    assertTrue("a settled die was drawn somewhere else", where === stage.placed.getValue(TRAY_PARTS + 1))
  }

  /** A throw of one die into a tray that already holds [down]. */
  private fun added(down: List<DieAtRest>): ThrowSpec =
    ThrowSpec(
      dice =
        listOf(
          DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = StandardDice.d6),
        ),
      geometry = geometry,
      table = look,
      seed = 2L,
      among = down,
    )

  private fun at(position: Vector3): DieAtRest =
    DieAtRest(StandardDice.d6, "builtin", RestingPlace(position, Quaternion.Identity))

  private fun spec(scale: Double = 1.0): ThrowSpec =
    ThrowSpec(
      dice =
        listOf(StandardDice.d20, StandardDice.d6, StandardDice.d4).mapIndexed { index, die ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
        },
      geometry = geometry,
      table = look,
      seed = 1L,
      dieScale = scale,
    )

  private companion object {
    /** The floor, the walls and the rim. */
    const val TRAY_PARTS = 3

    const val ASPECT = 320.0 / 640.0
    const val TOLERANCE = 1e-6
    const val FLOAT_TOLERANCE = 1e-4f

    /** Where the position sits in a column-major 4x4. */
    const val TRANSLATION_X = 12
  }
}
