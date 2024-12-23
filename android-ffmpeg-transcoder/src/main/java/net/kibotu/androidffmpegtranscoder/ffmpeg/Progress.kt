package net.kibotu.androidffmpegtranscoder.ffmpeg

import android.net.Uri

data class Progress(
    var progress: Int = 0,
    var message: String? = null,
    var uri: Uri? = null,
    var duration: Long = 0L
)