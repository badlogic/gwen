@file:JvmName("Gwen")

package com.badlogicgames.gwen

import com.esotericsoftware.minlog.Log.*
import com.esotericsoftware.minlog.Log
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.*
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import utils.MultiplexOutputStream
import java.lang.Exception
import java.net.InetSocketAddress

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
    val logs = mutableListOf<Pair<Long, String>>();

	constructor () {
	}

	@Synchronized override fun print(message: String) {
        super.print(message)
		 if (logs.size > 20000) logs.removeAt(0);
        logs.add(Pair(System.currentTimeMillis(), message));
    }

    @Synchronized fun getSince(timeStamp: Long): List<Pair<Long, String>> {
        return logs.filter { it.first >= timeStamp }
    }
}

val logger by lazy { Logger(); }

data class GwenConfig(val clientId: String,
                      val clientSecret: String,
                      val playAudioLocally: Boolean,
                      val recordStereo: Boolean,
                      val pubSubPort: Int = 8778,
                      val websocketPubSubPort: Int = 8779);

data class GwenModel(val name: String, val file: String, val type: GwenModelType, @kotlin.jvm.Transient var detector: HotwordDetector);


class GwenEngine {
    @Volatile var running = false;
    @Volatile var models: Array<GwenModel> = emptyArray();
    var thread: Thread? = null;
    var pubSubServer: GwenPubSubServer? = null;

    fun start(config: GwenConfig, oauth: OAuth) {
        stop();
        synchronized (this) {
	        try {
	            val audioPlayer = if (config.playAudioLocally) LocalAudioPlayer(16000) else NullAudioPlayer();
	            val audioRecorder = LocalAudioRecorder(16000, 1600, config.recordStereo);
	            models = loadModels();
	            val assistant = GoogleAssistant(oauth, audioRecorder, audioPlayer);
	            val thread = Thread(fun() {
	                try {
	                    info("Gwen started");
	                    running = true;
	                    while (running) {
	                        audioRecorder.read();
                            pubSubServer?.audioInput(audioRecorder.getByteData());
	                        synchronized(this) {
	                            for (model in models) {
	                                if (model.detector.detect(audioRecorder.getShortData())) {
	                                    pubSubServer?.hotwordDetected(model.name, model.type);
	                                    when (model.type) {
	                                        GwenModelType.Question -> {
	                                            info("QA hotword detected, starting assistant conversation");
	                                            // FIXME should we continue conversation?
	                                            assistant.converse(object: GoogleAssistant.GoogleAssistantCallback {
                                                    override fun questionComplete(question: String) {
                                                        pubSubServer?.question(model.name, question);
                                                    }

                                                    override fun answerAudio(audio: ByteArray) {
                                                        pubSubServer?.questionAnswerAudio(model.name, audio);
                                                    }
                                                });
                                                info("Conversation ended");
                                                pubSubServer?.questionEnd(model.name);
	                                            info("Waiting for hotword");
	                                        }
	                                        GwenModelType.Command -> {
	                                            info("Command hotword detected, starting speech-to-text");
	                                            val command = assistant.speechToText();
	                                            info("Speech-to-text result: '$command'");
	                                            info("Waiting for hotword");
	                                            if (!command.isEmpty()) pubSubServer?.command(model.name, command);
	                                        }
	                                    }
	                                    break;
	                                }
	                            }
	                        }
	                    }
	                } catch(t: Throwable) {
	                    error("An unexpected error occurred.", t);
	                    running = false;
	                } finally {
	                    for (model in models) model.detector.close();
	                    audioRecorder.close();
	                    audioPlayer.close();
	                    info("Gwen stopped");
	                }
	            });
	            thread.isDaemon = true;
	            thread.name = "Gwen engine thread";
	            this.thread = thread;
	            pubSubServer = GwenComposablePubSubServer(
                        GwenTCPPubSubServer(config.pubSubPort),
                        GwenWebSocketPubSubServer(config.websocketPubSubPort)
                );
	            thread.start();
	        } catch (t: Throwable) {
	            error("Couldn't reload Gwen", t);
	        }
        }
    }

    private fun loadModels(): Array<GwenModel> {
        val modelConfig = File(appPath, "models.json");
        val models: Array<GwenModel>;
        if (!modelConfig.exists()) {
            info("Loading default models");
            if (System.getProperty("os.name").contains("Windows")) {
            	models = arrayOf(
            		GwenModel("Web Command", "", GwenModelType.Command, WebHotwordDetector()),
            		GwenModel("Web Question", "", GwenModelType.Question, WebHotwordDetector())
            		);
				} else {
					models = arrayOf(
            		    GwenModel("Web Command", "", GwenModelType.Command, WebHotwordDetector()),
            		    GwenModel("Web Question", "", GwenModelType.Question, WebHotwordDetector()),
						GwenModel("Snowboy", "assets/snowboy/snowboy.umdl", GwenModelType.Command, SnowboyHotwordDetector(File(appPath, "assets/snowboy/snowboy.umdl"))),
						GwenModel("Alexa", "assets/snowboy/alexa.umdl", GwenModelType.Question, SnowboyHotwordDetector(File(appPath, "assets/snowboy/alexa.umdl")))
						);
				}
            FileWriter(modelConfig).use {
                Gson().toJson(models, it);
            }
            return models;
        } else {
            models = Gson().fromJson<Array<GwenModel>>(JsonReader(FileReader(File(appPath, "models.json"))), Array<GwenModel>::class.java);
            for (model in models) {
                info("Loading model ${model.name} (${model.type})")
					if (model.file.endsWith(".umdl") || model.file.endsWith(".pmdl"))
						model.detector = SnowboyHotwordDetector(File(appPath, model.file));
					else
						model.detector = WebHotwordDetector();
            }
            return models;
        }
    }

    @Synchronized fun addModel(name: String, fileName: String, type: GwenModelType, modelData: ByteArray) {
        info("Adding model $name, $type");

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

    @Synchronized fun deleteModel(modelName: String) {
        info("Deleting model $modelName");

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
    
    @Synchronized fun triggerModel(modelName: String) {
   	 info("Triggering model $modelName");
   	 
   	 val newModels = models.toMutableList();
   	 newModels.removeIf() {
   		 if(it.name.equals(modelName)) {
   			 it.detector.trigger()
   		 }
   		 it.name.equals(modelName);
   	 }
    }

    fun stop() {
        if (running) {
            info("Stopping Gwen");
            synchronized(this) {
                running = false;
            }
            thread?.join();
            pubSubServer?.close();
        }
    }
}

interface GwenPubSubServer: Closeable {
    fun hotwordDetected(name: String, type: GwenModelType);
    fun command(name: String, text: String);
    fun question(name: String, question: String);
    fun questionAnswerAudio(name: String, audio: ByteArray);
    fun questionEnd(name: String);
    fun audioInput(audio: ByteArray);
    fun broadcast(data: ByteArray);
}

abstract class GwenBasePubSubServer: GwenPubSubServer {
    override fun hotwordDetected(name: String, type: GwenModelType) {
        val bytes = ByteArrayOutputStream();
        val out = DataOutputStream(bytes);
        out.writeByte(GwenPubSubMessageType.HOTWORD.id);
        val nameBytes = name.toByteArray();
        out.writeInt(nameBytes.size);
        out.write(nameBytes);
        out.writeInt(type.id);
        out.flush();
        broadcast(bytes.toByteArray());
    }

    override fun command(name: String, text: String) {
        val bytes = ByteArrayOutputStream();
        val out = DataOutputStream(bytes);
        out.writeByte(GwenPubSubMessageType.COMMAND.id);
        val nameBytes = name.toByteArray();
        out.writeInt(nameBytes.size);
        out.write(nameBytes);
        val textBytes = text.toByteArray();
        out.writeInt(textBytes.size);
        out.write(textBytes);
        out.flush();
        broadcast(bytes.toByteArray());
    }

    override fun question(name: String, question: String) {
        val bytes = ByteArrayOutputStream();
        val out = DataOutputStream(bytes);
        out.writeByte(GwenPubSubMessageType.QUESTION.id);
        val nameBytes = name.toByteArray();
        out.writeInt(nameBytes.size);
        out.write(nameBytes);
        val textBytes = question.toByteArray();
        out.writeInt(textBytes.size);
        out.write(textBytes);
        out.flush();
        broadcast(bytes.toByteArray());
    }

    override fun questionAnswerAudio(name: String, audio: ByteArray) {
        val bytes = ByteArrayOutputStream();
        val out = DataOutputStream(bytes);
        out.writeByte(GwenPubSubMessageType.QUESTION_ANSWER_AUDIO.id);
        val nameBytes = name.toByteArray();
        out.writeInt(nameBytes.size);
        out.write(nameBytes);
        out.writeInt(audio.size);
        out.write(audio);
        out.flush();
        broadcast(bytes.toByteArray());
    }

    override fun questionEnd(name: String) {
        val bytes = ByteArrayOutputStream();
        val out = DataOutputStream(bytes);
        out.writeByte(GwenPubSubMessageType.QUESTION_END.id);
        val nameBytes = name.toByteArray();
        out.writeInt(nameBytes.size);
        out.write(nameBytes);
        out.flush();
        broadcast(bytes.toByteArray());
    }

    override fun audioInput(audio: ByteArray) {
        val bytes = ByteArrayOutputStream();
        val out = DataOutputStream(bytes);
        out.writeByte(GwenPubSubMessageType.AUDIO_INPUT.id);
        out.writeInt(audio.size);
        out.write(audio);
        out.flush();
        broadcast(bytes.toByteArray());
    }
}

class GwenComposablePubSubServer: GwenPubSubServer {
    private val servers: Array<out GwenPubSubServer>;

    constructor(vararg servers: GwenPubSubServer) {
        this.servers = servers;
    }

    override fun hotwordDetected(name: String, type: GwenModelType) {
        for (server in servers) server.hotwordDetected(name, type);
    }

    override fun command(name: String, text: String) {
        for (server in servers) server.command(name, text);
    }

    override fun question(name: String, question: String) {
        for (server in servers) server.question(name, question);
    }

    override fun questionAnswerAudio(name: String, audio: ByteArray) {
        for (server in servers) server.questionAnswerAudio(name, audio);
    }

    override fun questionEnd(name: String) {
        for (server in servers) server.questionEnd(name);
    }

    override fun audioInput(audio: ByteArray) {
        for (server in servers) server.audioInput(audio);
    }

    override fun close() {
        for (server in servers) server.close();
    }

    override fun broadcast(data: ByteArray) {
        for (server in servers) server.broadcast(data);
    }
}

class GwenTCPPubSubServer: GwenBasePubSubServer {
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
                    client.tcpNoDelay = true;
                    clients.add(client);
                }
                info("New pub/sub client (${client.inetAddress.hostAddress})");
            }
        });
        thread.isDaemon = true;
        thread.name = "Pub/sub server thread";
        thread.start();
        info("Started pub/sub server on port $port");
    }

    override fun broadcast(data: ByteArray) {
        synchronized(clients) {
            val removed = mutableListOf<Socket>();
            for (client in clients) {
                try {
                    client.outputStream.write(data);
                } catch(t: Throwable) {
                    info("Client ${client.inetAddress.hostAddress} disconnected");
                    try { client.close() } catch (e: IOException) { /* YOLO */ };
                    removed.add(client);
                }
            }
            clients.removeAll(removed);
        }
    }

    override fun close() {
        synchronized(this) {
            if (running) {
                info("Stopping pub/sub server");
                running = false;
                serverSocket.close();
                for (client in clients) client.close();
                thread.interrupt();
                thread.join();
            }
        }
    }
}

class GwenWebSocketPubSubServer: GwenBasePubSubServer {
    private val serverSocket: WebSocketServer;
    private val clients = mutableListOf<WebSocket>();

    constructor(port: Int) {
        serverSocket = object: WebSocketServer(InetSocketAddress(port)) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                synchronized(clients) {
                    clients.add(conn);
                }
            }

            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                synchronized(clients) {
                    clients.remove(conn);
                }
            }

            override fun onMessage(conn: WebSocket, message: String) {
                // No-op
            }

            override fun onStart() {
                Log.info("Websocket pub/sub server started on port $port");
            }

            override fun onError(conn: WebSocket, ex: Exception) {
                Log.info("Error, removing websocket client ${conn.resourceDescriptor}");
            }
        };
        serverSocket.start();
    }

    override fun broadcast(data: ByteArray) {
        synchronized(clients) {
            for (client in clients) {
                client.send(data);
            }
        }
    }

    override fun close() {
        serverSocket.stop();
    }
}

var oauth: OAuth? = null;

var config: GwenConfig? = null;

val gwen = GwenEngine();

fun loadConfig(): GwenConfig? {
    try {
        val configFile = File(appPath, "gwen.json");
        if (!configFile.exists()) {
            debug("No config file found");
            return null;
        } else {
            debug("Loading config")
            return Gson().fromJson<GwenConfig>(JsonReader(FileReader(File(appPath, "gwen.json"))), GwenConfig::class.java);
        }
    } catch (e: Throwable) {
        error("Error loading config", e);
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
            error("Couldn't authorize", t);
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
		 setLogger(logger);
		 for (arg in args) {
			 if (arg.equals("debug", ignoreCase = true))
				 DEBUG();
			 else if (arg.equals("trace", ignoreCase = true)) //
				 TRACE();
		 }

		 var logFile = File(appPath, "/log.txt");
		 try {
			 var output = FileOutputStream(logFile);
			 System.setOut(PrintStream(MultiplexOutputStream(System.out, output), true));
			 System.setErr(PrintStream(MultiplexOutputStream(System.err, output), true));
		 } catch (ex:Throwable) {
			 warn("Unable to write log file.", ex);
		 }

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
                        Log.info("Pub/sub client received hotword, model name: $name, model type: $type");
                    }
                    override fun command(name: String, text: String) {
                        Log.info("Pub/sub client received command, model name: $name, command text: $text");
                    }
                    override fun questionStart(modelName: String, text: String) {
                        Log.info("Pub/sub client received question, model name: $modelName, question text: $text")
                    }

                    override fun questionAnswerAudio(modelName: String, audio: ByteArray) {
                        Log.info("Pub/sub client received answer audio, model name: $modelName, audio length: ${audio.size}");
                    }

                    override fun questionEnd(modelName: String) {
                        Log.info("Pub/sub client received question end, model name: $modelName")
                    }

                    override fun audioInput(audio: ByteArray) {
                        // Log.info("Pub/sub client received audio input, audio length: ${audio.size}");
                    }
                };
            } catch (t: Throwable) {
                error("Couldn't start Gwen, setup through webinterface required", t);
            }
        }
    } catch (e: Throwable) {
        error("Gwen stopped due to unrecoverable error", e);
    }
}
