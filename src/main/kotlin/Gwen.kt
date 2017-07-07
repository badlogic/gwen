@file:JvmName("Gwen")

package com.badlogicgames.gwen

import com.esotericsoftware.minlog.Log
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.net.NetworkInterface

val appPath: File by lazy {
    val path = File(HotwordDetector::class.java.protectionDomain.codeSource.location.toURI().path);
    when {
        path.absolutePath.endsWith("build/classes/main") -> path.parentFile.parentFile.parentFile
        path.absolutePath.endsWith("build/libs") -> path.parentFile.parentFile
        path.absolutePath.endsWith("bin") -> path.parentFile
        !path.isDirectory -> path.parentFile
        else -> path
    }
}

class Logger : Log.Logger {
    val writer: FileWriter;
    val logs = mutableListOf<Pair<Long, String>>();

    constructor(file: File) {
        writer = FileWriter(file, true);
    }

    @Synchronized override fun print(message: String) {
        super.print(message)
        writer.write(message);
        writer.flush();
        logs.add(Pair(System.currentTimeMillis(), message));
    }

    @Synchronized fun getSince(timeStamp: Long): List<Pair<Long, String>> {
        return logs.filter { it.first >= timeStamp }
    }
}

val logger by lazy { Logger(File(appPath, "/log.txt")); }

data class GwenStatus(val needsClientId: Boolean,
                      val needsAuthorization: Boolean,
                      val authorizationUrl: String?,
                      val isRunning: Boolean);

data class GwenConfig(val clientId: String, val clientSecret: String);

enum class GwenModelType { Question, Command }

data class GwenModel(val name: String, val file: String, val type: GwenModelType, @kotlin.jvm.Transient var detector: HotwordDetector);


class GwenEngine {
    @Volatile var running = false;
    @Volatile var models: Array<GwenModel> = emptyArray();
    var thread: Thread? = null;

    @Synchronized fun start(oauth: OAuth) {
        stop();

        try {
            val audioPlayer = LocalAudioPlayer(16000);
            val audioRecorder = LocalAudioRecorder(16000, 1600);
            models = loadModels();
            val assistant = GoogleAssistant(oauth, audioRecorder, audioPlayer);
            val thread = Thread(fun() {
                try {
                    Log.info("Gwen started");
                    running = true;
                    while (running) {
                        audioRecorder.read();
                        synchronized(this) {
                            for (model in models) {
                                if (model.detector.detect(audioRecorder.getShortData())) {
                                    when (model.type) {
                                        GwenModelType.Question -> {
                                            Log.info("QA hotword detected, starting assistant conversation");
                                            while (assistant.converse()) {
                                                Log.info("Continuing conversation");
                                            }
                                            Log.info("Conversation ended");
                                            Log.info("Waiting for hotword");
                                        }
                                        GwenModelType.Command -> {
                                            Log.info("Command hotword detected, starting speech-to-text");
                                            val command = assistant.speechToText();
                                            Log.info("Speech-to-text result: '$command'");
                                            Log.info("Waiting for hotword");
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } catch(t: Throwable) {
                    running = false;
                } finally {
                    for (model in models) model.detector.close();
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

    private fun  loadModels(): Array<GwenModel> {
        val modelConfig = File(appPath, "models.json");
        val models: Array<GwenModel>;
        if (!modelConfig.exists()) {
            Log.info("Loading default models");
            models = arrayOf(
                    GwenModel("Snowboy", "assets/snowboy/snowboy.umdl", GwenModelType.Command, SnowboyHotwordDetector(File(appPath, "assets/snowboy/snowboy.umdl"))),
                    GwenModel("Alexa", "assets/snowboy/alexa.umdl", GwenModelType.Question, SnowboyHotwordDetector(File(appPath, "assets/snowboy/alexa.umdl")))
            );
            FileWriter(modelConfig).use {
                Gson().toJson(models, it);
            }
            return models;
        } else {
            models = Gson().fromJson<Array<GwenModel>>(JsonReader(FileReader(File(appPath, "models.json"))), Array<GwenModel>::class.java);
            for (model in models) {
                Log.info("Loading model ${model.name} (${model.type})")
                model.detector = SnowboyHotwordDetector(File(appPath, model.file));
            }
            return models;
        }
    }

    @Synchronized fun addModel(name: String, fileName: String, type: GwenModelType, modelData: ByteArray) {
        Log.info("Adding model $name, $type");

        val userModelsDir = File(appPath, "usermodels");
        if (!userModelsDir.exists()) userModelsDir.mkdirs();

        val modelFile = File(userModelsDir, fileName);

        FileOutputStream(modelFile).use {
            it.write(modelData)
        }

        val newModels = models.toMutableList();
        newModels.add(GwenModel(name, "usermodels/$fileName", type, SnowboyHotwordDetector(modelFile)))
        val modelConfig = File(appPath, "models.json");
        FileWriter(modelConfig).use {
            Gson().toJson(newModels, it);
        }
        models = newModels.toTypedArray();
    }

    fun  deleteModel(modelName: String) {
        Log.info("Deleting model $modelName");

        val newModels = models.toMutableList();
        newModels.removeIf() {
            if(it.name.equals(modelName)) {
                it.detector.close();
            }
            it.name.equals(modelName);
        }
        val modelConfig = File(appPath, "models.json");
        FileWriter(modelConfig).use {
            Gson().toJson(newModels, it);
        }
        models = newModels.toTypedArray();
    }

    @Synchronized fun stop() {
        Log.info("Stopping Gwen");
        if (running) {
            running = false;
            val thread = this.thread;
            thread?.join();
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
    val oAuthConfig = OAuthConfig("https://www.googleapis.com/oauth2/v4/",
            config.clientId,
            config.clientSecret,
            File(appPath, "credentials.json"),
            "https://www.googleapis.com/auth/assistant-sdk-prototype",
            "urn:ietf:wg:oauth:2.0:oob",
            "https://accounts.google.com/o/oauth2/v2/auth");
    val oauth = OAuth(oAuthConfig);
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
            printWebInterfaceUrl();
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
