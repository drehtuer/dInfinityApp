# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-12

## Where we are

- **Phase:** implementation, Steps 1 and 2 of `docs/TODO.md` done. The app
  builds, tests, lints and releases; one screen exists, no dice yet.
- **Latest release:** `v0.0.1` — the skeleton, cut mainly to prove the
  release pipeline. Signed, fingerprint-checked, published with its SHA-256.
- **Branch state:** PRs #1–#25 merged. Step 2 (CI) is done bar two entries
  waiting on other projects and one optional secret; Step 3 is next.

## Done

- Design documentation written: `README.md`, `docs/` (architecture,
  physics and rendering, dice notation, dice sets, tables, probability, face
  designer, statistics).
- Working agreements in `.claude/CLAUDE.md`.
- License chosen: GPL-2.0-or-later.
- UI prototype for every v1 screen, imported from Claude Design into
  `design/`, cross-referenced with `docs/` in both directions and linked
  from `README.md`.
- The design's decisions are in `docs/`: v1's shape catalogue closed at eight
  solids (no d3, no d30, no author meshes), `d%` an alias for `d100`, seeds
  never exposed and no replay, tables global, import refuses duplicate
  groups, per-throw rounding override, manual-only power-saving.
- Implementation plan in `docs/TODO.md`: skeleton, CI with SonarQube,
  foundations, ten screens, an on-device physics step, release.
- Anti-stacking policy rewritten: nothing touches a resting die; a cocked die
  is re-thrown rather than nudged.
- Skeleton (plan Step 1): devcontainer, Gradle convention plugins, 24 modules
  matching `docs/architecture.md`, 63 tests, Compose theme from the Modernist
  tokens, navigation graph for all ten screens, APK naming. `./gradlew build
  test lint detekt ktlintCheck` is green.
- Build setup: Ubuntu 26.04 devcontainer with `adb` for WiFi debugging,
  `docs/build-setup.md`, signing keys kept out of the repository,
  `SECURITY.md`, `sonar-project.properties`.
- The device tier works end to end: the Pixel 10a is paired from inside the
  container over WiFi, and `connectedDebugAndroidTest` builds, installs and
  runs instrumented tests on it. Step 5 has somewhere to land.
- Real signing keys exist locally and as GitHub secrets; releases sign v2+v3.
- CI (plan Step 2, in part): build, test and every linter on each PR, CodeQL,
  Dependabot, dependency review and the resolved dependency graph.
- SonarQube runs from CI as the `sonar-scanner` CLI, not as automatic
  analysis, and the scanner blocks on the quality gate.
  `sonar-project.properties` is the only Sonar configuration there is.
- Coverage is measured by JaCoCo across the JVM and Robolectric suites and
  published: branch coverage reaches SonarQube and the README badges, function
  coverage only the reports — SonarQube has no counter for it. `verifyCoverage`
  holds both to a floor in `gradle.properties`, so the half SonarQube cannot
  see is still enforced.
- Every dependency is pinned by SHA-256 in `gradle/verification-metadata.xml`,
  verified on a cold cache.
- `main` requires its checks to pass before a merge, and releases are cut by
  `release.yml` from a `vX.Y.Z` tag: signed, fingerprint-checked, immutable.
- The specification is published at
  <https://drehtuer.github.io/dInfinityApp/> — documents and the clickable
  prototype, no clone needed.
- Everything in the repository is now linted by something: Markdown by
  markdownlint, mermaid by `mermaid-cli`, relative links and the docs index by
  Gradle tasks in `check`, and the convention plugins in `build-logic` by the
  ktlint CLI.
- Identity: the `d∞` mark from `design/Logo.dc.html`, as real Archivo outlines,
  in `README.md` and as the launcher icon.
- First working screen, out of plan order because the accent needed somewhere
  to live: Settings picks the accent, DataStore persists it, the theme follows.

## In progress

- Step 3 — foundations, bottom-up. `core/model`, `core/notation` and
  `core/probability` are done: a formula can be parsed, resolved against
  installed sets, graphed exactly and scored, all from a unit test with no UI
  and no physics engine. The graph is checked against the evaluator itself, by
  rolling small formulas every possible way. `dicesets/format` is done too: a
  package downloaded from a stranger is parsed, checked against every rule in
  `docs/dice-sets.md` and either installed or rejected with a `file:line`
  report — and `dicesets/builtin` is now a real `diceset.toml` read through
  that same validator, so the app eats its own dog food on every launch.
  `simulation/api` is done as well: the tray's geometry, the capacity rule
  reproducing every worked number in `docs/tables.md`, settle detection in
  fixed steps, face reading for all eight solids including the d4's vertex, and
  the correction ladder with "nothing touches a die at rest" as a function
  rather than a paragraph.
- The physics engine is decided: **Jolt 5.3.0**, by a spike that built both
  against this project's own toolchain. Jolt builds clean and offers
  cross-platform determinism as a supported mode; Bullet 3.25 does not
  configure with the CMake the Android SDK ships. `simulation/jolt`'s JNI
  bridge is the next piece of work.
- `render/headless` is done: the `Renderer` contract — which returns nothing
  anywhere, so a renderer cannot act on the roll it is watching — and the
  renderer power-saving mode uses, which creates no graphics engine because it
  lives in a module that has none to create.
- `input/shake` is done: the two-threshold shake detector, the gyroscope
  integration that tilts gravity, and the quantised recording that lets a roll
  replay to itself. Everything that decides anything is plain Kotlin; only the
  `SensorManager` wiring is Android, and that is covered by Robolectric.
- `core/stats` and `data` are done: the counters, streaks and histograms
  `docs/statistics.md` describes, folded in one transaction into a Room
  database at version 1, with its schema checked in and a test that refuses a
  version bump without a migration.
- `dicesets/install` is done: source recognition for GitHub, GitLab, Gitea and
  plain archives, an https-only fetch capped by what arrives, streamed
  extraction that refuses every hostile archive `SECURITY.md` names, and an
  install that either happens or leaves the app exactly as it was.
- The catalogue's solids now carry their **corners** as well as their face
  normals — the convex hulls the physics will collide — built from the same
  construction and turned by the same rotation, and checked against each other:
  every face direction has to be the outward normal of a real face of the hull.
  That check found two bugs in the geometry that shipped a step earlier.

## Blocked / waiting on

- Nothing.

## Decisions pending

- Two smaller decisions from the prototype are not yet in `docs/` (designer
  3D preview, picker remembering the last set per group) — see `docs/TODO.md`.

## Known risks

- The "no invisible hand" bar (zero post-rest corrections, zero stacked dice)
  is the hardest thing in the plan and can only be judged on a device. If
  prevention cannot get there, the fallback is a visible re-throw — which is
  honest but must not become common.

- Physics determinism across ABIs/devices is assumed, not yet proven; the
  golden test suite in Milestone 1 is the check.
- Table capacity constants (30 % floor, 0.40 min scale) are guesses until
  tried on the Pixel 10a.
