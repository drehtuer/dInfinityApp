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
| --- | --- |
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
  only to `https`, and downloads are size- and time-capped — at the size that
  suits what is being fetched, so a saved-roll collection is capped at the
  megabyte its reader would refuse a file above rather than at an archive's
  sixty-four. A dice set and a collection are the only two things downloaded,
  and both go through the same downloader.
- **Physics values from a set are clamped** at validation and again at load, so
  a set cannot drive the simulation into `NaN`.

If you find an input that gets past any of these, that is exactly the kind of
report this file is asking for.

## Network

The app works offline. The only traffic it ever makes is a download the user
asked for — a dice set, a table or a saved-roll collection — and those are
`https` only: the manifest sets `android:usesCleartextTraffic="false"`, so a
set that names an `http://` URL fails to download rather than fetching over a
link anyone on the network can rewrite.

## Data on the device

Statistics, saved rolls, history and installed sets stay on the phone. There is
no telemetry and no upload. Cloud backup is switched off deliberately
(`app/src/main/res/xml/data_extraction_rules.xml`); a device-to-device transfer
may carry the data, so a new phone does not cost you your campaign's history.
Export is a manual action through the share sheet, and the export never
includes the roll seeds.

`android:allowBackup` is deliberately left at its default rather than set to
`false`. On Android 12 and above `false` only stops cloud backup — which
`data_extraction_rules.xml` already stops, completely — while device-to-device
migration cannot reliably be disabled at all. Android's own guidance for an app
with data worth protecting is to keep the attribute and restrict through the
extraction rules, which is what this app does. Static analysis flags the
default as worth a look; it has been looked at, and this is the answer.

## Verifying a release

Every release APK is signed with the project's release key. Its certificate
fingerprint is public, and pinned in the repository at
[keystore/release-certificate.sha256](keystore/release-certificate.sha256):

```text
ce1366a9471577ad7bf5423351a1c45f2c5a6bfcd9d99fef1cc6436a62d26f39
```

To check a downloaded APK yourself:

```sh
apksigner verify --print-certs dInfinityApp-<version>.apk
```

The release workflow checks the same thing before publishing, so an APK signed
with the debug key or a regenerated one never reaches a release. Each release
also carries the APK's own SHA-256 alongside it.

## Signing keys

Keystores and their passwords are never committed — see
[keystore/README.md](keystore/README.md). A leaked release key cannot be
un-leaked, only rotated. Releases are signed with **v3**, which carries a
proof-of-rotation record, so a compromised key *can* be replaced without
stranding everyone who already installed the app — but rotation is still a
last resort, not a plan. Release signing happens where the key lives; a
checkout without the keystore still builds, it just produces unsigned release
artifacts.

## Automated checks

None of these replace review, but they catch the boring half:

- **CodeQL** on every pull request and weekly on `main`, over the workflow
  files. **Not** over the app's Kotlin: the extractor refuses Kotlin 2.4.20 and
  fails rather than degrading, so it is switched off until the bundle catches
  up — detekt, Android Lint and SonarQube cover Kotlin meanwhile
  (`.github/workflows/codeql.yml`). Findings land in the repository's code
  scanning alerts.
- **Dependabot** — alerts, automated security updates, and grouped weekly
  version bumps for Gradle, Actions and the devcontainer base image
  ([.github/dependabot.yml](.github/dependabot.yml)).
- **Dependency review** on every pull request, which fails when a change pulls
  in a dependency with a known moderate-or-worse advisory.
- **Secret scanning with push protection**, so a keystore password cannot be
  committed by accident.
- **Dependency verification**: every dependency the build resolves is pinned by
  SHA-256 in [gradle/verification-metadata.xml](gradle/verification-metadata.xml),
  and Gradle refuses an artifact whose checksum does not match. A version number
  says which artifact was asked for; a checksum says which one arrived. An app
  that installs downloaded content should not be built by a toolchain that
  cannot tell those apart. The metadata is regenerated on a cold cache —
  [docs/build-setup.md](docs/build-setup.md) says why that matters.

## Out of scope

- An attacker with physical access to an unlocked phone.
- A rooted device, or a modified build of the app.
- Denial of service you inflict on yourself — a dice set may be badly designed
  and roll unfairly. That is a quality problem, not a security one, and
  `docs/dice-sets.md` says how it is surfaced.
