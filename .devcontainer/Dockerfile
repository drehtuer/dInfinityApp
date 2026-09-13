# dInfinity development image.
#
# Everything the project needs to build, test and analyse itself. Nothing is
# expected on the host except Docker (see .claude/CLAUDE.md, "Environment").
# A phone is attached over WiFi debugging with the adb in this image, so the
# on-device suites run from inside the container too — see docs/build-setup.md.

FROM ubuntu:26.04

ARG ANDROID_API=37
ARG CMDLINE_TOOLS=13114758
ARG GRADLE_VERSION=9.7.1
ARG SONAR_SCANNER_VERSION=7.3.0.5189
# simulation/jolt is C++ and compiles Jolt from source, so without the NDK the
# module cannot be built at all. It is ~2.5 GB, so it can still be switched off
# for a quick image with no native code in it.
ARG INSTALL_NDK=true
# Pinned rather than resolved, unlike the platform and build-tools above: this
# is the compiler that builds the physics engine, and a solver built by a
# different compiler is a different solver — which goal 4 in
# docs/architecture.md cannot have. Keep in step with `dinfinity.ndk` and
# `dinfinity.cmake` in gradle.properties, which is what AGP reads.
ARG NDK_VERSION=30.0.16248370
ARG CMAKE_VERSION=4.1.2
# The emulator and one system image are another ~3 GB. They are the middle
# testing tier: faster to reach than a phone and the only place a regression in
# the physics can be caught before one (docs/TODO.md, Step 5.1).
ARG INSTALL_EMULATOR=true
# The oldest API the emulator may fall back to, which is the app's own minSdk —
# keep in step with `dinfinity.minSdk` in gradle.properties. See the emulator
# block below for why it ever falls back at all.
ARG ANDROID_MIN_API=36
# The developer's uid, needed this early because every layer that writes into
# the SDK hands it over in the same layer — see the note on the last one.
ARG USER_UID=1000
ARG USER_GID=${USER_UID}

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    GRADLE_HOME=/opt/gradle \
    SONAR_SCANNER_HOME=/opt/sonar-scanner \
    DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

# The emulator's own libraries pull in exactly two things the base image does
# not have — found by walking `ldd` over every library it ships, not guessed.
# Everything else it needs it brings with it, and `-no-window` means it needs
# no X server at all. `cpu-checker` earns its 30 KB the first time somebody
# asks why the emulator will not start.
RUN apt-get update && apt-get install -y --no-install-recommends \
        android-sdk-platform-tools-common \
        ca-certificates \
        cpu-checker \
        curl \
        git \
        jq \
        less \
        libpulse0 \
        libxkbfile1 \
        openjdk-21-jdk \
        python3 \
        ripgrep \
        sudo \
        unzip \
        zip \
    && rm -rf /var/lib/apt/lists/* \
# ── Android SDK ──────────────────────────────────────────────────────────
 && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
 && curl --proto '=https' --proto-redir '=https' -fsSL -o /tmp/clt.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip" \
 && unzip -q /tmp/clt.zip -d /tmp/clt \
 && mv /tmp/clt/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
 && rm -rf /tmp/clt /tmp/clt.zip \
 && chown -R "${USER_UID}:${USER_GID}" "${ANDROID_HOME}"

# build-tools is added to PATH after it is installed, below, so apksigner,
# zipalign and aapt2 are usable by hand.
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${GRADLE_HOME}/bin:${SONAR_SCANNER_HOME}/bin:${PATH}"

# The platform and build-tools are resolved from what the repository actually
# offers, so a new Android release does not need this file edited.
#
# Platform packages are minor-versioned since Android 16 ("platforms;android-37.0",
# "…-37.1", …). `compileSdk = 37` resolves to the *base* minor, android-37.0, so
# that is what gets installed — installing 37.2 instead just makes the build
# download 37.0 itself on first run. Using a later minor would mean setting
# compileSdkMinor as well. Pre-release packages are never used.
#
# platform-tools brings adb, which is what talks to a phone over WiFi debugging.
RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
 && sdkmanager --list 2>/dev/null | awk -F'|' '{gsub(/[ \t]/, "", $1); print $1}' \
      | grep -vE 'alpha|beta|rc[0-9]*$' | sort -uV > /tmp/sdk-packages.txt \
 && PLATFORM="$(grep -E "^platforms;android-${ANDROID_API}(\.0)?$" /tmp/sdk-packages.txt | head -1)" \
 && if [ -z "${PLATFORM}" ]; then \
      echo "ERROR: no stable platform for API ${ANDROID_API} in the SDK repository."; \
      echo "Newest platforms offered:"; grep -E '^platforms;android-[0-9.]+$' /tmp/sdk-packages.txt | tail -6; \
      exit 1; \
    fi \
 && BUILD_TOOLS="$(grep -E "^build-tools;${ANDROID_API}\.[0-9]+\.[0-9]+$" /tmp/sdk-packages.txt | tail -1)" \
 && if [ -z "${BUILD_TOOLS}" ]; then \
      BUILD_TOOLS="$(grep -E '^build-tools;[0-9]+\.[0-9]+\.[0-9]+$' /tmp/sdk-packages.txt | tail -1)"; \
    fi \
 && echo "Using ${PLATFORM} and ${BUILD_TOOLS}" \
 && sdkmanager --install "platform-tools" "${PLATFORM}" "${BUILD_TOOLS}" > /dev/null \
 && echo "${PLATFORM##*android-}" > "${ANDROID_HOME}/.platform-version" \
 && echo "${BUILD_TOOLS##*;}" > "${ANDROID_HOME}/.build-tools-version" \
 && chown -R "${USER_UID}:${USER_GID}" "${ANDROID_HOME}"

# Symlinks rather than versioned paths on PATH: the versions above are resolved
# from the repository at build time, so a path with a number in it would have to
# be edited every time one moves. CMake's link lives outside ANDROID_HOME
# because sdkmanager walks that tree and reports a symlinked copy of a package
# as one installed "in an inconsistent location", on every invocation.
ENV PATH="${ANDROID_HOME}/build-tools/latest:/opt/android-cmake/bin:${ANDROID_HOME}/emulator:${PATH}"
RUN BUILD_TOOLS_VERSION="$(cat "${ANDROID_HOME}/.build-tools-version")" \
 && ln -s "${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}" \
          "${ANDROID_HOME}/build-tools/latest" \
 && if [ "${INSTALL_NDK}" = "true" ]; then \
      grep -qx "ndk;${NDK_VERSION}" /tmp/sdk-packages.txt \
      || { echo "ERROR: ndk;${NDK_VERSION} is not offered by the SDK repository."; \
           echo "Newest NDKs offered:"; grep -E '^ndk;' /tmp/sdk-packages.txt | tail -4; \
           exit 1; } \
      && echo "Using ndk;${NDK_VERSION} and cmake;${CMAKE_VERSION}" \
      && sdkmanager --install "ndk;${NDK_VERSION}" "cmake;${CMAKE_VERSION}" > /dev/null \
      && echo "${NDK_VERSION}" > "${ANDROID_HOME}/.ndk-version" \
      && ln -s "${ANDROID_HOME}/cmake/${CMAKE_VERSION}" /opt/android-cmake; \
    else \
      echo "NDK skipped (INSTALL_NDK=${INSTALL_NDK})"; \
    fi \
# ── Emulator ─────────────────────────────────────────────────────────────
# The middle testing tier, resolved the same way as the platform and for the
# same reason: a hard-coded image name rots the moment Google publishes a minor.
#
# **ATD images first, even if that means an older API.** An "automated test
# device" image carries no launcher, no wallpaper and no Play services; it is
# built to be driven headless, and it is the difference between an emulator
# that works and one that does not. API 37's `google_apis` image crashes
# `surfaceflinger` in its `RegionSampling` thread under software rendering with
# no window, and takes the framework down with it — an install then fails with
# "Can't find service: package", which says nothing whatever about the cause.
# The ATD image at API 36 boots in half a minute and runs the suite in nine
# seconds.
#
# Testing one API below the target is a trade, and the same one the project has
# already made for Robolectric (docs/architecture.md, decision 17): API 36 is
# the app's own minSdk, so it is a device the app must work on in any case, and
# the API it *targets* is covered by the phone. The floor stops that trade
# sliding any further than one release.
#
# The ABI follows the host, so this works on an Apple Silicon machine too.
 && if [ "${INSTALL_EMULATOR}" = "true" ]; then \
      case "$(dpkg --print-architecture)" in \
        amd64) ABI=x86_64 ;; \
        arm64) ABI=arm64-v8a ;; \
        *) echo "ERROR: no emulator system image for $(dpkg --print-architecture)."; exit 1 ;; \
      esac \
      && IMAGE="" \
      && API="${ANDROID_API}" \
      && while [ "${API}" -ge "${ANDROID_MIN_API}" ] && [ -z "${IMAGE}" ]; do \
           for TAG in aosp_atd google_atd; do \
             CANDIDATE="$(grep -E "^system-images;android-${API}(\.0)?;${TAG};${ABI}$" \
                            /tmp/sdk-packages.txt | head -1)"; \
             if [ -n "${CANDIDATE}" ]; then IMAGE="${CANDIDATE}"; break; fi; \
           done; \
           API=$((API - 1)); \
         done \
      && if [ -z "${IMAGE}" ]; then \
           echo "No automated-test image down to API ${ANDROID_MIN_API}; falling back to a full one."; \
           for TAG in default google_apis; do \
             CANDIDATE="$(grep -E "^system-images;android-${ANDROID_API}(\.0)?;${TAG};${ABI}$" \
                            /tmp/sdk-packages.txt | head -1)"; \
             if [ -n "${CANDIDATE}" ]; then IMAGE="${CANDIDATE}"; break; fi; \
           done; \
         fi \
      && if [ -z "${IMAGE}" ]; then \
           echo "ERROR: no ${ABI} system image for API ${ANDROID_API} in the SDK repository."; \
           echo "Images offered for that API:"; \
           grep -E "^system-images;android-${ANDROID_API}" /tmp/sdk-packages.txt | head -10; \
           exit 1; \
         fi \
      && echo "Using emulator with ${IMAGE}" \
      && sdkmanager --install "emulator" "${IMAGE}" > /dev/null \
      && echo "${IMAGE}" > "${ANDROID_HOME}/.system-image"; \
    else \
      echo "Emulator skipped (INSTALL_EMULATOR=${INSTALL_EMULATOR})"; \
    fi \
# ── Gradle ───────────────────────────────────────────────────────────────
# The project uses the wrapper; this Gradle exists so the wrapper can be
# (re)generated and so a clean checkout has a working `gradle` on PATH.
 && curl --proto '=https' --proto-redir '=https' -fsSL \
      -o /tmp/gradle.zip \
      "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
 && unzip -q /tmp/gradle.zip -d /opt \
 && mv "/opt/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}" \
 && rm /tmp/gradle.zip \
# ── SonarQube scanner ────────────────────────────────────────────────────
 && curl --proto '=https' --proto-redir '=https' -fsSL -o /tmp/sonar.zip \
      "https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_SCANNER_VERSION}-linux-x64.zip" \
 && unzip -q /tmp/sonar.zip -d /opt \
 && mv "/opt/sonar-scanner-${SONAR_SCANNER_VERSION}-linux-x64" "${SONAR_SCANNER_HOME}" \
 && rm /tmp/sonar.zip \
# Handed over here rather than at the end. A `chown -R` in a later layer copies
# every file it touches into that layer, and by this point the SDK is five
# gigabytes of NDK, emulator and system image — which is five gigabytes of
# image for a change of ownership.
 && chown -R "${USER_UID}:${USER_GID}" "${ANDROID_HOME}"

# ── Non-root user ────────────────────────────────────────────────────────
# Ubuntu ships its own `ubuntu` account at uid 1000, which is exactly the uid a
# devcontainer wants for the developer so that files written in the bind mount
# belong to them on the host. It is removed first, and the creation below is
# *not* guarded with `|| true`: a swallowed failure here leaves `USER dev`
# pointing at an account that does not exist, and the image then refuses to
# start at all with "unable to find user dev".
RUN if getent passwd "${USER_UID}" > /dev/null; then \
      userdel --remove "$(getent passwd "${USER_UID}" | cut -d: -f1)"; \
    fi \
 && if getent group "${USER_GID}" > /dev/null; then \
      groupdel "$(getent group "${USER_GID}" | cut -d: -f1)"; \
    fi \
 && groupadd --gid "${USER_GID}" dev \
 && useradd --uid "${USER_UID}" --gid "${USER_GID}" --create-home --shell /bin/bash dev \
 && echo "dev ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/dev \
# The emulator checks /etc/group by hand for a `kvm` line with the user on it,
# and says LINE_NOT_FOUND rather than anything about permissions when there is
# none. The group's id here is arbitrary: `post-create.sh` moves it to whatever
# the host's /dev/kvm actually uses, which is the number the kernel checks.
 && groupadd --force kvm \
 && usermod --append --groups kvm dev \
 && mkdir -p /home/dev/.gradle /home/dev/.android \
 && chown -R "${USER_UID}:${USER_GID}" /home/dev \
 && [ "$(id -u dev)" = "${USER_UID}" ]

COPY --chmod=0755 emulator.sh /usr/local/bin/dinfinity-emulator
COPY --chmod=0755 await-device.sh /usr/local/bin/dinfinity-await-device
COPY --chmod=0755 phone.sh /usr/local/bin/dinfinity-phone
COPY --chmod=0755 post-create.sh /usr/local/bin/dinfinity-post-create

USER dev
ENV GRADLE_USER_HOME=/home/dev/.gradle
WORKDIR /workspace
