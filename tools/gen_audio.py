#!/usr/bin/env python3
"""Procedural score + sound design for ZARYA.

Everything here is synthesised from scratch (oscillators, filters,
additive/FM synthesis, convolution reverb with synthetic impulse responses)
so the demo ships without any third-party audio.

Usage:  pip install numpy scipy soundfile numba
        python tools/gen_audio.py            # writes assets/music + assets/sfx
"""
import math
import os
import sys

import numpy as np
import soundfile as sf
from numba import njit
from scipy import signal

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SR = 44100
rng = np.random.default_rng(408)


def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def secs(t):
    return int(round(t * SR))


# ====================================================================== DSP
@njit(cache=True)
def _svf(x, cutoff, res, mode):
    out = np.empty_like(x)
    ic1 = 0.0
    ic2 = 0.0
    k = 2.0 - 2.0 * res
    for i in range(len(x)):
        fc = cutoff[i]
        if fc < 10.0:
            fc = 10.0
        if fc > SR * 0.45:
            fc = SR * 0.45
        g = math.tan(math.pi * fc / SR)
        a1 = 1.0 / (1.0 + g * (g + k))
        a2 = g * a1
        a3 = g * a2
        v3 = x[i] - ic2
        v1 = a1 * ic1 + a2 * v3
        v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        if mode == 0:
            out[i] = v2
        elif mode == 1:
            out[i] = v1
        else:
            out[i] = x[i] - k * v1 - v2
    return out


def svf(x, cutoff, res=0.0, mode="lp"):
    c = np.broadcast_to(np.asarray(cutoff, dtype=np.float64), x.shape).copy()
    return _svf(x.astype(np.float64), c, float(res), {"lp": 0, "bp": 1, "hp": 2}[mode])


@njit(cache=True)
def _saw(freq, phase0):
    n = len(freq)
    out = np.empty(n)
    ph = phase0
    for i in range(n):
        dt = freq[i] / SR
        v = 2.0 * ph - 1.0
        # polyBLEP
        if ph < dt:
            t = ph / dt
            v -= t + t - t * t - 1.0
        elif ph > 1.0 - dt:
            t = (ph - 1.0) / dt
            v -= t * t + t + t + 1.0
        out[i] = v
        ph += dt
        if ph >= 1.0:
            ph -= 1.0
    return out


def saw(freq, n=None, phase=None):
    f = np.broadcast_to(np.asarray(freq, dtype=np.float64), (n,) if n else np.shape(freq)).copy()
    return _saw(f, rng.uniform() if phase is None else phase)


def sine(freq, n, phase=0.0):
    f = np.broadcast_to(np.asarray(freq, dtype=np.float64), (n,))
    return np.sin(2 * np.pi * np.cumsum(f) / SR + phase)


def noise(n):
    return rng.standard_normal(n)


def pink(n):
    w = rng.standard_normal(n)
    b = [0.049922035, -0.095993537, 0.050612699, -0.004408786]
    a = [1, -2.494956002, 2.017265875, -0.522189400]
    return signal.lfilter(b, a, w) * 8


def butter(x, kind, f, order=2):
    sos = signal.butter(order, f, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x, axis=-1)


def env_adsr(n, a, d, s, r, total=None):
    """ADSR where the note is held for n samples then released over r seconds."""
    a, d, r = secs(a), secs(d), secs(r)
    held = n
    e = np.full(held + r, s, dtype=np.float64)
    a = max(a, 1)
    e[: min(a, held)] = np.linspace(0, 1, a)[: min(a, held)]
    if held > a:
        dd = min(d, held - a)
        e[a : a + dd] = np.linspace(1, s, max(d, 1))[:dd]
    last = e[held - 1] if held > 0 else 0
    e[held:] = last * np.linspace(1, 0, r) ** 2 if r > 0 else 0
    return e


def env_exp(n, tau, attack=0.002):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(attack, 1e-5)) * np.exp(-t / tau)


def pan(mono, p):
    """Equal power pan, p in [-1, 1]. Returns (2, n)."""
    a = (p + 1) * np.pi / 4
    return np.vstack([mono * np.cos(a), mono * np.sin(a)])


def stereo_ir(length, decay, predelay=0.02, lp=6000, hp=150, early=True):
    n = secs(length)
    t = np.arange(n) / SR
    irs = []
    for ch in range(2):
        ir = noise(n) * np.exp(-t / decay)
        ir = butter(ir, "lowpass", lp, 1)
        ir = butter(ir, "highpass", hp, 1)
        if early:
            for _ in range(10):
                p = rng.integers(secs(0.005), secs(0.08))
                ir[p] += rng.uniform(-1.5, 1.5) * np.exp(-p / SR / decay)
        irs.append(np.concatenate([np.zeros(secs(predelay)), ir]))
    ir = np.vstack(irs)
    ir /= np.sqrt(np.sum(ir ** 2) / 2)
    return ir


def convolve_stereo(x, ir):
    out = np.vstack([signal.fftconvolve(x[c], ir[c]) for c in range(2)])
    return out


class Track:
    """A stereo mix bus with a reverb send."""

    def __init__(self, dur):
        self.n = secs(dur)
        self.dry = np.zeros((2, self.n + secs(12)))
        self.send = np.zeros((2, self.n + secs(12)))

    def add(self, sig, t, gain=1.0, p=0.0, rev=0.3):
        if sig.ndim == 1:
            sig = pan(sig, p)
        s = secs(t)
        e = min(s + sig.shape[1], self.dry.shape[1])
        if s >= e:
            return
        self.dry[:, s:e] += sig[:, : e - s] * gain
        self.send[:, s:e] += sig[:, : e - s] * gain * rev

    def render(self, ir, rev_gain=1.0, loop=False, tail=6.0):
        wet = convolve_stereo(self.send, ir)[:, : self.dry.shape[1]]
        mix = self.dry + wet * rev_gain
        if loop:
            body = mix[:, : self.n].copy()
            spill = mix[:, self.n : self.n * 2]
            body[:, : spill.shape[1]] += spill
            return body
        return mix[:, : self.n + secs(tail)]


def master(x, rms_db=-17.0, ceiling=0.89, fade_in=0.0, fade_out=0.0):
    x = butter(x, "highpass", 25, 2)
    rms = np.sqrt(np.mean(x ** 2)) + 1e-9
    x = x * (10 ** (rms_db / 20) / rms)
    x = np.tanh(x / ceiling) * ceiling
    n = x.shape[1]
    if fade_in > 0:
        k = secs(fade_in)
        x[:, :k] *= np.linspace(0, 1, k) ** 2
    if fade_out > 0:
        k = secs(fade_out)
        x[:, n - k :] *= np.linspace(1, 0, k) ** 2
    return x


def write(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = np.ascontiguousarray((x.T if x.ndim == 2 else x).astype(np.float32))
    ch = 1 if data.ndim == 1 else data.shape[1]
    # libsndfile's vorbis encoder can crash on very large single writes: stream in blocks
    with sf.SoundFile(path, "w", SR, ch, format="OGG", subtype="VORBIS") as f:
        for i in range(0, data.shape[0], 8192):
            f.write(data[i : i + 8192])
    print(f"  {os.path.relpath(path, ROOT)}  {data.shape[0] / SR:5.1f}s")


# ================================================================ instruments
def organ(m, dur, vel=1.0, attack=1.6, release=2.5, bright=1.0):
    n = secs(dur)
    f = mtof(m)
    e = env_adsr(n, attack, 0.5, 0.85, release)
    total = len(e)
    t = np.arange(total) / SR
    out = np.zeros((2, total))
    partials = [(1, 1.0), (2, 0.55 * bright), (3, 0.28 * bright), (4, 0.22 * bright),
                (6, 0.09 * bright), (8, 0.06 * bright), (0.5, 0.35)]
    for ch in range(2):
        det = 1 + (0.0012 if ch else -0.0012)
        sig = np.zeros(total)
        for h, a in partials:
            fh = f * h * det
            if fh > 16000:
                continue
            trem = 1 + 0.04 * np.sin(2 * np.pi * (0.13 + 0.05 * h) * t + h)
            sig += a * np.sin(2 * np.pi * fh * t + rng.uniform(0, 6)) * trem
        out[ch] = sig
    return out * e * vel * 0.18


def pad(m, dur, vel=1.0, attack=2.0, release=3.0, cutoff=1400, voices=6, res=0.15, spread=0.14):
    n = secs(dur)
    e = env_adsr(n, attack, 1.0, 0.8, release)
    total = len(e)
    f = mtof(m)
    out = np.zeros((2, total))
    for v in range(voices):
        cents = (v / (voices - 1) - 0.5) * 2 * spread * 100
        fv = f * 2 ** (cents / 1200)
        drift = 1 + 0.0015 * np.sin(2 * np.pi * rng.uniform(0.05, 0.2) * np.arange(total) / SR)
        s = saw(fv * drift, total)
        out += pan(s, (v / (voices - 1) - 0.5) * 1.6)
    cut = cutoff * (0.55 + 0.45 * e)
    out = np.vstack([svf(out[c], cut, res) for c in range(2)])
    return out * e * vel * 0.09


def piano(m, dur, vel=0.8):
    f0 = mtof(m)
    n = secs(dur + 3.5)
    t = np.arange(n) / SR
    B = 0.00035
    sig = np.zeros(n)
    for k in range(1, 16):
        fk = k * f0 * math.sqrt(1 + B * k * k)
        if fk > 14000:
            break
        amp = (1 / k ** 1.25) * (0.35 + 0.65 * vel ** (0.5 + 0.25 * k))
        tau = (2.8 / (1 + 0.25 * k)) * (220 / max(f0, 80)) ** 0.35
        beat = 1 + 0.0007 * rng.uniform(-1, 1)
        sig += amp * np.exp(-t / tau) * (np.sin(2 * np.pi * fk * t) + 0.6 * np.sin(2 * np.pi * fk * beat * t))
    hammer = butter(noise(secs(0.02)), "bandpass", [800, 5000]) * np.exp(-np.arange(secs(0.02)) / (0.004 * SR))
    sig[: len(hammer)] += hammer * 0.15 * vel
    # damper at note off
    off = secs(dur)
    if off < n:
        sig[off:] *= np.exp(-np.arange(n - off) / (0.35 * SR))
    sig *= np.minimum(1, t / 0.002)
    p = np.clip((m - 60) / 30, -0.6, 0.6)
    return pan(sig * vel * 0.16, p)


def bell(m, dur=4.0, vel=0.5):
    f = mtof(m)
    n = secs(dur)
    t = np.arange(n) / SR
    idx = 2.2 * np.exp(-t / 0.6)
    mod = np.sin(2 * np.pi * f * 3.5 * t) * idx
    s = np.sin(2 * np.pi * f * t + mod) * np.exp(-t / (dur * 0.3))
    s += 0.3 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / (dur * 0.12))
    return s * vel * 0.12 * np.minimum(1, t / 0.003)


def pluck_saw(m, dur, vel=1.0, cutoff=900, env_amt=2200, decay=0.12, res=0.35):
    n = secs(dur + 0.25)
    f = mtof(m)
    s = saw(f, n) + 0.6 * saw(f * 1.004, n)
    t = np.arange(n) / SR
    fenv = np.exp(-t / decay)
    s = svf(s, cutoff + env_amt * fenv, res)
    amp = env_adsr(secs(dur), 0.003, 0.08, 0.6, 0.12)[:n]
    amp = np.pad(amp, (0, n - len(amp)))
    return s * amp * vel * 0.2


def taiko(vel=1.0, pitch=1.0):
    n = secs(1.4)
    t = np.arange(n) / SR
    f = (48 + 90 * np.exp(-t / 0.035)) * pitch
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.45)
    skin = np.sin(2 * np.pi * np.cumsum(f * 2.3) / SR) * np.exp(-t / 0.08) * 0.35
    slap = butter(noise(n), "bandpass", [200, 2200]) * np.exp(-t / 0.03) * 0.6
    s = np.tanh((body + skin + slap) * 1.6)
    return s * vel * 0.55


def kick(vel=1.0):
    n = secs(0.6)
    t = np.arange(n) / SR
    f = 42 + 110 * np.exp(-t / 0.03)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.22)
    s += butter(noise(n), "lowpass", 3000) * np.exp(-t / 0.006) * 0.3
    return np.tanh(s * 1.4) * vel * 0.5


def hat(vel=0.3, decay=0.03):
    n = secs(0.25)
    t = np.arange(n) / SR
    s = butter(noise(n), "highpass", 7000) * np.exp(-t / decay)
    return s * vel * 0.25


def tick_click(vel=0.4, freq=1800):
    n = secs(0.06)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.006) + butter(noise(n), "highpass", 4000) * np.exp(-t / 0.002)
    return s * vel * 0.4


def braam(m=26, dur=4.5, vel=1.0):
    n = secs(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for mm, a in [(m, 1.0), (m + 12, 0.8), (m + 19, 0.45), (m + 24, 0.3)]:
        for v in range(4):
            f = mtof(mm) * 2 ** ((v - 1.5) * 9 / 1200)
            out += pan(saw(f, n) * a, (v - 1.5) / 2)
    cut = 180 + 2600 * np.exp(-t / 0.35) + 300 * np.exp(-t / 2)
    out = np.vstack([svf(out[c], cut, 0.25) for c in range(2)])
    out = np.tanh(out * 1.8)
    amp = np.minimum(1, t / 0.02) * np.exp(-t / (dur * 0.35))
    sub = np.sin(2 * np.pi * mtof(m - 12) * t) * np.exp(-t / 1.5) * 0.9
    return (out * amp + sub * amp) * vel * 0.25


def string_note(m, dur, vel=1.0, attack=0.5, release=1.0, cutoff=2400):
    n = secs(dur)
    e = env_adsr(n, attack, 0.2, 0.9, release)
    total = len(e)
    t = np.arange(total) / SR
    out = np.zeros((2, total))
    f = mtof(m)
    for v in range(5):
        vib = 1 + 0.004 * np.sin(2 * np.pi * (5.2 + v * 0.2) * t + v) * np.minimum(1, t / 0.8)
        fv = f * 2 ** ((v - 2) * 7 / 1200) * vib
        out += pan(saw(fv, total), (v - 2) / 2.5)
    out = np.vstack([svf(out[c], cutoff * (0.6 + 0.4 * e), 0.1) for c in range(2)])
    out = np.vstack([svf(out[c], 250.0, 0.0, "hp") for c in range(2)])
    return out * e * vel * 0.07


def swell_noise(dur, f0=300, f1=6000, vel=0.3):
    n = secs(dur)
    t = np.arange(n) / SR
    k = t / dur
    s = svf(noise(n), f0 * (f1 / f0) ** k, 0.5, "bp")
    return s * (k ** 2) * vel


# ===================================================================== music
def mus_intro():
    dur = 64.0
    tr = Track(dur)
    chords = [
        [38, 45, 50, 53, 64, 69],  # Dm(add9)
        [34, 41, 50, 57, 64],      # Bbmaj7#11
        [33, 40, 48, 53, 57, 64],  # Fmaj7/A
        [36, 43, 50, 55, 62],      # Csus2
    ]
    for rep in range(2):
        for ci, ch in enumerate(chords):
            t0 = rep * 32 + ci * 8
            for m in ch:
                tr.add(organ(m, 8.6, vel=0.9 if m > 40 else 1.1, attack=2.2, release=3.0), t0, rev=0.55)
            if rep == 1:
                tr.add(pad(ch[-1] + 12, 8.0, vel=0.35, cutoff=2200, attack=3), t0, rev=0.7)
    # shimmering bells (sparse, gentle)
    scale = [62, 64, 65, 69, 72, 74, 76, 81]
    t = 10.0
    while t < dur - 4:
        m = scale[rng.integers(len(scale))]
        tr.add(bell(m, 5, vel=rng.uniform(0.25, 0.45)), t, p=rng.uniform(-0.7, 0.7), rev=0.8)
        tr.add(bell(m, 5, vel=0.12), t + 0.42, p=rng.uniform(-0.7, 0.7), rev=0.9)
        t += rng.uniform(1.2, 2.6)
    tr.add(sine(mtof(26), secs(dur)) * 0.12 * np.minimum(1, np.arange(secs(dur)) / secs(6)), 0, rev=0.1)
    ir = stereo_ir(7.0, 1.9, 0.03, lp=5500)
    x = tr.render(ir, 0.9, loop=True)
    return master(x, -19.0)


def mus_title():
    dur = 32.0
    tr = Track(dur)
    for m, v in [(26, 1.0), (33, 0.8), (38, 0.5), (45, 0.35)]:
        tr.add(organ(m, dur + 2, vel=v, attack=5, release=4, bright=0.6), 0, rev=0.6)
    t = 3.0
    scale = [69, 72, 74, 76, 81, 86]
    while t < dur - 3:
        m = scale[rng.integers(len(scale))]
        tr.add(bell(m, 6, 0.25), t, p=rng.uniform(-0.8, 0.8), rev=0.9)
        t += rng.uniform(2.5, 4.5)
    tr.add(swell_noise(dur, 200, 900, 0.03), 0, rev=0.5)
    ir = stereo_ir(8.0, 2.2, 0.04, lp=5000)
    return master(tr.render(ir, 1.0, loop=True), -21.0)


def mus_tension():
    bpm = 110
    beat = 60 / bpm
    bars = 16
    dur = bars * 4 * beat
    tr = Track(dur)
    pattern = [[38, 38, 50, 38], [38, 38, 41, 38], [38, 38, 50, 38], [39, 38, 36, 38]]
    for bar in range(bars):
        phase = 0.5 - 0.5 * math.cos(2 * math.pi * bar / bars)
        for b in range(4):
            for s16 in range(4):
                t = (bar * 4 + b) * beat + s16 * beat / 4
                m = pattern[bar % 4][s16] if s16 != 0 or b % 2 == 0 else 38
                vel = 1.0 if s16 == 0 else 0.7
                tr.add(pluck_saw(m, beat / 4 * 0.9, vel, cutoff=250 + 700 * phase, env_amt=900 + 1500 * phase),
                       t, rev=0.12)
            tr.add(tick_click(0.25 if b % 2 else 0.4, 2400), (bar * 4 + b) * beat + beat / 2, p=0.5 if b % 2 else -0.5, rev=0.3)
        # heartbeat kick
        tr.add(kick(0.9), bar * 4 * beat, rev=0.1)
        tr.add(kick(0.6), bar * 4 * beat + beat * 0.45, rev=0.1)
        if bar % 4 == 0:
            for m in [50, 57, 63]:
                tr.add(string_note(m, 4 * 4 * beat - 0.5, 0.7, attack=3.0, release=2.0, cutoff=1800), bar * 4 * beat, rev=0.5)
        if bar % 4 == 3:
            tr.add(swell_noise(4 * beat, 400, 7000, 0.12), bar * 4 * beat, rev=0.4)
    ir = stereo_ir(3.5, 0.9, 0.02)
    return master(tr.render(ir, 0.8, loop=True), -16.5)


def mus_action():
    bpm = 120
    beat = 60 / bpm
    bars = 16
    dur = bars * 4 * beat
    tr = Track(dur)
    ost = [50, 50, 57, 50, 53, 50, 52, 50]
    roots = [26, 22, 24, 21]
    taiko_pat = [(0, 1.0, 1.0), (1.75, 0.6, 1.2), (2.5, 0.8, 1.0), (3.0, 0.7, 1.3), (3.5, 0.5, 1.3), (3.75, 0.6, 1.3)]
    for bar in range(bars):
        t0 = bar * 4 * beat
        for i in range(16):
            m = ost[i % 8] + (12 if bar >= 8 and i % 4 == 2 else 0)
            tr.add(pluck_saw(m, beat / 4 * 0.8, 0.8, cutoff=700, env_amt=2600, decay=0.07), t0 + i * beat / 4, p=-0.3, rev=0.15)
            tr.add(hat(0.5 if i % 2 else 0.25, 0.02 if i % 2 else 0.05), t0 + i * beat / 4, p=0.4, rev=0.1)
        for pos, v, pt in taiko_pat:
            tr.add(taiko(v, pt), t0 + pos * beat, p=rng.uniform(-0.3, 0.3), rev=0.35)
        if bar % 4 == 0:
            r = roots[(bar // 4) % 4]
            for m in [r + 12, r + 24, r + 31]:
                tr.add(string_note(m, 4 * 4 * beat - 0.3, 0.9, attack=0.4, release=0.6, cutoff=2600), t0, rev=0.4)
            tr.add(braam(r, 4.5, 1.0 if bar % 8 == 0 else 0.7), t0, rev=0.5)
        if bar % 8 == 7:
            for k in range(8):
                tr.add(taiko(0.4 + k * 0.08, 1.4), t0 + 2 * beat + k * beat / 4, rev=0.35)
    ir = stereo_ir(3.0, 0.8, 0.02)
    return master(tr.render(ir, 0.7, loop=True), -15.0)


def mus_climax():
    bpm = 100
    beat = 60 / bpm
    bars = 16
    dur = bars * 4 * beat
    tr = Track(dur)
    prog = [[38, 50, 53, 57, 62], [34, 50, 53, 58, 62], [31, 50, 55, 58, 62], [33, 49, 52, 57, 61]]
    for ci, ch in enumerate(prog):
        t0 = ci * 4 * 4 * beat
        for m in ch:
            tr.add(organ(m, 4 * 4 * beat + 0.4, vel=0.8 + 0.1 * ci, attack=1.2, release=1.5), t0, rev=0.45)
            if ci >= 2:
                tr.add(organ(m + 12, 4 * 4 * beat, vel=0.35, attack=2.0, release=1.5), t0, rev=0.5)
    for bar in range(bars):
        t0 = bar * 4 * beat
        root = prog[bar // 4][0]
        for i in range(8):
            tr.add(pluck_saw(root + 12, beat / 2 * 0.7, 0.7, cutoff=400, env_amt=1600, decay=0.09), t0 + i * beat / 2, rev=0.2)
        for b in range(4):
            tr.add(tick_click(0.55, 1500), t0 + b * beat, p=0.0, rev=0.4)
        tr.add(taiko(0.8, 0.9), t0, rev=0.4)
        if bar % 2 == 1:
            tr.add(taiko(0.5, 1.1), t0 + 2.5 * beat, rev=0.4)
    ir = stereo_ir(5.0, 1.4, 0.03)
    return master(tr.render(ir, 0.8, loop=True), -16.0)


def mus_sad():
    bpm = 64
    beat = 60 / bpm
    tr = Track(60.0)
    chords = [[38, 50, 53, 57], [34, 50, 53, 58], [29, 48, 53, 57], [36, 48, 52, 55],
              [38, 50, 53, 57], [31, 50, 55, 58], [34, 50, 53, 58], [33, 49, 52, 57]]
    cl = 7.0
    for i, ch in enumerate(chords):
        for m in ch:
            tr.add(string_note(m, cl + 0.5, 0.75, attack=2.0, release=2.5, cutoff=1500), i * cl, rev=0.6)
        tr.add(piano(ch[0] + 12, 3.0, 0.45), i * cl, rev=0.5)
        tr.add(piano(ch[0] + 24, 3.0, 0.3), i * cl + 1.5 * beat, rev=0.5)
    mel = [
        (0.0, 69, 1.5), (1.5, 67, 0.5), (2.0, 65, 1.0), (3.0, 64, 1.0), (4.0, 62, 3.0),
        (7.5, 65, 1.0), (8.5, 64, 1.0), (9.5, 62, 1.0), (10.5, 60, 1.0), (11.5, 62, 3.5),
        (15.0, 69, 1.5), (16.5, 70, 0.5), (17.0, 69, 1.0), (18.0, 67, 1.0), (19.0, 65, 2.0), (21.0, 64, 2.0),
        (23.0, 62, 1.0), (24.0, 64, 1.0), (25.0, 65, 1.5), (26.5, 64, 0.5), (27.0, 62, 1.0), (28.0, 61, 1.0),
        (29.0, 62, 2.0), (31.0, 64, 1.0), (32.0, 65, 1.0), (33.0, 69, 3.0),
        (37.0, 67, 1.0), (38.0, 65, 1.0), (39.0, 64, 1.0), (40.0, 62, 1.0), (41.0, 61, 2.0), (43.0, 62, 6.0),
    ]
    for b, m, d in mel:
        tr.add(piano(m, d * beat, 0.62), 1.0 + b * beat, rev=0.45)
    ir = stereo_ir(6.0, 1.7, 0.03, lp=5000)
    return master(tr.render(ir, 0.9, tail=6), -19.0, fade_out=4.0)


def mus_hope():
    bpm = 72
    beat = 60 / bpm
    bar = 4 * beat
    tr = Track(52.0)
    prog = [
        [38, 50, 54, 57, 62], [38, 50, 54, 57, 62],
        [37, 49, 52, 57, 64], [35, 50, 54, 59, 62], [31, 50, 55, 59, 62],
        [30, 50, 54, 57, 62], [31, 47, 55, 59, 62], [33, 49, 52, 57, 64],
        [38, 50, 54, 57, 62, 66, 69],
    ]
    for i, ch in enumerate(prog):
        t0 = i * bar
        last = i == len(prog) - 1
        d = bar * (3.0 if last else 1.0) + 0.4
        for m in ch:
            tr.add(organ(m, d, vel=0.7 + 0.04 * i, attack=1.5 if i < 2 else 0.8, release=3.0 if last else 1.2), t0, rev=0.5)
            if i >= 4:
                tr.add(string_note(m + 12, d, 0.5 + 0.05 * i, attack=1.0, release=2.5, cutoff=2600), t0, rev=0.55)
        arp = sorted(ch[1:])
        if not last:
            for k in range(8):
                m = arp[k % len(arp)] + 12
                tr.add(piano(m, beat * 0.6, 0.35 + 0.02 * i), t0 + k * beat / 2, rev=0.5)
        else:
            tr.add(swell_noise(2.0, 300, 5000, 0.05), t0 - 2.0, rev=0.5)
            tr.add(taiko(0.9, 0.8), t0, rev=0.6)
            for m in [74, 78, 81, 86]:
                tr.add(bell(m, 7, 0.35), t0 + 0.5 + rng.uniform(0, 2), p=rng.uniform(-0.6, 0.6), rev=0.8)
    ir = stereo_ir(6.5, 1.8, 0.03, lp=6000)
    return master(tr.render(ir, 0.9, tail=6), -17.0, fade_out=5.0)


def mus_tragic():
    bpm = 58
    beat = 60 / bpm
    bar = 4 * beat
    tr = Track(50.0)
    prog = [[38, 45, 50], [34, 41, 50], [31, 43, 50], [33, 45, 49], [38, 45, 50], [34, 41, 53], [33, 40, 49], [26, 38, 45]]
    for i, ch in enumerate(prog):
        t0 = i * bar * 0.75
        for m in ch:
            tr.add(organ(m, bar * 0.75 + 0.5, vel=0.55, attack=2.0, release=2.5, bright=0.5), t0, rev=0.6)
        tr.add(piano(ch[0] + 12, 2.5, 0.35), t0, rev=0.6)
    mel = [(1, 74, 2), (3, 72, 1), (4, 70, 2), (6, 69, 3), (10, 69, 1), (11, 70, 1), (12, 69, 1), (13, 67, 1),
           (14, 65, 2), (16, 64, 3), (19.5, 62, 1), (20.5, 64, 1), (21.5, 65, 2), (23.5, 64, 1), (24.5, 61, 3), (28, 62, 6)]
    for b, m, d in mel:
        tr.add(piano(m, d * beat, 0.55), b * beat, rev=0.55)
    tr.add(sine(mtof(26), secs(48)) * 0.1 * np.minimum(1, np.arange(secs(48)) / secs(8)), 0, rev=0.1)
    ir = stereo_ir(7.0, 2.0, 0.04, lp=4500)
    return master(tr.render(ir, 1.0, tail=4), -19.5, fade_out=6.0)


# ======================================================================= sfx
def fold_loop(x, xfade=0.5):
    """Make a mono/stereo buffer loop seamlessly by crossfading its tail into its head."""
    k = secs(xfade)
    x = np.atleast_2d(x).astype(np.float64)
    head = x[:, :k]
    tail = x[:, -k:]
    w = np.linspace(0, 1, k)
    x = x[:, : x.shape[1] - k].copy()
    x[:, :k] = tail * (1 - w) + head * w
    return x


def fit(x, n):
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def norm(x, peak=0.9):
    return x * (peak / (np.max(np.abs(x)) + 1e-9))


def lfo_noise(n, rate, smooth=True):
    k = max(4, int(n / SR * rate) + 4)
    pts = rng.uniform(-1, 1, k)
    xs = np.linspace(0, n, k)
    return np.interp(np.arange(n), xs, pts)


def sfx_heartbeat():
    n = secs(0.9)
    x = np.zeros(n)
    for t0, v in [(0.0, 1.0), (0.26, 0.7)]:
        m = secs(0.35)
        t = np.arange(m) / SR
        f = 38 + 30 * np.exp(-t / 0.03)
        s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.09) * np.minimum(1, t / 0.004)
        s += butter(noise(m), "lowpass", 180) * np.exp(-t / 0.05) * 0.4
        x[secs(t0) : secs(t0) + m] += s * v
    return norm(np.tanh(x * 1.5), 0.95)


def breath(n_cycles, dur):
    n = secs(dur)
    x = np.zeros(n)
    cyc = dur / n_cycles
    for c in range(n_cycles):
        t0 = c * cyc + rng.uniform(0, 0.1)
        ins = cyc * 0.38
        exh = cyc * 0.45
        for start, length, lo, hi, v in [(t0, ins, 700, 2600, 0.6), (t0 + ins + cyc * 0.05, exh, 350, 1700, 1.0)]:
            m = secs(length)
            t = np.arange(m) / SR
            e = np.sin(np.pi * t / length) ** 1.5
            cut = lo + (hi - lo) * e
            s = svf(noise(m), cut, 0.3, "bp")
            s = svf(s, 1200.0, 0.4, "bp") * 0.4 + s
            a = secs(start)
            x[a : a + m] += s[: max(0, min(m, n - a))] * e[: max(0, min(m, n - a))] * v
    return norm(x, 0.7)


def amb_station():
    dur = 10.5
    n = secs(dur)
    t = np.arange(n) / SR
    x = 0.25 * np.sin(2 * np.pi * 50 * t) + 0.12 * np.sin(2 * np.pi * 100 * t) + 0.05 * np.sin(2 * np.pi * 150 * t)
    fan = butter(pink(n), "lowpass", 2500) * 0.25
    fan += svf(noise(n), 420.0, 0.8, "bp") * 0.05
    whine = 0.008 * np.sin(2 * np.pi * 6200 * t)
    y = x + fan + whine
    st = np.vstack([y, np.roll(y, secs(0.013))])
    return norm(fold_loop(st, 0.5), 0.6)


def amb_suit():
    dur = 10.5
    n = secs(dur)
    t = np.arange(n) / SR
    fan = butter(pink(n), "bandpass", [80, 1400]) * 0.3
    fan += 0.06 * np.sin(2 * np.pi * 117 * t) * (1 + 0.2 * np.sin(2 * np.pi * 0.3 * t))
    st = np.vstack([fan, np.roll(fan, secs(0.02))])
    return norm(fold_loop(st, 0.5), 0.5)


def amb_alarm():
    period = 1.6
    n = secs(period)
    t = np.arange(n) / SR
    whoop = secs(0.9)
    tw = t[:whoop]
    f = 520 + 520 * (tw / 0.9) ** 0.8
    s = np.zeros(n)
    tone_ = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.5 + np.sin(2 * np.pi * np.cumsum(f * 2) / SR) * 0.3
    s[:whoop] = tone_ * np.minimum(1, tw / 0.02) * np.minimum(1, (0.9 - tw) / 0.05)
    s = butter(s, "lowpass", 3200)
    s = butter(s, "highpass", 300)
    ir = stereo_ir(0.9, 0.2, 0.01, lp=4000)[0]
    w = signal.fftconvolve(s, ir)
    out = w[:n].copy()
    spill = w[n : 2 * n]
    out[: len(spill)] += spill
    s = out
    return norm(s, 0.8)


def amb_master_alarm():
    period = 1.5
    n = secs(period)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for k in range(3):
        a = secs(k * 0.3)
        m = secs(0.15)
        tt = np.arange(m) / SR
        s[a : a + m] += (np.sin(2 * np.pi * 1320 * tt) + 0.3 * np.sin(2 * np.pi * 2640 * tt)) * np.minimum(1, tt / 0.004) * np.minimum(1, (0.15 - tt) / 0.01)
    return norm(s, 0.7)


def amb_hiss():
    dur = 6.5
    n = secs(dur)
    s = butter(noise(n), "highpass", 1400) * (0.75 + 0.25 * lfo_noise(n, 3))
    s += svf(noise(n), 3200 + 800 * lfo_noise(n, 1.5), 0.7, "bp") * 0.3
    st = np.vstack([s, np.roll(s, secs(0.01))])
    return norm(fold_loop(st, 0.5), 0.6)


def amb_thruster():
    dur = 6.5
    n = secs(dur)
    t = np.arange(n) / SR
    r = butter(noise(n), "lowpass", 180, 4) * 2.5
    r *= 1 + 0.3 * np.sin(2 * np.pi * 11 * t)
    r += 0.3 * np.sin(2 * np.pi * 38 * t + 2 * np.sin(2 * np.pi * 0.7 * t))
    cr = np.zeros(n)
    idx = rng.integers(0, n, size=int(dur * 60))
    cr[idx] = rng.uniform(-1, 1, len(idx))
    cr = butter(cr, "bandpass", [300, 2500]) * 0.6
    s = np.tanh(r + cr)
    st = np.vstack([s, np.roll(s, secs(0.017))])
    return norm(fold_loop(st, 0.5), 0.85)


def amb_reentry():
    dur = 6.5
    n = secs(dur)
    t = np.arange(n) / SR
    roar = svf(noise(n), 500 + 300 * lfo_noise(n, 2), 0.2) * 1.5
    roar += butter(noise(n), "lowpass", 90, 4) * 3
    roar *= 1 + 0.25 * lfo_noise(n, 8)
    cr = np.zeros(n)
    idx = rng.integers(0, n, size=int(dur * 120))
    cr[idx] = rng.uniform(-1, 1, len(idx))
    cr = butter(cr, "bandpass", [600, 5000]) * 0.8
    s = np.tanh(roar + cr)
    st = np.vstack([s, np.roll(s, secs(0.021))])
    return norm(fold_loop(st, 0.5), 0.85)


def amb_ocean():
    dur = 12.5
    n = secs(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for ch in range(2):
        swell = 0.55 + 0.45 * np.sin(2 * np.pi * t / 6.25 + ch * 1.3) ** 2
        body = svf(pink(n), 400 + 1400 * swell, 0.0) * swell
        foam = butter(noise(n), "highpass", 2500) * np.maximum(0, np.sin(2 * np.pi * t / 6.25 + ch * 1.3 - 0.6)) ** 4 * 0.5
        out[ch] = body * 0.5 + foam
    return norm(fold_loop(out, 0.8), 0.6)


def amb_wind():
    dur = 8.5
    n = secs(dur)
    out = np.zeros((2, n))
    for ch in range(2):
        out[ch] = svf(noise(n), 500 + 400 * lfo_noise(n, 0.8), 0.6, "bp") * (0.6 + 0.4 * lfo_noise(n, 0.5))
    return norm(fold_loop(out, 0.8), 0.5)


def amb_static():
    dur = 4.5
    n = secs(dur)
    s = butter(noise(n), "bandpass", [300, 6000]) * 0.4
    cr = np.zeros(n)
    idx = rng.integers(0, n, size=int(dur * 40))
    cr[idx] = rng.uniform(-1, 1, len(idx))
    s += butter(cr, "highpass", 1000)
    return norm(fold_loop(s, 0.3)[0], 0.5)


def impact(size=1.0, seed=0):
    r = np.random.default_rng(seed)
    n = secs(2.2)
    t = np.arange(n) / SR
    f = (70 + 60 * np.exp(-t / 0.04)) / size
    thump = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (0.18 * size))
    ring = np.zeros(n)
    for _ in range(7):
        fr = r.uniform(180, 2400) / size ** 0.5
        ring += r.uniform(0.2, 1) * np.sin(2 * np.pi * fr * t + r.uniform(0, 6)) * np.exp(-t / r.uniform(0.15, 0.9))
    crunch = butter(noise(n), "bandpass", [300, 4000]) * np.exp(-t / 0.03)
    s = thump * 1.2 + ring * 0.25 + crunch * 0.6
    s = butter(s, "lowpass", 4500)
    s = np.tanh(s * 1.5)
    return norm(s, 0.88)


def sfx_impact_big():
    s = impact(1.6, 11)
    n = len(s)
    rattle = np.zeros(n)
    for _ in range(60):
        p = int(abs(rng.normal(0.25, 0.3)) * SR)
        if p + 200 < n:
            m = secs(0.02)
            tt = np.arange(m) / SR
            rattle[p : p + m] += np.sin(2 * np.pi * rng.uniform(900, 4000) * tt) * np.exp(-tt / 0.004) * rng.uniform(0.1, 0.4)
    return norm(s + rattle, 0.95)


def sfx_whoosh(dur=0.9):
    n = secs(dur)
    t = np.arange(n) / SR
    k = t / dur
    e = np.sin(np.pi * k) ** 2
    s = svf(noise(n), 300 + 3500 * e, 0.5, "bp") * e
    l = s * (1 - k)
    r = s * k
    return norm(np.vstack([l, r]), 0.8)


def sfx_crack():
    n = secs(1.2)
    t = np.arange(n) / SR
    s = butter(noise(n), "highpass", 1500) * np.exp(-t / 0.01)
    for _ in range(40):
        p = int(abs(rng.normal(0.0, 0.18)) * SR)
        m = secs(0.01)
        if p + m < n:
            tt = np.arange(m) / SR
            s[p : p + m] += butter(noise(m), "highpass", 2500) * np.exp(-tt / 0.002) * rng.uniform(0.2, 0.7)
    for _ in range(6):
        p = int(rng.uniform(0.05, 0.6) * SR)
        m = secs(0.5)
        if p + m < n:
            tt = np.arange(m) / SR
            s[p : p + m] += np.sin(2 * np.pi * rng.uniform(3500, 7000) * tt) * np.exp(-tt / 0.08) * 0.1
    return norm(s, 0.9)


def sfx_sparks():
    n = secs(1.6)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for _ in range(90):
        p = int(abs(rng.normal(0.3, 0.35)) * SR)
        m = secs(0.015)
        if p + m < n:
            tt = np.arange(m) / SR
            s[p : p + m] += butter(noise(m), "bandpass", [2000, 9000]) * np.exp(-tt / 0.003) * rng.uniform(0.3, 1)
    buzz = np.sign(np.sin(2 * np.pi * 100 * t)) * 0.08 * np.exp(-t / 0.6)
    return norm(s + butter(buzz, "lowpass", 2000), 0.85)


def sfx_hatch():
    n = secs(2.5)
    t = np.arange(n) / SR
    s = fit(impact(1.3, 5), n) * 0.9
    for k, tt0 in enumerate([0.35, 0.5]):
        c = tick_click(0.9, 900 + k * 300)
        a = secs(tt0)
        s[a : a + len(c)] += c
    h0 = secs(0.6)
    hiss = butter(noise(n - h0), "highpass", 1200) * np.exp(-np.arange(n - h0) / SR / 0.5) * 0.4
    s[h0:] += hiss
    return norm(s, 0.95)


def sfx_metal_groan():
    dur = 3.2
    n = secs(dur)
    t = np.arange(n) / SR
    f = 55 + 25 * np.sin(2 * np.pi * 0.35 * t) + 8 * lfo_noise(n, 6)
    stick = saw(f, n) * (0.6 + 0.4 * np.abs(lfo_noise(n, 25)))
    s = svf(stick, 350 + 250 * np.sin(2 * np.pi * 0.5 * t), 0.85, "bp")
    s += svf(stick, 1200.0, 0.9, "bp") * 0.3
    e = np.sin(np.pi * t / dur) ** 0.7
    return norm(s * e, 0.8)


def sfx_undock():
    n = secs(3.0)
    s = np.zeros(n)
    for k, tt in enumerate([0.0, 0.18, 0.36, 0.54]):
        c = fit(impact(0.7 + 0.1 * k, 20 + k), n) * 0.6
        a = secs(tt)
        s[a:] += c[: n - a]
    p = secs(1.0)
    m = n - p
    tt = np.arange(m) / SR
    puff = butter(noise(m), "lowpass", 900) * np.minimum(1, tt / 0.05) * np.exp(-tt / 0.6)
    s[p:] += puff * 0.8
    return norm(s, 0.95)


def sfx_parachute():
    n = secs(3.0)
    t = np.arange(n) / SR
    whomp = butter(noise(n), "lowpass", 400) * np.exp(-t / 0.25) * 2
    whomp += np.sin(2 * np.pi * np.cumsum(60 + 40 * np.exp(-t / 0.1)) / SR) * np.exp(-t / 0.4)
    flutter = butter(noise(n), "bandpass", [200, 1800]) * (0.5 + 0.5 * np.sin(2 * np.pi * 14 * t)) * np.exp(-t / 1.2) * 0.4
    return norm(np.tanh(whomp + flutter), 0.9)


def sfx_riser(dur=4.0):
    n = secs(dur)
    t = np.arange(n) / SR
    k = t / dur
    f = 90 * (10 ** (k * 1.1))
    s = saw(f, n) * 0.4 + saw(f * 1.5, n) * 0.25
    s = svf(s, 300 + 5000 * k ** 2, 0.4)
    s += svf(noise(n), 500 + 8000 * k ** 2, 0.5, "bp") * 0.5
    s *= k ** 2.2
    st = np.vstack([s, np.roll(s, secs(0.012))])
    return norm(st, 0.85)


def sfx_hit():
    n = secs(4.0)
    t = np.arange(n) / SR
    f = 28 + 70 * np.exp(-t / 0.08)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 1.1)
    s += butter(noise(n), "lowpass", 1500) * np.exp(-t / 0.08) * 0.6
    s = np.tanh(s * 1.8)
    ir = stereo_ir(3.0, 0.8)
    wet = convolve_stereo(np.vstack([s, s]), ir)[:, :n] * 0.25
    return norm(np.vstack([s, s]) + wet, 0.95)


def sfx_braam():
    return norm(braam(26, 5.0, 1.0), 0.95)


def sfx_tick():
    return norm(tick_click(1.0, 2200), 0.6)


def sfx_select():
    s = np.pad(bell(81, 1.5, 1.0), (0, 0)) + np.pad(bell(88, 1.5, 0.7), (secs(0.07), 0))[: secs(1.5)]
    n = len(s)
    t = np.arange(n) / SR
    s += np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.2) * 0.5
    return norm(s, 0.8)


def sfx_qte_ok():
    s = np.zeros(secs(1.0))
    for k, m in enumerate([76, 83, 88]):
        b = bell(m, 0.9, 0.8)
        a = secs(k * 0.06)
        s[a : a + len(b)] += b[: len(s) - a]
    return norm(s, 0.8)


def sfx_qte_fail():
    n = secs(0.8)
    t = np.arange(n) / SR
    s = np.sign(np.sin(2 * np.pi * 98 * t)) + np.sign(np.sin(2 * np.pi * 104 * t))
    s = butter(s, "lowpass", 1400) * np.exp(-t / 0.3)
    s += np.sin(2 * np.pi * 49 * t) * np.exp(-t / 0.35)
    return norm(s, 0.8)


def sfx_ui():
    return norm(tick_click(0.6, 3200), 0.4)


def sfx_suit_warn():
    n = secs(0.8)
    s = np.zeros(n)
    for k in range(2):
        m = secs(0.12)
        tt = np.arange(m) / SR
        a = secs(k * 0.22)
        s[a : a + m] += np.sin(2 * np.pi * 2200 * tt) * np.minimum(1, tt / 0.003) * np.minimum(1, (0.12 - tt) / 0.01)
    return norm(s, 0.6)


def sfx_valve():
    dur = 2.2
    n = secs(dur)
    t = np.arange(n) / SR
    f = 700 + 500 * t / dur + 60 * lfo_noise(n, 20)
    s = svf(saw(f, n) * np.abs(lfo_noise(n, 40)), f * 1.5, 0.9, "bp") * 0.5
    for k in range(int(dur * 7)):
        c = tick_click(0.8, 700)
        a = secs(k / 7)
        s[a : a + len(c)] += c[: n - a]
    return norm(s * np.minimum(1, t / 0.05), 0.85)


def sfx_explosion():
    s = impact(2.2, 42)
    n = secs(4.0)
    s = np.pad(s, (0, max(0, n - len(s))))[:n]
    t = np.arange(n) / SR
    s += butter(noise(n), "lowpass", 300) * np.exp(-t / 0.8) * 0.8
    return norm(np.tanh(s * 1.3), 0.88)


def sfx_glitch():
    n = secs(0.6)
    s = np.zeros(n)
    pos = 0
    while pos < n:
        m = int(rng.uniform(0.01, 0.06) * SR)
        kind = rng.integers(3)
        tt = np.arange(m) / SR
        if kind == 0:
            seg = np.sign(np.sin(2 * np.pi * rng.uniform(200, 3000) * tt))
        elif kind == 1:
            seg = np.round(noise(m) * 3) / 3
        else:
            seg = np.zeros(m)
        s[pos : pos + m] = seg[: n - pos] * rng.uniform(0.3, 1)
        pos += m
    return norm(butter(s, "lowpass", 8000), 0.6)


def sfx_title():
    n = secs(6.0)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * np.cumsum(30 + 50 * np.exp(-t / 0.15)) / SR) * np.exp(-t / 1.8)
    s = np.tanh(s * 1.5)
    sh = np.zeros(n)
    for m in [74, 81, 86, 90]:
        b = bell(m, 5.5, 0.5)
        sh[: len(b)] += b[:n]
    st = np.vstack([s + sh * 0.6, s + np.roll(sh, secs(0.02)) * 0.6])
    ir = stereo_ir(5.0, 1.6)
    wet = convolve_stereo(st, ir)[:, :n]
    return norm(st + wet * 0.3, 0.95)


def sfx_tether():
    n = secs(1.2)
    t = np.arange(n) / SR
    f = 180 * np.exp(-t / 0.4) + 60
    s = saw(f, n) * np.exp(-t / 0.3)
    s = svf(s, 1500.0, 0.6)
    s += butter(noise(n), "highpass", 2000) * np.exp(-t / 0.01) * 0.8
    return norm(s, 0.85)


def sfx_grab():
    return norm(impact(0.5, 77)[: secs(0.8)] * np.exp(-np.arange(secs(0.8)) / SR / 0.2), 0.7)


def sfx_splash():
    n = secs(3.0)
    t = np.arange(n) / SR
    s = butter(noise(n), "lowpass", 1800) * np.exp(-t / 0.5) * np.minimum(1, t / 0.01)
    s += np.sin(2 * np.pi * np.cumsum(50 + 60 * np.exp(-t / 0.05)) / SR) * np.exp(-t / 0.3)
    for _ in range(30):
        p = int(rng.uniform(0.2, 2.0) * SR)
        m = secs(0.04)
        tt = np.arange(m) / SR
        if p + m < n:
            s[p : p + m] += np.sin(2 * np.pi * (rng.uniform(400, 1400) + 3000 * tt) * tt) * np.exp(-tt / 0.01) * 0.15
    return norm(s, 0.9)


def sfx_heartbeat_fast():
    return sfx_heartbeat()


def sfx_pulse_low():
    n = secs(1.2)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * 45 * t) * np.exp(-t / 0.35) * np.minimum(1, t / 0.01)
    return norm(s, 0.9)


# ====================================================================== main
MUSIC = {
    "mus_title": mus_title,
    "mus_intro": mus_intro,
    "mus_tension": mus_tension,
    "mus_action": mus_action,
    "mus_climax": mus_climax,
    "mus_sad": mus_sad,
    "mus_hope": mus_hope,
    "mus_tragic": mus_tragic,
}

SFX = {
    "heartbeat": sfx_heartbeat,
    "impact1": lambda: impact(1.0, 1),
    "impact2": lambda: impact(0.8, 2),
    "impact3": lambda: impact(1.25, 3),
    "impact_big": sfx_impact_big,
    "whoosh": sfx_whoosh,
    "whoosh_short": lambda: sfx_whoosh(0.45),
    "crack": sfx_crack,
    "sparks": sfx_sparks,
    "hatch": sfx_hatch,
    "metal_groan": sfx_metal_groan,
    "undock": sfx_undock,
    "parachute": sfx_parachute,
    "riser": sfx_riser,
    "hit": sfx_hit,
    "braam": sfx_braam,
    "tick": sfx_tick,
    "select": sfx_select,
    "qte_ok": sfx_qte_ok,
    "qte_fail": sfx_qte_fail,
    "ui": sfx_ui,
    "suit_warn": sfx_suit_warn,
    "valve": sfx_valve,
    "explosion": sfx_explosion,
    "glitch": sfx_glitch,
    "title": sfx_title,
    "tether": sfx_tether,
    "grab": sfx_grab,
    "splash": sfx_splash,
    "pulse": sfx_pulse_low,
}

AMBIENCE = {
    "amb_breath": lambda: fold_loop(breath(2, 8.5), 0.4)[0],
    "amb_breath_fast": lambda: fold_loop(breath(4, 5.0), 0.3)[0],
    "amb_station": amb_station,
    "amb_suit": amb_suit,
    "amb_alarm": amb_alarm,
    "amb_master_alarm": amb_master_alarm,
    "amb_hiss": amb_hiss,
    "amb_thruster": amb_thruster,
    "amb_reentry": amb_reentry,
    "amb_ocean": amb_ocean,
    "amb_wind": amb_wind,
    "amb_static": amb_static,
}


def main():
    only = set(sys.argv[1:])
    for name, fn in MUSIC.items():
        if only and name not in only:
            continue
        write(os.path.join(ROOT, "assets", "music", name + ".ogg"), fn())
    for name, fn in {**SFX, **AMBIENCE}.items():
        if only and name not in only:
            continue
        x = fn()
        write(os.path.join(ROOT, "assets", "sfx", name + ".ogg"), x)


if __name__ == "__main__":
    main()
