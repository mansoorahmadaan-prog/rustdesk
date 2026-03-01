# Android Auto-Start and Auto-Accept Configuration Guide

This guide explains the auto-start service and auto-accept connections features implemented for the RustDesk Android app.

## Overview

Two new features have been implemented to provide a seamless remote access experience on Android:

1. **Auto-Start Service**: Automatically starts the RustDesk service when the app launches
2. **Auto-Accept Connections**: Automatically accepts incoming connections without requiring manual confirmation

## Features Implemented

### 1. Auto-Start Service

**What it does**: When enabled, the RustDesk service starts automatically when you open the app, without showing any permission dialogs.

**Location in app**: 
- Open RustDesk app → "Share screen" tab
- Scroll down to "Permissions" section
- You'll see a new toggle: "Auto-start service on app launch"

**How to enable**:
1. Open the RustDesk Android app
2. Go to the "Share screen" tab
3. In the Permissions section, toggle "Auto-start service on app launch" to ON
4. Now, whenever you open the app, the service will start automatically

**What happens when enabled**:
- The service starts in the background without showing dialogs
- Screen capture will begin automatically
- The app is ready to accept connections immediately

### 2. Auto-Accept Connections

**What it does**: Automatically accepts incoming connections from other devices without showing a confirmation dialog or requiring manual approval.

**Location in app**:
- Open RustDesk app → "Share screen" tab
- Tap the menu button (⋮) at the top right
- Look for options like "Accept sessions via..." 
- Select "Auto-accept connections (no confirmation)"

**How to enable**:
1. Open the RustDesk Android app
2. Go to the "Share screen" tab
3. Tap the menu (⋮) button at the top right
4. Select "Auto-accept connections (no confirmation)"
5. Incoming connections will now be automatically accepted

**Connection acceptance modes available**:
- **Accept sessions via password**: Requires the remote user to enter the password
- **Accept sessions via click**: Requires you to manually click "Accept" on your phone
- **Accept sessions via both**: Requires both password and manual click
- **Auto-accept connections (no confirmation)**: ✨ *New* - Accepts automatically without any action required

## Code Changes Summary

### Android Kotlin Files

#### 1. `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/common.kt`
- Added new Flutter channel constants:
  - `GET_AUTO_START_SERVICE`: Check if auto-start is enabled
  - `SET_AUTO_START_SERVICE`: Enable/disable auto-start
  - `GET_AUTO_ACCEPT_CONNECTIONS`: Check if auto-accept is enabled
  - `SET_AUTO_ACCEPT_CONNECTIONS`: Enable/disable auto-accept
- Added new SharedPreferences keys:
  - `KEY_AUTO_START_SERVICE`
  - `KEY_AUTO_ACCEPT_CONNECTIONS`

#### 2. `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainActivity.kt`
- Modified `configureFlutterEngine()` to call auto-start initialization
- Added `autoStartService()` method that:
  - Checks if auto-start is enabled in SharedPreferences
  - Starts the MainService without showing permission dialogs
- Added method call handlers for the new Flutter channel methods:
  - `GET_AUTO_START_SERVICE`
  - `SET_AUTO_START_SERVICE`
  - `GET_AUTO_ACCEPT_CONNECTIONS`
  - `SET_AUTO_ACCEPT_CONNECTIONS`

### Flutter Dart Files

#### 1. `flutter/lib/consts.dart`
- Added new option constants:
  - `kOptionAutoStartService = "auto-start-service"`
  - `kOptionAutoAcceptConnections = "auto-accept-connections"`

#### 2. `flutter/lib/models/server_model.dart`
- Added `initAutoAcceptConnections()` method:
  - Checks if auto-accept is enabled
  - Sets approve mode to empty string (no confirmation needed)
- Added `initAutoStartService()` method:
  - Calls `startService()` if auto-start is enabled

#### 3. `flutter/lib/main.dart`
- Modified `runMobileApp()` function to:
  - Call `gFFI.serverModel.initAutoStartService()`
  - Call `gFFI.serverModel.initAutoAcceptConnections()`
  - These methods run automatically when the app starts on Android

#### 4. `flutter/lib/mobile/pages/server_page.dart`
- Added "Auto-accept connections (no confirmation)" menu option
- Added handler for this new option
- Added new `AutoStartServiceOption` widget:
  - Displays a toggle switch for auto-start service
  - Loads current setting from SharedPreferences
  - Saves setting when toggled
- Added divider to separate settings

## How It Works

### Auto-Start Service Flow

```
App Launch
    ↓
runMobileApp() called
    ↓
initAutoStartService() checks if enabled
    ↓
If enabled: startService() called automatically
    ↓
Service starts without showing dialogs
    ↓
App ready for connections
```

### Auto-Accept Connections Flow

```
App Launch
    ↓
runMobileApp() called
    ↓
initAutoAcceptConnections() checks if enabled
    ↓
If enabled: setApproveMode('') called
    ↓
Incoming connection received
    ↓
Connection automatically accepted (no user confirmation needed)
```

## Important Notes

1. **Permissions**: The Android app must have the necessary permissions granted:
   - SYSTEM_ALERT_WINDOW
   - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
   - RECORD_AUDIO (for audio)
   - MANAGE_EXTERNAL_STORAGE (for file transfer)

2. **Battery Optimization**: When auto-start is enabled, ensure RustDesk is exempted from battery optimization to prevent the system from killing the service.

3. **Security Consideration**: Using auto-accept connections means any device can connect to your phone without confirmation. Only enable this on trusted networks or if you understand the security implications.

4. **Service vs. Floating Window**: Auto-start only starts the background service. The app UI will still appear as normal. To minimize the app to the system tray, use the floating window service.

## Settings Persistence

- Settings are saved in Android SharedPreferences
- Setting key: `KEY_SHARED_PREFERENCES` (package name)
- Auto-start status: `KEY_AUTO_START_SERVICE`
- Auto-accept status: `KEY_AUTO_ACCEPT_CONNECTIONS`
- These settings persist across app restarts

## Troubleshooting

### Auto-start isn't working
1. Verify the toggle is enabled in the Permissions section
2. Check that necessary permissions are granted
3. Check Android battery optimization settings - RustDesk should be exempted
4. Try restarting the app

### Auto-accept isn't working
1. Verify "Auto-accept connections (no confirmation)" is selected in the menu
2. Ensure you've selected it from the menu dropdown, not other options like "Accept sessions via password"
3. Try toggling it off and back on
4. Force close the app and reopen it

### Service stops after a while
- This is likely due to Android battery optimization
- Go to `Settings → Apps & notifications → [permissions/battery] → RustDesk`
- Exempt RustDesk from battery optimization

## Build Instructions

To build the modified APK:

```bash
# From the flutter directory
flutter build android

# Or for release
flutter build android --release
```

The changes are backward compatible with existing installations and won't affect users who don't enable these features.

## Support

If you experience issues:
1. Check the logcat output: `adb logcat | grep -i rustdesk`
2. Verify permissions in Android Settings
3. Ensure your Android version is compatible
