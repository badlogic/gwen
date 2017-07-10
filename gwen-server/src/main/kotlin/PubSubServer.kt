package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

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
         val thread = Thread(fun () {
             while (running) {
                 val client = serverSocket.accept();
                 synchronized(clients) {
                     client.tcpNoDelay = true;
                     clients.add(client);
                 }
                 Log.info("New TCP pub/sub client (${client.inetAddress.hostAddress})");
             }
         });
         this.thread = thread;
         thread.isDaemon = true;
         thread.name = "Pub/sub server thread";
         thread.start();
         Log.info("TCP pub/sub server started on port $config.pubSubPort");
    }

    override fun broadcast(data: ByteArray) {
        synchronized(clients) {
            val removed = mutableListOf<Socket>();
            for (client in clients) {
                try {
                    client.outputStream.write(data);
                } catch(t: Throwable) {
                    Log.info("TCP client ${client.inetAddress.hostAddress} disconnected");
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
                Log.info("Stopping TCP pub/sub server");
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
    private var serverSocket: WebSocketServer;
    private val clients = mutableListOf<WebSocket>();

	constructor(port: Int) {
         serverSocket = object: WebSocketServer(InetSocketAddress(port)) {
             override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                 synchronized(clients) {
                     clients.add(conn);
                     Log.info("New Websocket pub/sub client (${conn.remoteSocketAddress.address.hostAddress})");
                 }
             }

             override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                 synchronized(clients) {
                     clients.remove(conn);
                     Log.info("Websocket client ${conn.remoteSocketAddress.address.hostAddress} disconnected");
                 }
             }

             override fun onMessage(conn: WebSocket, message: String) {
                 // No-op
             }

             override fun onStart() {
                 Log.info("Websocket pub/sub server started on port $port");
             }

             override fun onError(conn: WebSocket, ex: Exception) {
                 Log.info("Error, removing Websocket client ${conn.remoteSocketAddress.address.hostAddress}");
                 synchronized(clients) {
                     clients.remove(conn);
                 }
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
        Log.info("Stopping Websocket pub/sub server");
        serverSocket.stop();
    }
}
