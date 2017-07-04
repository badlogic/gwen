package com.badlogicgames.gwen;

import ai.kitt.snowboy.SnowboyDetect
import java.io.Closeable
import java.io.File

interface HotwordDetector : Closeable {
    fun detect(audioData: ShortArray): Boolean;
}

class SnowboyHotwordDetector : HotwordDetector {
    val detector: SnowboyDetect;

    constructor(modelFile: String) {
        val osName = System.getProperty("os.name").toLowerCase();

        when {
            osName.contains("mac") -> System.load(File("jni/libsnowboy-detect-java.dylib").absolutePath);
            else -> System.load(File("jni/libsnowboy-detect-java.so").absolutePath);
        }
        this.detector = SnowboyDetect("assets/snowboy/common.res", modelFile);
    }

    override fun detect(audioData: ShortArray): Boolean {
        return detector.RunDetection(audioData, audioData.size) > 0;
    }

    override fun close() {
        detector.delete();
    }
}
