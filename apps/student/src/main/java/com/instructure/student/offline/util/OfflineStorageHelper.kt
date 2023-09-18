package com.instructure.student.offline.util

import android.content.Context
import android.content.SharedPreferences
import com.instructure.canvasapi2.utils.ContextKeeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object OfflineStorageHelper {

    private const val PLAYER_SPEED = "playerSpeed"
    private const val DEVICE_TOKEN = "deviceToken"

    private var mSharedPreferences: SharedPreferences? = null

    private val _speedState = MutableStateFlow(1f)
    val speedState: StateFlow<Float> = _speedState

    var playerSpeed: Float
        get() {
            val value = getValue(PLAYER_SPEED)?.toFloat() ?: 1f
            _speedState.update { value }
            return value
        }
        set(value) {
            _speedState.update { value }
            storeValue(PLAYER_SPEED, value.toString())
        }

    var deviceToken: String
        get() {
            return getValue(DEVICE_TOKEN) ?: ""
        }
        set(value) {
            storeValue(DEVICE_TOKEN, value)
        }


    private fun getValue(key: String): String? {
        val prefs = getPrefs()
        if (prefs.contains(key)) {
            return prefs.getString(key, null)
        }
        return null
    }

    private fun storeValue(key: String, value: String) {
        val sharedPrefsEditor = getPrefs().edit()
        sharedPrefsEditor.putString(key, value)
        sharedPrefsEditor.apply()
    }

    private fun getPrefs(): SharedPreferences {
        mSharedPreferences?.let { return it }
        mSharedPreferences =
            ContextKeeper.appContext.getSharedPreferences("OfflinePrefs", Context.MODE_PRIVATE)
        return mSharedPreferences!!
    }
}