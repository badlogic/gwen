@file:JvmName("Gwen")
package com.badlogicgames.gwen

import ai.kitt.snowboy.SnowboyDetect
import java.io.File

fun main(args: Array<String>) {
    val osName = System.getProperty("os.name").toLowerCase();
    val osArch = System.getProperty("os.arch").toLowerCase();

    println("OS name: ${osName}");
    println("OS arch: ${osArch}");

    when {
        osName.contains("mac") -> System.load(File("jni/libsnowboy-detect-java.dylib").absolutePath);
        else -> System.load(File("jni/libsnowboy-detect-java.so").absolutePath);
    }

    val detector = SnowboyDetect("models/snowboy/common.res", "models/snowboy/OK Google.pmdl");
    val recorder = AudioRecorder(16000f);

    while (true) {
        val data = recorder.read();
        val result = detector.RunDetection(data, data.size);
        if (result > 0) {
            println("Hotword ${result} detected!");
        }
    }
}
