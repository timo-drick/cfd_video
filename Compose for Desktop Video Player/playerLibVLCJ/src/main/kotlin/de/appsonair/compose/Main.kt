package de.appsonair.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File



val videoFile = File("sample video file.MP4")

@Composable
fun App() {
    MaterialTheme {
        val videoPlayerState = remember { VlcjVideoPlayerState() }
        Box(Modifier.fillMaxHeight()
            .aspectRatio(videoPlayerState.aspectRatio)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { videoPlayerState.togglePause() }
                )
            }
        ) {
            VideoPlayerDirect(
                modifier = Modifier.fillMaxSize(),
                state = videoPlayerState,
                url = videoFile.toString()
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
