package net.kibotu.androidffmpegtranscoder.demo

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.fragment.app.FragmentActivity
import net.kibotu.androidffmpegtranscoder.ffmpeg.Progress
import net.kibotu.androidffmpegtranscoder.mcvideoeditor.MediaCodecTranscoder
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import net.kibotu.androidffmpegtranscoder.demo.databinding.ActivityDemoBinding
import net.kibotu.logger.Logger

class DemoActivity : FragmentActivity() {

    private lateinit var binding : ActivityDemoBinding

    var subscription: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscription = CompositeDisposable()
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val frameFolder = "transcoding/process".parseInternalStorageFile(this)
        val inputVideo = "transcoding/input/source_video.mp4".parseInternalStorageFile(this)
        val outputVideo = "transcoding/output/output_${System.currentTimeMillis()}.mp4".parseInternalStorageFile(this)

        val increment = 63f / 120f
        val times = (0..120).map {
            increment * it.toDouble()
        }

        binding.extractFrames.setOnClickListener {

            var progress: Progress? = null

            MediaCodecTranscoder.extractFramesFromVideo(
                context = this,
                frameTimes = times,
                inputVideo = inputVideo,
                id = "loremipsum",
                outputDir = frameFolder,
                photoQuality = 100
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        Logger.v( "extractFramesFromVideo onNext $it" )
                        progress = it

                        binding.extractFramesProgress.setProgress(it.progress)
                    },
                    {
                        Logger.v( "extractFramesFromVideo onError ${it.localizedMessage}" )
                    },
                    {  Logger.v( "extractFramesFromVideo onComplete $progress" ) }
                ).addTo(subscription)
        }

       binding.mergeFrames.setOnClickListener {

            var progress: Progress? = null

            MediaCodecTranscoder.createVideoFromFrames(
                frameFolder = frameFolder,
                outputUri = outputVideo,
                deleteFramesOnComplete = true
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        Logger.v( "createVideoFromFrames onNext $it" )
                        progress = it

                        binding.mergeFramesProgress.setProgress(it.progress)
                    },
                    {
                        Logger.v( "createVideoFromFrames onError ${it.localizedMessage}" )
                    },
                    { Logger.v( "createVideoFromFrames onComplete $progress" ) }
                ).addTo(subscription)
        }

        binding.cancel.setOnClickListener {
            dispose()
        }

        (0 until 10).toList().subList(0,10)
    }

    override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    private fun dispose() {
        if (!subscription.isDisposed)
            subscription.dispose()
        subscription = CompositeDisposable()
    }
}

fun String.parseExternalStorageFile(): Uri =
    Uri.parse("${Environment.getExternalStorageDirectory()}/$this")

fun String.parseAssetFile(): Uri = Uri.parse("file:///android_asset/$this")

fun String.parseInternalStorageFile(context: Context): Uri =
    Uri.parse("${context.filesDir.absolutePath}/$this")