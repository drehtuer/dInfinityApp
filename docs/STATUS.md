# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-13

## Where we are

- **Phase:** implementation. Steps 1 and 2 of `docs/TODO.md` are done and Step
  3 is nearly done: a formula can now be parsed, planned, thrown as real dice,
  read off their faces and pinned against a recorded outcome, end to end. What
  is left of Step 3 needs a screen to land on, which is Step 4.1 — now in
  progress — plus the shake on real hardware and the smaller items listed
  there.
- **Latest release:** `v0.0.1` — the skeleton, cut mainly to prove the release
  pipeline. Signed, fingerprint-checked, published with its SHA-256.
- **Branch state:** everything up to and including #54 is merged and `main` is
  green. Step 4.1, the roll screen, is what is being built now.

## Done

- **The specification.** `README.md`, `docs/` and the clickable prototype in
  `design/`, cross-referenced in both directions and published at
  <https://drehtuer.github.io/dInfinityApp/>. GPL-2.0-or-later.
- **The skeleton.** Devcontainer, Gradle convention plugins, the 24 modules
  `docs/architecture.md` describes, the Compose theme from the Modernist
  tokens, the navigation graph for all ten screens, APK naming. Settings picks
  the accent and DataStore remembers it — the one screen that exists.
- **CI.** Build, tests and every linter on each PR, plus CodeQL, Dependabot,
  dependency review and the submitted dependency graph. SonarQube runs as the
  scanner and blocks on its quality gate; JaCoCo measures function *and*
  branch coverage against a floor in `gradle.properties`, so the half
  SonarQube has no counter for is still enforced. Every dependency is pinned
  by SHA-256. `main` requires its checks; a `vX.Y.Z` tag cuts a signed,
  fingerprint-checked, immutable release.
- **All three testing tiers are reachable, and each has been run.** JVM and
  Robolectric on CI; the emulator that ships in the devcontainer, which boots
  headless in half a minute and runs the instrumented suite in ten seconds;
  and the Pixel 10a over wireless debugging, one command away
  (`dinfinity-phone`, which remembers where the phone was). The emulator is
  API 36 on x86_64 and the phone API 37 on `arm64-v8a`, so between them they
  cover the API the app targets and the ABI it ships. Step 5 has somewhere to
  land, and a regression in the physics can be caught before the phone.
- **Step 3's foundations.** A formula can be parsed and resolved against the
  installed sets (`core/notation`), graphed exactly — checked against the
  evaluator itself by rolling small formulas every possible way
  (`core/probability`) — planned against the tray's capacity rule, settled and
  read face by face (`simulation/api`), watched by a renderer that cannot
  touch it (`render/headless`), thrown by a shake that replays to itself
  (`input/shake`) and written down in one transaction (`core/stats`, `data`).
  A package from a stranger is validated rule by rule with a `file:line`
  report (`dicesets/format`) and installed without leaving anything behind if
  it fails (`dicesets/install`); the bundled dice go through that same
  validator on every launch. Every catalogue solid carries its corners as well
  as its face normals, from one construction — which is how two geometry bugs
  were found.
- **The physics engine is decided and wired up:** Jolt 5.3.0, chosen by
  building both candidates against this project's own toolchain rather than by
  reading about them (`docs/architecture.md`, decision 37), and now a working
  bridge. Dice spawn on a staggered grid, are shaken by an inverse acceleration
  rather than by a moving tray, settle, and are read off their faces. The
  engine is native but every *decision* about a roll is Kotlin over an
  interface (decision 40), so the rule that matters most — nothing touches a
  die that has come to rest — is proved by JVM tests rather than sampled on a
  phone. Built for `arm64-v8a` and `x86_64` on every CI run.
- **Rolls are written down and asserted.** Ten (seed, formula, input) cases
  carry what they came to — the scale, the throw handed to the engine, the
  faces, the steps, the corrections, the re-throws. The half of the chain CI
  can reach is checked on every pull request and the whole of it on each ABI,
  and the emulator and the Pixel 10a agree bit for bit, digest included. The
  trigonometry upstream of the engine went to `StrictMath` first, so that
  agreement rests on a guarantee rather than on two libms happening to match
  (`docs/architecture.md`, decisions 43 and 44).

## In progress

- **Step 4.1, the roll screen.** First piece: a roll can now be *watched*. The
  loop steps one step at a time and a frame clock decides when, so a rendered
  roll is the same object as a power-saving roll with somebody calling it —
  rather than a second implementation of the one rule the app cannot bend
  (`docs/architecture.md`, decision 48). What is still missing before the phone
  can be shaken is a surface to draw on and a screen to put it in.

## Blocked / waiting on

- Nothing.

## Decisions pending

- Two smaller decisions from the prototype are not yet in `docs/` (designer
  3D preview, picker remembering the last set per group) — see `docs/TODO.md`.

## Known risks

- The "no invisible hand" bar — zero post-rest corrections, zero stacked dice
  — is the hardest thing in the plan and can only be judged on a device. If
  prevention cannot get there, the fallback is a visible re-throw, which is
  honest but must not become common.
- Physics determinism across ABIs is no longer assumed *or* sampled: the golden
  suite asserts the same faces, step counts and correction counts on the
  emulator (`x86_64`, API 36) and the Pixel 10a (`arm64-v8a`, API 37), and
  fails if either moves. What is still unproven is determinism across *devices*
  of the same ABI and across time, which is the same suite run somewhere else.
- Table capacity constants (30 % floor, 0.40 minimum scale) were worked out on
  paper. First evidence is good: 20 d20s at the scale the rule picks (0.73)
  settle on the Pixel 10a in 89–132 steps with no forced settles. Twenty dice
  at *full* size, which the rule would refuse, need eight or nine re-throws —
  which is the rule earning its keep.
- **The correction ladder leans on corrections far too hard.** Nine of twenty
  dice get a nudge, against a budget of one in two hundred. Every one of them
  lands while the die is still moving and post-rest corrections are zero, so
  the honest rule holds — but Step 5.5 is where the prevention has to get good
  enough that the ladder is rarely reached at all.
- The container's emulator is an API 36 automated-test image, so it has no
  real GPU and is one API below `targetSdk`. It answers "does this run", not
  "does this look right" — the phone remains the only answer to the second.
