package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log.*
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.*
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException

data class GwenPubSubClientConfigWithId(val config: GwenPubSubClientConfig, val id: Int)

interface GwenPubSubServer : Closeable {
	fun hotwordDetected(name: String, type: GwenModelType);
	fun command(name: String, text: String);
	fun question(name: String, question: String);
	fun questionAnswerAudio(name: String, audio: ByteArray);
	fun questionEnd(name: String);
	fun audioInput(audio: ByteArray);
	fun broadcast(data: ByteArray);
	fun send(id: Int, data: ByteArray);
	fun getClientConfigs(): List<GwenPubSubClientConfigWithId>;
	fun setClientConfigOptions(id: Int, options: List<GwenPubSubClientConfigOption>);
}

class GwenComposablePubSubServer : GwenPubSubServer {
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

	override fun send(id: Int, data: ByteArray) {
		for (server in servers) server.send(id, data);
	}

	override fun getClientConfigs(): List<GwenPubSubClientConfigWithId> {
		val configs = mutableListOf<GwenPubSubClientConfigWithId>();
		for (server in servers) configs.addAll(server.getClientConfigs());
		return configs;
	}

	override fun setClientConfigOptions(id: Int, options: List<GwenPubSubClientConfigOption>) {
		for (server in servers) server.setClientConfigOptions(id, options);
	}
}

abstract class GwenNetworkedPubSubServer<T> : GwenPubSubServer {
	protected val clients = mutableListOf<Client<T>>();

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

	override fun getClientConfigs(): List<GwenPubSubClientConfigWithId> {
		val bytes = ByteArrayOutputStream();
		val out = DataOutputStream(bytes);
		out.writeByte(GwenPubSubMessageType.GET_CONFIG.id);
		out.flush();
		broadcast(bytes.toByteArray());
		val configs = mutableListOf<GwenPubSubClientConfigWithId>();
		synchronized(clients) {
			val removed = mutableListOf<Client<T>>();
			for (client in clients) {
				try {
					val input = getClientInput(client);
					val name = input.readString();
					val description = input.readString();
					val numOptions = input.readInt();
					val options = mutableListOf<GwenPubSubClientConfigOption>();
					for (i in 0 until numOptions) {
						val optionName = input.readString();
						val optionType = input.readInt();
						when (optionType) {
							GwenPubSubClientConfigOptionType.BOOLEAN.id -> options.add(GwenPubSubClientConfigOption(optionName, GwenPubSubClientConfigOptionType.BOOLEAN, input.readInt() != 0));
							GwenPubSubClientConfigOptionType.NUMBER.id -> options.add(GwenPubSubClientConfigOption(optionName, GwenPubSubClientConfigOptionType.NUMBER, input.readFloat()));
							GwenPubSubClientConfigOptionType.STRING.id -> options.add(GwenPubSubClientConfigOption(optionName, GwenPubSubClientConfigOptionType.STRING, input.readString()));
						}
					}
					configs.add(GwenPubSubClientConfigWithId(GwenPubSubClientConfig(name, description, options), client.id));
				} catch (t: Throwable) {
					info("Removed pub/sub client while reading config: ${client.socket}");
					removed.add(client);
				}
			}
			clients.removeAll(removed);
		}
		return configs;
	}

	override fun setClientConfigOptions(id: Int, options: List<GwenPubSubClientConfigOption>) {
		synchronized(clients) {
			val removed = mutableListOf<Client<T>>();
			for (client in clients) {
				if (client.id == id) {
					try {
						val bytes = ByteArrayOutputStream();
						val out = DataOutputStream(bytes);
						out.writeByte(GwenPubSubMessageType.SET_CONFIG.id);
						out.writeInt(options.size);
						for (option in options) {
							out.writeString(option.name);
							out.writeInt(option.type.id);
							when (option.type) {
								GwenPubSubClientConfigOptionType.BOOLEAN -> out.writeInt(if (option.value as Boolean) 1 else 0)
								GwenPubSubClientConfigOptionType.NUMBER -> out.writeFloat(option.value as Float);
								GwenPubSubClientConfigOptionType.STRING -> out.writeString(option.value as String);
							}
						}
						out.flush();
						send(id, bytes.toByteArray());
						break;
					} catch (t: Throwable) {
						info("Removed pub/sub client while reading config: ${client.socket}");
						removed.add(client);
					}
					break;
				}
			}
			clients.removeAll(removed);
		}
	}

	protected abstract fun getClientInput(client: Client<T>): DataInputStream;
}

@Volatile var nextClientId = 0;

open class Client<T>(val socket: T) {
	val id: Int = nextClientId++;
};

class GwenTCPPubSubServer : GwenNetworkedPubSubServer<Socket> {
	private val serverSocket: ServerSocket;
	private val thread: Thread;
	@Volatile var running = true;

	constructor(port: Int) {
		serverSocket = ServerSocket(port);
		val thread = Thread(fun() {
			while (running) {
				val client = serverSocket.accept();
				synchronized(clients) {
					client.tcpNoDelay = true;
					client.soTimeout = 2000;
					clients.add(Client(client));
				}
				info("TCP pub/sub client connected: ${client.inetAddress.hostAddress}");
			}
			info("TCP pub/sub server stopped");
		});
		this.thread = thread;
		thread.isDaemon = true;
		thread.name = "GwenTCPPubSubServer";
		thread.start();
		info("TCP pub/sub server started on port: $port");
	}

	override fun broadcast(data: ByteArray) {
		synchronized(clients) {
			val removed = mutableListOf<Client<Socket>>();
			for (client in clients) {
				try {
					client.socket.outputStream.write(data);
				} catch(t: Throwable) {
					info("TCP pub/sub client disconnected: ${client.socket.inetAddress.hostAddress}");
					try {
						client.socket.close()
					} catch (e: IOException) { /* YOLO */
					};
					removed.add(client);
				}
			}
			clients.removeAll(removed);
		}
	}

	override fun send(id: Int, data: ByteArray) {
		synchronized(clients) {
			val removed = mutableListOf<Client<Socket>>();
			for (client in clients) {
				if (client.id == id) {
					try {
						client.socket.outputStream.write(data);
					} catch(t: Throwable) {
						info("TCP pub/sub client disconnected: ${client.socket.inetAddress.hostAddress}");
						try {
							client.socket.close()
						} catch (e: IOException) { /* YOLO */
						};
						removed.add(client);
					}
					break;
				}
			}
			clients.removeAll(removed);
		}
	}

	override fun close() {
		synchronized(this) {
			if (running) {
				debug("Stopping TCP pub/sub server");
				running = false;
				serverSocket.close();
				for (client in clients) client.socket.close();
				thread.interrupt();
				thread.join();
			}
		}
	}

	override fun getClientInput(client: Client<Socket>): DataInputStream {
		return DataInputStream(client.socket.getInputStream());
	}
}

class GwenWebSocketPubSubServer : GwenNetworkedPubSubServer<WebSocket> {
	private var serverSocket: WebSocketServer;

	class WebsocketClient(socket: WebSocket): Client<WebSocket>(socket) {
		val bytes = mutableListOf<Byte>();

		fun getInputStream(): InputStream {
			return object: InputStream() {
				override fun read(): Int {
					// Welp...
					val start = System.nanoTime();
					while(System.nanoTime() - start < 2000000000L) {
						synchronized(bytes) {
							if(bytes.size > 0) return bytes.removeAt(0).toInt();
						}
					}
					throw TimeoutException("Socket timed out");
				}
			};
		}
	}

	constructor(port: Int) {
		serverSocket = object : WebSocketServer(InetSocketAddress(port)) {
			override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
				synchronized(clients) {
					clients.add(Client<WebSocket>(conn));
					info("Websocket pub/sub client connected: ${conn.remoteSocketAddress.address.hostAddress}");
				}
			}

			override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
				synchronized(clients) {
					val iter = clients.iterator();
					while (iter.hasNext()) {
						val client = iter.next();
						if (client.socket.localSocketAddress == conn.localSocketAddress) {
							iter.remove();
							break;
						}
					}
					info("Websocket pub/sub client disconnected: ${conn.remoteSocketAddress.address.hostAddress}");
				}
			}

			override fun onMessage(conn: WebSocket, message: String) {
			}

			override fun onMessage(conn: WebSocket, message: ByteBuffer) {
				synchronized(clients) {
					val iter = clients.iterator();
					while (iter.hasNext()) {
						val client = iter.next();
						if (client.socket.localSocketAddress == conn.localSocketAddress) {
							val wsClient = client as WebsocketClient;
							synchronized(wsClient.bytes) {
								while (message.remaining() > 0) {
									wsClient.bytes.add(message.get());
								}
							}
							break;
						}
					}
				}
			}

			override fun onStart() {
			}

			override fun onError(conn: WebSocket, ex: Exception) {
				error("Websocket client error: ${conn.remoteSocketAddress.address.hostAddress}", ex);
				conn.close();
				synchronized(clients) {
					val iter = clients.iterator();
					while (iter.hasNext()) {
						val client = iter.next();
						if (client.socket.localSocketAddress == conn.localSocketAddress) {
							iter.remove();
							break;
						}
					}
				}
			}
		};
		serverSocket.isTcpNoDelay = true;
		serverSocket.start();
		info("Websocket pub/sub server started on port: $port");
	}

	override fun broadcast(data: ByteArray) {
		synchronized(clients) {
			for (client in clients) {
				client.socket.send(data);
			}
		}
	}

	override fun send(id: Int, data: ByteArray) {
		synchronized(clients) {
			for (client in clients) {
				if (client.id == id) {
					client.socket.send(data);
					break;
				}
			}
		}
	}

	override fun getClientInput(client: Client<WebSocket>): DataInputStream {
		val wsClient = client as WebsocketClient;
		return DataInputStream(wsClient.getInputStream());
	}

	override fun close() {
		serverSocket.stop();
		info("Websocket pub/sub server stopped");
	}
}
