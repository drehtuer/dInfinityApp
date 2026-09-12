# dInfinity — working agreements

Android dice roller with physics-simulated rolls. Design docs live in
`README.md` and `docs/`; read `docs/architecture.md` before touching module
structure.

## Git workflow

- Never commit directly to `main`. Every feature, fix or doc change goes on
  its own branch and lands on `main` through a pull request.
- When several PRs are produced in one session, **stack them**: base each new
  branch on the previous PR's branch, not on `main`, so they merge in order
  without conflicts. Say in the PR description which PR it is based on.
- Branch names: `feature/<short-slug>`, `fix/<short-slug>`, `docs/<short-slug>`.
- Commit messages: imperative subject, body explaining *why* when it is not
  obvious.

## Tracking files

- `docs/TODO.md` holds open tasks; `docs/STATUS.md` holds the current state
  of the project (phase, what is done, in progress, blocked, pending
  decisions).
- Update them as part of the work, not afterwards: starting a task moves it
  to "In progress" in `docs/STATUS.md`; finishing it removes it from
  `docs/TODO.md` and
  moves the status line to "Done" (or drops it once it is old news).
- **Compact and clean both files regularly** — at least whenever a milestone
  completes or a PR touches them. Remove finished items, merge duplicates,
  drop stale "Done" entries that git history already records, and keep each
  file to roughly one screen. They are snapshots, not changelogs.
- Refresh the `Last updated` date in `docs/STATUS.md` when you change it.

## Documentation

- Documentation is part of the change. A PR that alters behaviour, module
  layout, file formats, limits or defaults updates the matching document in
  `docs/` (and `README.md` if the feature list or platform section is
  affected) in the same PR.
- `docs/architecture.md` must always reflect the real module layout, data
  flow and threading. If the code diverges from it, fix one or the other in
  the same PR — do not leave them apart.
- **Every document in `docs/` is linked from the table in `README.md`.**
  Adding, renaming or removing a document updates that table in the same
  PR; the README is the index and must never be incomplete.
- The prototype in `design/` is the visual half of the specification. It is
  linked from `README.md`, each document in `docs/` links to the screens that
  realise it, and `design/README.md` carries the same map in reverse — a
  change to any of the three keeps the other two true. A design decision that
  changes behaviour, limits or defaults is only done when `docs/` says the
  same thing.
- Diagrams are **mermaid** (` ```mermaid ` fences). No ASCII art, no images
  for things mermaid can draw.

## Testing

- Every new function gets a unit test. Pure Kotlin modules (`core/*`,
  `simulation/api`) are tested on the JVM with plain unit tests.
- UI and Android-framework-dependent code is tested with **Robolectric**.
- Physics/rendering bridges, sensors and anything that needs a real GPU or
  real sensors are tested with instrumented tests on the **emulator** or on
  an **actual device** (reference device: Pixel 10a).
- CI can only run JVM unit tests, Robolectric tests, linters and builds.
  Emulator and on-device tests **cannot run on CI**; they run on the
  developer's machine. When a change needs those, run what you can, then
  ask the user to run the emulator/device suite and report back — do not
  claim they passed.
- If verifying a change requires something you cannot do (a physical shake
  test, checking haptics, judging how a texture looks), ask the user to
  check and say exactly what to look for.

## Releases and builds

- Versions are `vX.Y.Z`, semantic versioning: breaking changes to file
  formats or saved data bump X, new features bump Y, fixes bump Z.
- Pushing a tag of that form triggers the release build of the app and the
  documentation. **Releases are immutable**: never move, delete or re-tag a
  published version; ship a new one instead.
- Release builds are optimised (R8/ProGuard, shrinking, no debug symbols in
  the APK). Development and testing use **debug** builds.
- APK names:
  - debug: `dInfinityApp-<version>-debug.apk`
  - release: `dInfinityApp-<version>.apk`

  `<version>` is the semver without the `v`, e.g. `dInfinityApp-1.2.0.apk`.

## Code

- Prefer existing, well-tested libraries over hand-rolled implementations of
  complex parts (physics, rendering, TOML parsing, archive extraction, image
  decoding, probability convolution helpers). Own code is for the glue and
  the domain logic. Adding a dependency is fine; justify it in the PR.
- Never trust downloaded content: everything from a dice set, table or
  saved-roll collection goes through the validator described in
  `docs/dice-sets.md`. Do not add code paths that bypass it.
- The physics result *is* the roll. Do not introduce any RNG shortcut that
  decides a die's value outside the simulation, in any mode.
- Run the linters and static checks (ktlint/detekt, Android Lint) before
  opening a PR and keep them clean. New warnings are not acceptable in a PR.

## Environment

- Development happens in the **devcontainer**; it has the JDK, Android SDK,
  NDK, Gradle, linters and everything else needed. Do not install tools on
  the host or assume host tools exist.
- The emulator and physical devices are attached from the developer's
  machine, outside the container's control — see Testing.
