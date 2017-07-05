@file:JvmName("Gwen")

package com.badlogicgames.gwen

import com.esotericsoftware.minlog.Log
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.NetworkInterface

val appPath: File by lazy {
    val path = File(HotwordDetector::class.java.protectionDomain.codeSource.location.toURI().path);
    when {
        path.absolutePath.endsWith("build/classes/main") -> path.parentFile.parentFile.parentFile
        path.absolutePath.endsWith("build/libs") -> path.parentFile.parentFile
        path.absolutePath.endsWith("bin") -> path.parentFile
        else -> path
    }
}

class Logger : Log.Logger {
    val writer: FileWriter;

    constructor(file: File) {
        writer = FileWriter(file, true);
    }

    override fun print(message: String?) {
        super.print(message)
        writer.write(message);
        writer.flush();
    }
}

val logger by lazy { Logger(File(appPath, "/log.txt")); }

data class GwenStatus(val needsClientId: Boolean,
                      val needsAuthorization: Boolean,
                      val authorizationUrl: String?,
                      val isRunning: Boolean);

data class GwenConfig(val clientId: String, val clientSecret: String);

class GwenEngine {
    @Volatile var running = false;
    var thread: Thread? = null;

    @Synchronized fun start(oauth: OAuth) {
        stop();

        try {
            val audioPlayer = LocalAudioPlayer(16000);
            val audioRecorder = LocalAudioRecorder(16000, 1600);
            val oauth = oauth;
            val commandDetector = SnowboyHotwordDetector(File(appPath.absolutePath, "assets/snowboy/alexa.umdl"));
            val qaDetector = SnowboyHotwordDetector(File(appPath.absolutePath, "assets/snowboy/snowboy.umdl"));
            val assistant = GoogleAssistant(oauth, audioRecorder, audioPlayer);
            val thread = Thread(fun() {
                try {
                    Log.info("Gwen started");
                    running = true;
                    while (running) {
                        audioRecorder.read();
                        if (qaDetector.detect(audioRecorder.getShortData())) {
                            Log.info("QA hotword detected, starting assistant conversation");
                            while (assistant.converse()) {
                                Log.info("Continuing conversation");
                            }
                            Log.info("Conversation ended");
                            Log.info("Waiting for hotword");
                        }
                        if (commandDetector.detect(audioRecorder.getShortData())) {
                            Log.info("Command hotword detected, starting speech-to-text");
                            val command = assistant.speechToText();
                            Log.info("Speech-to-text result: '${command}'");
                            Log.info("Waiting for hotword");
                        }
                    }
                } catch(t: Throwable) {
                    running = false;
                } finally {
                    commandDetector.close();
                    qaDetector.close();
                    audioRecorder.close();
                    audioPlayer.close();
                    Log.info("Gwen stopped");
                }
            });
            thread.isDaemon = true;
            this.thread = thread;
            thread.start();
        } catch (t: Throwable) {
            Log.error("Couldn't reload Gwen", t);
        }
    }

    @Synchronized fun stop() {
        if (running) {
            running = false;
            val thread = this.thread;
            thread?.join(10000);
        }
    }
}

var oauth: OAuth? = null;

var config: GwenConfig? = null;

val gwen = GwenEngine();

fun loadConfig(): GwenConfig? {
    try {
        val configFile = File(appPath, "gwen.json");
        if (!configFile.exists()) {
            Log.debug("No config file found");
            return null;
        } else {
            Log.debug("Loading config")
            return Gson().fromJson<GwenConfig>(JsonReader(FileReader(File(appPath, "gwen.json"))), GwenConfig::class.java);
        }
    } catch (e: Throwable) {
        Log.error("Error loading config", e);
        return null;
    }
}

fun loadOAuth(config: GwenConfig): OAuth {
    val config = OAuthConfig("https://www.googleapis.com/oauth2/v4/",
            config.clientId,
            config.clientSecret,
            File(appPath, "credentials.json"),
            "https://www.googleapis.com/auth/assistant-sdk-prototype",
            "urn:ietf:wg:oauth:2.0:oob",
            "https://accounts.google.com/o/oauth2/v2/auth");
    val oauth = OAuth(config);
    if (oauth.isAuthorized()) {
        try {
            oauth.getCredentials();
        } catch (t: Throwable) {
            Log.error("Couldn't authorize", t);
        }
    }
    return oauth;
}

private fun printWebInterfaceUrl() {
    for (itf in NetworkInterface.getNetworkInterfaces()) {
        if (itf.isLoopback) continue;
        for (addr in itf.inetAddresses) {
            // Because we don't like IPV6 and don't know how to get the local IP address...
            if (addr.hostAddress.startsWith("192") || addr.hostAddress.startsWith("10"))
                println("http://${addr.hostName}:8777/");
        }
    }
}

fun main(args: Array<String>) {
    try {
        Log.set(Log.LEVEL_DEBUG);
        Log.setLogger(logger);

        config = loadConfig();
        config?.let { oauth = loadOAuth(it); };

        if (config == null || oauth == null || !oauth!!.isAuthorized()) {
            startWebInterface();
            println("Setup through web interface required (http://<local-ip-address>:8777)");
            printWebInterfaceUrl();
        } else {
            startWebInterface();
            try {
                gwen.start(oauth!!);
            } catch (t: Throwable) {
                Log.error("Couldn't start Gwen, setup through webinterface required", t);
            }
        }
    } catch (e: Throwable) {
        Log.error("Gwen stopped due to unrecoverable error", e);
    }
}
