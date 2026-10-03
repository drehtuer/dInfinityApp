# TODO

The implementation plan. Steps run in order; within a step the boxes are
roughly ordered too. **Finished items are deleted, not ticked** — git history
and `docs/STATUS.md` are the record, and a step that is wholly done is
removed. Keep the file a plan, not a diary.

Everything here builds the specification in `README.md`, `docs/` and the
prototype in [design/](../design/). Where a step says *device*, it cannot run
on CI (see `.claude/CLAUDE.md`). Steps 1 and 3, 4.7, 4.9 and 5.1 are done.

## Step 2 — CI

Done apart from four boxes that wait on other projects or are optional; none
blocks anything.

- [ ] Re-enable CodeQL's `java-kotlin` analysis once the bundle supports Kotlin
      2.4.20 — the matrix entry is commented out in `.github/workflows/codeql.yml`
      with the build steps kept ready. Bundle 2.27.0 (2026-09-16) still refused
      with `Kotlin version 2.4.20 is too recent`. **Do not trust the extractor's
      source tree**: `github/codeql` already has a `v_2_4_20` shim in
      `java/kotlin-extractor/src/main/kotlin/utils/versions`, but it has not
      reached a bundle the action downloads, and the repository's tags are the
      query packs' rather than the bundle's. The check is: put the matrix entry
      back on a branch, open a pull request and read the **Analyse
      java-kotlin** job
- [ ] *Optional:* add a `DEPENDABOT_METADATA_TOKEN` Dependabot secret so the metadata commit starts the checks by itself. Without it the automation still works, and the pull request shows an *Approve workflows to run* banner to press (`docs/build-setup.md`)
- [ ] Drop `VerifyDeviceTestResultsTask` and the `ignoreFailures` on `connectedDebugAndroidTest` once AGP stops failing runs on devices whose adb serial contains a colon (`docs/build-setup.md`)
- [ ] Drop the note about detekt's `ReportingExtension.file(String)` deprecation from `docs/build-setup.md` once detekt releases a build that stops calling it. It is the only warning left on `./gradlew help --warning-mode all`, it fires once per module, and Gradle 10 removes the method — so the day it becomes an error is detekt's deadline, not ours

## Step 4 — Screens

One section per screen. Each is a vertical slice: state, UI, tests, and the
device check it needs. The design option ids (`1a`, `9c`, …) are the labels on
the canvas — open [design/](../design/) beside the code. Every screen is built
and connected; what is below is what is left.

Every change to a screen follows the same four steps:

1. **State** — a `ViewModel` over the foundations. No logic that belongs in
   `core/` leaks into the screen.
2. **UI** — Compose, Modernist tokens, matching the design option. Light and
   dark, phone widths from 360 dp up.
3. **Tests** — unit tests for the state machine; Robolectric/Compose tests for
   rendering, interaction, empty and error states; accessibility (labels,
   touch targets, TalkBack order).
4. **Docs** — update the matching document in the same PR if behaviour,
   limits or defaults move.

### 4.1 Roll screen — `feature/roll`

Design `1a`–`1j`, `2a`, `3a`–`3c`, `4a`, `4b`, `6d`, `6f`, `9a`, `9c`, `1z`.
Spec: `docs/dice-notation.md`, `docs/tables.md`,
`docs/physics-and-rendering.md` ("What is drawn over the table").

**From the design pass of 2026-09-17:**

- [ ] **Mark the dice of a later pass** — 4 dp accent-700 outline and a
      `pass 2` label in the `dropped` slot — so a total counting twenty dice
      over a table holding three explains itself on the felt
- [ ] **Stagger the spawn**, 85 ms between dice, with the result sheet waiting
      `min(2400, 950 + (n − 1) × 85)` ms for the last landing. The prototype's
      collision shove is **not** to be ported: a settled die moved by another
      die is physics, a settled die moved by code is the invisible hand

**Wire the hand re-throw:**

- [ ] **Wire the one-finger touch to a hand re-throw.** Everything underneath
      it is decided and built: `TrayPick` (`render/filament`) says which die a
      finger is on, `PickUp` (`core/notation`) says which dice a hand may go
      near (a group carrying `!` or `r n` offers none), the throw is the one an
      explosion already makes (`ThrowSpec.among`), and the history rule is
      decided — the die's history keeps every throw, the roll's keeps the sum
      (`docs/physics-and-rendering.md`, "Picking a die up and throwing it
      again"). The camera takes two fingers and consumes nothing while only one
      is down, so a single finger reaches the tray unclaimed. Settle "Does a
      tap on the table roll?" (Open questions) first: they want the same finger
- [ ] **Decided, not built: braced notation, so a set's own dice can be typed
      and picked.** Plain notation spells `dN`, `d%` and `dF`, so `skull-d6`
      has nothing a formula could carry and `DicePicker.offeredBy` filters the
      row to `StandardDieIds` for that reason. Decision 31 refused
      `brass:skull-d6kh1` because a die id and a modifier are made of the same
      characters; **braces close the id before the modifiers start**, and
      braces are the one bracket the grammar does not already use.

      | written | means |
      | --- | --- |
      | `3{skull-d6}kh1` | three of the die whose own id is `skull-d6`, keep highest |
      | `3{brass:skull-d6}kh1` | the same, said to come from the set `brass` |
      | `3{skull:d6}kh1` | the `d6` **of the set `skull`** — valid, and a different thing |

      The colon inside the braces is the existing set separator
      (`brass:1d20`). A braced id is **lexed** without knowing whether it
      exists — the parser may not consult installed sets — and resolution is
      left to `DieResolver`. Cost: notation grows a second way to name a die,
      which touches the grammar, `FormulaParser`, `NotationReference` (every
      example parsed by a test), the breakdown, the history, saved rolls and
      collection files already written

- [ ] **The welcome and the result sheet share the bottom edge.** The
      first-launch screen is a full-screen takeover whose buttons run to the
      bottom, so a roll thrown from the saved-roll strip while the welcome is
      up puts the breakdown under `Add somebody else's dice`. Nothing is lost
      (any of its buttons dismisses it), but the screen should probably not
      draw a result behind a takeover at all

**Implementation notes recorded only here:**

- The result pull-up is a component of its own rather than an extension of
  `ui/common`'s modal `Sheet` (a `Dialog` with a scrim); it is
  `Modifier.draggable` over an `Animatable`, not the experimental
  `AnchoredDraggableState`, with the decisions in `SheetSlide`.
- **A pull-up's test tag goes inside `Modifier.offset`** — outside it, a
  semantics node reports where the sheet would be had it never slid, and every
  position assertion quietly passes.
- **The arrival waits for the measurement inside the coroutine**, not in the
  effect's key: keyed on "measured yet", the slide started on the first run
  and was cancelled by the second, parking the sheet below the edge for good.
- The formula drawer slides, and only horizontally — no fade, no expansion,
  because the fault being fixed was a menu that dropped down.

**Judgement on the phone** (each needs eyes or a hand, not a test):

- [ ] *A second shake* at dice still in the air keeps them moving — it is
      **more of the same roll**, not a throw that replaces it (decided). Does
      that read as the dice answering the hand, and can a roll now be kept
      going longer than anybody wants? (`docs/physics-and-rendering.md`,
      "Shake input")
- [ ] *The pinch and the pan:* two fingers pan, the pan limit grows with the
      zoom so at `TrayView.CLOSEST` the middle of the screen reaches the corner
      of the floor, and a pinch happens about the fingers. Is four times in far
      enough to settle an argument about a face and near enough that the table
      has not gone; does a two-finger drag read as moving the table; does
      pinching into a corner go where the fingers are; and **does the wall
      rising past the floor's edge at the far end of the pan read as the table
      or as having fallen off it** (`docs/physics-and-rendering.md`,
      "Rendering")
- [ ] *The result pull-up:* does the result arriving by itself read as the
      sheet answering the roll, is the grip where a thumb already is, and does
      the parked sheet leave enough felt to see a die at the bottom edge
- [ ] *Two pull-ups on the bottom edge:* do two stacked grips read as two
      things, is `SAVED ROLLS` needed on the lower grip, and does a result
      arriving while the saved rolls are up read as the total answering the
      roll or as the strip being snatched away
      (`docs/physics-and-rendering.md`, "Two pull-ups, one bottom edge")
- [ ] *The formula tab:* does a tab at the right edge read as "the formula is
      in there", does the slide feel like a drawer, and — the one that matters —
      does **not being able to see the formula** cost more than the felt it
      gives back (`design/dInfinity.dc.html`, option 2a)
- [ ] *The empty tray* now says nothing at all (the welcome still does on a
      fresh install). Calm, or broken? (`docs/design-handover.md`, question 6)
- [ ] *The dice pull-down:* does a shut menu read as "the dice are in there",
      does the count on the head answer that, and does the scrolling row read
      as "more dice over there" or as "the d20 is missing"
      (`design/dInfinity.dc.html`, option 1h)
- [ ] *The tray with no shadow of its own:* the wall and rim no longer cast,
      the dice still do. Does the join between wall and floor still read as a
      corner? The darkening there is SSAO and cannot be turned off per
      renderable (`docs/physics-and-rendering.md`, "Rendering (normal mode)")
- [ ] *Power-saving:* the screen it leaves is the formula, the dice menu and a
      total with nothing above them; the design shows a short progress
      indicator and a result sheet in the tray's place (`1z`). "Instant" or
      "broken"?
- [ ] *Device:* the whole of Step 5 hangs off this screen

**Done when** every example in `docs/dice-notation.md` can be typed, rolled
and read here, and the same seed gives the same result with the renderer on
and off.

### 4.2 Outcome graph — `feature/graph`

Design `1k`–`1m`, `2c`, `7a`. Spec: `docs/probability.md`.

- [ ] *Judge the chart on the phone:* whether a hundred and ten bars at three dp each reads as a distribution or as a smear, and whether the ±1σ band behind the bars is visible enough to mean anything in both themes
- [ ] The ledger (`1m`) and the stepped area (`1l`) are alternative presentations of the same numbers; `1k` is the default and the other two are not v1

### 4.3 Saved rolls — `feature/saved`

Design `1n`–`1p`, `1r`, `6e`, `7b`, `9b`, `9d`, `9f`, `9g`. Spec:
`docs/dice-notation.md` (Saved rolls).

- [ ] The editor offers ten emoji as icons. The design has an icon pack; whether one is worth drawing, or emoji is the answer, is a decision rather than an omission (`docs/dice-notation.md` says "an emoji or a name from the built-in icon pack")
- [ ] Auto-scroll while dragging a row past the top or bottom of the list is
      not built — moving a row further than the list shows takes a second drag
      (the grip's *move up* / *move down* actions work without a drag). Worth
      doing if a long list turns out to be tedious
- [ ] `design/dInfinity.dc.html`'s caption for option `1r` still lists a
      favourite among the editor's fields, and `1o`/`1p` still describe
      favourites-first tiles. The screens (`dInfinityPhone.dc.html`) are right;
      these captions want fixing in the design project and re-importing rather
      than by hand here
- [ ] *Judge the drag on the phone:* whether a row follows the finger closely
      enough to feel picked up rather than nudged, and whether the grip is
      where a thumb expects it on a list the length of a character sheet

### 4.4 Dice sets — `feature/sets`

Design `1s`, `1t`, `5a`, `6a`, `6b`, `8c`, `9h`, `9i`. Spec: `docs/dice-sets.md`.

- [ ] **Decide the built-in set's `size_mm`.** `size_mm` is the width across
      the corners (`docs/dice-sets.md`, "Size"), so the built-in 16 mm d6 is a
      9.2 mm cube and the Physical block honestly prints **0.9 g** where the
      design pass quotes 4.2 g — what a 16 mm-*edge* d6 weighs. Whether the
      built-in set should declare something nearer `size_mm = 28` changes
      every built-in die's mass and how many fit a table, so it is a decision
      to take on purpose rather than a constant to tweak

### 4.5 Table picker — `feature/tables`

Design `1u`, `9j`. Spec: `docs/tables.md`.

- [ ] *Judgement, on a phone:* whether the thumbnail reads as a table at 44 × 64 dp. The camera is as close as a pinch may go, in the far corner — but whether two walls, a rounded corner and a d20 in a box that size is a *picture* or a smudge is not something a test can say (`docs/tables.md`, "Thumbnails")

### 4.6 Face designer — `feature/designer`

Design `1v`, `4c`, `8d`. Spec: `docs/face-designer.md`.

- [ ] **The canvas draws one kite for two different ones.** `FaceOutline.Kite`
      is used for both the d10 and the d18, whose kites are differently
      proportioned, so the exporter covers instead of fitting: the covering
      size is 1.20× the best fit on a d10 and 1.37× on a d18 (exact on every
      other shape), leaving a drawing a little large and clipped at the tip.
      Closing it means the canvas outline being the face's own polygon, which
      needs `FaceOutline` to stop being one enum value per family — and
      changes the shape somebody draws on, so drafts on disk are masked
      differently (`docs/face-designer.md`, "Export details")
- [ ] **Whether the Solid tab should draw pen strokes too**, as thin filled
      outlines rather than lines of a width. Costs a stroke turned into a
      polygon per mark per face per frame; buys a hand-drawn face that is not
      blank on the tab meant to show it. Today the tab says what it does not
      draw instead
- [ ] **More than one personal set.** `Save to set` opens a sheet over the
      writable sets, but `MinePackage.ID` is fixed to `mine` and `MineSets` is
      built on one folder, so the sheet lists exactly one set and nothing names
      a new one. What is left is the *model*: an id per personal set, a
      `MineSets` per folder, drafts keyed by set as well as by die, and an
      export and a physical record each. A new set starts at the average
      weight, translucency and size and is in the list, the picker and
      notation immediately (`docs/face-designer.md`, "Save to set")
- [ ] *Judgement, with a finger:* the bucket calls a stroke closed when its
      ends come back within 0.08 of the canvas, and fills the smallest shape
      the tap is inside. Is a loop somebody meant to close treated as closed,
      and does the region they meant fill?
- [ ] *Judgement, on a screen:* the Solid tab has never been looked at. Does
      a d20 turning every sixteen seconds read as a die being turned over or as
      fidgeting; is one lamp and a floor enough to tell twenty triangles
      apart in both themes; does the 4 dp accent outline find the selected
      face edge-on at the back; is a stage-width per 176° the rate a finger
      expects; and does a face carrying only background and number read as a
      face somebody drew or as one that lost its drawing
- [ ] *Judgement, on a phone:* a drawing reaches the tray the right way round
      now (#327) and nobody has seen one there since. Does a finger drawing
      read as *their drawing* at tray distance; does a d10's (painted 1.2×
      large and clipped) read at all; do eight 44 dp glyphs in a wrapping row
      read as tools without words, or do the bucket and the stamp need a
      caption; is a 52 dp face thumbnail a face or a grey square
- [ ] *Judgement, on a phone:* the way back from a test throw. Does the banner
      over the tray read as "you are testing this" or as something in the way,
      and is coming back to the designer on the die being tested what a hand
      expects (`docs/face-designer.md`, "The way back")

### 4.8 History — `feature/stats`

Design `1x`. Spec: `docs/statistics.md`.

- [ ] **The face histogram prints a face's value, not its label.** A dF's
      rows read `-1`, `0`, `1` where the die says `−`, blank and `+`, and
      TalkBack says "Face -1 came up three times" (`StatsScreen`,
      `FaceHistogram`'s `FaceBar`). Tallies are keyed by face *value* and
      `FaceBar` carries no label, so the fix is a label plumbed from the
      installed die at read time — and a decision about the CSV and JSON
      exports (`HistoryExport`, `DiceExport`), which write the value too. The
      value is right for a machine; a screen is a different question

### 4.10 Settings and menu — `feature/settings`

Design `1q`, `1y`, `2d`. Spec: `README.md`, `docs/architecture.md`.

- [ ] **The design's Settings has two rows the app does not.** *Example dice
      set on GitHub* is nowhere in the app, and *Reset statistics for
      «session»…* is on the Statistics screen, next to the thing it resets.
      Whether Settings is where a person looks for them decides whether the app
      grows two rows or the prototype loses them
- [ ] **The accent's hex is on a line of its own, not at the end of the
      sentence.** The design writes it inline in tabular numerals — an
      `AnnotatedString` with a `fontFeatureSettings` span. Worth doing when the
      accent block itself is reconsidered: it is the one setting that is not a
      row, because a four-column grid of swatches has nothing left of itself in
      half of one, and doing only the hex would leave it half converted
- [ ] **Judgement, on the phone:** a row whose control has no room beside the
      text stacks the control under the sentence. Nothing on the Pixel 10a's
      Settings should reach it, so look at a **split screen**: does a stacked
      row still read as one setting?

## Step 5 — Physics and rendering on a real phone

**None of it can run on CI.** This is where the app either convinces or does
not: a roll has to look like dice landing, not like an animation of a random
number. Runs again after every physics change. The harness is
`tools/harness.sh` (`docs/build-setup.md`, "The physics harness").

- [ ] **A test that sees the printed numbers.** They were drawn reflected for
      the whole of `v0.1.0` (`MaterialBuilder.flipUV` defaults to `true` and
      was turning `v` a second time), and the fix was confirmed by photograph.
      A reflection in `u` and one in `v` differ by a half-turn and a die lands
      at an arbitrary orientation, so a screenshot cannot tell them apart.
      **The instrument:** an instrumented test in `render/filament` that puts
      one die where the camera is aimed, turns it so a chosen face's normal is
      `-forward` and its texture-up is the camera's up, reads the frame back
      through `Snapshot`, and asserts the ink is heavier in the half of the
      frame `DieNumbers.fieldOf` says is the heavier half of that cell. It
      needs no golden image and no projection arithmetic (decision 40), and it
      needs a way to build a quaternion from two orthonormal frames, which
      `Quaternion` does not have yet
- [ ] Delete `TroubleCheck` from `simulation/jolt`'s `RollLoop.kt`: it is the
      ladder's rung-2 test and nothing has called it since counting replaced
      the ladder

### 5.2 Fairness and determinism

Fairness is done on the Pixel 10a (the d18 held to the worst-face bound,
`docs/physics-and-rendering.md`, "The bar the d18 is held to"), and
`ModesAgreeTest` holds power-saving and drawn modes to the same faces.

- [ ] Identical outcomes for identical seeds across JVM, emulator and device — any divergence is a release blocker. The golden suite is the check and already holds for its ten cases on both ABIs; Step 5 is the same claim at ten thousand rolls and on a second phone

### 5.3 Capacity and corner cases

- [ ] **Re-run the count sweep under counting.** The last sweep (1–100 dice,
      200 rolls each up to 20, 60 above) was taken under the old correction
      ladder. What held then, and must still: zero dice at rest on another
      die, zero post-rest corrections, no NaN, no die through a wall, p99 step
      3.09 ms at a hundred dice against 8.33. What did not: one roll in sixty
      at 40 and at 100 dice reached the twelve-second backstop, and re-throws
      climbed from 1 % at one die to 6.3 % at a hundred
- [ ] **`100d4` does not reliably settle.** The d4 cannot rest flat on another,
      so a heap of them has no stable packing: twenty-four seeds in
      `JoltBridgeTest` show five reaching the twelve-second backstop, where the
      roll gives up rather than invents an answer (since #319 without taking
      the app with it). Nothing is touched after rest on any seed; the bound
      in the test is today's worst case written down, not a target
- [ ] **A hundred coins stack: four to ten of them, on every seed tried**
      (`CornerCasesTest`, bounded at today's worst). A coin landing on a coin
      is *stable* there, where a cube or an icosahedron rolls off. The test's
      comment still explains it by the old ladder's rung 3; re-measure under
      counting, where a stacked coin is not read but thrown again, and update
      the bound and the comment
- [ ] **Decide what a tilted phone should mean.** Deferred. The table is horizontal and the gyroscope no longer turns the world, which stopped the dice pouring into a wall — but "tilt the phone and the dice slide" was a real idea. The direction is still recorded with every sample. The three answers: gravity always straight down and only the hand moves the dice; anchor to `TYPE_GRAVITY` and accept that a phone held upright pours everything to the bottom wall; or keep a tilt and clamp it so a tray can lean without becoming a chute
- [ ] **A shake along the phone's long axis still drives the dice into one end.** Seen as dice stuck at the bottom after a vertical shake. The hand's own force points that way, and a hundred dice pushed at one wall have nowhere else to be. Whether that is right (it is what a hand does) or wants shaping is a 5.6 question with a phone in it
- [ ] *With a hand:* shake **vertical** and **upside down** for real.
      `ExtremeInputTest` covers the machine-drivable extremes; the screen holds
      its shape rather than its rotation now, so `PhoneAxes` is told the truth
      upside down, and a quarter turn is still refused
- [ ] **Decided, left alone:** `FLOOR_SHARE` shrinks and `MIN_SCALE` refuses;
      at the engine's cap of a hundred 16 mm d6 the shrink is 0.62, and
      `MIN_SCALE` would not begin refusing until **241 dice**. Both are
      `TableCapacityTest` assertions, so raising `MAX_DICE` past 241 is noticed
      — it would make the scale floor live for the first time

### 5.4 Collisions

- [ ] **Dice go 5 mm into each other, and the bar is 0.2 mm.** 9.019 mm at
      first measurement, **5.04 mm** after two collision sub-steps and a throw
      that tapers with the dice count (2026-09-18). Part of what is left was a
      spawn bug — a pass throwing several dice again dropped each at a point
      drawn blind, so two could start inside each other — fixed in #321 and
      **not re-measured on the phone**. Re-measure first; then what remains is
      the solver's discrete-detection error. Measured under the ladder at 20
      d20: four sub-steps took the overlap to 3.18 mm, eight to 1.58 mm
      (sixteen is worse, 5.97 mm), at a p99 step of 0.85–1.32 ms — but both
      made a shaken throw pack into one end (8 and 13 of 16 seeds heaped,
      against 2), because the solver's own error was part of what spread it.
      Four and eight are untried against the throw as it is now

### 5.5 Stacking and cocking — and no invisible hand

There is no correction at all: a roll counts the dice that can be read, lifts
them only when something has to be thrown again, and throws the rest until
everything is counted (`docs/physics-and-rendering.md`, "Avoiding stacked and
cocked dice"). Measured on the Pixel 10a over 2,000 rolls of 20d20: no die at
rest on another, no post-rest correction, **0.000 %** corrected, median settle
0.78 s, p99 1.45 s, nothing reaching the backstop, re-throws **2.20 %**.

- [ ] **Decide what the re-throw budget means now.** 2.20 % against a 0.05 %
      bar. The bar was written when a re-throw was the last resort after two
      rungs of correction; re-throwing **is** the mechanism now. What is worth
      bounding is probably how *long* a roll takes and how many passes it
      needs, both of which the harness measures
- [ ] **Does an automatic re-throw count as a throw in the statistics?** The
      *hand* re-throw is decided — the die's history keeps every throw, the
      roll's keeps the sum — but a counting re-throw is the app's doing, and a
      `d6` that took four passes to be read would contribute four faces to its
      fairness figure. That is either exactly right (it landed four times) or
      a bias worth naming (it landed four times *because it was hard to read*)
- [ ] **Repeat the 10,000-roll runs at 20 and 60 dice under counting.** Under
      the ladder: 200,000 dice at 20 put none at rest on another and touched
      none after rest, but three rolls in ten thousand reached the cap; at 60,
      29 dice in 600,000 were left standing, two thirds of them in rolls that
      ran out of time. Zero post-rest corrections in 800,000 dice either way
- [ ] Tune prevention (spawn spread and stagger, dice-on-dice friction, throw
      energy, scale) to bring the re-throw share and the overlap down. Tried
      under the ladder and **neutral**: five spawn height bands rather than
      three
- [ ] *With the user:* frame-by-frame review of 50 recorded 20-dice rolls — nobody can point at the moment a die was helped

### 5.6 Feel — the user's call, not a metric

- [ ] Dice respond to a shake within ~100 ms, and they move the way the hand did — the tray itself never moves, because it is the screen (`docs/physics-and-rendering.md`). The direction and the dropped-force stutter are both fixed; what is left to judge is the *start*, which read as a lag on the Pixel 10a: the dice are already travelling fast when the shake begins to reach them. The 100 ms start threshold and the spawn impulse are the two numbers in it
- [ ] The tumble reads as dice: bounce height, spin decay, dice rolling on an edge before toppling. The device session said **better, and now too fast**; the physics has no time left to give, so the roll is *shown* over more wall clock than it takes (`RollPace.WATCHED`, `docs/physics-and-rendering.md`, "The simulation clock"). Open until an eye says the tumble reads
- [ ] **Is the paced roll the right speed?** `RollPace.WATCHED` is 0.5 — a 20d20 throw the solver finishes in 0.81 s takes about 1.6 s to watch — one constant meant to be changed on this judgement. **Does a roll now read as dice landing**; **is the answer still prompt** (roll `1d20` a dozen times — does the wait annoy?); and **does the change of speed when the hand lets go read as intended** or as a stutter. If it wants easing, that is a second constant, added on evidence
- [ ] **Does a second shake at tumbling dice still answer instantly?** A live sample is filed on the step the world is about to take, so the slow motion should not delay it. Shake, let go, and shake again before the dice stop: the dice should jump on the hand, not a beat later (`docs/physics-and-rendering.md`, "Shake input")
- [ ] *Judge the formula editor on the phone:* whether a keyboard over the lower half of the tray is right or wants the tray to shift up while the editor is open (`design/dInfinity.dc.html`, option 2a)
- [ ] **Do the haptics land?** They fire on real impacts only — a speed change the step's own gravity explains is never reported. Does a die hitting the tray read as a knock rather than a rattle, is one die landing among twenty still felt, and does the 45 ms rate limit turn a hundred dice into distinct knocks or one long buzz? Listen for: a single d20, then `20d6`, then `100d6`
- [ ] **Do the five tables sound like their materials?** The sounds are generated, so this is the first time anybody hears them. Roll the same `5d6` on `felt-green`, `oak`, `dark-glass` and `plain` and say whether each reads as its surface; then `2d20` and `20d6` on one table — does the pitch difference read as dice of different sizes or as an effect? The four numbers behind each preset are in `ImpactWaveform`
- [ ] **Does power-saving mode's second read as the roll?** There are no frames, so the impacts are replayed across about a second after the dice stop. A throw that happened, or a sound effect played at you — and is a second the right length?
- [ ] Settled faces are legible at arm's length without zooming. The size (16 mm) is settled; what is left is `DieNumbers.FACE_SHARE`, the one knob that moves every shape at once — answered together with "0.78 of its face, or 0.78 squared" (Open questions). The d4's three-to-a-triangle (`CORNER_HEIGHT`) is the second question, and the d18's small numbers the third
- [ ] Power-saving feels instant and gives the same answer
- [ ] *Judge an exploding roll on the phone:* `8d6!` **waits** between links of the chain for a shake. It works (`4d6!` came up `6 6 1 1 3 5` on the Pixel 10a, six dice, none stacked, total 22). **Does the wait read as part of the roll** or as a stall; **does the added die look thrown**, dropped from 25 mm rather than hurled; and **does it ever appear to pass through a die already lying there** — it cannot touch one, so if it looks that way the drop point is too close and `ClearSpace` is the number to move (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds"). Two ways it really did are closed (#321)
- [ ] **Is the roll different on the first throws of a session?** The device session saw the re-roll overlap and the chain throwing itself "only in the beginning, correct after a few throws". The overlap had a cause that is not first-throw-specific and is fixed, and no path in the app throws an earned die without `RollPresenter.roll`. What is unexplained is the *pattern*. The candidate: at the start the roll thread is busy building the Filament engine and material, decoding the atlas and loading Jolt, so the frame clock **drops steps** (`FrameClock.MAX_STEPS_PER_FRAME` is 4) — a materially different throw from a warm one. Shake samples no longer strand on a dropped step (`ShakeDriver.add`), but the steps are still dropped. `LiveRoll.droppedSteps` counts them and nothing on screen shows it: put it on the debug overlay, then throw `4d6!` three times on a cold start and read the number
- [ ] **Does a die the picker adds read as being dropped on the table?** It falls 60 mm and stops in about a fifth of a second (`docs/physics-and-rendering.md`, "The dice waiting to be thrown"). Does a single tap read as a die landing or as one blinking in late (`FallingIn.DROP_HEIGHT_MM` is the one number to turn); does tapping a d6 eight times look right, with the dice already down not twitching; and does a falling die ever appear to pass through one standing (it falls straight down its own column of floor, so the clearance is the number to move)
- [ ] *Judge a chain that fills the tray:* the sheet says which way the chain ended — "Exploding stopped at 20 dice." or "The tray had no room for another die." — under its group (`docs/dice-notation.md`, "Evaluation", step 7). Does the stop read as a rule or as a bug, and is a line under the group where the eye goes?
- [ ] Rendering polish — shader tuning, and the optimisation pass — is deliberately **last**: worth doing once the dice move the way they should. Nothing above should wait for it

### 5.7 Performance on the Pixel 10a

`MemoryTest` holds the native heap across 500 rolls (1,440 bytes left
behind).

- [ ] **A rendered harness, and a frame-rate readout on the phone** (decided).
      The headless harness's paced run times `LiveRoll.advance` with no
      surface, so it scores the simulation half of a frame and says so
      (`FrameTimes.drawn`). Needed: an instrumented test that opens a real
      surface, rolls twenty dice and reports its own frame times, with its
      arithmetic in `:simulation:harness`; and a **setting that puts the frame
      rate on screen** for a person holding the phone
- [ ] 60 fps sustained at 20 dice, frame time p99 under 16.6 ms; at least 30 fps at the capacity limit
- [ ] Battery cost of 100 rolls measured, then written into `docs/physics-and-rendering.md` as the budget

**Done when** every target above is met on the Pixel 10a and the user agrees
the dice look right. Numbers that turn out wrong become the new numbers in
`docs/physics-and-rendering.md` and `docs/tables.md` — the docs follow the
device, not the other way round.

## Step 6 — v1 release

- [ ] Accessibility: **walk every screen with TalkBack on a real phone.** The
      rules, the labels and the measured contrast are done and tested
      (`docs/architecture.md`, "Accessibility"); what a test cannot answer is
      whether the reading *order* is sensible, whether the announcements are
      the right length when they arrive one after another, and whether the tray
      is comprehensible with the screen curtain on
- [ ] Localisation, **once a second language exists** — nothing is wrong
      today, v1 ships English. Every word a screen says is a string resource and
      `verifyTextIsAResource` keeps it that way (`docs/architecture.md`, "Text a
      person reads"); two things are exempted by file name in their build
      scripts and need deciding first:
      - **Does a refusal keep its words, or become a reason?** The sentences
        that say why something was refused are assembled across modules that
        may not depend on Android — `dicesets/format`'s and `dicesets/install`'s
        `ValidationMessage`, `core/collection`'s reader, and the `app/` helpers
        that read a file or fetch a URL. Either every `ValidationMessage` gets a
        typed reason with arguments that the screen phrases (correct,
        translatable, and a change to every producer and every test asserting
        on words), or a refusal speaks English in a translated app. The same
        is true of `core/notation`'s `NotationReference`, the notation screen's
        whole content
      - **Is percent typography text?** `"0 %"`, `"< 0.1 %"` and `"%.1f %%"`
        in `feature/graph` and `feature/stats` contain no words, and
        `String.format` already follows the locale's decimal point — but the
        space before `%` and the `"—"` for no value differ by language. Moving
        them means `percent()` taking a `Resources`, which costs its JVM tests
        a context
- [ ] Play Store metadata, screenshots taken from the real app, privacy statement (no analytics, nothing leaves the phone)
- [ ] Tag `v1.0.0`

## After v1

Written down so the format need not change later. Not v1 scope.

- [ ] More catalogue solids, `rhombic-triacontahedron` (d30) first
- [ ] Author-supplied convex meshes, with the fairness preview they require (`docs/dice-sets.md`, "Shapes after v1")
- [ ] **Author-supplied materials.** Compiling materials at runtime (`docs/architecture.md`, decision 46) means a set *could* ship its own `.mat` — iridescent dice, a proper glass d20, a brushed-metal table. v1 does not allow it because a shader is code that runs on the GPU, and "the app never runs anything from the repository" is a rule of the format (`docs/dice-sets.md`). Turning it on needs a decision about what a stranger's shader may do — a compile that never finishes is a hung GPU, and a driver is a large attack surface — plus a limit on compile time, a cap on instruction count, and a refusal as legible as the validator's others. Until then a set varies a material's *parameters*

## Coverage

Branch coverage is **~71.4 %** against a floor of 62, and roughly seven in ten
of the branches it is missing are inside `@Composable` functions — the skip
branch the Compose compiler emits per parameter, which a single-pass test can
only reach one side of. What has held it flat or rising: extract the decision
from the draw lambda and test it as plain Kotlin; give a shared component its
own tests; add a recomposition test to take the other side of every skip
branch. The figures are reported in every PR description.

- [ ] Decide whether the floor should track the drift or stay where it is. It
      has not been moved since it was set, and moving a floor to make a check
      pass is the thing `.claude/CLAUDE.md` says not to do — so this is a
      question for a person, not a change to make quietly

## Open questions

### Rendering and physics

- [ ] **Why does a six-level cubemap not upload?** The room a polished die
      reflects is a 32-pixel cubemap generated on the device (`render/filament`'s
      `RoomLight`). Level nought (24,576 bytes, one `setImage`) is accepted.
      Level one is refused: `buffer overflow: (size=3072 …) smaller than
      specified region {{0,0,0},{16,16,6}}`, where the buffer is 6,144 bytes —
      `RoomLightUploadTest` asserts both halves on the device. The texture ships
      with one level, which costs a few per cent of one channel on a sheen.
      Worth an hour with Filament's JNI source
- [ ] **Is a printed numeral meant to be 0.78 of its face, or 0.78 squared of
      it?** `FACE_SHARE` is applied twice — to the box solved clear of the
      edges, and again to what is printed inside it — so a numeral is about
      0.61 of its room (`core/glyphs`' `LabelRoom.centred`). The stamp and
      "fill all with numbers" match it, because the size on the Pixel 10a was
      judged with it in place; applying it once makes every number 28 % bigger.
      Answer together with Step 5.6's legibility question; the tray and the
      designer move together either way
- [ ] Whether a die an explosion adds should be able to *collide* with the dice
      already down, as immovable furniture. Today it is thrown in a world
      holding only itself, which makes "nothing touches a die that has come to
      rest" true by construction — but it cannot bounce off the pile. Turning
      it on means the native bridge growing a second kind of body (static,
      never stepped, never reported), which only a phone can verify
      (`docs/architecture.md`, decision 54)
- [ ] **Should a heavy die sound heavier?** The sound follows the change in a
      die's speed and its *size*; `density` reaches the physics and not the
      sound, so a brass d6 and a resin d6 land with the same noise. Adding it
      means giving the impact a mass, which means a hull volume the shape
      catalogue does not compute (`docs/dice-sets.md`, "Size")
- [ ] The harness scores every run against the **same** settle bars — median
      2 s, p99 4 s — stated at twenty dice, so `100d4` is held to a twenty-dice
      bar. Exactly right (a player waiting four seconds does not care how many
      dice they threw) or unfair to the worst case on purpose? The bars are
      data — one line in `HarnessTargets` (`docs/build-setup.md`, "The physics
      harness")

### Product

- [ ] **Does the sound go?** The design's Settings has no Impact sound switch:
      haptics is the only feedback toggle it offers. The app has a `feedback/`
      module that generates an impact sound per table material and die size,
      tested and with its own place in power-saving mode, and `README.md` sells
      it. A prototype cannot make a noise, so it not having a switch is weak
      evidence. Take the feature out, keep it and put the row back in the
      design, or keep it with no switch and let the system volume be the
      control
- [ ] **Does a tap on the table roll?** The design says "a felt table you
      shake or tap to roll" and the prototype's table rolls on a tap. The app
      spends that tap on nothing (`docs/physics-and-rendering.md`, "Starting a
      roll"), keeping the one-finger touch for picking a die up. With the Roll
      button gone this is the last open question about how a throw starts, and
      it pulls both ways: the tap is the obvious stand-in for a hand that
      cannot shake, and the tray's accessibility action already is one. Settle
      before the hand re-throw is wired up
- [ ] **Should the picker row offer "Doodle this die" as well?** Quick mode is
      a long press on a die in the **breakdown** (`docs/face-designer.md`,
      "Quick mode"), because the picker's long press takes a die off the
      formula. A menu there ("Take one off" / "Doodle this die") would reach
      the designer before a roll at the cost of a tap on every removal. Needs a
      phone to judge
- [ ] **Should a stamp be draggable after it is put down?** The prototype lets
      one be moved (`1v`); the app does not, because a stamp is a mark like a
      stroke and no other mark can be picked up. The case for the prototype is
      that a glyph is the one mark somebody places rather than draws. Does
      re-stamping feel like correcting a typo or like losing work?
      (`docs/face-designer.md`, "The stamp")
- [ ] **Should the anomaly log survive a restart?** It is in memory, bounded to
      fifty entries, because an entry carries the seed that reproduces the roll
      and a stored seed is a replay waiting to be written into a screen
      (`docs/architecture.md`, decisions 13 and 56). Against that, losing an
      anomaly to a restart may be losing the only one anybody sees. If it
      persists, only a file the developer toggle owns and the ordinary app
      cannot read is consistent with decision 13
- [ ] **Should an exported package carry a name?** `author` is left out:
      Android has no device user name an app can read without asking for
      contacts. A text field beside the licence chooser on "My dice", or a name
      kept in the settings and used by every export (`8c`,
      `docs/face-designer.md`)
- [ ] Division rounding default is Down with a per-throw override — confirm Nearest is worth having
- [ ] The dice picker remembers the last set per saved-roll group — confirm, then add to `docs/dice-notation.md`

### Tables and photos

- [ ] **Where does a table look's texture say which package it came from?** A
      die's artwork reaches the tray by package and path (`docs/dice-sets.md`,
      "How an atlas reaches the tray"), but a `TableLook`'s `floor_texture` and
      `wall_texture` carry a path only, so a table is drawn in its own colours.
      Either the package id goes on `TableLook`, or it is threaded through
      `Tray.table` and `Renderer.begin`, the seam that is deliberately narrow.
      Decide when a package that ships one exists
- [ ] **A photo table is in the package before the tray can draw it** — the
      one thing in the personal package whose picture the tray does not draw,
      for the reason above. Confirm that is the right order (`docs/tables.md`,
      "Your own photo")
- [ ] **How should a photo sit on the tray?** The upload sheet offers centre,
      fit width, fit height (`1u`); none is implemented, and a photo table is
      written with `floor_tiling = [1, 1]`. Cropping at import loses pixels
      somebody chose; mapping at draw time needs the tray's aspect, which
      changes with rotation

### Sets and collections

- [ ] **Where should a load-time texture report be shown?** `AtlasDecoder`
      produces `ValidationMessage` lines — a file that will not decode, an atlas
      with empty cells — and nothing reads them; the die falls back to its
      labels. The obvious home is the set's details screen beside the
      validation report (`6b`), which needs the decode somewhere a screen can
      reach rather than only on the roll thread
- [ ] A collection imported from a git repository records nothing about where
      it came from, so there is no "check for updates" for one as there is for
      a dice set; `RefResolver` is not asked which commit the ref was at. If
      collections should be updatable, start recording the commit
      (`docs/dice-notation.md`)
- [ ] A forge link that names a *file* inside a repository
      (`…/blob/main/goblins.dinfinity.json`) imports whatever is at the
      repository root instead. Decide whether such a link should import the
      file it names
- [ ] `core/probability` hand-rolls its convolution and its FFT rather than
      taking a library, which `.claude/CLAUDE.md` names as a "complex part".
      The judgement was that the exact PMF *is* the domain logic and ninety
      lines of transform beat a general maths library — worth a second opinion

### The design's own

From the design's decision log of 2026-09-17, and what the app found against
it. None blocks code.

- [ ] **Uppercase on a session-name kicker.** Kickers are small-caps at .1em
      tracking everywhere; `THORIN'S CAMPAIGN` is a decision about somebody's
      words — the same question the app's uppercase note asks from the other
      side (`docs/design-handover.md`)
- [ ] **Statistics is three axes on one screen** — per die, per set, per
      session. Split them, or keep the picker at the bottom?
- [ ] **History is flat and reverse-chronological.** Group by day, or by saved
      roll? The app emits a heading when the *session* changes, a third answer
      nobody has chosen
- [ ] **Make-default lives on a set's detail screen only.** Should the list
      rows carry it too?
- [ ] **The d18 is a true enneagonal trapezohedron** — geometrically right, a
      tall pointed barrel, busy at eighteen faces. Keep it, or stand in a
      rounded barrel? Verify it reads at phone size; read it beside the
      fairness note — the d18 is the one shape that cannot pass chi-squared,
      for the same narrowness that makes it look busy
- [ ] **An oversized stamp on a d4 clips at the face edge**, in the flat editor
      and on the solid, as it would on a real die. Leave it as authoring
      feedback, or shrink it to fit?
- [ ] **A filled button's label is 3.65:1 on its own accent, and wants 4.5:1.**
      `onPrimary` is the ground colour by design, so the label on **Save
      group** and every other filled button is pale ink on the accent. On the
      clamped accent against the light ground: light blue 3.65:1, Modernist
      red 3.76:1, amber 3.38:1, pine 4.79:1, magenta 5.14:1, cobalt 5.16:1;
      against the dark one 6.17, 3.95, 4.39, 3.10, 3.29 and 3.49:1. All twelve
      clear 3:1, which the clamp guarantees for any colour, so better presets
      cannot answer it. Fill with the ramp's 700 step and keep the pale label,
      keep the fill and darken the label, or make the primary action an
      outlined button. `ModernistContrastTest` holds the floor at the measured
      ratio
- [ ] **The divider is 2.41:1 on the light ground, and Material uses the same
      token for a control's border.** `--color-divider` is the text colour at
      40 % (3.51:1 on dark). As a rule between rows 3:1 does not apply; as
      `outline` it is also an `OutlinedButton`'s border, where it does. About
      55 % on the light ground would clear it, at the cost of heavier rules
      everywhere — the design's to answer
- [ ] **A neutral tag has no dark ground.** The prototype's `.dz-dark` override
      reflects each ramp about its middle step and writes out eight steps;
      `.tag-neutral` (`--color-neutral-100` filled, `-800` lettered) uses
      neither, so on a dark page it is a near-white chip. The app follows the
      rule; the inference is recorded in `docs/design-handover.md` rather than
      taken as settled, and `ModernistTest` asserts the eight that are written
      down
- [ ] **Statistics and History narrow their lists with a scrolling row of
      accent words; the prototype uses a segmented control.** `Cut`
      (`feature/stats`) draws a bare accent word; the prototype draws a `.seg`.
      `ui/common`'s `SegmentedControl` and `OptionBox` can draw either, so what
      is missing is a decision: a visible redesign plus a semantics change
      (`Role.RadioButton` via `selectable`). The number of sets is unbounded,
      which argues for the scrolling row
- [ ] **The table picker is a list of rows; the prototype's `1u` is a grid of
      cards.** The thumbnails went into the existing list (44 × 64 dp at the
      head of each row) rather than the two-column grid of 150 px cards, which
      keeps the package name, "Chosen" and **Remove** in one TalkBack node but
      gives the picture about a fifth of the area. Rebuild as the grid, or
      change `1u` to a list (`design/README.md`)

### Toolchain

- [ ] Raise `dinfinity.robolectricSdk` in `gradle.properties` from 36 to 37
      when Robolectric supports it (`build-logic`'s `RobolectricSdk.kt` writes
      it into every module's test resources)
- [ ] Move the container's emulator up when an automated-test image exists
      above API 36 — the same wait as the line above, for the same reason
      (`docs/architecture.md`, decision 39)
- [ ] The devcontainer asks Docker for `/dev/kvm` unconditionally, so a
      machine without nested virtualisation cannot open the project at all.
      Confirm that is the right default rather than building the image
      without an emulator and asking for the device only when one is wanted
      (`docs/build-setup.md`)
