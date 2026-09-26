#version 330
// ---------------------------------------------------------------------------
// ZARYA — interior set: module "Alpha" corridor, the hatch to module "Beta",
// emergency lighting, smoke, venting mist, sparks and floating objects.
// Output: rgb = HDR radiance, a = depth in metres.
// ---------------------------------------------------------------------------
out vec4 finalColor;

#include "common.glsl"

uniform float uMainLight;
uniform float uAlarm;
uniform float uSmoke;
uniform float uHatch;      // 0 closed .. 1 open
uniform float uBetaMist;
uniform float uSunShaft;
uniform float uFlashlight;
uniform vec3 uFlashPos;
uniform vec3 uFlashDir;
uniform float uFloaters;
uniform float uSeal;       // bolts of the isolation seal
uniform float uAirIn;      // inner airlock hatch at the node end: 0 closed .. 1 open
uniform vec4 uSpark[24];
uniform int uSparkN;
uniform vec3 uPointPos;
uniform vec3 uPointCol;

#define M_WALL 1.0
#define M_EQUIP 2.0
#define M_FABRIC 3.0
#define M_RAIL 4.0
#define M_LIGHT 5.0
#define M_CABLE 6.0
#define M_HATCH 9.0
#define M_SCREEN 10.0
#define M_BEACON 13.0
#define M_ITEM 15.0
#define M_BREACH 16.0
#define M_WATER 17.0

const float HZ = 6.2;                 // bulkhead position
const vec3 BREACH = vec3(1.1, 0.35, 11.5);
const vec3 BEACON0 = vec3(0.92, 0.92, -2.2);
const vec3 BEACON1 = vec3(-0.92, 0.92, 3.4);

float lightFlicker() {
    float f = step(0.12, fract(sin(floor(uTime * 9.0) * 12.9898) * 43758.5)) * 0.8 + 0.2;
    return mix(1.0, f, step(uMainLight, 0.99));
}

vec3 beaconDir(int i) {
    float a = uTime * 4.2 + float(i) * 2.1;
    return normalize(vec3(cos(a), -0.25, sin(a)));
}

// hatch door transform (hinge on -x side of the opening)
vec3 doorLocal(vec3 p) {
    vec3 q = p - vec3(-0.64, 0.0, HZ + 0.02);
    float a = -uHatch * 1.9;
    q.xz = rot2(a) * q.xz;
    return q - vec3(0.64, 0.0, 0.0);
}

vec2 mapInterior(vec3 p) {
    // ---- shell: two module volumes joined by the round hatch opening
    float volA = sdRoundBox(p - vec3(0.0, 0.0, 0.0), vec3(1.12, 1.12, HZ - 0.12), 0.38);
    float volB = sdRoundBox(p - vec3(0.0, 0.0, HZ + 5.2), vec3(1.12, 1.12, 5.0), 0.38);
    float open = sdCylZ(p, HZ - 0.3, HZ + 0.4, 0.62);
    float lock = sdCylZ(p, -8.4, -5.7, 0.74);  // airlock chamber behind the node end
    float space = min(min(min(volA, volB), open), lock);
    float walls = -space;
    // breach hole in Beta
    walls = max(walls, -(length(p - BREACH) - 0.24));
    vec2 res = vec2(walls, M_WALL);

    // ---- wall equipment: racks, boxes, bags (repeat along z every 1 m)
    float cz = floor(p.z);
    vec3 q = vec3(abs(p.x) - 1.1, p.y, fract(p.z) - 0.5);
    float side = p.x > 0.0 ? 1.0 : 0.0;
    vec3 h = hash33(vec3(cz, side, 3.0));
    if (abs(p.z - HZ) > 0.9 && p.z > -5.8 && p.z < 15.8) {
        vec3 bs = vec3(0.04 + 0.1 * h.x, 0.18 + 0.3 * h.y, 0.3 + 0.12 * h.z);
        float by = (h.z - 0.5) * 0.9;
        float box = sdRoundBox(q - vec3(0.0, by, 0.0), bs, 0.02);
        float mat = h.x > 0.7 ? M_FABRIC : M_EQUIP;
        res = opU(res, vec2(box, mat));
        // ceiling / floor stowage
        vec3 q2 = vec3(p.x, abs(p.y) - 1.1, fract(p.z + 0.5) - 0.5);
        vec3 h2 = hash33(vec3(cz, step(0.0, p.y), 9.0));
        float b2 = sdRoundBox(q2 - vec3((h2.x - 0.5) * 1.0, 0.0, 0.0), vec3(0.2 + 0.2 * h2.y, 0.05 + 0.08 * h2.z, 0.3), 0.04);
        res = opU(res, vec2(b2, h2.z > 0.5 ? M_FABRIC : M_EQUIP));
    }
    // ---- handrails on side walls and ceiling
    float rails = length(vec2(abs(p.x) - 1.0, abs(p.y) - 0.62)) - 0.022;
    rails = min(rails, length(vec2(abs(p.x) - 0.6, abs(p.y) - 1.02)) - 0.022);
    rails = max(rails, -(abs(p.z - HZ) - 0.8));
    rails = max(rails, abs(p.z - 5.0) - 10.8);
    res = opU(res, vec2(rails, M_RAIL));
    // ---- light strips at the ceiling corners
    vec3 lq = vec3(abs(p.x) - 0.82, p.y - 0.95, mod(p.z + 1.0, 2.5) - 1.25);
    float lights = sdRoundBox(lq, vec3(0.1, 0.02, 0.45), 0.01);
    lights = max(lights, abs(p.z - 0.0) - 5.9);
    res = opU(res, vec2(lights, M_LIGHT));
    // ---- cable bundle with sag, plus a torn cable hanging down
    float sag = 0.06 * sin(3.14159 * fract(p.z / 1.5));
    float cab = length(vec2(p.x + 0.9, p.y - 0.88 + sag)) - 0.03;
    cab = min(cab, length(vec2(p.x + 0.84, p.y - 0.93 + sag * 0.8)) - 0.022);
    cab = max(cab, abs(p.z) - 5.8);
    vec3 hc = p - vec3(0.25, 1.05, 1.6);
    float sway = sin(uTime * 1.3) * 0.15;
    float torn = sdCapsule(hc, vec3(0.0), vec3(sway, -0.55, 0.1 + sway * 0.3), 0.018);
    res = opU(res, vec2(min(cab, torn), M_CABLE));
    // ---- ORION console near the hatch
    float con = sdRoundBox(p - vec3(1.04, 0.15, 4.9), vec3(0.08, 0.32, 0.45), 0.02);
    res = opU(res, vec2(con, M_SCREEN));
    // ---- laptop on an arm
    vec3 lp = p - vec3(-0.85, 0.05, -2.6);
    lp.xz = rot2(0.5) * lp.xz;
    float lap = sdBox(lp, vec3(0.18, 0.012, 0.13));
    float scr = sdBox(lp - vec3(0.0, 0.12, -0.13), vec3(0.18, 0.12, 0.008));
    res = opU(res, vec2(lap, M_EQUIP));
    res = opU(res, vec2(scr, M_SCREEN));
    // ---- alarm beacons
    float bc = min(length(p - BEACON0) - 0.07, length(p - BEACON1) - 0.07);
    res = opU(res, vec2(bc, M_BEACON));

    // ---- hatch: frame + door + seal bolts
    float frame = sdTorusZ(p - vec3(0.0, 0.0, HZ), 0.66, 0.07);
    res = opU(res, vec2(frame, M_HATCH));
    vec3 dq = doorLocal(p);
    float door = sdCylZ(dq, -0.04, 0.04, 0.6);
    door = max(door, -sdCylZ(dq, -0.1, 0.1, 0.13));  // window hole
    float wheel = sdTorusZ(dq - vec3(0.0, 0.0, -0.1), 0.2, 0.018);
    wheel = min(wheel, sdCapsule(dq, vec3(-0.2, 0.0, -0.1), vec3(0.2, 0.0, -0.1), 0.012));
    wheel = min(wheel, sdCapsule(dq, vec3(0.0, -0.2, -0.1), vec3(0.0, 0.2, -0.1), 0.012));
    wheel = max(wheel, -sdCylZ(dq, -0.2, 0.2, 0.14));
    float winFrame = max(sdTorusZ(dq, 0.14, 0.02), 0.0);
    res = opU(res, vec2(min(min(door, wheel), winFrame), M_HATCH));
    if (uSeal > 0.0) {
        float bolts = 1e5;
        for (int i = 0; i < 4; i++) {
            float a = float(i) * 1.5708 + 0.785;
            vec2 d = vec2(cos(a), sin(a));
            float r = mix(0.95, 0.58, uSeal);
            vec3 c = vec3(d * r, HZ - 0.1);
            bolts = min(bolts, sdCapsule(p, c, c + vec3(d * 0.3, 0.0), 0.04));
        }
        res = opU(res, vec2(bolts, M_HATCH));
    }

    // ---- inner airlock hatch (hinge on +x)
    float aframe = sdTorusZ(p - vec3(0.0, 0.0, -6.02), 0.8, 0.06);
    res = opU(res, vec2(aframe, M_HATCH));
    vec3 aq = p - vec3(0.78, 0.0, -6.0);
    aq.xz = rot2(uAirIn * 1.9) * aq.xz;
    aq += vec3(0.78, 0.0, 0.0);
    float adoor = sdCylZ(aq, -0.04, 0.04, 0.74);
    adoor = min(adoor, sdTorusZ(aq - vec3(0.0, 0.0, 0.08), 0.2, 0.018));
    res = opU(res, vec2(adoor, M_HATCH));

    // ---- floating objects
    if (uFloaters > 0.0) {
        for (int i = 0; i < 5; i++) {
            vec3 hh = hash33(vec3(float(i), 4.0, 1.0));
            vec3 c = vec3((hh.x - 0.5) * 1.3, (hh.y - 0.5) * 1.2, -4.0 + hh.z * 9.0);
            c += vec3(sin(uTime * 0.3 + hh.x * 6.0), cos(uTime * 0.25 + hh.y * 6.0), sin(uTime * 0.2 + hh.z * 6.0)) * 0.15 * uFloaters;
            vec3 o = p - c;
            o.xy = rot2(uTime * (0.4 + hh.x)) * o.xy;
            o.yz = rot2(uTime * (0.3 + hh.y)) * o.yz;
            float d = i == 0 ? sdBox(o, vec3(0.1, 0.007, 0.07)) : (i == 1 ? sdCapsule(o, vec3(-0.07, 0.0, 0.0), vec3(0.07, 0.0, 0.0), 0.006) : sdRoundBox(o, vec3(0.035, 0.02, 0.028), 0.008));
            res = opU(res, vec2(d, M_ITEM));
        }
        // water droplets
        for (int i = 0; i < 4; i++) {
            vec3 hh = hash33(vec3(float(i), 7.0, 2.0));
            vec3 c = vec3((hh.x - 0.5) * 1.0, (hh.y - 0.5) * 0.9, -1.0 + hh.z * 4.0);
            c += vec3(sin(uTime * 0.5 + hh.x * 9.0), cos(uTime * 0.4 + hh.y * 9.0), 0.0) * 0.1;
            float wob = 1.0 + 0.1 * sin(uTime * 3.0 + hh.z * 10.0);
            float d = length((p - c) * vec3(wob, 1.0 / wob, 1.0)) - (0.02 + 0.03 * hh.z);
            res = opU(res, vec2(d, M_WATER));
        }
    }
    return res;
}

vec2 map(vec3 p) {
    vec2 res = mapInterior(p);
    if (uAstroOn[0] > 0.5) res = opU(res, sdAstro(p, 0));
    if (uAstroOn[1] > 0.5) res = opU(res, sdAstro(p, 1));
    return res;
}

vec3 calcNormal(vec3 p) {
    const float e = 0.0012;
    vec2 k = vec2(1.0, -1.0);
    return normalize(k.xyy * map(p + k.xyy * e).x + k.yyx * map(p + k.yyx * e).x + k.yxy * map(p + k.yxy * e).x + k.xxx * map(p + k.xxx * e).x);
}

float calcAO(vec3 p, vec3 n) {
    float occ = 0.0, sca = 1.0;
    for (int i = 0; i < 5; i++) {
        float h = 0.02 + 0.09 * float(i);
        float d = map(p + n * h).x;
        occ += (h - d) * sca;
        sca *= 0.75;
    }
    return sat(1.0 - 2.2 * occ);
}

float softShadow(vec3 ro, vec3 rd, float maxt, float k) {
    float res = 1.0;
    float t = 0.02;
    for (int i = 0; i < 28; i++) {
        float h = map(ro + rd * t).x;
        res = min(res, k * h / t);
        t += clamp(h, 0.02, 0.4);
        if (res < 0.02 || t > maxt) break;
    }
    return sat(res);
}

// ---------------------------------------------------------------- lights
struct Light { vec3 pos; vec3 col; };

vec3 mainLightPos(int i) {
    float z = -3.5 + float(i) * 2.5;
    return vec3((i % 2 == 0) ? 0.75 : -0.75, 0.86, z);
}

// radiance arriving at p from all lights (no shadows), and the strongest one
vec3 lightAt(vec3 p, vec3 n, vec3 v, vec3 alb, float rough, float metal, bool surface, out vec3 keyDir, out float keyDist, out vec3 keyCol) {
    vec3 acc = vec3(0.0);
    float best = 0.0;
    keyDir = vec3(0.0, 1.0, 0.0);
    keyDist = 1.0;
    keyCol = vec3(0.0);
    float fl = lightFlicker();
    // main ceiling lights (only in module Alpha)
    for (int i = 0; i < 4; i++) {
        vec3 lp = mainLightPos(i);
        vec3 lv = lp - p;
        float d2 = dot(lv, lv);
        vec3 c = vec3(1.0, 0.9, 0.78) * 1.4 * uMainLight * fl / (d2 + 0.15);
        if (p.z > HZ + 0.2) c *= 0.05;
        vec3 l = lv * inversesqrt(d2);
        vec3 contrib = surface ? brdf(n, v, l, alb, rough, metal) * c : c * 0.08;
        acc += contrib;
        float lum = dot(c, vec3(0.3));
        if (lum > best) { best = lum; keyDir = l; keyDist = sqrt(d2); keyCol = contrib; }
    }
    // rotating red beacons
    for (int i = 0; i < 2; i++) {
        vec3 bp = i == 0 ? BEACON0 : BEACON1;
        vec3 lv = bp - p;
        float d2 = dot(lv, lv);
        vec3 l = lv * inversesqrt(d2);
        float beam = pow(sat(dot(-l, beaconDir(i))), 7.0);
        vec3 c = vec3(1.0, 0.05, 0.02) * uAlarm * (0.1 + 9.0 * beam) / (d2 + 0.6);
        if (p.z > HZ + 0.2) c *= 0.15;
        vec3 contrib = surface ? brdf(n, v, l, alb, rough, metal) * c : c * 0.08;
        acc += contrib;
        float lum = dot(c, vec3(0.3));
        if (lum > best) { best = lum; keyDir = l; keyDist = sqrt(d2); keyCol = contrib; }
    }
    // helmet flashlight
    if (uFlashlight > 0.0) {
        vec3 lv = uFlashPos - p;
        float d2 = dot(lv, lv);
        vec3 l = lv * inversesqrt(d2);
        float cone = smoothstep(0.86, 0.97, dot(-l, uFlashDir));
        vec3 c = vec3(1.0, 0.96, 0.88) * uFlashlight * 2.5 * cone / (d2 + 0.05);
        vec3 contrib = surface ? brdf(n, v, l, alb, rough, metal) * c : c * 0.08;
        acc += contrib;
        float lum = dot(c, vec3(0.3));
        if (lum > best) { best = lum; keyDir = l; keyDist = sqrt(d2); keyCol = contrib; }
    }
    // Beta: cold emergency glow sticks + sun shaft through the breach
    if (p.z > HZ - 0.5) {
        vec3 gp = vec3(-0.9, -0.7, 9.0);
        vec3 lv = gp - p;
        float d2 = dot(lv, lv);
        vec3 c = vec3(0.1, 0.55, 0.7) * 2.2 / (d2 + 0.2);
        acc += surface ? brdf(n, v, lv * inversesqrt(d2), alb, rough, metal) * c : c * 0.05;
        // cold light just behind the hatch window
        vec3 wp = vec3(0.35, 0.55, 7.3);
        lv = wp - p;
        d2 = dot(lv, lv);
        c = vec3(0.55, 0.8, 1.0) * 1.2 / (d2 + 0.15);
        acc += surface ? brdf(n, v, lv * inversesqrt(d2), alb, rough, metal) * c : c * 0.04;
        if (uSunShaft > 0.0) {
            vec3 sd = normalize(vec3(-1.0, -0.25, 0.35));
            vec3 rel = p - BREACH;
            float along = dot(rel, sd);
            float rad = length(rel - sd * along);
            float shaft = step(0.0, along) * smoothstep(0.3 + along * 0.08, 0.1 + along * 0.05, rad);
            vec3 sc = vec3(1.0, 0.95, 0.88) * 8.0 * uSunShaft * shaft;
            acc += surface ? brdf(n, v, -sd, alb, rough, metal) * sc : sc * 0.05;
        }
    }
    // spark flash
    if (dot(uPointCol, uPointCol) > 0.0) {
        vec3 lv = uPointPos - p;
        float d2 = dot(lv, lv);
        vec3 c = uPointCol / (d2 + 0.05);
        acc += surface ? brdf(n, v, lv * inversesqrt(d2), alb, rough, metal) * c : c * 0.08;
    }
    return acc;
}

void material(float m, vec3 p, inout vec3 n, out vec3 alb, out float rough, out float metal, out vec3 emis) {
    emis = vec3(0.0);
    metal = 0.0;
    rough = 0.6;
    alb = vec3(0.7);
    bool beta = p.z > HZ + 0.15;
    if (m == M_WALL) {
        vec2 uv = abs(p.x) > abs(p.y) ? p.zy : p.zx;
        vec2 g = abs(fract(uv * vec2(1.0, 1.6)) - 0.5);
        float seam = smoothstep(0.485, 0.495, max(g.x, g.y));
        alb = vec3(0.7, 0.69, 0.64) * (0.9 + 0.12 * vnoise(p * 7.0)) * (1.0 - seam * 0.6);
        vec2 lab = fract(uv * vec2(2.0, 3.0) + 0.3);
        float label = step(0.8, hash12(floor(uv * vec2(2.0, 3.0) + 0.3))) * step(abs(lab.x - 0.5), 0.2) * step(abs(lab.y - 0.5), 0.08);
        alb = mix(alb, vec3(0.08, 0.1, 0.18), label);
        rough = 0.55;
        if (beta) alb *= 0.7;
    } else if (m == M_EQUIP) {
        alb = vec3(0.32, 0.35, 0.38) * (0.8 + 0.3 * hash13(floor(p * 4.0)));
        rough = 0.45;
        metal = 0.3;
        vec3 lc = floor(p * 45.0);
        float led = step(0.992, hash13(lc)) * step(abs(abs(p.x) - 1.0), 0.2);
        emis = mix(vec3(0.1, 1.0, 0.3), vec3(1.0, 0.2, 0.1), step(0.5, hash13(lc + 1.0))) * led * 2.0 * (0.5 + 0.5 * step(0.5, fract(uTime * 1.3 + hash13(lc))));
    } else if (m == M_FABRIC) {
        alb = vec3(0.78, 0.75, 0.66);
        rough = 0.95;
        n = normalize(n + 0.1 * (vec3(vnoise(p * 40.0), vnoise(p * 40.0 + 3.0), vnoise(p * 40.0 + 6.0)) - 0.5));
    } else if (m == M_RAIL) {
        alb = vec3(0.25, 0.36, 0.52);
        rough = 0.35;
        metal = 0.5;
    } else if (m == M_LIGHT) {
        alb = vec3(0.9);
        rough = 0.3;
        emis = vec3(1.0, 0.92, 0.8) * 9.0 * uMainLight * lightFlicker();
    } else if (m == M_CABLE) {
        alb = vec3(0.05, 0.05, 0.06);
        rough = 0.4;
    } else if (m == M_HATCH) {
        alb = vec3(0.52, 0.54, 0.56);
        rough = 0.32;
        metal = 0.75;
    } else if (m == M_SCREEN) {
        alb = vec3(0.02);
        rough = 0.1;
        vec2 uv = p.zy * 6.0;
        float lines = step(0.5, fract(uv.y * 2.0 + floor(uv.x) * 0.3)) * step(0.3, hash12(floor(uv * vec2(3.0, 2.0))));
        vec3 ui = mix(vec3(0.1, 0.6, 0.9), vec3(1.0, 0.15, 0.1), uAlarm * step(0.5, fract(uTime * 1.5)));
        emis = ui * (0.6 + 1.8 * lines);
    } else if (m == M_BEACON) {
        alb = vec3(0.2, 0.0, 0.0);
        emis = vec3(1.0, 0.05, 0.02) * uAlarm * 30.0;
    } else if (m == M_ITEM) {
        alb = mix(vec3(0.75, 0.74, 0.7), vec3(0.15, 0.2, 0.3), step(0.5, hash13(floor(p * 3.0))));
        rough = 0.4;
    } else if (m == M_WATER) {
        alb = vec3(0.6, 0.8, 0.9);
        rough = 0.05;
    }
}

vec3 shade(vec3 ro, vec3 rd, float t, float m) {
    vec3 p = ro + rd * t;
    vec3 n = calcNormal(p);
    vec3 v = -rd;
    vec3 alb, emis;
    float rough, metal;
    if (m >= MAT_SUIT && m <= MAT_LAMP) {
        int k = (uAstroOn[1] > 0.5 && (uAstroOn[0] < 0.5 || sdAstro(p, 1).x < sdAstro(p, 0).x)) ? 1 : 0;
        astroMaterial(m, p, k, alb, rough, metal);
        emis = m == MAT_LAMP ? vec3(1.0, 0.95, 0.85) * 1.5 * uFlashlight : vec3(0.0);
        if (m == MAT_SUIT) n = normalize(n + 0.06 * (vec3(vnoise(p * 30.0), vnoise(p * 30.0 + 2.0), vnoise(p * 30.0 + 4.0)) - 0.5));
    } else if (m == M_BREACH) {
        alb = vec3(0.0); rough = 1.0; metal = 0.0; emis = vec3(0.0);
    } else {
        material(m, p, n, alb, rough, metal, emis);
    }
    float ao = calcAO(p, n);
    vec3 kd, kc;
    float kdist;
    vec3 direct = lightAt(p, n, v, alb, rough, metal, true, kd, kdist, kc);
    if (m == MAT_VISOR) {
        // keep the gold visor dark and glossy instead of glowing under red light
        direct *= 0.25;
        kc *= 0.25;
    }
    float sh = softShadow(p + n * 0.01, kd, kdist - 0.05, 10.0);
    direct -= kc * (1.0 - sh);
    vec3 col = direct * mix(0.6, 1.0, ao);
    // ambient bounce
    vec3 amb = vec3(0.05, 0.045, 0.04) * uMainLight * lightFlicker() + vec3(0.018, 0.002, 0.001) * uAlarm;
    if (p.z > HZ) amb = vec3(0.004, 0.02, 0.03);
    col += alb * amb * ao;
    // glossy reflections: cheap fake of the corridor lights
    if (rough < 0.4 || m == M_WATER || m == MAT_VISOR) {
        vec3 r = reflect(rd, n);
        vec3 f0 = mix(vec3(0.04), alb, metal);
        if (m == M_WATER) f0 = vec3(0.02);
        vec3 F = F_Schlick(f0, sat(dot(n, v)));
        vec3 env = vec3(0.35, 0.33, 0.3) * uMainLight * smoothstep(0.2, 0.9, r.y) + vec3(0.8, 0.05, 0.02) * uAlarm * 0.3 * (0.5 + 0.5 * sin(uTime * 4.2 + r.x * 3.0));
        if (m == MAT_VISOR) {
            // visor mirrors the red corridor
            float stripe = smoothstep(0.8, 0.97, abs(r.y)) * 0.7;
            env = vec3(0.4, 0.38, 0.35) * uMainLight * stripe + vec3(1.0, 0.08, 0.03) * uAlarm * (0.03 + 0.6 * pow(0.5 + 0.5 * sin(uTime * 4.2 + atan(r.x, r.z) * 2.0), 12.0));
            env += vec3(0.015, 0.016, 0.02);
        }
        col += env * F * ao;
    }
    if (m == M_WATER) col += vec3(0.4, 0.5, 0.55) * 0.05;
    col += emis;
    return col;
}

vec3 breachGlow(vec3 ro, vec3 rd, float tmax) {
    // bright space outside the breach hole (seen through the wall)
    vec2 sg = raySegment(ro, rd, BREACH, BREACH + vec3(0.3, 0.0, 0.0));
    if (sg.y > tmax + 0.4 || uSunShaft <= 0.0) return vec3(0.0);
    return vec3(0.9, 0.95, 1.0) * exp(-sg.x * sg.x / 0.02) * 6.0 * uSunShaft;
}

// volumetric scattering in smoke and venting mist
vec3 volume(vec3 ro, vec3 rd, float tmax, inout float trans) {
    float dmax = min(tmax, 14.0);
    const int N = 22;
    float dt = dmax / float(N);
    vec3 acc = vec3(0.0);
    float jitter = hash12(gl_FragCoord.xy + fract(uTime) * 37.0);
    for (int i = 0; i < N; i++) {
        float t = (float(i) + jitter) * dt;
        vec3 p = ro + rd * t;
        float beta = smoothstep(HZ - 0.2, HZ + 0.6, p.z);
        float nz = fbm3(p * 1.4 + vec3(0.0, uTime * 0.12, uTime * 0.08), 3);
        float dens = uSmoke * (0.15 + 0.85 * smoothstep(0.35, 0.75, nz)) * (1.0 - beta);
        // mist streaming towards the breach
        vec3 toB = normalize(BREACH - p);
        float mn = fbm3(p * 2.2 - toB * uTime * 2.5, 3);
        dens += uBetaMist * beta * (0.12 + 0.7 * mn);
        if (dens < 0.001) continue;
        vec3 kd, kc;
        float kdist;
        vec3 li = lightAt(p, vec3(0.0, 1.0, 0.0), -rd, vec3(1.0), 1.0, 0.0, false, kd, kdist, kc);
        vec3 s = li * dens * 1.4;
        acc += s * trans * dt;
        trans *= exp(-dens * dt * 0.35);
    }
    return acc;
}

vec3 sparksGlow(vec3 ro, vec3 rd, float tmax) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 24; i++) {
        if (i >= uSparkN) break;
        vec3 sp = uSpark[i].xyz;
        float tt = dot(sp - ro, rd);
        if (tt < 0.0 || tt > tmax) continue;
        float d = length(ro + rd * tt - sp);
        float w = 0.006 + tt * 0.0015;
        col += vec3(1.0, 0.6, 0.25) * uSpark[i].w * exp(-d * d / (w * w)) * 4.0;
    }
    return col;
}

void main() {
    vec3 ro = uCamPos;
    vec3 rd = cameraRay(gl_FragCoord.xy);
    float t = 0.01;
    float m = -1.0;
    for (int i = 0; i < 128; i++) {
        vec2 h = map(ro + rd * t);
        if (h.x < 0.0004 * t + 0.0005) { m = h.y; break; }
        t += h.x * 0.85;
        if (t > 30.0) break;
    }
    vec3 col = vec3(0.0);
    if (m >= 0.0) col = shade(ro, rd, t, m);
    else t = 30.0;
    float trans = 1.0;
    vec3 vol = volume(ro, rd, t, trans);
    col = col * trans + vol;
    col += breachGlow(ro, rd, t);
    col += sparksGlow(ro, rd, t);
    finalColor = vec4(max(col, 0.0), t);
}
