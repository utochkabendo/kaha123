#include "director.h"

#include <algorithm>
#include <fstream>

// ------------------------------------------------------------------ builder
Director::Builder Director::Add(const std::string& id) {
    Sequence& s = seqs_[id];
    s = Sequence{};
    s.id = id;
    return Builder{this, &s};
}

Director::Builder& Director::Builder::Shot(float dur, ShotFn fn) {
    s->shots.push_back(::Shot{shotEnd, dur, std::move(fn)});
    shotEnd += dur;
    s->length = std::max(s->length, shotEnd);
    return *this;
}

float Director::Builder::Dur(const std::string& id) const { return d->audio ? d->audio->VoiceDuration(id) : 2.0f; }

Director::Builder& Director::Builder::Say(const std::string& id, float gap, Cond c) {
    Line(cursor, id, std::move(c));
    cursor += Dur(id) + gap;
    return *this;
}

Director::Builder& Director::Builder::Line(float t, const std::string& id, Cond c) {
    Event e;
    e.t = t;
    e.kind = EvKind::Line;
    e.id = id;
    e.cond = std::move(c);
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Sfx(float t, const std::string& name, float vol, float pitch, float pan) {
    Event e;
    e.t = t;
    e.kind = EvKind::Sfx;
    e.id = name;
    e.a = vol;
    e.b = pitch;
    e.c = pan;
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Music(float t, const std::string& name, float fade, float vol) {
    Event e;
    e.t = t;
    e.kind = EvKind::Music;
    e.id = name;
    e.a = fade;
    e.b = vol;
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Amb(float t, const std::string& name, float vol, float fade) {
    Event e;
    e.t = t;
    e.kind = EvKind::Amb;
    e.id = name;
    e.a = vol;
    e.b = fade;
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Call(float t, Action a, Cond c) {
    Event e;
    e.t = t;
    e.kind = EvKind::Call;
    e.act = std::move(a);
    e.cond = std::move(c);
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Choice(float t, float timer, std::vector<ChoiceOpt> opts, int timeoutOpt) {
    Event e;
    e.t = t;
    e.kind = EvKind::Choice;
    e.a = timer;
    e.opts = std::move(opts);
    e.timeoutOpt = timeoutOpt;
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Qte(float t, QteDef q, Cond c) {
    Event e;
    e.t = t;
    e.kind = EvKind::Qte;
    e.qte = std::move(q);
    e.cond = std::move(c);
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Chapter(float t, const std::string& text) {
    Event e;
    e.t = t;
    e.kind = EvKind::Chapter;
    e.id = text;
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Caption(float t, const std::string& text, float dur) {
    Event e;
    e.t = t;
    e.kind = EvKind::Caption;
    e.id = text;
    e.a = dur;
    s->events.push_back(std::move(e));
    return *this;
}

Director::Builder& Director::Builder::Next(std::function<std::string(const Flags&)> fn) {
    s->next = std::move(fn);
    return *this;
}

Director::Builder& Director::Builder::Next(const std::string& id) {
    std::string n = id;
    s->next = [n](const Flags&) { return n; };
    return *this;
}

Director::Builder& Director::Builder::Length(float len) {
    s->length = len;
    return *this;
}

// ------------------------------------------------------------------ runtime
static std::string StripStress(const std::string& in) {
    std::string out;
    for (size_t i = 0; i < in.size(); i++) {
        // U+0301 COMBINING ACUTE ACCENT = CC 81
        if ((unsigned char)in[i] == 0xCC && i + 1 < in.size() && (unsigned char)in[i + 1] == 0x81) {
            i++;
            continue;
        }
        out += in[i];
    }
    return out;
}

void Director::LoadDialogue() {
    std::ifstream f(AssetPath("assets/dialogue.tsv"));
    std::string line;
    static const std::map<std::string, std::string> names = {
        {"GROMOV", "ГРОМОВ"}, {"MARINA", "МАРИНА"}, {"CUP", "ЦУП"}, {"ORION", "ОРИОН"}};
    while (std::getline(f, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::vector<std::string> cols;
        size_t p = 0;
        while (true) {
            size_t q = line.find('\t', p);
            cols.push_back(line.substr(p, q == std::string::npos ? std::string::npos : q - p));
            if (q == std::string::npos) break;
            p = q + 1;
        }
        if (cols.size() < 5) continue;
        if (!cols[4].empty() && cols[4].back() == '\r') cols[4].pop_back();
        auto it = names.find(cols[1]);
        lines[cols[0]] = {it != names.end() ? it->second : cols[1], StripStress(cols[4])};
    }
}

void Director::Init(AudioSys* a) {
    audio = a;
    LoadDialogue();
    BuildStory(*this);
    for (auto& [id, s] : seqs_)
        std::stable_sort(s.events.begin(), s.events.end(), [](const Event& x, const Event& y) { return x.t < y.t; });
    Validate();
}

// Authoring checks: dialogue that runs past the end of its sequence or
// overlaps another unconditional line gets cut off by PlayVoice.
int Director::Validate() const {
    int problems = 0;
    for (const auto& [id, s] : seqs_) {
        std::vector<const Event*> ls;
        for (const auto& e : s.events)
            if (e.kind == EvKind::Line) ls.push_back(&e);
        for (size_t i = 0; i < ls.size(); i++) {
            float dur = audio ? audio->VoiceDuration(ls[i]->id) : 2.0f;
            float end = ls[i]->t + dur;
            if (end > s.length + 0.35f) {
                TraceLog(LOG_WARNING, "SCRIPT: %s: line %s ends at %.2f after sequence end %.2f", id.c_str(), ls[i]->id.c_str(), end, s.length);
                problems++;
            }
            for (size_t j = i + 1; j < ls.size(); j++) {
                if (ls[j]->t >= end - 0.05f) continue;
                if (ls[i]->cond && ls[j]->cond) continue;  // mutually exclusive branches are fine
                TraceLog(LOG_WARNING, "SCRIPT: %s: line %s (%.2f-%.2f) overlaps %s at %.2f", id.c_str(), ls[i]->id.c_str(), ls[i]->t, end, ls[j]->id.c_str(), ls[j]->t);
                problems++;
            }
        }
    }
    return problems;
}

void Director::Reset() {
    flags = Flags{};
    subtitle = Subtitle{};
    storyClock = 0;
    chapter.clear();
    chapterT = -10;
    caption.clear();
    captionT = -10;
    choice = ChoiceState{};
    qte = QteState{};
    finished_ = false;
    pendingJump_.clear();
    shake_ = 0;
    flash_ = 0;
    timeScale_ = 1;
    path.clear();
    cur_ = nullptr;
}

void Director::Start(const std::string& id) {
    Reset();
    Jump(id);
}

void Director::Jump(const std::string& id) {
    auto it = seqs_.find(id);
    if (it == seqs_.end()) {
        TraceLog(LOG_WARNING, "DIRECTOR: unknown sequence '%s'", id.c_str());
        finished_ = true;
        return;
    }
    cur_ = &it->second;
    t_ = 0;
    nextEv_ = 0;
    path.push_back(id);
}

void Director::Seek(float t) {
    if (!cur_) return;
    t_ = t;
    nextEv_ = 0;
    while (nextEv_ < cur_->events.size() && cur_->events[nextEv_].t <= t) nextEv_++;
}

void Director::Shake(float amp, float decay) {
    shake_ = std::max(shake_, amp);
    shakeDecay_ = decay;
}

void Director::Flash(float amount, Vector3 col) {
    flash_ = std::max(flash_, amount);
    flashCol_ = col;
}

float Director::AutoRand() {
    autoSeed = autoSeed * 1664525u + 1013904223u;
    return (float)((autoSeed >> 8) & 0xFFFF) / 65535.0f;
}

void Director::Fire(Event& e) {
    if (e.cond && !e.cond(flags)) return;
    switch (e.kind) {
        case EvKind::Line: {
            if (audio) audio->PlayVoice(e.id);
            auto it = lines.find(e.id);
            if (it != lines.end()) {
                float dur = audio ? audio->VoiceDuration(e.id) : 2.0f;
                subtitle = Subtitle{it->second.first, it->second.second, storyClock, storyClock + dur + 0.3f};
            }
            break;
        }
        case EvKind::Sfx:
            if (audio) audio->Sfx(e.id, e.a, e.b, e.c);
            break;
        case EvKind::Music:
            if (audio) audio->Music(e.id, e.a, e.b);
            break;
        case EvKind::Amb:
            if (audio) audio->Amb(e.id, e.a, e.b);
            break;
        case EvKind::Call:
            if (e.act) e.act(*this);
            break;
        case EvKind::Choice:
            choice = ChoiceState{};
            choice.on = true;
            choice.opts = e.opts;
            choice.timer = choice.left = e.a;
            choice.timeoutOpt = e.timeoutOpt;
            if (audio) audio->Sfx("riser", 0.5f);
            break;
        case EvKind::Qte:
            qte.on = true;
            qte.def = e.qte;
            qte.left = e.qte.window;
            qte.presses = 0;
            qte.held = 0;
            qte.result = 0;
            if (audio) audio->Sfx("whoosh_short", 0.6f);
            break;
        case EvKind::Chapter:
            chapter = e.id;
            chapterT = storyClock;
            break;
        case EvKind::Caption:
            caption = e.id;
            captionT = storyClock;
            captionDur = e.a;
            break;
    }
}

void Director::ResolveChoice(int idx) {
    if (idx < 0 || idx >= (int)choice.opts.size()) idx = choice.timeoutOpt;
    ChoiceOpt& o = choice.opts[idx];
    choice.on = false;
    choice.picked = idx;
    choice.fx = 0;
    if (o.apply) o.apply(flags);
    flags.decisions.push_back(o.record);
    if (audio) audio->Sfx("select", 0.9f);
    if (!o.next.empty()) pendingJump_ = o.next;
}

void Director::ResolveQte(bool ok) {
    qte.on = false;
    qte.result = ok ? 1 : -1;
    qte.fx = 0;
    qte.lastResult = qte.result;
    qte.lastType = qte.def.type;
    qte.lastKey = qte.def.key;
    qte.lastLabel = qte.def.label;
    flags.qteTotal++;
    if (ok) flags.qteOk++;
    if (qte.def.apply) qte.def.apply(flags, ok);
    if (audio) audio->Sfx(ok ? "qte_ok" : "qte_fail", 0.8f);
    const std::string& n = ok ? qte.def.okNext : qte.def.failNext;
    if (!n.empty()) pendingJump_ = n;
}

void Director::Update(float dt, const Input& in) {
    if (finished_ || !cur_) return;
    storyClock += dt;

    float target = 1.0f;
    if (choice.on) target = 0.3f;
    if (qte.on) target = qte.def.slowmo;
    timeScale_ += (target - timeScale_) * std::min(1.0f, dt * 7.0f);
    if (audio) audio->SetSlowmo(timeScale_);

    // ---- choices
    if (choice.on) {
        choice.left -= dt;
        int pick = -1;
        int n = (int)choice.opts.size();
        if (in.left) pick = 0;
        if (in.right && n > 1) pick = n - 1;
        if (in.mouseChoice >= 0) pick = in.mouseChoice;
        if (autoplay && choice.left < choice.timer * (0.3f + 0.4f * AutoRand())) pick = (int)(AutoRand() * n) % n;
        if (pick >= 0) {
            flags.timedOut = false;
            ResolveChoice(pick);
        } else if (choice.left <= 0) {
            flags.timedOut = true;
            ResolveChoice(choice.timeoutOpt);
            flags.decisions.back() += " (время вышло)";
        }
    } else {
        choice.fx += dt;
    }

    // ---- quick time events
    if (qte.on) {
        qte.left -= dt;
        const QteDef& q = qte.def;
        bool ok = false, fail = false;
        switch (q.type) {
            case QteType::Press:
                if (in.keyPressed[q.key]) ok = true;
                break;
            case QteType::Mash:
                if (in.keyPressed[q.key]) {
                    qte.presses++;
                    if (audio) audio->Sfx("tick", 0.5f, 0.9f + 0.02f * qte.presses);
                }
                if (qte.presses >= q.count) ok = true;
                break;
            case QteType::Hold:
                if (in.keyDown[q.key]) qte.held += dt;
                else qte.held = std::max(0.0f, qte.held - dt * 0.5f);
                if (qte.held >= q.hold) ok = true;
                break;
            case QteType::Dir:
                if (in.left) (q.left ? ok : fail) = true;
                if (in.right) (!q.left ? ok : fail) = true;
                break;
        }
        if (autoplay && qte.left < q.window * 0.5f) {
            if (AutoRand() < 0.65f) ok = true;
            else fail = true;
        }
        if (ok) ResolveQte(true);
        else if (fail || qte.left <= 0) ResolveQte(false);
    } else {
        qte.fx += dt;
    }

    // ---- advance story time and fire events
    t_ += dt * timeScale_;
    while (cur_ && nextEv_ < cur_->events.size() && cur_->events[nextEv_].t <= t_ && pendingJump_.empty()) {
        Event& e = cur_->events[nextEv_++];
        Fire(e);
        if (choice.on || qte.on) break;  // interactive beat: hold further events
    }
    if (!pendingJump_.empty()) {
        std::string j = pendingJump_;
        pendingJump_.clear();
        Jump(j);
    }
    if (cur_ && !choice.on && !qte.on && t_ >= cur_->length && nextEv_ >= cur_->events.size()) {
        std::string n = cur_->next ? cur_->next(flags) : "";
        if (n.empty() || n == "END") finished_ = true;
        else Jump(n);
    }

    shake_ = std::max(0.0f, shake_ - shake_ * shakeDecay_ * dt - dt * 0.02f);
    flash_ = std::max(0.0f, flash_ - dt * 2.5f);
}

void Director::Build(SceneState& s) {
    s = SceneState{};
    if (!cur_ || cur_->shots.empty()) return;
    const ::Shot* sh = &cur_->shots[0];
    for (const auto& x : cur_->shots)
        if (x.start <= t_) sh = &x;
    sh->fn(s, t_ - sh->start, flags);

    if (shake_ > 0.001f) {
        float c = storyClock;
        float dist = Len(s.camTarget - s.camPos);
        Vector3 off = V3(Noise1(c * 27.0f), Noise1(c * 23.0f + 5.0f), Noise1(c * 29.0f + 9.0f)) * (shake_ * 0.035f * dist);
        s.camTarget = s.camTarget + off;
        s.roll += Noise1(c * 19.0f + 3.0f) * shake_ * 3.0f;
    }
    if (flash_ > 0) {
        s.flash = std::max(s.flash, std::min(flash_, 1.0f));
        s.flashCol = flashCol_;
    }
}
