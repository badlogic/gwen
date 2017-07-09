package com.badlogicgames.gwen;

import java.io.Closeable
import java.io.DataInputStream
import java.net.Socket

enum class GwenModelType (val id: Int) {
    Question(0),
    Command(1)
}

enum class GwenPubSubMessageType(val id: Int) {
    HOTWORD(0),
    COMMAND(1),
    QUESTION(2),
    QUESTION_ANSWER_AUDIO(3),
    QUESTION_END(4),
    AUDIO_INPUT(5)
}

abstract class GwenPubSubClient: Closeable {
    val socket: Socket;
    val thread: Thread;
    @Volatile var running = true;

    constructor(host: String, port: Int) {
        socket = Socket(host, port);
        socket.tcpNoDelay = true;
        thread = Thread(fun() {
            val input = DataInputStream(socket.inputStream);
            while(running) {
                val typeId = input.readByte();
                when(typeId.toInt()) {
                    GwenPubSubMessageType.HOTWORD.id -> {
                        val nameSize = input.readInt();
                        val bytes = ByteArray(nameSize);
                        input.readFully(bytes);
                        hotword(String(bytes), when(input.readInt()) {
                            0 -> GwenModelType.Question
                            1 -> GwenModelType.Command
                            else -> throw Exception("Unknown model type");
                        });
                    }
                    GwenPubSubMessageType.COMMAND.id -> {
                        val nameSize = input.readInt();
                        val nameBytes = ByteArray(nameSize);
                        input.readFully(nameBytes);

                        val textSize = input.readInt();
                        val textBytes = ByteArray(textSize);
                        input.readFully(textBytes);

                        command(String(nameBytes), String(textBytes));
                    }
                    GwenPubSubMessageType.QUESTION.id -> {
                        val nameSize = input.readInt();
                        val nameBytes = ByteArray(nameSize);
                        input.readFully(nameBytes);

                        val textSize = input.readInt();
                        val textBytes = ByteArray(textSize);
                        input.readFully(textBytes);

                        questionStart(String(nameBytes), String(textBytes));
                    }
                    GwenPubSubMessageType.QUESTION_ANSWER_AUDIO.id -> {
                        val nameSize = input.readInt();
                        val nameBytes = ByteArray(nameSize);
                        input.readFully(nameBytes);

                        val audioSize = input.readInt();
                        val audioBytes = ByteArray(audioSize);
                        input.readFully(audioBytes);

                        questionAnswerAudio(String(nameBytes), audioBytes);
                    }
                    GwenPubSubMessageType.QUESTION_END.id -> {
                        val nameSize = input.readInt();
                        val nameBytes = ByteArray(nameSize);
                        input.readFully(nameBytes);

                        questionEnd(String(nameBytes));
                    }
                    GwenPubSubMessageType.AUDIO_INPUT.id -> {
                        val audioSize = input.readInt();
                        val audioBytes = ByteArray(audioSize);
                        input.readFully(audioBytes);

                        audioInput(audioBytes);
                    }
                    else -> throw Exception("Unknown message type $typeId")
                }
            }
        });
        thread.isDaemon = true;
        thread.name = "Pub/sub client thread";
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

    abstract fun hotword(modelName: String, type: GwenModelType);

    abstract fun command(modelName: String, text: String);

    abstract fun questionStart(modelName: String, text: String);

    abstract fun questionAnswerAudio(modelName: String, audio: ByteArray);

    abstract fun questionEnd(modelName: String);

    abstract fun audioInput(audio: ByteArray);
}