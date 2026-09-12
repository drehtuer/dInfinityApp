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

SonarQube is configured in [../sonar-project.properties](../sonar-project.properties).
CI runs the scanner with `SONAR_TOKEN` from the repository secrets; the token
is never in the repository. Coverage is reported for **functions and branches**
and may not drop in a pull request (`.claude/CLAUDE.md`).

## Continuous integration

Workflows live in `.github/workflows/`. They run the same commands this
document gives a developer, so a green pull request means what a green
terminal means.

| Workflow | Runs | Does |
|---|---|---|
| `ci.yml` — Build, test and analyse | PR, push to `main` | `./gradlew build test lint detekt ktlintCheck`, the whole JVM and Robolectric suite plus every linter and the repository invariants below |
| `ci.yml` — Device tests compile | PR, push to `main` | `assembleDebugAndroidTest`. The instrumented suite **cannot run here** — it needs the phone — so CI at least proves it still compiles rather than letting it rot between runs on real hardware |
| `ci.yml` — Dependency review | PR | Fails a pull request that introduces a dependency with a known moderate-or-worse advisory |
| `ci.yml` — Submit dependency graph | push to `main` | Sends the *resolved* Gradle graph to GitHub, so Dependabot alerts see transitive dependencies and not just what the version catalog names |
| `codeql.yml` | PR, push to `main`, weekly | CodeQL over Kotlin/Java and over the workflow files themselves |

The JDK, the SDK packages and Gradle come from one composite action,
`.github/actions/setup-android-build`, so CI and CodeQL cannot drift apart. It
reads `dinfinity.androidApi` and `dinfinity.buildTools` straight out of
`gradle.properties` — the same two properties the container image and the
Gradle build read, so there is no fourth place to update an SDK version.

Coverage and the SonarQube quality gate are not wired yet; `docs/TODO.md`
Step 2 has what is left.

## Repository invariants

Three rules that are easy to break are checked by the build rather than by
memory, and run as part of `check`:

```sh
./gradlew verifyModuleGraph     # every module on disk is in settings.gradle.kts
./gradlew verifyDocsIndex       # README.md links every document in docs/
./gradlew verifySourcesTracked  # git ignores no Kotlin source file
```

The last one exists because `build/` in `.gitignore` matches any directory of
that name, and the convention plugins' task classes live in a Kotlin package
called `build` — so they were quietly kept out of the repository while every
machine that already had them kept building fine.

## Editor settings

`.editorconfig` sets two-space indentation, UTF-8, LF line endings and a final
newline for every file, and `.gitattributes` makes git store and check out
every text file with LF whatever the platform does. Any editor that reads
`.editorconfig` needs no further configuration.
