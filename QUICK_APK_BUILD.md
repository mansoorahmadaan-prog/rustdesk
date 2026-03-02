# Quick APK Build Guide

Build Android APKs locally without waiting for GitHub Actions CI/CD!

## Quick Start (2 minutes)

### 1. **For Testing (Fastest - Universal APK)**

```bash
cd /workspaces/rustdesk
./build_apk_local.sh
# Select option 4 (Build Universal APK)
```

**Output:** `rustdesk-1.4.5-universal-unsigned.apk`

This creates a single APK containing all architectures (aarch64, armv7, x86_64). Android automatically selects the best one for your device.

---

### 2. **For Testing Specific Architecture**

```bash
./build_apk_local.sh
# Select option 1, 2, or 3
```

Options:
- **1** = aarch64 (modern 64-bit ARM) → `rustdesk-1.4.5-aarch64-unsigned.apk`
- **2** = armv7 (older 32-bit ARM) → `rustdesk-1.4.5-armv7-unsigned.apk`
- **3** = x86_64 (emulator/tablet) → `rustdesk-1.4.5-x86_64-unsigned.apk`

---

### 3. **For Complete Testing (All Architectures)**

```bash
./build_apk_local.sh
# Select option 5 (Build All architecture APKs)
```

Creates three separate APKs (one per architecture).

---

## Environment Setup

### If you don't have Android SDK/NDK installed:

```bash
# 1. Set Android environment (if not already set)
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/r27c"

# 2. Verify setup
echo $ANDROID_HOME
ls -la $ANDROID_NDK_HOME

# 3. If missing, download NDK:
# For the environment used here, Android SDK should already be set up
# from previous builds. If not, download from:
# https://developer.android.com/studio/command-line-tools
```

### Check Prerequisites

```bash
# Verify all required tools are installed
./build_apk_local.sh
# The script will check for: Rust, Flutter, Android SDK/NDK
```

---

## Build Output

APKs are created in the project root directory:

```
/workspaces/rustdesk/
├── rustdesk-1.4.5-aarch64-unsigned.apk      (arm64 only, ~150MB)
├── rustdesk-1.4.5-armv7-unsigned.apk        (32-bit ARM only, ~140MB)
├── rustdesk-1.4.5-x86_64-unsigned.apk       (Intel x64 emulator, ~150MB)
└── rustdesk-1.4.5-universal-unsigned.apk    (all archs, ~420MB)
```

---

## Installing on Android Device

### **Option A: Unsigned APK (Testing)**

```bash
# Enable "Unknown Sources" or "Install from Unknown Sources" in Settings
adb install rustdesk-1.4.5-universal-unsigned.apk
```

### **Option B: Signed APK (Testing with Keystore)**

If you already have a signing keystore:

```bash
# The script automatically signs with: flutter/android/rustdesk.keystore
# Password: rustdesk123 (if using the default from setup)
adb install rustdesk-1.4.5-universal-unsigned.apk
```

---

## Troubleshooting

### ❌ "Rust not found"
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env
```

### ❌ "Flutter not found"
```bash
# Install Flutter SDK
git clone https://github.com/flutter/flutter.git -b stable $HOME/flutter
export PATH="$HOME/flutter/bin:$PATH"
```

### ❌ "Android SDK not found"
```bash
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/r27c"
```

### ❌ "gradle build failed"
```bash
# Increase RAM allocation for Gradle
export _JAVA_OPTIONS="-Xmx4g"

# Or use the build script (it handles this automatically)
./build_apk_local.sh
```

### ❌ "APK file not created / Flutter build error"
```bash
# Clean Flutter cache
cd flutter
flutter clean
rm -rf build/
cd ..

# Try building again
./build_apk_local.sh
```

---

## Performance & Time

| Option | Architecture | Time | File Size |
|--------|--------------|------|-----------|
| **Option 1-3** | Single arch | 8-12 min | ~140-150 MB |
| **Option 4** | Universal | 20-25 min | ~420 MB |
| **Option 5** | All (3x) | 25-30 min | 3x ~140-150 MB |

---

## Features Included

The APKs built with this script include your recent features:

✅ **Auto-start service** - App starts on device boot  
✅ **Auto-accept connections** - New connections auto-approved  
✅ **Custom signing** - Uses your keystore credentials  
✅ **Release build** - Optimized and obfuscated  

---

## Next Steps

1. **Build your first APK:**
   ```bash
   ./build_apk_local.sh
   ```

2. **Transfer to your phone:**
   ```bash
   adb install rustdesk-1.4.5-universal-unsigned.apk
   ```

3. **Test the app:**
   - Check auto-start service (should start on app boot)
   - Check auto-accept connections (should approve incoming requests)

4. **For CI/CD automated builds:**
   - GitHub Actions will use `.github/workflows/flutter-build.yml` (Android-only)
   - APKs appear in GitHub Releases

---

## For GitHub Actions CI/CD

Your GitHub Actions workflow is already set up to build APKs automatically on push. To trigger:

```bash
git add .
git commit -m "trigger CI build"
git push
```

Then check the **Actions** tab to monitor the build.

---

**Questions?** Check the build script or review `ANDROID_BUILD_INSTRUCTIONS.md`
