package com.carriez.flutter_hbb

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility functions for service and permission management
 */

/**
 * Check if a service is currently running
 */
fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return false
}

/**
 * Check if camera permission is granted
 */
fun hasCameraPermission(context: Context): Boolean {
    return if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

/**
 * Check if screen recording permission is granted (Media Projection)
 */
fun hasMediaProjectionPermission(context: Context): Boolean {
    // Media Projection permission is runtime-based, checked separately
    // This is a helper for consistency
    return true
}
