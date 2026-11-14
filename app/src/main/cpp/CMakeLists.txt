cmake_minimum_required(VERSION 3.22.1)

project("hearpod_audio" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(hearpod_audio SHARED
    OboeAudioEngineJni.cpp
    OboeAudioEngine.cpp
)

target_include_directories(hearpod_audio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(hearpod_audio PRIVATE
    oboe::oboe
    log
    android
)
