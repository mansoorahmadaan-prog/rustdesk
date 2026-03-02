# APK Testing Guide - Auto-Start & Auto-Accept Features

Complete guide for building and testing your Android APK with auto-start service and auto-accept connections.

## Overview

Your RustDesk Android APK now includes two key features:
- ✅ **Auto-start Service** - App starts automatically when device boots
- ✅ **Auto-accept Connections** - Incoming connections are automatically approved

## Build Options

### 🚀 Fastest (Recommended for Testing)

```bash
cd /workspaces/rustdesk
./setup_and_build_apk.sh
```

**Time:** 10-15 minutes  
**Output:** `rustdesk-1.4.5-aarch64-unsigned.apk` (arm64 devices)

### 📦 Interactive Menu (Multiple Options)

```bash
./build_apk_local.sh
```

**Choose:**
- Option 1: Single arch (aarch64) - 10 min
- Option 2: Single arch (armv7) - 10 min  
- Option 3: Single arch (x86_64) - 10 min
- Option 4: Universal APK - 20-25 min
- Option 5: All three APKs - 25-30 min

---

## Testing Procedure

### Step 1: Build APK (Choose One)

**Option A - Fastest (Recommended):**
```bash
./setup_and_build_apk.sh
# Wait 10-15 minutes
# Output: rustdesk-1.4.5-aarch64-unsigned.apk
```

**Option B - Custom:**
```bash
./build_apk_local.sh
# Select menu option (1-5)
```

---

### Step 2: Install on Android Device

#### **Using ADB (Recommended)**

```bash
# 1. Connect device via USB
# 2. Enable USB Debugging in Settings
# 3. Accept debug permission prompt on device
# 4. Install APK:

adb install rustdesk-1.4.5-aarch64-unsigned.apk

# Wait for installation to complete
# Check for success: "Success"
```

#### **Using File Transfer**

```bash
# 1. Copy APK to device storage
adb push rustdesk-1.4.5-aarch64-unsigned.apk /sdcard/Download/

# 2. On device: Settings > Open Unknown apps > Downloads
#    Enable "Allow installation from this source"

# 3. Open Files app, navigate to Downloads/
#    Tap rustdesk-1.4.5-aarch64-unsigned.apk
#    Tap Install
```

---

### Step 3: Test Auto-Start Service

#### **Test 1: App Starts on Boot**

1. Open the app once (to initialize)
2. Go to: Settings > Permissions > Enable "Auto-start Service"
3. Restart device: `adb reboot`
4. After boot, **don't open the app manually**
5. Check if RustDesk is running:
   ```bash
   adb shell "ps aux | grep rustdesk"
   # Should show RustDesk process running
   ```

**Pass/Fail:**
- ✅ **Pass**: Process running after boot without manual launch
- ❌ **Fail**: Process only starts after manually opening app

---

### Step 4: Test Auto-Accept Connections

#### **Test 1: Accept via Control Panel**

1. Open RustDesk app on test device
2. Go to: Settings > Permissions
3. Enable "Auto-accept connections (no confirmation)"
4. From another device/computer: Connect to this device ID
5. **No permission dialog should appear**
6. Connection should be **automatically approved**

**Pass/Fail:**
- ✅ **Pass**: Connection immediately accepted, no approval needed
- ❌ **Fail**: Approval dialog still appears

#### **Test 2: Accept from Locked Screen**

1. Enable "Auto-accept connections" in settings
2. Lock device (press power button)
3. From another device: Connect
4. Device should accept without unlock screen appearing

**Pass/Fail:**
- ✅ **Pass**: Connection accepted on locked device
- ❌ **Fail**: Requires unlock to accept

---

## Troubleshooting

### ❌ "adb: command not found"

Android Platform Tools not installed:

```bash
# Download and install
cd ~/android-sdk
mkdir -p platform-tools
cd platform-tools
wget https://dl.google.com/android/repository/platform-tools-latest-linux.zip
unzip platform-tools-latest-linux.zip
export PATH="$PATH:$HOME/android-sdk/platform-tools"
adb devices
```

### ❌ "APK Installation Failed"

**"App not installed":**
```bash
# Try clearing cache
adb uninstall com.carriez.flutter_hbb
# Try again
adb install rustdesk-1.4.5-aarch64-unsigned.apk
```

**"Incompatible architecture":**
```bash
# Check device architecture
adb shell getprop ro.product.cpu.abilist
# Output examples:
# arm64-v8a,armeabi-v7a,armeabi -> Use aarch64 or armv7
# x86_64,x86 -> Use x86_64
# If mismatch, build correct architecture and reinstall
```

**"Signature mismatch":**
```bash
# Remove old unsigned APK
adb uninstall com.carriez.flutter_hbb

# Reinstall new one
adb install rustdesk-1.4.5-aarch64-unsigned.apk
```

### ❌ "Auto-start not working"

**Check if feature is enabled:**
```bash
adb shell am start -n com.carriez.flutter_hbb/com.carriez.flutter_hbb.MainActivity
# Open Settings > Permissions
# Verify "Auto-start Service" toggle is ON
```

**Check device settings:**
```bash
# Some devices have aggressive battery optimization
# Settings > Battery > Battery optimization
# Find RustDesk, set to "Don't optimize"

# Settings > Apps > Special app access > Device admin apps
# Verify RustDesk is enabled
```

### ❌ "Auto-accept not working"

**Verify setting is enabled:**
1. Open RustDesk
2. Menu (⋮) > Settings > Permissions tab
3. Look for "Auto-accept connections (no confirmation)"
4. Toggle must be ON (blue)

**Check Android permissions:**
```bash
adb shell pm dump com.carriez.flutter_hbb | grep -i permission
# Should show multiple permissions granted
```

---

## APK File Information

| File | Device Type | Size |
|------|------------|------|
| `rustdesk-1.4.5-aarch64-unsigned.apk` | Modern phones (2015+) | ~150MB |
| `rustdesk-1.4.5-armv7-unsigned.apk` | Older phones (2010-2015) | ~140MB |
| `rustdesk-1.4.5-x86_64-unsigned.apk` | Emulators, tablets | ~150MB |
| `rustdesk-1.4.5-universal-unsigned.apk` | Any device (auto-select) | ~420MB |

---

## Logs for Debugging

### Get App Logs

```bash
# Clear logs
adb logcat -c

# Start app and capture logs
adb shell am start -n com.carriez.flutter_hbb/com.carriez.flutter_hbb.MainActivity
adb logcat -s flutter:* -v time > rustdesk_logs.txt

# Wait 30-60 seconds, stop with Ctrl+C
# Review logs:
cat rustdesk_logs.txt | grep -i "auto\|start\|connection"
```

### Check Boot Start Logs

```bash
# Reboot device
adb reboot

# Wait 30 seconds for boot to complete
adb logcat -s flutter:* | head -50
```

---

## Next Steps

1. **Build APK**: Run `./setup_and_build_apk.sh`
2. **Install**: Use `adb install rustdesk-1.4.5-aarch64-unsigned.apk`
3. **Test Auto-Start**: Reboot and verify running
4. **Test Auto-Accept**: Accept connection from another device
5. **Report Issues**: If tests fail, check troubleshooting section

---

## For CI/CD Automated Builds

Your GitHub Actions is configured in `.github/workflows/flutter-build.yml` (Android-only).

**To trigger automatic builds:**
```bash
git add .
git commit -m "test auto-build"
git push
```

**APKs will be available in GitHub Releases** (if configured with signing keys)

---

**Last Updated:** 2025-03-02
