package net.kibotu.androidffmpegtranscoder.mcvideoeditor

data class MediaConfig(

    /**
    A key describing the average bitrate in bits/sec. The associated value is an integer
     **/
    val bitRate: Int? = 16_000_000,

    /**
     * A key describing the frame rate of a video format in frames/sec.
     * The associated value is normally an integer when the value is used by the platform,
     * but video codecs also accept float configuration values.
     * Specifically, MediaExtractor#getTrackFormat provides an integer value corresponding to the frame rate information of the track if specified and non-zero.
     * Otherwise, this key is not present. MediaCodec#configure accepts both float and integer values.
     * This represents the desired operating frame rate if the KEY_OPERATING_RATE is not present and KEY_PRIORITY is 0 (realtime).
     * For video encoders this value corresponds to the intended frame rate, although encoders are expected to support variable frame rate based on MediaCodec.BufferInfo#presentationTimeUs.
     * This key is not used in the MediaCodec MediaCodec#getInputFormat/MediaCodec#getOutputFormat formats, nor by MediaMuxer#addTrack.
     */
    val frameRate: Int? = 30,


    /**
     *A key describing the frequency of key frames expressed in seconds between key frames.
     *This key is used by video encoders.
     * A negative value means no key frames are requested after the first frame.
     * A zero value means a stream containing all key frames is requested.
     *
     * Most video encoders will convert this value of the number of non-key-frames between key-frames, using the frame rate information;
     * therefore, if the actual frame rate differs (e.g. input frames are dropped or the frame rate changes),
     * the time interval between key frames will not be the configured value.
     */
    val iFrameInterval: Int? = 1,

    /**
     * The mime type of the content. This value must never be null.
     */
    val mimeType: String = "video/avc"

)