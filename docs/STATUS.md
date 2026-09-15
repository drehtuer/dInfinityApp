# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-16

## Where we are

- **Phase:** implementation, **Step 4**. Steps 1 and 2 are done; Step 3 is done
  but for the atlases, which arrive with the screen that needs them. Step 5 —
  physics and rendering on a real phone — is where the remaining hard problems
  are.
- **The app rolls dice on a phone.** Type a formula, tap Roll or shake the
  Pixel 10a, and the dice tumble onto a felt tray, come to rest, and their
  total appears. Every screen in the menu is written, connected and does
  something.
- **Latest release:** `v0.0.1` — the skeleton, cut to prove the release
  pipeline. Signed, fingerprint-checked, published with its SHA-256.

### Branch state

`main` has everything up to **#182** and is **green**: both test tiers, ktlint,
detekt and Android Lint. In flight, stacked in this order: `feature/shake-record`
— a shake-driven throw's record now carries the shake that drove it, and stops
there; `feature/haptics` — **the dice can be felt and heard**;
`feature/designer-tools`; `feature/harness` — Step 5's physics harness, and what
it said about the Pixel 10a; and `feature/explosion-dice` — the die an explosion
or a reroll adds is now thrown into the tray the player is watching.

### Which device the tier runs on

The emulator in the devcontainer (API 36, `x86_64`) answers most questions;
the Pixel 10a (API 37, `arm64-v8a`) answers the rest. **All 34 device tests
pass on the phone**, which closes the two checks that had been waiting: Filament
1.76.1 compiles its material on the real driver and draws a frame with more than
one colour in it, and the golden cases re-recorded on the emulator match on
`arm64-v8a`.

A device run keeps the screen on now. A phone sleeps after a minute and a full
run takes four, and everything after that failed with "no compose hierarchies
found in the app" — on whichever module happened to run last, so it read as a
different bug each time (`docs/build-setup.md`).

## Done

- **The specification.** `README.md`, `docs/` and the clickable prototype in
  `design/`, cross-referenced both ways and published at
  <https://drehtuer.github.io/dInfinityApp/>. GPL-2.0-or-later.
- **The skeleton and CI.** Devcontainer, convention plugins, 27 modules, the
  Modernist theme, the navigation graph. Every linter and both test tiers run
  on each pull request; SonarQube blocks on its gate and JaCoCo on a function
  *and* branch floor. Dependencies pinned by SHA-256; a `vX.Y.Z` tag cuts a
  signed, immutable release.
- **All three testing tiers are reachable and each is run every time.** JVM and
  Robolectric on CI; the devcontainer's emulator; the Pixel 10a over wireless
  debugging (API 37, `arm64-v8a`).
- **Step 3's foundations.** A formula parsed and resolved against the installed
  sets, graphed exactly, planned against the capacity rule, settled and read
  face by face, watched by a renderer that cannot touch it, thrown by a shake,
  and written down in one transaction. A package from a stranger is validated
  rule by rule and installed without leaving anything behind if it fails.
- **The physics.** Jolt 5.3.0. Every *decision* about a roll is Kotlin over an
  interface, so the rule that matters most — nothing touches a die that has
  come to rest — is proved by JVM tests rather than sampled on a phone. Every
  random number comes from one place, stirred through SplitMix64's finaliser.
- **Determinism is asserted, not assumed.** Ten (seed, formula, input) cases;
  the emulator and the Pixel 10a agree bit for bit, spawn digest included.
- **The renderer draws a roll.** Filament opens on the device's own driver and
  compiles its material there. One thread owns the physics world, the engine
  and the frame callback.

## In progress

**Step 4: every screen is written and connected.** What is left on each is in
`docs/TODO.md`; the shape of it is that the *screens* are done and what remains
is mostly polish, the export paths, and the things that need a phone.

- **4.1 Roll.** The tray from the moment the screen opens, the formula on it as
  text that a tap turns into a live-validated editor whose Enter rolls, the
  dice picker row, the saved-roll strip, roll from the button or a shake, the
  total and a breakdown that itemises the modifiers as well as the dice, pinch
  and pan, power-saving, and a first launch that offers all three ways in with
  a count line that counts. **The dice carry numbers now**: real Archivo
  outlines turned into a distance field per die, so a `6` stays a `6` at four
  times in, and barred when the same die also carries a `9` — the rule a
  moulded die follows. A d4 prints three to a triangle, one at each corner.
  **And they are felt and heard as they land**: a roll reports the impacts it
  made, one player lays them out in wall time — as they happen on a watched
  tray, across about a second in power-saving mode — and the five table sounds
  are generated in Kotlin rather than shipped, so no decoder is in the path.
  **An explosion is visible too**: each die it adds is a throw of its own, made
  once the last has stopped, dropped into the clear floor the settled dice leave
  and drawn among them. No body is created for a die that has come to rest, so
  an added die cannot shove one; a chain stops when the tray runs out of floor,
  and the breakdown says so.
- **4.2 Graph, 4.3 Saved rolls, 4.4 Dice sets, 4.5 Tables, 4.7–4.9 Statistics,
  history and sessions, 4.10 Settings and Notation.** All built. Collections
  travel as JSON and arrive from a file, a link or a git repository — one
  `*.dinfinity.json` at its root, down the same downloader and the same
  hardened extractor a dice set uses; dice sets install from
  either, can be checked for updates and re-installed through the same
  validator; tables are chosen where the tables are, and a throw lands on the
  saved roll's pinned table, then its group's, then the app's.
- **4.6 Face designer.** Draw on any die of any usable set, undo and redo an
  action at a time, and **each die keeps its own draft on disk** — written
  after every stroke, so a drawing outlives the screen. **Roll it** hands the
  tray the die being drawn. Most of the `4c` toolbar is there: a fill bucket
  that adds a region rather than flooding pixels, copy face → paste with a
  turn and a mirror, and a colour picker past the twelve presets. Still to
  come: the stamp and "fill all faces with numbers" — both place a glyph, and
  the font they would take it from exists now, so what is left there is wiring
  rather than waiting — and the export.
- **Step 5.1, the harness, is built.** `tools/harness.sh` rolls N throws
  headlessly on the emulator or the phone, pulls back a JSON document of what
  they did — settle times, corrections, post-rest corrections, re-throws,
  forced settles, stacked dice, the deepest die–die overlap, per-step wall
  time — and prints a pass/fail table against Step 5's targets. Everything it
  decides is plain Kotlin in `simulation/harness` and tested on the JVM; the
  device only rolls, times and writes. It **fails** on three of ten targets on
  the Pixel 10a, which is what it is for. Frame times, soak mode and screen
  capture are what is left of 5.1.

## Blocked / waiting on

- **Judgements that need a person and a phone**, all listed in `docs/TODO.md`:
  whether the dice have weight, whether 16 mm dice read too small, whether the
  empty table looks worth rolling on, whether four times in is the right pinch
  limit, and now whether the haptics land and whether five generated waveforms
  sound like felt, oak, glass, stone and plastic. None of them blocks anything else. `screencap` on the phone returns a
  real frame, so what a screen *contains* can be checked from here — that is
  how the menu's invisible header was found — but whether a thing feels right
  is still a person's call.

## Decisions pending

- Whether the branch-coverage floor should follow the drift, or stay where it
  is. Moving a floor to make a check pass is what `.claude/CLAUDE.md` says not
  to do, so this is a question rather than a change to make quietly.
- Two smaller ones from the prototype (designer 3D preview, the picker
  remembering the last set per group) — see `docs/TODO.md`, Open questions.

## Known risks

- **The "no invisible hand" bar** — zero post-rest corrections, zero stacked
  dice — is the hardest thing in the plan and can only be judged on a device.
  If prevention cannot get there, the fallback is a visible re-throw, which is
  honest but must not become common.
- **The correction ladder leans on corrections far too hard, and now there is
  a number for what that costs.** The Step 5 harness ran on the Pixel 10a for
  the first time: 200 throws of 20 d20s. Every honesty bar passes — **zero**
  dice at rest on another die, **zero** post-rest corrections, zero forced
  settles — and so does every timing bar, with a median settle of 0.81 s and a
  p99 step of 1.00 ms against the 8.33 ms a 120 Hz step has. Three fail:
  **43.55 %** of dice corrected against a 0.5 % budget, **3.50 %** re-thrown
  against 0.05 %, and dice reaching **9.0 mm** into each other against a bar of
  0.2 mm, on dice 16 mm across. The last is new and is almost certainly the
  same fault seen from the collision side rather than a second one. Step 5.5.
- **The d18 is a known limitation, decided and written down.** It cannot pass
  chi-squared at a hundred thousand rolls, because its resting basins are
  narrow enough that the float32 hull's own rounding biases it and Jolt stores
  hull points in single precision whatever else is configured. It is held to
  the worst-face bound instead — no face off its share by more than 1 %, and
  its worst measured is 0.389 % — which is the bar a player would recognise,
  against the 1–2 % a moulded plastic d20 manages. One shape by name in
  `FairnessTest`, its χ² still printed every run, and nothing said to the
  player in the app (`docs/physics-and-rendering.md`).
- **`100d4` does not reliably settle, and never did.** Five seeds in
  twenty-four run out of the twelve-second cap. Nothing is ever touched after
  coming to rest, on any seed — the rule that matters holds — but the cap
  firing at all is prevention work (Step 5.5).
- **The capacity constants barely bite.** It would take about 240 dice to reach
  the 40 % floor and the engine stops at 100, so the refusal a player meets is
  the body cap rather than the table. A Step 5.3 question, with a device.
- Determinism holds across the two ABIs. What is unproven is determinism across
  *devices* of the same ABI and across time.
- The container's emulator has no real GPU and no display, so `screencap`
  returns black. It answers "does this run", never "does this look right".
- **Branch coverage is 70.1 % against a floor of 62**, and the drift that used
  to come with every screen has stopped: seven in ten of the missed branches
  are Compose skip branches a test can only take one side of, and the answer —
  lift decisions out of draw lambdas, give shared components their own tests,
  add recomposition tests that take the other side — has held the number flat
  or moved it up in each of the last six pull requests. Function coverage is
  91.8 % against a floor of 85.
