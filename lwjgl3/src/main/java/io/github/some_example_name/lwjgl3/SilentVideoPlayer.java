// Must be in com.badlogic.gdx.video to access package-private createMusic()
package com.badlogic.gdx.video;

import com.badlogic.gdx.audio.Music;
import java.nio.ByteBuffer;

// RawMusic.setup() is incompatible with libgdx 1.13.x; skip audio for background video
public class SilentVideoPlayer extends VideoPlayerDesktop {
    @Override
    Music createMusic(VideoDecoder decoder, ByteBuffer audioBuffer,
                      int audioChannels, int sampleRate) {
        return null;
    }
}
