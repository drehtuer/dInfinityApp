# The native half of the physics bridge: Jolt, plus the thin executor that
# Kotlin drives it through (`docs/architecture.md`, decisions 37 and 40).
#
# Everything that decides anything about a roll is Kotlin. This library owns a
# world, steps it, and answers questions about it — which is the whole reason
# the untestable surface stays this small.

cmake_minimum_required(VERSION 3.22.1)

# Jolt is built optimised in every variant, the debug build included, and this
# has to be set before the toolchain reads it. An unoptimised solver is not a
# slower version of the same roll: it is too slow to step 120 Hz on a phone, so
# the emulator and device tiers would be judging something the release build
# never does. Nobody steps into Jolt with a debugger; the bridge's own bugs are
# in the Kotlin above it (`docs/architecture.md`, decision 41).
set(CMAKE_BUILD_TYPE Distribution CACHE STRING "Jolt's optimised configuration" FORCE)

project(dinfinity_jolt CXX)

include(FetchContent)

# "Distribution" is Jolt's own configuration name, so CMake knows no flags for
# it until Jolt's build script fills them in — and it fills them in for its own
# directory only. Without these two lines this library's own translation units
# would compile with no optimisation and no NDEBUG while Jolt compiled with
# both.
set(CMAKE_CXX_FLAGS_DISTRIBUTION "${CMAKE_CXX_FLAGS_RELEASE}")
set(CMAKE_SHARED_LINKER_FLAGS_DISTRIBUTION "${CMAKE_SHARED_LINKER_FLAGS_RELEASE}")
set(CMAKE_EXE_LINKER_FLAGS_DISTRIBUTION "${CMAKE_EXE_LINKER_FLAGS_RELEASE}")

# Jolt at a pinned tag, verified by checksum rather than by name. Every other
# dependency in this project is pinned by SHA-256 (`docs/architecture.md`,
# decision 28) and the engine that decides every roll is a poor place to make
# an exception: a tag is a label somebody can move, a digest is not.
#
# Each ABI fetches into its own build tree, which is FetchContent's default and
# is not worth being clever about: one source tree shared between two Android
# toolchains means one precompiled header shared between them too, and the
# build fails with "AST file was compiled for the target x86_64 but the current
# translation unit is being compiled for aarch64". A second download is cheaper
# than that sentence.
set(JOLT_VERSION "5.6.0")
set(JOLT_SHA256 "6e069ee0172478cc78182047aac87e5310ba14a67a53348ae14cc37801fd3f8e")

# CROSS_PLATFORM_DETERMINISTIC is the reason this engine was chosen at all
# (`docs/architecture.md`, decision 37). Without it the golden determinism
# suite asserts nothing and power-saving mode stops being provably the same
# roll.
set(CROSS_PLATFORM_DETERMINISTIC ON CACHE BOOL "" FORCE)
set(TARGET_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(TARGET_HELLO_WORLD OFF CACHE BOOL "" FORCE)
set(TARGET_PERFORMANCE_TEST OFF CACHE BOOL "" FORCE)
set(TARGET_SAMPLES OFF CACHE BOOL "" FORCE)
set(TARGET_VIEWER OFF CACHE BOOL "" FORCE)

# Jolt 5.6 can run compute shaders on the GPU. Nothing here does: every roll
# is decided on the CPU, because GPU floating point is not the same on every
# driver and decision 37's determinism would not survive it
# (`docs/architecture.md`, decision 112). Off, so the build pulls in no
# Vulkan headers or shader compiler, and the library carries none of it.
set(JPH_USE_DX12 OFF CACHE BOOL "" FORCE)
set(JPH_USE_VK OFF CACHE BOOL "" FORCE)
set(JPH_USE_MTL OFF CACHE BOOL "" FORCE)
set(JPH_USE_CPU_COMPUTE OFF CACHE BOOL "" FORCE)

# A workaround for a build that otherwise does not happen. Jolt's deterministic
# build passes both `-ffp-model=precise` and `-ffp-contract=off`, Clang 21 (NDK
# 30) warns that the second overrides the first, and Jolt builds with -Werror.
# Both flags do the same job — keeping the compiler from reassociating
# floating-point arithmetic — so silencing the overlap costs nothing they exist
# for (`docs/build-setup.md`, "The native build").
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-overriding-option")

FetchContent_Declare(
  JoltPhysics
  URL "https://github.com/jrouwe/JoltPhysics/archive/refs/tags/v${JOLT_VERSION}.tar.gz"
  URL_HASH "SHA256=${JOLT_SHA256}"
  SOURCE_SUBDIR Build
  # Stamped when extracted, not with the archive's own dates. With the
  # archive's, a new pin's headers are older than the bridge's object files
  # and nothing is recompiled: the 5.6 upgrade linked a bridge built against
  # 5.3's headers, and Jolt aborted on its version check the first time a
  # world was made. It is only extracted when the URL or digest changes, so
  # this costs a rebuild exactly when one is owed.
  DOWNLOAD_EXTRACT_TIMESTAMP FALSE
)
FetchContent_MakeAvailable(JoltPhysics)

add_library(dinfinity_jolt SHARED
  dinfinity_jolt.cpp
  world.cpp
)

target_include_directories(dinfinity_jolt PRIVATE "${joltphysics_SOURCE_DIR}")

target_compile_features(dinfinity_jolt PRIVATE cxx_std_17)

target_compile_options(dinfinity_jolt PRIVATE -Wall -Wextra -Werror)

target_link_libraries(dinfinity_jolt PRIVATE Jolt log)
