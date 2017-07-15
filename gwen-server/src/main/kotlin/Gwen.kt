@file:JvmName("Gwen")

package com.badlogicgames.gwen

import com.esotericsoftware.minlog.Log
import com.esotericsoftware.minlog.Log.*
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import utils.MultiplexOutputStream
import java.io.*

val appPath: File by lazy {
	File(".").canonicalFile;
}

class Logger : Log.Logger() {
	val logs = mutableListOf<Pair<Long, String>>();

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

data class GwenModel(val name: String, val file: String, val type: GwenModelType, @Transient var detector: HotwordDetector);

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
				val audioRecorder = LocalAudioRecorder(16000, 1600, config.recordStereo);
				val audioPlayer = if (config.playAudioLocally) LocalAudioPlayer(16000) else NullAudioPlayer();
				models = loadModels();
				val assistant = GoogleAssistant();
				val thread = Thread(fun() {
					try {
						assistant.initialize(oauth);
						info("Gwen started");
						while (running) {
							audioRecorder.read();
							if (config.sendLocalAudioInput) pubSubServer?.audioInput(audioRecorder.getByteData());
							synchronized(this) {
								for ((name, _, type, detector) in models) {
									if (detector.detect(audioRecorder.getShortData())) {
										pubSubServer?.hotwordDetected(name, type);
										info("Hotword detected: $name ($type)");
										when (type) {
											GwenModelType.Question -> {
												debug("Starting assistant conversation");
												// FIXME should we continue conversation?
												assistant.converse(oauth, audioRecorder, audioPlayer, object : GoogleAssistant.GoogleAssistantCallback {
													override fun questionComplete(question: String) {
														info("Question: $question");
														pubSubServer?.question(name, question);
													}

													override fun answerAudio(audio: ByteArray) {
														if (config.playAudioLocally) audioPlayer.play(audio, 0, audio.size);
														pubSubServer?.questionAnswerAudio(name, audio);
													}
												});
												info("Conversation ended");
												pubSubServer?.questionEnd(name);
											}
											GwenModelType.Command -> {
												debug("Starting speech-to-text");
												val command = assistant.speechToText(oauth, audioRecorder, audioPlayer);
												info("Command: $command");
												if (!command.isEmpty()) pubSubServer?.command(name, command);
											}
										}
										for (model in models)
											if (model.detector != detector) model.detector.reset();
										debug("Waiting for hotword");
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
				thread.name = "GwenEngine";
				this.thread = thread;
				running = true;
				thread.start();
			} catch (t: Throwable) {
				error("Error starting Gwen", t);
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
						  GwenModel("Snowboy", "assets/snowboy/snowboy.umdl", GwenModelType.Command, SnowboyHotwordDetector("assets/snowboy/snowboy.umdl")),
						  GwenModel("Alexa", "assets/snowboy/alexa.umdl", GwenModelType.Question, SnowboyHotwordDetector("assets/snowboy/alexa.umdl"))
				);
			}
			FileWriter(modelConfig).use {
				Gson().toJson(models, it);
			}
			return models;
		} else {
			models = Gson().fromJson<Array<GwenModel>>(JsonReader(FileReader(File(appPath, "models.json"))), Array<GwenModel>::class.java);
			for (model in models) {
				info("Loading model: ${model.name} (${model.type})")
				if (model.file.endsWith(".umdl") || model.file.endsWith(".pmdl"))
					if (model.file.startsWith("assets/snowboy"))
						model.detector = SnowboyHotwordDetector(model.file);
					else
						model.detector = SnowboyHotwordDetector(File(appPath, model.file));
				else
					model.detector = WebHotwordDetector();
			}
			return models;
		}
	}

	@Synchronized fun addModel(name: String, fileName: String, type: GwenModelType, modelData: ByteArray) {
		info("Adding model: $name, $type");

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
		info("Deleting model: $modelName");

		val newModels = models.toMutableList();
		newModels.removeIf {
			if (it.name == modelName) {
				it.detector.close();
			}
			it.name == modelName;
		}
		val modelConfig = File(appPath, "models.json");
		FileWriter(modelConfig).use {
			Gson().toJson(newModels, it);
		}
		models = newModels.toTypedArray();
	}

	@Synchronized fun triggerModel(modelName: String) {
		info("Triggering model: $modelName");

		val newModels = models.toMutableList();
		newModels.removeIf {
			if (it.name == modelName) {
				it.detector.trigger()
			}
			it.name == modelName;
		}
	}

	fun stop() {
		if (running) {
			debug("Stopping Gwen");
			synchronized(this) {
				running = false;
			}
			pubSubServer?.close();
			thread?.join();
		}
	}
}

val logger by lazy { Logger() }

fun main(args: Array<String>) {
	try {
		setLogger(logger);

		for (arg in args) {
			when (arg.toLowerCase()) {
				"debug" -> DEBUG();
				"trace" -> TRACE();
			}
		}

		val logFile = File(appPath, "log.txt");
		try {
			val output = FileOutputStream(logFile);
			System.setOut(PrintStream(MultiplexOutputStream(System.out, output), true));
			System.setErr(PrintStream(MultiplexOutputStream(System.err, output), true));
		} catch (ex: Throwable) {
			warn("Unable to write log file", ex);
		}

		val config = loadConfig();
		val oauth = loadOAuth(config);
		val gwen = GwenEngine();

		startWebInterface(config, oauth, gwen);
		if (config.assistantConfig == null || !oauth.isAuthorized()) {
			println("Setup through web interface required");
		} else {
			try {
				gwen.start(config, oauth);
			} catch (t: Throwable) {
				error("Error starting Gwen, setup through webinterface required", t);
			}
		}
	} catch (e: Throwable) {
		error("Gwen stopped due to an unrecoverable error", e);
	}
}
