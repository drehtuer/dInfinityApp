# Signing keys

This directory holds the keystores that sign the app. **Nothing in here is
committed except this file**: `.gitignore` excludes `keystore/*`, and a leaked
release key cannot be un-leaked. It can only be rotated — which the v3
signature makes possible, but which still means every release from then on is
signed by a different key.

- `debug.keystore` — shared debug key, so every developer and every CI run
  produces debug builds that install over each other.
- `release.keystore` — the release key. It signs what users install. Treat it
  the way you would treat a password manager's master key.
- `keystore.properties` — paths, aliases and passwords for both, read by the
  build. Its format is in
  [../docs/build-setup.md](../docs/build-setup.md#signing-keys), alongside the
  `keytool` commands that create the keystores.

Without `keystore.properties` the build still works: debug builds use the
Android default debug key and release builds come out unsigned. That is the
right behaviour for a fresh checkout and for CI runs that only need to
compile.

See [../docs/build-setup.md](../docs/build-setup.md) for the `keytool`
commands that create both keystores.
