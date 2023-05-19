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
import kotlinx.coroutines.Dispatchers

interface VideoPlayerState {
    val time: Long //Timestamp milliseconds
    val progress: Float //from 0 to 1 -> 0 is start and 1 is end
    val aspectRatio: Float
    fun seek(position: Float)
    fun togglePause()

}

class FFmpegVideoPlayerState: VideoPlayerState {
    private val decoder = FFMPEGVideoDecoder()

    override var time: Long = 0L
        internal set
    override var progress: Float by mutableStateOf(0f)
        internal set
    override var aspectRatio: Float by mutableStateOf(1f)
        internal set

    fun open(file: String): MediaFile = decoder.open(file)

    internal var seekPosition: Float = -1f

    override fun seek(position: Float) {
        seekPosition = position
    }

    override fun togglePause() {
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
        val media = state.open(file)
        val stream = media.streams.first()
        state.aspectRatio = stream.width.toFloat() / stream.height.toFloat()
        val frameGrabber = media.openFrameGrabber(stream, divider = 1, hardwareAcceleration = true)
        var startTs = -1L
        while (true) {
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
                frameGrabber.nextFrame(pos)
                videoImage.value = frameGrabber.imageBitmap
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

        //media.closeFrameGrabber()
        //decoder.close(media)
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
