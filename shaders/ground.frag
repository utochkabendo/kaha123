#version 330
// ---------------------------------------------------------------------------
// ZARYA — ground set: dawn over the ocean. Sky scattering, cloud deck,
// waves with sun glitter, the capsule under its parachute, and the burning
// station crossing the sky like a meteor.
// ---------------------------------------------------------------------------
out vec4 finalColor;

#include "common.glsl"

uniform vec3 uSunDir;
uniform vec3 uChutePos;
uniform float uChute;
uniform float uMeteor;       // 0..1 progress, < 0 hidden
uniform float uMeteorSplit;  // 0..1 fragmentation

const float RE = 6371.0;
const float RA = 6471.0;
const vec3 BR = vec3(5.8e-3, 13.5e-3, 33.1e-3);
const float BM = 21e-3;
const float HR = 8.0;
const float HM = 1.2;
const float SUN_I = 11.0;

vec2 lightDepth(vec3 p, vec3 l) {
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

vec3 sky(vec3 rd) {
    vec3 ro = vec3(0.0, RE + 0.01, 0.0);
    float t1 = raySphere(ro, rd, vec3(0.0), RA).y;
    vec2 eg = raySphere(ro, rd, vec3(0.0), RE);
    if (eg.x > 0.0) t1 = eg.x;
    const int N = 12;
    float dt = t1 / float(N);
    vec2 odv = vec2(0.0);
    vec3 sr = vec3(0.0), sm = vec3(0.0);
    float mu = dot(rd, uSunDir);
    float pr = 3.0 / (16.0 * PI) * (1.0 + mu * mu);
    float g = 0.8;
    float pm = 3.0 / (8.0 * PI) * ((1.0 - g * g) * (1.0 + mu * mu)) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    for (int i = 0; i < N; i++) {
        vec3 p = ro + rd * (float(i) + 0.5) * dt;
        float h = max(length(p) - RE, 0.0);
        vec2 d = exp(-vec2(h / HR, h / HM)) * dt;
        odv += d;
        vec2 odl = lightDepth(p, uSunDir);
        vec3 T = exp(-(BR * (odv.x + odl.x) + BM * 1.1 * (odv.y + odl.y)));
        sr += d.x * T;
        sm += d.y * T;
    }
    vec3 col = SUN_I * (sr * BR * pr + sm * BM * pm);
    vec3 trans = exp(-(BR * odv.x + BM * 1.1 * odv.y));
    // sun disk
    col += vec3(1.0, 0.9, 0.75) * smoothstep(0.9997, 0.99985, mu) * 60.0 * trans;
    // fading stars high up
    vec3 sp = rd * 180.0;
    vec3 h3 = hash33(floor(sp));
    float st = step(0.985, h3.x) * smoothstep(0.15, 0.0, length(fract(sp) - 0.5 - (h3 - 0.5) * 0.5));
    col += st * vec3(0.8, 0.85, 1.0) * 0.6 * smoothstep(0.1, 0.5, rd.y) * smoothstep(0.08, -0.02, uSunDir.y);
    return col;
}

// cloud deck at 2.4 km, light from the low sun
vec4 clouds(vec3 ro, vec3 rd) {
    if (rd.y <= 0.002) return vec4(0.0);
    float t = (2400.0 - ro.y) / rd.y;
    vec2 p = (ro + rd * t).xz * 0.00022 + vec2(uTime * 0.004, 0.0);
    float d = fbm2(p * 3.0, 6);
    float cov = smoothstep(0.48, 0.72, d);
    float thick = smoothstep(0.5, 0.85, d);
    // light: underside glows orange/pink, thin edges bright
    float toSun = pow(sat(dot(normalize(rd.xz), normalize(uSunDir.xz)) * 0.5 + 0.5), 3.0);
    vec3 lit = mix(vec3(0.42, 0.3, 0.5), vec3(1.5, 0.62, 0.45), toSun);
    lit = mix(lit * 1.3, lit * 0.35, thick);
    float fade = smoothstep(0.002, 0.06, rd.y) * exp(-t * 0.00002);
    return vec4(lit, cov * fade);
}

// ---------------------------------------------------------------- ocean
float waveH(vec2 p) {
    float h = 0.0;
    float a = 0.5;
    vec2 d = vec2(1.0, 0.3);
    for (int i = 0; i < 5; i++) {
        float f = 0.08 * pow(1.9, float(i));
        h += a * sin(dot(d, p) * f + uTime * sqrt(9.8 * f) * 1.0);
        d = rot2(1.1) * d;
        a *= 0.5;
    }
    h += (fbm2(p * 0.35 + uTime * 0.3, 4) - 0.5) * 0.6;
    return h * 0.6;
}
vec3 waveN(vec2 p, float dist) {
    float e = 0.05 + dist * 0.01;
    float h = waveH(p);
    vec3 n = normalize(vec3(h - waveH(p + vec2(e, 0.0)), e, h - waveH(p + vec2(0.0, e))));
    // capillary detail fades with distance
    float det = smoothstep(60.0, 5.0, dist);
    vec2 q = p * 3.0 + uTime * 0.8;
    n = normalize(n + det * 0.15 * vec3(vnoise2(q) - 0.5, 0.0, vnoise2(q + 7.3) - 0.5));
    return n;
}

// ---------------------------------------------------------------- parachute
#define M_CHUTE 1.0
#define M_LINE 2.0
#define M_CAPS 3.0

vec2 mapChute(vec3 p) {
    vec3 c = p - uChutePos;
    float sway = sin(uTime * 0.7) * 0.06;
    c.xy = rot2(sway) * c.xy;
    // canopy: thin spherical cap with scalloped rim
    float R = 7.0;
    vec3 q = c - vec3(0.0, -3.0, 0.0);
    float az = atan(q.z, q.x);
    float breathe = 1.0 + 0.02 * sin(uTime * 2.0);
    float shell = abs(length(q * vec3(1.0, 1.25, 1.0)) - R * breathe) - 0.06;
    float cap = q.y - (3.2 + 0.25 * abs(sin(az * 8.0)));
    float canopy = max(shell, -cap);
    // vent hole at the apex
    canopy = max(canopy, -(length(q.xz) - 0.6));
    vec2 res = vec2(canopy, M_CHUTE);
    // suspension lines (8) to the capsule
    vec3 caps = vec3(0.0, -15.0, 0.0);
    float lines = 1e5;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785;
        vec3 rim = vec3(cos(a) * 5.4, 0.2, sin(a) * 5.4);
        lines = min(lines, sdCapsule(c, rim, caps + vec3(0.0, 1.4, 0.0), 0.012));
    }
    res = opU(res, vec2(lines, M_LINE));
    // capsule (descent module): bell shape
    vec3 k = c - caps;
    k.xz = rot2(uTime * 0.2) * k.xz;
    float body = sdConeZ(k.xzy, -1.0, 1.1, 1.15, 0.75) - 0.08;
    res = opU(res, vec2(body, M_CAPS));
    return res;
}

vec3 chuteNormal(vec3 p) {
    const float e = 0.01;
    vec2 k = vec2(1.0, -1.0);
    return normalize(k.xyy * mapChute(p + k.xyy * e).x + k.yyx * mapChute(p + k.yyx * e).x + k.yxy * mapChute(p + k.yxy * e).x + k.xxx * mapChute(p + k.xxx * e).x);
}

// ---------------------------------------------------------------- meteor
vec3 meteor(vec3 rd) {
    if (uMeteor < 0.0) return vec3(0.0);
    // path across the sky in direction space
    vec3 a = normalize(vec3(-0.9, 0.42, 0.9));
    vec3 b = normalize(vec3(0.7, 0.08, 1.0));
    vec3 col = vec3(0.0);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        if (i > 0 && uMeteorSplit < fi * 0.2) break;
        float lag = fi * 0.012 * uMeteorSplit;
        float pr = clamp(uMeteor - lag, 0.0, 1.0);
        vec3 side = normalize(cross(b - a, vec3(0.0, 1.0, 0.0)));
        vec3 head = normalize(mix(a, b, pr) + side * (fi - 2.0) * 0.004 * uMeteorSplit + vec3(0.0, -fi * 0.002 * uMeteorSplit, 0.0));
        vec3 dir = normalize(mix(a, b, pr) - mix(a, b, max(pr - 0.02, 0.0)));
        float along = -dot(rd - head, dir);
        vec3 perp = (rd - head) + dir * along;
        float w = 0.002 + max(along, 0.0) * 0.025;
        float trail = exp(-dot(perp, perp) / (w * w)) * step(0.0, along) * exp(-along * (i == 0 ? 3.0 : 7.0));
        float core = exp(-length(rd - head) * 1400.0);
        float glow = exp(-length(rd - head) * 90.0);
        float fl = 0.8 + 0.2 * sin(uTime * 30.0 + fi * 7.0);
        vec3 tcol = mix(vec3(1.0, 0.75, 0.4), vec3(1.0, 0.3, 0.1), sat(along * 6.0));
        float k = i == 0 ? 1.0 : 0.45;
        col += (tcol * trail * 14.0 + vec3(1.0, 0.95, 0.85) * core * 160.0 + vec3(1.0, 0.6, 0.3) * glow * 5.0) * fl * k;
    }
    return col * smoothstep(0.0, 0.05, uMeteor) * smoothstep(1.0, 0.9, uMeteor);
}

void main() {
    vec3 ro = uCamPos;
    vec3 rd = cameraRay(gl_FragCoord.xy);
    vec3 col;
    float depth = 60000.0;

    // parachute + capsule
    float tc = -1.0;
    float mc = 0.0;
    if (uChute > 0.5) {
        vec2 bs = raySphere(ro, rd, uChutePos + vec3(0.0, -6.0, 0.0), 13.0);
        if (bs.y > 0.0) {
            float t = max(bs.x, 0.0);
            for (int i = 0; i < 90; i++) {
                vec2 h = mapChute(ro + rd * t);
                if (h.x < 0.002 * t) { tc = t; mc = h.y; break; }
                t += h.x * 0.8;
                if (t > bs.y) break;
            }
        }
    }

    // ocean
    float tw = rd.y < 0.0 ? -(ro.y) / rd.y : -1.0;
    if (tc > 0.0 && (tw < 0.0 || tc < tw)) {
        vec3 p = ro + rd * tc;
        vec3 n = chuteNormal(p);
        vec3 alb;
        float rough = 0.8;
        if (mc == M_CHUTE) {
            vec3 q = p - uChutePos;
            float az = atan(q.z, q.x);
            float gore = step(0.5, fract(az / 6.2831 * 16.0));
            alb = mix(vec3(0.95, 0.35, 0.05), vec3(0.9, 0.88, 0.84), gore);
            // translucency: glowing when backlit by the sun
            float back = pow(sat(dot(rd, uSunDir)), 2.0);
            vec3 sunC = vec3(1.0, 0.6, 0.35) * 3.0;
            col = alb * (sat(dot(n, uSunDir)) * sunC + vec3(0.25, 0.3, 0.45) * (0.5 + 0.5 * n.y)) / PI;
            col += alb * sunC * back * 0.8;
        } else if (mc == M_LINE) {
            col = vec3(0.9, 0.7, 0.5) * 0.35;
        } else {
            alb = vec3(0.12, 0.1, 0.09) * (0.7 + 0.5 * vnoise(p * 3.0));
            vec3 sunC = vec3(1.0, 0.6, 0.35) * 3.0;
            col = alb * (sat(dot(n, uSunDir)) * sunC + vec3(0.25, 0.3, 0.45) * (0.5 + 0.5 * n.y)) / PI;
            col += vec3(1.0, 0.4, 0.1) * 0.8 * pow(sat(-n.y), 3.0) * vnoise(p * 5.0 + uTime);  // still glowing heat shield
        }
        depth = tc;
    } else if (tw > 0.0) {
        vec3 p = ro + rd * tw;
        vec3 n = waveN(p.xz, tw);
        vec3 r = reflect(rd, n);
        r.y = abs(r.y);
        float fres = 0.02 + 0.98 * pow(1.0 - sat(dot(n, -rd)), 5.0);
        vec3 refl = sky(r) * 0.8;
        vec4 cl = clouds(p, r);
        refl = mix(refl, cl.rgb, cl.a);
        refl += meteor(r) * 0.6;
        vec3 deep = vec3(0.004, 0.02, 0.03);
        vec3 sss = vec3(0.02, 0.09, 0.08) * pow(sat(waveH(p.xz) * 0.8 + 0.4), 2.0) * sat(dot(uSunDir, -rd) * 0.5 + 0.5);
        col = mix(deep + sss, refl, fres);
        // sun glitter
        vec3 hv = normalize(uSunDir - rd);
        float gl = pow(sat(dot(n, hv)), 900.0) * 400.0 / (1.0 + tw * 0.01);
        col += vec3(1.0, 0.75, 0.45) * gl * smoothstep(-0.02, 0.05, uSunDir.y);
        // aerial perspective
        float fog = 1.0 - exp(-tw * 0.00012);
        col = mix(col, sky(normalize(vec3(rd.x, 0.02, rd.z))), fog);
        depth = tw;
    } else {
        col = sky(rd);
        vec4 cl = clouds(ro, rd);
        col = mix(col, cl.rgb, cl.a);
        col += meteor(rd);
    }
    finalColor = vec4(max(col, 0.0), depth);
}
