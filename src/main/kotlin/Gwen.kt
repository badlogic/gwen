@file:JvmName("Gwen")

package com.badlogicgames.gwen

import ai.kitt.snowboy.SnowboyDetect
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader

data class GwenConfig (val clientId: String, val clientSecret: String)

fun oauth(): OAuth {
    val gwenConfig: GwenConfig = Gson().fromJson(JsonReader(FileReader("${System.getProperty("user.home")}/gwen.json")), GwenConfig::class.java);
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
    val hotwordDetector = SnowboyHotwordDetector(audioRecorder, "models/snowboy/alexa.umdl");
    val assistant = GoogleAssistant(oauth, audioRecorder, audioPlayer);

    println("Say 'Alexa' to start a query");
    while (true) {
        if (hotwordDetector.detect()) {
            println("What's up?");
            assistant.converse();
            println("Say 'Alexa' to start a query");
        }
    }
}

fun main(args: Array<String>) {
    // snowball();
    // oauth();
    assistant();
}
