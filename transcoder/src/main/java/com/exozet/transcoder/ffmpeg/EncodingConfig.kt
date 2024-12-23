package com.exozet.transcoder.ffmpeg

import androidx.annotation.IntRange

/**
 * https://ffmpeg.org/ffmpeg-all.html
 *
 * https://video.stackexchange.com/a/24684
 *
 * The frames in your H.264 video are grouped into units called [GOP]s (Group Of Pictures). Inside these GOPs frames are classified into three types:
 *
 * [I-frame]: frame that stores the whole picture
 * [P-frame]: frame that stores only the changes between the current picture and previous ones
 * [B-frame]: frame that stores differences with previous or future pictures
 *
 * Additionally, I-frames can be classified as IDR frames and non-IDR frames.
 *
 * The difference is that frames following an IDR frame cannot reference any frame that comes before the IDR frame, while in the case of a non-IDR frame there are no limitations.
 *
 * Every GOP starts with an I-frame, also called a keyframe, but may contain more than one. To create further confusion,
 * a GOP can start with an IDR frame or with a non-IDR frame. This means that frames in the GOP can sometimes refer to previous GOPs (in this case the GOP is said to be "open"),
 * and sometimes not (in this case it's closed).
 *
 * It's common to see the structure of a GOP represented as in this example: [IBBBPBBBPBBBI]. Here the length of the the GOP is 12 frames, with 3 B-frames between each P-frame.
 *
 */
data class EncodingConfig(

    /**
     * [keyInt] specifies the maximum length of the GOP, so the maximum interval between each keyframe,
     * which remember that can be either an IDR frame or a non-IDR frame.
     * I'm not completely sure but I think that by default ffmpeg will require every I-frame to be an IDR frame,
     * so in practice you can use the terms IDR frame and I-frame interchangeably
     */
    val keyInt: Int = 10,
    /**
     * min-keyint specifies the minimum length of the GOP.
     * This is because the encoder might decide that it makes sense to add a keyframe before the keyint value, so you can put a limit.
     */
    val minKeyInt: Int = 10,

    /**
     * GoP size.
     */
    val gopValue: Int? = null,

    /**
     * crf
     * Set the quality/size tradeoff for constant-quality (no bitrate target) and constrained-quality (with maximum bitrate target) modes.
     * Valid range is 0 to 63, higher numbers indicating lower quality and smaller output size. Only used if set; by default only the bitrate target is used.
     *
     * 0 for lossless, 23 is default in ffmpeg
     *
     * reasonable value 18
     */
    @IntRange(from = 0, to = 63)
    val videoQuality: Int? = null,

    /**
     * -framerate input Frame rate of the video.
     * https://lists.ffmpeg.org/pipermail/ffmpeg-user/2013-July/016273.html
     */
    val sourceFrameRate: Float? = 30000f / 1001f,

    /**
     * -r output Frame rate of the video.
     * https://video.stackexchange.com/a/13074
     */
    val outputFrameRate: Float? = 30000f / 1001f,

    /**
     * pix_fmts
     * A ’|’-separated list of pixel format names, such as "pix_fmts=yuv420p|monow|rgb24".
     */
    val pixelFormat: PixelFormat = PixelFormat.yuv420p,

    /**
     * -preset type
     * Configuration preset. This does some automatic settings based on the general type of the image.
     *
     * https://trac.ffmpeg.org/wiki/Encode/H.264
     */
    val preset: Preset? = null,
    /**
     * -c:v Video codec.
     */
    val encoding: Encoding = Encoding.libx264,

    /**
     * https://trac.ffmpeg.org/wiki/Limiting%20the%20output%20bitrate
     * specifies a maximum tolerance. this is only used in conjunction with bufsize
     *
     * reasonable value for full hd = 8 * 1024
     */
    val maxrate: Int? = null,

    /**
     * specifies the decoder buffer size, which determines the variability of the output bitrate
     *
     * reasonable value for full hd = 8 * 1024
     */
    val bufsize: Int? = null
)