#!/usr/bin/env bash
#
# Draws the render gallery on a device and pulls the pictures back.
#
# The same seeded throws on the same tables, settled by the real physics and
# drawn through the real renderer onto a surface the size of the Pixel 10a's
# screen (`RenderGalleryTest`). Nothing is scored: the PNGs are for a person to
# compare before and after a rendering change
# (docs/physics-and-rendering.md, "Rendering (normal mode)").
#
# It installs only `render/filament`'s test APK, which is its own application,
# so the app on the phone and everything saved in it are left alone.

set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
device="${ANDROID_SERIAL:-}"
out="${root}/build/gallery"
build=1

usage() {
  cat >&2 <<'USAGE'
tools/gallery.sh — draw the render gallery on a device and pull the PNGs.

  -d, --device <serial>  which device; default $ANDROID_SERIAL, or the only
                         one attached
  -o, --out <dir>        where to leave the pictures (default build/gallery)
      --no-build         install the test APK already built
  -h, --help             this
USAGE
}

fail() {
  echo "gallery: $*" >&2
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    -d | --device) device="${2:?--device needs a serial}"; shift 2 ;;
    -o | --out) out="${2:?--out needs a directory}"; shift 2 ;;
    --no-build) build=0; shift ;;
    *) usage; fail "unknown option $1" ;;
  esac
done

if [ -z "${device}" ]; then
  device="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
  [ -n "${device}" ] || fail "no device attached; connect one or pass --device"
fi

package="de.drehtuer.dinfinity.render.filament.test"
runner="androidx.test.runner.AndroidJUnitRunner"

if [ "${build}" -eq 1 ]; then
  echo "Building the test APK…"
  "${root}/gradlew" --console=plain -p "${root}" ":render:filament:assembleDebugAndroidTest"
fi
apk="$(find "${root}/render/filament/build/outputs/apk/androidTest/debug" -name '*.apk' -print -quit 2> /dev/null || true)"
[ -n "${apk}" ] || fail "no androidTest APK was built; look above for why"
echo "Installing $(basename "${apk}")…"
adb -s "${device}" install -r -t -g "${apk}" > /dev/null

remote_dir="/sdcard/Android/data/${package}/files/gallery"

echo "Drawing…"
set +e
adb -s "${device}" shell am instrument -w \
  -e class de.drehtuer.dinfinity.render.filament.RenderGalleryTest \
  -e gallery 1 \
  "${package}/${runner}"
set -e

rm -rf "${out}"
mkdir -p "${out}"
adb -s "${device}" pull "${remote_dir}/." "${out}/" > /dev/null || fail "nothing to pull from ${remote_dir}"
count="$(find "${out}" -name '*.png' | wc -l)"
[ "${count}" -gt 0 ] || fail "the run wrote no pictures; look above for why"
echo
echo "Pictures: ${out} (${count})"
