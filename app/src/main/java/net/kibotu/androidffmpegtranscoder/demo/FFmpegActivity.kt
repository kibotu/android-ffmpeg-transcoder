package net.kibotu.androidffmpegtranscoder.demo

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import net.kibotu.androidffmpegtranscoder.demo.databinding.ActivityMainBinding
import net.kibotu.androidffmpegtranscoder.ffmpeg.EncodingConfig
import net.kibotu.androidffmpegtranscoder.ffmpeg.FFMpegTranscoder
import net.kibotu.androidffmpegtranscoder.ffmpeg.Progress
import net.kibotu.androidffmpegtranscoder.mcvideoeditor.MediaCodecTranscoder
import net.kibotu.androidffmpegtranscoder.mcvideoeditor.MediaConfig
import net.kibotu.logger.Logger
import net.kibotu.logger.TAG
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt


class FFmpegActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    var subscription: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        run()
    }

    // region location permission

    private fun run() {

        binding.initFfmpeg.text =
            "FFmpeg is ${if (FFMpegTranscoder.isSupported(this)) "" else "not"} supported."

        val frameFolder = "transcoding/process".parseInternalStorageFile(this)
        val inputVideo = "input/source_video.mp4".parseInternalStorageFile(this)
        val outputVideo = "transcoding/output/output_${System.currentTimeMillis()}.mp4".parseInternalStorageFile(this)

        extractFrames(inputVideo, frameFolder)

        mergeFrames(frameFolder, outputVideo)

        transcode(inputVideo, outputVideo)

        binding.stabilize(inputVideo, outputVideo)

        binding.stopProcess.setOnClickListener {
            stopProcessing()
        }

        binding.deleteFolder.setOnClickListener {
            Logger.v("delete folder = ${FFMpegTranscoder.deleteExtractedFrameFolder(frameFolder)}")
        }

        binding.deleteAll.setOnClickListener {
            Logger.v("delete all = ${FFMpegTranscoder.deleteAllProcessFiles(this)}")
        }
    }

    private fun ActivityMainBinding.stabilize(inputVideo: Uri, outputVideo: Uri) {
        stabilizeVideo.setOnClickListener {
            FFMpegTranscoder.analyseAndFilter(
                context = this@FFmpegActivity,
                inputVideo = inputVideo
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    extractFramesProgress.isVisible = true
                    extractFramesProgress.setProgress(it.progress)

                    Logger.v("Analyze $it")

                    output.text =
                        "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${output.text}"

                }, {
                    Logger.v("transcode fails ${it.message}")
                    it.printStackTrace()
                }, {
                    Logger.v("transcode on complete ")

                    FFMpegTranscoder.stabilize(
                        context = this@FFmpegActivity,
                        inputVideo = inputVideo,
                        outputUri = outputVideo
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({

                            extractFramesProgress.isVisible = true
                            extractFramesProgress.setProgress(it.progress)

                            Logger.v("Stabilize $it")

                            output.text =
                                "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${output.text}"

                        }, {
                            Logger.v("transcode fails ${it.message}")
                            it.printStackTrace()
                        }, { Logger.v("transcode on complete ") })
                        .addTo(subscription)
                })
                .addTo(subscription)
        }
    }


    private fun extractFrames(inputVideo: Uri, frameFolder: Uri) {

        Logger.v("uri=${inputVideo.assetFileExists}")

        binding.extractFrames.setOnClickListener {

            val increment = 63f / 120f

            val times = (0..120).map {
                increment * it.toDouble()
            }

            extractByFFMpeg(
                inputVideo,
                frameFolder
            )   //Uri.parse(copyAssetFileToCache("walkaround.mp4")!!.path)
            //extactByMediaCodec(times, inputVideo, frameFolder)

        }
    }

    private fun mergeFrames(frameFolder: Uri, outputVideo: Uri) {

        binding.makeVideo.setOnClickListener {
            mergeByFFMpeg(frameFolder, outputVideo)
            //mergeByMediaCodec(frameFolder, outputVideo)
        }
    }

    private fun transcode(inputVideo: Uri, outputVideo: Uri) {

        binding.transcodeVideo.setOnClickListener {

            Logger.v("transcode $inputVideo -> $outputVideo")

            binding.output.text = ""

            FFMpegTranscoder.transcode(
                inputVideo = inputVideo,
                outputUri = outputVideo
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    Logger.v("transcode $it")

                }, {
                    Logger.v("transcode fails ${it.message}")
                    it.printStackTrace()
                }, { Logger.v("transcode on complete ") })
                .addTo(subscription)
        }
    }

    //region ffmpeg

    private fun extractByFFMpeg(inputVideo: Uri, frameFolder: Uri) {
        Logger.v("extractFramesFromVideo $inputVideo -> $frameFolder")

        val increment = 63f / 120f

        val times = (0..120).map {
            increment * it.toDouble()
        }

        binding.output.text = ""

        FFMpegTranscoder.extractFramesFromVideo(
            context = this,
            frameTimes = times.map { it.toString() },
            inputVideo = inputVideo,
            id = "12345",
            outputDir = frameFolder
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({

                binding.extractFramesProgress.isVisible = true
                binding.extractFramesProgress.setProgress(it.progress)

                Logger.v("extract frames $it")

                binding.output.text =
                    "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${binding.output.text}"
            }, {
                Logger.v("extracting frames fail ${it.message}")

                it.printStackTrace()
            }, {
                Logger.v("extractFramesFromVideo on complete")
            })
            .addTo(subscription)
    }

    private fun mergeByFFMpeg(frameFolder: Uri, outputVideo: Uri) {
        Logger.v("mergeFrames $frameFolder -> $outputVideo")

        binding.output.text = ""

        FFMpegTranscoder.createVideoFromFrames(
            frameFolder = frameFolder,
            outputUri = outputVideo,
            config = EncodingConfig(
//                    sourceFrameRate = 120f / 63f, // original video length: 120f / 63f;
//                    outputFrameRate = 30f
            ),
            deleteFramesOnComplete = false
        ).subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({

                binding.mergeFramesProgress.isVisible = true
                binding.mergeFramesProgress.setProgress(it.progress)

                Logger.v("extract frames $it")

                binding.output.text =
                    "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${binding.output.text}"

            }, {
                Logger.v("creating video fails ${it.message}")
            }, { Logger.v("createVideoFromFrames on complete ") })
            .addTo(subscription)
    }
    //endregion

    //region MediaCodec


    private fun extactByMediaCodec(times: List<Double>, inputVideo: Uri, frameFolder: Uri) {
        var progress: Progress? = null

        MediaCodecTranscoder.extractFramesFromVideo(
            context = this,
            frameTimes = times,
            inputVideo = inputVideo,
            id = "12345",
            outputDir = frameFolder

        )
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.v(TAG, "1 extract frames $it")
                progress = it
                binding.extractFramesProgress.isVisible = true
                binding.extractFramesProgress.setProgress(it.progress)
            }, {
                Log.v(TAG, "\"1 extracting frames fail ${it.message} $progress")


            }, {
                Log.v(TAG, "1 extractFramesFromVideo on complete $progress")

            })
            .addTo(subscription)
    }

    private fun mergeByMediaCodec(frameFolder: Uri, outputVideo: Uri) {

        MediaCodecTranscoder.createVideoFromFrames(
            frameFolder = frameFolder,
            outputUri = outputVideo,
            config = MediaConfig(
                //bitRate = 16000000,
                // frameRate = 30,
                //iFrameInterval = 1,
                // mimeType = "video/avc"
            ),
            deleteFramesOnComplete = false
        )
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                binding.mergeFramesProgress.isVisible = true
                binding.mergeFramesProgress.setProgress(it.progress)

                Logger.v("merge frames $it")

                binding.output.text =
                    "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${binding.output.text}"

            }, {
                Logger.v("creating video fails ${it.message}")

            }, {
                Logger.v("createVideoFromFrames on complete ")
            })
            .addTo(subscription)


    }

    //endregion

    override fun onDestroy() {
        stopProcessing()
        super.onDestroy()
    }

    private fun stopProcessing() {
        if (!subscription.isDisposed) {
            subscription.dispose()
        }
        subscription = CompositeDisposable()
    }

    private val Uri.assetFileExists: Boolean
        get() {
            val mg = resources.assets
            var `is`: InputStream? = null
            return try {
                `is` = mg.open(this.toString())
                true
            } catch (ex: IOException) {
                false
            } finally {
                `is`?.close()
            }
        }
}

fun Context.copyAssetFileToCache(fileName: String): File? {
    return try {
        File(this.cacheDir, fileName).apply {
            outputStream().use { cache -> assets.open(fileName).use { it.copyTo(cache) } }
        }
    } catch (e: IOException) {
        Logger.e(e)
        null
    }
}