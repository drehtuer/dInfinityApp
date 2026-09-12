# Security

dInfinity runs entirely on the phone. It has no backend, no accounts and no
analytics, so most of the usual attack surface does not exist. What is left is
the part that matters: **the app installs content other people wrote.**

## Reporting a vulnerability

Report privately through GitHub's
[security advisories](https://github.com/drehtuer/dInfinityApp/security/advisories/new)
— not as a public issue, and not as a pull request that fixes it, since either
one publishes the problem before there is a release to upgrade to.

Please include what you did, what happened, and the smallest dice set,
collection or file that reproduces it. You will get an acknowledgement, and a
say in how the fix is described when it ships.

There is no bounty. This is a hobby project.

## Supported versions

The latest release. Releases are immutable, so a fix ships as a new version
rather than a re-tag of an old one.

## What the app trusts, and what it does not

The threat model in one line: **a dice set, table or saved-roll collection is
data written by a stranger, and the app treats it that way.**

| Input | Treated as |
|---|---|
| A downloaded dice set or table (`docs/dice-sets.md`) | Hostile until validated |
| An imported saved-roll collection (`docs/dice-notation.md`) | Hostile until validated |
| A formula typed by the user | Untrusted length and shape; bounded by the notation limits |
| A photo chosen as a table background | Untrusted image, decoded with explicit bounds |

The defences, all documented in `docs/dice-sets.md`:

- **Nothing downloaded is ever executed.** Sets are TOML plus images. There is
  no script hook, no build step, no reflection-driven deserialization into
  arbitrary types.
- **Archives are extracted defensively**: absolute paths, `..`, symlinks, hard
  links and device files are refused; entry count, uncompressed size and file
  extensions are capped.
- **Each set lives in its own folder** and file references are resolved against
  it; a canonicalised path that escapes the folder is an error.
- **Validation is all-or-nothing.** A set that fails is not partially
  installed, and a set that fails to load later is disabled with a reason
  rather than crashing the app.
- **Images are bounds-checked before decoding**, so an oversized or malformed
  image is rejected without allocating for it.
- **Only `https` is accepted** for installs, redirects are limited and followed
  only to `https`, and downloads are size- and time-capped. Nothing else in the
  app touches the network.
- **Physics values from a set are clamped** at validation and again at load, so
  a set cannot drive the simulation into `NaN`.

If you find an input that gets past any of these, that is exactly the kind of
report this file is asking for.

## Data on the device

Statistics, saved rolls, history and installed sets stay on the phone. There is
no telemetry and no upload. Cloud backup is switched off deliberately
(`app/src/main/res/xml/data_extraction_rules.xml`); a device-to-device transfer
may carry the data, so a new phone does not cost you your campaign's history.
Export is a manual action through the share sheet, and the export never
includes the roll seeds.

## Signing keys

Keystores and their passwords are never committed — see
[keystore/README.md](keystore/README.md). A leaked release key cannot be
un-leaked, only rotated, and rotating it breaks upgrades for everyone who
already installed the app. Release signing happens where the key lives; a
checkout without the keystore still builds, it just produces unsigned release
artifacts.

## Out of scope

- An attacker with physical access to an unlocked phone.
- A rooted device, or a modified build of the app.
- Denial of service you inflict on yourself — a dice set may be badly designed
  and roll unfairly. That is a quality problem, not a security one, and
  `docs/dice-sets.md` says how it is surfaced.
