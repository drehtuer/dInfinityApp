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
# The NDK is only needed once simulation/jolt has native code (plan Step 3).
# It is ~2.5 GB, so it can be switched off for a quick image.
ARG INSTALL_NDK=true

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    GRADLE_HOME=/opt/gradle \
    SONAR_SCANNER_HOME=/opt/sonar-scanner \
    DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

RUN apt-get update && apt-get install -y --no-install-recommends \
        android-sdk-platform-tools-common \
        ca-certificates \
        curl \
        git \
        jq \
        less \
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
 && rm -rf /tmp/clt /tmp/clt.zip

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
 && echo "${BUILD_TOOLS##*;}" > "${ANDROID_HOME}/.build-tools-version"

ENV PATH="${ANDROID_HOME}/build-tools/latest:${PATH}"
RUN ln -s "${ANDROID_HOME}/build-tools/$(cat "${ANDROID_HOME}/.build-tools-version")" "${ANDROID_HOME}/build-tools/latest" \
 && if [ "${INSTALL_NDK}" = "true" ]; then \
      NDK="$(grep -E '^ndk;[0-9]+\.[0-9]+\.[0-9]+$' /tmp/sdk-packages.txt | tail -1)" \
      && CMAKE="$(grep -E '^cmake;[0-9]+\.[0-9]+\.[0-9]+$' /tmp/sdk-packages.txt | tail -1)" \
      && echo "Using ${NDK} and ${CMAKE}" \
      && sdkmanager --install "${NDK}" "${CMAKE}" > /dev/null \
      && echo "${NDK##*;}" > "${ANDROID_HOME}/.ndk-version"; \
    else \
      echo "NDK skipped (INSTALL_NDK=${INSTALL_NDK})"; \
    fi

# ── Gradle ───────────────────────────────────────────────────────────────
# The project uses the wrapper; this Gradle exists so the wrapper can be
# (re)generated and so a clean checkout has a working `gradle` on PATH.
RUN curl --proto '=https' --proto-redir '=https' -fsSL -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
 && unzip -q /tmp/gradle.zip -d /opt \
 && mv "/opt/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}" \
 && rm /tmp/gradle.zip \
# ── SonarQube scanner ────────────────────────────────────────────────────
 && curl --proto '=https' --proto-redir '=https' -fsSL -o /tmp/sonar.zip \
      "https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_SCANNER_VERSION}-linux-x64.zip" \
 && unzip -q /tmp/sonar.zip -d /opt \
 && mv "/opt/sonar-scanner-${SONAR_SCANNER_VERSION}-linux-x64" "${SONAR_SCANNER_HOME}" \
 && rm /tmp/sonar.zip

# ── Non-root user ────────────────────────────────────────────────────────
ARG USER_UID=1000
ARG USER_GID=${USER_UID}
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
 && mkdir -p /home/dev/.gradle /home/dev/.android \
 && chown -R "${USER_UID}:${USER_GID}" "${ANDROID_HOME}" /home/dev \
 && [ "$(id -u dev)" = "${USER_UID}" ]

USER dev
ENV GRADLE_USER_HOME=/home/dev/.gradle
WORKDIR /workspace
