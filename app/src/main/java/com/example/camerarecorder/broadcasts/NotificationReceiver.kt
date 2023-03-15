package com.example.camerarecorder.broadcasts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.camerarecorder.MainActivity

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESUME = "ACTION_RESUME"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action) {
            ACTION_STOP -> {
                Log.d("stopppping", "Stoping................")
                Toast.makeText(context, "Stopping............", Toast.LENGTH_SHORT).show()
                /*   if(currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
                       return
                   }

                   val recording = currentRecording
                   if(recording != null) {
                       recording.stop()
                       currentRecording = null
                   }
                   stopForeground(LifecycleService.STOP_FOREGROUND_REMOVE)*/
            }

            ACTION_START -> {

            }

            ACTION_RESUME -> {

            }

        }
    }
}