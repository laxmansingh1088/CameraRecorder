package com.example.camerarecorder.newexample

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service.START_NOT_STICKY
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture.withOutput
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.camerarecorder.MainActivity
import com.example.camerarecorder.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class MediaRecordingService : LifecycleService() {
/*

    companion object {
        const val CHANNEL_ID: String = "media_recorder_service"
        private val TAG = MediaRecordingService::class.simpleName
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val CHANNEL_NAME: String = "Media recording service"
        const val ONGOING_NOTIFICATION_ID: Int = 2345
        const val ACTION_START_WITH_PREVIEW: String = "start_recording"
        const val BIND_USECASE: String = "bind_usecase"
    }

    enum class RecordingState {
        RECORDING, PAUSED, STOPPED
    }

    class RecordingServiceBinder(private val service: MediaRecordingService) : Binder() {
        fun getService(): MediaRecordingService {
            return service
        }
    }
    private var preview: Preview? = null
    private lateinit var timer: Timer
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var recordingServiceBinder: RecordingServiceBinder
    private var activeRecording: ActiveRecording? = null
    private var videoCapture: androidx.camera.video.VideoCapture<Recorder>? = null
    private val listeners = HashSet<DataListener>(1)
    private val pendingActions: HashMap<String, Runnable> = hashMapOf()
    private var recordingState: RecordingState = RecordingState.STOPPED
    private var duration: Int = 0
    private var timerTask: TimerTask? = null
    private var isSoundEnabled: Boolean = true

    override fun onCreate() {
        super.onCreate()
        recordingServiceBinder = RecordingServiceBinder(this)
        timer = Timer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when(intent?.action) {
            ACTION_START_WITH_PREVIEW -> {
                if (cameraProvider == null) {
                    initializeCamera()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()
            val qualitySelector = getQualitySelector()
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = withOutput(recorder)
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()
                // Bind use cases to camera
                cameraProvider?.bindToLifecycle(this, cameraSelector, videoCapture)
            } catch(exc: Exception) {
                Log.e(MediaRecordingService::class.simpleName, "Use case binding failed", exc)
            }
            val action = pendingActions[BIND_USECASE]
            action?.run()
            pendingActions.remove(BIND_USECASE)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getQualitySelector(): QualitySelector {
        return QualitySelector
            .firstTry(QualitySelector.QUALITY_UHD)
            .thenTry(QualitySelector.QUALITY_FHD)
            .thenTry(QualitySelector.QUALITY_HD)
            .finallyTry(
                QualitySelector.QUALITY_SD,
                QualitySelector.FALLBACK_STRATEGY_LOWER
            )
    }

    fun startRecording() {
        val mediaStoreOutputOptions = createMediaStoreOutputOptions()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        var pendingRecording = videoCapture?.output?.prepareRecording(this, mediaStoreOutputOptions)
        if (isSoundEnabled) {
            pendingRecording = pendingRecording?.withAudioEnabled()
        }
        activeRecording = pendingRecording?.withEventListener(
            ContextCompat.getMainExecutor(this),
            {
                when (it) {
                    is VideoRecordEvent.Start -> {
                        startTrackingTime()
                        recordingState = RecordingState.RECORDING
                    }

                    is VideoRecordEvent.Finalize -> {
                        recordingState = RecordingState.STOPPED
                        duration = 0
                        timerTask?.cancel()
                    }
                }
                for (listener in listeners) {
                    listener.onRecordingEvent(it)
                }
            })
            ?.start()
        recordingState = RecordingState.RECORDING
    }

    private fun startTrackingTime() {
        timerTask = object: TimerTask() {
            override fun run() {
                if (recordingState == RecordingState.RECORDING) {
                    duration += 1
                    for (listener in listeners) {
                        listener.onNewData(duration)
                    }
                }
            }
        }
        timer.scheduleAtFixedRate(timerTask, 1000, 1000)
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun createMediaStoreOutputOptions(): MediaStoreOutputOptions {
        val name = "CameraX-recording-" +
                SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
                    .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Recorded Videos")
            }
        }
        return MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()
    }

    fun bindPreviewUseCase(surfaceProvider: Preview.SurfaceProvider?) {
        activeRecording?.pause()
        if (cameraProvider != null) {
            bindInternal(surfaceProvider)
        } else {
            pendingActions[BIND_USECASE] = Runnable {
                bindInternal(surfaceProvider)
            }
        }
    }

    private fun bindInternal(surfaceProvider: Preview.SurfaceProvider?) {
        if (preview != null) {
            cameraProvider?.unbind(preview)
        }
        initPreviewUseCase()
        preview?.setSurfaceProvider(surfaceProvider)
        val cameraInfo: CameraInfo? = cameraProvider?.bindToLifecycle(
            this@MediaRecordingService,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview
        )?.cameraInfo
        observeCameraState(cameraInfo, this)
    }

    private fun initPreviewUseCase() {
        preview?.setSurfaceProvider(null)
        preview = Preview.Builder()
            .build()
    }

    fun unbindPreview() {
        // Just remove the surface provider. I discovered that for some reason if you unbind the Preview usecase the camera willl stop recording the video.
        preview?.setSurfaceProvider(null)
    }

    fun startRunningInForeground() {
        val parentStack = TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(Intent(this, MainActivity::class.java))

        val pendingIntent1 = parentStack.getPendingIntent(0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video")
            .setContentText("Video Recording")
            .setSmallIcon(R.drawable.ic_pause)
            .setContentIntent(pendingIntent1)
            .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    fun isSoundEnabled(): Boolean {
        return isSoundEnabled
    }

    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
    }

    // Stop recording and remove SurfaceView
    override fun onDestroy() {
        super.onDestroy()
        activeRecording?.stop()
        timerTask?.cancel()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return recordingServiceBinder
    }

    fun addListener(listener: DataListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DataListener) {
        listeners.remove(listener)
    }

    fun getRecordingState(): RecordingState {
        return recordingState
    }

    private fun observeCameraState(cameraInfo: androidx.camera.core.CameraInfo?, context: Context) {
        cameraInfo?.cameraState?.observe(this) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                    }
                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        for (listener in listeners) {
                            listener.onCameraOpened()
                        }
                    }
                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                    }
                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                    }
                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                    }
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        Toast.makeText(context,
                            "Stream config error. Restart application",
                            Toast.LENGTH_SHORT).show()
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        Toast.makeText(context,
                            "Camera in use. Close any apps that are using the camera",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {

                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        Toast.makeText(context,
                            "Camera disabled",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        Toast.makeText(context,
                            "Fatal error",
                            Toast.LENGTH_SHORT).show()
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        Toast.makeText(context,
                            "Do not disturb mode enabled",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    interface DataListener {
        fun onNewData(duration: Int)
        fun onCameraOpened()
        fun onRecordingEvent(it: VideoRecordEvent?)
    }
*/

}