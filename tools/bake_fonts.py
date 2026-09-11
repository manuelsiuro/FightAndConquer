#!/usr/bin/env python3
"""Bake the UI typefaces from their variable-font sources into static res/font TTFs.

    python3 tools/bake_fonts.py        # needs fontTools: pip install fonttools

Sources are the upstream Google Fonts variable files in ``art/fonts/`` (SIL OFL 1.1,
license texts beside them). Output goes to ``app/src/main/res/font/`` as one static
instance per weight the app uses, so Compose picks them up as plain ``Font(R.font.x,
weight)`` entries with no variation settings (docs/ui-hud.md "Typography").

- **Figtree** is the UI face: every weight the composables ask for (400-800).
- **Fraunces** is the display face for screen and overlay titles. It is pinned at
  SOFT 100 (the rounded "soft" drawing), WONK 0 (no wonky leaning glyphs) and one
  optical size, because a static file cannot follow the text size the way a browser
  does; opsz 28 sits in the middle of the 22-34 sp title range.

Instancing is deterministic, so re-running on unchanged sources reproduces the files.
"""
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "art" / "fonts"
OUT = ROOT / "app" / "src" / "main" / "res" / "font"

# (source file, output resource name, pinned axis values)
INSTANCES = [
    ("Figtree-VF.ttf", "figtree_regular", {"wght": 400}),
    ("Figtree-VF.ttf", "figtree_medium", {"wght": 500}),
    ("Figtree-VF.ttf", "figtree_semibold", {"wght": 600}),
    ("Figtree-VF.ttf", "figtree_bold", {"wght": 700}),
    ("Figtree-VF.ttf", "figtree_extrabold", {"wght": 800}),
    ("Fraunces-VF.ttf", "fraunces_bold", {"wght": 700, "opsz": 28, "SOFT": 100, "WONK": 0}),
    ("Fraunces-VF.ttf", "fraunces_extrabold", {"wght": 800, "opsz": 28, "SOFT": 100, "WONK": 0}),
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for src, name, axes in INSTANCES:
        # Keep the source's head.modified; the default stamps "now" and churns the files.
        font = TTFont(SRC / src, recalcTimestamp=False)
        tags = {a.axisTag for a in font["fvar"].axes}
        if set(axes) != tags:
            # An unpinned axis would leave a partial variable font behind.
            raise SystemExit(f"{name}: pin exactly {sorted(tags)}, got {sorted(axes)}")
        static = instancer.instantiateVariableFont(font, axes, updateFontNames=False)
        path = OUT / f"{name}.ttf"
        static.save(path)
        print(f"{path.relative_to(ROOT)}  {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
