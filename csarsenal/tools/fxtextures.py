"""Particle, decal, muzzle flash and GUI textures."""
import os, math, json
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from textures import fnoise, save

def rgba(a):
    return np.clip(a, 0, 255).astype(np.uint8)

def radial(n):
    y, x = np.mgrid[0:n, 0:n] + 0.5
    c = n / 2
    return np.sqrt((x - c) ** 2 + (y - c) ** 2) / c, np.arctan2(y - c, x - c)

def smoke(n, seed):
    r, _ = radial(n)
    nz = fnoise(n, 2.2, seed)
    nz2 = fnoise(n, 1.4, seed + 9)
    a = np.clip(1.0 - r * (0.85 + 0.35 * nz), 0, 1) ** 1.6
    a *= 0.75 + 0.25 * nz2
    g = 200 + 40 * nz2
    img = np.stack([g, g, g * 1.0, a * 255], -1)
    return img

def puff(n, seed, col=(150, 145, 138)):
    r, _ = radial(n)
    nz = fnoise(n, 1.8, seed)
    a = np.clip(1 - r * (1.0 + 0.4 * nz), 0, 1) ** 1.2
    img = np.zeros((n, n, 4)); img[..., 0] = col[0]; img[..., 1] = col[1]; img[..., 2] = col[2]; img[..., 3] = a * 255
    return img

def spark(n):
    r, _ = radial(n)
    a = np.clip(1 - r, 0, 1) ** 2
    img = np.stack([255 + 0 * r, 225 * np.ones_like(r) , 140 + 100 * a, a * 255], -1)
    return img

def blood(n, seed):
    rng = np.random.default_rng(seed)
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _ in range(6):
        cx, cy = rng.uniform(n * 0.25, n * 0.75, 2)
        rr = rng.uniform(n * 0.1, n * 0.28)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(int(rng.uniform(110, 160)), 8, 8, 235))
    return np.array(img.filter(ImageFilter.GaussianBlur(0.6))).astype(float)

def flame(n, frame):
    y, x = np.mgrid[0:n, 0:n] / n
    nz = fnoise(n, 2.0, 100 + frame)
    cx = 0.5 + (nz - 0.5) * 0.25
    w = 0.32 * (1 - y) ** 0.6 + 0.02
    body = np.clip(1 - np.abs(x - cx) / w, 0, 1) * np.clip((y - 0.05) * 3, 0, 1)
    body *= np.clip(1 - (1 - y) * (0.8 + 0.6 * nz), 0, 1) + 0.2
    body = np.clip(body, 0, 1)
    hot = body ** 1.5
    img = np.stack([255 * np.ones_like(body), 90 + 150 * hot, 20 + 120 * hot ** 2, body * 255], -1)
    return img[::-1]

def fireball(n, frame):
    r, th = radial(n)
    nz = fnoise(n, 2.1, 200 + frame)
    a = np.clip(1 - r * (0.8 + 0.5 * nz), 0, 1)
    hot = a ** 1.3
    img = np.stack([255 * np.ones_like(a), 120 + 130 * hot, 30 + 150 * hot ** 3, np.clip(a * 1.4, 0, 1) * 255], -1)
    return img

def muzzle_front(n):
    r, th = radial(n)
    star = 0.55 + 0.45 * np.cos(th * 5) ** 8
    a = np.clip(1 - r / star, 0, 1) ** 1.3 + np.clip(1 - r * 3, 0, 1)
    a = np.clip(a, 0, 1)
    img = np.stack([255 * np.ones_like(a), 200 + 55 * a, 110 + 140 * a ** 2, a * 255], -1)
    return img

def muzzle_side(w, h):
    y, x = np.mgrid[0:h, 0:w] + 0.5
    u = x / w; v = (y - h / 2) / (h / 2)
    width = 0.25 + 0.75 * np.sin(np.clip(u, 0, 1) * math.pi) ** 0.7 * (1 - u) ** 0.4
    nz = fnoise(max(w, h), 1.5, 7)[:h, :w]
    a = np.clip(1 - np.abs(v) / (width * (0.7 + 0.5 * nz)), 0, 1) * np.clip(1 - u * 0.9, 0, 1)
    img = np.stack([255 * np.ones_like(a), 190 + 60 * a, 100 + 150 * a ** 2, a * 255], -1)
    return img

def hole(n, kind, seed):
    rng = np.random.default_rng(seed)
    r, th = radial(n)
    nz = fnoise(n, 1.6, seed)
    img = np.zeros((n, n, 4))
    base = {'concrete': (60, 58, 55), 'wood': (60, 40, 22), 'metal': (40, 40, 44), 'glass': (200, 210, 220), 'dirt': (45, 36, 28)}[kind]
    if kind == 'glass':
        im = Image.new('RGBA', (n, n), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        for k in range(9):
            a = rng.uniform(0, 2 * math.pi); L = rng.uniform(0.3, 0.5) * n
            d.line([n / 2, n / 2, n / 2 + math.cos(a) * L, n / 2 + math.sin(a) * L], fill=(230, 240, 250, 200), width=1)
        for rr in (0.18, 0.3):
            d.ellipse([n / 2 - rr * n, n / 2 - rr * n, n / 2 + rr * n, n / 2 + rr * n], outline=(220, 230, 240, 120))
        d.ellipse([n / 2 - 2, n / 2 - 2, n / 2 + 2, n / 2 + 2], fill=(20, 20, 20, 255))
        return np.array(im).astype(float)
    core = np.clip(1 - r / 0.16, 0, 1)
    ring = np.clip(1 - np.abs(r - 0.28) / 0.18, 0, 1) * (0.5 + 0.5 * nz)
    chips = (np.cos(th * rng.integers(5, 9)) * 0.5 + 0.5) * np.clip(1 - r / 0.55, 0, 1) * nz
    a = np.clip(core + ring * 0.8 + chips * 0.5, 0, 1)
    img[..., 0] = base[0] * (1 - core) * 1.0 + 5 * core
    img[..., 1] = base[1] * (1 - core) + 5 * core
    img[..., 2] = base[2] * (1 - core) + 5 * core
    img[..., 3] = a * 255
    return img

def scope(n, lines=True):
    img = Image.new('RGBA', (n, n), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    r = n * 0.49
    d.ellipse([n / 2 - r, n / 2 - r, n / 2 + r, n / 2 + r], fill=(0, 0, 0, 0))
    # soft vignette ring
    arr = np.array(img).astype(float)
    rr, _ = radial(n)
    vig = np.clip((rr - 0.86) / 0.12, 0, 1)
    arr[..., 3] = np.maximum(arr[..., 3], vig * 255)
    img = Image.fromarray(rgba(arr), 'RGBA')
    d = ImageDraw.Draw(img)
    if lines:
        d.line([0, n / 2, n, n / 2], fill=(0, 0, 0, 255), width=max(1, n // 512))
        d.line([n / 2, 0, n / 2, n], fill=(0, 0, 0, 255), width=max(1, n // 512))
    return np.array(img).astype(float)

def scope_dot(n):
    arr = np.zeros((n, n, 4))
    rr, _ = radial(n)
    arr[..., 3] = np.clip((rr - 0.78) / 0.18, 0, 1) * 235
    dot = np.clip(1 - rr / 0.012, 0, 1)
    arr[..., 0] = 255 * dot; arr[..., 3] = np.maximum(arr[..., 3], dot * 255)
    ring = np.clip(1 - np.abs(rr - 0.06) / 0.004, 0, 1) * 0.6
    arr[..., 0] = np.maximum(arr[..., 0], ring * 255); arr[..., 3] = np.maximum(arr[..., 3], ring * 200)
    return arr

def premul(img):
    img = img.copy()
    a = img[..., 3:4] / 255.0
    img[..., :3] = img[..., :3] * a
    return img

def build(root):
    tex = os.path.join(root, 'assets/csarsenal/textures')
    parts = {}
    for i in range(4):
        save(smoke(64, 300 + i), f'{tex}/particle/smoke_{i}.png')
    parts['smoke'] = [f'csarsenal:smoke_{i}' for i in range(4)]
    for i in range(3):
        save(puff(16, 320 + i), f'{tex}/particle/impact_{i}.png')
    parts['impact'] = [f'csarsenal:impact_{i}' for i in range(3)]
    parts['muzzle_smoke'] = [f'csarsenal:impact_{i}' for i in range(3)]
    save(premul(spark(8)), f'{tex}/particle/spark.png'); parts['spark'] = ['csarsenal:spark']
    for i in range(3):
        save(blood(16, 340 + i), f'{tex}/particle/blood_{i}.png')
    parts['blood'] = [f'csarsenal:blood_{i}' for i in range(3)]
    for i in range(4):
        save(flame(32, i), f'{tex}/particle/flame_{i}.png')
    parts['flame'] = [f'csarsenal:flame_{i}' for i in range(4)]
    for i in range(4):
        save(fireball(64, i), f'{tex}/particle/fireball_{i}.png')
    parts['fireball'] = [f'csarsenal:fireball_{i}' for i in range(4)]
    pdir = os.path.join(root, 'assets/csarsenal/particles'); os.makedirs(pdir, exist_ok=True)
    for k, v in parts.items():
        json.dump({'textures': v}, open(f'{pdir}/{k}.json', 'w'))
    save(premul(muzzle_front(64)), f'{tex}/fx/muzzle_front.png')
    save(premul(muzzle_side(128, 48)), f'{tex}/fx/muzzle_side.png')
    for kind in ('concrete', 'wood', 'metal', 'glass', 'dirt'):
        save(hole(32, kind, hash(kind) % 1000), f'{tex}/fx/hole_{kind}.png')
    save(scope(1024), f'{tex}/gui/scope.png')
    save(scope_dot(512), f'{tex}/gui/scope_dot.png')
    return len(parts)

if __name__ == '__main__':
    import sys
    print(build(sys.argv[1]))
