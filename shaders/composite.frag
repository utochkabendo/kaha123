#version 330
// ---------------------------------------------------------------------------
// ZARYA — final composite: depth of field, bloom, lens flare, visor crack,
// ACES tone mapping, colour grading, chromatic aberration, vignette, grain.
// ---------------------------------------------------------------------------
out vec4 finalColor;

uniform sampler2D texture0;  // HDR scene, alpha = depth (m)
uniform sampler2D uBloom;
uniform vec2 uRes;           // viewport size in pixels
uniform vec2 uOffset;        // viewport offset in the window
uniform float uTime;
uniform float uExposure;
uniform float uFocus;
uniform float uAperture;
uniform vec3 uTint;
uniform float uSat;
uniform float uContrast;
uniform vec3 uLift;
uniform vec3 uGain;
uniform float uVignette;
uniform float uGrain;
uniform float uAberr;
uniform float uBloomAmt;
uniform float uFade;
uniform float uFlash;
uniform vec3 uFlashCol;
uniform float uCrack;
uniform vec2 uCrackPos;
uniform float uHelmet;
uniform float uRedPulse;
uniform float uShock;
uniform vec3 uSun;           // xy = sun position in uv, z = on-screen intensity
uniform float uFlare;

float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 aces(vec3 x) {
    x *= 0.6;
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// procedural cracked visor: returns crack line mask; writes a refraction offset
float crackPattern(vec2 p, out vec2 offs) {
    vec2 d = p - uCrackPos;
    d.x *= uRes.x / uRes.y;
    float r = length(d);
    float a = atan(d.y, d.x);
    float lines = 0.0;
    offs = vec2(0.0);
    // radial cracks
    const float N = 13.0;
    float sector = floor((a / 6.2831853 + 0.5) * N);
    float h = hash12(vec2(sector, 7.0));
    float ca = (sector + 0.5 + (h - 0.5) * 0.6) / N * 6.2831853 - 3.14159265;
    float wig = sin(r * 40.0 + h * 20.0) * 0.012 + sin(r * 90.0 + h * 9.0) * 0.005;
    float da = abs(a - ca - wig / max(r, 0.02)) * r;
    float len = 0.25 + h * 0.5;
    lines += smoothstep(0.0025, 0.0, da) * smoothstep(len, len * 0.5, r);
    // concentric fractures
    float ring = abs(fract(r * 9.0 + hash12(vec2(sector, 3.0)) * 0.4) - 0.5);
    lines += smoothstep(0.012, 0.0, ring * 0.11) * step(hash12(vec2(sector, floor(r * 9.0))), 0.45) * smoothstep(0.35, 0.05, r);
    // impact star
    lines += smoothstep(0.03, 0.0, r) * 1.5;
    offs = normalize(d + 1e-5) * 0.004 * smoothstep(0.4, 0.0, r) * sign(sin(sector * 3.7));
    return clamp(lines, 0.0, 1.5);
}

vec4 dofSample(vec2 uv) {
    vec4 c = texture(texture0, uv);
    return c;
}

vec3 depthOfField(vec2 uv) {
    vec4 center = texture(texture0, uv);
    if (uAperture <= 0.0) return center.rgb;
    float px = uRes.y / 1080.0;
    float coc0 = clamp(abs(1.0 / max(uFocus, 0.05) - 1.0 / max(center.a, 0.05)) * uAperture * uFocus, 0.0, 22.0) * px;
    vec3 acc = center.rgb;
    float tot = 1.0;
    float rad = 0.6;
    const float GA = 2.39996323;
    float maxR = 22.0 * px;
    for (float ang = 0.0; rad < maxR; ang += GA) {
        vec2 tc = uv + vec2(cos(ang), sin(ang)) * rad / uRes;
        vec4 s = texture(texture0, tc);
        float coc = clamp(abs(1.0 / max(uFocus, 0.05) - 1.0 / max(s.a, 0.05)) * uAperture * uFocus, 0.0, 22.0) * px;
        if (s.a > center.a) coc = clamp(coc, 0.0, coc0 * 2.0);
        float m = smoothstep(rad - 0.5, rad + 0.5, coc);
        acc += mix(acc / tot, s.rgb, m);
        tot += 1.0;
        rad += 1.0 / rad;
        if (tot > 48.0) break;
    }
    return acc / tot;
}

vec3 lensFlare(vec2 uv) {
    if (uSun.z <= 0.001) return vec3(0.0);
    vec2 asp = vec2(uRes.x / uRes.y, 1.0);
    vec2 s = uSun.xy;
    vec2 c = vec2(0.5);
    vec3 col = vec3(0.0);
    // occlusion: is the sun disk actually visible in the rendered frame?
    float vis = 0.0;
    for (int i = 0; i < 9; i++) {
        vec2 o = vec2(float(i % 3) - 1.0, float(i / 3) - 1.0) * 0.006;
        vec4 sc = texture(texture0, s + o);
        vis += step(40000.0, sc.a) * clamp(lum(sc.rgb) / 30.0, 0.0, 1.0);
    }
    vis /= 9.0;
    if (vis <= 0.0) return vec3(0.0);
    vec2 dir = c - s;
    for (int i = 1; i <= 5; i++) {
        float f = float(i) * 0.38 - 0.2;
        vec2 gp = s + dir * f * 2.0;
        float size = 0.03 + 0.05 * fract(float(i) * 0.618);
        float d = length((uv - gp) * asp);
        float ring = smoothstep(size, size * 0.85, d) * 0.6 + smoothstep(size * 1.02, size * 0.97, d) * 0.4;
        vec3 tint = 0.5 + 0.5 * cos(6.2831 * (float(i) * 0.17 + vec3(0.0, 0.33, 0.67)));
        col += tint * ring * 0.035;
    }
    // anamorphic streak + glow
    vec2 d = (uv - s) * asp;
    col += vec3(0.55, 0.7, 1.0) * exp(-abs(d.y) * 260.0) * exp(-abs(d.x) * 2.2) * 0.9;
    col += vec3(1.0, 0.85, 0.7) * exp(-length(d) * 9.0) * 0.25;
    // starburst
    float a = atan(d.y, d.x);
    float rays = pow(abs(sin(a * 6.0 + 0.3)), 60.0) + pow(abs(sin(a * 9.0 + 1.1)), 90.0) * 0.6;
    col += vec3(1.0, 0.9, 0.8) * rays * exp(-length(d) * 7.0) * 0.4;
    return col * uSun.z * uFlare * vis;
}

void main() {
    vec2 frag = gl_FragCoord.xy - uOffset;
    vec2 uv = frag / uRes;

    // visor crack refraction
    vec2 coff = vec2(0.0);
    float crack = 0.0;
    if (uCrack > 0.0) crack = crackPattern(uv, coff) * uCrack;
    uv += coff * uCrack;

    // helmet visor curvature
    if (uHelmet > 0.0) {
        vec2 q = uv - 0.5;
        uv = 0.5 + q * (1.0 - uHelmet * 0.06 * dot(q, q));
    }

    // shock radial blur
    vec3 col;
    if (uShock > 0.001) {
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 10; i++) {
            float k = 1.0 - float(i) * 0.012 * uShock;
            acc += depthOfField(0.5 + (uv - 0.5) * k);
        }
        col = acc / 10.0;
    } else {
        col = depthOfField(uv);
    }

    // chromatic aberration (in HDR)
    vec2 ca = (uv - 0.5) * uAberr * (1.0 + uShock * 4.0);
    col.r = mix(col.r, texture(texture0, uv + ca).r, 0.85);
    col.b = mix(col.b, texture(texture0, uv - ca).b, 0.85);

    vec3 bloom = texture(uBloom, uv).rgb;
    col += bloom * uBloomAmt;
    col += lensFlare(uv);

    col *= uExposure * uTint;

    // crack lines catch light
    col += vec3(0.8, 0.9, 1.0) * crack * (0.2 + lum(col) * 0.6);

    col = aces(col);

    // grading
    float l = lum(col);
    col = mix(vec3(l), col, uSat);
    col = (col - 0.5) * uContrast + 0.5;
    // split toning: cool shadows, warm highlights — pure black stays black
    vec3 shadows = vec3(-0.004, 0.008, 0.02) * smoothstep(0.0, 0.12, l) * (1.0 - smoothstep(0.12, 0.5, l));
    vec3 highs = vec3(0.035, 0.012, -0.02) * smoothstep(0.5, 1.0, l);
    col += shadows + highs;
    col = col * uGain + uLift * (1.0 - col);
    col = max(col, 0.0);

    // vignette + red alarm pulse
    vec2 q = uv - 0.5;
    float vig = 1.0 - uVignette * dot(q * vec2(1.3, 1.0), q * vec2(1.3, 1.0)) * 2.2;
    col *= clamp(vig, 0.0, 1.0);
    col += vec3(0.5, 0.02, 0.0) * uRedPulse * smoothstep(0.15, 0.7, length(q * vec2(1.2, 1.0)));

    // helmet frame: darkened rim + breath fog
    if (uHelmet > 0.0) {
        float r = length(q * vec2(uRes.x / uRes.y * 0.62, 1.0));
        float rim = smoothstep(0.5, 0.72, r);
        col *= 1.0 - rim * 0.92 * uHelmet;
        float fog = smoothstep(0.35, 0.0, length(q - vec2(0.0, -0.55))) * (0.5 + 0.5 * sin(uTime * 1.7));
        col = mix(col, vec3(0.75, 0.8, 0.85) * (0.3 + l), fog * 0.12 * uHelmet);
    }

    // flash / fade
    col = mix(col, uFlashCol, clamp(uFlash, 0.0, 1.0));
    col *= 1.0 - clamp(uFade, 0.0, 1.0);

    // gamma (sRGB approx)
    col = pow(max(col, 0.0), vec3(1.0 / 2.2));

    // film grain in display space, strongest in the mid-tones
    float g = hash12(frag + fract(uTime * 13.7) * 1000.0) - 0.5;
    float gl = dot(col, vec3(0.333));
    col += g * uGrain * (0.25 + 1.5 * gl * (1.0 - gl));
    col += (hash12(frag * 1.37 + 17.0) - 0.5) / 255.0;
    finalColor = vec4(col, 1.0);
}
