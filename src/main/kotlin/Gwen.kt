@file:JvmName("Gwen")

package com.badlogicgames.gwen

import com.esotericsoftware.minlog.Log
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.*
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

val appPath: File by lazy {
    val path = File(HotwordDetector::class.java.protectionDomain.codeSource.location.toURI().path);
    val pathText = path.absolutePath.replace('\\', '/');
    when {
        pathText.endsWith("build/classes/main") -> path.parentFile.parentFile.parentFile
        pathText.endsWith("build/libs") -> path.parentFile.parentFile
        pathText.endsWith("bin") -> path.parentFile
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

data class GwenConfig(val clientId: String,
                      val clientSecret: String,
                      val playAudioLocally: Boolean,
                      val recordStereo: Boolean,
                      val pubSubPort: Int);

enum class GwenModelType (val id: Int) { Question(0), Command(1) }

data class GwenModel(val name: String, val file: String, val type: GwenModelType, @kotlin.jvm.Transient var detector: HotwordDetector);


class GwenEngine {
    @Volatile var running = false;
    @Volatile var models: Array<GwenModel> = emptyArray();
    var thread: Thread? = null;
    var pubSubServer: GwenPubSubServer? = null;

    @Synchronized fun start(config: GwenConfig, oauth: OAuth) {
        stop();
        try {
            val audioPlayer = if (config.playAudioLocally) LocalAudioPlayer(16000) else NullAudioPlayer();
            val audioRecorder = LocalAudioRecorder(16000, 1600, config.recordStereo);
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
                                    pubSubServer?.hotwordDetected(model.name, model.type.id);
                                    when (model.type) {
                                        GwenModelType.Question -> {
                                            Log.info("QA hotword detected, starting assistant conversation");
                                            // FIXME should we continue conversation?
                                            assistant.converse();
                                            Log.info("Conversation ended");
                                            Log.info("Waiting for hotword");
                                        }
                                        GwenModelType.Command -> {
                                            Log.info("Command hotword detected, starting speech-to-text");
                                            val command = assistant.speechToText();
                                            Log.info("Speech-to-text result: '$command'");
                                            Log.info("Waiting for hotword");
                                            if (!command.isEmpty()) pubSubServer?.command(model.name, command);
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } catch(t: Throwable) {
                    Log.error("An unexpected error occurred.", t);
                    running = false;
                } finally {
                    for (model in models) model.detector.close();
                    audioRecorder.close();
                    audioPlayer.close();
                    Log.info("Gwen stopped");
                }
            });
            thread.isDaemon = true;
            thread.name = "Gwen engine thread";
            this.thread = thread;
            pubSubServer = GwenPubSubServer(config.pubSubPort);
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

    @Synchronized fun  deleteModel(modelName: String) {
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

    fun stop() {
        if (running) {
            Log.info("Stopping Gwen");
            synchronized(this) {
                running = false;
            }
            thread?.join();
            pubSubServer?.close();
        }
    }
}

class GwenPubSubServer: Closeable {
    enum class MessageType(val id: Int) {
        HOTWORD(0),
        COMMAND(1)
    }

    val serverSocket: ServerSocket;
    val thread: Thread;
    val clients = mutableListOf<Socket>();
    @Volatile var running = true;

    constructor(port: Int) {
        serverSocket = ServerSocket(port);
        thread = Thread(fun () {
            while (running) {
                val client = serverSocket.accept();
                synchronized(clients) {
                    clients.add(client);
                }
                Log.info("New pub/sub client (${client.inetAddress.hostAddress})");
            }
        });
        thread.isDaemon = true;
        thread.name = "Pub/sub server thread";
        thread.start();
        Log.info("Started pub/sub server on port $port");
    }

    fun hotwordDetected(name: String, type: Int) {
        val bytes = ByteArrayOutputStream();
        val out = DataOutputStream(bytes);
        out.writeByte(MessageType.HOTWORD.id);
        val nameBytes = name.toByteArray();
        out.writeInt(nameBytes.size);
        out.write(nameBytes);
        out.writeInt(type);
        out.flush();
        broadcast(bytes.toByteArray());
    }

    fun command(name: String, text: String) {
        val bytes = ByteArrayOutputStream();
        val out = DataOutputStream(bytes);
        out.writeByte(MessageType.COMMAND.id);
        val nameBytes = name.toByteArray();
        out.writeInt(nameBytes.size);
        out.write(nameBytes);
        val textBytes = text.toByteArray();
        out.writeInt(textBytes.size);
        out.write(textBytes);
        out.flush();
        broadcast(bytes.toByteArray());
    }

    @Synchronized private fun broadcast(data: ByteArray) {
        for (client in clients) {
            try {
                client.outputStream.write(data);
            } catch(t: Throwable) {
                Log.info("Client ${client.inetAddress.hostAddress} disconnected");
            }
        }
    }

    override fun close() {
        synchronized(this) {
            if (running) {
                Log.info("Stopping pub/sub server");
                running = false;
                serverSocket.close();
                for (client in clients) client.close();
                thread.interrupt();
                thread.join();
            }
        }
    }
}

abstract class GwenPubSubClient: Closeable {
    val socket: Socket;
    val thread: Thread;
    @Volatile var running = true;

    constructor(host: String, port: Int) {
        socket = Socket(host, port);
        thread = Thread(fun() {
            val input = DataInputStream(socket.inputStream);
            Log.info("Started pub/sub client");
            while(running) {
                val typeId = input.readByte();
                when(typeId.toInt()) {
                    GwenPubSubServer.MessageType.HOTWORD.id -> {
                        val nameSize = input.readInt();
                        val bytes = ByteArray(nameSize);
                        input.readFully(bytes);
                        hotword(String(bytes), when(input.readInt()) {
                            0 -> GwenModelType.Question
                            1 -> GwenModelType.Command
                            else -> throw Exception("Unknown model type");
                        });
                    }
                    GwenPubSubServer.MessageType.COMMAND.id -> {
                        val nameSize = input.readInt();
                        val nameBytes = ByteArray(nameSize);
                        input.readFully(nameBytes);

                        val textSize = input.readInt();
                        val textBytes = ByteArray(textSize);
                        input.readFully(textBytes);

                        command(String(nameBytes), String(textBytes));
                    }
                    else -> throw Exception("Unknown message type $typeId")
                }
            }
        });
        thread.isDaemon = true;
        thread.name = "Pub/sub client thread";
        thread.start();
    }

    abstract fun hotword(name: String, type: GwenModelType);

    abstract fun command(name: String, text: String);

    override fun close() {
        synchronized(this) {
            if (running) {
                Log.info("Stopping pub/sub client");
                running = false;
                socket.close();
                thread.interrupt();
                thread.join();
            }
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
                gwen.start(config!!, oauth!!);
                Thread.sleep(1000);
                object : GwenPubSubClient("localhost", 8778) {
                    override fun hotword(name: String, type: GwenModelType) {
                        println("Pub/sub client received hotword $name $type");
                    }
                    override fun command(name: String, text: String) {
                        println("Pub/sub client received command $name $text");
                    }
                };
            } catch (t: Throwable) {
                Log.error("Couldn't start Gwen, setup through webinterface required", t);
            }
        }
    } catch (e: Throwable) {
        Log.error("Gwen stopped due to unrecoverable error", e);
    }
}
