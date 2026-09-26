#include "audio.h"

#include <algorithm>
#include <cmath>
#include <fstream>
#include <sstream>

#include "common.h"

static std::vector<std::string> ListDialogueIds() {
    std::vector<std::string> ids;
    std::ifstream f(AssetPath("assets/dialogue.tsv"));
    std::string line;
    while (std::getline(f, line)) {
        if (line.empty() || line[0] == '#') continue;
        auto tab = line.find('\t');
        if (tab != std::string::npos) ids.push_back(line.substr(0, tab));
    }
    return ids;
}

bool AudioSys::Init(bool mute) {
    if (!mute) {
        InitAudioDevice();
        enabled_ = IsAudioDeviceReady();
    }
    // voice lines: durations are needed even without an audio device (timeline layout)
    for (const auto& id : ListDialogueIds()) {
        std::string path = AssetPath("assets/voice/" + id + ".ogg");
        Wave w = LoadWave(path.c_str());
        if (w.frameCount == 0) {
            voiceDur_[id] = 2.0f;
            continue;
        }
        voiceDur_[id] = (float)w.frameCount / (float)w.sampleRate;
        if (enabled_) voice_[id] = LoadSoundFromWave(w);
        UnloadWave(w);
    }
    if (!enabled_) return false;

    static const char* kSfx[] = {"heartbeat", "impact1", "impact2", "impact3", "impact_big", "whoosh", "whoosh_short",
                                 "crack", "sparks", "hatch", "metal_groan", "undock", "parachute", "riser", "hit",
                                 "braam", "tick", "select", "qte_ok", "qte_fail", "ui", "suit_warn", "valve",
                                 "explosion", "glitch", "title", "tether", "grab", "splash", "pulse"};
    for (const char* n : kSfx) {
        std::string path = AssetPath(std::string("assets/sfx/") + n + ".ogg");
        Sound base = LoadSound(path.c_str());
        if (base.frameCount == 0) continue;
        SfxEntry e;
        e.voices.push_back(base);
        for (int i = 0; i < 3; i++) e.voices.push_back(LoadSoundAlias(base));
        sfx_[n] = e;
    }
    return true;
}

void AudioSys::Unload() {
    if (!enabled_) return;
    for (auto& [k, s] : voice_) UnloadSound(s);
    for (auto& [k, e] : sfx_) {
        for (size_t i = 1; i < e.voices.size(); i++) UnloadSoundAlias(e.voices[i]);
        UnloadSound(e.voices[0]);
    }
    for (auto& [k, s] : streams_)
        if (s.loaded) UnloadMusicStream(s.mus);
    voice_.clear();
    sfx_.clear();
    streams_.clear();
    CloseAudioDevice();
    enabled_ = false;
}

float AudioSys::VoiceDuration(const std::string& id) const {
    auto it = voiceDur_.find(id);
    return it == voiceDur_.end() ? 2.0f : it->second;
}

void AudioSys::PlayVoice(const std::string& id) {
    if (!enabled_) {
        curVoice_ = id;
        return;
    }
    StopVoice();
    auto it = voice_.find(id);
    if (it == voice_.end()) return;
    SetSoundVolume(it->second, 1.0f);
    PlaySound(it->second);
    curVoice_ = id;
}

void AudioSys::StopVoice() {
    if (!enabled_ || curVoice_.empty()) return;
    auto it = voice_.find(curVoice_);
    if (it != voice_.end()) StopSound(it->second);
    curVoice_.clear();
}

bool AudioSys::VoicePlaying() const {
    if (!enabled_ || curVoice_.empty()) return false;
    auto it = voice_.find(curVoice_);
    return it != voice_.end() && IsSoundPlaying(it->second);
}

void AudioSys::Sfx(const std::string& name, float vol, float pitch, float pan) {
    if (!enabled_ || paused_) return;
    auto it = sfx_.find(name);
    if (it == sfx_.end()) return;
    SfxEntry& e = it->second;
    Sound& s = e.voices[e.next];
    e.next = (e.next + 1) % (int)e.voices.size();
    SetSoundVolume(s, vol);
    SetSoundPitch(s, pitch * (slowmo_ < 0.9f ? 0.75f : 1.0f));
    SetSoundPan(s, pan);
    PlaySound(s);
}

AudioSys::Stream* AudioSys::GetStream(const std::string& name, bool loop) {
    auto it = streams_.find(name);
    if (it != streams_.end()) return &it->second;
    std::string dir = name.rfind("mus_", 0) == 0 ? "assets/music/" : "assets/sfx/";
    Stream st;
    std::string path = AssetPath(dir + name + ".ogg");
    st.mus = LoadMusicStream(path.c_str());
    st.loaded = st.mus.frameCount > 0;
    st.mus.looping = loop;
    streams_[name] = st;
    return &streams_[name];
}

void AudioSys::Music(const std::string& name, float fade, float vol) {
    if (!enabled_) return;
    float rate = fade > 0.01f ? 1.0f / fade : 1000.0f;
    // fade out the current cue
    if (!curMusic_.empty() && curMusic_ != name) {
        auto it = streams_.find(curMusic_);
        if (it != streams_.end()) {
            it->second.target = 0;
            it->second.rate = rate;
        }
    }
    curMusic_ = name;
    if (name.empty()) return;
    bool loop = name != "mus_sad" && name != "mus_hope" && name != "mus_tragic";
    Stream* s = GetStream(name, loop);
    if (!s->loaded) return;
    if (!s->playing || s->vol <= 0.001f) {
        SeekMusicStream(s->mus, 0);
        PlayMusicStream(s->mus);
        s->playing = true;
        s->vol = 0;
    }
    s->gain = 1.0f;
    s->target = vol;
    s->rate = rate;
}

void AudioSys::Amb(const std::string& name, float vol, float fade) {
    if (!enabled_) return;
    Stream* s = GetStream(name, true);
    if (!s->loaded) return;
    if (!s->playing && vol > 0) {
        PlayMusicStream(s->mus);
        s->playing = true;
        s->vol = 0;
    }
    s->target = vol;
    s->rate = fade > 0.01f ? 1.0f / fade : 1000.0f;
}

void AudioSys::StopAll(float fade) {
    if (!enabled_) return;
    for (auto& [k, s] : streams_) {
        s.target = 0;
        s.rate = fade > 0.01f ? 1.0f / fade : 1000.0f;
    }
    curMusic_.clear();
    StopVoice();
}

void AudioSys::SetPaused(bool p) {
    if (!enabled_ || p == paused_) return;
    paused_ = p;
    for (auto& [k, s] : streams_) {
        if (!s.playing) continue;
        if (p) PauseMusicStream(s.mus);
        else ResumeMusicStream(s.mus);
    }
    for (auto& [k, v] : voice_) {
        if (p) PauseSound(v);
        else ResumeSound(v);
    }
}

void AudioSys::SetMasterVolume(float v) {
    if (enabled_) ::SetMasterVolume(v);
}

void AudioSys::Update(float dt) {
    if (!enabled_ || paused_) return;
    // duck music/ambience under dialogue
    float duckTarget = VoicePlaying() ? 0.5f : 1.0f;
    duck_ += (duckTarget - duck_) * std::min(1.0f, dt * 6.0f);
    for (auto& [name, s] : streams_) {
        if (!s.playing) continue;
        float d = s.target - s.vol;
        float step = s.rate * dt;
        s.vol = fabsf(d) <= step ? s.target : s.vol + (d > 0 ? step : -step);
        bool isMusic = name.rfind("mus_", 0) == 0;
        float v = s.vol * (isMusic ? duck_ : Mix(1.0f, duck_, 0.4f));
        SetMusicVolume(s.mus, v);
        if (!isMusic) SetMusicPitch(s.mus, slowmo_ < 0.9f ? 0.8f : 1.0f);
        if (s.vol <= 0.0001f && s.target <= 0.0f) {
            StopMusicStream(s.mus);
            s.playing = false;
            continue;
        }
        UpdateMusicStream(s.mus);
    }
}
