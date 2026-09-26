cmake_minimum_required(VERSION 3.16)
project(zarya VERSION 1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

# ---------------------------------------------------------------- raylib 5.5
# Offline builds: pass -DFETCHCONTENT_SOURCE_DIR_RAYLIB=/path/to/raylib
include(FetchContent)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_GAMES OFF CACHE BOOL "" FORCE)
set(CUSTOMIZE_BUILD OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
  raylib
  GIT_REPOSITORY https://github.com/raysan5/raylib.git
  GIT_TAG 5.5
  GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(raylib)

# ---------------------------------------------------------------- game
file(GLOB ZARYA_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp)
add_executable(zarya ${ZARYA_SOURCES})
target_link_libraries(zarya PRIVATE raylib)
target_include_directories(zarya PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)

if(MSVC)
  target_compile_options(zarya PRIVATE /W3 /utf-8)
  # no console window in release builds
  set_target_properties(zarya PROPERTIES LINK_FLAGS_RELEASE "/SUBSYSTEM:WINDOWS /ENTRY:mainCRTStartup")
else()
  target_compile_options(zarya PRIVATE -Wall -Wno-missing-braces)
endif()

if(WIN32 AND NOT MSVC)
  target_link_options(zarya PRIVATE -static -static-libgcc -static-libstdc++ $<$<CONFIG:Release>:-mwindows>)
endif()

if(APPLE)
  target_link_libraries(zarya PRIVATE "-framework IOKit" "-framework Cocoa" "-framework OpenGL")
endif()

# assets + shaders next to the executable (re-synced on every build)
add_custom_target(zarya_data ALL
  COMMAND ${CMAKE_COMMAND} -E copy_directory ${CMAKE_CURRENT_SOURCE_DIR}/assets $<TARGET_FILE_DIR:zarya>/assets
  COMMAND ${CMAKE_COMMAND} -E copy_directory ${CMAKE_CURRENT_SOURCE_DIR}/shaders $<TARGET_FILE_DIR:zarya>/shaders
  COMMENT "Copying game data"
)
add_dependencies(zarya_data zarya)
