package com.example.externalsensorframework

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper


fun HandlerThread.getThreadHandler(): Handler = Handler(Looper.myLooper()!!)
const val BUFFER_LENGTH_BYTE_SIZE = 4
const val REQUEST_BYTE_SIZE = 1
const val RESPONSE_BYTE_SIZE = 1

