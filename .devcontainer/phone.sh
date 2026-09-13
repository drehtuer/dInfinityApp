#!/usr/bin/env bash
#
# Attaches the phone over WiFi debugging, and remembers where it was.
#
# The emulator in this image answers "does this run". It has no real GPU, no
# sensors and no hands, so it cannot answer "does this look right", "does a
# shake feel like a shake" or "is it 60 fps on the reference device" — and
# those are the questions Step 5 is made of. This is how the phone is reached
# for the rest.
#
# The address is the awkward part: the port the phone listens on changes every
# time wireless debugging is switched off and on, or the phone reboots, so it
# has to be typed at least once per session. It is then kept in the
# `dinfinity-android` volume alongside the adb key, and every later reconnect
# is `dinfinity-phone` with nothing after it.
set -euo pipefail

state="${HOME}/.android/phone-address"
patience="${DINFINITY_PHONE_TIMEOUT:-20}"

usage() {
  cat >&2 <<'USAGE'
dinfinity-phone — attach the phone over WiFi debugging.

  dinfinity-phone                  reconnect to the address last used
  dinfinity-phone <ip:port>        connect there, and remember it
  dinfinity-phone pair <ip:port> [code]
                                   pair, which is needed once per machine

On the phone: Settings -> System -> Developer options -> Wireless debugging.
That screen shows the address to connect to. "Pair device with pairing code"
shows a *different* address and a code, and those are the ones `pair` wants —
connecting to a pairing port fails however open the port is.
USAGE
}

# A phone, as opposed to the emulator this image also ships.
phone_serial() {
  adb devices | awk '$2 == "device" && $1 !~ /^emulator-/ { print $1; exit }'
}

# `adb connect` answers about the attempt, not about the device: it reports
# success for a host that merely accepted a socket. So ask adb afterwards.
settled() {
  local serial="$1" deadline=$(( SECONDS + patience ))
  while (( SECONDS < deadline )); do
    if adb devices | awk -v s="${serial}" '$1 == s && $2 == "device" { ok = 1 } END { exit !ok }'; then
      return 0
    fi
    sleep 1
  done
  return 1
}

attach() {
  local address="$1"
  adb connect "${address}" > /dev/null 2>&1 || true
  if settled "${address}"; then
    printf '%s\n' "${address}" > "${state}"
    return 0
  fi
  adb disconnect "${address}" > /dev/null 2>&1 || true
  return 1
}

case "${1-}" in
  -h | --help | help)
    usage
    exit 0
    ;;
  pair)
    shift
    [ "$#" -ge 1 ] || { usage; exit 2; }
    adb pair "$@"
    echo "Paired. Now connect to the address on the Wireless debugging screen," \
         "which is a different port: dinfinity-phone <ip:port>"
    exit 0
    ;;
esac

adb start-server > /dev/null 2>&1 || true

if serial="$(phone_serial)" && [ -n "${serial}" ]; then
  echo "Phone already attached: ${serial}"
else
  candidates=()
  [ "$#" -ge 1 ] && candidates+=("$1")
  [ -s "${state}" ] && candidates+=("$(cat "${state}")")
  # Asked only when nothing is remembered: mDNS needs multicast to reach the
  # container, which the default bridge does not carry, so it usually finds
  # nothing here. It is the only route that needs no address at all, though,
  # so it is worth the second it costs when there is no address to try.
  if [ "${#candidates[@]}" -eq 0 ]; then
    while read -r found; do
      [ -n "${found}" ] && candidates+=("${found}")
    done < <(timeout 5 adb mdns services 2>/dev/null | awk '$2 ~ /_adb-tls-connect/ { print $3 }')
  fi

  serial=""
  for candidate in ${candidates[@]+"${candidates[@]}"}; do
    if attach "${candidate}"; then
      serial="${candidate}"
      break
    fi
    echo "No phone at ${candidate}." >&2
  done

  if [ -z "${serial}" ]; then
    cat >&2 <<'HELP'
No phone attached.

Switch Wireless debugging on (Settings -> System -> Developer options), read
the ip:port off that screen, and pass it once:

  dinfinity-phone 192.168.1.42:39337

If this machine has never been paired with the phone, pair first, with the
other address and the code from "Pair device with pairing code":

  dinfinity-phone pair 192.168.1.42:37105 832269
HELP
    exit 1
  fi
fi

# Say which device, because the emulator may be attached as well and an adb
# command that does not name one fails outright when two are.
export ANDROID_SERIAL="${serial}"
dinfinity-await-device "${DINFINITY_PHONE_READY_TIMEOUT:-60}"

if adb devices | grep -q '^emulator-'; then
  cat >&2 <<HELP

The emulator is attached too, and a run that does not choose picks both. Tell
this shell which one you mean — nothing that happened in here can set it for
you, because this script is a child of that shell:

  export ANDROID_SERIAL=${serial}
HELP
fi
