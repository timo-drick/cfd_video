package de.appsonair.cppffmpeg

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive


class FFmpegVideoPlayerState {
    private val kContext = KAVFormatContext()

    var time: Long = 0L
        internal set
    var progress: Float by mutableStateOf(0f)
        internal set
    var aspectRatio: Float by mutableStateOf(1f)
        internal set

    fun open(file: String) {
        kContext.openInput(file)
    }

    fun close() {
        kContext.closeInput()
    }

    fun streams(): List<KVideoStream> = kContext.findVideoStreams()
    fun codec(stream: KVideoStream): KAVCodec = kContext.findCodec(stream)

    fun openFrameGrabber(
        stream: KVideoStream,
        hwDecoder: KHWDecoder? = null,
        targetSize: IntSize? = null
    ) = KFrameGrabber(stream, kContext, hwDecoder, targetSize)

    fun closeFrameGrabber(frameGrabber: KFrameGrabber) {
        frameGrabber.close()
    }

    internal var seekPosition: Float = -1f

    fun seek(position: Float) {
        seekPosition = position
    }

    fun togglePause() {
        //TODO
    }

    init {
        println("New VideoPlayerState")
    }
}

@Composable
fun VideoPlayerFFMpeg(
    modifier: Modifier = Modifier,
    state: FFmpegVideoPlayerState = remember { FFmpegVideoPlayerState() },
    file: String
) {
    DisposableEffect(state) {

        onDispose {

        }
    }
    var frameTime: Long by remember { mutableStateOf(0L) }

    var frame by remember { mutableStateOf(0) }
    val videoImage = remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file, Dispatchers.IO) {
        state.open(file)
        val stream = state.streams().first()
        val codec = state.codec(stream)
        state.aspectRatio = stream.width.toFloat() / stream.height.toFloat()

        val frameGrabber = state.openFrameGrabber(stream, codec.hwDecoder.firstOrNull())
        var startTs = -1L
        while (isActive) {
            withFrameMillis { currentTs ->
                val newTs = currentTs
                if (startTs < 0) startTs = newTs
                if (state.seekPosition >= 0) {
                    //Set new start time
                    val seekMillis: Long = (stream.durationMillis * state.seekPosition.toDouble()).toLong()
                    startTs = newTs - seekMillis
                    state.seekPosition = -1f
                }
                val pos = newTs - startTs
                //println("Ts millis: $pos")
                frameGrabber.grabNextFrame(pos)
                videoImage.value = frameGrabber.composeImage
                state.time = pos
                state.progress = (pos.toDouble() / stream.durationMillis.toDouble()).toFloat()
                frame++
                /*if (frame % 60 == 0) {
                    val fps = 1f / ((newTs - ts).toFloat() / 1000f / 60f)
                    println("Frame: $frame fps: $fps")
                    ts = newTs
                }*/
            }
            //delay(5)
        }
        state.closeFrameGrabber(frameGrabber)
        state.close()
    }


    videoImage.value?.let { image ->
        Spacer(modifier.drawBehind {
            //val c = frame
            val scaleW = size.width / image.width.toFloat()
            val scaleH = size.height / image.height.toFloat()

            //val size = IntSize(size.width.toInt(), size.height.toInt())
            scale(scale = kotlin.math.min(scaleH, scaleW), pivot = Offset.Zero) {
                drawImage(image)
            }
        })
    }
}
