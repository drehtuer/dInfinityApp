# TODO

The implementation plan. Steps run in order; within a step the boxes are
roughly ordered too. **Finished items are deleted, not ticked** — git history
and `docs/STATUS.md` are the record, and a step that is wholly done is
removed. Keep the file a plan, not a diary.

Everything here builds the specification in `README.md`, `docs/` and the
prototype in [design/](../design/). Where a step says *device*, it cannot run
on CI (see `.claude/CLAUDE.md`).

## Step 1 — Skeleton

An empty but complete module graph that builds and tests green. No features.

- [ ] Devcontainer: JDK 21, Android SDK API 37, NDK, Gradle, ktlint, detekt, sonar-scanner, `adb`
- [ ] Gradle: version catalog (`gradle/libs.versions.toml`) and convention plugins (`kotlin-jvm`, `android-library`, `compose`, `test`) so no module repeats build config
- [ ] Module graph exactly as `docs/architecture.md`: `core/{model,notation,probability,stats}`, `dicesets/{format,install,builtin}`, `simulation/{api,jolt}`, `render/{filament,headless}`, `input/shake`, `designer`, `data`, `feature/*`, `app`
- [ ] Every module gets one placeholder type **and one real test that asserts something true about it** — a graph that only compiles proves nothing
- [ ] Test stack: JUnit5 + `kotlin.test` on the JVM, Robolectric + Compose UI test for Android modules, Turbine for flows, an `androidTest` source set in `simulation/jolt` and `render/filament`
- [ ] `test-fixtures/`: golden roll seeds, a valid `diceset.toml`, one file per validator rejection case, sample saved-roll collections
- [ ] App shell: single activity, Compose theme generated from the Modernist tokens (`design/_ds/…/styles.css` → colours, type, spacing), navigation graph with one empty destination per screen in Step 4
- [ ] Version in one place (`version.txt` or the tag), APK naming `dInfinityApp-<version>[-debug].apk` wired into the build
- [ ] `.editorconfig`, ktlint and detekt configs, `gradle.properties`

**Done when** `./gradlew build test lint detekt ktlintCheck` is green in the
devcontainer and every module named in `docs/architecture.md` exists and is
reachable from `app`.

## Step 2 — CI

Everything a machine can check, on every PR. Emulator and device suites are
Step 5 and stay off CI.

- [ ] **Build and test:** `assembleDebug`, JVM unit tests, Robolectric tests; test results and failures annotated on the PR
- [ ] **Static analysis:** ktlint, detekt, Android Lint — warnings fail the build (`.claude/CLAUDE.md`)
- [ ] **Coverage → SonarQube:** JaCoCo on JVM + Robolectric, reports merged into one XML, `sonar-scanner` publishes it. Quality gate blocks merge; coverage on new code ≥ 80 %
- [ ] Coverage counters are **function and branch**, not lines alone, and a PR that lowers either against `main` fails — the comparison job needs `main`'s report cached or recomputed
- [ ] Sonar exclusions: generated code, Compose previews, and the JNI/renderer bridges whose tests only run on a device — excluded from *coverage*, never from *analysis*, with the device results reported separately so the gap is visible rather than hidden
- [ ] **Docs:** markdown lint, link check (internal links must resolve), mermaid fences must parse, and a check that `README.md` indexes every `docs/*.md` — the rule in `.claude/CLAUDE.md` should be enforced, not remembered
- [ ] **Docs site:** publish `docs/` and `design/` to GitHub Pages so the prototype is clickable straight from the repo, not only from claude.ai
- [ ] **Release on tag `vX.Y.Z`:** optimised build (R8, shrinking), `dInfinityApp-<version>.apk` attached to a GitHub Release, docs site built for the tag. Releases are immutable — the workflow refuses to overwrite an existing tag
- [ ] Caching (Gradle, SDK) so a PR run stays under ~10 minutes

**Done when** a PR shows one green check per concern, Sonar decorates it with
coverage, and a throwaway tag produces a correctly named release APK.

## Step 3 — Foundations

The shared layer every screen sits on. Built bottom-up, each piece tested to
completion before the screens start, because a bug here is a bug in every
screen.

- [ ] `core/model` — `Die`, `DiceSet`, `Face`, `RollPlan`, `DieInstance`, `RollResult`, `SavedRoll`, `SavedRollGroup`. Pure data, no Android
- [ ] `core/notation` — parser and evaluator per `docs/dice-notation.md`: full grammar, keep/drop/explode/reroll/min, set references, limits, error ranges with character offsets. Property tests plus one test per limit and per error message
- [ ] `core/probability` — exact PMF by convolution, order statistics for keep/drop, truncated geometric for explode. Golden tests against a brute-force enumerator; PMF sums to 1 ± 1e-12
- [ ] `dicesets/format` — strict TOML reader, shape catalogue, validator. One test per error and per warning in `docs/dice-sets.md`, driven from `test-fixtures/`
- [ ] `dicesets/builtin` — the bundled set as a real `diceset.toml` package, loaded through the same validator as any download (no privileged path)
- [ ] `simulation/api` — table geometry, the capacity rule with its worked numbers, settle detection, face reading (including `vertex-up` for the d4), the correction ladder in `docs/physics-and-rendering.md`. Testable without a physics engine via a fake simulator
- [ ] `simulation/jolt` — JNI bridge, fixed 120 Hz timestep, seeded and deterministic. **Decide Jolt vs. Bullet with a spike first** and record the result in `docs/architecture.md`
- [ ] Golden determinism suite: (seed, formula, input) → outcome, asserted on every ABI CI can run, and re-asserted on the device in Step 5
- [ ] `render/headless` and `render/filament` — the headless one first, so power-saving works before anything is drawn
- [ ] `input/shake` — sensor fusion to tray motion, recorded and quantised so a roll stays reproducible
- [ ] `data` — Room schema from `docs/statistics.md`, DAOs, migrations from day one
- [ ] `dicesets/install` — fetch (forges, archive URLs, local files), safe extraction (path traversal, symlinks, size and entry caps), atomic install. Tests include a malicious archive per rejection rule

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

- [ ] Tray view bound to the simulation, stack layout (`1b`), table look applied
- [ ] Dice picker row (`1h`) — tap adds, long-press removes, count badges; set dropdown (`4a`)
- [ ] Formula display and inline editor (`2a`) with live validation, error squiggle over the offending range (`6f`, `9c`), rolling blocked while invalid
- [ ] Roll by tap; shake to roll wired to `input/shake`
- [ ] Result sheet, full density (`1f`): total, per-group subtotals, dice, modifiers, dropped dice struck through, natural max in the accent
- [ ] Division rounding control on the sheet (`6d`) — Down / Nearest / Up for this throw only
- [ ] Capacity refusal (`500d6`) with the largest count that would fit
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
- [ ] Appearance (System / Light / Dark), power-saving (on/off only), shake, haptics, sound, rounding default, default set, table, session
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
- [ ] Same harness runs on the emulator, so a regression is caught before the phone

### 5.2 Fairness and determinism

- [ ] Every catalogue shape, 100,000 headless rolls: chi-squared p > 0.001, no face off by more than 1 %
- [ ] Identical outcomes for identical seeds across JVM, emulator and device — any divergence is a release blocker
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
- [ ] Tune prevention (spawn spread and stagger, dice-on-dice friction, throw energy, scale) until the numbers above hold without leaning on corrections
- [ ] *With the user:* frame-by-frame review of 50 recorded 20-dice rolls — nobody can point at the moment a die was helped

### 5.6 Feel — the user's call, not a metric

- [ ] Dice respond to a shake within ~100 ms and the tray motion matches the hand
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

## Open questions

- [ ] The face designer has no 3D preview in the prototype — "Roll it" is the preview. Confirm, then fix `docs/face-designer.md` (4.6)
- [ ] The dice picker remembers the last set per saved-roll group — confirm, then add to `docs/dice-notation.md`
- [ ] SonarCloud or a self-hosted SonarQube? SonarCloud is free for public repositories and decorates PRs out of the box
- [ ] Should `minSdk` stay at Android 17, or drop lower once v1 is out?
- [ ] d18 shape: the enneagonal trapezohedron is assumed; verify it reads well at phone size
- [ ] Division rounding default is Down with a per-throw override — confirm Nearest is worth having
- [ ] The design project's `.thumbnail` is not imported; decide whether a preview image belongs in the repo
