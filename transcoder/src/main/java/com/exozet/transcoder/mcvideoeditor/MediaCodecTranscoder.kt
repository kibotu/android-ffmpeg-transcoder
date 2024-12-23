package com.exozet.transcoder.mcvideoeditor

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.IntRange
import com.exozet.transcoder.ffmpeg.Progress
import io.reactivex.Observable
import java.io.File

object MediaCodecTranscoder {

    fun extractFramesFromVideo(
        context: Context,
        frameTimes: List<Double>,
        inputVideo: Uri,
        id: String,
        outputDir: Uri?,
        @IntRange(from = 1, to = 100) photoQuality: Int = 100
    ): Observable<Progress> {
        val mediaCodec = MediaCodecExtractImages()

        val internalStoragePath: String = context.filesDir.absolutePath
        val startTime = System.currentTimeMillis()

        val localSavePath = "${outputDir ?: "$internalStoragePath/postProcess/$id/$startTime/"}"

        //create new folder
        val file = File(localSavePath)
        if (!file.exists())
            file.mkdirs()

        return mediaCodec.extractMpegFrames(
            inputVideo,
            frameTimes,
            Uri.parse(localSavePath),
            photoQuality
        )
    }

    fun createVideoFromFrames(
        frameFolder: Uri,
        outputUri: Uri,
        config: MediaConfig = MediaConfig(),
        deleteFramesOnComplete: Boolean = true
    ): Observable<Progress> {

        var mediaCodecCreateVideo: MediaCodecCreateVideo? = null
        val shouldCancel = MediaCodecExtractImages.Cancelable()

        return Observable.create<Progress> { emitter ->

            if (emitter.isDisposed)
                return@create

            val items = File(frameFolder.path!!).listFiles()?.sorted() ?: return@create

            mediaCodecCreateVideo = MediaCodecCreateVideo(config)

            val firstFrame =
                BitmapFactory.decodeFile(items.firstOrNull()?.absolutePath ?: return@create)

            mediaCodecCreateVideo!!.startEncoding(
                items,
                firstFrame.width,
                firstFrame.height,
                outputUri,
                shouldCancel,
                emitter
            )

            if (!firstFrame.isRecycled) firstFrame.recycle()

        }.doOnDispose {
            shouldCancel.cancel.set(true)
            mediaCodecCreateVideo = null
        }

    }

    /**
     * Deletes directory path recursively.
     */
    internal fun deleteFolder(path: String): Boolean = File(path).deleteRecursively()
}