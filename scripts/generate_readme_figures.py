#!/usr/bin/env python3
"""Generate README figures from benchmark_results/Search_results_v2.csv."""

import csv
import math
import os
from collections import Counter, defaultdict
from pathlib import Path
from statistics import fmean, median
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:
    raise SystemExit("Pillow is required: python -m pip install Pillow") from exc

ROOT = Path(__file__).resolve().parents[1]
CSV_PATH, ASSETS = ROOT / "benchmark_results" / "Search_results_v2.csv", ROOT / "assets"
CONFIGS = ("IDA*_Manhattan", "IDA*_LinearConflict", "IDA*_PDB_OOP", "IDA*_PDB_MutableArray", "IDA*_PDB_Bitboard")
C = {"bg": "#F7F9FB", "ink": "#17212B", "muted": "#536170", "grid": "#D8DEE5",
     "red": "#C84C4C", "gold": "#D89B2B", "green": "#2E8B75",
     "red_fill": "#F8E2E0", "gold_fill": "#FFF0CD", "green_fill": "#DDF1EA"}


def font(size, bold=False):
    windows = Path(os.environ.get("WINDIR", r"C:\Windows")) / "Fonts"
    windows_names = ("segoeuib.ttf", "arialbd.ttf") if bold else ("segoeui.ttf", "arial.ttf")
    dejavu_name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    candidates = [windows / name for name in windows_names]
    candidates.extend((
        Path("/usr/share/fonts/truetype/dejavu") / dejavu_name,
        Path("/usr/share/fonts/dejavu") / dejavu_name,
        Path(dejavu_name),
    ))
    for candidate in candidates:
        try:
            return ImageFont.truetype(str(candidate), size)
        except OSError:
            pass
    return ImageFont.load_default()


def center(draw, x, y, text, face, fill):
    box = draw.textbbox((0, 0), text, font=face)
    draw.text((x - (box[2] - box[0]) / 2, y), text, font=face, fill=fill)


def load():
    with CSV_PATH.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    if len(rows) != 2500 or any(row["Status"] != "Solved" for row in rows):
        raise ValueError("Expected 2500 solved rows")
    if {row["Configuration"] for row in rows} != set(CONFIGS):
        raise ValueError("Unexpected configuration set")
    if {int(row["Trial"]) for row in rows} != set(range(1, 6)):
        raise ValueError("Expected trials 1 through 5")
    if {int(row["Instance_ID"]) for row in rows} != set(range(1, 101)):
        raise ValueError("Expected 100 unique instances")
    if any(int(row["Search_Time_ns"]) <= 0 for row in rows):
        raise ValueError("Search times must be positive")
    cells = Counter((row["Configuration"], int(row["Execution_Order"])) for row in rows)
    if len(cells) != 25 or set(cells.values()) != {100}:
        raise ValueError("Execution positions are not balanced")
    groups = defaultdict(list)
    cases = defaultdict(dict)
    for row in rows:
        config = row["Configuration"]
        instance = int(row["Instance_ID"])
        trial = int(row["Trial"])
        groups[(config, instance)].append(row)
        if config in cases[(trial, instance)]:
            raise ValueError(f"Duplicate configuration for trial-instance {(trial, instance)}")
        cases[(trial, instance)][config] = row
    for key, group in groups.items():
        if {int(row["Trial"]) for row in group} != set(range(1, 6)):
            raise ValueError(f"Expected trials 1 through 5 for {key}")
        if len({int(row["Expanded_Nodes"]) for row in group}) != 1:
            raise ValueError(f"Repeated-trial mismatch for {key}")
    pdb_configs = ("IDA*_PDB_OOP", "IDA*_PDB_MutableArray", "IDA*_PDB_Bitboard")
    if len(cases) != 500:
        raise ValueError("Expected 500 trial-instance groups")
    for key, case in cases.items():
        if set(case) != set(CONFIGS):
            raise ValueError(f"Incomplete configuration set for trial-instance {key}")
        if {int(row["Execution_Order"]) for row in case.values()} != set(range(1, 6)):
            raise ValueError(f"Incomplete execution-order set for trial-instance {key}")
        if len({int(row["Solution_Length"]) for row in case.values()}) != 1:
            raise ValueError(f"Solution-depth mismatch for trial-instance {key}")
        if len({int(case[config]["Generated_Nodes"]) for config in pdb_configs}) != 1:
            raise ValueError(f"PDB generated-node mismatch for trial-instance {key}")
        if len({int(case[config]["Expanded_Nodes"]) for config in pdb_configs}) != 1:
            raise ValueError(f"PDB expanded-node mismatch for trial-instance {key}")
    return groups


def aggregate(groups):
    nodes, times = {}, defaultdict(dict)
    for config in CONFIGS:
        nodes[config] = fmean(int(groups[(config, i)][0]["Expanded_Nodes"]) for i in range(1, 101))
        for i in range(1, 101):
            times[config][i] = median(int(row["Search_Time_ns"]) for row in groups[(config, i)])
    def speedup(slower, faster):
        ratios = [times[slower][i] / times[faster][i] for i in range(1, 101)]
        return math.exp(fmean(math.log(value) for value in ratios))
    return nodes, {"oa": speedup("IDA*_PDB_OOP", "IDA*_PDB_MutableArray"),
                   "ab": speedup("IDA*_PDB_MutableArray", "IDA*_PDB_Bitboard"),
                   "ob": speedup("IDA*_PDB_OOP", "IDA*_PDB_Bitboard")}


def search_scale(nodes):
    image = Image.new("RGB", (1600, 900), C["bg"])
    draw = ImageDraw.Draw(image)
    center(draw, 800, 48, "Expanded-Node Reduction", font(48, True), C["ink"])
    center(draw, 800, 112, "Mean expanded nodes across 100 unique instances", font(25), C["muted"])
    left, right, top, bottom, maximum = 150, 1510, 205, 720, 25_000_000
    for tick in range(0, maximum + 1, 5_000_000):
        y = bottom - tick / maximum * (bottom - top)
        draw.line((left, y, right, y), fill=C["grid"], width=2)
        draw.text((82, y - 13), "0" if tick == 0 else f"{tick // 1_000_000}M", font=font(21), fill=C["muted"])
    series = (("Manhattan", nodes["IDA*_Manhattan"], C["red"]),
              ("Linear Conflict", nodes["IDA*_LinearConflict"], C["gold"]),
              ("6-6-3 PDB", nodes["IDA*_PDB_OOP"], C["green"]))
    for x, (name, value, color) in zip((390, 830, 1270), series):
        bar_top = bottom - value / maximum * (bottom - top)
        draw.rounded_rectangle((x - 125, bar_top, x + 125, bottom), radius=8, fill=color)
        center(draw, x, max(top - 10, bar_top - 38), f"{round(value):,}", font(24, True), C["ink"])
        center(draw, x, 748, name, font(26, True), C["ink"])
    reduction = nodes["IDA*_Manhattan"] / nodes["IDA*_PDB_OOP"]
    center(draw, 800, 822, f"Ratio of mean expanded nodes (Manhattan / PDB): {reduction:.2f}\u00d7", font(23), C["ink"])
    center(draw, 800, 858, "Fixed in-house 100-instance set; repeated trials had identical node counts", font(21), C["muted"])
    image.save(ASSETS / "search_scale_reduction.png", optimize=True)


def arrow(draw, start, end, y):
    draw.line((start, y, end - 18, y), fill=C["muted"], width=6)
    draw.polygon(((end - 18, y - 13), (end, y), (end - 18, y + 13)), fill=C["muted"])


def layered(speed):
    image = Image.new("RGB", (1600, 900), C["bg"])
    draw = ImageDraw.Draw(image)
    center(draw, 800, 48, "Layered Performance Comparison", font(48, True), C["ink"])
    center(draw, 800, 112, "Paired geometric mean of per-instance median search times", font(25), C["muted"])
    boxes = ((70, "General OOP", "Immutable board copies", "Full PDB-index rebuild", C["red_fill"], C["red"]),
             (650, "Mutable Array", "In-place swap/undo", "Incremental PDB index", C["gold_fill"], C["gold"]),
             (1230, "Bitboard", "Packed 64-bit board", "Incremental PDB index", C["green_fill"], C["green"]))
    for x, title, line1, line2, fill, outline in boxes:
        draw.rounded_rectangle((x, 270, x + 300, 500), radius=8, fill=fill, outline=outline, width=4)
        center(draw, x + 150, 315, title, font(30, True), C["ink"])
        center(draw, x + 150, 385, line1, font(22), C["muted"])
        center(draw, x + 150, 425, line2, font(22), C["muted"])
    arrow(draw, 390, 630, 375)
    arrow(draw, 970, 1210, 375)
    center(draw, 510, 305, f"{speed['oa']:.2f}\u00d7", font(31, True), C["ink"])
    center(draw, 1090, 305, f"{speed['ab']:.2f}\u00d7", font(31, True), C["ink"])
    center(draw, 510, 425, "combined hot-path gain", font(22), C["muted"])
    center(draw, 1090, 412, "additional packed", font(18), C["muted"])
    center(draw, 1090, 440, "representation gain", font(18), C["muted"])
    draw.rounded_rectangle((330, 610, 1270, 730), radius=8, fill="#FFFFFF", outline=C["grid"], width=3)
    center(draw, 800, 638, f"OOP to Bitboard: {speed['ob']:.2f}\u00d7 combined specialized-path gain", font(29, True), C["ink"])
    center(draw, 800, 690, "The first step combines several hot-path changes; it is not a per-optimization attribution.", font(22), C["muted"])
    center(draw, 800, 825, "Fixed in-house 100-instance set | 5 repeated trials per instance | single warmed JVM process", font(22), C["muted"])
    image.save(ASSETS / "layered_performance_comparison.png", optimize=True)


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    nodes, speed = aggregate(load())
    search_scale(nodes)
    layered(speed)
    print(f"Generated figures: PDB nodes={round(nodes['IDA*_PDB_OOP']):,}, OOP/Array={speed['oa']:.3f}x, Array/Bitboard={speed['ab']:.3f}x")


if __name__ == "__main__":
    main()
