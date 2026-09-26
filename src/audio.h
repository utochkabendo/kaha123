// ZARYA — audio: voice-over, streamed music with crossfades, ambience beds,
// one-shot effects with voice ducking and slow-motion pitch bending.
#pragma once

#include <map>
#include <string>
#include <vector>

#include "raylib.h"

class AudioSys {
public:
    bool Init(bool mute);
    void Unload();
    void Update(float dt);

    float VoiceDuration(const std::string& id) const;
    void PlayVoice(const std::string& id);
    void StopVoice();
    bool VoicePlaying() const;

    void Sfx(const std::string& name, float vol = 1.0f, float pitch = 1.0f, float pan = 0.5f);
    void Music(const std::string& name, float fade = 2.0f, float vol = 1.0f);
    void Amb(const std::string& name, float vol, float fade = 1.0f);
    void StopAll(float fade);
    void SetPaused(bool p);
    void SetSlowmo(float timeScale) { slowmo_ = timeScale; }
    void SetMasterVolume(float v);
    bool Enabled() const { return enabled_; }

private:
    struct Stream {
        ::Music mus{};
        bool loaded = false;
        float vol = 0, target = 0, rate = 1, gain = 1;
        bool playing = false;
    };
    struct SfxEntry {
        std::vector<Sound> voices;
        int next = 0;
    };
    Stream* GetStream(const std::string& path, bool loop);

    bool enabled_ = false;
    bool paused_ = false;
    float slowmo_ = 1.0f;
    float duck_ = 1.0f;
    std::map<std::string, Sound> voice_;
    std::map<std::string, float> voiceDur_;
    std::string curVoice_;
    std::map<std::string, SfxEntry> sfx_;
    std::map<std::string, Stream> streams_;
    std::string curMusic_;
};
