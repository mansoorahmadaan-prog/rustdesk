// CAMERA SHARE UI - PRACTICAL CODE EXAMPLES
// Add these code snippets to the locations shown

// ============================================================
// 1. In server_model.dart - Add methods to ServerModel class
// ============================================================

  /// Request camera view from the device
  Future<void> requestCameraView(Client client) async {
    if (!isAndroid) {
      debugPrint("Camera share is only available on Android");
      return;
    }
    
    try {
      Log.d("ServerModel", "Requesting camera view from client: ${client.name}");
      
      // Call Rust backend to set camera mode for this client
      parent.target?.invokeMethod("request_camera_share", {
        "client_id": client.id,
        "peer_id": client.peerId,
        "name": client.name,
      });
      
    } catch (e) {
      debugPrint("Failed to request camera view: $e");
    }
  }

  /// Stop camera share for a client
  Future<void> stopCameraView(Client client) async {
    try {
      Log.d("ServerModel", "Stopping camera view for client: ${client.name}");
      
      // Close the connection which will stop camera capture
      await bind.cmCloseConnection(connId: client.id);
      
    } catch (e) {
      debugPrint("Failed to stop camera view: $e");
    }
  }

  /// Check if camera is currently being shared
  bool isCameraSharing(Client client) {
    return client.isViewCamera;
  }

// ============================================================
// 2. In server_page.dart - Add button in client menu
// ============================================================

// MOBILE VERSION - Add to PopupMenu in client list:

PopupMenuButton<String>(
  itemBuilder: (BuildContext context) => <PopupMenuEntry<String>>[
    PopupMenuItem<String>(
      value: 'accept',
      child: Row(
        children: [
          Icon(Icons.check),
          SizedBox(width: 10),
          Text(translate('Accept')),
        ],
      ),
    ),
    PopupMenuItem<String>(
      value: 'deny',
      child: Row(
        children: [
          Icon(Icons.close),
          SizedBox(width: 10),
          Text(translate('Deny')),
        ],
      ),
    ),
    PopupMenuDivider(),
    // ADD CAMERA SHARE OPTION HERE:
    PopupMenuItem<String>(
      value: 'camera_view',
      child: Row(
        children: [
          Icon(Icons.videocam),
          SizedBox(width: 10),
          Text(translate('Share Camera')),  // Or "View Camera"
        ],
      ),
    ),
    PopupMenuDivider(),
    PopupMenuItem<String>(
      value: 'block',
      child: Row(
        children: [
          Icon(Icons.block),
          SizedBox(width: 10),
          Text(translate('Block')),
        ],
      ),
    ),
  ],
  onSelected: (String value) async {
    final model = gFFI.serverModel;
    switch (value) {
      case 'accept':
        model.sendLoginResponse(client, true);
        break;
      case 'deny':
        model.sendLoginResponse(client, false);
        break;
      case 'camera_view':
        // CAMERA SHARE TRIGGERED HERE
        await model.requestCameraView(client);
        break;
      case 'block':
        model.sendLoginResponse(client, false);
        break;
    }
  },
)

// ============================================================
// 3. In MainActivity.kt - Add method channel handler
// ============================================================

"request_camera_share" -> {
    Log.d(logTag, "Received request_camera_share method call")
    val clientId = call.argument<Int>("client_id") ?: -1
    val peerId = call.argument<String>("peer_id") ?: ""
    val name = call.argument<String>("name") ?: "Unknown"
    
    Log.d(logTag, "Camera share requested: client=$clientId, peer=$peerId, name=$name")
    
    mainService?.let {
        // Start camera capture for this client
        it.startCameraCapture()
        result.success(true)
    } ?: let {
        Log.w(logTag, "MainService not available")
        result.success(false)
    }
}

// ============================================================
// 4. In server_page.dart - Alternative: Add FAB
// ============================================================

floatingActionButton: FloatingActionButton.extended(
  onPressed: () async {
    final client = gFFI.serverModel.clients.firstOrNull;
    if (client != null) {
      await gFFI.serverModel.requestCameraView(client);
    }
  },
  icon: Icon(Icons.videocam),
  label: Text('Share Camera'),
)

// ============================================================
// 5. In server_page.dart - Alternative: Add to Settings
// ============================================================

ListTile(
  leading: Icon(Icons.videocam),
  title: Text('Camera Sharing'),
  subtitle: Text('Share device camera with connected clients'),
  trailing: Switch(
    value: isCameraSharingEnabled,
    onChanged: (bool value) async {
      if (value) {
        final client = gFFI.serverModel.clients.firstOrNull;
        if (client != null) {
          await gFFI.serverModel.requestCameraView(client);
        }
      } else {
        final client = gFFI.serverModel.clients.firstOrNull;
        if (client != null) {
          await gFFI.serverModel.stopCameraView(client);
        }
      }
    },
  ),
)

// ============================================================
// 6. Optional: Add to Connection Request Dialog
// ============================================================

// In server_model.dart showLoginDialog():

void showLoginDialog(Client client) {
  String title;
  String message;
  String? cameraOption;
  
  if (client.isFileTransfer) {
    title = translate('Transfer file');
    message = translate('android_new_connection_tip');
  } else if (client.isViewCamera) {
    title = translate('View camera');
    message = translate('Client wants to view your camera');
  } else if (client.isTerminal) {
    title = translate('Terminal');
    message = translate('android_new_connection_tip');
  } else {
    title = translate('Share screen');
    message = translate('android_new_connection_tip');
    // Add extra button for quick camera share
    cameraOption = translate('Share Camera Instead');
  }
  
  showClientDialog(
    client,
    title,
    message,
    'android_new_connection_tip',
    () => sendLoginResponse(client, false),
    () => sendLoginResponse(client, true),
    // Optional third button for camera share:
    cameraOption != null ? () => requestCameraView(client) : null,
  );
}

// ============================================================
// Usage Flow:
// ============================================================
/*
1. User opens server app on Android device
2. Remote client connects and sees "Share Camera" option
3. User taps "Share Camera" button
4. requestCameraView(client) is called
5. MainActivity receives "request_camera_share" method call
6. MainService.startCameraCapture() starts
7. Camera frames sent to Rust backend via FFI
8. Rust marks connection as is_view_camera=true
9. Remote client receives camera feed
   
To stop:
1. User taps "Stop Camera" or connection closes
2. stopCameraView(client) is called
3. MainService.stopCameraCapture() is called
4. Camera resources released
*/
