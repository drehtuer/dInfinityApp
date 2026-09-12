# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-12

## Where we are

- **Phase:** implementation, Step 1 of `docs/TODO.md` done. The app builds,
  tests and lints; no features yet.
- **Latest release:** none.
- **Branch state:** PRs #1–#15 merged. Step 2 (CI) is nearly done.

## Done

- Design documentation written: `README.md`, `docs/` (architecture,
  physics and rendering, dice notation, dice sets, tables, probability, face
  designer, statistics).
- Working agreements in `.claude/CLAUDE.md`.
- License chosen: GPL-2.0-or-later.
- UI prototype for every v1 screen, imported from Claude Design into
  `design/`, cross-referenced with `docs/` in both directions and linked
  from `README.md`.
- The design's decisions are in `docs/`: v1's shape catalogue closed at nine
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

- `docs/TODO.md` Step 2 is essentially done: release-on-tag, the documentation
  site, test annotations and required status checks all landed. What is left is
  one thing to prove (a throwaway tag, to run `release.yml` for real), one to
  automate (regenerating the dependency metadata on Dependabot branches), and
  two waiting on other people (CodeQL's Kotlin support, an AGP fix).

## Blocked / waiting on

- Nothing.

## Decisions pending

- Two smaller decisions from the prototype are not yet in `docs/` (designer
  3D preview, picker remembering the last set per group) — see `docs/TODO.md`.
- Physics engine (Jolt vs. Bullet) — spike planned in Milestone 0.
- TOML parser choice.

## Known risks

- The "no invisible hand" bar (zero post-rest corrections, zero stacked dice)
  is the hardest thing in the plan and can only be judged on a device. If
  prevention cannot get there, the fallback is a visible re-throw — which is
  honest but must not become common.

- Physics determinism across ABIs/devices is assumed, not yet proven; the
  golden test suite in Milestone 1 is the check.
- Table capacity constants (30 % floor, 0.40 min scale) are guesses until
  tried on the Pixel 10a.
