# Compose for Desktop FFmpeg Player

This code serves as a proof of concept demonstrating the usage of the FFmpeg library
to implement a video player in Compose for Desktop. It relies on the 
'javacpp-presets ffmpeg' Java library, which can be found 
[here](https://github.com/bytedeco/javacpp-presets/tree/master/ffmpeg).

Real-time playback is achievable when the video resolution is 1080p and the frame rate
ranges from 30fps to 60fps. However, for higher resolutions, smooth playback is only
possible when the displayed resolution is scaled down.
This implementation leverages hardware acceleration through FFmpeg for video decoding.
You can observe these playback
scenarios in the provided demonstration video: 
![Demonstration video](doc/testing_different_resolution_fps.mp4).

Please note that my display is limited to  60fps.

## Implementation Details

Previous implementations used an AWT canvas to display the video, making it 
challenging to integrate with Compose UI due to the inability to overlay Compose 
components onto the video.

In this proof of concept, the image data from the video library is copied into JVM
memory and then transferred to a Skia bitmap. This allows the video to be used as
a regular Compose image, facilitating easy integration with the UI.

## Performance Considerations

One limitation of this implementation is the significant amount of data movement
in memory, especially for large resolution videos. It would be beneficial to 
explore more efficient solutions for transferring image data to Skia bitmap memory
or consider enabling direct data access via a Skia shader.