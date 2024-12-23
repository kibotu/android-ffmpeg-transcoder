/**
 * Created by [Jan Rabe](https://about.me/janrabe).
 */
package net.kibotu.androidffmpegtranscoder.ffmpeg

import android.util.Log
//import com.exozet.transcoder.BuildConfig

//internal var debug = BuildConfig.DEBUG
internal var debug = false // BuildConfig.DEBUG

internal fun Any.log(message: String?) {
    if (debug)
        Log.d(this::class.java.simpleName, "$message")
}

internal fun Any.loge(message: String?) {
    if (debug)
        Log.e(this::class.java.simpleName, "$message")
}

internal fun Throwable.log() {
    if (debug)
        Log.d(this::class.java.simpleName, "$message")
}