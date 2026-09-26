"""Procedural, seamlessly tiling material textures."""
import os
import numpy as np
from PIL import Image

RNG = np.random.default_rng(1337)


def fnoise(n, beta=2.0, seed=None):
    """periodic 1/f^beta noise in [0,1]"""
    rng = np.random.default_rng(seed) if seed is not None else RNG
    w = rng.normal(size=(n, n))
    F = np.fft.fft2(w)
    fx = np.fft.fftfreq(n)[:, None]
    fy = np.fft.fftfreq(n)[None, :]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1
    F = F / f ** (beta / 2)
    F[0, 0] = 0
    r = np.real(np.fft.ifft2(F))
    r = (r - r.min()) / (r.max() - r.min() + 1e-9)
    return r


def aniso_noise(n, sx, sy, seed=None):
    """periodic noise stretched along x (brushed / grain)"""
    rng = np.random.default_rng(seed) if seed is not None else RNG
    w = rng.normal(size=(n, n))
    F = np.fft.fft2(w)
    fx = np.fft.fftfreq(n)[:, None] * sy
    fy = np.fft.fftfreq(n)[None, :] * sx
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1
    F = F / f ** 1.2
    F[0, 0] = 0
    r = np.real(np.fft.ifft2(F))
    return (r - r.min()) / (r.max() - r.min() + 1e-9)


def colorize(v, c0, c1):
    c0 = np.array(c0, dtype=float)
    c1 = np.array(c1, dtype=float)
    return c0[None, None, :] * (1 - v[..., None]) + c1[None, None, :] * v[..., None]


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    a = np.clip(img, 0, 255).astype(np.uint8)
    if a.shape[2] == 3:
        Image.fromarray(a, 'RGB').convert('RGBA').save(path)
    else:
        Image.fromarray(a, 'RGBA').save(path)


def base(col, n=128, amp=0.12, beta=2.0, fine=0.05, seed=None):
    v = fnoise(n, beta, seed)
    f = fnoise(n, 0.3, None if seed is None else seed + 1)
    s = 1 + (v - 0.5) * amp * 2 + (f - 0.5) * fine * 2
    return np.array(col, dtype=float)[None, None, :] * s[..., None]


def metal(col, n=128, brushed=0.10, seed=None):
    img = base(col, n, amp=0.07, fine=0.05, seed=seed)
    b = aniso_noise(n, 1.0, 25.0, seed)
    img *= (1 + (b - 0.5) * brushed * 2)[..., None]
    # edge wear specks
    sp = fnoise(n, 0.5, None if seed is None else seed + 7)
    img += ((sp > 0.93) * 22.0)[..., None]
    return img


def wood(col, n=128, seed=None):
    rng = np.random.default_rng(seed)
    y = np.linspace(0, 1, n, endpoint=False)[:, None]
    x = np.linspace(0, 1, n, endpoint=False)[None, :]
    warp = fnoise(n, 2.2, seed) * 3.0
    rings = np.sin((y * 9 + warp) * 2 * np.pi) * 0.5 + 0.5
    grain = aniso_noise(n, 1.0, 30.0, seed)
    v = 0.55 * rings ** 2 + 0.45 * grain
    img = colorize(v, np.array(col) * 0.72, np.array(col) * 1.12)
    return img


def camo(cols, n=256, scale_beta=2.6, seed=None):
    a = fnoise(n, scale_beta, seed)
    b = fnoise(n, scale_beta, None if seed is None else seed + 3)
    img = np.zeros((n, n, 3))
    img[:] = cols[0]
    img[a > 0.55] = cols[1]
    img[(b > 0.6)] = cols[2]
    if len(cols) > 3:
        img[(a < 0.3) & (b > 0.45)] = cols[3]
    weave = fnoise(n, 0.2, None if seed is None else seed + 5)
    img *= (0.92 + weave * 0.16)[..., None]
    return img


def fabric(col, n=128, seed=None):
    img = base(col, n, amp=0.08, fine=0.10, seed=seed)
    yy, xx = np.mgrid[0:n, 0:n]
    weave = ((xx + yy) % 4 < 2) * 0.05 + ((xx - yy) % 4 < 2) * 0.03
    img *= (0.95 + weave)[..., None]
    return img


def rubber(col, n=128, seed=None):
    img = base(col, n, amp=0.05, fine=0.08, seed=seed)
    yy, xx = np.mgrid[0:n, 0:n]
    diamond = ((xx + yy) % 16 < 2) | ((xx - yy) % 16 < 2)
    img[diamond] *= 0.6
    return img


def solid(col, n=16):
    img = np.zeros((n, n, 3))
    img[:] = col
    return img


WEAPON_MATERIALS = {
    'metal_dark': lambda: metal((52, 54, 58), seed=1),
    'metal': lambda: metal((112, 114, 120), brushed=0.14, seed=2),
    'chrome': lambda: metal((188, 190, 196), brushed=0.18, seed=3),
    'polymer': lambda: base((36, 36, 38), amp=0.06, fine=0.12, seed=4),
    'polymer_od': lambda: base((86, 94, 60), amp=0.07, fine=0.10, seed=5),
    'polymer_tan': lambda: base((168, 144, 104), amp=0.07, fine=0.10, seed=6),
    'polymer_clear': lambda: base((128, 116, 82), amp=0.10, fine=0.06, seed=7),
    'rubber': lambda: rubber((26, 26, 27), seed=8),
    'bore': lambda: solid((8, 8, 8)),
    'brass': lambda: metal((196, 156, 64), brushed=0.12, seed=9),
    'lens': lambda: base((34, 52, 96), amp=0.25, fine=0.03, beta=3.0, seed=10),
    'white': lambda: solid((235, 235, 235)),
    'wood': lambda: wood((156, 76, 36), seed=11),
    'wood_dark': lambda: wood((104, 54, 28), seed=12),
    'bakelite': lambda: base((96, 38, 22), amp=0.10, fine=0.05, seed=13),
    'mag_orange': lambda: base((168, 72, 32), amp=0.10, fine=0.06, seed=14),
    'zeus_yellow': lambda: base((214, 182, 34), amp=0.06, fine=0.08, seed=15),
    'blade': lambda: metal((178, 180, 186), brushed=0.22, seed=16),
    'blade_dark': lambda: metal((104, 106, 112), brushed=0.2, seed=17),
    'grenade_od': lambda: base((78, 90, 54), amp=0.08, fine=0.08, seed=18),
    'grenade_gray': lambda: metal((150, 152, 150), brushed=0.06, seed=19),
    'grenade_smoke': lambda: base((106, 112, 102), amp=0.08, fine=0.08, seed=20),
    'grenade_band': lambda: base((206, 196, 48), amp=0.05, fine=0.05, seed=21),
    'grenade_red': lambda: base((172, 40, 30), amp=0.05, fine=0.05, seed=22),
    'grenade_decoy': lambda: base((86, 108, 118), amp=0.06, fine=0.06, seed=23),
    'glass_green': lambda: base((56, 118, 70), amp=0.15, fine=0.04, beta=3.0, seed=24),
    'label': lambda: base((204, 190, 150), amp=0.12, fine=0.08, seed=25),
    'cloth': lambda: fabric((176, 150, 116), seed=26),
    'c4_clay': lambda: base((192, 176, 130), amp=0.10, fine=0.10, seed=27),
    'tape': lambda: fabric((58, 58, 60), seed=28),
    'c4_panel': lambda: base((58, 68, 58), amp=0.05, fine=0.06, seed=29),
    'keys': lambda: base((28, 28, 30), amp=0.05, fine=0.05, seed=30),
    'lcd': lambda: base((90, 150, 90), amp=0.05, fine=0.02, seed=31),
    'wire_red': lambda: solid((200, 30, 30)),
    'wire_blue': lambda: solid((40, 60, 200)),
    'vest_od': lambda: fabric((80, 86, 60), seed=32),
    'helmet_od': lambda: base((78, 88, 58), amp=0.08, fine=0.08, seed=33),
}

AGENT = {
    't': {
        'shirt': lambda: camo([(176, 152, 110), (140, 116, 80), (196, 178, 136), (112, 92, 64)], seed=40),
        'pants': lambda: camo([(150, 130, 94), (120, 100, 70), (176, 158, 118)], seed=41),
        'vest': lambda: fabric((104, 94, 64), seed=42),
        'gear': lambda: fabric((84, 76, 52), seed=43),
        'mask': lambda: fabric((34, 32, 30), seed=44),
        'helmet': lambda: base((118, 104, 72), amp=0.08, fine=0.08, seed=45),
    },
    'ct': {
        'shirt': lambda: camo([(62, 74, 92), (44, 54, 70), (88, 100, 116), (36, 42, 52)], seed=50),
        'pants': lambda: camo([(56, 64, 76), (40, 46, 56), (80, 90, 102)], seed=51),
        'vest': lambda: fabric((40, 44, 50), seed=52),
        'gear': lambda: fabric((56, 62, 50), seed=53),
        'mask': lambda: fabric((26, 28, 32), seed=54),
        'helmet': lambda: base((52, 58, 64), amp=0.08, fine=0.08, seed=55),
    },
}
AGENT_COMMON = {
    'lens': lambda: base((30, 40, 50), amp=0.2, fine=0.03, beta=3.0, seed=60),
    'glove': lambda: rubber((34, 32, 30), seed=61),
    'boot': lambda: base((56, 44, 32), amp=0.08, fine=0.10, seed=62),
    'sole': lambda: rubber((20, 20, 20), seed=63),
    'belt': lambda: fabric((40, 40, 38), seed=64),
    'metal': lambda: metal((140, 140, 140), seed=65),
}


def build_all(root):
    tex = os.path.join(root, 'assets/csarsenal/textures')
    for name, fn in WEAPON_MATERIALS.items():
        save(fn(), f'{tex}/material/{name}.png')
    for team, mats in AGENT.items():
        for name, fn in {**AGENT_COMMON, **mats}.items():
            save(fn(), f'{tex}/agent/{team}/{name}.png')
    return list(WEAPON_MATERIALS) + [f'agent/{t}/{m}' for t in AGENT for m in {**AGENT_COMMON, **AGENT[t]}]


if __name__ == '__main__':
    import sys
    print(len(build_all(sys.argv[1])))
