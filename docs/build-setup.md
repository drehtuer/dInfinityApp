# Build setup

> **Design:** nothing here affects the UI — this document is about the
> toolchain. For the app itself, start at [../README.md](../README.md).

Everything is built inside the devcontainer. Nothing is expected on the host
but Docker: no JDK, no Android SDK, no Gradle. That is deliberate — a build
that only works on one laptop is a build nobody else can reproduce.

## What the container has

| Tool | Version | Why |
| --- | --- | --- |
| Ubuntu | 26.04 LTS | Base image |
| OpenJDK | 21 | Gradle and the Kotlin compiler |
| Android SDK platform | newest stable minor of API 37 | `compileSdk` / `targetSdk` |
| Android build-tools | newest for API 37 | aapt2, d8, apksigner |
| `adb` (platform-tools) | newest | Talking to a phone over WiFi debugging |
| Android NDK + CMake | pinned: 30.0.16248370 and 4.1.2 | The physics engine and the renderer are native (`simulation/jolt`, `render/filament`), so the NDK is not optional — it is only switched off for a quick image with no native code in it. These two are *pinned* rather than resolved, unlike everything else in this table: see [The native build](#the-native-build). `cmake` and `ninja` are on `PATH`, so a native build can be driven by hand as well as by Gradle |
| Android emulator + one system image | newest automated-test image, API 36 | The middle testing tier: faster to reach than a phone, and the only place a regression in the physics is caught before one (Step 5.1). See [The emulator](#the-emulator) |
| Gradle | 9.7.1 | Also present as the wrapper in the repository |
| ktlint, detekt | via Gradle | Style and static analysis |
| sonar-scanner | 7.3 | Coverage and quality gate |

The SDK packages are *resolved* at image build time from what Google's
repository offers, not pinned by name — with one exception, the NDK and CMake,
for the reason in [The native build](#the-native-build). Platform packages have been
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

The emulator and its system image are another ~2.5 GB and come off the same
way:

```sh
docker build --build-arg INSTALL_EMULATOR=false -t dinfinity-dev .devcontainer
```

Both off gives an image with no native toolchain and no device tier — enough
for documentation, the JVM suites and the linters, which is most of a day.

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

## The native build

`simulation/jolt` is C++ compiled by the NDK, and it is part of the ordinary
build: `./gradlew build` compiles Jolt from source and links
`libdinfinity_jolt.so` for both ABIs. Nothing has to be run by hand.

The container has what it needs, and so does CI: **NDK 30.0.16248370 and CMake
4.1.2**, pinned in `gradle.properties` as `dinfinity.ndk` and
`dinfinity.cmake`. Pinned rather than resolved, unlike the platform and
build-tools, because this is the compiler that builds the physics engine and a
solver built by a different compiler is a different solver — which is the one
thing goal 4 in `docs/architecture.md` cannot have. The devcontainer takes the
same two values as build arguments and the CI action reads them straight out of
`gradle.properties`, so there is one number to change and three places that
follow it.

Two ABIs are built: `arm64-v8a`, which is the phone and every Android device
that matters, and `x86_64`, which is the emulator in this container. Both,
because "identical outcomes for identical seeds across JVM, emulator and
device" is a release blocker and cannot be checked on an ABI nobody built.

Jolt itself is fetched by `src/main/cpp/CMakeLists.txt` from its release
tarball at a pinned tag **and a pinned SHA-256**, the way every other
dependency in this project is pinned (`docs/architecture.md`, decision 28). A
tag is a label somebody can move; a digest is not, and the engine that decides
every roll is a poor place to make an exception. Each ABI fetches into its own
build tree, which is CMake's default and is not worth being clever about: one
source tree shared between two Android toolchains means one precompiled header
shared between them too, and the build stops with *"AST file was compiled for
the target x86_64 but the current translation unit is being compiled for
aarch64"*. A second download is cheaper than that sentence.

Three settings in that file are load-bearing.

`CROSS_PLATFORM_DETERMINISTIC=ON` is the whole reason this engine was chosen
(`docs/architecture.md`, decision 37). Without it the golden determinism suite
is asserting nothing.

`CMAKE_BUILD_TYPE=Distribution` is forced for **every** variant, the debug
build included. An unoptimised solver is not a slower version of the same roll:
it is too slow to step 120 Hz on a phone, so the emulator and device tiers
would be judging something the release build never does (decision 42).

`-Wno-overriding-option` is a workaround, and it is here rather than in a
comment nobody reads because the failure is baffling without it. Jolt's
deterministic build passes both `-ffp-model=precise` and `-ffp-contract=off`;
Clang 21, which is what NDK 30 ships, warns that the second overrides the
first, and Jolt builds with `-Werror`. The build stops with:

```text
clang++: error: overriding '-ffp-model=precise' option with '-ffp-contract=off'
         [-Werror,-Woverriding-option]
```

Both flags are doing the same job — keeping the compiler from reassociating
floating-point arithmetic — so silencing the overlap changes nothing about the
determinism they exist for.

### Building Jolt by hand

Rarely needed, but the container has `cmake` and `ninja` on `PATH` and this is
the line the engine spike was decided on:

```sh
cmake -S <jolt>/Build -B <build> -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_HOME/ndk/<version>/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-36 \
  -DCMAKE_BUILD_TYPE=Distribution \
  -DCROSS_PLATFORM_DETERMINISTIC=ON \
  -DCMAKE_CXX_FLAGS="-Wno-overriding-option" \
  -DTARGET_UNIT_TESTS=OFF -DTARGET_HELLO_WORLD=OFF -DTARGET_PERFORMANCE_TEST=OFF \
  -DTARGET_SAMPLES=OFF -DTARGET_VIEWER=OFF
```

### The simulation's units are centimetres, not metres

Written down here because it is the kind of thing that gets "tidied" back:
`simulation/jolt` converts the app's millimetres to **centimetres and grams**,
not to SI. Jolt's tolerances are absolute numbers tuned for objects about a
metre across, and one of them cannot be configured at all — it tests a body's
inertia tensor against a hard-coded `1e-12` and silently replaces anything
smaller with the inertia of a **sphere a metre across**. A 16 mm die falls under
it, friction can then no longer take the spin out of a die, and rolls never end.
The symptom is dice that slide and spin for ever and look exactly like broken
friction. See `docs/architecture.md`, decision 41.

## The emulator

The middle testing tier runs *inside* the container. Nothing is installed on
the host and nothing is shared with it except one device node.

```sh
dinfinity-emulator &          # creates the AVD the first time, then boots it
dinfinity-await-device        # waits until it can actually be installed on
./gradlew connectedDebugAndroidTest
```

From a cold start that is about thirty seconds to a ready device and another
ten to a green suite.

### It needs `/dev/kvm`

Without it the emulator interprets every instruction and takes minutes to boot,
if it boots at all — so `dinfinity-emulator` refuses to start rather than
appear to hang. The devcontainer passes the device through in `runArgs`; by
hand it is:

```sh
docker run --rm -it --device=/dev/kvm \
  -v "$PWD":/workspace -v dinfinity-gradle:/home/dev/.gradle \
  -v dinfinity-android:/home/dev/.android \
  -w /workspace dinfinity-dev bash -lc 'dinfinity-post-create; exec bash'
```

`dinfinity-post-create` is what the devcontainer runs on creation. Its job here
is one line: the kernel checks the *numeric* group of `/dev/kvm`, and that
number is the host's, which no image can know in advance — so the `kvm` group
inside the container is moved onto it. (The emulator separately reads
`/etc/group` looking for the group by name, and reports `LINE_NOT_FOUND`
rather than anything about permissions when it is missing. Both are handled.)

A shell that was already open when the group was renumbered keeps the
membership it started with — group membership is fixed when a session begins
and cannot be changed underneath it — so the emulator would fail in it with a
complaint about `/etc/group` that is, by then, wrong. `dinfinity-emulator`
checks whether it can actually open `/dev/kvm` rather than whether the file
exists, and says to open a new terminal.

**A host with no `/dev/kvm` cannot start the devcontainer at all** — Docker
refuses a device that is not there. On such a machine, delete the `runArgs`
line from `devcontainer.json` and build with `INSTALL_EMULATOR=false`.

### Why the emulator is an API 36 test image

The image is resolved at build time, like everything else in the SDK, but with
a preference that matters: an **ATD** ("automated test device") image first,
even if that means one API below `ANDROID_API`. An ATD image carries no
launcher, no wallpaper and no Play services and is built to be driven headless.

That is not a nicety. API 37's `google_apis` image crashes `surfaceflinger` in
its `RegionSampling` thread under software rendering with no window, and takes
the framework down with it; the install that follows fails with

```text
adb: failed to install …: cmd: Can't find service: package
```

which says nothing whatever about the cause. The ATD image at API 36 boots in
half a minute and runs the suite in nine seconds.

Testing one API below the target is a trade, and the same one this project has
already made for Robolectric (`docs/architecture.md`, decision 17): API 36 is
the app's own `minSdk`, so it is a device the app has to work on regardless,
and the API it *targets* is covered by the phone. The fallback stops at
`ANDROID_MIN_API`, so it can never slide further than that.

The AVD is built on a Pixel 9 profile — the closest the SDK ships to this
project's reference device, and the same screen shape, which matters here
because the tray *is* the screen (`docs/tables.md`). It lives in the
`dinfinity-android` volume rather than in the image, so it survives a rebuild;
if the image is rebuilt onto a different system image, the AVD is replaced
rather than left to fail puzzlingly.

### What it cannot tell you

It is headless and software-rendered. That is fine for physics, determinism and
the instrumented suites, and useless for judging how the dice *look* or how a
shake *feels*. Those need the phone (`docs/TODO.md`, Step 5.6).

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
./gradlew connectedDebugAndroidTest   # on the emulator or a phone
```

**Which API Robolectric runs against is said once**, in `gradle.properties` as
`dinfinity.robolectricSdk`, and written into every Android module's test
resources by the convention plugins. It has to be the same everywhere and it
cannot be the API the app targets: Robolectric's `InputManager` shadow still
calls `InputManager.getInstance()`, which API 37 removed
(`docs/architecture.md`, decision 17).

A module that simply had no such file did not fail with anything about SDKs —
it failed with `NoSuchMethodException: InputManager.getInstance()` in tests
that have nothing to do with input, which is a long way from "this module is
missing a properties file". Six modules carried identical copies before this
was generated; the seventh is what found out.

CI runs everything except the last line — including the native build, so
`simulation/jolt` is compiled for both ABIs on every pull request. It costs a
few minutes of NDK download and about a minute of Jolt, and it buys the one
thing CI can give native code: a build that cannot rot between runs on real
hardware. Instrumented tests need a device: the
[emulator](#the-emulator), which is in the container and is a minute away, or
[the phone](#connecting-a-phone-over-wifi), which answers what the emulator
cannot — a real GPU, real sensors, real timing, the API the app targets.

```sh
dinfinity-emulator &   # boots in ~30 s; needs /dev/kvm
dinfinity-phone        # attaches the phone over wireless debugging
```

Whichever is attached is the one they run on, and if both are, both run them
— [choose with `ANDROID_SERIAL`](#when-both-a-phone-and-the-emulator-are-attached).

### What the renderer's device test can and cannot say

`render/filament` has an instrumented suite, and it is deliberately narrow. It
asks whether the native library loads, whether the material compiles **on this
driver** — which is the thing compiling at launch both buys and costs
(`docs/architecture.md`, decision 46) — whether the buffers make a scene
Filament accepts, and whether a frame comes back with more than one colour in
it. That last one is the cheapest thing that notices a scene which builds,
draws, reports no error and shows nothing: a camera pointing the wrong way, a
mesh wound inside out, a material that compiled to black.

The other half of that suite is the tray on a real thread. It asks whether a
background thread gets vsync callbacks at all, whether Filament accepts a
`Surface` belonging to something else, whether a whole roll is drawn *frame by
frame* rather than in one callback, whether a resize replaces the engine
without stopping the roll, and whether a surface withdrawn mid-roll leaves
nothing holding it — the one that crashes if it is got wrong, because a
`Surface` may not be touched once the callback that withdrew it has returned.
The surface is an `ImageReader`'s rather than a `SurfaceView`'s: a
`SurfaceView` needs a window and a window needs an activity, and from
Filament's side a surface is a surface. What it buys is the last assertion —
a frame can be taken off the other end, which is as close as an automated test
gets to "it appeared".

Both tiers pass that, including the frame arriving, so the emulator's
software backend *can* deliver to a real surface. Its limitation below is
specific to reading a headless swap chain back.

What none of it can say is whether the picture is any *good*. Nothing automated
can. That is Step 5.6, and it needs a screen and a person.

One wrinkle is worth knowing rather than rediscovering. The frame is read back
with Filament's **post-processing turned off**, and only there. Filament renders
the post pass to an offscreen target and blits it, and on the emulator's
software backend (SwiftShader, feature level 1) that blit never reaches a
readable headless swap chain: every pixel comes back opaque black while the
same scene draws correctly on the Pixel 10a. Reading the frame before the post
pass asks the question the test is for — was anything drawn — on both tiers
rather than on one. A headless swap chain also has to be created with
`CONFIG_READABLE`; without it a real driver may hand the pixels over anyway,
which is how that would have shipped unnoticed.

### Re-recording the golden cases

`test-fixtures/src/main/resources/fixtures/golden/cases.tsv` holds what each
(seed, formula, input) triple came to, and two suites assert it: the JVM half
(`GoldenCasesTest`) checks everything the engine is handed, the device half
(`GoldenDeterminismTest`) checks what it did with it
(`docs/physics-and-rendering.md`, "Timestep and determinism").

A deliberate change to the physics, the spawn or the capacity rule moves those
numbers, and the fixture is then re-recorded rather than edited:

```sh
adb logcat -c
./gradlew :simulation:jolt:connectedDebugAndroidTest      # fails, and records
adb logcat -d -s dinfinity.golden:I -v raw | grep -P '^\d+\t'
```

Every run logs one ready-made line per case, matched or not, so the last
command prints the new file body to paste in under the comment block. Record on
**one** device and then run the suite on the other: the point of the fixture is
that the emulator and the phone agree, and recording separately on each would
be two fixtures that can never disagree.

The diff is the review. A changed `spawn` column means something upstream of
the engine moved; changed `faces` or `steps` mean the physics did. Either can
be right — what is not right is either of them changing without anyone
noticing.

### The verdict on an instrumented run

`connectedDebugAndroidTest` is followed by `verifyDeviceTestResults`, which
reads the JUnit XML the run produced and fails the build if anything in it
failed, erred, or if the run produced no results at all. A module with no
`src/androidTest` at all is expected to produce nothing and says so.

That indirection is a workaround for a bug in AGP 9.4.0, not a softening of the
check. AGP keys its per-device verdict by the device id it pulls back out of the
JUnit unique id — but JUnit percent-escapes a unique id segment, so a phone
attached as `192.168.89.49:39337` is recorded under
`192.168.89.49%3A39337` and then looked up under its raw serial. The lookup
misses and the run is declared failed however green the tests were. Every
wireless device has a colon in its serial, so the task can never pass on its
own here — the phone run above printed `There were failing tests` with all
three passing, which is the bug in one line. `ignoreFailures` is therefore set on it and the XML — which is
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
dinfinity-phone                      # reconnect to the address last used
dinfinity-phone 192.168.1.42:39337   # a new address; it is remembered
./gradlew connectedDebugAndroidTest
```

`dinfinity-phone` connects, waits until the phone can actually be installed on,
and writes the address into the `dinfinity-android` volume, so every later
session is the bare command. An address is only typed again when the phone has
rebooted or wireless debugging has been switched off and on, because the port
changes then.

**It also keeps the screen on**, which is not a convenience. A phone sleeps
after a minute and a full device run takes four, so everything that runs after
the screen goes dark fails the same way — *no compose hierarchies found in the
app*, because an activity launched onto a sleeping display never composes. That
is worse than an ordinary flake: it lands on whichever module happens to run
last, so it reads as a different bug each time, and it is not a bug in the code
under test at all. It is the same switch as **Developer options → Stay awake**,
and unplugging the phone undoes it.

Pairing is separate, and needed once per machine:

```sh
# "Pair device with pairing code" shows a DIFFERENT ip:port, and a code.
dinfinity-phone pair 192.168.1.42:37105 832269
```

This is the procedure as run, not as imagined: a Pixel 10a on Android 17,
paired and driven from the container over the default Docker bridge.
`dinfinity-phone 192.168.89.49:40697` attached it, `connectedDebugAndroidTest`
installed and ran, and the JUnit XML came back `tests=3 failures=0 errors=0`.

That run is also the one the emulator cannot stand in for: the phone is
**API 37 on `arm64-v8a`**, the API the app targets and the ABI it ships, where
the container's image is API 36 on x86_64. Both tiers run the same suite; only
one of them runs it on what a user will hold.

### When both a phone and the emulator are attached

A run that does not choose runs on both, and every plain `adb` command fails
with `more than one device/emulator`. `ANDROID_SERIAL` chooses, for adb and for
Gradle alike:

```sh
export ANDROID_SERIAL=192.168.1.42:39337
```

That it reaches AGP is checked rather than assumed: with the emulator attached
and `ANDROID_SERIAL` naming a device that is not, `connectedDebugAndroidTest`
runs no tests at all — and says so, because `verifyDeviceTestResults` refuses
an empty result rather than passing it.

`dinfinity-phone` prints the line to export when it sees the emulator running.
It cannot set the variable itself: it is a child of the shell that would need
it.

Notes:

- Pairing and connecting use **different ports**. The pairing port changes
  every time the dialog is opened; the connect port is stable per session.
- **`adb connect` failing while the port is plainly open means you are
  pointing at the pairing port.** Check the port is reachable at all with
  `bash -c 'cat < /dev/null > /dev/tcp/<ip>/<port>'`; if that succeeds and
  `adb connect` still says "failed to connect", the port speaks the pairing
  protocol and wants `adb pair` and a code, not `adb connect`.
- The phone and the container must be on the same network. With Docker's
  default bridge that works out of the box; the container reaches the LAN even
  though the LAN cannot reach it.
- **A phone reached over a VPN does not stay reached.** It is worse than not
  working, because it does work at first: `adb connect` succeeds, an APK
  installs, `connectedDebugAndroidTest` runs and passes. Then the port starts
  refusing, mid-session, with the phone untouched and wireless debugging still
  switched on — and the only cure is a new address off the phone's screen,
  which buys another few minutes. Treat the VPN route as unsupported rather
  than flaky: put the phone and the container on the same LAN for anything
  that has to finish, and do not read a drop as the phone having gone to
  sleep.
- mDNS discovery (`adb mdns services`) runs but finds nothing through the
  bridge, which does not carry multicast — which is why the addresses above
  are typed by hand. `dinfinity-phone` asks anyway when it has no address to
  try, since it costs a second and is the one route that needs no typing.
  **Do not reach for `--network=host` to fix it here:** on Docker Desktop
  under WSL2 the container then shares the host's port 5037 with Windows' own
  adb server, and every `adb` command hangs instead of answering.
- Keep the `dinfinity-android` volume mounted at `/home/dev/.android`: it holds
  the adb key the phone authorises and the remembered address, so you approve
  the connection once instead of on every container start.

## Static analysis and coverage

```sh
./gradlew ktlintCheck detekt lint   # style, static analysis, Android lint
./gradlew ktlintFormat              # fix what can be fixed automatically
```

**Build the release variant before opening a pull request**, not just the debug
one:

```sh
./gradlew assembleRelease           # R8 runs here and nowhere else
```

R8 only shrinks the release build, and it fails on a class it can see
referenced and cannot find — which is a thing a *new dependency* can cause
without a line of new code being wrong. Nothing in the debug build will tell
you. CI runs `build`, so it catches this; a stacked pull request based on
another branch does not get CI at all until it is retargeted, so on a stack it
is the developer's to run.

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
| --- | --- | --- |
| Pure Kotlin (`core/*`, `simulation/api`, …) | Gradle's `jacocoTestReport` | `build/reports/jacoco/test/jacocoTestReport.xml` |
| Android (`app`, `data`, `feature/*`, …) | AGP's `createDebugUnitTestCoverageReport` | `build/reports/coverage/test/debug/report.xml` |

`coverageReport` is the one name that works in either, so CI and a developer run
the same command. The reports are written one at a time, build-wide: JaCoCo's
HTML formatter shares an open jar of static resources between concurrent report
tasks, and they close it under each other. Writing a report takes milliseconds,
so nothing is lost by queueing them. AGP builds the Android report rather than a hand-written
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
| --- | --- | --- |
| `ci.yml` — Build, test and analyse | PR, push to `main` | `./gradlew build test coverageReport lint detekt ktlintCheck`, the whole JVM and Robolectric suite plus every linter and the repository invariants below, then the SonarQube scan and its quality gate |
| `ci.yml` — Device tests compile | PR, push to `main` | `assembleDebugAndroidTest`. The instrumented suite **cannot run here** — it needs the phone — so CI at least proves it still compiles rather than letting it rot between runs on real hardware |
| `ci.yml` — Dependency review | PR | Fails a pull request that introduces a dependency with a known moderate-or-worse advisory |
| `ci.yml` — Submit dependency graph | push to `main` | Sends the *resolved* Gradle graph to GitHub, so Dependabot alerts see transitive dependencies and not just what the version catalog names |
| `ci.yml` — Documentation | PR, push to `main` | markdownlint over every document, and every mermaid fence parsed by `mermaid-cli`. These two need Node and a headless browser, which the devcontainer does not carry for one linter and one diagram, so unlike the invariants above they run only here |
| `release.yml` | tag `vX.Y.Z` | The full check suite, then a signed release APK attached to a GitHub Release with its SHA-256. Refuses to republish an existing release, refuses a tag that disagrees with `version.txt`, and refuses an APK not signed by the release key |
| `pages.yml` | push to `main` touching docs, design or the site config | Publishes `docs/` and `design/` to GitHub Pages, so the prototype opens from a link instead of a clone |
| `dependabot-metadata.yml` | PR opened by Dependabot | Regenerates `gradle/verification-metadata.xml` for the bumped dependency and commits it to the branch |
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
| --- | --- |
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

A Dependabot pull request changes which artifacts the build resolves, so it
would fail until someone regenerated the file by hand. That is the check
working — a changed artifact is supposed to stop the build — but it is not work
worth doing by hand every week, so
[`.github/workflows/dependabot-metadata.yml`](../.github/workflows/dependabot-metadata.yml)
does it: it regenerates the file on the branch and commits it.

It runs as two jobs, and the split is the point. Regenerating the metadata means
running the project's build, and a build runs code from the branch — so that job
has **no write access**. The job that does have write access never checks out
the branch and never runs anything from it: it takes the finished file as an
artifact and commits it through the contents API. CodeQL flags the one-job
version of this, rightly.

One wrinkle is worth knowing rather than puzzling over. GitHub will not let a
workflow set itself off again, so events caused by `GITHUB_TOKEN` are treated
specially: for a pull request updated this way, the resulting run is created in
an **approval-required** state. The pull request shows an *Approve workflows to
run* banner in the merge box, and someone with write access presses it. The
metadata commit is correct either way — the checks are waiting to be allowed to
start, not failing.

(That is what GitHub's documentation describes. No Dependabot pull request has
opened since this workflow landed, so it has not yet been watched happening
here.)

To skip that press, add a fine-grained personal access token with
**contents: write** on this repository as a **Dependabot secret** named
`DEPENDABOT_METADATA_TOKEN` (Settings → Secrets and variables → Dependabot — not
the Actions secrets, which a Dependabot-triggered run cannot read). A token that
is not `GITHUB_TOKEN` is not subject to the rule above, so the checks start on
their own.

It has to be a *separate* credential to be worth anything. `GITHUB_TOKEN` is
already what the workflow falls back to when the secret is absent, and it is
minted per run and expires with it, so there is no value to copy into a secret
even if it would help.

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
