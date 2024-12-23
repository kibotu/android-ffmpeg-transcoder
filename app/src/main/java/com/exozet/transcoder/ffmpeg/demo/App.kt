package com.exozet.transcoder.ffmpeg.demo

import android.app.Application
import net.kibotu.logger.LogcatLogger
import net.kibotu.logger.Logger

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        Logger.addLogger(LogcatLogger())
    }
}