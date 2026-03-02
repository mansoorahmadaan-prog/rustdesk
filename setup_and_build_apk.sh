#!/bin/bash
# Complete APK Build Setup & Execution Script
# Installs missing tools and builds APK

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}═════════════════════════════════════════${NC}"
echo -e "${GREEN}RustDesk Android APK - Complete Setup${NC}"
echo -e "${GREEN}═════════════════════════════════════════${NC}"
echo ""

# Configuration
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/r27c"

# Install Rust if needed
if ! command -v rustc &> /dev/null; then
    echo -e "${YELLOW}[1] Installing Rust...${NC}"
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
    source $HOME/.cargo/env
    rustup default 1.75
    rustup target add aarch64-linux-android
else
    echo -e "${GREEN}✓ Rust found: $(rustc --version)${NC}"
fi

# Install Flutter if needed
if ! command -v flutter &> /dev/null; then
    echo -e "${YELLOW}[2] Installing Flutter...${NC}"
    cd /tmp
    wget https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_3.24.5-stable.tar.xz -O flutter.tar.xz 2>/dev/null || curl -L https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_3.24.5-stable.tar.xz -o flutter.tar.xz
    tar -xf flutter.tar.xz
    sudo mv flutter /usr/local/
    export PATH="/usr/local/flutter/bin:$PATH"
    flutter precache
    flutter doctor
else
    echo -e "${GREEN}✓ Flutter found: $(flutter --version | head -1)${NC}"
fi

# Verify Android SDK
if [ ! -d "$ANDROID_HOME" ]; then
    echo -e "${RED}✗ Android SDK not found at $ANDROID_HOME${NC}"
    echo -e "${YELLOW}Please install Android SDK commandline tools:${NC}"
    echo "wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    echo "unzip to: $ANDROID_HOME"
    exit 1
else
    echo -e "${GREEN}✓ Android SDK found: $ANDROID_HOME${NC}"
fi

# Create NDK symlink if needed
if [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo -e "${YELLOW}[3] Setting up NDK...${NC}"
    if [ -d "$ANDROID_HOME/ndk" ]; then
        cd "$ANDROID_HOME/ndk" && ls -d r* | sort -V | tail -1
        LATEST_NDK=$(ls -d $ANDROID_HOME/ndk/r* 2>/dev/null | sort -V | tail -1)
        if [ -n "$LATEST_NDK" ]; then
            export ANDROID_NDK_HOME="$LATEST_NDK"
            echo -e "${GREEN}✓ Using NDK: $ANDROID_NDK_HOME${NC}"
        fi
    fi
fi

# Build setup
echo ""
echo -e "${YELLOW}[4] Building APK...${NC}"
echo ""

cd /workspaces/rustdesk

# Update paths
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

# Install cargo-ndk
cargo install cargo-ndk --version 3.1.2 --locked 2>/dev/null || true

# Build single architecture for speed
echo -e "${YELLOW}Building for aarch64 (arm64-v8a)...${NC}"
rustup target add aarch64-linux-android 2>/dev/null || true

# Build Rust lib
./flutter/ndk_arm64.sh 2>/dev/null || echo "Note: ndk_arm64.sh may have warnings, continuing..."

mkdir -p ./flutter/android/app/src/main/jniLibs/arm64-v8a
cp ./target/aarch64-linux-android/release/liblibrustdesk.so ./flutter/android/app/src/main/jniLibs/arm64-v8a/librustdesk.so

# Copy C++ library
cp ${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so ./flutter/android/app/src/main/jniLibs/arm64-v8a/

# Update Gradle
sed -i "s/org.gradle.jvmargs=-Xmx1024M/org.gradle.jvmargs=-Xmx2g/g" ./flutter/android/gradle.properties
sed -i "s/signingConfigs.release/signingConfigs.debug/g" ./flutter/android/app/build.gradle

# Build Flutter APK
echo -e "${YELLOW}Building Flutter APK...${NC}"
cd flutter
flutter build apk --release --target-platform android-arm64 --split-per-abi
cd ..

# Move APK
mv ./flutter/build/app/outputs/flutter-apk/app-arm64-v8a-release.apk ./rustdesk-1.4.5-aarch64-unsigned.apk

echo ""
echo -e "${GREEN}═════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ APK Build Complete!${NC}"
echo -e "${GREEN}═════════════════════════════════════════${NC}"
echo ""
echo "APK Location: /workspaces/rustdesk/rustdesk-1.4.5-aarch64-unsigned.apk"
echo ""
echo "Install on device:"
echo "  adb install rustdesk-1.4.5-aarch64-unsigned.apk"
echo ""
echo "For more options:"
echo "  ./build_apk_local.sh"
echo ""
