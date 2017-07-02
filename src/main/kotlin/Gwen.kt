@file:JvmName("Gwen")

package com.badlogicgames.gwen

import ai.kitt.snowboy.SnowboyDetect
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader

data class GwenConfig (val clientId: String, val clientSecret: String)

fun snowball() {
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

fun oauth() {
    val gwenConfig: GwenConfig = Gson().fromJson(JsonReader(FileReader("${System.getProperty("user.home")}/gwen.json")), GwenConfig::class.java);
    val config = OAuthConfig("https://www.googleapis.com/oauth2/v4/",
            gwenConfig.clientId,
            gwenConfig.clientSecret,
            "credentials.json",
            "https://www.googleapis.com/auth/assistant-sdk-prototype",
            "urn:ietf:wg:oauth:2.0:oob",
            "https://accounts.google.com/o/oauth2/v2/auth");
    val oAuth = OAuth(config);
    if (!oAuth.isAuthorized()) {
        oAuth.commandLineRequestFlow();
    } else {
        println("Already authorized");
    }
}

fun main(args: Array<String>) {
    // snowball();
    oauth();
}
