#!/bin/bash
# Local Android APK builder - for quick testing without GitHub Actions
# Supports building individual architecture APKs or universal APK

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
VERSION="1.4.5"
NDK_VERSION="r27c"
RUST_VERSION="1.75"
ANDROID_FLUTTER_VERSION="3.24.5"
CARGO_NDK_VERSION="3.1.2"

# Detect Android SDK/NDK paths
if [ -z "$ANDROID_HOME" ]; then
    ANDROID_HOME="$HOME/android-sdk"
    export ANDROID_HOME
fi

if [ -z "$ANDROID_NDK_HOME" ]; then
    ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
    export ANDROID_NDK_HOME
fi

echo -e "${GREEN}═══════════════════════════════════════════════${NC}"
echo -e "${GREEN}RustDesk Android APK Builder${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════${NC}"
echo ""

# Check prerequisites
check_prerequisites() {
    echo -e "${YELLOW}[1] Checking prerequisites...${NC}"
    
    if ! command -v rustup &> /dev/null; then
        echo -e "${RED}✗ Rust not found. Install from https://rustup.rs${NC}"
        exit 1
    fi
    
    if ! command -v flutter &> /dev/null; then
        echo -e "${RED}✗ Flutter not found. Install from https://flutter.dev${NC}"
        exit 1
    fi
    
    if [ ! -d "$ANDROID_HOME" ]; then
        echo -e "${RED}✗ Android SDK not found at $ANDROID_HOME${NC}"
        echo "Set ANDROID_HOME environment variable or download from:"
        echo "https://developer.android.com/studio/command-line-tools"
        exit 1
    fi
    
    if [ ! -d "$ANDROID_NDK_HOME" ]; then
        echo -e "${RED}✗ Android NDK $NDK_VERSION not found at $ANDROID_NDK_HOME${NC}"
        exit 1
    fi
    
    if ! command -v cargo-ndk &> /dev/null; then
        echo -e "${YELLOW}Installing cargo-ndk...${NC}"
        cargo install cargo-ndk --version $CARGO_NDK_VERSION --locked
    fi
    
    echo -e "${GREEN}✓ All prerequisites found${NC}"
    echo ""
}

# Build Rust library for specific architecture
build_rust_lib() {
    local target=$1
    local arch=$2
    
    echo -e "${YELLOW}[2] Building Rust library for $arch ($target)...${NC}"
    
    rustup target add "$target" 2>/dev/null || true
    
    case "$target" in
        aarch64-linux-android)
            ./flutter/ndk_arm64.sh
            mkdir -p ./flutter/android/app/src/main/jniLibs/arm64-v8a
            cp ./target/$target/release/liblibrustdesk.so ./flutter/android/app/src/main/jniLibs/arm64-v8a/librustdesk.so
            ;;
        armv7-linux-androideabi)
            ./flutter/ndk_arm.sh
            mkdir -p ./flutter/android/app/src/main/jniLibs/armeabi-v7a
            cp ./target/$target/release/liblibrustdesk.so ./flutter/android/app/src/main/jniLibs/armeabi-v7a/librustdesk.so
            ;;
        x86_64-linux-android)
            ./flutter/ndk_x64.sh
            mkdir -p ./flutter/android/app/src/main/jniLibs/x86_64
            cp ./target/$target/release/liblibrustdesk.so ./flutter/android/app/src/main/jniLibs/x86_64/librustdesk.so
            ;;
        i686-linux-android)
            ./flutter/ndk_x86.sh
            mkdir -p ./flutter/android/app/src/main/jniLibs/x86
            cp ./target/$target/release/liblibrustdesk.so ./flutter/android/app/src/main/jniLibs/x86/librustdesk.so
            ;;
    esac
    
    echo -e "${GREEN}✓ Built Rust library for $arch${NC}"
}

# Build Flutter APK for specific architecture
build_flutter_apk() {
    local target=$1
    local arch=$2
    local platform=$3
    
    echo -e "${YELLOW}[3] Building Flutter APK for $arch...${NC}"
    
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    export PATH=$JAVA_HOME/bin:$PATH
    
    # Update Gradle config
    sed -i "s/org.gradle.jvmargs=-Xmx1024M/org.gradle.jvmargs=-Xmx2g/g" ./flutter/android/gradle.properties
    sed -i "s/signingConfigs.release/signingConfigs.debug/g" ./flutter/android/app/build.gradle
    
    # Copy C++ shared libraries
    case "$target" in
        aarch64-linux-android)
            cp ${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so ./flutter/android/app/src/main/jniLibs/arm64-v8a/
            ;;
        armv7-linux-androideabi)
            cp ${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so ./flutter/android/app/src/main/jniLibs/armeabi-v7a/
            ;;
        x86_64-linux-android)
            cp ${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so ./flutter/android/app/src/main/jniLibs/x86_64/
            ;;
        i686-linux-android)
            cp ${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/i686-linux-android/libc++_shared.so ./flutter/android/app/src/main/jniLibs/x86/
            ;;
    esac
    
    # Build APK
    pushd flutter > /dev/null
    flutter build apk --release --target-platform "$platform" --split-per-abi
    popd > /dev/null
    
    # Move APK to root
    case "$platform" in
        android-arm64)
            mv ./flutter/build/app/outputs/flutter-apk/app-arm64-v8a-release.apk ./rustdesk-$VERSION-$arch-unsigned.apk
            ;;
        android-arm)
            mv ./flutter/build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk ./rustdesk-$VERSION-$arch-unsigned.apk
            ;;
        android-x64)
            mv ./flutter/build/app/outputs/flutter-apk/app-x86_64-release.apk ./rustdesk-$VERSION-$arch-unsigned.apk
            ;;
        android-x86)
            mv ./flutter/build/app/outputs/flutter-apk/app-x86-release.apk ./rustdesk-$VERSION-$arch-unsigned.apk
            ;;
    esac
    
    echo -e "${GREEN}✓ Built APK for $arch${NC}"
}

# Build universal APK combining all architectures
build_universal_apk() {
    echo -e "${YELLOW}[4] Building Universal APK (all architectures)...${NC}"
    
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    export PATH=$JAVA_HOME/bin:$PATH
    
    # Update Gradle config
    sed -i "s/org.gradle.jvmargs=-Xmx1024M/org.gradle.jvmargs=-Xmx2g/g" ./flutter/android/gradle.properties
    sed -i "s/signingConfigs.release/signingConfigs.debug/g" ./flutter/android/app/build.gradle
    
    # Build universal APK
    pushd flutter > /dev/null
    flutter build apk --release --target-platform android-arm64,android-arm,android-x64
    popd > /dev/null
    
    # Move APK to root
    mv ./flutter/build/app/outputs/flutter-apk/app-release.apk ./rustdesk-$VERSION-universal-unsigned.apk
    
    echo -e "${GREEN}✓ Built Universal APK${NC}"
}

# Sign APK
sign_apk() {
    local apk_file=$1
    
    echo -e "${YELLOW}[5] Signing APK ($apk_file)...${NC}"
    
    # Check if keystore exists
    if [ ! -f "./flutter/android/rustdesk.keystore" ]; then
        echo -e "${YELLOW}Keystore not found. Using unsigned APK for testing.${NC}"
        echo -e "${YELLOW}For production builds, generate signing key:${NC}"
        echo "  keytool -genkey -v -keystore ./flutter/android/rustdesk.keystore \\"
        echo "    -keyalg RSA -keysize 2048 -validity 10000 -alias rustdesk"
        return
    fi
    
    jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
        -keystore ./flutter/android/rustdesk.keystore \
        -storepass rustdesk123 -keypass rustdesk123 \
        "$apk_file" rustdesk
    
    echo -e "${GREEN}✓ Signed APK: $apk_file${NC}"
}

# Main menu
show_menu() {
    echo -e "${YELLOW}Select build option:${NC}"
    echo "1) Build aarch64 (arm64-v8a) APK"
    echo "2) Build armv7 (armeabi-v7a) APK"
    echo "3) Build x86_64 APK"
    echo "4) Build Universal APK (all architectures)"
    echo "5) Build All architecture APKs"
    echo "6) Exit"
    echo ""
    read -p "Enter choice [1-6]: " choice
}

# Main execution
main() {
    check_prerequisites
    
    while true; do
        show_menu
        
        case $choice in
            1)
                echo ""
                build_rust_lib "aarch64-linux-android" "aarch64"
                build_flutter_apk "aarch64-linux-android" "aarch64" "android-arm64"
                sign_apk "rustdesk-$VERSION-aarch64-unsigned.apk"
                echo -e "${GREEN}✓ Done! APK: rustdesk-$VERSION-aarch64-unsigned.apk${NC}"
                echo ""
                ;;
            2)
                echo ""
                build_rust_lib "armv7-linux-androideabi" "armv7"
                build_flutter_apk "armv7-linux-androideabi" "armv7" "android-arm"
                sign_apk "rustdesk-$VERSION-armv7-unsigned.apk"
                echo -e "${GREEN}✓ Done! APK: rustdesk-$VERSION-armv7-unsigned.apk${NC}"
                echo ""
                ;;
            3)
                echo ""
                build_rust_lib "x86_64-linux-android" "x86_64"
                build_flutter_apk "x86_64-linux-android" "x86_64" "android-x64"
                sign_apk "rustdesk-$VERSION-x86_64-unsigned.apk"
                echo -e "${GREEN}✓ Done! APK: rustdesk-$VERSION-x86_64-unsigned.apk${NC}"
                echo ""
                ;;
            4)
                echo ""
                # Ensure all architectures are built first
                echo -e "${YELLOW}Building Rust libraries for all architectures...${NC}"
                build_rust_lib "aarch64-linux-android" "aarch64"
                build_rust_lib "armv7-linux-androideabi" "armv7"
                build_rust_lib "x86_64-linux-android" "x86_64"
                build_universal_apk
                sign_apk "rustdesk-$VERSION-universal-unsigned.apk"
                echo -e "${GREEN}✓ Done! APK: rustdesk-$VERSION-universal-unsigned.apk${NC}"
                echo ""
                ;;
            5)
                echo ""
                echo -e "${YELLOW}Building all architecture APKs...${NC}"
                build_rust_lib "aarch64-linux-android" "aarch64"
                build_flutter_apk "aarch64-linux-android" "aarch64" "android-arm64"
                sign_apk "rustdesk-$VERSION-aarch64-unsigned.apk"
                
                build_rust_lib "armv7-linux-androideabi" "armv7"
                build_flutter_apk "armv7-linux-androideabi" "armv7" "android-arm"
                sign_apk "rustdesk-$VERSION-armv7-unsigned.apk"
                
                build_rust_lib "x86_64-linux-android" "x86_64"
                build_flutter_apk "x86_64-linux-android" "x86_64" "android-x64"
                sign_apk "rustdesk-$VERSION-x86_64-unsigned.apk"
                
                echo -e "${GREEN}✓ All APKs built!${NC}"
                echo -e "${GREEN}  - rustdesk-$VERSION-aarch64-unsigned.apk${NC}"
                echo -e "${GREEN}  - rustdesk-$VERSION-armv7-unsigned.apk${NC}"
                echo -e "${GREEN}  - rustdesk-$VERSION-x86_64-unsigned.apk${NC}"
                echo ""
                ;;
            6)
                echo "Exiting."
                exit 0
                ;;
            *)
                echo -e "${RED}Invalid option. Try again.${NC}"
                echo ""
                ;;
        esac
    done
}

# Run main
main
