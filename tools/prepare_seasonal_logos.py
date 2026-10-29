#!/usr/bin/env python3
"""Extract the supplied SoftMusic wordmarks; no drawing or generative processing.

Requires Python 3, Pillow and numpy. Sources stay byte-for-byte unchanged.
Run from any directory: python tools/prepare_seasonal_logos.py
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, __version__ as pillow_version


ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = "SoftMusic_9_Logotipos_Tematicos.zip"
OFFICIAL = "Logotipo oficial de SoftMusic.png"
MEMBERS = {
    "halloween": "01_Halloween.png",
    "christmas": "02_Navidad.png",
    "day_of_the_dead": "03_Dia_de_Muertos.png",
    "new_year": "04_Ano_Nuevo.png",
    "valentines": "05_San_Valentin.png",
    "independence": "06_Independencia_de_Mexico.png",
    "easter": "07_Pascua.png",
    "music_day": "08_Dia_de_la_Musica.png",
    "anniversary": "09_Aniversario.png",
}
ORDER = ["original", *MEMBERS]
PARAMETERS = {
    "canvas_pixels": [840, 280],
    "source_pixels": [2172, 724],
    "foreground_seed_max_channel_above": 32,
    "icon_region": [0, 0, 640, 724],
    "icon_close_kernel": 5,
    "opaque_erosion_kernel": 3,
    "edge_neighborhood_kernel": 5,
    "edge_peak_kernel": 7,
    "background_sample": [0, 0, 2172, 120],
    "background_median_adjustment": [-1, -1, -1],
    "background_noise_cutoff_above_max": 2,
    "registration_white_threshold": 140,
    "registration_white_region": [850, 0, 1200, 724],
    "registration_icon_left": 237,
    "registration_text_bottom": 464,
    "registration_text_height": 227,
    "common_canvas_side_margin_source_pixels": 26,
    "resampling": "Lanczos",
    "webp": {"lossless": True, "method": 6, "exact": True},
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sources(directory: Path) -> dict[str, bytes]:
    result = {"original": (directory / OFFICIAL).read_bytes()}
    with zipfile.ZipFile(directory / ARCHIVE) as archive:
        for key, member in MEMBERS.items():
            result[key] = archive.read(f"SoftMusic_Logotipos/{member}")
    return result


def extract(image: Image.Image) -> tuple[Image.Image, dict]:
    """Remove the matte, retaining dark details inside the icon silhouette."""
    rgb = np.asarray(image.convert("RGB")).astype(np.float32)
    value = rgb.max(axis=2)
    seed = Image.fromarray(np.uint8(value > 32) * 255)

    # The icon is separate from the wordmark. Close tiny dark slits at its edge,
    # then fill its enclosed S, hat, cobweb, headphones, etc. Letter counters are
    # deliberately outside this region and keep the background's transparency.
    icon = seed.crop((0, 0, 640, image.height))
    icon = icon.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(5))
    inverted = Image.fromarray(255 - np.asarray(icon)).copy()
    ImageDraw.floodfill(inverted, (0, 0), 128)
    icon_filled = (np.asarray(icon) > 0) | (np.asarray(inverted) == 255)
    mask = np.asarray(seed).copy()
    mask[:, :640] = np.maximum(mask[:, :640], icon_filled * 255)
    mask_image = Image.fromarray(mask.astype(np.uint8))
    interior = np.asarray(mask_image.filter(ImageFilter.MinFilter(3))) > 0
    nearby = np.asarray(mask_image.filter(ImageFilter.MaxFilter(5))) > 0
    peak = np.asarray(
        Image.fromarray(value.astype(np.uint8)).filter(ImageFilter.MaxFilter(7))
    ).astype(np.float32)

    # Estimate coverage only at the contour. Interior source RGB stays intact.
    # Unmatting partially covered pixels avoids a dark fringe on light themes.
    background = np.median(rgb[:120].reshape(-1, 3), axis=0) - 1
    alpha = np.clip(
        (value - background.max()) / np.maximum(peak - background.max(), 1), 0, 1
    )
    alpha[(~nearby) | (value < background.max() + 2)] = 0
    alpha[interior] = 1
    edge = (alpha > 0) & (alpha < 1)
    unmatted = rgb.copy()
    unmatted[edge] = (
        rgb[edge] - background[None, :] * (1 - alpha[edge, None])
    ) / alpha[edge, None]
    unmatted[alpha == 0] = 0
    rgba = np.concatenate(
        [
            np.clip(np.rint(unmatted), 0, 255).astype(np.uint8),
            np.rint(alpha[:, :, None] * 255).astype(np.uint8),
        ],
        axis=2,
    )
    extracted = Image.fromarray(rgba)

    # Register to the unadorned white "oft", which no supplied ornament covers.
    # Use an isotropic scale: the supplied shapes and their proportions survive.
    white = (rgb[:, :, 0] > 140) & (rgb[:, :, 1] > 140) & (rgb[:, :, 2] > 140)
    ys, _ = np.where(white[:, 850:1200])
    text_top, text_bottom = int(ys.min()), int(ys.max() + 1)
    scale = 227 / (text_bottom - text_top)
    icon_left = seed.crop((0, 0, 640, image.height)).getbbox()[0]
    translate_x = 237 - scale * icon_left
    translate_y = 464 - scale * text_bottom
    return extracted, {
        "background_rgb": background.tolist(),
        "source_alpha_bounds": list(extracted.getbbox()),
        "source_text_top_bottom": [text_top, text_bottom],
        "source_icon_left": icon_left,
        "registration_scale": scale,
        "registration_translation": [translate_x, translate_y],
    }


def make_previews(images: dict[str, Image.Image], destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    backgrounds = ["#E8E5DF", "#171A20"]
    sheet = Image.new("RGB", (1680, len(ORDER) * 308), "#343840")
    draw = ImageDraw.Draw(sheet)
    for row, key in enumerate(ORDER):
        draw.text((12, row * 308 + 7), key, fill="white")
        for column, color in enumerate(backgrounds):
            composed = Image.new("RGBA", (840, 280), color)
            composed.alpha_composite(images[key])
            sheet.paste(composed.convert("RGB"), (column * 840, row * 308 + 28))
        if key in {"original", "halloween", "day_of_the_dead", "anniversary"}:
            pair = sheet.crop((0, row * 308, 1680, (row + 1) * 308))
            pair.save(destination / f"{key}-light-dark.jpg", quality=95, subsampling=0)
    sheet.save(destination / "all-light-dark.jpg", quality=92, subsampling=0)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", type=Path, default=ROOT / "design/logos/originals")
    parser.add_argument("--output", type=Path, default=ROOT / "app/src/main/res/drawable-nodpi")
    parser.add_argument("--manifest", type=Path, default=ROOT / "design/logos/manifest.json")
    parser.add_argument("--previews", type=Path, default=ROOT / "design/logos/previews")
    args = parser.parse_args()
    inputs = sources(args.sources)
    prepared: dict[str, Image.Image] = {}
    records: dict[str, dict] = {}
    bounds = []
    for key, data in inputs.items():
        image = Image.open(io.BytesIO(data))
        if image.size != (2172, 724):
            raise ValueError(f"Unexpected dimensions for {key}: {image.size}")
        prepared[key], record = extract(image)
        record.update({"source_sha256": digest(data), "source_bytes": len(data)})
        records[key] = record
        scale = record["registration_scale"]
        x, y = record["registration_translation"]
        left, top, right, bottom = record["source_alpha_bounds"]
        bounds.append((left * scale + x, top * scale + y, right * scale + x, bottom * scale + y))

    left = min(b[0] for b in bounds) - 26
    right = max(b[2] for b in bounds) + 26
    center_y = (min(b[1] for b in bounds) + max(b[3] for b in bounds)) / 2
    height = (right - left) / 3
    crop = [left, center_y - height / 2, right, center_y + height / 2]
    args.output.mkdir(parents=True, exist_ok=True)
    outputs = {}
    for key in ORDER:
        record = records[key]
        scale = record["registration_scale"]
        tx, ty = record["registration_translation"]
        source_crop = [(crop[0] - tx) / scale, (crop[1] - ty) / scale,
                       (crop[2] - tx) / scale, (crop[3] - ty) / scale]
        image = prepared[key].resize((840, 280), Image.Resampling.LANCZOS, box=source_crop)
        # Transparent RGB is immaterial, but zeroing it improves compression and
        # gives reproducible, clean output to editors that inspect RGB separately.
        array = np.asarray(image).copy()
        array[array[:, :, 3] == 0, :3] = 0
        image = Image.fromarray(array)
        path = args.output / f"seasonal_logo_{key}.webp"
        image.save(path, format="WEBP", lossless=True, method=6, exact=True)
        decoded = Image.open(path).convert("RGBA")
        if not np.array_equal(np.asarray(decoded), np.asarray(image)):
            raise ValueError(f"Lossless WebP verification failed: {path}")
        alpha = np.asarray(decoded)[:, :, 3]
        if alpha.min() != 0 or alpha.max() != 255 or np.any(alpha[0]) or np.any(alpha[-1]):
            raise ValueError(f"Alpha/margin verification failed: {path}")
        record.update({"source_crop": source_crop, "output_alpha_bounds": list(image.getbbox()),
                       "output_bytes": path.stat().st_size, "output_sha256": digest(path.read_bytes())})
        outputs[key] = decoded
        # Only retire runtime PNG after its archive and lossless replacement are
        # verified. Leave unrelated or modified PNG files alone.
        png = path.with_suffix(".png")
        if png.exists():
            if digest(png.read_bytes()) != record["source_sha256"]:
                raise ValueError(f"Runtime source differs from archive: {png}")
            png.unlink()
        print(f"{key}: {record['source_bytes']:,} -> {record['output_bytes']:,} bytes")

    manifest = {
        "algorithm": "Technical matte extraction; no generated or redrawn artwork",
        "pillow": pillow_version, "numpy": np.__version__,
        "parameters": PARAMETERS, "registered_common_crop": crop,
        "archives": {name: digest((args.sources / name).read_bytes()) for name in [ARCHIVE, OFFICIAL]},
        "logos": records,
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    make_previews(outputs, args.previews)


if __name__ == "__main__":
    main()
