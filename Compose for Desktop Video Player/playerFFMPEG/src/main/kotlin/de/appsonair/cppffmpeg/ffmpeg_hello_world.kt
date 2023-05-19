package de.appsonair.cppffmpeg

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application



fun main() {
    val file = "sample video file.mp4"

    application {
        Window(onCloseRequest = ::exitApplication) {
            VideoPlayerFFMpeg(modifier = Modifier.fillMaxSize(), file = file)
        }
    }
}