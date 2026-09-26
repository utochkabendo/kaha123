#!/usr/bin/env python3
"""Voice-over generator for ZARYA.

Synthesises every line of assets/dialogue.tsv with Piper neural TTS
(Russian voices) and runs it through a per-character FX chain:
  helmet - commander inside the EVA helmet (tight early reflections)
  dry    - inside the station (small metallic room)
  radio  - suit-to-suit radio (band-limited, saturated, squelch)
  ground - Mission Control over a long-range link (quindar tones, crackle)
  ai     - ORION, the station AI (pitched down, metallic comb, chime)

Usage:
  pip install piper-tts numpy scipy soundfile
  python tools/gen_voice.py --models <dir with ru_RU-*-medium.onnx>
Models: https://huggingface.co/rhasspy/piper-voices/tree/main/ru/ru_RU
"""
import argparse
import os
import re
import sys

import numpy as np
import soundfile as sf
from scipy import signal

from piper import PiperVoice, SynthesisConfig

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SR = 22050

VOICE_MODEL = {
    "GROMOV": "ru_RU-dmitri-medium.onnx",
    "MARINA": "ru_RU-irina-medium.onnx",
    "CUP": "ru_RU-ruslan-medium.onnx",
    "ORION": "ru_RU-denis-medium.onnx",
}

rng = np.random.default_rng(1961)


# ----------------------------------------------------------------- helpers
def sos_filter(x, kind, freq, order=4):
    sos = signal.butter(order, freq, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def peaking(x, f0, gain_db, q=1.0):
    a = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f0 / SR
    alpha = np.sin(w0) / (2 * q)
    b = [1 + alpha * a, -2 * np.cos(w0), 1 - alpha * a]
    den = [1 + alpha / a, -2 * np.cos(w0), 1 - alpha / a]
    return signal.lfilter(b, den, x)


def make_ir(length, decay, predelay=0.0, density_lp=None, bright=1.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t / decay)
    if density_lp:
        ir = sos_filter(ir, "lowpass", density_lp, 2)
    if bright < 1.0:
        # darken the tail progressively
        tail = sos_filter(ir, "lowpass", 2500, 2)
        mix = np.clip(t / length, 0, 1) * (1 - bright)
        ir = ir * (1 - mix) + tail * mix
    pd = int(predelay * SR)
    ir = np.concatenate([np.zeros(pd), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    return ir


def reverb(x, ir, mix):
    wet = signal.fftconvolve(x, ir)[: len(x) + len(ir) // 2]
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry * (1 - mix) + wet * mix


def compress(x, thresh_db=-22, ratio=3.0, attack=0.005, release=0.08):
    env = np.abs(x)
    a_att = np.exp(-1 / (attack * SR))
    a_rel = np.exp(-1 / (release * SR))
    e = np.zeros_like(env)
    prev = 0.0
    for i, v in enumerate(env):
        coef = a_att if v > prev else a_rel
        prev = coef * prev + (1 - coef) * v
        e[i] = prev
    lvl = 20 * np.log10(e + 1e-7)
    over = np.maximum(lvl - thresh_db, 0)
    gain = 10 ** (-over * (1 - 1 / ratio) / 20)
    return x * gain


def normalize(x, rms_db=-18.0, peak_db=-1.0):
    voiced = x[np.abs(x) > 1e-3]
    rms = np.sqrt(np.mean(voiced ** 2)) if len(voiced) else 1e-3
    x = x * (10 ** (rms_db / 20) / rms)
    peak = 10 ** (peak_db / 20)
    return np.tanh(x / peak) * peak


def tone(freq, dur, amp, attack=0.004, release=0.02):
    n = int(dur * SR)
    t = np.arange(n) / SR
    env = np.minimum(1, t / attack) * np.minimum(1, (dur - t) / release)
    return amp * np.sin(2 * np.pi * freq * t) * np.clip(env, 0, 1)


def silence(dur):
    return np.zeros(int(dur * SR))


def pitch_shift(x, factor):
    """Shift pitch by `factor` (0.9 = lower) by resampling; changes duration by 1/factor."""
    from fractions import Fraction

    fr = Fraction(factor).limit_denominator(100)
    return signal.resample_poly(x, fr.denominator, fr.numerator)


# ------------------------------------------------------------ FX chains
def fx_helmet(x):
    x = sos_filter(x, "highpass", 110, 2)
    x = peaking(x, 2600, 3.0, 0.8)
    x = sos_filter(x, "lowpass", 8500, 2)
    x = compress(x, -24, 3.0)
    ir = make_ir(0.14, 0.025, 0.0015, density_lp=6000)
    return reverb(x, ir, 0.22)


def fx_dry(x):
    x = sos_filter(x, "highpass", 85, 2)
    x = peaking(x, 3200, 2.0, 0.9)
    x = compress(x, -24, 2.5)
    ir = make_ir(0.7, 0.13, 0.012, density_lp=7000, bright=0.6)
    return reverb(x, ir, 0.16)


def squelch(dur, amp):
    n = int(dur * SR)
    t = np.arange(n) / SR
    nz = sos_filter(rng.standard_normal(n), "bandpass", [900, 4200], 2)
    return amp * nz * np.exp(-t / (dur * 0.35))


def click(amp=0.35):
    n = int(0.012 * SR)
    c = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.002 * SR))
    return amp * sos_filter(c, "bandpass", [700, 5000], 2)


def fx_radio(x):
    x = sos_filter(x, "highpass", 320, 4)
    x = sos_filter(x, "lowpass", 3300, 4)
    x = peaking(x, 1800, 4.0, 1.2)
    x = normalize(x, -16)
    drive = 2.4
    x = np.tanh(x * drive) / np.tanh(drive)
    hiss = sos_filter(rng.standard_normal(len(x)), "bandpass", [500, 5000], 2) * 0.012
    x = x + hiss
    pre = np.concatenate([click(0.3), squelch(0.05, 0.05), silence(0.05)])
    post = np.concatenate([silence(0.04), squelch(0.18, 0.22)])
    return np.concatenate([pre, x, post])


def fx_ground(x):
    x = sos_filter(x, "highpass", 450, 4)
    x = sos_filter(x, "lowpass", 2800, 4)
    x = peaking(x, 1500, 5.0, 1.0)
    x = normalize(x, -15)
    drive = 3.4
    x = np.tanh(x * drive) / np.tanh(drive)
    n = len(x)
    # link fading + a couple of dropouts
    t = np.arange(n) / SR
    fade = 1.0 - 0.18 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * t + rng.uniform(0, 6)))
    for _ in range(max(1, n // (SR * 2))):
        c = rng.integers(0, n)
        w = int(0.03 * SR)
        fade[c : c + w] *= 0.25
    x = x * fade
    static = sos_filter(rng.standard_normal(n), "bandpass", [300, 6000], 2) * 0.03
    crackle = np.zeros(n)
    idx = rng.integers(0, n, size=max(4, n // 900))
    crackle[idx] = rng.uniform(-0.5, 0.5, size=len(idx))
    crackle = sos_filter(crackle, "highpass", 1500, 2)
    x = x + static + crackle
    lead = np.concatenate([tone(2525, 0.22, 0.22), silence(0.08)])
    lead = lead + sos_filter(rng.standard_normal(len(lead)), "bandpass", [300, 6000], 2) * 0.03
    tail = np.concatenate([silence(0.06), tone(2475, 0.22, 0.22)])
    tail = tail + sos_filter(rng.standard_normal(len(tail)), "bandpass", [300, 6000], 2) * 0.03
    return np.concatenate([lead, x, tail])


def fx_ai(x):
    n = len(x)
    t = np.arange(n) / SR
    # metallic comb (short feedback delay)
    d = int(0.0034 * SR)
    den = np.zeros(d + 1)
    den[0], den[d] = 1.0, -0.38
    y = signal.lfilter([1.0], den, x)
    x = 0.6 * x + 0.4 * y / 1.6
    # subtle chorus
    lfo = (0.009 + 0.0025 * np.sin(2 * np.pi * 0.6 * t)) * SR
    idx = np.clip(np.arange(n) - lfo, 0, n - 1)
    ch = np.interp(idx, np.arange(n), x)
    x = 0.8 * x + 0.35 * ch
    # a touch of ring modulation for the synthetic timbre
    x = 0.9 * x + 0.1 * x * np.sin(2 * np.pi * 70 * t)
    x = sos_filter(x, "highpass", 130, 2)
    x = sos_filter(x, "lowpass", 7200, 4)
    x = peaking(x, 2200, 2.5, 1.0)
    x = compress(x, -22, 3.0)
    ir = make_ir(1.1, 0.22, 0.02, density_lp=5000, bright=0.5)
    x = reverb(x, ir, 0.14)
    chime = np.concatenate(
        [
            tone(1318.5, 0.09, 0.10, release=0.07),
            tone(1760.0, 0.16, 0.08, release=0.14),
            silence(0.10),
        ]
    )
    chime = sos_filter(chime, "lowpass", 5000, 2)
    return np.concatenate([chime, x])


FX = {"helmet": fx_helmet, "dry": fx_dry, "radio": fx_radio, "ground": fx_ground, "ai": fx_ai}


# ------------------------------------------------------------ synthesis
def split_sentences(text):
    parts = re.findall(r"[^.!?…]+(?:\.\.\.|[.!?…]+)?", text)
    return [p.strip() for p in parts if p.strip()]


def gap_after(sentence):
    if sentence.endswith("..."):
        return 0.55
    if sentence.endswith("?"):
        return 0.28
    if sentence.endswith("!"):
        return 0.16
    return 0.26


def synth(voice, text, rate, speaker):
    pitch = 0.9 if speaker == "ORION" else 1.0
    cfg = SynthesisConfig(
        length_scale=rate * pitch,
        noise_scale=0.72,
        noise_w_scale=0.85,
        normalize_audio=True,
    )
    out = []
    sents = split_sentences(text)
    for i, s in enumerate(sents):
        chunks = [c.audio_float_array for c in voice.synthesize(s, cfg)]
        audio = np.concatenate(chunks) if chunks else np.zeros(1)
        # trim leading/trailing near-silence
        nz = np.where(np.abs(audio) > 0.01)[0]
        if len(nz):
            audio = audio[max(0, nz[0] - 200) : nz[-1] + 400]
        out.append(audio.astype(np.float64))
        if i < len(sents) - 1:
            out.append(silence(gap_after(s) * rate))
    x = np.concatenate(out)
    if pitch != 1.0:
        x = pitch_shift(x, pitch)
    return x


def load_dialogue(path):
    rows = []
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        cid, speaker, fx, rate, text = line.split("\t")[:5]
        rows.append((cid, speaker, fx, float(rate), text))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", required=True, help="directory with Piper ru_RU models")
    ap.add_argument("--out", default=os.path.join(ROOT, "assets", "voice"))
    ap.add_argument("--only", nargs="*", help="line ids to regenerate")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    voices = {}
    rows = load_dialogue(os.path.join(ROOT, "assets", "dialogue.tsv"))
    manifest = []
    for cid, speaker, fx, rate, text in rows:
        if args.only and cid not in args.only:
            continue
        if speaker not in voices:
            voices[speaker] = PiperVoice.load(os.path.join(args.models, VOICE_MODEL[speaker]))
        x = synth(voices[speaker], text, rate, speaker)
        x = normalize(x, -19)
        x = FX[fx](x)
        x = normalize(x, -18.5 if fx in ("radio", "ground") else -18.0)
        x = np.concatenate([silence(0.02), x, silence(0.08)])
        path = os.path.join(args.out, f"{cid}.ogg")
        sf.write(path, x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        dur = len(x) / SR
        manifest.append(f"{cid}\t{dur:.2f}")
        print(f"{cid:6s} {speaker:7s} {fx:7s} {dur:5.2f}s  {text}", flush=True)
    if not args.only:
        with open(os.path.join(ROOT, "tools", "voice_durations.txt"), "w") as f:
            f.write("\n".join(manifest) + "\n")


if __name__ == "__main__":
    sys.exit(main())
