package com.badlogicgames.gwen;

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

enum class GwenModelType(val id: Int) {
	Question(0),
	Command(1)
}

enum class GwenPubSubMessageType(val id: Int) {
	HOTWORD(0),
	COMMAND(1),
	QUESTION(2),
	QUESTION_ANSWER_AUDIO(3),
	QUESTION_END(4),
	AUDIO_INPUT(5),
	GET_CONFIG(6),
	SET_CONFIG(7)
}

enum class GwenPubSubClientConfigOptionType(val id: Int) {
	BOOLEAN(0),
	NUMBER(1),
	STRING(2)
}

data class GwenPubSubClientConfigOption(val name: String, val type: GwenPubSubClientConfigOptionType, val value: Any)

data class GwenPubSubClientConfig(val name: String, val description: String, val options: MutableList<GwenPubSubClientConfigOption>)

interface GwenPubSubClient : Closeable {
	fun hotword(modelName: String, type: GwenModelType);

	fun command(modelName: String, text: String);

	fun questionStart(modelName: String, text: String);

	fun questionAnswerAudio(modelName: String, audio: ByteArray);

	fun questionEnd(modelName: String);

	fun audioInput(audio: ByteArray);

	fun getConfig(): GwenPubSubClientConfig;

	fun setOptions(options: List<GwenPubSubClientConfigOption>)
}

abstract class GwenTCPPubSubClient : GwenPubSubClient {
	val socket: Socket;
	val thread: Thread;
	@Volatile var running = true;

	constructor(host: String, port: Int) {
		socket = Socket(host, port);
		socket.tcpNoDelay = true;
		thread = Thread(fun() {
			val input = DataInputStream(socket.inputStream);
			val output = DataOutputStream(socket.outputStream);
			while (running) {
				val typeId = input.readByte();
				when (typeId.toInt()) {
					GwenPubSubMessageType.HOTWORD.id -> {
						hotword(input.readString(), when (input.readInt()) {
							0 -> GwenModelType.Question
							1 -> GwenModelType.Command
							else -> throw Exception("Unknown model type");
						});
					}
					GwenPubSubMessageType.COMMAND.id -> {
						val name = input.readString();
						val text = input.readString();
						command(name, text);
					}
					GwenPubSubMessageType.QUESTION.id -> {
						val name = input.readString();
						val text = input.readString();
						questionStart(name, text);
					}
					GwenPubSubMessageType.QUESTION_ANSWER_AUDIO.id -> {
						val name = input.readString();

						val audioSize = input.readInt();
						val audioBytes = ByteArray(audioSize);
						input.readFully(audioBytes);

						questionAnswerAudio(name, audioBytes);
					}
					GwenPubSubMessageType.QUESTION_END.id -> {
						val name = input.readString()
						questionEnd(name);
					}
					GwenPubSubMessageType.AUDIO_INPUT.id -> {
						val audioSize = input.readInt();
						val audioBytes = ByteArray(audioSize);
						input.readFully(audioBytes);

						audioInput(audioBytes);
					}
					GwenPubSubMessageType.GET_CONFIG.id -> {
						val config = getConfig();
						output.writeString(config.name);
						output.writeString(config.description);
						output.writeInt(config.options.size);
						for (option in config.options) {
							output.writeString(option.name);
							output.writeInt(option.type.id);
							when (option.type) {
								GwenPubSubClientConfigOptionType.BOOLEAN -> output.writeInt(if (option.value as Boolean) 1 else 0)
								GwenPubSubClientConfigOptionType.NUMBER -> output.writeFloat(option.value as Float);
								GwenPubSubClientConfigOptionType.STRING -> output.writeString(option.value as String);
							}
						}
						output.flush();
					}
					GwenPubSubMessageType.SET_CONFIG.id -> {
						val options = mutableListOf<GwenPubSubClientConfigOption>();
						val numOptions = input.readInt();
						for (i in 0 .. numOptions) {
							val optionName = input.readString();
							val optionType: GwenPubSubClientConfigOptionType;
							val optionValue: Any;
							when (input.readInt()) {
								GwenPubSubClientConfigOptionType.BOOLEAN.id -> {
									optionType = GwenPubSubClientConfigOptionType.BOOLEAN;
									optionValue = if (input.readInt() != 0) true else false;
								}
								GwenPubSubClientConfigOptionType.NUMBER.id -> {
									optionType = GwenPubSubClientConfigOptionType.NUMBER;
									optionValue = input.readFloat();
								}
								GwenPubSubClientConfigOptionType.STRING.id -> {
									optionType = GwenPubSubClientConfigOptionType.STRING;
									optionValue = input.readString();
								}
								else -> throw Exception("Unknown option type")
							}
							options.add(GwenPubSubClientConfigOption(optionName, optionType, optionValue));
						}
						setOptions(options);
					}
					else -> throw Exception("Unknown message type $typeId")
				}
			}
		});
		thread.isDaemon = true;
		thread.name = "GwenPubSubClient";
		thread.start();
	}

	override fun close() {
		synchronized(this) {
			if (running) {
				running = false;
				socket.close();
				thread.interrupt();
				thread.join();
			}
		}
	}

	abstract override fun hotword(modelName: String, type: GwenModelType);

	abstract override fun command(modelName: String, text: String);

	abstract override fun questionStart(modelName: String, text: String);

	abstract override fun questionAnswerAudio(modelName: String, audio: ByteArray);

	abstract override fun questionEnd(modelName: String);

	abstract override fun audioInput(audio: ByteArray);
}

fun DataInputStream.readString(): String {
	val size = this.readInt();
	val bytes = ByteArray(size);
	this.readFully(bytes);
	return String(bytes);
}

fun DataOutputStream.writeString(str: String) {
	val bytes = str.toByteArray();
	this.writeInt(bytes.size);
	this.write(bytes);
	this.flush();
}