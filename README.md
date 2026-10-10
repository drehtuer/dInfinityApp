<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-dark.svg">
    <img src="docs/assets/logo-light.svg" alt="dInfinity" width="200">
  </picture>
</p>

# dInfinity

[![CI](https://github.com/drehtuer/dInfinityApp/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/drehtuer/dInfinityApp/actions/workflows/ci.yml)
[![CodeQL](https://github.com/drehtuer/dInfinityApp/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/drehtuer/dInfinityApp/actions/workflows/codeql.yml)
[![Dependabot](https://img.shields.io/badge/dependabot-enabled-025e8c?logo=dependabot&logoColor=white)](.github/dependabot.yml)
[![Quality gate](https://sonarcloud.io/api/project_badges/measure?project=drehtuer_dInfinityApp&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=drehtuer_dInfinityApp)
[![Security rating](https://sonarcloud.io/api/project_badges/measure?project=drehtuer_dInfinityApp&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=drehtuer_dInfinityApp)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=drehtuer_dInfinityApp&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=drehtuer_dInfinityApp)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=drehtuer_dInfinityApp&metric=coverage)](https://sonarcloud.io/component_measures?id=drehtuer_dInfinityApp&metric=coverage)
[![Line coverage](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fsonarcloud.io%2Fapi%2Fmeasures%2Fcomponent%3Fcomponent%3Ddrehtuer_dInfinityApp%26metricKeys%3Dline_coverage&query=%24.component.measures%5B0%5D.value&suffix=%25&label=lines&color=4c1)](https://sonarcloud.io/component_measures?id=drehtuer_dInfinityApp&metric=line_coverage)
[![Branch coverage](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fsonarcloud.io%2Fapi%2Fmeasures%2Fcomponent%3Fcomponent%3Ddrehtuer_dInfinityApp%26metricKeys%3Dbranch_coverage&query=%24.component.measures%5B0%5D.value&suffix=%25&label=branches&color=4c1)](https://sonarcloud.io/component_measures?id=drehtuer_dInfinityApp&metric=branch_coverage)
[![License](https://img.shields.io/badge/license-GPL--2.0--or--later-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-17%20(API%2037)-3ddc84?logo=android&logoColor=white)](#platform)

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

**▶ [design/dInfinity.dc.html](design/dInfinity.dc.html)** — every v1 screen,
live in the browser. Clone the repository and serve the folder — `cd design &&
python3 -m http.server 8000`, then open
`http://localhost:8000/dInfinity.dc.html`. GitHub shows you its source, not the
running prototype, and a file opened straight from disk cannot fetch the parts
it imports. It needs no account or sign-in of any kind, only the internet: React
and Babel come from unpkg, pinned (`design/README.md`).

It is not a picture of the app: the notation parser, the table capacity rule
and the exact outcome graph all run, following the specification in `docs/`.
Tap dice to build a formula and shake the phone to throw them — in the
prototype, the tray's own control stands in for the shake. Type `500d6` to see
it refused, `2d20kh1 + 6` for advantage. Physics is faked with a random face and
a tumble; everything else is real.

| | |
| --- | --- |
| [design/dInfinity.dc.html](design/dInfinity.dc.html) | The canvas: every screen and every variant on one board — start here |
| [design/dInfinityPhone.dc.html](design/dInfinityPhone.dc.html) | The phone prototype on its own, without the board around it |
| [design/README.md](design/README.md) | What each file is, and what the prototype needs to run |

## Features

- **Physics-based rolls** — dice are convex rigid bodies with correct mass
  distribution; results come from which face lands up, not from `random()`.
- **3D rendering** of the tray and dice, seen straight down or at an angle that
  shows the walls — your choice — with haptics and sound on every real impact,
  never on a die sliding or a die at rest. The table decides what it sounds
  like, the die's size decides the pitch, and both switch off. The first launch
  after an install or update compiles the shaders for your phone's GPU, and
  the tray says so while it waits, with a bar and about how long is left.
- **Shake to roll** — accelerometer and gyroscope drive the throw, and a
  shake is the *only* way to start one. There is no Roll button, a tap on the
  table throws nothing, the formula editor's key says Done and only closes the
  editor, and there is no setting that turns shaking off. The display stays
  on while the tray is in front, because a shake takes both hands and puts
  neither of them on the glass.
- **Says what a throw is worth** — the lowest, the highest and the exact
  average, before you shake and again beside the total.
- **Power-saving mode** — same physics, no rendering; just the result. The dice
  are still heard: the impacts the throw made are played back over a second.
- **Tabletop notation** — roll `3d6 + 1d20 - 4`, `2d10kh1`, `d%`, and so on,
  and a dice set's own dice by name: `3{skull-d6}kh1`. The whole grammar is in
  the app under **Notation**, with an example on every line you can tap to
  try.
- **Does the math** — the total, the modifiers and the per-die breakdown are
  shown the moment the dice stop. No counting pips in the middle of a fight.
- **Saved rolls** — name a formula, give it an icon ("Fireball", "Sneak
  Attack"), put it in the field with one tap and shake. Group them per game,
  per character, however you like; export and import them as files, from a
  URL, or from a git repository holding one.
- **Outcome graph** — see the exact probability distribution before you roll,
  for a typed formula or for a handful of dice picked by tapping, with mean
  and standard deviation.
- **Standard dice** — d2, d4, d6, d8, d10, d12, d18, d20, d100 (as two d10s;
  `d%` is an alias for `d100`), and Fudge dice (`dF`), whose total carries its
  sign.
- **Extensible dice sets** — dice are defined in plain text files with
  optional face textures; install sets from GitHub, GitLab, Codeberg or any
  `https` link to an archive. [examples/](examples/) is a working set to copy:
  every shape, every field commented, blank atlases to draw on.
  [sample-sets/](sample-sets/) has four finished ones to install — marble,
  steel, green resin and red glass.
- **Exchangeable tables** — swap the look of the dice tray (green or black
  felt, oak, dark glass, plain, your own photo) the same way you would swap a
  wallpaper. Felt and oak are drawn from real textures at real size; plain stays
  a flat colour. The tray's shape never changes: it is the phone's screen,
  walls at the edges.
- **Safe imports** — a broken or malicious dice set can fail to load, but it
  cannot crash the app or affect other sets.
- **Your accent** — the one colour the interface spends is yours to choose: six
  presets, or any colour from the app's own picker, pushed toward the ground
  it is read against until it is legible on both the light and the dark one.
- **Your view of the table** — look straight down at the tray, or lean the
  camera over and see the top and left walls. Straight down is what a new
  install rolls with, because on a tall phone a leaning shot spends more of the
  frame on the wooden rim than on the felt. It is a camera either way: the dice
  are still drawn in perspective and still cast their shadows. A throw puts
  the formula and the dice menu away while the dice roll, and a double tap on
  the table clears every control off it until the next double tap.
- **Readable out loud** — every screen is labelled for TalkBack, including the
  tray and the charts, which are drawings and would otherwise be silent.
  Nothing is said by a colour alone: a natural 20, a dropped die, the chosen
  filter and the line a fair die would draw all say so in words as well. Touch
  targets are 48 dp — save the colour picker's sliders, which Material holds at
  44 dp and which a test records — and the palette's contrast is measured in a
  test rather than eyeballed.
- **Face designer** — in three steps: pick the die; make it plastic, pearl,
  resin, glass, metal or stone, give it a colour and round its edges on a
  slider; then draw its faces with your finger and turn it over. Save the lot
  as a dice set of your own, and roll the die you drew. How round a die is, is
  physics as well as looks: the dice roll on the edges they are drawn with.
- **Statistics** — count of lowest/highest results per die, averages, streaks,
  per-formula history. Yes, we know a natural 20 is exactly as likely as a
  natural 7. It still matters.
- **No cocked dice, no invisible hand** — nothing nudges, steers or pokes a
  die, and nothing throws one but you. A roll reads the dice that came to rest
  flat; a die that lands cocked or on another stays where it lies, the screen
  says so, and your next shake throws that die again — only that one — the way
  you would at a real table.
- **No 500d6** — a roll is refused past a hundred dice, or sooner when the
  dice would not fit on the table with room to tumble. Dice shrink to make
  room up to a point; past that the simulation would only produce nonsense, so
  the app says no.

## Documentation

`docs/` is the written specification; [the prototype](#the-design-is-clickable)
is the visual one. Each document below links to the screens that realise it.

| Document | Contents |
| --- | --- |
| [docs/STATUS.md](docs/STATUS.md) | Where the project stands right now: phase, in progress, blocked, pending decisions |
| [docs/TODO.md](docs/TODO.md) | Open tasks by milestone and open questions |
| [docs/build-setup.md](docs/build-setup.md) | Devcontainer, building, signing keys, running tests, connecting a phone over WiFi |
| [docs/architecture.md](docs/architecture.md) | Module layout, tech stack, data flow, key decisions |
| [docs/physics-and-rendering.md](docs/physics-and-rendering.md) | Simulation, shake input, settling and face detection, whether the dice are fair, stacking avoidance, power-saving mode |
| [docs/dice-notation.md](docs/dice-notation.md) | Roll formula grammar, evaluation rules, saved rolls |
| [docs/dice-sets.md](docs/dice-sets.md) | Dice set file format, shapes, textures, installing from git forges or archive URLs, validation and sandboxing |
| [docs/tables.md](docs/tables.md) | Table (tray) geometry, capacity limits, exchangeable table looks and their textures |
| [docs/probability.md](docs/probability.md) | How the outcome graph is computed |
| [docs/face-designer.md](docs/face-designer.md) | The three-step designer: shape; material, colour and edge rounding; finger-drawn faces, the Solid view and saving to a set |
| [docs/statistics.md](docs/statistics.md) | What is tracked, how it is stored, privacy |
| [docs/assets/README.md](docs/assets/README.md) | The logo files and the die font, how both are generated from Archivo, and the font licence; the photographed room the dice are lit by; the bundled felt and oak textures, their CC0 sources and how they were cut |
| [docs/design-handover.md](docs/design-handover.md) | Where the app and the prototype still differ, and the questions each side is waiting on |
| [design/README.md](design/README.md) | The prototype: what each file is, how to serve it locally, how to keep it in step with `docs/` |
| [examples/README.md](examples/README.md) | The worked dice set: a commented `diceset.toml` using every catalogue shape, and blank atlases to draw on |
| [sample-sets/README.md](sample-sets/README.md) | Four finished sets to install from a file or a link — marble, steel, green resin, red glass — and how their artwork and archives are made and checked |

## Status

**Implementation. The app rolls dice on a phone, and `v0.2.0` is out** — a
signed early release, published with its SHA-256, built from this repository by
pushing a tag. Every screen is written and connected; the documents in `docs/`
and the prototype in `design/` are still the specification, and where the two
disagree one of them is a bug.

The features above run ahead of that release: the three-step face designer
with its materials and edge rounding, felt and oak drawn from real textures,
the plate that says why the first launch waits for its shaders, and the four
sample sets are built since `v0.2.0` and ship in the next one.

It is still `0.x` because the physics is not finished: `100d4` does not
reliably settle, and a hard sideways shake can run a roll out to its
twelve-second cap, where it gives up rather than invent an answer. See
[docs/STATUS.md](docs/STATUS.md) for where things stand and
[docs/TODO.md](docs/TODO.md) for what is next.

## Building

Everything happens in the devcontainer — it carries the JDK, the Android SDK,
Gradle and the linters, and nothing is expected on the host but Docker. Open
the repository in a devcontainer-aware editor, or:

```sh
docker build -t dinfinity-dev .devcontainer
docker run --rm -it -v "$PWD":/workspace -w /workspace dinfinity-dev \
  ./gradlew build test lint detekt ktlintCheck assembleDebugAndroidTest
```

Release and debug APKs land in `app/build/outputs/named-apk/<variant>/` as
`dInfinityApp-<version>.apk` and `dInfinityApp-<version>-debug.apk`. The
container also carries `adb`, so a phone attached over WiFi debugging runs the
on-device tests without leaving it.

[docs/build-setup.md](docs/build-setup.md) has the details: signing keys,
wireless debugging, static analysis, and what the container contains.

## Contributing

The working agreements — branching, PRs, tests, releases, build naming, how
the tracking files are kept tidy — are in
[.claude/CLAUDE.md](.claude/CLAUDE.md). Read it before opening a PR.

## Platform

- Targets Android 17 (API 37); `minSdk` is 36, because Robolectric cannot
  start API 37 — decision 17 in
  [docs/architecture.md](docs/architecture.md#key-decisions-log)
- Reference device: Google Pixel 10a; that is where it is tested first
- Kotlin, Jetpack Compose
- Accessibility: TalkBack labels on every screen, 48 dp touch targets, no
  meaning carried by colour alone, and WCAG 2.2 AA contrast measured rather
  than assumed — the rules, the measurements and the two ratios that fall
  short are in [docs/architecture.md](docs/architecture.md#accessibility).
  **Rolling needs a hand that can shake the phone.** There is no other way to
  start a roll — no button, no key and no accessibility action — so somebody
  who cannot shake a phone cannot roll in this app. That is a decided
  limitation, not an oversight (decision 66)
- Language: English, and only English ships. Every word a screen says is a
  string resource, so nothing in the app stands between here and a translation
  somebody writes — the rule, where the line between text and a test tag is
  drawn, and the check that enforces it are in
  [docs/architecture.md](docs/architecture.md#text-a-person-reads)
- 3D rendering and physics run on-device; the app works fully offline.
  Network access is only used when you explicitly install a dice set, a
  table or a saved-roll collection from a URL.

## Security

The app installs dice sets other people wrote, and that is the whole attack
surface worth caring about. [SECURITY.md](SECURITY.md) sets out the threat
model, the defences, and how to report a vulnerability privately.

## License

GPL-2.0-or-later. See [LICENSE](LICENSE).

Dice sets, tables and saved-roll collections you create with the app are
yours; the app does not impose a license on them. Sets downloaded from the
internet carry whatever license their author chose, shown in the set details.
