# Build setup

> **Design:** nothing here affects the UI — this document is about the
> toolchain. For the app itself, start at [../README.md](../README.md).

Everything is built inside the devcontainer. Nothing is expected on the host
but Docker: no JDK, no Android SDK, no Gradle. That is deliberate — a build
that only works on one laptop is a build nobody else can reproduce.

## What the container has

| Tool | Version | Why |
|---|---|---|
| Ubuntu | 26.04 LTS | Base image |
| OpenJDK | 21 | Gradle and the Kotlin compiler |
| Android SDK platform | newest stable minor of API 37 | `compileSdk` / `targetSdk` |
| Android build-tools | newest for API 37 | aapt2, d8, apksigner |
| `adb` (platform-tools) | newest | Talking to a phone over WiFi debugging |
| Android NDK + CMake | newest stable | The physics engine and the renderer are native (`simulation/jolt`, `render/filament`), so the NDK is not optional — it is only switched off for a quick image with no native code in it |
| Gradle | 9.7.1 | Also present as the wrapper in the repository |
| ktlint, detekt | via Gradle | Style and static analysis |
| sonar-scanner | 7.3 | Coverage and quality gate |

The SDK packages are *resolved* at image build time from what Google's
repository offers, not pinned by name. Platform packages have been
minor-versioned since Android 16 (`platforms;android-37.2`, not
`platforms;android-37`), so a hard-coded name rots silently; the image asks for
the newest stable minor of the API in `ANDROID_API` and fails loudly, listing
what is available, if that major does not exist yet.

## First build

With a devcontainer-aware editor, open the repository and let it build the
container. Otherwise:

```sh
docker build -t dinfinity-dev .devcontainer
docker run --rm -it \
  -v "$PWD":/workspace -v dinfinity-gradle:/home/dev/.gradle \
  -w /workspace dinfinity-dev bash
```

Inside the container:

```sh
./gradlew build test lint detekt ktlintCheck
```

That is the same command CI runs, and it is what "green" means. It compiles
every module, runs the JVM and Robolectric suites, and fails on any lint,
detekt, ktlint or compiler warning — warnings are errors here
(`.claude/CLAUDE.md`).

The first run downloads Gradle's dependencies into the `dinfinity-gradle`
volume and takes a few minutes; later runs are seconds.

### A quicker image

The full image is about 7 GB, most of it the NDK. Until `simulation/jolt` and
`render/filament` have their native sources there is nothing for it to
compile, so skipping it gives a 2.1 GB image that is fine for doc or UI work:

```sh
docker build --build-arg INSTALL_NDK=false -t dinfinity-dev .devcontainer
```

The default is `true`, because the physics engine and the renderer are native
and every real build of the app needs it.

### File ownership

The container's `dev` user is uid 1000. If your host uid differs, pass it so
files the build writes belong to you:

```sh
docker build --build-arg USER_UID="$(id -u)" -t dinfinity-dev .devcontainer
```

The image takes that uid over from whoever already holds it — Ubuntu's base
image ships its own `ubuntu` account at uid 1000 — and then asserts that `dev`
really ended up with it. That check is there because the failure it catches is
silent and total: if the account is not created, `USER dev` refers to nobody and
the image cannot start at all, with `unable to find user dev`.

## Building the app

```sh
./gradlew :app:assembleDebug     # dInfinityApp-<version>-debug.apk
./gradlew :app:assembleRelease   # dInfinityApp-<version>.apk, R8-shrunk
```

Both land in `app/build/outputs/named-apk/`. The version comes from
`version.txt` at the repository root — the only place it is written down.

## Signing keys

A fresh checkout builds without any keys: debug builds use the Android default
debug key, release builds come out unsigned. To sign properly, create the two
keystores and tell the build where they are. **Neither file, nor the properties
file, is ever committed** — see [../keystore/README.md](../keystore/README.md).

```sh
# A shared debug key, so debug builds from any machine install over each other.
keytool -genkeypair -v \
  -keystore keystore/debug.keystore \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass android -keypass android \
  -dname "CN=dInfinity Debug, OU=dev, O=dInfinity, C=DE"

# The release key. Keep a backup somewhere safe: losing it means never being
# able to update the published app again.
RELEASE_PW="$(openssl rand -hex 24)"
keytool -genkeypair -v \
  -keystore keystore/release.keystore \
  -alias dinfinity -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "$RELEASE_PW" -keypass "$RELEASE_PW" \
  -dname "CN=dInfinity, O=dInfinity, C=DE"
```

Both keystores are PKCS12, where the key password must equal the store
password. Then write `keystore/keystore.properties` — the build reads it, git
never sees it:

```properties
# Paths are relative to the repository root.
debugStoreFile=keystore/debug.keystore
debugStorePassword=android
debugKeyAlias=androiddebugkey
debugKeyPassword=android

releaseStoreFile=keystore/release.keystore
releaseStorePassword=<the release password>
releaseKeyAlias=dinfinity
releaseKeyPassword=<the release password>
```

```sh
chmod 600 keystore/keystore.properties keystore/*.keystore
```

The debug password is deliberately the well-known `android`: the debug key is
shared so debug builds from any machine install over each other. The release
password is not — generate it, and back up both it and `release.keystore`
somewhere that is not this directory.

### Signature schemes

Releases are signed with **v2 and v3**, set in the app convention plugin. v3 is
the one that matters: it carries the proof-of-rotation record that lets a lost
or compromised release key be replaced without breaking updates for anyone who
already installed the app. AGP does not enable it by default.

v1 is off — it is only read below API 24 and this app starts at 36. v4 is off
because it writes a separate `.apk.idsig` that only speeds up
`adb install --incremental` and would have to travel with every release
artefact.

`apksigner verify --verbose` reports v2 as `false` on these APKs. That is not a
missing signature: with `minSdk` 36 it verifies through v3 alone and does not
consult the v2 block. Pass `--min-sdk-version 24` and both report `true`.

## Running tests

```sh
./gradlew test                 # JVM unit tests and Robolectric, everywhere
./gradlew :core:notation:test  # one module
./gradlew connectedDebugAndroidTest   # on a real phone, see below
```

CI runs everything except the last line. Instrumented tests need a device, and
that device is yours.

### The verdict on an instrumented run

`connectedDebugAndroidTest` is followed by `verifyDeviceTestResults`, which
reads the JUnit XML the run produced and fails the build if anything in it
failed, erred, or if the run produced no results at all.

That indirection is a workaround for a bug in AGP 9.4.0, not a softening of the
check. AGP keys its per-device verdict by the device id it pulls back out of the
JUnit unique id — but JUnit percent-escapes a unique id segment, so a phone
attached as `192.168.89.49:39337` is recorded under
`192.168.89.49%3A39337` and then looked up under its raw serial. The lookup
misses and the run is declared failed however green the tests were. Every
wireless device has a colon in its serial, so the task can never pass on its
own here. `ignoreFailures` is therefore set on it and the XML — which is
correct — decides instead.

Delete `VerifyDeviceTestResultsTask` and the `ignoreFailures` beside it once
AGP compares like with like.

## Connecting a phone over WiFi

The container has `adb`, so on-device tests run from inside it — no need to
bounce through the host. Wireless debugging keeps the phone off USB, which
matters because USB passthrough into a container is awkward on most setups.

On the phone, once: **Settings → System → Developer options → Wireless
debugging**, on.

Then, inside the container:

```sh
# 1. "Pair device with pairing code" on the phone shows an ip:port and a code.
adb pair 192.168.1.42:37105 832269   # or omit the code and be prompted

# 2. The Wireless debugging screen itself shows a different ip:port. Connect:
adb connect 192.168.1.42:39337

adb devices                        # should list the phone as "device"
./gradlew connectedDebugAndroidTest
```

This is the procedure as run, not as imagined: a Pixel 10a on Android 17,
paired and driven from the container over the default Docker bridge.

Notes:

- Pairing and connecting use **different ports**. The pairing port changes
  every time the dialog is opened; the connect port is stable per session.
- **`adb connect` failing while the port is plainly open means you are
  pointing at the pairing port.** Check the port is reachable at all with
  `bash -c 'cat < /dev/null > /dev/tcp/<ip>/<port>'`; if that succeeds and
  `adb connect` still says "failed to connect", the port speaks the pairing
  protocol and wants `adb pair` and a code, not `adb connect`.
- Pairing is needed once per machine. After that `adb connect` is enough, until
  the phone reboots or the port changes.
- The phone and the container must be on the same network. With Docker's
  default bridge that works out of the box; the container reaches the LAN even
  though the LAN cannot reach it.
- mDNS discovery (`adb mdns services`) usually does **not** work through the
  bridge, which is why the addresses above are typed by hand. If you want
  discovery, run the container with `--network=host`.
- Keep the `dinfinity-android` volume mounted at `/home/dev/.android`: it holds
  the adb key the phone authorises, so you approve the connection once instead
  of on every container start.

## Static analysis and coverage

```sh
./gradlew ktlintCheck detekt lint   # style, static analysis, Android lint
./gradlew ktlintFormat              # fix what can be fixed automatically
```

SonarQube analyses the project on every push and pull request. The scan runs
from CI as part of the build job, using the `sonar-scanner` CLI and the single
configuration file [../sonar-project.properties](../sonar-project.properties).
The scanner blocks on the quality gate, so a red gate is a red check.

It used to run as SonarQube Cloud **automatic analysis**, which is switched off
now. Automatic analysis reads `.sonarcloud.properties` and ignores
`sonar-project.properties`, which is how the `design/` prototype came to account
for 211 of the first 229 findings. It also cannot ingest a coverage report at
all, and it ignores `sonar.issue.ignore.*`, so a reviewed-and-accepted finding
could only be clicked away in the web UI instead of being recorded in the
repository. Both files existed for a while and had to be kept in step; there is
now only one.

If a scan ever reports the whole repository as new code, the checkout was
shallow: Sonar reads the git history to decide what "new" means, which is why
the build job clones with `fetch-depth: 0`.

### Coverage

```sh
./gradlew coverageReport            # every module; JVM and Robolectric
```

Coverage is JaCoCo, and the counters that matter are **function and branch**,
not lines (`.claude/CLAUDE.md`). Each module writes its own report and Sonar
merges the list, because the two kinds of module produce coverage differently:

| Module kind | Report task | XML |
|---|---|---|
| Pure Kotlin (`core/*`, `simulation/api`, …) | Gradle's `jacocoTestReport` | `build/reports/jacoco/test/jacocoTestReport.xml` |
| Android (`app`, `data`, `feature/*`, …) | AGP's `createDebugUnitTestCoverageReport` | `build/reports/coverage/test/debug/report.xml` |

`coverageReport` is the one name that works in either, so CI and a developer run
the same command. AGP builds the Android report rather than a hand-written
`JacocoReport` task so that nothing has to hard-code the paths of AGP's
intermediate class directories, which are not API and have moved between
versions.

Device-only code — the physics bridge and the renderer — is excluded from the
*coverage* figure but not from the analysis, and its device results are reported
separately, so the gap stays visible instead of quietly counting as covered.
`tools/` is excluded from coverage on the same principle: it is developer
tooling run by hand about once in the life of the project, not something the app
ships, and its lines said nothing about whether the app is tested while costing
the figure six points.

**SonarQube cannot show function coverage.** Its coverage model has exactly two
counters, lines and conditions; there is no method counter to import, whatever
JaCoCo measures. So `.claude/CLAUDE.md`'s pair is only half met on the server:
**branch** coverage is published as `branch_coverage` and is in the README
badges, while **function** coverage exists only in the JaCoCo reports, where
`./gradlew coverageReport` writes it and the reports CI uploads carry it. Making
it fail a build needs a check of our own — `docs/TODO.md`.

## Continuous integration

Workflows live in `.github/workflows/`. They run the same commands this
document gives a developer, so a green pull request means what a green
terminal means.

| Workflow | Runs | Does |
|---|---|---|
| `ci.yml` — Build, test and analyse | PR, push to `main` | `./gradlew build test coverageReport lint detekt ktlintCheck`, the whole JVM and Robolectric suite plus every linter and the repository invariants below, then the SonarQube scan and its quality gate |
| `ci.yml` — Device tests compile | PR, push to `main` | `assembleDebugAndroidTest`. The instrumented suite **cannot run here** — it needs the phone — so CI at least proves it still compiles rather than letting it rot between runs on real hardware |
| `ci.yml` — Dependency review | PR | Fails a pull request that introduces a dependency with a known moderate-or-worse advisory |
| `ci.yml` — Submit dependency graph | push to `main` | Sends the *resolved* Gradle graph to GitHub, so Dependabot alerts see transitive dependencies and not just what the version catalog names |
| `ci.yml` — Documentation | PR, push to `main` | markdownlint over every document, and every mermaid fence parsed by `mermaid-cli`. These two need Node and a headless browser, which the devcontainer does not carry for one linter and one diagram, so unlike the invariants above they run only here |
| `release.yml` | tag `vX.Y.Z` | The full check suite, then a signed release APK attached to a GitHub Release with its SHA-256. Refuses to republish an existing release, refuses a tag that disagrees with `version.txt`, and refuses an APK not signed by the release key |
| `pages.yml` | push to `main` touching docs, design or the site config | Publishes `docs/` and `design/` to GitHub Pages, so the prototype opens from a link instead of a clone |
| `codeql.yml` | PR, push to `main`, weekly | CodeQL over the workflow files. **Not** over the app's Kotlin: the extractor refuses Kotlin 2.4.20 and fails the build rather than degrading, so it is switched off until the bundle catches up — see the comment in the workflow. detekt, Android Lint and SonarQube cover Kotlin meanwhile |

The JDK, the SDK packages and Gradle come from one composite action,
`.github/actions/setup-android-build`, so CI and CodeQL cannot drift apart. It
reads `dinfinity.androidApi` and `dinfinity.buildTools` straight out of
`gradle.properties` — the same two properties the container image and the
Gradle build read, so there is no fourth place to update an SDK version.

The SonarQube scan is part of the build job rather than a job of its own, so it
reuses that build instead of paying for a second one. It is skipped when
`SONAR_TOKEN` is absent — what a pull request from a fork looks like — because a
missing token should not read as a failed build.

## Repository invariants

Rules that are easy to break are checked by the build rather than by memory,
and all of these run as part of `check`:

```sh
./gradlew verifyModuleGraph     # every module on disk is in settings.gradle.kts
./gradlew verifyDocsIndex       # README.md links every document in docs/
./gradlew verifyDocsLinks       # every relative Markdown link resolves
./gradlew verifySourcesTracked  # git ignores no Kotlin source file
./gradlew verifyCoverage        # function and branch coverage are above the floor
```

`verifySourcesTracked` exists because `build/` in `.gitignore` matches any
directory of that name, and the convention plugins' task classes live in a
Kotlin package called `build` — so they were quietly kept out of the repository
while every machine that already had them kept building fine.

`verifyDocsLinks` checks relative links only. External URLs need the network,
and a link checker that fails without it turns an offline build into a broken
one.

`verifyCoverage` reads the JaCoCo reports and holds **function** and **branch**
coverage to the floors in `gradle.properties`:

```properties
dinfinity.coverage.minFunction=85.0
dinfinity.coverage.minBranch=62.0
```

A floor rather than a comparison against `main`, because the floor works
everywhere — a developer sees the same failure CI does — and because raising it
is a visible line in a diff while lowering it is an argument someone has to make
in the pull request. Recomputing `main`'s coverage to diff against would be
slower, would only work on CI, and would still need someone to read the number.
Device-only modules are left out, exactly as they are from SonarQube's figure.

## Releasing

Push a tag of the form `vX.Y.Z`. Nothing else triggers a release, and the
workflow refuses more than it accepts:

| Refusal | Why |
|---|---|
| A release for the tag already exists | Releases are immutable — never move, delete or re-tag a published version, ship a new one (`.claude/CLAUDE.md`). The tag itself is protected by the repository's `releases` ruleset |
| The tag disagrees with `version.txt` | The APK is named from `version.txt`, so `v1.2.0` around an APK called `dInfinityApp-1.1.0.apk` is a release nobody can reason about. Bump `version.txt` in the commit you tag |
| The APK is not signed by the release key | Checked against the fingerprint in `keystore/release-certificate.sha256`. Without `keystore.properties` the build produces an *unsigned* APK rather than failing, and a release signed with the debug key — or a regenerated one — installs as a different app and can never update anyone |

The signing key is rebuilt from the repository secrets for the length of the
job and removed again whatever happens, including on failure.

One quirk worth knowing if you read the log: `apksigner verify` reports only the
scheme it actually used, and for an APK whose `minSdk` is 36 that is v3 alone —
v2 shows as `false` even though the block is there. The workflow therefore
checks each scheme in the era it governs, verifying v2 with
`--min-sdk-version 24 --max-sdk-version 27`.

The documentation site tracks `main`, not the tag. There is one published site
and it has no versions, so building it per tag would only overwrite it with the
same content.

## Dependency verification

Every dependency the build resolves is pinned by SHA-256 in
[../gradle/verification-metadata.xml](../gradle/verification-metadata.xml), and
Gradle refuses to use an artifact whose checksum does not match. A version
number says which artifact was asked for; a checksum says which one arrived.

**Adding or upgrading a dependency means regenerating it**, or the build fails
with "Dependency verification failed" and the artifact's name:

```sh
./gradlew --write-verification-metadata sha256 \
  build test coverageReport lint detekt ktlintCheck assembleDebugAndroidTest
```

Run it with a **cold** dependency cache, in a throwaway `GRADLE_USER_HOME`:

```sh
GRADLE_USER_HOME=/tmp/cold ./gradlew --write-verification-metadata sha256 …
```

A warm cache does not re-resolve what it already has, so the generated file
silently omits it and the next clean machine — CI, or a new clone — fails on a
POM nobody has seen for weeks. That is not hypothetical: the first generated
file was missing `kotlinx-coroutines-bom`, and passed locally while failing the
moment the cache was empty.

The task list matters for the same reason. It has to resolve every configuration
the build uses, `assembleDebugAndroidTest` included, or the device suite fails on
its own dependencies.

This also means a Dependabot pull request will fail its build until the metadata
is regenerated on that branch. That is the cost of the check, and it is the
point of it: a changed artifact is supposed to stop the build.

## Linting the convention plugins

`build-logic` is linted by the ktlint **CLI**, not its Gradle plugin, and
`check` reaches into that build to run it:

```sh
./gradlew -p build-logic ktlintCheckConventions   # or ktlintFormatConventions
```

The Gradle plugin lints whole source sets, and Gradle generates its plugin
accessors into that build's main source set — tens of thousands of violations in
code nobody wrote, and neither a path filter nor overriding the tasks' source
would keep it off them. The CLI takes explicit file patterns, so it sees the
hand-written files and nothing else.

## Editor settings

`.editorconfig` sets two-space indentation, UTF-8, LF line endings and a final
newline for every file, and `.gitattributes` makes git store and check out
every text file with LF whatever the platform does. Any editor that reads
`.editorconfig` needs no further configuration.
