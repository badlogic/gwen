package com.badlogicgames.gwen;

import ai.kitt.snowboy.SnowboyDetect
import java.io.Closeable
import java.io.File

interface HotwordDetector : Closeable {
    fun detect(): Boolean;
}

class SnowboyHotwordDetector : HotwordDetector {
    val audioRecorder: AudioRecorder;
    val detector: SnowboyDetect;

    constructor(audioRecorder: AudioRecorder, modelFile: String) {
        val osName = System.getProperty("os.name").toLowerCase();
        val osArch = System.getProperty("os.arch").toLowerCase();

        when {
            osName.contains("mac") -> System.load(File("jni/libsnowboy-detect-java.dylib").absolutePath);
            else -> System.load(File("jni/libsnowboy-detect-java.so").absolutePath);
        }

        this.audioRecorder = audioRecorder;
        this.detector = SnowboyDetect("models/snowboy/common.res", modelFile);
    }

    override fun detect(): Boolean {
        audioRecorder.read();
        val audioData = audioRecorder.getShortData();
        return detector.RunDetection(audioData, audioData.size) > 0;
    }

    override fun close() {
        detector.delete();
    }
}
