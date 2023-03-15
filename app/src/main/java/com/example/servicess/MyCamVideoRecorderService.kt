package com.example.servicess

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.ViewGroup.LayoutParams
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.concurrent.futures.await
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.camerarecorder.MainActivity
import com.example.camerarecorder.R
import com.example.camerarecorder.broadcasts.NotificationReceiver
import com.example.camerarecorder.broadcasts.NotificationReceiver.Companion.ACTION_RESUME
import com.example.camerarecorder.broadcasts.NotificationReceiver.Companion.ACTION_START
import com.example.camerarecorder.broadcasts.NotificationReceiver.Companion.ACTION_STOP
import com.example.camerarecorder.extensions.getNameString
import com.example.camerarecorder.fragments.CaptureFragment
import com.example.camerarecorder.utils.FileHelper.getResourcesDirectoryPath
import com.example.camerarecorder.utils.SharedPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class MyCamVideoRecorderService : LifecycleService() {


    private var savedPath: String = ""
    private var floatView: ViewGroup? = null
    private var LAYOUT_TYPE = 0
    private var floatWindowLayoutParam: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var tvStatus: TextView? = null
    private var btnClose: ImageView? = null
    private var btnStop: ImageButton? = null
    private var btnStart: ImageButton? = null
    private var btnResume: ImageButton? = null


    companion object {
        // default Quality selection if no input from UI
        const val DEFAULT_QUALITY_IDX = 0
        val TAG: String = MyCamVideoRecorderService::class.java.simpleName
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private const val CHANNEL_NAME = "video_record_service"
        private const val CHANNEL_DESCRIPTION = "recording video in background"
        private const val CHANNEL_ID = "VIDEO_RECORD"
    }

    private var audioEnabled = true
    private var currentRecording: Recording? = null
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var recordingState: VideoRecordEvent


    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(this) }
    var builder: NotificationCompat.Builder? = null

    private lateinit var messenger: Messenger
    private val handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when(msg.what) {
                // Handle message from the Activity
                // Perform any action based on the message
            }
        }
    }
    private val messengerCallback = Messenger(handler)


    /*  override fun onBind(intent: Intent): IBinder? {
          super.onBind(intent)
          messenger = Messenger(messengerCallback)
          return messenger.binder

      }*/


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start foreground service to avoid unexpected kill
        startForeground(1234, getNotification())

        audioEnabled = SharedPref(this).getBoolean(SharedPref.AUDIO_ENABLED, false) == true
        lifecycleScope.async {
            initializeCamera()
            withContext(Dispatchers.Default) {
                startRecording()
            }
            /* delay(12000)

             if(currentRecording == null || recordingState is VideoRecordEvent.Finalize) {

             }

             val recording = currentRecording
             if(recording != null) {
                 recording.stop()
                 currentRecording = null
             }
             stopForeground(STOP_FOREGROUND_REMOVE)*/
        }
        showFloatingWindow()
    }


    fun showFloatingWindow() {
        // The screen height and width are calculated, cause
        // the height and width of the floating window is set depending on this
        // The screen height and width are calculated, cause
        // the height and width of the floating window is set depending on this
        val metrics = applicationContext.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // To obtain a WindowManager of a different Display,
        // we need a Context for that display, so WINDOW_SERVICE is used

        // To obtain a WindowManager of a different Display,
        // we need a Context for that display, so WINDOW_SERVICE is used
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // A LayoutInflater instance is created to retrieve the
        // LayoutInflater for the floating_layout xml

        // A LayoutInflater instance is created to retrieve the
        // LayoutInflater for the floating_layout xml
        val inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // inflate a new view hierarchy from the floating_layout xml

        // inflate a new view hierarchy from the floating_layout xml
        floatView = inflater.inflate(R.layout.floating_layout, null) as ViewGroup

        // The Buttons and the EditText are connected with
        // the corresponding component id used in floating_layout xml file

        // The Buttons and the EditText are connected with
        // the corresponding component id used in floating_layout xml file
        tvStatus = floatView?.findViewById(R.id.tvstatus)
        btnClose = floatView?.findViewById(R.id.btnClose)
        btnStop = floatView?.findViewById(R.id.btnStop)
        btnResume = floatView?.findViewById(R.id.btnResume)
        btnStart = floatView?.findViewById(R.id.btnStart)

        // Just like MainActivity, the text written
        // in Maximized will stay


        // WindowManager.LayoutParams takes a lot of parameters to set the
        // the parameters of the layout. One of them is Layout_type.

        // WindowManager.LayoutParams takes a lot of parameters to set the
        // the parameters of the layout. One of them is Layout_type.
        LAYOUT_TYPE = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If API Level is more than 26, we need TYPE_APPLICATION_OVERLAY
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            // If API Level is lesser than 26, then we can
            // use TYPE_SYSTEM_ERROR,
            // TYPE_SYSTEM_OVERLAY, TYPE_PHONE, TYPE_PRIORITY_PHONE.
            // But these are all
            // deprecated in API 26 and later. Here TYPE_TOAST works best.
            WindowManager.LayoutParams.TYPE_TOAST
        }

        // Now the Parameter of the floating-window layout is set.
        // 1) The Width of the window will be 55% of the phone width.
        // 2) The Height of the window will be 58% of the phone height.
        // 3) Layout_Type is already set.
        // 4) Next Parameter is Window_Flag. Here FLAG_NOT_FOCUSABLE is used. But
        // problem with this flag is key inputs can't be given to the EditText.
        // This problem is solved later.
        // 5) Next parameter is Layout_Format. System chooses a format that supports
        // translucency by PixelFormat.TRANSLUCENT

        // Now the Parameter of the floating-window layout is set.
        // 1) The Width of the window will be 55% of the phone width.
        // 2) The Height of the window will be 58% of the phone height.
        // 3) Layout_Type is already set.
        // 4) Next Parameter is Window_Flag. Here FLAG_NOT_FOCUSABLE is used. But
        // problem with this flag is key inputs can't be given to the EditText.
        // This problem is solved later.
        // 5) Next parameter is Layout_Format. System chooses a format that supports
        // translucency by PixelFormat.TRANSLUCENT
        floatWindowLayoutParam = WindowManager.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            LAYOUT_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // The Gravity of the Floating Window is set.
        // The Window will appear in the center of the screen

        // The Gravity of the Floating Window is set.
        // The Window will appear in the center of the screen
        floatWindowLayoutParam?.gravity = Gravity.CENTER

        // X and Y value of the window is set

        // X and Y value of the window is set
        floatWindowLayoutParam?.x = 0
        floatWindowLayoutParam?.y = 0

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters
        windowManager?.addView(floatView, floatWindowLayoutParam)

        // The button that helps to maximize the app


        btnStop?.setOnClickListener {
            if(currentRecording != null || recordingState !is VideoRecordEvent.Finalize) {
                Toast.makeText(this, "File Saved To:-\n$savedPath", Toast.LENGTH_LONG).show()
                val recording = currentRecording
                if(recording != null) {
                    recording.stop()
                    currentRecording = null
                }
                stopService()
                windowManager?.removeView(floatView);
            }
        }

        btnClose?.setOnClickListener {
            if(currentRecording != null || recordingState !is VideoRecordEvent.Finalize) {
                Toast.makeText(this, "File Saved To:-\n$savedPath", Toast.LENGTH_LONG).show()
                val recording = currentRecording
                if(recording != null) {
                    recording.stop()
                    currentRecording = null
                }
                stopService()
                windowManager?.removeView(floatView);
            }
        }


        /*  // The button that helps to maximize the app
          maximizeBtn.setOnClickListener(object : OnClickListener() {
              fun onClick(v: View?) {
                  // stopSelf() method is used to stop the service if
                  // it was previously started
                  stopSelf()

                  // The window is removed from the screen
                  windowManager.removeView(floatView)

                  // The app will maximize again. So the MainActivity
                  // class will be called again.
                  val backToHome = Intent(this@FloatingWindowGFG, MainActivity::class.java)

                  // 1) FLAG_ACTIVITY_NEW_TASK flag helps activity to start a new task on the history stack.
                  // If a task is already running like the floating window service, a new activity will not be started.
                  // Instead the task will be brought back to the front just like the MainActivity here
                  // 2) FLAG_ACTIVITY_CLEAR_TASK can be used in the conjunction with FLAG_ACTIVITY_NEW_TASK. This flag will
                  // kill the existing task first and then new activity is started.
                  backToHome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                  startActivity(backToHome)
              }
          })*/


        // Another feature of the floating window is, the window is movable.
        // The window can be moved at any position on the screen.

        // Another feature of the floating window is, the window is movable.
        // The window can be moved at any position on the screen.
        floatView?.setOnTouchListener(object : View.OnTouchListener {
            val floatWindowLayoutUpdateParam: WindowManager.LayoutParams =
                floatWindowLayoutParam as WindowManager.LayoutParams
            var x = 0.0
            var y = 0.0
            var px = 0.0
            var py = 0.0
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x = floatWindowLayoutUpdateParam.x.toDouble()
                        y = floatWindowLayoutUpdateParam.y.toDouble()

                        // returns the original raw X
                        // coordinate of this event
                        px = event.rawX.toDouble()

                        // returns the original raw Y
                        // coordinate of this event
                        py = event.rawY.toDouble()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        floatWindowLayoutUpdateParam.x = (x + event.rawX - px).toInt()
                        floatWindowLayoutUpdateParam.y = (y + event.rawY - py).toInt()

                        // updated parameter is applied to the WindowManager
                        windowManager?.updateViewLayout(floatView, floatWindowLayoutUpdateParam)
                    }
                }
                return false
            }
        })

        /*  saveBtn.setOnClickListener(object : OnClickListener() {
              fun onClick(v: View?) {
                  // saves the text in savedDesc variable
                  Common.savedDesc = descEditArea.getText().toString()
                  descEditArea.setCursorVisible(false)
                  val floatWindowLayoutParamUpdateFlag: WindowManager.LayoutParams =
                      floatWindowLayoutParam
                  floatWindowLayoutParamUpdateFlag.flags =
                      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

                  // The Layout Flag is changed back to FLAG_NOT_FOCUSABLE. and the Layout is updated with new Flag
                  windowManager.updateViewLayout(floatView, floatWindowLayoutParamUpdateFlag)

                  // INPUT_METHOD_SERVICE with Context is used
                  // to retrieve a InputMethodManager for
                  // accessing input methods which is the soft keyboard here
                  val inputMethodManager: InputMethodManager =
                      getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

                  // The soft keyboard slides back in
                  inputMethodManager.hideSoftInputFromWindow(floatView.getApplicationWindowToken(), 0)

                  // A Toast is shown when the text is saved
                  Toast.makeText(this@FloatingWindowGFG, "Text Saved!!!", Toast.LENGTH_SHORT).show()
              }
          })*/
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }


    private suspend fun initializeCamera() {
        val cameraProvider =
            ProcessCameraProvider.getInstance(this@MyCamVideoRecorderService).await()

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val qualitySelector = QualitySelector
            .from(
                Quality.LOWEST,
                FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
            )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this@MyCamVideoRecorderService,
                cameraSelector,
                videoCapture
            )
        } catch(exc: Exception) {
            // we are on main thread, let's reset the controls on the UI.
            Log.e(CaptureFragment.TAG, "Use case binding failed", exc)
        }
    }


    private fun startRecording() {
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val name = "CameraX-recording-" +
                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                    .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        val videoPath = getResourcesDirectoryPath(this)
        val directory: File = File(videoPath)
        if(!directory.exists()) directory.mkdirs()
        val currentTime = System.currentTimeMillis()
        val fileNameString = "videooutput$currentTime.mp4"
        val outFile = File(directory, fileNameString)
        savedPath = outFile.absolutePath
        val fileOutputOptions = FileOutputOptions.Builder(outFile).build()
        // configure Recorder and Start recording to the mediaStoreOutput.
        currentRecording = videoCapture.output
            .prepareRecording(this, fileOutputOptions)
            // .prepareRecording(this, mediaStoreOutput)
            .apply { if(audioEnabled) withAudioEnabled() }
            .start(mainThreadExecutor, captureListener)

        Log.i(TAG, "Recording started")
    }

    /**
     * CaptureEvent listener.
     */
    private val captureListener = Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        Log.d("eventttttt", "eventtttt")
        if(event !is VideoRecordEvent.Status)
            recordingState = event

        updateUI(event)

        if(event is VideoRecordEvent.Finalize) {
            // display the captured video
            /*  lifecycleScope.launch {
                  navController.navigate(
                      CaptureFragmentDirections.actionCaptureToVideoViewer(
                          event.outputResults.outputUri
                      )
                  )
              }*/
        }
    }

    private fun updateUI(event: VideoRecordEvent) {
        val state = if(event is VideoRecordEvent.Status) recordingState.getNameString()
        else event.getNameString()
        when(event) {
            is VideoRecordEvent.Status -> {
                // placeholder: we update the UI with new status after this when() block,
                // nothing needs to do here.
            }
            is VideoRecordEvent.Start -> {
                //  showUI(CaptureFragment.UiState.RECORDING, event.getNameString())
            }
            is VideoRecordEvent.Finalize -> {
                //  showUI(CaptureFragment.UiState.FINALIZED, event.getNameString())
            }
            is VideoRecordEvent.Pause -> {
                //  captureViewBinding.captureButton.setImageResource(R.drawable.ic_resume)
            }
            is VideoRecordEvent.Resume -> {
                //  captureViewBinding.captureButton.setImageResource(R.drawable.ic_pause)
            }
        }

        val stats = event.recordingStats
        val size = stats.numBytesRecorded / 1000
        val megabytes: Long = size / 1024
        val time =
            calculateTime(java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos))
        var text = "${state}: recorded ${megabytes}MB, in $time"
        if(event is VideoRecordEvent.Finalize)
            text = "${text}\nFile saved to: ${event.outputResults.outputUri}"

        builder?.setContentTitle("Video Recording")
        builder?.setContentText(text)
        tvStatus?.text = text
        Log.i(TAG, "recording event: $text")
    }


    private fun stopService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun calculateTime(seconds: Long): String {
        val day = TimeUnit.SECONDS.toDays(seconds).toInt()
        val hours: Long = TimeUnit.SECONDS.toHours(seconds) - day * 24
        val minute: Long =
            TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.SECONDS.toHours(seconds) * 60
        val second: Long =
            TimeUnit.SECONDS.toSeconds(seconds) - TimeUnit.SECONDS.toMinutes(seconds) * 60

        val sb = java.lang.StringBuilder()
        if(hours > 0) {
            sb.append("$hours hr : ")
        }
        sb.append("$minute min : $second sec")
        return sb.toString()
    }


    override fun onDestroy() {
        super.onDestroy()
    }


    private fun getNotification(): Notification? {

        val flag =
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE
            else
                0

        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getService(this, 0, intent, flag)


        val stopIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(this, 8756, stopIntent, flag)

        val startIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_START
        }
        val startPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(this, 8757, startIntent, flag)

        val resumeIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_RESUME
        }
        val resumePendingIntent: PendingIntent =
            PendingIntent.getBroadcast(this, 8758, resumeIntent, flag)


        builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_video_notification)
            .setContentTitle("Video Recording")
            .setContentText("Recording video in background......")
            .addAction(R.drawable.ic_resume, "Resume", resumePendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .addAction(R.drawable.ic_start, "Start", startPendingIntent)
            .setContentIntent(pendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Much longer text that cannot fit one line...")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)

        return builder?.build()
    }


    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}