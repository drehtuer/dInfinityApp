# TODO

The implementation plan. Steps run in order; within a step the boxes are
roughly ordered too. **Finished items are deleted, not ticked** — git history
and `docs/STATUS.md` are the record. Keep the file a plan, not a diary.

Everything here builds the specification in `README.md`, `docs/` and the
prototype in [design/](../design/). Where a step says *device*, it cannot run
on CI (see `.claude/CLAUDE.md`). Steps 1 and 3, 4.7, 4.9 and 5.1 are done.

## Step 2 — CI

Done apart from boxes that wait on other projects; none blocks anything.

- [ ] Re-enable CodeQL's `java-kotlin` analysis once the bundle supports Kotlin
      2.4.20. The matrix entry is commented out in `.github/workflows/codeql.yml`
      with its build steps kept. Bundle 2.27.0 (2026-09-16) still refused. A
      `v_2_4_20` shim in the `github/codeql` source tree does not mean a bundle
      ships it: the check is to put the entry back on a branch and read the
      **Analyse java-kotlin** job
- [ ] *Optional:* a `DEPENDABOT_METADATA_TOKEN` secret, so the metadata commit
      starts the checks without the *Approve workflows to run* banner
      (`docs/build-setup.md`)
- [ ] Drop `VerifyDeviceTestResultsTask` and `ignoreFailures` on
      `connectedDebugAndroidTest` once AGP stops failing runs on devices whose
      adb serial contains a colon (`docs/build-setup.md`)
- [ ] Drop the note on detekt's `ReportingExtension.file(String)` deprecation
      from `docs/build-setup.md` once detekt stops calling it — Gradle 10
      removes the method, so that is detekt's deadline

## Step 4 — Screens

Every screen is built and connected; what is below is what is left. The design
option ids (`1a`, `9c`, …) are the labels on the canvas in
[design/](../design/). A change to a screen is state, UI, tests (unit,
Robolectric, accessibility) and the matching document, in one PR.

### 4.1 Roll screen — `feature/roll`

Design `1a`–`1j`, `2a`, `3a`–`3c`, `4a`, `4b`, `6d`, `6f`, `9a`, `9c`, `1z`.
Spec: `docs/dice-notation.md`, `docs/tables.md`,
`docs/physics-and-rendering.md` ("What is drawn over the table").

- [ ] **Stagger the spawn**, 85 ms between dice, the result sheet waiting
      `min(2400, 950 + (n − 1) × 85)` ms for the last landing. The prototype's
      collision shove is **not** ported: a settled die moved by code is the
      invisible hand

Implementation notes recorded only here:

- The result pull-up is its own component — `Modifier.draggable` over an
  `Animatable`, decisions in `SheetSlide` — not `ui/common`'s modal `Sheet`.
- **A pull-up's test tag goes inside `Modifier.offset`**, or the semantics node
  reports the unslid position and every position assertion quietly passes.
- **The arrival waits for the measurement inside the coroutine**, not in the
  effect's key, which cancelled the slide and parked the sheet off-screen.

*Judgement on the phone:*

- [ ] **A second shake** at dice in the air is more of the same roll. Does it
      read as the dice answering the hand, and can a roll be kept going too long?
- [ ] **Pinch and pan:** is 4× in far enough, does a two-finger drag read as
      moving the table, does a pinch go where the fingers are, and does the wall
      rising at the far end of the pan read as the table or as falling off it?
- [ ] **The result pull-up and the saved-roll pull-up:** does the result
      arriving read as the answer; is the grip under the thumb; is there felt
      left to see a die at the bottom; do two stacked grips read as two things;
      is `SAVED ROLLS` needed on the lower one?
- [ ] **The formula tab** at the right edge: does it read as "the formula is in
      there", and does not seeing the formula cost more than the felt it gives
      back (`2a`)?
- [ ] **The formula's ×** (`docs/dice-notation.md`, "Emptying the field"):
      open the drawer on a long formula and tap the × at the end of the
      field — is it found without looking for it, is it far enough from the
      text that a tap meant for the end of the formula does not empty it, and
      does the keyboard stay up for the next one?
- [ ] **The empty tray** says nothing (the welcome does on a fresh install).
      Calm, or broken?
- [ ] **The dice pull-down:** does a shut menu read as "the dice are in there",
      and does the scrolling row read as "more dice" or "the d20 is missing"
      (`1h`)?
- [ ] **No tray shadow:** wall and rim no longer cast. Does the wall–floor join
      still read as a corner (the darkening there is SSAO)?
- [ ] **Power-saving** leaves the formula, the dice menu and a total; the
      design shows a progress indicator and a result sheet (`1z`). Instant, or
      broken?
- [ ] **Picking a die up** (decisions 76 and 84): push the sheet down, tap a
      die, and look at the accent ring round it on each table — does it pop
      out on the felt, light and dark, with whichever accent is set, and does
      it sit round the die you touched at every pinch? Does "Shake to throw
      the picked die" at the top say what picking is for, and is it in the
      way of a die you want to see? Then shake: does only that die go, does
      it land clear of the others, and does the struck-through face beside the
      new one on the sheet read as "thrown again"?
- [ ] **Clearing the table** (decision 83): shake, and see whether the formula
      tab folding into `Dice` reads as "put away" rather than "gone", and
      whether opening `Dice` to reach the formula is one press too many.
      Double-tap the felt: do the controls leave and come back the way you
      expect, and is the double tap found at all without being told? Tap a
      die: is the ~300 ms before its ring appears noticeable, or does a pick
      still feel immediate?
- [ ] **The shake prompt** (decision 84): land a die cocked (or a `1d6!` that
      explodes) — is "Shake to re-throw 1 die" at the top impossible to miss
      now, does it go the moment you shake, and does TalkBack read it once?

**Done when** every example in `docs/dice-notation.md` can be typed, rolled
and read here, and a seed gives the same result with the renderer on and off.

### 4.2 Outcome graph — `feature/graph`

- [ ] *Judge on the phone:* do 110 bars at 3 dp read as a distribution or a
      smear, and is the ±1σ band visible in both themes? The ledger (`1m`) and
      stepped area (`1l`) are not v1

### 4.3 Saved rolls — `feature/saved`

- [ ] Ten emoji as icons, or draw the design's icon pack? A decision, not an
      omission (`docs/dice-notation.md`)
- [ ] The canvas captions for `1r`, `1o`, `1p` still mention favourites; fix in
      the design project and re-import
- [ ] *Judge the drag on the phone:* picked up or nudged, and is the grip where
      a thumb expects it? With 30 or more rolls, hold a row at the top and the
      bottom edge: is a 64 dp band easy to find without hitting it by accident,
      is 640 dp/s at the edge controllable (can you stop on the row you want),
      and does a row picked up at the edge stay still until you move it
      outwards? Tune `SavedEdgeScroll.ZONE_DP` / `TOP_SPEED_DP_PER_SECOND`

### 4.4 Dice sets — `feature/sets`

- [ ] **Decide the built-in set's `size_mm`.** It is the width across the
      corners, so the built-in 16 mm d6 is a 9.2 mm cube weighing **0.9 g**
      against the design's 4.2 g. Something near `size_mm = 28` changes every
      built-in die's mass and how many fit a table — a decision, not a tweak

### 4.5 Table picker — `feature/tables`

- [ ] *Judge on the phone:* does a 44 × 64 dp thumbnail read as a table?

### 4.6 Face designer — `feature/designer`

- [ ] *Judge on the phone:* the d10's and the d18's own kites (decision 86).
      Open a d18 in the designer: does the long, narrow canvas still leave room
      to draw, and does a whole-face fill and a stamped number land on the
      thrown die edge to edge, nothing spilling onto the neighbouring faces?
      A drawing made on a kite die before the change shows larger in the
      editor than it was drawn — is that read as the die's size or as a fault?
- [ ] Should the Solid tab draw pen strokes (as thin filled outlines)? Today it
      says what it does not draw
- [ ] *Judgement:* named personal sets (decision 79). A drawing saved into a
      named set is a copy and also stays in "My dice" — is that what somebody
      expects, or should a set-only drawing leave "My dice" alone? Does the
      line under the name field ("Its id will be brass-bone: that is what
      notation calls the set") read as help rather than jargon, and is a
      refused name's sentence clear about what to change?
- [ ] *Judgement:* does the bucket close a loop somebody meant to close (ends
      within 0.08 of the canvas)? Does the Solid tab's turning d20 read as a die
      being turned over; is one lamp enough; does the selected-face outline
      find a face at the back? Does a drawing read as *theirs* at tray distance,
      a d10's at all? Do seven 44 dp tool glyphs need captions? Does the
      test-throw banner read as "you are testing this"? Is `Clear face`, in
      words under the strip, where a thumb looks for it (decision 82)?

### 4.10 Settings and menu — `feature/settings`

- [ ] The design's Settings has *Example dice set on GitHub* and *Reset
      statistics for «session»…*; the app has neither there. Grow two rows, or
      drop them from the prototype?
- [ ] The accent's hex inline in tabular numerals, as the design draws it —
      when the accent block is reconsidered as a whole
- [ ] *Judge in split screen:* does a row whose control stacks under the
      sentence still read as one setting?

## Step 5 — Physics and rendering on a real phone

**None of it can run on CI.** A roll has to look like dice landing, not like
an animation of a random number. Runs again after every physics change, with
`tools/harness.sh` (`docs/build-setup.md`, "The physics harness").

**Latest harness run** — Pixel 10a, 1,000 rolls of 20d20, 2026-10-03, after
the livelier tumble (60–120 rad/s of spin, die restitution 0.55; the before
column is `main` at #336):

| target | bar | before | measured |
| --- | --- | --- | --- |
| dice at rest on another die / corrections after rest | 0 / 0 | 0 / 0 | 0 / 0 |
| dice corrected | 0.5 % | 0.000 % | 0.000 % |
| **dice re-thrown** | 0.05 % | 2.65 % | **1.18 %** |
| median / p99 settle | 2 s / 4 s | 0.83 s / 1.53 s | 0.96 s / 1.76 s |
| rolls that gave up / forced settles | 0 / 0 | 0 / 0 | 0 / 0 |
| **deepest die-into-die overlap** | 0.2 mm | 5.29 mm | **7.76 mm** (p99 roll 4.67 → 5.68 mm) |
| turns after landing | ≥ 1.00 | 1.55 | 2.70 |
| p99 step time | 8.33 ms | 0.22 ms | 0.38 ms (0.20 ms on a cooler phone, same physics) |

Re-run at the stack's tip on 2026-10-04 before trying collision sub-steps
(5.4): the same within noise — re-throws 1.19 %, settle 0.96 s / 1.96 s,
overlap 7.76 mm, 2.72 turns, p99 step 0.23 ms; over 5,000 rolls each on seeds
2 and 3, overlap 6.94 mm and 7.90 mm and re-throws 1.23 % and 1.21 %.

60d20 over 1,000 rolls: turns 1.16 → 2.06, median settle 1.28 s → 1.52 s,
re-throws 5.11 % → 2.64 %, none gave up, slowest roll 10.5 s (4.7 s before).
`100d6` still gives up within its first 200 rolls, before and after.

### 5.2 Fairness and determinism

Fairness is done on the Pixel 10a (the d18 held to the worst-face bound), and
`ModesAgreeTest` holds power-saving and drawn modes to the same faces.

- [ ] Identical outcomes for identical seeds across JVM, emulator and device at
      ten thousand rolls and on a second phone. The golden suite already holds
      for its ten cases on both ABIs; any divergence is a release blocker.
      A linux-x86_64 build of the bridge, made outside the build for the coin
      replay (`docs/physics-and-rendering.md`, "Why a die could rock for
      ever"), matched all ten cases and the phone's coin give-up. Building it
      in Gradle would put real rolls in the JVM tier

### 5.3 Capacity and corner cases

The 1–100 d6 sweep under counting (Pixel 10a, 2026-10-04, at #349; 200 rolls
each up to 20 dice, 60 above): **no roll gave up at any count**, no die read
while standing on another, p99 step at most 1.23 ms (60 dice) against 8.33.

| dice | 1 | 2 | 5 | 10 | 20 | 40 | 60 | 80 | 100 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| re-thrown | 0 % | 0.25 % | 0.10 % | 0.20 % | 1.10 % | 1.42 % | 1.83 % | 1.38 % | 1.22 % |
| settle median / p99 (s) | 0.68 / 0.92 | 0.75 / 0.96 | 0.81 / 0.98 | 0.83 / 1.54 | 0.86 / 1.70 | 0.88 / 1.64 | 1.45 / 1.73 | 1.40 / 1.68 | 1.43 / 1.64 |
| deepest overlap (mm) | 0 | 3.6 | 5.6 | 4.4 | 6.4 | 5.3 | 6.0 | 6.4 | 4.8 |
| turns after landing | 2.73 | 2.90 | 2.96 | 2.90 | 2.54 | 2.02 | 2.03 | 1.94 | 1.82 |

- [ ] **`100d4` does not reliably settle.** Five of twenty-four seeds in
      `JoltBridgeTest` reach the twelve-second backstop and give up (since #319
      without taking the app with them). The bound is today's worst case
- [ ] **A hundred coins stack: 18–29 a seed are left on another coin by one
      throw** (Pixel 10a, 2026-10-04; four to ten under the old ladder, which
      re-threw them itself). None is read (decision 70), and
      `CornerCasesTest` bounds the heap at today's worst, 29. The target is
      zero
- [ ] **Decide what a tilted phone means** (deferred): gravity always straight
      down; anchored to `TYPE_GRAVITY`; or a clamped tilt
- [ ] *With a hand:* **do the dice still gather at the top of the screen?**
      The wrist's swing pushed them there whichever way the phone was shaken;
      it is taken out now (decision 87), and off the phone a swung shake went
      from 45–55 % of the dice in the top third to 33–36 %. Shake ten `10d6`
      each way — left and right, up and down, back and forth — and watch where
      they stop: no end should win. If the bottom wins instead, the estimate is
      too eager (`SwingCorrection.DAMPING`). A shake that only *carries* the
      phone along the long axis was never biased off the phone; whether a
      heap at the end it last moved towards is right is still a 5.6 question
- [ ] *With a hand:* shake vertical and upside down for real. `ExtremeInputTest`
      covers what a machine can drive
- [ ] Decided, left alone: `MIN_SCALE` would not refuse before **241 dice**,
      and the engine stops at 100. `TableCapacityTest` notices if `MAX_DICE`
      passes 241

### 5.4 Collisions

- [ ] **Dice go 5–8 mm into each other; the bar is 0.2 mm.** 9.02 mm first,
      5.04 mm after two sub-steps and a tapering throw (2026-09-18), 5.29 mm
      on 2026-10-03 after #321's spawn fix — so the spawn was not where it
      came from, and what is left is the solver's discrete detection. The
      livelier tumble took it to **7.76 mm** (the p99 roll from 4.67 to 5.68
      mm): bouncier dice meet harder. Four and eight collision sub-steps
      against it (2026-10-04, decision 77): 4.7–5.0 mm and 2.5–2.7 mm, no
      more shaken heaps (7 of 16 at 2, 4 and 8), but eight settles a few steps
      later on every seed and re-throws 2.87 % against 2.62 % at sixty dice,
      so two stays (`docs/physics-and-rendering.md` has the table). Sub-steps
      alone will not reach 0.2 mm. The dice already sweep their travel
      (`LinearCast`), which does not sweep a spinning die's corners — at
      60–120 rad/s that is the likelier source, and the next thing to look at
- [ ] *Judge on the phone:* is a die sinking into another visible while the
      dice tumble? Watch a few `20d20` throws (or `tools/harness.sh --capture
      20` and step through it). If it is, eight collision steps is the measured
      fix — one constant in `World::Step`, then the goldens re-recorded and
      `FairnessTest` re-run at 20,000 a shape

### 5.5 Stacking and cocking — no invisible hand

Nothing corrects a die and nothing throws one but the player: a roll counts the
dice that can be read, and the ones that cannot wait where they lie for the
shake that throws them again (`docs/physics-and-rendering.md`, "Avoiding
stacked and cocked dice"; decision 70). Figures in the table above, measured
while the roll still threw them again itself; the harness's scripted hand
keeps them comparable.

- [ ] **Decide what the re-throw bar means now.** 0.05 % was written when a
      re-throw was the last resort after two rungs of correction. A re-throw is
      now a second shake the player is asked for, so the share is how often
      that happens — 2.65 % of dice, which at 20 dice is a second shake on
      roughly two rolls in five. Bound that per roll, and the passes a roll
      needs, rather than per die
- [ ] *With a hand:* does a throw that stops with a cocked die read as the
      app asking, rather than as the roll hanging? Is the plate, the shake
      prompt and the heap left as it lay enough to know which die the shake is for — or
      does the waiting die want marking on the tray (`docs/design-handover.md`)?
- [ ] **A die pinched at two points spins for ever.** The two `60d20` rolls in
      10,000 that gave up were dice leaning on the round post that stood in
      each corner; the corners are fillets now (decision 81) and over 50,000
      rolls a count give-ups went from 9 to 2 at `60d20` and 1 to 2 at `20d20`.
      Every one left — and most from before — is one die with its centre still,
      turning at 1–31 rad/s about the line through two single contacts (two
      neighbours, or a neighbour and a wall), which friction cannot reach and
      0.02 damping takes minutes to stop (`docs/physics-and-rendering.md`,
      "Why a die could rock for ever"). Missing physics: drilling friction.
      Options for the owner — a per-contact spin resistance in the contact
      listener, or more angular damping — both change every roll's tumble, so
      each wants the turns-after-landing figure, the goldens and `FairnessTest`
      with it. Replays: `20d20` seed 1 roll 9996, seed 2 roll 5193; `60d20`
      seed 2 roll 4518, seed 4 roll 8361
- [ ] Tune prevention — spawn spread, dice-on-dice friction, throw energy,
      scale — to bring re-throws and overlap down. Five spawn height bands
      instead of three was neutral
- [ ] *With the user:* frame-by-frame review of 50 recorded 20-dice rolls

### 5.6 Feel — the user's call, not a metric

- [ ] **The start of a shake** read as a lag: the dice are already fast when
      the shake reaches them. The 100 ms start threshold and the spawn impulse
      are the two numbers
- [ ] **The pace.** The tumble (60–120 rad/s, restitution 0.55) is approved;
      the travel was still a little fast at 0.5, so `RollPace.WATCHED` is now
      0.4 and a 0.96 s 20d20 throw takes 2.4 s to watch. Is the travel right
      now, or too slow? Does `1d20` a dozen times still feel prompt; does the
      speed change when the hand lets go read as intended; does a second shake
      at tumbling dice still answer instantly?
- [ ] The keyboard over the lower half of the tray — right, or shift the tray
      up while editing (`2a`)?
- [ ] **Haptics:** a knock or a rattle; one die among twenty still felt; a
      hundred dice distinct knocks or one buzz (45 ms rate limit)? Try `1d20`,
      `20d6`, `100d6`
- [ ] **Sound:** do `felt-green`, `oak`, `dark-glass` and `plain` sound like
      their surfaces with `5d6`; does `2d20` against `20d6` read as size? Is
      power-saving's one-second replay a throw or an effect?
- [ ] **Legibility at arm's length.** `DieNumbers.FACE_SHARE` (with "0.78 or
      0.78 squared", Open questions), the d4's `CORNER_HEIGHT`, the d18's small
      numbers
- [ ] Power-saving feels instant and gives the same answer
- [ ] **Exploding rolls:** does the wait between links read as part of the
      roll; does the added die look thrown (dropped from 25 mm); does it ever
      seem to pass through a die lying there (`ClearSpace` is the number)? Does
      a chain that fills the tray read its stop line as a rule or a bug?
- [ ] **Is the first throw of a session different?** Seen: overlap and a chain
      throwing itself "only in the beginning". The candidate is the frame clock
      dropping steps while Filament, the atlas and Jolt load
      (`MAX_STEPS_PER_FRAME` is 4). With developer tools on, throw `4d6!`
      three times from a cold start and read the overlay's
      `dropped <roll> · visit <total>` line after each
- [ ] **Does a die the picker adds read as dropped and tumbled?** Does it
      land and roll rather than appear; do the bumps look right after eight
      quick taps of a d6; does a removal let a leaning die fall plausibly? Put
      a saved roll of `8d6` on the table: can the eye follow the stream out of
      the one spot, and does `40d6` read as a patter rather than a wait?
      The numbers to turn are `BoardDrops.DROP_SPOT` (the middle of the tray),
      `SPOT_JITTER_MM` (1.5), `DROP_INTERVAL_SECONDS` (0.1),
      `LONGEST_STREAM_SECONDS` (4), `DROP_HEIGHT_MM` (60),
      `LEAST_SLIDE_MM_PER_SECOND`–
      `MOST_SLIDE_MM_PER_SECOND` (40–150), `MOST_DOWNWARD_MM_PER_SECOND` (150)
      and `LEAST_SPIN_RADIANS_PER_SECOND`–`MOST_SPIN_RADIANS_PER_SECOND`
      (9–18). Also run `BoardSettlerTest` and read its timings (`dinfinity.board`
      in logcat)
- [ ] Rendering polish and the optimisation pass — deliberately **last**

### 5.7 Performance on the Pixel 10a

`MemoryTest` holds the native heap across 500 rolls (1,440 bytes left
behind).

Frame targets met on the Pixel 10a (`tools/harness.sh --rendered`, decision
80): 60.3 fps at 20d20 with p99 work 8.3 ms and GPU 12.6 ms; 57.3 fps at the
100-dice limit (`docs/physics-and-rendering.md`, "Performance, and how it is
measured"). Re-run after any change to the renderer or the physics step.

- [ ] Battery cost of 100 rolls, written into `docs/physics-and-rendering.md`

**Done when** every target is met on the Pixel 10a and the user agrees the
dice look right. Wrong numbers become the new numbers in the docs.

## Step 6 — v1 release

- [ ] **Walk every screen with TalkBack on a real phone** — reading order,
      announcement length, the tray with the screen curtain on
      (`docs/architecture.md`, "Accessibility")
- [ ] Localisation, **once a second language exists**. Decide first: does a
      refusal (`ValidationMessage`, `core/collection`'s reader,
      `NotationReference`) keep its English words or become a typed reason the
      screen phrases; and is percent typography (`"0 %"`, `"< 0.1 %"`, `"—"`)
      text?
- [ ] Play Store metadata, screenshots from the real app, privacy statement
- [ ] Tag `v1.0.0`

## After v1

- [ ] More catalogue solids, `rhombic-triacontahedron` (d30) first
- [ ] Author-supplied convex meshes, with the fairness preview they need
      (`docs/dice-sets.md`, "Shapes after v1")
- [ ] Author-supplied materials — needs a decision on what a stranger's shader
      may do, a compile-time limit and an instruction cap (decision 46)

## Coverage

Branch coverage is **~71.4 %** against a floor of 62; about seven in ten missed
branches are Compose skip branches a single-pass test reaches one side of.
What helps: extract decisions from draw lambdas, test shared components on
their own, add recomposition tests. Figures go in every PR description.

- [ ] Should the floor track the drift? A question for a person — moving a
      floor to make a check pass is what `.claude/CLAUDE.md` forbids

## Open questions

### Rendering and physics

- [ ] **A numeral at 0.78 of its face, or 0.78 squared?** `FACE_SHARE` is
      applied twice (`LabelRoom.centred`), so a numeral is ~0.61 of its room.
      Once would make every number 28 % bigger; tray and designer move together
- [ ] Should a die an explosion adds collide with the dice already down, as
      immovable bodies? Needs a static body kind in the bridge (decision 54)
- [ ] Should a heavy die sound heavier? `density` reaches the physics, not the
      sound; it needs a hull volume the catalogue does not compute
- [ ] Is `100d4` fairly held to the twenty-dice settle bars (`HarnessTargets`)?

### Product

- [ ] **Does the sound go?** The design's Settings has no sound switch; the app
      generates impact sounds per table and die size. Remove, add the row back
      to the design, or keep with no switch
- [ ] "Doodle this die" on the picker's long press as well as the breakdown's?
- [ ] Should a stamp be draggable after it is put down, as in `1v`?
- [ ] Should the anomaly log survive a restart? A stored seed is a replay
      waiting to happen (decisions 13 and 56)
- [ ] Should an exported package carry the author's name, typed once in
      Settings or per export (`8c`)?
- [ ] Division rounds Down with a per-throw override — is Nearest worth having?
- [ ] The picker remembers the last set per saved-roll group — confirm, then
      document in `docs/dice-notation.md`

### Tables and photos

- [ ] **Where does a table look's texture say which package it came from?**
      Dice artwork reaches the tray by package and path; a `TableLook` carries a
      path only, so a table draws in its own colours. Decide when a package
      ships one — which also decides whether a photo table belongs in the
      personal package before the tray can draw it
- [ ] How should a photo sit on the tray — centre, fit width, fit height
      (`1u`)? None is implemented

### Sets and collections

- [ ] Where is a load-time texture report shown? `AtlasDecoder`'s messages are
      read by nothing; the set's details screen (`6b`) is the obvious home
- [ ] Should git-imported collections be updatable? Then record the commit
- [ ] Should a forge link to a *file* import that file, not the repository root?
- [ ] `core/probability` hand-rolls its convolution and FFT — a second opinion
      on taking a library

### The design's own

None blocks code (design decision log of 2026-09-17).

- [ ] Uppercase on a session-name kicker (`THORIN'S CAMPAIGN`)?
- [ ] Statistics is three axes on one screen — split, or keep the picker?
- [ ] History grouped by day, by saved roll, or by session as the app does?
- [ ] Make-default on the set list rows as well as the detail screen?
- [ ] The d18 as a true enneagonal trapezohedron, or a rounded barrel?
- [ ] An oversized stamp on a d4 clips at the edge — keep, or shrink to fit?
- [ ] **A filled button's label is 3.65:1 on its accent, wants 4.5:1.** Fill
      with the 700 step, darken the label, or outline the primary action
      (`ModernistContrastTest` holds today's ratio)
- [ ] The divider is 2.41:1 on light and doubles as a control border, where 3:1
      applies; ~55 % would clear it
- [ ] A neutral tag has no dark-ground rule; the app's inference is in
      `docs/design-handover.md`
- [ ] Statistics and History filter with a row of accent words; the prototype
      uses a segmented control
- [ ] The table picker is a list; `1u` is a grid of cards. Rebuild, or change
      `1u`

### Toolchain

- [ ] Raise `dinfinity.robolectricSdk` from 36 to 37 when Robolectric supports
      it; move the container's emulator above API 36 when an automated-test
      image exists (decision 39)
- [ ] The devcontainer asks for `/dev/kvm` unconditionally — is that the right
      default (`docs/build-setup.md`)?
