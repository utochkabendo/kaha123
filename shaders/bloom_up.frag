#version 330
// 9-tap tent upsample of the lower mip, added to the current mip
out vec4 finalColor;
uniform sampler2D texture0;  // current (larger) down mip
uniform sampler2D uLow;      // accumulated lower mip
uniform vec2 uTexel;         // 1 / low size
uniform vec2 uDstRes;
uniform float uRadius;

void main() {
    vec2 uv = gl_FragCoord.xy / uDstRes;
    vec2 t = uTexel * uRadius;
    vec3 s = texture(uLow, uv + t * vec2(-1, 1)).rgb + texture(uLow, uv + t * vec2(0, 1)).rgb * 2.0 + texture(uLow, uv + t * vec2(1, 1)).rgb
           + texture(uLow, uv + t * vec2(-1, 0)).rgb * 2.0 + texture(uLow, uv).rgb * 4.0 + texture(uLow, uv + t * vec2(1, 0)).rgb * 2.0
           + texture(uLow, uv + t * vec2(-1, -1)).rgb + texture(uLow, uv + t * vec2(0, -1)).rgb * 2.0 + texture(uLow, uv + t * vec2(1, -1)).rgb;
    finalColor = vec4(texture(texture0, uv).rgb + s / 16.0, 1.0);
}
