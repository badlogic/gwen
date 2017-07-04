@file:JvmName("Gwen")

package com.badlogicgames.gwen

import ai.kitt.snowboy.SnowboyDetect
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader

data class GwenConfig (var clientId: String, var clientSecret: String, var oauthConfig: OAuthConfig)

val gwenConfig: GwenConfig by lazy {
    Gson().fromJson<GwenConfig>(JsonReader(FileReader("${System.getProperty("user.home")}/gwen.json")), GwenConfig::class.java);
}

fun oauth(): OAuth {
    val config = OAuthConfig("https://www.googleapis.com/oauth2/v4/",
            gwenConfig.clientId,
            gwenConfig.clientSecret,
            "credentials.json",
            "https://www.googleapis.com/auth/assistant-sdk-prototype",
            "urn:ietf:wg:oauth:2.0:oob",
            "https://accounts.google.com/o/oauth2/v2/auth");
    val oauth = OAuth(config);
    if (!oauth.isAuthorized()) {
        oauth.commandLineRequestFlow();
    } else {
        oauth.getCredentials();
    }
    return oauth;
}

fun assistant() {
    val audioRecorder = LocalAudioRecorder(16000, 1600);
    val audioPlayer = LocalAudioPlayer(16000);
    val oauth = oauth();
    val commandHotword = SnowboyHotwordDetector("models/snowboy/alexa.umdl");
    val qaHotword = SnowboyHotwordDetector("models/snowboy/OK Google.pmdl");
    val assistant = GoogleAssistant(oauth, audioRecorder, audioPlayer);

    val prompt = "Say 'OK Google' to start a query, 'Alexa' to ask a question"
    println(prompt);
    while (true) {
        audioRecorder.read();
        if (qaHotword.detect(audioRecorder.getShortData())) {
            println("What's up?");
            while (assistant.converse()) {
                println("Continuing conversation");
            }
            println(prompt);
        }
        if (commandHotword.detect(audioRecorder.getShortData())) {
            println("At your command");
            val command = assistant.speechToText();
            println("Command: ${command}")
            println(prompt)
        }
    }
}

fun main(args: Array<String>) {
    val config = gwenConfig;
    // snowball();
    // oauth();
    assistant();
}
