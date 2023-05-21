package de.appsonair.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.appsonair.cppffmpeg.FFmpegVideoPlayerState
import de.appsonair.cppffmpeg.VideoPlayerFFMpeg
import java.io.File



val videoFile = File("sample video file.MP4")

@Composable
fun App() {
    MaterialTheme {
        val videoPlayerState = remember { FFmpegVideoPlayerState() }
        Box(Modifier.fillMaxHeight()
            .aspectRatio(videoPlayerState.aspectRatio)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { videoPlayerState.togglePause() }
                )
            }
        ) {
            VideoPlayerFFMpeg(
                modifier = Modifier.fillMaxSize(),
                state = videoPlayerState,
                file = videoFile.toString()
            )
            var thumbPosition by remember { mutableStateOf(0f) }
            LaunchedEffect(videoPlayerState) {
                snapshotFlow { videoPlayerState.progress }.collect {
                    thumbPosition = it
                }
            }
            Slider(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                value = thumbPosition,
                onValueChange = {
                    thumbPosition = it
                    videoPlayerState.seek(it)
                },
                valueRange = 0f..1f
            )
            Box(Modifier.align(Alignment.TopStart).background(Color.LightGray.copy(alpha = 0.5f)).padding(8.dp)) {
                Column {
                    videoPlayerState.frameGrabber?.let { kFrameGrabber ->
                        val stream = kFrameGrabber.kStream
                        Text("Dimension: ${stream.width}x${stream.height}")
                        Text("Bitrate: ${"%.2f".format((stream.bitrate / 1024L).toFloat() / 1024f)} mBit/s")
                        Text("Codec: ${stream.codec}")
                        Text("HW Decoder: ${kFrameGrabber.hwDecoder?.name ?: "-"}")
                        Text("Stream FPS: ${"%.2f".format(stream.fps)}")
                        Text("Decoded FPS: ${"%.2f".format(videoPlayerState.decodedFPS)}")
                        Spacer(Modifier.height(8.dp))
                        Text("Display size: ${kFrameGrabber.targetWidth}x${kFrameGrabber.targetHeight}")
                        Text("Display FPS: ${"%.2f".format(videoPlayerState.displayFPS)}")
                    }
                    Spacer(Modifier.height(16.dp))
                    videoPlayerState.metadata.forEach { (key, value) ->
                        Row {
                            Text("$key:")
                            Spacer(Modifier.width(8.dp))
                            Text(value)
                        }
                    }
                }
            }
        }
    }
}


fun main() {
    application {
        Window(onCloseRequest = ::exitApplication) {
            App()
        }
    }
}
