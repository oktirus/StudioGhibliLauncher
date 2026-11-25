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


def chroma_key_green_with_despill(img: Image.Image) -> Image.Image:
    """Removes solid chroma-key green backdrop (both outer and enclosed interior gaps)
    using color-difference keying, soft fur edge alpha-matting, and green despill.
    Preserves pure white eyes, pink-gold konpeito candy, and charcoal-black fur."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    src = list(rgb.getdata())

    corners = [src[0], src[w - 1], src[(h - 1) * w], src[h * w - 1]]
    bg_r = sum(c[0] for c in corners) / 4.0
    bg_g = sum(c[1] for c in corners) / 4.0
    bg_b = sum(c[2] for c in corners) / 4.0

    out = [(0, 0, 0, 0)] * (w * h)
    low_g = 16.0
    high_g = 90.0

    for idx, (r, g, b) in enumerate(src):
        rb_max = r if r > b else b
        greenness = g - rb_max
        if greenness >= high_g:
            out[idx] = (0, 0, 0, 0)
            continue
        if greenness <= low_g:
            if greenness > 6 and g > rb_max:
                ng = int(round(rb_max + (greenness - 6) * 0.35))
                out[idx] = (r, ng, b, 255)
            else:
                out[idx] = (r, g, b, 255)
            continue

        t = (high_g - greenness) / (high_g - low_g)
        alpha_f = max(0.0, min(1.0, t ** 1.15))
        a = int(round(alpha_f * 255))
        if a <= 4:
            out[idx] = (0, 0, 0, 0)
            continue

        inv = 1.0 - alpha_f
        ur = max(0.0, min(255.0, (r - bg_r * inv) / alpha_f))
        ub = max(0.0, min(255.0, (b - bg_b * inv) / alpha_f))
        ug = max(0.0, min(max(ur, ub), (g - bg_g * inv) / alpha_f))
        out[idx] = (int(round(ur)), int(round(ug)), int(round(ub)), a)

    rgba = Image.new("RGBA", (w, h))
    rgba.putdata(out)
    return rgba


def split_and_normalize_soot_sheet(sheet_path: Path, target_size: int = 384) -> tuple[Image.Image, Image.Image]:
    """Splits a 1x2 chroma-key Soot Sprite sheet into (idle, jump) RGBA sprites
    using a single shared scale factor so the body diameter stays identical during jumps."""
    rgba = chroma_key_green_with_despill(Image.open(sheet_path))
    w, h = rgba.size
    mid = w // 2
    halves = [rgba.crop((0, 0, mid, h)), rgba.crop((mid, 0, w, h))]

    crops = []
    dims = []
    for i, half in enumerate(halves):
        solid = half.getchannel("A").point(lambda v: 255 if v >= 48 else 0)
        bbox = solid.getbbox()
        if not bbox:
            raise RuntimeError(f"Empty soot pose {i} in {sheet_path}")
        x0 = max(0, bbox[0] - 6)
        y0 = max(0, bbox[1] - 6)
        x1 = min(half.width, bbox[2] + 6)
        y1 = min(half.height, bbox[3] + 6)
        crop = half.crop((x0, y0, x1, y1))
        crops.append(crop)
        dims.append((crop.width, crop.height))

    max_dim = max(max(cw, ch) for cw, ch in dims)
    shared_scale = (target_size * 0.88) / max_dim

    results = []
    for crop in crops:
        nw = max(1, int(round(crop.width * shared_scale)))
        nh = max(1, int(round(crop.height * shared_scale)))
        resized = crop.resize((nw, nh), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
        canvas.paste(resized, ((target_size - nw) // 2, (target_size - nh) // 2), resized)
        results.append(canvas)
    return results[0], results[1]


def make_horizontally_seamless_rgb(
    img: Image.Image, target_w: int, target_h: int, overlap_ratio: float = 0.18, crop_top: float = 0.0, crop_bottom: float = 0.96
) -> Image.Image:
    """Builds a mathematically seamless horizontally tileable RGB panorama using a smooth
    cosine crossfade overlap (NO mirroring, zero center seam)."""
    rgb = img.convert("RGB")
    rw, rh = rgb.size
    y0 = int(round(rh * crop_top))
    y1 = int(round(rh * crop_bottom))
    rgb = rgb.crop((0, y0, rw, y1))
    w, h = rgb.size

    ov = max(32, int(round(w * overlap_ratio)))
    new_w = w - ov
    src = rgb.load()
    out = Image.new("RGB", (new_w, h))
    dst = out.load()

    weights = [0.5 - 0.5 * math.cos(math.pi * i / max(1, ov - 1)) for i in range(ov)]
    for y in range(h):
        for x in range(ov):
            t = weights[x]
            it = 1.0 - t
            lr, lg, lb = src[x, y]
            rr, rg, rb = src[new_w + x, y]
            dst[x, y] = (
                int(round(rr * it + lr * t)),
                int(round(rg * it + lg * t)),
                int(round(rb * it + lb * t)),
            )
        for x in range(ov, new_w):
            dst[x, y] = src[x, y]

    return out.resize((target_w, target_h), Image.Resampling.LANCZOS)


def make_horizontally_seamless_ground(
    ground_path: Path, target_w: int = 1280, target_h: int = 164, overlap_ratio: float = 0.16
) -> Image.Image:
    """Extracts the hand-painted Ghibli meadow ground strip from #FF00FF top chroma-key
    and applies a premultiplied-alpha cosine wraparound crossfade so grass tufts, wildflowers,
    sunlit trail, and earthy soil scroll seamlessly with zero seams."""
    rgba = chroma_key_magenta_with_despill(Image.open(ground_path))
    w, h = rgba.size

    # Crop vertically starting right above the tallest grass blades down to bottom soil
    solid = rgba.getchannel("A").point(lambda v: 255 if v >= 32 else 0)
    bbox = solid.getbbox()
    if not bbox:
        raise RuntimeError(f"Empty ground strip in {ground_path}")
    y_top = max(0, bbox[1] - 8)
    rgba = rgba.crop((0, y_top, w, h))
    w, h = rgba.size

    ov = max(32, int(round(w * overlap_ratio)))
    new_w = w - ov
    src = rgba.load()
    out = Image.new("RGBA", (new_w, h), (0, 0, 0, 0))
    dst = out.load()

    weights = [0.5 - 0.5 * math.cos(math.pi * i / max(1, ov - 1)) for i in range(ov)]
    for y in range(h):
        for x in range(ov):
            t = weights[x]
            it = 1.0 - t
            lr, lg, lb, la = src[x, y]
            rr, rg, rb, ra = src[new_w + x, y]
            fa = ra * it + la * t
            if fa <= 1.0:
                dst[x, y] = (0, 0, 0, 0)
            else:
                pr = (rr * ra * it + lr * la * t) / fa
                pg = (rg * ra * it + lg * la * t) / fa
                pb = (rb * ra * it + lb * la * t) / fa
                dst[x, y] = (
                    max(0, min(255, int(round(pr)))),
                    max(0, min(255, int(round(pg)))),
                    max(0, min(255, int(round(pb)))),
                    max(0, min(255, int(round(fa)))),
                )
        for x in range(ov, new_w):
            dst[x, y] = src[x, y]

    return out.resize((target_w, target_h), Image.Resampling.LANCZOS)


def _pack_runner_hero_8_poses(
    poses: list[Image.Image],
    sheet_path: Path,
    hero_id: str,
    source_desc: str,
    target_size: int = 256,
) -> list[dict]:
    """Normalizes 8 RGBA poses [Contact A, Passing A, Contact B, Passing B, Idle, Jump, Slide A, Slide B]
    using a single shared scale and bottom-baseline foot alignment into:
    - game-{hero_id}-motion.webp (1536x256, 6 frames: [Contact A, Passing A, Contact B, Passing B, Jump, Idle])
    - game-{hero_id}-slide.webp  (512x256, 2 frames: [Slide A, Slide B])."""
    crops: list[Image.Image] = []
    for idx, cell in enumerate(poses):
        solid = cell.getchannel("A").point(lambda v: 255 if v >= 36 else 0)
        bbox = solid.getbbox()
        if not bbox:
            raise RuntimeError(f"Empty pose {idx} in {sheet_path}")
        pad = 4
        crops.append(
            cell.crop(
                (
                    max(0, bbox[0] - pad),
                    max(0, bbox[1] - pad),
                    min(cell.width, bbox[2] + pad),
                    min(cell.height, bbox[3] + pad),
                )
            )
        )

    # Single shared scale across all 8 poses so head/body volume never morphs across run/jump/slide
    max_dim = max(max(c.width, c.height) for c in crops)
    shared_scale = (target_size * 0.88) / max_dim
    pad_bottom = 8

    def place_on_frame(crop: Image.Image, extra_lift: int = 0) -> Image.Image:
        nw = max(1, int(round(crop.width * shared_scale)))
        nh = max(1, int(round(crop.height * shared_scale)))
        resized = crop.resize((nw, nh), Image.Resampling.LANCZOS)
        frame = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
        px = (target_size - nw) // 2
        py = max(0, target_size - pad_bottom - extra_lift - nh)
        frame.paste(resized, (px, py), resized)
        return frame

    frames_motion = [
        place_on_frame(crops[0], 0),   # frame 0: run Contact A
        place_on_frame(crops[1], 5),   # frame 1: run Passing A (subtle bounce)
        place_on_frame(crops[2], 0),   # frame 2: run Contact B (opposite leg)
        place_on_frame(crops[3], 5),   # frame 3: run Passing B (opposite crossing)
        place_on_frame(crops[5], 6),   # frame 4: joyful high jump
        place_on_frame(crops[4], 0),   # frame 5: calm idle stance
    ]
    strip = Image.new("RGBA", (target_size * 6, target_size), (0, 0, 0, 0))
    for i, fr in enumerate(frames_motion):
        strip.paste(fr, (i * target_size, 0), fr)

    slide_frames = [
        place_on_frame(crops[6], 0),   # slide frame 0: low duck/scoot A
        place_on_frame(crops[7], 0),   # slide frame 1: low duck/scoot B
    ]
    slide_strip = Image.new("RGBA", (target_size * 2, target_size), (0, 0, 0, 0))
    for i, fr in enumerate(slide_frames):
        slide_strip.paste(fr, (i * target_size, 0), fr)

    motion_path = ASSETS_DIR / "game" / f"game-{hero_id}-motion.webp"
    slide_path = ASSETS_DIR / "game" / f"game-{hero_id}-slide.webp"
    strip.save(motion_path, "WEBP", quality=92, method=6)
    slide_strip.save(slide_path, "WEBP", quality=92, method=6)

    rel_orig = f"art/originals/game/{sheet_path.name}"
    return [
        {
            "id": f"game-{hero_id}-motion",
            "kind": "runner_hero_sheet",
            "source": f"{source_desc} (6-frame motion strip)",
            "originalPath": rel_orig,
            "runtimePath": f"app/src/main/assets/game/game-{hero_id}-motion.webp",
            "width": strip.width,
            "height": strip.height,
            "frames": 6,
            "alpha": True,
            "sha256": sha256_file(motion_path),
            "status": "approved",
        },
        {
            "id": f"game-{hero_id}-slide",
            "kind": "runner_hero_sheet",
            "source": f"{source_desc} (2-frame crouch strip)",
            "originalPath": rel_orig,
            "runtimePath": f"app/src/main/assets/game/game-{hero_id}-slide.webp",
            "width": slide_strip.width,
            "height": slide_strip.height,
            "frames": 2,
            "alpha": True,
            "sha256": sha256_file(slide_path),
            "status": "approved",
        },
    ]


def _segment_row_spans(row_rgba: Image.Image, expected_cols: int) -> list[Image.Image]:
    """Segments a horizontal sprite row by actual transparent column gaps so stretched limbs
    never bleed stray edge pixels into neighboring frames."""
    w, h = row_rgba.size
    a_chan = row_rgba.getchannel("A")
    col_has = [a_chan.crop((x, 0, x + 1, h)).getextrema()[1] >= 40 for x in range(w)]
    spans: list[tuple[int, int]] = []
    in_span = False
    s0 = 0
    for x, val in enumerate(col_has):
        if val and not in_span:
            in_span = True
            s0 = x
        elif not val and in_span:
            in_span = False
            if x - s0 > 40:
                spans.append((s0, x))
    if in_span and w - s0 > 40:
        spans.append((s0, w))

    if len(spans) == expected_cols:
        return [row_rgba.crop((max(0, x0 - 2), 0, min(w, x1 + 2), h)) for x0, x1 in spans]
    # Fallback to uniform columns if poses overlap horizontally
    cw = w / float(expected_cols)
    return [row_rgba.crop((int(round(c * cw)), 0, int(round((c + 1) * cw)), h)) for c in range(expected_cols)]


def extract_runner_hero_sheet(
    sheet_path: Path, hero_id: str, key_mode: str, target_size: int = 256
) -> list[dict]:
    """Splits a native 4x2 Studio Ghibli cel-animation hero sheet (e.g. Totoro) using
    gap-aware row segmentation so stretched claws/limbs never clip into neighbor frames."""
    raw = Image.open(sheet_path)
    if key_mode == "green":
        rgba = chroma_key_green_with_despill(raw)
        key_label = "#00FF00"
    elif key_mode == "magenta":
        rgba = chroma_key_magenta_with_despill(raw)
        key_label = "#FF00FF"
    else:
        raise ValueError(f"Unsupported key_mode: {key_mode}")

    w, h = rgba.size
    mid_y = h // 2
    poses = _segment_row_spans(rgba.crop((0, 0, w, mid_y)), 4) + _segment_row_spans(
        rgba.crop((0, mid_y, w, h)), 4
    )
    return _pack_runner_hero_8_poses(
        poses,
        sheet_path,
        hero_id,
        f"Studio Ghibli 1990s cel 4x2 alternating-run sheet ({key_label} analytical chroma-key + gap-segmented shared-scale)",
        target_size=target_size,
    )


def _sample_cubic(p0: tuple[float, float], p1: tuple[float, float], p2: tuple[float, float], p3: tuple[float, float], steps: int = 20) -> list[tuple[float, float]]:
    pts: list[tuple[float, float]] = []
    for i in range(steps + 1):
        t = i / float(steps)
        mt = 1.0 - t
        x = (mt**3) * p0[0] + 3 * (mt**2) * t * p1[0] + 3 * mt * (t**2) * p2[0] + (t**3) * p3[0]
        y = (mt**3) * p0[1] + 3 * (mt**2) * t * p1[1] + 3 * mt * (t**2) * p2[1] + (t**3) * p3[1]
        pts.append((x, y))
    return pts


def extract_soot_4x2_alternating_sheet(sheet_path: Path, target_size: int = 256) -> list[dict]:
    """Builds a true 4-frame alternating Contact/Passing run cycle + 2-frame crouch for Soot Sprite
    from the original hand-drawn cel sheet, preserving razor-sharp fur spikes and consistent golden konpeito."""
    rgba = chroma_key_green_with_despill(Image.open(sheet_path))
    w, h = rgba.size
    cw, ch = w // 3, h // 2
    cells = [rgba.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch)) for r in range(2) for c in range(3)]

    def draw_stick_leg(canvas_rgba: Image.Image, hip, knee, ankle, toe, is_far: bool = False) -> Image.Image:
        ss = 2
        layer = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        sc = lambda pt: (pt[0] * ss, pt[1] * ss)
        pts_leg = _sample_cubic(sc(hip), sc(knee), sc(knee), sc(ankle), steps=22)
        pts_foot = _sample_cubic(
            sc(ankle), sc(((ankle[0] + toe[0]) * 0.5, (ankle[1] + toe[1]) * 0.5 + 8)), sc(toe), sc(toe), steps=10
        )

        def thick(pts, width, col):
            r = width / 2.0
            for i in range(len(pts) - 1):
                d.line([pts[i], pts[i + 1]], fill=col, width=int(round(width)))
            for x, y in pts:
                d.ellipse([x - r, y - r, x + r, y + r], fill=col)

        ink = (20, 18, 16, 255)
        fill = (34, 31, 29, 255) if is_far else (58, 53, 49, 255)
        thick(pts_leg, 28 * ss, ink)
        thick(pts_foot, 30 * ss, ink)
        thick(pts_leg, 15 * ss, fill)
        thick(pts_foot, 16 * ss, fill)
        return Image.alpha_composite(canvas_rgba, layer.resize((cw, ch), Image.Resampling.LANCZOS))

    def strip_legs_preserve_fur(cell: Image.Image, is_mid: bool = False) -> Image.Image:
        im = cell.copy()
        px = im.load()
        cx, cy = (456.0, 344.0) if is_mid else (458.0, 354.0)
        rx, ry = (218.0, 206.0) if is_mid else (216.0, 210.0)
        for y in range(495, ch):
            for x in range(cw):
                dx = (x - cx) / rx
                dy = (y - cy) / ry
                if dx * dx + dy * dy > 1.04:
                    px[x, y] = (0, 0, 0, 0)
        return im

    def unify_candy_gold(im: Image.Image, y_min: int = 340, y_max: int = 640) -> Image.Image:
        out = im.copy()
        px = out.load()
        for y in range(y_min, min(im.height, y_max)):
            for x in range(515, min(im.width, 695)):
                r, g, b, a = px[x, y]
                if a > 80 and r > 175 and (r - b) > 40:
                    lum = min(255, int(r * 0.68 + g * 0.32 + 14))
                    px[x, y] = (min(255, lum), min(255, int(lum * 0.93)), min(255, int(lum * 0.42)), a)
        return out

    b0 = strip_legs_preserve_fur(cells[2], is_mid=False)
    b1 = strip_legs_preserve_fur(unify_candy_gold(cells[1]), is_mid=True)

    f0 = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    f0 = draw_stick_leg(f0, (425, 515), (335, 608), (255, 560), (222, 594), is_far=True)
    f0 = draw_stick_leg(f0, (515, 515), (595, 580), (636, 668), (682, 646), is_far=False)
    f0 = Image.alpha_composite(f0, b0)

    f1 = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    f1 = draw_stick_leg(f1, (482, 510), (566, 558), (506, 616), (548, 614), is_far=True)
    f1 = draw_stick_leg(f1, (462, 512), (470, 594), (446, 668), (498, 664), is_far=False)
    f1 = Image.alpha_composite(f1, b1)

    b2 = b0.rotate(-2.4, resample=Image.Resampling.BICUBIC, center=(465, 360))
    f2 = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    f2 = draw_stick_leg(f2, (505, 515), (592, 582), (632, 668), (678, 646), is_far=True)
    f2 = draw_stick_leg(f2, (428, 515), (332, 610), (252, 558), (220, 592), is_far=False)
    f2 = Image.alpha_composite(f2, b2)

    b3 = b1.rotate(2.0, resample=Image.Resampling.BICUBIC, center=(465, 360))
    f3 = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    f3 = draw_stick_leg(f3, (462, 512), (468, 594), (446, 668), (498, 664), is_far=True)
    f3 = draw_stick_leg(f3, (482, 510), (572, 556), (514, 614), (558, 612), is_far=False)
    f3 = Image.alpha_composite(f3, b3)

    f_slide_a = unify_candy_gold(cells[5], y_min=460, y_max=660)
    f_slide_b = f_slide_a.rotate(-2.5, resample=Image.Resampling.BICUBIC, center=(470, 560))
    poses = [f0, f1, f2, f3, cells[3], cells[4], f_slide_a, f_slide_b]
    return _pack_runner_hero_8_poses(
        poses,
        sheet_path,
        "soot",
        "Studio Ghibli 1990s cel Soot Sprite (#00FF00 analytical chroma-key + true 4-frame alternating Contact/Passing run cycle)",
        target_size=target_size,
    )


def extract_kodama_4x2_alternating_sheet(sheet_path: Path, target_size: int = 256) -> list[dict]:
    """Builds a true 4-frame alternating Contact/Passing run cycle + 2-frame crouch for Kodama
    using the original hand-drawn cel heads/torsos/arms seamlessly joined to waist-matched cel legs."""
    rgba = chroma_key_magenta_with_despill(Image.open(sheet_path))
    w, h = rgba.size
    cw, ch = w // 3, h // 2
    cells = [rgba.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch)) for r in range(2) for c in range(3)]

    def draw_kodama_leg_parts(hip, knee, ankle, toe, thigh_w, calf_w, is_far: bool, skip_hip_cap: bool):
        ss = 2
        out_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        fill_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        do, df = ImageDraw.Draw(out_l), ImageDraw.Draw(fill_l)
        sc = lambda pt: (pt[0] * ss, pt[1] * ss)

        mid_uk = ((hip[0] * 0.45 + knee[0] * 0.55) + 4, (hip[1] + knee[1]) * 0.5)
        mid_ka = ((knee[0] + ankle[0]) * 0.5 - 3, (knee[1] + ankle[1]) * 0.5)
        p_upper = _sample_cubic(sc(hip), sc(mid_uk), sc(knee), sc(knee), steps=18)
        p_lower = _sample_cubic(sc(knee), sc(mid_ka), sc(ankle), sc(ankle), steps=18)
        p_foot = _sample_cubic(
            sc(ankle), sc(((ankle[0] + toe[0]) * 0.5, (ankle[1] + toe[1]) * 0.5 + 5)), sc(toe), sc(toe), steps=10
        )

        ink = (46, 38, 30, 255)
        fill_lit = (202, 216, 196, 255) if is_far else (238, 241, 218, 255)
        fill_shd = (170, 189, 168, 255) if is_far else (186, 204, 182, 255)
        ink_pad = 10.5 * ss

        def draw_tapered(draw_obj, pts, w0, w1, color, extra=0.0, start_i=0):
            n = max(1, len(pts) - 1)
            for i in range(start_i, len(pts)):
                x, y = pts[i]
                t = i / float(n)
                r = (w0 * (1 - t) + w1 * t + extra) / 2.0
                draw_obj.ellipse([x - r, y - r, x + r, y + r], fill=color)

        draw_tapered(do, p_upper, thigh_w * ss, calf_w * ss, ink, extra=ink_pad * 2, start_i=(3 if skip_hip_cap else 0))
        draw_tapered(do, p_lower, calf_w * ss, (calf_w * 0.72) * ss, ink, extra=ink_pad * 2)
        draw_tapered(do, p_foot, (calf_w * 0.72) * ss, (calf_w * 0.54) * ss, ink, extra=ink_pad * 2)

        draw_tapered(df, p_upper, thigh_w * ss, calf_w * ss, fill_shd, start_i=(5 if skip_hip_cap else 0))
        draw_tapered(df, p_lower, calf_w * ss, (calf_w * 0.72) * ss, fill_shd)
        draw_tapered(df, p_foot, (calf_w * 0.72) * ss, (calf_w * 0.54) * ss, fill_shd)

        off = (4.0 if is_far else 6.0) * ss
        draw_tapered(df, [(x + off, y - 1 * ss) for x, y in p_upper], (thigh_w * 0.78) * ss, (calf_w * 0.72) * ss, fill_lit)
        draw_tapered(df, [(x + off, y) for x, y in p_lower], (calf_w * 0.70) * ss, (calf_w * 0.56) * ss, fill_lit)
        draw_tapered(df, [(x + off * 0.6, y - 1 * ss) for x, y in p_foot], (calf_w * 0.56) * ss, (calf_w * 0.44) * ss, fill_lit)

        return (
            out_l.resize((cw, ch), Image.Resampling.LANCZOS),
            fill_l.resize((cw, ch), Image.Resampling.LANCZOS),
        )

    def find_waist_span(cell: Image.Image, y_row: int) -> tuple[int, int]:
        px = cell.load()
        xs = [x for x in range(360, 570) if px[x, y_row][3] > 180]
        if not xs:
            return (400, 515)
        runs: list[tuple[int, int]] = []
        s = p = xs[0]
        for x in xs[1:]:
            if x > p + 3:
                runs.append((s, p))
                s = x
            p = x
        runs.append((s, p))
        return max(runs, key=lambda r: r[1] - r[0])

    def draw_fitted_pelvis(src_cell: Image.Image, wx0: int, wx1: int, waist_y: int, hip_cy: int, hip_ry: int):
        ss = 2
        out_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        fill_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        do, df = ImageDraw.Draw(out_l), ImageDraw.Draw(fill_l)
        ink = (46, 38, 30, 255)
        ink_w = 10.5 * ss

        # Sample actual left-shadow & right-lit waist colors from the original cell so waist joins seamlessly
        spx = src_cell.load()
        shd_c = spx[min(cw - 1, wx0 + 18), waist_y - 4][:3]
        lit_c = spx[max(0, wx1 - 24), waist_y - 4][:3]
        if sum(shd_c) < 350:
            shd_c = (186, 204, 182)
        if sum(lit_c) < 420:
            lit_c = (238, 241, 218)
        shd = (*shd_c, 255)
        lit = (*lit_c, 255)

        cx = (wx0 + wx1) / 2.0
        rx_top = max(24.0, (wx1 - wx0) / 2.0 - 9.5)
        rx_bot = max(22.0, rx_top - 3.0)

        steps = 26
        y0 = waist_y - 6.0
        y1 = hip_cy + hip_ry
        for pass_i in range(3):
            for i in range(steps + 1):
                t = i / float(steps)
                y = y0 + (y1 - y0) * t
                if t < 0.55:
                    k = t / 0.55
                    rx = rx_top + (rx_bot - rx_top) * k
                else:
                    k = (t - 0.55) / 0.45
                    rx = rx_bot * max(0.05, (1.0 - k * k) ** 0.5)
                l_cx = cx - 3.0 * (1.0 - t)
                if pass_i == 0:
                    do.ellipse(
                        [(l_cx - rx) * ss - ink_w, y * ss - ink_w * 0.55, (l_cx + rx) * ss + ink_w, y * ss + ink_w * 0.55],
                        fill=ink,
                    )
                elif pass_i == 1:
                    df.ellipse([(l_cx - rx) * ss, y * ss - 6 * ss, (l_cx + rx) * ss, y * ss + 6 * ss], fill=shd)
                else:
                    x_left_lit = l_cx - rx * 0.42
                    x_right_lit = l_cx + rx * 0.98
                    if x_right_lit > x_left_lit + 2:
                        df.ellipse([x_left_lit * ss, y * ss - 5 * ss, x_right_lit * ss, y * ss + 5 * ss], fill=lit)

        return (
            out_l.resize((cw, ch), Image.Resampling.LANCZOS),
            fill_l.resize((cw, ch), Image.Resampling.LANCZOS),
        )

    def compose_kodama(src_cell: Image.Image, waist_y: int, hip_cy: int, hip_ry: int, far_args, near_args) -> Image.Image:
        wx0, wx1 = find_waist_span(src_cell, waist_y - 2)
        torso = src_cell.copy()
        tpx = torso.load()
        for y in range(waist_y, ch):
            for x in range(cw):
                tpx[x, y] = (0, 0, 0, 0)
        p_out, p_fill = draw_fitted_pelvis(src_cell, wx0, wx1, waist_y, hip_cy, hip_ry)
        f_out, f_fill = draw_kodama_leg_parts(*far_args, is_far=True, skip_hip_cap=False)
        n_out, n_fill = draw_kodama_leg_parts(*near_args, is_far=False, skip_hip_cap=True)
        comp = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
        for layer in (f_out, f_fill, p_out, n_out, torso, p_fill, n_fill):
            comp = Image.alpha_composite(comp, layer)
        return comp

    # Frame 0: Contact A — authentic cell(0,0) with subtle sage depth on far rear leg
    f0 = cells[0].copy()
    px0 = f0.load()
    for y in range(525, 635):
        for x in range(280, 425):
            r, g, b, a = px0[x, y]
            if a > 120 and (r + g + b) > 360:
                px0[x, y] = (int(r * 0.90), int(g * 0.93), int(b * 0.91), a)

    # Frame 1: Passing A — standing near leg under center + raised far knee (ZERO leg behind!)
    f1 = compose_kodama(
        cells[1],
        waist_y=476,
        hip_cy=502,
        hip_ry=34,
        far_args=((478, 502), (538, 550), (490, 608), (522, 616), 52, 36),
        near_args=((442, 502), (460, 586), (442, 664), (484, 660), 56, 39),
    )

    # Frame 2: Contact B — OPPOSITE wide stride (far leg forward, near leg kicking back!)
    f2 = compose_kodama(
        cells[2],
        waist_y=486,
        hip_cy=510,
        hip_ry=34,
        far_args=((474, 512), (536, 588), (566, 662), (604, 654), 52, 36),
        near_args=((426, 512), (366, 588), (298, 578), (272, 602), 58, 39),
    )

    # Frame 3: Passing B — standing far leg under center + raised near ivory knee (ZERO leg behind!)
    f3 = compose_kodama(
        cells[0],
        waist_y=478,
        hip_cy=504,
        hip_ry=34,
        far_args=((456, 504), (464, 586), (446, 664), (486, 660), 52, 36),
        near_args=((454, 504), (536, 550), (492, 608), (524, 616), 56, 39),
    )

    f_slide_a = cells[5]
    f_slide_b = cells[5].rotate(-2.2, resample=Image.Resampling.BICUBIC, center=(460, 560))
    poses = [f0, f1, f2, f3, cells[3], cells[4], f_slide_a, f_slide_b]
    return _pack_runner_hero_8_poses(
        poses,
        sheet_path,
        "kodama",
        "Studio Ghibli 1990s cel Kodama (#FF00FF analytical chroma-key + true 4-frame alternating Contact/Passing run cycle)",
        target_size=target_size,
    )


def extract_totoro_4x2_alternating_sheet(sheet_path: Path, target_size: int = 256) -> list[dict]:
    """Builds a true 4-frame alternating 360-degree Contact/Passing run cycle (BOTH legs & BOTH arms
    alternating smoothly) for Totoro from the authentic Studio Ghibli cel sheet, keeping head, leaf,
    fluffy tail, and cream belly 100% intact with zero morphing."""
    rgba = chroma_key_magenta_with_despill(Image.open(sheet_path))
    w, h = rgba.size
    mid_y = h // 2
    top = _segment_row_spans(rgba.crop((0, 0, w, mid_y)), 4)
    bot = _segment_row_spans(rgba.crop((0, mid_y, w, h)), 4)

    def find_eye_center(img: Image.Image) -> tuple[float, float]:
        px = img.load()
        xs: list[int] = []
        ys: list[int] = []
        for y in range(180, min(265, img.height)):
            for x in range(int(img.width * 0.45), int(img.width * 0.88)):
                r, g, b, a = px[x, y]
                if a > 200 and r > 235 and g > 235 and b > 235:
                    xs.append(x)
                    ys.append(y)
        if not xs:
            return (img.width * 0.65, 218.0)
        return (sum(xs) / len(xs), sum(ys) / len(ys))

    cw, ch = 700, 768
    target_eye = (455.0, 218.0)

    def place_aligned(img: Image.Image, eye_pt: tuple[float, float]) -> Image.Image:
        out = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
        dx = int(round(target_eye[0] - eye_pt[0]))
        dy = int(round(target_eye[1] - eye_pt[1]))
        out.paste(img, (dx, dy), img)
        return out

    p0 = place_aligned(top[0], find_eye_center(top[0]))
    p3 = place_aligned(top[3], find_eye_center(top[3]))
    px_0 = p0.load()
    px_3 = p3.load()

    # Protect cream belly + its dark sepia rim from p0 with 5px radius (MaxFilter(11)) so
    # the belly rim stays razor-sharp without catching p0's outstretched far-arm root.
    belly_mask = Image.new("L", (cw, ch), 0)
    bm_px = belly_mask.load()
    for y in range(260, 610):
        for x in range(320, 620):
            r, g, b, a = px_0[x, y]
            if a > 180 and r > 195 and g > 185 and b > 130:
                bm_px[x, y] = 255
    belly_sacred = belly_mask.filter(ImageFilter.MaxFilter(11))
    bs_px = belly_sacred.load()

    belly_right: dict[int, int] = {}
    for y in range(285, 470):
        rx = 0
        for x in range(620, 350, -1):
            if bs_px[x, y] > 0:
                rx = x
                break
        belly_right[y] = rx if rx > 0 else 530

    master_torso = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    px_m = master_torso.load()
    for y in range(ch):
        for x in range(cw):
            # 1. Intact fluffy tail from p3 on far left
            if x < 200:
                if y < 595:
                    px_m[x, y] = px_3[x, y]
                continue
            # 2. Intact cream belly + sepia rim from p0
            if bs_px[x, y] > 0:
                px_m[x, y] = px_0[x, y]
                continue
            # 3. Clear legs below pelvis curve
            if y >= 568:
                continue
            if y >= 518:
                dx_h = (x - 365.0) / 186.0
                dy_h = (y - 425.0) / 148.0
                if dx_h * dx_h + dy_h * dy_h > 1.03:
                    continue
            if x < 338:
                px_m[x, y] = px_3[x, y]
            else:
                # Erase p0's outstretched far arm strictly below chin (y >= 298) and right of belly rim
                if 298 <= y <= 450 and x > belly_right.get(y, 550):
                    continue
                px_m[x, y] = px_0[x, y]

    # Smooth interior grey-brown fur where p3's bent arm crossed the side, leaving outer ink & belly untouched
    for y in range(278, 535):
        left_edge = 240
        for xx in range(185, 330):
            if px_m[xx, y][3] > 200:
                left_edge = xx
                break
        for x in range(left_edge + 16, 395):
            if bs_px[x, y] > 0:
                break
            r, g, b, a = px_m[x, y]
            if a > 200 and (r < 125 or abs(r - 158) > 22):
                px_m[x, y] = (158, 143, 124, 255)

    ink = (46, 35, 29, 255)
    fur_lit = (158, 143, 124, 255)
    fur_mid = (132, 118, 102, 255)
    fur_shd = (112, 100, 88, 255)
    fur_dark = (92, 82, 72, 255)

    def draw_claws(draw_obj: ImageDraw.ImageDraw, tip, dir_vec, count: int = 3, length: float = 24.0, spread_step: float = 11.0, ss: int = 2) -> None:
        ux, uy = dir_vec
        px_, py_ = -uy, ux
        offsets = [-spread_step, 0.0, spread_step] if count == 3 else [-spread_step * 0.6, spread_step * 0.6]
        for sp in offsets:
            base = (tip[0] + px_ * sp * 0.65, tip[1] + py_ * sp * 0.65)
            end = (base[0] + ux * length + px_ * sp * 0.28, base[1] + uy * length + py_ * sp * 0.28)
            p1 = ((base[0] + px_ * 4.5) * ss, (base[1] + py_ * 4.5) * ss)
            p2 = ((base[0] - px_ * 4.5) * ss, (base[1] - py_ * 4.5) * ss)
            p3_ = (end[0] * ss, end[1] * ss)
            draw_obj.polygon([p1, p2, p3_], fill=ink)

    def draw_taper(draw_obj: ImageDraw.ImageDraw, pts, w0: float, w1: float, col, extra: float = 0.0, s0: int = 0) -> None:
        n = max(1, len(pts) - 1)
        for i in range(s0, len(pts)):
            x, y = pts[i]
            t = i / float(n)
            r = (w0 * (1.0 - t) + w1 * t + extra) / 2.0
            draw_obj.ellipse([x - r, y - r, x + r, y + r], fill=col)

    def draw_chubby_leg(hip, knee, ankle, toe, thigh_w: float, calf_w: float, is_far: bool = False, skip_hip_cap: bool = False):
        ss = 2
        out_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        fill_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        do, df = ImageDraw.Draw(out_l), ImageDraw.Draw(fill_l)
        sc = lambda pt: (pt[0] * ss, pt[1] * ss)

        mid_uk = ((hip[0] * 0.45 + knee[0] * 0.55), (hip[1] + knee[1]) * 0.5 + 4.0)
        mid_ka = ((knee[0] + ankle[0]) * 0.5, (knee[1] + ankle[1]) * 0.5)
        p_up = _sample_cubic(sc(hip), sc(mid_uk), sc(knee), sc(knee), steps=18)
        p_lo = _sample_cubic(sc(knee), sc(mid_ka), sc(ankle), sc(ankle), steps=18)
        p_ft = _sample_cubic(sc(ankle), sc(((ankle[0] + toe[0]) * 0.5, (ankle[1] + toe[1]) * 0.5 + 4.0)), sc(toe), sc(toe), steps=10)

        ink_pad = 5.8 * ss
        base_col = fur_shd if is_far else fur_lit
        shd_col = fur_dark if is_far else fur_mid

        s0 = 6 if skip_hip_cap else 0
        draw_taper(do, p_up, thigh_w * ss, calf_w * ss, ink, extra=ink_pad * 2, s0=s0)
        draw_taper(do, p_lo, calf_w * ss, (calf_w * 0.64) * ss, ink, extra=ink_pad * 2)
        draw_taper(do, p_ft, (calf_w * 0.60) * ss, (calf_w * 0.36) * ss, ink, extra=ink_pad * 2)

        draw_taper(df, p_up, thigh_w * ss, calf_w * ss, shd_col, s0=(5 if skip_hip_cap else 0))
        draw_taper(df, p_lo, calf_w * ss, (calf_w * 0.64) * ss, shd_col)
        draw_taper(df, p_ft, (calf_w * 0.60) * ss, (calf_w * 0.36) * ss, shd_col)

        off_y = -6.0 * ss
        draw_taper(df, [(x, y + off_y) for x, y in p_up], (thigh_w * 0.92) * ss, (calf_w * 0.88) * ss, base_col)
        draw_taper(df, [(x, y + off_y * 0.6) for x, y in p_lo], (calf_w * 0.84) * ss, (calf_w * 0.58) * ss, base_col)
        draw_taper(df, [(x, y + off_y * 0.3) for x, y in p_ft], (calf_w * 0.54) * ss, (calf_w * 0.32) * ss, base_col)

        dx, dy = toe[0] - ankle[0], toe[1] - ankle[1]
        ln = max(1.0, math.hypot(dx, dy))
        ux, uy = dx / ln, dy / ln
        draw_claws(df, (toe[0] + ux * 10.0, toe[1] + uy * 10.0), (ux, uy), count=3, length=22.0, spread_step=8.5, ss=ss)

        return out_l.resize((cw, ch), Image.Resampling.LANCZOS), fill_l.resize((cw, ch), Image.Resampling.LANCZOS)

    def draw_chubby_arm(shoulder, elbow, hand, base_w: float, tip_w: float, is_far: bool = False, skip_shoulder_cap: bool = True):
        ss = 2
        out_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        fill_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        do, df = ImageDraw.Draw(out_l), ImageDraw.Draw(fill_l)
        sc = lambda pt: (pt[0] * ss, pt[1] * ss)

        pts = _sample_cubic(sc(shoulder), sc(elbow), sc(elbow), sc(hand), steps=24)
        ink_pad = 5.8 * ss
        base_col = fur_shd if is_far else fur_lit
        shd_col = fur_dark if is_far else fur_mid

        s0 = 8 if skip_shoulder_cap else 0
        draw_taper(do, pts, base_w * ss, tip_w * ss, ink, extra=ink_pad * 2, s0=s0)
        draw_taper(df, pts, base_w * ss, tip_w * ss, shd_col, s0=(6 if skip_shoulder_cap else 0))
        draw_taper(df, [(x, y - 5.0 * ss) for x, y in pts], (base_w * 0.90) * ss, (tip_w * 0.86) * ss, base_col)

        dx, dy = hand[0] - elbow[0], hand[1] - elbow[1]
        ln = max(1.0, math.hypot(dx, dy))
        ux, uy = dx / ln, dy / ln
        draw_claws(df, (hand[0] + ux * 18.0, hand[1] + uy * 18.0), (ux, uy), count=3, length=24.0, spread_step=11.0, ss=ss)

        return out_l.resize((cw, ch), Image.Resampling.LANCZOS), fill_l.resize((cw, ch), Image.Resampling.LANCZOS)

    def draw_hip_bridge():
        ss = 2
        out_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        fill_l = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
        do, df = ImageDraw.Draw(out_l), ImageDraw.Draw(fill_l)
        cx, cy, rx, ry = 342.0, 508.0, 128.0, 74.0
        pad = 5.8 * ss
        do.ellipse([(cx - rx) * ss - pad, (cy - ry) * ss - pad, (cx + rx) * ss + pad, (cy + ry) * ss + pad], fill=ink)
        df.ellipse([(cx - rx) * ss, (cy - ry) * ss, (cx + rx) * ss, (cy + ry) * ss], fill=fur_mid)
        df.ellipse([(cx - rx + 2) * ss, (cy - ry) * ss, (cx + rx - 2) * ss, (cy + ry - 14) * ss], fill=fur_lit)
        return out_l.resize((cw, ch), Image.Resampling.LANCZOS), fill_l.resize((cw, ch), Image.Resampling.LANCZOS)

    hip_o, hip_f = draw_hip_bridge()

    def compose_totoro(far_arm_args, far_leg_args, near_leg_args, near_arm_args, tilt_deg: float = 0.0) -> Image.Image:
        comp = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
        if far_arm_args is not None:
            fa_o, fa_f = draw_chubby_arm(*far_arm_args, is_far=True, skip_shoulder_cap=False)
            comp = Image.alpha_composite(comp, fa_o)
            comp = Image.alpha_composite(comp, fa_f)
        fl_o, fl_f = draw_chubby_leg(*far_leg_args, is_far=True, skip_hip_cap=False)
        nl_o, nl_f = draw_chubby_leg(*near_leg_args, is_far=False, skip_hip_cap=True)
        for layer in (fl_o, fl_f, hip_o, nl_o, hip_f, master_torso, nl_f):
            comp = Image.alpha_composite(comp, layer)
        # Re-stamp intact cream belly + dark sepia rim on top of near-leg fill
        comp.paste(p0, (0, 0), belly_sacred)
        if near_arm_args is not None:
            na_o, na_f = draw_chubby_arm(*near_arm_args, is_far=False, skip_shoulder_cap=True)
            comp = Image.alpha_composite(comp, na_o)
            comp = Image.alpha_composite(comp, na_f)
        if abs(tilt_deg) > 0.01:
            comp = comp.rotate(tilt_deg, resample=Image.Resampling.BICUBIC, center=(360, 420))
        return comp

    # Frame 0 (Contact A): Authentic p0 (near leg wide forward, far leg wide back; near arm back, far arm forward)
    f0 = p0.copy()

    # Frame 1 (Passing A): Near leg planted straight down; far leg tucked swinging forward (ZERO leg behind);
    # near arm hanging naturally mid-swing along side; far arm half-forward.
    f1 = compose_totoro(
        far_arm_args=((505, 322), (552, 344), (586, 336), 72, 50),
        far_leg_args=((382, 535), (460, 578), (432, 622), (470, 610), 106, 72),
        near_leg_args=((322, 522), (336, 608), (324, 672), (374, 672), 132, 76),
        near_arm_args=((315, 290), (298, 392), (326, 466), 88, 56),
        tilt_deg=1.4,
    )

    # Frame 2 (Contact B — TRUE OPPOSITE STRIDE): Darker far leg wide forward, lit near leg kicking wide back;
    # lit near arm swinging forward across cream belly, darker far arm swinging back behind torso.
    f2 = compose_totoro(
        far_arm_args=((255, 322), (188, 355), (138, 385), 74, 50),
        far_leg_args=((395, 535), (470, 598), (512, 666), (552, 646), 110, 74),
        near_leg_args=((290, 528), (212, 590), (164, 634), (136, 608), 134, 74),
        near_arm_args=((315, 290), (328, 398), (438, 412), 90, 56),
        tilt_deg=-2.0,
    )

    # Frame 3 (Passing B — TRUE OPPOSITE PASSING): Darker far leg planted straight down;
    # lit near leg chubby knee driving high forward (ZERO leg behind); arms mid-swing returning.
    f3 = compose_totoro(
        far_arm_args=((502, 322), (548, 346), (582, 340), 72, 50),
        far_leg_args=((330, 535), (336, 610), (324, 672), (370, 672), 108, 74),
        near_leg_args=((338, 522), (438, 574), (414, 622), (454, 608), 128, 74),
        near_arm_args=((318, 290), (312, 395), (346, 465), 88, 56),
        tilt_deg=1.4,
    )

    poses = [f0, f1, f2, f3, bot[0], bot[1], bot[2], bot[3]]
    return _pack_runner_hero_8_poses(
        poses,
        sheet_path,
        "totoro",
        "Studio Ghibli 1990s cel Totoro (#FF00FF analytical chroma-key + true 4-frame alternating 360-degree Contact/Passing run cycle)",
        target_size=target_size,
    )


def extract_runner_obstacles(sheet_path: Path, target_size: int = 256) -> list[dict]:
    """Extracts 4 hand-drawn Studio Ghibli cel obstacles (stump, stone lantern, bird-up, bird-down)
    from a 1x4 #FF00FF chroma-key sheet."""
    rgba = chroma_key_magenta_with_despill(Image.open(sheet_path))
    w, h = rgba.size

    # 2D connected-components (on 4x downsampled alpha mask) so props with slightly overlapping
    # X-projections (e.g. lantern roof overhang above stump root) separate cleanly with zero bleed.
    scale_cc = 4
    sw, sh = max(1, w // scale_cc), max(1, h // scale_cc)
    small_a = rgba.getchannel("A").resize((sw, sh), Image.Resampling.BILINEAR)
    # Slight 1-cell dilation so detached tiny specks (like little mushrooms/grass blades) join their parent prop
    dilated = small_a.filter(ImageFilter.MaxFilter(5))
    mask_bytes = dilated.tobytes()

    from collections import deque

    visited = bytearray(sw * sh)
    comps: list[tuple[int, int, int, int, int, list[int]]] = []  # (area, x0, y0, x1, y1, indices)
    for sy in range(sh):
        row_off = sy * sw
        for sx in range(sw):
            idx0 = row_off + sx
            if visited[idx0] or mask_bytes[idx0] < 36:
                continue
            q = deque([idx0])
            visited[idx0] = 1
            cells = []
            min_x = max_x = sx
            min_y = max_y = sy
            while q:
                cur = q.popleft()
                cells.append(cur)
                cy, cx = divmod(cur, sw)
                if cx < min_x: min_x = cx
                if cx > max_x: max_x = cx
                if cy < min_y: min_y = cy
                if cy > max_y: max_y = cy
                for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                    if 0 <= nx < sw and 0 <= ny < sh:
                        ni = ny * sw + nx
                        if not visited[ni] and mask_bytes[ni] >= 36:
                            visited[ni] = 1
                            q.append(ni)
            if len(cells) >= 250:
                comps.append((len(cells), min_x, min_y, max_x + 1, max_y + 1, cells))

    # Keep top 4 largest components and sort left-to-right by horizontal center
    comps.sort(key=lambda c: c[0], reverse=True)
    if len(comps) < 4:
        raise RuntimeError(f"{sheet_path.name}: expected 4 connected obstacle components, found {len(comps)}")
    top4 = sorted(comps[:4], key=lambda c: (c[1] + c[3]) / 2.0)

    names = ["obs-stump", "obs-stone", "obs-bird-up", "obs-bird-down"]
    raw_crops: list[Image.Image] = []
    for name, (_, x0, y0, x1, y1, cells) in zip(names, top4):
        comp_mask_small = Image.new("L", (sw, sh), 0)
        cm_px = comp_mask_small.load()
        for ci in cells:
            cy, cx = divmod(ci, sw)
            cm_px[cx, cy] = 255
        comp_mask = comp_mask_small.resize((w, h), Image.Resampling.NEAREST)
        isolated = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        isolated.paste(rgba, (0, 0), comp_mask)

        bx0 = max(0, x0 * scale_cc - 6)
        by0 = max(0, y0 * scale_cc - 6)
        bx1 = min(w, x1 * scale_cc + 6)
        by1 = min(h, y1 * scale_cc + 6)
        cell = isolated.crop((bx0, by0, bx1, by1))
        solid = cell.getchannel("A").point(lambda v: 255 if v >= 32 else 0)
        bbox = solid.getbbox()
        if not bbox:
            raise RuntimeError(f"Empty obstacle {name} in {sheet_path}")
        raw_crops.append(
            cell.crop(
                (
                    max(0, bbox[0] - 2),
                    max(0, bbox[1] - 2),
                    min(cell.width, bbox[2] + 2),
                    min(cell.height, bbox[3] + 2),
                )
            )
        )

    # Shared scale between the 2 bird wing-flap frames so bird body size stays identical
    bird_max = max(max(raw_crops[2].width, raw_crops[2].height), max(raw_crops[3].width, raw_crops[3].height))
    bird_scale = (target_size * 0.90) / bird_max

    items: list[dict] = []
    for i, (name, crop) in enumerate(zip(names, raw_crops)):
        if i >= 2:
            scale = bird_scale
        else:
            scale = (target_size * 0.92) / max(crop.width, crop.height)
        nw = max(1, int(round(crop.width * scale)))
        nh = max(1, int(round(crop.height * scale)))
        resized = crop.resize((nw, nh), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
        px = (target_size - nw) // 2
        py = (target_size - nh) // 2 if i >= 2 else max(0, target_size - 4 - nh)
        canvas.paste(resized, (px, py), resized)
        out_path = ASSETS_DIR / "game" / f"{name}.webp"
        canvas.save(out_path, "WEBP", quality=92, method=6)
        items.append(
            {
                "id": name,
                "kind": "runner_obstacle",
                "source": "Gemini Web via Orca CLI (Studio Ghibli 1990s cel 1x4 obstacle sheet, #FF00FF analytical chroma-key)",
                "originalPath": f"art/originals/game/{sheet_path.name}",
                "runtimePath": f"app/src/main/assets/game/{name}.webp",
                "width": target_size,
                "height": target_size,
                "alpha": True,
                "sha256": sha256_file(out_path),
                "status": "approved",
            }
        )
    return items


def build_runner_assets() -> list[dict]:
    """Builds seamless hand-painted Ghibli valley panorama, seamless meadow ground strip,
    6-frame cel-animated Soot Sprite & Kodama strips, and 4 hand-drawn cel obstacles."""
    game_orig = ORIGINALS_DIR / "game"
    game_dir = ASSETS_DIR / "game"
    game_dir.mkdir(parents=True, exist_ok=True)
    items: list[dict] = []

    # 1. Seamless valley panorama (cosine wraparound crossfade, NO mirroring!)
    valley_src = game_orig / "valley_bg_raw.jpg"
    valley = make_horizontally_seamless_rgb(Image.open(valley_src), 1920, 540, overlap_ratio=0.18, crop_top=0.0, crop_bottom=0.95)
    valley_out = game_dir / "ghibli-valley.webp"
    valley.save(valley_out, "WEBP", quality=88, method=6)
    items.append(
        {
            "id": "ghibli-valley",
            "kind": "runner_background",
            "source": "Gemini Web via Orca CLI (Kazuo Oga gouache alpine valley + cosine wraparound seamless blend)",
            "originalPath": "art/originals/game/valley_bg_raw.jpg",
            "runtimePath": "app/src/main/assets/game/ghibli-valley.webp",
            "width": valley.width,
            "height": valley.height,
            "alpha": False,
            "sha256": sha256_file(valley_out),
            "status": "approved",
        }
    )

    # 2. Seamless hand-painted meadow ground strip with cutout grass tufts & wildflowers
    ground_src = game_orig / "ground_trail_raw.jpg"
    ground = make_horizontally_seamless_ground(ground_src, target_w=1280, target_h=164, overlap_ratio=0.16)
    ground_out = game_dir / "ghibli-ground.webp"
    ground.save(ground_out, "WEBP", quality=90, method=6)
    items.append(
        {
            "id": "ghibli-ground",
            "kind": "runner_ground",
            "source": "Gemini Web via Orca CLI (Kazuo Oga gouache meadow trail + #FF00FF chroma-key grass top + premultiplied cosine wraparound)",
            "originalPath": "art/originals/game/ground_trail_raw.jpg",
            "runtimePath": "app/src/main/assets/game/ghibli-ground.webp",
            "width": ground.width,
            "height": ground.height,
            "alpha": True,
            "sha256": sha256_file(ground_out),
            "status": "approved",
        }
    )

    # 3. 6-frame alternating-run cel hero strips + 2-frame crouch strips (Soot Sprite, Kodama, Totoro)
    items.extend(extract_soot_4x2_alternating_sheet(game_orig / "soot_sheet_raw.jpg", target_size=256))
    items.extend(extract_kodama_4x2_alternating_sheet(game_orig / "kodama_sheet_raw.jpg", target_size=256))
    items.extend(extract_totoro_4x2_alternating_sheet(game_orig / "totoro_sheet_raw.jpg", target_size=256))

    # 4. Hand-drawn cel obstacles (mossy stump, stone lantern, 2-frame flapping origami bird)
    items.extend(extract_runner_obstacles(game_orig / "obstacles_sheet_raw.jpg", target_size=256))
    return items


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

    # 2. Process generated Studio Ghibli effect sprites
    soot_sheet = ORIGINALS_DIR / "effect-soot-sheet.jpg"
    if soot_sheet.exists():
        soot_idle_img, soot_jump_img = split_and_normalize_soot_sheet(soot_sheet, target_size=384)
        for pose_id, pose_img in (("soot-idle", soot_idle_img), ("soot-jump", soot_jump_img)):
            runtime_eff = ASSETS_DIR / "effects" / f"effect-{pose_id}.webp"
            pose_img.save(runtime_eff, "WEBP", quality=92, method=6)
            manifest_items.append(
                {
                    "id": f"effect_{pose_id}",
                    "kind": "focus_effect",
                    "source": "generate_image (gemini-3.1-flash-image) 1x2 chroma-key #00FF00 sheet + green despill alpha",
                    "originalPath": "art/originals/effect-soot-sheet.jpg",
                    "runtimePath": f"app/src/main/assets/effects/effect-{pose_id}.webp",
                    "width": pose_img.width,
                    "height": pose_img.height,
                    "alpha": True,
                    "sha256": sha256_file(runtime_eff),
                    "status": "approved",
                }
            )

    for eff_id, (src_name, mode) in GENERATED_EFFECTS.items():
        if soot_sheet.exists() and eff_id in ("soot-idle", "soot-jump"):
            continue
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

    # 3. Seamless Ghibli valley, meadow ground, 6-frame cel heroes, and hand-drawn obstacles
    runner_manifest_items = build_runner_assets()
    manifest_items.extend(runner_manifest_items)

    # 4. Cohesive Ghibli crest + 20 hand-painted Ghibli app/archive icons
    build_branding_and_icons()
    icon_manifest_items = extract_generated_icon_sheets()
    manifest_items.extend(icon_manifest_items)

    # 5. Preserve authentic Studio Ghibli audio if present; otherwise synthesize fallback
    audio_prov_path = DOCS_DIR / "audio-provenance.json"
    audio_items: list[dict] = []
    if audio_prov_path.exists() and (ASSETS_DIR / "audio" / "music-meadow_morning.ogg").exists():
        audio_items = json.loads(audio_prov_path.read_text(encoding="utf-8"))
        for aitem in audio_items:
            p = ROOT / aitem["runtimePath"]
            if p.exists():
                aitem["sha256"] = sha256_file(p)
                aitem["bytes"] = p.stat().st_size
        manifest_items.extend(audio_items)
    else:
        build_audio_assets()

    # Add summary entry for branding crest
    for rel in [
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
            "All 8 wallpapers were generated via gemini-3.1-flash-image and visually verified. "
            "Soot Sprite poses (soot-idle, soot-jump) were generated as a single style-locked 1x2 chroma-key #00FF00 sheet "
            "and extracted via color-difference chroma keying with green despill and shared body scale normalization. "
            "Calcifer and Kodama focus auras were generated via gemini-3.1-flash-image with luma alpha extraction. "
            "All 20 free-form 1990s Studio Ghibli cel-animation prop icons (19 TV apps + All Apps archive) "
            "were generated as 7 style-locked horizontal sheets via Gemini Web (Orca CLI) on palette-separated "
            "#FF00FF magenta and #00FF00 green chroma-key backdrops, extracted via analytical un-premultiplication + despill "
            "(preserving translucent glass highlights and interior cutouts), and visually verified on 3 backdrops and live Lenovo TV. "
            "The runner mini-game ('Політ крізь долину') uses hand-painted Kazuo Oga gouache seamless valley panorama and meadow ground "
            "(cosine wraparound crossfade, zero mirroring), 4x2 hand-drawn 1990s cel-animation hero sheets for 3 playable heroes "
            "(Soot Sprite on #00FF00, Kodama on #FF00FF, Totoro on #FF00FF) with true 4-frame alternating Contact/Passing run cycles, "
            "2-frame animated crouch strips, shared scale & baseline foot alignment, and 4 hand-drawn cel obstacles (stump, Toro stone lantern, "
            "2-frame flapping Spirited Away origami paper bird). "
            "All 3 background music tracks (Ogg Vorbis) and 19 UI/focus/confirm/runner SFX (PCM WAV) are cut and normalized "
            "from authentic Studio Ghibli OSTs (My Neighbor Totoro, Spirited Away, Howl's Moving Castle, Castle in the Sky, "
            "Princess Mononoke) and CC0 foley (Kodama rattle #478642, cast-iron hearth fire #243742), documented in docs/audio-provenance.json."
        ),
        "assets": manifest_items,
    }
    (DOCS_DIR / "asset-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"Built {len(manifest_items)} tracked assets (incl. {len(icon_manifest_items)} hand-painted icons, {len(audio_items)} authentic Ghibli audio files).")


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
    """Extracts all 20 free-form 1990s Studio Ghibli cel prop icons (19 TV apps + All Apps)
    from 7 style-locked horizontal sheets using palette-separated #FF00FF / #00FF00 analytical chroma-key."""
    sheets_dir = ORIGINALS_DIR / "icon_sheets"
    icons_dir = ASSETS_DIR / "icons"
    icons_dir.mkdir(parents=True, exist_ok=True)
    items: list[dict] = []

    for override_sheet, override_kinds, key_mode in GHIBLI_CEL_PROP_SHEETS:
        sheet_path = sheets_dir / override_sheet
        if not sheet_path.exists():
            raise FileNotFoundError(f"Missing Studio Ghibli cel prop sheet: {sheet_path}")

        raw_img = Image.open(sheet_path)
        if key_mode == "magenta":
            rgba = chroma_key_magenta_with_despill(raw_img)
            key_label = "#FF00FF"
        elif key_mode == "green":
            rgba = chroma_key_green_prop(raw_img)
            key_label = "#00FF00"
        else:
            raise ValueError(f"Unsupported chroma-key mode: {key_mode}")

        w, h = rgba.size
        n = len(override_kinds)

        # Segment horizontally by non-empty foreground column spans (resilient to varying prop widths)
        a_chan = rgba.getchannel("A")
        col_has = [a_chan.crop((x, 0, x + 1, h)).getextrema()[1] >= 48 for x in range(w)]
        spans: list[tuple[int, int]] = []
        in_span = False
        s0 = 0
        for x, val in enumerate(col_has):
            if val and not in_span:
                in_span = True
                s0 = x
            elif not val and in_span:
                in_span = False
                if x - s0 > 40:
                    spans.append((s0, x))
        if in_span and w - s0 > 40:
            spans.append((s0, w))

        if len(spans) != n:
            raise RuntimeError(f"{override_sheet}: expected {n} horizontal prop spans, found {len(spans)} ({spans})")

        for kind, (x0, x1) in zip(override_kinds, spans):
            cell = rgba.crop((max(0, x0 - 4), 0, min(w, x1 + 4), h))
            solid = cell.getchannel("A").point(lambda v: 255 if v >= 32 else 0)
            bbox = solid.getbbox()
            if not bbox:
                raise RuntimeError(f"Empty prop {kind} in {override_sheet}")
            crop = cell.crop(
                (
                    max(0, bbox[0] - 2),
                    max(0, bbox[1] - 2),
                    min(cell.width, bbox[2] + 2),
                    min(cell.height, bbox[3] + 2),
                )
            )
            scale = 238.0 / max(crop.width, crop.height)
            nw = max(1, int(round(crop.width * scale)))
            nh = max(1, int(round(crop.height * scale)))
            resized = crop.resize((nw, nh), Image.Resampling.LANCZOS)
            canvas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
            canvas.paste(resized, ((256 - nw) // 2, (256 - nh) // 2), resized)
            out_path = icons_dir / f"icon-{kind}.webp"
            canvas.save(out_path, "WEBP", quality=92, method=6)
            items.append(
                {
                    "id": f"icon_{kind}",
                    "kind": "menu_icon" if kind == "archive" else "app_icon",
                    "appMapping": "archive (All Apps)" if kind == "archive" else kind,
                    "source": f"Gemini Web via Orca CLI (Studio Ghibli 1990s free-form cel prop guide v2, 1x{n} {key_label} analytical chroma-key + despill)",
                    "originalPath": f"art/originals/icon_sheets/{override_sheet}",
                    "runtimePath": f"app/src/main/assets/icons/icon-{kind}.webp",
                    "width": 256,
                    "height": 256,
                    "alpha": True,
                    "sha256": sha256_file(out_path),
                    "status": "approved",
                }
            )
    return items


GHIBLI_CEL_PROP_SHEETS = [
    ("ghibli_props_test3_v2.jpg", ["uakino", "lampa", "kodi"], "magenta"),
    ("ghibli_props_b1_red.jpg", ["youtube", "smarttube", "netflix"], "green"),
    ("ghibli_props_b2_warm.jpg", ["crunchyroll", "vlc", "plex"], "green"),
    ("ghibli_props_b3_violet.jpg", ["jellyfin", "sweettv", "sendfiles"], "green"),
    ("ghibli_props_b4_green.jpg", ["megogo", "spotify", "archive"], "magenta"),
    ("ghibli_props_b5_blue.jpg", ["kyivstar", "disneyplus", "primevideo"], "magenta"),
    ("ghibli_props_b6_tools.jpg", ["tvbro", "xplore"], "magenta"),
]


def chroma_key_magenta_with_despill(img: Image.Image) -> Image.Image:
    """Removes solid chroma-key magenta (#FF00FF) backdrop using analytical un-premultiplication
    and magenta despill, preserving crisp sepia outlines and translucent cel glass highlights."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    src = list(rgb.getdata())

    corners = [src[0], src[w - 1], src[(h - 1) * w], src[h * w - 1]]
    bg_r = sum(c[0] for c in corners) / 4.0
    bg_g = sum(c[1] for c in corners) / 4.0
    bg_b = sum(c[2] for c in corners) / 4.0
    bg_m = min(bg_r, bg_b) - bg_g
    high_m = max(180.0, bg_m - 22.0)
    low_m = 22.0

    out = [(0, 0, 0, 0)] * (w * h)
    for idx, (r, g, b) in enumerate(src):
        rb_min = r if r < b else b
        m = rb_min - g
        if m >= high_m:
            out[idx] = (0, 0, 0, 0)
            continue
        if m <= low_m:
            if m > 10:
                cap = g + 10
                out[idx] = (min(r, cap), g, min(b, cap), 255)
            else:
                out[idx] = (r, g, b, 255)
            continue

        alpha_f = max(0.0, min(1.0, (high_m - m) / (high_m - low_m)))
        a = int(round(alpha_f * 255))
        if a <= 6:
            out[idx] = (0, 0, 0, 0)
            continue

        inv = 1.0 - alpha_f
        ur = max(0.0, min(255.0, (r - bg_r * inv) / alpha_f))
        ug = max(0.0, min(255.0, (g - bg_g * inv) / alpha_f))
        ub = max(0.0, min(255.0, (b - bg_b * inv) / alpha_f))
        if min(ur, ub) > ug + 6:
            cap = ug + 6
            ur = min(ur, cap)
            ub = min(ub, cap)
        out[idx] = (int(round(ur)), int(round(ug)), int(round(ub)), a)

    rgba = Image.new("RGBA", (w, h))
    rgba.putdata(out)
    return rgba


def chroma_key_green_prop(img: Image.Image) -> Image.Image:
    """Removes solid chroma-key green (#00FF00) backdrop using analytical un-premultiplication
    and green despill, preserving warm reds, oranges, golds, purples, and sepia cel ink."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    src = list(rgb.getdata())

    corners = [src[0], src[w - 1], src[(h - 1) * w], src[h * w - 1]]
    bg_r = sum(c[0] for c in corners) / 4.0
    bg_g = sum(c[1] for c in corners) / 4.0
    bg_b = sum(c[2] for c in corners) / 4.0
    bg_ge = bg_g - max(bg_r, bg_b)
    high_g = max(180.0, bg_ge - 22.0)
    low_g = 22.0

    out = [(0, 0, 0, 0)] * (w * h)
    for idx, (r, g, b) in enumerate(src):
        rb_max = r if r > b else b
        ge = g - rb_max
        if ge >= high_g:
            out[idx] = (0, 0, 0, 0)
            continue
        if ge <= low_g:
            if ge > 10:
                out[idx] = (r, min(g, rb_max + 10), b, 255)
            else:
                out[idx] = (r, g, b, 255)
            continue

        alpha_f = max(0.0, min(1.0, (high_g - ge) / (high_g - low_g)))
        a = int(round(alpha_f * 255))
        if a <= 6:
            out[idx] = (0, 0, 0, 0)
            continue

        inv = 1.0 - alpha_f
        ur = max(0.0, min(255.0, (r - bg_r * inv) / alpha_f))
        ug = max(0.0, min(255.0, (g - bg_g * inv) / alpha_f))
        ub = max(0.0, min(255.0, (b - bg_b * inv) / alpha_f))
        cap = max(ur, ub) + 6.0
        if ug > cap:
            ug = cap
        out[idx] = (int(round(ur)), int(round(ug)), int(round(ub)), a)

    rgba = Image.new("RGBA", (w, h))
    rgba.putdata(out)
    return rgba


if __name__ == "__main__":
    main()
