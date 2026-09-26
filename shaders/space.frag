#version 330
// ---------------------------------------------------------------------------
// ZARYA — orbit set: Earth with physically based atmosphere, clouds, city
// lights; the station, the capsule, astronauts, debris, plumes and plasma.
// Output: rgb = HDR radiance, a = linear depth in metres.
// ---------------------------------------------------------------------------
out vec4 finalColor;

#include "common.glsl"

uniform vec3 uSunDir;
uniform vec3 uSunCol;
uniform float uSunSize;
uniform float uAltitude;
uniform float uEarthRot;
uniform float uStars;
uniform float uEarthshine;

uniform float uStationOn;
uniform vec3 uStationB[3];
#define uStationRot mat3(uStationB[0], uStationB[1], uStationB[2])
uniform vec3 uStationPos;
uniform float uPanelAngle;
uniform float uDamage;
uniform float uBetaVent;
uniform float uThrust;
uniform float uHeat;
uniform vec3 uHeatDir;
uniform float uCapsuleMode;
uniform vec3 uCapsulePos;
uniform vec3 uCapsuleB[3];
#define uCapsuleRot mat3(uCapsuleB[0], uCapsuleB[1], uCapsuleB[2])
uniform float uPlasma;
uniform float uDish;
uniform float uAirlock;   // outer hatch: 0 open .. 1 closed
uniform float uNav;
uniform vec4 uBurst;

uniform vec4 uDeb[16];
uniform vec4 uDebV[16];
uniform int uDebN;
uniform vec4 uSpark[24];
uniform int uSparkN;
uniform vec4 uChunk[3];
uniform vec3 uPointPos;
uniform vec3 uPointCol;

#define MAT_MLI 1.0
#define MAT_PANEL 2.0
#define MAT_TRUSS 3.0
#define MAT_GOLD 4.0
#define MAT_RAD 5.0
#define MAT_DARK 6.0
#define MAT_CAPS 9.0
#define MAT_NAV 10.0
#define MAT_RAIL 13.0
#define MAT_DISH 15.0
#define MAT_CHUNK 16.0
#define MAT_VALVE 17.0

const float SUN_I = 5.0;
const float ATM_I = 6.5;
const float FAR = 60000.0;

const vec3 HOLE = vec3(1.35, 1.6, -8.0);

// =========================================================== atmosphere (km)
const float RE = 6371.0;
const float RA = 6471.0;
const vec3 BR = vec3(5.8e-3, 13.5e-3, 33.1e-3);
const float BM = 6.0e-3;
const float HR = 9.5;
const float HM = 1.2;

vec3 earthPos(vec3 pw) { return vec3(0.0, RE + uAltitude, 0.0) + pw * 0.001; }

vec2 lightDepth(vec3 p, vec3 l) {
    vec2 e = raySphere(p, l, vec3(0.0), RE);
    if (e.x > 0.0) return vec2(1e5);
    float t1 = raySphere(p, l, vec3(0.0), RA).y;
    float dt = t1 / 6.0;
    vec2 od = vec2(0.0);
    for (int i = 0; i < 6; i++) {
        vec3 q = p + l * (float(i) + 0.5) * dt;
        float h = max(length(q) - RE, 0.0);
        od += exp(-vec2(h / HR, h / HM)) * dt;
    }
    return od;
}
vec3 transmit(vec2 od) { return exp(-(BR * od.x + BM * 1.1 * od.y)); }

vec3 atmosphere(vec3 ro, vec3 rd, float tmax, out vec3 trans) {
    trans = vec3(1.0);
    vec2 ta = raySphere(ro, rd, vec3(0.0), RA);
    if (ta.y < 0.0) return vec3(0.0);
    float t0 = max(ta.x, 0.0);
    float t1 = min(ta.y, tmax);
    if (t1 <= t0) return vec3(0.0);
    const int N = 14;
    float dt = (t1 - t0) / float(N);
    vec2 odv = vec2(0.0);
    vec3 sr = vec3(0.0), sm = vec3(0.0);
    float mu = dot(rd, uSunDir);
    float pr = 3.0 / (16.0 * PI) * (1.0 + mu * mu);
    float g = 0.76;
    float pm = 3.0 / (8.0 * PI) * ((1.0 - g * g) * (1.0 + mu * mu)) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    for (int i = 0; i < N; i++) {
        vec3 p = ro + rd * (t0 + (float(i) + 0.5) * dt);
        float h = max(length(p) - RE, 0.0);
        vec2 d = exp(-vec2(h / HR, h / HM)) * dt;
        odv += d;
        vec2 odl = lightDepth(p, uSunDir);
        vec3 T = transmit(odv + odl);
        sr += d.x * T;
        sm += d.y * T;
    }
    trans = transmit(odv);
    return ATM_I * (sr * BR * pr + sm * BM * pm);
}

// ================================================================ Earth
mat3 earthSpin() {
    float a = uEarthRot;
    float c = cos(a), s = sin(a);
    mat3 rx = mat3(1, 0, 0, 0, c, s, 0, -s, c);
    float b = 0.9;
    mat3 ry = mat3(cos(b), 0, -sin(b), 0, 1, 0, sin(b), 0, cos(b));
    return ry * rx;
}

float cloudCover(vec3 q, float lod) {
    vec3 w = vec3(fbm3(q * 3.0 + 11.0, 3), fbm3(q * 3.0 + 27.0, 3), fbm3(q * 3.0 + 5.0, 3));
    float big = fbm3(q * 4.0 + w * 2.2, 5);
    float mid = fbm3(q * 38.0 + w * 3.0, 4);
    float fine = lod > 0.01 ? fbm3(q * 260.0, 3) : 0.5;
    float c = big * 0.62 + mid * 0.3 + (fine - 0.5) * 0.22 * lod;
    return smoothstep(0.5, 0.66, c);
}

vec3 earthShade(vec3 pk, vec3 rd, float dist) {
    vec3 n = normalize(pk);
    vec3 q = earthSpin() * n;
    float lod = smoothstep(2500.0, 500.0, dist);  // fade fine detail with distance
    float h = fbm3(q * 1.9 + 3.0, 5) * 0.8 + fbm3(q * 16.0, 4) * 0.2;
    float hd = lod > 0.01 ? fbm3(q * 140.0, 4) : 0.5;
    h += (hd - 0.5) * 0.06 * lod;
    float land = smoothstep(0.575, 0.58, h);
    float lat = abs(q.y);
    float moist = fbm3(q * 5.0 + 7.0, 4);
    float ridge = 1.0 - abs(fbm3(q * 60.0 + 4.0, 4) * 2.0 - 1.0);
    float micro = lod > 0.01 ? fbm3(q * 900.0, 3) : 0.5;
    vec3 desert = vec3(0.3, 0.21, 0.12);
    vec3 steppe = vec3(0.16, 0.16, 0.08);
    vec3 forest = vec3(0.035, 0.07, 0.025);
    vec3 lc = mix(desert, steppe, smoothstep(0.42, 0.56, moist + lat * 0.25));
    lc = mix(lc, forest, smoothstep(0.52, 0.64, moist + lat * 0.1));
    lc *= 0.7 + 0.45 * ridge * ridge + (micro - 0.5) * 0.35 * lod;
    float mount = smoothstep(0.66, 0.74, h + ridge * 0.05);
    lc = mix(lc, vec3(0.22, 0.2, 0.18), mount * 0.6);
    float ice = smoothstep(0.8, 0.86, lat + 0.06 * fbm3(q * 9.0, 3)) + smoothstep(0.72, 0.78, h + ridge * 0.04) * 0.7;
    lc = mix(lc, vec3(0.75, 0.8, 0.86), sat(ice));
    float shallow = smoothstep(0.53, 0.575, h);
    vec3 oc = mix(vec3(0.004, 0.016, 0.045), vec3(0.01, 0.07, 0.085), shallow * shallow * shallow);
    vec3 albedo = mix(oc, lc, land);

    float NoL = dot(n, uSunDir);
    vec3 Tsun = transmit(lightDepth(pk + n * 0.5, uSunDir));
    float cloud = cloudCover(q, lod);
    vec3 qs = earthSpin() * normalize(pk + uSunDir * 8.0);
    float shadow = 1.0 - 0.7 * cloudCover(qs, 0.0);

    vec3 sunL = SUN_I * uSunCol * Tsun;
    vec3 col = albedo / PI * sunL * sat(NoL) * shadow;
    if (land < 0.5) {
        vec3 wn = normalize(n + 0.05 * (vec3(vnoise(q * 1500.0), vnoise(q * 1500.0 + 5.0), vnoise(q * 1500.0 + 9.0)) - 0.5));
        vec3 v = -rd;
        vec3 hv = normalize(v + uSunDir);
        float a = 0.09;
        float spec = D_GGX(sat(dot(wn, hv)), a) * V_Smith(sat(dot(wn, v)) + 1e-3, sat(dot(wn, uSunDir)), a) * 0.14;
        col += spec * sunL * sat(NoL) * shadow;
    }
    // clouds: lit tops with soft terminator, slightly blue shadowed sides
    float wrap = sat((NoL + 0.1) / 1.1);
    float cshade = 0.75 + 0.25 * fbm3(q * 90.0 + 2.0, 3);
    vec3 cc = vec3(0.92) / PI * sunL * wrap * cshade + vec3(0.01, 0.015, 0.03) * sat(NoL + 0.2);
    col = mix(col, cc, cloud);
    // night: city lights + lightning
    float night = smoothstep(0.02, -0.12, NoL);
    if (night > 0.0) {
        // metro regions -> cities -> scattered towns
        float region = smoothstep(0.5, 0.72, fbm3(q * 18.0 + 2.0, 4));
        float urban = smoothstep(0.64, 0.86, fbm3(q * 160.0, 3)) * region;
        float towns = step(0.975, hash13(floor(q * 1400.0))) * smoothstep(0.4, 0.8, fbm3(q * 60.0, 2));
        float city = urban * 1.6 + towns * 0.7;
        vec3 lights = vec3(1.0, 0.55, 0.22) * city * 2.6 * land * (1.0 - 0.75 * cloud);
        // moonlight keeps the night side from being a flat black disc
        col += (albedo * 2.0 + vec3(0.05) * cloud) * vec3(0.06, 0.08, 0.14) * night;
        vec2 cell = floor(vec2(atan(q.z, q.x), asin(q.y)) * 60.0);
        float flick = step(0.992, hash12(cell + floor(uTime * 6.0))) * cloud;
        col += (lights + vec3(0.6, 0.7, 1.0) * flick * 0.35) * night;
    }
    return col;
}

// ================================================================ sky
vec3 starField(vec3 rd) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float sc = 140.0 + 120.0 * float(i);
        vec3 p = rd * sc;
        vec3 id = floor(p);
        vec3 f = fract(p) - 0.5;
        vec3 h = hash33(id + float(i) * 17.0);
        float d = length(f - (h - 0.5) * 0.6);
        float br = step(0.965, h.x) * (0.4 + 3.0 * pow(h.z, 6.0)) + pow(h.z, 80.0) * 25.0;
        col += br * smoothstep(0.16, 0.0, d) * mix(vec3(1.0, 0.85, 0.7), vec3(0.8, 0.88, 1.0), h.y);
    }
    vec3 ax = normalize(vec3(0.35, 0.55, -0.76));
    float bd = dot(rd, ax);
    float band = exp(-bd * bd * 12.0);
    float mw = fbm3(rd * 5.0, 5);
    float dust = smoothstep(0.45, 0.7, fbm3(rd * 9.0 + 3.0, 4));
    col += band * (vec3(0.12, 0.11, 0.13) * pow(mw, 2.0) * (1.0 - 0.7 * dust) + vec3(0.025, 0.024, 0.03) * band);
    return col * uStars;
}

vec3 sunDisk(vec3 rd) {
    float mu = dot(rd, uSunDir);
    float r = 0.0065 * uSunSize;
    float disk = smoothstep(cos(r), cos(r * 0.85), mu);
    float halo = pow(sat(mu), 900.0) * 6.0 + pow(sat(mu), 60.0) * 0.25;
    return uSunCol * (disk * 900.0 + halo);
}

vec3 burstGlow(vec3 rd) {
    if (uBurst.w <= 0.0) return vec3(0.0);
    vec3 dir = normalize(uBurst.xyz);
    float ang = acos(clamp(dot(rd, dir), -1.0, 1.0));
    float age = uBurst.w;
    float flash = exp(-age * 2.5);
    vec3 col = vec3(1.0, 0.85, 0.7) * (exp(-ang * 900.0) * 400.0 * flash + exp(-ang * 120.0) * 3.0 * flash);
    // glittering expanding cloud
    float radius = 0.004 + age * 0.012;
    if (ang < radius * 1.3) {
        vec3 t1 = normalize(cross(dir, vec3(0, 1, 0)));
        vec3 t2 = cross(dir, t1);
        vec2 uv = vec2(dot(rd, t1), dot(rd, t2)) / radius;
        vec2 cell = floor(uv * 14.0);
        vec2 f = fract(uv * 14.0) - 0.5;
        vec2 h = hash22(cell);
        float tw = 0.5 + 0.5 * sin(uTime * 13.0 + h.x * 40.0);
        float sp = step(0.72, h.y) * smoothstep(0.2, 0.0, length(f - (h - 0.5) * 0.5)) * tw;
        col += vec3(1.0, 0.9, 0.8) * sp * 4.0 * smoothstep(1.2, 0.3, length(uv));
    }
    return col;
}

// full background (earth + atmosphere + stars + sun). depth returned in metres.
vec3 background(vec3 rdw, out float depth) {
    vec3 ro = earthPos(uCamPos);
    vec2 e = raySphere(ro, rdw, vec3(0.0), RE);
    vec3 trans;
    depth = FAR;
    if (e.x > 0.0) {
        vec3 pk = ro + rdw * e.x;
        vec3 ins = atmosphere(ro, rdw, e.x, trans);
        return earthShade(pk, rdw, e.x) * trans + ins;
    }
    vec3 ins = atmosphere(ro, rdw, 1e9, trans);
    return (starField(rdw) * trans.b + sunDisk(rdw)) * trans + ins + burstGlow(rdw);
}

// cheap environment for glossy reflections
vec3 envCheap(vec3 rd) {
    vec3 ro = earthPos(uCamPos);
    vec2 e = raySphere(ro, rd, vec3(0.0), RE);
    float sunUp = sat(dot(normalize(ro), uSunDir) * 3.0 + 0.4);
    if (e.x > 0.0) {
        vec3 n = normalize(ro + rd * e.x);
        float l = sat(dot(n, uSunDir));
        return vec3(0.1, 0.16, 0.26) * l * SUN_I * 0.25 + vec3(0.002, 0.003, 0.006);
    }
    float limb = exp(-abs(dot(rd, normalize(ro)) + 0.34) * 20.0);
    return sunDisk(rd) * 0.5 + vec3(0.2, 0.35, 0.7) * limb * sunUp * 0.6;
}

// ================================================================ geometry
float moduleShell(vec3 q, float z0, float z1, float r) { return sdCylZ(q, z0 + 0.2, z1 - 0.2, r - 0.2) - 0.2; }

float sdDish(vec3 p, vec3 axis, float R) {
    float w = dot(p, axis);
    float rr = length(p - axis * w);
    float surf = rr * rr / 2.6;
    float d = abs(w - surf) * 0.8 - 0.025;
    return max(d, rr - R);
}

vec3 dishAxis() {
    vec3 fixedA = normalize(vec3(0.2, 0.9, 0.4));
    vec3 loose = normalize(vec3(0.8 + 0.1 * sin(uTime * 0.9), -0.25, 0.5));
    return normalize(mix(loose, fixedA, uDish));
}

vec2 mapCapsule(vec3 c) {
    float bound = length(c - vec3(0.0, 0.0, -3.8)) - 6.6;
    if (bound > 1.0) return vec2(bound, 0.0);
    if (uPlasma > 0.01) {
        // re-entry: only the descent module is left, heat shield first
        float dm = sdConeZ(c, -4.25, -2.35, 1.12, 0.9) - 0.04;
        dm = min(dm, sdCylZ(c, -4.4, -4.2, 1.05) - 0.03);
        return vec2(dm, MAT_CAPS);
    }
    float d = length((c - vec3(0.0, 0.0, -1.25)) * vec3(1.0, 1.0, 0.92)) - 1.08;
    d = min(d, sdConeZ(c, -4.25, -2.35, 1.12, 0.9) - 0.04);
    vec2 res = vec2(d, MAT_CAPS);
    float sm = min(sdCylZ(c, -7.0, -4.3, 1.25), sdConeZ(c, -7.45, -6.95, 1.36, 1.25));
    res = opU(res, vec2(sm, MAT_MLI));
    float wings = sdBox(vec3(abs(c.x) - 3.95, c.y, c.z + 5.9), vec3(2.5, 0.015, 0.6));
    res = opU(res, vec2(wings, MAT_PANEL));
    float probe = sdCylZ(c, -0.2, 0.45, 0.28);
    res = opU(res, vec2(probe, MAT_DARK));
    return res;
}

vec2 mapStation(vec3 q) {
    vec2 res = vec2(1e5, 0.0);
    // ---- pressurised modules along z
    float bm = sdBox(q - vec3(0.0, 0.0, 4.5), vec3(2.7, 2.7, 21.5));
    if (bm < 1.5) {
        float node1 = moduleShell(q, -2.7, 2.7, 2.35);
        float alpha = moduleShell(q, 2.5, 13.0, 2.1);
        float serv = moduleShell(q, 12.8, 21.5, 2.0);
        float cone = sdConeZ(q, 21.3, 24.1, 2.0, 1.45);
        float beta = moduleShell(q, -13.2, -2.5, 2.1);
        float node2 = moduleShell(q, -16.6, -13.0, 1.55);
        float dm = min(min(min(node1, alpha), min(serv, cone)), min(beta, node2));
        if (uBetaVent > 0.0) dm = max(dm, -(length(q - HOLE) - 0.32));
        res = vec2(dm, MAT_MLI);
        float rings = min(min(sdTorusZ(q - vec3(0.0, 0.0, 2.6), 2.2, 0.11), sdTorusZ(q - vec3(0.0, 0.0, -2.6), 2.2, 0.11)),
                          min(sdTorusZ(q - vec3(0.0, 0.0, 12.9), 2.08, 0.11), sdTorusZ(q - vec3(0.0, 0.0, -13.1), 2.08, 0.11)));
        rings = min(rings, sdTorusZ(q - vec3(0.0, 0.0, 21.4), 1.98, 0.1));
        res = opU(res, vec2(rings, MAT_GOLD));
        // handrails on top of Alpha and Beta
        float rl = length(vec2(abs(q.x) - 0.55, q.y - 2.28)) - 0.028;
        float ra = max(rl, abs(q.z - 7.75) - 4.8);
        float rb = max(rl, abs(q.z + 7.85) - 4.8);
        vec3 sq = vec3(abs(q.x) - 0.55, q.y - 2.18, mod(q.z + 0.6, 1.2) - 0.6);
        float so = max(sdBox(sq, vec3(0.025, 0.1, 0.025)), min(abs(q.z - 7.75), abs(q.z + 7.85)) - 4.8);
        res = opU(res, vec2(min(min(ra, rb), so), MAT_RAIL));
        // engine bells
        vec3 nq = vec3(abs(q.x) - 0.72, q.y, q.z);
        float noz = sdConeZ(nq, 24.0, 25.5, 0.32, 0.62);
        noz = max(noz, -sdConeZ(nq, 24.08, 25.6, 0.26, 0.56));
        res = opU(res, vec2(noz, MAT_DARK));
    } else res.x = bm;

    // ---- airlock (axis x) on node 1
    float al = sdCylX(q, -6.9, -2.0, 1.1) - 0.12;
    al = max(al, -sdCylX(q, -7.4, -5.2, 0.82));  // open hatchway
    float alRing = sdTorusZ((q - vec3(-7.0, 0.0, 0.0)).zyx, 0.95, 0.09);
    res = opU(res, vec2(al, MAT_MLI));
    res = opU(res, vec2(alRing, MAT_DARK));
    vec3 hq = q - vec3(-7.12, 0.0, 0.95);
    hq.xz = rot2((1.0 - uAirlock) * 1.85) * hq.xz;
    hq += vec3(0.0, 0.0, 0.95);
    float door = sdCylX(hq, -0.05, 0.05, 0.88);
    door = min(door, sdTorusZ((hq - vec3(-0.08, 0.0, 0.0)).zyx, 0.35, 0.03));
    res = opU(res, vec2(door, MAT_RAD));
    float alRail = length(vec2(q.y - 1.3, abs(q.z) - 0.45)) - 0.028;
    alRail = max(alRail, abs(q.x + 4.5) - 2.3);
    res = opU(res, vec2(alRail, MAT_RAIL));

    // ---- mast to truss
    res = opU(res, vec2(sdCylY(q, 2.0, 5.0, 0.55), MAT_TRUSS));

    // ---- truss
    vec3 tq = q - vec3(0.0, 6.0, 0.0);
    float tb = sdBox(tq, vec3(34.0, 1.15, 1.15));
    if (tb < 0.8) {
        vec3 rq = tq;
        rq.x = mod(tq.x + 1.5, 3.0) - 1.5;
        float frame = sdBoxFrame(rq, vec3(1.5, 1.1, 1.1), 0.055);
        float diag = sdCapsule(vec3(rq.x, rq.y, abs(rq.z)), vec3(-1.5, -1.1, 1.1), vec3(1.5, 1.1, 1.1), 0.04);
        float tr = max(min(frame, diag), abs(tq.x) - 34.0);
        res = opU(res, vec2(tr, MAT_TRUSS));
        vec3 eq = tq;
        float cell = floor((tq.x + 4.5) / 9.0);
        eq.x = mod(tq.x + 4.5, 9.0) - 4.5;
        float box = max(sdRoundBox(eq, vec3(1.1, 0.75, 0.75), 0.05), abs(tq.x) - 29.0);
        res = opU(res, vec2(box, hash11(cell * 7.3) > 0.5 ? MAT_GOLD : MAT_MLI));
    } else res = opU(res, vec2(tb, MAT_TRUSS));

    // ---- solar arrays (rotate about the truss axis)
    vec3 r = tq;
    r.yz = rot2(uPanelAngle) * r.yz;
    float bp = sdBox(vec3(abs(r.x) - 27.5, r.y, r.z), vec3(6.5, 0.6, 26.6));
    if (bp < 1.0) {
        for (int i = 0; i < 2; i++) {
            float xa = i == 0 ? 24.0 : 31.0;
            vec3 bq = vec3(abs(r.x) - xa, r.y, abs(r.z) - 14.2);
            float bl = sdBox(bq, vec3(2.75, 0.02, 12.2));
            if (i == 0 && uDamage > 0.0 && r.x > 0.0 && r.z > 0.0) {
                float tear = fbm2(r.xz * 0.35 + 3.0, 3) + 0.25 * sat(1.0 - abs(r.z - 16.0) / 9.0);
                bl = max(bl, (uDamage * 0.95 - tear) * 2.0);
            }
            res = opU(res, vec2(bl, MAT_PANEL));
            float spine = sdBox(bq, vec3(0.07, 0.07, 12.3));
            float gimbal = sdBox(vec3(abs(r.x) - xa, r.y, r.z), vec3(0.45, 0.45, 2.0));
            res = opU(res, vec2(min(spine, gimbal), MAT_DARK));
        }
    } else res = opU(res, vec2(bp, MAT_PANEL));

    // ---- radiator fins
    vec3 fq = vec3(abs(q.x) - 12.5, q.y - 3.0, q.z + 5.5);
    float fb = sdBox(fq, vec3(1.6, 2.4, 4.0));
    if (fb < 0.5) {
        vec3 f2 = fq;
        f2.x = mod(fq.x + 0.6, 1.2) - 0.6;
        float fin = max(sdBox(f2, vec3(0.035, 2.2, 3.8)), abs(fq.x) - 1.3);
        res = opU(res, vec2(fin, MAT_RAD));
    } else res = opU(res, vec2(fb, MAT_RAD));
    res = opU(res, vec2(sdBox(vec3(abs(q.x) - 12.5, q.y - 5.3, q.z + 1.6), vec3(0.15, 0.4, 2.0)), MAT_TRUSS));

    // ---- antenna: tall mast, drive unit at chest height, dish on top
    float am = sdCylY(q - vec3(0.0, 0.0, 17.5), 1.8, 6.2, 0.085);
    res = opU(res, vec2(am, MAT_TRUSS));
    float drive = sdRoundBox(q - vec3(0.0, 3.55, 17.5), vec3(0.19, 0.25, 0.19), 0.03);
    res = opU(res, vec2(drive, MAT_GOLD));
    vec3 dq = q - vec3(0.0, 6.4, 17.5);
    vec3 ax = dishAxis();
    float dish = sdDish(dq + ax * 0.1, ax, 0.8);
    float feed = sdCapsule(dq, vec3(0.0), ax * 0.5, 0.022);
    res = opU(res, vec2(dish, MAT_DISH));
    res = opU(res, vec2(min(feed, length(dq) - 0.11), MAT_DARK));
    // ---- manual valve on the engine section ("section four")
    vec3 vq = q - vec3(0.98, 1.98, 22.3);
    vec3 va = normalize(vec3(0.45, 0.9, 0.0));
    vec3 vx = normalize(cross(va, vec3(0.0, 0.0, 1.0)));
    vec3 vy = cross(va, vx);
    vec3 vl = vec3(dot(vq, vx), dot(vq, vy), dot(vq, va));
    float wheel = sdTorusZ(vl - vec3(0.0, 0.0, 0.16), 0.17, 0.022);
    wheel = min(wheel, sdCapsule(vl, vec3(-0.17, 0.0, 0.16), vec3(0.17, 0.0, 0.16), 0.014));
    wheel = min(wheel, sdCapsule(vl, vec3(0.0, -0.17, 0.16), vec3(0.0, 0.17, 0.16), 0.014));
    wheel = min(wheel, sdCylZ(vl, -0.05, 0.16, 0.05));
    res = opU(res, vec2(wheel, MAT_VALVE));

    // ---- navigation lights
    float nav = min(length(q - vec3(-34.4, 6.0, 0.0)), length(q - vec3(34.4, 6.0, 0.0))) - 0.14;
    res = opU(res, vec2(nav, MAT_NAV));

    // ---- docked capsule
    if (uCapsuleMode > 0.5 && uCapsuleMode < 1.5) res = opU(res, mapCapsule(q - vec3(0.0, 0.0, -16.6)));
    return res;
}

vec3 toStation(vec3 p) { return transpose(uStationRot) * (p - uStationPos); }
vec3 toCapsule(vec3 p) { return transpose(uCapsuleRot) * (p - uCapsulePos); }

vec2 mapChunks(vec3 p) {
    vec2 res = vec2(1e5, 0.0);
    for (int i = 0; i < 3; i++) {
        if (uChunk[i].w <= 0.0) continue;
        vec3 c = p - uChunk[i].xyz;
        float s = uChunk[i].w;
        if (length(c) > s * 2.5 + 0.5) { res.x = min(res.x, length(c) - s * 2.0); continue; }
        c.xy = rot2(uTime * (1.3 + float(i)) + float(i)) * c.xy;
        c.yz = rot2(uTime * (0.9 + 0.4 * float(i))) * c.yz;
        float d = sdBox(c, vec3(s, s * 0.35, s * 0.7));
        d = max(d, -sdBox(c - vec3(s * 0.5, 0.0, s * 0.4), vec3(s * 0.6, s, s * 0.4)));
        d = min(d, sdCapsule(c, vec3(-s, 0.0, 0.0), vec3(-s * 2.2, s * 0.5, -s * 0.4), s * 0.06));
        res = opU(res, vec2(d, MAT_CHUNK));
    }
    return res;
}

vec2 map(vec3 p) {
    vec2 res = vec2(1e5, 0.0);
    if (uStationOn > 0.5) res = mapStation(toStation(p));
    if (uCapsuleMode > 1.5) res = opU(res, mapCapsule(toCapsule(p)));
    if (uAstroOn[0] > 0.5) res = opU(res, sdAstro(p, 0));
    if (uAstroOn[1] > 0.5) res = opU(res, sdAstro(p, 1));
    res = opU(res, mapChunks(p));
    return res;
}

vec3 calcNormal(vec3 p, float t) {
    float e = max(0.0008 * t, 0.0015);
    vec2 k = vec2(1.0, -1.0);
    return normalize(k.xyy * map(p + k.xyy * e).x + k.yyx * map(p + k.yyx * e).x + k.yxy * map(p + k.yxy * e).x + k.xxx * map(p + k.xxx * e).x);
}

float softShadow(vec3 ro, vec3 rd, float k) {
    float res = 1.0;
    float t = 0.03;
    for (int i = 0; i < 40; i++) {
        float h = map(ro + rd * t).x;
        res = min(res, k * h / t);
        t += clamp(h, 0.03, 2.5);
        if (res < 0.01 || t > 70.0) break;
    }
    return sat(res);
}

float calcAO(vec3 p, vec3 n) {
    float occ = 0.0, sca = 1.0;
    for (int i = 0; i < 5; i++) {
        float h = 0.05 + 0.25 * float(i);
        float d = map(p + n * h).x;
        occ += (h - d) * sca;
        sca *= 0.7;
    }
    return sat(1.0 - 1.4 * occ);
}

// intersect bounding volumes to limit marching
vec2 sceneBounds(vec3 ro, vec3 rd) {
    float tn = 1e9, tf = -1.0;
    if (uStationOn > 0.5) {
        vec3 lo = transpose(uStationRot) * (ro - uStationPos);
        vec3 ld = transpose(uStationRot) * rd;
        float ext = 26.8 * abs(sin(uPanelAngle));
        vec3 bmin = vec3(-35.0, min(-3.0, 6.0 - ext), min(-27.2, -24.5));
        vec3 bmax = vec3(35.0, max(10.0, 6.0 + ext), 27.2);
        vec2 b = rayBox(lo, ld, bmin, bmax);
        if (b.x < b.y && b.y > 0.0) { tn = min(tn, max(b.x, 0.0)); tf = max(tf, b.y); }
    }
    if (uCapsuleMode > 1.5) {
        vec2 s = raySphere(ro, rd, uCapsulePos + uCapsuleRot * vec3(0.0, 0.0, -3.8), 7.0);
        if (s.y > 0.0) { tn = min(tn, max(s.x, 0.0)); tf = max(tf, s.y); }
    }
    for (int k = 0; k < 2; k++) {
        if (uAstroOn[k] < 0.5) continue;
        vec2 s = raySphere(ro, rd, uAstroPos[k] + ASTRO_ROT(k) * vec3(0.0, 0.2, 0.0), 1.3);
        if (s.y > 0.0) { tn = min(tn, max(s.x, 0.0)); tf = max(tf, s.y); }
    }
    for (int i = 0; i < 3; i++) {
        if (uChunk[i].w <= 0.0) continue;
        vec2 s = raySphere(ro, rd, uChunk[i].xyz, uChunk[i].w * 2.6);
        if (s.y > 0.0) { tn = min(tn, max(s.x, 0.0)); tf = max(tf, s.y); }
    }
    return vec2(tn, tf);
}

// ================================================================ shading
void stationMaterial(float m, vec3 p, float foot, inout vec3 n, out vec3 alb, out float rough, out float metal, out vec3 emis) {
    emis = vec3(0.0);
    metal = 0.0;
    rough = 0.6;
    alb = vec3(0.8);
    vec3 q = toStation(p);
    vec3 qn = transpose(uStationRot) * n;
    if (m == MAT_MLI) {
        float quilt = sin(q.z * 9.0) * sin(atan(q.y, q.x) * 18.0);
        alb = vec3(0.82, 0.81, 0.78) * (0.85 + 0.2 * fbm3(q * 1.7, 3));
        alb *= 0.93 + 0.07 * quilt;
        rough = 0.65;
        n = normalize(n + uStationRot * (vec3(0.0, 0.0, cos(q.z * 9.0)) * 0.04));
        // portholes on Alpha and Beta (warm interior light)
        vec2 pw1 = vec2(q.y, q.z - 8.0);
        vec2 pw2 = vec2(q.y, q.z + 6.5);
        float win = max(step(length(pw1), 0.28), step(length(pw2), 0.28)) * step(1.8, q.x);
        if (win > 0.5) {
            alb = vec3(0.02);
            rough = 0.05;
            metal = 0.0;
            float frame = smoothstep(0.2, 0.28, min(length(pw1), length(pw2)));
            emis = mix(vec3(1.0, 0.72, 0.45) * 1.6, vec3(0.0), frame);
        }
    } else if (m == MAT_PANEL) {
        vec3 r = q - vec3(0.0, 6.0, 0.0);
        r.yz = rot2(uPanelAngle) * r.yz;
        vec3 pn = vec3(0.0, 1.0, 0.0);
        pn.yz = rot2(-uPanelAngle) * pn.yz;
        float front = step(0.0, dot(qn, pn));
        vec2 cu = r.xz / vec2(0.55, 0.38);
        vec2 gf = abs(fract(cu) - 0.5);
        float gridAA = sat(1.0 - foot / 0.12);
        float grid = mix(0.15, smoothstep(0.43, 0.47, max(gf.x, gf.y)), gridAA);
        vec2 ci = floor(cu);
        float var = hash12(ci);
        vec3 cellc = vec3(0.025, 0.035, 0.09) * (0.8 + 0.4 * var);
        alb = mix(mix(vec3(0.55, 0.55, 0.52), cellc, front), vec3(0.6, 0.55, 0.45), grid * front);
        rough = mix(0.5, mix(0.25, 0.4, grid), front) + (1.0 - gridAA) * 0.15;
        metal = front * 0.6;
        if (uCapsuleMode > 0.0 && length(q.xy) < 6.0 && q.z < -16.0) {  // capsule wings
            alb = mix(vec3(0.5), vec3(0.03, 0.04, 0.1), front);
        }
    } else if (m == MAT_TRUSS) {
        alb = vec3(0.62, 0.62, 0.64);
        rough = 0.38;
        metal = 0.85;
    } else if (m == MAT_GOLD) {
        alb = vec3(1.0, 0.72, 0.32);
        rough = 0.28;
        metal = 1.0;
        n = normalize(n + 0.18 * (vec3(vnoise(q * 14.0), vnoise(q * 14.0 + 3.1), vnoise(q * 14.0 + 7.7)) - 0.5));
    } else if (m == MAT_RAD) {
        alb = vec3(0.9);
        rough = 0.45;
        alb *= 0.9 + 0.1 * step(0.5, fract(q.z * 1.2));
    } else if (m == MAT_DARK) {
        alb = vec3(0.07);
        rough = 0.35;
        metal = 0.7;
        if (q.z > 24.0 && uThrust > 0.0) {
            float inner = sat((q.z - 24.0) / 1.5);
            emis = vec3(1.0, 0.45, 0.15) * uThrust * 12.0 * inner * inner;
        }
    } else if (m == MAT_CAPS) {
        alb = vec3(0.3, 0.32, 0.26) * (0.85 + 0.25 * fbm3(q * 3.0, 3));
        rough = 0.75;
        if (uPlasma > 0.0) {
            alb = vec3(0.12, 0.1, 0.09);
            float hot = pow(sat(dot(n, uHeatDir)), 1.5);
            emis = vec3(1.0, 0.42, 0.12) * uPlasma * (0.4 + 7.0 * hot) * (0.8 + 0.4 * vnoise(p * 4.0 + uTime * 3.0));
        }
    } else if (m == MAT_NAV) {
        alb = vec3(0.1);
        float blink = step(0.7, fract(uTime * 0.7 + (q.x > 0.0 ? 0.5 : 0.0)));
        emis = (q.x > 0.0 ? vec3(0.1, 1.0, 0.25) : vec3(1.0, 0.08, 0.05)) * 40.0 * blink * uNav;
    } else if (m == MAT_RAIL) {
        alb = vec3(0.85, 0.62, 0.12);
        rough = 0.45;
        metal = 0.3;
    } else if (m == MAT_DISH) {
        alb = vec3(0.88);
        rough = 0.35;
    } else if (m == MAT_VALVE) {
        alb = vec3(0.2, 0.18, 0.17);
        rough = 0.3;
        metal = 0.8;
        emis = vec3(1.0, 0.25, 0.05) * 2.2 * uThrust * (0.8 + 0.2 * sin(uTime * 3.0));
    }
}

vec3 shade(vec3 ro, vec3 rd, float t, float m) {
    vec3 p = ro + rd * t;
    vec3 n = calcNormal(p, t);
    vec3 v = -rd;
    vec3 alb;
    float rough, metal;
    vec3 emis = vec3(0.0);
    int ak = -1;
    if (m >= MAT_SUIT && m <= MAT_LAMP) {
        ak = (uAstroOn[1] > 0.5 && sdAstro(p, 1).x < sdAstro(p, 0).x + 1e-3 && uAstroOn[0] > 0.5) ? 1 : (uAstroOn[0] > 0.5 ? 0 : 1);
        astroMaterial(m, p, ak, alb, rough, metal);
        if (m == MAT_SUIT) n = normalize(n + 0.05 * (vec3(vnoise(p * 30.0), vnoise(p * 30.0 + 2.0), vnoise(p * 30.0 + 4.0)) - 0.5));
        if (m == MAT_LAMP) emis = vec3(1.0, 0.95, 0.85) * 1.4;
    } else if (m == MAT_CHUNK) {
        alb = vec3(0.35, 0.34, 0.33);
        rough = 0.4;
        metal = 0.6;
        emis = vec3(1.0, 0.4, 0.1) * 2.0 * pow(sat(fbm3(p * 8.0, 3) - 0.3), 2.0);
    } else {
        stationMaterial(m, p, t * uTanHalfFov * 2.0 / uRes.y, n, alb, rough, metal, emis);
    }

    // sun visibility: earth eclipse + soft shadows
    vec3 pk = earthPos(p);
    float ecl = raySphere(pk, uSunDir, vec3(0.0), RE).x > 0.0 ? 0.0 : 1.0;
    vec3 sunT = transmit(lightDepth(pk, uSunDir));
    float sh = ecl > 0.0 ? softShadow(p + n * 0.02, uSunDir, 24.0) : 0.0;
    float ao = calcAO(p, n);

    vec3 col = brdf(n, v, uSunDir, alb, rough, metal) * uSunCol * sunT * SUN_I * sh;
    // earthshine from below
    vec3 down = -normalize(pk);
    float eDay = sat(dot(-down, uSunDir) + 0.3);
    vec3 eCol = vec3(0.18, 0.27, 0.42) * uEarthshine * (0.15 + eDay);
    col += alb * (1.0 - metal * 0.7) * eCol * (0.5 + 0.5 * dot(n, down)) * ao;
    col += alb * vec3(0.004, 0.005, 0.008) * ao;
    // impact / flare point light
    if (dot(uPointCol, uPointCol) > 0.0) {
        vec3 lv = uPointPos - p;
        float d2 = dot(lv, lv);
        col += brdf(n, v, lv * inversesqrt(d2), alb, rough, metal) * uPointCol / (d2 + 0.5);
    }
    // engine plume light
    if (uThrust > 0.0 && uStationOn > 0.5) {
        vec3 lp = uStationPos + uStationRot * vec3(0.0, 0.0, 27.0);
        vec3 lv = lp - p;
        float d2 = dot(lv, lv);
        col += brdf(n, v, lv * inversesqrt(d2), alb, rough, metal) * vec3(1.0, 0.5, 0.2) * uThrust * 400.0 / (d2 + 4.0);
    }
    // helmet lamps
    for (int k = 0; k < 2; k++) {
        if (uAstroOn[k] < 0.5) continue;
        vec3 lp = uAstroPos[k] + ASTRO_ROT(k) * vec3(0.0, 0.76, 0.3);
        vec3 ld = ASTRO_ROT(k) * vec3(0.0, -0.05, 1.0);
        vec3 lv = lp - p;
        float d2 = dot(lv, lv);
        float cone = smoothstep(0.82, 0.95, dot(normalize(-lv), ld));
        col += brdf(n, v, lv * inversesqrt(d2), alb, rough, metal) * vec3(1.0, 0.95, 0.85) * cone * 4.0 / (d2 + 0.3);
    }
    // reflections for glossy materials
    if (rough < 0.5) {
        vec3 r = reflect(rd, n);
        vec3 f0 = mix(vec3(0.04), alb, metal);
        vec3 F = F_Schlick(f0, sat(dot(n, v)));
        vec3 env;
        if (m == MAT_VISOR) {
            float dd;
            env = background(r, dd);
            // the station itself shows in the visor as a dark silhouette
            vec2 bb = sceneBounds(p + n * 0.05, r);
            if (bb.y > 0.0 && uStationOn > 0.5) {
                float tt = max(bb.x, 0.05);
                for (int i = 0; i < 40; i++) {
                    float h = map(p + n * 0.05 + r * tt).x;
                    if (h < 0.01) { env *= 0.08; env += vec3(0.25, 0.25, 0.24) * sh * SUN_I * 0.05; break; }
                    tt += h;
                    if (tt > bb.y) break;
                }
            }
        } else env = envCheap(r);
        col += env * F * (1.0 - rough) * (m == MAT_VISOR ? 1.0 : 0.6) * ao;
    }
    // re-entry heating
    if (uHeat > 0.0 && m != MAT_VISOR && ak < 0) {
        float front = sat(dot(n, uHeatDir));
        float fl = 0.6 + 0.4 * vnoise(p * 1.5 + uHeatDir * uTime * 20.0);
        col += vec3(1.0, 0.36, 0.1) * uHeat * 6.0 * pow(front, 1.5) * fl;
    }
    col += emis;
    return col;
}

// ================================================================ volumetrics
vec3 ventPlume(vec3 ro, vec3 rd, float tmax) {
    if (uBetaVent <= 0.0 || uStationOn < 0.5) return vec3(0.0);
    vec3 hp = uStationPos + uStationRot * HOLE;
    vec3 hd = uStationRot * normalize(vec3(HOLE.xy, 0.4));
    vec2 s = raySphere(ro, rd, hp + hd * 5.0, 6.0);
    if (s.y < 0.0) return vec3(0.0);
    float t0 = max(s.x, 0.0), t1 = min(s.y, tmax);
    if (t1 <= t0) return vec3(0.0);
    vec3 acc = vec3(0.0);
    float dt = (t1 - t0) / 14.0;
    for (int i = 0; i < 14; i++) {
        vec3 p = ro + rd * (t0 + (float(i) + hash12(gl_FragCoord.xy) ) * dt);
        vec3 lp = p - hp;
        float along = dot(lp, hd);
        if (along < 0.0) continue;
        float rad = length(lp - hd * along);
        float w = 0.3 + along * 0.45;
        float d = exp(-rad * rad / (w * w)) * exp(-along * 0.25) * 2.5;
        d *= 0.6 + 0.8 * vnoise(p * 2.5 - hd * uTime * 6.0);
        acc += d * dt;
    }
    float ph = 0.3 + 2.5 * pow(sat(dot(rd, uSunDir)), 6.0);
    return acc * uBetaVent * (uSunCol * SUN_I * 0.35 * ph + vec3(0.05, 0.07, 0.1));
}

vec3 thrusterPlume(vec3 ro, vec3 rd, float tmax) {
    if (uThrust <= 0.0 || uStationOn < 0.5) return vec3(0.0);
    vec3 col = vec3(0.0);
    for (int k = -1; k <= 1; k += 2) {
        vec3 a = uStationPos + uStationRot * vec3(0.72 * float(k), 0.0, 25.4);
        vec3 b = uStationPos + uStationRot * vec3(0.72 * float(k) * 2.5, 0.0, 25.4 + 16.0);
        vec2 sg = raySegment(ro, rd, a, b);
        if (sg.y > tmax) continue;
        vec3 cp = ro + rd * sg.y;
        float along = sat(dot(cp - a, b - a) / dot(b - a, b - a));
        float w = 0.35 + along * 2.2;
        float core = exp(-sg.x * sg.x / (w * w * 0.25));
        float fl = 0.75 + 0.25 * sin(uTime * 60.0 + along * 20.0);
        vec3 c = mix(vec3(1.0, 0.9, 0.75), vec3(1.0, 0.35, 0.08), sat(along * 1.6));
        c = mix(c, vec3(0.3, 0.4, 1.0), smoothstep(0.6, 1.0, along) * 0.5);
        col += c * core * (1.0 - along) * fl * uThrust * 6.0;
        // shock diamonds
        float dia = pow(0.5 + 0.5 * cos(along * 40.0), 8.0) * exp(-along * 4.0);
        col += vec3(1.0, 0.8, 0.6) * dia * core * uThrust * 8.0;
    }
    return col;
}

vec3 capsulePlasma(vec3 ro, vec3 rd, float tmax) {
    if (uPlasma <= 0.0) return vec3(0.0);
    vec3 axis = uCapsuleRot * vec3(0.0, 0.0, 1.0);  // docking end forward; heat shield is -z
    vec3 shield = uCapsulePos + uCapsuleRot * vec3(0.0, 0.0, -4.3);
    vec3 vel = uHeatDir;  // direction of motion
    // shell
    vec2 s = raySphere(ro, rd, shield - vel * 12.0, 18.0);
    if (s.y < 0.0) return vec3(0.0);
    float t0 = max(s.x, 0.0), t1 = min(s.y, tmax);
    vec3 acc = vec3(0.0);
    float dt = (t1 - t0) / 20.0;
    for (int i = 0; i < 20; i++) {
        vec3 p = ro + rd * (t0 + (float(i) + hash12(gl_FragCoord.xy + uTime)) * dt);
        vec3 lp = p - shield;
        float along = dot(lp, -vel);  // behind the capsule is positive
        float rad = length(lp + vel * along);
        float w = 1.6 + max(along, 0.0) * 0.28;
        float shellD = exp(-pow(max(rad - (1.3 + max(along, 0.0) * 0.18), 0.0) / 0.5, 2.0)) * smoothstep(-1.5, 0.3, along);
        float n = vnoise(lp * 1.2 + vel * uTime * 25.0);
        float trail = exp(-rad * rad / (w * w)) * exp(-max(along, 0.0) * 0.12) * smoothstep(-0.5, 1.0, along);
        float d = (shellD * 1.2 + trail * 0.6) * (0.3 + 0.9 * n * n);
        // bow shock hugging the heat shield
        float shock = exp(-pow(max(rad - 1.25, 0.0) / 0.35, 2.0)) * exp(-pow((along + 0.5) / 0.55, 2.0));
        d += shock * 6.0 * (0.7 + 0.3 * n);
        vec3 c = mix(vec3(1.0, 0.85, 0.6), vec3(1.0, 0.38, 0.1), sat(along / 5.0));
        c = mix(c, vec3(0.85, 0.16, 0.12), sat(along / 12.0));
        c = mix(c, vec3(0.55, 0.12, 0.35), sat((along - 12.0) / 10.0));
        acc += c * d * dt;
    }
    return acc * uPlasma * 0.09;
}

vec3 stationFire(vec3 ro, vec3 rd, float tmax) {
    // plasma trail behind the burning station
    if (uHeat < 0.35 || uStationOn < 0.5) return vec3(0.0);
    vec3 a = uStationPos;
    vec3 b = uStationPos - uHeatDir * 120.0;
    vec2 sg = raySegment(ro, rd, a, b);
    if (sg.y > tmax + 30.0) return vec3(0.0);
    vec3 cp = ro + rd * sg.y;
    float along = sat(dot(cp - a, b - a) / dot(b - a, b - a));
    float w = 4.0 + along * 14.0;
    float n = 0.5 + vnoise(cp * 0.08 + uHeatDir * uTime * 4.0);
    vec3 c = mix(vec3(1.0, 0.6, 0.3), vec3(0.9, 0.2, 0.3), along);
    float camFade = smoothstep(15.0, 60.0, length(uCamPos - uStationPos));
    return c * exp(-sg.x * sg.x / (w * w)) * (1.0 - along) * n * (uHeat - 0.35) * 1.6 * camFade;
}

vec3 debrisGlow(vec3 ro, vec3 rd, float tmax) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= uDebN) break;
        vec3 a = uDeb[i].xyz;
        vec3 b = a - uDebV[i].xyz * 0.035;
        vec2 sg = raySegment(ro, rd, a, b);
        if (sg.y > tmax) continue;
        float w = uDeb[i].w * 0.5 + sg.y * 0.0012;
        float g = exp(-sg.x * sg.x / (w * w));
        float hot = uDebV[i].w;
        vec3 c = mix(uSunCol * 1.5, vec3(1.0, 0.45, 0.12) * 4.0, hot);
        col += c * g * (0.6 + 0.4 * hash11(float(i))) * (1.0 + hot * 2.0);
    }
    for (int i = 0; i < 24; i++) {
        if (i >= uSparkN) break;
        vec3 sp = uSpark[i].xyz;
        float tt = dot(sp - ro, rd);
        if (tt < 0.0 || tt > tmax) continue;
        float d = length(ro + rd * tt - sp);
        float w = 0.02 + tt * 0.0012;
        col += vec3(1.0, 0.55, 0.2) * uSpark[i].w * exp(-d * d / (w * w)) * 3.0;
    }
    return col;
}

// ================================================================ main
void main() {
    vec3 ro = uCamPos;
    vec3 rd = cameraRay(gl_FragCoord.xy);

    float t = -1.0;
    float m = 0.0;
    vec2 bb = sceneBounds(ro, rd);
    if (bb.y > 0.0 && bb.x < bb.y) {
        float tt = max(bb.x, 0.02);
        for (int i = 0; i < 160; i++) {
            vec2 h = map(ro + rd * tt);
            if (h.x < 0.0006 * tt + 0.0008) { t = tt; m = h.y; break; }
            tt += h.x * 0.9;
            if (tt > bb.y) break;
        }
    }

    vec3 col;
    float depth;
    if (t > 0.0) {
        col = shade(ro, rd, t, m);
        depth = t;
    } else {
        col = background(rd, depth);
    }
    float tmax = t > 0.0 ? t : 1e6;
    col += ventPlume(ro, rd, tmax);
    col += thrusterPlume(ro, rd, tmax);
    col += capsulePlasma(ro, rd, tmax);
    col += stationFire(ro, rd, tmax);
    col += debrisGlow(ro, rd, tmax);

    finalColor = vec4(max(col, 0.0), depth);
}
