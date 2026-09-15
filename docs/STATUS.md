# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-15

## Where we are

- **Phase:** implementation, **Step 4**. Steps 1 and 2 are done; Step 3 is done
  but for the SDF numbers and the atlases, each of which now arrives with the
  screen that needs it. Step 5 — physics and rendering on a real phone — is
  where the remaining hard problems are.
- **The app rolls dice on a phone.** Type a formula, tap Roll or shake the
  Pixel 10a, and the dice tumble onto a felt tray, come to rest, and their
  total appears. Every screen in the menu is written, connected and does
  something.
- **Latest release:** `v0.0.1` — the skeleton, cut to prove the release
  pipeline. Signed, fingerprint-checked, published with its SHA-256.

### Branch state

`main` has everything up to **#173** and is **green**: both test tiers, ktlint,
detekt and Android Lint. No branches are in flight.

### Which device the tier runs on

The emulator in the devcontainer (API 36, `x86_64`) answers most questions and
is what the recent work was checked against. Two things still want the Pixel
10a: whether Filament 1.76.1 looks right on a real GPU, and whether the golden
cases — re-recorded on the emulator after the spawn streams were stirred —
still match on `arm64-v8a`.

## Done

- **The specification.** `README.md`, `docs/` and the clickable prototype in
  `design/`, cross-referenced both ways and published at
  <https://drehtuer.github.io/dInfinityApp/>. GPL-2.0-or-later.
- **The skeleton and CI.** Devcontainer, convention plugins, 26 modules, the
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
  a count line that counts. **Missing: numbers on the faces**, which is the SDF
  item in Step 3 — until it lands the tray shows a roll that cannot be read
  without the total.
- **4.2 Graph, 4.3 Saved rolls, 4.4 Dice sets, 4.5 Tables, 4.7–4.9 Statistics,
  history and sessions, 4.10 Settings and Notation.** All built. Collections
  travel as JSON and arrive from a file or a link; dice sets install from
  either, can be checked for updates and re-installed through the same
  validator; tables are chosen where the tables are, and a throw lands on the
  saved roll's pinned table, then its group's, then the app's.
- **4.6 Face designer.** Draw on any die of any usable set, undo and redo an
  action at a time, and **each die keeps its own draft on disk** — written
  after every stroke, so a drawing outlives the screen. **Roll it** hands the
  tray the die being drawn. Still to come: the fill bucket and stamp, and the
  export.

## Blocked / waiting on

- **Judgements that need a person and a phone**, all listed in `docs/TODO.md`:
  whether the dice have weight, whether 16 mm dice read too small, whether the
  empty table looks worth rolling on, whether four times in is the right pinch
  limit. None of them blocks anything else. `screencap` on the phone returns a
  real frame, so what a screen *contains* can be checked from here — that is
  how the menu's invisible header was found — but whether a thing feels right
  is still a person's call.

## Decisions pending

- **What the d18 should be held to.** It is fair to better than half a percent
  per face and cannot pass chi-squared at a hundred thousand rolls, for a
  reason that is understood and cannot be engineered away in this engine.
  Restate the bar, carry it as a known defect, or drop the shape.
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
- **The correction ladder leans on corrections far too hard.** Nine of twenty
  dice get a nudge, against a budget of one in two hundred. Every one lands
  while the die is still moving and post-rest corrections are zero, so the
  honest rule holds — but at a hundred dice the corrections are *visible*, and
  "it does not cheat" and "it does not look like it cheats" are different
  claims. Step 5.5.
- **The d18 is not fair, and it is the first physics claim to fail on a
  device.** 100,000 rolls of each catalogue shape on the Pixel 10a: seven pass
  with their χ² summing to 55.33 against 55 degrees of freedom, and the
  enneagonal trapezohedron comes to 197.34 against a limit of 40.79. The body,
  the solver, the reading, the seeds and the throw were each measured and each
  holds. **It is the float32 hull** — Jolt holds hull points in single
  precision whatever else is configured, so this is as fair as the engine can
  make that solid. No face is off by more than 0.455 %, against the 1 % this
  project set itself.
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
- **Branch coverage is 69.2 % against a floor of 62**, and the drift that used
  to come with every screen has stopped: seven in ten of the missed branches
  are Compose skip branches a test can only take one side of, and the answer —
  lift decisions out of draw lambdas, give shared components their own tests,
  add recomposition tests that take the other side — has held the number flat
  or moved it up in each of the last six pull requests. Function coverage is
  91.7 % against a floor of 85.
