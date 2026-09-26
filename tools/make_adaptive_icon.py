#!/usr/bin/env python3
"""
ForgeBuild adaptive-icon generator: foreground artwork in -> correctly
safe-zoned adaptive icon layers out.

Adaptive icon spec: total canvas 108x108dp, the inner 72x72dp (centered) is the
safe zone guaranteed visible in every mask. This script:
  * scales the foreground artwork to fit INSIDE the 72dp safe zone (66% of canvas)
  * centers it on a 108dp canvas with transparent padding (never a white square)
  * generates background (solid color or artwork), mipmap densities, and the
    adaptive-icon XML.

MONOCHROME / THEMED ICONS POLICY (Android 13+, enforced by this tool):
  The source artwork is classified as vector/icon-style vs raster/photo-style.
    * Vector/icon-style source (flat shapes, few colors, icon-like): a
      purpose-drawn <monochrome> layer (alpha silhouette of the foreground) is
      generated and referenced from the adaptive-icon XML, opting the app into
      themed icons with a clean, controllable glyph.
    * Raster/photo-style source (complex, full-color, photographic): the
      <monochrome> element is OMITTED ENTIRELY and no ic_launcher_monochrome
      assets are written (stale ones are deleted). A baked black silhouette of
      a photo renders as a flat, detail-less blob under forced themed icons;
      with no explicit monochrome layer the launcher falls back to auto-tracing
      the foreground layer's own alpha/detail, preserving shape detail
      (the behavior seen on Duolingo/Drive/Filmora etc.). This is the real
      platform fallback, not a workaround.
  Override with --monochrome force|skip if the operator/AI wants to decide
  manually; default is --monochrome auto (classify the source).

Usage:
  python3 tools/make_adaptive_icon.py --foreground icon_fg.png \
      --background-color "#6750A4" --out app/src/main/res [--background bg.png] \
      [--monochrome auto|force|skip]

If the operator supplies their own artwork, pass it as --foreground. If not,
the generating AI produces simple artwork first, then calls this script.
"""
import argparse, pathlib, sys

try:
    from PIL import Image, ImageFilter
except ImportError:
    sys.exit("pip install pillow")

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_FRACTION = 72 / 108  # inner safe zone

# --- source classification thresholds (vector/icon-style vs raster/photo-style) ---
CLASSIFY_SIZE = 144            # probe resolution; small + deterministic
SIG_COLOR_LIMIT = 24           # more significant 4-bit color bins than this => raster/photo
SIG_COLOR_MIN_SHARE = 0.005    # a bin is significant if it covers > 0.5% of pixels
EDGE_DENSITY_LIMIT = 0.15      # strong-edge pixel fraction (flattened) => raster/photo
INTERIOR_EDGE_LIMIT = 0.06     # strong-edge fraction INSIDE the opaque subject => raster/photo

def is_raster_like(img: Image.Image) -> bool:
    """True when the source looks raster/photo-style rather than flat icon-style.

    Three anti-aliasing-robust signals on a downscaled probe:
      1. significant color count: flatten onto white, quantize to 4 bits/channel,
         and count only bins covering > 0.5% of pixels. Anti-aliasing turns a flat
         icon's 3 source colors into hundreds of single-pixel shades after a
         LANCZOS resize, so raw distinct-color counts are useless — significant
         bins are not (flat icon art: a handful; photos/gradients: dozens+).
      2. edge density of the flattened luminance channel (flat icon art is mostly
         uniform regions with sparse edges; photos have detail everywhere).
      3. INTERIOR edge density: same edge metric restricted to pixels opaque in
         the source (alpha >= 128). A flat icon's edges sit almost entirely on
         its silhouette (interior ~0); a photo subject on transparent background
         has a clean silhouette but a textured interior (gradient/noise/detail),
         so its interior density stays high. This separates Duolingo-style raster
         mascots from Material-Symbols-style flat glyphs.
    """
    probe = img.convert("RGBA")
    probe.thumbnail((CLASSIFY_SIZE, CLASSIFY_SIZE), Image.LANCZOS)
    total = float(probe.width * probe.height)
    flat = Image.new("RGB", probe.size, (255, 255, 255))
    flat.paste(probe, (0, 0), probe)
    bins = {}
    for r, g, b in flat.getdata():
        key = (r >> 4, g >> 4, b >> 4)
        bins[key] = bins.get(key, 0) + 1
    significant = sum(1 for n in bins.values() if n > SIG_COLOR_MIN_SHARE * total)
    if significant > SIG_COLOR_LIMIT:
        return True
    edges = flat.convert("L").filter(ImageFilter.FIND_EDGES)
    edge_vals = list(edges.getdata())
    strong = sum(1 for v in edge_vals if v > 32)
    if strong / total > EDGE_DENSITY_LIMIT:
        return True
    alpha = probe.split()[3]
    interior_strong = interior_total = 0
    for v, a in zip(edge_vals, alpha.getdata()):
        if a >= 128:
            interior_total += 1
            if v > 32:
                interior_strong += 1
    return interior_total > 0 and interior_strong / float(interior_total) > INTERIOR_EDGE_LIMIT

XML_ANYDPI_MONO = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
</adaptive-icon>
"""

XML_ANYDPI_PLAIN = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""

def make_layers(fg_path, bg_color, bg_path, out: pathlib.Path, mono: bool | None = None):
    """Write all density layers + adaptive-icon XML. Returns True if a purpose-drawn
    monochrome layer was emitted, False if the <monochrome> element was omitted."""
    fg_src = Image.open(fg_path).convert("RGBA")
    if mono is None:
        mono = not is_raster_like(fg_src)
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
        if mono:
            # monochrome (themed icons): alpha silhouette of the foreground
            mono_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            alpha = fg.split()[3]
            black = Image.new("RGBA", fg.size, (0, 0, 0, 255))
            black.putalpha(alpha)
            mono_img.paste(black, ((size - fg.width) // 2, (size - fg.height) // 2), black)
            mono_img.save(d / "ic_launcher_monochrome.png")
        else:
            # raster/photo path: NO monochrome element may reference a baked blob,
            # and no stale monochrome asset from an earlier run may linger.
            stale = d / "ic_launcher_monochrome.png"
            if stale.exists():
                stale.unlink()
    xml = XML_ANYDPI_MONO if mono else XML_ANYDPI_PLAIN
    for name in ("mipmap-anydpi-v26",):
        d = out / name; d.mkdir(parents=True, exist_ok=True)
        (d / "ic_launcher.xml").write_text(xml)
        (d / "ic_launcher_round.xml").write_text(xml)
    return mono

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--foreground", required=True)
    ap.add_argument("--background-color", default="#6750A4")
    ap.add_argument("--background", default=None)
    ap.add_argument("--out", default="app/src/main/res")
    ap.add_argument("--monochrome", choices=("auto", "force", "skip"), default="auto",
                    help="auto = classify the source (default); force = always emit a "
                         "purpose-drawn <monochrome> layer; skip = never emit one")
    a = ap.parse_args()
    out = pathlib.Path(a.out)
    mono_override = {"auto": None, "force": True, "skip": False}[a.monochrome]
    emitted = make_layers(a.foreground, a.background_color, a.background, out, mono=mono_override)
    style = "vector/icon-style" if emitted else "raster/photo-style"
    decision = ("purpose-drawn <monochrome> layer emitted"
                if emitted else
                "<monochrome> element OMITTED (launcher foreground-alpha auto-trace fallback)")
    print(f"source classification: {style} -> {decision}")
    print("adaptive icon layers written to", out)

if __name__ == "__main__":
    main()
