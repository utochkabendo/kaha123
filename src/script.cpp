// ---------------------------------------------------------------------------
// ZARYA — the screenplay. Every shot is a pure function of its local time and
// the story flags, so slow motion, pauses and branching never desync.
//
// World layout (metres, station at the origin):
//   modules along z: capsule dock -16.6 .. engines +25.5, Beta at z -13..-2.5
//   truss along x at y = 6, solar wings at |x| = 24 and 31
//   antenna dish at (0, 4.75, 17.5), airlock hatch at x = -7
//   Earth below (-y), flight direction +z
// ---------------------------------------------------------------------------
#include <cmath>

#include "director.h"

namespace {

// ------------------------------------------------------------------ helpers
Vector3 SunDir(float elDeg, float azDeg) {
    float el = Deg(elDeg), az = Deg(azDeg);
    return V3(sinf(az) * cosf(el), sinf(el), cosf(az) * cosf(el));
}

Basis BodyBasis(Vector3 head, Vector3 face) {
    Basis b;
    b.y = Norm(head);
    b.z = Norm(face - b.y * Dot(face, b.y));
    b.x = Cross(b.y, b.z);
    return b;
}
Basis Yaw(float deg) { return BodyBasis(V3(0, 1, 0), V3(sinf(Deg(deg)), 0, cosf(Deg(deg)))); }

Vector3 Orbit(Vector3 c, float r, float h, float deg) {
    return c + V3(sinf(Deg(deg)) * r, h, cosf(Deg(deg)) * r);
}

void Cam(SceneState& s, Vector3 p, Vector3 t, float fov) {
    s.camPos = p;
    s.camTarget = t;
    s.fov = fov;
}

Pose MakePose(float lp, float lr, float le, float rp, float rr, float re, float lh, float lk, float rh, float rk) {
    Pose p;
    p.lShPitch = lp; p.lShRoll = lr; p.lElbow = le;
    p.rShPitch = rp; p.rShRoll = rr; p.rElbow = re;
    p.lHip = lh; p.lKnee = lk; p.rHip = rh; p.rKnee = rk;
    return p;
}
Pose PIdle(float t = 0) { return MakePose(28 + 4 * sinf(t), 14, 35, 24 + 4 * cosf(t * 0.9f), 14, 32, 12, 24, 6, 18); }
Pose PWork(float t) {
    Pose p = MakePose(92 + 8 * sinf(t * 2.1f), 12, 48 + 12 * sinf(t * 3.3f), 86 + 6 * cosf(t * 1.7f), 10, 58, 10, 16, 6, 12);
    p.lReach = 22;
    p.rReach = 22;
    return p;
}
Pose PCrawl(float t) {
    float s = sinf(t * 3.4f);
    return MakePose(150 + 25 * s, 14, 35 - 25 * s, 150 - 25 * s, 14, 35 + 25 * s, 18 + 8 * s, 30, 18 - 8 * s, 30);
}
Pose PFlail(float t) {
    return MakePose(90 + 45 * sinf(t * 5.1f), 60, 40, 70 + 45 * cosf(t * 4.3f), 70, 35, 35 + 20 * sinf(t * 3.1f), 45, 15 + 20 * cosf(t * 3.7f), 30);
}
Pose PGrab() { return MakePose(168, 8, 6, 45, 45, 60, 20, 30, 10, 20); }
Pose PFly() { return MakePose(172, 6, 4, 168, 6, 6, 2, 6, 0, 4); }
Pose PWheel(float t) {
    Pose p = MakePose(82, 18, 65 + 8 * sinf(t * 9), 82, 18, 65 - 8 * sinf(t * 9), 16, 28, 10, 22);
    p.lReach = 28;
    p.rReach = 28;
    return p;
}
Pose PFloat(float t) { return MakePose(38 + 6 * sinf(t * 0.8f), 28, 45, 32 + 6 * cosf(t * 0.7f), 26, 42, 22, 38, 12, 30); }
Pose PHandOnGlass() { return MakePose(95, 6, 15, 30, 25, 50, 15, 30, 8, 20); }

void Put(Astro& a, Vector3 pos, Basis rot, Pose p, int style = 0) {
    a.on = true;
    a.pos = pos;
    a.rot = rot;
    a.pose = p;
    a.style = style;
}

// deterministic debris stream through a region
void DebrisStream(SceneState& s, float t, float density, Vector3 center, float radius, float seed, Vector3 dir, float speed = 110) {
    dir = Norm(dir);
    Vector3 u = Norm(Cross(dir, V3(0, 1, 0.1f)));
    Vector3 v = Cross(dir, u);
    for (int i = 0; i < 16 && (int)s.debris.size() < 16; i++) {
        float h1 = Hash1(seed + i * 7.13f), h2 = Hash1(seed + i * 3.71f + 1), h3 = Hash1(seed + i * 5.29f + 2);
        if (h3 > density) continue;
        float period = 0.9f + h1 * 1.6f;
        float ph = (t + h2 * period) / period;
        float cycle = floorf(ph);
        ph -= cycle;
        float ang = Hash1(seed + i + cycle * 13.1f) * 6.2831f;
        float rad = sqrtf(Hash1(seed + i * 2.3f + cycle * 7.7f)) * radius * 0.85f;
        Vector3 off = u * (cosf(ang) * rad) + v * (sinf(ang) * rad);
        Vector3 d = Norm(dir + u * ((h1 - 0.5f) * 0.15f) + v * ((h2 - 0.5f) * 0.15f));
        Vector3 pos = center + off + d * (-radius * 1.5f + 3.0f * radius * ph);
        s.debris.push_back(Vector4{pos.x, pos.y, pos.z, 0.05f + 0.12f * h2});
        Vector3 vel = d * speed;
        s.debrisVel.push_back(Vector4{vel.x, vel.y, vel.z, 0.3f + 0.6f * h1});
    }
}

void Chunk(SceneState& s, Vector3 pos, Vector3 vel, float size, float heat = 0.2f) {
    // hero chunks go first so the renderer picks them for the SDF pass
    s.debris.insert(s.debris.begin(), Vector4{pos.x, pos.y, pos.z, size});
    s.debrisVel.insert(s.debrisVel.begin(), Vector4{vel.x, vel.y, vel.z, heat});
    if (s.debris.size() > 16) {
        s.debris.resize(16);
        s.debrisVel.resize(16);
    }
}

void SparkBurst(SceneState& s, Vector3 pos, float age, int n, float seed, float speed = 6, float life = 0.9f) {
    if (age < 0 || age > life) return;
    for (int i = 0; i < n && (int)s.sparks.size() < 24; i++) {
        float a = Hash1(seed + i * 1.7f) * 6.2831f;
        float z = Hash1(seed + i * 2.9f) * 2 - 1;
        float r = sqrtf(1 - z * z);
        Vector3 d = V3(r * cosf(a), z, r * sinf(a));
        float sp = speed * (0.4f + Hash1(seed + i * 4.1f));
        Vector3 p = pos + d * (sp * age);
        float k = 1 - age / life;
        s.sparks.push_back(Vector4{p.x, p.y, p.z, k * k * (0.5f + Hash1(seed + i))});
    }
}

// ------------------------------------------------------------------ set presets
void Space(SceneState& s, float earthRot) {
    s.set = SET_SPACE;
    s.sunDir = SunDir(30, 40);
    s.sunCol = V3(1.0f, 0.96f, 0.9f);
    s.earthRot = earthRot;
    s.stars = 0.22f;
    s.panelAngle = 0.35f;
    s.capsuleMode = 1;
    s.exposure = 1.0f;
    s.bloom = 0.5f;
    s.grain = 0.035f;
    s.vignette = 0.45f;
    s.aberration = 0.0015f;
    s.airlock = 0;
}

void Interior(SceneState& s, float alarm) {
    s.set = SET_INTERIOR;
    s.mainLight = alarm > 0.5f ? 0.22f : 1.0f;
    s.alarmLight = alarm;
    s.smoke = 0.1f + 0.2f * alarm;
    s.betaMist = 0.9f;
    s.sunShaft = 1.0f;
    s.exposure = 1.25f;
    s.grain = 0.05f;
    s.vignette = 0.55f;
    s.redPulse = alarm * 0.12f * (0.5f + 0.5f * sinf((float)GetTime() * 4.2f));
    s.saturation = 1.05f;
    s.floaters = 1;
}

void Ground(SceneState& s) {
    s.set = SET_GROUND;
    s.sunElev = 3.5f;
    s.sunAz = 12.0f;
    s.exposure = 0.95f;
    s.grain = 0.04f;
    s.vignette = 0.5f;
    s.tint = V3(1.0f, 0.97f, 1.02f);
    s.saturation = 1.08f;
}

constexpr float kER = 1.35f;  // earth rotation phase for day shots (land + ocean + clouds below)

const Vector3 kDish = V3(0, 6.4f, 17.5f);
const Vector3 kGAnt = V3(0.72f, 2.98f, 17.5f);  // feet in the foot restraint, hands on the drive unit
const Vector3 kBurstDir = V3(-0.45f, 0.1f, 0.88f);

void GromovAtAntenna(SceneState& s, float t, bool working = true) {
    Put(s.astro[0], kGAnt + V3(0, 0.02f * sinf(t * 0.7f), 0), BodyBasis(V3(0.05f, 1, 0), V3(-1, 0.05f, 0)),
        working ? PWork(t) : PIdle(t));
}

// crawling along Alpha's top handrails towards -z; z decreases with time
void GromovCrawl(SceneState& s, float z, float t) {
    Put(s.astro[0], V3(0.0f, 2.78f, z), BodyBasis(V3(0, 0.15f, -1), V3(0, -1, 0)), PCrawl(t));
}

bool Saved(const Flags& f) { return f.marinaSaved; }
bool NotSaved(const Flags& f) { return !f.marinaSaved; }
bool Antenna(const Flags& f) { return f.antennaFixed; }
bool NoAntenna(const Flags& f) { return !f.antennaFixed; }

}  // namespace

// ==========================================================================
void BuildStory(Director& D) {
    // ------------------------------------------------------------------ COLD OPEN (flash-forward)
    {
        auto b = D.Add("cold");
        b.Amb(0.0f, "amb_breath_fast", 0.55f, 0.3f).Amb(0.0f, "amb_alarm", 0.35f, 0.3f).Sfx(0.2f, "heartbeat", 0.9f);
        b.Shot(1.2f, [](SceneState& s, float t, const Flags&) { s.set = SET_BLACK; });
        b.Sfx(1.2f, "glitch", 0.8f).Sfx(1.25f, "impact_big", 0.7f);
        b.Shot(0.9f, [](SceneState& s, float t, const Flags&) {
            Interior(s, 1);
            s.smoke = 0.5f;
            Cam(s, V3(0.35f, 0.25f, -3.5f) + V3(0, 0, t * 0.8f), V3(0.0f, 0.0f, 6.0f), 58);
            s.handheld = 3.0f;
            s.handFreq = 3.0f;
            s.roll = -8;
            SparkBurst(s, V3(0.25f, 0.5f, 1.6f), t, 18, 3, 3.0f, 1.2f);
            s.pointPos = V3(0.25f, 0.5f, 1.6f);
            s.pointCol = V3(1.0f, 0.5f, 0.2f) * (1.5f * (1 - t));
            s.aberration = 0.006f;
        });
        b.Sfx(2.1f, "glitch", 0.7f);
        b.Shot(0.9f, [](SceneState& s, float t, const Flags&) {
            Interior(s, 1);
            s.betaMist = 1.3f;
            Put(s.astro[1], V3(0.05f, -0.15f, 6.75f), Yaw(180), PHandOnGlass(), 1);
            Cam(s, V3(0.08f, 0.02f, 5.55f), V3(0.0f, 0.0f, 6.6f), 34);
            s.handheld = 2.0f;
            s.handFreq = 2.5f;
            s.roll = 6;
            s.aberration = 0.005f;
        });
        b.Line(2.3f, "ff01").Sfx(3.0f, "glitch", 0.7f).Sfx(3.05f, "impact3", 0.6f);
        b.Shot(1.4f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.4f);
            s.altitude = 160;
            s.thrust = 1;
            s.heat = 0.55f;
            s.heatDir = V3(0, -0.3f, 1);
            s.stationRot = Euler(Deg(8 + t * 6), Deg(-14 - t * 4), Deg(10));
            s.sunDir = SunDir(12, 60);
            Cam(s, V3(-70, 18, -40) + V3(t * 4, 0, 0), V3(0, -2, 4), 38);
            s.roll = -12;
            s.handheld = 2.5f;
            s.handFreq = 2.0f;
            s.aberration = 0.005f;
        });
        b.Sfx(4.4f, "crack", 0.8f).Sfx(4.4f, "glitch", 0.8f);
        b.Shot(1.0f, [](SceneState& s, float t, const Flags&) {
            Interior(s, 1);
            s.smoke = 0.6f;
            Cam(s, V3(0.1f, 0.15f, 2.4f), V3(-0.2f, 0.1f, 6.0f), 64);
            s.crack = 1;
            s.helmet = 1;
            s.handheld = 3.5f;
            s.handFreq = 3.0f;
            s.hud = 1;
            s.hudWarn = 1;
            s.hudPress = 12.4f;
            s.hudPulse = 148;
            s.flash = t < 0.15f ? 0.8f * (1 - t / 0.15f) : 0;
        });
        b.Sfx(5.4f, "hit", 1.0f).Amb(5.4f, "amb_breath_fast", 0.0f, 0.05f).Amb(5.4f, "amb_alarm", 0.0f, 0.05f);
        b.Shot(4.6f, [](SceneState& s, float t, const Flags&) { s.set = SET_BLACK; });
        b.Caption(6.0f, "ЗА ПЯТЬ МИНУТ ДО ЭТОГО", 3.6f);
        b.Next("intro");
    }

    // ------------------------------------------------------------------ PART I — DAWN
    {
        auto b = D.Add("intro");
        b.Music(0.0f, "mus_intro", 4.0f, 0.9f).Amb(0.0f, "amb_suit", 0.3f, 3.0f).Amb(0.5f, "amb_breath", 0.22f, 3.0f);
        b.Caption(1.0f, "НИЗКАЯ ОКОЛОЗЕМНАЯ ОРБИТА  ·  408 КМ", 4.6f);
        // i1: night side, city lights, the limb glowing ahead
        b.Shot(7.5f, [](SceneState& s, float t, const Flags&) {
            Space(s, 7.1f + t * 0.004f);
            s.stationOn = false;
            s.sunDir = SunDir(-23.5f, 0);
            s.stars = 1.0f;
            s.fade = 1 - Smooth(t / 2.5f);
            float k = Smooth(t / 7.5f);
            Cam(s, V3(0, 0, 0), V3(0, Mix(-120.0f, -42.0f, k), 100), 52);
            s.roll = Mix(-6.0f, -2.0f, k);
            s.exposure = 1.3f;
        });
        // i2: sunrise behind the station
        b.Shot(8.0f, [](SceneState& s, float t, const Flags&) {
            Space(s, 0.4f);
            float k = Smooth(t / 8.0f);
            s.sunDir = SunDir(Mix(-21.2f, -18.6f, EaseOut(t / 6.0f)), 2);
            s.stars = 0.8f * (1 - k);
            s.panelAngle = 0.1f;
            Vector3 sd = SunDir(-19.6f, 2);
            Vector3 cp = V3(0, 3, 0) - sd * 78.0f + V3(-3.0f + t * 0.35f, 1.5f, t * 0.6f);
            Cam(s, cp, cp + sd * 100.0f + V3(0, 3.0f, 0), 34);
            s.exposure = 1.1f;
            s.titleCard = Clamp01((t - 2.4f) / 1.2f) * Clamp01((7.6f - t) / 1.0f);
        });
        b.Sfx(9.8f, "title", 0.9f);
        // i3: wide orbit around the sunlit station
        b.Shot(6.5f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER);
            s.sunDir = SunDir(22, 28);
            Cam(s, Orbit(V3(0, 2, 4), 72, 16 - t * 0.5f, 205 + t * 3.5f), V3(0, 1, 4), 42);
            GromovAtAntenna(s, t);
        });
        b.Chapter(16.0f, "ЧАСТЬ I|РАССВЕТ");
        b.At(15.8f).Say("i01", 0.5f);
        // i4: Gromov at the antenna, slow dolly in
        b.Shot(6.2f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.002f * t);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t + 20);
            float k = Smooth(t / 6.2f);
            Cam(s, Mix(V3(6.8f, 3.3f, 21.8f), V3(3.3f, 3.75f, 19.4f), k), Mix(V3(0.4f, 3.3f, 17.4f), V3(0.5f, 3.55f, 17.5f), k), 38);
            s.aperture = 5;
            s.handheld = 0.3f;
        });
        b.Say("i02", 0.4f);
        // i5: visor close-up — Earth in the reflection
        b.Shot(4.6f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.01f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t + 30);
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Vector3 face = s.astro[0].rot.z;
            (void)face;
            Cam(s, head + V3(-0.5f + t * 0.01f, 0.32f, 0.72f - t * 0.02f), head + V3(0, -0.02f, 0), 30);
            s.aperture = 9;
            s.handheld = 0.5f;
        });
        b.Say("i03", 0.4f);
        // i6: over the shoulder — the dish and the world
        b.Shot(6.4f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.015f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t + 40);
            Cam(s, V3(2.1f, 3.95f, 16.55f) + V3(t * 0.04f, 0, 0), V3(-1.2f, 3.3f, 17.9f), 46);
            s.focus = 2.2f;
            s.aperture = 4;
            s.handheld = 0.4f;
        });
        b.Say("i04", 0.6f);
        // i7: oxygen check — wide shot, the station glides over the planet
        b.Shot(8.6f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.02f + t * 0.002f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t + 50);
            Cam(s, V3(14, -6, 36) + V3(-t * 0.6f, t * 0.2f, 0), V3(0, 3, 14), 40);
        });
        b.Say("i05", 0.3f).Say("i06", 0.4f);
        b.Next("warning");
    }

    // ------------------------------------------------------------------ WARNING
    {
        auto b = D.Add("warning");
        b.Music(0.2f, "", 2.5f);
        b.Sfx(0.5f, "pulse", 0.8f);
        // w1: extreme wide, a flash far away above the limb
        b.Shot(4.4f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.04f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t + 60, t < 2.5f);
            s.burst = Vector4{kBurstDir.x * 6000, kBurstDir.y * 6000, kBurstDir.z * 6000, t - 0.4f};
            Cam(s, V3(6, 6, 10), V3(-2.4f, 4.4f, 22), 40);
            s.stars = 0.4f;
        });
        b.At(0.6f).Say("w01", 0.3f);
        // w2: he turns to the flash — reflection in the visor
        b.Shot(3.4f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.045f);
            s.sunDir = SunDir(22, 28);
            Put(s.astro[0], kGAnt, BodyBasis(V3(0.05f, 1, 0), Norm(Mix(V3(-1, 0.05f, 0), V3(-0.45f, 0.15f, 0.88f), Smooth(t / 1.5f)))), PIdle(t));
            s.burst = Vector4{kBurstDir.x * 6000, kBurstDir.y * 6000, kBurstDir.z * 6000, 4.0f + t};
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Cam(s, head + V3(-0.6f, -0.05f, 1.0f), head, 32);
            s.aperture = 8;
            s.handheld = 0.6f;
        });
        b.Music(7.6f, "mus_tension", 0.4f, 0.9f).Sfx(7.6f, "braam", 0.9f);
        // w3: from the debris' point of view — the station, small and alone
        b.Shot(5.4f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.05f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t + 70);
            float k = Smooth(t / 5.4f);
            Cam(s, Mix(V3(-210, 60, 330), V3(-150, 42, 240), k), V3(0, 2, 6), 30);
            DebrisStream(s, t, 0.35f, V3(-120, 35, 200), 25, 17, V3(0.4f, -0.1f, -0.7f), 45);
            s.stars = 0.5f;
        });
        b.Say("w02", 0.3f);
        // w4: gloves on the loose dish bracket
        b.Shot(4.4f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.055f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t * 1.6f + 80);
            Cam(s, V3(1.45f, 4.1f, 18.9f), V3(0.25f, 3.45f, 17.5f), 32);
            s.aperture = 7;
            s.handheld = 0.9f;
        });
        b.Say("w03", 0.3f);
        // w5: dutch angle on the helmet
        b.Shot(3.6f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.06f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t + 90, false);
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Cam(s, head + V3(-1.1f, -0.55f, 0.5f), head + V3(0, 0.05f, 0), 36);
            s.roll = 14;
            s.aperture = 6;
            s.handheld = 0.8f;
        });
        b.Say("w04", 0.3f);
        b.Amb(b.cursor - 0.4f, "amb_breath_fast", 0.45f, 0.8f);
        // decision: push in, the glittering cloud grows behind him
        b.Shot(12.0f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.065f);
            s.sunDir = SunDir(22, 28);
            Put(s.astro[0], kGAnt, BodyBasis(V3(0.05f, 1, 0), Norm(kBurstDir)), PIdle(t + 100));
            s.burst = Vector4{kBurstDir.x * 6000, kBurstDir.y * 6000, kBurstDir.z * 6000, 12.0f + t * 2.0f};
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Vector3 side = Norm(Cross(kBurstDir, V3(0, 1, 0)));
            Cam(s, head - kBurstDir * (1.9f - t * 0.05f) + V3(0, 0.25f, 0) + side * 0.55f, head + kBurstDir * 30.0f, 40 - t * 0.4f);
            s.focus = 1.9f;
            s.aperture = 6;
            s.handheld = 0.5f;
            s.saturation = 0.9f;
        });
        b.Choice(b.cursor + 0.2f, 7.0f,
                 {{"БРОСИТЬ АНТЕННУ", "Бросил антенну и ушёл к шлюзу", "c1a", [](Flags& f) { f.antennaFixed = false; }},
                  {"ЗАКРЕПИТЬ АНТЕННУ", "Остался закрепить антенну", "c1b", [](Flags& f) {}}},
                 0);
        b.Length(b.cursor + 0.4f);
        b.Next("c1a");
    }

    // ------------------------------------------------------------------ choice 1 outcomes
    {
        auto b = D.Add("c1a");
        b.Line(0.2f, "c1a").Amb(0.0f, "amb_breath_fast", 0.5f, 0.3f);
        b.Shot(5.0f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.07f);
            s.sunDir = SunDir(22, 28);
            float k = Smooth(t / 5.0f);
            Vector3 p = Mix(kGAnt, V3(0.2f, 2.9f, 13.0f), k);
            Put(s.astro[0], p, BodyBasis(Norm(Mix(V3(0.05f, 1, 0), V3(0, 0.2f, -1), Smooth(t / 1.5f))), Norm(Mix(V3(-1, 0.05f, 0), V3(0, -1, 0), Smooth(t / 1.5f)))), PCrawl(t));
            Cam(s, p + V3(3.0f, 1.2f, -2.5f + t * 0.2f), p + V3(0, 0, -1.5f), 44);
            s.handheld = 1.2f;
            s.aperture = 4;
            DebrisStream(s, t, 0.25f, V3(-40, 20, 60), 18, 29, V3(0.4f, -0.1f, -0.7f), 60);
        });
        b.Next("storm");
    }
    {
        auto b = D.Add("c1b");
        b.Line(0.2f, "c1b1").Amb(0.0f, "amb_breath_fast", 0.6f, 0.3f);
        b.Shot(2.6f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.07f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t * 2.5f + 110);
            Cam(s, V3(1.4f, 4.05f, 18.8f), V3(0.25f, 3.5f, 17.5f), 30);
            s.aperture = 8;
            s.handheld = 1.0f;
        });
        QteDef q;
        q.type = QteType::Mash;
        q.key = KEY_SPACE;
        q.count = 8;
        q.window = 3.6f;
        q.slowmo = 0.6f;
        q.label = "ЗАКРЕПИТЬ АНТЕННУ";
        q.apply = [](Flags& f, bool ok) { f.antennaFixed = ok; };
        b.Qte(2.5f, q);
        b.Line(3.0f, "c1b2");
        b.Shot(4.4f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.075f);
            s.sunDir = SunDir(22, 28);
            GromovAtAntenna(s, t * 3.0f + 120);
            s.dishAngle = f.antennaFixed ? 1.0f : 0.0f;
            Cam(s, V3(2.6f, 2.5f, 15.6f), V3(0.0f, 5.6f, 17.6f), 44);
            s.handheld = 1.2f;
            DebrisStream(s, t, 0.2f, V3(-80, 30, 120), 22, 31, V3(0.4f, -0.1f, -0.7f), 50);
        });
        b.Sfx(6.9f, "impact2", 0.5f, 1.3f);
        b.Line(7.1f, "c1b3", Antenna).Line(9.0f, "c1b4", Antenna);
        b.Shot(5.8f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.08f);
            s.sunDir = SunDir(22, 28);
            s.dishAngle = f.antennaFixed ? 1.0f : 0.0f;
            float k = Smooth((t - 0.8f) / 5.0f);
            Vector3 p = Mix(kGAnt, V3(0.2f, 2.9f, 12.5f), k);
            Put(s.astro[0], p, BodyBasis(Norm(Mix(V3(0.05f, 1, 0), V3(0, 0.2f, -1), Smooth(t / 2.0f))), Norm(Mix(V3(-1, 0.05f, 0), V3(0, -1, 0), Smooth(t / 2.0f)))), PCrawl(t));
            Cam(s, V3(9, 7.5f, 24) + V3(-t * 0.3f, 0, 0), p + V3(0, 0.5f, 0), 36);
            DebrisStream(s, t, 0.4f, V3(-40, 20, 60), 20, 37, V3(0.4f, -0.1f, -0.7f), 60);
        });
        b.Next("storm");
    }

    // ------------------------------------------------------------------ PART II — STORM
    {
        auto b = D.Add("storm");
        b.Music(0.0f, "mus_action", 0.3f, 0.95f).Sfx(0.0f, "braam", 1.0f);
        b.Chapter(0.6f, "ЧАСТЬ II|ШТОРМ");
        b.Line(0.3f, "s01");
        // s1: the first wave — tracers shred a solar wing
        b.Shot(3.4f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.09f);
            s.sunDir = SunDir(22, 28);
            s.dishAngle = f.antennaFixed ? 1.0f : 0.0f;
            s.damage = Smooth((t - 0.6f) / 2.0f) * 0.9f;
            DebrisStream(s, t, 0.9f, V3(20, 8, 16), 26, 41, V3(0.35f, -0.12f, -0.75f), 140);
            SparkBurst(s, V3(26, 6, 14), t - 0.8f, 10, 11, 9);
            SparkBurst(s, V3(23, 6.5f, 19), t - 1.6f, 10, 12, 9);
            SparkBurst(s, V3(28, 5.5f, 9), t - 2.3f, 10, 13, 9);
            s.pointPos = V3(25, 7, 15);
            s.pointCol = V3(1, 0.6f, 0.3f) * (40.0f * fmaxf(0, 1 - fabsf(t - 1.0f) * 3));
            Cam(s, V3(46, -12, -14) + V3(0, t * 0.8f, 0), V3(18, 4, 14), 44);
            s.handheld = 1.2f;
            GromovCrawl(s, 12.0f, t);
        });
        b.Sfx(0.8f, "impact1", 0.8f, 1, 0.7f).Sfx(1.6f, "impact2", 0.7f, 1, 0.8f).Sfx(2.3f, "impact3", 0.8f, 1, 0.6f);
        b.Call(0.8f, [](Director& d) { d.Shake(0.8f); });
        b.Call(1.6f, [](Director& d) { d.Shake(0.6f); });
        b.Call(2.3f, [](Director& d) { d.Shake(0.9f); });
        // s2: tracking — hand over hand along the rails
        b.Shot(2.8f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.095f);
            s.sunDir = SunDir(22, 28);
            float z = 11.5f - t * 0.9f;
            GromovCrawl(s, z, t + 5);
            Cam(s, V3(1.8f, 3.3f, z - 2.2f), V3(0, 2.8f, z + 0.3f), 50);
            s.handheld = 2.0f;
            s.aperture = 3;
            DebrisStream(s, t, 0.8f, V3(0, 6, z), 10, 53, V3(0.35f, -0.12f, -0.75f), 90);
            if (t > 1.1f && t < 1.6f) Chunk(s, V3(1.2f, 3.6f, z - 2.4f) + V3(-1.0f, 0.3f, 12.0f) * ((t - 1.35f) * 6.0f), V3(0, 0, -60), 0.28f, 0.6f);
        });
        b.Sfx(4.5f, "whoosh", 1.0f);
        // s3: impact close by — he loses his grip
        b.Shot(2.9f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.1f);
            s.sunDir = SunDir(22, 28);
            float z = 8.8f;
            float knock = Smooth((t - 0.5f) / 1.2f);
            Put(s.astro[0], V3(0.0f, 2.78f + knock * 0.9f, z + knock * 0.4f),
                BodyBasis(Norm(Mix(V3(0, 0.15f, -1), V3(0.4f, 0.8f, -0.5f), knock)), V3(0, -1, 0.2f)),
                PoseLerp(PCrawl(t), PFlail(t), knock));
            SparkBurst(s, V3(0.9f, 2.4f, 10.5f), t - 0.45f, 20, 21, 5);
            s.pointPos = V3(0.9f, 2.6f, 10.5f);
            s.pointCol = V3(1, 0.55f, 0.25f) * (30.0f * fmaxf(0, 1 - fabsf(t - 0.5f) * 4));
            Cam(s, V3(-2.8f, 3.6f, 6.4f), V3(0, 3.1f, 9.4f), 42);
            s.handheld = 1.5f;
            DebrisStream(s, t, 0.7f, V3(0, 6, z), 12, 61, V3(0.35f, -0.12f, -0.75f), 110);
        });
        b.Sfx(6.7f, "impact_big", 1.0f).Call(6.7f, [](Director& d) { d.Shake(1.6f); d.Flash(0.35f, V3(1, 0.8f, 0.6f)); });
        b.Amb(6.7f, "amb_breath_fast", 0.6f, 0.2f);
        QteDef q;
        q.type = QteType::Press;
        q.key = KEY_SPACE;
        q.window = 1.25f;
        q.slowmo = 0.18f;
        q.label = "СХВАТИТЬСЯ ЗА ПОРУЧЕНЬ";
        q.failNext = "storm_drift";
        q.apply = [](Flags& f, bool ok) { f.railGrabbed = ok; };
        b.Qte(7.0f, q);
        // s4: slow motion — the glove closes on the rail
        b.Shot(3.2f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.105f);
            s.sunDir = SunDir(22, 28);
            float back = Smooth(t / 2.0f);
            Put(s.astro[0], V3(0.0f, Mix(3.6f, 2.95f, back), 9.1f), BodyBasis(Norm(Mix(V3(0.4f, 0.8f, -0.5f), V3(0, 0.3f, -1), back)), V3(0, -1, 0.2f)), PGrab());
            Cam(s, V3(0.9f, 2.55f, 7.4f), V3(0.45f, 2.35f, 8.3f), 30);
            s.aperture = 10;
            s.focus = 1.1f;
            s.handheld = 0.6f;
            DebrisStream(s, t, 0.5f, V3(0, 5, 9), 10, 67, V3(0.35f, -0.12f, -0.75f), 110);
        });
        b.Sfx(8.8f, "grab", 0.9f).Line(9.0f, "s03");
        b.Length(12.3f);
        b.Next("storm_b");
    }
    {
        // QTE failed: the tether snaps, he tumbles away from the station
        auto b = D.Add("storm_drift");
        b.Sfx(0.0f, "tether", 1.0f).Line(0.25f, "s02");
        b.Shot(4.4f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.11f);
            s.sunDir = SunDir(22, 28);
            Vector3 p = V3(0.6f, 3.4f, 9.0f) + V3(0.8f, 1.1f, 0.3f) * t;
            Put(s.astro[0], p, Euler(t * 1.3f, t * 0.9f, t * 0.6f), PFlail(t));
            Vector3 cp = p + V3(-2.2f, 0.7f, -2.6f);
            Cam(s, cp, p, 40);
            s.roll = t * 25;
            s.handheld = 2.0f;
            s.hud = 0;
        });
        QteDef q;
        q.type = QteType::Mash;
        q.key = KEY_SPACE;
        q.count = 7;
        q.window = 3.0f;
        q.slowmo = 0.45f;
        q.label = "ТЯНИСЬ К ПОРУЧНЮ";
        q.apply = [](Flags& f, bool ok) {
            if (!ok) f.gromovHurt = true;
        };
        b.Qte(1.8f, q);
        b.Shot(4.0f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.115f);
            s.sunDir = SunDir(22, 28);
            float k = EaseOut(t / 1.0f);
            Vector3 p = Mix(V3(2.0f, 5.6f, 9.8f), V3(0.3f, 3.1f, 9.4f), k);
            Put(s.astro[0], p, BodyBasis(V3(0, 0.3f, -1), V3(0, -1, 0.2f)), f.gromovHurt ? PFlail(t * 0.3f) : PGrab());
            Cam(s, V3(3.4f, 2.3f, 12.4f), p + V3(0, 0.2f, 0), 38);
            s.handheld = 1.2f;
        });
        b.Sfx(4.4f, "impact2", 0.9f).Call(4.4f, [](Director& d) { d.Shake(1.0f); });
        b.Line(5.3f, "s03");
        b.Length(8.4f);
        b.Next("storm_b");
    }
    {
        auto b = D.Add("storm_b");
        // s5: the hull of module Beta is punctured
        b.Shot(4.4f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.12f);
            s.sunDir = SunDir(22, 28);
            s.damage = 0.9f;
            s.dishAngle = f.antennaFixed ? 1.0f : 0.0f;
            s.betaVent = Smooth((t - 0.5f) / 0.8f);
            SparkBurst(s, V3(1.35f, 1.6f, -8.0f), t - 0.45f, 22, 71, 7);
            s.pointPos = V3(2.5f, 2.2f, -8);
            s.pointCol = V3(1, 0.6f, 0.3f) * (30.0f * fmaxf(0, 1 - fabsf(t - 0.5f) * 3));
            Cam(s, V3(16, 9, -24), V3(1, 0.5f, -7), 34);
            s.handheld = 1.0f;
            DebrisStream(s, t, 0.7f, V3(0, 2, -6), 16, 73, V3(0.35f, -0.12f, -0.75f), 120);
            GromovCrawl(s, 7.5f, t);
        });
        b.Sfx(0.45f, "impact_big", 1.0f).Call(0.45f, [](Director& d) { d.Shake(1.2f); });
        b.Amb(0.6f, "amb_hiss", 0.25f, 1.0f);
        b.At(0.9f).Say("s05", 0.2f);
        b.Say("s08", 0.2f).Say("s09", 0.3f);
        // s5b: running the gauntlet along the rails
        b.Shot(6.2f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.125f);
            s.sunDir = SunDir(22, 28);
            s.betaVent = 1;
            s.damage = 0.9f;
            float z = 7.3f - t * 0.75f;
            GromovCrawl(s, z, t * 1.3f + 9);
            Cam(s, V3(-0.4f, 3.8f + t * 0.05f, z - 3.0f), V3(0, 2.9f, z), 48);
            s.handheld = 1.6f;
            s.aperture = 3;
            DebrisStream(s, t, 0.8f, V3(0, 5, z), 12, 79, V3(0.35f, -0.12f, -0.75f), 100);
        });
        // s6: first person — a tumbling fragment coming straight at him
        b.Shot(3.2f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.13f);
            s.sunDir = SunDir(22, 28);
            s.betaVent = 1;
            s.damage = 0.9f;
            Vector3 eye = V3(0.6f, 3.15f, 7.2f);
            Vector3 look = V3(0.9f, 2.8f, -2.0f);
            float dodge = (!f.visorCracked && t > 1.0f) ? Smooth((t - 1.0f) / 0.35f) : 0.0f;
            eye = eye + V3(0.9f, 0.2f, 0) * dodge;
            Cam(s, eye, look + V3(1.4f, 0, 0) * dodge, 62);
            s.helmet = 1;
            s.hud = 1;
            s.hudPulse = 136;
            s.hudO2 = 81;
            s.handheld = 1.5f;
            s.roll = -18 * dodge;
            float arrive = 1.45f;
            Vector3 hit = V3(0.65f, 3.15f, 6.9f);
            if (t < arrive + 0.2f) Chunk(s, hit + V3(-0.5f, 0.3f, -26.0f) * (arrive - t), V3(0.5f, -0.3f, 26), 0.35f, 0.4f);
            if (f.visorCracked && t > arrive) s.crack = 1;
            if (f.visorCracked && t > arrive && t < arrive + 0.2f) s.flash = 0.7f;
            DebrisStream(s, t, 0.6f, V3(0, 3, -2), 10, 83, V3(0.35f, -0.12f, -0.75f), 100);
        });
        QteDef q;
        q.type = QteType::Dir;
        q.left = false;
        q.window = 1.0f;
        q.slowmo = 0.16f;
        q.label = "УКЛОНИТЬСЯ";
        q.apply = [](Flags& f, bool ok) {
            if (!ok) f.visorCracked = true;
        };
        b.Qte(b.shotEnd - 3.2f + 0.55f, q);
        float crackT = b.shotEnd - 3.2f + 1.45f;
        b.Sfx(crackT, "crack", 1.0f).Call(crackT, [](Director& d) {
            if (d.flags.visorCracked) {
                d.Shake(1.4f);
                if (d.audio) d.audio->Sfx("suit_warn", 0.7f);
            }
        });
        b.Sfx(crackT - 0.3f, "whoosh", 1.0f);
        b.Line(crackT + 0.4f, "s04", [](const Flags& f) { return f.visorCracked; });
        // gap: the rail is gone, he has to jump
        b.Shot(3.6f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.135f);
            s.sunDir = SunDir(22, 28);
            s.betaVent = 1;
            s.damage = 0.9f;
            Put(s.astro[0], V3(0.0f, 2.95f, 2.9f), BodyBasis(V3(0, 0.3f, -1), V3(0, -1, 0.1f)), PIdle(t));
            Cam(s, V3(4.2f, 3.4f, 0.6f), V3(-0.8f, 2.6f, 1.4f), 44);
            s.crack = f.visorCracked ? 0.35f : 0;
            s.handheld = 0.9f;
            SparkBurst(s, V3(0.5f, 2.5f, 1.8f), fmodf(t, 1.1f), 6, 91 + floorf(t / 1.1f), 3, 0.8f);
        });
        b.Line(b.shotEnd - 3.6f + 0.2f, "s10", [](const Flags& f) { return !f.visorCracked; });
        QteDef j;
        j.type = QteType::Press;
        j.key = KEY_W;
        j.window = 1.3f;
        j.slowmo = 0.2f;
        j.label = "ОТТОЛКНУТЬСЯ";
        j.apply = [](Flags& f, bool ok) {
            if (!ok) f.gromovHurt = true;
        };
        b.Qte(b.shotEnd + 0.1f, j);
        // the jump — slow-motion flight across the gap
        b.Shot(3.4f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.14f);
            s.sunDir = SunDir(22, 28);
            s.betaVent = 1;
            s.damage = 0.9f;
            float k = Smooth(t / 3.0f);
            Vector3 p = Mix(V3(0.0f, 3.1f, 2.6f), V3(-5.6f, 1.9f, 0.2f), k);
            Put(s.astro[0], p, BodyBasis(Norm(V3(-0.9f, -0.15f, -0.35f)), V3(0, -1, 0)), PFly());
            Cam(s, V3(-2.6f, 4.8f, 3.9f) + V3(-t * 0.6f, 0, 0), p, 44);
            s.aperture = 5;
            s.handheld = 0.7f;
            DebrisStream(s, t, 0.6f, V3(-3, 3, 1), 10, 97, V3(0.35f, -0.12f, -0.75f), 110);
        });
        b.Sfx(b.shotEnd - 3.4f + 0.1f, "whoosh", 0.8f);
        b.Sfx(b.shotEnd - 0.4f, "grab", 1.0f);
        b.Call(b.shotEnd - 0.4f, [](Director& d) {
            if (d.flags.gromovHurt) {
                d.Shake(1.2f);
                if (d.audio) d.audio->Sfx("impact1", 0.9f);
            }
        });
        // s7: Beta venting, a warm porthole — Marina is inside
        b.Shot(4.4f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.145f);
            s.sunDir = SunDir(22, 28);
            s.betaVent = 1;
            s.damage = 0.9f;
            float k = Smooth(t / 4.4f);
            Cam(s, Mix(V3(8.0f, -1.6f, -1.5f), V3(6.2f, -0.9f, -3.8f), k), V3(1.8f, 1.0f, -7.6f), 40);
            s.aperture = 4;
            s.handheld = 0.6f;
            DebrisStream(s, t, 0.4f, V3(2, 1, -6), 12, 101, V3(0.35f, -0.12f, -0.75f), 110);
        });
        b.Line(b.shotEnd - 4.4f + 0.3f, "s06");
        // s8: the airlock — swing inside, the hatch closes
        b.Shot(6.4f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.15f);
            s.sunDir = SunDir(22, 28);
            s.betaVent = 1;
            s.damage = 0.9f;
            float k = Smooth(t / 2.6f);
            Vector3 p = Mix(V3(-8.6f, 1.2f, 0.9f), V3(-6.3f, 0.0f, 0.0f), k);
            Put(s.astro[0], p, BodyBasis(Norm(Mix(V3(0.3f, 1, 0), V3(1, 0.1f, 0), k)), V3(0, 0, -1)), PoseLerp(PGrab(), PIdle(t), k));
            s.airlock = f.airlockClean ? Smooth((t - 3.4f) / 1.2f) : Smooth((t - 4.6f) / 0.8f);
            Cam(s, V3(-13.5f, 1.8f, 4.6f), V3(-7.0f, 0.2f, 0.2f), 34);
            s.handheld = 1.0f;
            DebrisStream(s, t, 0.8f, V3(-8, 1, 0), 10, 107, V3(0.35f, -0.12f, -0.75f), 120);
            if (!f.airlockClean) SparkBurst(s, V3(-7.1f, 0.5f, 0.6f), t - 4.4f, 16, 109, 5);
        });
        float s8 = b.shotEnd - 6.4f;
        b.Line(s8 + 0.2f, "s07");
        QteDef h;
        h.type = QteType::Hold;
        h.key = KEY_E;
        h.hold = 0.9f;
        h.window = 3.0f;
        h.slowmo = 0.5f;
        h.label = "ЗАКРЫТЬ ШЛЮЗ";
        h.apply = [](Flags& f, bool ok) { f.airlockClean = ok; };
        b.Qte(s8 + 2.8f, h);
        b.Sfx(s8 + 4.5f, "hatch", 1.0f);
        b.Call(s8 + 4.5f, [](Director& d) {
            if (!d.flags.airlockClean && d.audio) {
                d.audio->Sfx("sparks", 1.0f);
                d.Shake(0.9f);
            }
        });
        b.Next("airlock");
    }

    // ------------------------------------------------------------------ PART III — THE HATCH
    {
        // the inner airlock hatch: pressure equalises, the station's red chaos is revealed
        auto b = D.Add("airlock");
        b.Music(0.0f, "mus_tension", 1.0f, 0.85f);
        b.Amb(0.0f, "amb_alarm", 0.2f, 0.5f).Amb(0.0f, "amb_master_alarm", 0.12f, 0.5f).Amb(0.0f, "amb_station", 0.35f, 0.5f);
        b.Amb(0.0f, "amb_suit", 0.0f, 1.0f).Amb(0.0f, "amb_hiss", 0.45f, 0.5f);
        b.Chapter(0.6f, "ЧАСТЬ III|ЛЮК");
        b.Line(0.3f, "a01");
        b.Shot(5.6f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.smoke = 0.45f;
            Cam(s, V3(0.45f, 0.35f, -3.4f) + V3(0, 0, -t * 0.05f), V3(0.0f, -0.05f, -6.0f), 46);
            s.handheld = 0.7f;
            SparkBurst(s, V3(0.25f, 0.5f, 1.6f), fmodf(t, 1.3f), 10, 7 + floorf(t / 1.3f), 2.5f, 1.1f);
        });
        b.Sfx(1.0f, "metal_groan", 0.6f).Line(4.6f, "a02");
        b.Shot(4.4f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.smoke = 0.45f;
            s.airlockIn = EaseOut((t - 1.0f) / 0.9f);
            float k = Smooth((t - 1.6f) / 2.8f);
            Put(s.astro[0], V3(-0.1f, -0.15f, Mix(-7.3f, -5.0f, k)), BodyBasis(V3(0, 0.15f, 1), V3(0, -1, 0.15f)), PFloat(t));
            s.flashlight = 1;
            s.flashlightPos = s.astro[0].pos + V3(0, 0.1f, 0.8f);
            s.flashlightDir = V3(0.05f, 0.05f, 1);
            Cam(s, V3(0.5f, 0.3f, -3.0f), V3(0.0f, -0.05f, -6.0f), 44);
            s.aperture = 3;
            s.handheld = 0.9f;
        });
        b.Sfx(6.6f, "hatch", 1.0f).Amb(6.6f, "amb_hiss", 0.3f, 1.0f).Call(6.6f, [](Director& d) { d.Shake(0.5f); });
        b.Next("interior");
    }
    {
        auto b = D.Add("interior");
        // n1: the corridor in red — Gromov drifts in from the node
        b.Shot(6.6f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            float k = Smooth(t / 6.6f);
            Put(s.astro[0], V3(-0.25f, -0.2f, Mix(-4.6f, -1.6f, k)), BodyBasis(V3(0, 0.2f, 1), V3(0, -1, 0.2f)), PFloat(t));
            s.flashlight = 1;
            s.flashlightPos = s.astro[0].pos + V3(0, 0.1f, 0.8f);
            s.flashlightDir = V3(0.05f, 0.05f, 1);
            Cam(s, V3(0.55f, 0.55f, 0.6f) + V3(0, 0, -t * 0.1f), s.astro[0].pos + V3(0, 0.2f, 0.4f), 52);
            s.handheld = 1.0f;
            SparkBurst(s, V3(0.25f, 0.5f, 1.6f), fmodf(t, 1.3f), 12, 3 + floorf(t / 1.3f), 2.5f, 1.1f);
            s.pointPos = V3(0.25f, 0.5f, 1.6f);
            s.pointCol = V3(1, 0.5f, 0.2f) * (1.2f * (1 - fmodf(t, 1.3f) / 1.3f));
        });
        b.Sfx(0.3f, "sparks", 0.6f).Sfx(1.6f, "sparks", 0.5f).Sfx(2.9f, "sparks", 0.6f).Sfx(4.2f, "sparks", 0.5f);
        b.At(0.5f).Say("n01", 0.2f);
        // n2: close on the visor
        b.Shot(2.6f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            Put(s.astro[0], V3(-0.25f, -0.2f, -1.4f), BodyBasis(V3(0, 0.2f, 1), V3(0, -1, 0.2f)), PFloat(t));
            s.flashlight = 1;
            s.flashlightPos = s.astro[0].pos + V3(0, 0.1f, 0.8f);
            s.flashlightDir = V3(0.05f, 0.05f, 1);
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Cam(s, head + V3(0.25f, -0.55f, 0.55f), head, 34);
            s.aperture = 8;
            s.handheld = 0.9f;
            s.crack = f.visorCracked ? 0.2f : 0;
        });
        b.Say("n02", 0.3f);
        // n3: pushing down the corridor, camera retreats in front of him
        b.Shot(5.2f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            float z = -1.2f + t * 0.55f;
            Put(s.astro[0], V3(-0.15f, -0.1f, z), BodyBasis(V3(0, 0.25f, 1), V3(0, -1, 0.25f)), PCrawl(t));
            s.flashlight = 1;
            s.flashlightPos = s.astro[0].pos + V3(0, 0.1f, 0.8f);
            s.flashlightDir = V3(0.05f, 0.0f, 1);
            Cam(s, V3(0.3f, 0.25f, z + 2.3f), V3(-0.1f, 0.0f, z), 50);
            s.handheld = 1.3f;
        });
        b.Say("n03", 0.2f);
        // sparks burst from the ceiling — dodge
        b.Shot(3.4f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            Vector3 eye = V3(-0.1f, 0.35f, 2.2f + t * 0.15f);
            float dodge = t > 1.0f ? Smooth((t - 1.0f) / 0.3f) : 0;
            Cam(s, eye + V3(-0.4f, -0.15f, 0) * dodge, V3(0.1f, 0.2f, 6.2f), 60);
            s.helmet = 1;
            s.hud = 1;
            s.hudWarn = 2;
            s.hudPulse = 128;
            s.crack = f.visorCracked ? 1.0f : 0;
            s.handheld = 1.4f;
            SparkBurst(s, V3(0.3f, 1.0f, 3.2f), t - 0.35f, 24, 131, 4.5f, 1.4f);
            s.pointPos = V3(0.3f, 0.9f, 3.2f);
            s.pointCol = V3(1, 0.55f, 0.2f) * (6.0f * fmaxf(0, 1 - fabsf(t - 0.5f) * 1.5f));
        });
        float spT = b.shotEnd - 3.4f;
        b.Sfx(spT + 0.35f, "sparks", 1.0f).Line(spT + 0.9f, "n06");
        QteDef q;
        q.type = QteType::Dir;
        q.left = true;
        q.window = 1.0f;
        q.slowmo = 0.2f;
        q.label = "УКЛОНИТЬСЯ";
        q.apply = [](Flags& f, bool ok) {};
        b.Qte(spT + 0.45f, q);
        // n4: reverse — from inside Beta, through the window
        b.Shot(5.4f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            Put(s.astro[0], V3(0.0f, -0.35f, 5.35f), BodyBasis(V3(0, 1, 0.1f), V3(0, 0, 1)), PWheel(t * 0.2f));
            Put(s.astro[1], V3(0.62f, -0.4f, 7.9f), BodyBasis(V3(0.1f, 1, 0), V3(-0.3f, 0, -1)), PFloat(t), 1);
            s.flashlight = 1;
            s.flashlightPos = V3(0.0f, 0.4f, 5.6f);
            s.flashlightDir = V3(0, 0, 1);
            Cam(s, V3(0.05f, 0.3f, 9.1f), V3(-0.12f, 0.08f, 6.2f), 40);
            s.focus = 2.8f;
            s.aperture = 9;
            s.handheld = 0.7f;
            s.betaMist = 1.2f;
        });
        b.Line(b.shotEnd - 5.4f + 1.0f, "n05");
        b.Line(b.shotEnd - 5.4f + 1.0f + b.Dur("n05") + 0.2f, "n04");
        // decision
        b.Shot(12.0f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            Put(s.astro[0], V3(0.0f, -0.35f, 5.3f), BodyBasis(V3(0, 1, 0.1f), V3(0, 0, 1)), PWheel(t * 0.1f));
            s.flashlight = 1;
            s.flashlightPos = V3(0.0f, 0.4f, 5.6f);
            s.flashlightDir = V3(0, 0, 1);
            Cam(s, V3(-0.75f, 0.55f, 3.2f + t * 0.06f), V3(0.3f, -0.05f, 5.9f), 48);
            s.handheld = 0.6f;
            s.saturation = 0.9f;
        });
        b.Choice(b.shotEnd - 12.0f + 0.6f, 7.0f,
                 {{"ОТКРЫТЬ ЛЮК", "Открыл люк, чтобы спасти Марину", "open", [](Flags& f) {}},
                  {"ИЗОЛИРОВАТЬ МОДУЛЬ", "Изолировал модуль «Бета»", "isolate", [](Flags& f) {}}},
                 1);
        b.Length(b.shotEnd - 6.4f);
        b.Next("isolate");
    }
    {
        auto b = D.Add("open");
        b.Call(0.0f, [](Director& d) { d.flags.marinaSaved = true; });
        b.Line(0.15f, "o01").Sfx(0.9f, "metal_groan", 1.0f);
        b.Shot(4.6f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            Put(s.astro[0], V3(0.0f, -0.35f, 5.3f), BodyBasis(V3(0, 1, 0.1f), V3(0, 0, 1)), PWheel(t * 4));
            s.flashlight = 1;
            s.flashlightPos = V3(0.0f, 0.4f, 5.6f);
            s.flashlightDir = V3(0, 0, 1);
            Cam(s, V3(0.74f, 0.18f, 5.9f), V3(-0.05f, -0.02f, 6.12f), 44);
            s.hatchOpen = 0.02f * sinf(t * 30);
            s.handheld = 2.0f;
            s.aperture = 6;
        });
        QteDef q;
        q.type = QteType::Mash;
        q.key = KEY_SPACE;
        q.count = 10;
        q.window = 3.6f;
        q.slowmo = 0.55f;
        q.label = "ОТКРЫТЬ ЛЮК";
        q.apply = [](Flags& f, bool ok) {
            if (!ok) f.gromovHurt = true;
        };
        b.Qte(1.1f, q);
        // the hatch gives way — air rushes towards the breach
        b.Shot(3.8f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.hatchOpen = EaseOut(t / 0.8f);
            s.betaMist = 2.0f;
            s.smoke = 0.7f;
            float reach = Smooth((t - 0.8f) / 1.6f);
            Put(s.astro[0], V3(0.0f, -0.3f, 5.4f + reach * 0.3f), BodyBasis(V3(0, 1, 0.1f), V3(0, 0, 1)), PoseLerp(PWheel(t), PGrab(), reach));
            Put(s.astro[1], V3(0.2f, -0.2f, Mix(8.0f, 6.9f, reach)), BodyBasis(V3(0.2f, 1, 0), V3(0, 0, -1)), PFlail(t * 0.5f), 1);
            Cam(s, V3(-0.8f, 0.6f, 4.0f), V3(0.2f, -0.1f, 6.6f), 44);
            s.shockBlur = 0.4f * (1 - Smooth(t / 1.5f));
            s.handheld = 2.0f;
        });
        b.Sfx(4.6f, "hatch", 1.0f).Sfx(4.6f, "explosion", 0.5f).Call(4.6f, [](Director& d) { d.Shake(1.3f); });
        b.Amb(4.6f, "amb_hiss", 0.8f, 0.2f);
        b.Line(5.6f, "o02");
        // together — the hatch slams shut behind them
        b.Shot(4.0f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.hatchOpen = 1.0f - Smooth((t - 0.3f) / 0.5f);
            s.smoke = 0.55f;
            Put(s.astro[0], V3(-0.2f, -0.25f, 4.6f - t * 0.1f), BodyBasis(V3(0, 1, 0.1f), V3(0.2f, 0, -1)), PGrab());
            Put(s.astro[1], V3(0.35f, -0.1f, 5.0f - t * 0.1f), BodyBasis(V3(-0.2f, 1, 0), V3(-0.3f, 0, -1)), PFloat(t), 1);
            Cam(s, V3(0.3f, 0.3f, 2.5f), V3(0.1f, 0.0f, 5.2f), 46);
            s.aperture = 4;
            s.handheld = 1.2f;
        });
        b.Sfx(8.9f, "hatch", 1.0f).Amb(8.9f, "amb_hiss", 0.1f, 0.3f).Call(8.9f, [](Director& d) { d.Shake(0.8f); });
        b.Line(9.4f, "o03", [](const Flags& f) { return !f.gromovHurt; });
        b.Line(9.4f, "o05", [](const Flags& f) { return f.gromovHurt; });
        b.Shot(6.0f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.smoke = 0.4f;
            Put(s.astro[0], V3(-0.2f, -0.3f, 3.8f), BodyBasis(V3(0.1f, 1, 0.1f), V3(0.3f, 0, -1)), f.gromovHurt ? PFloat(t * 0.5f) : PGrab());
            Put(s.astro[1], V3(0.3f, -0.1f, 3.9f), BodyBasis(V3(-0.2f, 1, 0), V3(-0.5f, 0, -1)), PHandOnGlass(), 1);
            Cam(s, Orbit(V3(0.05f, 0.1f, 3.9f), 1.05f, 0.2f, 150 + t * 7), V3(0.05f, 0.05f, 3.9f), 46);
            s.aperture = 5;
            s.handheld = 0.5f;
            s.hud = 0;
        });
        b.Line(12.6f, "o04", [](const Flags& f) { return !f.gromovHurt; });
        b.Line(14.4f, "o06", [](const Flags& f) { return f.gromovHurt; });
        b.Length(18.6f);
        b.Next("decay");
    }
    {
        auto b = D.Add("isolate");
        b.Music(0.0f, "mus_sad", 2.0f, 0.95f).Amb(0.0f, "amb_master_alarm", 0.0f, 2.0f);
        b.Line(0.3f, "x01", [](const Flags& f) { return !f.timedOut; });
        b.Shot(3.4f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            Put(s.astro[0], V3(0.3f, -0.2f, 4.7f), BodyBasis(V3(0, 1, 0.1f), V3(0.8f, 0, 0.6f)), MakePose(80, 20, 30, 20, 20, 40, 15, 25, 10, 20));
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Cam(s, head + V3(-0.55f, -0.08f, 1.05f), head + V3(0.15f, -0.08f, 0), 40);
            s.aperture = 7;
            s.handheld = 0.5f;
        });
        b.Line(3.4f, "x02");
        // the seal locks
        b.Shot(2.8f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.sealLock = Smooth((t - 0.3f) / 0.9f);
            Cam(s, V3(0.55f, 0.55f, 5.4f), V3(0.2f, 0.3f, 6.1f), 44);
            s.aperture = 5;
            s.handheld = 0.4f;
        });
        b.Sfx(3.8f, "hatch", 1.0f).Amb(3.8f, "amb_hiss", 0.0f, 3.0f);
        // Marina behind the glass
        b.Shot(7.8f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.sealLock = 1;
            s.alarmLight = 0.3f;
            s.redPulse *= 0.4f;
            s.betaMist = 0.7f;
            Put(s.astro[1], V3(0.06f, -0.6f, 6.66f + 0.02f * sinf(t)), BodyBasis(V3(0, 1, 0), V3(0, 0, -1)), PHandOnGlass(), 1);
            Cam(s, V3(0.02f + t * 0.004f, 0.06f, 5.45f + t * 0.03f), V3(0.0f, 0.06f, 6.6f), 26);
            s.focus = 1.2f;
            s.aperture = 7;
            s.handheld = 0.3f;
            s.saturation = 0.8f;
        });
        b.At(6.5f).Say("x03", 0.5f).Say("x04", 0.6f);
        // his visor
        b.Shot(3.8f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.alarmLight = 0.6f;
            s.sealLock = 1;
            Put(s.astro[0], V3(0.0f, -0.35f, 5.45f), BodyBasis(V3(0, 1, 0.05f), V3(0, 0, 1)), PHandOnGlass());
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Cam(s, head + V3(0.22f, -0.06f, 0.46f), head, 40);
            s.aperture = 8;
            s.saturation = 0.75f;
        });
        b.Say("x05", 0.4f);
        b.Sfx(b.cursor, "glitch", 0.3f).Amb(b.cursor, "amb_static", 0.25f, 0.2f).Amb(b.cursor + 1.2f, "amb_static", 0.0f, 1.5f);
        // exterior: the venting stops; silence
        b.Shot(5.2f, [](SceneState& s, float t, const Flags&) {
            Space(s, kER + 0.2f);
            s.sunDir = SunDir(22, 28);
            s.damage = 0.9f;
            s.betaVent = 1 - Smooth(t / 3.5f);
            Cam(s, V3(11, 3.5f, -19) + V3(t * 0.2f, 0, 0), V3(1, 0.5f, -7), 34);
            s.saturation = 0.8f;
        });
        b.Call(0.0f, [](Director& d) { d.flags.marinaSaved = false; });
        b.Next("decay");
    }

    // ------------------------------------------------------------------ PART IV — THE FALL
    {
        auto b = D.Add("decay");
        b.Music(0.0f, "mus_climax", 1.2f, 0.9f).Sfx(0.2f, "braam", 0.9f);
        b.Amb(0.0f, "amb_thruster", 0.25f, 1.0f).Amb(0.0f, "amb_alarm", 0.16f, 1.0f).Amb(0.0f, "amb_master_alarm", 0.1f, 1.0f);
        b.Chapter(0.8f, "ЧАСТЬ IV|ПАДЕНИЕ");
        // d1: the stuck engine fires — the station is braking
        b.Shot(7.6f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.25f);
            s.sunDir = SunDir(18, 40);
            s.damage = 0.9f;
            s.dishAngle = f.antennaFixed ? 1.0f : 0.0f;
            s.thrust = 1;
            s.altitude = 395 - t;
            s.stationRot = Euler(0, Deg(-2 - t * 0.4f), Deg(1));
            s.betaVent = f.marinaSaved ? 0.3f : 0.0f;
            Cam(s, V3(38, 12, 62) + V3(-t * 0.8f, 0, -t * 0.5f), V3(0, 1, 8), 38);
            s.handheld = 0.6f;
        });
        b.At(0.7f).Say("d01", 0.3f);
        // d2: close on the glowing nozzles
        b.Shot(5.6f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.26f);
            s.sunDir = SunDir(18, 40);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 385;
            s.stationRot = Euler(0, Deg(-5), Deg(1));
            Cam(s, V3(12.5f, 3.5f, 19.0f), V3(0.0f, 0.0f, 27.5f), 40);
            s.handheld = 1.4f;
            s.aperture = 3;
        });
        b.Say("d02", 0.4f);
        // d3: inside — the crew and the voice from the ground (or its absence)
        b.Shot(11.4f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.smoke = 0.3f;
            Put(s.astro[0], V3(-0.3f, -0.2f, -3.2f), BodyBasis(V3(0, 1, 0), V3(0.3f, 0, 1)), PFloat(t));
            if (f.marinaSaved) Put(s.astro[1], V3(0.4f, -0.1f, -2.6f), BodyBasis(V3(0, 1, 0), V3(-0.3f, 0, 1)), PFloat(t + 2), 1);
            Cam(s, V3(0.35f, 0.3f, -0.6f) + V3(0, 0, -t * 0.04f), V3(0.0f, 0.0f, -3.0f), 46);
            s.aperture = 4;
            s.handheld = 0.8f;
        });
        float d3 = b.cursor;
        b.Line(d3, "d03", Antenna).Line(d3, "d04", NoAntenna);
        b.Line(d3 + b.Dur("d04") + 0.3f, "d07", NoAntenna);
        // jolt — a second impact throws them against the wall
        b.Shot(4.2f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.smoke = 0.5f;
            float k = Smooth((t - 0.3f) / 0.6f);
            Put(s.astro[0], V3(-0.3f + k * 0.5f, -0.2f + k * 0.3f, -3.0f), BodyBasis(Norm(V3(k * 0.6f, 1, 0)), V3(0.3f, 0, 1)), PoseLerp(PFloat(t), PFlail(t), k));
            if (f.marinaSaved) Put(s.astro[1], V3(0.4f + k * 0.3f, -0.1f + k * 0.4f, -2.4f), BodyBasis(Norm(V3(k * 0.5f, 1, 0)), V3(-0.3f, 0, 1)), PoseLerp(PFloat(t + 2), PFlail(t + 1), k), 1);
            Cam(s, V3(0.35f, 0.3f, -1.0f), V3(0.0f, 0.1f, -3.0f), 50);
            s.handheld = 2.0f;
            s.mainLight = t < 0.3f ? 0.22f : 0.1f + 0.1f * Noise1(t * 20.0f);
        });
        float jolt = b.shotEnd - 4.2f;
        b.Sfx(jolt + 0.3f, "impact_big", 1.0f).Sfx(jolt + 0.4f, "metal_groan", 0.8f).Call(jolt + 0.3f, [](Director& d) { d.Shake(1.8f); });
        b.Line(jolt + 0.6f, "d08");
        QteDef q;
        q.type = QteType::Press;
        q.key = KEY_SPACE;
        q.window = 1.2f;
        q.slowmo = 0.25f;
        q.label = "ДЕРЖАТЬСЯ";
        q.apply = [](Flags& f, bool ok) {
            if (!ok) f.gromovHurt = true;
        };
        b.Qte(jolt + 1.2f, q);
        // d4: faces — Marina offers the way out, or Gromov alone
        b.Shot(5.0f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            s.smoke = 0.35f;
            if (f.marinaSaved) {
                Put(s.astro[1], V3(0.3f, -0.2f, -2.6f), BodyBasis(V3(0, 1, 0), V3(-0.5f, 0, 1)), PFloat(t), 1);
                Vector3 head = s.astro[1].pos + s.astro[1].rot.Apply(V3(0, 0.66f, 0.04f));
                Cam(s, head + V3(-0.35f, -0.05f, 0.75f), head, 34);
            } else {
                Put(s.astro[0], V3(-0.2f, -0.2f, -2.8f), BodyBasis(V3(0, 1, 0), V3(0.4f, 0, 1)), PFloat(t));
                Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
                Cam(s, head + V3(0.45f, -0.1f, 0.7f), head, 34);
            }
            s.aperture = 8;
            s.handheld = 0.5f;
        });
        float d4 = b.shotEnd - 5.0f;
        b.Line(d4 + 0.3f, "d05", Saved).Line(d4 + 0.3f, "d06", NotSaved);
        // decision: capsule on the left, the burning engine on the right
        b.Shot(14.0f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.28f);
            s.sunDir = SunDir(18, 40);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 375 - t;
            s.stationRot = Euler(0, Deg(-6), Deg(1));
            s.heat = 0.1f;
            s.heatDir = V3(0, -0.3f, 1);
            Cam(s, V3(-52 + t * 0.8f, 7, 4), V3(0, 0.5f, 4), 44);
            s.saturation = 0.9f;
        });
        b.Choice(b.shotEnd - 14.0f + 0.4f, 8.0f,
                 {{"УЙТИ НА КАПСУЛЕ", "Эвакуировался на капсуле «Стриж»", "evac", [](Flags& f) { f.evacuated = true; }},
                  {"ПЕРЕКРЫТЬ КЛАПАН", "Остался перекрыть клапан", "stay", [](Flags& f) {}}},
                 0);
        b.Length(b.shotEnd - 13.0f);
        b.Next("evac");
    }

    // ------------------------------------------------------------------ ENDING A: evacuation
    {
        auto b = D.Add("evac");
        b.Call(0.0f, [](Director& d) {
            d.flags.evacuated = true;
            d.flags.ending = "ПАДАЮЩАЯ ЗВЕЗДА";
            d.flags.endingIndex = 2;
        });
        b.Line(0.2f, "e01");
        b.Shot(4.6f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.3f);
            s.sunDir = SunDir(18, 40);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 360;
            s.stationRot = Euler(0, Deg(-6), Deg(1));
            Cam(s, V3(6.5f, 2.5f, -21.0f), V3(0, 0, -17.0f), 40);
            s.handheld = 1.2f;
        });
        QteDef q;
        q.type = QteType::Press;
        q.key = KEY_SPACE;
        q.window = 2.0f;
        q.slowmo = 0.4f;
        q.label = "ОТСТЫКОВКА";
        q.apply = [](Flags& f, bool ok) {};
        b.Qte(1.6f, q);
        b.Sfx(2.6f, "undock", 1.0f).Call(2.6f, [](Director& d) { d.Shake(0.7f); });
        // the capsule drifts away from the dying station
        b.Shot(7.0f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.31f);
            s.sunDir = SunDir(18, 40);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 355;
            s.heat = 0.15f + t * 0.02f;
            s.heatDir = V3(0, -0.3f, 1);
            s.stationRot = Euler(0, Deg(-6 - t * 0.5f), Deg(1));
            s.capsuleMode = 2;
            s.capsulePos = V3(0, -t * 0.8f, -16.6f - t * 1.6f);
            s.capsuleRot = Euler(0, Deg(t * 1.5f), 0);
            Cam(s, s.capsulePos + V3(4.5f, 2.0f, -9.0f), s.capsulePos + V3(0, 1.5f, 8.0f) * 0.5f + V3(0, 0, 4), 44);
            s.aperture = 3;
        });
        b.Line(4.9f, "e02");
        b.Amb(11.0f, "amb_thruster", 0.0f, 2.0f).Amb(11.0f, "amb_alarm", 0.0f, 1.0f).Amb(11.0f, "amb_master_alarm", 0.0f, 1.0f);
        b.Amb(11.6f, "amb_reentry", 0.42f, 1.2f);
        // re-entry plasma
        b.Shot(8.0f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.5f);
            s.sunDir = SunDir(10, 60);
            s.stationOn = false;
            s.altitude = 95 - t * 3;
            s.capsuleMode = 2;
            s.capsulePos = V3(0, 0, 0);
            s.capsuleRot = LookBasis(V3(0.0f, 0.45f, 1.0f), V3(0, 1, 0));
            s.plasma = Smooth(t / 1.5f);
            s.heatDir = V3(0.0f, -0.45f, -1.0f);
            Cam(s, V3(10.0f, -2.5f, -9.0f) + V3(0, sinf(t * 0.4f) * 0.5f, t * 0.2f), V3(0, 0.8f, 3.0f), 48);
            s.handheld = 2.2f;
            s.handFreq = 1.6f;
            s.exposure = 0.9f;
            s.stars = 0.1f;
        });
        b.Line(11.9f, "e08").Call(12.0f, [](Director& d) { d.Shake(1.0f, 0.5f); });
        QteDef h;
        h.type = QteType::Hold;
        h.key = KEY_E;
        h.hold = 1.4f;
        h.window = 3.6f;
        h.slowmo = 0.6f;
        h.label = "СТАБИЛИЗИРОВАТЬ КАПСУЛУ";
        h.apply = [](Flags& f, bool ok) {};
        b.Qte(14.8f, h);
        b.Line(17.6f, "e03", Antenna).Line(17.6f, "e04", [](const Flags& f) { return !f.antennaFixed && f.marinaSaved; });
        b.Line(17.6f, "e04b", [](const Flags& f) { return !f.antennaFixed && !f.marinaSaved; });
        // dawn over the ocean: the parachute and the burning station
        b.Music(19.4f, "mus_sad", 1.5f, 0.9f).Amb(19.4f, "amb_reentry", 0.0f, 0.4f).Sfx(19.5f, "parachute", 0.9f);
        b.Amb(19.4f, "amb_ocean", 0.45f, 2.0f).Amb(19.4f, "amb_wind", 0.3f, 2.0f);
        b.Shot(12.4f, [](SceneState& s, float t, const Flags& f) {
            Ground(s);
            s.chuteOn = 1;
            s.chutePos = V3(-30, 120 - t * 5.5f, 190);
            s.meteor = Clamp01((t - 1.5f) / 9.0f);
            s.meteorSplit = Clamp01((t - 4.0f) / 5.0f);
            Cam(s, V3(0, 2.2f + 0.3f * sinf(t * 0.8f), 0), V3(-14, 62 - t * 1.5f, 200), 48);
            s.handheld = 0.4f;
        });
        b.Line(22.2f, "e05", Saved).Line(27.2f, "e06", Saved).Line(22.3f, "e07", NotSaved);
        b.Sfx(31.9f, "splash", 0.8f);
        b.Line(32.2f, "e10", Antenna);
        b.Shot(6.4f, [](SceneState& s, float t, const Flags& f) {
            Ground(s);
            s.sunElev = 4.0f;
            s.chuteOn = 1;
            s.chutePos = V3(-60, 14 - t * 2.0f, 260);
            Cam(s, V3(0, 1.6f, 0), V3(-40, 8, 250), 38);
            s.fade = Smooth((t - 4.2f) / 2.0f);
            s.endCard = Clamp01((t - 1.0f) / 1.0f);
        });
        b.Next("END");
    }

    // ------------------------------------------------------------------ ENDING B: stay and close the valve
    {
        auto b = D.Add("stay");
        b.Line(0.2f, "v01");
        b.Shot(2.8f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            Put(s.astro[0], V3(-0.2f, -0.2f, -2.8f), BodyBasis(V3(0, 1, 0), V3(0.4f, 0, 1)), PFloat(t));
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Cam(s, head + V3(0.5f, -0.2f, 0.9f), head, 36);
            s.aperture = 7;
        });
        b.Next([](const Flags& f) { return f.marinaSaved ? std::string("stay_m") : std::string("valve"); });
    }
    {
        auto b = D.Add("stay_m");
        b.Line(0.1f, "v02").Line(0.1f + b.Dur("v02") + 0.4f, "v03");
        b.Shot(7.0f, [](SceneState& s, float t, const Flags& f) {
            Interior(s, 1);
            Put(s.astro[0], V3(-0.3f, -0.2f, -2.8f), BodyBasis(V3(0, 1, 0), V3(0.6f, 0, 0.8f)), PFloat(t));
            Put(s.astro[1], V3(0.45f, -0.1f, -2.2f), BodyBasis(V3(0, 1, 0), V3(-0.7f, 0, -0.6f)), PHandOnGlass(), 1);
            Cam(s, V3(0.6f, 0.35f, -4.6f), V3(0.05f, 0.1f, -2.5f), 40);
            s.aperture = 4;
            s.handheld = 0.5f;
        });
        b.Next("valve");
    }
    {
        auto b = D.Add("valve");
        b.Amb(0.0f, "amb_thruster", 0.38f, 1.0f).Amb(0.0f, "amb_suit", 0.3f, 1.0f).Amb(0.0f, "amb_breath_fast", 0.5f, 1.0f);
        b.Amb(0.0f, "amb_station", 0.0f, 1.0f).Amb(0.0f, "amb_alarm", 0.0f, 1.0f);
        b.Call(0.0f, [](Director& d) { d.flags.valveScore = 0; });
        // climbing along the service module into the heat
        b.Shot(6.2f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.33f);
            s.sunDir = SunDir(18, 40);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 340;
            s.heat = 0.12f;
            s.heatDir = V3(0, -0.3f, 1);
            s.stationRot = Euler(0, Deg(-7), Deg(1));
            float z = 15.0f + t * 0.9f;
            Put(s.astro[0], V3(0.2f, 2.65f, z), BodyBasis(V3(0, 0.2f, 1), V3(0, -1, 0)), PCrawl(t));
            s.astro[0].pos = s.stationRot.Apply(s.astro[0].pos);
            s.astro[0].rot = s.stationRot.Mul(s.astro[0].rot);
            Cam(s, s.stationRot.Apply(V3(3.2f, 4.2f, z - 4.5f)), s.stationRot.Apply(V3(0, 2.4f, z + 3)), 46);
            s.handheld = 1.2f;
            s.hud = 0;
        });
        b.Line(0.4f, "v12");
        // a jet of burning propellant — dodge (first person)
        b.Shot(3.2f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.335f);
            s.sunDir = SunDir(18, 40);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 335;
            s.heat = 0.14f;
            s.heatDir = V3(0, -0.3f, 1);
            float dodge = t > 1.0f ? Smooth((t - 1.0f) / 0.3f) : 0;
            Cam(s, V3(0.3f - dodge * 0.8f, 3.1f, 20.2f), V3(0.6f - dodge * 0.5f, 1.8f, 24.5f), 62);
            s.helmet = 1;
            s.hud = 1;
            s.hudWarn = 3;
            s.hudPulse = 152;
            s.crack = f.visorCracked ? 1.0f : 0;
            s.handheld = 1.6f;
            SparkBurst(s, V3(1.4f, 2.2f, 22.3f), t - 0.3f, 24, 151, 6.0f, 1.3f);
            s.pointPos = V3(1.3f, 2.3f, 22.0f);
            s.pointCol = V3(1, 0.5f, 0.2f) * (25.0f * fmaxf(0, 1 - fabsf(t - 0.6f)));
        });
        QteDef a;
        a.type = QteType::Dir;
        a.left = true;
        a.window = 1.0f;
        a.slowmo = 0.18f;
        a.label = "УКЛОНИТЬСЯ";
        a.apply = [](Flags& f, bool ok) { f.valveScore += ok ? 1 : 0; };
        b.Qte(6.6f, a);
        b.Sfx(6.5f, "explosion", 0.7f).Call(6.5f, [](Director& d) { d.Shake(1.2f); });
        // the valve wheel glows red
        b.Shot(5.4f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.34f);
            s.sunDir = SunDir(18, 40);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 330;
            s.heat = 0.16f;
            s.heatDir = V3(0, -0.3f, 1);
            Put(s.astro[0], V3(0.9f, 2.4f, 21.4f), BodyBasis(V3(0, 1, 0.3f), V3(0.3f, -0.3f, 1)), PWheel(t * 2));
            Cam(s, V3(2.4f, 3.3f, 20.0f), V3(0.9f, 2.6f, 22.2f), 38);
            s.aperture = 6;
            s.handheld = 1.3f;
            s.pointPos = V3(1.2f, 2.2f, 22.4f);
            s.pointCol = V3(1, 0.35f, 0.1f) * 6.0f;
        });
        QteDef h;
        h.type = QteType::Hold;
        h.key = KEY_E;
        h.hold = 1.4f;
        h.window = 4.0f;
        h.slowmo = 0.6f;
        h.label = "ПЕРЕКРЫТЬ ВЕНТИЛЬ";
        h.apply = [](Flags& f, bool ok) { f.valveScore += ok ? 1 : 0; };
        b.Qte(10.0f, h);
        b.Sfx(10.1f, "valve", 1.0f).Line(10.5f, "v13");
        b.Shot(4.6f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.345f);
            s.sunDir = SunDir(18, 40);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 325;
            s.heat = 0.2f;
            s.heatDir = V3(0, -0.3f, 1);
            Put(s.astro[0], V3(0.9f, 2.4f, 21.4f), BodyBasis(V3(0, 1, 0.3f), V3(0.3f, -0.3f, 1)), PWheel(t * 6));
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Cam(s, head + V3(1.05f, 0.3f, 0.15f), head + V3(-0.05f, -0.45f, 0.55f), 36);
            s.aperture = 7;
            s.handheld = 2.2f;
            s.handFreq = 2.0f;
            s.pointPos = V3(1.2f, 2.2f, 22.4f);
            s.pointCol = V3(1, 0.35f, 0.1f) * 6.0f;
        });
        QteDef m;
        m.type = QteType::Mash;
        m.key = KEY_SPACE;
        m.count = 9;
        m.window = 3.3f;
        m.slowmo = 0.5f;
        m.label = "ДОЖАТЬ";
        m.apply = [](Flags& f, bool ok) {
            f.valveScore += ok ? 1 : 0;
            int need = f.gromovHurt ? 3 : 2;
            f.valveFixed = f.valveScore >= need;
        };
        b.Qte(15.3f, m);
        b.Sfx(15.4f, "metal_groan", 0.9f);
        b.Length(19.5f);
        b.Next([](const Flags& f) { return f.valveFixed ? std::string("saved") : std::string("lost"); });
    }
    {
        auto b = D.Add("saved");
        b.Call(0.0f, [](Director& d) {
            d.flags.ending = d.flags.marinaSaved ? "РАССВЕТ ВДВОЁМ" : "ОДИНОКИЙ РАССВЕТ";
            d.flags.endingIndex = d.flags.marinaSaved ? 0 : 1;
        });
        b.Amb(0.4f, "amb_thruster", 0.0f, 1.5f).Amb(0.4f, "amb_breath_fast", 0.0f, 2.0f).Amb(0.4f, "amb_breath", 0.25f, 2.0f);
        b.Music(0.3f, "", 2.0f);
        // the plume sputters and dies
        b.Shot(5.0f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.35f);
            s.sunDir = SunDir(18, 40);
            s.damage = 0.9f;
            s.altitude = 322;
            float k = Smooth(t / 1.8f);
            s.thrust = (1 - k) * (0.6f + 0.4f * sinf(t * 40));
            s.heat = 0.2f * (1 - k);
            s.heatDir = V3(0, -0.3f, 1);
            Put(s.astro[0], V3(0.9f, 2.4f, 21.4f), BodyBasis(V3(0, 1, 0.3f), V3(0.3f, -0.3f, 1)), PIdle(t));
            Cam(s, V3(6.0f, 4.5f, 30.0f), V3(0.6f, 1.2f, 22.5f), 36);
        });
        b.Line(1.6f, "v04");
        b.Music(5.2f, "mus_hope", 1.0f, 0.95f);
        b.Shot(6.4f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.36f);
            s.sunDir = SunDir(-18.6f, 4);
            s.damage = 0.9f;
            s.dishAngle = f.antennaFixed ? 1.0f : 0.0f;
            s.altitude = 322;
            Vector3 sd = SunDir(-17.2f, 4);
            Vector3 cp = V3(0, 3, 4) - sd * 95.0f + V3(-8.0f + t * 0.6f, 4.0f, 0);
            Cam(s, cp, cp + sd * 100.0f + V3(0, 6.0f, 0), 30);
            s.stars = 0.8f;
            s.exposure = 1.3f;
        });
        b.Line(6.0f, "v05", Antenna).Line(6.0f, "v14", NoAntenna);
        // sunrise — the seventeenth
        b.Shot(15.0f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.37f);
            s.sunDir = SunDir(Mix(-18.9f, -16.3f, EaseOut(t / 8.0f)), 2);
            s.damage = 0.9f;
            s.altitude = 322;
            s.panelAngle = 0.1f;
            s.stars = 0.5f;
            Put(s.astro[0], V3(0.3f, 2.9f, 20.5f), BodyBasis(V3(0, 1, 0.2f), V3(0, -0.1f, 1)), PIdle(t));
            Cam(s, V3(-1.6f, 3.3f, 16.2f) + V3(t * 0.03f, 0, -t * 0.08f), V3(0.6f, 0.2f, 60), 40);
            s.focus = 4.6f;
            s.aperture = 3;
            s.fade = Smooth((t - 12.5f) / 2.5f);
            s.endCard = Clamp01((t - 9.0f) / 1.2f);
            s.flare = 1.3f;
        });
        b.Sfx(14.2f, "title", 0.6f);
        b.Line(12.6f, "v06", Saved).Line(12.6f + 3.4f, "v07", Saved);
        b.Line(12.8f, "v08", NotSaved);
        b.Line(19.2f, "v15");
        b.Next("END");
    }
    {
        auto b = D.Add("lost");
        b.Call(0.0f, [](Director& d) {
            d.flags.ending = "ПОСЛЕДНИЙ СИГНАЛ";
            d.flags.endingIndex = 3;
        });
        b.Music(0.0f, "mus_tragic", 2.0f, 0.95f).Amb(0.0f, "amb_reentry", 0.25f, 3.0f);
        b.Shot(6.8f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.4f);
            s.sunDir = SunDir(-11.0f, 5);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 160 - t * 4;
            s.heat = 0.35f + t * 0.06f;
            s.heatDir = V3(0, -0.35f, 1);
            s.stationRot = Euler(Deg(t * 0.8f), Deg(-12 - t * 0.6f), Deg(4));
            Cam(s, V3(-60, 22, -70) + V3(t * 1.2f, 0, t * 1.5f), V3(0, 0, 6), 36);
            s.handheld = 1.0f;
            s.stars = 0.4f;
        });
        b.Line(0.5f, "v09");
        b.Shot(6.6f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.41f);
            s.sunDir = SunDir(-11.0f, 5);
            s.thrust = 1;
            s.damage = 0.9f;
            s.altitude = 125;
            s.heat = 0.8f;
            s.heatDir = V3(0, -0.35f, 1);
            Put(s.astro[0], V3(0.9f, 2.6f, 20.4f), BodyBasis(V3(0, 1, 0.1f), V3(0.2f, -0.5f, 1)), PIdle(t));
            Vector3 head = s.astro[0].pos + s.astro[0].rot.Apply(V3(0, 0.66f, 0.04f));
            Cam(s, head + V3(-0.35f, 0.05f, 0.8f), head, 32);
            s.aperture = 8;
            s.handheld = 1.2f;
            s.crack = f.visorCracked ? 0.7f : 0;
            s.flare = 1.2f;
        });
        b.Line(7.2f, "v10");
        b.Shot(4.4f, [](SceneState& s, float t, const Flags& f) {
            Space(s, kER + 0.42f);
            s.sunDir = SunDir(-11.0f, 5);
            s.damage = 0.9f;
            s.thrust = 1;
            s.altitude = 118;
            s.heat = 0.95f;
            s.heatDir = V3(0, -0.35f, 1);
            s.capsuleMode = f.marinaSaved ? 2 : 1;
            s.capsulePos = V3(0, -2 - t * 1.0f, -16.6f - t * 3.0f);
            Cam(s, V3(20, -6, -46), V3(0, -1, -12), 34);
            s.handheld = 1.0f;
        });
        b.Line(13.9f, "v11", Saved).Line(13.9f, "v16", [](const Flags& f) { return !f.marinaSaved && f.antennaFixed; });
        b.Amb(17.8f, "amb_reentry", 0.0f, 1.0f).Amb(17.8f, "amb_ocean", 0.4f, 2.0f).Amb(17.8f, "amb_wind", 0.3f, 2.0f);
        b.Amb(17.8f, "amb_suit", 0.0f, 1.0f).Amb(17.8f, "amb_breath_fast", 0.0f, 1.0f).Amb(17.8f, "amb_thruster", 0.0f, 1.0f);
        b.Shot(11.0f, [](SceneState& s, float t, const Flags& f) {
            Ground(s);
            s.chuteOn = f.marinaSaved ? 1.0f : 0.0f;
            s.chutePos = V3(40, 150 - t * 5, 260);
            s.meteor = Clamp01(t / 9.0f);
            s.meteorSplit = Clamp01((t - 2.0f) / 4.0f);
            Cam(s, V3(0, 2.0f, 0), V3(-10, 58, 200), 55);
            s.fade = Smooth((t - 8.5f) / 2.5f);
            s.endCard = Clamp01((t - 5.0f) / 1.0f);
            s.saturation = 0.9f;
        });
        b.Line(18.6f, "v16", [](const Flags& f) { return f.marinaSaved && f.antennaFixed; });
        b.Next("END");
    }
}
