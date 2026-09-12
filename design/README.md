# Design

The UI design of dInfinity, exported from a [Claude Design](https://claude.ai/design)
project. It is a living prototype, not a picture: every screen runs in the
browser with a working notation parser, the table capacity rule and the exact
outcome distribution, all following the specification in `docs/`. Physics is
faked with a random face and a tumble animation.

Source project: <https://claude.ai/design/p/5cee69c8-e516-4414-a446-7fd89bb7c706>

## Files

| File | What it is |
|---|---|
| `dInfinity.dc.html` | The canvas: an options board of every design turn — layouts, result-sheet densities, picker styles, graph styles, saved-roll tile styles, and one phone per screen. Open this one. |
| `dInfinityPhone.dc.html` | The phone prototype itself, imported by the canvas once per variant with different attributes (`screen`, `formula`, `theme`, …). |
| `android-frame.jsx` | Android (Material 3) device frame: status bar, app bar, gesture nav, keyboard. Starter scaffold; intentionally uses raw values. |
| `support.js` | Claude Design's generated runtime that renders `.dc.html` files. Do not edit. |
| `_ds/modernist-…/` | The "Modernist" design system the prototype is built on: `styles.css` (tokens + component classes), `readme.md` (usage guide), `_ds_manifest.json`, `_ds_bundle.js`, and the adherence lint config. |
| `github.md` | The design project's own sync notes: what it read from this repo, and design decisions that still need feeding back into `docs/`. |

The project's `.thumbnail` (a binary preview image) is not imported.

## Viewing

Open `dInfinity.dc.html` from a local web server (the runtime fetches the
imported files, so `file://` will not work):

```sh
cd design && python3 -m http.server 8000
# then http://localhost:8000/dInfinity.dc.html
```

## Editing

Edit in the Claude Design project and re-import here so the two stay in step.
Decisions made in the design that change behaviour, limits or defaults must
be reflected in `docs/` in the same PR (see `.claude/CLAUDE.md`); `github.md`
lists the ones still outstanding.
