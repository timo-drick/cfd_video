package de.appsonair

import de.appsonair.cppffmpeg.KAVFormatContext

val videoFile = "video file"

fun main() {
    val fmtCtx = KAVFormatContext()
    println("Open file: $videoFile")
    fmtCtx.openInput(videoFile)
    val streams = fmtCtx.findVideoStreams()
    println("Video streams:")
    streams.forEach { stream ->
        println("   $stream")
        val codec = fmtCtx.findCodec(stream)
        println("   $codec")
        println()
    }
    fmtCtx.closeInput()
}