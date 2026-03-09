# RustDesk Android Debug Guide

## Overview
Comprehensive logging has been added to help debug screen sharing issues when the app is swiped from recent apps.

## What's Been Added

### 1. Debug Log File
- **Location**: `/sdcard/Android/data/com.carriez.flutter_hbb/files/rustdesk_debug.log`
- **Content**: Timestamped debug messages from both MainService and PermissionRequestTransparentActivity
- **How to retrieve**:
  ```bash
  adb pull /sdcard/Android/data/com.carriez.flutter_hbb/files/rustdesk_debug.log
  ```

### 2. Toast Messages
Visual notifications will appear on the device screen at key steps:
- "RustDesk Service Starting" - When service starts
- "Connection from [username] - mediaProj: [true/false]" - When connection arrives
- "Capture started for [username]" or "Capture failed..." - Capture attempt result
- "Media projection null, requesting..." - Permission request starting
- "Permission approved!", "Sending projection to service..." - Permission flow

### 3. Logcat Output
- **Tag**: `LOG_SERVICE` (MainService logs)
- **Tag**: `permissionRequest` (Permission activity logs)
- **How to view**:
  ```bash
  adb logcat LOG_SERVICE:V permissionRequest:V *:S
  ```

## Expected Debug Flow (When Working)

1. **Connection Received**
   ```
   [HH:MM:SS.SSS] [CONN_RECEIVED] Connection from username (id=X) - mediaProjection: true, isStart: false
   [HH:MM:SS.SSS] [CAPTURE_START_ATTEMPT] Calling startCapture() for username
   [HH:MM:SS.SSS] [CAPTURE] startCapture() called - isStart: false, mediaProj: true
   [HH:MM:SS.SSS] [CAPTURE] mediaProjection available, starting capture
   [HH:MM:SS.SSS] [CAPTURE] Creating surface...
   [HH:MM:SS.SSS] [CAPTURE] Surface created successfully
   [HH:MM:SS.SSS] [CAPTURE] Starting raw video recorder
   [HH:MM:SS.SSS] [CAPTURE] Creating audio recorder...
   [HH:MM:SS.SSS] [CAPTURE_SUCCESS] Screen capture started successfully
   ```

## Expected Debug Flow (When Permission Needed)

1. **Connection Without Permission**
   ```
   [HH:MM:SS.SSS] [CONN_RECEIVED] Connection from username - mediaProjection: false, isStart: false
   [HH:MM:SS.SSS] [CAPTURE_START_ATTEMPT] Calling startCapture()
   [HH:MM:SS.SSS] [CAPTURE] mediaProjection is NULL - requesting it
   [HH:MM:SS.SSS] [REQUEST_MEDIA_PROJ] Starting PermissionRequestTransparentActivity
   [HH:MM:SS.SSS] [REQUEST_MEDIA_PROJ] Activity started successfully
   ```

2. **Permission Activity Flow**
   ```
   [HH:MM:SS.SSS] PERM_ACTIVITY: onCreate - action: com.carriez.flutter_hbb.ACT_REQUEST_MEDIA_PROJECTION
   [HH:MM:SS.SSS] PERM_ACTIVITY: Showing screen capture permission dialog
   [HH:MM:SS.SSS] PERM_ACTIVITY: Starting activity for result
   [HH:MM:SS.SSS] PERM_ACTIVITY: onActivityResult - requestCode=1001, resultCode=1, data=true
   [HH:MM:SS.SSS] PERM_ACTIVITY: User approved screen capture permission
   [HH:MM:SS.SSS] PERM_ACTIVITY: launchService called
   [HH:MM:SS.SSS] PERM_ACTIVITY: Attempting to bind to service...
   [HH:MM:SS.SSS] PERM_ACTIVITY: bindService called successfully
   [HH:MM:SS.SSS] PERM_ACTIVITY: Starting service...
   [HH:MM:SS.SSS] PERM_ACTIVITY: startForegroundService called
   ```

3. **Service Receives Projection**
   ```
   [HH:MM:SS.SSS] PERM_ACTIVITY: onServiceConnected - service available
   [HH:MM:SS.SSS] PERM_ACTIVITY: Got service instance from binder
   [HH:MM:SS.SSS] PERM_ACTIVITY: Calling setMediaProjection on service...
   [HH:MM:SS.SSS] [SET_MEDIA_PROJ] setMediaProjection called from activity
   [HH:MM:SS.SSS] [SET_MEDIA_PROJ] Media projection set successfully, isReady=true
   [HH:MM:SS.SSS] [SET_MEDIA_PROJ] Attempting to start capture after receiving projection
   [HH:MM:SS.SSS] [CAPTURE] startCapture() called - isStart: false, mediaProj: true
   [HH:MM:SS.SSS] [CAPTURE_SUCCESS] Screen capture started successfully
   ```

## Common Issues and What to Look For

### Issue 1: "Notification received but no screen sharing"

**What to check in logs:**
1. Is `[CAPTURE_START_RESULT]` returning false?
   - If yes, look for the error message following it
   
2. Look for `[CAPTURE_ERROR]` messages - they indicate what went wrong

3. Check if `[SET_MEDIA_PROJ]` appears in the logs
   - If not: Permission activity didn't reach the service
   - Check: `PERM_ACTIVITY: onServiceConnected` should appear

### Issue 2: "Capture fails after app swiped"

**What to check:**
1. After app is swiped, look for `[ONSTARTCOMMAND] ACT_INIT_MEDIA_PROJECTION_AND_SERVICE received`
2. Check if `[ONSTARTCOMMAND] Media projection intent received` or `[ONSTARTCOMMAND] Media projection intent is NULL`
3. If intent is NULL, this is the issue - service started without projection

### Issue 3: "Permission dialog doesn't appear"

**What to check:**
1. Look for `[REQUEST_MEDIA_PROJ] Starting PermissionRequestTransparentActivity`
2. Check if `PERM_ACTIVITY: onCreate` appears after that
3. If onCreate doesn't appear, the activity failed to start
4. Look for `[REQUEST_MEDIA_PROJ_ERROR]` - this will show the error

### Issue 4: "Service doesn't receive projection"

**What to check:**
1. Look for `PERM_ACTIVITY: onServiceConnected - service available`
2. Check if `[SET_MEDIA_PROJ] setMediaProjection called from activity` appears
3. If `onServiceConnected` appears but `setMediaProjection called` doesn't, check for errors
4. Look for `PERM_ACTIVITY: Error in onServiceConnected` - this will show what failed

## How to Retrieve Logs

### Via ADB
```bash
# Pull debug log file
adb pull /sdcard/Android/data/com.carriez.flutter_hbb/files/rustdesk_debug.log ./rustdesk_debug.log

# View recent logcat
adb logcat -d LOG_SERVICE:V permissionRequest:V *:S > logcat_dump.txt
```

### Via Android Studio
1. Open Android Studio
2. Click `Logcat` at the bottom
3. Filter by tag: `LOG_SERVICE` or `permissionRequest`
4. Reproduce the issue and watch the logs in real-time

## Clearing Logs

To start fresh:
```bash
# Remove old debug file
adb shell rm /sdcard/Android/data/com.carriez.flutter_hbb/files/rustdesk_debug.log

# Clear logcat
adb logcat -c
```

## Testing Procedure

1. **Clear logs**: Remove old debug files and clear logcat
2. **Uninstall app**: `adb uninstall com.carriez.flutter_hbb`
3. **Install APK**: `adb install app-release.apk`
4. **Start watching logs**: `adb logcat -f logcat.log LOG_SERVICE:V permissionRequest:V *:S`
5. **Reproduce issue**:
   - Connect from remote device
   - Watch for toast messages on Android device
   - Check if screen sharing starts
6. **Pull debug files**:
   - Pull log file for persistent debug output
   - Save logcat for complete history

## Expected Toast Sequence

### Successful Flow (With Permission):
1. "RustDesk Service Starting"
2. "Connection from [username] - mediaProj: true"
3. "Capture started for [username]"

### Successful Flow (First Time - Needs Permission):
1. "RustDesk Service Starting"
2. "Connection from [username] - mediaProj: false"
3. "Capture failed - requesting permission..."
4. "Permission Activity Started"
5. "Requesting permission..."
6. "Permission result: code=1"
7. "Permission approved!"
8. "Connecting to service..."
9. "Service connected!"
10. "Sending projection to service..."
11. "Projection sent!"
12. (Should see "Capture started" in logs)

## Debug Key Points

Mark these patterns in the logs to understand the flow:
- `[CONN_RECEIVED]` - Remote connection arrived
- `[CAPTURE_START_ATTEMPT]` - startCapture() was called
- `[CAPTURE_START_RESULT]` - Result of startCapture()
- `[REQUEST_MEDIA_PROJ]` - Permission request started
- `[SET_MEDIA_PROJ]` - Service received projection
- `[CAPTURE_SUCCESS]` or `[CAPTURE_ERROR]` - Final capture result

## Notes

- Timestamps are in HH:MM:SS.SSS format for easy correlation with other events
- Toast messages appear for major state changes - watch the screen during testing
- The activity prefix "PERM_ACTIVITY:" distinguishes permission activity logs from service logs
- Debug file grows over time - clear periodically during development
