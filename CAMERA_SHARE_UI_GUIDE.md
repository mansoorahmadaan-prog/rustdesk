# Camera Share UI Implementation Guide

## Overview
To enable clients to request camera view from the server's device, add UI elements in the following locations:

---

## 1. Mobile Server Page (Flutter UI)
**File**: `flutter/lib/mobile/pages/server_page.dart`

### Location A: Client Action Menu (Best for individual client options)
```dart
// Around line 700-800 where client action buttons are displayed
// Add a new action button for camera share:

PopupMenuItem(
  value: 'request_camera_view',
  child: Row(
    children: [
      Icons.videocam,
      SizedBox(width: 10),
      Text('Request Camera View'),
    ],
  ),
),

// In the onSelected handler, add:
if (value == 'request_camera_view') {
  _requestCameraView(selectedClient);
}
```

### Location B: Client List Item Context Menu
```dart
// In the ListTile or Card that displays each client:
// Add trailing buttons or long-press menu with camera option

onLongPress: () {
  _showCameraShareOptions(client);
},

// Or add action buttons directly:
trailing: Row(
  mainAxisSize: MainAxisSize.min,
  children: [
    IconButton(
      icon: Icon(Icons.videocam),
      onPressed: () => _requestCameraView(client),
      tooltip: 'Request Camera View',
    ),
    IconButton(
      icon: Icon(Icons.close),
      onPressed: () => _blockClient(client),
    ),
  ],
),
```

---

## 2. Server Model (Dart Logic)
**File**: `flutter/lib/models/server_model.dart`

### Add these methods to ServerModel class:

```dart
/// Request camera view from a specific client/connection
Future<void> requestCameraView(Client client) async {
  try {
    Log.d("Requesting camera view from client: ${client.name}");
    
    // Method 1: Use existing bind method (if available)
    await bind.cmRequestView(
      connId: client.id,
      viewType: 'camera',  // or 1 for camera
    );
    
    // Method 2: Call Rust backend directly
    parent.target?.invokeMethod("request_camera_share", {
      "client_id": client.id,
      "peer_id": client.peerId,
    });
    
  } catch (e) {
    debugPrint("Failed to request camera view: $e");
  }
}

/// Stop camera share for a client
Future<void> stopCameraShare(Client client) async {
  try {
    Log.d("Stopping camera share for client: ${client.name}");
    
    await bind.cmCloseConnection(connId: client.id);
    
  } catch (e) {
    debugPrint("Failed to stop camera share: $e");
  }
}

/// Get camera share status
bool isCameraShareActive(Client client) {
  return client.isViewCamera;
}

/// Check if camera sharing is available
bool isCameraShareAvailable() {
  if (!isAndroid) return false; // Camera share is Android-specific
  return hasCameraPermission();
}
```

---

## 3. Client Dialog/Login Response
**File**: `flutter/lib/models/server_model.dart` (around line 770)

### Show camera share option in login dialog:

```dart
void showLoginDialog(Client client) {
  String dialogTitle = client.isFileTransfer
      ? "Transfer file"
      : client.isViewCamera
          ? "View camera"      // ← Already supports this!
          : client.isTerminal
              ? "Terminal"
              : "Share screen";
  
  String dialogMessage = client.isViewCamera
      ? "Client wants to view your camera"
      : "Client wants to share your screen";
  
  showClientDialog(
    client,
    dialogTitle,
    dialogMessage,
    'android_new_connection_tip',
    () => sendLoginResponse(client, false),  // Deny
    () => sendLoginResponse(client, true),   // Accept
  );
}
```

---

## 4. Main Server Page (Desktop/Web UI)
**File**: `flutter/lib/desktop/pages/server_page.dart`

### Add camera share button in control panel:

```dart
// In the control panel where screen share is controlled, add:

Row(
  children: [
    ElevatedButton.icon(
      icon: Icon(Icons.videocam),
      label: Text('Start Screen Share'),
      onPressed: () => _startScreenShare(selectedClient),
    ),
    SizedBox(width: 10),
    ElevatedButton.icon(
      icon: Icon(Icons.videocam_off),
      label: Text('Start Camera Share'),
      onPressed: () => _startCameraShare(selectedClient),
    ),
  ],
)
```

---

## 5. Floating Action Button (Quick Action)
**File**: `flutter/lib/mobile/pages/server_page.dart`

### Add FAB for camera settings:

```dart
floatingActionButton: FloatingActionButton(
  onPressed: () => _showCameraShareDialog(),
  child: Icon(Icons.videocam),
  tooltip: 'Camera Share Settings',
),
```

---

## Complete Implementation Example

### In server_model.dart, add this helper method:

```dart
Future<void> requestCameraView(Client client) async {
  if (!isAndroid) {
    debugPrint("Camera share is only available on Android");
    return;
  }
  
  if (!hasCameraPermission()) {
    debugPrint("Camera permission not granted");
    // Could request permission here
    return;
  }
  
  try {
    // Send message to Rust backend to initiate camera view mode
    parent.target?.invokeMethod("request_camera_share", {
      "client_id": client.id,
      "peer_id": client.peerId,
      "name": client.name,
    });
    
    // Update local UI
    client.isViewCamera = true;
    notifyListeners();
    
  } catch (e) {
    debugPrint("Error requesting camera view: $e");
  }
}
```

### In server page widget, call it:

```dart
PopupMenuButton<String>(
  itemBuilder: (context) => [
    PopupMenuItem(
      value: 'screen_share',
      child: Text('Share Screen'),
    ),
    PopupMenuItem(
      value: 'camera_view',
      child: Text('Share Camera'),  // ← New option
    ),
    PopupMenuItem(
      value: 'block',
      child: Text('Block Connection'),
    ),
  ],
  onSelected: (value) async {
    switch (value) {
      case 'screen_share':
        // Already implemented
        break;
      case 'camera_view':
        await gFFI.serverModel.requestCameraView(client);
        break;
      case 'block':
        await _blockClient(client);
        break;
    }
  },
)
```

---

## Android Kotlin Backend Integration

### In MainActivity.kt, handle the request:

```kotlin
"request_camera_share" -> {
    val clientId = call.argument<Int>("client_id") ?: -1
    val peerId = call.argument<String>("peer_id") ?: ""
    
    Log.d(logTag, "Requesting camera share for client $clientId")
    
    mainService?.let {
        it.startCameraCapture()  // Start camera immediately
        result.success(true)
    } ?: result.success(false)
}
```

### In MainService.kt:

```kotlin
// Camera starts automatically when client requests it
// Already implemented in add_connection handler with is_view_camera flag
```

---

## User Flow

```
User selects "Share Camera" from UI
    ↓
server_model.requestCameraView(client)
    ↓
MainActivity.kt receives "request_camera_share"
    ↓
MainService.kt startCameraCapture()
    ↓
Camera frames sent to Rust backend
    ↓
Rust sends is_view_camera=true to Android
    ↓
Remote client receives camera video stream
```

---

## Summary of Changes Needed

| File | Location | Action |
|------|----------|--------|
| server_page.dart | Client menu | Add "Request Camera View" button |
| server_model.dart | ServerModel class | Add `requestCameraView()` method |
| MainActivity.kt | Method channel | Add "request_camera_share" handler |
| server_page.dart | Dialog | Display "View camera" in login dialog |

---

## Notes

- ✅ Backend camera capture: Already implemented
- ✅ Android service integration: Already implemented  
- ✅ FFI bridge to Rust: Already implemented
- ⏳ **TODO**: Add UI buttons to trigger camera share from user interface

The architecture is ready! You just need to add UI elements to trigger the camera share flow.
