#!/usr/bin/env python3
"""
ForgeBuild adaptive-icon generator: foreground artwork in -> correctly
safe-zoned adaptive icon layers out.

Adaptive icon spec: total canvas 108x108dp, the inner 72x72dp (centered) is the
safe zone guaranteed visible in every mask. This script:
  * scales the foreground artwork to fit INSIDE the 72dp safe zone (66% of canvas)
  * centers it on a 108dp canvas with transparent padding (never a white square)
  * generates background (solid color or artwork), mipmap densities, and the
    adaptive-icon XML, plus a monochrome layer for themed icons (API 33+).

Usage:
  python3 tools/make_adaptive_icon.py --foreground icon_fg.png \
      --background-color "#6750A4" --out app/src/main/res [--background bg.png]

If the operator supplies their own artwork, pass it as --foreground. If not,
the generating AI produces simple artwork first, then calls this script.
"""
import argparse, pathlib, sys

try:
    from PIL import Image
except ImportError:
    sys.exit("pip install pillow")

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_FRACTION = 72 / 108  # inner safe zone

def make_layers(fg_path, bg_color, bg_path, out: pathlib.Path):
    fg_src = Image.open(fg_path).convert("RGBA")
    if bg_path: bg_src = Image.open(bg_path).convert("RGBA")
    for density, size in DENSITIES.items():
        d = out / f"mipmap-{density}"
        d.mkdir(parents=True, exist_ok=True)
        # background layer: full 108dp
        if bg_path:
            bg = bg_src.resize((size, size), Image.LANCZOS)
        else:
            bg = Image.new("RGBA", (size, size), bg_color)
        bg.save(d / "ic_launcher_background.png")
        # foreground layer: artwork scaled to safe zone, centered, transparent outside
        safe = int(size * SAFE_FRACTION)
        fg = fg_src.copy(); fg.thumbnail((safe, safe), Image.LANCZOS)
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        layer.paste(fg, ((size - fg.width) // 2, (size - fg.height) // 2), fg)
        layer.save(d / "ic_launcher_foreground.png")
        # monochrome (themed icons): alpha silhouette of the foreground
        mono = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        alpha = fg.split()[3]
        black = Image.new("RGBA", fg.size, (0, 0, 0, 255))
        black.putalpha(alpha)
        mono.paste(black, ((size - fg.width) // 2, (size - fg.height) // 2), black)
        mono.save(d / "ic_launcher_monochrome.png")

XML_ANYDPI = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
</adaptive-icon>
"""

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--foreground", required=True)
    ap.add_argument("--background-color", default="#6750A4")
    ap.add_argument("--background", default=None)
    ap.add_argument("--out", default="app/src/main/res")
    a = ap.parse_args()
    out = pathlib.Path(a.out)
    make_layers(a.foreground, a.background_color, a.background, out)
    for name in ("mipmap-anydpi-v26",):
        d = out / name; d.mkdir(parents=True, exist_ok=True)
        (d / "ic_launcher.xml").write_text(XML_ANYDPI)
        (d / "ic_launcher_round.xml").write_text(XML_ANYDPI)
    print("adaptive icon layers written to", out)

if __name__ == "__main__":
    main()
