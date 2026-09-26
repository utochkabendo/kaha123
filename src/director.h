// ZARYA — the director: a graph of timed sequences. Each sequence owns a
// list of camera shots (pure functions of time -> SceneState) and timed events
// (dialogue, music, sound, choices, quick-time events, story logic).
#pragma once

#include <functional>
#include <map>
#include <string>
#include <vector>

#include "audio.h"
#include "common.h"

class Director;

using ShotFn = std::function<void(SceneState&, float t, const Flags&)>;
using Cond = std::function<bool(const Flags&)>;
using Action = std::function<void(Director&)>;

struct Shot {
    float start = 0, dur = 1;
    ShotFn fn;
};

struct ChoiceOpt {
    std::string label;   // shown on screen
    std::string record;  // text for the decisions summary
    std::string next;    // sequence to jump to ("" = continue)
    std::function<void(Flags&)> apply;
};

enum class QteType { Press, Mash, Hold, Dir };

struct QteDef {
    QteType type = QteType::Press;
    int key = KEY_SPACE;
    int count = 8;          // Mash: presses needed
    float window = 1.5f;    // seconds of real time
    float hold = 1.2f;      // Hold: seconds to hold
    bool left = false;      // Dir: correct side
    float slowmo = 0.25f;   // story time scale while active
    std::string label;
    std::string okNext, failNext;
    std::function<void(Flags&, bool)> apply;
};

enum class EvKind { Line, Sfx, Music, Amb, Call, Choice, Qte, Chapter, Caption };

struct Event {
    float t = 0;
    EvKind kind = EvKind::Call;
    std::string id;
    float a = 1, b = 1, c = 0.5f;
    Cond cond;
    Action act;
    std::vector<ChoiceOpt> opts;
    int timeoutOpt = 0;
    QteDef qte;
};

struct Sequence {
    std::string id;
    std::vector<Shot> shots;
    std::vector<Event> events;
    float length = 0;
    std::function<std::string(const Flags&)> next;
};

struct Subtitle {
    std::string speaker, text;
    float start = 0, until = 0;
};

struct Input {
    bool left = false, right = false, confirm = false;
    bool keyPressed[512] = {};
    bool keyDown[512] = {};
    int mouseChoice = -1;
};

class Director {
public:
    // ---- authoring
    struct Builder {
        Director* d;
        Sequence* s;
        float shotEnd = 0;
        float cursor = 0;
        Builder& Shot(float dur, ShotFn fn);
        Builder& At(float t) { cursor = t; return *this; }
        Builder& Wait(float dt) { cursor += dt; return *this; }
        Builder& Say(const std::string& id, float gap = 0.35f, Cond c = nullptr);
        Builder& Line(float t, const std::string& id, Cond c = nullptr);
        Builder& Sfx(float t, const std::string& name, float vol = 1, float pitch = 1, float pan = 0.5f);
        Builder& Music(float t, const std::string& name, float fade = 2, float vol = 1);
        Builder& Amb(float t, const std::string& name, float vol, float fade = 1);
        Builder& Call(float t, Action a, Cond c = nullptr);
        Builder& Choice(float t, float timer, std::vector<ChoiceOpt> opts, int timeoutOpt);
        Builder& Qte(float t, QteDef q, Cond c = nullptr);
        Builder& Chapter(float t, const std::string& text);
        Builder& Caption(float t, const std::string& text, float dur);
        Builder& Next(std::function<std::string(const Flags&)> fn);
        Builder& Next(const std::string& id);
        Builder& Length(float len);
        float Dur(const std::string& voiceId) const;
    };
    Builder Add(const std::string& id);

    // ---- runtime
    void Init(AudioSys* audio);
    void LoadDialogue();
    int Validate() const;
    void Start(const std::string& id);
    void Seek(float t);  // jump inside the current sequence without firing events (tools)
    void Reset();
    void Update(float dt, const Input& in);
    void Build(SceneState& s);
    bool Finished() const { return finished_; }
    float TimeScale() const { return timeScale_; }
    float SeqTime() const { return t_; }
    const std::string& SeqId() const { return cur_ ? cur_->id : empty_; }

    // effects callable from events
    void Shake(float amp, float decay = 3.0f);
    void Flash(float amount, Vector3 col = V3(1, 1, 1));

    // UI queries
    Flags flags;
    AudioSys* audio = nullptr;
    std::map<std::string, std::pair<std::string, std::string>> lines;  // id -> speaker, text
    Subtitle subtitle;
    float storyClock = 0;  // total real time spent in the story
    std::string chapter;
    float chapterT = -10;
    std::string caption;
    float captionT = -10, captionDur = 0;

    struct ChoiceState {
        bool on = false;
        std::vector<ChoiceOpt> opts;
        float timer = 0, left = 0;
        int timeoutOpt = 0;
        int picked = -1;
        float fx = 0;  // feedback animation
    } choice;
    struct QteState {
        bool on = false;
        QteDef def;
        float left = 0;
        int presses = 0;
        float held = 0;
        int result = 0;   // 0 pending, 1 ok, -1 fail
        float fx = 0;     // feedback timer after resolution
        int lastResult = 0;
        QteType lastType = QteType::Press;
        int lastKey = 0;
        std::string lastLabel;
    } qte;

    // autoplay (for testing): picks random options / QTE results
    bool autoplay = false;
    unsigned autoSeed = 1;
    std::vector<std::string> path;

private:
    void Jump(const std::string& id);
    void Fire(Event& e);
    void ResolveChoice(int idx);
    void ResolveQte(bool ok);
    float AutoRand();

    std::map<std::string, Sequence> seqs_;
    Sequence* cur_ = nullptr;
    size_t nextEv_ = 0;
    float t_ = 0;
    float timeScale_ = 1;
    float shake_ = 0, shakeDecay_ = 3;
    float flash_ = 0;
    Vector3 flashCol_{1, 1, 1};
    bool finished_ = false;
    std::string pendingJump_;
    std::string empty_;
};

void BuildStory(Director& d);
