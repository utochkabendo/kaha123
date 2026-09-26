#include "ui.h"

#include <algorithm>
#include <cmath>
#include <cstdio>

// ------------------------------------------------------------------ palette
static const Color kWhite = {235, 238, 242, 255};
static const Color kDim = {150, 158, 170, 255};
static const Color kAccent = {140, 215, 255, 255};
static const Color kDanger = {255, 80, 60, 255};
static const Color kOk = {150, 255, 190, 255};

static Color Alpha(Color c, float a) {
    c.a = (unsigned char)(std::clamp(a, 0.0f, 1.0f) * c.a);
    return c;
}

static Color SpeakerColor(const std::string& sp) {
    if (sp == "ГРОМОВ") return Color{205, 225, 255, 255};
    if (sp == "МАРИНА") return Color{255, 196, 138, 255};
    if (sp == "ЦУП") return Color{158, 230, 168, 255};
    if (sp == "ОРИОН") return Color{127, 227, 240, 255};
    return kWhite;
}

std::string KeyName(int key) {
    switch (key) {
        case KEY_SPACE: return "ПРОБЕЛ";
        case KEY_E: return "E";
        case KEY_A: return "A";
        case KEY_D: return "D";
        case KEY_W: return "W";
        case KEY_F: return "F";
        default: return "?";
    }
}

// ------------------------------------------------------------------ fonts
static std::vector<int> Codepoints() {
    std::vector<int> cp;
    for (int c = 32; c < 127; c++) cp.push_back(c);
    for (int c = 0x400; c < 0x460; c++) cp.push_back(c);
    int extra[] = {0xAB, 0xBB, 0x2014, 0x2013, 0x2026, 0x2116, 0xB7, 0x2082, 0xB0, 0x2190, 0x2192, 0x2191, 0x2193};
    for (int c : extra) cp.push_back(c);
    return cp;
}

static Font LoadF(const char* file, int size) {
    std::vector<int> cp = Codepoints();
    std::string path = AssetPath(std::string("assets/fonts/") + file);
    Font f = LoadFontEx(path.c_str(), size, cp.data(), (int)cp.size());
    if (f.texture.id == 0) return GetFontDefault();
    GenTextureMipmaps(&f.texture);
    SetTextureFilter(f.texture, TEXTURE_FILTER_TRILINEAR);
    return f;
}

void UI::Load() { loaded_ = true; }

void UI::EnsureFonts(int screenH) {
    int h = std::max(360, screenH);
    if (fontH_ && std::abs(h - fontH_) < 40) return;
    Unload();
    fontH_ = h;
    light_ = LoadF("Montserrat-ExtraLight.ttf", std::max(48, (int)(h * 0.16f)));
    medium_ = LoadF("Montserrat-Medium.ttf", std::max(24, (int)(h * 0.05f)));
    semi_ = LoadF("Montserrat-SemiBold.ttf", std::max(24, (int)(h * 0.05f)));
    mono_ = LoadF("IBMPlexMono-Regular.ttf", std::max(20, (int)(h * 0.036f)));
}

void UI::Unload() {
    Font* fs[] = {&light_, &medium_, &semi_, &mono_};
    for (Font* f : fs) {
        if (f->texture.id && f->texture.id != GetFontDefault().texture.id) UnloadFont(*f);
        *f = Font{};
    }
    fontH_ = 0;
}

Rectangle UI::Viewport(int w, int h) const {
    const float aspect = 2.39f;
    float vw = (float)w, vh = vw / aspect;
    if (vh > h) {
        vh = (float)h;
        vw = vh * aspect;
    }
    return Rectangle{floorf((w - vw) * 0.5f), floorf((h - vh) * 0.5f), floorf(vw), floorf(vh)};
}

void UI::Text(const Font& f, const std::string& s, float x, float y, float size, Color c, float spacing, int align) {
    if (s.empty()) return;
    Vector2 m = MeasureTextEx(f, s.c_str(), size, spacing);
    if (align == 1) x -= m.x * 0.5f;
    if (align == 2) x -= m.x;
    DrawTextEx(f, s.c_str(), Vector2{roundf(x), roundf(y)}, size, spacing, c);
}

float UI::Width(const Font& f, const std::string& s, float size, float spacing) const {
    return MeasureTextEx(f, s.c_str(), size, spacing).x;
}

// Word wrap for UTF-8 strings (splits on spaces)
static std::vector<std::string> Wrap(const Font& f, const std::string& s, float size, float maxW) {
    std::vector<std::string> out;
    std::string line, word;
    auto flush = [&](const std::string& w) {
        std::string cand = line.empty() ? w : line + " " + w;
        if (!line.empty() && MeasureTextEx(f, cand.c_str(), size, 0).x > maxW) {
            out.push_back(line);
            line = w;
        } else line = cand;
    };
    for (char ch : s) {
        if (ch == ' ') {
            if (!word.empty()) flush(word);
            word.clear();
        } else word += ch;
    }
    if (!word.empty()) flush(word);
    if (!line.empty()) out.push_back(line);
    return out;
}

// ------------------------------------------------------------------ story overlay
void UI::DrawSubtitle(const Director& d, Rectangle vp) {
    const Subtitle& st = d.subtitle;
    float now = d.storyClock;
    if (st.text.empty() || now > st.until + 0.4f) return;
    float a = std::min(Clamp01((now - st.start) / 0.15f), Clamp01((st.until + 0.4f - now) / 0.4f));
    float H = (float)GetScreenHeight(), W = (float)GetScreenWidth();
    float size = H * 0.034f;
    float bottomBar = H - (vp.y + vp.height);
    auto lines = Wrap(medium_, st.text, size, W * 0.7f);
    float lh = size * 1.28f;
    float blockH = lh * lines.size() + size * 0.9f;
    float y0 = bottomBar > blockH + 10 ? vp.y + vp.height + (bottomBar - blockH) * 0.5f : vp.y + vp.height - blockH - H * 0.04f;
    Color sc = SpeakerColor(st.speaker);
    Text(semi_, st.speaker, W * 0.5f, y0, size * 0.62f, Alpha(sc, a), size * 0.18f, 1);
    float y = y0 + size * 0.9f;
    for (const auto& l : lines) {
        Text(medium_, l, W * 0.5f + 1.5f, y + 1.5f, size, Alpha(BLACK, a * 0.8f), 0, 1);
        Text(medium_, l, W * 0.5f, y, size, Alpha(kWhite, a), 0, 1);
        y += lh;
    }
}

void UI::DrawKeyCap(const std::string& label, float cx, float cy, float size, Color c, float fill) {
    float w = std::max(size * 1.6f, Width(semi_, label, size * 0.5f, 1) + size * 0.8f);
    Rectangle r{cx - w * 0.5f, cy - size * 0.8f, w, size * 1.6f};
    if (fill > 0) DrawRectangleRounded(r, 0.3f, 8, Alpha(c, 0.25f * fill));
    DrawRectangleRoundedLinesEx(r, 0.3f, 8, std::max(1.5f, size * 0.06f), c);
    Text(semi_, label, cx, cy - size * 0.3f, size * 0.6f, c, 1, 1);
}

void UI::DrawChoice(const Director& d, Rectangle vp, float time) {
    const auto& ch = d.choice;
    choiceCount = 0;
    bool fading = !ch.on && ch.picked >= 0 && ch.fx < 1.2f;
    if (!ch.on && !fading) return;
    float H = (float)GetScreenHeight();
    int n = (int)ch.opts.size();
    float appear = ch.on ? Clamp01((ch.timer - ch.left) / 0.35f) : 1.0f;
    float out = fading ? 1.0f - Clamp01(ch.fx / 1.2f) : 1.0f;
    float cy = vp.y + vp.height * 0.72f;
    // dark band for legibility
    for (int i = 0; i < 24; i++) {
        float k = i / 23.0f;
        float a = (1.0f - fabsf(k - 0.5f) * 2.0f) * 0.55f * appear * out;
        DrawRectangle((int)vp.x, (int)(cy - H * 0.12f + k * H * 0.24f), (int)vp.width, (int)(H * 0.24f / 24 + 1), Alpha(BLACK, a));
    }
    // timer bar
    if (ch.on) {
        float frac = Clamp01(ch.left / ch.timer);
        float bw = vp.width * 0.36f * frac;
        Color tc = frac < 0.3f ? (fmodf(time * 6, 1.0f) < 0.5f ? kDanger : kWhite) : kWhite;
        DrawRectangleRec(Rectangle{vp.x + vp.width * 0.5f - bw * 0.5f, cy + H * 0.075f, bw, std::max(2.0f, H * 0.003f)}, Alpha(tc, 0.9f * appear));
    }
    float size = H * 0.03f;
    for (int i = 0; i < n; i++) {
        float x = n == 1 ? 0.5f : (n == 2 ? (i == 0 ? 0.3f : 0.7f) : 0.2f + 0.3f * i);
        float cx = vp.x + vp.width * x;
        bool picked = ch.picked == i;
        float a = appear * (fading ? (picked ? out : out * 0.3f) : 1.0f);
        float slide = (1.0f - EaseOut(appear)) * H * 0.03f * (i == 0 ? -1 : 1);
        float scale = picked && fading ? 1.0f + 0.12f * EaseOut(ch.fx / 0.4f) : 1.0f;
        std::string key = (n == 2) ? (i == 0 ? "A  ←" : "→  D") : std::to_string(i + 1);
        Color c = picked && fading ? kAccent : kWhite;
        DrawKeyCap(key, cx + slide, cy - size * 1.2f, size * 1.1f * scale, Alpha(c, a), picked && fading ? 1.0f : 0.0f);
        Text(semi_, ch.opts[i].label, cx + slide, cy + size * 0.3f, size * scale, Alpha(c, a), size * 0.12f, 1);
        float w = Width(semi_, ch.opts[i].label, size, size * 0.12f);
        choiceRects[i] = Rectangle{cx - w * 0.5f - size, cy - size * 2.2f, w + size * 2, size * 4};
    }
    choiceCount = ch.on ? n : 0;
}

void UI::DrawQte(const Director& d, Rectangle vp, float time) {
    const auto& q = d.qte;
    float H = (float)GetScreenHeight();
    float cx = vp.x + vp.width * 0.5f, cy = vp.y + vp.height * 0.56f;
    float R = H * 0.065f;
    if (q.on) {
        const QteDef& def = q.def;
        float appear = EaseOut(Clamp01((def.window - q.left) / 0.2f));
        float frac = Clamp01(q.left / def.window);
        float pulse = 1.0f + 0.05f * sinf(time * 18.0f);
        float r = R * (1.4f - 0.4f * appear) * pulse;
        DrawCircleV(Vector2{cx, cy}, r * 1.05f, Alpha(BLACK, 0.45f * appear));
        DrawRing(Vector2{cx, cy}, r * 0.92f, r, 0, 360, 64, Alpha(kWhite, 0.25f * appear));
        Color rc = frac < 0.3f ? kDanger : kWhite;
        DrawRing(Vector2{cx, cy}, r * 0.92f, r, -90, -90 + 360 * frac, 64, Alpha(rc, appear));
        float prog = 0;
        if (def.type == QteType::Mash) prog = (float)q.presses / def.count;
        if (def.type == QteType::Hold) prog = q.held / def.hold;
        if (prog > 0) DrawRing(Vector2{cx, cy}, r * 0.72f, r * 0.84f, -90, -90 + 360 * Clamp01(prog), 64, Alpha(kAccent, appear));
        std::string key = KeyName(def.key);
        if (def.type == QteType::Dir) {
            key = def.left ? "← A" : "D →";
            // arrow hint on the correct side
            float ax = cx + (def.left ? -1 : 1) * r * 2.2f;
            float s = r * 0.5f * (1.0f + 0.15f * sinf(time * 14.0f));
            Vector2 tip{ax + (def.left ? -s : s), cy};
            Vector2 b1{ax, cy - s * 0.8f}, b2{ax, cy + s * 0.8f};
            if (def.left) DrawTriangle(tip, b2, b1, Alpha(kWhite, appear));
            else DrawTriangle(tip, b1, b2, Alpha(kWhite, appear));
        }
        float ks = key.size() > 3 ? r * 0.36f : r * 0.62f;
        Text(semi_, key, cx, cy - ks * 0.55f, ks, Alpha(kWhite, appear), 1, 1);
        std::string hint = def.type == QteType::Mash ? "ЖМИТЕ БЫСТРО" : (def.type == QteType::Hold ? "УДЕРЖИВАЙТЕ" : "");
        Text(semi_, def.label, cx, cy + r * 1.3f, H * 0.026f, Alpha(kWhite, appear), H * 0.004f, 1);
        if (!hint.empty()) Text(medium_, hint, cx, cy + r * 1.3f + H * 0.035f, H * 0.018f, Alpha(kDim, appear), H * 0.003f, 1);
    } else if (q.lastResult != 0 && q.fx < 0.8f) {
        float k = q.fx / 0.8f;
        float a = 1.0f - k;
        if (q.lastResult > 0) {
            float r = R * (1.0f + k * 0.8f);
            DrawRing(Vector2{cx, cy}, r * 0.9f, r, 0, 360, 64, Alpha(kOk, a));
            DrawRing(Vector2{cx, cy}, r * 0.55f, r * 0.6f, 0, 360, 64, Alpha(kOk, a * 0.6f));
        } else {
            float sx = sinf(q.fx * 60.0f) * R * 0.12f * a;
            float s = R * 0.5f;
            DrawLineEx(Vector2{cx - s + sx, cy - s}, Vector2{cx + s + sx, cy + s}, R * 0.12f, Alpha(kDanger, a));
            DrawLineEx(Vector2{cx + s + sx, cy - s}, Vector2{cx - s + sx, cy + s}, R * 0.12f, Alpha(kDanger, a));
        }
    }
}

void UI::DrawHud(const SceneState& s, Rectangle vp, float time) {
    if (s.hud <= 0.01f) return;
    float H = (float)GetScreenHeight();
    float a = Clamp01(s.hud);
    float size = H * 0.019f;
    float m = vp.height * 0.09f;
    float x0 = vp.x + vp.width * 0.08f, y0 = vp.y + m;
    Color c = Alpha(Color{170, 230, 255, 255}, 0.75f * a);
    char buf[128];
    snprintf(buf, sizeof buf, "O₂  %4.1f %%", s.hudO2);
    Text(mono_, buf, x0, y0, size, c);
    snprintf(buf, sizeof buf, "ДАВЛ %4.1f кПа", s.hudPress);
    Text(mono_, buf, x0, y0 + size * 1.3f, size, s.hudPress < 20 ? Alpha(kDanger, a) : c);
    snprintf(buf, sizeof buf, "ЧСС  %3d", (int)(s.hudPulse + 3 * sinf(time * 2.0f)));
    Text(mono_, buf, x0, y0 + size * 2.6f, size, c);
    float xr = vp.x + vp.width * 0.92f;
    Text(mono_, "ОРЛАН-МКС  ·  ГРОМОВ А.", xr, y0, size, c, 0, 2);
    Text(mono_, "СВЯЗЬ: ЗАРЯ-9", xr, y0 + size * 1.3f, size, c, 0, 2);
    // heartbeat line
    float bx = x0, by = y0 + size * 4.4f, bw = vp.width * 0.1f;
    Vector2 prev{bx, by};
    for (int i = 1; i <= 40; i++) {
        float u = i / 40.0f;
        float ph = fmodf(u * 2.0f - time * s.hudPulse / 60.0f, 1.0f);
        if (ph < 0) ph += 1;
        float v = ph < 0.08f ? sinf(ph / 0.08f * PI_F * 2) * size * 0.7f : 0.0f;
        Vector2 p{bx + u * bw, by - v};
        DrawLineEx(prev, p, 1.5f, c);
        prev = p;
    }
    if (s.hudWarn > 0 && fmodf(time * 2.0f, 1.0f) < 0.6f) {
        const char* w = s.hudWarn == 1 ? "▲ РАЗГЕРМЕТИЗАЦИЯ СКАФАНДРА" : (s.hudWarn == 2 ? "▲ ПРОБОЙ КОРПУСА" : "▲ ПЕРЕГРЕВ ОБШИВКИ");
        std::string ws = w;
        ws = ws.substr(ws.find(' ') + 1);
        Text(semi_, ws, vp.x + vp.width * 0.5f, vp.y + vp.height * 0.12f, size * 1.2f, Alpha(kDanger, a), size * 0.2f, 1);
    }
}

void UI::DrawStory(const Director& d, const SceneState& s, Rectangle vp, float time) {
    float H = (float)GetScreenHeight();
    float W = (float)GetScreenWidth();
    DrawHud(s, vp, time);
    if (s.titleCard > 0.01f) {
        float a = s.titleCard;
        float ts = H * 0.14f;
        float spread = ts * (0.3f + 0.08f * a);
        Text(light_, "ЗАРЯ", W * 0.5f + spread * 0.5f, vp.y + vp.height * 0.5f - ts * 0.62f, ts, Alpha(kWhite, a), spread, 1);
        Text(semi_, "ИНТЕРАКТИВНОЕ КИНО", W * 0.5f, vp.y + vp.height * 0.5f + ts * 0.5f, H * 0.018f, Alpha(kWhite, a * 0.8f), H * 0.009f, 1);
    }
    if (s.endCard > 0.01f && !d.flags.ending.empty()) {
        float a = s.endCard;
        Text(semi_, "КОНЦОВКА", W * 0.5f, vp.y + vp.height * 0.4f, H * 0.018f, Alpha(kAccent, a), H * 0.008f, 1);
        Text(light_, d.flags.ending, W * 0.5f + H * 0.006f, vp.y + vp.height * 0.44f, H * 0.07f, Alpha(kWhite, a), H * 0.012f, 1);
    }
    // chapter card
    float ct = d.storyClock - d.chapterT;
    if (ct >= 0 && ct < 5.0f && !d.chapter.empty()) {
        float a = std::min(Clamp01(ct / 0.8f), Clamp01((5.0f - ct) / 1.0f));
        std::string c = d.chapter;
        auto sep = c.find('|');
        std::string part = sep == std::string::npos ? "" : c.substr(0, sep);
        std::string title = sep == std::string::npos ? c : c.substr(sep + 1);
        float x = vp.x + vp.width * 0.06f, y = vp.y + vp.height * 0.08f;
        Text(semi_, part, x, y, H * 0.018f, Alpha(kAccent, a), H * 0.006f);
        Text(light_, title, x - H * 0.003f, y + H * 0.024f, H * 0.055f, Alpha(kWhite, a), H * 0.01f);
        DrawRectangle((int)x, (int)(y + H * 0.09f), (int)(H * 0.08f * EaseOut(ct / 1.2f)), 2, Alpha(kWhite, a * 0.7f));
    }
    // caption (typewriter)
    float kt = d.storyClock - d.captionT;
    if (kt >= 0 && kt < d.captionDur && !d.caption.empty()) {
        float a = std::min(Clamp01(kt / 0.3f), Clamp01((d.captionDur - kt) / 0.6f));
        // count UTF-8 codepoints to reveal progressively
        int total = 0;
        for (unsigned char ch : d.caption)
            if ((ch & 0xC0) != 0x80) total++;
        int show = std::min(total, (int)(kt * 22.0f));
        std::string vis;
        int cnt = 0;
        for (size_t i = 0; i < d.caption.size(); i++) {
            unsigned char ch = d.caption[i];
            if ((ch & 0xC0) != 0x80) {
                if (cnt == show) break;
                cnt++;
            }
            vis += d.caption[i];
        }
        if (fmodf(kt * 2.5f, 1.0f) < 0.5f && show < total + 3) vis += "_";
        Text(mono_, vis, vp.x + vp.width * 0.5f, vp.y + vp.height * 0.5f - H * 0.015f, H * 0.03f, Alpha(kWhite, a), H * 0.004f, 1);
    }
    DrawChoice(d, vp, time);
    DrawQte(d, vp, time);
    DrawSubtitle(d, vp);
}

// ------------------------------------------------------------------ screens
void UI::DrawBoot(float t) {
    float W = (float)GetScreenWidth(), H = (float)GetScreenHeight();
    float a = std::min(Clamp01(t / 0.8f), Clamp01((3.4f - t) / 0.8f));
    Text(semi_, "ЗВУК ВАЖЕН", W * 0.5f, H * 0.44f, H * 0.02f, Alpha(kAccent, a), H * 0.008f, 1);
    Text(medium_, "Лучше всего играть в наушниках", W * 0.5f, H * 0.48f, H * 0.03f, Alpha(kWhite, a), 0, 1);
}

void UI::DrawTitle(const MenuState& m, const char* quality, Rectangle vp, float t, float fade) {
    float W = (float)GetScreenWidth(), H = (float)GetScreenHeight();
    float a = Clamp01(t / 2.0f) * fade;
    float ts = H * 0.15f;
    Text(light_, "ЗАРЯ", W * 0.5f + ts * 0.18f, vp.y + vp.height * 0.2f, ts, Alpha(kWhite, a), ts * 0.36f, 1);
    Text(semi_, "ИНТЕРАКТИВНОЕ КИНО  ·  ДЕМО", W * 0.5f, vp.y + vp.height * 0.2f + ts * 1.1f, H * 0.018f, Alpha(kAccent, a), H * 0.008f, 1);
    menuCount = (int)m.items.size();
    float y = vp.y + vp.height * 0.64f;
    for (int i = 0; i < (int)m.items.size(); i++) {
        std::string label = m.items[i];
        if (label == "КАЧЕСТВО") label = std::string("КАЧЕСТВО:  ") + quality;
        bool sel = i == m.sel;
        float size = H * 0.026f;
        Color c = sel ? kWhite : kDim;
        float w = Width(semi_, label, size, size * 0.2f);
        if (sel) {
            float pulse = 0.5f + 0.5f * sinf(m.t * 4.0f);
            DrawRectangle((int)(W * 0.5f - w * 0.5f - size * 1.2f), (int)(y + size * 0.4f), (int)(size * 0.5f), 2, Alpha(kAccent, a * (0.6f + 0.4f * pulse)));
            DrawRectangle((int)(W * 0.5f + w * 0.5f + size * 0.7f), (int)(y + size * 0.4f), (int)(size * 0.5f), 2, Alpha(kAccent, a * (0.6f + 0.4f * pulse)));
        }
        Text(semi_, label, W * 0.5f, y, size, Alpha(c, a), size * 0.2f, 1);
        menuRects[i] = Rectangle{W * 0.5f - w * 0.5f - size, y - size * 0.3f, w + size * 2, size * 1.6f};
        y += size * 2.0f;
    }
    float hs = H * 0.017f;
    float by = vp.y + vp.height + (H - vp.y - vp.height) * 0.5f - hs;
    if (H - vp.y - vp.height < hs * 3) by = H - hs * 3;
    Text(medium_, "Выбор: A / D  или  ← / →   ·   Действия — клавиши на экране   ·   ESC — пауза   ·   F11 — полный экран",
         W * 0.5f, by, hs, Alpha(kDim, a), 0, 1);
    Text(medium_, "~5 минут  ·  4 концовки  ·  ваши решения меняют историю", W * 0.5f, by + hs * 1.5f, hs, Alpha(kDim, a * 0.8f), 0, 1);
}

void UI::DrawPause(const MenuState& m, Rectangle vp) {
    float W = (float)GetScreenWidth(), H = (float)GetScreenHeight();
    DrawRectangle(0, 0, (int)W, (int)H, Alpha(BLACK, 0.6f));
    Text(light_, "ПАУЗА", W * 0.5f + H * 0.01f, vp.y + vp.height * 0.25f, H * 0.07f, kWhite, H * 0.02f, 1);
    menuCount = (int)m.items.size();
    float y = vp.y + vp.height * 0.52f;
    for (int i = 0; i < (int)m.items.size(); i++) {
        float size = H * 0.026f;
        bool sel = i == m.sel;
        float w = Width(semi_, m.items[i], size, size * 0.2f);
        Text(semi_, m.items[i], W * 0.5f, y, size, sel ? kWhite : kDim, size * 0.2f, 1);
        if (sel) DrawRectangle((int)(W * 0.5f - w * 0.5f), (int)(y + size * 1.3f), (int)w, 2, kAccent);
        menuRects[i] = Rectangle{W * 0.5f - w * 0.5f - size, y - size * 0.3f, w + size * 2, size * 1.6f};
        y += size * 2.0f;
    }
}

void UI::DrawEnding(const Director& d, const std::vector<bool>& found, Rectangle vp, float t) {
    float W = (float)GetScreenWidth(), H = (float)GetScreenHeight();
    ClearBackground(Color{4, 6, 9, 255});
    float a = Clamp01(t / 1.5f);
    static const char* names[4] = {"РАССВЕТ ВДВОЁМ", "ОДИНОКИЙ РАССВЕТ", "ПАДАЮЩАЯ ЗВЕЗДА", "ПОСЛЕДНИЙ СИГНАЛ"};
    Text(semi_, "КОНЦОВКА", W * 0.5f, vp.y + vp.height * 0.08f, H * 0.018f, Alpha(kAccent, a), H * 0.008f, 1);
    Text(light_, d.flags.ending, W * 0.5f + H * 0.006f, vp.y + vp.height * 0.12f, H * 0.065f, Alpha(kWhite, a), H * 0.012f, 1);

    // decisions
    float y = vp.y + vp.height * 0.36f;
    float b = Clamp01((t - 0.8f) / 1.0f);
    Text(semi_, "ВАШИ РЕШЕНИЯ", W * 0.5f, y, H * 0.017f, Alpha(kDim, b), H * 0.006f, 1);
    y += H * 0.04f;
    for (size_t i = 0; i < d.flags.decisions.size(); i++) {
        float bi = Clamp01((t - 1.0f - i * 0.25f) / 0.6f);
        Text(medium_, d.flags.decisions[i], W * 0.5f, y, H * 0.024f, Alpha(kWhite, bi), 0, 1);
        y += H * 0.036f;
    }
    char buf[96];
    snprintf(buf, sizeof buf, "Реакция: %d из %d", d.flags.qteOk, d.flags.qteTotal);
    Text(medium_, buf, W * 0.5f, y + H * 0.01f, H * 0.02f, Alpha(kDim, b), 0, 1);

    // ending slots
    float c = Clamp01((t - 2.0f) / 1.0f);
    int nf = 0;
    for (bool f : found) nf += f;
    snprintf(buf, sizeof buf, "ОТКРЫТО КОНЦОВОК: %d ИЗ 4", nf);
    float sy = vp.y + vp.height * 0.8f;
    Text(semi_, buf, W * 0.5f, sy, H * 0.017f, Alpha(kAccent, c), H * 0.006f, 1);
    float slotW = vp.width * 0.19f;
    for (int i = 0; i < 4; i++) {
        float x = W * 0.5f + (i - 1.5f) * (slotW + H * 0.02f);
        Rectangle r{x - slotW * 0.5f, sy + H * 0.035f, slotW, H * 0.05f};
        bool cur = i == d.flags.endingIndex;
        DrawRectangleRoundedLinesEx(r, 0.2f, 6, cur ? 2.0f : 1.0f, Alpha(cur ? kAccent : kDim, c * (found[i] ? 1.0f : 0.4f)));
        Text(semi_, found[i] ? names[i] : "? ? ?", x, r.y + r.height * 0.5f - H * 0.009f, H * 0.017f, Alpha(found[i] ? kWhite : kDim, c), H * 0.003f, 1);
    }
    float e = Clamp01((t - 3.0f) / 1.0f);
    Text(medium_, "ENTER — сыграть снова   ·   ESC — в главное меню", W * 0.5f, H - H * 0.06f, H * 0.02f, Alpha(kDim, e), 0, 1);
}

void UI::DrawDebug(const Director& d, float fps, float scale) {
    char buf[160];
    snprintf(buf, sizeof buf, "%s  t=%.2f  clock=%.1f  fps=%.0f  scale=%.2f", d.SeqId().c_str(), d.SeqTime(), d.storyClock, fps, scale);
    DrawTextEx(mono_, buf, Vector2{8, 8}, 16, 0, Color{255, 255, 0, 200});
}
