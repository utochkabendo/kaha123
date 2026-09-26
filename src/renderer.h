// ZARYA — HDR renderer: raymarched scene sets -> bloom -> cinematic composite.
#pragma once

#include <string>
#include <unordered_map>

#include "common.h"

struct ShaderProg {
    Shader sh{};
    std::string name;
    std::unordered_map<std::string, int> locs;
    int Loc(const char* uniform);
    void F(const char* u, float v);
    void V2(const char* u, float x, float y);
    void V3v(const char* u, Vector3 v);
    void V4(const char* u, Vector4 v);
    void I(const char* u, int v);
    void V3Arr(const char* u, const Vector3* v, int n);
    void V4Arr(const char* u, const Vector4* v, int n);
    void FArr(const char* u, const float* v, int n);
    void Tex(const char* u, Texture2D t);
    bool Valid() const { return sh.id > 0; }
};

class Renderer {
public:
    bool Init();
    void Unload();
    void ReloadShaders();

    // Renders the scene into the given window rectangle (letterboxed viewport).
    void Render(const SceneState& s, float time, Rectangle viewport);

    // Quality presets cap the internal render scale: 0 low .. 3 ultra
    void SetQuality(int q);
    int Quality() const { return quality_; }
    const char* QualityName() const;
    float Scale() const { return scale_; }
    void SetFixedScale(float s) { fixedScale_ = s; scale_ = s; }
    void UpdateDynamicResolution(float frameTime);

    // camera vectors of the last rendered frame (for UI projections)
    Vector3 camPos, camFwd, camRight, camUp;
    float tanHalfFov = 0.4f;

private:
    bool LoadAll();
    void EnsureTargets(int w, int h);
    void FreeTargets();
    void SetCommon(ShaderProg& p, const SceneState& s, float time, int w, int h);
    void SetAstro(ShaderProg& p, const SceneState& s);
    void DrawSpace(const SceneState& s, float time, int w, int h);
    void DrawInterior(const SceneState& s, float time, int w, int h);
    void DrawGround(const SceneState& s, float time, int w, int h);
    void Bloom(int w, int h);
    void Composite(const SceneState& s, float time, Rectangle vp);
    void FullQuad(int w, int h);

    ShaderProg space_, interior_, ground_, down_, up_, composite_;
    RenderTexture2D scene_{};
    static constexpr int kMips = 6;
    RenderTexture2D down_rt_[kMips]{};
    RenderTexture2D up_rt_[kMips]{};
    int mips_ = 0;
    int rw_ = 0, rh_ = 0;

    int quality_ = 2;
    float scale_ = 0.6f;
    float fixedScale_ = 0;
    float ftAccum_ = 0;
    int ftCount_ = 0;
    float cooldown_ = 0;
    float stableTime_ = 0;
};

// Converts an astronaut pose into 12 joint positions (suit local frame).
void PoseJoints(const Pose& p, Vector3 out[12]);
