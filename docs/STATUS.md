# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-13

## Where we are

- **Phase:** implementation. Steps 1 and 2 of `docs/TODO.md` are done, Step 3
  is nearly done: everything a formula touches before it becomes a physical
  throw exists and is tested. What is left of Step 3 is the physics bridge,
  the renderer and the golden determinism suite that ties them together.
- **Latest release:** `v0.0.1` — the skeleton, cut mainly to prove the release
  pipeline. Signed, fingerprint-checked, published with its SHA-256.
- **Branch state:** PRs #1–#25 are merged. #28 through #40 are open as a
  stack, each based on the one before it, and they merge in that order.

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
- **All three testing tiers are reachable.** JVM and Robolectric on CI; the
  emulator that ships in the devcontainer, which boots headless in half a
  minute and runs the instrumented suite in ten seconds; and the Pixel 10a,
  paired from inside the container over WiFi debugging. Step 5 has somewhere
  to land, and a regression in the physics can be caught before the phone.
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
- **The physics engine is decided:** Jolt 5.3.0, by building both candidates
  against this project's own toolchain rather than by reading about them
  (`docs/architecture.md`, decision 37).

## In progress

- `simulation/jolt` — the JNI bridge over Jolt: CMake and NDK wiring, Jolt
  vendored at a pinned tag, convex hulls from the shape catalogue, the fixed
  120 Hz step, spawn and shake input, the correction ladder. This is the piece
  that turns `simulation/api`'s contract into dice that actually land.

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
- Physics determinism across ABIs and devices is assumed, not proven; the
  golden suite in Step 3 is the check.
- Table capacity constants (30 % floor, 0.40 minimum scale) are worked out on
  paper and untried on the Pixel 10a.
- The container's emulator is an API 36 automated-test image, so it has no
  real GPU and is one API below `targetSdk`. It answers "does this run", not
  "does this look right" — the phone remains the only answer to the second.
