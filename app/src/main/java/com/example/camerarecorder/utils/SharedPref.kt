package com.example.camerarecorder.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.camera.video.Quality


class SharedPref(val context: Context) {
    companion object {

        const val Quality_LOWEST = "Quality_LOWEST"
        const val Quality_HIGHEST = "Quality_HIGHEST"
        const val Quality_SD = "Quality_SD"
        const val Quality_HD = "Quality_HD"
        const val Quality_FHD = "Quality_FHD"
        const val Quality_UHD = "Quality_UHD"

        const val FRONT_CAMERA = "FRONT_CAMERA"
        const val BACK_CAMERA = "BACK_CAMERA"

        const val AUDIO_ENABLED = "AUDIO_ENABLED"
        const val CAMERA_SELECTED = "CAMERA_SELECTED"
        const val QUALITY_SELECTED = "QUALITY_SELECTED"
    }

    private var mSharedPref: SharedPreferences? = null

    init {
        if(mSharedPref == null) mSharedPref =
            context.getSharedPreferences(context.packageName, Activity.MODE_PRIVATE)
    }

    fun getString(key: String?, defValue: String?): String? {
        return mSharedPref?.getString(key, defValue)
    }

    fun putString(key: String?, value: String?) {
        val prefsEditor = mSharedPref?.edit()
        prefsEditor?.putString(key, value)
        prefsEditor?.apply()
    }

    fun getInteger(key: String?, defValue: Int): Int? {
        return mSharedPref?.getInt(key, defValue)
    }

    fun putInteger(key: String?, value: Int?) {
        val prefsEditor = mSharedPref?.edit()
        prefsEditor?.putInt(key, value!!)
        prefsEditor?.apply()
    }


    fun getBoolean(key: String?, defValue: Boolean): Boolean? {
        return mSharedPref?.getBoolean(key, defValue)
    }

    fun putBoolean(key: String?, value: Boolean) {
        val prefsEditor = mSharedPref?.edit()
        prefsEditor?.putBoolean(key, value)
        prefsEditor?.apply()
    }

    fun getLong(key: String?, defValue: Long): Long? {
        return mSharedPref?.getLong(key, defValue)
    }

    fun putLong(key: String?, value: Long) {
        val prefsEditor = mSharedPref?.edit()
        prefsEditor?.putLong(key, value)
        prefsEditor?.apply()
    }


    fun getFloat(key: String?, defValue: Float): Float? {
        return mSharedPref?.getFloat(key, defValue)
    }

    fun putFloat(key: String?, value: Float) {
        val prefsEditor = mSharedPref?.edit()
        prefsEditor?.putFloat(key, value)
        prefsEditor?.apply()
    }


    //// Clear Preference ////
    fun clearPreference() {
        mSharedPref?.edit()?.clear()?.apply()
    }

    //// Remove ////
    fun removePreference(Key: String?) {
        mSharedPref?.edit()?.remove(Key)?.apply()
    }
}