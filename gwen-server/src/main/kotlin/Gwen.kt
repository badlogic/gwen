@file:JvmName("Gwen")

package com.badlogicgames.gwen

import com.esotericsoftware.minlog.Log
import com.esotericsoftware.minlog.Log.*
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import utils.MultiplexOutputStream
import java.io.*
import java.net.NetworkInterface

val appPath: File by lazy {
	File(".").canonicalFile;
}

class Logger : Log.Logger {
	val logs = mutableListOf<Pair<Long, String>>();

	constructor () {
	}

	@Synchronized override fun print(message: String) {
		super.print(message)
		add(message);
	}

	@Synchronized fun add(message: String) {
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
							 val playAudioLocally: Boolean = true,
							 val sendLocalAudioInput: Boolean = false,
							 val recordStereo: Boolean = false,
							 val pubSubPort: Int = 8778,
							 val websocketPubSubPort: Int = 8779);

data class GwenModel(val name: String, val file: String, val type: GwenModelType, @kotlin.jvm.Transient var detector: HotwordDetector);


class GwenEngine {
	@Volatile var running = false;
	@Volatile var models: Array<GwenModel> = emptyArray();
	var thread: Thread? = null;
	var pubSubServer: GwenPubSubServer? = null;

	fun start(config: GwenConfig, oauth: OAuth, pubSubServer: GwenPubSubServer? = GwenComposablePubSubServer(
			  GwenTCPPubSubServer(config.pubSubPort),
			  GwenWebSocketPubSubServer(config.websocketPubSubPort)
	)) {
		stop();
		synchronized(this) {
			this.pubSubServer = pubSubServer;
			try {
				val audioPlayer = if (config.playAudioLocally) LocalAudioPlayer(16000) else NullAudioPlayer();
				val audioRecorder = LocalAudioRecorder(16000, 1600, config.recordStereo);
				models = loadModels();
				val assistant = GoogleAssistant(oauth, audioRecorder, audioPlayer);
				val thread = Thread(fun() {
					try {
						info("Gwen started");
						while (running) {
							audioRecorder.read();
							if (config.sendLocalAudioInput) pubSubServer?.audioInput(audioRecorder.getByteData());
							synchronized(this) {
								for (model in models) {
									if (model.detector.detect(audioRecorder.getShortData())) {
										pubSubServer?.hotwordDetected(model.name, model.type);
										when (model.type) {
											GwenModelType.Question -> {
												info("QA hotword detected, starting assistant conversation");
												// FIXME should we continue conversation?
												assistant.converse(object : GoogleAssistant.GoogleAssistantCallback {
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
				running = true;
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
			if (it.name.equals(modelName)) {
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
			if (it.name.equals(modelName)) {
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
			pubSubServer?.close();
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
			else if (arg.equals("trace", ignoreCase = true))
				TRACE();
		}

		var logFile = File(appPath, "/log.txt");
		try {
			var output = FileOutputStream(logFile);
			System.setOut(PrintStream(MultiplexOutputStream(System.out, output), true));
			System.setErr(PrintStream(MultiplexOutputStream(System.err, output), true));
		} catch (ex: Throwable) {
			warn("Unable to write log file.", ex);
		}

		config = loadConfig();
		config?.let { oauth = loadOAuth(it); };

		startWebInterface();
		if (config == null || oauth == null || !oauth!!.isAuthorized()) {
			println("Setup through web interface required (http://<local-ip-address>:8777)");
			printWebInterfaceUrl();
		} else {
			printWebInterfaceUrl();
			try {
				gwen.start(config!!, oauth!!);
			} catch (t: Throwable) {
				error("Couldn't start Gwen, setup through webinterface required", t);
			}
		}
	} catch (e: Throwable) {
		error("Gwen stopped due to unrecoverable error", e);
	}
}
