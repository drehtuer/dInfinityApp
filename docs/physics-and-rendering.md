# Physics and rendering

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
  icosahedron, enneagonal trapezohedron (d18), triangular prism (d3), coin
  (d2), etc.
- Custom mesh dice are loaded as convex hulls of their vertices. If the hull
  differs from the source mesh by more than a tolerance, the set fails
  validation (it was not convex).
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
- **d2 (coin):** two faces, the edge is treated as cocked and re-nudged.
- **d100:** two d10s rolled together; one is flagged as the tens die in the
  `RollPlan`. 0 + 0 reads as 100.

## Avoiding stacked and cocked dice

On a real table dice practically never stay stacked on top of each other or
balanced on an edge. In a small simulated tray with many dice this *can*
happen, and it looks wrong and makes the result unreadable. Handled in
layers:

1. **Prevention.** Dice-on-dice friction is lower than dice-on-floor friction.
   Dice are scaled down so the table always has free floor area (capacity
   rule in `docs/tables.md`). Spawn positions are spread so dice do not fall
   straight onto each other.
2. **Detection.** Once a die is at rest, it is checked for:
   - **Cocked:** top-face dot product below `cos(15°)`.
   - **Stacked:** its lowest contact point is not with the floor, i.e. it is
     supported by another die.
   - **Leaning:** it has a persistent contact with a wall and is cocked.
3. **Nudge.** An offending die gets a small seeded random impulse and a bit of
   spin, its rest timer is reset, and the simulation continues. Any die it
   knocks into is also re-evaluated when it stops. Each nudge is recorded in
   the outcome (`nudges` counter) — the roll is still deterministic because
   the nudge impulse is derived from the roll seed.
4. **Give up gracefully.** After 5 nudges on the same die, the tray is briefly
   tilted (simulated, seeded) to slide everything. After that, if a die is
   still cocked, it is snapped to its nearest face and the anomaly is logged.
   This should be extraordinarily rare and shows up in the debug stats.

In normal mode the nudge is visible as a little twitch, which is fine — it
reads as "the die was wobbling". The whole check-and-nudge loop runs inside
the simulation, so power-saving mode gets identical behaviour.

## Rendering (normal mode)

- Filament scene: tray mesh, one renderable per die, one directional light
  plus an image-based light for reflections, soft shadows from the key light.
- Camera looks down at the tray at a slight angle; auto-frames all dice once
  they settle, then eases in on the results.
- Die meshes come from the shape catalogue or the validated custom mesh.
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
  the same fixed timestep, still with the same seed, nudge logic and settle
  rules. Typical roll finishes in well under 100 ms of wall time.
- The UI shows the formula, a short progress indicator, then the result and
  breakdown as plain text/graphics.
- Shake input still works: the shake session is recorded, then fed to the
  simulation as a batch.
- Haptics and sounds can stay on; they are then triggered from recorded
  impact events played back over ~1 s rather than in real time.
- Auto-enable option: switch to power-saving mode below a battery percentage
  or when the system's battery saver is on.

## Debug tooling

- Overlay toggle showing collision shapes, contact points, rest timers, nudge
  count.
- "Replay last roll" and "replay from seed" actions.
- Anomaly log (forced settles, snapped faces) exported with statistics.
