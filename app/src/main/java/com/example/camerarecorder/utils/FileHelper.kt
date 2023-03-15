package com.example.camerarecorder.utils

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager

object FileHelper {
    const val APP_TYPE="com.example.camerarecorder"
    const val RESOURCES="VideosCameraas"

    fun getResourcesDirectoryPath(context: Context) =
        "${getDownloadsDirectoryPath(context)}/$RESOURCES"


    private fun getDownloadsDirectoryPath(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            APP_TYPE,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toString() + "/"
                    + context.getApplicationContext().getPackageName()
        )
}