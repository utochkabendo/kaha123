"""Procedural sound design for CS Arsenal (all sounds are synthesised, mono 44.1 kHz OGG)."""
import os
import json
import math
import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def noise(n, rng):
    return rng.standard_normal(n)


def bp(x, lo, hi, order=2):
    lo = max(20, lo)
    hi = min(SR / 2 - 100, hi)
    if hi <= lo + 10:
        hi = lo + 10
    sos = signal.butter(order, [lo, hi], btype='band', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def lp(x, fc, order=2):
    sos = signal.butter(order, min(fc, SR / 2 - 100), btype='low', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def hp(x, fc, order=2):
    sos = signal.butter(order, max(fc, 20), btype='high', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def env_exp(n, attack, tau, delay=0.0):
    t = np.arange(n) / SR - delay
    e = np.where(t < 0, 0.0, np.where(t < attack, t / max(attack, 1e-6), np.exp(-np.clip(t - attack, 0, None) / tau)))
    return e


def modal(n, freqs, decays, amps, rng, delay=0.0):
    t = np.arange(n) / SR - delay
    out = np.zeros(n)
    m = t >= 0
    for f, d, a in zip(freqs, decays, amps):
        ph = rng.uniform(0, 2 * np.pi)
        out[m] += a * np.sin(2 * np.pi * f * t[m] + ph) * np.exp(-t[m] / d)
    return out


def click(n, rng, delay, freqs=(2500, 4200, 6100), decay=0.012, amp=1.0, noise_amp=0.6):
    out = modal(n, [f * rng.uniform(0.92, 1.08) for f in freqs], [decay * rng.uniform(0.7, 1.3) for _ in freqs], [amp * rng.uniform(0.5, 1.0) for _ in freqs], rng, delay)
    nb = noise(n, rng) * env_exp(n, 0.0002, 0.0025, delay) * noise_amp * amp
    return out + hp(nb, 1500)


def friction(n, rng, delay, dur, lo=800, hi=4000, amp=0.4):
    t = np.arange(n) / SR - delay
    e = np.where((t >= 0) & (t < dur), np.sin(np.clip(t / dur, 0, 1) * np.pi) ** 0.7, 0.0)
    return bp(noise(n, rng), lo, hi) * e * amp


def reverb_ir(dur, tau, rng, bright=4000, predelay=0.012):
    n = int(dur * SR)
    t = np.arange(n) / SR
    ir = noise(n, rng) * np.exp(-t / tau)
    ir = lp(ir, bright)
    ir[: int(predelay * SR)] = 0
    # a few early reflections
    for d, a in ((0.017, 0.5), (0.029, 0.35), (0.043, 0.25), (0.061, 0.2)):
        k = int(d * SR * rng.uniform(0.9, 1.1))
        if k < n:
            ir[k] += a * rng.choice([-1, 1])
    return ir / (np.sqrt(np.sum(ir ** 2)) + 1e-9)


def conv(x, ir):
    return signal.fftconvolve(x, ir)[: len(x)]


def finish(x, peak=0.84, fade=0.05, drive=0.0):
    if drive > 0:
        x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * (1 + drive)) / np.tanh(1 + drive)
    n = len(x)
    f = int(fade * SR)
    if f > 0 and f < n:
        x[-f:] *= np.linspace(1, 0, f) ** 2
    x = x - np.mean(x)
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


# ------------------------------------------------------------------------------ gunshots

def gunshot(p, rng, far=False):
    dur = p.get('dur', 1.1) * (1.6 if far else 1.0)
    n = int(dur * SR)
    x = np.zeros(n)
    # 1) supersonic crack / muzzle report transient
    crack = hp(noise(n, rng), 900) * env_exp(n, 0.00015, p['crack_tau'])
    x += crack * p['crack']
    # 2) blast body - band limited noise
    body = bp(noise(n, rng), p['body_lo'], p['body_hi'])
    body *= env_exp(n, 0.0006, p['body_tau'])
    x += body * p['body'] * 3.0
    # low end "chest" noise
    low = lp(noise(n, rng), p.get('low_fc', 250)) * env_exp(n, 0.001, p['body_tau'] * 1.8)
    x += low * p.get('low', 0.8) * 4.0
    # 3) thump - pitched sine sweep
    t = np.arange(n) / SR
    f0, f1 = p['thump_f']
    fr = f1 + (f0 - f1) * np.exp(-t / 0.03)
    ph = 2 * np.pi * np.cumsum(fr) / SR
    x += np.sin(ph) * env_exp(n, 0.001, p['thump_tau']) * p['thump']
    # 4) mechanics (bolt / slide)
    for (d, a) in p.get('mech', []):
        x += click(n, rng, d, freqs=p.get('mech_freqs', (1800, 3300, 5200)), decay=0.02, amp=a * 0.35, noise_amp=0.8)
    # saturate the dry report (punch) before the tail is added
    dry = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * (1 + p.get('drive', 1.2))) / np.tanh(1 + p.get('drive', 1.2))
    # 5) room / outdoor tail: darker and much quieter than the report
    ir = reverb_ir(min(dur, p['rev_tau'] * 6 + 0.1), p['rev_tau'] * (1.5 if far else 1.0), rng, bright=p.get('rev_bright', 2200))
    wet = lp(conv(dry, ir), 3000 if not far else 1500)
    wet_amt = p['wet'] * 0.30 * (2.5 if far else 1.0)
    y = dry * (0.25 if far else 1.0) + wet * wet_amt
    if far:
        y = lp(y, 1400, order=3)
        # distant: the attack arrives smeared
        y = conv(y, np.exp(-np.arange(int(0.01 * SR)) / (0.003 * SR)))
    return finish(y, drive=0.0)


def suppressed_shot(p, rng):
    n = int(0.6 * SR)
    x = np.zeros(n)
    x += bp(noise(n, rng), p.get('sil_lo', 600), p.get('sil_hi', 3200)) * env_exp(n, 0.0005, p.get('sil_tau', 0.018)) * 2.4
    x += hp(noise(n, rng), 3000) * env_exp(n, 0.0001, 0.0015) * 0.6
    t = np.arange(n) / SR
    x += np.sin(2 * np.pi * (160 + 200 * np.exp(-t / 0.01)) * t) * env_exp(n, 0.0008, 0.03) * 0.5
    for (d, a) in p.get('mech', [(0.012, 1.0), (0.055, 0.8)]):
        x += click(n, rng, d, freqs=(1600, 2900, 4700), decay=0.018, amp=a * 0.55)
    ir = reverb_ir(0.4, 0.07, rng, bright=3000)
    y = x + conv(x, ir) * 0.25
    return finish(y, drive=0.6)


GUN_BASE = {
    'pistol': dict(crack=1.0, crack_tau=0.0025, body=1.0, body_lo=500, body_hi=3500, body_tau=0.022, low=0.6, low_fc=300,
                   thump=0.6, thump_f=(180, 70), thump_tau=0.05, mech=[(0.018, 1.0), (0.06, 0.5)], rev_tau=0.22, wet=0.45, dur=0.9, drive=1.4),
    'smg': dict(crack=1.0, crack_tau=0.002, body=1.0, body_lo=600, body_hi=4000, body_tau=0.02, low=0.7, low_fc=280,
                thump=0.6, thump_f=(170, 70), thump_tau=0.045, mech=[(0.014, 0.8), (0.045, 0.6)], rev_tau=0.2, wet=0.4, dur=0.8, drive=1.4),
    'rifle': dict(crack=1.3, crack_tau=0.003, body=1.2, body_lo=350, body_hi=3000, body_tau=0.03, low=1.0, low_fc=220,
                  thump=0.9, thump_f=(150, 55), thump_tau=0.07, mech=[(0.012, 0.9), (0.05, 0.7)], rev_tau=0.35, wet=0.55, dur=1.2, drive=1.8),
    'sniper': dict(crack=1.6, crack_tau=0.004, body=1.4, body_lo=250, body_hi=2600, body_tau=0.05, low=1.4, low_fc=180,
                   thump=1.3, thump_f=(120, 40), thump_tau=0.12, mech=[], rev_tau=0.7, wet=0.7, dur=2.2, drive=2.2),
    'shotgun': dict(crack=1.2, crack_tau=0.004, body=1.5, body_lo=200, body_hi=2200, body_tau=0.05, low=1.5, low_fc=200,
                    thump=1.2, thump_f=(130, 45), thump_tau=0.1, mech=[], rev_tau=0.5, wet=0.6, dur=1.6, drive=2.0),
    'mg': dict(crack=1.2, crack_tau=0.0025, body=1.2, body_lo=380, body_hi=3200, body_tau=0.026, low=1.0, low_fc=230,
               thump=0.9, thump_f=(150, 55), thump_tau=0.06, mech=[(0.01, 0.9), (0.04, 0.6)], rev_tau=0.3, wet=0.5, dur=1.0, drive=1.8),
}

GUNS = {
    # id: (class, overrides)
    'glock': ('pistol', dict(body_lo=700, body_hi=4200, thump_f=(200, 80))),
    'usp_s': ('pistol', dict(body_lo=520, thump_f=(170, 65))),
    'p2000': ('pistol', dict(body_lo=560, thump_f=(175, 68))),
    'p250': ('pistol', dict(body_lo=480, body_tau=0.026, thump=0.8)),
    'fiveseven': ('pistol', dict(body_lo=800, body_hi=4800, crack=1.3)),
    'tec9': ('pistol', dict(body_lo=650, body_hi=3800, thump_f=(190, 75), low=0.7)),
    'cz75a': ('pistol', dict(body_lo=620, body_hi=3900, body_tau=0.02)),
    'elite': ('pistol', dict(body_lo=560, thump_f=(170, 65))),
    'deagle': ('pistol', dict(crack=1.6, body=1.5, body_lo=300, body_hi=2800, body_tau=0.045, low=1.4, thump=1.3, thump_f=(130, 45), thump_tau=0.1, rev_tau=0.5, wet=0.6, dur=1.5, drive=2.2)),
    'revolver': ('pistol', dict(crack=1.6, body=1.5, body_lo=280, body_hi=2600, body_tau=0.05, low=1.5, thump=1.3, thump_f=(120, 42), thump_tau=0.11, mech=[(0.0, 0.8)], rev_tau=0.55, wet=0.6, dur=1.6, drive=2.3)),
    'mac10': ('smg', dict(body_lo=700, body_hi=4500, body_tau=0.016, thump_f=(210, 80))),
    'mp9': ('smg', dict(body_lo=720, body_hi=4400, body_tau=0.017)),
    'mp7': ('smg', dict(body_lo=850, body_hi=5000, crack=1.2)),
    'mp5sd': ('smg', dict(sil=True)),
    'ump45': ('smg', dict(body_lo=450, body_hi=3200, body_tau=0.026, thump=0.8, thump_f=(150, 60))),
    'p90': ('smg', dict(body_lo=800, body_hi=4800)),
    'bizon': ('smg', dict(body_lo=600, body_hi=3800)),
    'galilar': ('rifle', dict(body_lo=380, thump_f=(155, 58))),
    'famas': ('rifle', dict(body_lo=420, body_hi=3400, body_tau=0.026)),
    'ak47': ('rifle', dict(body_lo=280, body_hi=2800, body_tau=0.036, low=1.3, thump=1.1, thump_f=(135, 48), crack=1.5, drive=2.0)),
    'm4a4': ('rifle', dict(body_lo=420, body_hi=3400, body_tau=0.028)),
    'm4a1_s': ('rifle', dict(body_lo=420, body_hi=3400, body_tau=0.028)),
    'sg556': ('rifle', dict(body_lo=360, body_hi=3100)),
    'aug': ('rifle', dict(body_lo=400, body_hi=3300)),
    'ssg08': ('sniper', dict(body_lo=320, body_hi=3000, body_tau=0.04, low=1.1, thump=1.0, rev_tau=0.55, dur=1.8)),
    'awp': ('sniper', dict()),
    'g3sg1': ('sniper', dict(body_lo=300, body_tau=0.04, rev_tau=0.5, dur=1.6, mech=[(0.012, 0.8), (0.05, 0.6)])),
    'scar20': ('sniper', dict(body_lo=320, body_tau=0.04, rev_tau=0.5, dur=1.6, mech=[(0.012, 0.8), (0.05, 0.6)])),
    'nova': ('shotgun', dict()),
    'xm1014': ('shotgun', dict(body_tau=0.04, mech=[(0.03, 0.8)])),
    'sawedoff': ('shotgun', dict(body_lo=180, low=1.8, thump=1.4)),
    'mag7': ('shotgun', dict(body_lo=230, body_hi=2500)),
    'm249': ('mg', dict()),
    'negev': ('mg', dict(body_lo=420, body_hi=3500, body_tau=0.022)),
    'taser': (None, None),
}
SILENCED = {'usp_s': dict(sil_lo=700, sil_hi=3600, sil_tau=0.016), 'm4a1_s': dict(sil_lo=500, sil_hi=3000, sil_tau=0.022), 'mp5sd': dict(sil_lo=550, sil_hi=3200, sil_tau=0.02)}


# ------------------------------------------------------------------------------ misc sound design

def whoosh(dur, rng, lo0=300, lo1=1500, amp=1.0, peak_at=0.4):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = noise(n, rng)
    # time varying band: process in blocks
    out = np.zeros(n)
    blocks = 24
    for b in range(blocks):
        s, e = b * n // blocks, (b + 1) * n // blocks
        u = (b + 0.5) / blocks
        c = lo0 + (lo1 - lo0) * math.sin(u * math.pi)
        seg = bp(x[max(0, s - 512):e], c * 0.6, c * 1.8)[-(e - s):]
        out[s:e] = seg
    envl = np.exp(-((t / dur - peak_at) ** 2) / 0.03)
    return out * envl * amp


def explosion(dur, rng, big=1.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x += hp(noise(n, rng), 800) * env_exp(n, 0.0002, 0.006) * 1.5
    # rumble with falling cutoff
    r = noise(n, rng)
    out = np.zeros(n)
    blocks = 40
    for b in range(blocks):
        s, e = b * n // blocks, (b + 1) * n // blocks
        u = b / blocks
        fc = 2500 * math.exp(-u * 5) + 90
        out[s:e] = lp(r[max(0, s - 2048):e], fc)[-(e - s):]
    x += out * env_exp(n, 0.004, 0.22 * big) * 6.0
    x += np.sin(2 * np.pi * (35 + 60 * np.exp(-t / 0.08)) * t) * env_exp(n, 0.005, 0.25 * big) * 1.6
    # debris crackle
    for _ in range(int(40 * big)):
        d = rng.uniform(0.05, dur * 0.7)
        x += click(n, rng, d, freqs=(rng.uniform(1500, 5000),), decay=0.006, amp=rng.uniform(0.02, 0.12) * math.exp(-d * 2))
    x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * 2.0) / np.tanh(2.0)
    ir = reverb_ir(min(dur, 2.5), 0.7 * big, rng, bright=1500)
    y = x + lp(conv(x, ir), 1800) * 0.35
    return finish(y, drive=0.0, fade=0.3)


def tone(dur, f, rng, decay=None, wobble=0.0, harm=()):
    n = int(dur * SR)
    t = np.arange(n) / SR
    ph = 2 * np.pi * f * t + wobble * np.sin(2 * np.pi * 5.5 * t)
    x = np.sin(ph)
    for (k, a) in harm:
        x += a * np.sin(ph * k)
    if decay:
        x *= np.exp(-t / decay)
    return x


def gen_misc(rng):
    S = {}
    n = lambda d: int(d * SR)
    # --- reload parts
    def mag_out(heavy=1.0):
        N = n(0.35)
        x = friction(N, rng, 0.0, 0.08, 700, 3500, 0.5) + click(N, rng, 0.0, freqs=(1400 / heavy, 2600 / heavy, 4100), decay=0.02, amp=1.0)
        x += click(N, rng, 0.09, freqs=(900, 1700), decay=0.03, amp=0.4)
        return finish(x + conv(x, reverb_ir(0.3, 0.05, rng)) * 0.2)

    def mag_in(heavy=1.0):
        N = n(0.35)
        x = friction(N, rng, 0.0, 0.06, 600, 3000, 0.5) + click(N, rng, 0.06, freqs=(1200 / heavy, 2300 / heavy, 3900 / heavy), decay=0.025, amp=1.3)
        x += lp(noise(N, rng), 400) * env_exp(N, 0.001, 0.02, 0.06) * 1.2
        return finish(x + conv(x, reverb_ir(0.3, 0.05, rng)) * 0.2)

    def bolt(pull=True, heavy=1.0):
        N = n(0.4)
        x = friction(N, rng, 0.0, 0.1, 500, 3000, 0.6)
        x += click(N, rng, 0.0 if pull else 0.08, freqs=(1100 / heavy, 2100 / heavy, 3500 / heavy), decay=0.03, amp=1.2)
        if pull:
            x += tone(0.4, 900, rng, decay=0.05) * env_exp(N, 0.03, 0.05) * 0.1
        return finish(x + conv(x, reverb_ir(0.3, 0.06, rng)) * 0.2)

    S['reload/rifle_magout'] = [mag_out(), mag_out()]
    S['reload/rifle_magin'] = [mag_in(), mag_in()]
    S['reload/rifle_boltpull'] = [bolt(True)]
    S['reload/rifle_boltrelease'] = [bolt(False)]
    S['reload/pistol_magout'] = [mag_out(0.8)]
    S['reload/pistol_magin'] = [mag_in(0.8)]
    S['reload/pistol_slide'] = [bolt(False, 0.8)]
    S['reload/sniper_boltback'] = [bolt(True, 1.2)]
    S['reload/sniper_boltforward'] = [bolt(False, 1.2)]
    S['reload/shell_insert'] = [finish(friction(n(0.25), rng, 0, 0.05, 600, 2500, 0.6) + click(n(0.25), rng, 0.04, freqs=(900, 1900, 3100), decay=0.02)) for _ in range(3)]
    S['reload/pump'] = [finish(friction(n(0.45), rng, 0, 0.12, 400, 2200, 0.8) + click(n(0.45), rng, 0.0, freqs=(700, 1500, 2600), decay=0.04, amp=1.2) + click(n(0.45), rng, 0.2, freqs=(800, 1600, 2800), decay=0.04, amp=1.2))]
    S['reload/mg_cover'] = [bolt(False, 1.5)]
    S['reload/mg_box'] = [mag_in(1.6)]
    S['reload/revolver_open'] = [finish(click(n(0.3), rng, 0, freqs=(1300, 2700, 4400), decay=0.03) + friction(n(0.3), rng, 0.02, 0.1, 1500, 5000, 0.3))]
    S['reload/revolver_close'] = [finish(click(n(0.3), rng, 0.02, freqs=(1100, 2400, 3900), decay=0.035, amp=1.3))]
    S['weapon/draw'] = [finish(friction(n(0.45), rng, 0.0, 0.25, 300, 2500, 0.5) + click(n(0.45), rng, 0.22, freqs=(1400, 2800), decay=0.02, amp=0.6)) for _ in range(2)]
    S['weapon/dryfire'] = [finish(click(n(0.2), rng, 0.0, freqs=(2200, 3900, 5600), decay=0.01))]
    S['weapon/silencer_on'] = [finish(friction(n(0.9), rng, 0.0, 0.6, 1500, 6000, 0.35) + click(n(0.9), rng, 0.62, freqs=(2000, 3600), decay=0.02))]
    S['weapon/silencer_off'] = [finish(friction(n(0.9), rng, 0.0, 0.6, 1500, 6000, 0.35) + click(n(0.9), rng, 0.0, freqs=(2000, 3600), decay=0.02))]
    S['weapon/zoom'] = [finish(click(n(0.15), rng, 0.0, freqs=(3000, 5200), decay=0.006, amp=0.8) + friction(n(0.15), rng, 0.0, 0.05, 2000, 7000, 0.2))]
    S['weapon/switchmode'] = [finish(click(n(0.15), rng, 0.0, freqs=(2600, 4600), decay=0.008))]
    S['weapon/inspect'] = [finish(friction(n(0.8), rng, 0.0, 0.5, 250, 2000, 0.5))]
    # --- knife
    S['knife/deploy'] = [finish(whoosh(0.35, rng, 1500, 5000) + click(n(0.35), rng, 0.25, freqs=(3500, 6200, 8200), decay=0.05, amp=0.6))]
    S['knife/slash'] = [finish(whoosh(0.28, rng, 700 + 200 * i, 3500)) for i in range(3)]
    S['knife/stab'] = [finish(whoosh(0.35, rng, 400, 2200))]
    S['knife/hit'] = [finish(lp(noise(n(0.3), rng), 700) * env_exp(n(0.3), 0.002, 0.04) * 2 + bp(noise(n(0.3), rng), 800, 3000) * env_exp(n(0.3), 0.001, 0.02)) for _ in range(2)]
    S['knife/hitwall'] = [finish(click(n(0.4), rng, 0.0, freqs=(2400, 4100, 6900, 9100), decay=0.06, amp=1.2) + hp(noise(n(0.4), rng), 2000) * env_exp(n(0.4), 0.0003, 0.01)) for _ in range(2)]
    # --- zeus
    zt = n(0.9)
    zz = np.sign(np.sin(2 * np.pi * 60 * np.arange(zt) / SR)) * 0.3 + bp(noise(zt, rng), 2000, 9000)
    zz *= env_exp(zt, 0.001, 0.25)
    zz += hp(noise(zt, rng), 2000) * env_exp(zt, 0.0001, 0.004) * 3
    S['weapon/taser_fire'] = [finish(zz, drive=1.0)]
    # --- grenades
    S['grenade/pin'] = [finish(click(n(0.4), rng, 0.0, freqs=(3100, 5200, 7400), decay=0.06, amp=1.0) + friction(n(0.4), rng, 0.05, 0.08, 2000, 7000, 0.3))]
    S['grenade/throw'] = [finish(whoosh(0.4, rng, 300, 1400))]
    S['grenade/bounce'] = [finish(click(n(0.3), rng, 0.0, freqs=(700 * k, 1300 * k, 2400 * k), decay=0.03, amp=1.0) + lp(noise(n(0.3), rng), 600) * env_exp(n(0.3), 0.001, 0.01)) for k in (0.9, 1.0, 1.15)]
    S['grenade/he_explode'] = [explosion(2.6, rng, 1.0), explosion(2.6, rng, 1.0)]
    fb = explosion(1.8, rng, 0.6)
    fb2 = fb + tone(1.8, 2900, rng, decay=0.15) * 0.15
    S['grenade/flash_explode'] = [finish(fb2)]
    ring = tone(3.5, 3600, rng, wobble=0.4, harm=((2, 0.08),)) * np.exp(-np.arange(n(3.5)) / SR / 1.6)
    ring *= np.minimum(1, np.arange(n(3.5)) / (0.05 * SR))
    S['grenade/flash_ring'] = [finish(ring, peak=0.6, fade=0.4)]
    sm = hp(noise(n(3.0), rng), 1200) * np.minimum(1, np.arange(n(3.0)) / (0.08 * SR)) * np.exp(-np.arange(n(3.0)) / SR / 1.6)
    sm += lp(noise(n(3.0), rng), 300) * np.exp(-np.arange(n(3.0)) / SR / 0.5) * 0.6
    S['grenade/smoke_emit'] = [finish(sm, fade=0.6)]
    gl = np.zeros(n(1.2))
    for _ in range(40):
        gl += click(n(1.2), rng, rng.exponential(0.06), freqs=(rng.uniform(3000, 9000), rng.uniform(4000, 11000)), decay=rng.uniform(0.01, 0.05), amp=rng.uniform(0.1, 0.5))
    gl += hp(noise(n(1.2), rng), 2500) * env_exp(n(1.2), 0.0003, 0.03) * 1.5
    gl += lp(noise(n(1.2), rng), 500) * env_exp(n(1.2), 0.05, 0.3, 0.05) * 2.5  # ignition whoomp
    S['grenade/molotov_shatter'] = [finish(gl)]
    # inferno loop (loopable)
    L = n(2.0)
    fire = lp(noise(L + n(0.5), rng), 700) * 0.9
    for _ in range(220):
        d = rng.uniform(0, 2.4)
        fire += click(L + n(0.5), rng, d, freqs=(rng.uniform(800, 4000),), decay=0.004, amp=rng.uniform(0.05, 0.4), noise_amp=1.0)
    fire = fire[:L] * np.linspace(0, 1, L) ** 0 + 0
    xf = n(0.25)
    fire[:xf] = fire[:xf] * np.linspace(0, 1, xf) + fire[-xf:] * np.linspace(1, 0, xf)
    fire = fire[:L - xf]
    S['grenade/inferno_loop'] = [finish(fire, fade=0.0, peak=0.8)]
    S['grenade/inferno_start'] = [finish(lp(noise(n(1.0), rng), 900) * env_exp(n(1.0), 0.08, 0.3) * 2 + whoosh(1.0, rng, 200, 900))]
    ext = hp(noise(n(1.5), rng), 2500) * env_exp(n(1.5), 0.02, 0.4)
    S['grenade/inferno_extinguish'] = [finish(ext, fade=0.3)]
    # --- c4
    bl = n(0.14)
    beep = np.sign(np.sin(2 * np.pi * 2100 * np.arange(bl) / SR)) * 0.5 + np.sin(2 * np.pi * 2100 * np.arange(bl) / SR)
    beep = lp(beep, 6000) * np.minimum(1, np.minimum(np.arange(bl), bl - np.arange(bl)) / (0.004 * SR))
    S['c4/beep'] = [finish(beep, fade=0.0, peak=0.8)]
    kp = n(0.12)
    key = lp(np.sin(2 * np.pi * 1400 * np.arange(kp) / SR), 5000) * env_exp(kp, 0.002, 0.03) + click(kp, rng, 0, freqs=(3000,), decay=0.005, amp=0.3)
    S['c4/press'] = [finish(key * 0.7, fade=0.0)]
    S['c4/plant'] = [finish(click(n(0.4), rng, 0.0, freqs=(800, 1500), decay=0.04) + lp(noise(n(0.4), rng), 700) * env_exp(n(0.4), 0.002, 0.05) * 1.5)]
    S['c4/armed'] = [finish(np.concatenate([beep, np.zeros(n(0.06)), beep, np.zeros(n(0.06)), beep]), fade=0.0)]
    S['c4/defuse'] = [finish(friction(n(1.0), rng, 0, 0.8, 1500, 6000, 0.4) + click(n(1.0), rng, 0.3, freqs=(2500, 4000), decay=0.02) + click(n(1.0), rng, 0.7, freqs=(2500, 4000), decay=0.02))]
    S['c4/defused'] = [finish(np.concatenate([tone(0.15, 1500, rng), np.zeros(n(0.05)), tone(0.3, 2000, rng)]) * 0.7)]
    S['c4/explode'] = [explosion(4.0, rng, 2.2)]
    # --- impacts
    def impact(kind):
        N = n(0.45)
        if kind == 'concrete':
            x = hp(noise(N, rng), 1200) * env_exp(N, 0.0002, 0.008) * 2 + bp(noise(N, rng), 400, 2500) * env_exp(N, 0.0005, 0.03)
            for _ in range(6):
                x += click(N, rng, rng.uniform(0.01, 0.12), freqs=(rng.uniform(2000, 6000),), decay=0.004, amp=0.15)
        elif kind == 'dirt':
            x = lp(noise(N, rng), 900) * env_exp(N, 0.001, 0.04) * 2 + bp(noise(N, rng), 600, 2500) * env_exp(N, 0.002, 0.08) * 0.4
        elif kind == 'wood':
            x = modal(N, [rng.uniform(350, 500), rng.uniform(800, 1100), rng.uniform(1600, 2100)], [0.05, 0.03, 0.02], [1, 0.6, 0.3], rng) + hp(noise(N, rng), 1500) * env_exp(N, 0.0003, 0.006)
        elif kind == 'metal':
            x = modal(N, [rng.uniform(1800, 2600), rng.uniform(3900, 5200), rng.uniform(6500, 8200)], [0.25, 0.15, 0.08], [1, 0.7, 0.4], rng) + hp(noise(N, rng), 2000) * env_exp(N, 0.0002, 0.004) * 2
        elif kind == 'glass':
            x = np.zeros(N)
            for _ in range(18):
                x += click(N, rng, rng.exponential(0.03), freqs=(rng.uniform(3000, 10000),), decay=rng.uniform(0.01, 0.04), amp=rng.uniform(0.1, 0.5))
        elif kind == 'flesh':
            x = lp(noise(N, rng), 500) * env_exp(N, 0.001, 0.03) * 2 + bp(noise(N, rng), 700, 2500) * env_exp(N, 0.001, 0.015)
        elif kind == 'headshot':
            x = hp(noise(N, rng), 1500) * env_exp(N, 0.0002, 0.005) * 2 + lp(noise(N, rng), 600) * env_exp(N, 0.001, 0.05) * 2.5
        elif kind == 'helmet':
            x = modal(N, [3450, 5200, 7900, 2400], [0.12, 0.08, 0.05, 0.1], [1, 0.6, 0.4, 0.5], rng) + hp(noise(N, rng), 2500) * env_exp(N, 0.0002, 0.003) * 2
        elif kind == 'kevlar':
            x = lp(noise(N, rng), 400) * env_exp(N, 0.001, 0.035) * 2.2 + bp(noise(N, rng), 1500, 4000) * env_exp(N, 0.0005, 0.008) * 0.6
        return finish(x, fade=0.1)
    for k in ('concrete', 'dirt', 'wood', 'metal', 'glass', 'flesh', 'headshot', 'helmet', 'kevlar'):
        S['impact/' + k] = [impact(k) for _ in range(3 if k in ('concrete', 'flesh') else 2)]
    # bullet whiz (doppler crack)
    def whiz():
        N = n(0.3)
        t = np.arange(N) / SR
        x = bp(noise(N, rng), 1500, 6000) * np.exp(-((t - 0.06) ** 2) / 0.0006)
        x += np.sin(2 * np.pi * (2400 * np.exp(-t / 0.08) + 500) * t) * np.exp(-((t - 0.06) ** 2) / 0.0008) * 0.3
        return finish(x, fade=0.05)
    S['bullet/whiz'] = [whiz() for _ in range(3)]
    def shell(k=1.0):
        N = n(0.9)
        x = np.zeros(N)
        d = 0.0
        a = 1.0
        for i in range(4):
            x += click(N, rng, d, freqs=(3600 * k * rng.uniform(0.95, 1.05), 5900 * k, 8300 * k), decay=0.04 * a, amp=a, noise_amp=0.2)
            d += 0.12 * a + 0.02
            a *= 0.55
        return finish(x, peak=0.7)
    S['bullet/shell'] = [shell(), shell(1.1), shell(0.92)]
    S['bullet/shell_shotgun'] = [finish(lp(noise(n(0.5), rng), 800) * env_exp(n(0.5), 0.001, 0.03) + click(n(0.5), rng, 0.0, freqs=(900, 1700), decay=0.03, amp=0.5)) for _ in range(2)]
    S['ui/buy'] = [finish(click(n(0.2), rng, 0.0, freqs=(1800, 3600), decay=0.02, amp=0.6) + tone(0.2, 900, rng, decay=0.04) * 0.3)]
    S['player/death'] = [finish(lp(noise(n(0.6), rng), 400) * env_exp(n(0.6), 0.01, 0.12) * 2)]
    return S


def write_ogg(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, x.astype(np.float32), SR, format='OGG', subtype='VORBIS')


# attenuation distances (blocks) per event prefix
ATTEN = {
    'weapon/fire': 110, 'weapon/fire_far': 320, 'weapon/fire_sil': 40,
    'grenade/he_explode': 160, 'grenade/flash_explode': 120, 'c4/explode': 300, 'c4/beep': 48,
    'grenade/molotov_shatter': 64, 'grenade/inferno_loop': 32, 'grenade/smoke_emit': 48,
    'bullet/whiz': 12, 'bullet/shell': 10, 'impact': 24, 'knife': 20, 'reload': 18, 'weapon': 16,
}


def atten_for(event):
    best = None
    for k, v in ATTEN.items():
        if event.startswith(k) and (best is None or len(k) > len(best[0])):
            best = (k, v)
    return best[1] if best else 16


def build_all(root):
    rng = np.random.default_rng(2024)
    snd = os.path.join(root, 'assets/csarsenal/sounds')
    events = {}
    # guns
    for gid, (cls, ov) in GUNS.items():
        if cls is None:
            continue
        p = dict(GUN_BASE[cls])
        p.update({k: v for k, v in ov.items() if k != 'sil'})
        if not ov.get('sil'):
            files = []
            for v in range(3):
                q = dict(p)
                for key in ('body_lo', 'body_hi', 'body_tau', 'thump_tau'):
                    q[key] = p[key] * rng.uniform(0.94, 1.06)
                write_ogg(f'{snd}/weapon/{gid}/fire{v}.ogg', gunshot(q, rng))
                files.append(f'weapon/{gid}/fire{v}')
            events[f'weapon.{gid}.fire'] = files
            write_ogg(f'{snd}/weapon/{gid}/far.ogg', gunshot(p, rng, far=True))
            events[f'weapon.{gid}.far'] = [f'weapon/{gid}/far']
        if gid in SILENCED:
            sp = dict(p)
            sp.update(SILENCED[gid])
            files = []
            for v in range(3):
                write_ogg(f'{snd}/weapon/{gid}/sil{v}.ogg', suppressed_shot(sp, rng))
                files.append(f'weapon/{gid}/sil{v}')
            events[f'weapon.{gid}.fire_sil'] = files
    misc = gen_misc(rng)
    for key, clips in misc.items():
        files = []
        for i, x in enumerate(clips):
            write_ogg(f'{snd}/{key}{i}.ogg', x)
            files.append(f'{key}{i}')
        events[key.replace('/', '.')] = files
    # sounds.json
    sj = {}
    for ev, files in events.items():
        path_ev = ev.replace('.', '/')
        if ev.endswith('.fire'):
            att = 110
        elif ev.endswith('.far'):
            att = 320
        elif ev.endswith('.fire_sil'):
            att = 40
        else:
            att = atten_for(path_ev)
        entry = {'sounds': [{'name': 'csarsenal:' + f, 'attenuation_distance': att} for f in files]}
        if ev == 'grenade.inferno_loop':
            entry['sounds'] = [{'name': 'csarsenal:' + f, 'attenuation_distance': att, 'stream': False} for f in files]
        sj[ev] = entry
    with open(os.path.join(root, 'assets/csarsenal/sounds.json'), 'w') as fp:
        json.dump(sj, fp, indent=1, sort_keys=True)
    return sorted(sj)


if __name__ == '__main__':
    import sys
    ev = build_all(sys.argv[1])
    print(len(ev), 'events')
