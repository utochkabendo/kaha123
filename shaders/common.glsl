// ---------------------------------------------------------------------------
// ZARYA — shared shader code (included by the scene shaders)
// ---------------------------------------------------------------------------
#define PI 3.14159265
#define TAU 6.28318531

uniform vec2 uRes;
uniform float uTime;
uniform vec3 uCamPos;
uniform vec3 uCamFwd;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform float uTanHalfFov;

float sat(float x) { return clamp(x, 0.0, 1.0); }
vec3 sat3(vec3 x) { return clamp(x, 0.0, 1.0); }

// --- hashes (Dave Hoskins, "hash without sine")
float hash11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float hash13(vec3 p3) { p3 = fract(p3 * 0.1031); p3 += dot(p3, p3.zyx + 31.32); return fract((p3.x + p3.y) * p3.z); }
vec2 hash22(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973)); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.xx + p3.yz) * p3.zy); }
vec3 hash33(vec3 p3) { p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973)); p3 += dot(p3, p3.yxz + 33.33); return fract((p3.xxy + p3.yxx) * p3.zyx); }

// --- value noise
float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i + vec3(0, 0, 0)), hash13(i + vec3(1, 0, 0)), f.x),
                   mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
                   mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float vnoise2(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}
float fbm3(vec3 p, int oct) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= oct) break;
        s += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}
float fbm2(vec2 p, int oct) {
    float a = 0.5, s = 0.0;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 8; i++) {
        if (i >= oct) break;
        s += a * vnoise2(p);
        p = m * p + vec2(3.1, 1.7);
        a *= 0.5;
    }
    return s;
}

mat2 rot2(float a) { float c = cos(a), s = sin(a); return mat2(c, s, -s, c); }

// --- SDF primitives
float sdSphere(vec3 p, float r) { return length(p) - r; }
float sdBox(vec3 p, vec3 b) { vec3 q = abs(p) - b; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0); }
float sdRoundBox(vec3 p, vec3 b, float r) { vec3 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}
// capped cylinder along z, from z0 to z1, radius r
float sdCylZ(vec3 p, float z0, float z1, float r) {
    float hz = 0.5 * (z1 - z0);
    vec2 d = abs(vec2(length(p.xy), p.z - (z0 + hz))) - vec2(r, hz);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}
float sdCylX(vec3 p, float x0, float x1, float r) { return sdCylZ(p.zyx, x0, x1, r); }
float sdCylY(vec3 p, float y0, float y1, float r) { return sdCylZ(p.xzy, y0, y1, r); }
float sdTorusZ(vec3 p, float R, float r) { vec2 q = vec2(length(p.xy) - R, p.z); return length(q) - r; }
float sdBoxFrame(vec3 p, vec3 b, float e) {
    p = abs(p) - b;
    vec3 q = abs(p + e) - e;
    return min(min(length(max(vec3(p.x, q.y, q.z), 0.0)) + min(max(p.x, max(q.y, q.z)), 0.0),
                   length(max(vec3(q.x, p.y, q.z), 0.0)) + min(max(q.x, max(p.y, q.z)), 0.0)),
               length(max(vec3(q.x, q.y, p.z), 0.0)) + min(max(q.x, max(q.y, p.z)), 0.0));
}
// cone frustum along z: radius r0 at z0, r1 at z1
float sdConeZ(vec3 p, float z0, float z1, float r0, float r1) {
    float h = 0.5 * (z1 - z0);
    vec3 q = vec3(p.xy, p.z - (z0 + h));
    vec2 w = vec2(length(q.xy), q.z);
    vec2 k1 = vec2(r1, h);
    vec2 k2 = vec2(r1 - r0, 2.0 * h);
    vec2 ca = vec2(w.x - min(w.x, (w.y < 0.0) ? r0 : r1), abs(w.y) - h);
    vec2 cb = w - k1 + k2 * clamp(dot(k1 - w, k2) / dot(k2, k2), 0.0, 1.0);
    float s = (cb.x < 0.0 && ca.y < 0.0) ? -1.0 : 1.0;
    return s * sqrt(min(dot(ca, ca), dot(cb, cb)));
}
float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}
vec2 opU(vec2 a, vec2 b) { return a.x < b.x ? a : b; }

// Ray / box slab test in local space: returns (tnear, tfar)
vec2 rayBox(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax) {
    vec3 inv = 1.0 / rd;
    vec3 t0 = (bmin - ro) * inv;
    vec3 t1 = (bmax - ro) * inv;
    vec3 tmin = min(t0, t1), tmax = max(t0, t1);
    return vec2(max(max(tmin.x, tmin.y), tmin.z), min(min(tmax.x, tmax.y), tmax.z));
}
vec2 raySphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    vec3 qc = oc - b * rd;
    float h = r * r - dot(qc, qc);
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// Distance from a ray to a segment. Returns (distance, t along ray)
vec2 raySegment(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 ba = b - a;
    vec3 oa = ro - a;
    float baba = dot(ba, ba) + 1e-6;
    float bard = dot(ba, rd);
    float baoa = dot(ba, oa);
    float rdoa = dot(rd, oa);
    float den = baba - bard * bard;
    float h = den > 1e-6 ? clamp((baoa - bard * rdoa) / den, 0.0, 1.0) : 0.0;
    float t = max(dot(a + ba * h - ro, rd), 0.0);
    return vec2(length(ro + rd * t - (a + ba * h)), t);
}

vec3 cameraRay(vec2 fragCoord) {
    vec2 uv = (fragCoord - 0.5 * uRes) / (0.5 * uRes.y);
    return normalize(uCamFwd + uv.x * uTanHalfFov * uCamRight + uv.y * uTanHalfFov * uCamUp);
}

// GGX specular
float D_GGX(float NoH, float a) { float a2 = a * a; float f = (NoH * a2 - NoH) * NoH + 1.0; return a2 / (PI * f * f + 1e-7); }
float V_Smith(float NoV, float NoL, float a) {
    float a2 = a * a;
    float gv = NoL * sqrt(NoV * NoV * (1.0 - a2) + a2);
    float gl = NoV * sqrt(NoL * NoL * (1.0 - a2) + a2);
    return 0.5 / (gv + gl + 1e-5);
}
vec3 F_Schlick(vec3 f0, float VoH) { return f0 + (1.0 - f0) * pow(1.0 - VoH, 5.0); }
vec3 brdf(vec3 n, vec3 v, vec3 l, vec3 albedo, float rough, float metal) {
    vec3 h = normalize(v + l);
    float NoL = sat(dot(n, l)), NoV = abs(dot(n, v)) + 1e-4, NoH = sat(dot(n, h)), VoH = sat(dot(v, h));
    float a = max(rough * rough, 0.002);
    vec3 f0 = mix(vec3(0.04), albedo, metal);
    vec3 F = F_Schlick(f0, VoH);
    vec3 spec = D_GGX(NoH, a) * V_Smith(NoV, NoL, a) * F;
    vec3 diff = (1.0 - F) * (1.0 - metal) * albedo / PI;
    return (diff + spec) * NoL;
}

// ---------------------------------------------------------------------------
// Astronaut (EVA suit) — joints are provided in the suit's local frame
// (y up, z = facing direction). Two instances: k = 0 (Gromov), 1 (Marina).
// ---------------------------------------------------------------------------
uniform vec3 uJ[24];
uniform vec3 uAstroB[6];  // basis columns of both suits
#define ASTRO_ROT(k) mat3(uAstroB[(k) * 3], uAstroB[(k) * 3 + 1], uAstroB[(k) * 3 + 2])
uniform vec3 uAstroPos[2];
uniform float uAstroOn[2];
uniform float uAstroStyle[2];

#define MAT_SUIT 7.0
#define MAT_VISOR 8.0
#define MAT_PACK 11.0
#define MAT_SUITDARK 12.0
#define MAT_LAMP 14.0

vec2 sdAstro(vec3 pw, int k) {
    vec3 p = transpose(ASTRO_ROT(k)) * (pw - uAstroPos[k]);
    float bound = length(p - vec3(0.0, 0.2, 0.0)) - 1.25;
    if (bound > 0.3) return vec2(bound, 0.0);
    int o = k * 12;
    float style = uAstroStyle[k];
    // hard upper torso + waist
    float d = sdRoundBox(p - vec3(0.0, 0.3, 0.0), vec3(0.21, 0.23, 0.15), 0.08);
    d = smin(d, sdRoundBox(p - vec3(0.0, 0.02, 0.0), vec3(0.16, 0.12, 0.12), 0.07), 0.08);
    d = smin(d, sdRoundBox(p - vec3(0.0, 0.34, 0.12), vec3(0.14, 0.08, 0.05), 0.04), 0.04);  // chest control unit
    vec2 res = vec2(d, MAT_SUIT);
    // arms
    float a = sdCapsule(p, uJ[o + 0], uJ[o + 1], 0.075);
    a = smin(a, sdCapsule(p, uJ[o + 1], uJ[o + 2], 0.068), 0.04);
    a = smin(a, sdCapsule(p, uJ[o + 3], uJ[o + 4], 0.075), 0.04);
    a = smin(a, sdCapsule(p, uJ[o + 4], uJ[o + 5], 0.068), 0.04);
    res.x = smin(res.x, a, 0.07);
    // legs
    float l = sdCapsule(p, uJ[o + 6], uJ[o + 7], 0.09);
    l = smin(l, sdCapsule(p, uJ[o + 7], uJ[o + 8], 0.08), 0.04);
    l = min(l, sdCapsule(p, uJ[o + 9], uJ[o + 10], 0.09));
    l = smin(l, sdCapsule(p, uJ[o + 10], uJ[o + 11], 0.08), 0.04);
    res.x = smin(res.x, l, 0.06);
    // joint bellows (elbows, knees)
    float bel = min(sdSphere(p - uJ[o + 1], 0.082), sdSphere(p - uJ[o + 4], 0.082));
    bel = min(bel, min(sdSphere(p - uJ[o + 7], 0.098), sdSphere(p - uJ[o + 10], 0.098)));
    res.x = smin(res.x, bel, 0.03);
    // gloves (mittens along the forearm) and boots
    vec3 ld = normalize(uJ[o + 2] - uJ[o + 1]);
    vec3 rd2 = normalize(uJ[o + 5] - uJ[o + 4]);
    float g = min(sdCapsule(p, uJ[o + 2] - ld * 0.02, uJ[o + 2] + ld * 0.08, 0.058), sdCapsule(p, uJ[o + 5] - rd2 * 0.02, uJ[o + 5] + rd2 * 0.08, 0.058));
    vec3 fwd = vec3(0.0, -0.25, 1.0);
    g = min(g, sdCapsule(p, uJ[o + 8], uJ[o + 8] + fwd * 0.16, 0.085));
    g = min(g, sdCapsule(p, uJ[o + 11], uJ[o + 11] + fwd * 0.16, 0.085));
    res = opU(res, vec2(g, MAT_SUITDARK));
    // hoses from the chest unit to the backpack
    float hose = min(sdCapsule(p, vec3(0.1, 0.3, 0.2), vec3(0.24, 0.12, -0.05), 0.022), sdCapsule(p, vec3(-0.1, 0.3, 0.2), vec3(-0.24, 0.12, -0.05), 0.022));
    res = opU(res, vec2(hose, MAT_SUITDARK));
    // helmet
    vec3 hp = p - vec3(0.0, 0.66, 0.04);
    float helm = sdSphere(hp, 0.19);
    float ring = sdTorusZ((p - vec3(0.0, 0.51, 0.02)).xzy, 0.15, 0.035);
    res.x = smin(res.x, ring, 0.03);
    vec3 hn = normalize(hp);
    float visor = step(0.3, hn.z) * step(-0.5, hn.y) * step(hn.y, 0.58);
    // raised rim around the visor
    float rim = step(0.18, hn.z) * step(-0.62, hn.y) * step(hn.y, 0.7) * (1.0 - visor);
    helm -= rim * 0.012;
    res = opU(res, vec2(helm, visor > 0.5 ? MAT_VISOR : (rim > 0.5 ? MAT_SUITDARK : MAT_SUIT)));
    // helmet lamps
    float lamp = min(sdCylX(hp - vec3(0.17, 0.08, 0.02), -0.04, 0.05, 0.035), sdCylX(hp - vec3(-0.17, 0.08, 0.02), -0.05, 0.04, 0.035));
    res = opU(res, vec2(lamp, MAT_LAMP));
    // life support backpack (EVA suit only)
    if (style < 0.5) {
        float pack = sdRoundBox(p - vec3(0.0, 0.3, -0.27), vec3(0.23, 0.3, 0.1), 0.05);
        res = opU(res, vec2(pack, MAT_PACK));
    }
    return res;
}

// Suit material parameters
void astroMaterial(float m, vec3 p, int k, out vec3 albedo, out float rough, out float metal) {
    metal = 0.0;
    rough = 0.75;
    if (m == MAT_VISOR) { albedo = vec3(1.0, 0.72, 0.32); rough = 0.06; metal = 1.0; }
    else if (m == MAT_PACK) { albedo = vec3(0.78, 0.78, 0.76); rough = 0.55; }
    else if (m == MAT_SUITDARK) { albedo = vec3(0.12, 0.12, 0.13); rough = 0.6; }
    else if (m == MAT_LAMP) { albedo = vec3(0.3); rough = 0.3; metal = 0.5; }
    else {
        albedo = uAstroStyle[k] > 0.5 ? vec3(0.74, 0.77, 0.8) : vec3(0.8, 0.79, 0.76);
        vec3 lp = transpose(ASTRO_ROT(k)) * (p - uAstroPos[k]);
        int o = k * 12;
        // stripes: red (Gromov) / blue (Marina) arm bands
        float band = step(abs(lp.y - 0.25), 0.02) * step(0.22, abs(lp.x));
        vec3 bandCol = uAstroStyle[k] > 0.5 ? vec3(0.1, 0.25, 0.7) : vec3(0.7, 0.08, 0.06);
        albedo = mix(albedo, bandCol, band);
        // bearing rings: waist, shoulders, wrists
        float ring = step(abs(lp.y - 0.08), 0.014) * step(abs(lp.x), 0.25);
        ring = max(ring, step(abs(length(lp - uJ[o + 0]) - 0.075), 0.012));
        ring = max(ring, step(abs(length(lp - uJ[o + 3]) - 0.075), 0.012));
        ring = max(ring, step(abs(length(lp - uJ[o + 2]) - 0.07), 0.01));
        ring = max(ring, step(abs(length(lp - uJ[o + 5]) - 0.07), 0.01));
        albedo = mix(albedo, vec3(0.25, 0.26, 0.28), ring);
        // flag patch on the left shoulder
        vec2 fp = vec2(lp.x - 0.27, lp.y - 0.36);
        if (abs(fp.x) < 0.035 && abs(fp.y) < 0.022 && lp.z > -0.05) {
            albedo = fp.y > 0.007 ? vec3(0.85) : (fp.y > -0.007 ? vec3(0.1, 0.2, 0.6) : vec3(0.7, 0.08, 0.06));
        }
        albedo *= 0.92 + 0.12 * vnoise(lp * 40.0);
    }
}
