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

- [ ] Re-enable CodeQL's `java-kotlin` analysis once the bundle supports Kotlin 2.4.20 — the matrix entry is commented out in `.github/workflows/codeql.yml` with the build steps kept ready. **Checked against bundle 2.27.0 (2026-09-09): still not there.** The extractor ships one shim per Kotlin release and its newest is `v_2_4_0`, so the bound the error names is unchanged. The cheapest way to check again is to list `java/kotlin-extractor/src/main/kotlin/utils/versions` at the bundle's tag and look for a `v_2_4_20`
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

**Done.** A formula is parsed, planned, simulated headless and scored from a
unit test with no UI in the picture. The last two boxes closed together: a die's
artwork now reaches the tray and is composited over its printed labels rather
than instead of them, with the two decoder-only texture checks that came with it
(`docs/dice-sets.md`, "How an atlas reaches the tray"); and a roll driven by a
recorded shake was shown on the Pixel 10a to replay to itself from
`FinishedThrow.thrown` — same faces, same step count.

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
row can only ever write a formula that rolls what it showed.

**The formula is tapped, not filled in** (`2a`): it sits on the tray as text
with a dashed rule under it, a tap brings the field and the keyboard up, and
Enter rolls. The squiggle and the error line are in the editor where they can
be acted on; the line itself is marked in red (`6f`, `9c`).

**First launch offers all three ways in** (`9a`): throw a d20 now, go straight
to the tray, bring saved rolls in from a file or a link, or add somebody else's
dice. The last two do not dismiss it — somebody who goes to fetch something
comes back to a welcome whose count line has something new to say, and that
line now counts the sets, the saved rolls and the sessions there really are
rather than a sentence with a zero written into it.

What is below is what it does not have yet.

- [ ] Revisit the capacity constants now that they bite much later. 30 % of the floor and a 40 % minimum scale no longer refuse anything the engine would take: it would take about 240 dice to reach the floor and the engine stops at 100 (`docs/tables.md`). Step 5.3 is where those numbers meet a device
- [ ] **Freeze the dice that are down and let the player re-roll the ones that are not.** The user's proposal for unstacking, and worth taking seriously: a die that has landed cleanly is finished and could be lifted off the mat and shown as an overlay, leaving only the stuck ones in the tray to be thrown again. It keeps the honest rule — a settled die is never *moved*, only taken out of play once its face is read — and it turns the worst case from "the app fixes it invisibly" into "you roll again", which is what a person does at a table. Needs the design for how ninety-nine finished dice are shown; the mechanism can be decided first (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice")
- [ ] *Confirm on the phone:* a roll stranded by losing its surface is fixed (a roll now asks for frames with nowhere to draw), but whether that was what left `100d4` on "Rolling…" for ever is unproven — the physics settles that throw headlessly on eight seeds, so the hang was never in the engine
- [ ] **The surface outlives the screen going off.** After a lock and unlock the old rendering surface is still there. Found on the Pixel 10a; `DiceTray` gives the surface up on `onDestroyed` and the driver keeps the engine now (Step 4.1, done), so what is left is which of those two the lock screen actually triggers
- [ ] *Judge a second shake on the phone:* a shake at dice still in the air now keeps them moving rather than doing nothing — it starts no throw, and its moments are numbered on the running roll's clock so they reach it at all, which is what was actually broken (`docs/physics-and-rendering.md`, "Shake input"). Decided along the way: a second shake is **more of the same roll**, not a throw that replaces it, because the dice are the ones already tumbling. Whether that reads as the dice answering the hand, and whether a roll can now be kept going longer than anybody wants, needs a phone
- [ ] Judge the pinch and the pan on a phone: whether `TrayView.CLOSEST` (four times in) is far enough to settle an argument about a face and near enough that the table has not gone, and whether a two-finger drag feels like moving the table rather than the camera. The arithmetic is tested; the feel is not testable (`docs/physics-and-rendering.md`)
- [ ] Pick a die up and throw it again, which is what the tray's one-finger touch is being kept for (`docs/physics-and-rendering.md`, "Starting a roll")
- [ ] *Judge the picker row on the phone:* the built-in set offers ten dice, and ten at a touch target worth pressing do not fit across a 360 dp screen, so the row scrolls. Whether that reads as "there are more dice over there" or as "the d20 is missing" is not something a test can answer — and the d20 is the die most people want (`design/dInfinity.dc.html`, option 1h)
- [ ] **A set's own dice cannot be picked**, which is the open half of decision 31: plain notation names `dN`, `d%` and `dF`, so `skull-d6` has no spelling the formula field could carry and the row cannot offer it. Either notation gains a way to name a set's die, or picked dice stop going through the text — and the second is a bigger change than it looks, because the text *is* the roll everywhere downstream (`docs/dice-notation.md`)
- [ ] *Judge power-saving on the phone:* it throws and reports with no tray on screen, but the screen it leaves behind is the formula, the picker and a total with nothing above them. The design shows a short progress indicator and a result sheet in the tray's place (`1z`); whether the gap reads as "instant" or as "broken" needs eyes
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

Rolls and groups are one repository each now, joined by `SavedRollLibrary` for
the screens that need both — the split the class size had been asking for, and
the place the table fallback below has to live.

A collection comes in from a file, a link or a **git repository**, and the
three are one path: `InstallSource` says which repository a URL means, the same
downloader fetches it under the collection's own megabyte, the same hardened
extractor unpacks it, and the one `*.dinfinity.json` at its root goes through
`CollectionReader` like anything else.

- [ ] The editor offers ten emoji as icons. The design has an icon pack; whether one is worth drawing, or emoji is the answer, is a decision rather than an omission (`docs/dice-notation.md` says "an emoji or a name from the built-in icon pack")

### 4.4 Dice sets — `feature/sets`

Design `1s`, `1t`, `5a`, `6a`, `6b`, `8c`, `9h`, `9i`. Spec: `docs/dice-sets.md`.

**Every kind of source can be checked for updates.** A forge is asked which
commit its ref is at; a plain archive is asked, with a `HEAD`, what its server
says about the file. A set installed before the second was recorded, or against
a server that has stopped sending what it once sent, is *unanswerable* rather
than badged — re-downloading a set that has not changed is the one wrong answer
here (`docs/dice-sets.md`, "Updates").

A download shows how far it has got and can be stopped (`9i`): the bar is drawn
from bytes that arrived rather than from what the server claimed, it is there
only while something is on the wire, and Cancel stops the download and says
nothing about it.

The screen is built: the installed list with each set's status and a long-press
to disable or remove it (`5a`, bundled set protected); the details behind a tap
(`6a`) with author, licence, source and commit, the dice the set defines, and
set-as-default; the validation report standing where the dice grid would be for
a package that stopped validating (`6b`), its folder kept so an update can fix
it; and installing **from a file or from a pasted `https` link** through the
validator, where a rejection lists every error (`1t`) and a download that never
arrives is refused the same way. Database version 4 holds which sets are
switched on.

**"My dice" is an ordinary package** (`8c`). The drawings on the phone are
built into `dicesets/mine/` whenever the folder is read and a drawing has
changed, so the list, the details screen, the notation and the remove button
all treat it like anything else. Its details screen is the one place that is
different: it offers the package as a zip, **shut until a licence has been
chosen**, and the choice is written into the file and into the installed folder
alike. What goes out is validated first, by the same validator a download goes
through.

- [ ] *Done, and worth knowing where:* a malicious archive is refused at every layer and a failed install leaves nothing behind. `SafeExtractorTest` has the paths that climb out, the absolute and Windows paths, the symbolic links, the entry count and the zip bomb refused at the megabyte it becomes obvious; `PackageInstallerTest` has the failed, hostile, interrupted and unwritable installs, each leaving nothing behind and each leaving an existing package alone; `dicesets/format` has the set files that lie about themselves and the images that are not images; and `HostileArchiveTest` joins them up over a real HTTPS server now that an archive can arrive from a link. A malicious **texture** is covered too, now that there is a decoder: `InstalledArtworkTest` has the paths that climb out of a package and the file over the cap, each refused before a decoder sees it, `AtlasDecoderTest` has the image refused from its bounds with nothing decoded, and `AtlasDecoderDeviceTest` has the file that passes the header check and will not decode — on a device, because Robolectric hands back a fake bitmap for bytes it cannot identify

### 4.5 Table picker — `feature/tables`

Design `1u`, `9j`. Spec: `docs/tables.md`.

Precedence is done: a saved roll's pin beats its group's, which beats the app
default. The group sheet has the field the model and the schema had been
waiting for, the rule is settled where the roll and its group are both in hand
and travels with the throw, and the tray is retold whenever the table changes.
A throw from the saved-rolls *list* carries no pin because it carries no
attribution — that tap fills the field and the player throws it.

The screen is built: every look from every installed package in one list —
tables are global, so a set never brings its own along — with the chosen one
marked, the package named beside a look only when more than one supplies
tables, and a swatch of the two colours a look is actually made of. Choosing
one writes it to the settings and the tray is built on it the next time it is
opened, which is immediately in the only sense that matters. A pin whose
package is gone shows the look the tray would really use, and the setting is
left alone in case it comes back.

- [ ] Thumbnails rendered on the real box mesh, with a "roll a d20 here" preview. The swatch stands in: it is two colours in a box and says so. This wants the renderer on a screen that is not the tray, which nothing has needed yet

### 4.6 Face designer — `feature/designer`

Design `1v`, `4c`, `8d`. Spec: `docs/face-designer.md`.

Drawing is built: the canvas with the face's outline masked in, marks stored as
vectors in fractions of the canvas, the guide under them that can be turned
off, three pen widths and an eraser, undo/redo and clear per face, the twelve
presets, and the face strip. The **bucket** adds a region rather than flooding
pixels — the smallest closed stroke the tap is inside, or the face — and fills
sink under the ink; **copy and paste** merge a turned or mirrored copy onto
another face in one undoable step, the turn being a whole step of the cell's
own symmetry; the **colour picker** goes past the twelve presets in hue, depth
and brightness, and the ink it makes is opaque and round-trips through the
draft file (`docs/face-designer.md`). **Drafts are on disk** — one file per
die, written after every stroke and read back when the die is opened — so a
drawing outlives the screen and each die keeps its own. That made the "start
over?" question unnecessary and it is gone: changing die no longer loses
anything. **Roll it** hands the tray the die being drawn — the die as its set
defines it, since nothing puts an atlas on one yet, and absent rather than dead
for a die plain notation cannot name (decision 31). The d4's
three-numbers-per-corner rule is **derived rather than checked** — a cell's
numbers are read from the corners it meets, so two cells sharing an edge cannot
be made to disagree along it.

**The export is built.** A drawing becomes an atlas at 256 px per cell in the
shape catalogue's own grid, with the cells nobody drew on left out so they stay
transparent, and a generated `diceset.toml` beside it; what decides where
things go is plain Kotlin and only the painting touches a `Bitmap`
(`docs/architecture.md`, decision 55). The package is validated before it is
written and again before its zip is offered, the licence is asked for first
(`8c`, in 4.4 above), and the file leaves through the share sheet the way an
exported collection does.

- [ ] The guide draws a dot where each number goes rather than the number: text inside a `Canvas` wants a measurer, and the value is legible on the strip meanwhile
- [ ] Quick mode: long-press a die on the roll screen for "Doodle this die"
- [ ] *Judgement, with a finger:* the bucket calls a stroke closed when its
      ends come back within 0.08 of the canvas of each other, and fills the
      smallest shape the tap is inside. Both numbers are guesses about how
      accurately somebody draws on glass; whether a loop somebody meant to
      close is treated as closed, and whether the region that fills is the one
      they meant, can only be told by drawing on a phone
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
tests wire, and one of them walks every menu destination and fails on **any**
placeholder — the list of screens that had not been written is empty now, so
the assertion is simply that none of them draws one. Both halves were checked
by putting the original bug back: removing the sessions screen's dispatch turns
the list into `[sessions]`.

Appearance, the accent, shake, haptics, sound, the default rounding, power
saving, the version and the repository link are all there, and each of them does
something. Haptics and sound are one section with two switches, because they are
one answer to one question and because with both off a roll records no impacts
at all — the pair is what the saving is measured against
(`docs/physics-and-rendering.md`, "Impacts, haptics and sound").

All three defaults now behave the same way, which was the point of the item
that used to be here: the default **set**, the default **table** and the active
**session** are each chosen where the thing itself is, remembered with the
settings, and fall back when what was chosen is not there. The set and the
table fall back while leaving the setting alone, so re-installing the package
restores the choice; the session falls back where a roll is *recorded*, because
a session deleted while another screen was in front would otherwise strand
every throw filed under it (`docs/statistics.md`, per session).

The **developer toggle** is the last thing on that screen, and it is the one
setting that is off on every install. It adds a debug overlay over the tray, a
Developer row in the menu, and behind that row an anomaly log and two ways of
throwing the last roll again. What it does *not* do is the point: the history
still has no replay and still never shows a seed, and the exports have no
column for one, because neither type has a field the toggle could unhide
(`docs/architecture.md`, decisions 13 and 56;
`docs/physics-and-rendering.md`, "Debug tooling").

## Step 5 — Physics and rendering on a real phone

**Reserved, and none of it can run on CI.** This is where the app either
convinces or does not: a roll has to look like dice landing, not like an
animation of a random number. Runs first as soon as 4.1 renders, then again
after every physics change.

### 5.1 Harness

**Built, and it runs on either tier.** `tools/harness.sh` rolls N throws
headlessly on the emulator or the phone, pulls back a JSON document — settle
time in steps and in milliseconds with median, p99 and worst case, corrections,
post-rest corrections, re-throws, forced settles, dice left standing on another
die, the deepest die–die overlap and the per-step wall time — and prints a
pass/fail table against the targets below (`docs/build-setup.md`, "The physics
harness"). The emulator is a minute away, so a regression need never reach the
phone.

Everything it *decides* is plain Kotlin in `simulation/harness` and is tested on
the JVM: what a run was asked for, the percentiles, the shares, the document and
the comparison. The device only rolls, times and writes two files
(`docs/architecture.md`, decision 53). It **fails** on the targets the engine
misses today, which is the plan being behind the check rather than the check
being wrong.

### 5.2 Fairness and determinism

**Done, on the Pixel 10a.** Every catalogue shape at 100,000 rolls: seven pass
chi-squared at p > 0.001 and their χ² sums to 57.63 against 55 degrees of
freedom, which is as close to "exactly as fair as chance predicts" as a number
gets. Every shape, the d18 included, keeps every face within 1 % of its share —
its worst is 0.389 %.

The **d18 is held to the worst-face bound and not to chi-squared**, which is a
decision taken rather than a check skipped: its resting basins are narrow enough
that the float32 hull's own rounding biases it, and Jolt stores hull points in
single precision whatever else is configured, so no single-precision engine can
do better for that solid. It is one shape by name in
`FairnessTest.HELD_TO_THE_FACE_BOUND`, its χ² is still printed every run, and
the app says nothing about it to the player — a number nobody can act on, about
a die fairer than the plastic one in their hand, is not worth a warning
(`docs/physics-and-rendering.md`, "The bar the d18 is held to").

- [ ] Identical outcomes for identical seeds across JVM, emulator and device — any divergence is a release blocker. The golden suite is the check and already holds for its ten cases on both ABIs; Step 5 is the same claim at ten thousand rolls and on a second phone
- [ ] Power-saving and rendered mode agree on every seed in the golden suite

### 5.3 Capacity and corner cases

- [ ] Counts 1, 2, 5, 8, 20, 40, 60 and the capacity limit: all settle, no NaN, no tunnelling. `tools/harness.sh -c <n>` is the run; each count is one invocation
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

- [ ] **Dice go 9 mm into each other, and the bar is 0.2 mm.** Measured on the Pixel 10a the first time the harness ran: 200 throws of 20 d20s, deepest die–die overlap **9.019 mm** against a target of 0.2, on dice 16 mm across. More than half a die. It is the number the plan asked for and nobody had ever had, and it is almost certainly the same fault as the correction rate below rather than a second one: dice are spawned or corrected into each other and the solver pushes them apart afterwards, which is what a 45 % correction rate looks like from the collision side. Prevention (5.5) is where it is fixed; this is where it is measured
- [ ] No tunnelling at maximum shake velocity — assert every body inside the box on every step, all roll long
- [ ] Dice driven into a corner at speed neither wedge nor jitter
- [ ] A settled pile is stable: no creep, no vibration, no slow slide
- [ ] Assert containment on *every step* rather than only at rest, at the capacity limit: the at-rest check is in `JoltBridgeTest` now, but a die that leaves the tray mid-roll and comes back would still pass it

### 5.5 Stacking and cocking — and no invisible hand

The two failures to hunt, per `docs/physics-and-rendering.md`:

- a die left resting on another, or on its edge; and
- **a die visibly moved after it stopped**, which is worse — it turns a roll
  into an arrangement in front of the player's eyes.

- [ ] 10,000 headless rolls at 20 dice and 10,000 at 60: **zero** dice at rest supported by another die. `tools/harness.sh -n 10000 -c 20` and `-c 60`; the count is in every run's JSON and its scorecard
- [ ] Fewer than 0.5 % of dice need any correction; **100 %** of those corrections land while the die is still moving
- [ ] **Zero** post-rest corrections. The harness asserts this; one occurrence is a bug, not a statistic
- [ ] Re-throws (the last resort) under 0.05 % of dice, and each one looks like a die being picked up and thrown again
- [ ] Settle time at 20 dice: median under 2 s, p99 under 4 s; the 12 s cap never reached in 10,000 rolls
- [ ] Tune prevention (spawn spread and stagger, dice-on-dice friction, throw energy, scale) until the numbers above hold without leaning on corrections. **Where it starts:** 20 d20s at the capacity rule's scale settle in 89–132 steps on the Pixel 10a, with 9 of the 20 corrected, 0–1 re-thrown and **zero** post-rest corrections. The last figure is the one that must stay at zero and does; the correction rate is 45 % against a 0.5 % budget, and bringing it down is what this task is
- [ ] *Measured, on the Pixel 10a:* 200 throws of 20 d20s, base seed 1. **43.55 %** of dice corrected against a 0.5 % budget, **3.50 %** re-thrown against 0.05 %. What passes on the same run is every honesty bar and every timing one: **zero** dice at rest on another die, **zero** post-rest corrections, zero forced settles, no throw near the twelve-second cap, median settle 0.81 s and p99 1.83 s against 2 s and 4 s, and a p99 step of 1.00 ms against the 8.33 ms a 120 Hz step has. The engine is fast and honest and leans on corrections far too hard, which is what the rest of this section is about
- [ ] **The corrections are visible at 100 dice, and they look like popcorn.** Seen on the Pixel 10a: dice stack against a wall and then *pop* apart to unstack, and individual dice jump to find a better spot. Every one of those lands while the die is still moving, so the honest rule holds and nothing touches a die at rest — but "it does not cheat" and "it does not look like it cheats" are different claims, and this is the second one failing. It is the 45 %-against-0.5 % correction rate above, seen rather than counted, and it is the argument for prevention over correction rather than a separate task
- [ ] *With the user:* frame-by-frame review of 50 recorded 20-dice rolls — nobody can point at the moment a die was helped

### 5.6 Feel — the user's call, not a metric

- [ ] Dice respond to a shake within ~100 ms, and they move the way the hand did — the tray itself never moves, because it is the screen (`docs/physics-and-rendering.md`). The direction and the dropped-force stutter are both fixed; what is left to judge is the *start*, which read as a lag on the Pixel 10a: the dice are already travelling fast when the shake begins to reach them, so the hand seems to be catching up with dice that left without it. The 100 ms start threshold and the spawn impulse are the two numbers in it
- [ ] The tumble reads as dice: bounce height, spin decay, dice rolling on an edge before toppling. **Not yet:** on the Pixel 10a the dice do not travel far enough and the tumble does not read as dice being thrown. Throw energy and spawn spread are where that is tuned (5.5), and this is the judgement that says when it is right
- [ ] *Judge the formula editor on the phone:* whether a dashed rule under the formula reads as "you can type here", and whether a keyboard over the lower half of the tray is right or wants the tray to shift up while the editor is open (`design/dInfinity.dc.html`, option 2a)
- [ ] The rim's shadow still looks wrong — the band across the top of the wall casts something that does not read as a rim. Lighting and the shadow map, not geometry, on present evidence
- [ ] Rendering polish — shader tuning, and the optimisation pass — is deliberately **last**: it is worth doing once the dice move the way they should, and worth nothing before that. Nothing above should wait for it
- [ ] **Do the haptics land?** They fire on real impacts only and the rule is asserted rather than tuned: a change in a die's speed that the step's own gravity explains is never reported, so a die sliding and a die at rest are silent by construction. What a phone has to answer is the *feel* — whether a die hitting the tray reads as a knock rather than a rattle, whether one die landing among twenty is still felt, and whether the 45 ms rate limit turns a hundred dice into a handful of distinct knocks or into one long buzz. Listen for: a single d20 landing, then `20d6`, then `100d6`
- [ ] **Do the five tables sound like their materials?** The sounds are generated rather than recorded (`docs/physics-and-rendering.md`), so this is the first time anybody hears them. Roll the same `5d6` on `felt-green`, `oak`, `dark-glass` and `plain` and say whether each reads as its surface; then roll `2d20` and `20d6` on one table and say whether the pitch difference between a big die and a shrunk one reads as dice of different sizes or as an effect. If a preset is wrong, the four numbers behind it are in `ImpactWaveform`
- [ ] **Does power-saving mode's second read as the roll?** There are no frames there, so the impacts are replayed across about a second after the dice have stopped. Whether that sounds like a throw that happened or like a sound effect played at you is the judgement — and whether a second is the right length
- [ ] Settled faces are legible at arm's length without zooming. The *size* is settled — 16 mm reads fine on the Pixel 10a — and the numbers are drawn now. What is left to judge is one number: `DieNumbers.FACE_SHARE`, how much of the room a face has a numeral takes up. Everything else about the size is solved from the face itself, so this is the only knob and it moves every shape at once. The d4's three-to-a-triangle (`CORNER_HEIGHT`) is the second question, and the d18 is the third — its kites are long enough that its numbers are a third the size of a d6's, which is the shape question already open below
- [ ] Power-saving feels instant and gives the same answer
- [ ] *Judge an exploding roll on the phone:* `8d6!` now throws each added die into the tray you are watching, one at a time, once the last has stopped. **It works, and was seen working**: `4d6!` came up `6 6 1 1 3 5` on the Pixel 10a, six dice on the tray for a throw of four, none of them on top of another, total 22. What is left is not whether it happens but how it reads. Three things need eyes. **Does the wait read as part of the roll** — a die lands, a beat, another die drops — or as the app having stalled? **Does the added die look thrown**, given that it is dropped from 25 mm straight down rather than hurled like the first eight? And **does it ever appear to pass through a die already lying there** on its way to a stop: it cannot touch one, because there is no body for the settled dice in its world, so if it *looks* as though it did, the drop point is too close and `ClearSpace` is the number to move (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds")
- [ ] *Judge a chain that fills the tray:* roll enough exploding dice that the tray runs out of clear floor. The sheet now says which of the two ways the chain ended — "Exploding stopped at 20 dice." or "The tray had no room for another die." — under the group it happened in (`docs/dice-notation.md`, "Evaluation", step 7). What is left is a person's call: whether the stop reads as a rule or as a bug, and whether a line under the group is where the eye actually goes

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

Branch coverage is **70.1 %** against a floor of 62, and roughly **seven in ten
of the branches it is missing are inside `@Composable` functions**. That is not
untested UI: the Compose compiler emits a skip branch for every parameter of
every composable so that a recomposition can be avoided, and a single-pass test
can only reach one side of each. A screen with twelve controls is a hundred
branches no test will ever take.

The drift that used to come with every new screen has stopped. Three levers do
it, and between them they have held the number flat or moved it up in each of
the last six pull requests:

- **Extract the decision, test the decision.** `FaceHistogram`, `Breakdown`,
  `CollectionExport`, `GraphBars`, `underlinesOf` and `crestPath` are all
  arithmetic that used to be inside a draw lambda. Each is now a plain object
  with plain tests, and a Canvas is the one place a test genuinely cannot go.
  A composable that returns a value rather than drawing one is the same trick:
  it is not skippable, so it costs no skip branch at all.
- **A shared component gets its own test.** `ui/common`'s formula field had
  152 branches and none of them covered, because three screens each tested
  *their use* of it and nobody tested the thing. It has its own tests now,
  including the recomposition one, and sits at 62 of 98. That was a real gap
  and it looked exactly like the mechanical one in a report — which is why the
  number is worth reading rather than merely watching.
- **A recomposition test takes the other side.** Drawing a screen once takes
  the "something changed" side of every skip branch it has; recomposing around
  it with nothing changed takes the other. The roll screen has the most
  parameters of any and had no such test.

The figures are reported in every PR description either way.

- [ ] Decide whether the floor should track the drift or stay where it is. It
      has not been moved since it was set, and moving a floor to make a check
      pass is the thing `.claude/CLAUDE.md` says not to do — so this is a
      question for a person, not a change to make quietly

## Open questions

- [ ] **Who measures a drawn frame?** The harness now paces a roll the way the
      screen does (`tools/harness.sh --frames`) and times `LiveRoll.advance`,
      but it has no surface, so what it measures is the simulation half of a
      frame; Step 5.7's "p99 under 16.6 ms" is about drawing. The harness
      therefore scores that row as **not measured** rather than as a pass, and
      the eye gets `--capture` instead (`docs/architecture.md`, decision 57).
      The alternative is a second, rendered harness — an instrumented test in
      `app/` or `render/filament` that opens a real surface, rolls twenty dice
      and reports its own frame times — which would answer Step 5.7 with a
      number rather than with a video. It is not small: it needs an activity, a
      Filament engine and a device, and none of its arithmetic could be reused
      without moving it into `:simulation:harness` first. Worth doing when 5.7
      is reached, or worth leaving to the eye and the systrace — a decision for
      a person

- [ ] **Where does a table look's texture say which package it came from?** A
      die's artwork now reaches the tray by a key of package and path
      (`docs/dice-sets.md`, "How an atlas reaches the tray"), and a
      `TableLook`'s `floor_texture` and `wall_texture` carry a path and nothing
      else — so they resolve to nothing and a table is drawn in its own
      colours, exactly as it was before. Chosen because the alternatives both
      reach a long way for a case nothing ships: the bundled package has no
      table textures and "My dice" has dice atlases only. The two ways out are
      putting the package id on `TableLook` itself, which makes every table a
      little wider for one field, and threading it through `Tray.table` and
      `Renderer.begin`, which puts it on the seam that is deliberately narrow.
      Worth deciding when a package that actually ships one exists
- [ ] **Where should a load-time texture report be shown?** `AtlasDecoder`
      produces the same `ValidationMessage` lines the validator does — a file
      that will not decode, an atlas with empty cells — and today nothing reads
      them: the die falls back to its labels and the lines are dropped. The
      obvious home is the set's details screen beside the validation report
      (design `6b`), which would mean the decode happening somewhere a screen
      can reach rather than only on the roll thread. Chosen to leave it for now
      because the fall-back is the behaviour either way and a report nobody
      asked for is not worth a second decode
- [ ] **Is a printed numeral meant to be 0.78 of its face, or 0.78 squared of
      it?** `FACE_SHARE` is applied twice on the way to a printed height: once
      to the box whose centre is solved clear of the edges, and once again to
      what is printed inside that box, so a numeral comes out at about 0.61 of
      the room its face has (`core/glyphs`' `LabelRoom.centred`). The stamp and
      "fill all with numbers" were built to match it exactly rather than to
      correct it, because the size on the Pixel 10a was judged with it in place
      and applying the share once would make every number on every die 28 %
      bigger overnight. The alternative is to apply it once and re-judge the
      fraction on the phone, which is Step 5.6's question anyway — the two
      should be answered together, and whichever way it goes the tray and the
      designer move together because they read the same number
- [ ] **Should a stamp be draggable after it is put down?** The prototype lets
      one be picked up and moved (`design/dInfinity.dc.html`, option `1v`); the
      app does not, because a stamp is a mark like a stroke and no other mark
      can be picked up — a drawing where one kind of mark moves and the others
      do not is two drawings, and the way to move one is undo and stamp again.
      The case for the prototype's answer is that a glyph is the one mark
      somebody places rather than draws, so landing it a finger's width off is
      a miss rather than a wrong drawing. It needs a phone to judge: whether
      re-stamping feels like correcting a typo or like losing work
      (`docs/face-designer.md`, "The stamp")
- [ ] **Should the anomaly log survive a restart?** It is in memory today,
      bounded to fifty entries, and goes when the app does — because an entry
      carries the seed that reproduces the roll, and a stored seed is a replay
      waiting to be written into a screen a player can reach
      (`docs/architecture.md`, decisions 13 and 56). Against that: an anomaly is
      supposed to be so rare that losing one to a restart may be losing the only
      one anybody ever sees. If it should persist, the question to answer first
      is where — a file the developer toggle owns and the ordinary app cannot
      read is a different thing from a table beside the history, and only the
      first of those is consistent with decision 13
- [ ] **Should a heavy die sound heavier?** An impact reports the change in a
      die's speed, which is impulse per unit of mass, and the sound follows that
      and the die's *size*. A die's `density` — which a set may put anywhere from
      balsa to brass — reaches the physics and does not reach the sound, so a
      brass d6 and a resin d6 of the same size land with the same noise. Adding
      it means giving the impact a mass, which means a hull volume the shape
      catalogue does not currently compute. Worth it or not is a judgement about
      how much anybody would notice (`docs/dice-sets.md`, "Size")
- [ ] `core/probability` hand-rolls its convolution and its FFT rather than
      taking a library, which `.claude/CLAUDE.md` names as a "complex part".
      The judgement was that the exact PMF *is* the domain logic and that
      pulling in a general maths library for ninety lines of transform is a
      worse trade than owning them — but it is a trade, and it is worth a
      second opinion
- [ ] Whether a die an explosion adds should be able to *collide* with the dice
      already down, as immovable furniture rather than as bodies that can move.
      Today it is thrown in a world holding only itself, which is what makes
      "nothing touches a die that has come to rest" true by construction — but
      it also means the added die cannot bounce off the pile, which is what
      would really happen on a table. Turning it on means the native bridge
      growing a second kind of body (static, never stepped, never reported),
      which cannot be tested on the JVM and cannot be verified without a phone.
      Worth deciding deliberately rather than by default
      (`docs/architecture.md`, decision 54)
- [ ] **Should an exported package carry a name, and where would it come from?**
      The `author` field is currently left out rather than filled: Android has
      no device user name an app can read without asking for contacts, and a
      field saying "You" would be a name on somebody else's phone. The
      alternatives are a text field beside the licence chooser on the "My dice"
      details screen, or a name kept in the settings and used by every export.
      Both are small; which one is wanted is a judgement about how much the
      export screen should ask for before it will share (`8c`,
      `docs/face-designer.md`)
- [ ] **A photo table is in the package before the tray can draw it.** "Use a
      photo" writes a valid, exportable `[[table]]` with its picture, and the
      tray shows it as its colours until something fills the `atlases` seam
      (Step 3, above) — which is the same state a *drawn* die's artwork is in,
      so the alternative was holding the feature until the renderer loads
      textures. I chose to ship it: the package, the validator path and the
      export are the hard parts and they are done, and the picture appearing is
      one seam away for dice and tables alike. Worth confirming that is the
      right order (`docs/tables.md`, "Your own photo")
- [ ] **How should a photo sit on the tray?** The prototype's upload sheet
      offers three fits — centre, fit width, fit height (`1u`) — and none is
      implemented: a photo table is written with `floor_tiling = [1, 1]`, which
      means the picture covers the floor once. A *fit* is a question about UV
      mapping and cropping, and nothing draws a table texture yet, so there is
      nothing to be right or wrong against. I chose the one answer that needs
      no renderer. Deciding it properly means choosing between cropping the
      photo at import (which loses pixels somebody chose) and mapping it at
      draw time (which needs the tray's aspect, and the tray's aspect changes
      with the phone's rotation)
- [ ] The face designer has no 3D preview in the prototype — "Roll it" is the preview. Confirm, then fix `docs/face-designer.md` (4.6)
- [ ] The dice picker remembers the last set per saved-roll group — confirm, then add to `docs/dice-notation.md`
- [ ] A collection imported from a git repository records nothing about where
      it came from, so there is no "check for updates" for one the way there is
      for a dice set: an import becomes rows in the database rather than a
      folder with a `.meta.json` beside it, and `RefResolver` is therefore not
      asked which commit the ref was at. If a collection should be updatable
      from its repository later, the commit is the thing to start recording
      (`docs/dice-notation.md`)
- [ ] A forge link that names a *file* inside a repository
      (`…/blob/main/goblins.dinfinity.json`) imports whatever is at the
      repository root instead, because the path after the ref is a dice set's
      subfolder and a collection is found at the root. Decide whether such a
      link should import the file it names
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
- [ ] The harness scores every run against the **same** settle bars — median
      2 s, p99 4 s — but Step 5.5 states them at twenty dice. A `100d4` run is
      therefore held to a twenty-dice bar, which is either exactly right (a
      roll is a roll, and a player waiting four seconds does not care how many
      dice they threw) or unfair to the worst case on purpose. The harness
      takes the first reading, and the bars are data, so changing it is one
      line in `HarnessTargets` — but which it should be is a decision
      (`docs/build-setup.md`, "The physics harness")
