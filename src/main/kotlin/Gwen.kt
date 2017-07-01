@file:JvmName("Gwen")
package com.badlogicgames.gwen

import ai.kitt.snowboy.SnowboyDetect

fun main(args: Array<String>) {
    val osName = System.getProperty("os.name").toLowerCase();
    val osArch = System.getProperty("os.arch").toLowerCase();

    println("OS name: ${osName}");
    println("OS arch: ${osArch}");

    when {
        osName.contains("mac") -> System.load("jni/libsnowboy-detect-java.dylib");
        else -> System.load("jni/libsnowboy-detect-java.so");
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
