#!/usr/bin/env bash
#
# Waits until a device is ready to be installed on — an emulator or a phone.
#
# `adb wait-for-device` returns as soon as adb can talk to the device, which is
# a long way before Android can install anything. Asking too early gets
# "Error: device is still booting" out of the package manager, or, worse, a
# half-started framework answering "Can't find service: package" — neither of
# which sounds like "wait a bit longer", which is all it means.
set -euo pipefail

timeout="${1:-300}"
adb wait-for-device

deadline=$(( $(date +%s) + timeout ))
while [ "$(date +%s)" -lt "${deadline}" ]; do
  booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  animation="$(adb shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')"
  if [ "${booted}" = "1" ] && [ "${animation}" != "running" ]; then
    # The package manager is what an install actually goes through, so it is
    # what the last word belongs to.
    if adb shell pm path android > /dev/null 2>&1; then
      echo "Device ready: $(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')," \
           "API $(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
      exit 0
    fi
  fi
  sleep 2
done

echo "The device did not become ready within ${timeout}s." >&2
adb devices >&2
exit 1
