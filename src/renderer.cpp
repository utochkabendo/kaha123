#include "renderer.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <regex>
#include <sstream>

#include "rlgl.h"

// ------------------------------------------------------------ basis helpers
Basis RotX(float a) {
    Basis b;
    float c = cosf(a), s = sinf(a);
    b.y = V3(0, c, s);
    b.z = V3(0, -s, c);
    return b;
}
Basis RotY(float a) {
    Basis b;
    float c = cosf(a), s = sinf(a);
    b.x = V3(c, 0, -s);
    b.z = V3(s, 0, c);
    return b;
}
Basis RotZ(float a) {
    Basis b;
    float c = cosf(a), s = sinf(a);
    b.x = V3(c, s, 0);
    b.y = V3(-s, c, 0);
    return b;
}
Basis Euler(float yaw, float pitch, float roll) { return RotY(yaw).Mul(RotX(pitch)).Mul(RotZ(roll)); }
Basis LookBasis(Vector3 f, Vector3 up) {
    Basis b;
    b.z = Norm(f);
    b.x = Norm(Cross(up, b.z));
    b.y = Cross(b.z, b.x);
    return b;
}

Pose PoseLerp(const Pose& a, const Pose& b, float t) {
    Pose r;
    const float* pa = &a.lShPitch;
    const float* pb = &b.lShPitch;
    float* pr = &r.lShPitch;
    int n = (int)(sizeof(Pose) / sizeof(float));
    for (int i = 0; i < n; i++) pr[i] = Mix(pa[i], pb[i], t);
    return r;
}

static Vector3 LimbDir(float pitchDeg, float rollDeg, float yawDeg, float side) {
    float p = Deg(pitchDeg), r = Deg(rollDeg) * side, y = Deg(yawDeg) * side;
    Vector3 d = V3(0, -cosf(p), sinf(p));
    // abduction: rotate around z, towards +x for the left side
    Vector3 e = V3(d.x * cosf(r) - d.y * sinf(r), d.x * sinf(r) + d.y * cosf(r), d.z);
    // swing inwards (yaw around y)
    Vector3 f = V3(e.x * cosf(y) - e.z * sinf(y), e.y, e.x * sinf(y) + e.z * cosf(y));
    return f;
}

void PoseJoints(const Pose& p, Vector3 out[12]) {
    // left = +x, right = -x (suit faces +z)
    const float upper = 0.29f, fore = 0.27f, thigh = 0.40f, shin = 0.40f;
    Vector3 ls = V3(0.25f, 0.44f, 0), rs = V3(-0.25f, 0.44f, 0);
    Vector3 le = ls + LimbDir(p.lShPitch, p.lShRoll, -p.lReach, 1) * upper;
    Vector3 lh = le + LimbDir(p.lShPitch + p.lElbow, p.lShRoll * 0.6f, -p.lReach, 1) * fore;
    Vector3 re = rs + LimbDir(p.rShPitch, p.rShRoll, -p.rReach, -1) * upper;
    Vector3 rh = re + LimbDir(p.rShPitch + p.rElbow, p.rShRoll * 0.6f, -p.rReach, -1) * fore;
    Vector3 lp = V3(0.11f, -0.1f, 0), rp = V3(-0.11f, -0.1f, 0);
    Vector3 lk = lp + LimbDir(p.lHip, 4, 0, 1) * thigh;
    Vector3 lf = lk + LimbDir(p.lHip - p.lKnee, 3, 0, 1) * shin;
    Vector3 rk = rp + LimbDir(p.rHip, 4, 0, -1) * thigh;
    Vector3 rf = rk + LimbDir(p.rHip - p.rKnee, 3, 0, -1) * shin;
    out[0] = ls; out[1] = le; out[2] = lh;
    out[3] = rs; out[4] = re; out[5] = rh;
    out[6] = lp; out[7] = lk; out[8] = lf;
    out[9] = rp; out[10] = rk; out[11] = rf;
}

// ------------------------------------------------------------ shader program
int ShaderProg::Loc(const char* u) {
    auto it = locs.find(u);
    if (it != locs.end()) return it->second;
    int l = GetShaderLocation(sh, u);
    locs[u] = l;
    return l;
}
void ShaderProg::F(const char* u, float v) {
    int l = Loc(u);
    if (l >= 0) SetShaderValue(sh, l, &v, SHADER_UNIFORM_FLOAT);
}
void ShaderProg::V2(const char* u, float x, float y) {
    int l = Loc(u);
    float v[2] = {x, y};
    if (l >= 0) SetShaderValue(sh, l, v, SHADER_UNIFORM_VEC2);
}
void ShaderProg::V3v(const char* u, Vector3 v) {
    int l = Loc(u);
    if (l >= 0) SetShaderValue(sh, l, &v, SHADER_UNIFORM_VEC3);
}
void ShaderProg::V4(const char* u, Vector4 v) {
    int l = Loc(u);
    if (l >= 0) SetShaderValue(sh, l, &v, SHADER_UNIFORM_VEC4);
}
void ShaderProg::I(const char* u, int v) {
    int l = Loc(u);
    if (l >= 0) SetShaderValue(sh, l, &v, SHADER_UNIFORM_INT);
}
void ShaderProg::V3Arr(const char* u, const Vector3* v, int n) {
    int l = Loc(u);
    if (l >= 0 && n > 0) SetShaderValueV(sh, l, v, SHADER_UNIFORM_VEC3, n);
}
void ShaderProg::V4Arr(const char* u, const Vector4* v, int n) {
    int l = Loc(u);
    if (l >= 0 && n > 0) SetShaderValueV(sh, l, v, SHADER_UNIFORM_VEC4, n);
}
void ShaderProg::FArr(const char* u, const float* v, int n) {
    int l = Loc(u);
    if (l >= 0 && n > 0) SetShaderValueV(sh, l, v, SHADER_UNIFORM_FLOAT, n);
}
void ShaderProg::Tex(const char* u, Texture2D t) {
    int l = Loc(u);
    if (l >= 0) SetShaderValueTexture(sh, l, t);
}

static std::string ReadText(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return {};
    std::stringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

static std::string Preprocess(const std::string& file) {
    std::string src = ReadText(AssetPath("shaders/" + file));
    if (src.empty()) {
        TraceLog(LOG_ERROR, "SHADER: cannot read %s", file.c_str());
        return {};
    }
    std::string out;
    std::istringstream in(src);
    std::string line;
    while (std::getline(in, line)) {
        auto p = line.find("#include \"");
        if (p != std::string::npos) {
            auto q = line.find('"', p + 10);
            out += Preprocess(line.substr(p + 10, q - p - 10));
            out += "\n";
        } else {
            out += line + "\n";
        }
    }
    return out;
}

static ShaderProg LoadProg(const char* file) {
    ShaderProg p;
    p.name = file;
    std::string src = Preprocess(file);
    if (!src.empty()) p.sh = LoadShaderFromMemory(nullptr, src.c_str());
    if (p.sh.id == rlGetShaderIdDefault()) {
        TraceLog(LOG_ERROR, "SHADER: %s failed to compile, see log above", file);
        p.sh.id = 0;
    }
    return p;
}

// ------------------------------------------------------------ render targets
static RenderTexture2D LoadHdrTarget(int w, int h) {
    RenderTexture2D t{};
    t.id = rlLoadFramebuffer();
    if (!t.id) return t;
    rlEnableFramebuffer(t.id);
    t.texture.id = rlLoadTexture(nullptr, w, h, RL_PIXELFORMAT_UNCOMPRESSED_R16G16B16A16, 1);
    t.texture.width = w;
    t.texture.height = h;
    t.texture.format = PIXELFORMAT_UNCOMPRESSED_R16G16B16A16;
    t.texture.mipmaps = 1;
    rlFramebufferAttach(t.id, t.texture.id, RL_ATTACHMENT_COLOR_CHANNEL0, RL_ATTACHMENT_TEXTURE2D, 0);
    if (!rlFramebufferComplete(t.id)) TraceLog(LOG_WARNING, "HDR framebuffer incomplete");
    rlDisableFramebuffer();
    SetTextureFilter(t.texture, TEXTURE_FILTER_BILINEAR);
    SetTextureWrap(t.texture, TEXTURE_WRAP_CLAMP);
    return t;
}
static void FreeHdr(RenderTexture2D& t) {
    if (t.id) {
        rlUnloadTexture(t.texture.id);
        rlUnloadFramebuffer(t.id);
    }
    t = RenderTexture2D{};
}

bool Renderer::LoadAll() {
    space_ = LoadProg("space.frag");
    interior_ = LoadProg("interior.frag");
    ground_ = LoadProg("ground.frag");
    down_ = LoadProg("bloom_down.frag");
    up_ = LoadProg("bloom_up.frag");
    composite_ = LoadProg("composite.frag");
    return composite_.Valid() && down_.Valid() && up_.Valid();
}

bool Renderer::Init() { return LoadAll(); }

void Renderer::ReloadShaders() {
    ShaderProg* all[] = {&space_, &interior_, &ground_, &down_, &up_, &composite_};
    for (auto* p : all)
        if (p->Valid()) UnloadShader(p->sh);
    LoadAll();
}

void Renderer::FreeTargets() {
    FreeHdr(scene_);
    for (int i = 0; i < kMips; i++) {
        FreeHdr(down_rt_[i]);
        FreeHdr(up_rt_[i]);
    }
    mips_ = 0;
}

void Renderer::Unload() {
    FreeTargets();
    ShaderProg* all[] = {&space_, &interior_, &ground_, &down_, &up_, &composite_};
    for (auto* p : all)
        if (p->Valid()) UnloadShader(p->sh);
}

void Renderer::EnsureTargets(int w, int h) {
    if (w == rw_ && h == rh_ && scene_.id) return;
    FreeTargets();
    rw_ = w;
    rh_ = h;
    scene_ = LoadHdrTarget(w, h);
    int mw = std::max(w / 2, 1), mh = std::max(h / 2, 1);
    mips_ = 0;
    for (int i = 0; i < kMips && mw >= 4 && mh >= 4; i++) {
        down_rt_[i] = LoadHdrTarget(mw, mh);
        up_rt_[i] = LoadHdrTarget(mw, mh);
        mips_++;
        mw /= 2;
        mh /= 2;
    }
}

void Renderer::SetQuality(int q) {
    quality_ = std::clamp(q, 0, 3);
    static const float caps[4] = {0.45f, 0.62f, 0.8f, 1.0f};
    if (fixedScale_ <= 0) scale_ = std::min(scale_, caps[quality_]);
    if (fixedScale_ <= 0 && scale_ < caps[quality_] * 0.6f) scale_ = caps[quality_] * 0.7f;
}

const char* Renderer::QualityName() const {
    static const char* n[4] = {"НИЗКОЕ", "СРЕДНЕЕ", "ВЫСОКОЕ", "УЛЬТРА"};
    return n[quality_];
}

void Renderer::UpdateDynamicResolution(float ft) {
    if (fixedScale_ > 0) return;
    static const float caps[4] = {0.45f, 0.62f, 0.8f, 1.0f};
    float cap = caps[quality_];
    ftAccum_ += ft;
    ftCount_++;
    cooldown_ -= ft;
    if (ftAccum_ < 0.4f) return;
    float avg = ftAccum_ / ftCount_;
    ftAccum_ = 0;
    ftCount_ = 0;
    if (avg > 1.0f / 45.0f && scale_ > 0.3f) {
        scale_ = std::max(0.3f, scale_ * (avg > 1.0f / 25.0f ? 0.8f : 0.92f));
        cooldown_ = 4.0f;
        stableTime_ = 0;
    } else if (avg < 1.0f / 56.0f) {
        stableTime_ += 0.4f;
        if (cooldown_ <= 0 && stableTime_ > 2.0f && scale_ < cap) {
            scale_ = std::min(cap, scale_ + 0.05f);
            stableTime_ = 0;
        }
    }
    scale_ = std::min(scale_, cap);
}

// ------------------------------------------------------------ passes
void Renderer::FullQuad(int w, int h) { DrawRectangle(0, 0, w, h, WHITE); }

void Renderer::SetCommon(ShaderProg& p, const SceneState& s, float time, int w, int h) {
    // camera basis with roll and handheld wobble
    Vector3 pos = s.camPos;
    Vector3 tgt = s.camTarget;
    Vector3 fwd = Norm(tgt - pos);
    Vector3 right = Norm(Cross(fwd, s.camUp));
    Vector3 up = Cross(right, fwd);
    float rollA = Deg(s.roll);
    if (s.handheld > 0) {
        float f = s.handFreq;
        float hx = (Noise1(time * 0.9f * f) * 0.7f + Noise1(time * 2.3f * f + 7) * 0.3f) * s.handheld;
        float hy = (Noise1(time * 0.8f * f + 3) * 0.7f + Noise1(time * 2.1f * f + 11) * 0.3f) * s.handheld;
        fwd = Norm(fwd + right * hx * 0.02f + up * hy * 0.02f);
        rollA += Noise1(time * 0.6f * f + 5) * s.handheld * 0.012f;
        right = Norm(Cross(fwd, s.camUp));
        up = Cross(right, fwd);
    }
    Vector3 r2 = right * cosf(rollA) + up * sinf(rollA);
    Vector3 u2 = Cross(r2, fwd);
    camPos = pos;
    camFwd = fwd;
    camRight = r2;
    camUp = u2;
    tanHalfFov = tanf(Deg(s.fov) * 0.5f);

    p.V2("uRes", (float)w, (float)h);
    p.F("uTime", time);
    p.V3v("uCamPos", pos);
    p.V3v("uCamFwd", fwd);
    p.V3v("uCamRight", r2);
    p.V3v("uCamUp", u2);
    p.F("uTanHalfFov", tanHalfFov);
}

void Renderer::SetAstro(ShaderProg& p, const SceneState& s) {
    Vector3 joints[24];
    Vector3 basis[6];
    Vector3 pos[2];
    float on[2], style[2];
    for (int k = 0; k < 2; k++) {
        PoseJoints(s.astro[k].pose, joints + k * 12);
        basis[k * 3 + 0] = s.astro[k].rot.x;
        basis[k * 3 + 1] = s.astro[k].rot.y;
        basis[k * 3 + 2] = s.astro[k].rot.z;
        pos[k] = s.astro[k].pos;
        on[k] = s.astro[k].on ? 1.0f : 0.0f;
        style[k] = (float)s.astro[k].style;
    }
    p.V3Arr("uJ", joints, 24);
    p.V3Arr("uAstroB", basis, 6);
    p.V3Arr("uAstroPos", pos, 2);
    p.FArr("uAstroOn", on, 2);
    p.FArr("uAstroStyle", style, 2);
}

void Renderer::DrawSpace(const SceneState& s, float time, int w, int h) {
    ShaderProg& p = space_;
    BeginShaderMode(p.sh);
    SetCommon(p, s, time, w, h);
    SetAstro(p, s);
    p.V3v("uSunDir", Norm(s.sunDir));
    p.V3v("uSunCol", s.sunCol);
    p.F("uSunSize", s.sunSize);
    p.F("uAltitude", s.altitude);
    p.F("uEarthRot", s.earthRot);
    p.F("uStars", s.stars);
    p.F("uEarthshine", s.earthshine);
    p.F("uStationOn", s.stationOn ? 1.0f : 0.0f);
    Vector3 sb[3] = {s.stationRot.x, s.stationRot.y, s.stationRot.z};
    p.V3Arr("uStationB", sb, 3);
    p.V3v("uStationPos", s.stationPos);
    p.F("uPanelAngle", s.panelAngle);
    p.F("uDamage", s.damage);
    p.F("uBetaVent", s.betaVent);
    p.F("uThrust", s.thrust);
    p.F("uHeat", s.heat);
    p.V3v("uHeatDir", Norm(s.heatDir));
    p.F("uCapsuleMode", (float)s.capsuleMode);
    p.V3v("uCapsulePos", s.capsulePos);
    Vector3 cb[3] = {s.capsuleRot.x, s.capsuleRot.y, s.capsuleRot.z};
    p.V3Arr("uCapsuleB", cb, 3);
    p.F("uPlasma", s.plasma);
    p.F("uDish", s.dishAngle);
    p.F("uAirlock", s.airlock);
    p.F("uNav", s.navLights);
    p.V4("uBurst", s.burst);
    int nd = std::min((int)s.debris.size(), 16);
    p.I("uDebN", nd);
    if (nd) {
        p.V4Arr("uDeb", s.debris.data(), nd);
        p.V4Arr("uDebV", s.debrisVel.data(), nd);
    }
    int ns = std::min((int)s.sparks.size(), 24);
    p.I("uSparkN", ns);
    if (ns) p.V4Arr("uSpark", s.sparks.data(), ns);
    Vector4 chunks[3] = {};
    // chunks are debris entries with size >= 0.25 closest to the camera
    int nc = 0;
    for (int i = 0; i < (int)s.debris.size() && nc < 3; i++)
        if (s.debris[i].w >= 0.25f) chunks[nc++] = s.debris[i];
    p.V4Arr("uChunk", chunks, 3);
    p.V3v("uPointPos", s.pointPos);
    p.V3v("uPointCol", s.pointCol);
    FullQuad(w, h);
    EndShaderMode();
}

void Renderer::DrawInterior(const SceneState& s, float time, int w, int h) {
    ShaderProg& p = interior_;
    BeginShaderMode(p.sh);
    SetCommon(p, s, time, w, h);
    SetAstro(p, s);
    p.F("uMainLight", s.mainLight);
    p.F("uAlarm", s.alarmLight);
    p.F("uSmoke", s.smoke);
    p.F("uHatch", s.hatchOpen);
    p.F("uBetaMist", s.betaMist);
    p.F("uSunShaft", s.sunShaft);
    p.F("uFlashlight", s.flashlight);
    p.V3v("uFlashPos", s.flashlightPos);
    p.V3v("uFlashDir", Norm(s.flashlightDir));
    p.F("uFloaters", s.floaters);
    p.F("uSeal", s.sealLock);
    p.F("uAirIn", s.airlockIn);
    int ns = std::min((int)s.sparks.size(), 24);
    p.I("uSparkN", ns);
    if (ns) p.V4Arr("uSpark", s.sparks.data(), ns);
    p.V3v("uPointPos", s.pointPos);
    p.V3v("uPointCol", s.pointCol);
    FullQuad(w, h);
    EndShaderMode();
}

void Renderer::DrawGround(const SceneState& s, float time, int w, int h) {
    ShaderProg& p = ground_;
    BeginShaderMode(p.sh);
    SetCommon(p, s, time, w, h);
    SetAstro(p, s);
    float el = Deg(s.sunElev), az = Deg(s.sunAz);
    Vector3 sun = V3(sinf(az) * cosf(el), sinf(el), cosf(az) * cosf(el));
    p.V3v("uSunDir", sun);
    p.V3v("uChutePos", s.chutePos);
    p.F("uChute", s.chuteOn);
    p.F("uMeteor", s.meteor);
    p.F("uMeteorSplit", s.meteorSplit);
    FullQuad(w, h);
    EndShaderMode();
}

void Renderer::Bloom(int w, int h) {
    if (!mips_) return;
    // downsample chain
    for (int i = 0; i < mips_; i++) {
        RenderTexture2D& dst = down_rt_[i];
        const Texture2D& src = i == 0 ? scene_.texture : down_rt_[i - 1].texture;
        BeginTextureMode(dst);
        BeginShaderMode(down_.sh);
        down_.V2("uTexel", 1.0f / src.width, 1.0f / src.height);
        down_.V2("uDstRes", (float)dst.texture.width, (float)dst.texture.height);
        down_.F("uPrefilter", i == 0 ? 1.0f : 0.0f);
        down_.F("uThreshold", 1.1f);
        DrawTexturePro(src, Rectangle{0, 0, (float)src.width, (float)src.height},
                       Rectangle{0, 0, (float)dst.texture.width, (float)dst.texture.height}, Vector2{0, 0}, 0, WHITE);
        EndShaderMode();
        EndTextureMode();
    }
    // upsample chain
    for (int i = mips_ - 1; i >= 0; i--) {
        RenderTexture2D& dst = up_rt_[i];
        if (i == mips_ - 1) {
            BeginTextureMode(dst);
            DrawTexturePro(down_rt_[i].texture, Rectangle{0, 0, (float)dst.texture.width, (float)dst.texture.height},
                           Rectangle{0, 0, (float)dst.texture.width, (float)dst.texture.height}, Vector2{0, 0}, 0, WHITE);
            EndTextureMode();
            continue;
        }
        const Texture2D& low = up_rt_[i + 1].texture;
        BeginTextureMode(dst);
        BeginShaderMode(up_.sh);
        up_.Tex("uLow", low);
        up_.V2("uTexel", 1.0f / low.width, 1.0f / low.height);
        up_.V2("uDstRes", (float)dst.texture.width, (float)dst.texture.height);
        up_.F("uRadius", 1.0f);
        DrawTexturePro(down_rt_[i].texture, Rectangle{0, 0, (float)dst.texture.width, (float)dst.texture.height},
                       Rectangle{0, 0, (float)dst.texture.width, (float)dst.texture.height}, Vector2{0, 0}, 0, WHITE);
        EndShaderMode();
        EndTextureMode();
    }
    (void)w;
    (void)h;
}

void Renderer::Composite(const SceneState& s, float time, Rectangle vp) {
    ShaderProg& p = composite_;
    BeginShaderMode(p.sh);
    p.Tex("uBloom", mips_ ? up_rt_[0].texture : scene_.texture);
    p.V2("uRes", vp.width, vp.height);
    p.V2("uOffset", vp.x, (float)GetRenderHeight() - vp.y - vp.height);
    p.F("uTime", time);
    p.F("uExposure", s.exposure);
    float focus = s.focus > 0 ? s.focus : Len(s.camTarget - s.camPos);
    p.F("uFocus", focus);
    p.F("uAperture", s.aperture);
    p.V3v("uTint", s.tint);
    p.F("uSat", s.saturation);
    p.F("uContrast", s.contrast);
    p.V3v("uLift", s.lift);
    p.V3v("uGain", s.gain);
    p.F("uVignette", s.vignette);
    p.F("uGrain", s.grain);
    p.F("uAberr", s.aberration);
    p.F("uBloomAmt", s.bloom);
    p.F("uFade", s.fade);
    p.F("uFlash", s.flash);
    p.V3v("uFlashCol", s.flashCol);
    p.F("uCrack", s.crack);
    p.V2("uCrackPos", 0.62f, 0.58f);
    p.F("uHelmet", s.helmet);
    p.F("uRedPulse", s.redPulse);
    p.F("uShock", s.shockBlur);
    p.F("uFlare", s.flare);
    // sun position on screen for the lens flare
    Vector3 sun = V3(0, 0, 0);
    float sunI = 0;
    if (s.set == SET_SPACE) {
        Vector3 d = Norm(s.sunDir);
        float z = Dot(d, camFwd);
        if (z > 0.05f) {
            float aspect = vp.width / vp.height;
            float x = Dot(d, camRight) / z / (tanHalfFov * aspect);
            float y = Dot(d, camUp) / z / tanHalfFov;
            sun = V3(0.5f + 0.5f * x, 0.5f + 0.5f * y, 0);
            float edge = std::max(fabsf(x), fabsf(y));
            sunI = Clamp01((1.15f - edge) / 0.3f);
        }
    }
    p.V3v("uSun", V3(sun.x, sun.y, sunI));
    DrawTexturePro(scene_.texture, Rectangle{0, 0, (float)scene_.texture.width, (float)scene_.texture.height}, vp,
                   Vector2{0, 0}, 0, WHITE);
    EndShaderMode();
}

void Renderer::Render(const SceneState& s, float time, Rectangle vp) {
    int w = std::max(16, (int)(vp.width * scale_));
    int h = std::max(16, (int)(vp.height * scale_));
    EnsureTargets(w, h);

    BeginTextureMode(scene_);
    rlDisableColorBlend();
    ClearBackground(BLACK);
    bool drawn = false;
    if (s.set == SET_SPACE && space_.Valid()) {
        DrawSpace(s, time, w, h);
        drawn = true;
    } else if (s.set == SET_INTERIOR && interior_.Valid()) {
        DrawInterior(s, time, w, h);
        drawn = true;
    } else if (s.set == SET_GROUND && ground_.Valid()) {
        DrawGround(s, time, w, h);
        drawn = true;
    }
    if (!drawn) {
        // black frame, depth far
        DrawRectangle(0, 0, w, h, Color{0, 0, 0, 255});
    }
    rlDrawRenderBatchActive();
    rlEnableColorBlend();
    EndTextureMode();

    rlDisableColorBlend();
    Bloom(w, h);
    rlEnableColorBlend();

    rlDisableColorBlend();
    Composite(s, time, vp);
    rlDrawRenderBatchActive();
    rlEnableColorBlend();
}
