// ---------------------------------------------------------------------------
// ZARYA — interactive film demo (C++17 / raylib / raymarched HDR renderer)
// ---------------------------------------------------------------------------
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>

#include "audio.h"
#include "common.h"
#include "director.h"
#include "renderer.h"
#include "rlgl.h"
#include "ui.h"

namespace {

struct Options {
    int width = 1600, height = 900;
    bool fullscreen = false;
    bool mute = false;
    bool autoplay = false;
    unsigned seed = 1;
    float fixedDt = 0;
    std::string start;
    std::string captureDir;
    float every = 0;
    float until = 0;
    bool noRender = false;
    float scale = 0;
    bool check = false;
    bool debug = false;
    std::string shots;   // "seq:t,seq:t" — render stills and exit
    std::string flags;   // "marina,antenna,crack,hurt"
};

Options Parse(int argc, char** argv) {
    Options o;
    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        auto next = [&]() -> const char* { return i + 1 < argc ? argv[++i] : ""; };
        if (a == "--size") sscanf(next(), "%dx%d", &o.width, &o.height);
        else if (a == "--fullscreen") o.fullscreen = true;
        else if (a == "--mute") o.mute = true;
        else if (a == "--autoplay") { o.autoplay = true; o.seed = (unsigned)atoi(next()); }
        else if (a == "--fixed-dt") o.fixedDt = (float)atof(next());
        else if (a == "--start") o.start = next();
        else if (a == "--capture") o.captureDir = next();
        else if (a == "--every") o.every = (float)atof(next());
        else if (a == "--until") o.until = (float)atof(next());
        else if (a == "--no-render") o.noRender = true;
        else if (a == "--scale") o.scale = (float)atof(next());
        else if (a == "--check") o.check = true;
        else if (a == "--debug") o.debug = true;
        else if (a == "--shot") o.shots = next();
        else if (a == "--flags") o.flags = next();
    }
    return o;
}

std::string ProgressPath() { return "zarya_progress.txt"; }

std::vector<bool> LoadProgress() {
    std::vector<bool> f(4, false);
    std::ifstream in(ProgressPath());
    std::string s;
    if (in >> s)
        for (int i = 0; i < 4 && i < (int)s.size(); i++) f[i] = s[i] == '1';
    return f;
}

void SaveProgress(const std::vector<bool>& f) {
    std::ofstream out(ProgressPath());
    for (bool b : f) out << (b ? '1' : '0');
}

// background of the title screen: the station waits for the sunrise
void TitleScene(SceneState& s, float t) {
    s = SceneState{};
    s.set = SET_SPACE;
    float el = Deg(-20.6f + 0.25f * sinf(t * 0.05f)), az = Deg(3);
    s.sunDir = V3(sinf(az) * cosf(el), sinf(el), cosf(az) * cosf(el));
    s.sunCol = V3(1.0f, 0.96f, 0.9f);
    s.earthRot = 0.4f + t * 0.002f;
    s.stars = 0.9f;
    s.panelAngle = 0.1f;
    s.capsuleMode = 1;
    s.camPos = V3(-26 + sinf(t * 0.03f) * 4, 9, -62);
    s.camTarget = V3(-12, 8, 60);
    s.fov = 38;
    s.exposure = 1.15f;
    s.bloom = 0.6f;
    s.grain = 0.035f;
    s.vignette = 0.5f;
}

Input GatherInput(const UI& ui) {
    Input in;
    for (int k = 32; k < 350; k++) {
        in.keyPressed[k] = IsKeyPressed(k);
        in.keyDown[k] = IsKeyDown(k);
    }
    in.left = IsKeyPressed(KEY_A) || IsKeyPressed(KEY_LEFT) || IsKeyPressed(KEY_ONE);
    in.right = IsKeyPressed(KEY_D) || IsKeyPressed(KEY_RIGHT) || IsKeyPressed(KEY_TWO);
    // gamepad: A = space, X = E, Y = W, d-pad = directions
    if (IsGamepadAvailable(0)) {
        auto gp = [](int b) { return IsGamepadButtonPressed(0, b); };
        auto gd = [](int b) { return IsGamepadButtonDown(0, b); };
        if (gp(GAMEPAD_BUTTON_RIGHT_FACE_DOWN)) in.keyPressed[KEY_SPACE] = true;
        if (gd(GAMEPAD_BUTTON_RIGHT_FACE_DOWN)) in.keyDown[KEY_SPACE] = true;
        if (gp(GAMEPAD_BUTTON_RIGHT_FACE_LEFT)) in.keyPressed[KEY_E] = true;
        if (gd(GAMEPAD_BUTTON_RIGHT_FACE_LEFT)) in.keyDown[KEY_E] = true;
        if (gp(GAMEPAD_BUTTON_RIGHT_FACE_UP)) in.keyPressed[KEY_W] = true;
        if (gp(GAMEPAD_BUTTON_LEFT_FACE_LEFT) || gp(GAMEPAD_BUTTON_LEFT_TRIGGER_1)) in.left = true;
        if (gp(GAMEPAD_BUTTON_LEFT_FACE_RIGHT) || gp(GAMEPAD_BUTTON_RIGHT_TRIGGER_1)) in.right = true;
    }
    if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
        Vector2 m = GetMousePosition();
        for (int i = 0; i < ui.choiceCount; i++)
            if (CheckCollisionPointRec(m, ui.choiceRects[i])) in.mouseChoice = i;
    }
    return in;
}

enum class State { Boot, Title, Play, Pause, Ending };

}  // namespace

int main(int argc, char** argv) {
    Options opt = Parse(argc, argv);
    ChangeDirectory(GetApplicationDirectory());
    bool headless = !opt.captureDir.empty() || opt.noRender || opt.check || !opt.shots.empty();

    unsigned flags = FLAG_WINDOW_RESIZABLE | FLAG_VSYNC_HINT | FLAG_MSAA_4X_HINT;
    if (headless) flags = FLAG_WINDOW_HIDDEN;
    SetConfigFlags(flags);
    SetTraceLogLevel(opt.debug || headless ? LOG_INFO : LOG_WARNING);
    InitWindow(opt.width, opt.height, "ЗАРЯ — интерактивное кино");
    SetExitKey(0);
    SetWindowMinSize(640, 360);
    if (!headless && !opt.fullscreen && opt.width == 1600 && opt.height == 900) {
        // default window: 80% of the current monitor, centred
        int mon = GetCurrentMonitor();
        int mw = GetMonitorWidth(mon), mh = GetMonitorHeight(mon);
        if (mw > 0 && mh > 0) {
            int w = (int)(mw * 0.8f), h = (int)(mh * 0.8f);
            SetWindowSize(w, h);
            SetWindowPosition((mw - w) / 2, (mh - h) / 2);
        }
    }
    if (!headless) SetTargetFPS(144);
    if (opt.fullscreen) ToggleBorderlessWindowed();

    AudioSys audio;
    audio.Init(opt.mute || headless);

    Renderer renderer;
    renderer.Init();
    if (opt.scale > 0) renderer.SetFixedScale(opt.scale);

    UI ui;
    ui.Load();
    ui.EnsureFonts(GetScreenHeight());

    Director director;
    director.Init(&audio);
    director.autoplay = opt.autoplay;
    director.autoSeed = opt.seed * 2654435761u + 7;
    if (opt.check) {
        int n = director.Validate();
        printf("timeline check: %d problem(s)\n", n);
        CloseWindow();
        return n ? 1 : 0;
    }

    if (!opt.shots.empty()) {
        // still frames for look development: --shot seq:t[,seq:t...] --capture dir
        std::string all = opt.shots + ",";
        size_t p = 0;
        int idx = 0;
        while (true) {
            size_t q = all.find(',', p);
            if (q == std::string::npos) break;
            std::string item = all.substr(p, q - p);
            p = q + 1;
            size_t c = item.find(':');
            if (c == std::string::npos) continue;
            std::string seq = item.substr(0, c);
            float t = (float)atof(item.substr(c + 1).c_str());
            if (seq == "title") {
                for (int pass = 0; pass < 2; pass++) {
                    BeginDrawing();
                    ClearBackground(BLACK);
                    Rectangle vp = ui.Viewport(GetScreenWidth(), GetScreenHeight());
                    SceneState scene;
                    TitleScene(scene, t);
                    renderer.Render(scene, t, vp);
                    MenuState m;
                    m.items = {"НАЧАТЬ", "КАЧЕСТВО", "ВЫХОД"};
                    ui.DrawTitle(m, renderer.QualityName(), vp, 5.0f, 1.0f);
                    if (pass == 1) {
                        rlDrawRenderBatchActive();
                        Image img = LoadImageFromScreen();
                        char path[512];
                        snprintf(path, sizeof path, "%s/shot_%02d_title.png", opt.captureDir.c_str(), idx++);
                        ExportImage(img, path);
                        UnloadImage(img);
                    }
                    EndDrawing();
                }
                continue;
            }
            director.Start(seq);
            Flags& f = director.flags;
            f.marinaSaved = opt.flags.find("marina") != std::string::npos;
            f.antennaFixed = opt.flags.find("antenna") != std::string::npos;
            f.visorCracked = opt.flags.find("crack") != std::string::npos;
            f.gromovHurt = opt.flags.find("hurt") != std::string::npos;
            f.ending = "ПАДАЮЩАЯ ЗВЕЗДА";
            director.Seek(t);
            for (int pass = 0; pass < 2; pass++) {
                BeginDrawing();
                ClearBackground(BLACK);
                Rectangle vp = ui.Viewport(GetScreenWidth(), GetScreenHeight());
                SceneState scene;
                director.Build(scene);
                renderer.Render(scene, 10.0f + t, vp);
                ui.DrawStory(director, scene, vp, 10.0f + t);
                if (pass == 1) {
                    rlDrawRenderBatchActive();
                    Image img = LoadImageFromScreen();
                    char path[512];
                    snprintf(path, sizeof path, "%s/shot_%02d_%s_%05.1f.png", opt.captureDir.c_str(), idx++, seq.c_str(), t);
                    ExportImage(img, path);
                    UnloadImage(img);
                }
                EndDrawing();
            }
        }
        CloseWindow();
        return 0;
    }

    std::vector<bool> found = LoadProgress();
    State state = State::Boot;
    if (!opt.start.empty() || opt.autoplay) {
        director.Start(opt.start.empty() ? "cold" : opt.start);
        state = State::Play;
    }
    MenuState title;
    title.items = {"НАЧАТЬ", "КАЧЕСТВО", "ВЫХОД"};
    MenuState pause;
    pause.items = {"ПРОДОЛЖИТЬ", "НАЧАТЬ ЗАНОВО", "ГЛАВНОЕ МЕНЮ", "ВЫХОД"};
    float stateT = 0;
    float time = 0;
    float titleFade = 1;
    bool quit = false;
    bool showDebug = opt.debug;
    int lastTick = -1;
    float heartT = 0;
    float nextCapture = 0;
    int captureIdx = 0;
    bool startedTitleMusic = false;

    auto startStory = [&]() {
        audio.StopAll(1.0f);
        director.Start("cold");
        state = State::Play;
        stateT = 0;
    };

    while (!quit && !WindowShouldClose()) {
        float raw = GetFrameTime();
        float dt = opt.fixedDt > 0 ? opt.fixedDt : std::min(raw, 0.1f);
        time += dt;
        stateT += dt;

        if (IsKeyPressed(KEY_F11) || (IsKeyDown(KEY_LEFT_ALT) && IsKeyPressed(KEY_ENTER))) ToggleBorderlessWindowed();
        if (IsKeyPressed(KEY_F2)) renderer.SetQuality((renderer.Quality() + 1) % 4);
        if (IsKeyPressed(KEY_F3)) showDebug = !showDebug;
        if (IsKeyPressed(KEY_F5)) renderer.ReloadShaders();
        ui.EnsureFonts(GetScreenHeight());

        Input in = GatherInput(ui);
        audio.Update(dt);

        // ------------------------------------------------------------ logic
        switch (state) {
            case State::Boot:
                if (stateT > 3.6f || IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE) || IsKeyPressed(KEY_ESCAPE)) {
                    state = State::Title;
                    stateT = 0;
                }
                break;
            case State::Title: {
                if (!startedTitleMusic) {
                    audio.Music("mus_title", 3.0f, 0.8f);
                    audio.Amb("amb_suit", 0.12f, 3.0f);
                    startedTitleMusic = true;
                }
                title.t += dt;
                int n = (int)title.items.size();
                if (IsKeyPressed(KEY_DOWN) || IsKeyPressed(KEY_S)) { title.sel = (title.sel + 1) % n; audio.Sfx("ui", 0.6f); }
                if (IsKeyPressed(KEY_UP) || IsKeyPressed(KEY_W)) { title.sel = (title.sel + n - 1) % n; audio.Sfx("ui", 0.6f); }
                Vector2 m = GetMousePosition();
                bool click = false;
                for (int i = 0; i < ui.menuCount && i < n; i++)
                    if (CheckCollisionPointRec(m, ui.menuRects[i])) {
                        if (GetMouseDelta().x != 0 || GetMouseDelta().y != 0) title.sel = i;
                        if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) { title.sel = i; click = true; }
                    }
                bool enter = IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE) || click ||
                             (IsGamepadAvailable(0) && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
                bool lr = IsKeyPressed(KEY_LEFT) || IsKeyPressed(KEY_RIGHT) || IsKeyPressed(KEY_A) || IsKeyPressed(KEY_D);
                if (title.items[title.sel] == "КАЧЕСТВО" && (enter || lr)) {
                    renderer.SetQuality((renderer.Quality() + (IsKeyPressed(KEY_LEFT) || IsKeyPressed(KEY_A) ? 3 : 1)) % 4);
                    audio.Sfx("ui", 0.8f);
                } else if (enter) {
                    audio.Sfx("select", 0.9f);
                    if (title.items[title.sel] == "НАЧАТЬ") {
                        startStory();
                        startedTitleMusic = false;
                    } else if (title.items[title.sel] == "ВЫХОД") quit = true;
                }
                if (IsKeyPressed(KEY_ESCAPE)) quit = true;
                break;
            }
            case State::Play: {
                if (IsKeyPressed(KEY_ESCAPE) || IsKeyPressed(KEY_P) ||
                    (IsGamepadAvailable(0) && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_MIDDLE_RIGHT))) {
                    state = State::Pause;
                    pause.sel = 0;
                    audio.SetPaused(true);
                    break;
                }
                director.Update(dt, in);
                // ticking clock + heartbeat while a decision is pending
                if (director.choice.on) {
                    int tick = (int)ceilf(director.choice.left);
                    if (tick != lastTick) {
                        audio.Sfx("tick", 0.7f, tick <= 2 ? 1.25f : 1.0f);
                        lastTick = tick;
                    }
                    heartT -= dt;
                    if (heartT <= 0) {
                        audio.Sfx("heartbeat", 0.8f);
                        heartT = director.choice.left < 3 ? 0.55f : 0.85f;
                    }
                } else {
                    lastTick = -1;
                    heartT = 0;
                }
                if (director.Finished()) {
                    if (director.flags.endingIndex >= 0) {
                        found[director.flags.endingIndex] = true;
                        if (!headless) SaveProgress(found);
                    }
                    if (headless) {
                        printf("PATH:");
                        for (auto& p : director.path) printf(" %s", p.c_str());
                        printf("\nENDING: %s  story time %.1f s  qte %d/%d\n", director.flags.ending.c_str(), director.storyClock,
                               director.flags.qteOk, director.flags.qteTotal);
                        for (auto& d : director.flags.decisions) printf("  - %s\n", d.c_str());
                        quit = true;
                    }
                    state = State::Ending;
                    stateT = 0;
                }
                if (opt.until > 0 && director.storyClock >= opt.until) quit = true;
                break;
            }
            case State::Pause: {
                int n = (int)pause.items.size();
                if (IsKeyPressed(KEY_DOWN) || IsKeyPressed(KEY_S)) { pause.sel = (pause.sel + 1) % n; audio.Sfx("ui", 0.6f); }
                if (IsKeyPressed(KEY_UP) || IsKeyPressed(KEY_W)) { pause.sel = (pause.sel + n - 1) % n; audio.Sfx("ui", 0.6f); }
                Vector2 m = GetMousePosition();
                bool click = false;
                for (int i = 0; i < ui.menuCount && i < n; i++)
                    if (CheckCollisionPointRec(m, ui.menuRects[i])) {
                        if (GetMouseDelta().x != 0 || GetMouseDelta().y != 0) pause.sel = i;
                        if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) { pause.sel = i; click = true; }
                    }
                bool enter = IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE) || click;
                if (IsKeyPressed(KEY_ESCAPE) || (enter && pause.sel == 0)) {
                    state = State::Play;
                    audio.SetPaused(false);
                } else if (enter && pause.sel == 1) {
                    audio.SetPaused(false);
                    startStory();
                } else if (enter && pause.sel == 2) {
                    audio.SetPaused(false);
                    audio.StopAll(0.5f);
                    state = State::Title;
                    stateT = 0;
                } else if (enter && pause.sel == 3) quit = true;
                break;
            }
            case State::Ending:
                if (stateT > 2.0f && (IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE))) startStory();
                else if (stateT > 1.0f && IsKeyPressed(KEY_ESCAPE)) {
                    audio.StopAll(1.0f);
                    state = State::Title;
                    stateT = 0;
                }
                break;
        }

        // ------------------------------------------------------------ render
        bool capture = false;
        if (!opt.captureDir.empty() && state == State::Play && opt.every > 0 && director.storyClock >= nextCapture) {
            capture = true;
            nextCapture += opt.every;
        }
        if (opt.noRender && !capture) {
            continue;
        }
        if (headless && !capture && !opt.captureDir.empty()) continue;

        BeginDrawing();
        ClearBackground(BLACK);
        int W = GetScreenWidth(), H = GetScreenHeight();
        Rectangle vp = ui.Viewport(W, H);
        SceneState scene;
        if (state == State::Play || state == State::Pause) {
            director.Build(scene);
            renderer.Render(scene, time, vp);
            ui.DrawStory(director, scene, vp, time);
            if (state == State::Pause) ui.DrawPause(pause, vp);
        } else if (state == State::Title || state == State::Boot) {
            if (state == State::Title) {
                TitleScene(scene, time);
                scene.fade = 1.0f - Clamp01(stateT / 2.5f);
                renderer.Render(scene, time, vp);
                ui.DrawTitle(title, renderer.QualityName(), vp, stateT, titleFade);
            } else {
                ui.DrawBoot(stateT);
            }
        } else if (state == State::Ending) {
            ui.DrawEnding(director, found, vp, stateT);
        }
        if (showDebug) ui.DrawDebug(director, GetFPS(), renderer.Scale());
        if (capture) {
            rlDrawRenderBatchActive();
            Image img = LoadImageFromScreen();
            char path[512];
            snprintf(path, sizeof path, "%s/%04d_%s_%05.1f.png", opt.captureDir.c_str(), captureIdx++, director.SeqId().c_str(), director.SeqTime());
            ExportImage(img, path);
            UnloadImage(img);
        }
        EndDrawing();
        if (opt.fixedDt <= 0) renderer.UpdateDynamicResolution(raw);
    }

    renderer.Unload();
    ui.Unload();
    audio.Unload();
    CloseWindow();
    return 0;
}
