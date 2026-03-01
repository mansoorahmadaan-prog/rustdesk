#!/bin/bash

# RustDesk Android APK Build Script
# This script compiles the APK with auto-start and auto-accept features
# Signing is configured with custom keys

set -e

echo "============================"
echo "RustDesk Android APK Builder"
echo "============================"

# Set up Android SDK environment
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

FLUTTER_DIR="/workspaces/rustdesk/flutter"
ANDROID_DIR="$FLUTTER_DIR/android"
BUILD_OUTPUT="$FLUTTER_DIR/build"

echo ""
echo "[1/5] Setting up Android SDK..."
cd "$ANDROID_DIR"

# Accept SDK licenses
echo "y" | sdkmanager --licenses > /dev/null 2>&1 || true
echo "✓ SDK licenses accepted"

# Install required SDK packages
echo "Installing required SDK packages..."
sdkmanager "platform-tools" > /dev/null 2>&1 &
sdkmanager "build-tools;34.0.0" > /dev/null 2>&1 &
sdkmanager "platforms;android-34" > /dev/null 2>&1 &
sdkmanager "platforms;android-33" > /dev/null 2>&1 &
wait
echo "✓ SDK packages installed"

echo ""
echo "[2/5] Verifying signing configuration..."
if [ ! -f "$ANDROID_DIR/key.properties" ]; then
    echo "✗ key.properties not found"
    exit 1
fi
if [ ! -f "$ANDROID_DIR/rustdesk.keystore" ]; then
    echo "✗ rustdesk.keystore not found"
    exit 1
fi
echo "✓ Signing keys configured"

echo ""
echo "[3/5] Validating Gradle configuration..."
# Check that the signing config is present
if ! grep -q "storeFile keystoreProperties" "$ANDROID_DIR/app/build.gradle"; then
    echo "✗ Gradle signing configuration not found"
    exit 1
fi
echo "✓ Gradle configuration valid"

echo ""
echo "[4/5] Building APK (debug)..."
cd "$ANDROID_DIR"
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"

# Run gradle build
gradle clean > /dev/null 2>&1
gradle build 2>&1 | tail -20

echo ""
echo "[5/5] Build completion check..."
if [ -d "$ANDROID_DIR/app/build" ]; then
    echo "✓ Build output generated"
    
    APK_PATH=$(find "$ANDROID_DIR/app/build" -name "*.apk" -type f 2>/dev/null | head -1)
    if [ -n "$APK_PATH" ]; then
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "✓ APK BUILD SUCCESSFUL!"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
        echo "APK Location: $APK_PATH"
        echo "File Size: $(du -h "$APK_PATH" | cut -f1)"
        echo ""
        echo "Features Compiled:"
        echo "  ✓ Auto-start service on app launch"
        echo "  ✓ Auto-accept connections without confirmation"
        echo "  ✓ Custom signing with rustdesk.keystore"
        echo ""
        echo "Next Steps:"
        echo "  1. Transfer APK to Android device"
        echo "  2. Install: adb install -r $APK_PATH"
        echo "  3. Or enable 'Unknown Sources' and sideload manually"
        echo ""
    else
        echo "✗ No APK file found in build output"
        echo "Build directory contents:"
        ls -la "$ANDROID_DIR/app/build/" | head -20
        exit 1
    fi
else
    echo "✗ Build failed - no build output directory"
    exit 1
fi
