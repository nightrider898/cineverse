"""Draws the original CineVerse email artwork (logo + banners) as PNGs. Email clients can't render SVG or web fonts
reliably, so these are raster images embedded inline (CID) by the backend.

Usage: python3 email_art.py <fonts-dir with Sora/Jakarta TTFs> backend/src/main/resources/email/images
(fonts: npm pack @fontsource/sora @fontsource/plus-jakarta-sans, then convert the woff2 files with fontTools)
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONTS = Path(sys.argv[1])
OUT = Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)

# BookMyEvent palette (frontend/app/globals.css)
BRAND_DARK = (67, 20, 7)       # brand-950 #431407
INK = (33, 26, 21)             # ink #211A15
BRAND = (234, 88, 12)          # brand-600 #EA580C
BRAND_700 = (194, 65, 12)      # brand-700 #C2410C
BRAND_MID = (249, 115, 22)     # brand-500 #F97316
PINK = (251, 146, 60)          # brand-400 #FB923C (kept name for the light gradient stop)
PEACH = (253, 186, 116)        # brand-300 #FDBA74
GOLD = (254, 240, 138)
WHITE = (255, 255, 255)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(w, h, stops, angle_deg=120):
    img = Image.new("RGB", (w, h))
    px = img.load()
    a = math.radians(angle_deg)
    dx, dy = math.cos(a), math.sin(a)
    proj = [x * dx + y * dy for x, y in ((0, 0), (w, 0), (0, h), (w, h))]
    lo, hi = min(proj), max(proj)
    for y in range(h):
        for x in range(w):
            t = ((x * dx + y * dy) - lo) / (hi - lo)
            for i in range(len(stops) - 1):
                t0, c0 = stops[i]
                t1, c1 = stops[i + 1]
                if t0 <= t <= t1:
                    px[x, y] = lerp(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 0)
                    break
    return img


def mark(size):
    """The app mark from components/layout/Logo.tsx (40x40 viewBox), rasterised at 4x then downsampled."""
    k = size * 4 / 40
    s = size * 4
    tile = gradient(s, s, [(0, BRAND_700), (0.55, BRAND_MID), (1, PEACH)], 45).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=int(12 * k), fill=255)
    tile.putalpha(mask)
    d = ImageDraw.Draw(tile)
    d.rounded_rectangle((8 * k, 10 * k, 32 * k, 31 * k), radius=int(4 * k), fill=WHITE)
    w = max(1, int(2 * k))
    for x in (13, 27):
        d.line((x * k, 8 * k, x * k, 13 * k), fill=BRAND_700, width=w)
        d.ellipse((x * k - w / 2, 8 * k - w / 2, x * k + w / 2, 8 * k + w / 2), fill=BRAND_700)
        d.ellipse((x * k - w / 2, 13 * k - w / 2, x * k + w / 2, 13 * k + w / 2), fill=BRAND_700)
    d.line((9 * k, 16 * k, 31 * k, 16 * k), fill=BRAND_700, width=w)
    d.ellipse((17 * k, 20 * k, 23 * k, 26 * k), fill=BRAND_MID)
    return tile.resize((size, size), Image.LANCZOS)


def logo(path, height=96, on_dark=False):
    m = mark(height)
    f = font("Sora-ExtraBold.ttf", int(height * 0.5))
    first, second = "BookMy", "Event"
    text_w = int(f.getlength(first + second))
    w = height + int(height * 0.24) + text_w + 8
    img = Image.new("RGBA", (w, height), (0, 0, 0, 0))
    img.paste(m, (0, 0), m)
    d = ImageDraw.Draw(img)
    x = height + int(height * 0.24)
    y = (height - f.getbbox(first + second)[3]) // 2 - 2
    d.text((x, y), first, font=f, fill=WHITE if on_dark else INK)
    d.text((x + int(f.getlength(first)), y), second, font=f, fill=PEACH if on_dark else BRAND)
    img.save(path)
    return img


def stars(d, w, h, n, seed):
    rnd = random.Random(seed)
    for _ in range(n):
        x, y = rnd.randint(0, w), rnd.randint(0, int(h * 0.65))
        r = rnd.choice([1, 1, 2, 2, 3])
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, rnd.randint(60, 200)))


def cinema_banner(path, w=1200, h=440, seed=3):
    bg = gradient(w, h, [(0, (28, 12, 4)), (0.5, BRAND_DARK), (0.8, (124, 45, 18)), (1, BRAND_700)], 110).convert("RGBA")
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((w * 0.12, -h * 0.55, w * 0.88, h * 0.55), fill=(253, 186, 116, 110))
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    bg = Image.alpha_composite(bg, glow)
    d = ImageDraw.Draw(bg)
    stars(d, w, h, 70, seed)
    # the screen: a bright arc
    d.arc((w * 0.16, h * 0.08, w * 0.84, h * 0.62), start=200, end=340, fill=(255, 247, 237, 255), width=10)
    sheen = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(sheen).arc((w * 0.16, h * 0.08, w * 0.84, h * 0.62), start=200, end=340, fill=(253, 186, 116, 200), width=34)
    bg = Image.alpha_composite(bg, sheen.filter(ImageFilter.GaussianBlur(14)))
    d = ImageDraw.Draw(bg)
    # rows of seats in perspective
    rows = 5
    for r in range(rows):
        t = r / (rows - 1)
        y = h * (0.54 + 0.095 * r)
        seat_w = 26 + 12 * t
        gap = 10 + 5 * t
        count = int((w * (0.62 + 0.36 * t)) / (seat_w + gap))
        total = count * (seat_w + gap) - gap
        x0 = (w - total) / 2
        col = lerp((154, 52, 18), (90, 30, 10), t)
        for c in range(count):
            if c in (count // 2 - 1, count // 2):  # aisle
                continue
            x = x0 + c * (seat_w + gap)
            color = GOLD if (r == 2 and c in (count // 2 + 2, count // 2 + 3)) else col
            d.rounded_rectangle((x, y, x + seat_w, y + seat_w * 0.78), radius=int(seat_w * 0.3), fill=color + (255,))
    bg.convert("RGB").save(path, optimize=True)


def ticket_banner(path, w=1200, h=440, seed=9):
    bg = gradient(w, h, [(0, BRAND_DARK), (0.55, BRAND), (1, PINK)], 120).convert("RGBA")
    d = ImageDraw.Draw(bg)
    stars(d, w, h, 50, seed)
    rnd = random.Random(seed)
    # confetti
    for _ in range(90):
        x, y = rnd.randint(0, w), rnd.randint(0, h)
        c = rnd.choice([GOLD, (255, 255, 255), (254, 215, 170), (67, 20, 7)])
        a = rnd.random() * math.pi
        L = rnd.randint(8, 18)
        d.line((x, y, x + L * math.cos(a), y + L * math.sin(a)), fill=c + (220,), width=4)
    # a tilted ticket
    tw, th = 560, 230
    ticket = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    td = ImageDraw.Draw(ticket)
    td.rounded_rectangle((0, 0, tw - 1, th - 1), radius=26, fill=(255, 255, 255, 255))
    for cx in (0, tw):
        td.ellipse((cx - 26, th / 2 - 26, cx + 26, th / 2 + 26), fill=(0, 0, 0, 0))
    stub = int(tw * 0.72)
    for y in range(16, th - 16, 18):
        td.line((stub, y, stub, y + 9), fill=(253, 186, 116, 255), width=3)
    f1 = font("Sora-ExtraBold.ttf", 46)
    f2 = font("Jakarta-Bold.ttf", 18)
    td.text((40, 52), "ADMIT ONE", font=f1, fill=INK)
    td.text((42, 120), "BOOKMYEVENT  ·  ENJOY THE SHOW", font=f2, fill=BRAND)
    for i in range(5):  # QR-ish blocks on the stub (decorative, not a real code)
        for j in range(5):
            if (i * 7 + j * 3) % 4:
                td.rectangle((stub + 26 + i * 19, 68 + j * 19, stub + 41 + i * 19, 83 + j * 19), fill=INK)
    ticket = ticket.rotate(-7, resample=Image.BICUBIC, expand=True)
    pad = 60
    shadow = Image.new("RGBA", (ticket.width + 2 * pad, ticket.height + 2 * pad), (40, 10, 0, 0))
    shadow.paste((40, 10, 0, 130), (pad, pad), ticket.split()[3])
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    ox, oy = (w - ticket.width) // 2, (h - ticket.height) // 2 + 6
    bg.alpha_composite(shadow, (ox + 10 - pad, oy + 22 - pad))
    bg.alpha_composite(ticket, (ox, oy))
    bg.convert("RGB").save(path, optimize=True)


def shield_banner(path, w=1200, h=300):
    bg = gradient(w, h, [(0, (28, 12, 4)), (0.7, BRAND_DARK), (1, BRAND_700)], 120).convert("RGBA")
    d = ImageDraw.Draw(bg)
    stars(d, w, h, 40, 5)
    cx, cy, s = w / 2, h / 2, 92
    pts = [(cx, cy - s), (cx + s * 0.85, cy - s * 0.62), (cx + s * 0.72, cy + s * 0.35), (cx, cy + s), (cx - s * 0.72, cy + s * 0.35), (cx - s * 0.85, cy - s * 0.62)]
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(glow).polygon(pts, fill=(253, 186, 116, 150))
    bg = Image.alpha_composite(bg, glow.filter(ImageFilter.GaussianBlur(28)))
    d = ImageDraw.Draw(bg)
    d.polygon(pts, fill=(255, 255, 255, 255))
    d.line([(cx - 34, cy + 2), (cx - 8, cy + 28), (cx + 40, cy - 26)], fill=BRAND + (255,), width=16, joint="curve")
    bg.convert("RGB").save(path, optimize=True)


if __name__ == "__main__":
    logo(OUT / "logo.png", 96)
    logo(OUT / "logo-light.png", 72, on_dark=True)
    cinema_banner(OUT / "banner-welcome.png")
    ticket_banner(OUT / "banner-booking.png")
    shield_banner(OUT / "banner-security.png")
    for p in sorted(OUT.glob("*.png")):
        print(p.name, Image.open(p).size, p.stat().st_size // 1024, "KB")
