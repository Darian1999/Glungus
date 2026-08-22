cmake_minimum_required(VERSION 3.15)
project(glungus_raycast LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# -----------------------------------------------------------------------------
# JNI
# -----------------------------------------------------------------------------
find_package(JNI REQUIRED)
message(STATUS "JNI_INCLUDE_DIRS: ${JNI_INCLUDE_DIRS}")
message(STATUS "JNI_LIBRARIES: ${JNI_LIBRARIES}")

# -----------------------------------------------------------------------------
# Library: glungus_raycast
# Produces:
#   Linux:   libglungus_raycast.so
#   macOS:   libglungus_raycast.dylib
#   Windows: glungus_raycast.dll
# -----------------------------------------------------------------------------
add_library(glungus_raycast SHARED
    RaycastUtil.cpp
)

target_include_directories(glungus_raycast
    PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(glungus_raycast
    PRIVATE
        ${JNI_LIBRARIES}
)

# Warnings + optimization — intentionally aggressive for experimental perf test
if(MSVC)
    target_compile_options(glungus_raycast PRIVATE /W4 /O2 /arch:AVX2)
else()
    target_compile_options(glungus_raycast PRIVATE -Wall -Wextra -Wpedantic -O3 -ffast-math -march=native)
endif()

# Hide non-JNI symbols (optional, keeps .so clean)
set_target_properties(glungus_raycast PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# Output to build/native for easy pickup by Gradle task
set_target_properties(glungus_raycast PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
    ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
)

# -----------------------------------------------------------------------------
# Optional standalone test executable (pure C++ math, no JVM)
# Build with -DGLUNGUS_BUILD_TESTS=ON
# -----------------------------------------------------------------------------
option(GLUNGUS_BUILD_TESTS "Build standalone C++ tests for raycast math" OFF)

if(GLUNGUS_BUILD_TESTS)
    add_executable(raycast_test
        RaycastUtil.cpp
        test_main.cpp
    )
    target_include_directories(raycast_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    # Exclude JNI symbols for standalone — define stub so RaycastUtil.cpp compiles without jni.h if needed
    # (currently RaycastUtil.cpp always includes jni.h, so JNI must be present even for test)
    target_include_directories(raycast_test PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(raycast_test PRIVATE ${JNI_LIBRARIES})
endif()

# -----------------------------------------------------------------------------
# Install — copy to src/main/resources/native/<os>-<arch>/ for jar inclusion
# Manually: cmake --install build/native --prefix src/main/resources/native
# -----------------------------------------------------------------------------
install(TARGETS glungus_raycast
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION lib
)

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "To build: cmake -S src/main/cpp -B build/native -DCMAKE_BUILD_TYPE=Release && cmake --build build/native -j")
