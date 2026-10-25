#!/usr/bin/env python3
"""Writes the country borders that both apps frame their map with, from Natural Earth's 1:50m
admin 0 countries (public domain), simplified to about 2 km. Each country, keyed by its ISO
3166-1 alpha-2 code, is a list of polygons, each a list of rings (outer first, then holes), each
ring a flat list of longitude, latitude pairs. Rerun after changing this script:

    python3 -I scripts/make-countries.py
"""
import json
import urllib.request
from pathlib import Path

SOURCE = (
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/"
    "ne_50m_admin_0_countries.geojson"
)
TOLERANCE = 0.02
DECIMALS = 2
ROOT = Path(__file__).resolve().parent.parent
OUTPUTS = [
    ROOT / "ios/Mismeet/Location/countries.json",
    ROOT / "android/app/src/main/assets/countries.json",
]


def distance_to_segment(point, start, end):
    (x, y), (x1, y1), (x2, y2) = point, start, end
    dx, dy = x2 - x1, y2 - y1
    if dx == 0 and dy == 0:
        return ((x - x1) ** 2 + (y - y1) ** 2) ** 0.5
    t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)))
    return ((x - x1 - t * dx) ** 2 + (y - y1 - t * dy) ** 2) ** 0.5


def simplify(ring):
    """Ramer-Douglas-Peucker; a ring it would collapse, such as a microstate's, stays whole."""
    keep = {0, len(ring) - 1}
    stack = [(0, len(ring) - 1)]
    while stack:
        first, last = stack.pop()
        farthest, index = 0.0, None
        for i in range(first + 1, last):
            d = distance_to_segment(ring[i], ring[first], ring[last])
            if d > farthest:
                farthest, index = d, i
        if index is not None and farthest > TOLERANCE:
            keep.add(index)
            stack += [(first, index), (index, last)]
    simple = [ring[i] for i in sorted(keep)]
    return simple if len(simple) >= 4 else ring


def code(properties):
    for key in ("ISO_A2_EH", "ISO_A2"):
        value = properties.get(key) or ""
        if len(value) == 2 and value.isalpha():
            return value
    return properties["ADM0_A3"]


def main():
    with urllib.request.urlopen(SOURCE) as response:
        features = json.load(response)["features"]
    countries = {}
    for feature in features:
        geometry = feature["geometry"]
        polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
        country = countries.setdefault(code(feature["properties"]), [])
        for polygon in polygons:
            country.append([[round(value, DECIMALS) for point in simplify(ring) for value in point[:2]] for ring in polygon])
    text = json.dumps(countries, separators=(",", ":"), sort_keys=True) + "\n"
    for output in OUTPUTS:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(text)
    points = sum(len(ring) // 2 for country in countries.values() for polygon in country for ring in polygon)
    print(f"{len(countries)} countries, {points} points, {len(text) // 1024} KiB")


if __name__ == "__main__":
    main()
