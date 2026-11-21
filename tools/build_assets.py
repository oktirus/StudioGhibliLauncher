#!/usr/bin/env python3
"""Asset packaging & audio synthesis pipeline for StudioGhibliLauncher.

Processes generated Studio Ghibli originals into runtime TV assets:
- 8 full 1920x1080 wallpapers + 420x236 WebP thumbnails
- Clean RGBA WebP focus effect cutouts (soot sprites, Calcifer, Kodama)
- Runner stage & hero sprites derived from generated art
- Cohesive hand-styled Ghibli botanical crest, archive lantern, and 19 TV app medallions
- Original warm pentatonic Ghibli-mood WAV cues & seamless ambient music loops
- Complete docs/asset-manifest.json with SHA-256 hashes and provenance
"""

from __future__ import annotations

import collections
import hashlib
import json
import math
import os
import shutil
import struct
import wave
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ARTIFACTS_ENV = os.environ.get("GHIBLI_ARTIFACTS_DIR", "").strip()
ARTIFACTS_DIR = Path(ARTIFACTS_ENV) if ARTIFACTS_ENV else None
ORIGINALS_DIR = ROOT / "art" / "originals"
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
DOCS_DIR = ROOT / "docs"

GENERATED_SCENES = {
    "totoro_forest": "scene_totoro_forest_1795282723256.jpg",
    "bathhouse_dusk": "scene_bathhouse_dusk_1795282733001.jpg",
    "howl_meadow": "scene_howl_meadow_1795282741936.jpg",
    "kiki_coast": "scene_kiki_coast_v2_1795282781059.jpg",
    "mononoke_cedars": "scene_mononoke_cedars_1795282790047.jpg",
    "laputa_sky": "scene_laputa_sky_1795282799793.jpg",
    "ponyo_cliff": "scene_ponyo_cliff_1795282810251.jpg",
    "winter_train": "scene_winter_train_1795282822949.jpg",
}

GENERATED_EFFECTS = {
    "soot-idle": ("effect_soot_idle_1795282853694.jpg", "white"),
    "soot-jump": ("effect_soot_jump_1795282864223.jpg", "white"),
    "calcifer": ("effect_calcifer_1795282873990.jpg", "black"),
    "kodama": ("effect_kodama_1795282884508.jpg", "black"),
}


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def strip_dark_letterbox(img: Image.Image) -> Image.Image:
    """Removes thin top/bottom black letterbox bars if present."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    top = 0
    while top < h // 10:
        row_avg = sum(px[x, top][0] + px[x, top][1] + px[x, top][2] for x in range(0, w, 8)) / (
            3 * max(1, len(range(0, w, 8)))
        )
        if row_avg > 18:
            break
        top += 1
    bottom = h - 1
    while bottom > h - h // 10:
        row_avg = sum(
            px[x, bottom][0] + px[x, bottom][1] + px[x, bottom][2] for x in range(0, w, 8)
        ) / (3 * max(1, len(range(0, w, 8))))
        if row_avg > 18:
            break
        bottom -= 1
    if top > 0 or bottom < h - 1:
        # Add 2px safety trim to remove any dark JPEG ringing edge
        top = min(top + 2, h // 8)
        bottom = max(bottom - 2, h - h // 8)
        return img.crop((0, top, w, bottom + 1))
    return img


def fit_cover(img: Image.Image, target_w: int, target_h: int) -> Image.Image:
    w, h = img.size
    scale = max(target_w / w, target_h / h)
    nw, nh = int(round(w * scale)), int(round(h * scale))
    resized = img.resize((nw, nh), Image.Resampling.LANCZOS)
    left = (nw - target_w) // 2
    top = (nh - target_h) // 2
    return resized.crop((left, top, left + target_w, top + target_h))


def remove_white_bg_floodfill(img: Image.Image, target_size: int = 384) -> Image.Image:
    """Removes solid white outer backdrop via edge BFS while preserving interior white (e.g. eyes)."""
    rgba = img.convert("RGBA")
    w, h = rgba.size
    px = rgba.load()

    visited = bytearray(w * h)
    queue: collections.deque[tuple[int, int]] = collections.deque()

    def is_bg(r: int, g: int, b: int) -> bool:
        return r >= 232 and g >= 232 and b >= 232 and max(r, g, b) - min(r, g, b) <= 22

    for x in range(w):
        for y in (0, h - 1):
            r, g, b, _ = px[x, y]
            if is_bg(r, g, b):
                idx = y * w + x
                if not visited[idx]:
                    visited[idx] = 1
                    queue.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            r, g, b, _ = px[x, y]
            if is_bg(r, g, b):
                idx = y * w + x
                if not visited[idx]:
                    visited[idx] = 1
                    queue.append((x, y))

    while queue:
        cx, cy = queue.popleft()
        px[cx, cy] = (0, 0, 0, 0)
        for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
            if 0 <= nx < w and 0 <= ny < h:
                nidx = ny * w + nx
                if not visited[nidx]:
                    r, g, b, _ = px[nx, ny]
                    if is_bg(r, g, b):
                        visited[nidx] = 1
                        queue.append((nx, ny))

    # Feather near-white fringe pixels directly bordering transparent pixels
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            has_trans = (
                px[x - 1, y][3] == 0
                or px[x + 1, y][3] == 0
                or px[x, y - 1][3] == 0
                or px[x, y + 1][3] == 0
            )
            if has_trans:
                lum = (r + g + b) / 3.0
                if lum > 205 and max(r, g, b) - min(r, g, b) < 35:
                    new_a = max(0, min(255, int((242 - lum) * 5.0)))
                    px[x, y] = (r, g, b, new_a)

    # Crop tightly to visible silhouette with 10% padding into square
    bbox = rgba.getbbox()
    if bbox:
        cropped = rgba.crop(bbox)
        cw, ch = cropped.size
        side = int(max(cw, ch) * 1.14)
        canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        canvas.paste(cropped, ((side - cw) // 2, (side - ch) // 2), cropped)
        rgba = canvas

    return rgba.resize((target_size, target_size), Image.Resampling.LANCZOS)


def remove_black_bg_luma(img: Image.Image, target_size: int = 448) -> Image.Image:
    """Converts pitch-black backdrop of glowing aura rings into smooth feathered alpha."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    out = Image.new("RGBA", (w, h))
    src = rgb.load()
    dst = out.load()

    for y in range(h):
        for x in range(w):
            r, g, b = src[x, y]
            mx = max(r, g, b)
            if mx <= 14:
                dst[x, y] = (0, 0, 0, 0)
            elif mx < 70:
                alpha = int(((mx - 14) / 56.0) * 235)
                # Un-premultiply slightly to keep glow color vivid
                scale = 255.0 / max(1, mx)
                boost = 0.55 + 0.45 * (mx / 70.0)
                nr = min(255, int(r * (1.0 + (scale - 1.0) * 0.35) * boost))
                ng = min(255, int(g * (1.0 + (scale - 1.0) * 0.35) * boost))
                nb = min(255, int(b * (1.0 + (scale - 1.0) * 0.35) * boost))
                dst[x, y] = (nr, ng, nb, max(0, min(255, alpha)))
            else:
                alpha = min(255, int(235 + (mx - 70) * 20.0 / 185.0))
                dst[x, y] = (r, g, b, alpha)

    return out.resize((target_size, target_size), Image.Resampling.LANCZOS)


def extract_kodama_runner_sprites(kodama_src: Path) -> None:
    """Creates crisp, clean Kodama woodland spirit sprites (zero hard crop edges) for Runner hero 2."""
    def draw_kodama(tilted_deg: float = 0.0, squash_y: float = 1.0) -> Image.Image:
        sz = 512
        canvas = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
        # Soft luminous emerald-ivory forest spirit aura
        glow = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse([96, 62, 416, 448], fill=(170, 232, 188, 95))
        glow = glow.filter(ImageFilter.GaussianBlur(radius=32))
        canvas.alpha_composite(glow)

        body = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
        d = ImageDraw.Draw(body)
        outline = (42, 74, 56, 255)
        fill_main = (232, 248, 236, 252)
        fill_shade = (188, 224, 198, 252)

        # Torso & cute little legs
        d.rounded_rectangle([188, 248, 324, 396], radius=58, fill=fill_main, outline=outline, width=10)
        d.chord([188, 248, 324, 396], 300, 120, fill=fill_shade)
        d.rounded_rectangle([188, 248, 324, 396], radius=58, outline=outline, width=10)
        # Little arms reaching forward joyfully
        d.rounded_rectangle([296, 276, 376, 314], radius=18, fill=fill_main, outline=outline, width=9)
        d.rounded_rectangle([146, 286, 206, 324], radius=18, fill=fill_main, outline=outline, width=9)
        # Legs
        d.rounded_rectangle([200, 366, 246, 434], radius=22, fill=fill_main, outline=outline, width=9)
        d.rounded_rectangle([266, 366, 312, 434], radius=22, fill=fill_main, outline=outline, width=9)

        # Iconic tilted Kodama head (large round cel-shaded head with subtle green shadow)
        head = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
        hd = ImageDraw.Draw(head)
        hd.ellipse([136, 74, 376, 294], fill=fill_main, outline=outline, width=10)
        # Soft cel shadow crescent on right cheek
        hd.arc([148, 86, 364, 282], -40, 75, fill=fill_shade, width=26)
        hd.ellipse([136, 74, 376, 294], outline=outline, width=10)
        # Expressive Kodama dark forest eyes + small round o-mouth
        hd.ellipse([186, 152, 234, 204], fill=outline)
        hd.ellipse([282, 148, 336, 204], fill=outline)
        hd.ellipse([246, 224, 274, 248], fill=outline)
        head = head.rotate(tilted_deg, center=(256, 184), resample=Image.Resampling.BICUBIC)
        body.alpha_composite(head)

        if squash_y != 1.0:
            nh = int(sz * squash_y)
            squashed = body.resize((sz, nh), Image.Resampling.LANCZOS)
            shifted = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
            shifted.paste(squashed, (0, sz - nh - 16), squashed)
            body = shifted

        canvas.alpha_composite(body)
        return canvas.resize((256, 256), Image.Resampling.LANCZOS)

    draw_kodama(tilted_deg=-10.0, squash_y=1.0).save(
        ASSETS_DIR / "game" / "game-kodama-motion.webp", "WEBP", quality=92
    )
    draw_kodama(tilted_deg=-22.0, squash_y=0.72).save(
        ASSETS_DIR / "game" / "game-kodama-slide.webp", "WEBP", quality=92
    )


def build_runner_assets(howl_src: Path, soot_jump_webp: Path, soot_idle_webp: Path, kodama_src: Path) -> None:
    """Creates seamless Ghibli meadow scrolling stage & runner hero sprites."""
    meadow = Image.open(howl_src).convert("RGB")
    mw, mh = meadow.size
    # Take the lush left-mid meadow section (sky + rolling green alpine hills + wildflowers)
    left_slice = meadow.crop((0, int(mh * 0.10), int(mw * 0.56), int(mh * 0.94)))
    left_slice = left_slice.resize((960, 540), Image.Resampling.LANCZOS)
    mirrored = left_slice.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    stage = Image.new("RGB", (1920, 540))
    stage.paste(left_slice, (0, 0))
    stage.paste(mirrored, (960, 0))
    # Soft feather across center seam
    seam = stage.crop((944, 0, 976, 540)).filter(ImageFilter.GaussianBlur(radius=4))
    stage.paste(seam, (944, 0))
    stage.save(ASSETS_DIR / "game" / "ghibli-valley.webp", "WEBP", quality=88)

    # Soot Sprite runner hero (from generated soot-jump & soot-idle cutouts)
    soot_m = Image.open(soot_jump_webp).convert("RGBA").resize((256, 256), Image.Resampling.LANCZOS)
    soot_m.save(ASSETS_DIR / "game" / "game-soot-motion.webp", "WEBP", quality=92)

    soot_i = Image.open(soot_idle_webp).convert("RGBA")
    soot_squish = soot_i.resize((256, 180), Image.Resampling.LANCZOS)
    soot_slide = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    soot_slide.paste(soot_squish, (0, 68), soot_squish)
    soot_slide.save(ASSETS_DIR / "game" / "game-soot-slide.webp", "WEBP", quality=92)

    extract_kodama_runner_sprites(kodama_src)


def draw_ghibli_medallion_base(size: int = 512, rim_color=(226, 186, 102), fill_top=(38, 66, 52), fill_bot=(21, 40, 32)) -> Image.Image:
    """Creates a warm hand-crafted Ghibli botanical wood/parchment medallion frame."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    pad = int(size * 0.08)

    # Soft warm drop shadow
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle(
        [pad, pad + 8, size - pad, size - pad + 8],
        radius=int(size * 0.22),
        fill=(8, 16, 12, 130),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=14))
    img.alpha_composite(shadow)

    # Gradient wooden/forest medallion body
    body = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bpx = body.load()
    for y in range(pad, size - pad):
        t = (y - pad) / max(1, (size - 2 * pad))
        r = int(fill_top[0] * (1 - t) + fill_bot[0] * t)
        g = int(fill_top[1] * (1 - t) + fill_bot[1] * t)
        b = int(fill_top[2] * (1 - t) + fill_bot[2] * t)
        for x in range(pad, size - pad):
            # Subtle watercolor paper modulation
            grain = int(math.sin(x * 0.09 + y * 0.05) * 4 + math.cos(x * 0.04 - y * 0.08) * 3)
            bpx[x, y] = (
                max(0, min(255, r + grain)),
                max(0, min(255, g + grain)),
                max(0, min(255, b + grain)),
                248,
            )

    mask = Image.new("L", (size, size), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=int(size * 0.22), fill=255)
    body.putalpha(mask)
    img.alpha_composite(body)

    # Outer warm gold-parchment frame + inner delicate botanical hairline
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle(
        [pad, pad, size - pad, size - pad],
        radius=int(size * 0.22),
        outline=(*rim_color, 235),
        width=int(size * 0.022),
    )
    inner = pad + int(size * 0.045)
    draw.rounded_rectangle(
        [inner, inner, size - inner, size - inner],
        radius=int(size * 0.18),
        outline=(246, 238, 216, 70),
        width=int(size * 0.008),
    )

    # Tiny corner sprout leaf ornament (top-right corner)
    lx, ly = size - pad - 34, pad + 24
    draw.ellipse([lx - 14, ly - 7, lx + 14, ly + 7], fill=(138, 184, 126, 210))
    draw.ellipse([lx - 6, ly - 16, lx + 8, ly + 4], fill=(162, 204, 140, 210))
    return img


def load_font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    font_path = ASSETS_DIR / "fonts" / ("NunitoSans.ttf" if bold else "Caveat.ttf")
    try:
        return ImageFont.truetype(str(font_path), size)
    except Exception:
        return ImageFont.load_default()


def build_branding_and_icons() -> None:
    """Generates Studio Ghibli watercolor crest, archive icon, and 19 cohesive TV app icons."""
    # 1. Brand Crest (420x150 transparent WebP) — warm botanical leaf emblem + "GHIBLI · Долина"
    cw, ch = 560, 190
    crest = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    cd = ImageDraw.Draw(crest)
    # Soft warm badge medallion on left
    cx, cy, cr = 74, 95, 56
    cd.ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=(32, 56, 44, 228), outline=(232, 184, 88, 235), width=5)
    cd.ellipse([cx - cr + 10, cy - cr + 10, cx + cr - 10, cy + cr - 10], outline=(246, 238, 216, 95), width=2)
    # Stylized Ghibli Totoro-leaf / soot-star silhouette inside medallion
    cd.polygon(
        [(cx, cy - 34), (cx + 24, cy + 4), (cx + 12, cy + 28), (cx - 12, cy + 28), (cx - 24, cy + 4)],
        fill=(146, 194, 132, 240),
    )
    cd.line([(cx, cy - 26), (cx, cy + 32)], fill=(32, 56, 44, 240), width=4)
    cd.line([(cx, cy - 2), (cx + 12, cy - 12)], fill=(32, 56, 44, 220), width=3)
    cd.line([(cx, cy + 10), (cx - 12, cy)], fill=(32, 56, 44, 220), width=3)
    # Small golden konpeito star beside the leaf
    cd.ellipse([cx + 16, cy - 28, cx + 30, cy - 14], fill=(242, 206, 118, 255))

    font_title = ImageFont.truetype(str(ASSETS_DIR / "fonts" / "Caveat.ttf"), 74)
    font_sub = ImageFont.truetype(str(ASSETS_DIR / "fonts" / "NunitoSans.ttf"), 24)
    # Warm shadow + crisp cream hand-lettering
    cd.text((148, 26), "Ghibli", font=font_title, fill=(10, 20, 15, 180))
    cd.text((145, 23), "Ghibli", font=font_title, fill=(246, 238, 216, 255))
    cd.text((148, 112), "ВІТЕР НАД ДОЛИНОЮ", font=font_sub, fill=(10, 20, 15, 180))
    cd.text((146, 110), "ВІТЕР НАД ДОЛИНОЮ", font=font_sub, fill=(232, 184, 88, 245))
    crest.resize((360, 122), Image.Resampling.LANCZOS).save(
        ASSETS_DIR / "branding" / "ghibli-crest.webp", "WEBP", quality=92
    )

    # 2. Archive icon (warm wooden lantern / storybook library)
    arch = draw_ghibli_medallion_base(512, rim_color=(232, 184, 88), fill_top=(44, 72, 56), fill_bot=(24, 44, 34))
    ad = ImageDraw.Draw(arch)
    # Storybook grid + hanging lantern glow
    for row in range(2):
        for col in range(2):
            bx = 148 + col * 116
            by = 148 + row * 116
            fill = (232, 184, 88, 240) if (row + col) % 2 == 0 else (164, 206, 148, 235)
            ad.rounded_rectangle([bx, by, bx + 96, by + 96], radius=22, fill=fill, outline=(246, 238, 216, 220), width=5)
    arch.resize((256, 256), Image.Resampling.LANCZOS).save(
        ASSETS_DIR / "icons" / "icon-archive.webp", "WEBP", quality=90
    )

    # 3. 19 Cohesive Ghibli TV App Icons
    apps_spec = [
        ("smarttube", "ST", (217, 107, 75), "play"),
        ("youtube", "YT", (214, 82, 68), "play"),
        ("lampa", "LP", (236, 186, 86), "lantern"),
        ("uakino", "UA", (102, 168, 212), "film"),
        ("netflix", "N", (204, 68, 58), "ribbon"),
        ("kodi", "KD", (92, 174, 188), "diamond"),
        ("spotify", "SP", (112, 186, 124), "waves"),
        ("crunchyroll", "CR", (234, 146, 68), "spiral"),
        ("vlc", "VLC", (232, 134, 62), "cone"),
        ("plex", "PX", (232, 184, 82), "chevron"),
        ("tvbro", "WEB", (118, 176, 198), "compass"),
        ("kyivstar", "KT", (96, 168, 218), "star"),
        ("megogo", "MG", (134, 196, 168), "badge"),
        ("sweettv", "SW", (116, 184, 162), "heart"),
        ("primevideo", "PV", (98, 172, 208), "smile"),
        ("disneyplus", "D+", (106, 152, 214), "arc"),
        ("jellyfin", "JF", (158, 138, 204), "drop"),
        ("sendfiles", "SF", (128, 192, 146), "arrows"),
        ("xplore", "XP", (218, 176, 104), "folder"),
    ]

    font_badge = ImageFont.truetype(str(ASSETS_DIR / "fonts" / "NunitoSans.ttf"), 118)
    font_small = ImageFont.truetype(str(ASSETS_DIR / "fonts" / "NunitoSans.ttf"), 88)

    for kind, label, accent, motif in apps_spec:
        icon = draw_ghibli_medallion_base(512, rim_color=accent, fill_top=(36, 60, 48), fill_bot=(20, 36, 28))
        d = ImageDraw.Draw(icon)
        cx, cy = 256, 256

        # Soft watercolor halo behind emblem
        halo = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        hd = ImageDraw.Draw(halo)
        hd.ellipse([cx - 125, cy - 125, cx + 125, cy + 125], fill=(*accent, 58))
        halo = halo.filter(ImageFilter.GaussianBlur(radius=28))
        icon.alpha_composite(halo)
        d = ImageDraw.Draw(icon)

        if motif == "play":
            d.rounded_rectangle([126, 158, 386, 354], radius=46, fill=(*accent, 235), outline=(246, 238, 216, 230), width=6)
            d.polygon([(224, 206), (224, 306), (316, 256)], fill=(248, 242, 224, 250))
        elif motif == "lantern":
            d.ellipse([166, 134, 346, 336], fill=(*accent, 235), outline=(246, 238, 216, 235), width=6)
            d.rectangle([216, 114, 296, 146], fill=(110, 74, 48, 255))
            d.rectangle([216, 326, 296, 366], fill=(110, 74, 48, 255))
            d.ellipse([212, 184, 300, 284], fill=(255, 248, 218, 215))
        elif motif == "waves":
            d.ellipse([136, 136, 376, 376], fill=(*accent, 230), outline=(246, 238, 216, 220), width=6)
            for off, rad in ((-38, 92), (0, 74), (36, 56)):
                d.arc([cx - rad, cy + off - 24, cx + rad, cy + off + 44], 205, 335, fill=(24, 42, 32, 245), width=16)
        elif motif == "compass":
            d.ellipse([136, 136, 376, 376], fill=(32, 54, 44, 230), outline=(*accent, 240), width=12)
            d.polygon([(cx, 158), (cx + 28, cy), (cx, 354), (cx - 28, cy)], fill=(246, 238, 216, 240))
            d.polygon([(cx, 158), (cx + 28, cy), (cx, cy)], fill=(217, 107, 75, 245))
        elif motif == "folder":
            d.rounded_rectangle([124, 174, 388, 356], radius=28, fill=(*accent, 235), outline=(246, 238, 216, 230), width=6)
            d.rounded_rectangle([124, 144, 252, 200], radius=18, fill=(*accent, 245))
            d.line([(168, 256), (344, 256)], fill=(30, 48, 38, 200), width=12)
            d.line([(168, 296), (296, 296)], fill=(30, 48, 38, 170), width=10)
        elif motif == "chevron":
            d.polygon([(180, 146), (262, 146), (342, 256), (262, 366), (180, 366), (256, 256)], fill=(*accent, 245), outline=(246, 238, 216, 230))
        elif motif == "star":
            pts = []
            for k in range(10):
                ang = math.radians(-90 + k * 36)
                rr = 112 if k % 2 == 0 else 48
                pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
            d.polygon(pts, fill=(*accent, 240), outline=(246, 238, 216, 230))
        elif motif == "diamond":
            d.polygon([(cx, 132), (380, cy), (cx, 380), (132, cy)], fill=(*accent, 235), outline=(246, 238, 216, 230))
            d.polygon([(cx, 190), (322, cy), (cx, 322), (190, cy)], fill=(26, 46, 38, 230))
        elif motif == "arrows":
            d.polygon([(148, 232), (274, 232), (274, 184), (368, 256), (274, 328), (274, 280), (148, 280)], fill=(*accent, 240))
        else:
            # Crisp warm parchment emblem disc with unmistakable initials
            d.ellipse([136, 136, 376, 376], fill=(*accent, 230), outline=(246, 238, 216, 230), width=6)
            fnt = font_small if len(label) >= 3 else font_badge
            bbox = d.textbbox((0, 0), label, font=fnt)
            tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
            d.text((cx - tw / 2 - bbox[0], cy - th / 2 - bbox[1] - 4), label, font=fnt, fill=(248, 243, 228, 252))

        icon.resize((256, 256), Image.Resampling.LANCZOS).save(
            ASSETS_DIR / "icons" / f"icon-{kind}.webp", "WEBP", quality=90
        )


def write_wav(path: Path, samples: list[float], sample_rate: int = 22050) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    peak = max((abs(s) for s in samples), default=1.0)
    scale = 0.86 / peak if peak > 0.86 else 1.0
    pcm = bytearray()
    for s in samples:
        v = max(-1.0, min(1.0, s * scale))
        pcm.extend(struct.pack("<h", int(v * 32767)))
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(bytes(pcm))


def synth_chime_note(freq: float, dur: float, sr: int = 22050, timbre: str = "celesta", amp: float = 0.5) -> list[float]:
    n = int(dur * sr)
    out = []
    for i in range(n):
        t = i / sr
        attack = min(1.0, t / 0.012)
        decay = math.exp(-t * (4.2 if timbre == "celesta" else 3.0 if timbre == "kalimba" else 2.2))
        env = attack * decay
        if timbre == "celesta":
            sig = (
                math.sin(2 * math.pi * freq * t)
                + 0.35 * math.sin(2 * math.pi * freq * 2.0 * t) * math.exp(-t * 6.0)
                + 0.12 * math.sin(2 * math.pi * freq * 3.0 * t) * math.exp(-t * 9.0)
            )
        elif timbre == "kalimba":
            sig = (
                math.sin(2 * math.pi * freq * t)
                + 0.25 * math.sin(2 * math.pi * freq * 2.92 * t) * math.exp(-t * 14.0)
            )
        else:  # warm flute/pad
            vib = 1.0 + 0.004 * math.sin(2 * math.pi * 5.0 * t)
            sig = (
                math.sin(2 * math.pi * freq * vib * t)
                + 0.22 * math.sin(2 * math.pi * freq * 2.0 * t)
            )
        out.append(sig * env * amp)
    return out


def mix_into(buf: list[float], note: list[float], start_idx: int) -> None:
    end = min(len(buf), start_idx + len(note))
    for i in range(max(0, start_idx), end):
        buf[i] += note[i - start_idx]


def build_audio_assets() -> None:
    """Synthesizes original warm Studio Ghibli-inspired pentatonic cues and looping ambient tracks."""
    sr = 22050

    # UI chime (soft celesta two-note sparkle)
    buf = [0.0] * int(0.32 * sr)
    mix_into(buf, synth_chime_note(659.25, 0.22, sr, "celesta", 0.42), 0)
    mix_into(buf, synth_chime_note(987.77, 0.24, sr, "celesta", 0.32), int(0.06 * sr))
    write_wav(ASSETS_DIR / "audio" / "ghibli-ui-chime.wav", buf, sr)

    # 6 Spirit focus cues (short, crisp, warm) + 6 confirm cues (richer melodic cadence)
    spirit_cues = {
        "soot_sprites": ([523.25, 659.25, 783.99], "kalimba"),
        "calcifer": ([440.0, 554.37, 659.25], "celesta"),
        "kodama": ([587.33, 880.0, 1174.66], "kalimba"),
        "wind_petal": ([493.88, 659.25, 987.77], "flute"),
        "aetherium": ([523.25, 783.99, 1046.5], "celesta"),
        "paper_birds": ([659.25, 783.99, 1046.5], "flute"),
    }
    for sid, (freqs, timbre) in spirit_cues.items():
        # Focus cue (~0.26s)
        fbuf = [0.0] * int(0.28 * sr)
        mix_into(fbuf, synth_chime_note(freqs[0], 0.20, sr, timbre, 0.40), 0)
        mix_into(fbuf, synth_chime_note(freqs[1], 0.20, sr, timbre, 0.32), int(0.05 * sr))
        write_wav(ASSETS_DIR / "audio" / f"ghibli-{sid}-focus.wav", fbuf, sr)

        # Confirm cue (~0.62s)
        cbuf = [0.0] * int(0.65 * sr)
        for idx, fr in enumerate(freqs):
            mix_into(cbuf, synth_chime_note(fr, 0.42, sr, timbre, 0.42 - idx * 0.04), int(idx * 0.08 * sr))
        mix_into(cbuf, synth_chime_note(freqs[0] * 2.0, 0.36, sr, "celesta", 0.24), int(0.24 * sr))
        write_wav(ASSETS_DIR / "audio" / f"ghibli-{sid}-confirm.wav", cbuf, sr)

    # Runner mini-game cues
    runner_defs = {
        "start": [(523.25, 0.0, 0.2), (659.25, 0.08, 0.2), (783.99, 0.16, 0.28)],
        "jump": [(440.0, 0.0, 0.12), (659.25, 0.04, 0.18)],
        "slide": [(392.0, 0.0, 0.14), (293.66, 0.04, 0.16)],
        "land": [(220.0, 0.0, 0.12)],
        "clear": [(783.99, 0.0, 0.18), (1046.5, 0.06, 0.22)],
        "hit": [(246.94, 0.0, 0.22), (196.0, 0.06, 0.26)],
    }
    for rname, notes in runner_defs.items():
        rbuf = [0.0] * int(0.42 * sr)
        for fr, st, du in notes:
            mix_into(rbuf, synth_chime_note(fr, du, sr, "kalimba", 0.45), int(st * sr))
        write_wav(ASSETS_DIR / "audio" / "runner" / f"{rname}.wav", rbuf, sr)

    # 3 Original Ghibli-mood ambient music loops (~16 seconds each, smooth crossfade seam)
    tracks = {
        "meadow_morning": (
            # Warm C major / A minor pastoral music box & soft pad
            [261.63, 329.63, 392.00, 440.00, 523.25, 440.00, 392.00, 329.63,
             293.66, 349.23, 440.00, 523.25, 587.33, 523.25, 440.00, 392.00,
             329.63, 392.00, 523.25, 659.25, 587.33, 523.25, 440.00, 392.00,
             261.63, 329.63, 392.00, 523.25, 440.00, 392.00, 329.63, 261.63],
            [130.81, 174.61, 164.81, 196.00],
            "celesta",
        ),
        "lantern_dusk": (
            # Nostalgic twilight pentatonic F major / D minor kalimba & warm pad
            [349.23, 440.00, 523.25, 587.33, 523.25, 440.00, 392.00, 349.23,
             293.66, 349.23, 440.00, 523.25, 440.00, 392.00, 349.23, 329.63,
             349.23, 440.00, 587.33, 698.46, 587.33, 523.25, 440.00, 392.00,
             349.23, 440.00, 523.25, 440.00, 392.00, 349.23, 329.63, 293.66],
            [146.83, 174.61, 130.81, 146.83],
            "kalimba",
        ),
        "sky_garden": (
            # Airy G major / E minor floating sky flute & chime
            [392.00, 493.88, 587.33, 659.25, 783.99, 659.25, 587.33, 493.88,
             329.63, 392.00, 493.88, 587.33, 659.25, 587.33, 493.88, 440.00,
             392.00, 493.88, 659.25, 783.99, 880.00, 783.99, 659.25, 587.33,
             493.88, 587.33, 659.25, 587.33, 493.88, 440.00, 392.00, 392.00],
            [196.00, 164.81, 130.81, 146.83],
            "flute",
        ),
    }

    for tid, (melody, bass_prog, timbre) in tracks.items():
        dur = 16.0
        total = int(dur * sr)
        tbuf = [0.0] * total
        step = dur / len(melody)
        for idx, freq in enumerate(melody):
            st = int(idx * step * sr)
            mix_into(tbuf, synth_chime_note(freq, step * 2.2, sr, timbre, 0.24), st)
        # Warm soft bass pad per bar (4 bars of 4s)
        bar_len = total // len(bass_prog)
        for b_idx, b_freq in enumerate(bass_prog):
            b_start = b_idx * bar_len
            for i in range(bar_len):
                t = i / sr
                env = math.sin(math.pi * (i / bar_len)) ** 0.6
                pad = (
                    0.13 * math.sin(2 * math.pi * b_freq * t)
                    + 0.06 * math.sin(2 * math.pi * b_freq * 1.5 * t)
                ) * env
                tbuf[b_start + i] += pad
        # Smooth loop edges (80ms fade-in/out)
        fade = int(0.08 * sr)
        for i in range(fade):
            k = i / fade
            tbuf[i] *= k
            tbuf[total - 1 - i] *= k
        write_wav(ASSETS_DIR / "audio" / f"music-{tid}.wav", tbuf, sr)


def main() -> None:
    ORIGINALS_DIR.mkdir(parents=True, exist_ok=True)
    DOCS_DIR.mkdir(parents=True, exist_ok=True)
    manifest_items = []

    # 1. Process 8 generated Studio Ghibli wallpapers
    for scene_id, src_name in GENERATED_SCENES.items():
        orig_dst = ORIGINALS_DIR / f"scene-{scene_id}.jpg"
        if ARTIFACTS_DIR is not None and (ARTIFACTS_DIR / src_name).exists():
            shutil.copy2(ARTIFACTS_DIR / src_name, orig_dst)
        src_path = orig_dst

        img = Image.open(src_path).convert("RGB")
        img = strip_dark_letterbox(img)
        full = fit_cover(img, 1920, 1080)
        runtime_scene = ASSETS_DIR / "scenes" / f"scene-{scene_id}.jpg"
        full.save(runtime_scene, "JPEG", quality=88, optimize=True, progressive=True)

        thumb = fit_cover(img, 420, 236)
        runtime_thumb = ASSETS_DIR / "scenes" / f"thumb-{scene_id}.webp"
        thumb.save(runtime_thumb, "WEBP", quality=84)

        manifest_items.append(
            {
                "id": f"scene_{scene_id}",
                "kind": "wallpaper",
                "source": "generate_image (gemini-3.1-flash-image)",
                "originalPath": f"art/originals/scene-{scene_id}.jpg",
                "runtimePath": f"app/src/main/assets/scenes/scene-{scene_id}.jpg",
                "thumbPath": f"app/src/main/assets/scenes/thumb-{scene_id}.webp",
                "width": 1920,
                "height": 1080,
                "alpha": False,
                "safeRegion": "left 45% calm UI safe zone; subject on right 55%",
                "sha256": sha256_file(runtime_scene),
                "status": "approved",
            }
        )

    # 2. Process 4 generated Studio Ghibli effect sprites
    for eff_id, (src_name, mode) in GENERATED_EFFECTS.items():
        orig_dst = ORIGINALS_DIR / f"effect-{eff_id}.jpg"
        if ARTIFACTS_DIR is not None and (ARTIFACTS_DIR / src_name).exists():
            shutil.copy2(ARTIFACTS_DIR / src_name, orig_dst)
        src_path = orig_dst

        img = Image.open(src_path)
        if mode == "white":
            rgba = remove_white_bg_floodfill(img, target_size=384)
        else:
            rgba = remove_black_bg_luma(img, target_size=448)

        runtime_eff = ASSETS_DIR / "effects" / f"effect-{eff_id}.webp"
        rgba.save(runtime_eff, "WEBP", quality=92)
        manifest_items.append(
            {
                "id": f"effect_{eff_id}",
                "kind": "focus_effect",
                "source": "generate_image (gemini-3.1-flash-image) + alpha cutout",
                "originalPath": f"art/originals/effect-{eff_id}.jpg",
                "runtimePath": f"app/src/main/assets/effects/effect-{eff_id}.webp",
                "width": rgba.width,
                "height": rgba.height,
                "alpha": True,
                "sha256": sha256_file(runtime_eff),
                "status": "approved",
            }
        )

    # 3. Runner stage & heroes derived from generated Ghibli art
    build_runner_assets(
        ORIGINALS_DIR / "scene-howl_meadow.jpg",
        ASSETS_DIR / "effects" / "effect-soot-jump.webp",
        ASSETS_DIR / "effects" / "effect-soot-idle.webp",
        ORIGINALS_DIR / "effect-kodama.jpg",
    )

    # 4. Cohesive Ghibli crest + 20 hand-painted Ghibli app/archive icons
    build_branding_and_icons()
    icon_manifest_items = extract_generated_icon_sheets()
    manifest_items.extend(icon_manifest_items)

    # 5. Original synthesized Ghibli-mood pentatonic audio
    build_audio_assets()

    # Add summary entries for derived/procedural packs
    for rel in [
        "app/src/main/assets/game/ghibli-valley.webp",
        "app/src/main/assets/game/game-soot-motion.webp",
        "app/src/main/assets/game/game-soot-slide.webp",
        "app/src/main/assets/game/game-kodama-motion.webp",
        "app/src/main/assets/game/game-kodama-slide.webp",
        "app/src/main/assets/branding/ghibli-crest.webp",
    ]:
        p = ROOT / rel
        im = Image.open(p)
        manifest_items.append(
            {
                "id": p.stem,
                "kind": "derived_or_procedural_asset",
                "runtimePath": rel,
                "width": im.width,
                "height": im.height,
                "alpha": im.mode in ("RGBA", "LA"),
                "sha256": sha256_file(p),
                "status": "approved",
            }
        )

    manifest = {
        "project": "StudioGhibliLauncher (Ghibli · Вітер над долиною)",
        "generated_note": (
            "All 8 wallpapers and 4 spirit sprites (soot-idle, soot-jump, calcifer, kodama) "
            "were generated via gemini-3.1-flash-image and visually verified. "
            "All 20 hand-painted Ghibli wooden-and-brass medallion icons (19 TV apps + All Apps archive) "
            "were generated as 5 style-locked 2x2 sheets via Gemini Web (work-with-argon / Orca CLI pipeline), "
            "audited by BFS component count, flood-fill alpha-extracted with white-halo defringing, and visually verified. "
            "Procedural Canvas spirits (wind_petal, aetherium, paper_birds) and original pentatonic WAV audio "
            "are rendered natively / synthesized cleanly."
        ),
        "assets": manifest_items,
    }
    (DOCS_DIR / "asset-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"Built {len(manifest_items)} tracked visual assets (incl. {len(icon_manifest_items)} hand-painted icons) + original Ghibli audio.")


ICON_SHEETS_MAP = [
    ("sheet_01.png", ["lampa", "kodi", "youtube", "smarttube"]),
    ("sheet_02.png", ["uakino", "megogo", "netflix", "archive"]),
    ("sheet_03.png", ["spotify", "crunchyroll", "vlc", "plex"]),
    ("sheet_04.png", ["disneyplus", "primevideo", "jellyfin", "kyivstar"]),
    ("sheet_05.png", ["sweettv", "tvbro", "sendfiles", "xplore"]),
]


def _flood_remove_white_with_defringe(img: Image.Image) -> tuple[Image.Image, bytearray]:
    """Flood-fill remove solid white background from borders + defringe 2px edge halo."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    px = list(rgb.getdata())
    bg = bytearray(w * h)
    q = collections.deque()

    def is_white_bg(r: int, g: int, b: int) -> bool:
        return min(r, g, b) >= 232 and (max(r, g, b) - min(r, g, b)) <= 22

    for x in range(w):
        for y in (0, h - 1):
            idx = y * w + x
            if not bg[idx] and is_white_bg(*px[idx]):
                bg[idx] = 1
                q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            idx = y * w + x
            if not bg[idx] and is_white_bg(*px[idx]):
                bg[idx] = 1
                q.append((x, y))

    while q:
        cx, cy = q.popleft()
        for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
            if 0 <= nx < w and 0 <= ny < h:
                nidx = ny * w + nx
                if not bg[nidx] and is_white_bg(*px[nidx]):
                    bg[nidx] = 1
                    q.append((nx, ny))

    # Build RGBA pixels with soft edge anti-aliasing & white un-premultiplication
    out = [(0, 0, 0, 0)] * (w * h)
    # Identify 1-2px border band adjacent to background
    dist = bytearray(w * h)
    border_q = collections.deque()
    for y in range(h):
        row_off = y * w
        for x in range(w):
            idx = row_off + x
            if bg[idx]:
                continue
            touches_bg = False
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h and bg[ny * w + nx]:
                    touches_bg = True
                    break
            if touches_bg:
                dist[idx] = 1
                border_q.append((x, y))

    while border_q:
        cx, cy = border_q.popleft()
        cd = dist[cy * w + cx]
        if cd >= 2:
            continue
        for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
            if 0 <= nx < w and 0 <= ny < h:
                nidx = ny * w + nx
                if not bg[nidx] and dist[nidx] == 0:
                    dist[nidx] = cd + 1
                    border_q.append((nx, ny))

    for idx in range(w * h):
        if bg[idx]:
            out[idx] = (0, 0, 0, 0)
            continue
        r, g, b = px[idx]
        d = dist[idx]
        if d > 0:
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            if lum > 155:
                # Semi-transparent white-blended anti-aliased edge against dark sepia outline (~48, 36, 26)
                t = max(0.0, min(1.0, (242.0 - lum) / (242.0 - 155.0)))
                a = int(round(t * 255))
                if a <= 8:
                    out[idx] = (0, 0, 0, 0)
                    bg[idx] = 1
                else:
                    # Un-premultiply white halo toward dark outline tone
                    alpha_f = a / 255.0
                    ur = max(0, min(255, int(round((r - 255.0 * (1.0 - alpha_f)) / alpha_f))))
                    ug = max(0, min(255, int(round((g - 255.0 * (1.0 - alpha_f)) / alpha_f))))
                    ub = max(0, min(255, int(round((b - 255.0 * (1.0 - alpha_f)) / alpha_f))))
                    out[idx] = (ur, ug, ub, a)
                continue
        out[idx] = (r, g, b, 255)

    rgba = Image.new("RGBA", (w, h))
    rgba.putdata(out)
    return rgba, bg


def extract_generated_icon_sheets() -> list[dict]:
    """Segment the 5 Gemini-generated 2x2 Ghibli icon sheets into 20 256x256 RGBA WebP icons."""
    sheets_dir = ORIGINALS_DIR / "icon_sheets"
    icons_dir = ASSETS_DIR / "icons"
    icons_dir.mkdir(parents=True, exist_ok=True)
    items: list[dict] = []

    for sheet_name, icon_kinds in ICON_SHEETS_MAP:
        sheet_path = sheets_dir / sheet_name
        if not sheet_path.exists():
            raise FileNotFoundError(f"Missing generated icon sheet: {sheet_path}")

        raw_img = Image.open(sheet_path)
        rgba, bg = _flood_remove_white_with_defringe(raw_img)
        w, h = rgba.size

        # BFS connected components on foreground mask
        fg_vis = bytearray(w * h)
        comps = []
        for y in range(h):
            row_off = y * w
            for x in range(w):
                idx = row_off + x
                if not bg[idx] and not fg_vis[idx]:
                    q = collections.deque([(x, y)])
                    fg_vis[idx] = 1
                    cnt = 1
                    minx = maxx = x
                    miny = maxy = y
                    while q:
                        cx, cy = q.popleft()
                        for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                            if 0 <= nx < w and 0 <= ny < h:
                                nidx = ny * w + nx
                                if not bg[nidx] and not fg_vis[nidx]:
                                    fg_vis[nidx] = 1
                                    cnt += 1
                                    if nx < minx: minx = nx
                                    if nx > maxx: maxx = nx
                                    if ny < miny: miny = ny
                                    if ny > maxy: maxy = ny
                                    q.append((nx, ny))
                    if cnt > 2000:
                        comps.append((minx, miny, maxx, maxy))

        if len(comps) != 4:
            raise RuntimeError(f"{sheet_name}: expected 4 icon components, found {len(comps)}")

        # Sort strictly into 2x2 order: top-left, top-right, bottom-left, bottom-right
        comps.sort(key=lambda b: (0 if (b[1] + b[3]) // 2 < h // 2 else 1, (b[0] + b[2]) // 2))

        for kind, (minx, miny, maxx, maxy) in zip(icon_kinds, comps):
            crop = rgba.crop((minx, miny, maxx + 1, maxy + 1))
            cw, ch = crop.size
            target = 256
            inner = 236
            scale = min(inner / cw, inner / ch)
            nw = max(1, int(round(cw * scale)))
            nh = max(1, int(round(ch * scale)))
            resized = crop.resize((nw, nh), Image.Resampling.LANCZOS)
            canvas = Image.new("RGBA", (target, target), (0, 0, 0, 0))
            ox = (target - nw) // 2
            oy = (target - nh) // 2
            canvas.paste(resized, (ox, oy), resized)

            out_path = icons_dir / f"icon-{kind}.webp"
            canvas.save(out_path, "WEBP", quality=92, method=6)
            items.append(
                {
                    "id": f"icon_{kind}",
                    "kind": "menu_icon" if kind == "archive" else "app_icon",
                    "appMapping": "archive (All Apps)" if kind == "archive" else kind,
                    "source": "Gemini Web via Orca CLI (work-with-argon 2x2 sheet + flood-fill alpha)",
                    "originalPath": f"art/originals/icon_sheets/{sheet_name}",
                    "runtimePath": f"app/src/main/assets/icons/icon-{kind}.webp",
                    "width": target,
                    "height": target,
                    "alpha": True,
                    "sha256": sha256_file(out_path),
                    "status": "approved",
                }
            )
    return items


if __name__ == "__main__":
    main()
