#!/usr/bin/env bash
#
# Starts the project's emulator, creating it the first time.
#
# The AVD lives in ~/.android, which is a volume rather than part of the image.
# That is not an accident: anything written there while the image was being
# built would be shadowed by the mount the moment a container started, so the
# AVD has to be made on first use. Being a volume also means it survives the
# image being rebuilt, which is what makes every start after the first quick.
#
# Headless and software-rendered, because there is no display in a container.
# That is fine for what the emulator is for here — physics, determinism and the
# instrumented suites (docs/TODO.md, Step 5.1). It is not fine for judging how
# the dice *look*, which needs the phone.
set -euo pipefail

name="${DINFINITY_AVD:-dinfinity}"
image_file="${ANDROID_HOME:?ANDROID_HOME is not set}/.system-image"

if [ ! -r "${image_file}" ]; then
  echo "This image was built with INSTALL_EMULATOR=false, so there is no emulator in it." >&2
  echo "Rebuild with --build-arg INSTALL_EMULATOR=true (docs/build-setup.md)." >&2
  exit 1
fi
image="$(cat "${image_file}")"

if [ ! -e /dev/kvm ]; then
  echo "There is no /dev/kvm in this container, so the emulator would run" >&2
  echo "interpreted and take many minutes to boot, if it booted at all." >&2
  echo "Pass --device=/dev/kvm (docs/build-setup.md, 'The emulator')." >&2
  exit 1
fi

# Being able to see /dev/kvm is not the same as being able to open it, and the
# emulator's own complaint about the difference points at /etc/group, which is
# by then already correct. The group *is* correct; this process is holding the
# membership it was given before `dinfinity-post-create` renumbered it, and
# only a new session picks that up.
if [ ! -w /dev/kvm ]; then
  echo "This shell cannot open /dev/kvm: it is group $(stat -c %G /dev/kvm)" \
       "($(stat -c %g /dev/kvm)), and this shell has groups $(id -G | tr ' ' ',')." >&2
  echo "Run dinfinity-post-create, then open a new terminal — group membership" \
       "is fixed when a session starts and cannot be changed underneath it." >&2
  exit 1
fi

# The AVD is made from one system image and is no use with another, so a
# rebuilt container image that resolved a different one gets a new AVD rather
# than a puzzling failure to boot.
avd_dir="${HOME}/.android/avd/${name}.avd"
stamp="${avd_dir}/.dinfinity-image"
if avdmanager list avd --compact 2>/dev/null | grep -qx "${name}" \
   && [ "$(cat "${stamp}" 2>/dev/null)" != "${image}" ]; then
  echo "The '${name}' emulator was made from a different system image; replacing it."
  avdmanager delete avd --name "${name}" > /dev/null
fi

if ! avdmanager list avd --compact 2>/dev/null | grep -qx "${name}"; then
  echo "Creating the '${name}' emulator from ${image}…"
  # A device profile, not the default. Without one `avdmanager` produces a
  # 320x640 screen with a 32 MB heap, on which the framework services start and
  # then fall over — the tests then fail with "Can't find service: package",
  # which says nothing at all about the real cause. A Pixel 9 is the closest
  # profile the SDK ships to this project's reference device, and its screen is
  # the same shape, which matters here: the tray *is* the screen
  # (docs/tables.md).
  echo no | avdmanager create avd \
    --name "${name}" \
    --package "${image}" \
    --device pixel_9 \
    --force > /dev/null
  config="${avd_dir}/config.ini"
  for setting in \
      "hw.ramSize=4096" \
      "vm.heapSize=512" \
      "disk.dataPartition.size=6G" \
      "showDeviceFrame=no" \
      "hw.keyboard=yes"; do
    key="${setting%%=*}"
    sed -i "/^${key}=/d" "${config}"
    echo "${setting}" >> "${config}"
  done
  echo "${image}" > "${stamp}"
fi

exec emulator -avd "${name}" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -gpu swiftshader_indirect \
  "$@"
