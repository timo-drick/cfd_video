package de.appsonair.cppffmpeg

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.ffmpeg.global.swscale
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.PointerPointer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.charset.Charset
import kotlin.math.absoluteValue

enum class AVMediaType(val id: Int) {
    UNKNOWN(avutil.AVMEDIA_TYPE_UNKNOWN),
    VIDEO(avutil.AVMEDIA_TYPE_VIDEO),
    AUDIO(avutil.AVMEDIA_TYPE_AUDIO),
    DATA(avutil.AVMEDIA_TYPE_DATA),
    SUBTITLE(avutil.AVMEDIA_TYPE_SUBTITLE),
    ATTACHMENT(avutil.AVMEDIA_TYPE_ATTACHMENT),
    NB(avutil.AVMEDIA_TYPE_NB);
    companion object {
        private val idMap = values().associateBy { it.id }
        fun fromId(id: Int) = idMap[id] ?: UNKNOWN
    }
}

data class VideoStream(
    val id: Int,
    val width: Int,
    val height: Int,
    val durationMillis: Long
)

interface FrameGrabberDSL {
    val imageBitmap: ImageBitmap
    fun nextFrame(ts: Long)
}

class MediaFile(
    internal val fmtCtx: AVFormatContext
) {
    val streams = findStreams()

    private var frameGrabber: FrameGrabber? = null
    fun openFrameGrabber(
        stream: VideoStream,
        divider: Int = 1,
        hardwareAcceleration: Boolean = true
    ): FrameGrabberDSL {
        if (frameGrabber != null) throw IllegalStateException("FrameGrabber already open!")
        val newFrameGrabber = FrameGrabber(
            fmtCtx = fmtCtx,
            stream = stream.id,
            targetSize = IntSize(stream.width / divider, stream.height / divider),
            hardwareAcceleration = hardwareAcceleration
        )
        val dsl = object : FrameGrabberDSL {
            override val imageBitmap = newFrameGrabber.composeImage
            override fun nextFrame(ts: Long) {
                newFrameGrabber.grabNextFrame(ts)
            }
        }
        frameGrabber = newFrameGrabber
        return dsl
    }
    fun closeFrameGrabber() {
        if (frameGrabber == null) throw IllegalStateException("FrameGrabber not open!")
        frameGrabber?.cleanup()
        frameGrabber = null
    }

    /*fun useFrameGrabber(stream: VideoStream, block: FrameGrabberDSL.() -> Unit) {
        val divider = 2
        val frameGrabber = FrameGrabber(fmtCtx, stream.id, IntSize(stream.width / divider, stream.height / divider)) //TODO target size option
        val dsl = object : FrameGrabberDSL {
            override val imageBitmap = frameGrabber.composeImage
            override fun nextFrame(ts: Long) {
                frameGrabber.grabNextFrame(ts)
            }
        }
        block(dsl)
        frameGrabber.cleanup()
    }*/

    private fun findStreams(): List<VideoStream> =
        (0 until fmtCtx.nb_streams()).mapNotNull { i ->
            val type = AVMediaType.fromId(fmtCtx.streams(i).codecpar().codec_type())
            println("Stream: $i type: $type")
            if (type == AVMediaType.VIDEO) {
                val stream = fmtCtx.streams(i)
                val width = stream.codecpar().width()
                val height = stream.codecpar().height()
                val duration = stream.duration() * 1000L * stream.time_base().num().toLong() / stream.time_base().den().toLong()
                println("   ${width}x${height} duration: $duration ms")
                VideoStream(i, width, height, duration)
            } else {
                null
            }
        }
    init {
        findMetadata()
    }
    fun findMetadata(): Map<String, String> {
        val metadataMap = mutableMapOf<String, String>()
        var entry: AVDictionaryEntry? = null
        do {
            entry = avutil.av_dict_iterate(fmtCtx.metadata(), entry)
            if (entry != null) {
                val key = entry.key().getString(Charset.defaultCharset())
                val value = entry.value().getString(Charset.defaultCharset())
                metadataMap[key] = value
            }
        } while (entry != null)
        println("Metadata")
        metadataMap.forEach { (key, value) -> println("   $key: $value") }
        return metadataMap
    }
}

class FFMPEGVideoDecoder {

    fun open(filePath: String): MediaFile {
        val fmtCtx = avFormatOpen(filePath)
        return MediaFile(fmtCtx)
    }

    fun close(mediaFile: MediaFile) {
        avformat.avformat_close_input(mediaFile.fmtCtx)
    }

    fun useFile(filePath: String, block: (MediaFile) -> Unit) {
        val fmtCtx = avFormatOpen(filePath)
        block(MediaFile(fmtCtx))
        avformat.avformat_close_input(fmtCtx)
    }

    private fun avFormatOpen(filePath: String): AVFormatContext {
        val fmtCtx = AVFormatContext(null)
        if(avformat.avformat_open_input(fmtCtx, filePath, null, null) < 0)
            throw IllegalStateException("Open file $filePath failed!")
        if(avformat.avformat_find_stream_info(fmtCtx, null as PointerPointer<*>?) < 0)
            throw IllegalStateException("Unable to open stream_info!")
        return fmtCtx
    }
}

class FrameGrabber(
    private val fmtCtx: AVFormatContext,
    private val stream: Int,
    targetSize: IntSize? = null,
    hardwareAcceleration: Boolean = true
) {
    private var hwFormat = -1
    private val codecCtx = openDecoder(stream, hardwareAcceleration)

    private val targetWidth = targetSize?.width ?: codecCtx.width()
    private val targetHeight = targetSize?.height ?: codecCtx.height()

    private val dstFormat = avutil.AV_PIX_FMT_BGRA // Format matches the internal skia image format
    private val skiaFormat = ImageInfo(targetWidth, targetHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE) //N32 == BGRA_8888 Native format
    //private val format = avutil.AV_PIX_FMT_RGB565 // 2 Byte per pixel -> half memory size for one image
    //private val bitmapInfo = ImageInfo(targetWidth, targetHeight, ColorType.RGB_565, ColorAlphaType.OPAQUE)

    private val timeBaseNum = fmtCtx.streams(stream).time_base().num()
    private val timeBaseDen = fmtCtx.streams(stream).time_base().den()

    //Prepare rgb image
    private val numBytes = avutil.av_image_get_buffer_size(dstFormat, targetWidth, targetHeight, 1)
    private val jArray = ByteArray(numBytes)

    private val frame = avutil.av_frame_alloc() //TODO maybe null pointer handling
    private val hwFrame = avutil.av_frame_alloc()

    private val pFrameRGB = avutil.av_frame_alloc().also { frame ->
        val buffer = BytePointer(avutil.av_malloc(numBytes.toLong()))
        avutil.av_image_fill_arrays(frame.data(), frame.linesize(), buffer, dstFormat, targetWidth, targetHeight, 1)
    }
    private val bufferBitmap = Bitmap().also {
        it.allocPixels(skiaFormat)
    }
    val composeImage = bufferBitmap.asComposeImageBitmap()

    fun cleanup() {
        avutil.av_frame_free(frame)
        avutil.av_frame_free(hwFrame)
        avcodec.avcodec_close(codecCtx)
        avcodec.avcodec_free_context(codecCtx)
    }

    private fun openDecoder(stream: Int, tryHardwareAcceleration: Boolean = true): AVCodecContext {
        //Prepare decoder
        val codecCtx = avcodec.avcodec_alloc_context3(null)
        println("Codec thread count: ${codecCtx.thread_count()}")

        //codecCtx.thread_count(4)
        avcodec.avcodec_parameters_to_context(codecCtx, fmtCtx.streams(stream).codecpar())
        val codec = avcodec.avcodec_find_decoder(codecCtx.codec_id())
            ?: throw IllegalStateException("No codec found!")
        println("Use codec: ${codec.name().getString(Charset.defaultCharset())}")

        if (tryHardwareAcceleration) {
            //Search HWAccel config for codec
            val hwConfig = avcodec.avcodec_get_hw_config(codec, 0)
            //avcodec.AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX
            val hwMethods = hwConfig.methods() and avcodec.AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX
            println("hwconfig methods: ${hwConfig.methods()} hw format: ${hwConfig.pix_fmt()}")
            hwFormat = hwConfig.pix_fmt()
            var hwType = codec.type()
            /*do {
                hwType = avutil.av_hwdevice_iterate_types(hwType)
                val name = avutil.av_hwdevice_get_type_name(hwType).getString(Charset.defaultCharset())
                println("decoder name: $name")
            } while (hwType != avutil.AV_HWDEVICE_TYPE_NONE)
            */
            hwType = avutil.av_hwdevice_iterate_types(hwType)
            val name = avutil.av_hwdevice_get_type_name(hwType).getString(Charset.defaultCharset())
            println("hwDecoder name: $name hw type: $hwType codec type: ${codec.type()}")

            val hwDeviceCtx = AVBufferRef()
            avutil.av_hwdevice_ctx_create(hwDeviceCtx, hwType, null as BytePointer?, null, 0)
            codecCtx.hw_device_ctx(avutil.av_buffer_ref(hwDeviceCtx))
        }
        if (avcodec.avcodec_open2(codecCtx, codec, null as PointerPointer<*>?) < 0)
            throw IllegalStateException("Can not open codec!")
        //println("HWaccel: $${codecCtx.hwaccel().name().getString(Charset.defaultCharset())}")
        println("Format: ${codecCtx.pix_fmt()} sw: ${codecCtx.sw_pix_fmt()}")
        return codecCtx
    }

    private val pkt = AVPacket()
    private var decodedFrameCounter = 0L
    private var bitmapFrameCounter = 0L
    private var currentImageTS: Long = 0L
    fun grabNextFrame(ts: Long) {
        //TODO seek to ts when diff is to high or negative diff
        /*if (frameCounter == 0) {
            avformat.av_seek_frame(fmtCtx, stream, 120, avformat.AVSEEK_FLAG_BACKWARD)
        }*/
        val timeDiff = currentImageTS - ts
        //println("pts: ${hwFrame.pts()} td: $timeDiff")
        if (timeDiff.absoluteValue > 500) {
            val seekPts = ts * timeBaseDen / timeBaseNum / 1000L
            println("Time diff: $timeDiff seek to $seekPts ${hwFrame.pts()}")
            avformat.av_seek_frame(fmtCtx, stream, seekPts, avformat.AVSEEK_FLAG_BACKWARD)
        } else if(timeDiff in 11..499) {
            return
        }

        var repeatCounter = 0
        do {
            do {
                val rf = avformat.av_read_frame(fmtCtx, pkt)
                if (rf < 0) return
            } while (pkt.stream_index() != stream)
            //println("receive frame")
            val r1 = avcodec.avcodec_send_packet(codecCtx, pkt)
            avcodec.av_packet_unref(pkt)
            //val r2 = avcodec.avcodec_receive_frame(codecCtx, frame)
            val r2 = avcodec.avcodec_receive_frame(codecCtx, hwFrame)
            decodedFrameCounter++
            if (r2 < 0) {
                println("ret1 $r1 ret2 $r2")
                println("Frame skipped!")
            }
            val frameTime = hwFrame.pts() * 1000L * timeBaseNum / timeBaseDen
            currentImageTS = frameTime
            //println("Frame: $keyFrame ts: $frameTime")

            val isPast = frameTime < ts
            /*println("Frame time: $frameTime ts: $ts")
            if (isPast) {
                println("Skip frame: $frame  ts: $ts  stream ts: $frameTime")
            }*/
            repeatCounter++ // read maximum number of frames
        } while (r2 < 0 || (isPast && repeatCounter < 10))
        val srcFrame = if (hwFrame.format() == hwFormat) {
            //Transfer data from hardware accel device to memory
            if (avutil.av_hwframe_transfer_data(frame, hwFrame, 0) < 0)
                throw IllegalStateException("Error transferring data to system memory!")
            frame
        } else {
            hwFrame
        }
        //println("Frame: ${hwFrame.best_effort_timestamp()} format: ${frame.format()} (hw:${hwFrame.format()})")

        val swsCtx = swscale.sws_getContext(
            codecCtx.width(), codecCtx.height(), srcFrame.format(), //Src size, format
            targetWidth, targetHeight, dstFormat, //Dst size, format
            swscale.SWS_BILINEAR, null, null, null as DoublePointer?
        )
        //println("Frame format: ${frame.format()}")
        val r3 = swscale.sws_scale(
            swsCtx, srcFrame.data(), srcFrame.linesize(),
            0, codecCtx.height(),
            pFrameRGB.data(),
            pFrameRGB.linesize()
        )
        if (r3 < 0) throw IllegalStateException("swscale error: $r3")
        // Copy data to java array
        pFrameRGB.data(0).get(jArray)
        // Copy java array to bitmap
        bufferBitmap.installPixels(jArray)
        bitmapFrameCounter++
    }
}
