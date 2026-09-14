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
the Pixel 10a, and its total read off the faces. What is below is what it does
not have yet.

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
- [ ] Set dropdown under the picker row (`4a`) — waits on the installed-set registry (4.4); until there is a second set to choose, a chooser with one entry is furniture
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
screen's **See the odds**.

- [ ] "Roll this" and "Save as roll" under the chart. The first needs to hand a formula back to the roll screen, which no navigation does yet; the second needs saved rolls (4.3)
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
- [ ] Import from a **URL or a git repository**, over the same reader and `dicesets/install`'s fetcher. Kept separate from importing a file because it is the first code path that would actually reach the network, and that deserves its own review. **Not because of the permission:** `android.permission.INTERNET` is already in the merged manifest of both the debug and the release build, contributed by `okhttp-android`'s own manifest by way of `dicesets:install`. So the app can already talk to the network and nothing yet does — which is worth knowing before somebody plans a review around a permission prompt that will never appear
- [ ] A broken roll falls back to the built-in set when it is thrown; today it says so on the list but the fallback itself is the planner's and untested from here
- [ ] `SavedRollRepository` is at its function ceiling (detekt's `TooManyFunctions`, 11). Nothing needs to grow it yet — importing went into a class of its own, because it is a transaction rather than a repository operation — but the next thing that does needs the split first: groups one class, rolls another, rather than a raised threshold

### 4.4 Dice sets — `feature/sets`

Design `1s`, `1t`, `5a`, `6a`, `6b`, `8c`, `9h`, `9i`. Spec: `docs/dice-sets.md`.

The screen is built: the installed list with each set's status and a long-press
to disable or remove it (`5a`, bundled set protected); the details behind a tap
(`6a`) with author, licence, source and commit, the dice the set defines, and
set-as-default; the validation report standing where the dice grid would be for
a package that stopped validating (`6b`), its folder kept so an update can fix
it; and installing **from a file** through the validator, where a rejection
lists every error (`1t`). Database version 4 holds which sets are switched on.

- [ ] Install from a **URL**, over `dicesets/install`'s fetcher. The screen, the bounded copy and the report are all built and shared with the file path — what is left is the fetch itself, which is the app's first code that reaches the network
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

Rolls are recorded now — history rows, face counts and running summaries, one
transaction each — so these screens have something to read.

The list of every die thrown is built, with its average; choosing one opens
its overview tiles — natural highs and lows, average, throws — and its face
histogram against the fair line. It filters by set (`5b`) and rolls up across
sets (`5c`) with the fair line weighted by how often each die was thrown.
Forgetting one die's record or everything is there, each behind a confirmation.
Every saved roll's own totals sit against the exact distribution it was rolling
against (`8b`, `9e`), with the drift judged against the standard error rather
than shown bare.

- [ ] Sorting the all-dice list. It is most-recently-used first and nothing else, which is the right default and the only one
- [ ] Export as JSON/CSV — without seeds
- [ ] Reset per saved roll and per session; per die and everything are done

### 4.8 History — `feature/stats`

Design `1x`. Spec: `docs/statistics.md`.

The list is built: past rolls newest first, a tap to open one breakdown, every
die including the dropped ones, the set a roll fell back to, corrections
counted, and a natural maximum in the accent. It cuts to one session or one
saved roll, with a chooser that is not drawn until there is more than one thing
to choose between. No replay and no seed, which the types enforce rather than
the screen remembering. Pruning at 50,000 rows was already done and tested in
`StatisticsRepository`.

- [ ] Export as JSON/CSV, without seeds, shared like a collection (4.3's sharing is the pattern)

### 4.9 Sessions — `feature/stats`

Design `6c`. Spec: `docs/statistics.md`.

Built: database version 3 and its migration, the list with its roll and
natural-high counts, tap to activate, rename, create, and delete that moves the
rolls to the first session rather than deleting them. The active session is a
preference and every roll is filed under it; the history filters by it, and the
menu's header names it once there is more than one session to be in (`1q`).

- [ ] **Filtering the statistics by session needs a decision, not just a
      chooser.** `die_stats` and `die_summary` are keyed by set and die and
      carry no session (`docs/statistics.md`, "Storage"), so there is nothing
      to filter. Either they grow a session column — which multiplies every
      aggregate row by the number of sessions, for a number most players will
      never ask for — or per-session face counts are computed from
      `roll_history.breakdown_json` on demand, which is a scan rather than a
      lookup and is the only option that costs nothing until it is used. The
      second looks right; it is a schema decision either way and is not one to
      take in passing

### 4.10 Settings and menu — `feature/settings`

Design `1q`, `1y`, `2d`. Spec: `README.md`, `docs/architecture.md`.

The menu is built and **the navigation graph is connected**: every screen
carries the same menu button and the menu reaches every screen
(`docs/architecture.md`, "Screens and the states behind them").

- [ ] One row the prototype's menu has that the app has no screen for: "Notation" (the grammar, with examples you can roll). Decide whether it is a screen or belongs in the README. *Saved-roll statistics is built and in the menu (4.7).*
- [ ] A **default table** and a **default session**, the way the default set now works: chosen where the thing itself is, remembered with the settings, and falling back when what was chosen is not there any more
Appearance, the accent, shake, the default rounding, power saving, the version
and the repository link are all there, and each of them does something.

- [ ] Haptics and sound. Left out deliberately: nothing plays anything yet, in either mode, and a settings row that does nothing is a lie (Step 4.1 has the item)
- [ ] Default set, table and session, each of which waits on its own screen (4.4, 4.5, 4.9)
- [ ] Replace the single field on `DInfinityApplication` with a real container. `RollWiring` and `SavedWiring` are now the shape it should take; what is left is the application holding one of those rather than eight lazy fields
- [ ] **Nothing catches a screen that was built and never plugged in.** `DInfinityApp` takes one nullable factory per screen and draws a placeholder for a null, which is right for a test of the graph and wrong for the app: the sessions screen was finished, tested and unreachable for a whole step, because `MainActivity` never passed its presenter and `DInfinityScreensTest` builds its own wiring and so cannot notice. The container above is the fix — one object holding every factory, used by the activity *and* by that test, so a missing screen is a compile error rather than a placeholder
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
- [ ] **`100d4` on the phone: the dice pile into one corner and some wedge between floor and wall.** The d4 is the worst case by some way — it cannot rest flat on another one, so a heap of them has no stable packing. Eight seeds of `100d4` settle headlessly inside the cap with nothing forced (`JoltBridgeTest`), so the *engine* copes; what the phone shows is the pile, which is a prevention problem (5.5) rather than a settling one
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
