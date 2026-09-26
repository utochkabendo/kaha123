#version 330
// 13-tap downsample (Jimenez, "Next generation post processing in Call of Duty")
// The first pass also applies a soft threshold and a firefly-suppressing average.
in vec2 fragTexCoord;
out vec4 finalColor;
uniform sampler2D texture0;
uniform vec2 uTexel;      // 1 / source size
uniform vec2 uDstRes;     // destination size
uniform float uPrefilter; // 1 on the first pass
uniform float uThreshold;

vec3 S(vec2 uv) { return texture(texture0, uv).rgb; }
float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 karis(vec3 c) { return c / (1.0 + lum(c) * 0.25); }

void main() {
    vec2 uv = gl_FragCoord.xy / uDstRes;
    vec2 t = uTexel;
    vec3 a = S(uv + t * vec2(-2, 2)), b = S(uv + t * vec2(0, 2)), c = S(uv + t * vec2(2, 2));
    vec3 d = S(uv + t * vec2(-2, 0)), e = S(uv), f = S(uv + t * vec2(2, 0));
    vec3 g = S(uv + t * vec2(-2, -2)), h = S(uv + t * vec2(0, -2)), i = S(uv + t * vec2(2, -2));
    vec3 j = S(uv + t * vec2(-1, 1)), k = S(uv + t * vec2(1, 1)), l = S(uv + t * vec2(-1, -1)), m = S(uv + t * vec2(1, -1));
    vec3 col;
    if (uPrefilter > 0.5) {
        vec3 g0 = karis((j + k + l + m) * 0.25);
        vec3 g1 = karis((a + b + d + e) * 0.25), g2 = karis((b + c + e + f) * 0.25);
        vec3 g3 = karis((d + e + g + h) * 0.25), g4 = karis((e + f + h + i) * 0.25);
        col = g0 * 0.5 + (g1 + g2 + g3 + g4) * 0.125;
        float br = lum(col);
        float knee = uThreshold * 0.5;
        float soft = clamp(br - uThreshold + knee, 0.0, 2.0 * knee);
        soft = soft * soft / (4.0 * knee + 1e-4);
        float w = max(soft, br - uThreshold) / max(br, 1e-4);
        col *= w;
    } else {
        col = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
    }
    finalColor = vec4(min(col, vec3(6e4)), 1.0);
}
