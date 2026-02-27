package com.morphus.app.utils

import android.util.Log

/**
 * Centralized debug logger for Morphus SOS app.
 * All tags are prefixed with "MORPHUS_" for easy Logcat filtering.
 *
 * Logcat filter: MORPHUS_
 */
object MorphusLog {
    fun d(tag: String, msg: String) = Log.d("MORPHUS_$tag", msg)
    fun i(tag: String, msg: String) = Log.i("MORPHUS_$tag", msg)
    fun w(tag: String, msg: String) = Log.w("MORPHUS_$tag", msg)
    fun e(tag: String, msg: String, e: Exception? = null) = Log.e("MORPHUS_$tag", msg, e)
}
