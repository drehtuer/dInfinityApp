# Physics and rendering

> **Design:** the tray, a settled roll and the power-saving result are
> options 1a and 1z of the [clickable design](../design/dInfinity.dc.html) ([design/](../design/)).
> The dice there are flat silhouettes standing in for the 3D render.

## Overview

Each roll is a rigid-body simulation of dice inside a tray. The number on a
die is read from whichever face normal points closest to "up" once the die has
come to rest. Rendering is a passive observer of the simulation.

## The table

The table (tray) is a fixed rectangular box whose floor is the phone's screen:
its aspect ratio and apparent size match the visible area, and its walls sit
at the screen edges like the rim of a dice box. See `docs/tables.md` for the
geometry, the capacity rule and how the *look* of the table can be swapped.

- The mesh never changes. Only textures, colours, material and sound profile
  are exchangeable.
- Wall height is generous and there is an invisible ceiling; dice cannot leave
  the table no matter how hard the phone is shaken.
- The table orientation follows device gravity: tilt the phone and the dice
  slide. Hold it flat and they settle.
- Floor and walls have a slightly higher friction than the dice-on-dice
  contact so the pile spreads instead of stacking.
- Because the table cannot grow, **dice shrink** when there are many of them
  (`dieScale` in the `ThrowSpec`), down to a minimum. Below that minimum the
  roll is refused before any body is created. Smaller dice that roll honestly
  beat big dice that jam.

## Dice bodies

Every die is a **convex** rigid body:

- Built-in shapes come from the shape catalogue (see `docs/dice-sets.md`):
  tetrahedron, cube, octahedron, pentagonal trapezohedron (d10), dodecahedron,
  icosahedron, enneagonal trapezohedron (d18), coin (d2), etc.
- The catalogue is closed in v1 (`docs/dice-sets.md`), so every body is one
  of nine known solids. A set changes a die's size, material, face values and
  artwork, never its geometry — which is what makes the tuning below hold for
  every installed set.
- Mass is uniform density over the hull volume; inertia tensor from the hull.
  Standard sets use realistic sizes (a d6 of 16 mm) and density (~1.2 g/cm³,
  roughly acrylic).
- Restitution around 0.3, friction around 0.5. These are tunable per die in
  the set file within clamped ranges.
- Rounded edges: shapes with sharp corners (d4 especially) get a small hull
  margin so they tumble instead of catching on the floor.

## Timestep and determinism

- Fixed timestep of **1/120 s**, up to 4 sub-steps per rendered frame.
- The simulation is seeded per roll. The seed and every input impulse are
  recorded in the `RollResult` so a roll can be replayed exactly.
- The engine is configured in deterministic mode (single-threaded solver or
  Jolt's deterministic multithreading), no `System.nanoTime()` in any
  simulation decision.
- Golden tests: a fixed list of (seed, formula, impulse sequence) tuples with
  their expected outcomes, run on every CI build and on multiple ABIs.
  Any diff is a bug.

## Starting a roll

Two ways to start:

1. **Tap / button.** Dice are spawned in a cluster above the tray with a
   randomised (seeded) orientation, angular velocity and a modest downward
   plus lateral impulse. This is the "drop from the hand" throw.
2. **Shake.** See below. The dice are spawned when the shake begins and are
   driven by the phone's motion until the user stops shaking, then released.

In both cases the initial angular velocity is large enough that the outcome is
not predictable from the starting orientation. (A die dropped from 2 cm with
no spin *would* be predictable. We do not do that.)

## Shake input

- Sensors: linear acceleration (gravity removed) and gyroscope, at
  `SENSOR_DELAY_GAME`.
- A shake session starts when acceleration magnitude exceeds a threshold for
  more than ~80 ms, and ends after ~400 ms below the threshold.
- During the session, the tray itself is moved: the phone's acceleration is
  applied as an inverse acceleration to the tray (kinematic body), so the dice
  slam into the walls the same way they would in a cupped hand. This feels
  much more physical than applying random impulses to the dice.
- Phone rotation from the gyroscope rotates the gravity vector in the
  simulation.
- Sensor samples are quantised and recorded with the roll so the roll is still
  reproducible.
- Optional feedback: haptic ticks on wall/die impacts above an impulse
  threshold (rate-limited), and impact sounds with pitch/volume scaled by
  impulse and die size. Both default on, both individually switchable.

## Settling and reading the result

A die is **at rest** when both its linear speed is below 1 cm/s and angular
speed below 0.05 rad/s for 250 ms of simulated time.

The roll is **finished** when every die is at rest, or when a hard cap of 12
simulated seconds is hit (then all still-moving dice are force-settled by
zeroing velocity, which is logged as an anomaly).

Reading a face: for each face of the die, take the dot product of its outward
normal (in world space) with the up vector. The face with the largest dot
product is the result **if** that dot product is at least `cos(15°)`.
Otherwise the die is *cocked* — see below.

Special cases:

- **d4:** a tetrahedron never has a face pointing up. The value is read from
  the vertex pointing up, following the common "top number" convention. In the
  set file a d4 declares `read = "vertex-up"` and the values are mapped to
  vertices rather than faces; the cocked check then uses the vertex direction
  instead of a face normal.
- **d2 (coin):** two faces; landing on the edge counts as cocked.
- **d100:** two d10s rolled together; one is flagged as the tens die in the
  `RollPlan`. 0 + 0 reads as 100.

## Avoiding stacked and cocked dice

On a real table dice practically never stay stacked on top of each other or
balanced on an edge. In a small simulated tray with many dice this *can*
happen, it looks wrong, and it makes the result unreadable.

There are two ways to get this wrong, and the second is worse than the first:

- A die left resting on top of another, or balanced on an edge. Nobody
  believes it, and there is no face to read.
- **A die shoved by an invisible hand after everything has stopped.** That
  destroys the whole point of the app: if the player can see the app move a
  die, the result was not rolled, it was arranged. A visible twitch on a
  settled die is a bug, not a fix.

So the rule is: **nothing touches a die that has come to rest.** Everything
below happens either before the dice are thrown or while they are still
moving, except the last resort, which is an honest re-throw the player can
see.

1. **Prevention — where the work goes.** Dice-on-dice friction is lower than
   dice-on-floor friction. Dice are scaled down so the table always keeps
   free floor area (capacity rule in `docs/tables.md`). Spawn positions are
   spread and staggered in height so dice do not fall onto each other. The
   throw carries enough energy that a die landing on another slides off it
   while it still has speed. Tuning these until stacking is *rare* is the
   real fix; the steps below only catch what slips through.

2. **Early detection, while the die is still moving.** A die is watched from
   the moment its speed drops below a threshold but before it is at rest. If
   in that window it is supported by another die, leaning on a wall, or
   heading for a cocked orientation, a small seeded bias is added to the
   motion it already has — of the order of the energy still in the die, so it
   reads as the die finishing its tumble rather than as a kick. The bias is
   derived from the roll seed, so the roll stays deterministic and
   reproducible.

3. **Last resort: re-throw that die.** If a die does reach full rest cocked or
   stacked, it is **not** poked, tilted, or snapped to a face. It is picked up
   and thrown again — one die, from a low height, visibly, while the others
   stay where they are. That is exactly what a player does with a cocked die,
   it is fair (the re-throw is uniform over the faces), and it is honest:
   the player sees a die being re-rolled instead of a die being moved. Each
   re-throw is recorded in the outcome (`rethrows` counter).

4. **Never.** No impulse on a resting die. No tray tilt to slide a settled
   pile. No snapping a die to its nearest face — that fabricates a result
   nobody rolled.

The 12-second hard cap above is a safety valve for a simulation that has gone
wrong, not part of this ladder; any die still cocked when it fires is
re-thrown and the anomaly is logged.

The whole loop runs inside the simulation, so power-saving mode behaves
identically — including the re-throws, which simply do not get drawn.

Targets, verified on a device (`docs/TODO.md`, Step 5): zero dice at rest
supported by another die, fewer than 0.5 % of dice needing any correction at
all, and **zero** corrections applied after rest.

## Rendering (normal mode)

- Filament scene: tray mesh, one renderable per die, one directional light
  plus an image-based light for reflections, soft shadows from the key light.
- Camera looks down at the tray at a slight angle; auto-frames all dice once
  they settle, then eases in on the results.
- Die meshes come from the shape catalogue.
  Face textures are applied via a per-face UV atlas (see `docs/dice-sets.md`);
  dice without textures render numbers with a built-in SDF font on a plain
  PBR material with the set's colour.
- Transforms are interpolated between the last two simulation states based on
  render time, so 120 Hz physics looks smooth at any display refresh rate.
- Results are overlaid as labels near each die once settled; tap a die to
  highlight its contribution in the breakdown.

Target: 60 fps with 20 dice on the Pixel 10a with headroom; the capacity rule
caps a roll at what the table can hold, which on a phone-sized table is in
the region of 60–80 small dice. Beyond ~40 dice the renderer drops shadows.

## Power-saving mode

- No Filament engine is created at all; the `headless` renderer is used.
- The simulation runs on the simulation thread as fast as possible, still at
  the same fixed timestep, still with the same seed, correction logic and
  settle rules. Typical roll finishes in well under 100 ms of wall time.
- The UI shows the formula, a short progress indicator, then the result and
  breakdown as plain text/graphics.
- Shake input still works: the shake session is recorded, then fed to the
  simulation as a batch.
- Haptics and sounds can stay on; they are then triggered from recorded
  impact events played back over ~1 s rather than in real time.
- Power-saving is a setting the user turns on or off. It is never switched
  automatically — not on a low battery, not by the system's battery saver.
  A roll that silently stops being rendered because the battery dipped is a
  surprise, and the mode is one tap away in Settings.

## Debug tooling

- Overlay toggle showing collision shapes, contact points, rest timers,
  correction and re-throw counts.
- "Replay last roll" and "replay from seed" actions. These live behind the
  developer toggle only: the app's history has no replay and never shows a
  seed (`docs/statistics.md`).
- Anomaly log (forced settles, post-rest corrections — which should never
  occur) exported with statistics.
