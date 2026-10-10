"""Turns the icon sketch into the iOS app icon and the Android adaptive icon foreground.

The sketch is magenta strokes on black. The strokes are thickened so they survive at 60 px,
the pin is cropped square, and the stroke colour is sampled from the drawing.

    python3 -m venv design/.venv && design/.venv/bin/pip install pillow
    design/.venv/bin/python design/make-icon.py
"""

from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SKETCH = ROOT / "design" / "icon-sketch.png"
IOS_ICON = ROOT / "ios" / "Mismeet" / "Assets.xcassets" / "AppIcon.appiconset" / "icon-1024.png"
ANDROID_FOREGROUND = ROOT / "android" / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher_foreground.png"


def main() -> None:
    src = Image.open(SKETCH).convert("RGB")
    r, g, b = src.split()
    mask = Image.eval(ImageChops.subtract(ImageChops.darker(r, b), g), lambda v: 255 if v > 60 else 0).convert("L")
    x0, y0, x1, y1 = mask.getbbox()
    thick = mask.filter(ImageFilter.MaxFilter(23)).filter(ImageFilter.GaussianBlur(1.2))
    side = int(max(x1 - x0, y1 - y0) * 1.18)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    pin = Image.new("L", (side, side), 0)
    pin.paste(thick.crop((cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side)), (0, 0))
    samples = [src.getpixel((x, y)) for y in range(y0, y1, 7) for x in range(x0, x1, 7) if mask.getpixel((x, y))]
    color = tuple(sorted(s[i] for s in samples)[len(samples) // 2] for i in range(3))

    def render(size: int, fill: float, opaque: bool) -> Image.Image:
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 255 if opaque else 0))
        target = int(size * fill)
        layer = Image.new("RGBA", (target, target), color + (255,))
        layer.putalpha(pin.resize((target, target), Image.LANCZOS))
        canvas.alpha_composite(layer, ((size - target) // 2, (size - target) // 2))
        return canvas.convert("RGB") if opaque else canvas

    # iOS wants an opaque 1024 px square; the Android foreground keeps the pin inside the
    # 66 dp safe zone of the 108 dp layer, on a black background layer.
    render(1024, 0.78, True).save(IOS_ICON)
    render(432, 0.58, False).save(ANDROID_FOREGROUND)
    print(f"stroke colour {color}, wrote {IOS_ICON.name} and {ANDROID_FOREGROUND.name}")


if __name__ == "__main__":
    main()
