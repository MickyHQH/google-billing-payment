package com.mickyhqh.googlebillingpayment.utils

import android.util.Log

class Logger(val tag: String) {
    fun d(msg: String) {
        Log.d(tag, msg)
    }

    final
    fun e(msg: String) {
        Log.e(tag, msg)
    }

    final
    fun i(msg: String) {
        Log.i(tag, msg)
    }
}