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
- **Delete the local branch once its PR is merged.** Pull `main`, then
  `git branch -d <branch>` — it refuses anything not fully merged, so it can
  only remove work that is already on `main`. Do this as part of finishing
  the PR, not as an occasional tidy-up: a list of stale branches makes it
  hard to see what is actually in flight.

## Tracking files

- `docs/TODO.md` is the implementation plan and the list of open tasks;
  `docs/STATUS.md` holds the current state of the project (phase, what is
  done, in progress, blocked, pending decisions).
- Update them as part of the work, not afterwards: starting a task moves it
  to "In progress" in `docs/STATUS.md`; finishing it removes it from
  `docs/TODO.md` and
  moves the status line to "Done" (or drops it once it is old news).
- **Compact and clean both files regularly** — at least whenever a step
  completes or a PR touches them. Remove finished items, merge duplicates,
  drop stale "Done" entries that git history already records. `docs/TODO.md`
  is as long as the remaining plan needs, and gets shorter as steps are
  deleted; `docs/STATUS.md` stays at roughly one screen. Neither is a
  changelog — git history is.
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
  developer's machine, from inside the devcontainer — on the emulator that
  ships in the image (`dinfinity-emulator`, needs `/dev/kvm`) or against the
  phone over wireless debugging (`dinfinity-phone`, `docs/build-setup.md`).
- **Run both tiers yourself.** The emulator first, because it is a minute
  away and catches most of what breaks; the phone when the emulator cannot
  answer the question — a real GPU, real sensors, real timing, an API above
  the emulator image's. Do not ask the user to run a suite that adb can run.
  `dinfinity-phone` reconnects to the address it last used, so the only thing
  the user is ever needed for is switching wireless debugging on and reading
  the new address off the screen after the phone has rebooted. Ask for that
  when it is needed, and say why.
- Never claim a device run passed without having seen its output.
- What is genuinely left for the user is judgement, not execution: whether a
  shake feels like a shake, whether the dice look like dice, whether a texture
  reads at arm's length, whether the haptics land. Ask for those, and say
  exactly what to look for.

### Coverage

- Measure **function and branch** coverage, not lines alone — JaCoCo's
  `METHOD` and `BRANCH` counters, merged across JVM and Robolectric runs and
  published to SonarQube. Line coverage rewards code that is merely executed;
  branch coverage is what says the error path was tried, and function
  coverage is what catches the helper nobody ever calls from a test.
- **Coverage must not sink in a PR.** CI compares both counters against
  `main` and fails on a drop in either. Shipping untested code next to tested
  code is not a trade — write the tests in the same PR.
- A drop is occasionally legitimate (deleting well-covered code, or a
  refactor that moves logic behind the device-only line). Then say so in the
  PR description with the numbers; do not lower the threshold to make the
  check pass.
- Code that can only be exercised on a device (the physics bridge, the
  renderer) is excluded from the coverage figure but **not** from static
  analysis, and its device test results are reported separately — the gap
  should be visible, not hidden by an exclusion that quietly counts as
  covered.

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
- **Nothing touches a die that has come to rest.** No impulse, no tray tilt,
  no snapping to a face. Dice are kept from stacking by prevention and by
  corrections applied while they are still moving; a die that ends up cocked
  is re-thrown visibly. A settled die that twitches is a bug
  (`docs/physics-and-rendering.md`).
- Run the linters and static checks (ktlint/detekt, Android Lint) before
  opening a PR and keep them clean. New warnings are not acceptable in a PR.

## Environment

- Development happens in the **devcontainer**; it has the JDK, Android SDK,
  NDK, Gradle, linters and everything else needed. Do not install tools on
  the host or assume host tools exist.
- The container has `adb`. A phone is attached over **WiFi debugging** and
  paired from inside the container, so `connectedAndroidTest` runs there like
  any other Gradle task; `docs/build-setup.md` has the procedure. USB
  passthrough is not used — it is awkward in a container and wireless is
  enough.
- Signing keys are never committed. `keystore/keystore.properties` and the
  keystores it points at are gitignored; without them the build still works,
  producing default-signed debug builds and unsigned release builds.
