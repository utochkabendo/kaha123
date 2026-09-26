#include <string>

#include "common.h"

// The game switches its working directory to the executable's folder at
// startup (see main.cpp) and only ever opens relative paths afterwards. That
// keeps file access working when the game lives in a folder whose name is not
// plain ASCII (e.g. a Downloads folder under a Cyrillic user name on Windows).
static std::string FindRoot() {
    const char* cands[] = {"", "../", "../../", "../../../"};
    for (const char* c : cands) {
        std::string b = c;
        if (DirectoryExists((b + "shaders").c_str()) && DirectoryExists((b + "assets").c_str())) return b;
    }
    return "";
}

std::string AssetPath(const std::string& rel) {
    static std::string root = FindRoot();
    return root + rel;
}
