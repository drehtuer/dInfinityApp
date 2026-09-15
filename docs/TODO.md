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
the Pixel 10a, and its total read off the faces. The picker row offers a chosen
set's dice, with a chooser under it once there is a second set installed — and
a die taken from a set that is not the default is written `brass:1d20`, so the
row can only ever write a formula that rolls what it showed. What is below is
what it does not have yet.

- [ ] Revisit the capacity constants now that they bite much later. 30 % of the floor and a 40 % minimum scale no longer refuse anything the engine would take: it would take about 240 dice to reach the floor and the engine stops at 100 (`docs/tables.md`). Step 5.3 is where those numbers meet a device
- [ ] **Freeze the dice that are down and let the player re-roll the ones that are not.** The user's proposal for unstacking, and worth taking seriously: a die that has landed cleanly is finished and could be lifted off the mat and shown as an overlay, leaving only the stuck ones in the tray to be thrown again. It keeps the honest rule — a settled die is never *moved*, only taken out of play once its face is read — and it turns the worst case from "the app fixes it invisibly" into "you roll again", which is what a person does at a table. Needs the design for how ninety-nine finished dice are shown; the mechanism can be decided first (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice")
- [ ] *Confirm on the phone:* a roll stranded by losing its surface is fixed (a roll now asks for frames with nowhere to draw), but whether that was what left `100d4` on "Rolling…" for ever is unproven — the physics settles that throw headlessly on eight seeds, so the hang was never in the engine
- [ ] **The surface outlives the screen going off.** After a lock and unlock the old rendering surface is still there. Found on the Pixel 10a; `DiceTray` gives the surface up on `onDestroyed` and the driver keeps the engine now (Step 4.1, done), so what is left is which of those two the lock screen actually triggers
- [ ] **A shake during a roll is ignored.** Rolling is blocked until the dice have settled, so a second shake at dice still in the air does nothing. It should keep them moving instead — a hand that shakes again has not waited for the dice to stop, and `RollPresenter.shaking` already feeds a running roll. What a *new* shake means while one is in flight is the open half: more of the same roll, or a throw that replaces it
- [ ] Numbers on the faces. Dice are blank cream solids on the phone right now, which is the SDF item in Step 3 above; until it lands, the tray shows a roll that cannot be read without the total
- [ ] A shake-driven throw's `ThrowSpec` carries an empty `shake`: the dice are spawned the moment the shake is confirmed, and the samples arrive afterwards. The roll is driven by them and is reproducible from them, but the *record* of the throw does not yet hold them — which is what a replay and a bug report would need (`docs/physics-and-rendering.md`, "Shake input"). Attach the recorded session to the result when history arrives (4.8)
- [ ] Draw the dice an explosion or a reroll adds. They are simulated for real, one throw each, but into a tray nobody is looking at; they belong in the tray on screen, landing among the dice that set them off (`docs/dice-notation.md`)
- [ ] Judge the pinch and the pan on a phone: whether `TrayView.CLOSEST` (four times in) is far enough to settle an argument about a face and near enough that the table has not gone, and whether a two-finger drag feels like moving the table rather than the camera. The arithmetic is tested; the feel is not testable (`docs/physics-and-rendering.md`)
- [ ] Pick a die up and throw it again, which is what the tray's one-finger touch is being kept for (`docs/physics-and-rendering.md`, "Starting a roll")
- [ ] *Judge the picker row on the phone:* the built-in set offers ten dice, and ten at a touch target worth pressing do not fit across a 360 dp screen, so the row scrolls. Whether that reads as "there are more dice over there" or as "the d20 is missing" is not something a test can answer — and the d20 is the die most people want (`design/dInfinity.dc.html`, option 1h)
- [ ] **A set's own dice cannot be picked**, which is the open half of decision 31: plain notation names `dN`, `d%` and `dF`, so `skull-d6` has no spelling the formula field could carry and the row cannot offer it. Either notation gains a way to name a set's die, or picked dice stop going through the text — and the second is a bigger change than it looks, because the text *is* the roll everywhere downstream (`docs/dice-notation.md`)
- [ ] Formula editor (`2a`): the formula on the tray is not tappable — the field is always on screen instead of appearing when the formula is tapped, and Enter does not roll. The squiggle and the error line under it are done (`6f`, `9c`)
- [ ] The sheet itemises the *dice*; the modifiers are only visible in the formula line it prints. Itemising them — `+ 4` on a row of its own — needs the evaluator to report what it added, which it does not yet (`docs/dice-notation.md`)
- [ ] *Judge power-saving on the phone:* it throws and reports with no tray on screen, but the screen it leaves behind is the formula, the picker and a total with nothing above them. The design shows a short progress indicator and a result sheet in the tray's place (`1z`); whether the gap reads as "instant" or as "broken" needs eyes
- [ ] Haptics and sound in power-saving mode: the design plays recorded impacts back over about a second rather than in real time (`docs/physics-and-rendering.md`). Nothing plays anything yet, in either mode
- [ ] First launch (`9a`): the welcome is there, with its "roll a d20 now" and its way straight to the tray, and the saved-roll strip beneath it. Its other two offers — import a collection, add dice sets — are still missing: importing has a screen now and could be offered, and dice sets is still a placeholder (4.4). So is the rest of the count line: "0 saved rolls, 0 sessions" waits on sessions (4.9)
- [ ] *Device:* the whole of Step 5 hangs off this screen

**Done when** every example in `docs/dice-notation.md` can be typed, rolled
and read here, and the same seed gives the same result with the renderer on
and off.

### 4.2 Outcome graph — `feature/graph`

Design `1k`–`1m`, `2c`, `7a`. Spec: `docs/probability.md`.

The screen is built: the bar chart with its mean line and ±1σ band, the
`P(= k)` / `P(≥ k)` question, a tap for the exact numbers, the six statistics,
and the roll that opened it marked in the accent. It is reached from the roll
screen's **See the odds**, and under the chart are the two things to do with a
formula whose odds you have just read: **Roll this** hands it to the tray, and
**Save as roll** opens the editor with it already typed. Both carry what is in
the field rather than what the screen opened with, and neither is offered for a
formula there are no odds for.

- [ ] *Judge the chart on the phone:* whether a hundred and ten bars at three dp each reads as a distribution or as a smear, and whether the ±1σ band behind the bars is visible enough to mean anything in both themes
- [ ] The ledger (`1m`) and the stepped area (`1l`) are alternative presentations of the same numbers; `1k` is the default and the other two are not v1

### 4.3 Saved rolls — `feature/saved`

Design `1n`–`1p`, `1r`, `6e`, `7b`, `9b`, `9d`, `9f`, `9g`. Spec:
`docs/dice-notation.md` (Saved rolls).

The tables, their migration and the repository over them are built and tested:
groups nest one level and nothing can make them nest deeper, a roll always has
somewhere to be, and deleting a group moves its rolls rather than deleting
them. What is left is the screens.

The list is built: the group switcher, the row-style list in favourites-first
order, the warning on a roll whose dice are gone, the empty state, and tapping
a roll to send its formula to the tray. Groups can be made, renamed, moved and
deleted, from the switcher or from the editor — the same sheet in both places.

- [ ] The editor offers ten emoji as icons. The design has an icon pack; whether one is worth drawing, or emoji is the answer, is a decision rather than an omission (`docs/dice-notation.md` says "an emoji or a name from the built-in icon pack")
- [ ] Import from a **git repository**, which is the half of this the plain link does not cover — a repo of "stat blocks for monster manual X" resolved through `RefResolver` the way a dice set is (`docs/dice-sets.md`). Importing from a plain `https` link is built: it goes through the same downloader a dice set does, capped at the megabyte the reader refuses a file above, and what comes back goes through `CollectionReader` rule for rule
- [ ] `SavedRollRepository` is at its function ceiling (detekt's `TooManyFunctions`, 11). Nothing needs to grow it yet — importing went into a class of its own, because it is a transaction rather than a repository operation — but the next thing that does needs the split first: groups one class, rolls another, rather than a raised threshold

### 4.4 Dice sets — `feature/sets`

Design `1s`, `1t`, `5a`, `6a`, `6b`, `8c`, `9h`, `9i`. Spec: `docs/dice-sets.md`.

The screen is built: the installed list with each set's status and a long-press
to disable or remove it (`5a`, bundled set protected); the details behind a tap
(`6a`) with author, licence, source and commit, the dice the set defines, and
set-as-default; the validation report standing where the dice grid would be for
a package that stopped validating (`6b`), its folder kept so an update can fix
it; and installing **from a file or from a pasted `https` link** through the
validator, where a rejection lists every error (`1t`) and a download that never
arrives is refused the same way. Database version 4 holds which sets are
switched on.

- [ ] Check for updates, update with progress and cancel (`9h`, `9i`)
- [ ] "My dice" details with export as zip gated on a license choice (`8c`)
- [ ] *Done, and worth knowing where:* a malicious archive is refused at every layer and a failed install leaves nothing behind. `SafeExtractorTest` has the paths that climb out, the absolute and Windows paths, the symbolic links, the entry count and the zip bomb refused at the megabyte it becomes obvious; `PackageInstallerTest` has the failed, hostile, interrupted and unwritable installs, each leaving nothing behind and each leaving an existing package alone; `dicesets/format` has the set files that lie about themselves and the images that are not images; and `HostileArchiveTest` joins them up over a real HTTPS server now that an archive can arrive from a link. What is *not* covered is a malicious **texture**, which needs a decoder (Step 3)

### 4.5 Table picker — `feature/tables`

Design `1u`, `9j`. Spec: `docs/tables.md`.

The screen is built: every look from every installed package in one list —
tables are global, so a set never brings its own along — with the chosen one
marked, the package named beside a look only when more than one supplies
tables, and a swatch of the two colours a look is actually made of. Choosing
one writes it to the settings and the tray is built on it the next time it is
opened, which is immediately in the only sense that matters. A pin whose
package is gone shows the look the tray would really use, and the setting is
left alone in case it comes back.

- [ ] Thumbnails rendered on the real box mesh, with a "roll a d20 here" preview. The swatch stands in: it is two colours in a box and says so. This wants the renderer on a screen that is not the tray, which nothing has needed yet
- [ ] "Use a photo" → downsize, write into the personal package, validate like any table
- [ ] Precedence: saved roll pin > group pin > app default. The app default is this screen's and is done; `SavedRoll.tablePin` is in the model and the editor has the field, and a throw from the strip now carries which roll and which group it came from (`SavedRollSource`), so what is left is the group's pin and the three-way fallback at roll time — most of the plumbing this needed is there

### 4.6 Face designer — `feature/designer`

Design `1v`, `4c`, `8d`. Spec: `docs/face-designer.md`.

Drawing is built: the canvas with the face's outline masked in, strokes stored
as vectors in fractions of the canvas, the guide under them that can be turned
off, three pen widths and an eraser, undo/redo and clear per face, the twelve
presets, and the face strip. The d4's three-numbers-per-corner rule is
**derived rather than checked** — a cell's numbers are read from the corners it
meets, so two cells sharing an edge cannot be made to disagree along it.

- [ ] Fill bucket, stamp from the built-in font, copy face → paste with rotate/mirror, "fill all faces with numbers", and a colour picker beyond the twelve presets (`4c`)
- [ ] The guide draws a dot where each number goes rather than the number: text inside a `Canvas` wants a measurer, and the value is legible on the strip meanwhile
- [ ] Drafts on disk — vectors survive a rotation today, not process death (`docs/face-designer.md`, "Drawing tools"), and the 50-draft limit comes with them
- [ ] "Roll it" throws the die being drawn
- [ ] Export to a real dice set through the standard validator: atlas at 256 px per cell, transparent cells, generated `diceset.toml`, licence asked for before sharing
- [ ] Quick mode: long-press a die on the roll screen for "Doodle this die"
- [ ] *Confirm first:* the prototype has no 3D preview — see Open questions. Nothing here builds one

### 4.7 Statistics — `feature/stats`

Design `1w`, `5b`, `5c`, `8b`, `9e`. Spec: `docs/statistics.md`.

Rolls are recorded now — history rows, face counts and running summaries, one
transaction each — so these screens have something to read.

The list of every die thrown is built, with its average; choosing one opens
its overview tiles — natural highs and lows, average, throws — and its face
histogram against the fair line. It filters by set (`5b`) and rolls up across
sets (`5c`) with the fair line weighted by how often each die was thrown.
Forgetting one die's record or everything is there, each behind a confirmation.
Every saved roll's own totals sit against the exact distribution it was rolling
against (`8b`, `9e`), with the drift judged against the standard error rather
than shown bare. It exports as JSON or CSV through the share sheet: one row per
face in the flat form, and the per-die runs added in the full one, because a
run is the one thing face counts cannot give back. The list can be put in
three orders — most recently used, most thrown, highest average — with recency
the default, because that is the die somebody came about.

### 4.8 History — `feature/stats`

Design `1x`. Spec: `docs/statistics.md`.

The list is built: past rolls newest first, a tap to open one breakdown, every
die including the dropped ones, the set a roll fell back to, corrections
counted, and a natural maximum in the accent. It cuts to one session or one
saved roll, with a chooser that is not drawn until there is more than one thing
to choose between. It exports as JSON or CSV through the share sheet, carrying
everything the filter matches rather than the page on screen, and forgets those
same rolls — a session's or a saved roll's — behind a confirmation that says
what stays as well as what goes. No replay and no
seed, which the types enforce rather than the screen remembering — the export
is built from `HistoryEntry`, which has none to write. Pruning at 50,000 rows
was already done and tested in `StatisticsRepository`.

### 4.9 Sessions — `feature/stats`

Design `6c`. Spec: `docs/statistics.md`.

Built: database version 3 and its migration, the list with its roll and
natural-high counts, tap to activate, rename, create, and delete that moves the
rolls to the first session rather than deleting them. The active session is a
preference and every roll is filed under it; the history filters by it, and the
menu's header names it once there is more than one session to be in (`1q`).
Database version 5 puts the session on every face count, so the **statistics**
filter by it too — stored rather than recomputed, because a scan of fifty
thousand history rows on every draw is the thing a column is for.
`die_summary` deliberately did not grow one: counts add and streaks do not
(`docs/statistics.md`, per session).

### 4.10 Settings and menu — `feature/settings`

Design `1q`, `1y`, `2d`. Spec: `README.md`, `docs/architecture.md`.

The menu is built and **the navigation graph is connected**: every screen
carries the same menu button and the menu reaches every screen
(`docs/architecture.md`, "Screens and the states behind them").

Notation is a screen, in the App section beside Settings: the grammar in
sentences, with an example on every line that puts that formula in the tray.
It has no state — it is `NotationReference`, which lives beside the parser, with
a layout on it — and a test parses every example it offers, so it cannot show a
formula the app would refuse. That was the last row the prototype's menu had
and the app did not.

A screen can no longer be built and left unplugged. `Presenters` holds a
factory per screen with no optional fields, so adding a destination stops the
activity compiling until it says how to build one; the same object is what the
tests wire, and one of them walks every menu destination and fails on a
placeholder that is not Table picker or Face designer. Both halves were checked
by putting the original bug back: removing the sessions screen's dispatch turns
the list into `[sessions, tables, designer]`.

Appearance, the accent, shake, the default rounding, power saving, the version
and the repository link are all there, and each of them does something.

All three defaults now behave the same way, which was the point of the item
that used to be here: the default **set**, the default **table** and the active
**session** are each chosen where the thing itself is, remembered with the
settings, and fall back when what was chosen is not there. The set and the
table fall back while leaving the setting alone, so re-installing the package
restores the choice; the session falls back where a roll is *recorded*, because
a session deleted while another screen was in front would otherwise strand
every throw filed under it (`docs/statistics.md`, per session).

- [ ] Haptics and sound. Left out deliberately: nothing plays anything yet, in either mode, and a settings row that does nothing is a lie (Step 4.1 has the item)
- [ ] Developer toggle: debug overlay, anomaly log, replay from seed

## Step 5 — Physics and rendering on a real phone

**Reserved, and none of it can run on CI.** This is where the app either
convinces or does not: a roll has to look like dice landing, not like an
animation of a random number. Runs first as soon as 4.1 renders, then again
after every physics change.

### 5.1 Harness

- [ ] On-device instrumented runner: N rolls headless, dumps JSON — settle times, correction and re-throw counts, contact depths, frame times. **The per-face histogram half exists** as `FairnessTest`, which takes its roll count from an instrumentation argument and prints its table (`docs/physics-and-rendering.md`, "Are the dice fair"); the rest of the numbers, and JSON rather than a printed table, are what is left
- [ ] Devcontainer script that installs, runs, pulls the JSON and prints a pass/fail table against the targets below
- [ ] Soak mode (run for minutes, report worst case) and 60 fps screen capture for visual review
- [ ] Same harness runs on the emulator, which is in the devcontainer (`docs/build-setup.md`), so a regression is caught before the phone

### 5.2 Fairness and determinism

- [ ] Every catalogue shape, 100,000 headless rolls: chi-squared p > 0.001, no face off by more than 1 %. **Run on the Pixel 10a, and seven of the eight pass** — their χ² sums to 55.33 against 55 degrees of freedom, which is as close to "exactly as fair as chance predicts" as a number gets (`docs/physics-and-rendering.md`, "Are the dice fair"). This item closes when the eighth does
- [ ] **The d18 is not fair: χ² 197.34 against a limit of 40.79.** Reproducible — the same faces are heavy across three independent seed schemes, with the deviation patterns of separate runs correlating at +0.6 to +0.9 where independent samples of a fair die sit near ±0.24. No single face is off by more than 0.455 %, so the 1 % bound does not catch it; several are off by around 6 %. It is **not** the shape: an enneagonal trapezohedron is isohedral and the throw starts evenly over all orientations, which together make a fair die whatever the physics does — so the body the engine collides is not the solid the arithmetic describes. Ruled out on the phone: the seeds, Jolt's convex radius (rebuilt with shrinking off, same faces heavy), a corner dropped by the hull tolerance (every corner protrudes 35–70× it), and an off-centre mass (a dipole explains 12 % of the variance). The deviations pair up antipodally, so it is *axes* that finish vertical too often rather than faces that are sticky. **The body and the engine are both ruled out now, on the device:** `DieBodyTest` asks the solver what it built and every shape comes back exact — the d18 with eighteen faces, all at one inradius to six decimals, mass on the origin, inertia with the solid's own symmetry; and turning the d18 by one of its own symmetries gives a histogram that is the exact permutation of the untuned one (χ² 35.83 either way), so the solver is even-handed about the same solid. **The throw is ruled out too, now, and the clue is where the bias sits.** Stirring the streams left it at χ² 135.86; the starting turn is independent of the force it is thrown with (χ² 12–27 against 17 over 400,000 throws); and the turns are evenly spread whichever generator makes them (χ² 5.9–23.9 against 17 over two million). Three runs across two ABIs and three seed schemes correlate at 0.86–0.90, so it is one fixed bias. And it sits **within** the solid's own ninefold orbits — χ² 94.2 and 40.5 against 8 degrees of freedom each, against 1.34 between the two rings — which is exactly what that symmetry forbids. Every premise of the argument now holds to the precision it was measured at, so the next thing to measure is precision: the hull is exactly symmetric in double and reaches the engine as float32, symmetric to one part in 10⁷, and the d18 has the narrowest resting basins in the catalogue (adjacent faces 28.4° apart against a d10's 51.8°). Run the harness against a hull the engine holds in double and see whether it comes out fair
- [ ] Identical outcomes for identical seeds across JVM, emulator and device — any divergence is a release blocker. The golden suite is the check and already holds for its ten cases on both ABIs; Step 5 is the same claim at ten thousand rolls and on a second phone
- [ ] Power-saving and rendered mode agree on every seed in the golden suite

### 5.3 Capacity and corner cases

- [ ] Counts 1, 2, 5, 8, 20, 40, 60 and the capacity limit (~80 on the Pixel 10a): all settle, no NaN, no tunnelling
- [ ] **`100d4` does not reliably settle, and never did.** The d4 is the worst case by some way — it cannot rest flat on another one, so a heap of them has no stable packing. `JoltBridgeTest` used to try eight seeds and pass; twenty-four seeds show **five running out of the twelve-second cap**, and the same twenty-four under the correlated spawn streams that preceded them showed two — a difference well inside noise at that sample size. What changed is not the physics but the sample: the eight were the easy ones. Nothing is ever touched after it has come to rest, on any seed, which is the rule that matters; the cap firing at all is a prevention problem (5.5), and the bound in the test is today's worst case written down rather than a target
- [ ] **Decide what a tilted phone should mean.** Deferred, not answered. The table is horizontal now and the gyroscope no longer turns the world, which is what stopped the dice pouring into a wall — but "tilt the phone and the dice slide" was a real idea and this is not a verdict on it. The direction is still recorded with every sample, so whichever way it goes the data is there. The three answers, unchanged: gravity always straight down and only the hand moves the dice; anchor to `TYPE_GRAVITY` and accept that a phone held upright pours everything to the bottom wall; or keep a tilt and clamp it so a tray can lean without becoming a chute
- [ ] **A shake along the phone's long axis still drives the dice into one end.** Seen as dice stuck at the bottom after a vertical shake. The table being horizontal fixes the *pouring* — the tray no longer leans — but the hand's own force still points that way, and a hundred dice pushed at one wall have nowhere else to be. Whether that is right (it is what a hand does) or wants shaping is a Step 5.6 question with a phone in it
- [ ] Exactly at the limit, and one over — the one over is refused before a single body is created
- [ ] Worst shapes at the limit: d4 (sharpest corners) and the coin (flattest), which wedge and stack most easily
- [ ] Smallest scale (0.40) with the largest nominal die
- [ ] Mixed shapes and mixed sets in one throw
- [ ] Extreme input: sensor maxima, 30 s of shaking, rotation through all axes, shake-then-drop, phone vertical and upside down. **Upside down is done and was broken:** the roll screen pinned the display to the rotation it opened at, so `PhoneAxes` was told the phone was upright while it was being shaken the other way up and the dice pooled at the end away from the hand. The screen now holds its shape rather than its rotation (`docs/tables.md`); a quarter turn is still refused
- [ ] Interruptions mid-roll: call, backgrounding, rotation, low memory — the roll finishes or is discarded cleanly, never half-resolved
- [ ] Thermal: 100 consecutive 40-dice rolls with no frame-time cliff and no drift in outcomes

### 5.4 Collisions

- [ ] No die–die interpenetration deeper than 0.2 mm at any step
- [ ] No tunnelling at maximum shake velocity — assert every body inside the box on every step, all roll long
- [ ] Dice driven into a corner at speed neither wedge nor jitter
- [ ] A settled pile is stable: no creep, no vibration, no slow slide
- [ ] Assert containment on *every step* rather than only at rest, at the capacity limit: the at-rest check is in `JoltBridgeTest` now, but a die that leaves the tray mid-roll and comes back would still pass it

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
- [ ] **The corrections are visible at 100 dice, and they look like popcorn.** Seen on the Pixel 10a: dice stack against a wall and then *pop* apart to unstack, and individual dice jump to find a better spot. Every one of those lands while the die is still moving, so the honest rule holds and nothing touches a die at rest — but "it does not cheat" and "it does not look like it cheats" are different claims, and this is the second one failing. It is the 45 %-against-0.5 % correction rate above, seen rather than counted, and it is the argument for prevention over correction rather than a separate task
- [ ] *With the user:* frame-by-frame review of 50 recorded 20-dice rolls — nobody can point at the moment a die was helped

### 5.6 Feel — the user's call, not a metric

- [ ] Dice respond to a shake within ~100 ms, and they move the way the hand did — the tray itself never moves, because it is the screen (`docs/physics-and-rendering.md`). The direction and the dropped-force stutter are both fixed; what is left to judge is the *start*, which read as a lag on the Pixel 10a: the dice are already travelling fast when the shake begins to reach them, so the hand seems to be catching up with dice that left without it. The 100 ms start threshold and the spawn impulse are the two numbers in it
- [ ] The tumble reads as dice: bounce height, spin decay, dice rolling on an edge before toppling. **Not yet:** on the Pixel 10a the dice do not travel far enough and the tumble does not read as dice being thrown. Throw energy and spawn spread are where that is tuned (5.5), and this is the judgement that says when it is right
- [ ] The rim's shadow still looks wrong — the band across the top of the wall casts something that does not read as a rim. Lighting and the shadow map, not geometry, on present evidence
- [ ] Rendering polish — shader tuning, and the optimisation pass — is deliberately **last**: it is worth doing once the dice move the way they should, and worth nothing before that. Nothing above should wait for it
- [ ] Haptics fire on real impacts only, sound pitch tracks impulse and die size
- [ ] Settled faces are legible at arm's length without zooming. The *size* is settled — 16 mm reads fine on the Pixel 10a — so this is now a question about the numbers, once they are drawn
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

## Coverage

Branch coverage sits around 70 % against a floor of 62, and roughly **seven in
ten of the branches it is missing are inside `@Composable` functions**. That is
not untested UI: the Compose compiler emits a skip branch for every parameter
of every composable so that a recomposition can be avoided, and a test can only
reach one side of each. A screen with twelve controls is a hundred branches no
test will ever take.

What that means in practice, and the rule the last few PRs have followed:

- **Extract the decision, test the decision.** `FaceHistogram`, `Breakdown`,
  `CollectionExport`, `GraphBars`, `underlinesOf` and `crestPath` are all
  arithmetic that used to be inside a draw lambda. Each is now a plain object
  with plain tests, and a Canvas is the one place a test genuinely cannot go.
- **A shared component gets its own test.** `ui/common`'s formula field had
  152 branches and none of them covered, because three screens each tested
  *their use* of it and nobody tested the thing. That is a real gap and looks
  exactly like the mechanical one in a report.
- The number is worth watching for the second kind and not the first. It is
  reported with the figures in every PR description either way.

- [ ] Decide whether the floor should track the drift or stay where it is. It
      has not been moved since it was set, and moving a floor to make a check
      pass is the thing `.claude/CLAUDE.md` says not to do — so this is a
      question for a person, not a change to make quietly

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
