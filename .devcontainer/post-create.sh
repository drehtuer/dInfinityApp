#!/usr/bin/env bash
#
# Run once, when the container is created.
set -uo pipefail

# The two volumes are created by Docker owned by root; without this the first
# Gradle run cannot write its cache.
sudo chown -R dev /home/dev/.gradle /home/dev/.android || true

# The kernel checks the *numeric* group of /dev/kvm, and that number is the
# host's, not something this image can know in advance. The group already
# exists with the developer on it (see the Dockerfile); this moves it to the
# number that actually opens the device. `-o` because the id is very likely
# already taken by something else in here.
if [ -e /dev/kvm ]; then
  sudo groupmod -o -g "$(stat -c %g /dev/kvm)" kvm || true
  echo "KVM is available: the emulator will run at a useful speed."
else
  echo "No /dev/kvm in this container, so there is no emulator acceleration."
  echo "Everything else works; see docs/build-setup.md, 'The emulator'."
fi
