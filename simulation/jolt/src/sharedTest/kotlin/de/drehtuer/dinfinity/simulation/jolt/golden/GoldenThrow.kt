package de.drehtuer.dinfinity.simulation.jolt.golden

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.fixtures.GoldenCase
import de.drehtuer.dinfinity.fixtures.GoldenDigest
import de.drehtuer.dinfinity.fixtures.GoldenInput
import de.drehtuer.dinfinity.fixtures.GoldenShake
import de.drehtuer.dinfinity.fixtures.GoldenVector
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.CapacityVerdict
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.jolt.ShakeDriver
import de.drehtuer.dinfinity.simulation.jolt.SpawnLayout
import de.drehtuer.dinfinity.simulation.jolt.largestRadiusMm

/**
 * Turns a golden case into the throw it stands for, and into the one number
 * that says whether anything upstream of the engine has moved.
 *
 * It lives in a source set both test tiers compile, because the two tiers have
 * to agree about what the case *means* before they can disagree about what it
 * came to. A JVM copy and a device copy of this file would be two definitions
 * of the suite, and the first divergence between them would look exactly like
 * a physics bug.
 */
object GoldenThrow {
  /** Pinned, so a golden case is not a different roll on a different phone. */
  val geometry: TableGeometry = TableGeometry.referenceDevice()

  /** The bundled default table: its friction and restitution are in the roll. */
  val table: TableLook = StandardDice.tables.first()

  /** The standard set, and only it: a golden case resolves the same everywhere. */
  val catalog: DiceCatalog = DiceCatalog.of(listOf(StandardDice.set()))

  /**
   * How many steps of gravity go into the digest.
   *
   * Enough to cover the whole of [GoldenShake] and the still moments after it,
   * so a change to either the shake or the driver's idea of "the sample is
   * over" shows up.
   */
  const val GRAVITY_STEPS: Int = 128

  /** [case] as the throw the simulator is given. */
  fun specOf(case: GoldenCase): ThrowSpec {
    val planned = RollPlanner.plan(case.formula, catalog)
    check(planned is PlanResult.Planned) {
      "'${case.formula}' is a golden case and must plan: ${(planned as PlanResult.Failed).error}"
    }
    val verdict = TableCapacity.check(planned.plan, geometry)
    check(verdict is CapacityVerdict.Fits) {
      "'${case.formula}' is a golden case and must fit: ${(verdict as CapacityVerdict.Refused).reason}"
    }
    return ThrowSpec(
      dice = planned.plan.dice,
      geometry = geometry,
      table = table,
      seed = case.seed,
      dieScale = verdict.scale,
      shake = shakeOf(case.input),
    )
  }

  /**
   * Everything the engine is handed before it takes a step, as one number.
   *
   * The dice and their hulls, because the hull is what is actually collided;
   * where each die starts and how hard it was thrown, because that is the
   * whole of rung 1; and the gravity of every step, because that is the whole
   * of the shake. Nothing here is physics — which is why a JVM test can assert
   * it, and why asserting it on the device as well is what makes the JVM half
   * mean anything (`docs/architecture.md`, decision 40).
   */
  fun spawnDigest(spec: ThrowSpec): String {
    val digest = GoldenDigest()
    val layout = SpawnLayout(spec.geometry, largestRadiusMm(spec), spec.seed)

    digest.add("scale").add(spec.dieScale)
    spec.dice.forEachIndexed { index, instance ->
      digest
        .add("die")
        .add(instance.die.id)
        .add(instance.setId)
        .add(index)
      ShapeGeometry.hullOf(instance.die, spec.dieScale).forEach { digest.add(it) }
      with(layout.placementOf(index, spec.dice.size)) {
        digest.add(position)
        digest
          .add("rot")
          .add(rotation.w)
          .add(rotation.x)
          .add(rotation.y)
          .add(rotation.z)
        digest.add(linearVelocity)
        digest.add(angularVelocity)
      }
    }

    val driver = ShakeDriver(spec.shake)
    repeat(GRAVITY_STEPS) { step ->
      driver.advance(step)
      digest.add(driver.gravity)
    }
    return digest.hex
  }

  /** The recorded swing, or nothing at all for a throw from the hand. */
  private fun shakeOf(input: GoldenInput): List<ShakeSample> =
    when (input) {
      GoldenInput.Tap -> emptyList()
      GoldenInput.Shake ->
        GoldenShake.samples().map { sample ->
          ShakeSample(
            stepIndex = sample.stepIndex,
            accelerationMmPerSecond2 = sample.accelerationMmPerSecond2.asVector(),
            gravity = sample.gravity.asVector(),
          )
        }
    }

  private fun GoldenVector.asVector(): Vector3 = Vector3(x, y, z)

  private fun GoldenDigest.add(vector: Vector3): GoldenDigest = add(vector.x).add(vector.y).add(vector.z)
}
