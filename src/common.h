// ZARYA — shared types: scene state that the director fills every frame and
// the renderer turns into shader uniforms, plus small math helpers.
#pragma once

#include <cmath>
#include <string>
#include <vector>

#include "raylib.h"
#include "raymath.h"

// ------------------------------------------------------------------ math
inline Vector3 V3(float x, float y, float z) { return Vector3{x, y, z}; }
inline Vector3 operator-(const Vector3& v) { return Vector3{-v.x, -v.y, -v.z}; }
inline Vector3 operator*(float s, const Vector3& v) { return Vector3{v.x * s, v.y * s, v.z * s}; }
inline float Dot(Vector3 a, Vector3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
inline Vector3 Cross(Vector3 a, Vector3 b) { return Vector3CrossProduct(a, b); }
inline float Len(Vector3 a) { return sqrtf(Dot(a, a)); }
inline Vector3 Norm(Vector3 a) {
    float l = Len(a);
    return l > 1e-8f ? a * (1.0f / l) : V3(0, 0, 1);
}
inline float Clamp01(float x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }
inline float Mix(float a, float b, float t) { return a + (b - a) * t; }
inline Vector3 Mix(Vector3 a, Vector3 b, float t) { return a + (b - a) * t; }
inline float Smooth(float t) {
    t = Clamp01(t);
    return t * t * (3 - 2 * t);
}
inline float Smoother(float t) {
    t = Clamp01(t);
    return t * t * t * (t * (t * 6 - 15) + 10);
}
inline float EaseOut(float t) {
    t = Clamp01(t);
    return 1 - (1 - t) * (1 - t) * (1 - t);
}
inline float EaseIn(float t) {
    t = Clamp01(t);
    return t * t * t;
}
inline float Range(float t, float a, float b) { return Clamp01((t - a) / (b - a)); }
inline float Hash1(float n) {
    float s = sinf(n * 127.1f) * 43758.5453f;
    return s - floorf(s);
}
// Smooth 1D noise in [-1, 1]
inline float Noise1(float x) {
    float i = floorf(x), f = x - i;
    float u = f * f * (3 - 2 * f);
    return Mix(Hash1(i), Hash1(i + 1), u) * 2 - 1;
}
constexpr float PI_F = 3.14159265358979f;
inline float Deg(float d) { return d * PI_F / 180.0f; }

// Rotation helpers returning a 3x3 basis stored as three column vectors.
struct Basis {
    Vector3 x{1, 0, 0}, y{0, 1, 0}, z{0, 0, 1};
    Vector3 Apply(Vector3 v) const { return x * v.x + y * v.y + z * v.z; }
    Vector3 ApplyT(Vector3 v) const { return V3(Dot(x, v), Dot(y, v), Dot(z, v)); }
    Basis Mul(const Basis& b) const {
        Basis r;
        r.x = Apply(b.x);
        r.y = Apply(b.y);
        r.z = Apply(b.z);
        return r;
    }
};
Basis RotX(float a);
Basis RotY(float a);
Basis RotZ(float a);
Basis Euler(float yaw, float pitch, float roll);  // Y * X * Z
Basis LookBasis(Vector3 forward, Vector3 up);      // z = forward

// ------------------------------------------------------------------ scene
enum SetId { SET_BLACK = 0, SET_SPACE, SET_INTERIOR, SET_GROUND };

// Astronaut pose in degrees; the renderer converts it to joint positions.
struct Pose {
    float lShPitch = 10, lShRoll = 12, lElbow = 25;
    float rShPitch = 10, rShRoll = 12, rElbow = 25;
    float lHip = 8, lKnee = 18, rHip = 4, rKnee = 12;
    float lReach = 0, rReach = 0;  // shoulder yaw (arm swing inwards)
};
Pose PoseLerp(const Pose& a, const Pose& b, float t);

struct Astro {
    bool on = false;
    Vector3 pos{0, 0, 0};
    Basis rot;
    Pose pose;
    int style = 0;  // 0 = Orlan EVA suit with backpack, 1 = Sokol (Marina)
};

struct SceneState {
    int set = SET_BLACK;

    // camera
    Vector3 camPos{0, 0, -10}, camTarget{0, 0, 0};
    Vector3 camUp{0, 1, 0};
    float fov = 45;  // vertical, degrees
    float roll = 0;  // degrees
    float handheld = 0, handFreq = 1;
    float focus = -1;  // metres; <0 = auto (distance to target)
    float aperture = 0;  // CoC scale in pixels at 1080p

    // grading / post
    float exposure = 1.0f;
    Vector3 tint{1, 1, 1};
    float saturation = 1.0f, contrast = 1.0f;
    Vector3 lift{0, 0, 0}, gain{1, 1, 1};
    float vignette = 0.35f, grain = 0.05f, aberration = 0.0015f, bloom = 0.55f;
    float fade = 0;  // 1 = black
    float flash = 0;
    Vector3 flashCol{1, 1, 1};
    float crack = 0;      // visor crack overlay amount
    float helmet = 0;     // first person visor frame
    float redPulse = 0;   // alarm vignette
    float shockBlur = 0;  // radial blur
    float flare = 1.0f;   // lens flare strength

    // space set
    Vector3 sunDir{0, 0.2f, 1};
    Vector3 sunCol{1, 1, 1};
    float sunSize = 1.0f;
    float altitude = 408.0f;  // km
    float earthRot = 0;
    float stars = 1.0f;
    float earthshine = 1.0f;
    bool stationOn = true;
    Basis stationRot;
    Vector3 stationPos{0, 0, 0};
    float panelAngle = 0;
    float damage = 0;    // torn solar blanket
    float betaVent = 0;  // air venting from module Beta
    float thrust = 0;    // stuck engine plume
    float heat = 0;      // re-entry heating of the station
    Vector3 heatDir{0, 0, 1};
    int capsuleMode = 1;  // 0 hidden, 1 docked, 2 free flying
    Vector3 capsulePos{0, 0, -20};
    Basis capsuleRot;
    float plasma = 0;      // capsule re-entry plasma
    float dishAngle = 0;   // 0 = loose, 1 = fixed
    float airlock = 0;     // outer airlock hatch: 0 open, 1 closed
    float navLights = 1;
    Vector4 burst{0, 0, 0, 0};  // far satellite break-up: xyz dir*dist, w = age
    std::vector<Vector4> debris;     // xyz pos, w size
    std::vector<Vector4> debrisVel;  // xyz vel, w heat
    std::vector<Vector4> sparks;     // xyz pos, w intensity
    Vector3 pointPos{0, 0, 0};
    Vector3 pointCol{0, 0, 0};
    Astro astro[2];

    // interior set
    float mainLight = 1.0f;
    float alarmLight = 0;
    float smoke = 0.2f;
    float hatchOpen = 0;
    float betaMist = 0;
    float sunShaft = 0;
    float flashlight = 0;
    Vector3 flashlightPos{0, 0, 0}, flashlightDir{0, 0, 1};
    float floaters = 1.0f;
    float sealLock = 0;  // bolts of the Beta seal
    float airlockIn = 0; // inner airlock hatch at the node end
    float corridorLen = 0;

    // ground set
    Vector3 chutePos{0, 300, 0};
    float chuteOn = 0;
    float meteor = -1;     // progress of burning station streak, <0 hidden
    float meteorSplit = 0;
    float sunElev = 2.0f;  // degrees above horizon
    float sunAz = 0;

    // HUD / UI hints
    float hud = 0;
    float hudO2 = 92, hudPress = 29.4f, hudPulse = 84;
    int hudWarn = 0;  // 0 none, 1 suit leak, 2 hull breach, 3 heat
    float titleCard = 0;  // big title over the intro
    float endCard = 0;    // ending name over the final shot
};

// ------------------------------------------------------------------ story state
struct Flags {
    bool antennaFixed = false;
    bool railGrabbed = true;
    bool visorCracked = false;
    bool marinaSaved = false;
    bool gromovHurt = false;
    bool airlockClean = true;
    bool evacuated = false;
    bool valveFixed = false;
    bool timedOut = false;  // the last choice ran out of time
    int valveScore = 0;
    int qteOk = 0, qteTotal = 0;
    std::vector<std::string> decisions;
    std::string ending;
    int endingIndex = -1;
};

std::string AssetPath(const std::string& rel);
