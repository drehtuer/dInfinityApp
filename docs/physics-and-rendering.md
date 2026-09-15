# Physics and rendering

> **Design:** the tray, a settled roll and the power-saving result are
> options 1a and 1z of the [clickable design](../design/dInfinity.dc.html) ([design/](../design/)).
> The dice there are flat silhouettes standing in for the 3D render.

## Overview

Each roll is a rigid-body simulation of dice inside a tray. The number on a
die is read from whichever face normal points closest to "up" once the die has
come to rest. Rendering is a passive observer of the simulation.

## Coordinates

One right-handed system, shared by the tray, the solver and the renderer:
**`+z` is up**, the tray's long side runs along `+x` and its short side along
`+y`, and the origin is the middle of the floor. Distances are millimetres
everywhere above the bridge; the solver runs in centimetres on the far side of
it and converts in one place (decision 41).

Nothing is turned over on the way between the three. A renderer that used a
different up would have to flip every transform it was handed, and the first
thing to go wrong would be a die drawn resting on the face it did not land on.
It is also what "up" means everywhere else in this document: the face reading,
the reference orientation a shape's face order is numbered in, and the
direction a face's texture is drawn the right way up in
(`docs/dice-sets.md`).

## The table

The table (tray) is a fixed rectangular box whose floor is the phone's screen:
its aspect ratio and apparent size match the visible area, and its walls sit
at the screen edges like the rim of a dice box. See `docs/tables.md` for the
geometry, the capacity rule and how the *look* of the table can be swapped.

- The mesh never changes. Only textures, colours, material and sound profile
  are exchangeable.
- Wall height is generous and there is an invisible ceiling. The *collision*
  walls run all the way up to that ceiling rather than stopping at the rim the
  renderer draws, because a box open at the sides between the two is a box dice
  leave (`docs/tables.md`).
- **The table is horizontal, whatever the phone is doing.** Gravity in the tray
  is straight down and stays there; the hand moves the dice, the table does not
  tip under them. It used to follow the phone, and the cost was worse than the
  feature was worth — see "Shake input".
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
  of eight known solids. A set changes a die's size, material, face values and
  artwork, never its geometry — which is what makes the tuning below hold for
  every installed set.
- Mass is uniform density over the hull volume; inertia tensor from the hull.
  Standard sets use realistic sizes (a d6 of 16 mm) and density (~1.2 g/cm³,
  roughly acrylic).
- Restitution around 0.3, friction around 0.5. These are tunable per die in
  the set file within clamped ranges.
- Rounded edges: shapes with sharp corners (d4 especially) get a hull margin of
  3 % of the die's nominal size, so they tumble instead of catching on the
  floor. A share rather than a fixed millimetre, so a die shrunk by the
  capacity rule keeps the proportions it was tuned at.

## Timestep and determinism

- Fixed timestep of **1/120 s**, up to 4 sub-steps per rendered frame. Every
  threshold below is counted in *steps* rather than seconds — 30 steps to rest,
  1,440 to the cap — because a count of fixed steps is exact where a running
  total of doubles is not, and two devices that disagreed by one step about
  when a die stopped would disagree about the roll.
- The simulation is seeded per roll. The seed and every input impulse are
  recorded in the `RollResult` so a roll can be replayed exactly.
- **Every random number in a roll comes from `Seeds`, and the seed is stirred
  before it becomes a generator.** A seed handed straight to
  `kotlin.random.Random` becomes an xorwow state by way of sixty-four warm-up
  steps, and sixty-four is not enough to separate two seeds that differ only in
  their low bits: 200,000 d18 throws seeded `0, 1, 2, …` start in orientations
  spread *more* evenly than chance allows, χ² of 0.73 against 17 degrees of
  freedom. Too even is a correlation like any other, and a die is only fair
  because its starting turn is drawn evenly and independently of everything
  else about the throw. Rolls the app starts are seeded from `SecureRandom` and
  were never affected; an **exploding die was** — its extra throws used to be
  seeded one, two, three more than the throw that set them off. SplitMix64's
  finaliser fixes it in three lines: a bijection, so two seeds are still two
  streams, and pure arithmetic, so a roll still replays to itself on every
  device.
- The engine is configured in deterministic mode — Jolt built with
  `CROSS_PLATFORM_DETERMINISTIC=ON` (`docs/build-setup.md`) — stepped by a
  single-threaded job system, and no `System.nanoTime()` takes part in any
  simulation decision. Single-threaded because a roll is at most eighty small
  convex bodies, where the threads would cost more than they save, and because
  it removes a whole class of question about what "deterministic" depends on.
- The simulation runs in **centimetres and grams**, converted from the app's
  millimetres at the bridge and nowhere else. That is not cosmetic: at metre
  scale a die's inertia tensor falls under a hard-coded "near zero" test inside
  Jolt and is replaced by that of a sphere a metre across, which friction cannot
  slow (`docs/architecture.md`, decision 41).
- Everything that decides a throw *before* the engine sees it — the spawn
  layout's orientations and spins, the catalogue's hull vertices, the threshold
  a face is read against — is computed with `StrictMath` through
  `simulation/api`'s `Exact`. `Math.sin` may be an intrinsic and is allowed to
  be an ulp out; one ulp in a starting quaternion is a different face a hundred
  steps later (`docs/architecture.md`, decision 43).
- Golden tests: a fixed list of (seed, formula, input) triples with their
  recorded outcomes, in
  `test-fixtures/src/main/resources/fixtures/golden/cases.tsv`. They are
  asserted in two halves, split where the engine begins. The JVM half runs on
  every CI build and checks everything the engine is *handed* — which dice the
  formula resolved to, how far the capacity rule shrank them, every die's
  starting placement and hull, and the gravity of every step of the shake, as
  one digest. The device half runs on the emulator and the phone and checks
  what the engine *did* with it: the faces, the steps to rest, the corrections
  and the re-throws, exactly. Both halves assert the digest, which is what
  makes the CI half worth running: it is only evidence about a real roll while
  the device still agrees with it about what the roll was. Any diff is a bug —
  re-recording is deliberate and reviewed (`docs/build-setup.md`).

### The simulation clock

A roll is a loop over fixed steps, and the only question is who turns it. That
is the whole difference between a roll on screen and a roll in power-saving
mode; there is no other one.

- **A frame time never reaches the solver.** `FrameClock` accumulates the time
  a frame actually took, cuts it into whole 1/120 s steps and carries the
  remainder to the next frame, where it becomes the interpolation a renderer
  blends the last two states with. The world is advanced by a fixed step or it
  is not advanced.
- **A frame that ran long is not paid in full.** At most four steps are taken
  for one frame and the time behind the rest is dropped with them. Carrying it
  would mean the next frame owed more than this one did and the one after that
  more again — the spiral where a phone that fell behind once never catches up.
  Dropping it costs nothing but wall-clock time: the roll takes the same steps
  in the same order and comes to the same faces, it simply arrives there later.
  A number here is a smoothness problem, and Step 5.7 is where it stops being
  acceptable (`docs/TODO.md`).
- **A re-throw takes no simulated time.** Rung 3 picks a die up and puts it
  back at the spawn point between one step and the next, so there is nothing to
  interpolate across and the renderer is told so: the die is drawn at its new
  place, not sliding smoothly back through the air towards it. An invisible
  hand with an animation on it is still an invisible hand.
- **Watching is passive, and the type says so.** A roll in progress hands the
  renderer a frame and takes nothing back. It cannot be stepped, reached into
  or asked for another go from the far side of `Renderer`, so turning the
  renderer off cannot change what a roll comes to — which is what makes
  power-saving mode the same roll rather than a second implementation
  (`docs/architecture.md`, decision 48).

## Starting a roll

Two ways to start, and only two:

1. **The Roll button.** Dice are spawned in a cluster above the tray with a
   randomised (seeded) orientation, angular velocity and a modest downward
   plus lateral impulse. This is the "drop from the hand" throw.
2. **Shake.** See below. The dice are spawned when the shake begins and are
   driven by the phone's motion until the user stops shaking, then released.

In both cases the initial angular velocity is large enough that the outcome is
not predictable from the starting orientation. (A die dropped from 2 cm with
no spin *would* be predictable. We do not do that.)

**Tapping the tray does not roll.** It is the largest target on the screen and
the most tempting one, which is exactly why it is not spent here: the tray is
where the camera will be moved and where individual dice will be picked up and
re-thrown, and a surface that throws the whole formula the moment it is touched
has nowhere left to put either. A roll is also not something to start by
accident — it replaces a result somebody may still be reading. Two deliberate
gestures, one of them a button and the other a shake of the whole phone, are
enough.

## Shake input

- Sensors: linear acceleration (gravity removed) and gyroscope, at
  `SENSOR_DELAY_GAME`. Registered while the roll screen is resumed and let go
  when it is not — an accelerometer running behind a backgrounded app is a
  battery bill for nothing.
- **The display's rotation has to stay truthful, which is why the roll screen
  is not pinned to one.** `PhoneAxes` maps a sensor vector into the tray using
  `Display.getRotation()`, so a screen held at the rotation it opened at
  reports an upright phone while it is being shaken upside down. The map is
  right and is handed a lie, and the dice pool at the end away from the hand
  rather than the end towards it. The screen asks to keep its *shape* instead —
  a quarter turn still refused, because that rebuilds the table; a half turn
  allowed, because it does not (`docs/tables.md`).
- **Shaking a phone hard can trip Android's Theft Detection Lock.** It watches
  for the motion of a phone being snatched, and a good throw is not far off.
  Nothing in the app can suppress it and nothing should try: it is a security
  feature doing its job, and the player can turn it off in their own settings.
  Worth knowing about before it is reported as a crash — the app is not
  involved and carries on where it left off.
- **The dice are spawned when the shake begins**, and every moment after that
  reaches them while they are already in the air. What the player sees is dice
  answering their hand, not dice thrown once the hand has stopped.
- That works without a clock between the two because the samples name their
  own step. `ShakeRecorder` counts steps from the start of the shake at the
  simulation's own 120 Hz, and the frame clock never runs the simulation
  *faster* than real time — it drops steps when it falls behind and never
  gains any. So the step a sample names is always still ahead of the step the
  world is on: every sample is in place before it is needed, and replaying the
  record afterwards drives exactly the same steps. No gate, no waiting, and the
  live roll and its replay are the same roll.
- **The hand's force is held between readings.** `SENSOR_DELAY_GAME` is about
  50 Hz and the simulation runs at 120, so most steps have no reading of their
  own. A step with no reading keeps the last one rather than falling back to
  plain gravity: an arm does not stop between two moments of a shake, and
  letting the force go on every step without a sample drove the dice on two
  steps in five and let them coast through the rest. The hold lasts a tenth of
  a second, which is long against the gap between readings and short against a
  shake — so a shake that has actually ended stops driving and the dice come
  down.
- The samples are handed to the roll on the thread the roll lives on. A shake
  written into a world that is mid-step is a race with a physics engine on the
  other end of it.
- **A roll cannot end while the phone is still being shaken.** Dice that look
  still for a moment under a hand that is still going are not a roll that is
  over; they are a roll caught at the top of a swing. The settle rule is the
  dice's answer and this is the hand's, and both have to agree before a throw
  is read. A shake is not the signal to tumble the dice — it is what is
  throwing them, for as long as it lasts.
- A sample that arrives after the dice have stopped is dropped. Nothing touches
  a die that has come to rest, and a hand is not an exception. That is not in
  tension with the rule above: the dice only come to rest once the hand has
  stopped, so by then there is nothing left to drop.
- A shake session starts when acceleration magnitude stays above 3,500 mm/s²
  (about 0.35 g) for more than 80 ms, and ends after 400 ms below 1,500 mm/s².
  Two thresholds rather than one, with a gap between them: a single threshold
  would flicker on and off through the quiet moment at the top of every swing.
  A session that runs past 30 s is ended anyway — it has stopped being an
  input.
- During the session the phone's acceleration is applied **inverse**, added to
  gravity. The dice slam into the walls the same way they would in a cupped
  hand, because that is what a hand yanking a tray sideways is: in the frame
  the player is looking at, everything inside gets thrown the other way. It
  feels far more physical than applying random impulses to the dice, and it is
  the same thing a moving tray would do without the tray having to move.
- **The tray never moves.** It is the phone's screen, so in that frame it is
  nailed down — and it has to stay that way for a second reason: a tray carried
  at the speed a hand shakes crosses more than its own wall thickness in one
  simulation step, and continuous collision detection sweeps a fast *die*
  against the world, never a fast wall against a die. The wall then arrives
  already inside a die and the solver pushes that die out of whichever face is
  nearer, which half the time is the outside. It was built with a moving tray
  first and the dice escaped.
- **Every sensor vector is turned into the tray's frame before it is used.**
  Android reports in the device's own: `+x` across the screen to the right,
  `+y` up it, `+z` out of the glass. The tray's long side is the screen's long
  side and runs along `+x`, its short side along `+y` — so the two frames are a
  quarter turn apart before the phone is turned at all, and the roll screen
  holds its shape but not its rotation, so the map follows the display's.
  Used straight,
  a sideways shake loads the dice along the length of the tray and the dice
  move in a direction with nothing to do with the hand. `PhoneAxes` is the one
  place that map lives, and both the accelerometer and the gyroscope go through
  it.
- **The gyroscope no longer turns the world.** It did, and a tray that tips
  with the phone is a nice idea that does not survive being shaken:
  `GravityTracker` starts each shake at "straight down relative to the screen"
  and integrates rates with nothing to re-anchor them, so a vigorous shake
  could leave down pointing sideways in the tray — and it stayed there for the
  rest of the roll, because the last sample's direction is the one that sticks.
  A tray whose down points at a wall is a chute: the dice slide into it, pack
  against it and stop tumbling, which is what a hundred d6 heaped into one
  corner looked like on the Pixel 10a. Twenty dice shaken along the tray's own
  length end in a heap at every seed tried with the tilt and at none without
  it.
- The direction is still **recorded** with every sample. It costs nothing, it
  is what a replay would need if this is revisited, and the question of what a
  tilted phone should mean is deferred rather than answered
  (`docs/TODO.md`, Step 5.6).
- An acceleration above 40,000 mm/s² — about four gravities, harder than anyone
  shakes a fistful of dice — is clamped. Past that it is a sensor fault or a
  dropped phone, and no thickness of wall survives it.
- Sensor samples are quantised — acceleration to 1 mm/s², direction components
  to 1/4096 — and indexed by *simulation step* rather than by wall-clock
  moment. Both are what make a roll reproducible from its own record: a
  quantised value survives being written down, stored and read back, and a
  record indexed by step feeds the same steps in normal mode, where it is
  consumed as it arrives, and in power-saving mode, where the session is
  replayed as a batch afterwards.
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

The face normals come from `simulation/api`'s shape geometry, which computes
every catalogue solid from its closed form. The renderer's mesh and the
solver's hull are built from the same arithmetic, so all three agree about
which way a face points by construction rather than by inspection. The face
order — from the top of the reference orientation down, anticlockwise around
each ring — is the same order a set file's `faces` list and its texture atlas
use (`docs/dice-sets.md`).

Special cases:

- **d4:** a tetrahedron never has a face pointing up. The value is read from
  the vertex pointing up, following the common "top number" convention. In the
  set file a d4 declares `read = "vertex-up"` and the values are mapped to
  vertices rather than faces; the cocked check then uses the vertex direction
  instead of a face normal.
- **d2 (coin):** two faces; landing on the edge counts as cocked.
- **d100:** two d10s rolled together; one is flagged as the tens die in the
  `RollPlan`. 0 + 0 reads as 100.

## Are the dice fair

The app's central claim is that the physics *is* the roll: no generator decides
a face. That claim is worth nothing unless the physics turns out honest dice,
and the only way to know is to throw a great many and count.

`FairnessTest` in `simulation/jolt` is that count. It throws every catalogue
shape headlessly — no renderer, no frame clock, no screen — and checks the
histogram two ways, because a loaded die can fail either:

- **chi-squared at p = 0.001**, which catches a die that is skewed overall;
- **a worst-face bound of 1 %**, which catches one face that is wrong while the
  others cover for it.

Headless, but the same simulation a watched roll steps: the only difference is
who asks for the steps ("Power-saving mode"), so a fairness result here is a
fairness result for the app.

The roll count is an instrumentation argument, because the honest number and
the affordable number are not the same. The default is small enough to sit in
the ordinary device suite and still catch a die that is grossly loaded; the
claim above needs the hundred thousand and is run deliberately:

```sh
./gradlew :simulation:jolt:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=de.drehtuer.dinfinity.simulation.jolt.FairnessTest \
  -Pandroid.testInstrumentationRunnerArguments.rolls=100000
```

**The seeds are stirred, not counted.** A roll's seed becomes a
`kotlin.random.Random`, and two seeds differing only in their low bits do not
give two independent streams — throws seeded `0, 1, 2, …` start in orientations
spread *more* evenly than chance allows. Throws that are not independent break
the assumption chi-squared rests on, in both directions, so the harness puts
each roll number through SplitMix64 first. The roll number still decides the
seed, so a run is as repeatable as it was. That the seeds themselves need
stirring is a bug of its own, and has its own task (`docs/TODO.md`, Step 5.2).

### What it measured

100,000 rolls of each shape on the Pixel 10a, 800,000 in about fifteen minutes:

| shape | χ² | limit | worst face off |
| --- | --- | --- | --- |
| coin | 0.80 | 10.83 | 0.141 % |
| tetrahedron | 0.34 | 16.27 | 0.062 % |
| cube | 1.99 | 20.52 | 0.136 % |
| octahedron | 12.83 | 24.32 | 0.286 % |
| pentagonal trapezohedron (d10) | 5.29 | 27.88 | 0.135 % |
| dodecahedron | 12.73 | 31.26 | 0.239 % |
| **enneagonal trapezohedron (d18)** | **197.34** | **40.79** | 0.455 % |
| icosahedron | 21.35 | 43.82 | 0.131 % |

The seven that pass sum to χ² 55.33 against 55 degrees of freedom, which is as
close to "exactly as fair as chance predicts" as a number gets. That is also
what makes the eighth worth believing.

### The d18 is not fair, and the shape is not why

197 against a limit of 41 is not an unlucky run. The bias is reproducible: the
same faces are heavy across three independent seed schemes, the deviation
patterns of separate runs correlate at +0.6 to +0.9 where independent samples of
a fair die would sit near ±0.24. Some faces come up 6 % more often than their
share; no single face passes 1 %, which is why the worst-face bound does not
catch it and the chi-squared test does. Both are in the harness for this reason.

**An enneagonal trapezohedron is isohedral** — its symmetry group carries any
face onto any other — and the dice start in an orientation drawn evenly over all
of them (`SpawnLayout`, Shoemake's method). Turning the starting orientation by
one of the solid's own symmetries gives a physically identical throw with the
face labels permuted, so a fair sample of orientations has to produce a fair
sample of faces whatever happens in between. A bias means the die the engine
collides is not the solid the arithmetic describes. What has been ruled out, each
measured on the phone rather than argued:

- **not the seeds** — three independent schemes favour the same faces;
- **not Jolt's convex radius** — rebuilt with hull shrinking off entirely, the
  same faces stayed heavy;
- **not a dropped corner** — every corner of the d18 protrudes 35–70× Jolt's
  hull tolerance, so none is being merged away;
- **not a loaded die** — a dipole fit explains 12 % of the variance, so the
  centre of mass is not off-centre.

What the pattern does say: the deviations pair up antipodally. A face and the
face opposite it move together, and they are the two readings of the same
landing, so it is certain *axes* that finish vertical too often rather than
certain faces that are sticky. Nine axes, spread from −4.4 % to +6.5 %.

The d10 is the same family of solid and is fair (χ² 5.29 against 27.88), so
whatever this is, it bites when the kites get narrow. Finding it is Step 5.2.

**The body is not it, and neither is the engine.** Two things were suspected
first and both have been measured rather than argued:

- `DieBodyTest` asks the solver what it built. Every catalogue shape comes back
  with exactly the faces it should have — the d18 with eighteen — every face at
  the same inradius to six decimal places, the centre of mass on the origin,
  and an inertia tensor with the solid's own symmetry: isotropic for the
  Platonic solids, two matching moments and one apart for the trapezohedra and
  the coin. The die the engine collides *is* the die the arithmetic describes.
- The engine is even-handed about it. Turning the d18 by one of its own
  symmetries — the same solid, its faces relabelled — and throwing the same
  twenty thousand seeds gives a histogram that is the **exact permutation** of
  the untuned one, χ² 35.83 either way. Reversing the order the hull's corners
  are handed over moves nothing but the last digits.

So the solid is right and the solver treats it evenly. The throw was suspected
next — a starting turn and the force it is thrown with come out of one stream,
one after the other, and the symmetry argument assumes they are independent —
and that is now measured rather than assumed:

- the streams are stirred (`Seeds`, above), and the harness re-run on top of
  that still gives the d18 **χ² 135.86**, against 197.34 before. The other
  seven still pass, summing to 57.63 against 55 degrees of freedom;
- the starting turn is independent of the force: over 400,000 throws, which
  face is up at the moment of release against the sign of each other draw —
  the lateral throw, the drop speed, the spin, the height — gives χ² between
  12 and 27 against 17 degrees of freedom, which is what independence looks
  like;
- and the turns themselves are evenly spread: two million of them land on the
  d18's eighteen faces with χ² 5.9 to 23.9 against 17, whether they come from
  a fresh xorwow stream per roll, one long xorwow stream, or SplitMix64.

Three runs of a hundred thousand — two ABIs, three seed schemes — put the same
faces on top: their deviation patterns correlate at 0.86 to 0.90, where
independent samples of a fair die would sit near ±0.24. It is one fixed bias,
not three unlucky samples.

### Where the d18's bias actually sits

The sharpest clue, and the one to start from next. A d18's eighteen faces fall
into **two orbits of nine** under the solid's own ninefold turn, and the bias is
almost entirely *within* those orbits rather than between them: χ² 94.2 and
40.5 against 8 degrees of freedom each, and only 1.34 against 1 between the two
rings.

That is the combination symmetry forbids. A ninefold turn maps the solid onto
itself, so it maps the throw onto the same throw with its faces relabelled; with
starting turns drawn evenly, the nine faces of an orbit have to come up equally
often. They do not, by a margin of one in 10¹⁶.

Every premise of that argument has now been measured and holds — to the
precision the measurements have. What is left is precision itself: the hull the
solver collides is exactly symmetric in double arithmetic, and reaches the
engine as **float32**, where it is symmetric to about one part in 10⁷. The d18
is the shape that would notice. Its adjacent faces are 28.4° apart where a
d10's are 51.8°, so its resting basins are the narrowest in the catalogue and it
spends its last moments rolling along a nearly equatorial belt of nine of them.
The d10, the same family of solid with wider kites, is fair (χ² 8.35 against
27.88). So the next measurement is whether the same throws come out fair on a
hull the engine holds in double precision.

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
   re-throw is recorded in the outcome (`rethrows` counter). Re-throws come out
   of the same twelve-second budget as the rest of the roll — the cap is a cap
   on the throw, not on each attempt at it — and one die may be thrown again at
   most three times. A die that has come up cocked three times running is not
   unlucky, it is a physics bug, and letting it loop would spend the whole
   budget on one die while the rest of the table waits.

4. **Never.** No impulse on a resting die. No tray tilt to slide a settled
   pile. No snapping a die to its nearest face — that fabricates a result
   nobody rolled.

The 12-second hard cap above is a safety valve for a simulation that has gone
wrong, not part of this ladder. When it fires, every die still moving is
force-settled. A die that is still cocked at that point has had its three
re-throws and there is no budget left for a fourth: it reports the face that
came nearest and is counted in `forcedSettles`, which makes the outcome
`clean = false`. That is the one place in the app where a number is read off a
die that was not properly resting, it is recorded rather than hidden, and Step 5
asserts it never happens.

The whole loop runs inside the simulation, so power-saving mode behaves
identically — including the re-throws, which simply do not get drawn.

Targets, verified on a device (`docs/TODO.md`, Step 5): zero dice at rest
supported by another die, fewer than 0.5 % of dice needing any correction at
all, and **zero** corrections applied after rest.

## Rendering (normal mode)

- Filament scene: tray mesh, one renderable per die, a key directional light
  casting soft shadows, a dimmer fill from the other side, and a flat ambient.
- **The ambient is not decoration.** Two directional lights and nothing else
  leave every surface facing away from both at exactly black, and the surfaces
  facing away from both are the inner walls: the tray showed its lit rim, a
  shadow across the floor, and nothing in between casting it. It is a single
  spherical-harmonic band — the constant term, the same irradiance from every
  direction — rather than a sky-above/ground-below gradient, which would need
  three bands and this code being right about which axis Filament's harmonics
  run along. That is invisible when wrong, and a tray is lit by a room.
- The tray mesh is a function of the tray's geometry and nothing else — no
  package supplies one (`docs/tables.md`). Only the **inside** is modelled:
  the floor, the inner walls up to the 60 mm rim, and a 6 mm band across the
  top of it. The camera looks down into the tray, so the outside of the walls
  is never in shot, and the near wall's inner face points away and is culled —
  which is what lets the player see over it rather than at the back of it. The
  rounded corners are drawn as six segments to the quarter, which is under a
  pixel of a 12 mm arc at any size this is drawn at.
- The renderer draws through a `Stage` interface. `FilamentStage` is the one
  file in the module that talks to Filament, and everything that *decides*
  what a roll looks like sits on the near side of it and is tested on a JVM
  (`docs/architecture.md`, decision 47).
- **The camera frames the whole tray, and only the player moves it.** It never
  closes in on its own, not even when the dice settle: a camera on the dice
  takes the table away, and a player cannot then tell four dice from two.
  Looking closer is theirs to do — **pinch to zoom, two fingers to pan** — and
  what that produces is a `TrayView`, which cannot leave the table. At the
  whole tray there is nowhere to pan to; every step closer earns exactly as
  much room to move as it took away, so a fling cannot end up looking at the
  void beside the tray. A new throw goes back to the whole table, because the
  dice can land anywhere in it. A rotation does not: where the player was
  looking is part of the picture that is rebuilt. One finger is left alone, for
  picking a die up and for the tap that deliberately does not roll.
- **The table is drawn before anything is thrown onto it, and after.** A tray
  is a table, not a roll: the screen says *there is a table* as soon as it
  opens, and the floor, the walls and the rim are built and drawn with nothing
  standing on them. Putting a result away takes the dice off it and leaves the
  table. Only giving the tray up entirely takes the table away too.
- **A picture that is not moving still has to land.** A roll produces a frame
  sixty times a second and a skipped one is covered by the next; an empty
  table and a roll that has come to rest produce none at all, and there is no
  next frame to cover for a skip. So a still picture is *owed* a frame — when
  the table is named, when a surface arrives, and when the last die stops —
  and is asked for again until Filament actually draws one. It is one frame
  each time, not a loop: nothing is moving, so nothing more is worth drawing.
- **The surface comes and goes; the roll does not.** Filament fixes its swap
  chain and viewport when a stage is made, so a resize, a rotation or the app
  coming back from the background is a *new* stage. A roll being drawn on the
  old one is still going, and restarting it to get a picture back would be a
  different roll wearing the same seed's name — with the player watching the
  dice they were already watching begin again. So the picture is rebuilt
  instead: `TrayRenderer` remembers the throw, the tray and the last frame, and
  replays them onto the new stage. **The roll is not paused with it either.**
  The frame callback is what steps the simulation, so a roll that stops being
  asked for frames is a roll that stops — and one stopped half way is never
  read, never reported and never over, leaving the screen on "Rolling…" for
  good. A roll therefore asks for frames whether or not there is anywhere to
  draw; only the still pictures need a surface before they are worth one.
  **The engine is not rebuilt with it, and not with a visit either.** The
  material is compiled on the device for the driver that is actually there, and
  that costs long enough that doing it again for every rotation was itself the
  black tray: what a surface owns is its swap chain and its viewport, and
  `FilamentEngine` keeps the rest across all of them. The same line is drawn
  once more around a *visit* to the screen — leaving for the menu and coming
  back rebuilt the engine and showed the same black — so the engine and the
  thread it lives on belong to the application (`RollThread`,
  `docs/architecture.md`, decision 50). The world and the scene still go: a
  throw the player walked away from never landed. With no stage at all it
  draws nothing, which
  is the right thing to be while the app is in the background — the roll goes
  on and the dice are where they should be the moment there is somewhere to put
  them.
- The thread that steps the roll is the thread that draws it, off its own
  `Choreographer` (`docs/architecture.md`, decision 49). `TrayDriver` is that
  thread and the surface it draws to; `TrayLoop` is what it does each frame,
  and is tested on a JVM.
- `FilamentStage` and `TrayDriver` are the two files excluded from the coverage
  figure — a GPU context and a thread. Neither is excluded from static
  analysis (`.claude/CLAUDE.md`).
- The engine is created, the material compiled and the scene built in that one
  file.
  Everything it is *told* (where the camera stands, what shape a die is, how
  its mesh packs, which numbers its material takes, where it is between two
  simulation steps) is decided elsewhere and tested on a JVM
  (`docs/architecture.md`, decision 40). Filament hands out native handles
  rather than objects a garbage collector knows about, so everything made
  there is destroyed in reverse; a roll's own entities go at the end of the
  roll, and the engine and the compiled material stay.
- **One material** draws every surface of a roll: a lit, opaque, physically
  based one with a base colour, a roughness and a metalness, optionally
  multiplied by an atlas. Dice are dice and a tray is a tray. Everything a
  package may vary is a number going into it rather than a line of it changing
  (`docs/tables.md`, "Table looks"; `docs/TODO.md`, After v1).
- **Colours are converted out of sRGB before the renderer sees them.** A
  package writes `#1f5e3a`, which is the space a screen shows and a person
  picks colours in; light adds up in linear space. Handing a renderer sRGB
  makes every midtone too bright — mid grey is 21 % of the light, not 50 % —
  in a way nobody can point at and everybody sees. Alpha is coverage rather
  than light and is left alone.
- Every surface carries a **tangent frame**, not a bare normal: which way it
  faces and which way its texture runs, as one quaternion, because that is what
  a vertex buffer holds and what a lit surface needs. It comes from the same
  arithmetic that laid the texture out rather than being guessed back from the
  mesh afterwards. Corners are not shared between surfaces — two faces of a die
  meet at the same point but disagree about which way they face and where they
  sit in the texture — which is what makes a die read as a solid with edges.
- Floor and wall textures repeat as `floor_tiling` and `wall_tiling` ask. On
  the walls the texture walks continuously around the tray — a stretch running
  along the long side repeats as often as the look asks for that side, one
  along the short side as often as it asks for that, and a corner takes the
  rate of whichever it is nearer — so a change of rate stretches the pattern
  rather than cutting it.
- Camera looks down at the tray at a slight angle — 22° off straight down,
  40° field of view, standing off the near end of the tray. Straight down is a
  diagram, and the point of rolling real dice is watching them tumble. While a
  roll is running it frames the whole tray, because a die can be anywhere in
  it; once the dice settle it frames *them* — each as the box around its
  bounding sphere, so a die at the edge of the group is wholly in shot rather
  than centred and clipped — and eases in with a smoothstep, because a camera
  that starts and stops dead reads as a glitch rather than as attention.
- The distance is solved, not guessed: each framed corner names the nearest the
  camera may stand for it to be inside the frustum, and the camera takes the
  furthest of those. That is what makes "the dice are in shot" a test rather
  than a judgement.
- Die meshes come from the shape catalogue — the same closed forms the solver
  collides, grouped onto the same face directions the reader reads, so face *i*
  of the picture is face *i* of the roll by construction
  (`docs/architecture.md`, decision 45). Face textures are applied via a
  per-face UV atlas (see `docs/dice-sets.md`); dice without textures render
  numbers with a built-in SDF font on a plain PBR material with the set's
  colour — and a d4 draws three of them per triangle, one at each corner,
  because its values belong to corners rather than to faces
  (`docs/dice-sets.md`, "The d4"). A coin's rim belongs to neither face and
  carries no cell: it is drawn in the die's own colour.
- Transforms are interpolated between the last two simulation states based on
  render time, so 120 Hz physics looks smooth at any display refresh rate. A
  renderer is handed both states and how far between them the moment falls,
  and blends them itself: positions in a straight line, turns spherically and
  the short way round. The arithmetic is on `RenderFrame` rather than in each
  renderer, so two of them cannot disagree about where the same die was.
- Results are overlaid as labels near each die once settled; tap a die to
  highlight its contribution in the breakdown.

Target: 60 fps with 20 dice on the Pixel 10a with headroom; the capacity rule
caps a roll at what the table can hold, which on a phone-sized table is in
the region of 60–80 small dice. Beyond ~40 dice the renderer drops shadows.

## Power-saving mode

- No Filament engine is created at all; the `headless` renderer is used. The
  screen goes further and puts **no surface on the screen**, rather than a
  surface nothing draws to: a surface is a buffer the compositor keeps, and
  what this mode claims is that none of it exists. `PowerSavingTray` is the
  other implementation of `Tray`, and there is no Filament type in it.
- The simulation runs on a worker thread as fast as possible, still at
  the same fixed timestep, still with the same seed, correction logic and
  settle rules. Typical roll finishes in well under 100 ms of wall time.
- The mode is read **once, when the roll screen opens**, and not watched. A
  renderer appearing or vanishing under a roll in progress is not a setting
  taking effect, it is a bug; turning it on takes effect the next time the
  screen is opened.
- It is the *same* roll, not an equivalent one: the same loop over the same
  world, with nobody calling the clock. The difference between the two modes
  is one call — a frame callback asking for the time since the last frame, or
  a worker thread asking for the lot — and no frames are shown, because there
  is nobody to show them to (see "The simulation clock").
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
