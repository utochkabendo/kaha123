// ZARYA — 2D overlay: subtitles, choices, quick-time events, helmet HUD,
// chapter cards, title / pause / ending screens.
#pragma once

#include <string>
#include <vector>

#include "common.h"
#include "director.h"

struct MenuState {
    int sel = 0;
    std::vector<std::string> items;
    float t = 0;
};

class UI {
public:
    void Load();
    void Unload();
    void EnsureFonts(int screenH);

    // cinematic viewport (2.39:1) inside the window
    Rectangle Viewport(int w, int h) const;

    void DrawStory(const Director& d, const SceneState& s, Rectangle vp, float time);
    void DrawBoot(float t);
    void DrawTitle(const MenuState& m, const char* quality, Rectangle vp, float t, float fade);
    void DrawPause(const MenuState& m, Rectangle vp);
    void DrawEnding(const Director& d, const std::vector<bool>& found, Rectangle vp, float t);
    void DrawDebug(const Director& d, float fps, float scale);

    // mouse hit-testing of the choice buttons (screen space)
    Rectangle choiceRects[3]{};
    int choiceCount = 0;
    Rectangle menuRects[6]{};
    int menuCount = 0;

private:
    void Text(const Font& f, const std::string& s, float x, float y, float size, Color c, float spacing = 0, int align = 0);
    float Width(const Font& f, const std::string& s, float size, float spacing = 0) const;
    void DrawSubtitle(const Director& d, Rectangle vp);
    void DrawChoice(const Director& d, Rectangle vp, float time);
    void DrawQte(const Director& d, Rectangle vp, float time);
    void DrawHud(const SceneState& s, Rectangle vp, float time);
    void DrawKeyCap(const std::string& label, float cx, float cy, float size, Color c, float fill);

    Font light_{}, medium_{}, semi_{}, mono_{};
    int fontH_ = 0;
    bool loaded_ = false;
};

std::string KeyName(int key);
