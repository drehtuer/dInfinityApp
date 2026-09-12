# dInfinity

A dice roller for Android where the roll is *real*: every throw is a rigid-body
physics simulation of the actual dice shapes, rendered in 3D. Shake the phone
like you would shake a fistful of dice, and read the result off the faces that
land up.

## Why another dice app?

Most dice apps draw a random number and show a picture. dInfinity simulates
the dice. The number you get is whatever face ends up on top after the dice
tumble, collide, and settle in the tray — the same way it works on a table.
That makes rolls feel honest, lets you watch the d20 wobble before it stops,
and means a die is defined by its shape, not by a lookup table.

Passing one phone around the table beats passing a bag of dice around and
hunting for the d12 that rolled under the couch.

## The design is clickable

**▶ [Open the dInfinity prototype](https://claude.ai/design/p/5cee69c8-e516-4414-a446-7fd89bb7c706?file=dInfinity.dc.html)** — every v1 screen, live in the
browser.

It is not a picture of the app: the notation parser, the table capacity rule
and the exact outcome graph all run, following the specification in `docs/`.
Tap dice to build a formula, tap the tray to roll, type `500d6` to see it
refused, `2d20kh1 + 6` for advantage. Physics is faked with a random face and
a tumble; everything else is real.

| | |
|---|---|
| [Design canvas](https://claude.ai/design/p/5cee69c8-e516-4414-a446-7fd89bb7c706?file=dInfinity.dc.html) | Every screen and every variant on one board — start here |
| [Phone prototype](https://claude.ai/design/p/5cee69c8-e516-4414-a446-7fd89bb7c706?file=dInfinityPhone.dc.html) | The prototype on its own, without the board around it |
| [design/](design/) | The same files in this repo, and how to run them offline — see [design/README.md](design/README.md) |

## Features

- **Physics-based rolls** — dice are convex rigid bodies with correct mass
  distribution; results come from which face lands up, not from `random()`.
- **3D rendering** of the tray and dice, with optional haptics and sound.
- **Shake to roll** — accelerometer and gyroscope drive the throw.
- **Power-saving mode** — same physics, no rendering; just the result.
- **Tabletop notation** — roll `3d6 + 1d20 - 4`, `2d10kh1`, `d%`, and so on.
- **Does the math** — the total, the modifiers and the per-die breakdown are
  shown the moment the dice stop. No counting pips in the middle of a fight.
- **Saved rolls** — name a formula, give it an icon ("Fireball", "Sneak
  Attack"), roll it with one tap. Group them per game, per character, however
  you like; export and import them as files or from a URL.
- **Outcome graph** — see the exact probability distribution before you roll,
  for a typed formula or for a handful of dice picked by tapping, with mean
  and standard deviation.
- **Standard dice** — d2, d4, d6, d8, d10, d12, d18, d20, d100 (as two d10s;
  `d%` is an alias for `d100`).
- **Extensible dice sets** — dice are defined in plain text files with
  optional face textures; install sets from GitHub, GitLab, Codeberg or any
  `https` link to an archive.
- **Exchangeable tables** — swap the look of the dice tray (felt, wood, glass,
  your own photo) the same way you would swap a wallpaper. The tray's shape
  never changes: it is the phone's screen, walls at the edges.
- **Safe imports** — a broken or malicious dice set can fail to load, but it
  cannot crash the app or affect other sets.
- **Face designer** — draw die faces with your finger and roll them.
- **Statistics** — count of lowest/highest results per die, averages, streaks,
  per-formula history. Yes, we know a natural 20 is exactly as likely as a
  natural 7. It still matters.
- **No cocked dice, no invisible hand** — dice that would land on top of each
  other are steered apart while they are still tumbling, never poked once
  they have stopped. A die that does end up cocked is re-thrown where you can
  see it, the way you would at a real table.
- **No 500d6** — a roll is refused when the dice would not fit on the table
  with room to tumble. Dice shrink to make room up to a point; past that the
  simulation would only produce nonsense, so the app says no.

## Documentation

`docs/` is the written specification; [the prototype](#the-design-is-clickable)
is the visual one. Each document below links to the screens that realise it.

| Document | Contents |
|---|---|
| [docs/STATUS.md](docs/STATUS.md) | Where the project stands right now: phase, in progress, blocked, pending decisions |
| [docs/TODO.md](docs/TODO.md) | Open tasks by milestone and open questions |
| [docs/architecture.md](docs/architecture.md) | Module layout, tech stack, data flow, key decisions |
| [docs/physics-and-rendering.md](docs/physics-and-rendering.md) | Simulation, shake input, settling and face detection, stacking avoidance, power-saving mode |
| [docs/dice-notation.md](docs/dice-notation.md) | Roll formula grammar, evaluation rules, saved rolls |
| [docs/dice-sets.md](docs/dice-sets.md) | Dice set file format, shapes, textures, installing from git forges or archive URLs, validation and sandboxing |
| [docs/tables.md](docs/tables.md) | Table (tray) geometry, capacity limits, exchangeable table looks |
| [docs/probability.md](docs/probability.md) | How the outcome graph is computed |
| [docs/face-designer.md](docs/face-designer.md) | Finger-drawn face textures |
| [docs/statistics.md](docs/statistics.md) | What is tracked, how it is stored, privacy |
| [design/README.md](design/README.md) | The prototype: what each file is, how to open it offline, how to keep it in step with `docs/` |

## Status

Design phase. The documents in `docs/` and the prototype in `design/` are the
specification the implementation will be built against. Nothing here is
shipped yet. See
[docs/STATUS.md](docs/STATUS.md) for the current state and
[docs/TODO.md](docs/TODO.md) for what is next.

## Building

Everything happens in the devcontainer — it carries the JDK, the Android SDK,
Gradle and the linters, and nothing is expected on the host but Docker. Open
the repository in a devcontainer-aware editor, or:

```sh
docker build -t dinfinity-dev .devcontainer
docker run --rm -it -v "$PWD":/workspace -w /workspace dinfinity-dev \
  ./gradlew build test lint detekt ktlintCheck
```

Release and debug APKs land in `app/build/outputs/named-apk/` as
`dInfinityApp-<version>.apk` and `dInfinityApp-<version>-debug.apk`.

## Contributing

The working agreements — branching, PRs, tests, releases, build naming, how
the tracking files are kept tidy — are in
[.claude/CLAUDE.md](.claude/CLAUDE.md). Read it before opening a PR.

## Platform

- Targets Android 17 (API 37); `minSdk` is 36 for now — see
  [docs/TODO.md](docs/TODO.md), "Open questions"
- Reference device: Google Pixel 10a; that is where it is tested first
- Kotlin, Jetpack Compose
- 3D rendering and physics run on-device; the app works fully offline.
  Network access is only used when you explicitly install a dice set, a
  table or a saved-roll collection from a URL.

## License

GPL-2.0-or-later. See [LICENSE](LICENSE).

Dice sets, tables and saved-roll collections you create with the app are
yours; the app does not impose a license on them. Sets downloaded from the
internet carry whatever license their author chose, shown in the set details.
