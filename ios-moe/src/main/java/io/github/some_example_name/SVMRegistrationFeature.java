package io.github.some_example_name;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/**
 * Registers the reflection and JNI access done by libGDX by default.
 * The relevant documentation is here:
 * for <a href="https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html">Feature</a>,
 * and for <a href="https://www.graalvm.org/latest/reference-manual/native-image/metadata">metadata in general</a>.
 * This class may need to be modified if you use additional JNI or reflective access.
 */
public class SVMRegistrationFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeJNIAccess.register(String.class);
        RuntimeJNIAccess.register(DoubleBuffer.class, IntBuffer.class, FloatBuffer.class, Buffer.class, LongBuffer.class,
            CharBuffer.class, ByteBuffer.class, ShortBuffer.class);
    }
}
