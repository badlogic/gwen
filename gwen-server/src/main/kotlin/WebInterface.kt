package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log
import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Inet4Address

fun startWebInterface(gwenConfig: GwenConfig, oauth: OAuth, gwen: GwenEngine, port: Int = 8777) {
	val server = HttpServer.create(InetSocketAddress(port), 0);
	server.createContext("/", WebInterface(gwenConfig, oauth, gwen));
	server.start();
	Log.debug("Started web interface on port $port");
}

fun printWebInterfaceUrls() {
	for (iface in NetworkInterface.getNetworkInterfaces()) {
		try {
			if (iface.isLoopback) continue;
		} catch (ignored: Exception) {
		}
		for (iaddr in iface.interfaceAddresses) {
			// Because we don't like IPV6 and don't know how to get the local IP address...
			if (iaddr.address !is Inet4Address) continue;
			Log.info("http://${iaddr.address.hostName}:8777/");
		}
	}
}

val MIMETYPE_PLAINTEXT = "text/plain";
val MIMETYPE_HTML = "text/html";
val MIMETYPE_CSS = "text/css";
val MIMETYPE_BINARY = "application/octet-stream"
val MIMETYPE_JSON = "application/json"
val MIMETYPE_PNG = "image/png"
val MIMETYPE_JPEG = "image/jpeg"
val MIMETYPE_GIF = "image/gif"
val MIMETYPE_JS = "text/javascript"

class WebInterface (val gwenConfig: GwenConfig, val oauth: OAuth, val gwen: GwenEngine) : HttpHandler {

	override fun handle(request: HttpExchange) {
		Log.trace("Hanlding request ${request.requestURI.path}")

		// redirect to setup pages
		if (request.requestURI.path.endsWith(".html") || request.requestURI.path == "/") {
			if (gwenConfig.assistantConfig == null && request.requestURI.path != "/setup-project.html") {
				redirect(request, "/setup-project.html")
				return;
			} else {
				if (gwenConfig.assistantConfig != null && !oauth.isAuthorized() && request.requestURI.path != "/setup-oauth.html") {
					redirect(request, "/setup-oauth.html")
					return;
				};
			}
		}

		// Laugh, but it works! :D
		when (request.requestURI.path) {
			"/" -> respond(request, extractFromClasspath("assets/web/index.html"), MIMETYPE_HTML)
			"/projectSave" -> handleProjectSave(request);
			"/authorizationUrl" -> handleAuthorizationUrl(request);
			"/accountSave" -> handleAccountSave(request);
			"/models" -> handleModels(request);
			"/modelSave" -> handleModelSave(request);
			"/modelDelete" -> handleModelDelete(request);
			"/modelTrigger" -> handleModelTrigger(request);
			"/status" -> handleStatus(request);
			"/restart" -> handleRestart();
			"/config" -> handleGetConfig(request);
			"/configSave" -> handleSetConfig(request);
			else -> handleFile(request);
		}
	}

	private fun respond(request: HttpExchange, content: ByteArray, type: String, status: Int = 200) {
		request.responseHeaders.add("Content-Type", type);
		request.sendResponseHeaders(status, content.size.toLong());
		request.responseBody.use {
			it.write(content);
		}
	}

	private fun error(request: HttpExchange, message: String, status: Int) {
		respond(request, message.toByteArray(), MIMETYPE_PLAINTEXT, status);
	}

	private fun redirect(request: HttpExchange, url: String) {
		request.responseHeaders.add("Location", url);
		request.sendResponseHeaders(302, 0);
		request.responseBody.close();
	}

	private fun handleFile(request: HttpExchange) {
		val file = "assets/web" + request.requestURI.path;
		val extension = File(file).extension;
		val type: String
		when (extension.toLowerCase()) {
			"png" -> type = MIMETYPE_PNG
			"jpg", "jpeg" -> type = MIMETYPE_JPEG
			"gif" -> type = MIMETYPE_GIF
			"html" -> type = MIMETYPE_HTML
			"css" -> type = MIMETYPE_CSS
			"js" -> type = MIMETYPE_JS
			else -> type = MIMETYPE_BINARY
		}
		respond(request, extractFromClasspath(file), type);
	}

	private fun parseParams(request: HttpExchange): Map<String, String> {
		val params = mutableMapOf<String, String>();
		if (request.requestURI.query == null) return params;
		request.requestURI.query.split("&")
				  .filter { it.contains("=") }
				  .map { it.split("=") }
				  .forEach { params.put(it[0], it[1]) }
		return params;
	}

	private fun handleProjectSave(request: HttpExchange) {
		val params = parseParams(request);
		val id = params["clientId"];
		val secret = params["clientSecret"];

		Log.info("Saving project config");
		if (id != null && !id.isEmpty() && secret != null && !secret.isEmpty()) {
			gwenConfig.assistantConfig = GoogleAssistantConfig(id, secret);
			gwenConfig.credentials = null;
			try {
				gwenConfig.save();
			} catch(t: Throwable) {
				Log.error("Couldn't save config", t);
				error(request, "Couldn't save config", 400);
				File(appPath, "gwen.json").delete();
			}
			gwen.stop();
			redirect(request, "/");
		} else {
			error(request, "Invalid client id & secret", 400);
		}
	}

	private fun handleAccountSave(request: HttpExchange) {
		val params = parseParams(request);
		val code = params["code"];

		Log.info("Got OAuth code, saving account");
		if (code != null && !code.isEmpty()) {
			try {
				oauth.requestAccessToken(code);
			} catch(t: Throwable) {
				Log.error("Couldn't authorize", t);
				error(request, "Couldn't authorize", 400);
				return;
			}
			if (oauth.isAuthorized()) {
				gwen.stop();
				gwen.start(gwenConfig, oauth, gwen.pubSubServer);
				redirect(request, "/");
			} else {
				error(request, "Invalid code, authorization failed", 400);
			}
		} else {
			error(request, "Invalid code, authorization failed", 400);
		}
	}

	private fun handleModels(request: HttpExchange) {
		respond(request, Gson().toJson(gwen.models).toByteArray(), MIMETYPE_JSON);
	}

	class FileHandler : FormDataHandler() {
		var parts = emptyMap<String, MultiPart>()
		override fun handle(httpExchange: HttpExchange, parts: MutableList<MultiPart>) {
			val result = mutableMapOf<String, MultiPart>()
			for (part in parts) {
				result[part.name] = part;
			}
			this.parts = result;
		}
	}

	private fun handleModelSave(request: HttpExchange) {
		val fileHandler = FileHandler()
		fileHandler.handle(request)
		val parts = fileHandler.parts;

		if (parts["modelName"] == null || parts["modelType"] == null || parts["file"] == null) {
			Log.error("Couldn't save model, request incomplete");
			error(request, "Couldn't save model, request incomplete", 400);
		} else {
			try {
				gwen.addModel(parts["modelName"]!!.value, parts["file"]!!.filename, GwenModelType.valueOf(parts["modelType"]!!.value), parts["file"]!!.bytes);
				handleModels(request);
			} catch(t: Throwable) {
				Log.error("Couldn't save model", t);
				error(request, "Couldn't save model", 400);
			}
		}
	}

	private fun handleModelDelete(request: HttpExchange) {
		val params = parseParams(request);
		val modelName = params["name"];

		if (modelName != null) {
			try {
				gwen.deleteModel(modelName);
				handleModels(request);
			} catch(t: Throwable) {
				Log.error("Couldn't delete model $modelName", t);
				error(request, "Couldn't delete model", 400);
			}
		} else {
			error(request, "Couldn't delete model", 400);
		}
	}

	private fun handleModelTrigger(request: HttpExchange) {
		val params = parseParams(request);
		val modelName = params["name"];

		if (modelName != null) {
			try {
				gwen.triggerModel(modelName);
				handleModels(request);
			} catch(t: Throwable) {
				Log.error("Couldn't trigger model $modelName", t);
				error(request, "Couldn't trigger model", 400);
			}
		} else {
			error(request, "Couldn't trigger model", 400);
		}
	}

	private fun handleAuthorizationUrl(request: HttpExchange) {
		respond(request, """{ "authorizationUrl": "${oauth.getAuthorizationURL()}" }""".toByteArray(), MIMETYPE_JSON);
	}

	private fun handleStatus(request: HttpExchange) {
		val params = parseParams(request);
		val timeStamp = System.currentTimeMillis();
		val requestTimeStamp = params["timeStamp"];
		val logs = Gson().toJson(logger.getSince(requestTimeStamp?.toLong() ?: 0));
		respond(request, """{ "status": ${gwen.running}, "log": $logs, "timeStamp": $timeStamp }""".toByteArray(), MIMETYPE_JSON);
	}

	private fun handleRestart() {
		Log.info("Restarting");
		gwen.start(gwenConfig, oauth, gwen.pubSubServer);
	}

	private fun handleGetConfig(request: HttpExchange) {
		respond(request, Gson().toJson(gwenConfig).toByteArray(), MIMETYPE_JSON);
	}

	private fun handleSetConfig(request: HttpExchange) {
		val params = parseParams(request);
		val playAudioLocally = params["playAudioLocally"]?.toBoolean();
		val recordStereo = params["recordStereo"]?.toBoolean();
		val sendLocalAudioInput = params["sendLocalAudioInput"]?.toBoolean();
		val pubSubPort = params["pubSubPort"]?.toInt();
		val websocketPubSubPort = params["websocketPubSubPort"]?.toInt();

		if (playAudioLocally == null || recordStereo == null || sendLocalAudioInput == null || pubSubPort == null || websocketPubSubPort == null) {
			error(request, "Invalid config", 400);
		} else {
			Log.info("Saving config");
			gwenConfig.playAudioLocally = playAudioLocally;
			gwenConfig.sendLocalAudioInput = sendLocalAudioInput;
			gwenConfig.recordStereo = recordStereo;
			gwenConfig.pubSubPort = pubSubPort;
			gwenConfig.websocketPubSubPort = websocketPubSubPort;
			gwenConfig.save();
			gwen.stop();
			gwen.start(gwenConfig, oauth, gwen.pubSubServer);
			handleGetConfig(request);
		}
	}
}
