# RustDesk Android APK Build Guide
## With Auto-Start Service and Auto-Accept Connections

This guide provides instructions for building the modified RustDesk Android APK that includes:
- Auto-start service on app launch
- Auto-accept incoming connections without confirmation
- Custom signing with your own keystore

## Prerequisites

### Option 1: Using This Dev Container (Quick Setup)

You'll need:
- Java Development Kit (JDK) 11+ ✓ (Already installed)
- Android SDK ✓ (Will be downloaded and set up)
- Gradle ✓ (Already installed: 9.2.1)
- Android NDK (for native compilation)
- Flutter SDK (for code generation)

### Option 2: Full Development Environment (Recommended for Production)

1. **Install Android Studio**
   ```bash
   # Linux/Mac: Download from https://developer.android.com/studio
   # Or via package manager:
   sudo apt-get install android-studio
   ```

2. **Android SDK Setup**
   ```bash
   # Accept licenses
   sdkmanager --licenses
   
   # Install required tools
   sdkmanager "platform-tools"
   sdkmanager "build-tools;34.0.0"
   sdkmanager "platforms;android-34"
   sdkmanager "ndk;25.2.9519653"
   ```

3. **Install Flutter**
   ```bash
   git clone https://github.com/flutter/flutter.git -b stable
   export PATH="$PATH:$(pwd)/flutter/bin"
   flutter doctor
   ```

## Quick Build in Dev Container

### Step 1: Generate Signing Key (Done!)

The signing key has already been generated:
- **Keystore file**: `/workspaces/rustdesk/flutter/android/rustdesk.keystore`
- **Key properties**: `/workspaces/rustdesk/flutter/android/key.properties`
- **Alias**: `rustdesk`
- **Store password**: `rustdesk123`
- **Key password**: `rustdesk123`

**⚠️ Security Note**: These are development credentials. For production distribution:
```bash
# Generate a new production keystore
keytool -genkey -v -keystore my_app_release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my_key_alias
```

### Step 2: Set Up Android SDK

```bash
# Set environment variables
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"

# Accept licenses
yes | sdkmanager --licenses

# Install required packages
sdkmanager "platform-tools" "build-tools;34.0.0" "platforms;android-34"
```

### Step 3: Build APK

**Method A: Using the provided build script**
```bash
cd /workspaces/rustdesk
bash build_apk.sh
```

**Method B: Manual Gradle build**
```bash
cd /workspaces/rustdesk/flutter/android

# Set environment
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"

# Build
gradle clean
gradle build --stacktrace
```

**Method C: Using Python build script (if Flutter SDK is available)**
```bash
cd /workspaces/rustdesk
python3 build.py --flutter --release
```

## Build Output

The resulting APK will be located at:
```
/workspaces/rustdesk/flutter/android/app/build/outputs/apk/
```

Look for files matching: `app-*.apk`

## Installation on Android Device

### Option 1: Using ADB (Android Debug Bridge)

```bash
# Connect device via USB and enable Developer Mode + USB Debugging

# Install the APK
adb install flutter/android/app/build/outputs/apk/app-release.apk

# Or force reinstall over existing version
adb install -r flutter/android/app/build/outputs/apk/app-release.apk

# Verify installation
adb shell pm list packages | grep flutter_hbb

# Launch app
adb shell am start -n com.carriez.flutter_hbb/.MainActivity
```

### Option 2: Manual Sideload

1. Transfer APK to Android device (USB or wireless)
2. On Android: Settings → Security → Enable "Unknown Sources"
3. Open file manager, locate and tap the APK to install
4. Confirm installation

### Option 3: Using Android Studio

1. Open Android Studio
2. Run → Select Device
3. Select your APK file
4. Android Studio will install and launch it

## Verification

After installation, verify the features are working:

### Test Auto-Start Service
1. Go to RustDesk app
2. Navigate to "Share screen" tab
3. Scroll to "Permissions" section
4. You should see "Auto-start service on app launch" toggle
5. Enable it, then close and reopen the app
6. Service should start automatically

### Test Auto-Accept Connections
1. Go to RustDesk app → "Share screen" tab
2. Tap menu (⋮) at top right
3. Look for "Auto-accept connections (no confirmation)" option
4. Select it
5. Try connecting from another device
6. Connection should be accepted automatically

## Troubleshooting

### Build Fails: "Android SDK not found"
```bash
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

### Build Fails: "NDK not found"
```bash
# Install NDK
sdkmanager "ndk;25.2.9519653"

# Set NDK path
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/25.2.9519653"
```

### Build Fails: "Flutter SDK not found"
For full feature support, install Flutter SDK:
```bash
git clone https://github.com/flutter/flutter.git
export PATH="$(pwd)/flutter/bin:$PATH"
flutter pub get
```

### APK Installation Fails
- Ensure device is in Developer Mode
- Try: `adb install -r` (reinstall over existing)
- Check: `adb logcat | grep -i rustdesk`

### App Crashes After Install
Check logs:
```bash
adb logcat | grep -i flutter
adb logcat | grep -i rustdesk
```

## Build Configuration Details

### Modified Files

The following files were modified to add the auto-start and auto-accept features:

**Android (Kotlin)**:
- `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainActivity.kt`
- `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/common.kt`

**Flutter (Dart)**:
- `flutter/lib/main.dart`
- `flutter/lib/models/server_model.dart`
- `flutter/lib/consts.dart`
- `flutter/lib/mobile/pages/server_page.dart`

### Key Configuration Files

- `flutter/android/app/build.gradle` - Gradle build configuration with signing
- `flutter/android/key.properties` - Signing credentials
- `flutter/android/rustdesk.keystore` - Certificate keystore
- `flutter/android/AndroidManifest.xml` - App permissions and manifest

## Advanced: Release Build

For distribution on Google Play Store or production deployment:

```bash
# Generate production keystore
keytool -genkey -v -keystore release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my_release_key

# Update key.properties
cat > flutter/android/key.properties << EOF
storeFile=release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=my_release_key
keyPassword=YOUR_KEY_PASSWORD
EOF

# Build release APK
cd flutter/android
gradle assembleRelease

# Or App Bundle for Play Store
gradle bundleRelease
```

## Signing Certificate Info

To view details of the generated signing certificate:

```bash
keytool -list -v -keystore flutter/android/rustdesk.keystore \
  -storepass rustdesk123 -keypass rustdesk123
```

## Environment Variables Summary

```bash
# Android Development
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"

# Optional: NDK (if using native components)
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/25.2.9519653"

# Java
export JAVA_HOME="/usr/local/sdkman/candidates/java/current"

# Gradle
export GRADLE_USER_HOME="~/.gradle"

# Add to PATH
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

## Support & Issues

If you encounter issues:

1. **Check logcat**: `adb logcat -s "rustdesk"`
2. **Verify setup**: `gradle -v && java -version && sdkmanager --list_installed`
3. **Clean rebuild**: `gradle clean && gradle build`
4. **Review code changes**: See [ANDROID_AUTO_START_GUIDE.md](../ANDROID_AUTO_START_GUIDE.md)

## Next Steps

After successful APK build:

1. Test on Android device (as described above)
2. Configure auto-start and auto-accept in app settings
3. Test connectivity with another RustDesk instance
4. Share settings with your infrastructure team

---

**Build Date**: March 1, 2026
**Modified Features**: Auto-start Service, Auto-accept Connections
**Signing**: Custom keystore (rustdesk.keystore)
**API Level**: 22+ (Android 5.1+)
