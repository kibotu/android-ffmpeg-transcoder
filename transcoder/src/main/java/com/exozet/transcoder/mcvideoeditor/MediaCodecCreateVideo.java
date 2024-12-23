package com.exozet.transcoder.mcvideoeditor;

import static com.exozet.transcoder.ffmpeg.DebugExtensionsKt.log;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.exozet.transcoder.ffmpeg.Progress;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class MediaCodecCreateVideo {
    private static final String TAG = MediaCodecCreateVideo.class.getSimpleName();

    private CompositeDisposable subscription = new CompositeDisposable();
    private File mOutputFile;
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;

    private String mimeType; // H.264 Advanced Video Coding
    private static int mWidth;
    private static int mHeight;
    private int bitRate;
    private int frameRate; // Frames per second

    private int iFrameInterval;

    private int mGenerateIndex = 0;
    private int mTrackIndex;
    private boolean mNoMoreFrames = false;
    private boolean mAbort = false;

    private int colorFormat;
    private int frameNumber = 0;
    private long startTime;

    private List<File> frames;
    private MediaCodecExtractImages.Cancelable cancelable;

    public MediaCodecCreateVideo(MediaConfig mediaConfig) {

        this.mimeType = mediaConfig.getMimeType();

        if (mediaConfig.getBitRate() != null) {
            this.bitRate = mediaConfig.getBitRate();
        }

        if (mediaConfig.getFrameRate() != null) {
            this.frameRate = mediaConfig.getFrameRate();
        }

        if (mediaConfig.getIFrameInterval() != null) {
            this.iFrameInterval = mediaConfig.getIFrameInterval();
        }

        int[] formats = this.getMediaCodecList();

        if (colorFormat <= 0) {
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        }

        //need to select correct color format yuv420. In here we are deciding which one should we use
        //https://www.jianshu.com/p/54a702be01e1 check this link for more (Chinese)
        lab:
        for (int format : formats) {
            switch (format) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // yuv420sp
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // yuv420p
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // yuv420psp
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // yuv420pp
                    colorFormat = format;
                    break lab;
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void startEncoding(List<File> frames, int width, int height, Uri outputUri, MediaCodecExtractImages.Cancelable cancelable, ObservableEmitter<Progress> emitter) {
        mWidth = width;
        mHeight = height;
        this.frames = frames;
        this.cancelable = cancelable;

        startTime = System.currentTimeMillis();

        mOutputFile = new File(outputUri.getPath());

        MediaCodecInfo codecInfo = selectCodec(mimeType);
        if (codecInfo == null) {
            log(TAG, "Unable to find an appropriate codec for " + mimeType);
            return;
        }
        log(TAG, "found codec: " + codecInfo.getName());

        try {
            mediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            log(TAG, "Unable to create MediaCodec " + e.getMessage());
            emitter.onError(e);
            return;
        }

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(mimeType, mWidth, mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        try {
            mediaMuxer = new MediaMuxer(mOutputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            log(TAG, "MediaMuxer creation failed. " + e.getMessage());
            emitter.onError(e);
            return;
        }

        log(TAG, "Initialization complete. Starting encoder...");

        if (this.cancelable.getCancel().get()) {
            release();
            mOutputFile.delete();
            return;
        }

        subscription.add(Observable.fromIterable(frames)
                .map(input -> {
                    return BitmapFactory.decodeFile(input.getAbsolutePath());
                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(bitmap -> {
                    frameNumber++;
                    encode(bitmap, emitter);
                }));
    }

    private void encode(Bitmap bitmap, ObservableEmitter<Progress> emitter) {

        if (cancelable.getCancel().get()) {
            release();
            mOutputFile.delete();
            return;
        }

        Progress progress = new Progress((int) ((((float) frameNumber) / ((float) frames.size() - 1f)) * 100), null, Uri.parse(mOutputFile.getAbsolutePath()), System.currentTimeMillis() - startTime);
        emitter.onNext(progress);

        log(TAG, "Encoder started");
        byte[] byteConvertFrame = getNV12(bitmap.getWidth(), bitmap.getHeight(), bitmap);

        long TIMEOUT_USEC = 500000;
        int inputBufIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        long ptsUsec = computePresentationTime(mGenerateIndex, frameRate);
        if (inputBufIndex >= 0) {
            final ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufIndex);
            inputBuffer.clear();
            inputBuffer.put(byteConvertFrame);
            mediaCodec.queueInputBuffer(inputBufIndex, 0, byteConvertFrame.length, ptsUsec, 0);
            mGenerateIndex++;
        }
        MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
        int encoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
            // no output available yet
            log(TAG, "No output from encoder available");
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // not expected for an encoder
            MediaFormat newFormat = mediaCodec.getOutputFormat();
            newFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 3000 * 3000);
            mTrackIndex = mediaMuxer.addTrack(newFormat);
            mediaMuxer.start();
        } else if (encoderStatus < 0) {
            log(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
        } else if (mBufferInfo.size != 0) {
            ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                log(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
            } else {
                encodedData.position(mBufferInfo.offset);
                encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                mediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                mediaCodec.releaseOutputBuffer(encoderStatus, false);
            }
        }

        if (frameNumber >= frames.size() - 1) {
            emitter.onComplete();
            release();
        }
    }

    private void release() {
        subscription.dispose();

        frames = null;
        cancelable = null;

        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
            log(TAG, "RELEASE CODEC");
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
            log(TAG, "RELEASE MUXER");
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private byte[] getNV12(int inputWidth, int inputHeight, Bitmap scaled) {

        int[] argb = new int[inputWidth * inputHeight];

        //Log.i(TAG, "scaled : " + scaled);
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];

        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // yuv420sp
                encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // yuv420p
                encodeYUV420P(yuv, argb, inputWidth, inputHeight);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // yuv420psp
                encodeYUV420PSP(yuv, argb, inputWidth, inputHeight);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // yuv420pp
                encodeYUV420PP(yuv, argb, inputWidth, inputHeight);
                break;
        }

        return yuv;
    }

    private void encodeYUV420P(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + width * height / 4;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[vIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv420sp[uIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }

                index++;
            }
        }
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }

    private void encodeYUV420PSP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
//        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;
//                R = (argb[index] & 0xff000000) >>> 24;
//                G = (argb[index] & 0xff0000) >> 16;
//                B = (argb[index] & 0xff00) >> 8;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[yIndex + 1] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[yIndex + 3] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }
                if (index % 2 == 0) {
                    yIndex++;
                }
                index++;
            }
        }
    }

    private void encodeYUV420PP(byte[] yuv420sp, int[] argb, int width, int height) {

        int yIndex = 0;
        int vIndex = yuv420sp.length / 2;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;
//                R = (argb[index] & 0xff000000) >>> 24;
//                G = (argb[index] & 0xff0000) >> 16;
//                B = (argb[index] & 0xff00) >> 8;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                if (j % 2 == 0 && index % 2 == 0) {// 0
                    yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                    yuv420sp[yIndex + 1] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[vIndex + 1] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yIndex++;

                } else if (j % 2 == 0 && index % 2 == 1) { //1
                    yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                } else if (j % 2 == 1 && index % 2 == 0) { //2
                    yuv420sp[vIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                    vIndex++;
                } else if (j % 2 == 1 && index % 2 == 1) { //3
                    yuv420sp[vIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                }
                index++;
            }
        }
    }

    public int[] getMediaCodecList() {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo codecInfo = null;
        for (int i = 0; i < numCodecs && codecInfo == null; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            boolean found = false;
            for (int j = 0; j < types.length && !found; j++) {
                if (types[j].equals(mimeType)) {
                    found = true;
                }
            }
            if (!found) {
                continue;
            }
            codecInfo = info;
        }
        log(TAG, "found" + codecInfo.getName() + "supporting " + mimeType);
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        return capabilities.colorFormats;
    }

    private long computePresentationTime(long frameIndex, int framerate) {
        return 132 + frameIndex * 1000000 / framerate;
    }
}
