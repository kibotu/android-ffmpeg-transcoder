package com.exozet.videoeditor

import android.content.Context
import android.net.Uri
import android.util.Log
import io.reactivex.Observable
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import nl.bravobit.ffmpeg.FFtask
import java.io.File

class FFMpegTranscoder(context: Context) : IFFMpegTranscoder {

    private val internalStoragePath: String = context.filesDir.path

    private val TAG = FFMpegTranscoder::class.java.simpleName

    private var ffmpeg : FFmpeg = FFmpeg.getInstance(context)

    private var extractFrameTaskList: ArrayList<FFtask> = arrayListOf()
    private var createVideoTaskList: ArrayList<FFtask> = arrayListOf()


    //todo: add percentage calculation

    override fun extractFramesFromVideo(inputUri: Uri, carId: Long, photoQuality: Int, frameTimes: List<String>) = Observable.create<MetaData> { emitter ->

        val currentTime = System.currentTimeMillis()

        if (emitter.isDisposed) {
            return@create
        }

        val inputUriPath = inputUri.path

        val localSavePath = "$internalStoragePath/postProcess/$carId/$currentTime/"

        //create new folder
        File(localSavePath).mkdirs()

        var selectedTimePoints = "select='"

        frameTimes.forEach {
            selectedTimePoints += "lt(prev_pts*TB\\,$it)*gte(pts*TB\\,$it)+"
        }

        selectedTimePoints = selectedTimePoints.substring(0, selectedTimePoints.length - 1)
        selectedTimePoints += "'"

        /**
         * -i : input
         * -vf : filter_graph set video filters
         * -filter:v : video filter for given parameters - like requested frame times
         * -qscale:v :quality parameter
         * -vsync : drop : This allows to work around any non-monotonic time-stamp errors //not sure how it totally works
         */
        val cmd = arrayOf("-i", inputUriPath, "-qscale:v", "$photoQuality", "-filter:v", selectedTimePoints, "-vsync", "drop", "${localSavePath}image_%03d.jpg")

        val extractFrameTask = ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {

            override fun onFailure(result: String?) {
                loge("FAIL with output : $result")

                //delete failed process folder
                deleteFolder(localSavePath)

                emitter.onError(Throwable(result))
            }

            override fun onSuccess(result: String?) {
                log("SUCCESS with output : $result")

                emitter.onNext(MetaData(uri = Uri.fromFile(File(localSavePath))))
            }

            override fun onProgress(progress: String?) {
                log("progress : $progress")
                progress?.let {
                    //todo: enable
                    Log.d(TAG, "process = $progress")
                    // emitter.onNext(MetaData(progress))
                }
            }

            override fun onStart() {
                log("Started command : ffmpeg $cmd")
                //todo: enable
                //emitter.onNext(MetaData(message = "Started Command $cmd"))
            }

            override fun onFinish() {
                log("Finished command : ffmpeg $cmd")
                emitter.onComplete()
            }
        })
        //add it to list so can stop them later
        extractFrameTaskList.add(extractFrameTask)
    }


    override fun createVideoFromFrames(outputUri: Uri, frameFolder: Uri, videoQuality: Int, fps: Int, pixelFormat: PixelFormatType, deleteAfter: Boolean) = Observable.create<MetaData> { emitter ->

        if (emitter.isDisposed) {
            return@create
        }

        /**
         * -i : input
         * -framerate : frame rate of the video
         * -crf quality of the output video
         * -pix_fmt pixel format
         */
        val cmd = arrayOf("-framerate", "$fps", "-i", "${frameFolder.path}/image_%03d.jpg", "-crf", "$videoQuality", "-pix_fmt", pixelFormat.type, outputUri.path)

        val createVideoTask = ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {

            override fun onFailure(result: String?) {
                loge("FAIL create video with output : $result")
                emitter.onError(Throwable(result))
            }

            override fun onSuccess(result: String?) {
                log("SUCCESS create video with output : $result")
                result?.let { emitter.onNext(MetaData(message = outputUri.path)) }
            }

            override fun onProgress(progress: String?) {
                log("progress create video : $progress")

                //todo: update progress
                //todo: enable
                //progress?.let { emitter.onNext(MetaData(progress = it)) }
            }

            override fun onStart() {
                log("Started command create video : ffmpeg $cmd")
                //todo: enable
                //emitter.onNext(MetaData(message = "Started command create video : ffmpeg $cmd"))
            }

            override fun onFinish() {
                log("Finished command create video: ffmpeg $cmd")
                //delete temp files
                if (deleteAfter) {
                    val deleteStatus = deleteFolder(frameFolder.path)
                    log("Delete temp frame save path status: $deleteStatus")
                }

                emitter.onComplete()
            }
        })
        createVideoTaskList.add(createVideoTask)


    }

    override fun stopAllProcesses() {
        extractFrameTaskList.forEach {
            it.sendQuitSignal()
        }
        extractFrameTaskList.clear()

        createVideoTaskList.forEach {
            it.sendQuitSignal()
        }
        createVideoTaskList.clear()
    }

    private fun deleteFolder(path: String): Boolean {
        val someDir = File(path)
        return someDir.deleteRecursively()
    }
}