#!/usr/bin/env bash
#
# Throws every catalogue shape a hundred thousand times on the reference
# device and writes what it came to into fairness/record.txt — the record a
# release is checked against (docs/build-setup.md, "Fairness before a release").
#
# CI has no device to throw dice on, so it cannot run FairnessTest. What it can
# do is refuse a tag whose physics has no passing run of its own
# (`./gradlew verifyFairnessRecord`, in the release workflow). This makes that
# run: the physics' fingerprint first, then FairnessTest at the release's roll
# count, then the record, with the verdict the test itself gave. Commit the
# record; a record of physics that has since changed is refused like no record.
#
# About fifteen minutes on the Pixel 10a for the eight shapes.

set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
device="${ANDROID_SERIAL:-}"
rolls=100000
record="${root}/fairness/record.txt"

usage() {
  cat >&2 <<'USAGE'
tools/fairness.sh — the pre-release fairness run, on a device.

  -d, --device <serial>  which device; default $ANDROID_SERIAL, or the only
                         one attached
  -n, --rolls <n>        throws a shape (default 100000, which a release asks
                         for; fewer writes a record the release refuses)
  -h, --help             this
USAGE
}

fail() {
  echo "fairness: $*" >&2
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    -d | --device) device="${2:?}"; shift 2 ;;
    -n | --rolls) rolls="${2:?}"; shift 2 ;;
    *) usage; fail "unknown option $1" ;;
  esac
done

cd "$root"
if [ -z "$device" ]; then
  mapfile -t attached < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  [ "${#attached[@]}" -eq 1 ] || fail "attach exactly one device, or name it with --device (found ${#attached[@]})"
  device="${attached[0]}"
fi
export ANDROID_SERIAL="$device"
model="$(adb shell getprop ro.product.model | tr -d '\r')"
api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Throwing on ${model} (API ${api}), ${rolls} times a shape…"

./gradlew --console=plain -q physicsFingerprint
fingerprint="$(cat build/fairness/fingerprint.txt)"

adb logcat -c
verdict=pass
./gradlew --console=plain \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=de.drehtuer.dinfinity.simulation.jolt.FairnessTest \
  -Pandroid.testInstrumentationRunnerArguments.rolls="$rolls" \
  :simulation:jolt:connectedDebugAndroidTest || verdict=fail

mapfile -t report < <(adb logcat -d -s dinfinity.fairness:I -v raw | tr -d '\r' | grep -v '^-' || true)
[ "${#report[@]}" -gt 0 ] || fail "the device reported nothing; did FairnessTest run?"

commit="$(git rev-parse HEAD)"
git diff --quiet HEAD -- || commit="${commit} (uncommitted changes)"

mkdir -p "$(dirname "$record")"
{
  echo "# Written by tools/fairness.sh on the reference device. Not edited by hand: the release checks it."
  echo "fingerprint: ${fingerprint}"
  echo "rolls: ${rolls}"
  echo "verdict: ${verdict}"
  echo "commit: ${commit}"
  echo "device: ${model}, API ${api}"
  echo "date: $(date -u +%Y-%m-%d)"
  for line in "${report[@]}"; do
    case "$line" in
      "edge rounding: "*) echo "$line" ;;
      *": n="*) echo "shape: ${line}" ;;
    esac
  done
} > "$record"

echo "Wrote ${record#"$root"/} — verdict: ${verdict}."
./gradlew --console=plain -q verifyFairnessRecord && echo "The release would accept it." || true
[ "$verdict" = pass ]
