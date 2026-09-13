# TODO

The implementation plan. Steps run in order; within a step the boxes are
roughly ordered too. **Finished items are deleted, not ticked** — git history
and `docs/STATUS.md` are the record, and a step that is wholly done is
removed. Keep the file a plan, not a diary.

Everything here builds the specification in `README.md`, `docs/` and the
prototype in [design/](../design/). Where a step says *device*, it cannot run
on CI (see `.claude/CLAUDE.md`).

## Step 2 — CI

Everything a machine can check, on every PR. Emulator and device suites are
Step 5 and stay off CI.

- [ ] Re-enable CodeQL's `java-kotlin` analysis once the bundle supports Kotlin 2.4.20 — the matrix entry is commented out in `.github/workflows/codeql.yml` with the build steps kept ready
- [ ] *Optional:* add a `DEPENDABOT_METADATA_TOKEN` Dependabot secret so the metadata commit starts the checks by itself. Without it the automation still works, and the pull request shows an *Approve workflows to run* banner to press (`docs/build-setup.md`)
- [ ] Drop `VerifyDeviceTestResultsTask` and the `ignoreFailures` on `connectedDebugAndroidTest` once AGP stops failing runs on devices whose adb serial contains a colon (`docs/build-setup.md`)

**Done.** A pull request shows a green check per concern, Sonar decorates it
with coverage, and `v0.0.1` produced a signed `dInfinityApp-0.0.1.apk`. The
three boxes above are not blocking anything: two wait on other projects and one
is optional.

## Step 3 — Foundations

The shared layer every screen sits on. Built bottom-up, each piece tested to
completion before the screens start, because a bug here is a bug in every
screen.

- [ ] Atlases: decode a die's texture where its package is installed and hand it to the renderer. The seam is the `atlases` argument of `FilamentDiceRenderer`; until something fills it, dice are drawn in their own colours. Belongs with 4.4, and brings the two texture checks below with it
- [ ] Numbers for dice with no texture, drawn with the built-in SDF font (`docs/physics-and-rendering.md`). A d4 needs three per triangle, one at each corner, because its values belong to corners — the same rule the face designer follows (`docs/dice-sets.md`, "The d4")
- [ ] *Device:* that a roll driven by a recorded shake replays to itself on hardware (`input/shake`). The thresholds half of this is answered: shaking rolls and ordinary handling does not, confirmed on the Pixel 10a. What is not yet shown is the replay, and it cannot be until a throw's record carries its shake (4.1)
- [ ] The tables the rest of the app needs, each arriving with the screen that needs it and each as a *migration* on the version-1 database: saved rolls and groups (4.3), sessions (4.9) and the installed-set registry (4.4)
- [ ] The two texture checks that need a decoder, which `dicesets/format` cannot do from bytes alone: a file that passes the header check but will not actually decode, and an atlas with empty cells. Both belong wherever textures are first decoded (`docs/dice-sets.md`, "Validation")

**Done when** a formula can be parsed, planned, simulated headless and scored
from a unit test, with no UI in the picture, and Sonar reports ≥ 80 % on these
modules.

## Step 4 — Screens

One section per screen. Each is a vertical slice: state, UI, tests, and the
device check it needs. The design option ids (`1a`, `9c`, …) are the labels on
the canvas — open [design/](../design/) beside the code.

Every screen follows the same four steps, so they are written out once here
rather than repeated below:

1. **State** — a `ViewModel` over the Step 3 foundations. No logic that
   belongs in `core/` leaks into the screen.
2. **UI** — Compose, Modernist tokens, matching the design option. Light and
   dark, phone widths from 360 dp up.
3. **Tests** — unit tests for the state machine; Robolectric/Compose tests for
   rendering, interaction, empty and error states; accessibility (labels,
   touch targets, TalkBack order).
4. **Docs** — update the matching document in the same PR if behaviour,
   limits or defaults move.

### 4.1 Roll screen — `feature/roll`

Home. Design `1a`–`1j`, `2a`, `3a`–`3c`, `4a`, `4b`, `6d`, `6f`, `9a`, `9c`,
`1z`. Spec: `docs/dice-notation.md`, `docs/tables.md`,
`docs/physics-and-rendering.md`.

The screen rolls. A formula is typed, validated on every keystroke, refused if
the table cannot hold it, thrown from the Roll button or a shake, simulated and drawn on
the Pixel 10a, and its total read off the faces. What is below is what it does
not have yet.

- [ ] Judge the dice's size on a phone. A die is now `size_mm` across at its widest and the bundled set says 16 mm, which is realistic but small on a screen — and the numbers still have to be readable at arm's length once they are drawn (`docs/TODO.md`, Step 5.6). Raising the bundled set's `size_mm` is a one-line change; whether it wants raising is a question for a person
- [ ] Revisit the capacity constants now that they bite much later. 30 % of the floor and a 40 % minimum scale no longer refuse anything the engine would take: it would take about 240 dice to reach the floor and the engine stops at 100 (`docs/tables.md`). Step 5.3 is where those numbers meet a device
- [ ] Numbers on the faces. Dice are blank cream solids on the phone right now, which is the SDF item in Step 3 above; until it lands, the tray shows a roll that cannot be read without the total
- [ ] A shake-driven throw's `ThrowSpec` carries an empty `shake`: the dice are spawned the moment the shake is confirmed, and the samples arrive afterwards. The roll is driven by them and is reproducible from them, but the *record* of the throw does not yet hold them — which is what a replay and a bug report would need (`docs/physics-and-rendering.md`, "Shake input"). Attach the recorded session to the result when history arrives (4.8)
- [ ] Draw the dice an explosion or a reroll adds. They are simulated for real, one throw each, but into a tray nobody is looking at; they belong in the tray on screen, landing among the dice that set them off (`docs/dice-notation.md`)
- [ ] **Pinch to zoom, two fingers to pan.** The camera frames the whole tray and never moves off it, because a camera that closes in on the dice takes the table away and a player cannot then tell four dice from two. Looking closer is the player's to do, and nothing yet lets them. `FilamentDiceRenderer` and `TrayCamera` both point here for it. The one-finger tap on the tray is kept free for this and for picking a die up (`docs/physics-and-rendering.md`, "Starting a roll") — it deliberately does not roll
- [ ] **Keep the engine when the surface goes.** Filament fixes its swap chain and viewport when a `FilamentStage` is made, so every new surface — a rotation, a resize, the lock screen — builds a whole new engine and recompiles the material. The roll survives it (`TrayRenderer` replays the scene) but the tray is visibly black for a moment while it happens. Split what outlives a surface (the engine, the compiled material, the blank texture) from what does not (the swap chain, the viewport) and rebuild only the second
- [ ] Dice picker row (`1h`) — tap adds, long-press removes, count badges; set dropdown (`4a`)
- [ ] Formula editor (`2a`): the field validates live and blocks rolling, but the error is a line of text rather than a squiggle over the offending range (`6f`, `9c`)
- [ ] Result sheet, full density (`1f`): total, per-group subtotals, dice, modifiers, dropped dice struck through, natural max in the accent
- [ ] Division rounding control on the sheet (`6d`) — Down / Nearest / Up for this throw only
- [ ] Power-saving path (`1z`) — no renderer created, result appears at once
- [ ] First-launch state (`9a`): built-in set only, Unfiled group, no saved rolls
- [ ] *Device:* the whole of Step 5 hangs off this screen

**Done when** every example in `docs/dice-notation.md` can be typed, rolled
and read here, and the same seed gives the same result with the renderer on
and off.

### 4.2 Outcome graph — `feature/graph`

Design `1k`–`1m`, `2c`, `7a`. Spec: `docs/probability.md`.

- [ ] Bar chart (`1k`) of the exact PMF, mean line, ±1σ band
- [ ] P(= k) / P(≥ k) toggle; tap a bar for exact numbers
- [ ] Opened after a roll: the rolled total marked, shown only while the formula still matches (`7a`)
- [ ] Works for a typed formula and for picked dice; works for formulas too large to roll
- [ ] Tests: rendered values match `core/probability` exactly, not approximately

### 4.3 Saved rolls — `feature/saved`

Design `1n`–`1p`, `1r`, `6e`, `7b`, `9b`, `9d`, `9f`, `9g`. Spec:
`docs/dice-notation.md` (Saved rolls).

- [ ] Group switcher (one level: game → character), active group drives the home strip
- [ ] Row-style list (`1o`), favourites first then by recent use; icons in the roll's colour (`9d`)
- [ ] Editor (`1r`): live-validated formula with mean and range, icon, colour, group, favourite, per-roll table pin (`7b`)
- [ ] Export a group or everything as a collection; import from file, URL or repo
- [ ] Import refuses a duplicate group name outright (`6e`), naming the clash — no merge, nothing deleted
- [ ] Broken saved rolls (set uninstalled) show a warning badge and fall back
- [ ] Empty state (`9b`)

### 4.4 Dice sets — `feature/sets`

Design `1s`, `1t`, `5a`, `6a`, `6b`, `8c`, `9h`, `9i`. Spec: `docs/dice-sets.md`.

- [ ] Installed list with status; long-press → disable / remove (`5a`), bundled set protected
- [ ] Set details (`6a`): author, license, source with commit, dice rendered from the set, set-as-default
- [ ] Failed validation (`6b`): the report with file:line replaces the dice grid, folder kept for an update
- [ ] Install from URL or file with progress, then the validator; rejection shows every error (`1t`)
- [ ] Check for updates, update with progress and cancel (`9h`, `9i`)
- [ ] "My dice" details with export as zip gated on a license choice (`8c`)
- [ ] Tests: a malicious archive is refused at every layer, and a failed install leaves nothing behind

### 4.5 Table picker — `feature/tables`

Design `1u`, `9j`. Spec: `docs/tables.md`.

- [ ] Looks from every installed package, rendered on the real box mesh, with a "roll a d20 here" preview
- [ ] Selecting one recolours the tray immediately
- [ ] "Use a photo" → downsize, write into the personal package, validate like any table
- [ ] Precedence: saved roll pin > group pin > app default

### 4.6 Face designer — `feature/designer`

Design `1v`, `4c`, `8d`. Spec: `docs/face-designer.md`.

- [ ] Canvas with the face's outline masked in; strokes stored as vectors so drafts survive process death
- [ ] **The d4 draws three numbers per triangle, one per corner** — its values belong to corners, not faces (`docs/dice-sets.md`, "The d4"). Three guides rather than one, and the two triangles sharing an edge have to agree along it: a die drawn otherwise reads as a different number depending which way it is looked at. Make that hard to do by accident, not a warning afterwards
- [ ] Pen widths, eraser, fill, stamp, undo/redo, 12 presets plus custom colour (`4c`)
- [ ] Face strip for every shape (`8d`): triangle, kite, pentagon, circle
- [ ] "Roll it" throws the die being drawn
- [ ] Export to a real dice set through the standard validator
- [ ] *Confirm first:* the prototype has no 3D preview — see Open questions

### 4.7 Statistics — `feature/stats`

Design `1w`, `5b`, `5c`, `8b`, `9e`. Spec: `docs/statistics.md`.

- [ ] Overview tiles for the selected die: natural highs and lows, average, throws
- [ ] Face histogram against the fair line
- [ ] All-dice table, sortable; filter by set (`5b`), roll-up across sets (`5c`)
- [ ] Saved-roll statistics: observed totals against the exact expected distribution (`8b`, `9e`)
- [ ] Export as JSON/CSV — without seeds
- [ ] Reset per die, per roll, per session, everything

### 4.8 History — `feature/history`

Design `1x`. Spec: `docs/statistics.md`.

- [ ] Past rolls with breakdowns, grouped by session, natural max in the accent
- [ ] No replay, no seed on screen
- [ ] Pruning at 50,000 rows leaves aggregates intact

### 4.9 Sessions — `feature/sessions`

Design `6c`. Spec: `docs/statistics.md`.

- [ ] List with roll counts and nat-20 counts; tap to activate, rename inline, create
- [ ] Delete moves its rolls to Unfiled

### 4.10 Settings and menu — `feature/settings`

Design `1q`, `1y`, `2d`. Spec: `README.md`, `docs/architecture.md`.

- [ ] Full-screen menu (`1q`), grouped Play / Look back / Customise / App
- [ ] Appearance (System / Light / Dark), power-saving (on/off only), shake, haptics, sound, rounding default, default set, table, session — the accent picker is already there and is the pattern the rest follow
- [ ] Replace the single field on `DInfinityApplication` with a real container once more than settings hangs off it
- [ ] Version and repository link (`2d`)
- [ ] Developer toggle: debug overlay, anomaly log, replay from seed

## Step 5 — Physics and rendering on a real phone

**Reserved, and none of it can run on CI.** This is where the app either
convinces or does not: a roll has to look like dice landing, not like an
animation of a random number. Runs first as soon as 4.1 renders, then again
after every physics change.

### 5.1 Harness

- [ ] On-device instrumented runner: N rolls headless, dumps JSON — per-face histogram, settle times, correction and re-throw counts, contact depths, frame times
- [ ] Devcontainer script that installs, runs, pulls the JSON and prints a pass/fail table against the targets below
- [ ] Soak mode (run for minutes, report worst case) and 60 fps screen capture for visual review
- [ ] Same harness runs on the emulator, which is in the devcontainer (`docs/build-setup.md`), so a regression is caught before the phone

### 5.2 Fairness and determinism

- [ ] Every catalogue shape, 100,000 headless rolls: chi-squared p > 0.001, no face off by more than 1 %
- [ ] Identical outcomes for identical seeds across JVM, emulator and device — any divergence is a release blocker. The golden suite is the check and already holds for its ten cases on both ABIs; Step 5 is the same claim at ten thousand rolls and on a second phone
- [ ] Power-saving and rendered mode agree on every seed in the golden suite

### 5.3 Capacity and corner cases

- [ ] Counts 1, 2, 5, 8, 20, 40, 60 and the capacity limit (~80 on the Pixel 10a): all settle, no NaN, no tunnelling
- [ ] Exactly at the limit, and one over — the one over is refused before a single body is created
- [ ] Worst shapes at the limit: d4 (sharpest corners) and the coin (flattest), which wedge and stack most easily
- [ ] Smallest scale (0.40) with the largest nominal die
- [ ] Mixed shapes and mixed sets in one throw
- [ ] Extreme input: sensor maxima, 30 s of shaking, rotation through all axes, shake-then-drop, phone vertical and upside down
- [ ] Interruptions mid-roll: call, backgrounding, rotation, low memory — the roll finishes or is discarded cleanly, never half-resolved
- [ ] Thermal: 100 consecutive 40-dice rolls with no frame-time cliff and no drift in outcomes

### 5.4 Collisions

- [ ] No die–die interpenetration deeper than 0.2 mm at any step
- [ ] No tunnelling at maximum shake velocity — assert every body inside the box on every step, all roll long
- [ ] Dice driven into a corner at speed neither wedge nor jitter
- [ ] A settled pile is stable: no creep, no vibration, no slow slide

### 5.5 Stacking and cocking — and no invisible hand

The two failures to hunt, per `docs/physics-and-rendering.md`:

- a die left resting on another, or on its edge; and
- **a die visibly moved after it stopped**, which is worse — it turns a roll
  into an arrangement in front of the player's eyes.

- [ ] 10,000 headless rolls at 20 dice and 10,000 at 60: **zero** dice at rest supported by another die
- [ ] Fewer than 0.5 % of dice need any correction; **100 %** of those corrections land while the die is still moving
- [ ] **Zero** post-rest corrections. The harness asserts this; one occurrence is a bug, not a statistic
- [ ] Re-throws (the last resort) under 0.05 % of dice, and each one looks like a die being picked up and thrown again
- [ ] Settle time at 20 dice: median under 2 s, p99 under 4 s; the 12 s cap never reached in 10,000 rolls
- [ ] Tune prevention (spawn spread and stagger, dice-on-dice friction, throw energy, scale) until the numbers above hold without leaning on corrections. **Where it starts:** 20 d20s at the capacity rule's scale settle in 89–132 steps on the Pixel 10a, with 9 of the 20 corrected, 0–1 re-thrown and **zero** post-rest corrections. The last figure is the one that must stay at zero and does; the correction rate is 45 % against a 0.5 % budget, and bringing it down is what this task is
- [ ] *With the user:* frame-by-frame review of 50 recorded 20-dice rolls — nobody can point at the moment a die was helped

### 5.6 Feel — the user's call, not a metric

- [ ] Dice respond to a shake within ~100 ms, and they move the way the hand did — the tray itself never moves, because it is the screen (`docs/physics-and-rendering.md`)
- [ ] The tumble reads as dice: bounce height, spin decay, dice rolling on an edge before toppling
- [ ] Haptics fire on real impacts only, sound pitch tracks impulse and die size
- [ ] Settled faces are legible at arm's length without zooming
- [ ] Power-saving feels instant and gives the same answer

### 5.7 Performance on the Pixel 10a

- [ ] 60 fps sustained at 20 dice, frame time p99 under 16.6 ms; at least 30 fps at the capacity limit
- [ ] No memory growth over 500 rolls
- [ ] Battery cost of 100 rolls measured, then written into `docs/physics-and-rendering.md` as the budget

**Done when** every target above is met on the Pixel 10a and the user agrees
the dice look right. Numbers that turn out wrong become the new numbers in
`docs/physics-and-rendering.md` and `docs/tables.md` — the docs follow the
device, not the other way round.

## Step 6 — v1 release

- [ ] `examples/` dice set: every catalogue shape, commented, blank atlases (the built-in set has no export)
- [ ] Accessibility pass: TalkBack through every screen, contrast, touch targets, no colour-only meaning
- [ ] Localisation scaffolding (strings extracted) even if only English ships
- [ ] Play Store metadata, screenshots taken from the real app, privacy statement (no analytics, nothing leaves the phone)
- [ ] Tag `v1.0.0`

## After v1

Written down so the format need not change later. Not v1 scope.

- [ ] More catalogue solids, `rhombic-triacontahedron` (d30) first
- [ ] Author-supplied convex meshes, with the fairness preview they require (`docs/dice-sets.md`, "Shapes after v1")
- [ ] **Author-supplied materials.** Compiling materials at runtime (`docs/architecture.md`, decision 46) means a set *could* ship its own `.mat` rather than only values for the built-in one — iridescent dice, a proper glass d20, a table that is actually brushed metal. v1 does not allow it, and the reason is not effort: a shader is code, it runs on the GPU, and "the app never runs anything from the repository" is a rule of the format (`docs/dice-sets.md`). Turning it on needs a decision about what a shader from a stranger may do — a compile that never finishes is a hung GPU, and a driver is a large attack surface — plus a limit on compile time, a cap on instruction count, and a refusal that is as legible as the validator's other refusals. Until then a set varies a material's *parameters*, which is what `roughness`, `metallic` and the colours already are

## Open questions

- [ ] `core/probability` hand-rolls its convolution and its FFT rather than
      taking a library, which `.claude/CLAUDE.md` names as a "complex part".
      The judgement was that the exact PMF *is* the domain logic and that
      pulling in a general maths library for ninety lines of transform is a
      worse trade than owning them — but it is a trade, and it is worth a
      second opinion
- [ ] The face designer has no 3D preview in the prototype — "Roll it" is the preview. Confirm, then fix `docs/face-designer.md` (4.6)
- [ ] The dice picker remembers the last set per saved-roll group — confirm, then add to `docs/dice-notation.md`
- [ ] Raise `sdk` in `app/src/test/resources/robolectric.properties` to 37 when Robolectric supports it
- [ ] Move the container's emulator up when an automated-test image exists
      above API 36 — the same wait as the line above, for the same reason
      (`docs/architecture.md`, decision 39)
- [ ] The devcontainer asks Docker for `/dev/kvm` unconditionally, so a
      machine without nested virtualisation cannot open the project at all.
      Confirm that is the right default rather than building the image
      without an emulator and asking for the device only when one is wanted
      (`docs/build-setup.md`)
- [ ] d18 shape: the enneagonal trapezohedron is assumed; verify it reads well at phone size
- [ ] Division rounding default is Down with a per-throw override — confirm Nearest is worth having
- [ ] The design project's `.thumbnail` is not imported; decide whether a preview image belongs in the repo
