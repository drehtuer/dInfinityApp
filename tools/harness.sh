#!/usr/bin/env bash
#
# Runs the Step 5 physics harness on a device and prints its pass/fail table.
#
# Build, install, roll N times headlessly, pull the numbers back and score them
# against the targets in `docs/TODO.md` (Steps 5.3 to 5.7). It works the same
# against the emulator that ships in this container and against the phone over
# wireless debugging — which is the point of it: a regression should be caught
# on the emulator, a minute away, before anybody reaches for a phone
# (docs/build-setup.md).
#
# Nothing here decides anything. The comparison and the table are Kotlin, in
# `:simulation:harness`, and are tested on the JVM; the device renders the
# table into a file and this script prints it unchanged. A second copy of the
# comparison written in awk is exactly the thing that would eventually disagree
# with the one in the tests.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The test APK is self-instrumenting: `simulation/jolt` is a library, so its
# androidTest APK is the only APK in the run and its application id is the
# module's namespace with `.test` on the end.
package="de.drehtuer.dinfinity.simulation.jolt.test"
runner="androidx.test.runner.AndroidJUnitRunner"
harness_class="de.drehtuer.dinfinity.simulation.jolt.HarnessTest"

rolls=1000
dice=20
shape="d20"
seed=1
label=""
out="${root}/build/harness"
device="${ANDROID_SERIAL:-}"
build=1

usage() {
  cat >&2 <<'USAGE'
tools/harness.sh — run the physics harness on a device and score it.

  -d, --device <serial>  which device; default $ANDROID_SERIAL, or the only
                         one attached
  -n, --rolls <n>        how many throws (default 1000)
  -c, --dice <n>         dice per throw (default 20)
  -s, --shape <name>     d20, icosahedron or 20 (default d20)
      --seed <n>         the run's base seed (default 1); one number replays
                         the whole run
  -l, --label <name>     what to call the run and its files (default 20d20)
  -o, --out <dir>        where to leave the pulled files
                         (default build/harness)
      --no-build         skip assembling and installing, and run what is
                         already on the device

Examples:

  dinfinity-emulator &                     # the middle tier, in this container
  dinfinity-await-device
  tools/harness.sh -n 200                  # a quick look before the phone

  dinfinity-phone                          # the reference device
  tools/harness.sh -n 10000                # the run Step 5.5 asks for
  tools/harness.sh -n 200 -c 100 -s d4     # the worst case there is

The exit code is the verdict: zero when every target was met.
USAGE
}

fail() {
  echo "harness: $*" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    -d | --device) device="${2:?--device needs a serial}"; shift 2 ;;
    -n | --rolls) rolls="${2:?--rolls needs a number}"; shift 2 ;;
    -c | --dice) dice="${2:?--dice needs a number}"; shift 2 ;;
    -s | --shape) shape="${2:?--shape needs a die}"; shift 2 ;;
    --seed) seed="${2:?--seed needs a number}"; shift 2 ;;
    -l | --label) label="${2:?--label needs a name}"; shift 2 ;;
    -o | --out) out="${2:?--out needs a directory}"; shift 2 ;;
    --no-build) build=0; shift ;;
    *) usage; fail "unknown argument: $1" ;;
  esac
done

command -v adb > /dev/null 2>&1 || fail "there is no adb here; this runs inside the devcontainer (docs/build-setup.md)"

# Which device. Naming one is only necessary when both a phone and the emulator
# are attached, which is a real and easy situation to be in — and an adb
# command that does not choose fails outright when two are (docs/build-setup.md).
if [ -z "${device}" ]; then
  attached=()
  while read -r serial; do
    [ -n "${serial}" ] && attached+=("${serial}")
  done < <(adb devices | awk '$2 == "device" { print $1 }')

  case "${#attached[@]}" in
    0) fail "nothing is attached. Start the emulator with 'dinfinity-emulator &', or attach the phone with 'dinfinity-phone'" ;;
    1) device="${attached[0]}" ;;
    *) fail "more than one device is attached (${attached[*]}); choose one with --device or ANDROID_SERIAL" ;;
  esac
fi

property() {
  adb -s "${device}" shell getprop "$1" 2> /dev/null | tr -d '\r'
}

model="$(property ro.product.model)"
api="$(property ro.build.version.sdk)"
abi="$(property ro.product.cpu.abi)"
[ -n "${model}" ] || fail "${device} did not answer; is it still attached?"

echo "Device:  ${model} (${device}), API ${api}, ${abi}"
echo "Run:     ${rolls} rolls of ${dice}${shape}, seed ${seed}"

if [ "${build}" -eq 1 ]; then
  echo "Building the test APK…"
  "${root}/gradlew" --console=plain -p "${root}" :simulation:jolt:assembleDebugAndroidTest

  apk="$(find "${root}/simulation/jolt/build/outputs/apk/androidTest/debug" -name '*.apk' -print -quit 2> /dev/null || true)"
  [ -n "${apk}" ] || fail "no androidTest APK was built; look above for why"

  echo "Installing $(basename "${apk}")…"
  # -t allows a test APK, -r replaces one already there, and -g grants what the
  # manifest asks for so nothing stops to ask a person.
  adb -s "${device}" install -r -t -g "${apk}" > /dev/null
fi

# Where the run leaves its two files. External rather than internal so they can
# be pulled without root: an app may always read and write its own directory
# under Android/data, and adb may always reach it.
remote_dir="/sdcard/Android/data/${package}/files"

# Anything left from a previous run goes first. Otherwise a run that crashes
# before it writes leaves the last run's table to be printed as if it were this
# one's, which is the worst way for a harness to be wrong.
adb -s "${device}" shell "rm -f ${remote_dir}/harness-*" > /dev/null 2>&1 || true

arguments=(
  -e class "${harness_class}"
  -e harness.rolls "${rolls}"
  -e harness.dice "${dice}"
  -e harness.shape "${shape}"
  -e harness.seed "${seed}"
)
[ -n "${label}" ] && arguments+=(-e harness.label "${label}")

echo "Rolling…"
# `am instrument` rather than `connectedDebugAndroidTest`, and the verdict
# comes from what the run wrote rather than from either of them: AGP cannot
# pass a run on a device whose serial contains a colon, which is every phone
# attached over wireless debugging (docs/architecture.md, decision 19).
set +e
adb -s "${device}" shell am instrument -w "${arguments[@]}" "${package}/${runner}"
instrumentation=$?
set -e

mkdir -p "${out}"
pulled=0
while read -r remote; do
  [ -n "${remote}" ] || continue
  adb -s "${device}" pull "${remote}" "${out}/" > /dev/null 2>&1 && pulled=$(( pulled + 1 ))
done < <(adb -s "${device}" shell "ls ${remote_dir}/harness-* 2> /dev/null" | tr -d '\r')

if [ "${pulled}" -eq 0 ]; then
  fail "the run left no files behind (am instrument exited ${instrumentation}); the output above says why"
fi

echo
for table in "${out}"/harness-*.txt; do
  cat "${table}"
done
echo
echo "Files:   ${out}"

# The verdict is the line the Kotlin wrote, and nothing here second-guesses it.
if grep -qh 'HARNESS VERDICT: PASS' "${out}"/harness-*.txt; then
  exit 0
fi
exit 1
