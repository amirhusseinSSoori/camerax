

package com.amirhusseinsoori.cameraxs.util

import android.os.Handler
import android.os.Looper
import com.amirhusseinsoori.cameraxs.util.ThreadExecutor

class MainExecutor : ThreadExecutor(Handler(Looper.getMainLooper())) {
    override fun execute(runnable: Runnable) {
        handler.post(runnable)
    }
}
